-- Phase 13C route performance: support skill-filtered practice catalog lookup.
CREATE INDEX idx_practice_sections_set_skill
    ON practice_sections (set_id, skill);
