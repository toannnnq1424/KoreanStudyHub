package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_material_references")
public class PracticeMaterialReference {

    public static final String SCOPE_DRAFT = "DRAFT";
    public static final String SCOPE_PUBLISHED_VERSION = "PUBLISHED_VERSION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "set_id")
    private Long setId;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "reference_scope", nullable = false, length = 30)
    private String referenceScope;

    @Column(length = 64)
    private String placement;

    @Column(name = "reference_key", nullable = false, length = 255)
    private String referenceKey;

    @Column(name = "reference_metadata_json", columnDefinition = "JSON")
    private String referenceMetadataJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PracticeMaterialReference() {
    }

    public static PracticeMaterialReference draft(Long assetId, Long draftId,
                                                  String placement) {
        return draft(assetId, draftId, placement, "", null);
    }

    public static PracticeMaterialReference draft(Long assetId, Long draftId,
                                                  String placement,
                                                  String referenceKey,
                                                  String referenceMetadataJson) {
        PracticeMaterialReference reference = new PracticeMaterialReference();
        reference.assetId = assetId;
        reference.draftId = draftId;
        reference.referenceScope = SCOPE_DRAFT;
        reference.placement = placement;
        reference.referenceKey = referenceKey == null ? "" : referenceKey;
        reference.referenceMetadataJson = referenceMetadataJson;
        return reference;
    }

    public static PracticeMaterialReference published(Long assetId, Long setId,
                                                      Long publishedVersionId,
                                                      String placement) {
        PracticeMaterialReference reference = new PracticeMaterialReference();
        reference.assetId = assetId;
        reference.setId = setId;
        reference.publishedVersionId = publishedVersionId;
        reference.referenceScope = SCOPE_PUBLISHED_VERSION;
        reference.placement = placement;
        reference.referenceKey = "";
        return reference;
    }

    public Long getId() { return id; }
    public Long getAssetId() { return assetId; }
    public Long getDraftId() { return draftId; }
    public Long getSetId() { return setId; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public String getReferenceScope() { return referenceScope; }
    public String getPlacement() { return placement; }
    public String getReferenceKey() { return referenceKey; }
    public String getReferenceMetadataJson() { return referenceMetadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
