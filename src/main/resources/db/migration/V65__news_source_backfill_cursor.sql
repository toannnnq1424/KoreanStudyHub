-- Incremental archive backfill. Page 1 is always checked for fresh items while
-- this cursor advances through older pages over successive ingestion runs.
ALTER TABLE news_sources
    ADD COLUMN crawl_cursor INT NOT NULL DEFAULT 2 AFTER last_error;
