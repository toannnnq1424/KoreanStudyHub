package com.ksh.features.discovery.ingestion;

public record NewsAttachmentCandidate(
        String displayName,
        String sourceUrl,
        String mediaType,
        Long sizeBytes,
        int displayOrder
) {
}
