package com.ksh.features.practice.manage.validator;

import com.ksh.features.practice.manage.dto.AiDocumentImportRequest;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest.GroupHint;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest.RegionLocks;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest.RegionPayload;
import com.ksh.features.practice.manage.dto.AiDocumentImportRequest.SectionHint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ImportAiPayloadValidator {

    public record ValidationError(String code, String severity, List<String> regionIds, String messageVi) {}

    public List<ValidationError> validate(AiDocumentImportRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new ValidationError("EMPTY_REQUEST", "ERROR", List.of(), "Request rỗng."));
            return errors;
        }

        List<RegionPayload> regions = request.getRegions() != null ? request.getRegions() : List.of();
        List<GroupHint> groups = request.getGroups() != null ? request.getGroups() : List.of();
        List<SectionHint> sections = request.getSections() != null ? request.getSections() : List.of();

        if (regions.isEmpty()) {
            errors.add(new ValidationError("NO_VALID_REGIONS", "ERROR", List.of(),
                    "Chưa có vùng hợp lệ nào được gửi AI. Hãy khoanh ít nhất một vùng đề, đáp án, passage, transcript hoặc ảnh trước khi phân tích."));
        }

        List<String> validGroupIds = groups.stream()
                .map(GroupHint::getGroupTempId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<String> validSectionIds = sections.stream()
                .map(SectionHint::getSectionTempId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (int i = 0; i < regions.size(); i++) {
            RegionPayload r = regions.get(i);
            String rId = r.getRegionId();
            RegionLocks locks = r.getLocks();

            if (locks != null && Boolean.TRUE.equals(locks.getRegionType())
                    && "AUTO_DETECT".equalsIgnoreCase(r.getRegionType())) {
                errors.add(new ValidationError("LOCKED_AUTO_DETECT_CONTRADICTION", "ERROR", List.of(rId),
                        "Vùng " + rId + " đang khóa loại vùng nhưng lại để AUTO_DETECT. Hãy chọn loại cụ thể hoặc mở khóa phân loại."));
            }

            if (r.getBbox() != null) {
                double area = safeDouble(r.getBbox().getWidth()) * safeDouble(r.getBbox().getHeight());
                if (area > 0 && area < 0.0025) {
                    errors.add(new ValidationError("SMALL_CROP_WARNING", "WARNING", List.of(rId),
                            "Vùng " + rId + " quá nhỏ, AI có thể đọc thiếu chữ. Hãy phóng to hoặc vẽ lại sát nội dung hơn."));
                }
                if (area > 0.75) {
                    errors.add(new ValidationError("LARGE_IMAGE_REGION", "WARNING", List.of(rId),
                            "Vùng " + rId + " chiếm gần toàn trang. Nên chia nhỏ để AI không trộn passage, câu hỏi và đáp án."));
                }
            }

            if (Boolean.TRUE.equals(r.getSendImage()) && (r.getAssetRef() == null || r.getAssetRef().isBlank())) {
                errors.add(new ValidationError("MISSING_IMAGE_CROP", "WARNING", List.of(rId),
                        "Vùng " + rId + " chọn gửi ảnh nhưng chưa crop thành công hoặc thiếu file."));
            }

            if (Boolean.TRUE.equals(r.getSendText())
                    && (r.getOcrText() == null || r.getOcrText().isBlank())
                    && !"IMAGE_ASSET".equalsIgnoreCase(r.getRegionType())) {
                errors.add(new ValidationError("EMPTY_OCR_TEXT", "INFO", List.of(rId),
                        "Vùng " + rId + " yêu cầu gửi text nhưng nội dung OCR rỗng."));
            }

            if (Boolean.TRUE.equals(r.getSendText())
                    && r.getOcrText() != null
                    && !r.getOcrText().isBlank()
                    && r.getOcrText().trim().length() < 12) {
                errors.add(new ValidationError("SHORT_OCR_TEXT", "WARNING", List.of(rId),
                        "OCR của vùng " + rId + " rất ngắn. Nên kiểm tra lại crop hoặc bật gửi ảnh để AI đọc trực tiếp."));
            }

            if ("TRANSCRIPT".equalsIgnoreCase(r.getRegionType()) && looksLikeQuestionOrOptions(r.getOcrText())) {
                errors.add(new ValidationError("BAD_TRANSCRIPT_REGION", "ERROR", List.of(rId),
                        "Vùng " + rId + " được gắn TRANSCRIPT nhưng nội dung giống câu hỏi hoặc lựa chọn. Hãy đổi sang QUESTION_BLOCK/OPTIONS hoặc vẽ lại vùng transcript."));
            }

            if ("QUESTION_BLOCK".equalsIgnoreCase(r.getRegionType())
                    && r.getExpectedQuestionFrom() == null
                    && r.getExpectedQuestionTo() == null
                    && (r.getLecturerNote() == null || r.getLecturerNote().isBlank())) {
                errors.add(new ValidationError("QUESTION_BLOCK_MISSING_INFO", "WARNING", List.of(rId),
                        "Vùng câu hỏi " + rId + " chưa có phạm vi số câu hoặc ghi chú để định hướng AI."));
            }

            if (r.getExpectedQuestionFrom() != null && r.getExpectedQuestionTo() != null
                    && r.getExpectedQuestionFrom() > r.getExpectedQuestionTo()) {
                errors.add(new ValidationError("INVALID_QUESTION_RANGE", "ERROR", List.of(rId),
                        "Vùng " + rId + " có câu bắt đầu lớn hơn câu kết thúc ("
                                + r.getExpectedQuestionFrom() + " > " + r.getExpectedQuestionTo() + ")."));
            }

            validateLecturerNote(errors, r, rId);

            if ("IMAGE_ASSET".equalsIgnoreCase(r.getRegionType()) && (r.getAssetRef() == null || r.getAssetRef().isBlank())) {
                errors.add(new ValidationError("IMAGE_ASSET_MISSING_REF", "ERROR", List.of(rId),
                        "Vùng ảnh minh họa " + rId + " chưa được crop hoặc chưa được gán assetRef."));
            }

            if (r.getGroupTempId() != null && !r.getGroupTempId().isBlank() && !validGroupIds.contains(r.getGroupTempId())) {
                errors.add(new ValidationError("INVALID_GROUP_REFERENCE", "WARNING", List.of(rId),
                        "Vùng " + rId + " tham chiếu đến nhóm '" + r.getGroupTempId() + "' không tồn tại trong hints."));
            }
            if (r.getSectionTempId() != null && !r.getSectionTempId().isBlank() && !validSectionIds.contains(r.getSectionTempId())) {
                errors.add(new ValidationError("INVALID_SECTION_REFERENCE", "WARNING", List.of(rId),
                        "Vùng " + rId + " tham chiếu đến phần thi '" + r.getSectionTempId() + "' không tồn tại trong hints."));
            }

            for (int j = i + 1; j < regions.size(); j++) {
                RegionPayload other = regions.get(j);
                if (Objects.equals(r.getPageNumber(), other.getPageNumber())) {
                    double overlap = calculateContainmentOverlap(r.getBbox(), other.getBbox());
                    if (overlap > 0.85) {
                        errors.add(new ValidationError("HIGH_OVERLAP", "WARNING", List.of(rId, other.getRegionId()),
                                "Hai vùng " + rId + " và " + other.getRegionId() + " trên trang " + r.getPageNumber()
                                        + " chồng lấn trên 85%. Hãy xóa một vùng, tách lại crop, hoặc đổi vùng phụ thành IGNORE."));
                    }
                }
            }
        }

        validateGroupRanges(errors, groups);
        return errors;
    }

    private void validateLecturerNote(List<ValidationError> errors, RegionPayload r, String rId) {
        if (r.getLecturerNote() == null || r.getLecturerNote().isBlank()) {
            return;
        }
        String note = r.getLecturerNote().trim();
        if (note.length() > 1000) {
            errors.add(new ValidationError("NOTE_TOO_LONG", "WARNING", List.of(rId),
                    "Ghi chú cho AI của vùng " + rId + " quá dài, vượt 1.000 ký tự."));
        }
        if (isSpamNote(note)) {
            errors.add(new ValidationError("SPAM_NOTE", "WARNING", List.of(rId),
                    "Ghi chú cho AI của vùng " + rId + " có dấu hiệu gõ lặp hoặc không có thông tin hữu ích."));
        }
    }

    private void validateGroupRanges(List<ValidationError> errors, List<GroupHint> groups) {
        for (int i = 0; i < groups.size(); i++) {
            GroupHint g1 = groups.get(i);
            if (g1.getExpectedQuestionFrom() == null || g1.getExpectedQuestionTo() == null) continue;

            for (int j = i + 1; j < groups.size(); j++) {
                GroupHint g2 = groups.get(j);
                if (g2.getExpectedQuestionFrom() == null || g2.getExpectedQuestionTo() == null) continue;

                boolean overlap = g1.getExpectedQuestionFrom() <= g2.getExpectedQuestionTo()
                        && g2.getExpectedQuestionFrom() <= g1.getExpectedQuestionTo();
                if (overlap) {
                    errors.add(new ValidationError("GROUP_RANGE_OVERLAP", "WARNING", List.of(),
                            "Nhóm '" + g1.getGroupTempId() + "' (" + g1.getExpectedQuestionFrom() + "-"
                                    + g1.getExpectedQuestionTo() + ") và nhóm '" + g2.getGroupTempId()
                                    + "' (" + g2.getExpectedQuestionFrom() + "-" + g2.getExpectedQuestionTo()
                                    + ") bị chồng chéo số câu."));
                }
            }
        }
    }

    private boolean isSpamNote(String note) {
        if (note.length() < 5) return false;
        char first = note.charAt(0);
        boolean allSame = true;
        for (int i = 1; i < note.length(); i++) {
            if (note.charAt(i) != first) {
                allSame = false;
                break;
            }
        }
        if (allSame) return true;
        return Pattern.compile("(\\S)\\1{4,}").matcher(note).find();
    }

    private boolean looksLikeQuestionOrOptions(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = text.replaceAll("\\s+", " ");
        int markers = 0;
        if (Pattern.compile("\\b\\d{1,2}\\s*[.)]").matcher(normalized).find()) markers++;
        if (Pattern.compile("[①②③④⑤]").matcher(normalized).find()) markers++;
        if (Pattern.compile("\\b(A|B|C|D)\\s*[.)]").matcher(normalized).find()) markers++;
        if (normalized.contains("?") || normalized.contains("다음") || normalized.contains("고르십시오")) markers++;
        return markers >= 2;
    }

    private double calculateContainmentOverlap(AiDocumentImportRequest.NormalizedBoundingBox b1,
                                               AiDocumentImportRequest.NormalizedBoundingBox b2) {
        if (b1 == null || b2 == null) return 0.0;

        double x1 = safeDouble(b1.getX());
        double y1 = safeDouble(b1.getY());
        double w1 = safeDouble(b1.getWidth());
        double h1 = safeDouble(b1.getHeight());
        double x2 = safeDouble(b2.getX());
        double y2 = safeDouble(b2.getY());
        double w2 = safeDouble(b2.getWidth());
        double h2 = safeDouble(b2.getHeight());

        double xIntersection = Math.max(0, Math.min(x1 + w1, x2 + w2) - Math.max(x1, x2));
        double yIntersection = Math.max(0, Math.min(y1 + h1, y2 + h2) - Math.max(y1, y2));
        double areaIntersection = xIntersection * yIntersection;
        double minArea = Math.min(w1 * h1, w2 * h2);
        if (minArea <= 0.0) return 0.0;
        return areaIntersection / minArea;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
