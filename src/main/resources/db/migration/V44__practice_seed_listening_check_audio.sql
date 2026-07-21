-- Integrated as V44 because main already uses V38 for test media.
-- Repair only the canonical V25 Listening sample. Lecturer-authored Listening
-- sections remain restricted to material-library references by validation.
UPDATE practice_sections section_row
JOIN practice_sets set_row ON set_row.id = section_row.set_id
SET section_row.delivery_json = JSON_OBJECT(
        'schemaVersion', 'practice-section-delivery-v1',
        'listeningDelivery', JSON_OBJECT(
            'checkAudioReference', '/audio/practice/listening-speaker-check.wav'
        )
    )
WHERE set_row.id = 2
  AND section_row.id = 2
  AND section_row.test_id = 2
  AND section_row.skill = 'LISTENING'
  AND JSON_UNQUOTE(JSON_EXTRACT(set_row.metadata_json, '$.source')) = 'seed'
  AND COALESCE(
        JSON_UNQUOTE(JSON_EXTRACT(
            section_row.delivery_json,
            '$.listeningDelivery.checkAudioReference'
        )),
        ''
      ) = '';

-- Keep version 1 immutable. The repaired seed becomes a new published version
-- so historical attempts retain their original version lock.
INSERT INTO practice_published_versions (
    set_id, version_number, status, content_hash, published_by, published_at
)
SELECT set_row.id,
       COALESCE(MAX(existing.version_number), 0) + 1,
       'PUBLISHED',
       SHA2('ksh-seed-listening-check-audio-v38', 256),
       set_row.created_by,
       CURRENT_TIMESTAMP
FROM practice_sets set_row
JOIN practice_sections section_row ON section_row.set_id = set_row.id
LEFT JOIN practice_published_versions existing ON existing.set_id = set_row.id
WHERE set_row.id = 2
  AND section_row.id = 2
  AND section_row.test_id = 2
  AND JSON_UNQUOTE(JSON_EXTRACT(set_row.metadata_json, '$.source')) = 'seed'
  AND JSON_UNQUOTE(JSON_EXTRACT(
        section_row.delivery_json,
        '$.listeningDelivery.checkAudioReference'
      )) = '/audio/practice/listening-speaker-check.wav'
  AND NOT EXISTS (
      SELECT 1
      FROM practice_published_versions already_repaired
      WHERE already_repaired.set_id = set_row.id
        AND already_repaired.content_hash =
            SHA2('ksh-seed-listening-check-audio-v38', 256)
  )
GROUP BY set_row.id, set_row.created_by;

INSERT INTO practice_set_versions (
    published_version_id, set_id, title, description, skill, scope, class_id,
    metadata_json, creation_method, cover_image_url
)
SELECT published.id, set_row.id, set_row.title, set_row.description,
       set_row.skill, set_row.scope, set_row.class_id, set_row.metadata_json,
       set_row.creation_method, set_row.cover_image_url
FROM practice_published_versions published
JOIN practice_sets set_row ON set_row.id = published.set_id
WHERE published.set_id = 2
  AND published.content_hash = SHA2('ksh-seed-listening-check-audio-v38', 256)
  AND NOT EXISTS (
      SELECT 1 FROM practice_set_versions current_snapshot
      WHERE current_snapshot.published_version_id = published.id
  );

INSERT INTO practice_test_versions (
    published_version_id, set_version_id, test_id, title, description,
    display_order, estimated_minutes
)
SELECT published.id, set_version.id, test_row.id, test_row.title,
       test_row.description, test_row.display_order, test_row.estimated_minutes
FROM practice_published_versions published
JOIN practice_set_versions set_version
  ON set_version.published_version_id = published.id
JOIN practice_tests test_row ON test_row.set_id = published.set_id
WHERE published.set_id = 2
  AND published.content_hash = SHA2('ksh-seed-listening-check-audio-v38', 256)
  AND NOT EXISTS (
      SELECT 1 FROM practice_test_versions current_snapshot
      WHERE current_snapshot.published_version_id = published.id
        AND current_snapshot.test_id = test_row.id
  );

INSERT INTO practice_section_versions (
    published_version_id, test_version_id, section_id, title, skill,
    section_type, instructions, delivery_json, duration_minutes, total_points,
    display_order
)
SELECT published.id, test_version.id, section_row.id, section_row.title,
       section_row.skill, section_row.section_type, section_row.instructions,
       section_row.delivery_json, section_row.duration_minutes,
       section_row.total_points, section_row.display_order
FROM practice_published_versions published
JOIN practice_test_versions test_version
  ON test_version.published_version_id = published.id
JOIN practice_sections section_row
  ON section_row.test_id = test_version.test_id
WHERE published.set_id = 2
  AND published.content_hash = SHA2('ksh-seed-listening-check-audio-v38', 256)
  AND NOT EXISTS (
      SELECT 1 FROM practice_section_versions current_snapshot
      WHERE current_snapshot.published_version_id = published.id
        AND current_snapshot.section_id = section_row.id
  );

INSERT INTO practice_question_group_versions (
    published_version_id, section_version_id, group_id, group_label,
    question_from, question_to, instruction, stimulus_type, passage_text,
    transcript_text, image_url, stimulus_provenance_json, audio_url,
    example_json, display_order
)
SELECT published.id, section_version.id, group_row.id, group_row.group_label,
       group_row.question_from, group_row.question_to, group_row.instruction,
       group_row.stimulus_type, group_row.passage_text,
       group_row.transcript_text, group_row.image_url,
       group_row.stimulus_provenance_json, group_row.audio_url,
       group_row.example_json, group_row.display_order
FROM practice_published_versions published
JOIN practice_section_versions section_version
  ON section_version.published_version_id = published.id
JOIN practice_question_groups group_row
  ON group_row.section_id = section_version.section_id
WHERE published.set_id = 2
  AND published.content_hash = SHA2('ksh-seed-listening-check-audio-v38', 256)
  AND NOT EXISTS (
      SELECT 1 FROM practice_question_group_versions current_snapshot
      WHERE current_snapshot.published_version_id = published.id
        AND current_snapshot.group_id = group_row.id
  );

INSERT INTO practice_question_versions (
    published_version_id, section_version_id, group_version_id, question_id,
    question_no, question_type, prompt, options_json, question_content_json,
    answer_key, answer_spec_json, explanation, points, display_order,
    writing_task_type
)
SELECT published.id, section_version.id, group_version.id, question_row.id,
       question_row.question_no, question_row.question_type,
       question_row.prompt, question_row.options_json,
       question_row.question_content_json, question_row.answer_key,
       question_row.answer_spec_json, question_row.explanation,
       question_row.points, question_row.display_order,
       question_row.writing_task_type
FROM practice_published_versions published
JOIN practice_section_versions section_version
  ON section_version.published_version_id = published.id
JOIN practice_question_group_versions group_version
  ON group_version.published_version_id = published.id
 AND group_version.section_version_id = section_version.id
JOIN practice_questions question_row
  ON question_row.group_id = group_version.group_id
WHERE published.set_id = 2
  AND published.content_hash = SHA2('ksh-seed-listening-check-audio-v38', 256)
  AND NOT EXISTS (
      SELECT 1 FROM practice_question_versions current_snapshot
      WHERE current_snapshot.published_version_id = published.id
        AND current_snapshot.question_id = question_row.id
  );

INSERT INTO practice_question_versions (
    published_version_id, section_version_id, group_version_id, question_id,
    question_no, question_type, prompt, options_json, question_content_json,
    answer_key, answer_spec_json, explanation, points, display_order,
    writing_task_type
)
SELECT published.id, section_version.id, NULL, question_row.id,
       question_row.question_no, question_row.question_type,
       question_row.prompt, question_row.options_json,
       question_row.question_content_json, question_row.answer_key,
       question_row.answer_spec_json, question_row.explanation,
       question_row.points, question_row.display_order,
       question_row.writing_task_type
FROM practice_published_versions published
JOIN practice_section_versions section_version
  ON section_version.published_version_id = published.id
JOIN practice_questions question_row
  ON question_row.set_id = published.set_id
 AND question_row.group_id IS NULL
WHERE published.set_id = 2
  AND published.content_hash = SHA2('ksh-seed-listening-check-audio-v38', 256)
  AND NOT EXISTS (
      SELECT 1 FROM practice_question_versions current_snapshot
      WHERE current_snapshot.published_version_id = published.id
        AND current_snapshot.question_id = question_row.id
  );
