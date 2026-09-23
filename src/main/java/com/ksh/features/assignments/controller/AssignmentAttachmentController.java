package com.ksh.features.assignments.controller;

import com.ksh.features.assignments.repository.AssignmentSubmissionRepository;
import com.ksh.features.assignments.service.AssignmentAccessSupport;
import com.ksh.features.assignments.service.AssignmentAttachmentStorage;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Role;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
public class AssignmentAttachmentController {
    private final AssignmentSubmissionRepository submissions;
    private final AssignmentAccessSupport access;
    private final AssignmentAttachmentStorage storage;
    public AssignmentAttachmentController(AssignmentSubmissionRepository submissions, AssignmentAccessSupport access, AssignmentAttachmentStorage storage) {
        this.submissions = submissions; this.access = access; this.storage = storage;
    }

    @GetMapping({"/classes/{classId}/assignments/{assignmentId}/attachments/{submissionId}",
            "/lecturer/classes/{classId}/assignments/{assignmentId}/attachments/{submissionId}"})
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','LEADER','ADMIN')")
    public ResponseEntity<byte[]> download(@PathVariable Long classId, @PathVariable Long assignmentId,
                                          @PathVariable Long submissionId, @AuthenticationPrincipal KshUserDetails user) {
        return serve(classId, assignmentId, submissionId, user, false);
    }

    @GetMapping({"/classes/{classId}/assignments/{assignmentId}/attachments/{submissionId}/preview",
            "/lecturer/classes/{classId}/assignments/{assignmentId}/attachments/{submissionId}/preview"})
    @PreAuthorize("hasAnyRole('STUDENT','LECTURER','LEADER','ADMIN')")
    public ResponseEntity<byte[]> preview(@PathVariable Long classId, @PathVariable Long assignmentId,
                                         @PathVariable Long submissionId, @AuthenticationPrincipal KshUserDetails user) {
        return serve(classId, assignmentId, submissionId, user, true);
    }

    private ResponseEntity<byte[]> serve(Long classId, Long assignmentId, Long submissionId, KshUserDetails user, boolean inline) {
        try {
            if (user.getRole() == Role.STUDENT) access.requireActiveEnrollment(classId, user.getId());
            else access.requireEditableClass(classId, user.getId(), user.getRole());
            access.requireAssignment(classId, assignmentId);
            var sub = submissions.findById(submissionId)
                    .filter(s -> assignmentId.equals(s.getAssignmentId()))
                    .filter(s -> user.getRole() != Role.STUDENT || user.getId().equals(s.getUserId()))
                    .orElseThrow(EntityNotFoundException::new);
            String key = sub.getAttachmentUrl();
            byte[] bytes = storage.read(key, assignmentId, sub.getUserId());
            String ext = key.substring(key.lastIndexOf('.') + 1);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename("bai-nop-" + submissionId + "." + ext).build().toString())
                    .contentType(MediaType.parseMediaType(AssignmentAttachmentStorage.mime(ext))).body(bytes);
        } catch (EntityNotFoundException ex) { return ResponseEntity.notFound().build(); }
        catch (IOException ex) { return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build(); }
    }
}
