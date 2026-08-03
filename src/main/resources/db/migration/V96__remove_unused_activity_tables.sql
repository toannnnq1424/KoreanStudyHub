-- These V3 audit tables never acquired an application entity, writer, reader,
-- route, UI, or test caller. Active audit streams remain in activity_classes,
-- activity_sections, activity_lessons, activity_tests, user_activities,
-- permission_activities, and subject_activities.
DROP TABLE activity_enrollments;
DROP TABLE activity_assignments;
DROP TABLE activity_submissions;
DROP TABLE activity_users;
DROP TABLE activity_content_versions;
DROP TABLE activity_flashcard_decks;
