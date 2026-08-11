# Core Test Scope

This suite protects the reduced MVP only. It favours a small number of
high-value end-to-end flows plus unit tests for business rules and security
boundaries.

## Included functionality

| Area | Integration tests | Unit tests / main behaviour |
| --- | --- | --- |
| Authentication and security | `AuthIntegrationTest`, `AccountSecurityIntegrationTest` | password recovery, login throttling, identity resolution, role navigation and permission fail-safe |
| Subject and class lifecycle | `ClassIntegrationTest`, `SubjectGovernanceIntegrationTest` | class create/update, joining approval, class-role policy and subject approval |
| Lessons, attachments and learning progress | `LessonContentIntegrationTest`, `LessonAttachmentIntegrationTest`, `StudentLessonIntegrationTest`, `StudentLessonDetailIntegrationTest`, `LearningProgressIntegrationTest` | lesson/section mutations, attachment validation, student lesson visibility and completion progress |
| Question bank and class exams | `QuestionBankIntegrationTest`, `ClassExamManagementIntegrationTest`, `ClassExamDistributionIntegrationTest`, `StudentExamIntegrationTest` | bank access/import/review, grading, attempt lifecycle and test catalogue |
| Assignments | `AssignmentIntegrationTest` | lecturer create/grade and student submission |
| Independent objective practice | `PracticeObjectiveIntegrationTest` | draft validation, publishing, objective scoring, learner access, attempt state/answer codec/discard and result presentation |

`DisposableTestDatabaseEnvironmentGuardTest` proves that database-backed tests cannot connect to a non-disposable database.

## Explicitly excluded

The MVP test suite does not cover Flashcards, Discovery/Dictionary, Messaging,
Notifications, dashboards, outbox, advanced platform administration, AI
question generation, Practice AI control-plane, Writing AI, Speaking AI,
audio/media operations, or advanced Practice authoring/import workflows.

## Test rules

- An integration test must exercise a user-visible flow across web/security,
  service and persistence boundaries.
- A unit test is kept only for a business rule, validation rule, access rule or
  concurrency rule that is difficult to prove through one end-to-end flow.
- Do not add duplicate happy-path tests. Add a test when it protects a distinct
  failure mode: access denial, invalid input, concurrent mutation, stale state
  or immutable result/version behaviour.

## Run

Use JDK 17 and a disposable MySQL catalog named `ksh_test_<run_id>`:

```powershell
$env:TEST_DB_URL="jdbc:mysql://localhost:3306/ksh_test_000001?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh"
$env:TEST_DB_USERNAME="test_user"
$env:TEST_DB_PASSWORD="your_test_password"
.\mvnw.cmd test
```
