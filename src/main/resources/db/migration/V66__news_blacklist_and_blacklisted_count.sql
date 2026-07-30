-- Korea Discovery moderation: persisted blacklist + crawl run tracking.

ALTER TABLE news_ingestion_runs
    ADD COLUMN blacklisted_count INT NOT NULL DEFAULT 0 AFTER duplicate_count;

CREATE TABLE news_blacklist_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    canonical_url VARCHAR(2048) NOT NULL,
    canonical_url_hash CHAR(64) NOT NULL,
    source_name VARCHAR(180) NOT NULL,
    article_title VARCHAR(700) NOT NULL,
    reason VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    UNIQUE INDEX uq_news_blacklist_url_hash (canonical_url_hash),
    INDEX idx_news_blacklist_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
