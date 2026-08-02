package com.ksh.entities;

public enum PracticeStorageMigrationStatus {
    PLANNED,
    COPYING,
    COPIED_VERIFIED,
    LOGICAL_UPDATED,
    CLEANUP_PENDING,
    DELETING_SOURCE,
    COMPLETED,
    FAILED
}
