package com.ksh.features.practice.manage.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeQuestion;
import com.ksh.entities.WritingTaskType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PracticeDraftValidator {

    private final ObjectMapper objectMapper;

    public PracticeDraftValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(String draftJson) {
        List<ValidationMsg> messages = new ArrayList<>();
        int sectionCount = 0;
        int groupCount = 0;
        int questionCount = 0;
        double totalPoints = 0.0;

        try {
            JsonNode root = objectMapper.readTree(draftJson);
            
            // Validate category
            JsonNode docNode = root.path("document");
            String category = docNode.path("detectedCategory").asText("UNCLASSIFIED").trim();
            if (category.isEmpty() || "UNCLASSIFIED".equalsIgnoreCase(category)) {
                messages.add(new ValidationMsg("BLOCKING", "Phân loại học liệu (Category) là bắt buộc trước khi xuất bản. Vui lòng chọn phân loại hợp lệ."));
            }

            JsonNode sections = root.path("sections");

            if (!sections.isArray() || sections.size() == 0) {
                messages.add(new ValidationMsg("BLOCKING", "Đề thi bắt buộc phải có ít nhất một Phần thi (Section)."));
            } else {
                sectionCount = sections.size();
                for (int sIdx = 0; sIdx < sections.size(); sIdx++) {
                    JsonNode sec = sections.get(sIdx);
                    String sTitle = sec.path("title").asText("Phần thi " + (sIdx + 1));
                    String skill = sec.path("skill").asText("");

                    if (skill.isBlank()) {
                        messages.add(new ValidationMsg("BLOCKING", String.format("Phần thi '%s' chưa được cấu hình kỹ năng (Reading/Listening...).", sTitle), sIdx, null, null));
                    }

                    JsonNode groups = sec.path("groups");
                    if (!groups.isArray() || groups.size() == 0) {
                        messages.add(new ValidationMsg("BLOCKING", String.format("Phần thi '%s' trống rỗng, không chứa Nhóm câu hỏi nào.", sTitle), sIdx, null, null));
                    } else {
                        groupCount += groups.size();
                        for (int gIdx = 0; gIdx < groups.size(); gIdx++) {
                            JsonNode grp = groups.get(gIdx);
                            String gLabel = grp.path("label").asText("Nhóm " + (gIdx + 1));
                            
                            JsonNode questions = grp.path("questions");
                            if (!questions.isArray() || questions.size() == 0) {
                                messages.add(new ValidationMsg("BLOCKING", String.format("Nhóm '%s' trong phần '%s' chưa có câu hỏi nào.", gLabel, sTitle), sIdx, gIdx, null));
                            } else {
                                questionCount += questions.size();
                                for (int qIdx = 0; qIdx < questions.size(); qIdx++) {
                                    JsonNode q = questions.get(qIdx);
                                    int qNo = q.path("questionNo").asInt(qIdx + 1);
                                    String type = q.path("questionType").asText("SINGLE_CHOICE");
                                    String prompt = q.path("prompt").asText("");
                                    double points = q.path("points").asDouble(1.0);
                                    totalPoints += points;

                                    if (prompt.isBlank()) {
                                        messages.add(new ValidationMsg("WARNING", String.format("Câu hỏi số %d trong nhóm '%s' trống tiêu đề (prompt).", qNo, gLabel), sIdx, gIdx, qIdx));
                                    }

                                    // Answer and Option validation
                                    JsonNode options = q.path("options");
                                    JsonNode answer = q.path("answer");

                                    if ("SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)) {
                                        if (!options.isArray() || options.size() < 2) {
                                            messages.add(new ValidationMsg("BLOCKING", String.format("Câu hỏi trắc nghiệm số %d bắt buộc có ít nhất 2 đáp án lựa chọn.", qNo), sIdx, gIdx, qIdx));
                                        }
                                        if (answer.isMissingNode() || answer.path("value").asText("").isBlank()) {
                                            messages.add(new ValidationMsg("BLOCKING", String.format("Câu hỏi số %d chưa chọn đáp án đúng.", qNo), sIdx, gIdx, qIdx));
                                        }
                                    }

                                    validateWritingTaskMetadata(messages, skill, type, q, sIdx, gIdx, qIdx);

                                    String explanation = q.path("explanationVi").asText("");
                                    if (explanation.isBlank()) {
                                        messages.add(new ValidationMsg("WARNING", String.format("Câu hỏi số %d chưa có bài dịch hoặc giải thích tiếng Việt.", qNo), sIdx, gIdx, qIdx));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            messages.add(new ValidationMsg("BLOCKING", "Lỗi phân tích cú pháp dữ liệu: " + e.getMessage()));
        }

        boolean hasBlocking = messages.stream().anyMatch(m -> "BLOCKING".equals(m.type()));
        return new ValidationResult(hasBlocking, messages, sectionCount, groupCount, questionCount, totalPoints);
    }

    private void validateWritingTaskMetadata(List<ValidationMsg> messages,
                                             String skill,
                                             String questionType,
                                             JsonNode question,
                                             int sIdx,
                                             int gIdx,
                                             int qIdx) {
        if (!"WRITING".equalsIgnoreCase(skill) || !PracticeQuestion.TYPE_ESSAY.equals(questionType)) {
            return;
        }
        JsonNode taskNode = question.get("essayTaskType");
        if (taskNode == null || taskNode.isNull()) {
            messages.add(new ValidationMsg("WARNING",
                    "Câu Writing này chưa có loại bài rõ ràng. Kết quả chấm có thể tiếp tục dùng cơ chế tương thích cũ.",
                    sIdx, gIdx, qIdx));
            return;
        }
        if (!taskNode.isTextual()) {
            messages.add(new ValidationMsg("BLOCKING", "Loại bài Writing không hợp lệ.", sIdx, gIdx, qIdx));
            return;
        }
        String value = taskNode.asText();
        if (value.isBlank()) {
            messages.add(new ValidationMsg("BLOCKING", "Vui lòng chọn loại bài Writing cho câu tự luận.", sIdx, gIdx, qIdx));
            return;
        }
        try {
            WritingTaskType.valueOf(value);
        } catch (IllegalArgumentException e) {
            messages.add(new ValidationMsg("BLOCKING", "Loại bài Writing không hợp lệ.", sIdx, gIdx, qIdx));
        }
    }

    public record ValidationMsg(String type, String content, Integer sIdx, Integer gIdx, Integer qIdx) {
        public ValidationMsg(String type, String content) {
            this(type, content, null, null, null);
        }
    }

    public record ValidationResult(
            boolean hasBlocking,
            List<ValidationMsg> messages,
            int sectionCount,
            int groupCount,
            int questionCount,
            double totalPoints
    ) {}
}
