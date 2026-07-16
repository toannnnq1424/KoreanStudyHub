ALTER TABLE practice_sections
    ADD COLUMN delivery_json JSON NULL AFTER instructions;

ALTER TABLE practice_section_versions
    ADD COLUMN delivery_json JSON NULL AFTER instructions;
