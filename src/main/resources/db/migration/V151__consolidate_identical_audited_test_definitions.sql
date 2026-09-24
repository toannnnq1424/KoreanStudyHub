-- Never infer provenance from a title. Only V148's audited source links qualify.
-- V150 pins every existing attempt to its original question bank; those banks
-- remain in place for historical reviews and in-progress submissions.
UPDATE tests delivery
JOIN tests source ON source.id = delivery.source_test_id
SET delivery.shared_question_source_id = source.id
WHERE delivery.shared_question_source_id IS NULL
  AND source.shared_question_source_id IS NULL
  AND delivery.id <> source.id
  AND delivery.class_id IS NOT NULL
  AND source.is_deleted = 0 AND delivery.is_deleted = 0
  AND (BINARY delivery.description <=> BINARY source.description)
  AND (delivery.media_type <=> source.media_type)
  AND (BINARY delivery.media_url <=> BINARY source.media_url)
  AND EXISTS (SELECT 1 FROM questions q WHERE q.test_id = source.id)
  AND (SELECT COUNT(*) FROM questions q WHERE q.test_id = source.id)
      = (SELECT COUNT(*) FROM questions q WHERE q.test_id = delivery.id)
  -- Ambiguous ordering is deliberately not consolidated.
  AND NOT EXISTS (SELECT q.sort_order FROM questions q
      WHERE q.test_id IN (source.id, delivery.id)
      GROUP BY q.test_id, q.sort_order HAVING COUNT(*) > 1)
  AND NOT EXISTS (
      SELECT 1 FROM questions child
      WHERE child.test_id = delivery.id AND NOT EXISTS (
          SELECT 1 FROM questions original
          WHERE original.test_id = source.id
            AND (original.sort_order <=> child.sort_order)
            AND original.question_type = child.question_type
            AND (BINARY original.content <=> BINARY child.content)
            AND (BINARY original.explanation <=> BINARY child.explanation)
            AND (original.points <=> child.points)
            AND (SELECT COUNT(*) FROM question_options o WHERE o.question_id = original.id)
                = (SELECT COUNT(*) FROM question_options o WHERE o.question_id = child.id)
            AND NOT EXISTS (SELECT o.sort_order FROM question_options o
                WHERE o.question_id IN (original.id, child.id)
                GROUP BY o.question_id, o.sort_order HAVING COUNT(*) > 1)
            AND NOT EXISTS (
                SELECT 1 FROM question_options co WHERE co.question_id = child.id
                AND NOT EXISTS (
                    SELECT 1 FROM question_options so WHERE so.question_id = original.id
                      AND (so.sort_order <=> co.sort_order)
                      AND (BINARY so.content <=> BINARY co.content)
                      AND so.is_correct = co.is_correct
                )
            )
      )
  );
