package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticePdfRegionAnnotation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

final class PracticePdfRegionAssetSelector {

    private static final double COORDINATE_EPSILON = 0.000000001d;

    private PracticePdfRegionAssetSelector() {
    }

    static Optional<LecturerAsset> findCurrent(
            Collection<LecturerAsset> assets,
            PracticePdfRegionAnnotation annotation) {
        if (assets == null || annotation == null) {
            return Optional.empty();
        }
        return assets.stream()
                .filter(asset -> belongsToRegion(asset, annotation.getId()))
                .filter(PracticePdfRegionAssetSelector::isUsable)
                .filter(asset -> matchesCrop(asset, annotation))
                .max(Comparator
                        .comparing(
                                LecturerAsset::getUpdatedAt,
                                Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(
                                LecturerAsset::getId,
                                Comparator.nullsFirst(Long::compareTo)));
    }

    static boolean belongsToRegion(LecturerAsset asset, Long regionId) {
        return asset != null && Objects.equals(regionId, asset.getSourceRegionId());
    }

    static boolean isTemporary(LecturerAsset asset) {
        return asset != null
                && asset.getDeletedAt() == null
                && "TEMPORARY".equalsIgnoreCase(asset.getStatus());
    }

    static boolean matchesCrop(
            LecturerAsset asset,
            PracticePdfRegionAnnotation annotation) {
        return asset != null
                && annotation != null
                && Objects.equals(asset.getSourcePageNumber(), annotation.getPageNumber())
                && sameCoordinate(asset.getCropX(), annotation.getxRatio())
                && sameCoordinate(asset.getCropY(), annotation.getyRatio())
                && sameCoordinate(asset.getCropWidth(), annotation.getWidthRatio())
                && sameCoordinate(asset.getCropHeight(), annotation.getHeightRatio());
    }

    private static boolean isUsable(LecturerAsset asset) {
        return asset.getDeletedAt() == null
                && ("TEMPORARY".equalsIgnoreCase(asset.getStatus())
                || "ACTIVE".equalsIgnoreCase(asset.getStatus()));
    }

    private static boolean sameCoordinate(Double left, Double right) {
        return left != null
                && right != null
                && Math.abs(left - right) <= COORDINATE_EPSILON;
    }
}
