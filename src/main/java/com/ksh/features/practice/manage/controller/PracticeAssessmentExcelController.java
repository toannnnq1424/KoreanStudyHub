package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
import com.ksh.features.practice.manage.service.PracticeOverrideContextService;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Controller
@RequestMapping("/practice/manage/excel")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeAssessmentExcelController {

    private static final Logger log = LoggerFactory.getLogger(PracticeAssessmentExcelController.class);

    private final PracticeAssessmentExcelService excelService;
    private final AssessmentAuthoringCatalogService catalogService;
    private final PracticeOverrideContextService overrideContextService;

    @org.springframework.beans.factory.annotation.Autowired
    public PracticeAssessmentExcelController(PracticeAssessmentExcelService excelService,
                                             AssessmentAuthoringCatalogService catalogService,
                                             PracticeOverrideContextService overrideContextService) {
        this.excelService = excelService;
        this.catalogService = catalogService;
        this.overrideContextService = overrideContextService;
    }

    public PracticeAssessmentExcelController(PracticeAssessmentExcelService excelService,
                                             AssessmentAuthoringCatalogService catalogService) {
        this(excelService, catalogService, null);
    }

    @GetMapping
    public String page(@RequestParam("draftId") Long draftId,
                       @RequestParam("testNo") Integer testNo,
                       @RequestParam("lessonCode") String lessonCode,
                       @AuthenticationPrincipal KshUserDetails user,
                       jakarta.servlet.http.HttpSession session,
                       Model model) {
        String overrideReason = overrideReason(session, draftId, null);
        PracticeAssessmentExcelService.ExcelImportContext context =
                excelService.requireExcelImportContext(
                        draftId, user.getId(), testNo, lessonCode, overrideReason);
        model.addAttribute("draftId", context.draft().getId());
        model.addAttribute("testNo", context.testNo());
        model.addAttribute("lessonCode", context.lessonCode());
        model.addAttribute("selectedTemplateCode", context.templateCode());
        model.addAttribute("authoringCatalog", catalogService.catalog());
        return "practice/manage/excel-import";
    }

    @GetMapping("/templates/{templateCode}")
    public ResponseEntity<byte[]> template(@PathVariable String templateCode) {
        byte[] bytes = excelService.buildTemplate(templateCode);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=practice-" + templateCode.toLowerCase() + "-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file,
                                     @RequestParam("templateCode") String templateCode,
                                     @RequestParam("draftId") Long draftId,
                                     @RequestParam("testNo") Integer testNo,
                                     @RequestParam("lessonCode") String lessonCode,
                                     @RequestParam(value = "overrideReason", required = false)
                                     String explicitOverrideReason,
                                     @AuthenticationPrincipal KshUserDetails user,
                                     jakarta.servlet.http.HttpSession session) {
        try {
            String overrideReason = overrideReason(
                    session, draftId, explicitOverrideReason);
            PracticeAssessmentExcelService.ExcelImportContext context =
                    overrideReason == null
                            ? excelService.requireExcelImportContext(
                                    draftId, user.getId(), testNo, lessonCode)
                            : excelService.requireExcelImportContext(
                                    draftId, user.getId(), testNo, lessonCode,
                                    overrideReason);
            return ResponseEntity.ok(excelService.preview(file, context.templateCode()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (EntityNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Bản nháp liên kết không tồn tại."));
        } catch (AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền nhập dữ liệu vào bản nháp này."));
        } catch (RuntimeException exception) {
            log.error("Excel preview failed for draft {}", draftId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Không thể xem trước file Excel. Bản nháp chưa bị thay đổi."));
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> importDraft(@RequestParam("file") MultipartFile file,
                                         @RequestParam("templateCode") String templateCode,
                                         @RequestParam("draftId") Long draftId,
                                         @RequestParam("testNo") Integer testNo,
                                         @RequestParam("lessonCode") String lessonCode,
                                         @RequestParam(value = "mediaOverrides", required = false) String mediaOverrides,
                                         @RequestParam(value = "overrideReason", required = false)
                                         String explicitOverrideReason,
                                         @AuthenticationPrincipal KshUserDetails user,
                                         jakarta.servlet.http.HttpSession session) {
        try {
            String overrideReason = overrideReason(
                    session, draftId, explicitOverrideReason);
            PracticeAssessmentExcelService.ExcelImportContext context =
                    overrideReason == null
                            ? excelService.requireExcelImportContext(
                                    draftId, user.getId(), testNo, lessonCode)
                            : excelService.requireExcelImportContext(
                                    draftId, user.getId(), testNo, lessonCode,
                                    overrideReason);
            PracticeDraft draft = overrideReason == null
                    ? excelService.importDraft(file, context.templateCode(),
                            context.draft().getId(), user.getId(), mediaOverrides)
                    : excelService.importDraft(file, context.templateCode(),
                            context.draft().getId(), user.getId(), mediaOverrides,
                            overrideReason);
            return ResponseEntity.ok(Map.of(
                    "draftId", draft.getId(),
                    "redirectUrl", "/practice/manage/drafts/" + draft.getId()
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (EntityNotFoundException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Bản nháp liên kết không tồn tại."));
        } catch (AccessDeniedException exception) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Bạn không có quyền nhập dữ liệu vào bản nháp này."));
        } catch (RuntimeException exception) {
            log.error("Excel import failed for draft {}", draftId, exception);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Không thể hoàn tất nhập Excel. Bản nháp chưa bị thay đổi."));
        }
    }

    public ResponseEntity<?> preview(MultipartFile file, String templateCode,
                                     Long draftId, Integer testNo, String lessonCode,
                                     KshUserDetails user) {
        return preview(file, templateCode, draftId, testNo, lessonCode,
                null, user, null);
    }

    public ResponseEntity<?> importDraft(MultipartFile file, String templateCode,
                                         Long draftId, Integer testNo, String lessonCode,
                                         String mediaOverrides, KshUserDetails user) {
        return importDraft(file, templateCode, draftId, testNo, lessonCode,
                mediaOverrides, null, user, null);
    }

    private String overrideReason(jakarta.servlet.http.HttpSession session,
                                  Long draftId, String explicitReason) {
        if (overrideContextService == null || session == null) {
            return explicitReason;
        }
        if (explicitReason != null && !explicitReason.isBlank()) {
            overrideContextService.establishForDraft(session, draftId, explicitReason);
        }
        return overrideContextService.reasonForDraft(session, draftId, explicitReason);
    }
}
