package com.ksh.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Additional lecturer access that never replaces the class owner. */
@Entity
@Table(name = "class_co_lecturers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassCoLecturer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "lecturer_id", nullable = false)
    private Long lecturerId;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ClassCoLecturer(Long classId, Long lecturerId, Long assignedBy) {
        this.classId = classId;
        this.lecturerId = lecturerId;
        this.assignedBy = assignedBy;
    }
}
