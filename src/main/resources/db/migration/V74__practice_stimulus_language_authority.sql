ALTER TABLE practice_question_groups
    ADD COLUMN instruction_language_tag VARCHAR(8) NOT NULL DEFAULT 'vi'
        AFTER stimulus_type,
    ADD COLUMN stimulus_language_tag VARCHAR(8) NOT NULL DEFAULT 'ko'
        AFTER instruction_language_tag,
    ADD CONSTRAINT chk_practice_question_groups_instruction_language
        CHECK (instruction_language_tag IN ('ko', 'vi')),
    ADD CONSTRAINT chk_practice_question_groups_stimulus_language
        CHECK (stimulus_language_tag IN ('ko', 'vi'));

ALTER TABLE practice_question_group_versions
    ADD COLUMN instruction_language_tag VARCHAR(8) NOT NULL DEFAULT 'vi'
        AFTER stimulus_type,
    ADD COLUMN stimulus_language_tag VARCHAR(8) NOT NULL DEFAULT 'ko'
        AFTER instruction_language_tag,
    ADD CONSTRAINT chk_practice_question_group_versions_instruction_language
        CHECK (instruction_language_tag IN ('ko', 'vi')),
    ADD CONSTRAINT chk_practice_question_group_versions_stimulus_language
        CHECK (stimulus_language_tag IN ('ko', 'vi'));
