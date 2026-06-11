package com.ksh.KoreanStudyHub.entity;

import com.ksh.KoreanStudyHub.entity.base.BaseEntity;
import com.ksh.KoreanStudyHub.entity.enums.RoleName;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleName roleName;
}