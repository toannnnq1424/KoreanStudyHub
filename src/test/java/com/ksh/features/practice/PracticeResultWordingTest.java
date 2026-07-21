package com.ksh.features.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PracticeResultWordingTest {

    private static final Path RESOURCE_ROOT = Path.of("src", "main", "resources");

    @Test
    void practiceJsUsesNeutralWritingRawScoreLabel() throws IOException {
        String js = readResource("static/js/practice.js");

        assertFalse(js.contains("Điểm TOPIK thô"));
        assertTrue(js.contains("Điểm câu viết theo bộ chấm"));
    }

    @Test
    void resultDetailUsesNeutralWritingTaskScoreLabel() throws IOException {
        String html = readResource("templates/practice/result-detail.html");

        assertFalse(html.contains("'Task Score'"));
        assertFalse(html.contains(">Task Score<"));
        assertFalse(html.contains("Điểm đánh giá câu viết"));
        assertTrue(html.contains("Điểm bài làm"));
        assertTrue(html.contains("r.maxScore || 10"));
        assertFalse(html.contains("rubricValue / 10.0"));
    }

    @Test
    void resultDetailUsesBackendSpeakingRowsWithoutBrowserOwnedAcousticTaxonomy() throws IOException {
        String html = readResource("templates/practice/result-detail.html");

        assertTrue(html.contains("Sao chép"));
        assertFalse(html.contains(">Copy<"));
        assertFalse(html.contains("Đã copy"));
        assertFalse(html.contains("<strong>Evidence</strong>"));
        assertTrue(html.contains("Hoàn tất"));
        assertEquals(5, countOccurrences(html, "class=\"prd-tab-btn"));
        assertTrue(html.contains("data-tab=\"overview\""));
        assertTrue(html.contains("data-tab=\"strengths\""));
        assertTrue(html.contains("data-tab=\"needs\""));
        assertTrue(html.contains("data-tab=\"upgrade\""));
        assertTrue(html.contains("data-tab=\"sample\""));
        assertFalse(html.contains("data-tab=\"criterion-"));
        assertFalse(html.contains("data-speaking-criterion"));
        assertFalse(html.contains("renderSpeakingCriterionPanel"));
        assertFalse(html.contains("SPEAKING_TRANSCRIPT_CRITERIA"));
        assertFalse(html.contains("SPEAKING_ACOUSTIC_CRITERIA"));
        assertFalse(html.contains("SPEAKING_CRITERION_LABELS"));
        assertFalse(html.contains("S_CONTENT_TASK_FULFILLMENT"));
        assertFalse(html.contains("S_FLUENCY"));
        assertFalse(html.contains("S_PRONUNCIATION_DELIVERY"));
        assertFalse(html.contains("NOT_SCORABLE"));
        assertTrue(html.contains("speakingScoredRubricRows()"));
        assertTrue(html.contains("field(aiData, 'profile_available', 'profileAvailable') === true"));
        assertTrue(html.contains("row.availability === 'SCORED'"));
        assertTrue(html.contains("Number.isFinite(score)"));
        assertTrue(html.contains("Number.isFinite(maxScore)"));
        assertTrue(html.contains("firstValue(r.name, 'Tiêu chí hồ sơ')"));
        assertFalse(html.contains("r.displayName"));
        assertFalse(html.contains("r.criterionId"));
        assertEquals(1, countOccurrences(html,
                "Hồ sơ này chỉ dựa trên bản chép lời; không đánh giá độ lưu loát, phát âm hoặc đặc tính âm thanh."));
        assertTrue(html.contains("Bài nói nâng cấp"));
        assertFalse(html.contains("const status = field(aiData, 'evaluation_status', 'evaluationStatus')"));
        assertFalse(html.contains("[status, summary"));
    }

    @Test
    void resultOverviewUsesCanonicalScoreSummary() throws IOException {
        String html = readResource("templates/practice/result.html");

        assertFalse(html.contains("'Overall Score'"));
        assertFalse(html.contains(">Overall Score<"));
        assertTrue(html.contains("result.score().primaryDisplay()"));
        assertTrue(html.contains("result.score().pointsDisplay()"));
        assertTrue(html.contains("result.identity().skill() != 'SPEAKING'"));
        assertTrue(html.contains("result.identity().skill() == 'SPEAKING'"));
        assertTrue(html.contains("Phạm vi hồ sơ"));
        assertTrue(html.contains("phần có hồ sơ ngôn ngữ"));
        assertFalse(html.contains("matchedScore * 10"));
        assertFalse(html.contains("result.celebratory()"));
        assertFalse(html.contains("Kết quả nổi bật"));
    }

    @Test
    void writingFragmentUsesPresenterScoreAndRetainedLegacyJsUsesRubricMaxScore() throws IOException {
        String fragment = readResource("templates/practice/result/writing.html");
        String js = readResource("static/js/practice.js");

        assertTrue(fragment.contains("task.score().pointsDisplay()"));
        assertTrue(fragment.contains("criterion.scoreDisplay()"));
        assertTrue(fragment.contains("Đánh giá luyện tập KSH"));
        assertTrue(fragment.contains("không phải điểm hoặc chứng chỉ TOPIK chính thức"));
        assertTrue(fragment.contains("questionId=${task.questionId()}"));
        assertTrue(fragment.contains("không có điểm riêng và không cộng vào tổng điểm"));
        assertTrue(fragment.contains("Không có điểm hoặc mức chất lượng nào được suy đoán"));
        assertFalse(fragment.contains("criterion.band()"));
        assertFalse(fragment.contains("lens.band()"));
        assertFalse(fragment.contains("th:hidden"));
        assertFalse(fragment.contains("matchedScore * 10"));
        assertTrue(js.contains("item.maxScore || 10"));
    }

    @Test
    void speakingOverviewUsesTranscriptProfileWordingWithoutGenericBands() throws IOException {
        String fragment = readResource("templates/practice/result/speaking.html");
        String css = readResource("static/css/practice-result.css");

        assertTrue(fragment.contains("Hồ sơ ngôn ngữ dựa trên bản chép lời"));
        assertTrue(fragment.contains("Kết quả Nói tổng hợp"));
        assertTrue(fragment.contains("Chưa khả dụng"));
        assertTrue(fragment.contains("Không cộng bốn tiêu chí bản chép lời thành điểm Nói tổng hợp"));
        assertTrue(fragment.contains("Không có điểm số"));
        assertTrue(fragment.contains("Bản chép lời, độ tin cậy nhận dạng và thông tin tệp"));
        assertFalse(fragment.contains("IELTS"));
        assertFalse(fragment.contains("criterion.band()"));
        assertFalse(fragment.contains("criterion.percentage()"));
        assertFalse(fragment.contains("pr-scale"));
        assertFalse(fragment.toLowerCase().contains("radar"));
        assertFalse(css.contains(".pr-band-chip"));
        assertFalse(css.contains(".pr-scale"));
        assertFalse(css.toLowerCase().contains("radar"));
    }

    @Test
    void progressKeepsSpeakingScoresNonnumericAcrossCardsHistoryAndCharts() throws IOException {
        String html = readResource("templates/practice/progress.html");
        String js = readResource("static/js/practice-progress.js");

        assertTrue(html.contains(">Hồ sơ học tập</span>"));
        assertTrue(html.contains("alt=\"Ảnh đại diện\""));
        assertFalse(html.contains("Learning Profile"));
        assertFalse(html.contains("alt=\"Avatar\""));
        assertEquals(4, countOccurrences(html, "data-skill="));
        assertTrue(html.contains("data-skill=\"READING\" onclick=\"switchSkillAccordion('READING')\">Đọc</button>"));
        assertTrue(html.contains("data-skill=\"LISTENING\" onclick=\"switchSkillAccordion('LISTENING')\">Nghe</button>"));
        assertTrue(html.contains("data-skill=\"WRITING\" onclick=\"switchSkillAccordion('WRITING')\">Viết</button>"));
        assertTrue(html.contains("data-skill=\"SPEAKING\" onclick=\"switchSkillAccordion('SPEAKING')\">Nói</button>"));
        assertFalse(html.contains("Đọc (Reading)"));
        assertFalse(html.contains("Nghe (Listening)"));
        assertFalse(html.contains("Viết (Writing)"));
        assertFalse(html.contains("Nói (Speaking)"));
        assertTrue(html.contains("Không có điểm Nói tổng hợp"));
        assertTrue(html.contains("m.skill() == 'SPEAKING'"));
        assertTrue(html.contains("row.skill() == 'SPEAKING'"));
        assertTrue(html.contains("m.deltaFromLastPeriod() != null"));
        assertTrue(html.contains("Lượt đã hoàn thành"));
        assertTrue(html.contains("cải thiện kết quả"));
        assertFalse(html.contains("Bài đã được chấm điểm"));
        assertFalse(html.contains("kéo band điểm"));
        assertEquals(2, countOccurrences(html, ">Tiếp tục</a>"));
        assertEquals(2, countOccurrences(html, ">Xem kết quả</a>"));
        assertEquals(2, countOccurrences(html, ">Làm lại</button>"));
        assertFalse(html.contains(">Continue</a>"));
        assertFalse(html.contains(">View result</a>"));
        assertFalse(html.contains(">Retake</button>"));
        assertTrue(html.contains("Chưa có dữ liệu điểm"));
        assertTrue(js.contains("filter(m => m.skill !== 'SPEAKING')"));
        assertTrue(js.contains("skill === 'SPEAKING'"));
        assertTrue(js.contains("btn.dataset.skill === skill"));
        assertFalse(js.contains("btn.innerText.toUpperCase().includes(skill)"));
        assertTrue(js.contains("scoredMetrics.map(m => skillLabelVi(m.skill))"));
        assertTrue(js.contains("OVERVIEW_DATA.skillMetrics.map(m => skillLabelVi(m.skill))"));
        assertTrue(js.contains("typeName.textContent = row.questionTypeLabel || 'Dạng câu hỏi'"));
        assertFalse(js.contains("${row.questionType}"));
        assertFalse(js.contains("Đọc (Reading)"));
        assertFalse(js.contains("Nghe (Listening)"));
        assertFalse(js.contains("Viết (Writing)"));
        assertFalse(js.contains("Nói (Speaking)"));
        assertFalse(js.contains("'SPEAKING': { label: 'Nói (Speaking)', data:"));
    }

    private static String readResource(String relativePath) throws IOException {
        return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
