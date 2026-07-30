ALTER TABLE practice_user_preferences
    DROP CHECK chk_practice_user_preferences_korean_font,
    DROP CHECK chk_practice_user_preferences_schema_version;

UPDATE practice_user_preferences
SET korean_font = CASE korean_font
    WHEN 'GUNGSUH' THEN 'SONG_MYUNG'
    WHEN 'DOTUM' THEN 'NANUM_GOTHIC'
    WHEN 'BATANG' THEN 'NANUM_MYEONGJO'
    WHEN 'YOON_GOTHIC' THEN 'IBM_PLEX_SANS_KR'
    ELSE korean_font
END;

ALTER TABLE practice_user_preferences
    ADD COLUMN korean_font_size VARCHAR(24)
        NOT NULL DEFAULT 'DEFAULT' AFTER korean_font;

UPDATE practice_user_preferences
SET preference_schema_version = 2;

ALTER TABLE practice_user_preferences
    ADD CONSTRAINT chk_practice_user_preferences_korean_font
        CHECK (korean_font IN (
            'NANUM_MYEONGJO',
            'SONG_MYUNG',
            'NANUM_GOTHIC',
            'IBM_PLEX_SANS_KR',
            'DO_HYEON',
            'JUA',
            'GAEGU'
        )),
    ADD CONSTRAINT chk_practice_user_preferences_korean_font_size
        CHECK (korean_font_size IN (
            'DEFAULT',
            'LARGE',
            'EXTRA_LARGE'
        )),
    ADD CONSTRAINT chk_practice_user_preferences_schema_version
        CHECK (preference_schema_version = 2);
