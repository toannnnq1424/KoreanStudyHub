package com.ksh.features.practice.manage.service;

import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.TargetRoute;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds bounded immutable authoring requests without a PDF session or object. */
@Service
public class PracticePdfAiPayloadBuilder {

    static final long MAX_PDF_BYTES = 20L * 1024L * 1024L;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final PracticePdfAiLimits limits;

    public PracticePdfAiPayloadBuilder(PracticePdfAiLimits limits) {
        this.limits = limits;
    }

    public PracticePdfAuthoringRequest buildBasicText(
            String sourceText,
            SourceOperation operation,
            String lecturerRequest,
            TargetRoute target) {
        String normalized = PracticePdfAuthoringRequest.normalize(sourceText);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Vui lòng dán nội dung cần biên soạn.");
        }
        if (normalized.length() > limits.maxTextCharacters()) {
            throw new IllegalArgumentException(
                    "Nội dung Text vượt ngân sách ký tự an toàn.");
        }
        PracticePdfAuthoringRequest.SourceEvidence evidence =
                new PracticePdfAuthoringRequest.SourceEvidence(
                        "TEXT_SPAN", "text-1", null,
                        normalized.length(), normalized);
        List<PracticePdfAuthoringRequest.SourceEvidence> evidenceList =
                List.of(evidence);
        String digest = digest(normalized.getBytes(StandardCharsets.UTF_8));
        return new PracticePdfAuthoringRequest(
                PracticePdfAuthoringRequest.SourceType.TEXT,
                operation,
                "Pasted text",
                digest,
                target,
                lecturerRequest,
                evidenceList,
                sourceContext("BASIC_TEXT", target, evidenceList, digest, null, null),
                List.of());
    }

    public PracticePdfAuthoringRequest buildBasicPdf(
            MultipartFile file,
            Integer requestedStartPage,
            Integer requestedEndPage,
            SourceOperation operation,
            String lecturerRequest,
            TargetRoute target) {
        requirePdfMetadata(file);
        byte[] bytes = null;
        try {
            bytes = readBounded(file);
            requirePdfHeader(bytes);
            String sourceDigest = digest(bytes);
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted()) {
                    throw new IllegalArgumentException(
                            "PDF được mã hóa không được hỗ trợ.");
                }
                int totalPages = document.getNumberOfPages();
                int startPage = requestedStartPage == null ? 1 : requestedStartPage;
                int endPage = requestedEndPage == null ? totalPages : requestedEndPage;
                requirePageRange(startPage, endPage, totalPages);
                List<PracticePdfAuthoringRequest.SourceEvidence> evidence =
                        extractEvidence(document, startPage, endPage);
                String sourceName = safeSourceName(file.getOriginalFilename());
                return new PracticePdfAuthoringRequest(
                        PracticePdfAuthoringRequest.SourceType.PDF,
                        operation,
                        sourceName,
                        sourceDigest,
                        target,
                        lecturerRequest,
                        evidence,
                        sourceContext(
                                "BASIC_PDF", target, evidence, sourceDigest,
                                totalPages, Map.of(
                                        "filename", sourceName,
                                        "mimeType", "application/pdf",
                                        "byteLength", bytes.length,
                                        "startPage", startPage,
                                        "endPage", endPage)),
                        List.of());
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new IllegalArgumentException(
                        "Không thể đọc cấu trúc PDF hợp lệ.", exception);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc tệp PDF.", exception);
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    private List<PracticePdfAuthoringRequest.SourceEvidence> extractEvidence(
            PDDocument document,
            int startPage,
            int endPage) throws IOException {
        List<PracticePdfAuthoringRequest.SourceEvidence> evidence = new ArrayList<>();
        int totalCharacters = 0;
        PDFTextStripper stripper = new PDFTextStripper();
        for (int page = startPage; page <= endPage; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String text = PracticePdfAuthoringRequest.normalize(
                    stripper.getText(document));
            if (text.length() > limits.maxTextCharacters() - totalCharacters) {
                throw new IllegalArgumentException(
                        "Nội dung PDF vượt ngân sách ký tự an toàn.");
            }
            totalCharacters += text.length();
            evidence.add(new PracticePdfAuthoringRequest.SourceEvidence(
                    "PAGE", "page-" + page, page, text.length(), text));
        }
        if (totalCharacters == 0) {
            throw new IllegalArgumentException(
                    "Các trang đã chọn không có text để biên soạn.");
        }
        return List.copyOf(evidence);
    }

    private void requirePageRange(int startPage, int endPage, int totalPages) {
        if (totalPages < 1 || startPage < 1 || endPage < startPage
                || endPage > totalPages
                || endPage - startPage + 1 > limits.maxSelectedPages()) {
            throw new IllegalArgumentException(
                    "Phạm vi trang PDF vượt ngân sách xử lý an toàn.");
        }
    }

    private static void requirePdfMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file PDF.");
        }
        if (file.getSize() < PDF_HEADER.length || file.getSize() > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("PDF phải có dung lượng tối đa 20 MiB.");
        }
        String contentType = file.getContentType();
        String normalizedType = contentType == null ? ""
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String sourceName = safeSourceName(file.getOriginalFilename());
        if (!"application/pdf".equals(normalizedType)
                || !sourceName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận tệp có type và phần mở rộng PDF.");
        }
    }

    private static byte[] readBounded(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes((int) MAX_PDF_BYTES + 1);
            if (bytes.length > MAX_PDF_BYTES) {
                Arrays.fill(bytes, (byte) 0);
                throw new IllegalArgumentException(
                        "PDF phải có dung lượng tối đa 20 MiB.");
            }
            return bytes;
        }
    }

    private static void requirePdfHeader(byte[] bytes) {
        if (bytes.length < PDF_HEADER.length) {
            throw new IllegalArgumentException("Tệp không có PDF header hợp lệ.");
        }
        for (int index = 0; index < PDF_HEADER.length; index++) {
            if (bytes[index] != PDF_HEADER[index]) {
                throw new IllegalArgumentException("Tệp không có PDF header hợp lệ.");
            }
        }
    }

    private static Map<String, Object> sourceContext(
            String mode,
            TargetRoute target,
            List<PracticePdfAuthoringRequest.SourceEvidence> evidence,
            String digest,
            Integer totalPages,
            Map<String, Object> document) {
        List<Map<String, Object>> blocks = evidence.stream()
                .map(item -> {
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("kind", item.kind());
                    block.put("sourceId", item.sourceId());
                    if (item.pageNumber() != null) {
                        block.put("pageNumber", item.pageNumber());
                    }
                    block.put("textLength", item.textLength());
                    block.put("untrustedText", item.untrustedText());
                    return Map.copyOf(block);
                })
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mode", mode);
        context.put("sourceDigest", digest);
        context.put("evidenceDigest", digestEvidence(evidence));
        context.put("target", Map.of(
                "draftId", target.draftId(),
                "testNo", target.testNo(),
                "skill", target.skill(),
                "lessonCode", target.lessonCode()));
        context.put("evidence", blocks);
        if (totalPages != null) context.put("totalPages", totalPages);
        if (document != null && !document.isEmpty()) {
            context.put("document", Map.copyOf(document));
        }
        return Map.copyOf(context);
    }

    private static String safeSourceName(String raw) {
        String value = raw == null ? "" : raw.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = PracticePdfAuthoringRequest.normalize(value);
        if (value.isBlank()) value = "source.pdf";
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private static String digest(byte[] value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String digestEvidence(
            List<PracticePdfAuthoringRequest.SourceEvidence> evidence) {
        StringBuilder canonical = new StringBuilder();
        for (PracticePdfAuthoringRequest.SourceEvidence item : evidence) {
            canonical.append(item.kind()).append('\u001f')
                    .append(item.sourceId()).append('\u001f')
                    .append(item.pageNumber() == null ? "" : item.pageNumber())
                    .append('\u001f').append(item.textLength()).append('\u001f')
                    .append(item.untrustedText()).append('\u001e');
        }
        return digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
}
