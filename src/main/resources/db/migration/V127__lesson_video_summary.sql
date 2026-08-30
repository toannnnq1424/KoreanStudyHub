ALTER TABLE lesson_templates
    ADD COLUMN video_summary VARCHAR(1000) NULL AFTER video_url;

ALTER TABLE lessons
    ADD COLUMN video_summary VARCHAR(1000) NULL AFTER video_url;
