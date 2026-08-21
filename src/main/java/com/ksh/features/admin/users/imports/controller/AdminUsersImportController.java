package com.ksh.features.admin.users.imports.controller;

import com.ksh.features.admin.users.imports.InvalidRosterFileException;
import com.ksh.features.admin.users.imports.dto.UserImportPayloads;
import com.ksh.features.admin.users.imports.dto.UserImportResult;
import com.ksh.features.admin.users.imports.parser.UserImportTemplateBuilder;
import com.ksh.features.admin.users.imports.service.UserRosterImportService;
import com.ksh.features.admin.users.imports.session.UserImportSession;
import com.ksh.security.Roles;
import com.ksh.security.KshUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import static com.ksh.common.IConstant.MSG_ROSTER_IMPORT_CONFIRM_FAILED;
import static com.ksh.common.IConstant.MSG_ROSTER_IMPORT_READ_FAILED;
import static com.ksh.common.IConstant.PATH_IMPORT_CONFIRM;
import static com.ksh.common.IConstant.PATH_IMPORT_TEMPLATE;
import static com.ksh.common.IConstant.PATH_IMPORT_UPLOAD;
import static com.ksh.common.IConstant.URL_ADMIN_USERS_IMPORT;

/**
 * Controller for the admin roster-import modal on {@code /admin/users}.
 *
 * <p>Three endpoints under {@code /admin/users/import}:
 * <ul>
 *   <li>{@code POST /upload}   — multipart upload; returns the preview rows and
 *       writes nothing.</li>
 *   <li>{@code POST /confirm}  — creates the accounts for the staged preview.</li>
 *   <li>{@code GET  /template} — streams a blank .xlsx with the expected
 *       columns.</li>
 * </ul>
 *
 * <p>There is no page handler: the import UI is a {@code <dialog>} rendered by
 * {@code admin/users.html}, so the admin never leaves the list.
 *
 * <p>Authorization is layered. Layer 1: the {@code /admin/**} matcher in
 * {@code SecurityConfig} plus the class-level {@link PreAuthorize} restrict
 * every endpoint to {@code ADMIN}. Layer 2: the session store rejects a confirm
 * whose staged preview belongs to a different administrator, so the role gate
 * alone cannot be used to replay a peer's upload.
 */
@Controller
@RequestMapping(URL_ADMIN_USERS_IMPORT)
@PreAuthorize("hasRole('" + Roles.ADMIN + "')")
public class AdminUsersImportController {

    private static final Logger log = LoggerFactory.getLogger(AdminUsersImportController.class);

    /** JSON envelope key for error payloads, matching the other import screens. */
    private static final String JSON_KEY_ERROR = "error";

    private static final String XLSX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TEMPLATE_FILENAME = "mau-import-tai-khoan.xlsx";
    private static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";

    private final UserRosterImportService importService;
    private final UserImportTemplateBuilder templateBuilder;

    public AdminUsersImportController(UserRosterImportService importService,
                                      UserImportTemplateBuilder templateBuilder) {
        this.importService = importService;
        this.templateBuilder = templateBuilder;
    }

    /** Stages a preview of the uploaded roster. Creates no account. */
    @PostMapping(value = PATH_IMPORT_UPLOAD, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @AuthenticationPrincipal KshUserDetails admin) {
        try {
            UserImportSession session = importService.previewUpload(file, admin.getId());
            return ResponseEntity.ok(UserImportPayloads.preview(session));
        } catch (InvalidRosterFileException ex) {
            return badRequest(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected failure while previewing a roster import", ex);
            return internalError(MSG_ROSTER_IMPORT_READ_FAILED);
        }
    }

    /** Creates one account per creatable row of a previously staged preview. */
    @PostMapping(value = PATH_IMPORT_CONFIRM,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirm(@RequestBody(required = false) ConfirmRequest body,
                                                       @AuthenticationPrincipal KshUserDetails admin) {
        UUID sessionId = parseSessionId(body);
        if (sessionId == null) {
            return badRequest("Thiếu mã phiên import.");
        }
        try {
            UserImportResult result = importService.confirmImport(sessionId, admin.getId());
            return ResponseEntity.ok(UserImportPayloads.result(result));
        } catch (InvalidRosterFileException ex) {
            return badRequest(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Unexpected failure while confirming a roster import", ex);
            return internalError(MSG_ROSTER_IMPORT_CONFIRM_FAILED);
        }
    }

    /**
     * Streams a small .xlsx carrying the expected header row plus sample rows,
     * generated on the fly so no binary asset lives in the repository.
     */
    @GetMapping(PATH_IMPORT_TEMPLATE)
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            byte[] bytes = templateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(XLSX_MIME_TYPE));
            headers.setContentDispositionFormData(CONTENT_DISPOSITION_ATTACHMENT, TEMPLATE_FILENAME);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (IOException ex) {
            log.error("Failed to generate the roster import template", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Returns {@code null} for a missing or unparsable session id. */
    private static UUID parseSessionId(ConfirmRequest body) {
        if (body == null || body.sessionId() == null || body.sessionId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(body.sessionId().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(JSON_KEY_ERROR, message));
    }

    private static ResponseEntity<Map<String, Object>> internalError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(JSON_KEY_ERROR, message));
    }

    /** JSON body of the confirm endpoint. */
    public record ConfirmRequest(String sessionId) {}
}