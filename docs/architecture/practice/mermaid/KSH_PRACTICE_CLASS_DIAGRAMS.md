# KSH Practice Mermaid Class Diagrams

Status: `PRE_13E_ARCHITECTURE_BASELINE`

Each fenced block is a standalone Mermaid diagram. Copy only the code inside one block into Mermaid Live Editor.
The four reader-facing areas preserve the ten internal module codes used by code, Draw.io and Jira.

## 1. Practice Test Management

Modules: `AUT, XLS, PDF`

```mermaid
classDiagram
    direction LR
    note "Status: green = CURRENT; amber dashed = PLANNED 13E; blue dashed = PLANNED 13F"
    namespace AUT_Manual_Authoring_Publish {
        class PracticeDraftController {
            <<Controller>>
            +draftEditorEndpoints()
            +autosaveRequests()
        }
        class PracticeManageController {
            <<Controller>>
            +managePublishRevisionRoutes()
            +feedbackStates()
        }
        class PracticeDraftService {
            <<Service>>
            +mutateDraftGraph()
            +editLogging()
        }
        class PracticeDraftContractService {
            <<Service>>
            +canonicalContract()
            +skillTypeConstraints()
        }
        class PracticeDraftValidator {
            <<Validator>>
            +publishValidation()
            +actionableViolations()
        }
        class PracticePublisherService {
            <<Service>>
            +immutableSnapshotPublish()
            +afterCommitEvent()
        }
        class PracticeRevisionService {
            <<Service>>
            +revisionRestore()
            +publishedGraphSafety()
        }
        class PracticeCollaborationService {
            <<Service>>
            +collaboratorPermissions()
            +auditTrail()
        }
    }
    namespace XLS_Excel_Import {
        class PracticeAssessmentExcelController {
            <<Controller>>
            +templatePreviewImportRoutes()
            +draftContextChecks()
        }
        class PracticeAssessmentExcelService {
            <<Service>>
            +buildTemplate()
            +previewImportOrchestration()
        }
        class PracticeAssessmentExcelV2Codec {
            <<Codec>>
            +workbookReadWrite()
            +schemaValidation()
        }
        class AssessmentAuthoringCatalogService {
            <<Service>>
            +allowedSkillsTypes()
            +contractChoices()
        }
        class PracticeImportDraftService {
            <<Service>>
            +canonicalDraftMaterialization()
            +replaceImportBoundary()
        }
        class PracticeImportSnapshotService {
            <<Service>>
            +sourceSnapshot()
            +auditReplayEvidence()
        }
        class LecturerAssetService {
            <<Service>>
            +resolveMediaLinks()
            +ownershipChecks()
        }
        class PracticeDraftRepository {
            <<Repository>>
            +linkedDraftLookup()
            +draftPersistence()
        }
    }
    namespace PDF_PDF_Import_Workspace {
        class PracticeImportController {
            <<Controller>>
            +workspaceRoutes()
            +sessionEntry()
        }
        class PracticePdfImportApiController {
            <<APIController>>
            +pageRegionCropPayloadAPIs()
            +aiActions()
        }
        class PracticePdfImportSessionService {
            <<Service>>
            +sessionLifecycle()
            +ownershipStatus()
        }
        class PracticePdfPageExtractionService {
            <<Service>>
            +pageImagesText()
            +boundedExtraction()
        }
        class PracticePdfRegionService {
            <<Service>>
            +regionAnnotations()
            +staleCropInvalidation()
        }
        class PracticePdfCropService {
            <<Service>>
            +pixelCrop()
            +assetSelection()
        }
        class PracticePdfPayloadPreviewService {
            <<Service>>
            +humanReadableAIPayload()
            +evidenceSummary()
        }
        class PracticePdfAiOrchestrator {
            <<Service>>
            +providerCallRetry()
            +draftAssemblyFallback()
        }
    }
    PracticeDraftController --> PracticeDraftService : edits
    PracticeDraftService --> PracticeDraftContractService : applies contract
    PracticeManageController --> PracticeDraftValidator : validates
    PracticeManageController --> PracticePublisherService : publishes
    PracticePublisherService --> PracticeRevisionService : creates baseline
    PracticeDraftService --> PracticeCollaborationService : authorizes collaborator
    PracticeAssessmentExcelController --> PracticeAssessmentExcelService : delegates
    PracticeAssessmentExcelService --> PracticeAssessmentExcelV2Codec : encodes/decodes
    PracticeAssessmentExcelService --> AssessmentAuthoringCatalogService : loads contract
    PracticeAssessmentExcelService --> LecturerAssetService : resolves media
    PracticeAssessmentExcelService --> PracticeImportDraftService : imports
    PracticeImportDraftService --> PracticeImportSnapshotService : records source
    PracticeImportController --> PracticePdfImportSessionService : opens
    PracticePdfImportApiController --> PracticePdfPageExtractionService : extracts
    PracticePdfImportApiController --> PracticePdfRegionService : annotates
    PracticePdfRegionService --> PracticePdfCropService : creates crop
    PracticePdfImportApiController --> PracticePdfPayloadPreviewService : previews payload
    PracticePdfImportApiController --> PracticePdfAiOrchestrator : generates
    PracticeImportDraftService --> PracticeDraftService : creates canonical draft
    PracticePdfAiOrchestrator --> PracticeImportDraftService : creates canonical draft
    PracticeImportDraftService --> PracticeDraftValidator : reuses publish checks
    classDef current fill:#E8F5E9,stroke:#2E7D32,color:#0B2545,stroke-width:1.5px;
    classDef planned13e fill:#FFF8E1,stroke:#F9A825,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef planned13f fill:#E3F2FD,stroke:#1976D2,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef deferred fill:#F5F5F5,stroke:#616161,color:#263238,stroke-dasharray:3 3;
    cssClass "PracticeDraftController,PracticeManageController,PracticeDraftService,PracticeDraftContractService,PracticeDraftValidator,PracticePublisherService,PracticeRevisionService,PracticeCollaborationService,PracticeAssessmentExcelController,PracticeAssessmentExcelService,PracticeAssessmentExcelV2Codec,AssessmentAuthoringCatalogService,PracticeImportDraftService,PracticeImportSnapshotService,LecturerAssetService,PracticeDraftRepository,PracticeImportController,PracticePdfImportApiController,PracticePdfImportSessionService,PracticePdfPageExtractionService,PracticePdfRegionService,PracticePdfCropService,PracticePdfPayloadPreviewService,PracticePdfAiOrchestrator" current
```

## 2. Skill-based Attempt Lifecycle

Modules: `CAT, PLY`

```mermaid
classDiagram
    direction LR
    note "Status: green = CURRENT; amber dashed = PLANNED 13E; blue dashed = PLANNED 13F"
    namespace CAT_Catalog_Attempt_Entry {
        class PracticeController {
            <<Controller>>
            +catalogDetailRoutes()
            +startResumeActions()
        }
        class PracticeCatalogService {
            <<Service>>
            +boundedCatalogQuery()
            +filtersAndPagination()
        }
        class PracticeDetailPageService {
            <<Service>>
            +setTestDetailModel()
            +modePreflightMetadata()
        }
        class PracticeLearnerAccessService {
            <<Service>>
            +scopeAndEnrolmentChecks()
            +denyUnpublishedContent()
        }
        class PracticeService {
            <<Facade>>
            +attemptEntryOrchestration()
            +playerDelivery()
        }
        class PracticeAttemptVersionLock {
            <<Valueobject>>
            +publishedVersionIdentity()
            +immutableDeliveryBoundary()
        }
        class PracticeAttemptRepository {
            <<Repository>>
            +activeAttemptLookup()
            +attemptPersistence()
        }
        class PracticeAttempt {
            <<Entity>>
            +statusAndTimestamps()
            +lockedPublishedVersionId()
        }
    }
    namespace PLY_Skill_native_Player {
        class PracticeVersionSnapshot {
            <<Valueobject>>
            +attemptGraphSnapshot()
            +deliveryConsistency()
        }
        class PracticeAnswerFormMapper {
            <<Mapper>>
            +requestToLearnerAnswers()
            +canonicalFieldMapping()
        }
        class AssessmentScoringEngine {
            <<Domainservice>>
            +objectiveScoring()
            +partialEssayStatus()
        }
        class PracticeAttemptDiscardService {
            <<Service>>
            +confirmedDiscard()
            +transactionBoundary()
        }
        class PracticeSpeakingMediaService {
            <<Service>>
            +privateRecordingLifecycle()
            +attemptOwnership()
        }
    }
    PracticeController --> PracticeCatalogService : queries
    PracticeController --> PracticeDetailPageService : renders
    PracticeDetailPageService --> PracticeLearnerAccessService : authorizes
    PracticeController --> PracticeService : starts/resumes
    PracticeService --> PracticeAttemptRepository : persists
    PracticeService --> PracticeAttemptVersionLock : creates
    PracticeAttemptRepository --> PracticeAttempt : stores
    PracticeController --> PracticeService : delivers/saves/submits
    PracticeService --> PracticeVersionSnapshot : loads
    PracticeService --> PracticeAnswerFormMapper : maps
    PracticeService --> AssessmentScoringEngine : scores
    PracticeController --> PracticeAttemptDiscardService : discards
    PracticeService --> PracticeSpeakingMediaService : activates media
    PracticeService --> PracticeAttemptRepository : locks/persists
    PracticeDetailPageService --> PracticeService : supplies start context
    PracticeAttemptVersionLock --> PracticeVersionSnapshot : binds delivery
    PracticeAttempt --> PracticeVersionSnapshot : uses locked version
    classDef current fill:#E8F5E9,stroke:#2E7D32,color:#0B2545,stroke-width:1.5px;
    classDef planned13e fill:#FFF8E1,stroke:#F9A825,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef planned13f fill:#E3F2FD,stroke:#1976D2,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef deferred fill:#F5F5F5,stroke:#616161,color:#263238,stroke-dasharray:3 3;
    cssClass "PracticeController,PracticeCatalogService,PracticeDetailPageService,PracticeLearnerAccessService,PracticeService,PracticeAttemptVersionLock,PracticeAttemptRepository,PracticeAttempt,PracticeVersionSnapshot,PracticeAnswerFormMapper,AssessmentScoringEngine,PracticeAttemptDiscardService,PracticeSpeakingMediaService" current
```

## 3. Versioned Results and Evidence

Modules: `RLE, WRT, SPK, RES`

```mermaid
classDiagram
    direction TB
    note "Status: green = CURRENT; amber dashed = PLANNED 13E; blue dashed = PLANNED 13F"
    namespace RLE_R_L_Explanation_Lifecycle {
        class PublishedVersionExplanationListener {
            <<Listener>>
            +afterCommitIntake()
            +preparePublishedVersion()
        }
        class QuestionExplanationPreparationService {
            <<Service>>
            +eligibleQuestionScan()
            +bindingTaskPreparation()
        }
        class ExplanationInputFactory {
            <<Factory>>
            +immutablePromptEvidence()
            +languageSafeInput()
        }
        class ExplanationFingerprintBuilder {
            <<Service>>
            +idIndependentFingerprint()
            +contentIdentity()
        }
        class QuestionExplanationGenerationWorker {
            <<Worker>>
            +claimDurableTasks()
            +boundedProcessing()
        }
        class QuestionExplanationRetryService {
            <<Service>>
            +explicitRetry()
            +statusPolicy()
        }
        class QuestionExplanationReadService {
            <<Service>>
            +bindingSafeRead()
            +noProviderCalls()
        }
        class ObjectiveEvidencePresenter {
            <<Logicalpresenter>>
            +officialLearnerTeacherAILayers()
            +evidenceAnchors()
        }
    }
    namespace WRT_Writing_AI_Evaluation {
        class WritingEvaluationCacheService {
            <<Service>>
            +identityReuse()
            +persistEvaluationStatus()
        }
        class WritingEvaluationClient {
            <<Providerport>>
            +providerRequest()
            +typedFailure()
        }
        class WritingEvaluationNormalizer {
            <<Normalizer>>
            +schemaScoreNormalization()
            +rejectMalformedFeedback()
        }
        class WritingPromptRules {
            <<Rules>>
            +koreanTaskPrompt()
            +safeEvidenceBoundaries()
        }
        class WritingRuleEngine {
            <<Domainservice>>
            +rubricInvariants()
            +criterionChecks()
        }
        class WritingScoringPolicy {
            <<Domainservice>>
            +topikWeights()
            +scoreAggregation()
        }
        class WritingResultPresenter {
            <<Presenter>>
            +skillNativeOverview()
            +fourAnalysisLenses()
        }
        class WritingEvidencePresenter {
            <<Logicalpresenter>>
            +submittedCorrectionRewriteSample()
            +rubricEvidenceDetail()
        }
    }
    namespace SPK_Speaking_Evaluation {
        class PracticeSpeakingMediaController {
            <<Controller>>
            +uploadActivateDelete()
            +ownerScopedEndpoints()
        }
        class PracticeSpeakingMediaService {
            <<Service>>
            +mediaLifecycle()
            +attemptOwnership()
        }
        class SpeakingAudioPreparationService {
            <<Service>>
            +validateInspectAudio()
            +preparePrivateObject()
        }
        class SpeakingTranscriptionMediaResolver {
            <<Service>>
            +resolveAuthorizedRecording()
            +transcriptionInput()
        }
        class SpeakingEvaluationOrchestrator {
            <<Service>>
            +transcribeEvaluateSequence()
            +failurePolicy()
        }
        class SpeakingRuleEngine {
            <<Domainservice>>
            +fourTranscriptCriteria()
            +twoAcousticCriteriaNotScorable()
            +evidenceChecks()
        }
        class SpeakingResultPresenter {
            <<Presenter>>
            +transcriptLanguageProfile()
            +noHolisticScoreWithoutDirectAudio()
            +noPerQuestionOverview()
        }
        class SpeakingEvidencePresenter {
            <<Logicalpresenter>>
            +perQuestionRecordingTranscript()
            +structuredEvidence()
        }
    }
    namespace RES_Result_Overview_Detail {
        class PracticeResultAssembler {
            <<Assembler>>
            +canonicalResultEnvelope()
            +selectSkillPresenter()
        }
        class ObjectiveResultPresenter {
            <<Presenter>>
            +readingListeningOverview()
            +questionTypeAccuracy()
        }
        class PracticeResultContext {
            <<Context>>
            +attemptVersionEvaluationData()
            +authorizationSafeInput()
        }
        class PracticeResultDetailAssembler {
            <<Logicalassembler>>
            +evidenceLayers()
            +availabilityStates()
        }
        class Writing_SpeakingEvidencePresenter["Writing/SpeakingEvidencePresenter"] {
            <<Logicalpresenters>>
            +skillNativeDetail()
            +noSecondAIPipeline()
        }
    }
    PublishedVersionExplanationListener --> QuestionExplanationPreparationService : prepares
    QuestionExplanationPreparationService --> ExplanationInputFactory : builds input
    QuestionExplanationPreparationService --> ExplanationFingerprintBuilder : identifies
    QuestionExplanationGenerationWorker --> QuestionExplanationRetryService : shares task policy
    QuestionExplanationGenerationWorker --> QuestionExplanationReadService : produces readable artifact
    QuestionExplanationReadService --> ObjectiveEvidencePresenter : will feed
    WritingEvaluationCacheService --> WritingEvaluationClient : calls when not reusable
    WritingEvaluationClient --> WritingEvaluationNormalizer : returns provider data
    WritingEvaluationNormalizer --> WritingRuleEngine : validates
    WritingRuleEngine --> WritingScoringPolicy : scores
    WritingEvaluationCacheService --> WritingResultPresenter : feeds
    WritingResultPresenter --> WritingEvidencePresenter : will share context
    PracticeSpeakingMediaController --> PracticeSpeakingMediaService : manages
    PracticeSpeakingMediaService --> SpeakingAudioPreparationService : validates/stores
    SpeakingEvaluationOrchestrator --> SpeakingTranscriptionMediaResolver : loads audio
    SpeakingEvaluationOrchestrator --> SpeakingRuleEngine : validates feedback
    SpeakingEvaluationOrchestrator --> SpeakingResultPresenter : feeds
    SpeakingResultPresenter --> SpeakingEvidencePresenter : will share context
    PracticeResultAssembler --> PracticeResultContext : consumes
    PracticeResultAssembler --> ObjectiveResultPresenter : delegates
    PracticeResultAssembler --> WritingResultPresenter : delegates
    PracticeResultAssembler --> SpeakingResultPresenter : delegates
    PracticeResultContext --> PracticeResultDetailAssembler : will feed
    PracticeResultDetailAssembler --> ObjectiveEvidencePresenter : will delegate
    PracticeResultDetailAssembler --> Writing_SpeakingEvidencePresenter : will delegate
    QuestionExplanationReadService --> PracticeResultDetailAssembler : supplies objective evidence
    WritingEvaluationCacheService --> PracticeResultAssembler : supplies writing state
    SpeakingEvaluationOrchestrator --> PracticeResultAssembler : supplies speaking state
    classDef current fill:#E8F5E9,stroke:#2E7D32,color:#0B2545,stroke-width:1.5px;
    classDef planned13e fill:#FFF8E1,stroke:#F9A825,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef planned13f fill:#E3F2FD,stroke:#1976D2,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef deferred fill:#F5F5F5,stroke:#616161,color:#263238,stroke-dasharray:3 3;
    cssClass "PublishedVersionExplanationListener,QuestionExplanationPreparationService,ExplanationInputFactory,ExplanationFingerprintBuilder,QuestionExplanationGenerationWorker,QuestionExplanationRetryService,QuestionExplanationReadService,WritingEvaluationCacheService,WritingEvaluationClient,WritingEvaluationNormalizer,WritingPromptRules,WritingRuleEngine,WritingScoringPolicy,WritingResultPresenter,PracticeSpeakingMediaController,PracticeSpeakingMediaService,SpeakingAudioPreparationService,SpeakingTranscriptionMediaResolver,SpeakingEvaluationOrchestrator,SpeakingRuleEngine,SpeakingResultPresenter,PracticeResultAssembler,ObjectiveResultPresenter,PracticeResultContext" current
    cssClass "ObjectiveEvidencePresenter,WritingEvidencePresenter,SpeakingEvidencePresenter,PracticeResultDetailAssembler,Writing_SpeakingEvidencePresenter" planned13e
```

## 4. Practice Progress Management

Modules: `PRG`

```mermaid
classDiagram
    direction LR
    note "Status: green = CURRENT; amber dashed = PLANNED 13E; blue dashed = PLANNED 13F"
    namespace PRG_Progress_Recovery {
        class PracticeController {
            <<Controller>>
            +practiceProgressRoute()
            +accessSessionHandling()
        }
        class PracticeService {
            <<Facade>>
            +getprogresspagedata()
            +boundedAttemptSummary()
        }
        class PracticeAttemptRepository {
            <<Repository>>
            +attemptHistoryQuery()
            +statusDateOrdering()
        }
        class PracticeResultAssembler {
            <<Assembler>>
            +attemptResultDeepLink()
            +skillStatusContext()
        }
        class PracticeProgressQueryService {
            <<Logicalservice>>
            +filtersAndRealAggregates()
            +sampleRecencyConfidence()
        }
        class PracticeProgressAggregateRepository {
            <<Logicalrepository>>
            +boundedAggregateQueries()
            +partialCreditSupport()
        }
        class PracticeProgressPresenter {
            <<Logicalpresenter>>
            +chartsTablesEmptyStates()
            +noFakeMetrics()
        }
        class PracticeRecoveryPresenter {
            <<Logicalpresenter>>
            +pendingFailureRetryUX()
            +authorizedActions()
        }
    }
    PracticeController --> PracticeService : currently queries
    PracticeService --> PracticeAttemptRepository : reads
    PracticeController --> PracticeProgressQueryService : will query
    PracticeProgressQueryService --> PracticeProgressAggregateRepository : will aggregate
    PracticeProgressQueryService --> PracticeProgressPresenter : will feed
    PracticeProgressPresenter --> PracticeRecoveryPresenter : will compose
    PracticeProgressPresenter --> PracticeResultAssembler : deep-links
    classDef current fill:#E8F5E9,stroke:#2E7D32,color:#0B2545,stroke-width:1.5px;
    classDef planned13e fill:#FFF8E1,stroke:#F9A825,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef planned13f fill:#E3F2FD,stroke:#1976D2,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;
    classDef deferred fill:#F5F5F5,stroke:#616161,color:#263238,stroke-dasharray:3 3;
    cssClass "PracticeController,PracticeService,PracticeAttemptRepository,PracticeResultAssembler" current
    cssClass "PracticeProgressQueryService,PracticeProgressAggregateRepository,PracticeProgressPresenter,PracticeRecoveryPresenter" planned13f
```
