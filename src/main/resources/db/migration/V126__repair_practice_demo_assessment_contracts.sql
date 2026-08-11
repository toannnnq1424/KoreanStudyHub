-- Repair only the KSH-authored experimental demo bundle created by V118.
--
-- V118 was valid against the authoring shape used when it was drafted, but
-- three payload details are rejected by the current runtime codec:
--   * TFNG must use answerSpec.correctValue, not correctOptionIds;
--   * languageTag belongs to question-content-v3;
--   * immutable question snapshots must receive the same repair so attempts
--     already started from the demo catalog remain resumable/submittable.
--
-- This forward-only repair adds no table and deliberately does not touch the
-- independently sourced TOPIK 35 premium bundle from V119.

SET @demo_seed_bundle = 'practice-demo-canonical-v1';

UPDATE practice_questions q
JOIN practice_sets s ON s.id = q.set_id
SET q.answer_spec_json = JSON_SET(
        q.answer_spec_json,
        '$.correctValue', 'FALSE',
        '$.correctOptionIds', JSON_ARRAY())
WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json, '$.seedBundle')) =
          @demo_seed_bundle
  AND q.question_type = 'TRUE_FALSE_NOT_GIVEN'
  AND q.question_no = 2;

UPDATE practice_question_versions qv
JOIN practice_questions q ON q.id = qv.question_id
JOIN practice_sets s ON s.id = q.set_id
SET qv.answer_spec_json = JSON_SET(
        qv.answer_spec_json,
        '$.correctValue', 'FALSE',
        '$.correctOptionIds', JSON_ARRAY())
WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json, '$.seedBundle')) =
          @demo_seed_bundle
  AND qv.question_type = 'TRUE_FALSE_NOT_GIVEN'
  AND qv.question_no = 2;

UPDATE practice_questions q
JOIN practice_sets s ON s.id = q.set_id
SET q.question_content_json = JSON_SET(
        q.question_content_json,
        '$.schemaVersion', 'question-content-v3')
WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json, '$.seedBundle')) =
          @demo_seed_bundle
  AND JSON_EXTRACT(q.question_content_json, '$.languageTag') IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(
          q.question_content_json, '$.schemaVersion')) <>
          'question-content-v3';

UPDATE practice_question_versions qv
JOIN practice_questions q ON q.id = qv.question_id
JOIN practice_sets s ON s.id = q.set_id
SET qv.question_content_json = JSON_SET(
        qv.question_content_json,
        '$.schemaVersion', 'question-content-v3')
WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json, '$.seedBundle')) =
          @demo_seed_bundle
  AND JSON_EXTRACT(qv.question_content_json, '$.languageTag') IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(
          qv.question_content_json, '$.schemaVersion')) <>
          'question-content-v3';
