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
    void resultOverviewUsesCanonicalScoreSummary() throws IOException {
        String html = readResource("templates/practice/result.html");

        assertFalse(html.contains("'Overall Score'"));
        assertFalse(html.contains(">Overall Score<"));
        assertTrue(html.contains("result.score().primaryDisplay()"));
        assertTrue(html.contains("result.score().pointsDisplay()"));
        assertTrue(html.contains("pr-summary-writing-breakdown"));
        assertTrue(html.contains("pr-notebook-divider"));
        assertTrue(html.contains("Phạm vi điểm"));
        assertTrue(html.contains("không phải điểm hoặc chứng chỉ TOPIK chính thức"));
        assertTrue(html.contains("result.identity().skill() != 'SPEAKING'"));
        assertTrue(html.contains("result.identity().skill() == 'SPEAKING'"));
        assertTrue(html.contains("Phạm vi hồ sơ"));
        assertTrue(html.contains("phần có hồ sơ ngôn ngữ"));
        assertFalse(html.contains("matchedScore * 10"));
        assertFalse(html.contains("result.celebratory()"));
        assertFalse(html.contains("Kết quả nổi bật"));
    }

    @Test
    void writingFragmentUsesPresenterScore() throws IOException {
        String fragment = readResource("templates/practice/result/writing.html");

        assertTrue(fragment.contains("task.score().pointsDisplay()"));
        assertTrue(fragment.contains("criterion.scoreDisplay()"));
        assertFalse(fragment.contains("Đánh giá luyện tập KSH"));
        assertFalse(fragment.contains("không phải điểm hoặc chứng chỉ TOPIK chính thức"));
        assertTrue(fragment.contains("questionId=${task.questionId()}"));
        assertTrue(fragment.contains("Không có điểm hoặc mức chất lượng nào được suy đoán"));
        assertTrue(fragment.contains("th:hidden=\"${!taskStatus.first}\""));
        assertFalse(fragment.contains("Chẩn đoán để luyện tiếp"));
        assertFalse(fragment.contains("criterion.band()"));
        assertFalse(fragment.contains("lens.band()"));
        assertFalse(fragment.contains("stripTrailingZeros().toPlainString()"));
        assertFalse(fragment.contains("pr-task-prompt-toggle"));
        assertFalse(fragment.contains("Xem toàn bộ đề bài"));
        assertFalse(fragment.contains("<details class=\"pr-task-prompt\""));
        assertFalse(fragment.contains("<span class=\"pr-task-type\""));
        assertFalse(fragment.contains("<h3 th:text=\"${task.taskLabel()}\""));
        assertFalse(fragment.contains("matchedScore * 10"));
    }

    @Test
    void speakingOverviewUsesTranscriptProfileWordingWithKshCriterionLevels() throws IOException {
        String fragment = readResource("templates/practice/result/speaking.html");
        String css = readResource("static/css/practice-result.css");

        assertTrue(fragment.contains("Hồ sơ ngôn ngữ dựa trên bản chép lời"));
        assertTrue(fragment.contains("Kết quả Nói tổng hợp"));
        assertTrue(fragment.contains("Chưa khả dụng"));
        assertTrue(fragment.contains("Không cộng bốn tiêu chí bản chép lời thành điểm Nói tổng hợp"));
        assertTrue(fragment.contains("Không có điểm số"));
        assertTrue(fragment.contains("Bản chép lời, độ tin cậy nhận dạng và thông tin tệp"));
        assertFalse(fragment.contains("IELTS"));
        assertTrue(fragment.contains("criterion.band().label()"));
        assertTrue(fragment.contains("data-result-tabs=\"speaking-overview-criteria\""));
        assertTrue(fragment.contains("Bộ đánh giá chưa nhận âm thanh trực tiếp"));
        assertFalse(fragment.contains("criterion.percentage()"));
        assertFalse(fragment.contains("Band descriptors"));
        assertFalse(fragment.contains("pr-scale"));
        assertFalse(fragment.toLowerCase().contains("radar"));
        assertFalse(css.contains(".pr-band-chip"));
        assertFalse(css.contains(".pr-scale"));
        assertFalse(css.toLowerCase().contains("radar"));
    }

    @Test
    void speakingFailedOrUnavailableOverviewOffersOnlyCanonicalOtherAttemptRecovery()
            throws IOException {
        String fragment =
                readResource("templates/practice/result/speaking.html");

        assertTrue(fragment.contains(
                "result.feedback().state() == 'FAILED' or result.feedback().state() == 'UNAVAILABLE'"));
        assertTrue(fragment.contains(
                "Lần nộp này được lưu bất biến và không thể đánh giá lại"));
        assertFalse(fragment.contains(
                "제출한 시도는 변경되지 않으며 다시 평가할 수 없습니다"));
        assertTrue(fragment.contains(
                "“Luyện lại” mở bước chuẩn bị cho một lượt khác"));
        assertTrue(fragment.contains(
                "Nếu đã có một lượt khác đang làm dở và còn hợp lệ"));
        assertTrue(fragment.contains(
                "không dùng lại lần nộp hoặc"));
        assertFalse(fragment.contains(
                "sẽ bắt đầu một lần làm bài mới"));
        assertTrue(fragment.contains(
                "/practice/sets/{setId}/tests/{testId}"));
        assertTrue(fragment.contains(
                "setId=${result.identity().setId()}"));
        assertTrue(fragment.contains(
                "testId=${result.identity().testId()}"));
        assertTrue(fragment.contains("Luyện lại"));
        assertFalse(fragment.contains("Luyện lại · 다시 연습"));
        assertFalse(fragment.contains("<form"));
        assertFalse(fragment.contains("/re-evaluate"));
        assertFalse(fragment.contains("reuse"));
        assertFalse(fragment.contains("retry provider"));
    }

    @Test
    void progressKeepsSpeakingScoresNonnumericAcrossCardsHistoryAndCharts() throws IOException {
        String html = readResource("templates/practice/progress.html");
        String facts = readResource("templates/practice/fragments/progress-facts.html");
        String js = readResource("static/js/practice-progress.js");

        assertTrue(html.contains("Hồ sơ học tập"));
        assertFalse(html.contains("학습 기록"));
        assertTrue(html.contains("alt=\"Ảnh đại diện\""));
        assertFalse(html.contains("Learning Profile"));
        assertFalse(html.contains("alt=\"Avatar\""));
        assertFalse(html.contains("Đọc (Reading)"));
        assertFalse(html.contains("Nghe (Listening)"));
        assertFalse(html.contains("Viết (Writing)"));
        assertFalse(html.contains("Nói (Speaking)"));
        assertTrue(html.contains("không có điểm Nói tổng hợp"));
        assertTrue(html.contains("metric.skill() == 'SPEAKING'"));
        assertTrue(html.contains("row.skill() == 'SPEAKING'"));
        assertTrue(html.contains("metric.deltaFact().renderableValue()"));
        assertTrue(html.contains("Dữ kiện 7 ngày theo kỹ năng"));
        assertTrue(html.contains(
                "Không gọi giá trị thiếu so sánh là “không đổi”."));
        assertTrue(html.contains("Chưa có đủ mẫu so sánh"));
        assertFalse(html.contains("비교 표본 부족"));
        assertFalse(html.contains("bài tuần này"));
        assertFalse(html.contains("cải thiện kết quả"));
        assertFalse(html.contains("Bài đã được chấm điểm"));
        assertFalse(html.contains("kéo band điểm"));
        assertEquals(1, countOccurrences(html, ">Tiếp tục</a>"));
        assertEquals(1, countOccurrences(html, ">Xem kết quả</a>"));
        assertFalse(html.contains(">Continue</a>"));
        assertFalse(html.contains(">View result</a>"));
        assertFalse(html.contains(">Retake</button>"));
        assertTrue(html.contains("row.score() != null"));
        assertTrue(html.contains("row.totalPoints() != null"));
        assertTrue(html.contains("row.score() + ' / ' + row.totalPoints()"));
        assertFalse(html.contains(": row.status())"));
        assertFalse(html.contains("Lịch sử luyện tập đầy đủ"));
        assertTrue(html.contains("Viết không vào radar"));
        assertTrue(html.contains("Nói không có điểm tổng hợp"));
        assertTrue(html.contains("Không có điểm mới nhất/tốt nhất"));
        assertTrue(html.contains("Hồ sơ nguồn: hoạt động Nói không mang điểm"));
        assertTrue(html.contains("metric.scoreFact().profileId()"));
        assertFalse(html.contains("metric.scoreFact().profile()"));
        assertTrue(facts.contains("SPEAKING_NUMERIC_AGGREGATION_NOT_SUPPORTED"));
        assertTrue(facts.contains(
                "chỉ tóm tắt quy mô mẫu, độ mới và độ phủ"));
        assertTrue(facts.contains("Dữ liệu cũ chưa xác minh"));
        assertTrue(facts.contains("đã cắt bớt nguồn"));
        assertFalse(facts.matches("(?s).*[가-힣].*"));
        assertTrue(js.contains(
                "metric.skill === 'READING' || metric.skill === 'LISTENING'"));
        assertFalse(js.contains("metric.skill === 'WRITING'"));
        assertFalse(js.contains("metric.skill === 'SPEAKING'"));
        assertFalse(js.contains("skill === 'SPEAKING'"));
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
