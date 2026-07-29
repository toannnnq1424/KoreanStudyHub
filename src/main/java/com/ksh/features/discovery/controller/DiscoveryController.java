package com.ksh.features.discovery.controller;

import com.ksh.features.discovery.service.DiscoveryService;
import com.ksh.features.discovery.dto.DiscoveryDtos.ArticleDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final boolean rawPreviewEnabled;

    public DiscoveryController(
            DiscoveryService discoveryService,
            @Value("${app.news.raw-preview-enabled:false}") boolean rawPreviewEnabled
    ) {
        this.discoveryService = discoveryService;
        this.rawPreviewEnabled = rawPreviewEnabled;
    }

    @GetMapping("/discover")
    public String index(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "lang", required = false) String language,
            @RequestParam(name = "page", defaultValue = "1") int page,
            Model model
    ) {
        model.addAttribute("page", discoveryService.page(category, query, language, page));
        model.addAttribute("rawPreviewEnabled", rawPreviewEnabled);
        return "discovery/index";
    }

    @GetMapping("/discover/{slug}")
    public String detail(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false) String language,
            Model model
    ) {
        ArticleDetail article = discoveryService.detail(slug);
        String selectedLanguage = language == null
                ? (article.containsKorean() ? "ko" : "vi")
                : ("ko".equalsIgnoreCase(language) ? "ko" : "vi");
        model.addAttribute("article", article);
        model.addAttribute("selectedLanguage", selectedLanguage);
        model.addAttribute("rawPreviewEnabled", rawPreviewEnabled);
        return "discovery/detail";
    }
}
