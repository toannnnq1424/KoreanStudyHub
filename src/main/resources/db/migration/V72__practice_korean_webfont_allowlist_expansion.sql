ALTER TABLE practice_user_preferences
    DROP CHECK chk_practice_user_preferences_korean_font;

UPDATE practice_user_preferences
SET korean_font = CASE korean_font
    WHEN 'SONG_MYUNG' THEN 'NANUM_MYEONGJO'
    WHEN 'IBM_PLEX_SANS_KR' THEN 'NANUM_GOTHIC'
    WHEN 'DO_HYEON' THEN 'NANUM_GOTHIC'
    WHEN 'JUA' THEN 'NANUM_GOTHIC'
    ELSE korean_font
END;

ALTER TABLE practice_user_preferences
    ADD CONSTRAINT chk_practice_user_preferences_korean_font
        CHECK (korean_font IN (
            'NANUM_MYEONGJO',
            'DIPHYLLEIA',
            'GOWUN_BATANG',
            'NOTO_SERIF_KR',
            'NANUM_GOTHIC',
            'GOTHIC_A1',
            'GOWUN_DODUM',
            'ORBIT',
            'SUNFLOWER',
            'BLACK_AND_WHITE_PICTURE',
            'GUGI',
            'POOR_STORY',
            'SINGLE_DAY',
            'GAEGU',
            'HI_MELODY',
            'NANUM_GOTHIC_CODING',
            'NANUM_PEN_SCRIPT'
        ));
