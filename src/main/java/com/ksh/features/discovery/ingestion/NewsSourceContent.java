package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsSourceLayout;

import java.util.List;

public record NewsSourceContent(
        String html,
        String text,
        NewsSourceLayout layout,
        String author,
        Long viewCount,
        String attachmentGroupId,
        List<NewsAttachmentCandidate> attachments
) {
    public NewsSourceContent {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
