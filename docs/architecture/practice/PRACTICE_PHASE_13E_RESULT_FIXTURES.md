# Practice Phase 13E Result Fixtures

> **Historical-fixture note (`2026-07-22`, UX-05):** the Speaking 78-percent,
> six-row fixture below captured the pre-correction baseline and is not valid
> current contract evidence. Current validation must use F06/UX-05 transcript
> semantics and must not reinterpret this retained fixture as trusted scoring.

Status: `LOADED_BROWSER_BASELINE_REVIEWED`

Date: 2026-07-17

Branch: `feature/practice-reduce-scope`

## 1. Scope And Safety

These deterministic fixtures exist only for pre-13E visual comparison of the
Practice Result and Result Detail journeys. The loader explicitly selects the
disposable local schema `ksh_phase13e_result_ui`; it is not a Flyway migration
and must not be used as a production seed.

Loader:

```bash
MYSQL_PWD=sa123 mysql -h127.0.0.1 -uroot \
  < scripts/dev/practice-phase13e-result-fixtures.sql
```

The loader is idempotent. It was executed twice against the clean V1-V37 local
schema on 2026-07-17 with the same four attempts and explanation bindings.

Local learner login:

- email: `student@ksh.edu.vn`
- password: `password`

## 2. Stable Review URLs

| Skill | Attempt | Result overview | Result detail |
|---|---:|---|---|
| Reading | `13001` | `http://localhost:8080/practice/attempts/13001/result` | `http://localhost:8080/practice/attempts/13001/result/detail` |
| Listening | `13002` | `http://localhost:8080/practice/attempts/13002/result` | `http://localhost:8080/practice/attempts/13002/result/detail` |
| Writing | `13003` | `http://localhost:8080/practice/attempts/13003/result` | `http://localhost:8080/practice/attempts/13003/result/detail` |
| Speaking | `13004` | `http://localhost:8080/practice/attempts/13004/result` | `http://localhost:8080/practice/attempts/13004/result/detail` |

## 3. Fixture Intent

- Reading deliberately contains one correct and one incorrect answer so the
  detail page can show official-answer, learner-answer and explanation states.
- Listening contains one correct answer and a READY immutable explanation.
- Writing contains a Q53 essay, an 80-percent result and evidence across the
  current Korean TOPIK rubric rows.
- Speaking contains a holistic 78-percent evaluation using the current six
  Korean Speaking criteria and the `speaking_ai_v1` feedback envelope.
- Reading and Listening explanation GET paths use READY fixture artifacts and
  do not invoke an AI provider during browser review.

The fixtures are review data, not a replacement for Phase 15 premium seed/UAT
data.

## 4. Authenticated Browser Baseline

Step 7 was completed on 2026-07-17 with the local learner account:

- all eight routes loaded without redirect, HTTP 500 or browser console
  warning/error;
- all four overviews rendered their skill-specific persisted data;
- Reading and Listening Detail rendered READY immutable explanations without
  invoking an AI provider on GET;
- Writing and Speaking tabs changed visible content as expected;
- all routes avoided horizontal overflow at the available 480-pixel mobile CSS
  viewport.

The baseline intentionally retains deterministic defects that Phase 13E must
fix:

- Listening Detail is labelled as Reading and the legacy shell lacks complete
  semantic landmarks;
- Writing Detail changes Q53 criterion maxima from `12/9/9` to `10/10/10`;
- Writing tabs have an incomplete tab/tabpanel ARIA contract and retain hidden
  cross-skill placeholders;
- Speaking Detail omits the coherence/organization criterion tab and leaks
  Writing/English fallback labels;
- the fixed mobile Detail footer obscures the final explanation action.

The authoritative finding decisions are `PRE13E-F10..F14` in
`docs/PRACTICE_PHASE_13E_LIVE_CHANGE_LOG.md`.
