package com.ksh.KoreanStudyHub.entity;

import com.ksh.KoreanStudyHub.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private Boolean isActive = true;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;
}