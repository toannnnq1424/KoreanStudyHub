-- Development-stage source capture for admin-only review.
-- Raw third-party content must remain behind the preview gate until editorial/AI processing.

ALTER TABLE news_articles
    ADD COLUMN source_body_html MEDIUMTEXT NULL AFTER source_excerpt,
    ADD COLUMN source_body_text MEDIUMTEXT NULL AFTER source_body_html,
    ADD COLUMN source_layout VARCHAR(40) NULL AFTER source_body_text,
    ADD COLUMN source_author VARCHAR(180) NULL AFTER source_layout,
    ADD COLUMN source_view_count BIGINT NULL AFTER source_author,
    ADD COLUMN source_content_fetched_at DATETIME(6) NULL AFTER source_view_count;

CREATE TABLE news_article_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    media_type VARCHAR(120) NULL,
    size_bytes BIGINT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    INDEX idx_news_attachment_article (article_id, display_order, id),
    CONSTRAINT fk_news_attachment_article
        FOREIGN KEY (article_id) REFERENCES news_articles(id) ON DELETE CASCADE,
    CONSTRAINT chk_news_attachment_order CHECK (display_order BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
