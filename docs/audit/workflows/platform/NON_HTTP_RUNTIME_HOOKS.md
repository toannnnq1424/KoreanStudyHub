# Audit runtime hooks ngoài HTTP

_Phạm vi: toàn bộ src/main/java. Đây là entrypoint được Spring hoặc JVM gọi mà không phải @Controller: main/bootstrap, @Scheduled, SmartLifecycle, InitializingBean, @TransactionalEventListener và @PreDestroy. Không bao gồm security filter/interceptor hay các public service method không có cơ chế runtime gọi tự động._

## Kết quả inventory

Scan annotation/interface tìm thấy các hook thực thi bên dưới. Không có ApplicationRunner, CommandLineRunner, SmartInitializingSingleton, ApplicationListener, @EventListener thường, @PostConstruct, @RabbitListener, @KafkaListener, @JmsListener, @SqsListener, @MessageMapping hoặc @SubscribeMapping trong source hiện tại. Các class có chữ Scheduler/Worker nhưng không xuất hiện dưới đây không được coi là runtime entrypoint nếu không có annotation/interface trigger.

| Nhóm | Hook thực tế | Trigger mặc định / điều kiện |
|---|---|---|
| Bootstrap | KshApplication.main | process start |
| Spring scheduler | 16 @Scheduled methods | chỉ vì KshApplication có @EnableScheduling |
| Lifecycle | 3 SmartLifecycle workers, 1 @PreDestroy | context start/stop |
| Config init | 1 InitializingBean | bind properties/bean init |
| Transaction event | 3 AFTER_COMMIT listeners | publisher transaction commit thành công |

Các @Scheduled sử dụng Spring task scheduler chung, trừ ba SmartLifecycle worker tự tạo ScheduledThreadPoolExecutor riêng. Không có global @EnableAsync; bất đồng bộ thực có executor nội bộ của PracticeAttemptEvaluationProcessor và các private lifecycle executors.

## 1. Bootstrap và configuration hook

### KshApplication.main

- **Trigger/input:** JVM gọi main(String[] args); SpringApplication.run ở KshApplication:18-29 tạo context, auto-configuration, Flyway theo startup configuration và embedded web server.
- **Downstream:** @EnableScheduling tại :19 đăng ký mọi @Scheduled bên dưới với task scheduler Spring.
- **Transaction/async:** bootstrap không tự mở business transaction. Fail migration/bean validation sẽ chặn process start.
- **Risk:** mọi scheduler có initial delay 0/nhỏ có thể chạy sớm sau ready; production readiness cần bao gồm DB/schema/config không chỉ HTTP server listen.

### SpeakingPromptAuthoringAiProperties.afterPropertiesSet

- **Trigger/input:** Spring bean init sau ConfigurationProperties bind app.practice.speaking-prompt-authoring. afterPropertiesSet tại SpeakingPromptAuthoringAiProperties:50-77 gọi taskBounds.
- **Read/write/output:** chỉ đọc/bound-check config; kiểm positive bounds và leaseDuration phải dài hơn connect+read timeout envelope + 30s. Không DB/network/write.
- **Boundary/risk:** exception là bean-init failure, ngăn application khởi động thay vì vô hiệu hóa worker. Đây là configuration validator, không phải workflow người dùng hay background processor.

## 2. Spring @Scheduled workflows

### Retention và housekeeping

| Hook | Trigger/input | Query/write/output | Boundary và rủi ro |
|---|---|---|---|
| PasswordResetTokenRetention.cleanup (auth/service/PasswordResetTokenRetention:42-54) | initial 120s, fixed delay 1h; batch setting clamp 1..1000, age default 7d. | @Transactional timeout 10s: query retention IDs cutoff UTC then bulk delete used/expired reset tokens. | Bounded one batch, return count unused ngoài scheduler. Failure Spring logs; tokens có thể tồn lâu hơn nhưng expiry access vẫn là enforcement. |
| ExamImageStorageService.cleanupExpiredStagedImages (:133-163) | initial 5m/fixed delay 1h. | Retry pending delete; list staged object keys, parse timestamped allowed name, delete+verify keys older TTL; failed key giữ trong in-memory pendingDeletes. | Object storage operations không transaction với DB. List/delete failure chỉ log; restart mất pending list nhưng next scan lại thấy object. |
| TokenCleanupScheduler.cleanupExpiredTokens (lessons/scheduler/TokenCleanupScheduler:26-32) | fixed rate 30m. | PublicViewTokenService.cleanupExpired bulk-deletes expired public view tokens, log count >0. | Service transaction decides DB boundary; expired token also rejected/deleted on access, so sweep is retention not primary auth enforcement. |
| QuestionBankImportSessionStore.evictExpired (:66-75) | initial/fixed 60s. | Remove expired preview sessions from in-memory concurrent map; debug evicted count. | No DB/transaction; restart loses all sessions anyway. Cleanup race with claim is handled by atomic map operations, but multi-node previews are node-local. |
| ImportSessionStore.evictExpired (classes/imports/session/ImportSessionStore:107-116) | initial/fixed 60s. | Same in-memory eviction for lecturer class-import preview sessions. | No durable write; multi-node/restart continuity risk is intentional session-store limitation. |
| ClassAutoArchiveWorker.archiveDueClasses (:30-38) | cron daily 00:10 default. | @Transactional query ACTIVE classes endDate <= local clock today and invoke entity.archive; dirty checking writes status. | Idempotent by ACTIVE filter. Local system timezone controls date boundary; no explicit log/metric gives low observability on scheduler failure. |
| NewsIngestionScheduler.ingestEveryFiveHours (discovery/ingestion/NewsIngestionScheduler:26-46) | initial 2m/fixed 5h but returns immediately unless app.news.ingestion.enabled=true, default false. | Calls orchestrator.run(SCHEDULED), which owns source/read, lease, fetch, dedupe, article/run writes; logs RunSummary. | Orchestrator lease prevents concurrent ingestion. Network work occurs on scheduler thread; a long run delays next fixed-delay start, desired but needs timeout/monitoring. |
| PracticeAssetLifecycleWorker.processDue (practice/manage/service/PracticeAssetLifecycleWorker:22-29) | property enabled default true; initial 1m/fixed 5m. | processor first REQUIRES_NEW enqueues expired unbound temporary assets, then reads due lifecycle task IDs and executes up to 50. Downstream may set deletion pending/remove assets/storage. | Per-task execution boundary is delegated; physical storage and DB cannot be one transaction. Failure propagates out of scheduled invocation, so a batch can stop before remaining IDs. |
| SpeakingPromptRetentionWorker.reconcileExpired (practice/manage/speaking/SpeakingPromptRetentionWorker:26-34) | property enabled default true; initial 2m/fixed 15m. | retention REQUIRES_NEW locks up to 50 orphan old AI artifacts, checks no current source/version/active task references, deletes task/revision/artifact rows then queues unreferenced private assets. | DB delete commit precedes downstream asset lifecycle queue work inside same transaction path; retention safe-guards references but delayed physical deletion remains eventual. |
| SpeakingPromptAiTaskWorker.processDue (:29-40) | only bean when app.practice.speaking-prompt-authoring.worker-enabled=true (no matchIfMissing); initial/fixed 30s. | Reads claimable STT/TTS task IDs, validates provider operational, claims, invokes STT/TTS; TTS stores candidate asset; transaction records complete/fail/retry. | Runs synchronously on scheduler thread, each task has DB claim/lease. Provider unavailable leaves task unclaimed; retry/failure is processor-managed, so enabling without viable provider causes backlog rather than direct loss. |
| QuestionExplanationGenerationWorker.processDue (practice/ai/readinglistening/QuestionExplanationGenerationWorker:21-29) | property worker-enabled default true; initial 20s/fixed 30s. | Claims <=20 durable explanation tasks; loads immutable work/image evidence, calls AI client, transaction completes or records retryable/nonretryable failure. | External AI call outside claim transaction; stale completion is discarded. Scheduler serial execution bounds throughput and a long client call delays next batch. |
| QuestionExplanationPreparationReconciler.reconcilePreparationGaps (:30-51) | same worker-enabled condition; initial 45s/fixed 2m. | Query <=10 published-version IDs whose explanation preparation has gaps, calls preparePublishedVersion to create/reconcile bindings/tasks. | Each ID failure is caught/logged so remaining IDs continue. It repairs missed AFTER_COMMIT event work; repeated DB scans are deliberate eventual consistency cost. |
| PracticeAttemptDeadlineProcessor.runScheduledBatch (practice/service/PracticeAttemptDeadlineProcessor:42-49) | property worker-enabled default true; initial/fixed 5s. | Reads <=50 expired IN_PROGRESS attempt IDs. Speaking attempts are discarded; other skills submitAttempt with empty answers/lock version. On failure, records retry/quarantine disposition. | Each finalization service owns transactions/optimistic races. 5s cadence and bounded retries protect deadline progress but failures can quarantine an attempt pending operation review. |
| PracticeAttemptEvaluationProcessor.runScheduledBatch (:166-214) | initial/fixed 2s. | Reads claimable evaluation jobs <= available semaphore/10; atomic transaction claim then submits processAndRelease to private ExecutorService. Processor heartbeats lease, applies subjective evaluation and persists complete/fail/timeout outcome. | This is genuinely async: scheduler only claims/submits; external/model work is executor task, separate from claim transaction. Semaphore and leases prevent duplicate work; executor rejection/timeout/lost lease are handled. Backlog risk if concurrency saturated. |
| PracticeSpeakingMediaCleanupWorker.processDueTasks (:44-59) | bean only when cleanup-worker-enabled=true, default false; fixed delay 5m. | Gets due cleanup-task IDs <=100, claims each, validates PRACTICE_SPEAKING storage key, deletes audio and confirms nonexistence, then marks complete/retry/terminal. | Physical delete happens outside DB transaction; claim token protects stale worker. Failures use retry/terminal status, so disabled default retains scheduled cleanup tasks indefinitely until operator enables worker. |
| DirectAudioReviewerAccessAuditRetentionWorker.runOnce (:28-32) | bean only when retention-worker-enabled=true, default false; fixed delay 1h. | JDBC DELETE <=1000 rows from practice_speaking_audio_reviewer_access_events where immutable delete_after <= UTC now. | One bounded delete statement; no audit export/archive. Default-off means retention deadline is not physically enforced until operator enables it. |

## 3. Lifecycle-owned scheduler workflows

### MailOutboxWorker

- `MailOutboxWorker.start` tạo/lên lịch private executor khi worker enabled; `MailOutboxWorker.stop` hủy future, shutdown executor và chờ tối đa 15 giây trước `shutdownNow`.
- **Trigger/input:** SmartLifecycle auto-start, phase MAX-100, conditional app.mail.outbox.worker-enabled=true default. It creates one non-daemon private executor, then initial 30s/fixed delay 10s invokes processDue (MailOutboxWorker:19-132).
- **Read/write/downstream:** MailOutboxProcessor finds claimable durable outbox IDs, claims each using worker UUID, calls SMTP through MailService without holding DB transaction, then recordSuccess or recordFailure. A persistence-outcome failure leaves the lease for later retry (MailOutboxProcessor:41-80).
- **Shutdown/async:** stop awaits up to 15s then shutdownNow. Private scheduler avoids competing with Spring scheduler.
- **Risk:** at-least-once delivery: SMTP accept before DB success persistence can be resent after lease expiry. Delivery worker is non-daemon, so a stuck SMTP/worker can delay JVM shutdown to the bounded wait.

### MailOutboxRetentionWorker

- `MailOutboxRetentionWorker.start` tạo daemon retention executor và fixed-delay sweep; `MailOutboxRetentionWorker.stop` hủy lịch, shutdown executor rồi clear running state.
- **Trigger/input:** SmartLifecycle auto-start phase MAX-90, conditional retention worker enabled default; private daemon executor after 30s, every 5m (MailOutboxRetentionWorker:20-185).
- **Read/write/output:** calls retainTerminalJobs in batches (default 10×500, caps config), deleting only terminal SENT/FAILED jobs past distinct ages, publishing committed retention summaries and logging post-run operational snapshot.
- **Boundary/risk:** operation service owns transaction per batch; previous committed batches survive a later batch failure. Daemon executor can be cut off at JVM exit; retention is non-critical but delayed cleanup increases table size.

### AiQuestionDraftRetentionWorker

- `AiQuestionDraftRetentionWorker.start` tạo daemon cleanup executor và lịch sweep; `AiQuestionDraftRetentionWorker.stop` hủy future, shutdown executor và kết thúc lifecycle state.
- **Trigger/input:** SmartLifecycle auto-start phase MAX-110, default enabled; private daemon executor initial 5m/fixed 1h (AiQuestionDraftRetentionWorker:22-154).
- **Read/write/output:** calls maintenance cleanup at UTC now, each batch deletes expired durable AI question-draft sessions up to configured 20×500 (bounded further by service), records metrics/snapshot; failure records failure metric.
- **Boundary/risk:** cleanup batch commits independently, so partial delete is intentional on later failure. Like all private lifecycle workers, no cluster leader election: every node may sweep, with repository delete/claim semantics expected to tolerate races.

## 4. Transaction-event workflows

### ClassPendingReviewNotifier.notifyLeader

- **Publisher/trigger:** ClassCreator persists+flushes class and writes activity, then publishes ClassPendingReviewEvent (ClassCreator:41-61). Listener fires only AFTER_COMMIT (ClassPendingReviewNotifier:27-52).
- **Input/read/write/output:** If subject id exists, reads department leader; skips self-reviewing lecturer; creates one in-app CLASS_PENDING_APPROVAL notification addressed to leader with class reference.
- **Boundary/risk:** notification failure is caught/logged and cannot roll back class/activity. There is no retry/outbox for this listener, therefore missed admin approval notification is best-effort even though class remains pending.

### PublishedVersionExplanationListener.prepare

- **Publisher/trigger:** PracticePublisherService publishes PublishedVersionExplanationEvent after its publish transaction has committed (PracticePublisherService:619-637). Listener is AFTER_COMMIT (PublishedVersionExplanationListener:31-53).
- **Input/read/write/output:** prepares explanation bindings/tasks for the published version; when typed editorial context exists, promotes approved typed explanations. It logs eligible/reused/queued/failed/promoted counts.
- **Boundary/risk:** all listener exceptions are caught; publication remains valid but explanation preparation can be missing. QuestionExplanationPreparationReconciler is the compensating scheduled repair path.

### SpeakingPromptAssetService.queueRetiredPromptAssets

- **Publisher/trigger:** SpeakingPromptAssetService publishes RetiredPromptAssetCandidates after it unlinks/replaces exact draft material references (SpeakingPromptAssetService:445-465). The same service receives it AFTER_COMMIT in a REQUIRES_NEW transaction (:468-481).
- **Input/read/write/output:** for each old asset id, invokes centralized guard to queue private prompt asset only if now unreferenced; later PracticeAssetLifecycleWorker performs eventual physical deletion.
- **Boundary/risk:** source/reference transaction commits first; a listener failure does not restore stale references. Reconciliation/lifecycle can eventually find unbound assets, but no direct retry for the event listener is visible.

## 5. Shutdown hook

### PracticeAttemptEvaluationProcessor.shutdown

- **Trigger/input:** JVM/Spring bean destruction invokes @PreDestroy (PracticeAttemptEvaluationProcessor:424-433).
- **Effect:** sets shuttingDown=true so future scheduler batches return zero; if processor owns its executors, interrupts evaluation, heartbeat lease and timeout schedulers with shutdownNow.
- **Boundary/risk:** it does not synchronously resolve claimed jobs. Durable lease expiry/retry logic must recover interrupted jobs on a later process/node; custom injected executors are not stopped by this bean.

## 6. Items deliberately not treated as runtime workflows

- Flashcard Sm2Scheduler, service methods named process/schedule, repositories and DTO methods have no Spring/JVM trigger annotation/interface in this source; they run only when called by HTTP/service flows. **Entity lifecycle is the exception:** source has JPA `@PrePersist/@PreUpdate` callbacks (full current inventory is 36 annotation hooks, including the 29-hook subset previously cited) that run during an already-triggered JPA persist/update/flush. They are persistence hooks, not autonomous workers: no timer/thread/queue/scan, no independent HTTP trigger, and no callback executes unless some caller is already writing that entity. See `ENTITY_LIFECYCLE_CALLBACKS.md`.
- DefaultFfprobeProcessRunner creates local reader threads only during an explicit audio probe call; it is an internal bounded subprocess helper, not a background entrypoint.
- PracticeAssetLifecycleTaskExecutor, MailOutboxProcessor, and the various *Processor/*RetentionService classes are downstream work units. They are documented above only through their actual scheduled/lifecycle/event caller, not as independent hooks.
- Config/security WebSocket handlers and filters are excluded by scope; they are request/message transport configuration, not autonomous runtime workflows.
