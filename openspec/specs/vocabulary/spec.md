# KSH-5: Korean Vocabulary Module Epic [Sprint 5 — Planned]

## Overview

The core learning feature of KSH: a Korean vocabulary system where lecturers
create vocabulary sets, students practice with flashcards and quizzes,
and progress is tracked per user.

**Sprint:** 5 (Planned)
**Labels:** vocabulary, learning, core
**Priority:** High

---

## KSH-5.1 — Story: Vocabulary Sets

> As a LECTURER, I want to create, edit, and publish vocabulary sets (단어장)
> containing Korean words with definitions, pronunciation, and examples
> so that students can study them.

### KSH-5.1-DB: [DB] Vocabulary Schema

**Status:** Not started

Planned tables:
```sql
-- vocabulary_sets
id, title, description, class_id (nullable — standalone or class-linked),
  created_by, is_published, created_at

-- vocabulary_items
id, set_id, korean_word, romanization, meaning_vi, meaning_en,
  example_sentence, audio_url, image_url, order_index

-- vocabulary_progress
id, user_id, item_id, confidence_level (0–5), last_reviewed_at, next_review_at
```

Migration: `V12__vocabulary.sql` (planned)

---

### KSH-5.1-BE: [BE] Vocabulary Set CRUD

**Status:** Not started

Planned package: `com.ksh.vocabulary`

- `VocabularySetController` — `GET/POST /lecturer/vocabulary/**`
- `VocabularySetService`
- `VocabularyItemService`

**Authorization:**
- LECTURER: manage own sets
- STUDENT: read published sets only
- HEAD/ADMIN: manage all

---

### KSH-5.1-FE: [FE] Vocabulary Set Management

**Status:** Not started

Planned templates:
- `vocabulary/manage.html` — list of sets
- `vocabulary/form.html` — create/edit set + add items
- `vocabulary/study.html` — flashcard UI for students
- `vocabulary/quiz.html` — multiple choice quiz

---

## KSH-5.2 — Story: Spaced Repetition Practice

> As a STUDENT, I want to practice vocabulary with a spaced repetition system
> so that I can efficiently memorize Korean words over time.

### KSH-5.2-BE: [BE] Spaced Repetition Engine

**Status:** Not started

- Implements SM-2 algorithm (or simplified version)
- Endpoint: `POST /student/vocabulary/{itemId}/review` — accepts confidence rating 0–5,
  computes `next_review_at` and saves to `vocabulary_progress`
- Endpoint: `GET /student/vocabulary/due` — returns items due for review today

---

## KSH-5.3 — Story: Korean Pronunciation Audio [Sprint 5 — Planned]

> As a STUDENT, I want to hear the pronunciation of Korean words
> so that I can learn correct pronunciation alongside reading.

**Status:** Not started

Options:
1. Upload audio files (stored in `uploads/audio/`) — lecturers record MP3
2. Text-to-speech integration (Korean TTS API) — auto-generated
3. Embedded YouTube clips per item

Decision: TBD in Sprint 5 design phase.
