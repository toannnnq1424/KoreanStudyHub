package com.ksh.features.practice.manage.controller;
 
import com.ksh.entities.PracticeSet;
import com.ksh.features.practice.repository.PracticeSetRepository;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import com.ksh.security.KshUserDetails;
import com.ksh.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
 
import java.util.List;
 
@Controller
@RequestMapping("/practice/manage")
@PreAuthorize(Roles.PREAUTH_LECTURER_OR_ABOVE)
public class PracticeManageController {
 
    private final PracticeSetRepository setRepository;
    private final PracticeDraftRepository draftRepository;
    private final com.ksh.features.auth.repository.UserRepository userRepository;
    private final com.ksh.features.practice.manage.service.PracticeDraftService draftService;
    private final com.ksh.features.practice.repository.PracticeEditLogRepository editLogRepository;
    private final com.ksh.features.practice.manage.service.PracticeRevisionService revisionService;
 
    public PracticeManageController(PracticeSetRepository setRepository,
                                    PracticeDraftRepository draftRepository,
                                    com.ksh.features.auth.repository.UserRepository userRepository,
                                    com.ksh.features.practice.manage.service.PracticeDraftService draftService,
                                    com.ksh.features.practice.repository.PracticeEditLogRepository editLogRepository,
                                    com.ksh.features.practice.manage.service.PracticeRevisionService revisionService) {
        this.setRepository = setRepository;
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
        this.draftService = draftService;
        this.editLogRepository = editLogRepository;
        this.revisionService = revisionService;
    }
 
    @GetMapping("/sets/{setId}/edit")
    public String editSet(@org.springframework.web.bind.annotation.PathVariable("setId") Long setId,
                          @AuthenticationPrincipal KshUserDetails user) {
        com.ksh.entities.PracticeDraft draft = draftService.createDraftFromPublishedSet(setId, user.getId());
        return "redirect:/practice/manage/drafts/" + draft.getId();
    }
 
    @GetMapping({"", "/"})
    public String dashboard(@RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "category", required = false) String category,
                            @AuthenticationPrincipal KshUserDetails user,
                            Model model) {
        List<PracticeSet> sets;
        if (status != null && !status.isBlank()) {
            sets = setRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            sets = setRepository.findAll(); // simplified list
        }
        
        long publishedCount = sets.stream()
                .filter(s -> "PUBLISHED".equals(s.getStatus()))
                .count();
 
        // Load author names
        java.util.Map<Long, String> authorsMap = new java.util.HashMap<>();
        for (PracticeSet s : sets) {
            if (s.getCreatedBy() != null && !authorsMap.containsKey(s.getCreatedBy())) {
                userRepository.findById(s.getCreatedBy())
                        .ifPresent(u -> authorsMap.put(s.getCreatedBy(), u.getFullName()));
            }
        }
 
        // Clean up empty drafts for this user on loading dashboard
        try {
            draftService.cleanupEmptyDrafts(user.getId());
        } catch (Exception e) {
            // log and continue
        }

        List<com.ksh.entities.PracticeDraft> drafts = draftRepository.findByOwnerIdOrderByUpdatedAtDesc(user.getId());
 
        model.addAttribute("sets", sets);
        model.addAttribute("drafts", drafts);
        model.addAttribute("publishedCount", publishedCount);
        model.addAttribute("authorsMap", authorsMap);
        model.addAttribute("activeStatus", status != null ? status : "ALL");
        model.addAttribute("activeCategory", category != null ? category : "ALL");
        return "practice/manage/dashboard";
    }

    @GetMapping("/revisions")
    public String revisions(@AuthenticationPrincipal KshUserDetails user, Model model) {
        List<com.ksh.entities.PracticeEditLog> revisions = editLogRepository.findBySetOwnerOrderByEditedAtDesc(user.getId());
        
        java.util.Map<Long, String> setTitles = new java.util.HashMap<>();
        java.util.Map<Long, String> authorsMap = new java.util.HashMap<>();
        for (com.ksh.entities.PracticeEditLog log : revisions) {
            if (!setTitles.containsKey(log.getSetId())) {
                setRepository.findById(log.getSetId())
                        .ifPresent(s -> setTitles.put(log.getSetId(), s.getTitle()));
            }
            if (log.getEditedBy() != null && !authorsMap.containsKey(log.getEditedBy())) {
                userRepository.findById(log.getEditedBy())
                        .ifPresent(u -> authorsMap.put(log.getEditedBy(), u.getFullName()));
            }
        }
        
        model.addAttribute("revisions", revisions);
        model.addAttribute("setTitles", setTitles);
        model.addAttribute("authorsMap", authorsMap);
        return "practice/manage/revisions";
    }

    @org.springframework.web.bind.annotation.PostMapping("/revisions/{logId}/restore")
    public String restoreRevision(@org.springframework.web.bind.annotation.PathVariable("logId") Long logId,
                                  @AuthenticationPrincipal KshUserDetails user,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            revisionService.restoreRevision(logId, user.getId());
            redirectAttributes.addFlashAttribute("success", "Khôi phục phiên bản lịch sử thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khôi phục: " + e.getMessage());
        }
        return "redirect:/practice/manage/revisions";
    }
}
