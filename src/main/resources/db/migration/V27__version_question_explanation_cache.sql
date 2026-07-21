CREATE TABLE practice_writing_evaluation_cache (
                                                   cache_key CHAR(64) PRIMARY KEY,
                                                   user_scope_hash CHAR(64) NOT NULL,
                                                   task_type VARCHAR(20) NOT NULL,
                                                   model VARCHAR(128) NOT NULL,
                                                   prompt_version VARCHAR(32) NOT NULL,
                                                   rubric_version VARCHAR(32) NOT NULL,
                                                   evaluation_schema_version VARCHAR(32) NOT NULL,
                                                   result_json LONGTEXT NOT NULL,
                                                   expires_at DATETIME NOT NULL,
                                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                   INDEX idx_pwec_expires_at (expires_at),
                                                   INDEX idx_pwec_user_scope_hash (user_scope_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;