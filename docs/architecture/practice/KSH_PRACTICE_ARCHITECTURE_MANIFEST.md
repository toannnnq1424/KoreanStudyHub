# KSH Practice Architecture Artifact Manifest

Status: `PRE_13E_ARCHITECTURE_BASELINE`

Generated for branch: `feature/practice-reduce-scope`

Date: 2026-07-17

## 1. Purpose

This package is the traceable architecture baseline required before Phase 13E.
It decomposes `/practice` into bounded capabilities, links formal Use Cases to
current Spring Boot code, and distinguishes implemented classes from approved
13E/13F plans.

The package contains:

- `KSH_PRACTICE_USE_CASE_SPECIFICATIONS.docx`: formal Use Case specifications;
- `KSH_PRACTICE_ARCHITECTURE.drawio.xml`: valid, uncompressed, multi-page
  diagrams.net source;
- this manifest: scope, status and code traceability;
- `scripts/docs/generate_practice_architecture_artifacts.py`: reproducible
  generator for the DOCX and Draw.io artifacts.

The supplied `SEP490_G103_KoreanHub.drawio.xml` is not modified. The local copy
is truncated and cannot be used as a complete 84-page source.

## 2. Diagram Status Legend

- `CURRENT`: class/interaction exists at the current branch HEAD.
- `PLANNED 13E`: approved Result Detail target, not yet production code.
- `PLANNED 13F`: approved progress/recovery target, not yet production code.
- `DEFERRED 13H`: browser/device/accessibility/performance validation boundary.
- `DEFERRED 15`: compatibility cleanup or premium seed/UAT debt.

Planned nodes are visually distinct and must never be cited as existing
implementation evidence.

## 3. Capability Decomposition

| Code | Capability | Primary actors | Current implementation boundary | Planned boundary |
|---|---|---|---|---|
| CAT | Catalog, access and attempt entry | Learner | `PracticeController`, `PracticeCatalogService`, `PracticeDetailPageService`, `PracticeLearnerAccessService`, `PracticeService` | 13G scale/a11y checks |
| AUT | Manual authoring, publish and revision | Lecturer, collaborator | `PracticeDraftController`, `PracticeManageController`, `PracticeDraftService`, `PracticePublisherService`, governance and immutable version services | Phase 15 compatibility cleanup only |
| XLS | Excel template, preview and import | Lecturer | `PracticeAssessmentExcelController`, `PracticeAssessmentExcelService`, `PracticeAssessmentExcelV2Codec`, `PracticeImportDraftService` | 13G responsive preview review |
| PDF | PDF workspace and AI-assisted draft import | Lecturer | `PracticeImportController`, `PracticePdfImportApiController` and PDF session/region/crop/payload/orchestrator/assembler services | 13G workspace review; provider UAT later |
| PLY | Skill-native player and attempt lifecycle | Learner | `PracticeController`, `PracticeService`, immutable snapshot/scoring contracts and speaking media endpoint | 13H journey/device closure |
| RLE | Reading/Listening immutable explanation lifecycle | Lecturer/operator, learner | 13D event, preparation, fingerprint, binding, task, worker, retry, read services | 13E evidence rendering only |
| WRT | Korean Writing AI evaluation | Learner, reviewer | Writing client, normalizer, rubric/rule/score services, cache and current result presenter | 13E evidence detail |
| SPK | Speaking media, transcription and evaluation | Learner, reviewer | private media service/storage, transcription resolver/client, evaluation orchestrator/client and result presenter | 13E per-question evidence; multimodal audio scoring remains NO-GO |
| RES | Result overview and evidence detail | Learner | 13D `PracticeResultAssembler` and three skill-native presenters | 13E detail assembler/evidence presenters |
| PRG | Progress aggregates and recovery | Learner, lecturer/operator | bounded `PracticeService.getProgressPageData` and current `/practice/progress` page | 13F real filters, confidence/recency and recovery UX |

## 4. Use Case And Sequence Traceability

Each capability owns exactly three formal Use Cases. Every Use Case has one
corresponding sequence diagram, so the artifact contains 30 sequence diagrams.

| Capability | Use Cases / sequence pages |
|---|---|
| CAT | `UC-CAT-01` browse/filter catalog; `UC-CAT-02` inspect set/test; `UC-CAT-03` start/resume/discard with immutable lock and preflight |
| AUT | `UC-AUT-01` create/edit/autosave draft; `UC-AUT-02` validate/publish immutable graph; `UC-AUT-03` revision/restore/collaboration |
| XLS | `UC-XLS-01` download template; `UC-XLS-02` preview workbook/media; `UC-XLS-03` import canonical draft |
| PDF | `UC-PDF-01` create session/inspect pages; `UC-PDF-02` annotate/crop/preview payload; `UC-PDF-03` AI generate/retry/manual fallback |
| PLY | `UC-PLY-01` device preflight; `UC-PLY-02` play/save/resume; `UC-PLY-03` submit/timeout/discard |
| RLE | `UC-RLE-01` prepare artifacts after commit; `UC-RLE-02` generate/reuse/retry; `UC-RLE-03` immutable read-only result lookup |
| WRT | `UC-WRT-01` evaluate submitted essay; `UC-WRT-02` reuse/re-evaluate feedback; `UC-WRT-03` review Korean rubric evidence |
| SPK | `UC-SPK-01` manage private recording; `UC-SPK-02` transcribe/evaluate; `UC-SPK-03` review holistic and per-question evidence |
| RES | `UC-RES-01` view result overview; `UC-RES-02` view evidence detail; `UC-RES-03` handle pending/failed/unavailable states |
| PRG | `UC-PRG-01` view real aggregates; `UC-PRG-02` filter/drill down; `UC-PRG-03` recover/retry operational feedback |

## 5. Current Draw.io Layout

The current generated Draw.io source happens to contain 52 editable pages:

1. one feature decomposition page;
2. one system context/status legend page;
3. ten capability Use Case diagrams;
4. ten capability class diagrams;
5. thirty Use Case sequence diagrams.

This count is a layout result derived from ten Practice capabilities and thirty
Use Cases. It is not a user requirement or acceptance target. The package is
strictly limited to `/practice`; no Flashcard, lesson, iConstant or other
product-module diagrams are included.

Page names use stable prefixes: `00`, `01`, then `CAT`, `AUT`, `XLS`, `PDF`,
`PLY`, `RLE`, `WRT`, `SPK`, `RES` and `PRG`.

## 6. Current Code Evidence

Primary source areas reviewed:

- `src/main/java/com/ksh/features/practice/controller/`;
- `src/main/java/com/ksh/features/practice/manage/controller/`;
- `src/main/java/com/ksh/features/practice/manage/service/`;
- `src/main/java/com/ksh/features/practice/service/`;
- `src/main/java/com/ksh/features/practice/assessment/`;
- `src/main/java/com/ksh/features/practice/ai/readinglistening/`;
- `src/main/java/com/ksh/features/practice/ai/writing/`;
- `src/main/java/com/ksh/features/practice/ai/speaking/`;
- `src/main/java/com/ksh/features/practice/result/`;
- `src/main/java/com/ksh/features/practice/repository/`;
- `src/main/java/com/ksh/entities/Practice*.java` and `LecturerAsset.java`.

Approved future boundaries come from:

- `PRACTICE_PHASE_10_16_EXECUTION_BLUEPRINT.md`, Phase 13E-13H;
- `docs/PRACTICE_PHASE_13_IMPLEMENTATION_AND_GATE.md`, Section 6.9.10;
- `docs/PRACTICE_PHASE_15_COMPATIBILITY_CLEANUP_AND_SEED_UAT_INVENTORY.md`.

## 7. Non-Negotiable Architecture Rules

- Attempt rendering and scoring use the immutable version locked on the attempt.
- Reading/Listening explanation GET paths are read-only and never call the AI
  provider.
- Explanation artifact identity is content fingerprint based, independent of
  live IDs, while bindings remain explicit per immutable question version.
- Learner answer/correctness is an overlay and is not part of the shared AI
  explanation artifact.
- Writing and Speaking provider failures do not fabricate scores.
- Speaking media remains private, owner-scoped and validated before storage.
- Phase 13E adds evidence presentation, not a second generation pipeline.
- Phase 13F adds aggregate/recovery UX, not decorative or invented percentages.
- Phase 13H owns final browser/device/a11y/performance and answer-leak checks.
- Historical compatibility cleanup and premium seed replacement remain Phase
  15 work, not a hidden prerequisite inside 13E production code.
