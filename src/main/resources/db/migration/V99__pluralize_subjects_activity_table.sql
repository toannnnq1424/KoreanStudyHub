-- Final physical name requested for the subject-catalog audit stream.
-- V91 remains checksum-stable and this forward rename preserves every row.
RENAME TABLE subject_activities TO subjects_activities;
ALTER TABLE subjects_activities
    RENAME INDEX idx_subject_activity_subject TO idx_subjects_activity_subject;
