package com.ksh.KoreanStudyHub.repository;

import com.ksh.KoreanStudyHub.entity.Role;
import com.ksh.KoreanStudyHub.entity.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository
        extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(RoleName roleName);
}