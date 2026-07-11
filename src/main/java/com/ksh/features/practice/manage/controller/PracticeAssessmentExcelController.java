package com.ksh.features.practice.manage.controller;

import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.assessment.AssessmentAuthoringCatalogService;
import com.ksh.features.practice.manage.service.PracticeAssessmentExcelService;
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

    public PracticeAssessmentExcelController(PracticeAssessmentExcelService excelService,
                                             AssessmentAuthoringCatalogService catalogService) {
        this.excelService = excelService;
        this.catalogService = catalogService;
    }

    @GetMapping
    public String page(@RequestParam("draftId") Long draftId,
                       @RequestParam("testNo") Integer testNo,
                       @RequestParam("lessonCode") String lessonCode,
                       @AuthenticationPrincipal KshUserDetails user,
                       Model model) {
        PracticeAssessmentExcelService.ExcelImportContext context =
                excelService.requireExcelImportContext(draftId, user.getId(), testNo, lessonCode);
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
                                     @AuthenticationPrincipal KshUserDetails user) {
        try {
            PracticeAssessmentExcelService.ExcelImportContext context =
                    excelService.requireExcelImportContext(draftId, user.getId(), testNo, lessonCode);
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
                                         @AuthenticationPrincipal KshUserDetails user) {
        try {
            PracticeAssessmentExcelService.ExcelImportContext context =
                    excelService.requireExcelImportContext(draftId, user.getId(), testNo, lessonCode);
            PracticeDraft draft = excelService.importDraft(
                    file, context.templateCode(), context.draft().getId(), user.getId(), mediaOverrides);
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
}
