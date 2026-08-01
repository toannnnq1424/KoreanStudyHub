-- Pre-14 Gate B: persist the two typed R/L question families now supported
-- by authoring, player, scoring and Result Detail. Keep the reduced KSH
-- allowlist explicit; this migration does not restore legacy pseudo-types.

ALTER TABLE practice_questions
    DROP CHECK chk_pq_type,
    ADD CONSTRAINT chk_pq_type CHECK (question_type IN (
        'SINGLE_CHOICE', 'MULTIPLE_ANSWER', 'MATCHING',
        'FILL_BLANK', 'TRUE_FALSE_NOT_GIVEN', 'ESSAY', 'SPEAKING'
    ));

ALTER TABLE practice_question_versions
    DROP CHECK chk_pqv_type,
    ADD CONSTRAINT chk_pqv_type CHECK (question_type IN (
        'SINGLE_CHOICE', 'MULTIPLE_ANSWER', 'MATCHING',
        'FILL_BLANK', 'TRUE_FALSE_NOT_GIVEN', 'ESSAY', 'SPEAKING'
    ));
