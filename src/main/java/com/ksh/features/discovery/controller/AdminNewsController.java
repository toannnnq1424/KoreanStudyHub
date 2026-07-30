package com.ksh.features.discovery.controller;

import com.ksh.features.discovery.ingestion.NewsIngestionOrchestrator;
import com.ksh.features.discovery.service.AdminNewsService;
import com.ksh.features.discovery.service.DiscoveryDictionarySettingsService;
import com.ksh.security.KshUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;

@Controller
@RequestMapping("/admin/news")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNewsController {

    private final AdminNewsService adminNewsService;
    private final NewsIngestionOrchestrator orchestrator;
    private final DiscoveryDictionarySettingsService dictionarySettingsService;
    private final boolean rawPreviewEnabled;
    private final String environmentDictionaryApiKey;

    @Autowired
    public AdminNewsController(
            AdminNewsService adminNewsService,
            NewsIngestionOrchestrator orchestrator,
            DiscoveryDictionarySettingsService dictionarySettingsService,
            @Value("${app.news.raw-preview-enabled:false}") boolean rawPreviewEnabled,
            @Value("${app.news.dictionary.api-key:}") String environmentDictionaryApiKey
    ) {
        this.adminNewsService = adminNewsService;
        this.orchestrator = orchestrator;
        this.dictionarySettingsService = dictionarySettingsService;
        this.rawPreviewEnabled = rawPreviewEnabled;
        this.environmentDictionaryApiKey = environmentDictionaryApiKey;
    }

    AdminNewsController(
            AdminNewsService adminNewsService,
            NewsIngestionOrchestrator orchestrator,
            boolean rawPreviewEnabled
    ) {
        this(adminNewsService, orchestrator, null, rawPreviewEnabled, "");
    }

    @GetMapping
    public String index(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "runId", required = false) Long runId,
            @RequestParam(name = "ai", required = false) String aiStatus,
            Model model
    ) {
        model.addAttribute("overview", adminNewsService.overview(page, runId, aiStatus));
        if (dictionarySettingsService == null) {
            model.addAttribute("dictionaryKeyConfigured", false);
            model.addAttribute("dictionaryKeyMask", "");
            model.addAttribute("dictionaryKeyFromEnvironment", false);
            return "admin/news";
        }
        model.addAttribute(
                "dictionaryKeyConfigured",
                !dictionarySettingsService.apiKey(environmentDictionaryApiKey).isBlank()
        );
        model.addAttribute(
                "dictionaryKeyMask",
                dictionarySettingsService.maskedApiKey(environmentDictionaryApiKey)
        );
        model.addAttribute(
                "dictionaryKeyFromEnvironment",
                !dictionarySettingsService.hasStoredApiKey()
                        && environmentDictionaryApiKey != null
                        && !environmentDictionaryApiKey.isBlank()
        );
        return "admin/news";
    }

    @PostMapping("/dictionary")
    public String saveDictionaryApiKey(
            @RequestParam(name = "apiKey", required = false) String apiKey,
            @AuthenticationPrincipal KshUserDetails principal,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            dictionarySettingsService.saveApiKey(apiKey, principal.getId());
            redirectAttributes.addFlashAttribute(
                    "newsDictionaryMessage",
                    apiKey == null || apiKey.isBlank()
                            ? "Đã xóa API key KRDICT lưu trong giao diện admin."
                            : "Đã lưu API key KRDICT. Tra từ Hàn → Việt áp dụng ngay."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("newsDictionaryError", exception.getMessage());
        }
        return "redirect:/admin/news";
    }

    @PostMapping("/refresh")
    public String refresh(
            @RequestParam(name = "returnTo", defaultValue = "admin") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        NewsIngestionOrchestrator.RunSummary summary =
                orchestrator.run(NewsIngestionOrchestrator.Trigger.MANUAL);
        redirectAttributes.addFlashAttribute(
                "newsRefreshMessage",
                "Lần cào #" + summary.runId()
                        + ": " + summary.status()
                        + " · mới " + summary.published()
                        + " · loại " + summary.rejected()
                        + " · trùng " + summary.duplicates()
                        + " · blacklist " + summary.blacklisted()
                        + " · lỗi " + summary.errors()
                        + " · AI xong " + summary.aiGenerated()
                        + " · AI lỗi " + summary.aiFailed()
        );
        return "discover".equals(returnTo)
                ? "redirect:/discover"
                : "redirect:/admin/news?runId=" + summary.runId();
    }

    @PostMapping("/reset-sample")
    public String resetSample(
            @RequestParam(name = "returnTo", defaultValue = "discover") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        if (!rawPreviewEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        int deleted = adminNewsService.deleteOneRecentArticlePerSource(5);
        redirectAttributes.addFlashAttribute(
                "newsResetMessage",
                "Đã xóa " + deleted
                        + " bài local (tối đa 1 bài mỗi nguồn). Bấm “Cào tin ngay” để kiểm tra nhập lại."
        );
        return "admin".equals(returnTo)
                ? "redirect:/admin/news"
                : "redirect:/discover";
    }

    @PostMapping("/articles/delete")
    public String deleteArticles(
            @RequestParam(name = "articleIds", required = false) Collection<Long> articleIds,
            @RequestParam(name = "blacklistBeforeDelete", defaultValue = "false") boolean blacklistBeforeDelete,
            @RequestParam(name = "page", defaultValue = "1") int page,
            RedirectAttributes redirectAttributes
    ) {
        AdminNewsService.BulkActionResult result =
                adminNewsService.deleteArticles(articleIds, blacklistBeforeDelete);
        redirectAttributes.addFlashAttribute(
                "newsArticlesMessage",
                "Đã xóa " + result.affectedCount()
                        + " bài · blacklist " + result.blacklistedCount()
                        + " link · bỏ qua " + result.skippedCount() + " link"
        );
        return "redirect:/admin/news?page=" + Math.max(1, page);
    }

    @PostMapping("/articles/blacklist")
    public String blacklistArticles(
            @RequestParam(name = "articleIds", required = false) Collection<Long> articleIds,
            @RequestParam(name = "page", defaultValue = "1") int page,
            RedirectAttributes redirectAttributes
    ) {
        AdminNewsService.BulkActionResult result = adminNewsService.blacklistArticles(articleIds);
        redirectAttributes.addFlashAttribute(
                "newsArticlesMessage",
                "Đã blacklist " + result.blacklistedCount()
                        + " link · bỏ qua " + result.skippedCount() + " link"
        );
        return "redirect:/admin/news?page=" + Math.max(1, page);
    }
}
