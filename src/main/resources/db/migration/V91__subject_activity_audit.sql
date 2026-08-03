-- V3's activity_subjects has no application entity, writer, reader or UI.
-- The live catalog audit stream was created canonically as subjects_activities
-- in V41, so only the unused duplicate is removed here.
DROP TABLE activity_subjects;
