package com.ksh.features.practice.manage.service;

import com.ksh.entities.LecturerAsset;
import com.ksh.entities.PracticePdfRegionAnnotation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticePdfRegionAssetSelectorTest {

    @Test
    void findCurrentSelectsLatestUsableAssetForExactCrop() {
        PracticePdfRegionAnnotation annotation = annotation();
        LecturerAsset staleCrop = asset(1L, "ACTIVE", null, 1, 0.10, 0.20, 0.31, 0.40,
                LocalDateTime.of(2026, 7, 15, 9, 0));
        LecturerAsset deleted = asset(2L, "ACTIVE", LocalDateTime.of(2026, 7, 15, 10, 0),
                1, 0.10, 0.20, 0.30, 0.40, LocalDateTime.of(2026, 7, 15, 10, 0));
        LecturerAsset olderCurrent = asset(3L, "TEMPORARY", null, 1, 0.10, 0.20, 0.30, 0.40,
                LocalDateTime.of(2026, 7, 15, 11, 0));
        LecturerAsset latestCurrent = asset(4L, "ACTIVE", null, 1, 0.10, 0.20, 0.30, 0.40,
                LocalDateTime.of(2026, 7, 15, 12, 0));

        assertThat(PracticePdfRegionAssetSelector.findCurrent(
                List.of(staleCrop, deleted, olderCurrent, latestCurrent), annotation))
                .contains(latestCurrent);
    }

    @Test
    void findCurrentRejectsAssetsFromAnotherRegionOrPage() {
        PracticePdfRegionAnnotation annotation = annotation();
        LecturerAsset anotherRegion = asset(1L, "ACTIVE", null, 1, 0.10, 0.20, 0.30, 0.40,
                LocalDateTime.of(2026, 7, 15, 9, 0));
        anotherRegion.setSourceRegionId(88L);
        LecturerAsset anotherPage = asset(2L, "ACTIVE", null, 2, 0.10, 0.20, 0.30, 0.40,
                LocalDateTime.of(2026, 7, 15, 10, 0));

        assertThat(PracticePdfRegionAssetSelector.findCurrent(
                List.of(anotherRegion, anotherPage), annotation))
                .isEmpty();
    }

    private PracticePdfRegionAnnotation annotation() {
        PracticePdfRegionAnnotation annotation = new PracticePdfRegionAnnotation();
        annotation.setId(77L);
        annotation.setPageNumber(1);
        annotation.setxRatio(0.10);
        annotation.setyRatio(0.20);
        annotation.setWidthRatio(0.30);
        annotation.setHeightRatio(0.40);
        return annotation;
    }

    private LecturerAsset asset(Long id,
                                String status,
                                LocalDateTime deletedAt,
                                Integer page,
                                Double x,
                                Double y,
                                Double width,
                                Double height,
                                LocalDateTime updatedAt) {
        LecturerAsset asset = new LecturerAsset();
        asset.setId(id);
        asset.setSourceRegionId(77L);
        asset.setSourcePageNumber(page);
        asset.setCropX(x);
        asset.setCropY(y);
        asset.setCropWidth(width);
        asset.setCropHeight(height);
        asset.setStatus(status);
        asset.setDeletedAt(deletedAt);
        asset.setUpdatedAt(updatedAt);
        return asset;
    }
}
