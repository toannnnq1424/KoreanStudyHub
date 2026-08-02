package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.practice.manage.service.PracticeMaterialReferenceService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed authority check for mutable media references at candidate
 * preview/apply. Candidate review may edit typed JSON, but it may not use that
 * freedom to point at another draft's private media or an unbound asset.
 */
@Service
public class PracticeAuthoringCandidateMaterialAuthority {

    private static final Pattern MATERIAL = Pattern.compile(
            "^/practice/materials/([1-9][0-9]*)/content(?:\\?.*)?$");
    private static final Pattern DRAFT_MEDIA = Pattern.compile(
            "^/practice/manage/drafts/([1-9][0-9]*)(?:/.*)?$");

    private final PracticeMaterialReferenceService referenceService;
    private final ObjectMapper objectMapper;

    public PracticeAuthoringCandidateMaterialAuthority(
            PracticeMaterialReferenceService referenceService,
            ObjectMapper objectMapper) {
        this.referenceService = referenceService;
        this.objectMapper = objectMapper;
    }

    public void requireAuthorized(Long draftId, String normalizedDraftJson) {
        if (draftId == null || normalizedDraftJson == null) {
            throw rejected();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(normalizedDraftJson);
        } catch (Exception exception) {
            throw rejected();
        }
        Set<Long> allowedAssets = new HashSet<>();
        for (PracticeMaterialReference reference
                : referenceService.referencesForDraft(draftId)) {
            allowedAssets.add(reference.getAssetId());
        }
        inspect(root, "", draftId, allowedAssets);
    }

    private static void inspect(
            JsonNode node,
            String fieldName,
            Long draftId,
            Set<Long> allowedAssets) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            if (!isReferenceField(fieldName)) return;
            String value = node.asText().trim();
            Matcher material = MATERIAL.matcher(value);
            if (value.startsWith("material:")) throw rejected();
            if (value.startsWith("/practice/materials/")) {
                Long assetId = material.matches()
                        ? positiveLong(material.group(1)) : null;
                if (assetId == null || !allowedAssets.contains(assetId)) {
                    throw rejected();
                }
            }
            Matcher draftMedia = DRAFT_MEDIA.matcher(value);
            if (value.startsWith("/practice/manage/drafts/")) {
                Long referencedDraft = draftMedia.matches()
                        ? positiveLong(draftMedia.group(1)) : null;
                if (referencedDraft == null
                        || !draftId.equals(referencedDraft)) {
                    throw rejected();
                }
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                inspect(field.getValue(), field.getKey(), draftId, allowedAssets);
            }
        } else if (node.isArray()) {
            node.forEach(child -> inspect(
                    child, fieldName, draftId, allowedAssets));
        }
    }

    private static boolean isReferenceField(String fieldName) {
        String normalized = fieldName == null ? ""
                : fieldName.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("reference")
                || normalized.endsWith("url");
    }

    private static Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static PracticeAuthoringCandidateException rejected() {
        return new PracticeAuthoringCandidateException(
                "CANDIDATE_MATERIAL_AUTHORITY_INVALID",
                "Candidate chứa tài nguyên không thuộc đúng bản nháp target.");
    }
}
