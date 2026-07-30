-- Compact the discovery schema without losing crawled content.
-- Attachments become inline JSON and blacklist rows become article tombstones.

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE news_articles ADD COLUMN source_attachments_json MEDIUMTEXT NULL AFTER source_content_fetched_at',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'news_articles'
      AND column_name = 'source_attachments_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE news_articles ADD COLUMN blacklist_reason VARCHAR(300) NULL AFTER status',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'news_articles'
      AND column_name = 'blacklist_reason'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE news_articles ADD COLUMN blacklisted_at DATETIME(6) NULL AFTER blacklist_reason',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'news_articles'
      AND column_name = 'blacklisted_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE news_articles article
JOIN (
    SELECT
        article_id,
        JSON_ARRAYAGG(
            JSON_OBJECT(
                'displayName', display_name,
                'sourceUrl', source_url,
                'mediaType', media_type,
                'sizeBytes', size_bytes,
                'displayOrder', display_order
            )
        ) AS attachments_json
    FROM news_article_attachments
    GROUP BY article_id
) attachments ON attachments.article_id = article.id
SET article.source_attachments_json = attachments.attachments_json;

UPDATE news_articles article
JOIN news_blacklist_entries blacklist
  ON blacklist.canonical_url_hash = article.canonical_url_hash
SET article.status = 'BLACKLISTED',
    article.blacklist_reason = blacklist.reason,
    article.blacklisted_at = blacklist.created_at,
    article.source_excerpt = NULL,
    article.source_body_html = NULL,
    article.source_body_text = NULL,
    article.source_attachments_json = NULL,
    article.image_url = NULL,
    article.updated_at = CURRENT_TIMESTAMP(6);

ALTER TABLE news_articles
    DROP CHECK chk_news_article_status,
    ADD CONSTRAINT chk_news_article_status CHECK (
        status IN ('PUBLISHED', 'REJECTED', 'BLACKLISTED')
    );

DROP TABLE news_article_attachments;
DROP TABLE news_blacklist_entries;
DROP TABLE news_ingestion_locks;
