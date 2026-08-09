import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dev-only, idempotent Practice showcase importer.
 *
 * <p>This is intentionally not a Flyway migration. It creates governed local
 * media, the complete live authoring graph, immutable published snapshots and
 * material references without changing the Practice AI or storage settings.
 * It never deletes data outside its own fixture namespace.</p>
 */
public final class PracticeShowcaseSeeder {

    private static final String FIXTURE_KEY = "ksh-practice-showcase-v1";
    private static final int FIXTURE_VERSION = 1;
    private static final int EXPECTED_SET_COUNT = 13;
    private static final String LECTURER_EMAIL = "lecturer@ksh.edu.vn";
    private static final String MATERIAL_PREFIX = "/practice/materials/";
    private static final String MATERIAL_SUFFIX = "/content";
    private static final String BUILT_IN_LISTENING_CHECK =
            "/audio/practice/listening-speaker-check.wav";

    private static final Theme[] THEMES = {
            new Theme("daily-life", "처음 만나는 한국 생활",
                    "Các tình huống sinh hoạt hằng ngày từ chào hỏi đến sắp xếp lịch.",
                    "일상생활", "cover-01-daily-life.png"),
            new Theme("campus", "대학 생활 완전 정복",
                    "Ngôn ngữ học đường, câu lạc bộ và hỗ trợ sinh viên.",
                    "대학 생활", "cover-02-campus.png"),
            new Theme("travel", "한국 여행 실전 한국어",
                    "Giao thông, lưu trú và xử lý tình huống khi du lịch Hàn Quốc.",
                    "여행과 교통", "cover-03-travel.png"),
            new Theme("shopping", "쇼핑과 서비스 한국어",
                    "Đặt hàng, đổi trả và giao tiếp tại các điểm dịch vụ.",
                    "쇼핑과 서비스", "cover-04-shopping.png"),
            new Theme("health", "건강하고 안전한 생활",
                    "Bệnh viện, nhà thuốc, sức khỏe và thông báo an toàn.",
                    "건강과 안전", "cover-05-health.png"),
            new Theme("work", "취업과 직장 한국어",
                    "Phỏng vấn, lịch làm việc, báo cáo và phối hợp trong công sở.",
                    "취업과 직장", "cover-06-work.png"),
            new Theme("culture", "한국 문화 깊이 읽기",
                    "Lễ hội, biểu diễn, truyền thông và trải nghiệm văn hóa.",
                    "문화와 미디어", "cover-07-culture.png"),
            new Theme("environment", "환경과 우리 동네",
                    "Môi trường, tái chế và các đề xuất cải thiện khu dân cư.",
                    "환경과 지역", "cover-08-environment.png"),
            new Theme("digital", "디지털 생활과 미디어",
                    "Học trực tuyến, ứng dụng và cách sử dụng thông tin số.",
                    "디지털 생활", "cover-09-digital.png"),
            new Theme("community", "지역 사회와 공공 서비스",
                    "Thư viện, trung tâm cư dân và các dịch vụ công cộng.",
                    "공공 서비스", "cover-10-community.png"),
            new Theme("food", "한국 음식과 식생활",
                    "Thực đơn, cách nấu ăn và văn hóa ẩm thực Hàn Quốc.",
                    "음식과 식생활", "cover-11-food.png"),
            new Theme("future", "미래 계획과 자기 계발",
                    "Mục tiêu học tập, kế hoạch nghề nghiệp và phát triển bản thân.",
                    "미래와 자기 계발", "cover-12-future.png"),
            new Theme("social", "사회 이슈 토론 한국어",
                    "Đọc dữ liệu, trình bày lập trường và tranh luận có căn cứ.",
                    "사회 이슈", "cover-13-social.png")
    };

    private static final String[] TEST_TITLES = {
            "Test 1 — 기초 진단",
            "Test 2 — 실전 응용",
            "Test 3 — 종합 모의고사"
    };

    private static final String[] TEST_DESCRIPTIONS = {
            "Bài chẩn đoán nền tảng, ưu tiên hiểu đúng thông tin trực tiếp.",
            "Bài vận dụng tình huống, yêu cầu kết nối nhiều chi tiết.",
            "Bài mô phỏng tổng hợp với nhịp độ và lượng thông tin cao hơn."
    };

    private static final ReadingScenario[] READING_SCENARIOS = {
            new ReadingScenario(
                    "도서관 운영 안내",
                    "푸른마을 도서관은 8월부터 평일 문을 한 시간 일찍 엽니다. 오전 8시에 문을 열고 오후 8시에 닫습니다. 토요일에는 어린이 독서 모임이 있어 2층 열람실을 사용할 수 없습니다. 책을 반납하는 기계는 건물 밖에 있어 도서관이 닫힌 뒤에도 이용할 수 있습니다.",
                    "푸른마을 도서관", "오전 8시", "책 반납", "도서관 운영 변경을 알리기 위해",
                    "토요일에는 누구나 2층 열람실을 이용할 수 있습니다.",
                    "stimulus-04-library.png"),
            new ReadingScenario(
                    "주말 열차 안내",
                    "바다역으로 가는 주말 급행열차는 서울역에서 오전 10시 10분에 출발합니다. 표는 출발 20분 전까지 살 수 있고, 큰 짐은 3호차 보관실에 두어야 합니다. 비가 많이 오면 출발 시간이 바뀔 수 있으니 역의 안내판을 확인하십시오.",
                    "서울역", "오전 10시 10분", "안내판 확인", "주말 열차 이용 방법을 안내하기 위해",
                    "급행열차 표는 출발한 뒤에도 살 수 있습니다.",
                    "stimulus-01-train.png"),
            new ReadingScenario(
                    "카페 할인 행사",
                    "한결 카페는 이번 달 평일 오후 2시부터 5시까지 차 종류를 천 원 할인합니다. 할인 음료를 주문한 손님은 작은 쿠키도 받을 수 있습니다. 단, 배달 주문에는 할인이 적용되지 않으며 매장에서 주문할 때만 혜택을 받을 수 있습니다.",
                    "한결 카페", "오후 2시", "매장에서 주문", "평일 할인 행사를 소개하기 위해",
                    "배달 주문도 천 원 할인을 받을 수 있습니다.",
                    "stimulus-02-cafe.png"),
            new ReadingScenario(
                    "분리배출 공지",
                    "새봄아파트의 분리배출 시간은 매주 수요일 오후 7시부터 9시까지입니다. 종이 상자는 테이프를 떼고 접어야 하며, 플라스틱 용기는 내용물을 비우고 씻어야 합니다. 깨진 유리는 관리실 옆 전용 상자에 따로 넣어 주십시오.",
                    "새봄아파트", "오후 7시", "용기를 씻기", "올바른 분리배출 방법을 설명하기 위해",
                    "플라스틱 용기는 씻지 않고 바로 버려도 됩니다.",
                    "stimulus-05-recycle.png"),
            new ReadingScenario(
                    "가을 축제 일정",
                    "한빛 가을 축제는 토요일 시민 공원에서 열립니다. 오후 2시에는 전통 공연, 4시에는 한국 음식 만들기 체험, 저녁 7시에는 야외 영화가 시작됩니다. 음식 체험은 홈페이지에서 금요일까지 신청해야 하며 공연과 영화는 신청 없이 볼 수 있습니다.",
                    "시민 공원", "오후 2시", "체험 신청", "축제 일정과 참가 방법을 알리기 위해",
                    "모든 프로그램은 미리 신청해야 합니다.",
                    "stimulus-06-festival.png"),
            new ReadingScenario(
                    "학생 지원 시설",
                    "청솔대학교 학생회관 1층에는 학생 상담실이 있고 2층에는 국제 교류실이 있습니다. 3층 학습실에서는 조용히 개인 공부를 할 수 있습니다. 국제 교류실의 언어 교환 프로그램에 참여하려면 수요일까지 온라인 신청서를 내야 합니다.",
                    "학생회관", "수요일", "온라인 신청", "학생 지원 시설을 소개하기 위해",
                    "언어 교환 프로그램은 신청하지 않아도 참여할 수 있습니다.",
                    "stimulus-07-campus.png"),
            new ReadingScenario(
                    "주간 날씨 안내",
                    "이번 주 월요일은 맑고 낮 기온이 18도까지 오르겠습니다. 수요일에는 하루 종일 비가 내리고 기온이 14도로 낮아집니다. 금요일에는 구름이 많지만 20도로 따뜻하겠습니다. 수요일에 외출할 사람은 우산과 얇은 겉옷을 준비하는 것이 좋습니다.",
                    "기상 안내", "수요일", "우산 준비", "이번 주 날씨와 준비물을 알리기 위해",
                    "수요일은 이번 주에서 가장 따뜻한 날입니다.",
                    "stimulus-03-weather.png"),
            new ReadingScenario(
                    "통학 방법 조사",
                    "청솔대학교가 학생 200명의 통학 방법을 조사했습니다. 2024년에는 버스가 45%로 가장 많았지만 2026년에는 35%로 줄었습니다. 같은 기간 자전거는 20%에서 35%로 늘었고 승용차는 10%에서 5%로 줄었습니다. 도보는 두 해 모두 25%였습니다.",
                    "청솔대학교", "2026년", "자전거 이용", "통학 방법의 변화를 설명하기 위해",
                    "도보 통학 비율은 2026년에 크게 줄었습니다.",
                    "stimulus-08-survey.png")
    };

    private static final ListeningScenario[] LISTENING_SCENARIOS = {
            new ListeningScenario(
                    "여: 민수 씨, 내일 도서관에서 같이 공부할까요? 남: 좋아요. 그런데 오전에는 아르바이트가 있어요. 여: 그럼 오후 두 시에 일 층 카페 앞에서 만나요. 남: 네, 제가 먼저 가서 자리를 잡을게요.",
                    "도서관 1층 카페 앞", "오후 두 시", "자리를 잡기", "만날 시간과 장소를 정하기 위해",
                    "남자는 오전에 시간이 많습니다.", "listening-01-library.wav"),
            new ListeningScenario(
                    "안내 말씀드립니다. 오늘 오후 세 시부터 시청역 공사로 이 번 출구를 이용할 수 없습니다. 버스를 타실 분은 사 번 출구 앞 정류장을 이용해 주십시오.",
                    "시청역 4번 출구 앞", "오후 세 시", "다른 출구 이용", "공사에 따른 이동 방법을 안내하기 위해",
                    "오늘은 시청역의 모든 출구를 이용할 수 있습니다.", "listening-02-subway.wav"),
            new ListeningScenario(
                    "여: 어제부터 목이 아프고 열도 조금 나요. 남: 오늘은 따뜻한 물을 많이 드시고 쉬세요. 열이 계속 나면 내일 오전에 다시 병원에 오십시오.",
                    "병원", "내일 오전", "따뜻한 물을 마시고 쉬기", "건강 상태와 대처 방법을 상담하기 위해",
                    "여자는 오늘 아무 증상이 없습니다.", "listening-03-hospital.wav"),
            new ListeningScenario(
                    "남: 주문한 책이 아직 도착하지 않았어요. 여: 확인해 보니 주소에 아파트 동 번호가 빠져 있습니다. 남: 백이 동 오백삼 호입니다. 여: 네, 오늘 저녁에 다시 배송하겠습니다.",
                    "102동 503호", "오늘 저녁", "주소를 수정하기", "배송 문제를 해결하기 위해",
                    "책은 이미 정확한 주소로 배송되었습니다.", "listening-04-delivery.wav"),
            new ListeningScenario(
                    "여: 이번 토요일에 한빛 축제에 갈래요? 남: 좋아요. 저는 전통 공연을 보고 싶어요. 여: 공연은 오후 두 시에 시작해요. 먼저 점심을 먹고 만나요.",
                    "한빛 축제", "오후 두 시", "전통 공연 보기", "축제 계획을 세우기 위해",
                    "두 사람은 축제에 가지 않기로 했습니다.", "listening-05-festival.wav"),
            new ListeningScenario(
                    "팀장: 금요일 회의 자료는 목요일 오후까지 공유해 주세요. 직원: 표와 그래프도 새로 만들까요? 팀장: 네, 지난달 자료와 비교할 수 있게 정리해 주세요.",
                    "회사", "목요일 오후", "자료를 비교해서 정리하기", "회의 자료 준비를 지시하기 위해",
                    "직원은 자료를 금요일 회의 뒤에 공유합니다.", "listening-06-office.wav"),
            new ListeningScenario(
                    "관리실에서 알려 드립니다. 이번 주 분리배출은 수요일 저녁 일곱 시부터 아홉 시까지입니다. 종이 상자는 접고, 플라스틱 용기는 깨끗이 씻어서 버려 주십시오.",
                    "아파트 분리배출장", "수요일 저녁 일곱 시", "용기를 씻기", "분리배출 시간과 방법을 알리기 위해",
                    "분리배출은 목요일 아침에 합니다.", "listening-07-recycle.wav"),
            new ListeningScenario(
                    "내일은 오전에 맑겠지만 오후부터 비가 내리겠습니다. 낮 기온은 십사 도로 오늘보다 낮겠습니다. 외출하실 때 우산과 얇은 겉옷을 준비하십시오.",
                    "기상 안내", "내일 오후", "우산과 겉옷 준비", "내일 날씨와 준비물을 알리기 위해",
                    "내일은 하루 종일 비가 오지 않습니다.", "listening-08-weather.wav")
    };

    private static final String[] SPEAKING_PROMPTS = {
            "자신이 자주 가는 동네 장소를 소개하십시오. 어디인지, 무엇을 하는지, 좋아하는 이유를 말하십시오.",
            "친구가 버스를 잘못 타서 약속 장소에 늦는다고 전화했습니다. 친구에게 길을 알려 주고 새 약속 시간을 제안하십시오.",
            "온라인으로 산 물건에 문제가 있습니다. 고객 센터에 문제를 설명하고 원하는 해결 방법을 말하십시오.",
            "학교의 조용한 학습 공간을 늘리는 방안에 대해 찬성하거나 반대하는 입장을 말하고 두 가지 이유를 제시하십시오.",
            "우리 동네에서 일회용품 사용을 줄이기 위한 캠페인을 제안하십시오. 대상, 활동 방법, 기대 효과를 포함하십시오.",
            "팀 일정이 갑자기 바뀌었습니다. 동료에게 변경 내용을 설명하고 업무를 다시 나누는 방법을 제안하십시오.",
            "외국인 친구에게 추천하고 싶은 한국 문화 행사를 소개하십시오. 행사 내용과 추천 이유를 구체적으로 말하십시오.",
            "앞으로 일 년 동안 이루고 싶은 학습 목표를 말하고, 매달 실천할 계획과 어려움을 해결할 방법을 설명하십시오."
    };

    private static final String[] SPEAKING_AUDIO_FILES = {
            "speaking-01-place.wav",
            "speaking-02-late.wav",
            "speaking-03-service.wav",
            "speaking-04-campus.wav",
            "speaking-05-environment.wav",
            "speaking-06-work.wav",
            "speaking-07-culture.wav",
            "speaking-08-future.wav"
    };

    private static final String[] STIMULUS_IMAGES = {
            "stimulus-01-train.png",
            "stimulus-02-cafe.png",
            "stimulus-03-weather.png",
            "stimulus-04-library.png",
            "stimulus-05-recycle.png",
            "stimulus-06-festival.png",
            "stimulus-07-campus.png",
            "stimulus-08-survey.png"
    };

    private PracticeShowcaseSeeder() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnvironment();
        Path assetRoot = config.assetRoot().toAbsolutePath().normalize();
        Path uploadRoot = config.uploadRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(assetRoot)) {
            throw new IllegalStateException("Missing generated asset directory: " + assetRoot);
        }
        Files.createDirectories(uploadRoot);

        try (Connection connection = DriverManager.getConnection(
                config.jdbcUrl(), config.username(), config.password())) {
            Seeder seeder = new Seeder(connection, assetRoot, uploadRoot);
            seeder.run();
        }
    }

    private record Config(String jdbcUrl, String username, String password,
                          Path assetRoot, Path uploadRoot) {
        static Config fromEnvironment() {
            String jdbcUrl = requiredEnvironment("DB_URL");
            String username = requiredEnvironment("DB_USERNAME");
            String password = requiredEnvironment("DB_PASSWORD");
            Path assetRoot = Path.of(environmentOrDefault(
                    "PRACTICE_SHOWCASE_ASSET_DIR",
                    "scripts/dev/practice-showcase/assets"));
            Path uploadRoot = Path.of(environmentOrDefault("UPLOAD_DIR", "uploads"));
            return new Config(jdbcUrl, username, password, assetRoot, uploadRoot);
        }
    }

    private static final class Seeder {
        private final Connection connection;
        private final Path assetRoot;
        private final Path uploadRoot;
        private final Map<String, Asset> assets = new LinkedHashMap<>();
        private long lecturerId;
        private int insertedSets;
        private int insertedTests;
        private int insertedSections;
        private int insertedGroups;
        private int insertedQuestions;
        private int insertedReferences;

        private Seeder(Connection connection, Path assetRoot, Path uploadRoot) {
            this.connection = connection;
            this.assetRoot = assetRoot;
            this.uploadRoot = uploadRoot;
        }

        void run() throws Exception {
            assertDemoDatabase();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean lockAcquired = false;
            try {
                if (!acquireLock()) {
                    throw new IllegalStateException(
                            "Could not acquire the Practice showcase import lock.");
                }
                lockAcquired = true;
                int existing = existingFixtureSets();
                if (existing == EXPECTED_SET_COUNT) {
                    verifyFixtureInsideTransaction();
                    verifyFixtureFiles();
                    System.out.println("Practice showcase already present; import skipped safely.");
                    connection.rollback();
                    return;
                }
                if (existing != 0) {
                    throw new IllegalStateException(
                            "Partial Practice showcase detected (" + existing
                                    + "/" + EXPECTED_SET_COUNT
                                    + "). Refusing to duplicate or delete it.");
                }

                lecturerId = resolveLecturerId();
                registerAssets();

                for (int themeIndex = 0; themeIndex < THEMES.length; themeIndex++) {
                    seedSet(themeIndex, THEMES[themeIndex]);
                }

                verifyFixtureInsideTransaction();
                connection.commit();
                System.out.printf(
                        Locale.ROOT,
                        "Imported %d sets, %d tests, %d sections, %d groups, "
                                + "%d questions, %d assets and %d material references.%n",
                        insertedSets, insertedTests, insertedSections, insertedGroups,
                        insertedQuestions, assets.size(), insertedReferences);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                if (lockAcquired) {
                    releaseLock();
                }
                connection.setAutoCommit(previousAutoCommit);
            }
        }

        private void assertDemoDatabase() throws SQLException {
            String catalog = connection.getCatalog();
            if (catalog == null || !catalog.startsWith("ksh_practice_demo_")) {
                throw new IllegalStateException(
                        "Refusing to seed a non-demo database: " + catalog
                                + ". Use a fresh schema named ksh_practice_demo_*.");
            }
        }

        private boolean acquireLock() throws SQLException {
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT GET_LOCK(?, 15)")) {
                statement.setString(1, FIXTURE_KEY);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getInt(1) == 1;
                }
            }
        }

        private void releaseLock() {
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, FIXTURE_KEY);
                statement.executeQuery().close();
            } catch (SQLException ignored) {
                // Connection close also releases the named lock.
            }
        }

        private int existingFixtureSets() throws SQLException {
            String sql = """
                    SELECT COUNT(*)
                    FROM practice_sets
                    WHERE is_deleted = 0
                      AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.fixtureKey')) = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, FIXTURE_KEY);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        }

        private long resolveLecturerId() throws SQLException {
            String sql = """
                    SELECT id
                    FROM users
                    WHERE LOWER(email) = LOWER(?)
                      AND role IN ('LECTURER', 'LEADER', 'ADMIN')
                      AND is_active = 1
                      AND is_deleted = 0
                    LIMIT 1
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, LECTURER_EMAIL);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException(
                                "Demo lecturer account is missing: " + LECTURER_EMAIL);
                    }
                    return result.getLong(1);
                }
            }
        }

        private void registerAssets() throws Exception {
            List<Path> sourceFiles;
            try (var paths = Files.walk(assetRoot)) {
                sourceFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString()
                                    .toLowerCase(Locale.ROOT);
                            return name.endsWith(".png") || name.endsWith(".wav");
                        })
                        .sorted()
                        .toList();
            }
            if (sourceFiles.size() != 37) {
                throw new IllegalStateException(
                        "Expected 37 generated media files, found " + sourceFiles.size());
            }

            for (Path source : sourceFiles) {
                String fileName = source.getFileName().toString();
                byte[] bytes = Files.readAllBytes(source);
                String extension = extension(fileName);
                String mimeType;
                String assetType;
                int width = 0;
                int height = 0;
                if (".png".equals(extension)) {
                    validatePng(bytes, fileName);
                    mimeType = "image/png";
                    assetType = "IMAGE";
                    BufferedImage image = ImageIO.read(source.toFile());
                    if (image == null) {
                        throw new IllegalStateException("Unreadable PNG: " + source);
                    }
                    width = image.getWidth();
                    height = image.getHeight();
                } else if (".wav".equals(extension)) {
                    validateWav(bytes, fileName);
                    mimeType = "audio/wav";
                    assetType = "AUDIO";
                } else {
                    throw new IllegalStateException("Unsupported showcase asset: " + source);
                }

                String sha256 = sha256(bytes);
                String storageKey = ("lecturer-assets/" + lecturerId
                        + "/seed/" + FIXTURE_KEY + "/" + sha256 + extension)
                        .replace('\\', '/');
                Path destination = uploadRoot.resolve(storageKey).normalize();
                if (!destination.startsWith(uploadRoot)) {
                    throw new IllegalStateException("Unsafe storage destination: " + destination);
                }
                copyAtomicallyIfNeeded(source, destination, sha256);

                long assetId = insertAsset(
                        source, storageKey, mimeType, assetType,
                        width, height, bytes.length, sha256);
                assets.put(fileName, new Asset(
                        assetId,
                        MATERIAL_PREFIX + assetId + MATERIAL_SUFFIX,
                        storageKey,
                        fileName,
                        mimeType,
                        assetType));
            }
        }

        private long insertAsset(
                Path source,
                String storageKey,
                String mimeType,
                String assetType,
                int width,
                int height,
                long size,
                String sha256
        ) throws SQLException {
            String sql = """
                    INSERT INTO lecturer_assets (
                        owner_lecturer_id, source_import_session_id, source_region_id,
                        source_page_number, crop_x, crop_y, crop_width, crop_height,
                        sha256, source_type, storage_provider, storage_key,
                        original_filename, mime_type, content_verified,
                        width, height, size_bytes, asset_type, title, alt_text,
                        visibility, status, lecturer_note, tags_json,
                        retention_until, created_at, updated_at, deleted_at
                    ) VALUES (
                        ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                        ?, 'MANUAL_UPLOAD', 'LOCAL', ?,
                        ?, ?, 1, ?, ?, ?, ?, ?, ?,
                        'PUBLISHED', 'ACTIVE', ?, ?, NULL, NOW(), NOW(), NULL
                    )
                    """;
            return insertAndReturnId(sql, statement -> {
                String fileName = source.getFileName().toString();
                statement.setLong(1, lecturerId);
                statement.setString(2, sha256);
                statement.setString(3, storageKey);
                statement.setString(4, fileName);
                statement.setString(5, mimeType);
                if (width > 0) statement.setInt(6, width);
                else statement.setNull(6, java.sql.Types.INTEGER);
                if (height > 0) statement.setInt(7, height);
                else statement.setNull(7, java.sql.Types.INTEGER);
                statement.setLong(8, size);
                statement.setString(9, assetType);
                statement.setString(10, displayAssetTitle(fileName));
                statement.setString(11, displayAssetAlt(fileName));
                statement.setString(
                        12,
                        "Tài nguyên gốc do KSH tạo cho bộ dữ liệu demo Practice; "
                                + "không phải câu hỏi hay media TOPIK chính thức.");
                statement.setString(13, json(map(
                        "fixtureKey", FIXTURE_KEY,
                        "fixtureVersion", FIXTURE_VERSION,
                        "provenance", "KSH_ORIGINAL_DEMO",
                        "locale", "ko-KR",
                        "contentLicense", "KSH_INTERNAL_DEMO_ORIGINAL",
                        "sourceFile", fileName)));
            });
        }

        private void seedSet(int themeIndex, Theme theme) throws Exception {
            Asset cover = asset(theme.coverFile());
            Asset setAudio = asset(LISTENING_SCENARIOS[
                    themeIndex % LISTENING_SCENARIOS.length].audioFile());

            Set<Long> usedAssetIds = new LinkedHashSet<>();
            usedAssetIds.add(cover.id());
            usedAssetIds.add(setAudio.id());

            String metadataJson = json(map(
                    "fixtureKey", FIXTURE_KEY,
                    "fixtureVersion", FIXTURE_VERSION,
                    "demoOrder", themeIndex + 1,
                    "topic", theme.topic(),
                    "source", "KSH_ORIGINAL_DEMO",
                    "contentLicense", "KSH_INTERNAL_DEMO_ORIGINAL",
                    "officialTopikMaterial", false,
                    "skills", list("READING", "LISTENING", "WRITING", "SPEAKING"),
                    "testCount", 3,
                    "sectionCount", 12,
                    "questionCount", 72,
                    "teacherGuide",
                    "Mỗi câu có explanation bền vững; group example lưu gợi ý triển khai.",
                    "mediaPolicy", "Governed LOCAL assets via /practice/materials/{id}/content"));

            long setId = insertSet(
                    theme,
                    metadataJson,
                    setAudio.url(),
                    cover.url());
            insertedSets++;

            List<TestRow> tests = new ArrayList<>();
            List<SectionRow> sections = new ArrayList<>();
            List<GroupRow> groups = new ArrayList<>();
            List<QuestionRow> questions = new ArrayList<>();

            int globalSectionOrder = 0;
            int globalGroupOrder = 0;
            int globalQuestionOrder = 0;

            for (int testIndex = 0; testIndex < 3; testIndex++) {
                long testId = insertTest(
                        setId,
                        theme,
                        testIndex,
                        TEST_TITLES[testIndex],
                        TEST_DESCRIPTIONS[testIndex]);
                TestRow test = new TestRow(
                        testId,
                        TEST_TITLES[testIndex],
                        TEST_DESCRIPTIONS[testIndex],
                        testIndex,
                        155);
                tests.add(test);
                insertedTests++;

                ReadingScenario reading = READING_SCENARIOS[
                        (themeIndex + testIndex) % READING_SCENARIOS.length];
                ListeningScenario listening = LISTENING_SCENARIOS[
                        (themeIndex * 2 + testIndex) % LISTENING_SCENARIOS.length];
                Asset readingImage = asset(reading.imageFile());
                Asset listeningImage = asset(STIMULUS_IMAGES[
                        (themeIndex + testIndex + 2) % STIMULUS_IMAGES.length]);
                Asset listeningAudio = asset(listening.audioFile());
                Asset writingImage = asset("stimulus-08-survey.png");
                Asset speakingImage = asset(STIMULUS_IMAGES[
                        (themeIndex + testIndex + 5) % STIMULUS_IMAGES.length]);

                usedAssetIds.add(readingImage.id());
                usedAssetIds.add(listeningImage.id());
                usedAssetIds.add(listeningAudio.id());
                usedAssetIds.add(writingImage.id());
                usedAssetIds.add(speakingImage.id());

                SectionRow readingSection = insertSection(
                        setId, testId, "Phần Đọc · 읽기", "READING",
                        "Đọc kỹ văn bản và chọn đáp án dựa trên bằng chứng hiển thị.",
                        null, 40, new BigDecimal("40.00"), globalSectionOrder++);
                sections.add(readingSection);
                insertedSections++;
                GroupRow readingGroup = insertGroup(
                        setId, readingSection.id(), "R" + (testIndex + 1) + ".1",
                        1, 8,
                        "다음 글을 읽고 물음에 답하십시오.",
                        "READING_PASSAGE",
                        reading.passage(),
                        null,
                        readingImage.url(),
                        provenance("MANUAL", readingImage.fileName()),
                        null,
                        groupExample(theme, testIndex, "READING"),
                        globalGroupOrder++);
                groups.add(readingGroup);
                insertedGroups++;
                List<QuestionDraft> readingQuestions = readingQuestions(
                        reading, readingImage.url(), theme, testIndex);
                for (QuestionDraft draft : readingQuestions) {
                    QuestionRow question = insertQuestion(
                            setId, readingGroup.id(), draft, globalQuestionOrder++);
                    questions.add(question);
                    insertedQuestions++;
                }

                String deliveryJson = json(map(
                        "schemaVersion", "practice-section-delivery-v1",
                        "listeningDelivery", map(
                                "checkAudioReference", BUILT_IN_LISTENING_CHECK)));
                SectionRow listeningSection = insertSection(
                        setId, testId, "Phần Nghe · 듣기", "LISTENING",
                        "Kiểm tra loa trước khi bắt đầu; mỗi đoạn nghe có transcript phục vụ giải thích.",
                        deliveryJson, 35, new BigDecimal("40.00"), globalSectionOrder++);
                sections.add(listeningSection);
                insertedSections++;
                GroupRow listeningGroup = insertGroup(
                        setId, listeningSection.id(), "L" + (testIndex + 1) + ".1",
                        1, 8,
                        "대화를 잘 듣고 물음에 답하십시오.",
                        "LISTENING_AUDIO",
                        null,
                        listening.transcript(),
                        listeningImage.url(),
                        provenance("MANUAL", listeningAudio.fileName()),
                        listeningAudio.url(),
                        groupExample(theme, testIndex, "LISTENING"),
                        globalGroupOrder++);
                groups.add(listeningGroup);
                insertedGroups++;
                List<QuestionDraft> listeningQuestions = listeningQuestions(
                        listening,
                        listeningImage.url(),
                        listeningAudio.url(),
                        theme,
                        testIndex);
                for (QuestionDraft draft : listeningQuestions) {
                    QuestionRow question = insertQuestion(
                            setId, listeningGroup.id(), draft, globalQuestionOrder++);
                    questions.add(question);
                    insertedQuestions++;
                }

                SectionRow writingSection = insertSection(
                        setId, testId, "Phần Viết · 쓰기", "WRITING",
                        "Hoàn thành đủ Q51–Q54; chú ý dung lượng và mọi ý bắt buộc.",
                        null, 50, new BigDecimal("100.00"), globalSectionOrder++);
                sections.add(writingSection);
                insertedSections++;
                GroupRow writingGroup = insertGroup(
                        setId, writingSection.id(), "W" + (testIndex + 1) + ".1",
                        51, 54,
                        "문항의 조건을 모두 반영하여 한국어로 쓰십시오.",
                        "NONE",
                        null,
                        null,
                        writingImage.url(),
                        provenance("MANUAL", writingImage.fileName()),
                        null,
                        groupExample(theme, testIndex, "WRITING"),
                        globalGroupOrder++);
                groups.add(writingGroup);
                insertedGroups++;
                for (QuestionDraft draft : writingQuestions(
                        theme, testIndex, writingImage.url())) {
                    QuestionRow question = insertQuestion(
                            setId, writingGroup.id(), draft, globalQuestionOrder++);
                    questions.add(question);
                    insertedQuestions++;
                }

                SectionRow speakingSection = insertSection(
                        setId, testId, "Phần Nói · 말하기", "SPEAKING",
                        "Nghe đề, chuẩn bị trong thời gian quy định và ghi âm câu trả lời bằng tiếng Hàn.",
                        null, 30, new BigDecimal("100.00"), globalSectionOrder++);
                sections.add(speakingSection);
                insertedSections++;

                int firstSpeakingIndex = (themeIndex + testIndex) % SPEAKING_PROMPTS.length;
                Asset firstSpeakingAudio = asset(SPEAKING_AUDIO_FILES[firstSpeakingIndex]);
                usedAssetIds.add(firstSpeakingAudio.id());
                GroupRow speakingGroup = insertGroup(
                        setId, speakingSection.id(), "S" + (testIndex + 1) + ".1",
                        1, 4,
                        "음을 듣고 준비한 뒤 한국어로 말하십시오.",
                        "NONE",
                        null,
                        null,
                        speakingImage.url(),
                        provenance("MANUAL", firstSpeakingAudio.fileName()),
                        firstSpeakingAudio.url(),
                        groupExample(theme, testIndex, "SPEAKING"),
                        globalGroupOrder++);
                groups.add(speakingGroup);
                insertedGroups++;

                List<QuestionDraft> speakingDrafts = new ArrayList<>();
                for (int questionIndex = 0; questionIndex < 4; questionIndex++) {
                    int promptIndex = (firstSpeakingIndex + questionIndex)
                            % SPEAKING_PROMPTS.length;
                    Asset promptAudio = asset(SPEAKING_AUDIO_FILES[promptIndex]);
                    usedAssetIds.add(promptAudio.id());
                    speakingDrafts.add(speakingQuestion(
                            questionIndex + 1,
                            SPEAKING_PROMPTS[promptIndex],
                            promptAudio.url(),
                            questionIndex % 2 == 1 ? speakingImage.url() : null,
                            theme,
                            testIndex,
                            questionIndex));
                }
                for (QuestionDraft draft : speakingDrafts) {
                    QuestionRow question = insertQuestion(
                            setId, speakingGroup.id(), draft, globalQuestionOrder++);
                    questions.add(question);
                    insertedQuestions++;
                }
            }

            long publishedVersionId = insertPublishedVersion(setId, theme);
            long setVersionId = insertSetVersion(
                    publishedVersionId,
                    setId,
                    theme,
                    metadataJson,
                    cover.url());

            Map<Long, Long> testVersionIds = new LinkedHashMap<>();
            for (TestRow test : tests) {
                long testVersionId = insertTestVersion(
                        publishedVersionId, setVersionId, test);
                testVersionIds.put(test.id(), testVersionId);
            }

            Map<Long, Long> sectionVersionIds = new LinkedHashMap<>();
            for (SectionRow section : sections) {
                long sectionVersionId = insertSectionVersion(
                        publishedVersionId,
                        requireMapped(testVersionIds, section.testId(), "test version"),
                        section);
                sectionVersionIds.put(section.id(), sectionVersionId);
            }

            Map<Long, Long> groupVersionIds = new LinkedHashMap<>();
            for (GroupRow group : groups) {
                long groupVersionId = insertGroupVersion(
                        publishedVersionId,
                        requireMapped(sectionVersionIds, group.sectionId(), "section version"),
                        group);
                groupVersionIds.put(group.id(), groupVersionId);
            }

            for (QuestionRow question : questions) {
                GroupRow group = groups.stream()
                        .filter(candidate -> candidate.id() == question.groupId())
                        .findFirst()
                        .orElseThrow();
                insertQuestionVersion(
                        publishedVersionId,
                        requireMapped(sectionVersionIds, group.sectionId(), "section version"),
                        requireMapped(groupVersionIds, question.groupId(), "group version"),
                        question);
            }

            for (Long assetId : usedAssetIds) {
                Asset asset = assets.values().stream()
                        .filter(candidate -> candidate.id() == assetId)
                        .findFirst()
                        .orElseThrow();
                String placement = placement(asset);
                insertMaterialReference(
                        asset, setId, publishedVersionId, placement);
                insertedReferences++;
            }

            insertEditLog(
                    setId,
                    metadataJson,
                    tests.size(),
                    sections.size(),
                    groups.size(),
                    questions.size(),
                    usedAssetIds.size());
        }

        private long insertSet(
                Theme theme,
                String metadataJson,
                String audioPath,
                String coverImageUrl
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_sets (
                        title, description, skill, scope, class_id,
                        source_pdf_path, audio_path, metadata_json, status,
                        owner_locked, locked_by, locked_at, archived_at,
                        created_by, is_deleted, creation_method, cover_image_url
                    ) VALUES (
                        ?, ?, 'MIXED', 'GLOBAL', NULL,
                        NULL, ?, ?, 'PUBLISHED',
                        0, NULL, NULL, NULL,
                        ?, 0, 'MANUAL', ?
                    )
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setString(1, "[VIP DEMO] " + theme.title());
                statement.setString(
                        2,
                        theme.description()
                                + " Mỗi bộ có 3 test, mỗi test đủ 4 kỹ năng "
                                + "và lời giải/gợi ý của giảng viên.");
                statement.setString(3, audioPath);
                statement.setString(4, metadataJson);
                statement.setLong(5, lecturerId);
                statement.setString(6, coverImageUrl);
            });
        }

        private long insertTest(
                long setId,
                Theme theme,
                int testIndex,
                String title,
                String description
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_tests (
                        set_id, title, description, display_order, estimated_minutes
                    ) VALUES (?, ?, ?, ?, ?)
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setLong(1, setId);
                statement.setString(2, theme.title() + " · " + title);
                statement.setString(
                        3,
                        description + " Chủ đề trọng tâm: " + theme.topic() + ".");
                statement.setInt(4, testIndex);
                statement.setInt(5, 155);
            });
        }

        private SectionRow insertSection(
                long setId,
                long testId,
                String title,
                String skill,
                String instructions,
                String deliveryJson,
                int durationMinutes,
                BigDecimal totalPoints,
                int displayOrder
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_sections (
                        set_id, test_id, title, skill, section_type,
                        instructions, delivery_json, duration_minutes,
                        total_points, display_order
                    ) VALUES (?, ?, ?, ?, 'MAIN', ?, ?, ?, ?, ?)
                    """;
            long id = insertAndReturnId(sql, statement -> {
                statement.setLong(1, setId);
                statement.setLong(2, testId);
                statement.setString(3, title);
                statement.setString(4, skill);
                statement.setString(5, instructions);
                statement.setString(6, deliveryJson);
                statement.setInt(7, durationMinutes);
                statement.setBigDecimal(8, totalPoints);
                statement.setInt(9, displayOrder);
            });
            return new SectionRow(
                    id, testId, title, skill, "MAIN", instructions,
                    deliveryJson, durationMinutes, totalPoints, displayOrder);
        }

        private GroupRow insertGroup(
                long setId,
                long sectionId,
                String label,
                int questionFrom,
                int questionTo,
                String instruction,
                String stimulusType,
                String passageText,
                String transcriptText,
                String imageUrl,
                String stimulusProvenanceJson,
                String audioUrl,
                String exampleJson,
                int displayOrder
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_question_groups (
                        set_id, section_id, group_label, question_from, question_to,
                        instruction, stimulus_type, passage_text, transcript_text,
                        image_url, stimulus_provenance_json, audio_url,
                        example_json, display_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            long id = insertAndReturnId(sql, statement -> {
                statement.setLong(1, setId);
                statement.setLong(2, sectionId);
                statement.setString(3, label);
                statement.setInt(4, questionFrom);
                statement.setInt(5, questionTo);
                statement.setString(6, instruction);
                statement.setString(7, stimulusType);
                statement.setString(8, passageText);
                statement.setString(9, transcriptText);
                statement.setString(10, imageUrl);
                statement.setString(11, stimulusProvenanceJson);
                statement.setString(12, audioUrl);
                statement.setString(13, exampleJson);
                statement.setInt(14, displayOrder);
            });
            return new GroupRow(
                    id, sectionId, label, questionFrom, questionTo,
                    instruction, stimulusType, passageText, transcriptText,
                    imageUrl, stimulusProvenanceJson, audioUrl, exampleJson,
                    displayOrder);
        }

        private QuestionRow insertQuestion(
                long setId,
                long groupId,
                QuestionDraft draft,
                int displayOrder
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_questions (
                        set_id, group_id, question_no, question_type, prompt,
                        options_json, question_content_json, answer_key,
                        answer_spec_json, explanation, points, display_order,
                        writing_task_type
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            long id = insertAndReturnId(sql, statement -> {
                statement.setLong(1, setId);
                statement.setLong(2, groupId);
                statement.setInt(3, draft.questionNo());
                statement.setString(4, draft.questionType());
                statement.setString(5, draft.prompt());
                statement.setString(6, draft.optionsJson());
                statement.setString(7, draft.questionContentJson());
                statement.setString(8, draft.answerKey());
                statement.setString(9, draft.answerSpecJson());
                statement.setString(10, draft.explanation());
                statement.setBigDecimal(11, draft.points());
                statement.setInt(12, displayOrder);
                statement.setString(13, draft.writingTaskType());
            });
            return new QuestionRow(
                    id, groupId, draft.questionNo(), draft.questionType(),
                    draft.prompt(), draft.optionsJson(),
                    draft.questionContentJson(), draft.answerKey(),
                    draft.answerSpecJson(), draft.explanation(), draft.points(),
                    displayOrder, draft.writingTaskType());
        }

        private long insertPublishedVersion(long setId, Theme theme)
                throws SQLException {
            String sql = """
                    INSERT INTO practice_published_versions (
                        set_id, version_number, status, content_hash,
                        published_by, published_at
                    ) VALUES (?, 1, 'PUBLISHED', ?, ?, NOW())
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setLong(1, setId);
                statement.setString(
                        2,
                        sha256((FIXTURE_KEY + ":" + theme.slug())
                                .getBytes(StandardCharsets.UTF_8)));
                statement.setLong(3, lecturerId);
            });
        }

        private long insertSetVersion(
                long publishedVersionId,
                long setId,
                Theme theme,
                String metadataJson,
                String coverImageUrl
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_set_versions (
                        published_version_id, set_id, title, description,
                        skill, scope, class_id, metadata_json,
                        creation_method, cover_image_url
                    ) VALUES (?, ?, ?, ?, 'MIXED', 'GLOBAL', NULL, ?, 'MANUAL', ?)
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setLong(1, publishedVersionId);
                statement.setLong(2, setId);
                statement.setString(3, "[VIP DEMO] " + theme.title());
                statement.setString(
                        4,
                        theme.description()
                                + " Mỗi bộ có 3 test, mỗi test đủ 4 kỹ năng "
                                + "và lời giải/gợi ý của giảng viên.");
                statement.setString(5, metadataJson);
                statement.setString(6, coverImageUrl);
            });
        }

        private long insertTestVersion(
                long publishedVersionId,
                long setVersionId,
                TestRow test
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_test_versions (
                        published_version_id, set_version_id, test_id,
                        title, description, display_order, estimated_minutes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setLong(1, publishedVersionId);
                statement.setLong(2, setVersionId);
                statement.setLong(3, test.id());
                statement.setString(4, test.title());
                statement.setString(5, test.description());
                statement.setInt(6, test.displayOrder());
                statement.setInt(7, test.estimatedMinutes());
            });
        }

        private long insertSectionVersion(
                long publishedVersionId,
                long testVersionId,
                SectionRow section
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_section_versions (
                        published_version_id, test_version_id, section_id,
                        title, skill, section_type, instructions, delivery_json,
                        duration_minutes, total_points, display_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setLong(1, publishedVersionId);
                statement.setLong(2, testVersionId);
                statement.setLong(3, section.id());
                statement.setString(4, section.title());
                statement.setString(5, section.skill());
                statement.setString(6, section.sectionType());
                statement.setString(7, section.instructions());
                statement.setString(8, section.deliveryJson());
                statement.setInt(9, section.durationMinutes());
                statement.setBigDecimal(10, section.totalPoints());
                statement.setInt(11, section.displayOrder());
            });
        }

        private long insertGroupVersion(
                long publishedVersionId,
                long sectionVersionId,
                GroupRow group
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_question_group_versions (
                        published_version_id, section_version_id, group_id,
                        group_label, question_from, question_to, instruction,
                        stimulus_type, passage_text, transcript_text, image_url,
                        stimulus_provenance_json, audio_url, example_json,
                        display_order
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            return insertAndReturnId(sql, statement -> {
                statement.setLong(1, publishedVersionId);
                statement.setLong(2, sectionVersionId);
                statement.setLong(3, group.id());
                statement.setString(4, group.label());
                statement.setInt(5, group.questionFrom());
                statement.setInt(6, group.questionTo());
                statement.setString(7, group.instruction());
                statement.setString(8, group.stimulusType());
                statement.setString(9, group.passageText());
                statement.setString(10, group.transcriptText());
                statement.setString(11, group.imageUrl());
                statement.setString(12, group.stimulusProvenanceJson());
                statement.setString(13, group.audioUrl());
                statement.setString(14, group.exampleJson());
                statement.setInt(15, group.displayOrder());
            });
        }

        private void insertQuestionVersion(
                long publishedVersionId,
                long sectionVersionId,
                long groupVersionId,
                QuestionRow question
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_question_versions (
                        published_version_id, section_version_id,
                        group_version_id, question_id, question_no,
                        question_type, prompt, options_json,
                        question_content_json, answer_key, answer_spec_json,
                        explanation, points, display_order, writing_task_type
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, publishedVersionId);
                statement.setLong(2, sectionVersionId);
                statement.setLong(3, groupVersionId);
                statement.setLong(4, question.id());
                statement.setInt(5, question.questionNo());
                statement.setString(6, question.questionType());
                statement.setString(7, question.prompt());
                statement.setString(8, question.optionsJson());
                statement.setString(9, question.questionContentJson());
                statement.setString(10, question.answerKey());
                statement.setString(11, question.answerSpecJson());
                statement.setString(12, question.explanation());
                statement.setBigDecimal(13, question.points());
                statement.setInt(14, question.displayOrder());
                statement.setString(15, question.writingTaskType());
                statement.executeUpdate();
            }
        }

        private void insertMaterialReference(
                Asset asset,
                long setId,
                long publishedVersionId,
                String placement
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_material_references (
                        asset_id, draft_id, set_id, published_version_id,
                        reference_scope, placement, reference_key,
                        reference_metadata_json
                    ) VALUES (?, NULL, ?, ?, 'PUBLISHED_VERSION', ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, asset.id());
                statement.setLong(2, setId);
                statement.setLong(3, publishedVersionId);
                statement.setString(4, placement);
                statement.setString(5, asset.fileName());
                statement.setString(6, json(map(
                        "fixtureKey", FIXTURE_KEY,
                        "fixtureVersion", FIXTURE_VERSION,
                        "assetFile", asset.fileName(),
                        "mimeType", asset.mimeType(),
                        "assetType", asset.assetType(),
                        "contentLicense", "KSH_INTERNAL_DEMO_ORIGINAL")));
                statement.executeUpdate();
            }
        }

        private void insertEditLog(
                long setId,
                String metadataJson,
                int tests,
                int sections,
                int groups,
                int questions,
                int media
        ) throws SQLException {
            String sql = """
                    INSERT INTO practice_edit_logs (
                        set_id, edited_by, change_summary, change_details_json,
                        before_snapshot_json, after_snapshot_json,
                        edit_type, edited_at
                    ) VALUES (?, ?, ?, ?, NULL, ?, 'SEED_IMPORT', NOW())
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, setId);
                statement.setLong(2, lecturerId);
                statement.setString(
                        3,
                        "Tạo bộ đề demo KSH Practice Showcase v" + FIXTURE_VERSION);
                statement.setString(4, json(map(
                        "fixtureKey", FIXTURE_KEY,
                        "tests", tests,
                        "sections", sections,
                        "groups", groups,
                        "questions", questions,
                        "media", media)));
                statement.setString(5, metadataJson);
                statement.executeUpdate();
            }
        }

        private void verifyFixtureInsideTransaction() throws SQLException {
            Map<String, Integer> expected = mapOfCounts(
                    "sets", 13,
                    "tests", 39,
                    "sections", 156,
                    "groups", 156,
                    "questions", 936,
                    "publishedVersions", 13,
                    "setVersions", 13,
                    "testVersions", 39,
                    "sectionVersions", 156,
                    "groupVersions", 156,
                    "questionVersions", 936,
                    "assets", 37,
                    "materialReferences", 231);

            Map<String, String> queries = mapOfQueries();
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                int actual = scalarInt(queries.get(entry.getKey()));
                if (actual != entry.getValue()) {
                    throw new IllegalStateException(
                            "Fixture verification failed for " + entry.getKey()
                                    + ": expected " + entry.getValue()
                                    + ", got " + actual);
                }
            }

            int badTestSkills = scalarInt("""
                    SELECT COUNT(*)
                    FROM (
                        SELECT pt.id
                        FROM practice_tests pt
                        JOIN practice_sets ps ON ps.id = pt.set_id
                        JOIN practice_sections sec ON sec.test_id = pt.id
                        WHERE JSON_UNQUOTE(JSON_EXTRACT(
                            ps.metadata_json, '$.fixtureKey')) = 'ksh-practice-showcase-v1'
                        GROUP BY pt.id
                        HAVING COUNT(*) <> 4 OR COUNT(DISTINCT sec.skill) <> 4
                    ) invalid_tests
                    """);
            if (badTestSkills != 0) {
                throw new IllegalStateException(
                        "Every showcase test must contain exactly four skills.");
            }

            int badQuestionContracts = scalarInt("""
                    SELECT COUNT(*)
                    FROM practice_questions q
                    JOIN practice_sets ps ON ps.id = q.set_id
                    WHERE JSON_UNQUOTE(JSON_EXTRACT(
                        ps.metadata_json, '$.fixtureKey')) = 'ksh-practice-showcase-v1'
                      AND (
                          q.group_id IS NULL
                          OR q.explanation IS NULL
                          OR TRIM(q.explanation) = ''
                          OR q.question_content_json IS NULL
                          OR JSON_VALID(q.question_content_json) = 0
                          OR q.answer_spec_json IS NULL
                          OR JSON_VALID(q.answer_spec_json) = 0
                      )
                    """);
            if (badQuestionContracts != 0) {
                throw new IllegalStateException(
                        "Showcase contains incomplete question contracts.");
            }

            int badMediaReferences = scalarInt("""
                    SELECT COUNT(*)
                    FROM lecturer_assets a
                    WHERE JSON_UNQUOTE(JSON_EXTRACT(
                        CAST(a.tags_json AS JSON), '$.fixtureKey'))
                            = 'ksh-practice-showcase-v1'
                      AND (
                          a.content_verified <> 1
                          OR a.status <> 'ACTIVE'
                          OR a.visibility <> 'PUBLISHED'
                          OR a.sha256 IS NULL
                          OR CHAR_LENGTH(a.sha256) <> 64
                          OR a.size_bytes <= 0
                      )
                    """);
            if (badMediaReferences != 0) {
                throw new IllegalStateException(
                        "Showcase contains invalid governed media rows.");
            }
        }

        private void verifyFixtureFiles() throws Exception {
            String sql = """
                    SELECT storage_key, sha256
                    FROM lecturer_assets
                    WHERE JSON_UNQUOTE(JSON_EXTRACT(
                        CAST(tags_json AS JSON), '$.fixtureKey')) = ?
                    ORDER BY id
                    """;
            int verified = 0;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, FIXTURE_KEY);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Path file = uploadRoot.resolve(result.getString("storage_key"))
                                .normalize();
                        if (!file.startsWith(uploadRoot)
                                || !Files.isRegularFile(file)
                                || !sha256(Files.readAllBytes(file)).equalsIgnoreCase(
                                result.getString("sha256"))) {
                            throw new IllegalStateException(
                                    "Missing or corrupt showcase asset: " + file);
                        }
                        verified++;
                    }
                }
            }
            if (verified != 37) {
                throw new IllegalStateException(
                        "Expected 37 physical showcase assets, verified " + verified);
            }
        }

        private int scalarInt(String sql) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(sql)) {
                result.next();
                return result.getInt(1);
            }
        }

        private Map<String, Integer> mapOfCounts(Object... entries) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (int index = 0; index < entries.length; index += 2) {
                result.put((String) entries[index], (Integer) entries[index + 1]);
            }
            return result;
        }

        private Map<String, String> mapOfQueries() {
            String marker = """
                    JSON_UNQUOTE(JSON_EXTRACT(
                        ps.metadata_json, '$.fixtureKey')) = 'ksh-practice-showcase-v1'
                    """;
            Map<String, String> result = new LinkedHashMap<>();
            result.put("sets",
                    "SELECT COUNT(*) FROM practice_sets ps WHERE " + marker);
            result.put("tests", """
                    SELECT COUNT(*)
                    FROM practice_tests row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("sections", """
                    SELECT COUNT(*)
                    FROM practice_sections row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("groups", """
                    SELECT COUNT(*)
                    FROM practice_question_groups row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("questions", """
                    SELECT COUNT(*)
                    FROM practice_questions row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("publishedVersions", """
                    SELECT COUNT(*)
                    FROM practice_published_versions row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("setVersions", """
                    SELECT COUNT(*)
                    FROM practice_set_versions row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("testVersions", """
                    SELECT COUNT(*)
                    FROM practice_test_versions row_value
                    JOIN practice_published_versions ppv
                      ON ppv.id = row_value.published_version_id
                    JOIN practice_sets ps ON ps.id = ppv.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("sectionVersions", """
                    SELECT COUNT(*)
                    FROM practice_section_versions row_value
                    JOIN practice_published_versions ppv
                      ON ppv.id = row_value.published_version_id
                    JOIN practice_sets ps ON ps.id = ppv.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("groupVersions", """
                    SELECT COUNT(*)
                    FROM practice_question_group_versions row_value
                    JOIN practice_published_versions ppv
                      ON ppv.id = row_value.published_version_id
                    JOIN practice_sets ps ON ps.id = ppv.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("questionVersions", """
                    SELECT COUNT(*)
                    FROM practice_question_versions row_value
                    JOIN practice_published_versions ppv
                      ON ppv.id = row_value.published_version_id
                    JOIN practice_sets ps ON ps.id = ppv.set_id
                    WHERE %s
                    """.formatted(marker));
            result.put("assets", """
                    SELECT COUNT(*)
                    FROM lecturer_assets row_value
                    WHERE JSON_UNQUOTE(JSON_EXTRACT(
                        CAST(row_value.tags_json AS JSON), '$.fixtureKey'))
                            = 'ksh-practice-showcase-v1'
                    """);
            result.put("materialReferences", """
                    SELECT COUNT(*)
                    FROM practice_material_references row_value
                    JOIN practice_sets ps ON ps.id = row_value.set_id
                    WHERE %s
                    """.formatted(marker));
            return result;
        }

        private long insertAndReturnId(String sql, SqlBinder binder)
                throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                binder.bind(statement);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Expected exactly one inserted row.");
                }
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Insert did not return a generated key.");
                    }
                    return keys.getLong(1);
                }
            }
        }

        private Asset asset(String fileName) {
            Asset asset = assets.get(fileName);
            if (asset == null) {
                throw new IllegalStateException("Missing registered asset: " + fileName);
            }
            return asset;
        }
    }

    private static List<QuestionDraft> readingQuestions(
            ReadingScenario scenario,
            String imageUrl,
            Theme theme,
            int testIndex
    ) {
        List<QuestionDraft> questions = new ArrayList<>();
        questions.add(singleChoice(
                1,
                "다음 글의 내용과 같은 것을 고르십시오.",
                list(
                        option("opt_A", scenario.purpose()),
                        option("opt_B", "개인 경험을 재미있게 소개하기 위해"),
                        option("opt_C", "상품 가격을 비교하기 위해"),
                        option("opt_D", "친구에게 사과하기 위해")),
                "opt_A",
                imageUrl,
                null,
                "Gợi ý của giảng viên: xác định mục đích chung, không chọn một chi tiết rời rạc.",
                new BigDecimal("5.00")));
        questions.add(singleChoice(
                2,
                "글에서 중심이 되는 장소나 기관은 어디입니까?",
                list(
                        option("opt_A", "시청"),
                        option("opt_B", scenario.place()),
                        option("opt_C", "공항"),
                        option("opt_D", "우체국")),
                "opt_B",
                null,
                null,
                "Gợi ý của giảng viên: địa điểm được lặp lại hoặc gắn với hành động chính là đáp án.",
                new BigDecimal("5.00")));
        questions.add(tfng(
                3,
                scenario.falseStatement(),
                "FALSE",
                "Gợi ý của giảng viên: câu khẳng định trái trực tiếp với thông tin trong bài.",
                new BigDecimal("5.00")));
        questions.add(fillBlank(
                4,
                "핵심 장소는 {{blank_1}}입니다.",
                "blank_1",
                list(scenario.place()),
                null,
                "Gợi ý của giảng viên: chép chính xác cụm chỉ địa điểm xuất hiện trong bài.",
                new BigDecimal("5.00")));
        questions.add(singleChoice(
                5,
                "글에서 언급한 시간은 언제입니까?",
                list(
                        option("opt_A", "자정"),
                        option("opt_B", "다음 달"),
                        option("opt_C", scenario.time()),
                        option("opt_D", "작년 겨울")),
                "opt_C",
                imageUrl,
                null,
                "Gợi ý của giảng viên: đối chiếu số giờ/ngày với chính hình hoặc câu văn liên quan.",
                new BigDecimal("5.00")));
        questions.add(tfng(
                6,
                "이 글에는 모든 서비스의 입장료가 무료라고 나옵니다.",
                "NOT_GIVEN",
                "Gợi ý của giảng viên: bài không cung cấp thông tin đầy đủ về toàn bộ mức phí.",
                new BigDecimal("5.00")));
        questions.add(fillBlank(
                7,
                "글에서 강조한 행동은 {{blank_2}}입니다.",
                "blank_2",
                list(scenario.action()),
                null,
                "Gợi ý của giảng viên: dùng danh từ hóa hành động chính như trong phần hướng dẫn.",
                new BigDecimal("5.00")));
        questions.add(singleChoice(
                8,
                "이 글을 읽은 사람이 가장 먼저 확인해야 할 것은 무엇입니까?",
                list(
                        option("opt_A", scenario.time()),
                        option("opt_B", "친구의 생일"),
                        option("opt_C", "새 영화의 배우"),
                        option("opt_D", "외국의 환율")),
                "opt_A",
                null,
                null,
                "Gợi ý của giảng viên: chọn thông tin giúp người đọc thực hiện đúng hành động.",
                new BigDecimal("5.00")));
        return questions;
    }

    private static List<QuestionDraft> listeningQuestions(
            ListeningScenario scenario,
            String imageUrl,
            String audioUrl,
            Theme theme,
            int testIndex
    ) {
        List<QuestionDraft> questions = new ArrayList<>();
        questions.add(singleChoice(
                1,
                "들은 내용의 중심 목적을 고르십시오.",
                list(
                        option("opt_A", scenario.purpose()),
                        option("opt_B", "과거의 여행을 자랑하기 위해"),
                        option("opt_C", "새 제품을 광고하기 위해"),
                        option("opt_D", "운동 경기 결과를 전하기 위해")),
                "opt_A",
                imageUrl,
                audioUrl,
                "Gợi ý của giảng viên: nghe câu mở đầu và câu chốt để xác định mục đích.",
                new BigDecimal("5.00")));
        questions.add(singleChoice(
                2,
                "대화나 안내에서 중요한 장소는 어디입니까?",
                list(
                        option("opt_A", "공항 면세점"),
                        option("opt_B", scenario.place()),
                        option("opt_C", "해외 호텔"),
                        option("opt_D", "운동장")),
                "opt_B",
                null,
                audioUrl,
                "Gợi ý của giảng viên: chú ý danh từ địa điểm đi cùng số tầng/lối ra/số phòng.",
                new BigDecimal("5.00")));
        questions.add(tfng(
                3,
                scenario.falseStatement(),
                "FALSE",
                "Gợi ý của giảng viên: thông tin nghe được phủ định nhận định này.",
                new BigDecimal("5.00"),
                audioUrl));
        questions.add(fillBlank(
                4,
                "중요한 시간은 {{blank_1}}입니다.",
                "blank_1",
                list(scenario.time()),
                audioUrl,
                "Gợi ý của giảng viên: nghe kỹ đơn vị giờ/ngày và giữ nguyên cách nói.",
                new BigDecimal("5.00")));
        questions.add(singleChoice(
                5,
                "화자나 청자가 해야 할 일은 무엇입니까?",
                list(
                        option("opt_A", "아무것도 하지 않기"),
                        option("opt_B", "새 차를 사기"),
                        option("opt_C", scenario.action()),
                        option("opt_D", "외국으로 이사하기")),
                "opt_C",
                imageUrl,
                audioUrl,
                "Gợi ý của giảng viên: tìm động từ mệnh lệnh, đề nghị hoặc kế hoạch.",
                new BigDecimal("5.00")));
        questions.add(tfng(
                6,
                "대화에는 화자의 정확한 나이가 나옵니다.",
                "NOT_GIVEN",
                "Gợi ý của giảng viên: đoạn nghe không cung cấp tuổi cụ thể.",
                new BigDecimal("5.00"),
                audioUrl));
        questions.add(fillBlank(
                7,
                "가장 중요한 행동은 {{blank_2}}입니다.",
                "blank_2",
                list(scenario.action()),
                audioUrl,
                "Gợi ý của giảng viên: viết lại đúng cụm hành động đã nghe.",
                new BigDecimal("5.00")));
        questions.add(singleChoice(
                8,
                "들은 뒤에 가장 적절한 반응을 고르십시오.",
                list(
                        option("opt_A", "안내된 시간과 행동을 확인합니다."),
                        option("opt_B", "내용과 관계없는 사진을 보냅니다."),
                        option("opt_C", "약속을 모두 취소합니다."),
                        option("opt_D", "주소를 일부러 지웁니다.")),
                "opt_A",
                null,
                audioUrl,
                "Gợi ý của giảng viên: phản ứng đúng phải trực tiếp giải quyết tình huống vừa nghe.",
                new BigDecimal("5.00")));
        return questions;
    }

    private static List<QuestionDraft> writingQuestions(
            Theme theme,
            int testIndex,
            String surveyImageUrl
    ) {
        String topic = theme.topic();
        int variant = testIndex + 1;
        return list(
                essay(
                        51,
                        "오늘 오후에 만나기로 한 수진 씨에게 메시지를 쓰십시오. "
                                + "갑자기 일정이 생긴 이유, 사과, 새로 만나고 싶은 시간을 "
                                + "모두 포함하십시오. (80~120자)",
                        "Q51",
                        new BigDecimal("10.00"),
                        null,
                        "Gợi ý của giảng viên: viết đúng dạng tin nhắn, đủ lý do–xin lỗi–thời gian mới; không mở rộng lan man."),
                essay(
                        52,
                        topic + " 주제의 첫 모임 안내문을 쓰십시오. 날짜와 장소, "
                                + "참가자가 준비할 것 한 가지, 모임에서 할 활동 두 가지를 "
                                + "포함하십시오. (150~200자)",
                        "Q52",
                        new BigDecimal("10.00"),
                        null,
                        "Gợi ý của giảng viên: dùng văn phong thông báo và tách rõ thời gian, địa điểm, chuẩn bị, hoạt động."),
                essay(
                        53,
                        "청솔대학교 학생 200명의 통학 방법 자료를 설명하십시오. "
                                + "2024년: 버스 45%, 자전거 20%, 도보 25%, 승용차 10%. "
                                + "2026년: 버스 35%, 자전거 35%, 도보 25%, 승용차 5%. "
                                + "주요 변화와 가능한 이유를 쓰십시오. (200~300자)",
                        "Q53",
                        new BigDecimal("30.00"),
                        surveyImageUrl,
                        "Gợi ý của giảng viên: nêu xu hướng lớn nhất trước, so sánh bằng số liệu rồi mới đưa lý do hợp lý."),
                essay(
                        54,
                        "공공시설의 평일 운영 시간을 밤 10시까지 연장하는 방안에 대해 "
                                + "쓰십시오. 필요성, 예상되는 문제, 해결 방법, 자신의 입장을 "
                                + "포함하십시오. 주제 맥락: " + topic + ", 모의고사 "
                                + variant + ". (600~700자)",
                        "Q54",
                        new BigDecimal("50.00"),
                        null,
                        "Gợi ý của giảng viên: mở bài nêu lập trường, thân bài có nhu cầu–vấn đề–giải pháp, kết bài khẳng định quan điểm."));
    }

    private static QuestionDraft speakingQuestion(
            int questionNo,
            String prompt,
            String audioUrl,
            String imageUrl,
            Theme theme,
            int testIndex,
            int questionIndex
    ) {
        int preparation = 20 + questionIndex * 10;
        int response = 45 + questionIndex * 15;
        int playLimit = questionIndex < 2 ? 1 : 2;
        Map<String, Object> speakingDelivery = map(
                "promptAudioReference", audioUrl,
                "promptPlayLimit", playLimit,
                "preparationSeconds", preparation,
                "responseSeconds", response);
        String contentJson = json(map(
                "schemaVersion", "question-content-v1",
                "options", list(),
                "blanks", list(),
                "imageReference", imageUrl,
                "audioReference", audioUrl,
                "speakingDelivery", speakingDelivery));
        String answerSpecJson = json(map(
                "schemaVersion", "answer-spec-v1",
                "questionType", "SPEAKING",
                "correctOptionIds", list(),
                "correctValue", null,
                "blanks", list(),
                "scoringPolicyCode", "PROFILE_BASED"));
        return new QuestionDraft(
                questionNo,
                "SPEAKING",
                prompt,
                null,
                contentJson,
                null,
                answerSpecJson,
                "Gợi ý của giảng viên: mở đầu trực tiếp, triển khai ít nhất hai ý "
                        + "có liên kết và kết thúc bằng một câu chốt. Chủ đề: "
                        + theme.topic() + ", Test " + (testIndex + 1) + ".",
                new BigDecimal("25.00"),
                null);
    }

    private static QuestionDraft singleChoice(
            int number,
            String prompt,
            List<Map<String, Object>> options,
            String correctOptionId,
            String imageUrl,
            String audioUrl,
            String explanation,
            BigDecimal points
    ) {
        String contentJson = json(map(
                "schemaVersion", "question-content-v1",
                "options", options,
                "blanks", list(),
                "imageReference", imageUrl,
                "audioReference", audioUrl));
        String answerSpecJson = json(map(
                "schemaVersion", "answer-spec-v1",
                "questionType", "SINGLE_CHOICE",
                "correctOptionIds", list(correctOptionId),
                "correctValue", null,
                "blanks", list(),
                "scoringPolicyCode", "ALL_OR_NOTHING"));
        return new QuestionDraft(
                number,
                "SINGLE_CHOICE",
                prompt,
                json(options),
                contentJson,
                correctOptionId,
                answerSpecJson,
                explanation,
                points,
                null);
    }

    private static QuestionDraft tfng(
            int number,
            String prompt,
            String correctValue,
            String explanation,
            BigDecimal points
    ) {
        return tfng(number, prompt, correctValue, explanation, points, null);
    }

    private static QuestionDraft tfng(
            int number,
            String prompt,
            String correctValue,
            String explanation,
            BigDecimal points,
            String audioUrl
    ) {
        String contentJson = json(map(
                "schemaVersion", "question-content-v1",
                "options", list(),
                "blanks", list(),
                "imageReference", null,
                "audioReference", audioUrl));
        String answerSpecJson = json(map(
                "schemaVersion", "answer-spec-v1",
                "questionType", "TRUE_FALSE_NOT_GIVEN",
                "correctOptionIds", list(),
                "correctValue", correctValue,
                "blanks", list(),
                "scoringPolicyCode", "ALL_OR_NOTHING"));
        return new QuestionDraft(
                number,
                "TRUE_FALSE_NOT_GIVEN",
                prompt,
                null,
                contentJson,
                correctValue,
                answerSpecJson,
                explanation,
                points,
                null);
    }

    private static QuestionDraft fillBlank(
            int number,
            String prompt,
            String blankId,
            List<String> acceptedValues,
            String audioUrl,
            String explanation,
            BigDecimal points
    ) {
        List<Map<String, Object>> blanks = list(map(
                "id", blankId,
                "prompt", blankId));
        String contentJson = json(map(
                "schemaVersion", "question-content-v1",
                "options", list(),
                "blanks", blanks,
                "imageReference", null,
                "audioReference", audioUrl));
        String answerSpecJson = json(map(
                "schemaVersion", "answer-spec-v1",
                "questionType", "FILL_BLANK",
                "correctOptionIds", list(),
                "correctValue", null,
                "blanks", list(map(
                        "blankId", blankId,
                        "acceptedValues", acceptedValues)),
                "scoringPolicyCode", "NORMALIZED_EXACT"));
        return new QuestionDraft(
                number,
                "FILL_BLANK",
                prompt,
                null,
                contentJson,
                acceptedValues.get(0),
                answerSpecJson,
                explanation,
                points,
                null);
    }

    private static QuestionDraft essay(
            int number,
            String prompt,
            String writingTask,
            BigDecimal points,
            String imageUrl,
            String explanation
    ) {
        String contentJson = json(map(
                "schemaVersion", "question-content-v1",
                "options", list(),
                "blanks", list(),
                "imageReference", imageUrl,
                "audioReference", null));
        String answerSpecJson = json(map(
                "schemaVersion", "answer-spec-v1",
                "questionType", "ESSAY",
                "correctOptionIds", list(),
                "correctValue", null,
                "blanks", list(),
                "scoringPolicyCode", "PROFILE_BASED"));
        return new QuestionDraft(
                number,
                "ESSAY",
                prompt,
                null,
                contentJson,
                null,
                answerSpecJson,
                explanation,
                points,
                writingTask);
    }

    private static Map<String, Object> option(String id, String text) {
        return map("id", id, "text", text, "imageReference", null);
    }

    private static String provenance(String source, String sourceFile) {
        return json(map(
                "schemaVersion", "assessment-stimulus-v1",
                "source", source,
                "approved", true,
                "origin", "KSH_ORIGINAL_DEMO",
                "locale", "ko-KR",
                "contentLicense", "KSH_INTERNAL_DEMO_ORIGINAL",
                "officialTopikMaterial", false,
                "sourceFile", sourceFile));
    }

    private static String groupExample(Theme theme, int testIndex, String skill) {
        String teacherHint = switch (skill) {
                    case "READING" ->
                            "Yêu cầu học viên gạch chân bằng chứng trước khi chọn đáp án.";
                    case "LISTENING" ->
                            "Cho học viên nghe lượt đầu lấy ý chính, lượt sau kiểm tra chi tiết.";
                    case "WRITING" ->
                            "Kiểm tra đủ mọi ý bắt buộc trước khi sửa ngữ pháp và liên kết.";
                    case "SPEAKING" ->
                            "Khuyến khích cấu trúc mở–hai ý chính–kết luận, không học thuộc máy móc.";
                    default -> "Theo dõi mức độ hoàn thành nhiệm vụ.";
                };
        return json(map(
                "label", "Gợi ý giảng viên · 교사 가이드",
                "content", teacherHint + " Chủ đề: " + theme.topic()
                        + "; Test " + (testIndex + 1) + ".",
                "choices", null,
                "answer", null));
    }

    private static String placement(Asset asset) {
        if (asset.fileName().startsWith("cover-")) {
            return "SET_COVER";
        }
        if (asset.fileName().startsWith("speaking-")) {
            return "SPEAKING_PROMPT_ORIGINAL";
        }
        if (asset.fileName().startsWith("listening-")) {
            return "LISTENING_AUDIO";
        }
        return "QUESTION_IMAGE";
    }

    private static long requireMapped(
            Map<Long, Long> mapping,
            long key,
            String label
    ) {
        Long value = mapping.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing " + label + " for source ID " + key);
        }
        return value;
    }

    private static void copyAtomicallyIfNeeded(
            Path source,
            Path destination,
            String expectedSha
    ) throws IOException {
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination)) {
            String actual = sha256(Files.readAllBytes(destination));
            if (!expectedSha.equals(actual)) {
                throw new IllegalStateException(
                        "Existing storage object has unexpected bytes: " + destination);
            }
            return;
        }
        Path temporary = destination.resolveSibling(
                destination.getFileName() + ".importing");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validatePng(byte[] bytes, String fileName) {
        byte[] signature = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        if (bytes.length < signature.length
                || !Arrays.equals(signature, Arrays.copyOf(bytes, signature.length))) {
            throw new IllegalArgumentException("Invalid PNG magic: " + fileName);
        }
    }

    private static void validateWav(byte[] bytes, String fileName) {
        if (bytes.length < 12
                || !"RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid WAV magic: " + fileName);
        }
    }

    private static String displayAssetTitle(String fileName) {
        if (fileName.startsWith("cover-")) {
            return "Ảnh bìa " + fileName;
        }
        if (fileName.startsWith("stimulus-")) {
            return "Ảnh dữ kiện câu hỏi " + fileName;
        }
        if (fileName.startsWith("listening-")) {
            return "Audio hội thoại nghe " + fileName;
        }
        return "Audio đề nói " + fileName;
    }

    private static String displayAssetAlt(String fileName) {
        if (fileName.endsWith(".png")) {
            return "Hình minh họa tiếng Hàn tự tạo cho bộ dữ liệu demo KSH.";
        }
        return "Bản đọc tiếng Hàn tự tạo bằng giọng máy cục bộ cho bộ dữ liệu demo KSH.";
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must be key/value pairs.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static String json(Object value) {
        StringBuilder output = new StringBuilder();
        appendJson(output, value);
        return output.toString();
    }

    private static void appendJson(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
            return;
        }
        if (value instanceof String string) {
            output.append('"');
            for (int index = 0; index < string.length(); index++) {
                char character = string.charAt(index);
                switch (character) {
                    case '"' -> output.append("\\\"");
                    case '\\' -> output.append("\\\\");
                    case '\b' -> output.append("\\b");
                    case '\f' -> output.append("\\f");
                    case '\n' -> output.append("\\n");
                    case '\r' -> output.append("\\r");
                    case '\t' -> output.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            output.append("\\u")
                                    .append(String.format(Locale.ROOT, "%04x", (int) character));
                        } else {
                            output.append(character);
                        }
                    }
                }
            }
            output.append('"');
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                first = false;
                appendJson(output, String.valueOf(entry.getKey()));
                output.append(':');
                appendJson(output, entry.getValue());
            }
            output.append('}');
            return;
        }
        if (value instanceof Collection<?> collection) {
            output.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) output.append(',');
                first = false;
                appendJson(output, item);
            }
            output.append(']');
            return;
        }
        throw new IllegalArgumentException(
                "Unsupported JSON value: " + value.getClass().getName());
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record Theme(
            String slug,
            String title,
            String description,
            String topic,
            String coverFile
    ) {
    }

    private record ReadingScenario(
            String title,
            String passage,
            String place,
            String time,
            String action,
            String purpose,
            String falseStatement,
            String imageFile
    ) {
    }

    private record ListeningScenario(
            String transcript,
            String place,
            String time,
            String action,
            String purpose,
            String falseStatement,
            String audioFile
    ) {
    }

    private record Asset(
            long id,
            String url,
            String storageKey,
            String fileName,
            String mimeType,
            String assetType
    ) {
    }

    private record TestRow(
            long id,
            String title,
            String description,
            int displayOrder,
            int estimatedMinutes
    ) {
    }

    private record SectionRow(
            long id,
            long testId,
            String title,
            String skill,
            String sectionType,
            String instructions,
            String deliveryJson,
            int durationMinutes,
            BigDecimal totalPoints,
            int displayOrder
    ) {
    }

    private record GroupRow(
            long id,
            long sectionId,
            String label,
            int questionFrom,
            int questionTo,
            String instruction,
            String stimulusType,
            String passageText,
            String transcriptText,
            String imageUrl,
            String stimulusProvenanceJson,
            String audioUrl,
            String exampleJson,
            int displayOrder
    ) {
    }

    private record QuestionDraft(
            int questionNo,
            String questionType,
            String prompt,
            String optionsJson,
            String questionContentJson,
            String answerKey,
            String answerSpecJson,
            String explanation,
            BigDecimal points,
            String writingTaskType
    ) {
    }

    private record QuestionRow(
            long id,
            long groupId,
            int questionNo,
            String questionType,
            String prompt,
            String optionsJson,
            String questionContentJson,
            String answerKey,
            String answerSpecJson,
            String explanation,
            BigDecimal points,
            int displayOrder,
            String writingTaskType
    ) {
    }
}
