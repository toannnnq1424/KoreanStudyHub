# KSH Practice Mermaid Sequence Diagrams

Status: `PRE_13E_ARCHITECTURE_BASELINE`

This document contains exactly 30 standalone Mermaid sequence diagrams, one for each formal Practice Use Case.
Copy only the code inside one fenced block into Mermaid Live Editor.

## 1. Practice Test Management

### Module AUT - Manual Authoring & Publish

#### UC-AUT-01 - Create, edit and autosave a Practice draft

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as Draft Editor
    participant P03 as PracticeDraftController
    participant P04 as PracticeDraftService
    participant P05 as PracticeDraftContractService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-AUT-01 - Create, edit and autosave a Practice draft
    P01->>P02: edit and autosave
    P02->>P03: submit canonical mutation
    P03->>P04: authorize + mutate
    P04->>P05: normalize and enforce rules
    P04->>P06: persist graph + edit log
    P06-->>P04: draft version
    P04-->>P03: saved draft model
    P03-->>P02: show saved state/errors
```

#### UC-AUT-02 - Validate and publish an immutable version

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as Manage View
    participant P03 as PracticeManageController
    participant P04 as PracticeDraftValidator
    participant P05 as PracticePublisherService
    participant P06 as Database
    participant P07 as After-commit Listener
    Note over P01,P07: CURRENT - UC-AUT-02 - Validate and publish an immutable version
    P01->>P02: validate and confirm publish
    P02->>P03: POST publish
    P03->>P04: validate complete graph
    P03->>P05: publish immutable snapshot
    P05->>P06: commit graph version
    P06-->>P05: published version
    P05->>P07: publish explanation event
    P03-->>P02: redirect to published status
```

#### UC-AUT-03 - Create a revision and manage collaborators

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Owner or Collaborator
    participant P02 as Manage View
    participant P03 as PracticeManageController
    participant P04 as PracticeRevisionService
    participant P05 as PracticeCollaborationService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-AUT-03 - Create a revision and manage collaborators
    P01->>P02: request revision/restore
    P02->>P03: submit action + reason
    P03->>P04: authorize and clone source graph
    P04->>P06: persist revision + source link
    P03->>P05: apply collaborator changes
    P05->>P06: persist permissions + audit
    P03-->>P02: open new draft
```

### Module XLS - Excel Import

#### UC-XLS-01 - Download a rules-aware Excel template

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as Excel Workspace
    participant P03 as PracticeAssessmentExcelController
    participant P04 as PracticeAssessmentExcelService
    participant P05 as AssessmentAuthoringCatalogService
    participant P06 as Excel Codec
    Note over P01,P06: CURRENT - UC-XLS-01 - Download a rules-aware Excel template
    P01->>P02: download template
    P02->>P03: GET template for draft
    P03->>P04: buildTemplate(context)
    P04->>P05: load allowed contract
    P04->>P06: write versioned workbook
    P06-->>P04: workbook bytes
    P04-->>P02: download .xlsx
```

#### UC-XLS-02 - Preview a workbook and resolve validation issues

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as Excel Workspace
    participant P03 as PracticeAssessmentExcelController
    participant P04 as PracticeAssessmentExcelService
    participant P05 as Excel Codec
    participant P06 as LecturerAssetService
    Note over P01,P06: CURRENT - UC-XLS-02 - Preview a workbook and resolve validation issues
    P01->>P02: upload workbook for preview
    P02->>P03: POST preview
    P03->>P04: preview(workbook)
    P04->>P05: decode + validate schema
    P04->>P06: resolve referenced media
    P04-->>P03: preview + diagnostics
    P03-->>P02: render structured preview
```

#### UC-XLS-03 - Import a workbook as a standard editable draft

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as Excel Workspace
    participant P03 as PracticeAssessmentExcelController
    participant P04 as PracticeAssessmentExcelService
    participant P05 as PracticeImportDraftService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-XLS-03 - Import a workbook as a standard editable draft
    P01->>P02: confirm import
    P02->>P03: POST import
    P03->>P04: revalidate workbook + context
    P04->>P05: materialize canonical graph
    P05->>P06: persist graph + snapshot atomically
    P06-->>P05: new draft graph
    P05-->>P03: import summary
    P03-->>P02: redirect to editor
```

### Module PDF - PDF Import Workspace

#### UC-PDF-01 - Upload a PDF and create an import session

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as PDF Workspace
    participant P03 as PracticeImportController
    participant P04 as PracticePdfImportSessionService
    participant P05 as PracticePdfPageExtractionService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-PDF-01 - Upload a PDF and create an import session
    P01->>P02: select PDF
    P02->>P03: create import session
    P03->>P04: authorize + create
    P04->>P05: extract bounded pages
    P05->>P06: persist page evidence
    P06-->>P04: session + pages
    P03-->>P02: render workspace
```

#### UC-PDF-02 - Select, crop and preview PDF regions

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as PDF Workspace
    participant P03 as PracticePdfImportApiController
    participant P04 as PracticePdfRegionService
    participant P05 as PracticePdfCropService
    participant P06 as PracticePdfPayloadPreviewService
    Note over P01,P06: CURRENT - UC-PDF-02 - Select, crop and preview PDF regions
    P01->>P02: draw region and request crop
    P02->>P03: save annotation
    P03->>P04: normalize + persist region
    P04->>P05: invalidate old and create crop
    P02->>P03: preview AI payload
    P03->>P06: build readable preview
    P06-->>P02: text/image evidence summary
```

#### UC-PDF-03 - Generate questions with AI and import a validated draft

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Lecturer
    participant P02 as PDF Workspace
    participant P03 as PracticePdfImportApiController
    participant P04 as PracticePdfAiOrchestrator
    participant P05 as AI Provider
    participant P06 as PracticeImportDraftService
    participant P07 as Database
    Note over P01,P07: CURRENT - UC-PDF-03 - Generate questions with AI and import a validated draft
    P01->>P02: confirm AI generation
    P02->>P03: POST generate
    P03->>P04: build audited request
    P04->>P05: generate structured questions
    P05-->>P04: result or typed failure
    P04->>P06: validate + materialize
    P06->>P07: persist canonical draft
    P03-->>P02: show draft/retry/manual fallback
```

## 2. Skill-based Attempt Lifecycle

### Module CAT - Catalog & Attempt Entry

#### UC-CAT-01 - Browse and filter the Practice catalog

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Browser View
    participant P03 as PracticeController
    participant P04 as PracticeCatalogService
    participant P05 as PracticeLearnerAccessService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-CAT-01 - Browse and filter the Practice catalog
    P01->>P02: open catalog with filters
    P02->>P03: GET /practice
    P03->>P04: query bounded catalog
    P04->>P05: resolve visible scope
    P04->>P06: fetch permitted page
    P06-->>P04: catalog rows
    P04-->>P03: page model
    P03-->>P02: render catalog or empty state
```

#### UC-CAT-02 - View Practice set and test details

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Browser View
    participant P03 as PracticeController
    participant P04 as PracticeDetailPageService
    participant P05 as PracticeLearnerAccessService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-CAT-02 - View Practice set and test details
    P01->>P02: select set/test
    P02->>P03: GET set/test detail
    P03->>P04: build detail model
    P04->>P05: authorize published graph
    P04->>P06: load tests, skills, active attempt
    P06-->>P04: detail rows
    P04-->>P03: detail + CTA state
    P03-->>P02: render details
```

#### UC-CAT-03 - Start, resume or discard an attempt

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Browser View
    participant P03 as PracticeController
    participant P04 as PracticeService
    participant P05 as PracticeAttemptRepository
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-CAT-03 - Start, resume or discard an attempt
    P01->>P02: start/resume/discard choice
    P02->>P03: POST attempt action
    P03->>P04: authorize and resolve active attempt
    P04->>P05: lock/find active attempt
    P05->>P06: persist immutable version lock
    P06-->>P05: attempt identity
    P04-->>P03: preflight/player route
    P03-->>P02: redirect
```

### Module PLY - Skill-native Player

#### UC-PLY-01 - Complete device checks and enter the correct player

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Preflight View
    participant P03 as Browser Media API
    participant P04 as PracticeController
    participant P05 as PracticeService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-PLY-01 - Complete device checks and enter the correct player
    P01->>P02: open device check
    P02->>P04: GET immutable preflight
    P04->>P05: load attempt preflight delivery
    P05->>P06: read locked media reference
    P04-->>P02: render sample + player target
    P02->>P03: start playback/permission
    P03-->>P02: playing/ready or error
    P02-->>P01: enable continue or show recovery
```

#### UC-PLY-02 - Answer, autosave, navigate and resume

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Skill Player
    participant P03 as PracticeController
    participant P04 as PracticeService
    participant P05 as PracticeAttemptRepository
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-PLY-02 - Answer, autosave, navigate and resume
    P01->>P02: open/resume player
    P02->>P03: GET attempt player
    P03->>P04: load immutable delivery
    P04->>P05: load state + answers
    P03-->>P02: render delivery without key
    P02->>P03: POST autosave
    P03->>P04: validate + save canonical answers
    P04->>P06: persist answers/deadline state
    P03-->>P02: saved/conflict state
```

#### UC-PLY-03 - Submit or finalize an attempt safely

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Skill Player
    participant P03 as PracticeController
    participant P04 as PracticeService
    participant P05 as AssessmentScoringEngine
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-PLY-03 - Submit or finalize an attempt safely
    P01->>P02: submit / confirm discard
    P02->>P03: POST terminal action
    P03->>P04: lock + validate state
    P04->>P05: score immutable objective answers
    P05-->>P04: score/status
    P04->>P06: persist terminal state atomically
    P06-->>P04: completed/discarded attempt
    P03-->>P02: redirect result or catalog
```

## 3. Versioned Results and Evidence

### Module RLE - R/L Explanation Lifecycle

#### UC-RLE-01 - Queue explanation preparation after publishing

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    participant P01 as Publisher
    participant P02 as After-commit Listener
    participant P03 as PreparationService
    participant P04 as InputFactory
    participant P05 as FingerprintBuilder
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-RLE-01 - Queue explanation preparation after publishing
    P01->>P02: published version event
    P02->>P03: prepare(versionId)
    P03->>P04: build immutable inputs
    P03->>P05: compute content identity
    P03->>P06: upsert artifact/binding/task
    P06-->>P03: prepared counts/status
    P03-->>P02: preparation result
```

#### UC-RLE-02 - Generate, reuse and retry explanation artifacts

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    participant P01 as Worker or Operator
    participant P02 as Task Worker
    participant P03 as GenerationProcessor
    participant P04 as WorkLoader
    participant P05 as AI Provider
    participant P06 as TaskTransactions
    participant P07 as Database
    Note over P01,P07: CURRENT - UC-RLE-02 - Generate, reuse and retry explanation artifacts
    P01->>P02: claim or request retry
    P02->>P06: claim durable task
    P02->>P03: process claimed work
    P03->>P04: load immutable input
    P03->>P05: generate when no READY reuse
    P05-->>P03: validated result or typed error
    P03->>P06: persist READY/failure state
    P06->>P07: commit artifact/task
```

#### UC-RLE-03 - Read an explanation with a student-specific answer overlay

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Result View
    participant P03 as PracticeController
    participant P04 as PracticeResultAssembler
    participant P05 as QuestionExplanationReadService
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-RLE-03 - Read an explanation with a student-specific answer overlay
    P01->>P02: open result/detail
    P02->>P03: GET attempt result
    P03->>P04: assemble immutable result
    P04->>P05: read by question-version binding
    P05->>P06: fetch binding + artifact status
    P06-->>P05: READY/pending/failed/unavailable
    P04-->>P02: render artifact + separate overlay
```

### Module WRT - Writing AI Evaluation

#### UC-WRT-01 - Evaluate a submitted Korean writing response

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    participant P01 as Submit Service
    participant P02 as WritingEvaluationCacheService
    participant P03 as WritingPromptRules
    participant P04 as WritingEvaluationClient
    participant P05 as AI Provider
    participant P06 as WritingEvaluationNormalizer
    participant P07 as Database
    Note over P01,P07: CURRENT - UC-WRT-01 - Evaluate a submitted Korean writing response
    P01->>P02: evaluate immutable answer
    P02->>P03: build Korean rubric request
    P02->>P04: request evaluation
    P04->>P05: provider call
    P05-->>P04: feedback or typed error
    P04->>P06: normalize + validate
    P02->>P07: persist status/score/evidence
```

#### UC-WRT-02 - Reuse or re-evaluate stored Writing feedback

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student or Reviewer
    participant P02 as Result View
    participant P03 as PracticeController
    participant P04 as WritingEvaluationCacheService
    participant P05 as WritingEvaluationClient
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-WRT-02 - Reuse or re-evaluate stored Writing feedback
    P01->>P02: open result or request re-evaluate
    P02->>P03: GET result / POST re-evaluate
    P03->>P04: resolve identity + permission
    P04->>P06: find reusable evaluation/history
    P04->>P05: call only for approved command
    P04->>P06: persist new provenance when needed
    P03-->>P02: render reuse/pending/result
```

#### UC-WRT-03 - Review Korean Writing criteria through four student-friendly views

Status: `PLANNED 13E`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Writing Detail View
    participant P03 as PracticeController
    participant P04 as PracticeResultDetailAssembler (13E)
    participant P05 as WritingEvidencePresenter (13E)
    participant P06 as Database
    Note over P01,P06: PLANNED 13E - UC-WRT-03 - Review Korean Writing criteria through four student-friendly views
    P01->>P02: open Writing detail
    P02->>P03: GET result/detail
    P03->>P04: assemble immutable evidence
    P04->>P06: read answer + evaluation provenance
    P04->>P05: map four Korean lenses
    P05-->>P02: render evidence and availability states
```

### Module SPK - Speaking Evaluation

#### UC-SPK-01 - Manage private attempt recordings

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Speaking Player
    participant P03 as PracticeSpeakingMediaController
    participant P04 as PracticeSpeakingMediaService
    participant P05 as SpeakingAudioPreparationService
    participant P06 as Private Storage
    Note over P01,P06: CURRENT - UC-SPK-01 - Manage private attempt recordings
    P01->>P02: record and upload
    P02->>P03: POST private recording
    P03->>P04: authorize media action
    P04->>P05: inspect and prepare audio
    P05->>P06: store private object
    P06-->>P04: opaque storage key
    P04-->>P02: active recording identity
```

#### UC-SPK-02 - Transcribe and evaluate a Speaking attempt

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    participant P01 as Submit Service
    participant P02 as SpeakingEvaluationOrchestrator
    participant P03 as Media Resolver
    participant P04 as Transcription Provider
    participant P05 as Evaluation Provider
    participant P06 as SpeakingRuleEngine
    participant P07 as Database
    Note over P01,P07: CURRENT - UC-SPK-02 - Transcribe and evaluate a Speaking attempt
    P01->>P02: evaluate submitted attempt
    P02->>P03: resolve authorized recording
    P03->>P04: transcribe private audio
    P04-->>P02: transcript + provenance
    P02->>P05: evaluate Korean rubric
    P05-->>P06: feedback/score candidate
    P06->>P07: persist validated status/evidence
```

#### UC-SPK-03 - Review a transcript-grounded Speaking profile and per-question evidence

Status: `PLANNED 13E`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Speaking Detail View
    participant P03 as PracticeController
    participant P04 as PracticeResultDetailAssembler (13E)
    participant P05 as SpeakingEvidencePresenter (13E)
    participant P06 as Private Media Endpoint
    Note over P01,P06: PLANNED 13E - UC-SPK-03 - Review a transcript-grounded profile and per-question evidence
    P01->>P02: open Speaking detail
    P02->>P03: GET result/detail
    P03->>P04: load typed profile + question evidence
    P04->>P05: map transcript/media/evidence
    P05->>P06: create authorized playback references
    P05-->>P02: render evidence-honest profile and per-question evidence
```

### Module RES - Result Overview & Detail

#### UC-RES-01 - View a skill-specific Result Overview

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Result View
    participant P03 as PracticeController
    participant P04 as PracticeResultAssembler
    participant P05 as Skill Result Presenter
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-RES-01 - View a skill-specific Result Overview
    P01->>P02: open overview
    P02->>P03: GET /result
    P03->>P04: assemble canonical context
    P04->>P06: read attempt/version/evaluation
    P04->>P05: present by skill family
    P05-->>P02: render overview/status
```

#### UC-RES-02 - View Result Detail with clearly separated evidence

Status: `PLANNED 13E`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Result Detail View
    participant P03 as PracticeController
    participant P04 as PracticeResultDetailAssembler (13E)
    participant P05 as Skill Evidence Presenter (13E)
    participant P06 as Read Services
    participant P07 as Database
    Note over P01,P07: PLANNED 13E - UC-RES-02 - View Result Detail with clearly separated evidence
    P01->>P02: open evidence detail
    P02->>P03: GET /result/detail
    P03->>P04: load immutable result context
    P04->>P06: read explanation/evaluation/media metadata
    P06->>P07: fetch version-bound evidence
    P04->>P05: map separated layers
    P05-->>P02: render evidence/status
```

#### UC-RES-03 - Handle pending, failed and unavailable result states

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student or Reviewer
    participant P02 as Result View
    participant P03 as PracticeController
    participant P04 as PracticeResultAssembler
    participant P05 as Retry or Re-evaluate Service
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-RES-03 - Handle pending, failed and unavailable result states
    P01->>P02: view state / choose recovery
    P02->>P03: GET status or POST command
    P03->>P04: map learner-safe status
    P03->>P05: authorize recovery command
    P05->>P06: transition durable status
    P03-->>P02: render updated state
```

## 4. Practice Progress Management

### Module PRG - Progress & Recovery

#### UC-PRG-01 - View real Practice history and summaries

Status: `CURRENT`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Progress View
    participant P03 as PracticeController
    participant P04 as PracticeService
    participant P05 as PracticeAttemptRepository
    participant P06 as Database
    Note over P01,P06: CURRENT - UC-PRG-01 - View real Practice history and summaries
    P01->>P02: open progress
    P02->>P03: GET /practice/progress
    P03->>P04: getProgressPageData(owner)
    P04->>P05: query bounded history
    P05->>P06: fetch attempt rows
    P06-->>P04: history + scores/status
    P03-->>P02: render aggregates/empty/recovery
```

#### UC-PRG-02 - Filter and drill down into progress

Status: `PLANNED 13F`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student
    participant P02 as Progress View
    participant P03 as PracticeController
    participant P04 as PracticeProgressQueryService (13F)
    participant P05 as Aggregate Repository (13F)
    participant P06 as Result Routes
    Note over P01,P06: PLANNED 13F - UC-PRG-02 - Filter and drill down into progress
    P01->>P02: apply filters
    P02->>P03: GET progress?filters
    P03->>P04: normalize + query
    P04->>P05: aggregate + history
    P05-->>P04: rows + sample metadata
    P04-->>P02: render metrics/confidence
    P02->>P06: open selected attempt
```

#### UC-PRG-03 - Open authorized recovery actions for incomplete evaluations

Status: `PLANNED 13F`

```mermaid
sequenceDiagram
    autonumber
    actor P01 as Student or Operator
    participant P02 as Progress View
    participant P03 as PracticeController
    participant P04 as PracticeRecoveryPresenter (13F)
    participant P05 as Existing Retry Services
    participant P06 as Database
    Note over P01,P06: PLANNED 13F - UC-PRG-03 - Open authorized recovery actions for incomplete evaluations
    P01->>P02: view state / choose recovery
    P02->>P03: GET progress or POST recovery
    P03->>P04: map safe operational state
    P03->>P05: authorize explicit command
    P05->>P06: persist durable transition
    P03-->>P02: render refreshed state
```
