# Shared Korean Dictionary and Flashcard Capture

## Purpose and scope

KoreanStudyHub exposes a shared Korean-to-Vietnamese dictionary helper to authenticated users outside the bounded `/practice` context. It is not a news/content-discovery product and has no crawler, scheduler, editorial workflow, feed, source or article data model.

## Workflow

1. An Admin opens **Settings → Korean Dictionary** and stores the KRDICT API key and official `krdict.korean.go.kr` endpoint in the `DICTIONARY` settings group.
2. An authenticated user selects a Korean word or phrase in a supported KSH screen and opens the shared dictionary helper.
3. The server normalizes the Hangul input, calls only the official KRDICT host, securely parses the XML response, and returns the normalized pronunciation, Vietnamese meaning, part of speech and source URL.
4. The user requests their accessible Flashcard decks, chooses an owned deck, and saves a term.
5. The system creates a Flashcard with Korean on the front and the concise Vietnamese meaning on the back, or returns the existing matching card without creating a duplicate.

## Boundaries and safeguards

- The browser-facing endpoint is authenticated: `/api/korean-dictionary/lookup`, `/decks` and `/flashcards`.
- The outbound client permits HTTPS requests to `krdict.korean.go.kr` only, rejects redirects and caps a response at 2 MB.
- A user may only save into a deck they own; a duplicate is checked by deck and Korean front text.
- The global helper is disabled on `/practice`; Practice keeps its own AI and storage configuration.
- V112 retires the former Discovery News tables, routes, worker, source configuration and AI editorial prompt. This capability does not recreate a Discovery data model.
