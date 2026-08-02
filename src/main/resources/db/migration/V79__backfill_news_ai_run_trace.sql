SET NAMES utf8mb4;

UPDATE news_articles article
SET article.ai_generation_run_id = (
    SELECT run_match.id
    FROM news_ingestion_runs run_match
    WHERE run_match.started_at <= article.updated_at
      AND COALESCE(run_match.completed_at, DATE_ADD(run_match.started_at, INTERVAL 45 MINUTE))
          >= article.updated_at
    ORDER BY run_match.started_at DESC, run_match.id DESC
    LIMIT 1
)
WHERE article.ai_generation_run_id IS NULL
  AND (article.ai_generated_at IS NOT NULL OR article.ai_generation_error IS NOT NULL);
