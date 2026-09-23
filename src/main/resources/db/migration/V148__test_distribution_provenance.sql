-- Preserve class attempts and question snapshots. Link only audited distributions;
-- a matching title alone is never evidence that two tests share a source.
ALTER TABLE tests ADD COLUMN source_test_id BIGINT NULL;
CREATE INDEX idx_tests_source_test ON tests(source_test_id);

UPDATE tests child
JOIN (
    SELECT test_id,
           MIN(CAST(JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.sourceTestId')) AS UNSIGNED)) AS source_id
    FROM activity_tests
    WHERE type = 'CREATED'
      AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.sourceTestId')) REGEXP '^[0-9]+$'
    GROUP BY test_id
    HAVING COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.sourceTestId'))) = 1
) evidence ON evidence.test_id = child.id
JOIN tests source ON source.id = evidence.source_id
SET child.source_test_id = source.id
WHERE child.id <> source.id AND child.class_id IS NOT NULL
  AND child.subject_id = source.subject_id;
