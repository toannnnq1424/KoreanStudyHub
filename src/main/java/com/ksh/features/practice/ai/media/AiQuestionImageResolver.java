package com.ksh.features.practice.ai.media;

import com.ksh.features.practice.manage.service.PracticeMaterialAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiQuestionImageResolver {
    private static final Logger log = LoggerFactory.getLogger(AiQuestionImageResolver.class);
    private static final Pattern INTERNAL_MATERIAL_REFERENCE =
            Pattern.compile("^/practice/materials/([1-9][0-9]*)/content$");
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final PracticeMaterialAccessService materialAccessService;

    public AiQuestionImageResolver(PracticeMaterialAccessService materialAccessService) {
        this.materialAccessService = materialAccessService;
    }

    public Optional<AiImageEvidence> resolve(String imageReference, Long actorId) {
        Long assetId = internalAssetId(imageReference);
        if (assetId == null || actorId == null) {
            return Optional.empty();
        }
        return resolveContent(assetId, () -> materialAccessService.load(assetId, actorId));
    }

    public Optional<AiImageEvidence> resolvePublishedVersion(
            String imageReference, Long publishedVersionId) {
        Long assetId = internalAssetId(imageReference);
        if (assetId == null || publishedVersionId == null) {
            return Optional.empty();
        }
        return resolveContent(assetId,
                () -> materialAccessService.loadForPublishedVersion(assetId, publishedVersionId));
    }

    private Optional<AiImageEvidence> resolveContent(
            Long assetId, MaterialContentLoader contentLoader) {
        try {
            PracticeMaterialAccessService.MaterialContent content =
                    contentLoader.load();
            String mimeType = normalizeMimeType(content.mimeType());
            if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
                log.info("[PracticeAIImage] Ignored unsupported asset assetId={} mimeType={}",
                        assetId, mimeType);
                return Optional.empty();
            }
            if (content.sizeBytes() != null && content.sizeBytes() > MAX_IMAGE_BYTES) {
                log.info("[PracticeAIImage] Ignored oversized asset assetId={} declaredBytes={}",
                        assetId, content.sizeBytes());
                return Optional.empty();
            }
            byte[] bytes;
            try (InputStream input = content.resource().getInputStream()) {
                bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                log.info("[PracticeAIImage] Ignored empty or oversized asset assetId={} actualBytes={}",
                        assetId, bytes.length);
                return Optional.empty();
            }
            String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return Optional.of(new AiImageEvidence(
                    assetId, mimeType, dataUrl, sha256(bytes), bytes.length));
        } catch (Exception exception) {
            log.warn("[PracticeAIImage] Could not resolve governed asset assetId={} exception={}",
                    assetId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface MaterialContentLoader {
        PracticeMaterialAccessService.MaterialContent load() throws Exception;
    }

    public static boolean isInternalMaterialReference(String reference) {
        return internalAssetId(reference) != null;
    }

    public static Long internalAssetId(String reference) {
        if (reference == null) {
            return null;
        }
        Matcher matcher = INTERNAL_MATERIAL_REFERENCE.matcher(reference.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        int separator = mimeType.indexOf(';');
        String normalized = separator < 0 ? mimeType : mimeType.substring(0, separator);
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
