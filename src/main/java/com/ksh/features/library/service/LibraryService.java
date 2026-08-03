package com.ksh.features.library.service;

import com.ksh.entities.LibraryAsset;
import com.ksh.features.library.dto.LibraryDtos.LibraryAssetRow;
import com.ksh.features.library.repository.LibraryAssetRepository;
import com.ksh.features.storage.StorageTransactionLifecycle;
import com.ksh.features.upload.LibraryStorageService;
import com.ksh.features.upload.LibraryStorageService.StoredLibraryFile;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.ksh.common.IConstant.MSG_LIBRARY_ASSET_NOT_FOUND;

/** Material storage used only from canonical Library lesson authoring. */
@Service
public class LibraryService {

    private final LibraryAssetRepository assetRepository;
    private final LibraryStorageService storage;

    public LibraryService(LibraryAssetRepository assetRepository,
                          LibraryStorageService storage) {
        this.assetRepository = assetRepository;
        this.storage = storage;
    }

    @Transactional
    public LibraryAssetRow upload(Long ownerId, MultipartFile file, String kind) throws IOException {
        StoredLibraryFile stored = storage.store(file, ownerId, kind);
        StorageTransactionLifecycle.deleteOnRollback(
                () -> storage.delete(stored.storedPath()));
        LibraryAsset asset = new LibraryAsset(
                ownerId, stored.originalFilename(), stored.originalFilename(),
                stored.storedPath(), stored.mimeType(), stored.sizeBytes(), stored.kind());
        return toRow(assetRepository.save(asset));
    }

    /** Locks an owned material while a Library lesson creates a durable reference. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LibraryAsset getOwnedAssetForUpdate(Long ownerId, Long assetId) {
        return assetRepository.findByIdAndOwnerIdForUpdate(assetId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(MSG_LIBRARY_ASSET_NOT_FOUND));
    }

    private static LibraryAssetRow toRow(LibraryAsset asset) {
        return new LibraryAssetRow(
                asset.getId(), asset.getTitle(), asset.getOriginalFilename(),
                asset.getKind(), asset.getMimeType(), asset.getSizeBytes(), asset.getUpdatedAt());
    }
}
