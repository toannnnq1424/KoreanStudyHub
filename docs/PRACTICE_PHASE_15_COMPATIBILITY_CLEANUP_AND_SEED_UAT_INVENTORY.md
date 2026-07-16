# Practice Phase 15 Compatibility Cleanup And Seed UAT Inventory

> Status: `PHASE_13D_INVENTORY_ONLY`
>
> Recorded: `2026-07-16`
>
> Owner: Phase 15 debt closure and Manual UAT gate

## 1. Purpose and Phase 13D boundary

This document records compatibility paths, legacy test-data behavior, old
routes, adapters, fixtures and documentation that should be reviewed together
before the Phase 15 premium seed and Manual UAT.

This is an inventory, not a Phase 13D deletion list. Phase 13D must not remove,
guard, disable or rewrite the items below merely because they are listed here.
They remain available until Phase 15 performs an explicit impact review and a
single controlled cleanup batch.

The required order is:

1. finish and validate Phase 13D without deleting inventory items;
2. finish any replacement surface that an active compatibility path depends on,
   especially the Phase 13E result-detail replacement;
3. in Phase 15, confirm the target environment and data-retention obligation;
4. clean compatibility code, tests, routes, schema history and docs as one reviewed
   unit;
5. prove a fresh canonical database and no-legacy runtime contract;
6. only then create the high-quality deterministic seed and start Manual UAT.

## 2. Safety assumptions

- Current local data, including ad-hoc attempts such as `687` and `688`, is
  development fixture data and is not production evidence.
- The current project is treated as pre-production for planning purposes only.
  Phase 15 must reconfirm that no deployed database or retained learner attempt
  depends on a removal candidate before destructive schema/data work.
- If production or retained historical data exists, immutable attempt isolation
  and required migrations are not removable debt. The cleanup plan must migrate
  or archive that data before code removal.
- A confirmed removal candidate is still not authorization to delete it during
  Phase 13D.

## 3. Canonical target contract

Phase 15 cleanup and the final seed must converge on these contracts:

- Reading and Listening use only `SINGLE_CHOICE`, `FILL_BLANK` and
  `TRUE_FALSE_NOT_GIVEN`, backed by immutable published versions.
- Writing uses only Q51-Q54 as `ESSAY`, with fixed maximum points
  `10/10/30/50`, the Writing evaluator and task-native Korean rubrics.
- Speaking uses only `SPEAKING`, the Speaking evaluator and a holistic result
  overview. It must not route through Writing/ESSAY grading.
- Every new attempt has a complete immutable published/set/test/section version
  lock. Result, progress and media access do not read mutable live content.
- Reading/Listening explanations use the Phase 13D artifact, version binding and
  durable task lifecycle. Result GET remains read-only.

## 4. Classification

| Status | Meaning |
| --- | --- |
| `REMOVE_CANDIDATE_CONFIRMED` | Static evidence shows the path is unused or contradicts the canonical production contract. Phase 15 still performs impact review before removal. |
| `REVIEW_REQUIRED` | The path is active or may protect historical data. Remove only after its replacement/data decision is complete. |
| `KEEP` | The behavior is intentional architecture or security and must survive cleanup. |

## 5. Compatibility and legacy inventory

| ID | Status | Current evidence | Phase 15 action | Acceptance proof |
| --- | --- | --- | --- | --- |
| `P15-COMP-01` | `REVIEW_REQUIRED` | `WritingResultPresenter` still locally scores historical non-ESSAY Writing questions from immutable content/answer specs. `PracticeResultPresenterTest.historicalWritingFillBlankUsesLockedAnswerSpecWithoutAiFeedback` pins that behavior in tests. | After confirming no retained attempt requires it, remove the historical non-ESSAY Writing branch and its fixture. Keep immutable ESSAY history. | Every Writing version in the fresh database is Q51-Q54 `ESSAY`; no local objective scoring branch remains in the Writing presenter. |
| `P15-COMP-02` | `REVIEW_REQUIRED` | `PracticeService.NonWritingEssayGradingSnapshot` lets non-Writing ESSAY content enter Writing evaluation on submit and re-evaluation. | Remove the snapshot/load/verify/grade path after Speaking data is canonical and no other skill is allowed to publish ESSAY. | Non-Writing submission has no call path to the Writing evaluator; a contract test rejects non-Writing ESSAY authoring/publication. |
| `P15-COMP-03` | `REVIEW_REQUIRED` | `PracticeService` and `SpeakingResultPresenter` retain `speaking_mixed_v1` and `essay_feedback_by_question`; old ESSAY feedback can be merged into a holistic Speaking result. | Remove the mixed feedback contract and ESSAY merge after P15-COMP-02 and retained-attempt review. | Speaking persistence/result contracts contain only Speaking feedback; no `essay_feedback_by_question` reference remains. |
| `P15-COMP-04` | `REVIEW_REQUIRED` | `SpeakingFeedbackCompatibilityReader`, `SpeakingEvaluationReusePolicy`, `SpeakingEvaluationStatus.LEGACY_RESULT` and `WritingFeedbackCompatibilityReader` retain legacy payload/status branches. | Build a current-payload inventory, migrate or discard test rows, then remove only statuses/readers no longer needed by the current provider contract. | Current provider fixtures still parse; removed legacy statuses have no source/test/schema references. |
| `P15-COMP-05` | `REVIEW_REQUIRED` | `PracticeService.loadProgressAttemptData`, `progressQuestions`, `versionSnapshot` and related result/player paths retain live-question fallbacks for attempts with no version lock. | Reset test data or migrate retained attempts, then make incomplete/missing locks fail closed or appear as an explicit unavailable historical record. | New and seeded attempts all have complete locks; progress/result never query live questions for an attempt. |
| `P15-COMP-06` | `REVIEW_REQUIRED` | `PracticePublishedVersionService` and `PracticeService` retain single-section ungrouped-question compatibility plus synthetic/orphan group rendering. | Canonicalize seed/import data into groups, then remove only fallback branches unsupported by the final authoring contract. | Fresh publication contains no orphan question version; player/result tests use canonical groups only. |
| `P15-COMP-07` | `REVIEW_REQUIRED` | `PracticeRoutes` and `PracticeController` still expose legacy set/mode/room/submit/submission routes and redirect handlers. | Inventory real callers and bookmarks, retain intentional redirects for one documented window or remove them together with route tests. | Each old route is either absent or has one documented redirect contract; no hidden service path depends on it. |
| `P15-COMP-08` | `REVIEW_REQUIRED` | `QuestionTypeResolver`, editor/player JavaScript and templates still accept aliases such as `MCQ`, `MCQ_SINGLE`, `TFNG` and `GAP_FILL`. | Normalize/reset imported fixture data, then shrink runtime authoring/player aliases while preserving only any explicitly supported import boundary. | Persisted live/version question types are canonical; runtime templates do not branch on removed aliases. |
| `P15-COMP-09` | `REVIEW_REQUIRED` | `PracticeAssessmentExcelService` accepts `practice-excel-v1`; `PracticeAssessmentExcelV2Codec` and draft adapters still emit/read legacy `options`, `answerKey` and answer aliases beside canonical content/spec fields. | Decide the supported import window, remove v1/runtime duplicate fields together, and version the final template contract. | One documented Excel schema round-trips canonical fields without duplicate legacy answer sources. |
| `P15-COMP-10` | `REVIEW_REQUIRED` | Active detail routes still use `PracticeService.getResult`, `getReadingListeningResult`, `PracticeResultView`, `ReadingListeningResultView`, `result-detail.html` and `rl-result-detail.html`. | Do not remove before Phase 13E supplies the canonical evidence detail. After replacement, delete the old DTO/service/template/script cluster in one patch. | `/attempts/{id}/result/detail` uses only the 13E contract; old DTO/template symbols have no references. |
| `P15-COMP-11` | `REMOVE_CANDIDATE_CONFIRMED` | `src/main/resources/static/js/practice.js` is not loaded by a template/controller. `PracticeResultWordingTest` reads it directly, so tests currently preserve an otherwise dead asset. | Remove the asset and tests that exist only to inspect it, after a final resource-reference scan. | No template/resource/controller reference exists before deletion; current result assets cover required wording/behavior. |
| `P15-COMP-12` | `REVIEW_REQUIRED` | Tests still deliberately create historical Writing fill-blank, Speaking ESSAY/mixed feedback, ungrouped questions, no-version attempts, old routes and legacy import aliases. | Classify each fixture as current regression, migration fixture or obsolete test-data compatibility. Delete obsolete fixtures with the production branch they pin. | Test names and fixtures describe only canonical runtime or an explicitly retained migration boundary. |
| `P15-COMP-13` | `REVIEW_REQUIRED` | `V25__practice_hub.sql`, `V33__practice_immutable_versions.sql` and `V34__practice_single_scope_final.sql` carry old development seed, graph/type migration and alias normalization history. The files were renumbered without SQL changes when Practice joined `main` after its V24 migration. | First prove the project has no deployed database obligation. Then choose either an immutable deployed migration chain or a reviewed pre-production baseline squash plus fresh rehearsal. | Fresh schema and representative upgrade both have recorded outcomes; no migration is edited/deleted without deployment evidence. |
| `P15-COMP-14` | `REVIEW_REQUIRED` | Current and historical docs still mention retired `rl-result.html`, `QuestionExplanationCache`, lazy explanation generation and other superseded paths. Historical phase evidence may be intentionally archival. | Separate current contracts from historical records. Correct current specs/runbooks and preserve historical evidence with explicit superseded labels. | Current specs match source; archival docs are clearly dated and are not used as implementation instructions. |
| `P15-COMP-15` | `REVIEW_REQUIRED` | Local database fixtures and ad-hoc attempts such as `687`/`688` were created for UI investigation and do not form a coherent product seed. | Inventory IDs needed only for debugging, export evidence if required, then reset the dedicated local/UAT database before premium seed loading. | UAT starts from a documented clean database and deterministic seed manifest, not ad-hoc rows. |

## 6. Architecture that must remain

| ID | Status | Required behavior |
| --- | --- | --- |
| `P15-KEEP-01` | `KEEP` | Immutable published version graph and old-attempt isolation. A cleanup may remove unsupported legacy shapes, but must never make an attempt read the newest live question/version. |
| `P15-KEEP-02` | `KEEP` | Canonical `AssessmentContractCodec`, question-type validation and scoring for supported objective types. Only obsolete adapters/aliases are candidates. |
| `P15-KEEP-03` | `KEEP` | Current external-provider response normalization that is still required by tested provider payloads. Compatibility with a live API is not legacy debt merely because it normalizes fields. |
| `P15-KEEP-04` | `KEEP` | Material ownership, immutable reference checks, lifecycle states, private access and audit/security behavior. Data reset does not authorize weakening these controls. |
| `P15-KEEP-05` | `KEEP` | Phase 13D explanation artifact, question-version binding, durable task, retry, reconciliation and read-only result lifecycle introduced by `V37__question_explanation_artifact_lifecycle.sql`. |
| `P15-KEEP-06` | `KEEP` | The result-detail route until Phase 13E has replaced its implementation and equivalent learner evidence is verified. |

## 7. Phase 15 cleanup gate

Phase 15 executes this as one reviewable cleanup program, not as isolated
deletions discovered during Manual UAT:

1. **Environment freeze and proof**: identify local/UAT/production databases,
   backup retained data, record migration versions and reconfirm that destructive
   reset/squash is limited to an approved non-production environment.
2. **Inventory resolution**: assign each `P15-COMP-*` item `REMOVE`, `MIGRATE`,
   `RETAIN_WITH_EXPIRY` or `KEEP`, with owner and evidence.
3. **Controlled cleanup batch**: remove obsolete code, route, DTO, template,
   static asset, test, seed and current-doc references together so no parallel
   half-path remains.
4. **Static no-legacy audit**: scan canonical types, route aliases, old feedback
   contracts, no-version fallbacks, dead resources and superseded result/cache
   names. Any retained hit must point to an approved migration boundary.
5. **Database rehearsal**: run fresh migration/schema validation and any required
   representative legacy upgrade. Record why migrations were kept or squashed.
6. **Automated gate**: compile once, run the smallest focused cleanup/migration
   tests, required integration tests and the full suite once for release
   confirmation.
7. **Premium seed creation**: load only after the cleanup gate is green.
8. **Manual UAT**: run role, browser, device, media, immutable-result and AI
   journeys against the premium seed; capture screenshots and GO/NO-GO evidence.

## 8. Premium canonical seed checklist

The Phase 15 seed must be deterministic, versioned and suitable for product UAT:

- one coherent `Set > Test > Skill > Section > Group > Question` hierarchy;
- Reading/Listening questions with Korean passage/transcript, valid audio/image
  assets, answer specs, teacher explanations and READY explanation artifacts;
- Writing Q51-Q54 as ESSAY with realistic Korean prompts, maximum points
  `10/10/30/50` and rubric-compatible responses/feedback;
- Speaking questions only as `SPEAKING`, with valid prompt audio/recording
  targets and a holistic Speaking result fixture;
- deterministic in-progress, submitted, pending, graded and failed-provider
  attempts, all with complete immutable version locks;
- documented development-only learner, lecturer and reviewer credentials plus
  stable seed IDs where UAT automation needs them;
- original/licensed or project-owned content, not placeholders or copied PREP
  branding/assets;
- no non-Writing ESSAY, no historical Writing fill-blank, no orphan questions,
  no missing required media and no mock/provider wording presented as real AI.

## 9. Manual UAT exit evidence

Phase 15 cannot declare the cleanup/seed step complete until:

- every inventory item has a recorded resolution and evidence;
- static scans contain no unexplained confirmed-removal symbols;
- a fresh canonical database reaches the expected latest migration;
- the premium seed manifest matches persisted question types, weights, assets
  and immutable version locks;
- runtime logs show no unexpected compatibility fallback during the UAT matrix;
- old routes are either intentionally documented redirects or return the
  approved unavailable response;
- old ad-hoc attempts are absent from the dedicated UAT database;
- focused, integration and full-suite evidence is recorded once after the
  cleanup batch; and
- Manual UAT screenshots/results identify capability-specific GO/NO-GO status.

## 10. Linked gates

- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, Sections 6.9 and 6.9.12
- `CODEX_PRACTICE_WORKFLOW.md`, latest Phase 13D status and current next action
- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`, Phase 15 cleanup/seed/UAT order
