package com.ksh.features.classes.service.approval;

/** Immutable payload emitted when a newly created class awaits Leader review. */
public record ClassPendingReviewEvent(
        Long classId,
        Long departmentId,
        Long lecturerId,
        String className,
        String subjectCode
) {
}
