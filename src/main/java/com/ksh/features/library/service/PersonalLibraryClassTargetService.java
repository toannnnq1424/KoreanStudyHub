package com.ksh.features.library.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.LibraryAsset;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.service.ClassRoleAccessPolicy;
import com.ksh.features.library.dto.LibraryDtos.PersonalAssetClassTarget;
import com.ksh.features.library.dto.LibraryDtos.PersonalAssetClassTargets;
import com.ksh.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ksh.common.IConstant.MSG_LIBRARY_BIND_INVALID_KIND;
import static com.ksh.entities.LibraryAsset.KIND_DOCUMENT;

/** Read-only owner/admin class targets for sharing one personal document. */
@Service
public class PersonalLibraryClassTargetService {

    private final LibraryService libraryService;
    private final ClassRepository classRepository;
    private final ClassRoleAccessPolicy accessPolicy;

    public PersonalLibraryClassTargetService(LibraryService libraryService,
                                             ClassRepository classRepository, ClassRoleAccessPolicy accessPolicy) {
        this.libraryService = libraryService;
        this.classRepository = classRepository;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public PersonalAssetClassTargets targets(Long actorId, Role role, Long assetId) {
        LibraryAsset asset = libraryService.getOwnedAsset(actorId, assetId);
        if (!KIND_DOCUMENT.equals(asset.getKind())) {
            throw new IllegalArgumentException(MSG_LIBRARY_BIND_INVALID_KIND);
        }
        // Validate the persisted row/key before offering any target for it.
        libraryService.requireOwnedStorageKey(actorId, asset);

        List<ClassEntity> candidates;
        if (role == Role.ADMIN || role == Role.LEADER) {
            candidates = classRepository.findAllByOrderByCreatedAtDesc();
        } else if (role == Role.LECTURER || role == Role.LEADER) {
            candidates = classRepository.findAllById(classRepository.findClassIdsForLecturer(actorId));
        } else {
            throw new AccessDeniedException("Bạn không có quyền chia sẻ tài liệu vào lớp");
        }

        List<PersonalAssetClassTarget> classes = candidates.stream()
                .filter(clazz -> ClassEntity.STATUS_ACTIVE.equals(clazz.getStatus()))
                .filter(clazz -> accessPolicy.canAccess(clazz, actorId, role))
                .map(clazz -> new PersonalAssetClassTarget(
                        clazz.getId(), clazz.getName(), clazz.getStatus(), List.of()))
                .toList();
        return new PersonalAssetClassTargets(asset.getId(), classes);
    }
}
