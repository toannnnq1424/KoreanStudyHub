package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeDraft;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.pdf.PracticePdfStorageService;
import com.ksh.features.practice.repository.PracticeAiRequestAuditRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticePdfPageExtractionRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PracticePdfImportSessionService {

    private static final Logger log = LoggerFactory.getLogger(PracticePdfImportSessionService.class);

    private final PracticePdfImportSessionRepository sessionRepository;
    private final PracticePdfRegionAnnotationRepository annotationRepository;
    private final PracticePdfPageExtractionRepository pageExtractionRepository;
    private final PracticeAiRequestAuditRepository aiRequestAuditRepository;
    private final PracticeDraftRepository draftRepository;
    private final PracticePdfStorageService storageService;
    private final LecturerAssetService assetService;
    private final AssessmentAuthoringCatalogService authoringCatalogService;
    private final ObjectMapper objectMapper;
    private final PracticeAuthorizationService authorizationService;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticePdfImportSessionService(PracticePdfImportSessionRepository sessionRepository,
                                           PracticePdfRegionAnnotationRepository annotationRepository,
                                           PracticePdfPageExtractionRepository pageExtractionRepository,
                                           PracticeAiRequestAuditRepository aiRequestAuditRepository,
                                           PracticeDraftRepository draftRepository,
                                           PracticePdfStorageService storageService,
                                           LecturerAssetService assetService,
                                           AssessmentAuthoringCatalogService authoringCatalogService,
                                           ObjectMapper objectMapper,
                                           PracticeAuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.annotationRepository = annotationRepository;
        this.pageExtractionRepository = pageExtractionRepository;
        this.aiRequestAuditRepository = aiRequestAuditRepository;
        this.draftRepository = draftRepository;
        this.storageService = storageService;
        this.assetService = assetService;
        this.authoringCatalogService = authoringCatalogService;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
    }

    public PracticePdfImportSessionService(PracticePdfImportSessionRepository sessionRepository,
                                           PracticePdfRegionAnnotationRepository annotationRepository,
                                           PracticePdfPageExtractionRepository pageExtractionRepository,
                                           PracticeAiRequestAuditRepository aiRequestAuditRepository,
                                           PracticeDraftRepository draftRepository,
                                           PracticePdfStorageService storageService,
                                           LecturerAssetService assetService,
                                           AssessmentAuthoringCatalogService authoringCatalogService,
                                           ObjectMapper objectMapper) {
        this(sessionRepository, annotationRepository, pageExtractionRepository,
                aiRequestAuditRepository, draftRepository, storageService, assetService,
                authoringCatalogService, objectMapper, null);
    }

    @Transactional
    public PracticePdfImportSession createSession(Long uploaderId, MultipartFile file,
                                                  String examTemplateCode, String title,
                                                  Long linkedDraftId) throws IOException {
        return createSession(uploaderId, file, examTemplateCode, title, linkedDraftId,
                null, null, null);
    }

    @Transactional
    public PracticePdfImportSession createSession(Long uploaderId, MultipartFile file,
                                                  String requestedTemplateCode, String title,
                                                  Long linkedDraftId, Integer requestedTestNo,
                                                  String requestedSkill, String requestedLessonCode) throws IOException {
        PracticeDraft linkedDraft = linkedDraftId == null ? null
                : authorizedDraft(linkedDraftId, uploaderId);

        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = resolveTemplate(
                requestedTemplateCode, linkedDraft);
        TargetSectionOption target = linkedDraft == null
                ? standaloneTarget(template, requestedTestNo, requestedSkill)
                : linkedTarget(linkedDraft, requestedTestNo, requestedLessonCode);
        template.requireSkill(target.skill());

        PracticePdfStorageService.StoredPdf storedPdf = storageService.store(file, uploaderId);

        int totalPages = 1;
        try (PDDocument doc = Loader.loadPDF(storedPdf.absolutePath().toFile())) {
            totalPages = doc.getNumberOfPages();
        } catch (Exception e) {
            log.error("[PdfImportSessionService] Failed to read PDF page count", e);
        }

        String finalTitle = (title != null && !title.isBlank()) ? title : file.getOriginalFilename().replaceFirst("(?i)\\.pdf$", "");

        PracticePdfImportSession session = new PracticePdfImportSession(
                uploaderId,
                file.getOriginalFilename(),
                storedPdf.absolutePath().toString(),
                totalPages,
                "UPLOADED",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)
        );
        session.setCreatedBy(uploaderId);
        session.setTitle(finalTitle);
        session.setExamCategory(template.categoryCode());
        session.setAssessmentProgramCode(template.programCode());
        session.setAssessmentProgramVersionId(template.programVersionId());
        session.setExamTemplateCode(template.code());
        session.setTargetTestNo(target.testNo());
        session.setTargetSkill(target.skill());
        session.setTargetLessonCode(target.lessonCode());
        session.setLinkedDraftId(linkedDraftId);
        session.setExtractionStrategy("FULL_SELECTED_PAGES");

        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public PdfImportStartContext resolveStartContext(Long draftId, Integer requestedTestNo,
                                                     String requestedLessonCode, Long ownerId) {
        if (draftId == null) return null;
        PracticeDraft draft = authorizedDraft(draftId, ownerId);
        List<TargetSectionOption> sections = readTargetSections(draft);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Bản nháp chưa có phần kỹ năng để nhập PDF.");
        }
        TargetSectionOption selected = sections.stream()
                .filter(section -> requestedTestNo != null && requestedTestNo.equals(section.testNo())
                        && requestedLessonCode != null
                        && requestedLessonCode.equalsIgnoreCase(section.lessonCode()))
                .findFirst()
                .orElse(sections.get(0));
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template = resolveTemplate(null, draft);
        template.requireSkill(selected.skill());
        return new PdfImportStartContext(template.code(), template.programCode(),
                template.programVersionId(), selected, sections);
    }

    private AssessmentAuthoringCatalogService.ExamTemplatePolicy resolveTemplate(
            String requestedTemplateCode, PracticeDraft linkedDraft) {
        String linkedTemplateCode = linkedDraft == null ? null : linkedDraft.getExamTemplateCode();
        if ((linkedTemplateCode == null || linkedTemplateCode.isBlank()) && linkedDraft != null) {
            linkedTemplateCode = documentText(linkedDraft, "examTemplateCode");
        }
        String templateCode = linkedTemplateCode;
        if (templateCode == null || templateCode.isBlank()) {
            templateCode = requestedTemplateCode;
        }
        AssessmentAuthoringCatalogService.ExamTemplatePolicy template;
        try {
            template = authoringCatalogService.requireTemplate(templateCode);
        } catch (IllegalArgumentException exception) {
            template = authoringCatalogService.requireTemplate(
                    AssessmentAuthoringCatalogService.defaultTemplateForCategory(templateCode));
        }
        if (linkedTemplateCode != null && requestedTemplateCode != null
                && !requestedTemplateCode.isBlank()
                && !template.code().equalsIgnoreCase(requestedTemplateCode)) {
            throw new IllegalArgumentException("Mẫu đề PDF phải trùng với bản nháp đang biên soạn.");
        }
        return template;
    }

    private TargetSectionOption linkedTarget(PracticeDraft draft, Integer requestedTestNo,
                                             String requestedLessonCode) {
        List<TargetSectionOption> sections = readTargetSections(draft);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Bản nháp chưa có phần kỹ năng để nhập PDF.");
        }
        if (requestedTestNo == null || requestedLessonCode == null || requestedLessonCode.isBlank()) {
            return sections.get(0);
        }
        return sections.stream()
                .filter(section -> requestedTestNo.equals(section.testNo())
                        && requestedLessonCode.equalsIgnoreCase(section.lessonCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy phần thi đích trong bản nháp."));
    }

    private TargetSectionOption standaloneTarget(
            AssessmentAuthoringCatalogService.ExamTemplatePolicy template,
            Integer requestedTestNo, String requestedSkill) {
        int testNo = requestedTestNo == null ? 1 : requestedTestNo;
        if (testNo <= 0 || testNo > template.maxTests()) {
            throw new IllegalArgumentException("Số Test nằm ngoài giới hạn của mẫu đề.");
        }
        String skill = normalizeSkill(requestedSkill);
        if (skill == null) {
            skill = List.of("READING", "LISTENING", "WRITING", "SPEAKING").stream()
                    .filter(template.skills()::containsKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Mẫu đề chưa bật kỹ năng nào."));
        }
        template.requireSkill(skill);
        return new TargetSectionOption(testNo, lessonCode(skill, testNo), skill,
                skillLabel(skill));
    }

    private List<TargetSectionOption> readTargetSections(PracticeDraft draft) {
        try {
            JsonNode root = objectMapper.readTree(draft.getDraftJson());
            List<TargetSectionOption> result = new ArrayList<>();
            for (JsonNode section : root.path("sections")) {
                int testNo = section.path("testNo").asInt(testNoFromLesson(
                        section.path("lessonCode").asText("")));
                String skill = normalizeSkill(section.path("skill").asText(""));
                if (testNo <= 0 || skill == null) continue;
                String code = section.path("lessonCode").asText(lessonCode(skill, testNo));
                String title = section.path("title").asText(skillLabel(skill));
                result.add(new TargetSectionOption(testNo, code, skill, title));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc cấu trúc bản nháp hiện tại.", exception);
        }
    }

    private String documentText(PracticeDraft draft, String field) {
        try {
            return objectMapper.readTree(draft.getDraftJson()).path("document").path(field).asText(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private PracticeDraft authorizedDraft(Long draftId, Long actorId) {
        if (authorizationService == null) {
            return draftRepository.findByIdAndOwnerId(draftId, actorId)
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                            "Bản nháp liên kết không tồn tại."));
        }
        authorizationService.requireDraft(draftId, actorId, PracticeAction.EDIT, null);
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Bản nháp liên kết không tồn tại."));
    }

    private static String normalizeSkill(String value) {
        if (value == null || value.isBlank()) return null;
        String skill = value.trim().toUpperCase(Locale.ROOT);
        return List.of("READING", "LISTENING", "WRITING", "SPEAKING").contains(skill)
                ? skill : null;
    }

    private static String lessonCode(String skill, int testNo) {
        return switch (skill) {
            case "LISTENING" -> "L" + testNo;
            case "WRITING" -> "W" + testNo;
            case "SPEAKING" -> "S" + testNo;
            default -> "R" + testNo;
        };
    }

    private static int testNoFromLesson(String lessonCode) {
        if (lessonCode == null || !lessonCode.trim().toUpperCase(Locale.ROOT).matches("[LRWS]\\d+")) {
            return 0;
        }
        return Integer.parseInt(lessonCode.trim().substring(1));
    }

    private static String skillLabel(String skill) {
        return switch (skill) {
            case "LISTENING" -> "Phần Nghe";
            case "WRITING" -> "Phần Viết";
            case "SPEAKING" -> "Phần Nói";
            default -> "Phần Đọc";
        };
    }

    public record TargetSectionOption(Integer testNo, String lessonCode, String skill, String title) {
    }

    public record PdfImportStartContext(String templateCode, String programCode,
                                        Long programVersionId, TargetSectionOption selected,
                                        List<TargetSectionOption> sections) {
        public PdfImportStartContext {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }
    }



    public PracticePdfImportSession getSession(Long id, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập session này.");
        }
        return session;
    }

    @Transactional
    public PracticePdfImportSession updatePageRange(Long id, Integer startPage, Integer endPage, Long userId) {
        PracticePdfImportSession session = getSession(id, userId);
        if (startPage == null || startPage <= 0) startPage = 1;
        if (endPage == null || endPage > session.getTotalPages()) endPage = session.getTotalPages();
        if (startPage > endPage) {
            throw new IllegalArgumentException("Trang bắt đầu không thể lớn hơn trang kết thúc.");
        }
        session.setSelectedStartPage(startPage);
        session.setSelectedEndPage(endPage);
        session.setStatus("ANNOTATING");
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public PracticePdfImportSession saveState(Long id, Integer currentPage, Integer startPage, Integer endPage, String strategy, Long userId) {
        PracticePdfImportSession session = getSession(id, userId);
        if (currentPage != null && currentPage > 0 && currentPage <= session.getTotalPages()) {
            session.setCurrentPage(currentPage);
        }
        if (startPage != null && endPage != null && startPage <= endPage) {
            session.setSelectedStartPage(startPage);
            session.setSelectedEndPage(endPage);
        }
        if (strategy != null) {
            String normalizedStrategy = strategy.trim().toUpperCase();
            if (!List.of("HYBRID", "REGION_ONLY", "FULL_SELECTED_PAGES").contains(normalizedStrategy)) {
                throw new IllegalArgumentException("Chiến lược gửi AI không hợp lệ.");
            }
            session.setExtractionStrategy(normalizedStrategy);
        }
        session.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        PracticePdfImportSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        session.setStatus(status);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void updateDraftId(Long id, Long draftId) {
        PracticePdfImportSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        session.setLinkedDraftId(draftId);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        PracticePdfImportSession session = getSession(sessionId, userId);
        
        // 1. Delete actual PDF file
        if (session.getStoredPdfPath() != null) {
            File file = new File(session.getStoredPdfPath());
            if (file.exists() && file.delete()) {
                log.info("[PdfImportSessionService] Deleted PDF for sessionId={}", sessionId);
            }
        }

        // 2. Cleanup temporary and active assets
        assetService.cleanupTemporaryAssets(sessionId, userId);
        
        // 3. Delete cascades in db (managed by hibernate or query)
        annotationRepository.deleteBySessionId(sessionId);
        pageExtractionRepository.deleteBySessionId(sessionId);
        aiRequestAuditRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
        log.info("[PdfImportSessionService] Deleted import session id={}", sessionId);
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("[PdfImportSessionService] Running cleanup of expired import sessions");
        List<PracticePdfImportSession> expired = sessionRepository.findByExpiresAtBefore(LocalDateTime.now());
        for (PracticePdfImportSession session : expired) {
            try {
                deleteSession(session.getId(), session.getUploaderId());
            } catch (Exception e) {
                log.error("[PdfImportSessionService] Failed to clean up expired session id={}", session.getId(), e);
            }
        }
    }
}
