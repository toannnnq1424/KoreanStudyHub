-- Korea Discovery: source-attributed, metadata-only culture/news aggregation.
-- Full third-party article bodies are intentionally never persisted.

CREATE TABLE news_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    feed_url VARCHAR(2048) NOT NULL,
    site_url VARCHAR(2048) NOT NULL,
    default_category VARCHAR(30) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    priority_weight INT NOT NULL DEFAULT 50,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_attempt_at DATETIME(6) NULL,
    last_success_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE INDEX uq_news_source_code (code),
    INDEX idx_news_source_enabled (enabled, priority_weight DESC),
    CONSTRAINT chk_news_source_type CHECK (
        source_type IN ('RSS', 'KOREA_NET_HTML', 'STUDY_IN_KOREA_JSON')
    ),
    CONSTRAINT chk_news_source_category CHECK (
        default_category IN ('CULTURE', 'FOOD', 'ENTERTAINMENT', 'SCHOLARSHIP')
    ),
    CONSTRAINT chk_news_source_weight CHECK (priority_weight BETWEEN 0 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    source_name VARCHAR(180) NOT NULL,
    external_id VARCHAR(180) NULL,
    canonical_url VARCHAR(2048) NOT NULL,
    canonical_url_hash CHAR(64) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    original_title VARCHAR(700) NOT NULL,
    display_title VARCHAR(700) NOT NULL,
    source_excerpt TEXT NULL,
    category VARCHAR(30) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    image_url VARCHAR(2048) NULL,
    status VARCHAR(20) NOT NULL,
    political_risk BOOLEAN NOT NULL DEFAULT FALSE,
    vietnam_relevance INT NOT NULL DEFAULT 0,
    rank_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    published_at DATETIME(6) NOT NULL,
    deadline_at DATETIME(6) NULL,
    ingested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE INDEX uq_news_article_url_hash (canonical_url_hash),
    UNIQUE INDEX uq_news_article_slug (slug),
    INDEX idx_news_article_feed
        (status, rank_score DESC, published_at DESC, id DESC),
    INDEX idx_news_article_category
        (status, category, rank_score DESC, published_at DESC),
    INDEX idx_news_article_source (source_id, published_at DESC),
    INDEX idx_news_article_vietnam
        (status, vietnam_relevance DESC, rank_score DESC),
    CONSTRAINT fk_news_article_source
        FOREIGN KEY (source_id) REFERENCES news_sources(id),
    CONSTRAINT chk_news_article_category CHECK (
        category IN ('CULTURE', 'FOOD', 'ENTERTAINMENT', 'SCHOLARSHIP')
    ),
    CONSTRAINT chk_news_article_status CHECK (
        status IN ('PUBLISHED', 'REJECTED')
    ),
    CONSTRAINT chk_news_article_vietnam_relevance CHECK (
        vietnam_relevance BETWEEN 0 AND 100
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_vocabularies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    target_code VARCHAR(40) NOT NULL,
    korean_word VARCHAR(120) NOT NULL,
    pronunciation VARCHAR(180) NULL,
    part_of_speech VARCHAR(80) NULL,
    word_level VARCHAR(80) NULL,
    meaning_vi TEXT NOT NULL,
    dictionary_url VARCHAR(2048) NOT NULL,
    display_order INT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    UNIQUE INDEX uq_news_vocab_article_word (article_id, korean_word),
    INDEX idx_news_vocab_article_order (article_id, display_order),
    CONSTRAINT fk_news_vocab_article
        FOREIGN KEY (article_id) REFERENCES news_articles(id) ON DELETE CASCADE,
    CONSTRAINT chk_news_vocab_display_order CHECK (display_order BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_ingestion_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_count INT NOT NULL DEFAULT 0,
    fetched_count INT NOT NULL DEFAULT 0,
    published_count INT NOT NULL DEFAULT 0,
    rejected_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,

    INDEX idx_news_ingestion_run_started (started_at DESC),
    CONSTRAINT chk_news_ingestion_trigger CHECK (
        trigger_type IN ('SCHEDULED', 'MANUAL')
    ),
    CONSTRAINT chk_news_ingestion_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_ingestion_locks (
    lock_name VARCHAR(80) PRIMARY KEY,
    locked_until DATETIME(6) NULL,
    locked_by VARCHAR(120) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO news_ingestion_locks (lock_name)
VALUES ('korea-discovery-ingestion');

INSERT INTO news_sources (
    code, name, source_type, feed_url, site_url,
    default_category, language_code, priority_weight, enabled
) VALUES
    (
        'KBS_WORLD_CULTURE_VI',
        'KBS WORLD Vietnamese · Văn hóa',
        'RSS',
        'https://world.kbs.co.kr/rss/rss_news.htm?lang=v&id=Cu',
        'https://world.kbs.co.kr/service/index.htm?lang=v',
        'CULTURE',
        'vi',
        72,
        TRUE
    ),
    (
        'KBS_WORLD_ENTERTAINMENT_VI',
        'KBS WORLD Vietnamese · Giải trí',
        'RSS',
        'https://world.kbs.co.kr/rss/rss_enternews.htm?lang=v',
        'https://world.kbs.co.kr/service/contents_list.htm?lang=v&menu_cate=enternews',
        'ENTERTAINMENT',
        'vi',
        68,
        TRUE
    ),
    (
        'KOREA_NET_FOOD_TRAVEL_VI',
        'Korea.net Vietnamese · Ẩm thực & Du lịch',
        'KOREA_NET_HTML',
        'https://vietnamese.korea.net/NewsFocus/FoodTravel',
        'https://vietnamese.korea.net/NewsFocus/FoodTravel',
        'FOOD',
        'vi',
        84,
        TRUE
    ),
    (
        'KOREA_NET_CULTURE_VI',
        'Korea.net Vietnamese · Văn hóa',
        'KOREA_NET_HTML',
        'https://vietnamese.korea.net/NewsFocus/Culture',
        'https://vietnamese.korea.net/NewsFocus/Culture',
        'CULTURE',
        'vi',
        84,
        TRUE
    ),
    (
        'STUDY_IN_KOREA_GKS',
        'Study in Korea · GKS Notices',
        'STUDY_IN_KOREA_JSON',
        'https://www.studyinkorea.go.kr/plan/getGksNoticeList.do?bbsId=BBSMSTR_000000000461&page=1&boardSort=3&searchSort=&searchValue=',
        'https://www.studyinkorea.go.kr/eng/plan/scholarship.do?tab=gks-tab4',
        'SCHOLARSHIP',
        'en',
        92,
        TRUE
    );
