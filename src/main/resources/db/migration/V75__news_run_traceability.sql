SET NAMES utf8mb4;

ALTER TABLE news_articles
    ADD COLUMN ingestion_run_id BIGINT NULL AFTER source_id,
    ADD COLUMN ai_generation_run_id BIGINT NULL AFTER ai_generated_at,
    ADD INDEX idx_news_articles_ingestion_run (ingestion_run_id, ingested_at),
    ADD INDEX idx_news_articles_ai_generation_run (ai_generation_run_id, ai_generated_at);

ALTER TABLE news_ingestion_runs
    ADD COLUMN ai_generated_count INT NOT NULL DEFAULT 0 AFTER error_count,
    ADD COLUMN ai_failed_count INT NOT NULL DEFAULT 0 AFTER ai_generated_count;

UPDATE news_articles article
SET article.ingestion_run_id = (
    SELECT run_match.id
    FROM news_ingestion_runs run_match
    WHERE run_match.started_at <= article.ingested_at
      AND COALESCE(run_match.completed_at, DATE_ADD(run_match.started_at, INTERVAL 45 MINUTE))
          >= article.ingested_at
    ORDER BY run_match.started_at DESC, run_match.id DESC
    LIMIT 1
)
WHERE article.ingestion_run_id IS NULL;
