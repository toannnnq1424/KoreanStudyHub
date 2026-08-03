-- Binds reviewer playback to the exact captured private media, without storing
-- audio bytes, storage keys, URLs, secrets or learner-visible scores.
-- V92 rows may remain historical but are deliberately not reviewer-playable.

ALTER TABLE practice_speaking_direct_audio_dark_observations
    ADD COLUMN question_id BIGINT NULL AFTER attempt_id,
    ADD COLUMN media_id BIGINT NULL AFTER question_id,
    ADD INDEX idx_psdado_reviewer_media
        (attempt_id, question_id, media_id, deleted_at, delete_after),
    ADD CONSTRAINT fk_psdado_question
        FOREIGN KEY (question_id) REFERENCES practice_questions(id),
    ADD CONSTRAINT fk_psdado_media
        FOREIGN KEY (media_id) REFERENCES practice_speaking_media(id);
