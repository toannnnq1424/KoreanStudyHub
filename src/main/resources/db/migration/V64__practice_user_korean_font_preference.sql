CREATE TABLE practice_user_preferences (
    user_id BIGINT NOT NULL,
    korean_font VARCHAR(32) NOT NULL DEFAULT 'NANUM_MYEONGJO',
    preference_schema_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_practice_user_preferences_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_practice_user_preferences_korean_font
        CHECK (korean_font IN (
            'NANUM_MYEONGJO',
            'GUNGSUH',
            'DOTUM',
            'BATANG',
            'YOON_GOTHIC'
        )),
    CONSTRAINT chk_practice_user_preferences_schema_version
        CHECK (preference_schema_version = 1)
);
