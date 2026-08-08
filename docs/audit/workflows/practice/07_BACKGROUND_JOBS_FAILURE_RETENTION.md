# Practice background workflows: deadline, AI queues, cleanup và retention

Các workflow dưới đây không bắt đầu bằng button trực tiếp, nhưng quyết định request trước đó được hoàn tất, retry hoặc xóa như thế nào. Mỗi worker được nối với nguồn tạo task tương ứng; scheduler không tự chế dữ liệu business mới.

## 1. Hết hạn attempt

`PracticeAttemptDeadlineProcessor` chỉ tồn tại khi `app.practice.attempt-deadline.worker-enabled=true` (mặc định true), dòng 16–21. Scheduler mặc định chạy sau 5 giây và mỗi 5 giây, lấy tối đa 50 attempt (`:42–59`).

Với mỗi `IN_PROGRESS` đã hết deadline:

- `SPEAKING` → `PracticeAttemptDiscardService.discardForOwner`; bản ghi thiếu/đang upload không bị coi là bài hoàn chỉnh.
- skill khác → `PracticeService.submitAttempt(..., Map.of(), lockVersion)`; backend dùng answers đã autosave/snapshot hiện có, không tin empty map là toàn bộ answer.

Code tại `PracticeAttemptDeadlineProcessor.java:61–79`. Lỗi/race được `PracticeAttemptDeadlineTransactions.recordFailure` ghi số lần và next retry; vượt giới hạn thì quarantine thay vì loop vô hạn (`:80–108`). Request upload/submit đồng thời vẫn phải thắng optimistic lock/transaction, không double finalize.

## 2. Evaluation job Writing/Speaking

Nguồn task là submit/re-evaluate; chi tiết tại `PRACTICE_SUBMIT_AND_AI_EVALUATION.md`.

`PracticeAttemptEvaluationProcessor.runScheduledBatch`, dòng 166–173:

```text
initial delay = PT2S
fixed delay   = PT2S
batch request = 10
concurrency   = 2 (default wiring)
```

Processor claim row bằng lease owner, chạy evaluator ngoài transaction dài, heartbeat mỗi 30 giây và timeout 20 phút; complete/fail dùng fencing. Input fingerprint/contract identity được reload trước chấm và trước commit. Retryable provider/transport lỗi dùng attempts/backoff; contract/input mismatch là terminal. Browser result vì vậy có thể ở pending sau redirect; không có HTTP submit nào giữ connection chờ model.

## 3. Reading/Listening explanation preparation reconciler

Normal path là AFTER_COMMIT event sau publish. Nếu event/process bị gián đoạn, `QuestionExplanationPreparationReconciler` tìm published versions có preparation gaps.

Worker mặc định bật cùng `app.practice.explanation-generation.worker-enabled`, chạy sau 45 giây và mỗi 2 phút, batch 10 (`QuestionExplanationPreparationReconciler.java:11–49`). Nó gọi idempotent `preparePublishedVersion`; fingerprint/binding unique ngăn artifact/task trùng.

## 4. Reading/Listening explanation generation worker

`QuestionExplanationGenerationWorker` chạy sau 20 giây và mỗi 30 giây, batch 20 (`QuestionExplanationGenerationWorker.java:9–29`).

Task được tạo khi published objective explanation artifact là `PENDING` và evidence ready. Processor claim lease, resolve immutable input/ảnh/digest, gọi purpose `PRACTICE_RL_EXPLANATION`, validate strict JSON, rồi complete. Retry/fail/manual recovery chi tiết tại `04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md`.

## 5. Speaking prompt STT/TTS worker

`SpeakingPromptAiTaskWorker` chỉ bật khi `app.practice.speaking-prompt-authoring.worker-enabled=true`; chạy sau 30 giây và mỗi 30 giây (`SpeakingPromptAiTaskWorker.java:9–40`).

Task chỉ sinh từ upload original audio hoặc click Tạo audio/Retry. Processor:

1. kiểm purpose operational trước khi claim;
2. claim lease token;
3. STT hoặc TTS;
4. verify/store result;
5. complete chỉ khi source revision + exact input fingerprint còn current;
6. discard candidate audio nếu TTS completion stale/fail.

Max attempts/concurrency/quota/backoff nằm trong `SpeakingPromptAuthoringAiProperties`; UI poll GET state. Chi tiết request/response tại `05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md`.

## 6. Speaking learner-media cleanup

Upload replacement, delete, discard, temporary expiry, compensation failure và consent withdrawal đều ghi `PracticeSpeakingMediaCleanupTask`. `PracticeSpeakingMediaCleanupWorker`, dòng 44–58, mặc định poll mỗi 5 phút, batch config clamp 1–100.

`PracticeSpeakingMediaCleanupProcessor.processDueTasks`, dòng 38–60:

1. tìm due ids;
2. `claimForProcessing` tạo processing snapshot/claim token;
3. chỉ chấp nhận storage profile `PRACTICE_SPEAKING` và safe relative lower-case key; absolute path, `..`, backslash/control character bị terminal (`:62–76,117–139`);
4. `storage.delete`, rồi bắt buộc `exists=false` mới confirm physical deletion;
5. lost claim → skip;
6. storage lỗi → retry; hết attempt → terminal (`:77–103`).

DB logical state không được đánh dấu `DELETED` chỉ vì lệnh delete đã gửi; phải có confirmation từ storage.

## 7. Practice authoring asset lifecycle

`PracticeAssetLifecycleWorker` chạy sau 1 phút và mỗi 5 phút, batch 50 (`PracticeAssetLifecycleWorker.java:12–29`). `PracticeAssetLifecycleProcessor.processDue`, dòng 25–34:

1. `PracticeAssetOrphanReconciler.enqueueExpiredUnboundUploads` tìm staged/unbound upload hết hạn;
2. lấy due lifecycle task ids tối đa 100;
3. `PracticeAssetLifecycleTaskExecutor.processOne` claim và thực hiện physical cleanup/status transition.

Upload/publish/delete chỉ ghi reference/status/task trong transaction. Worker chịu trách nhiệm orphan hoặc object không còn reference; không xóa asset còn draft/published/material reference.

## 8. Speaking prompt artifact retention

`SpeakingPromptRetentionWorker` chạy sau 2 phút và mỗi 15 phút, batch 50 (`SpeakingPromptRetentionWorker.java:15–34`). Retention mặc định `P30D`.

`SpeakingPromptRetentionService.reconcileExpired`, dòng 48–84, lock artifact quá cutoff nhưng chỉ xóa khi:

- không source nào đang trỏ current STT/TTS artifact;
- không immutable published version context nào trỏ artifact;
- không task `QUEUED/PROCESSING/RETRY_WAIT`;
- artifact đã quá retention.

Sau khi xóa task/revision/artifact DB, service queue input/generated private assets nếu không còn reference. Đây là orphan retention, không xóa prompt hiện hành hoặc đã publish.

## 9. Direct-audio reviewer access audit retention

Worker này off mặc định và chỉ tồn tại khi:

```properties
app.practice.speaking-direct-audio.reviewer-access-audit.retention-worker-enabled=true
```

Mỗi giờ, `DirectAudioReviewerAccessAuditRetentionWorker.runOnce` xóa tối đa batch configured (clamp 1–1000) (`DirectAudioReviewerAccessAuditRetentionWorker.java:8–32`). SQL tại `DirectAudioReviewerAccessAuditRetention.java:31–41` chỉ delete event có immutable `delete_after <= now`, theo deadline/id. Nó không xóa media; media withdrawal đi qua speaking cleanup task riêng.

## 10. Storage migration không có scheduler tự động

`PracticeStorageMigrationCoordinator` và `PracticeStorageMigrationJobService` có state machine `PLANNED → COPYING → COPIED_VERIFIED → CLEANUP_PENDING → DELETING_SOURCE → COMPLETED`, nhưng source comment khẳng định **explicit-only** và không có `@Scheduled` caller (`PracticeStorageMigrationCoordinator.java:17–19`).

Đổi active storage profile/R2 config không tự copy object cũ. Một caller quản trị/migration phải:

1. `plan` exact logical object/source/target;
2. `processCopy(jobId)` stream source qua temp spool, hash/size verify target;
3. `switchIdentity(jobId)` transactionally đổi DB profile/key;
4. chờ source-delete delay (mặc định 24h);
5. `processCleanup(jobId)` xóa source rồi complete.

Hiện không có UI/controller sản xuất khởi tạo chuỗi này; đây là seam/library, không nên mô tả như một chức năng đã mở cho admin.

## 11. Trace chính xác: durable queue, event hậu-commit, lease và shutdown

Các chuỗi sau không phải cùng một kiểu “async”. `practice_attempt_evaluation_jobs`, `question_explanation_generation_tasks`, `speaking_prompt_ai_tasks` và `practice_asset_lifecycle_tasks` là hàng đợi DB có claim/fencing; còn `PublishedVersionExplanationEvent` và `RetiredPromptAssetCandidates` là Spring event **sau commit**, không phải transactional outbox bền vững. Vì vậy listener không thể rollback publish/unlink đã commit; recovery phải dựa vào reconciler/task DB.

| Hook / method chính xác | Trigger và durable hand-off | Claim, state và idempotency | Lỗi / retry / downstream |
|---|---|---|---|
| `PracticeService.queueWritingSubmission` / `PracticeService.queueSpeakingSubmission` | Trong transaction submit: attempt `IN_PROGRESS → SUBMITTED` và insert evaluation job `QUEUED` cùng input fingerprint + evaluation-contract identity. Speaking provider disabled vẫn insert terminal `UNAVAILABLE` (`SPEAKING_AI_DISABLED`) để result UI không poll vô hạn. | Unique theo attempt; `PracticeService.requestReEvaluation` trả `ALREADY_QUEUED` khi job `QUEUED/PROCESSING/RETRY_WAIT`, giới hạn manual retry và cooldown 1 phút. Re-evaluation giữ điểm cũ cho tới completion mới. | Browser redirect/result thấy analysis pending hoặc unavailable; không có request HTTP nào chờ model. |
| `PracticeAttemptEvaluationProcessor.runScheduledBatch` → `PracticeAttemptEvaluationProcessor.processDue` | `@Scheduled` 2 s/2 s (default), quét claimable id và chỉ dispatch khi semaphore còn capacity. | `PracticeAttemptEvaluationJobTransactions.claim` lock job + attempt (`REQUIRES_NEW`), lease 2 phút, marks analysis processing. Claim từ chối/terminal khi attempt không còn `SUBMITTED/GRADED`, job expired/hết attempts; owner + fingerprint + contract identity là fencing material. Processor heartbeat lease mỗi 30 s; complete/fail kiểm ownership, lease và immutable identity lần nữa. | Timeout 20 phút gọi fenced fail `EVALUATION_EXECUTION_TIMEOUT` non-retryable. Provider/internal retryable requeue theo 15/30/60/120/240/300 s; contract/input mismatch là terminal. `PracticeAttemptEvaluationProcessor.shutdown` (@PreDestroy) set shutdown rồi `shutdownNow`; result chạy dở bị bỏ, lease/next claim quyết định recovery thay vì commit mù. |
| `PracticePublisherService.publish` → `PublishedVersionExplanationListener.prepare` | Publish commit phát `PublishedVersionExplanationEvent(publishedVersionId, draftId, immutable question-version map)`. Listener `@TransactionalEventListener(AFTER_COMMIT)` gọi `QuestionExplanationPreparationService.preparePublishedVersion`, sau đó `ObjectiveExplanationEditorialService.promoteApproved`. | Preparation dùng fingerprint/binding/task uniqueness nên gọi lặp an toàn; listener không có outbox/không retry trong cùng event transaction. | Listener catch/log exception, publish vẫn thành công. `QuestionExplanationPreparationReconciler.reconcilePreparationGaps` (45 s rồi 2 phút, batch 10) quét published-version còn gap và gọi chuẩn bị lại; đây là recovery thực tế cho event bị mất/lỗi. |
| `QuestionExplanationGenerationWorker.processDue` → `QuestionExplanationGenerationProcessor.processDue` | Scheduler 20 s rồi 30 s, lấy tối đa 20 due task ids. | `QuestionExplanationTaskTransactions.claim` lock task+artifact (`REQUIRES_NEW`), lease 5 phút; artifact READY làm task succeeded idempotently, artifact không `PENDING`/attempt hết thì task terminal và artifact `FAILED`. Completion chỉ giữ khi task owner còn khớp. | `QuestionExplanationGenerationProcessor.process` load immutable context, resolve chỉ published image evidence và SHA-256 descriptor, rồi gọi `ReadingListeningExplanationClient.generate`. Provider category quyết định retry; generic `GENERATION_INTERNAL_ERROR` retryable. Backoff 30/60/120/240/480/900 s (cap 900); terminal fail đưa artifact `PENDING → FAILED`, còn success `PENDING → READY`. |
| `SpeakingPromptAiTaskWorker.processDue` → `SpeakingPromptAiTaskProcessor.processDue` | Chỉ tạo bean khi `app.practice.speaking-prompt-authoring.worker-enabled=true`; 30 s/30 s. Mỗi operation kiểm `SpeakingPromptAuthoringAiProperties.requireOperational` **trước** claim, nên binding/provider unavailable để row chưa claim chờ lần sau. | `SpeakingPromptTaskTransactions.claim` lock task, owner sentinel, draft/source/artifact; checks quota per lecturer/draft/hour, max attempts, source revision và artifact fingerprint. Lease/default max attempts/config validation được `SpeakingPromptAuthoringAiProperties.afterPropertiesSet` xác nhận: lease phải lớn hơn connect+read envelope + 30 s; config sai fail startup, không âm thầm chạy unsafe. | `SpeakingPromptAiTaskProcessor.process`: STT gọi `transcribe`, TTS gọi `synthesize` rồi staging generated candidate. `completeStt`/`completeTts` chỉ commit khi live claim, exact input SHA, provider identity, source revision/current attachment còn khớp; stale completion bị supersede và candidate bị discard. Provider category giữ retryability; `INVALID_INPUT` terminal, runtime `TRANSPORT` retryable. Retry tạo successor durable task với exponential delay/config cap; stale/failure không mutate reusable artifact sai fingerprint. |
| `SpeakingPromptAssetService.queueRetiredPromptAssets` | Sau khi prompt cũ đã unlink trong transaction, publish `RetiredPromptAssetCandidates`; listener `@TransactionalEventListener(AFTER_COMMIT)` mở transaction mới và gọi `LecturerAssetService.queuePrivatePromptAssetIfUnreferenced`. | Không xóa bytes inline; reference guard quyết định có còn draft/published/material reference hay không trước khi lifecycle task được enqueue. | Vì là post-commit event, lỗi không rollback unlink. Asset unbound vẫn có đường recovery bằng `PracticeAssetOrphanReconciler.enqueueExpiredUnboundUploads` khi `TEMPORARY + PRIVATE + (MANUAL_UPLOAD|AI_TTS)` hết retention và không retained. |
| `PracticeAssetLifecycleWorker.processDue` → `PracticeAssetLifecycleTaskExecutor.processOne` | Scheduler 1 phút rồi 5 phút; trước hết reconciles orphan, sau đó due lifecycle rows. | `PracticeAssetLifecycleTaskTransactions.claim` lock task và tất cả asset rows cùng physical key; lease 10 phút. Chỉ `DELETION_PENDING`, no reference và no sibling còn cần bytes mới cho delete. `PracticeAssetLifecycleTaskTransactions.confirmPhysicalDeleteAllowed` lặp lại chứng minh ngay trước storage I/O, chống key reuse/race. | `AssetStorageService.delete` phải theo sau bởi `exists=false` rồi `PracticeAssetLifecycleTaskTransactions.complete`: asset `DELETION_PENDING → DELETED`, task `RUNNING → COMPLETED`. Key retained thì defer 1 giờ; I/O error `PracticeAssetLifecycleTaskTransactions.retry` exponential phút (cap 360) với max 8 attempts. Đây là delete-at-least-once an toàn bằng recheck, không phải delete exactly-once. |

Provider request/response schema chi tiết không nằm trong scheduler: explanation dùng immutable typed context + verified image evidence qua `ReadingListeningExplanationClient.generate`; prompt STT/TTS dùng `SpeakingPromptWorkLoader` để tạo request hash-bound và port result phải mang provider identity. Các contract và field-level validation tương ứng được giữ tại `04_OBJECTIVE_EXPLANATION_AI_EDITORIAL.md` và `05_SPEAKING_PROMPT_AUTHORING_STT_TTS.md`; worker chỉ commit result sau các điều kiện fencing ở bảng trên.

## Tóm tắt liên hệ

| Task/job | Ai tạo | Worker | Kết quả terminal |
|---|---|---|---|
| expired attempt | clock + `IN_PROGRESS` deadline | deadline processor | submit objective/non-speaking hoặc discard Speaking |
| attempt evaluation | submit/re-evaluate | evaluation processor | result success/failed/unavailable |
| explanation artifact | publish/preparation | explanation worker | READY/FAILED |
| prompt STT/TTS | upload/generate/retry | prompt worker | READY/needs-review/failed |
| speaking media cleanup | replace/delete/discard/withdraw | media cleanup worker | physical deletion confirmed/terminal |
| authoring asset lifecycle | unlink/orphan/expiry | asset lifecycle worker | storage+DB lifecycle reconciled |
| prompt retention | unreferenced artifact age | retention worker | artifact/revisions removed, assets queued |
| reviewer audit retention | `delete_after` | default-off audit worker | expired access events purged |
