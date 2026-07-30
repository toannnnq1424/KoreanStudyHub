package com.ksh.features.discovery.controller;

import com.ksh.features.discovery.ingestion.NewsIngestionOrchestrator;
import com.ksh.features.discovery.service.AdminNewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNewsControllerTest {

    private NewsIngestionOrchestrator orchestrator;
    private AdminNewsService adminNewsService;
    private AdminNewsController controller;

    @BeforeEach
    void setUp() {
        orchestrator = mock(NewsIngestionOrchestrator.class);
        adminNewsService = mock(AdminNewsService.class);
        controller = new AdminNewsController(adminNewsService, orchestrator, true);
    }

    @Test
    void refreshRunsTheRealManualPipelineAndReturnsToDiscoveryFeed() {
        when(orchestrator.run(NewsIngestionOrchestrator.Trigger.MANUAL))
                .thenReturn(new NewsIngestionOrchestrator.RunSummary(42L, "SUCCEEDED", 15, 3, 2, 9, 4, 1, 2, 1));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.refresh("discover", attributes);

        assertThat(view).isEqualTo("redirect:/discover");
        assertThat(attributes.getFlashAttributes().get("newsRefreshMessage"))
                .isEqualTo("Lần cào #42: SUCCEEDED · mới 3 · loại 2 · trùng 9 · blacklist 4 · lỗi 1. Chưa gọi AI; hãy chọn bài cần biên tập ở danh sách bên dưới.");
        verify(orchestrator).run(NewsIngestionOrchestrator.Trigger.MANUAL);
    }

    @Test
    void refreshOnlyAllowsTheKnownFeedRedirect() {
        when(orchestrator.run(NewsIngestionOrchestrator.Trigger.MANUAL))
                .thenReturn(new NewsIngestionOrchestrator.RunSummary(43L, "SUCCEEDED", 0, 0, 0, 0, 0, 0, 0, 0));

        String view = controller.refresh("https://example.com", new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/admin/news?runId=43");
    }

    @Test
    void deleteArticlesReportsBulkCounts() {
        when(adminNewsService.deleteArticles(null, true))
                .thenReturn(new AdminNewsService.BulkActionResult(0, 0, 0, 0));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.deleteArticles(null, true, 2, attributes);

        assertThat(view).isEqualTo("redirect:/admin/news?page=2#articles");
        assertThat(attributes.getFlashAttributes().get("newsArticlesMessage").toString())
                .contains("Đã xóa 0 bài");
    }

    @Test
    void blacklistArticlesReportsBulkCounts() {
        when(adminNewsService.blacklistArticles(null))
                .thenReturn(new AdminNewsService.BulkActionResult(0, 0, 2, 1));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.blacklistArticles(null, 3, attributes);

        assertThat(view).isEqualTo("redirect:/admin/news?page=3#articles");
        assertThat(attributes.getFlashAttributes().get("newsArticlesMessage").toString())
                .contains("Đã blacklist 2 link");
    }

    @Test
    void resetSampleDeletesAtMostOneArticlePerSourceBeforeRecrawl() {
        when(adminNewsService.deleteOneRecentArticlePerSource(5)).thenReturn(5);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.resetSample("discover", attributes);

        assertThat(view).isEqualTo("redirect:/discover");
        assertThat(attributes.getFlashAttributes().get("newsResetMessage").toString())
                .contains("Đã xóa 5 bài local");
        verify(adminNewsService).deleteOneRecentArticlePerSource(5);
    }
}
