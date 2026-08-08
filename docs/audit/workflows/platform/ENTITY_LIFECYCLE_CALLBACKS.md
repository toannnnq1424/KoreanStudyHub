# Entity lifecycle callbacks: JPA persistence hooks, không phải runtime worker

Inventory này quét mọi annotation `@PrePersist`, `@PreUpdate`, `@PostPersist`, `@PostUpdate`, `@PreRemove`, `@PostRemove`, `@PostLoad` dưới `src/main/java`. Source hiện có **36 annotation callback trên 22 entity**, toàn bộ là `@PrePersist` (22) hoặc `@PreUpdate` (14); không có `Post*`, `PreRemove`, `PostRemove` hay `PostLoad`.

Đây là hook của JPA provider: callback chỉ chạy khi một instance managed đi qua persist hoặc dirty-update/flush trong transaction do HTTP service, scheduler, listener hay import gọi. Nó không có timer, thread, endpoint, queue consumer hoặc tự query danh sách record. `repository.save` một entity mới là trigger thường thấy cho `PrePersist`; thay field entity managed rồi flush/`save` là trigger thường thấy cho `PreUpdate`.

Các callback này cũng không phải universal database trigger: JPQL/native bulk update/delete hay SQL/migration đi vòng EntityManager không gọi chúng. Vì vậy timestamp/default dưới đây là contract của JPA entity path, không phải backfill/autonomous enforcement cho mọi write DB.

## Callback inventory

| Entity.method (source) | JPA trigger | Field mutation/default thật | Workflow effect |
|---|---|---|---|
| `Conversation.onPersist` (`entities/Conversation.java:65–68`) | Before INSERT of new conversation. | If null, `createdAt = now`. | Conversation created by messaging service gets immutable ordering timestamp; no participant/message side effect. |
| `Department.onPersist` (`entities/Department.java:69–74`) | Before INSERT. | Fill null `createdAt`, `updatedAt` with same `now`. | Admin/leader department creation has timestamps even when constructor/form omitted them. |
| `Department.onUpdate` (`entities/Department.java:76–79`) | Before provider emits UPDATE for dirty managed Department. | Always `updatedAt = now`. | Department edit/activity list can sort/display last update; does not validate code/name/role. |
| `LearningProgress.onPersist` (`entities/LearningProgress.java:88–93`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | First learner progress row gains timeline values; completion state is not changed. |
| `LearningProgress.onUpdate` (`entities/LearningProgress.java:95–98`) | Before UPDATE. | Always refresh `updatedAt`. | Lesson progress/resume changes carry freshness timestamp only. |
| `Lesson.onPersist` (`entities/Lesson.java:137–142`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | Class lesson authoring gets audit timestamps; it does not attach files or publish lesson. |
| `Lesson.onUpdate` (`entities/Lesson.java:144–147`) | Before UPDATE. | Always refresh `updatedAt`. | Rename/content/reorder/soft-delete flow records write time, not a lifecycle state transition. |
| `LessonAttachment.onPersist` (`entities/LessonAttachment.java:96–99`) | Before INSERT. | Fill null `uploadedAt`. | Attachment metadata has upload time; no object-storage write or access-policy check happens here. |
| `LessonTemplate.onPersist` (`entities/LessonTemplate.java:110–115`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | Lesson-library template creation has timestamps independently of class attachment/distribution. |
| `LessonTemplate.onUpdate` (`entities/LessonTemplate.java:117–120`) | Before UPDATE. | Always refresh `updatedAt`. | Template authoring/soft delete changes become visible to library sorting; no descendant propagation. |
| `LessonTemplateAttachment.onPersist` (`entities/LessonTemplateAttachment.java:65–68`) | Before INSERT. | Fill null `createdAt`. | Library attachment relation is timestamped; it neither copies blob nor attaches it to a class lesson. |
| `LibraryAsset.onPersist` (`entities/LibraryAsset.java:94–99`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | Uploaded library asset row gets timestamps after outer service has selected storage key/metadata. |
| `LibraryAsset.onUpdate` (`entities/LibraryAsset.java:101–104`) | Before UPDATE. | Always refresh `updatedAt`. | Rename/soft-delete metadata gets freshness value; physical blob deletion is not invoked. |
| `Message.onPersist` (`entities/Message.java:70–73`) | Before INSERT. | Fill null `createdAt`. | A sent message gains ordering time; it does not deliver WebSocket/mail notification itself. |
| `PracticeAttempt.onPersist` (`entities/PracticeAttempt.java:180–187`) | Before INSERT. | Default null `startedAt=now`; null `deadlineAt=startedAt+40m`; fill null `createdAt`, `updatedAt`. | Personal Practice attempt becomes time-bounded even if caller omitted dates; does not score, submit or schedule deadline processor. |
| `PracticeAttempt.onUpdate` (`entities/PracticeAttempt.java:189–192`) | Before UPDATE. | Always refresh `updatedAt`. | Answer/submit/evaluation updates carry last-write time only. |
| `PracticeDraft.onCreate` (`entities/PracticeDraft.java:88–93`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | Practice author draft first persist gets chronology; callback does not validate/publish schema. |
| `PracticeDraft.onUpdate` (`entities/PracticeDraft.java:95–98`) | Before UPDATE. | Always refresh `updatedAt`. | Autosave/manual edit changes are marked fresh; no collaboration/event work is started here. |
| `PracticeEditLog.onCreate` (`entities/PracticeEditLog.java:68–73`) | Before INSERT. | Fill null `editedAt`. | Audit-log record gains time when outer workflow writes it; callback does not create a log from edits by itself. |
| `PublicViewToken.onPersist` (`entities/PublicViewToken.java:61–64`) | Before INSERT. | Fill null `createdAt`. | Public lesson-view token receives issue timestamp; expiry/revocation remains service/scheduler logic. |
| `Section.onPersist` (`entities/Section.java:87–92`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | New class section is timestamped; ordering/class scope is caller-owned. |
| `Section.onUpdate` (`entities/Section.java:94–97`) | Before UPDATE. | Always refresh `updatedAt`. | Section edit/reorder status gets timestamp; no lesson cascade. |
| `Flashcard.onPersist` (`features/flashcards/entity/Flashcard.java:76–81`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | New card persists with chronology; deck/card validation happens in service. |
| `Flashcard.onUpdate` (`features/flashcards/entity/Flashcard.java:83–86`) | Before UPDATE. | Always refresh `updatedAt`. | In-place text/image/order edit receives freshness timestamp, not study-state mutation. |
| `FlashcardDeck.onPersist` (`features/flashcards/entity/FlashcardDeck.java:94–99`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | Deck creation gains chronology only. |
| `FlashcardDeck.onUpdate` (`features/flashcards/entity/FlashcardDeck.java:101–104`) | Before UPDATE. | Always refresh `updatedAt`. | Deck metadata/card-count related writes get freshness time; no card write is generated. |
| `FlashcardReview.onPersist` (`features/flashcards/entity/FlashcardReview.java:87–90`) | Before INSERT. | Fill null `reviewedAt`. | First SM-2 review row has review time if caller did not set it. |
| `FlashcardReview.onUpdate` (`features/flashcards/entity/FlashcardReview.java:92–95`) | Before UPDATE. | Always replace `reviewedAt = now`. | Every SM-2 upsert refreshes review time; interval/easiness/repetitions come from outer `apply`, not callback. |
| `QuestionBankItem.onPersist` (`features/questionbank/entity/QuestionBankItem.java:98–103`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | New draft/review item gets timestamps; no approval/reviewer state is inferred. |
| `QuestionBankItem.onUpdate` (`features/questionbank/entity/QuestionBankItem.java:105–108`) | Before UPDATE. | Always refresh `updatedAt`. | Author edit/review/archive transition becomes sortable; no lock/version or review authorization is added. |
| `Question.onPersist` (`features/tests/entity/Question.java:69–74`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | Manual/bank/AI-inserted test question snapshot gets timestamps. |
| `Question.onUpdate` (`features/tests/entity/Question.java:76–79`) | Before UPDATE. | Always refresh `updatedAt`. | Allowed question-content update gets freshness time; it does not rewrite options or attempts. |
| `Test.onPersist` (`features/tests/entity/Test.java:119–124`) | Before INSERT. | Fill null `createdAt`, `updatedAt`. | New lecturer/distributed/practice Test gets timestamps; publish/class distribution stay service decisions. |
| `Test.onUpdate` (`features/tests/entity/Test.java:126–129`) | Before UPDATE. | Always refresh `updatedAt`. | Test metadata/status soft-delete update gets time; no student notification/distribution occurs. |
| `TestAttempt.onPersist` (`features/tests/entity/TestAttempt.java:77–80`) | Before INSERT. | Fill null `startedAt`. | Explicit start/resume or legacy practice GET-created attempt has start time; no deadline/response/grade default is set by callback. |
| `TestResponse.onPersist` (`features/tests/entity/TestResponse.java:56–59`) | Before INSERT. | Fill null `createdAt`. | Submit persistence timestamps each authoritative response; correctness/points are supplied by grading service. |

## Cross-cutting implications and audit corrections

- The 14 `PreUpdate` hooks all mutate timestamp/review time just before JPA UPDATE; they cannot observe a write that bypasses entity lifecycle and they never autonomously load candidate rows.
- The 22 `PrePersist` hooks are defensive defaults. Caller-provided non-null values win for every timestamp/default except no `PrePersist` here unconditionally overwrites a caller value.
- Callbacks do not create records other than the already-persisting entity, call repositories, publish events, access storage/network, or schedule jobs. Workflow side effects named in product docs still originate in controller/service/scheduler/listener code.
- An earlier count of **29** is not consistent with the present source tree: the complete annotation scan yields 36 (22 PrePersist + 14 PreUpdate). Treat 29 as stale/subset inventory rather than omit seven real persistence hooks.
