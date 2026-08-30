package com.ksh.features.admin.users.imports.controller;

import com.ksh.features.admin.users.imports.InvalidRosterFileException;
import com.ksh.features.admin.users.imports.dto.UserImportPayloads;
import com.ksh.features.admin.users.imports.dto.UserImportResult;
import com.ksh.features.admin.users.imports.parser.UserImportTemplateBuilder;
import com.ksh.features.admin.users.imports.service.UserRosterImportService;
import com.ksh.features.admin.users.imports.session.UserImportSession;
import com.ksh.security.KshUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** JSON preview/confirm endpoints used by the Admin Users import dialog. */
@Controller
@RequestMapping("/admin/users/import")
@PreAuthorize("hasAuthority('PERM_user.view') and hasAuthority('PERM_user.create')")
public class AdminUsersImportController {
    private static final Logger log = LoggerFactory.getLogger(AdminUsersImportController.class);
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final UserRosterImportService importService;
    private final UserImportTemplateBuilder templateBuilder;

    public AdminUsersImportController(UserRosterImportService importService,
                                      UserImportTemplateBuilder templateBuilder) {
        this.importService = importService;
        this.templateBuilder = templateBuilder;
    }

    @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal KshUserDetails admin) {
        try {
            UserImportSession session = importService.previewUpload(file, admin.getId());
            return ResponseEntity.ok(UserImportPayloads.preview(session));
        } catch (InvalidRosterFileException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("Admin account import preview failed", ex);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Không thể đọc file import. Vui lòng thử lại."));
        }
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirm(
            @RequestBody(required = false) ConfirmRequest body,
            @AuthenticationPrincipal KshUserDetails admin) {
        UUID id = parse(body);
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu mã phiên import."));
        }
        try {
            UserImportResult result = importService.confirmImport(id, admin.getId());
            return ResponseEntity.ok(UserImportPayloads.result(result));
        } catch (InvalidRosterFileException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("Admin account import confirmation failed", ex);
            return ResponseEntity.internalServerError().body(Map.of("error",
                    "Không thể hoàn tất import. Không có tài khoản nào được tạo; bạn có thể thử lại."));
        }
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        try {
            return ResponseEntity.ok()
                    .contentType(XLSX)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=admin-account-import-template.xlsx")
                    .body(templateBuilder.build());
        } catch (IOException ex) {
            log.error("Admin account import template generation failed", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static UUID parse(ConfirmRequest body) {
        if (body == null || body.sessionId() == null || body.sessionId().isBlank()) return null;
        try {
            return UUID.fromString(body.sessionId().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record ConfirmRequest(String sessionId) {}
}
