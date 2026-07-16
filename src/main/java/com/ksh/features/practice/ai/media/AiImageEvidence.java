package com.ksh.features.practice.ai.media;

public record AiImageEvidence(
        Long assetId,
        String mimeType,
        String dataUrl,
        String sha256,
        long sizeBytes
) {
    @Override
    public String toString() {
        return "AiImageEvidence{"
                + "assetId=" + assetId
                + ", mimeType='" + mimeType + '\''
                + ", sha256='" + sha256 + '\''
                + ", sizeBytes=" + sizeBytes
                + '}';
    }
}
