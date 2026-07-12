package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticeAuthoringCollaboration;
import com.ksh.entities.PracticeDraftAssetUsage;
import com.ksh.entities.PracticeMaterialReference;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.governance.PracticeAction;
import com.ksh.features.practice.governance.PracticeAuthorizationService;
import com.ksh.features.practice.repository.LecturerAssetRepository;
import com.ksh.features.practice.repository.PracticeAuthoringCollaborationRepository;
import com.ksh.features.practice.repository.PracticeDraftAssetUsageRepository;
import com.ksh.features.practice.repository.PracticeMaterialReferenceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PracticeMaterialLibraryService {
    static final int VIEW_LIMIT = 100;
    private static final int COLLABORATION_LIMIT = 100;
    private static final Set<String> DELIVERABLE_STATUSES =
            Set.of("ACTIVE", "TEMPORARY", "ARCHIVED");

    private final LecturerAssetRepository assetRepository;
    private final PracticeAuthoringCollaborationRepository collaborationRepository;
    private final PracticeMaterialReferenceRepository referenceRepository;
    private final PracticeDraftAssetUsageRepository legacyUsageRepository;
    private final PracticeAuthorizationService authorizationService;
    private final UserRepository userRepository;

    public PracticeMaterialLibraryService(
            LecturerAssetRepository assetRepository,
            PracticeAuthoringCollaborationRepository collaborationRepository,
            PracticeMaterialReferenceRepository referenceRepository,
            PracticeDraftAssetUsageRepository legacyUsageRepository,
            PracticeAuthorizationService authorizationService,
            UserRepository userRepository) {
        this.assetRepository = assetRepository;
        this.collaborationRepository = collaborationRepository;
        this.referenceRepository = referenceRepository;
        this.legacyUsageRepository = legacyUsageRepository;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Catalog catalog(Long actorId) {
        authorizationService.requireGlobal(actorId, PracticeAction.READ);
        List<LecturerAsset> owned = assetRepository
                .findByOwnerLecturerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        actorId, PageRequest.of(0, VIEW_LIMIT));

        LinkedHashSet<Long> sharedAssetIds = new LinkedHashSet<>();
        List<PracticeAuthoringCollaboration> grants = collaborationRepository
                .findByCollaboratorIdAndRevokedAtIsNullOrderByGrantedAtDesc(
                        actorId, PageRequest.of(0, COLLABORATION_LIMIT));
        for (PracticeAuthoringCollaboration grant : grants) {
            if (PracticeAuthoringCollaboration.TARGET_DRAFT.equals(grant.getTargetType())) {
                referenceRepository.findByDraftId(grant.getTargetId()).stream()
                        .map(PracticeMaterialReference::getAssetId)
                        .forEach(sharedAssetIds::add);
                legacyUsageRepository.findByDraftId(grant.getTargetId()).stream()
                        .map(PracticeDraftAssetUsage::getAssetId)
                        .forEach(sharedAssetIds::add);
            } else if (PracticeAuthoringCollaboration.TARGET_SET.equals(grant.getTargetType())) {
                referenceRepository.findBySetId(grant.getTargetId()).stream()
                        .map(PracticeMaterialReference::getAssetId)
                        .forEach(sharedAssetIds::add);
            }
            if (sharedAssetIds.size() >= VIEW_LIMIT) {
                break;
            }
        }
        Set<Long> ownedIds = owned.stream().map(LecturerAsset::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> boundedSharedIds = sharedAssetIds.stream()
                .filter(id -> !ownedIds.contains(id))
                .limit(VIEW_LIMIT)
                .toList();
        List<LecturerAsset> shared = new ArrayList<>(
                assetRepository.findAllById(boundedSharedIds));
        shared.removeIf(asset -> asset.getDeletedAt() != null);
        shared.sort(Comparator.comparing(
                LecturerAsset::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<Long, String> ownerNames = ownerNames(owned, shared);
        return new Catalog(
                owned.stream().map(asset -> view(asset, ownerNames)).toList(),
                shared.stream().map(asset -> view(asset, ownerNames)).toList(),
                VIEW_LIMIT);
    }

    private MaterialView view(LecturerAsset asset, Map<Long, String> ownerNames) {
        List<PracticeMaterialReference> references =
                referenceRepository.findByAssetId(asset.getId());
        List<PracticeDraftAssetUsage> legacy =
                legacyUsageRepository.findByAssetId(asset.getId());
        LinkedHashSet<String> placements = new LinkedHashSet<>();
        references.stream().map(PracticeMaterialReference::getPlacement)
                .filter(value -> value != null && !value.isBlank())
                .forEach(placements::add);
        legacy.stream().map(PracticeDraftAssetUsage::getPlacement)
                .filter(value -> value != null && !value.isBlank())
                .forEach(placements::add);
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        references.stream().map(PracticeMaterialReference::getReferenceScope)
                .filter(value -> value != null && !value.isBlank())
                .forEach(scopes::add);
        if (!legacy.isEmpty()) scopes.add("DRAFT_LEGACY");
        boolean deliverable = asset.isContentVerified()
                && DELIVERABLE_STATUSES.contains(asset.getStatus());
        String mimeType = asset.getMimeType() == null
                ? "application/octet-stream" : asset.getMimeType();
        String mediaKind = mimeType.startsWith("image/") ? "IMAGE"
                : mimeType.startsWith("audio/") ? "AUDIO" : "FILE";
        return new MaterialView(
                asset.getId(),
                displayTitle(asset),
                ownerNames.getOrDefault(asset.getOwnerLecturerId(), "Giảng viên"),
                asset.getAssetType(),
                mimeType,
                asset.getFileSize(),
                asset.getStatus(),
                asset.getVisibility(),
                asset.isContentVerified(),
                references.size() + legacy.size(),
                List.copyOf(placements),
                List.copyOf(scopes),
                asset.getUpdatedAt(),
                deliverable ? "/practice/materials/" + asset.getId() + "/content" : null,
                mediaKind);
    }

    private Map<Long, String> ownerNames(List<LecturerAsset> owned,
                                         List<LecturerAsset> shared) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        owned.stream().map(LecturerAsset::getOwnerLecturerId).forEach(ids::add);
        shared.stream().map(LecturerAsset::getOwnerLecturerId).forEach(ids::add);
        Map<Long, String> result = new LinkedHashMap<>();
        userRepository.findAllById(ids).forEach(
                user -> result.put(user.getId(), user.getFullName()));
        return result;
    }

    private static String displayTitle(LecturerAsset asset) {
        if (asset.getTitle() != null && !asset.getTitle().isBlank()) {
            return asset.getTitle();
        }
        if (asset.getOriginalFilename() != null && !asset.getOriginalFilename().isBlank()) {
            return asset.getOriginalFilename();
        }
        return "Tài nguyên " + asset.getId();
    }

    public record Catalog(List<MaterialView> mine,
                          List<MaterialView> shared,
                          int limit) {
    }

    public record MaterialView(Long id,
                               String title,
                               String ownerName,
                               String assetType,
                               String mimeType,
                               Long sizeBytes,
                               String status,
                               String visibility,
                               boolean contentVerified,
                               int referenceCount,
                               List<String> placements,
                               List<String> scopes,
                               LocalDateTime updatedAt,
                               String contentUrl,
                               String mediaKind) {
    }
}
