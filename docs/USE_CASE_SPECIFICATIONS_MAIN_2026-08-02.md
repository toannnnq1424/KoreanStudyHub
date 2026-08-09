# KoreanStudyHub — Detailed Use-Case Specifications

**Baseline:** `origin/main` at `73ea51ba5e0580e1e56dd60e782c0a9d1c983f7b` (fetched 2026-08-02)
**Source catalog:** [CURRENT_FEATURE_USE_CASE_CATALOG_BALANCED_MAIN_2026-08-02.md](CURRENT_FEATURE_USE_CASE_CATALOG_BALANCED_MAIN_2026-08-02.md)
**Specification set:** 92 medium use cases (one table per UC).

The count is not artificially capped at 90: the current-code boundary is 92. The two additional rows are retained where the actor/state/alternative-flow boundary is materially different and would be lost by merging.

## How to use this document

Each use case follows the supplied committee format. **Business Rules** and **System Messages** are reusable IDs: one UC may reference many IDs, and the same ID may be referenced by many UCs. Status notes reflect the current implementation on `origin/main`, not an assumed future scope.

## Use-case index

| ID | Feature | Use case | Primary actors | Status |
|---|---|---|---|---|
| UC-001 | RFE-01 — Authentication and Account Access | Authenticate with password or Google OAuth and establish a session | Student, Lecturer, Subject Leader, Admin | Implemented |
| UC-002 | RFE-01 — Authentication and Account Access | Recover or change credentials and revoke affected sessions | All authenticated users | Implemented |
| UC-003 | RFE-01 — Authentication and Account Access | View or update personal profile and avatar | All authenticated users | Implemented |
| UC-004 | RFE-01 — Authentication and Account Access | Log out and end the current session | All authenticated users | Implemented |
| UC-005 | RFE-02 — Class Creation and Department Approval | Create or update an eligible Class draft | Lecturer | Implemented — creation queues review implicitly |
| UC-006 | RFE-02 — Class Creation and Department Approval | View own Classes, details, status and settings | Lecturer | Implemented |
| UC-007 | RFE-02 — Class Creation and Department Approval | Soft-delete an eligible Class | Lecturer | Implemented — no separate archive/restore flow |
| UC-008 | RFE-02 — Class Creation and Department Approval | Review a pending Department Class and approve or reject it with a reason | Subject Leader | Implemented |
| UC-009 | RFE-03 — Class Enrollment, Invitations and Membership | View membership and leave an eligible Class | Student | Implemented |
| UC-010 | RFE-03 — Class Enrollment, Invitations and Membership | Request membership by invitation code or link | Student | Implemented |
| UC-011 | RFE-03 — Class Enrollment, Invitations and Membership | Create or regenerate Class invitations | Lecturer | Implemented |
| UC-012 | RFE-03 — Class Enrollment, Invitations and Membership | Review join requests and manage roster status | Lecturer | Implemented — manual add/remove is not present |
| UC-013 | RFE-03 — Class Enrollment, Invitations and Membership | Import or bulk-add a roster from Excel | Lecturer | Implemented |
| UC-014 | RFE-04 — Section and Lesson Structure Management | Manage Sections and their ordering | Lecturer | Implemented |
| UC-015 | RFE-04 — Section and Lesson Structure Management | Manage Lessons and their ordering | Lecturer | Implemented |
| UC-016 | RFE-04 — Section and Lesson Structure Management | Publish or return a Lesson to draft | Lecturer | Implemented |
| UC-017 | RFE-04 — Section and Lesson Structure Management | Preview a published Lesson as a Student | Lecturer | Implemented |
| UC-018 | RFE-04 — Section and Lesson Structure Management | Review Lesson/Class content activity history | Lecturer | Implemented — constrained |
| UC-019 | RFE-05 — Lesson Materials, Lecturer Library and Reuse | Author rich-text Lesson content | Lecturer | Implemented |
| UC-020 | RFE-05 — Lesson Materials, Lecturer Library and Reuse | Add, update or remove lesson files and media | Lecturer | Implemented |
| UC-021 | RFE-05 — Lesson Materials, Lecturer Library and Reuse | Upload, rename or delete Lecturer Library assets | Lecturer | Implemented |
| UC-022 | RFE-05 — Lesson Materials, Lecturer Library and Reuse | Save and manage reusable Lesson templates | Lecturer | Implemented |
| UC-023 | RFE-05 — Lesson Materials, Lecturer Library and Reuse | Clone a template or existing Lesson into a Class Section | Lecturer | Implemented |
| UC-024 | RFE-06 — Lesson Learning, Progress and Q&A Discussion | View published Lessons and protected materials | Student | Implemented |
| UC-025 | RFE-06 — Lesson Learning, Progress and Q&A Discussion | Mark and review Lesson completion progress | Student | Implemented |
| UC-026 | RFE-06 — Lesson Learning, Progress and Q&A Discussion | Create and view Lesson questions/replies | Student, Lecturer | Implemented |
| UC-027 | RFE-06 — Lesson Learning, Progress and Q&A Discussion | Edit or delete eligible own discussion content | Student, Lecturer | Implemented |
| UC-028 | RFE-06 — Lesson Learning, Progress and Q&A Discussion | Hide, restore or bulk-moderate discussion content | Authorized moderator | Implemented — no separate Admin queue UI |
| UC-029 | RFE-07 — Flashcards, Sharing and Smart Study | Manage owned Flashcard Deck metadata and lifecycle | Authenticated deck owner | Implemented |
| UC-030 | RFE-07 — Flashcards, Sharing and Smart Study | Manage Cards and card images | Authenticated deck owner | Implemented |
| UC-031 | RFE-07 — Flashcards, Sharing and Smart Study | Preview or import Cards from Excel | Authenticated deck owner | Implemented |
| UC-032 | RFE-07 — Flashcards, Sharing and Smart Study | Generate and confirm AI Flashcard drafts | Authenticated deck owner | Implemented — provider-dependent |
| UC-033 | RFE-07 — Flashcards, Sharing and Smart Study | Share/unshare a Deck to a Class or manage its public link | Deck owner | Implemented — owner-deck model |
| UC-034 | RFE-07 — Flashcards, Sharing and Smart Study | Study private, shared, public or mixed Decks with Smart Review | Authenticated learner | Implemented |
| UC-035 | RFE-08 — Department Question Bank Governance | Contribute and maintain Department Question Bank items | Lecturer | Implemented |
| UC-036 | RFE-08 — Department Question Bank Governance | Preview and confirm Question import from Excel | Lecturer | Implemented |
| UC-037 | RFE-08 — Department Question Bank Governance | Manage Question draft, review and archive workflow | Lecturer, Subject Leader | Implemented — state-dependent |
| UC-038 | RFE-08 — Department Question Bank Governance | Manage Department Question Bank categories | Subject Leader | Implemented |
| UC-039 | RFE-08 — Department Question Bank Governance | Review, approve, reject or bulk-archive Questions | Subject Leader | Implemented |
| UC-040 | RFE-09 — Class Tests and Assessment | Manage Test catalogue and metadata | Lecturer | Implemented |
| UC-041 | RFE-09 — Class Tests and Assessment | Compose Test questions, media and timing | Lecturer | Implemented |
| UC-042 | RFE-09 — Class Tests and Assessment | Generate, review and confirm AI Question drafts | Lecturer | Implemented — provider-dependent |
| UC-043 | RFE-09 — Class Tests and Assessment | Preview and move a Test through draft/published/archived lifecycle | Lecturer | Implemented |
| UC-044 | RFE-09 — Class Tests and Assessment | Start, take, save and submit a timed Test attempt | Student | Implemented |
| UC-045 | RFE-09 — Class Tests and Assessment | View and review automatically graded results and answers | Student | Implemented |
| UC-046 | RFE-09 — Class Tests and Assessment | Monitor attempts/submissions and inspect history | Lecturer | Implemented |
| UC-047 | RFE-09 — Class Tests and Assessment | Create a custom Test and review readiness | Student | Implemented |
| UC-048 | RFE-10 — Class Assignments, Submissions and Feedback | Manage Assignment drafts, instructions and settings | Lecturer | Implemented |
| UC-049 | RFE-10 — Class Assignments, Submissions and Feedback | Publish or close an Assignment | Lecturer | Implemented |
| UC-050 | RFE-10 — Class Assignments, Submissions and Feedback | View, submit or resubmit Assignment work | Student | Implemented |
| UC-051 | RFE-10 — Class Assignments, Submissions and Feedback | Review and grade Assignment submissions | Lecturer | Implemented |
| UC-052 | RFE-10 — Class Assignments, Submissions and Feedback | Release and view Assignment score/feedback | Lecturer, Student | Implemented |
| UC-053 | RFE-11 — Notifications, Messaging and Durable Email | View/open notifications and unread state | Student, Lecturer, Subject Leader, Admin | Implemented |
| UC-054 | RFE-11 — Notifications, Messaging and Durable Email | Find recipients and start an authorized conversation | All authenticated users | Implemented |
| UC-055 | RFE-11 — Notifications, Messaging and Durable Email | Read, reply to and mark internal conversations | All authenticated users | Implemented |
| UC-056 | RFE-11 — Notifications, Messaging and Durable Email | Receive important notifications through durable email | All authenticated users | Implemented — selected events only |
| UC-057 | RFE-12 — Dashboards and Learning Tracking | View Lecturer teaching dashboard and class summaries | Lecturer | Implemented |
| UC-058 | RFE-12 — Dashboards and Learning Tracking | Track learner lesson, test and assignment progress | Lecturer | Implemented |
| UC-059 | RFE-12 — Dashboards and Learning Tracking | View Subject Leader department dashboard/report | Subject Leader | Implemented |
| UC-060 | RFE-12 — Dashboards and Learning Tracking | View Admin system-wide dashboard and activity summaries | Admin | Implemented — current activity sources only |
| UC-061 | RFE-13 — Subject Leader Department Oversight | View assigned Department information and Classes | Subject Leader | Implemented |
| UC-062 | RFE-13 — Subject Leader Department Oversight | Review Department Class queue and status | Subject Leader | Implemented |
| UC-063 | RFE-13 — Subject Leader Department Oversight | Assign or reassign Lecturer to a Department Class | Subject Leader | Implemented |
| UC-064 | RFE-13 — Subject Leader Department Oversight | View Department activity, enrollment and learning report | Subject Leader | Implemented |
| UC-065 | RFE-14 — Admin User, Taxonomy and Platform Administration | Administer complete User account lifecycle | Admin | Implemented |
| UC-066 | RFE-14 — Admin User, Taxonomy and Platform Administration | Assign Roles and per-user permissions | Admin | Implemented |
| UC-067 | RFE-14 — Admin User, Taxonomy and Platform Administration | Manage Role permission matrix and overrides | Admin | Implemented |
| UC-068 | RFE-14 — Admin User, Taxonomy and Platform Administration | Manage Departments and assign Subject Leaders | Admin | Implemented |
| UC-069 | RFE-14 — Admin User, Taxonomy and Platform Administration | Manage generic hierarchical subject Categories | Admin | Implemented |
| UC-070 | RFE-14 — Admin User, Taxonomy and Platform Administration | Configure branding, general, OAuth, SMTP and dictionary integrations | Admin | Implemented |
| UC-071 | RFE-14 — Admin User, Taxonomy and Platform Administration | Configure/test/audit AI providers, prompts and Practice bindings | Admin | Implemented — provider-dependent |
| UC-072 | RFE-14 — Admin User, Taxonomy and Platform Administration | Configure/test Storage profiles and managed secrets | Admin | Implemented — constrained |
| UC-073 | RFE-15 — AI-Assisted Learning and Evaluation | Generate AI Question drafts from source material | Lecturer | Implemented — provider-dependent |
| UC-074 | RFE-15 — AI-Assisted Learning and Evaluation | Generate AI Flashcard drafts | Authenticated deck owner | Implemented — provider-dependent |
| UC-075 | RFE-15 — AI-Assisted Learning and Evaluation | Evaluate Practice Writing/Speaking responses | Student/learner | Implemented — async/provider-dependent |
| UC-076 | RFE-15 — AI-Assisted Learning and Evaluation | Generate and retry Practice Reading/Listening explanations | Student/learner | Implemented — async/provider-dependent |
| UC-077 | RFE-16 — Shared Korean Dictionary and Flashcard Capture | Look up Korean vocabulary through the common helper | Authenticated user | Implemented |
| UC-078 | RFE-16 — Shared Korean Dictionary and Flashcard Capture | Select an owned Flashcard deck for a dictionary term | Authenticated user | Implemented |
| UC-079 | RFE-16 — Shared Korean Dictionary and Flashcard Capture | Save or reuse a dictionary term as a Flashcard | Authenticated user | Implemented |
| UC-080 | RFE-16 — Shared Korean Dictionary and Flashcard Capture | Configure the shared KRDICT connection | Admin | Implemented |
| UC-081 | RFE-17 — Independent Practice Hub — Learner Experience | Browse/filter accessible Practice catalog | Student/learner | Implemented — current GLOBAL/CLASS hybrid |
| UC-082 | RFE-17 — Independent Practice Hub — Learner Experience | View set/test details and complete Listening/Speaking preflight | Student/learner | Implemented — feature/config guarded |
| UC-083 | RFE-17 — Independent Practice Hub — Learner Experience | Start/resume/autosave/submit/discard a four-skill attempt | Student/learner | Implemented |
| UC-084 | RFE-17 — Independent Practice Hub — Learner Experience | View Practice result/detail/progress and request re-evaluation | Student/learner | Implemented — async/provider-dependent |
| UC-085 | RFE-17 — Independent Practice Hub — Learner Experience | Manage Practice learner preferences | Student/learner | Implemented |
| UC-086 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Create/edit/delete Practice drafts manually | Lecturer/Practice author | Implemented |
| UC-087 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Autosave, lock, collaborate, review revisions and restore versions | Lecturer/Practice author/reviewer | Implemented |
| UC-088 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Import Practice through Excel template/preview/validation | Lecturer/Practice author | Implemented |
| UC-089 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Import PDF with page ranges, extraction, annotations and assets | Lecturer/Practice author | Implemented |
| UC-090 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Review, edit, ready, reject, preview and apply an authoring candidate | Lecturer/Practice reviewer | Implemented |
| UC-091 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Publish immutable versions and archive/unarchive Practice Sets | Lecturer/Practice author | Implemented |
| UC-092 | RFE-18 — Practice Authoring, Publishing, Media and Content Operations | Manage Practice material assets and speaking media | Lecturer/Practice author | Implemented — storage/provider guarded |

## Shared Business Rules

### BR-001 — Active session and account

Protected operations require a valid session and an active, unlocked account.

### BR-002 — Credential security

Passwords use the configured encoder; recovery/change operations revoke affected sessions.

### BR-003 — OAuth account linking

Google OAuth follows the configured internal-domain and account-linking policy.

### BR-004 — Permission and ownership scope

Every read/write is checked against role, ownership, Department, Class membership or explicit permission.

### BR-005 — Lifecycle transition

An aggregate may move only through a state transition allowed by its current state and actor.

### BR-006 — Assigned Department authority

Subject Leader decisions apply only to the assigned Department and record the decision actor.

### BR-007 — Approved Class enrollment

Student membership requests target an approved, non-archived Class.

### BR-008 — Invitation token policy

Invitation codes/links are scoped, rotatable and validated for expiry/revocation.

### BR-009 — Membership uniqueness

A Student has at most one effective membership per Class; duplicate requests are idempotent.

### BR-010 — Preview before import

File imports are parsed and diagnosed before confirmation; invalid rows are never silently accepted.

### BR-011 — Publication visibility

Unpublished or hidden content is unavailable to learners outside authorized preview.

### BR-012 — Ordered children

Section/Lesson display order is unique within its parent and deterministic after updates.

### BR-013 — Protected material delivery

Stored files are delivered through an authorized/protected reference.

### BR-014 — Discussion authorization

Only owners or authorized moderators may mutate discussion content; actions are auditable.

### BR-015 — Idempotent progress

Repeated completion/progress writes produce the same effective state.

### BR-016 — Deck ownership and sharing

Deck/card mutation requires ownership/collaboration; sharing cannot grant broader mutation.

### BR-017 — Spaced repetition schedule

Study ratings update the configured SM-2/review schedule from the last confirmed state.

### BR-018 — Department taxonomy

Question-bank categories belong to one Department and differ from Admin global taxonomy.

### BR-019 — Question workflow

Question states and review decisions follow the Department Question Bank workflow.

### BR-020 — Immutable assessment snapshot

Published tests and attempts use an immutable question/media snapshot.

### BR-021 — Attempt timing

Attempt writes/submission require ownership, open state and server-time eligibility.

### BR-022 — Score provenance

Scores/readiness values come only from confirmed grading/evaluation artifacts.

### BR-023 — Assignment deadline policy

Server time, publish/close state and late policy determine submission eligibility.

### BR-024 — Feedback release

Lecturer grading/feedback is visible to Students only after the release state.

### BR-025 — Message visibility and unread state

Conversation/notification data is scoped to permitted participants; unread state persists.

### BR-026 — Durable outbox idempotency

Email/event jobs use idempotency keys and retry policy so transient failure loses no event.

### BR-027 — Administrative separation of duties

Role/permission/system changes require the corresponding Admin permission and audit.

### BR-028 — Soft-delete and dependency safety

Destructive actions are reversible where supported and blocked when dependants would orphan.

### BR-029 — AI purpose binding and budget

AI requests resolve an enabled purpose/model and obey budget/cost policy.

### BR-030 — Structured AI contract

Provider output is schema/safety validated before persistence or learner display.

### BR-031 — AI job idempotency and retry

AI jobs carry an idempotency key; retry reuses or supersedes a failed artifact by policy.

### BR-032 — Shared dictionary provider boundary

Dictionary lookup is restricted to the official KRDICT HTTPS host and does not create a content/news data model.

### BR-033 — Vocabulary ownership

Saved vocabulary targets a deck the actor may mutate; duplicate terms merge by deck policy.

### BR-034 — Practice scope

Practice access honors published version scope; current code supports GLOBAL and CLASS scopes.

### BR-035 — Practice attempt locking

Attempt/draft writes include ownership and revision/lock checks to prevent stale overwrites.

### BR-036 — Immutable Practice versions

Published Practice versions remain immutable; existing attempts retain their referenced version.

### BR-037 — Media consent and format

Audio/video capture and processing require consent and an allowed format/size.

### BR-038 — Candidate review gate

Imported/extracted candidates need diagnostics and an explicit review decision before application.

### BR-039 — Storage profile and secret handling

Storage/integration settings are validated; secrets are masked and handled by policy.

### BR-040 — Audit and privacy

Security, lifecycle, moderation and provider actions retain minimum actor/time/context data.

## Shared System Messages

### MSG-001 — Session expired

Your session has expired. Sign in again to continue.

### MSG-002 — Invalid credentials

The email/password or OAuth assertion could not be verified.

### MSG-003 — Account unavailable

This account is inactive or locked; contact an administrator.

### MSG-004 — Reset link invalid

The recovery link or code is invalid or expired.

### MSG-005 — Profile validation failed

One or more profile fields are invalid or unavailable.

### MSG-006 — Access denied

You do not have permission in the requested scope.

### MSG-007 — Record not found

The record is missing, deleted or no longer visible.

### MSG-008 — Invalid lifecycle transition

The record changed state and cannot accept this operation.

### MSG-009 — Decision recorded

The decision was recorded and the contributor was notified when configured.

### MSG-010 — Invitation invalid

The invitation code/link is invalid, revoked or expired.

### MSG-011 — Membership already exists

An active or pending membership already exists for this Class.

### MSG-012 — Import file invalid

The file type, headers or encoding is not supported.

### MSG-013 — Import row conflict

A row duplicates, conflicts with existing data or references an unknown record.

### MSG-014 — Validation required

Fix the highlighted validation errors before confirming.

### MSG-015 — Content unavailable

Content is unpublished, hidden or outside the permitted visibility window.

### MSG-016 — Upload or media failed

The file/media could not be stored or resolved.

### MSG-017 — Comment unavailable

The discussion item is hidden, deleted or outside the mutation policy.

### MSG-018 — Progress save failed

The last progress change was not confirmed; retry is available.

### MSG-019 — Deck access denied

This deck is private or no longer shared with you.

### MSG-020 — Card import failed

No valid card rows were applied; inspect import diagnostics.

### MSG-021 — Category unavailable

The category is missing, hidden or outside the Department scope.

### MSG-022 — Review conflict

Another reviewer changed this item; reload before deciding.

### MSG-023 — Activity unavailable

The test/assignment is unpublished, closed, archived or outside its window.

### MSG-024 — Attempt expired

The attempt deadline passed or the attempt is no longer open.

### MSG-025 — Answer save failed

The answer was not confirmed; the last saved version is retained.

### MSG-026 — Score pending

Grading/evaluation is pending; no final score is available yet.

### MSG-027 — Late submission blocked

The deadline or late policy does not permit this submission.

### MSG-028 — Submission failed

The submission could not be persisted; retry without losing the last version.

### MSG-029 — Feedback not released

The score or lecturer feedback is not released yet.

### MSG-030 — Notification unavailable

Notifications could not be loaded; retry is available.

### MSG-031 — Recipient not permitted

That recipient is unavailable under directory/privacy policy.

### MSG-032 — Email delivery deferred

The event is in the outbox and will retry when SMTP is available.

### MSG-033 — Admin action denied

The operation requires another permission or has a dependency.

### MSG-034 — Aggregate unavailable

Some report data is unavailable or stale; the result is marked incomplete.

### MSG-035 — AI provider unavailable

The configured AI provider timed out or is unavailable.

### MSG-036 — AI contract rejected

The provider response failed schema, safety or normalization validation.

### MSG-037 — AI budget/quota exceeded

The request cannot run under the current quota or budget policy.

### MSG-038 — AI retry queued

A retry was accepted and will update the result asynchronously.

### MSG-039 — Dictionary unavailable

The configured KRDICT provider returned no usable dictionary data.

### MSG-040 — Dictionary response rejected

The dictionary response is unavailable, malformed or outside the allowed provider boundary.

### MSG-041 — Practice preflight failed

Required device, media or task preflight checks did not pass.

### MSG-042 — Practice draft locked

The draft is locked or has a newer revision; reload before saving.

### MSG-043 — Practice version unavailable

The requested Practice version is unpublished, archived or missing.

### MSG-044 — Media processing pending/failed

Audio/video processing is pending or failed; retry/status is available.

### MSG-045 — Storage profile unavailable

The configured storage profile cannot be validated or reached.

### MSG-046 — Candidate invalid

The candidate has blocking diagnostics and cannot be applied.

### MSG-047 — Practice validation blocked

Required fields/assets/version checks are incomplete.

## Detailed specifications

### UC-001 — Authenticate with password or Google OAuth and establish a session

**Feature:** RFE-01 — Authentication and Account Access

| Field | Specification |
|---|---|
| **Primary Actors** | Student, Lecturer, Subject Leader, Admin |
| **Secondary Actors** | Google OAuth/OIDC service |
| **Description** | Authenticates an active internal account and creates the role-aware session. |
| **Preconditions** | The actor has an internal account. For authenticated operations, the account is active and the current session is valid. |
| **Postconditions** | **Success:**<br>• The authenticated session is created and the actor is redirected to the permitted role surface.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens Login and chooses password authentication or Google OAuth.<br>2. The system validates credentials/OIDC and resolves the internal account.<br>3. The system checks active/locked status and role mapping.<br>4. The system creates a session, records the login event and redirects to the role-appropriate surface. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired session: show MSG-001, preserve no partial change and redirect to Login. A2 — Account inactive/locked: show MSG-003 and end the flow. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004), [BR-002](#br-002), [BR-003](#br-003) |
| **System Messages** | [MSG-001](#msg-001), [MSG-002](#msg-002), [MSG-003](#msg-003), [MSG-004](#msg-004), [MSG-005](#msg-005) |
| **Implementation Status** | Implemented |

### UC-002 — Recover or change credentials and revoke affected sessions

**Feature:** RFE-01 — Authentication and Account Access

| Field | Specification |
|---|---|
| **Primary Actors** | All authenticated users |
| **Secondary Actors** | SMTP/reset-token service, session registry |
| **Description** | Recovers a password or changes the current password, then expires affected other sessions. |
| **Preconditions** | The actor has an internal account. For authenticated operations, the account is active and the current session is valid. |
| **Postconditions** | **Success:**<br>• The credential is securely changed/recovered and affected sessions are revoked.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens recovery or Change Password.<br>2. The system validates the reset token/current password and account state.<br>3. The actor supplies a compliant new password.<br>4. The system hashes it, revokes affected sessions, queues any notice and confirms completion. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired session: show MSG-001, preserve no partial change and redirect to Login. A2 — Account inactive/locked: show MSG-003 and end the flow. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004), [BR-002](#br-002) |
| **System Messages** | [MSG-001](#msg-001), [MSG-002](#msg-002), [MSG-003](#msg-003), [MSG-004](#msg-004), [MSG-005](#msg-005) |
| **Implementation Status** | Implemented |

### UC-003 — View or update personal profile and avatar

**Feature:** RFE-01 — Authentication and Account Access

| Field | Specification |
|---|---|
| **Primary Actors** | All authenticated users |
| **Secondary Actors** | Object-storage/upload service |
| **Description** | Displays and updates permitted profile fields and the user avatar. |
| **Preconditions** | The actor has an internal account. For authenticated operations, the account is active and the current session is valid. |
| **Postconditions** | **Success:**<br>• The permitted profile/avatar data is displayed or updated; unrelated account data is unchanged.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor selects Profile.<br>2. The system validates the session and loads permitted profile/avatar fields.<br>3. The actor reviews or submits an update.<br>4. The system validates, persists and renders the resulting profile. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired session: show MSG-001, preserve no partial change and redirect to Login. A2 — Account inactive/locked: show MSG-003 and end the flow. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004) |
| **System Messages** | [MSG-001](#msg-001), [MSG-002](#msg-002), [MSG-003](#msg-003), [MSG-004](#msg-004), [MSG-005](#msg-005) |
| **Implementation Status** | Implemented |

### UC-004 — Log out and end the current session

**Feature:** RFE-01 — Authentication and Account Access

| Field | Specification |
|---|---|
| **Primary Actors** | All authenticated users |
| **Secondary Actors** | Spring Security |
| **Description** | Invalidates the current session and redirects to the login surface. |
| **Preconditions** | The actor has an internal account. For authenticated operations, the account is active and the current session is valid. |
| **Postconditions** | **Success:**<br>• The current session is invalidated and the actor is redirected to Login.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor selects Logout.<br>2. The system invalidates the current session and security context.<br>3. The system records the logout event.<br>4. The system redirects to Login. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired session: show MSG-001, preserve no partial change and redirect to Login. A2 — Account inactive/locked: show MSG-003 and end the flow. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004) |
| **System Messages** | [MSG-001](#msg-001), [MSG-002](#msg-002), [MSG-003](#msg-003), [MSG-004](#msg-004) |
| **Implementation Status** | Implemented |

### UC-005 — Create or update an eligible Class draft

**Feature:** RFE-02 — Class Creation and Department Approval

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Class/activity service |
| **Description** | Creates a draft and updates its editable identity/settings before approval. |
| **Preconditions** | The Lecturer is authorized for the target Department/Class, or the Subject Leader is assigned to the Department. The aggregate is in a lifecycle state that permits the operation. |
| **Postconditions** | **Success:**<br>• The eligible Class draft is persisted with its current review status.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens Class creation or an owned editable Class.<br>2. The system resolves Department authorization and applies defaults.<br>3. The Lecturer enters or changes Class identity/settings.<br>4. The system validates and persists the draft, then exposes its review status. |
| **Alternative Sequences/Flows** | A1 — Required data missing: show MSG-014 and keep the draft unchanged. A2 — Class stale/missing/outside scope: show MSG-006/MSG-007 and record the denied attempt. |
| **Business Rules** | [BR-004](#br-004), [BR-005](#br-005) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008) |
| **Implementation Status** | Implemented — creation queues review implicitly |

### UC-006 — View own Classes, details, status and settings

**Feature:** RFE-02 — Class Creation and Department Approval

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | None |
| **Description** | Lists owned classes and displays details, lifecycle status, invitation/settings entry points. |
| **Preconditions** | The Lecturer is authorized for the target Department/Class, or the Subject Leader is assigned to the Department. The aggregate is in a lifecycle state that permits the operation. |
| **Postconditions** | **Success:**<br>• The permitted Class list/detail is displayed; no Class is changed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Required data missing: show MSG-014 and keep the draft unchanged. A2 — Class stale/missing/outside scope: show MSG-006/MSG-007 and record the denied attempt. |
| **Business Rules** | [BR-004](#br-004), [BR-005](#br-005) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008) |
| **Implementation Status** | Implemented |

### UC-007 — Soft-delete an eligible Class

**Feature:** RFE-02 — Class Creation and Department Approval

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Class/audit service |
| **Description** | Soft-deletes a class allowed by its current lifecycle and ownership policy. |
| **Preconditions** | The Lecturer is authorized for the target Department/Class, or the Subject Leader is assigned to the Department. The aggregate is in a lifecycle state that permits the operation. |
| **Postconditions** | **Success:**<br>• The eligible Class is soft-deleted according to retention/dependency policy.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor selects an eligible record and confirms the operation.<br>2. The system checks ownership, dependencies and lifecycle state.<br>3. The system applies the permitted delete/removal policy.<br>4. The system refreshes the view and records the mutation. |
| **Alternative Sequences/Flows** | A1 — Required data missing: show MSG-014 and keep the draft unchanged. A2 — Class stale/missing/outside scope: show MSG-006/MSG-007 and record the denied attempt. |
| **Business Rules** | [BR-004](#br-004), [BR-005](#br-005), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008) |
| **Implementation Status** | Implemented — no separate archive/restore flow |

### UC-008 — Review a pending Department Class and approve or reject it with a reason

**Feature:** RFE-02 — Class Creation and Department Approval

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Notification/activity service |
| **Description** | Opens the pending request and chooses approval or rejection as the alternative outcome. |
| **Preconditions** | The Lecturer is authorized for the target Department/Class, or the Subject Leader is assigned to the Department. The aggregate is in a lifecycle state that permits the operation. |
| **Postconditions** | **Success:**<br>• The approval/rejection decision and reason are persisted and the contributor notification/audit event is recorded.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Subject Leader opens the Department approval queue.<br>2. The system lists pending requests and loads the selected Class details.<br>3. The Leader approves or rejects with a reason.<br>4. The system persists the transition, records the decision and notifies the Lecturer. |
| **Alternative Sequences/Flows** | A1 — Required data missing: show MSG-014 and keep the draft unchanged. A2 — Class stale/missing/outside scope: show MSG-006/MSG-007 and record the denied attempt. |
| **Business Rules** | [BR-004](#br-004), [BR-005](#br-005), [BR-006](#br-006) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-009](#msg-009) |
| **Implementation Status** | Implemented |

### UC-009 — View membership and leave an eligible Class

**Feature:** RFE-03 — Class Enrollment, Invitations and Membership

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Enrollment/activity service |
| **Description** | Views active/pending membership and leaves an eligible active class. |
| **Preconditions** | The actor is authenticated and the Class is visible under enrollment policy. Invitation and roster operations require an approved Class and Lecturer ownership. |
| **Postconditions** | **Success:**<br>• The membership list is refreshed and an eligible Leave operation updates the Student’s membership state.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired invitation: create no membership and show MSG-010. A2 — Duplicate/conflicting row: attach MSG-011 or MSG-013 to that row. A3 — Other valid rows remain eligible under import policy. |
| **Business Rules** | [BR-004](#br-004), [BR-007](#br-007), [BR-009](#br-009) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-010](#msg-010), [MSG-011](#msg-011) |
| **Implementation Status** | Implemented |

### UC-010 — Request membership by invitation code or link

**Feature:** RFE-03 — Class Enrollment, Invitations and Membership

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Invitation validation service |
| **Description** | Submits a valid code/link and creates or reopens a pending enrollment request. |
| **Preconditions** | The actor is authenticated and the Class is visible under enrollment policy. Invitation and roster operations require an approved Class and Lecturer ownership. |
| **Postconditions** | **Success:**<br>• A valid membership request is created or reopened; duplicate membership is not created.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Student enters an invitation code or follows an invitation link.<br>2. The system validates token, Class status, expiry and duplicate membership.<br>3. The system creates or reopens a pending membership request.<br>4. The system confirms the request and notifies the appropriate reviewer. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired invitation: create no membership and show MSG-010. A2 — Duplicate/conflicting row: attach MSG-011 or MSG-013 to that row. A3 — Other valid rows remain eligible under import policy. |
| **Business Rules** | [BR-004](#br-004), [BR-007](#br-007), [BR-009](#br-009), [BR-008](#br-008) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-010](#msg-010), [MSG-011](#msg-011) |
| **Implementation Status** | Implemented |

### UC-011 — Create or regenerate Class invitations

**Feature:** RFE-03 — Class Enrollment, Invitations and Membership

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Invite-code/link service |
| **Description** | Creates or rotates the invitation code/link for an owned approved class. |
| **Preconditions** | The actor is authenticated and the Class is visible under enrollment policy. Invitation and roster operations require an approved Class and Lecturer ownership. |
| **Postconditions** | **Success:**<br>• The current invitation token is stored and the sharing representation is displayed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens invitations for an approved owned Class.<br>2. The system validates ownership and Class lifecycle.<br>3. The Lecturer creates or regenerates the code/link.<br>4. The system stores the new token, invalidates the prior token when required and displays sharing controls. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired invitation: create no membership and show MSG-010. A2 — Duplicate/conflicting row: attach MSG-011 or MSG-013 to that row. A3 — Other valid rows remain eligible under import policy. |
| **Business Rules** | [BR-004](#br-004), [BR-007](#br-007), [BR-009](#br-009), [BR-008](#br-008) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-010](#msg-010), [MSG-011](#msg-011) |
| **Implementation Status** | Implemented |

### UC-012 — Review join requests and manage roster status

**Feature:** RFE-03 — Class Enrollment, Invitations and Membership

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Membership/notification service |
| **Description** | Views active/pending members and approves or rejects pending requests. |
| **Preconditions** | The actor is authenticated and the Class is visible under enrollment policy. Invitation and roster operations require an approved Class and Lecturer ownership. |
| **Postconditions** | **Success:**<br>• The roster/request decision is persisted and the refreshed roster reflects the resulting status.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant queue or record.<br>2. The system verifies scope, permission and current state.<br>3. The actor reviews evidence and chooses an allowed decision.<br>4. The system persists the decision/reason, records an audit event and refreshes the queue. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired invitation: create no membership and show MSG-010. A2 — Duplicate/conflicting row: attach MSG-011 or MSG-013 to that row. A3 — Other valid rows remain eligible under import policy. |
| **Business Rules** | [BR-004](#br-004), [BR-007](#br-007), [BR-009](#br-009) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-010](#msg-010), [MSG-011](#msg-011) |
| **Implementation Status** | Implemented — manual add/remove is not present |

### UC-013 — Import or bulk-add a roster from Excel

**Feature:** RFE-03 — Class Enrollment, Invitations and Membership

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Excel parser/import session |
| **Description** | Previews, validates and confirms an Excel roster import according to duplicate/error rules. |
| **Preconditions** | The actor is authenticated and the Class is visible under enrollment policy. Invitation and roster operations require an approved Class and Lecturer ownership. The file is readable and uses the supported template. |
| **Postconditions** | **Success:**<br>• A confirmed import session persists valid roster rows and retains row-level diagnostics.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer selects an Excel roster file.<br>2. The system parses headers, identity, duplicates and membership rules.<br>3. The system shows row-level diagnostics and a preview.<br>4. The Lecturer confirms; only valid rows are imported according to the transaction policy. |
| **Alternative Sequences/Flows** | A1 — Invalid/expired invitation: create no membership and show MSG-010. A2 — Duplicate/conflicting row: attach MSG-011 or MSG-013 to that row. A3 — Other valid rows remain eligible under import policy. |
| **Business Rules** | [BR-004](#br-004), [BR-007](#br-007), [BR-009](#br-009), [BR-010](#br-010) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-010](#msg-010), [MSG-011](#msg-011), [MSG-012](#msg-012), [MSG-013](#msg-013), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-014 — Manage Sections and their ordering

**Feature:** RFE-04 — Section and Lesson Structure Management

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Section/audit service |
| **Description** | Creates, views, updates, deletes and reorders sections in an assigned class. |
| **Preconditions** | The Lecturer is assigned to an approved Class for editing, or the Student is enrolled and the Lesson is published for viewing. |
| **Postconditions** | **Success:**<br>• The Section structure and deterministic order are persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Invalid order/duplicate position: retain the old order and show MSG-014. A2 — Lesson not published: show MSG-015. A3 — Missing parent: create no orphan child. |
| **Business Rules** | [BR-004](#br-004), [BR-012](#br-012) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-015](#msg-015), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-015 — Manage Lessons and their ordering

**Feature:** RFE-04 — Section and Lesson Structure Management

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Lesson/audit service |
| **Description** | Creates, views, updates, deletes and reorders lessons within a section. |
| **Preconditions** | The Lecturer is assigned to an approved Class for editing, or the Student is enrolled and the Lesson is published for viewing. |
| **Postconditions** | **Success:**<br>• The Lesson structure and deterministic order are persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Invalid order/duplicate position: retain the old order and show MSG-014. A2 — Lesson not published: show MSG-015. A3 — Missing parent: create no orphan child. |
| **Business Rules** | [BR-004](#br-004), [BR-012](#br-012) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-015](#msg-015), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-016 — Publish or return a Lesson to draft

**Feature:** RFE-04 — Section and Lesson Structure Management

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Lesson lifecycle/notification service |
| **Description** | Publishes a lesson/material set or hides it from enrolled students. |
| **Preconditions** | The Lecturer is assigned to an approved Class for editing, or the Student is enrolled and the Lesson is published for viewing. Required fields and dependencies have been resolved. |
| **Postconditions** | **Success:**<br>• The Lesson visibility/lifecycle state is persisted and learner access follows it.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens an eligible Lesson.<br>2. The system validates required content and material state.<br>3. The Lecturer chooses Publish or Return to Draft.<br>4. The system changes visibility, records the transition and emits any configured notification. |
| **Alternative Sequences/Flows** | A1 — Invalid order/duplicate position: retain the old order and show MSG-014. A2 — Lesson not published: show MSG-015. A3 — Missing parent: create no orphan child. |
| **Business Rules** | [BR-004](#br-004), [BR-012](#br-012), [BR-005](#br-005), [BR-011](#br-011) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-015](#msg-015) |
| **Implementation Status** | Implemented |

### UC-017 — Preview a published Lesson as a Student

**Feature:** RFE-04 — Section and Lesson Structure Management

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Class access policy |
| **Description** | Renders the learner-facing lesson view before or after publication. |
| **Preconditions** | The Lecturer is assigned to an approved Class for editing, or the Student is enrolled and the Lesson is published for viewing. |
| **Postconditions** | **Success:**<br>• A learner-facing preview is displayed without changing publication state.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Invalid order/duplicate position: retain the old order and show MSG-014. A2 — Lesson not published: show MSG-015. A3 — Missing parent: create no orphan child. |
| **Business Rules** | [BR-004](#br-004), [BR-012](#br-012), [BR-011](#br-011) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-015](#msg-015) |
| **Implementation Status** | Implemented |

### UC-018 — Review Lesson/Class content activity history

**Feature:** RFE-04 — Section and Lesson Structure Management

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Activity repositories |
| **Description** | Views available lesson/section mutation history; this is not a full system audit log. |
| **Preconditions** | The Lecturer is assigned to an approved Class for editing, or the Student is enrolled and the Lesson is published for viewing. |
| **Postconditions** | **Success:**<br>• The retained activity events are displayed; no new mutation is created.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant queue or record.<br>2. The system verifies scope, permission and current state.<br>3. The actor reviews evidence and chooses an allowed decision.<br>4. The system persists the decision/reason, records an audit event and refreshes the queue. |
| **Alternative Sequences/Flows** | A1 — Invalid order/duplicate position: retain the old order and show MSG-014. A2 — Lesson not published: show MSG-015. A3 — Missing parent: create no orphan child. |
| **Business Rules** | [BR-004](#br-004), [BR-012](#br-012), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-015](#msg-015), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented — constrained |

### UC-019 — Author rich-text Lesson content

**Feature:** RFE-05 — Lesson Materials, Lecturer Library and Reuse

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Content sanitizer |
| **Description** | Creates or updates the supported rich-text body of a lesson. |
| **Preconditions** | The actor is authorized for the Lesson or owns the Library asset. Uploads have a configured storage profile and allowed file/media type. |
| **Postconditions** | **Success:**<br>• The sanitized rich-text body is persisted and rendered in the editor.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Unsupported/oversized file: reject before persistence with MSG-016. A2 — Storage/resolver failure: keep the Lesson unchanged and show MSG-045/MSG-016. A3 — Unauthorized clone target: create no duplicate. |
| **Business Rules** | [BR-004](#br-004), [BR-013](#br-013) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-016](#msg-016), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented |

### UC-020 — Add, update or remove lesson files and media

**Feature:** RFE-05 — Lesson Materials, Lecturer Library and Reuse

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Object storage, external video resolver |
| **Description** | Manages PDF, downloadable attachment, uploaded video or YouTube/Vimeo material. |
| **Preconditions** | The actor is authorized for the Lesson or owns the Library asset. Uploads have a configured storage profile and allowed file/media type. |
| **Postconditions** | **Success:**<br>• The Lesson material metadata/reference is added, replaced or removed through protected delivery.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens Lesson materials.<br>2. The system checks file type, size and storage configuration.<br>3. The Lecturer uploads, replaces or removes a file/video link.<br>4. The system persists metadata and delivers a protected reference. |
| **Alternative Sequences/Flows** | A1 — Unsupported/oversized file: reject before persistence with MSG-016. A2 — Storage/resolver failure: keep the Lesson unchanged and show MSG-045/MSG-016. A3 — Unauthorized clone target: create no duplicate. |
| **Business Rules** | [BR-004](#br-004), [BR-013](#br-013) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-016](#msg-016), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented |

### UC-021 — Upload, rename or delete Lecturer Library assets

**Feature:** RFE-05 — Lesson Materials, Lecturer Library and Reuse

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Local/R2 object storage |
| **Description** | Maintains reusable personal teaching assets. |
| **Preconditions** | The actor is authorized for the Lesson or owns the Library asset. Uploads have a configured storage profile and allowed file/media type. |
| **Postconditions** | **Success:**<br>• The owned Library asset metadata and storage reference are updated.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor selects an eligible record and confirms the operation.<br>2. The system checks ownership, dependencies and lifecycle state.<br>3. The system applies the permitted delete/removal policy.<br>4. The system refreshes the view and records the mutation. |
| **Alternative Sequences/Flows** | A1 — Unsupported/oversized file: reject before persistence with MSG-016. A2 — Storage/resolver failure: keep the Lesson unchanged and show MSG-045/MSG-016. A3 — Unauthorized clone target: create no duplicate. |
| **Business Rules** | [BR-004](#br-004), [BR-013](#br-013) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-016](#msg-016), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented |

### UC-022 — Save and manage reusable Lesson templates

**Feature:** RFE-05 — Lesson Materials, Lecturer Library and Reuse

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Library/template service |
| **Description** | Saves, lists, renames and deletes reusable lesson templates. |
| **Preconditions** | The actor is authorized for the Lesson or owns the Library asset. Uploads have a configured storage profile and allowed file/media type. |
| **Postconditions** | **Success:**<br>• The reusable template is saved or its metadata is updated/deleted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Unsupported/oversized file: reject before persistence with MSG-016. A2 — Storage/resolver failure: keep the Lesson unchanged and show MSG-045/MSG-016. A3 — Unauthorized clone target: create no duplicate. |
| **Business Rules** | [BR-004](#br-004), [BR-013](#br-013), [BR-011](#br-011) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-016](#msg-016), [MSG-045](#msg-045), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-023 — Clone a template or existing Lesson into a Class Section

**Feature:** RFE-05 — Lesson Materials, Lecturer Library and Reuse

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Library/lesson service |
| **Description** | Copies reusable structure/content into an authorized target section. |
| **Preconditions** | The actor is authorized for the Lesson or owns the Library asset. Uploads have a configured storage profile and allowed file/media type. |
| **Postconditions** | **Success:**<br>• An independent Lesson copy is created in the authorized target Section.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer selects a template/source Lesson and target Section.<br>2. The system checks source access, target authorization and collision policy.<br>3. The Lecturer confirms Clone.<br>4. The system creates an independent copy and reports its new identity. |
| **Alternative Sequences/Flows** | A1 — Unsupported/oversized file: reject before persistence with MSG-016. A2 — Storage/resolver failure: keep the Lesson unchanged and show MSG-045/MSG-016. A3 — Unauthorized clone target: create no duplicate. |
| **Business Rules** | [BR-004](#br-004), [BR-013](#br-013), [BR-011](#br-011), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-016](#msg-016), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented |

### UC-024 — View published Lessons and protected materials

**Feature:** RFE-06 — Lesson Learning, Progress and Q&A Discussion

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Class access policy, storage delivery |
| **Description** | Consumes published content and permitted files/video in a joined class. |
| **Preconditions** | The Student is enrolled and the Lesson is published, or the Lecturer/moderator has discussion permission. |
| **Postconditions** | **Success:**<br>• Published Lesson content/materials are displayed only to an eligible Student.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Protected content unavailable: show MSG-015 without disclosing a private path. A2 — Comment mutation violates policy: show MSG-017. A3 — Progress write fails: retain last confirmed state and show MSG-018. |
| **Business Rules** | [BR-004](#br-004), [BR-011](#br-011) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-015](#msg-015), [MSG-017](#msg-017) |
| **Implementation Status** | Implemented |

### UC-025 — Mark and review Lesson completion progress

**Feature:** RFE-06 — Lesson Learning, Progress and Q&A Discussion

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Progress service |
| **Description** | Toggles completion and displays the learner’s progress. |
| **Preconditions** | The Student is enrolled and the Lesson is published, or the Lecturer/moderator has discussion permission. |
| **Postconditions** | **Success:**<br>• The Student’s completion state and progress summary are updated idempotently.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Student opens a Lesson progress control.<br>2. The system loads the last confirmed completion state.<br>3. The Student marks or unmarks completion.<br>4. The system applies an idempotent update and refreshes the summary. |
| **Alternative Sequences/Flows** | A1 — Protected content unavailable: show MSG-015 without disclosing a private path. A2 — Comment mutation violates policy: show MSG-017. A3 — Progress write fails: retain last confirmed state and show MSG-018. |
| **Business Rules** | [BR-004](#br-004), [BR-011](#br-011), [BR-015](#br-015) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-015](#msg-015), [MSG-017](#msg-017), [MSG-018](#msg-018) |
| **Implementation Status** | Implemented |

### UC-026 — Create and view Lesson questions/replies

**Feature:** RFE-06 — Lesson Learning, Progress and Q&A Discussion

| Field | Specification |
|---|---|
| **Primary Actors** | Student, Lecturer |
| **Secondary Actors** | Comment service |
| **Description** | Creates a discussion thread and loads questions/replies under a lesson. |
| **Preconditions** | The Student is enrolled and the Lesson is published, or the Lecturer/moderator has discussion permission. |
| **Postconditions** | **Success:**<br>• The permitted discussion thread/reply is persisted and displayed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Protected content unavailable: show MSG-015 without disclosing a private path. A2 — Comment mutation violates policy: show MSG-017. A3 — Progress write fails: retain last confirmed state and show MSG-018. |
| **Business Rules** | [BR-004](#br-004), [BR-011](#br-011), [BR-014](#br-014) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-015](#msg-015), [MSG-017](#msg-017) |
| **Implementation Status** | Implemented |

### UC-027 — Edit or delete eligible own discussion content

**Feature:** RFE-06 — Lesson Learning, Progress and Q&A Discussion

| Field | Specification |
|---|---|
| **Primary Actors** | Student, Lecturer |
| **Secondary Actors** | Comment authorization policy |
| **Description** | Changes or removes an eligible comment owned by the actor. |
| **Preconditions** | The Student is enrolled and the Lesson is published, or the Lecturer/moderator has discussion permission. |
| **Postconditions** | **Success:**<br>• The eligible own comment is edited/deleted and the visible thread is refreshed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor selects an eligible record and confirms the operation.<br>2. The system checks ownership, dependencies and lifecycle state.<br>3. The system applies the permitted delete/removal policy.<br>4. The system refreshes the view and records the mutation. |
| **Alternative Sequences/Flows** | A1 — Protected content unavailable: show MSG-015 without disclosing a private path. A2 — Comment mutation violates policy: show MSG-017. A3 — Progress write fails: retain last confirmed state and show MSG-018. |
| **Business Rules** | [BR-004](#br-004), [BR-011](#br-011), [BR-014](#br-014) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-015](#msg-015), [MSG-017](#msg-017) |
| **Implementation Status** | Implemented |

### UC-028 — Hide, restore or bulk-moderate discussion content

**Feature:** RFE-06 — Lesson Learning, Progress and Q&A Discussion

| Field | Specification |
|---|---|
| **Primary Actors** | Authorized moderator |
| **Secondary Actors** | Comment moderation/audit service |
| **Description** | Applies hide/unhide moderation through the permission-aware API. |
| **Preconditions** | The Student is enrolled and the Lesson is published, or the Lecturer/moderator has discussion permission. |
| **Postconditions** | **Success:**<br>• The moderation state is persisted and the discussion/audit view reflects it.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. An authorized moderator opens a discussion item.<br>2. The system checks moderation permission and current state.<br>3. The moderator hides, restores or bulk-updates selected items.<br>4. The system persists the decision and records an audit event. |
| **Alternative Sequences/Flows** | A1 — Protected content unavailable: show MSG-015 without disclosing a private path. A2 — Comment mutation violates policy: show MSG-017. A3 — Progress write fails: retain last confirmed state and show MSG-018. |
| **Business Rules** | [BR-004](#br-004), [BR-011](#br-011), [BR-014](#br-014), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-015](#msg-015), [MSG-017](#msg-017) |
| **Implementation Status** | Implemented — no separate Admin queue UI |

### UC-029 — Manage owned Flashcard Deck metadata and lifecycle

**Feature:** RFE-07 — Flashcards, Sharing and Smart Study

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated deck owner |
| **Secondary Actors** | Deck repository |
| **Description** | Creates, views, updates, deletes and controls visibility of an owned deck. |
| **Preconditions** | The actor is authenticated. Deck/card mutation requires ownership or explicit collaboration; study requires access through private, Class or public visibility. |
| **Postconditions** | **Success:**<br>• The owned Deck metadata/lifecycle is persisted and listings reflect its visibility.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the eligible draft or published record.<br>2. The system checks required content, dependencies and current lifecycle state.<br>3. The actor confirms the lifecycle action.<br>4. The system persists visibility/state and reports resulting availability. |
| **Alternative Sequences/Flows** | A1 — Deck/card inaccessible: show MSG-019 and disclose no private data. A2 — Invalid import row/image: report it without corrupting existing cards. A3 — Stale review state: preserve last confirmed interval. |
| **Business Rules** | [BR-004](#br-004), [BR-016](#br-016) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019) |
| **Implementation Status** | Implemented |

### UC-030 — Manage Cards and card images

**Feature:** RFE-07 — Flashcards, Sharing and Smart Study

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated deck owner |
| **Secondary Actors** | Card repository, object storage |
| **Description** | Creates/updates/deletes cards and attaches images. |
| **Preconditions** | The actor is authenticated. Deck/card mutation requires ownership or explicit collaboration; study requires access through private, Class or public visibility. |
| **Postconditions** | **Success:**<br>• The Card/image mutation is persisted under deck ownership policy.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Deck/card inaccessible: show MSG-019 and disclose no private data. A2 — Invalid import row/image: report it without corrupting existing cards. A3 — Stale review state: preserve last confirmed interval. |
| **Business Rules** | [BR-004](#br-004), [BR-016](#br-016), [BR-013](#br-013) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019), [MSG-016](#msg-016) |
| **Implementation Status** | Implemented |

### UC-031 — Preview or import Cards from Excel

**Feature:** RFE-07 — Flashcards, Sharing and Smart Study

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated deck owner |
| **Secondary Actors** | Excel parser/import service |
| **Description** | Previews rows and confirms card import into an owned deck. |
| **Preconditions** | The actor is authenticated. Deck/card mutation requires ownership or explicit collaboration; study requires access through private, Class or public visibility. The file is readable and uses the supported template. |
| **Postconditions** | **Success:**<br>• The confirmed card import persists valid rows and retains diagnostics.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The deck owner selects an Excel card file.<br>2. The system parses headers, rows and optional image references.<br>3. The system displays preview and row diagnostics.<br>4. The owner confirms; valid rows are persisted without corrupting existing cards. |
| **Alternative Sequences/Flows** | A1 — Deck/card inaccessible: show MSG-019 and disclose no private data. A2 — Invalid import row/image: report it without corrupting existing cards. A3 — Stale review state: preserve last confirmed interval. |
| **Business Rules** | [BR-004](#br-004), [BR-016](#br-016), [BR-010](#br-010), [BR-013](#br-013) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019), [MSG-012](#msg-012), [MSG-013](#msg-013), [MSG-014](#msg-014), [MSG-020](#msg-020) |
| **Implementation Status** | Implemented |

### UC-032 — Generate and confirm AI Flashcard drafts

**Feature:** RFE-07 — Flashcards, Sharing and Smart Study

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated deck owner |
| **Secondary Actors** | AI flashcard provider |
| **Description** | Requests draft cards, edits/selects them and persists confirmed cards. |
| **Preconditions** | The actor is authenticated. Deck/card mutation requires ownership or explicit collaboration; study requires access through private, Class or public visibility. The relevant AI purpose binding is enabled and a budget/credential is available. |
| **Postconditions** | **Success:**<br>• Contract-validated AI card drafts are selected and persisted only after owner confirmation.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The owner requests AI card drafts from a supported source.<br>2. The system resolves purpose binding, model and budget.<br>3. The provider result is contract-validated and shown as a preview.<br>4. The owner edits/selects drafts and confirms persistence. |
| **Alternative Sequences/Flows** | A1 — Deck/card inaccessible: show MSG-019 and disclose no private data. A2 — Invalid import row/image: report it without corrupting existing cards. A3 — Stale review state: preserve last confirmed interval. |
| **Business Rules** | [BR-004](#br-004), [BR-016](#br-016), [BR-029](#br-029), [BR-030](#br-030), [BR-031](#br-031) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — provider-dependent |

### UC-033 — Share/unshare a Deck to a Class or manage its public link

**Feature:** RFE-07 — Flashcards, Sharing and Smart Study

| Field | Specification |
|---|---|
| **Primary Actors** | Deck owner |
| **Secondary Actors** | Enrollment policy, public-link token service |
| **Description** | Controls class sharing and public-link enable/disable/regeneration. |
| **Preconditions** | The actor is authenticated. Deck/card mutation requires ownership or explicit collaboration; study requires access through private, Class or public visibility. |
| **Postconditions** | **Success:**<br>• Deck sharing/public-link state is persisted and access follows the new policy.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Deck/card inaccessible: show MSG-019 and disclose no private data. A2 — Invalid import row/image: report it without corrupting existing cards. A3 — Stale review state: preserve last confirmed interval. |
| **Business Rules** | [BR-004](#br-004), [BR-016](#br-016) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019) |
| **Implementation Status** | Implemented — owner-deck model |

### UC-034 — Study private, shared, public or mixed Decks with Smart Review

**Feature:** RFE-07 — Flashcards, Sharing and Smart Study

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated learner |
| **Secondary Actors** | SM-2/review service |
| **Description** | Studies accessible cards and records spaced-repetition/game-mode progress. |
| **Preconditions** | The actor is authenticated. Deck/card mutation requires ownership or explicit collaboration; study requires access through private, Class or public visibility. The actor has an eligible activity/attempt and the server clock is authoritative. |
| **Postconditions** | **Success:**<br>• Card ratings/progress are persisted and the next Smart Review schedule is calculated.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner selects an accessible deck and study mode.<br>2. The system loads due cards and review state.<br>3. The learner answers/rates cards.<br>4. The system updates the SM-2 schedule and study progress. |
| **Alternative Sequences/Flows** | A1 — Deck/card inaccessible: show MSG-019 and disclose no private data. A2 — Invalid import row/image: report it without corrupting existing cards. A3 — Stale review state: preserve last confirmed interval. |
| **Business Rules** | [BR-004](#br-004), [BR-016](#br-016), [BR-017](#br-017) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019), [MSG-020](#msg-020) |
| **Implementation Status** | Implemented |

### UC-035 — Contribute and maintain Department Question Bank items

**Feature:** RFE-08 — Department Question Bank Governance

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Question-bank service |
| **Description** | Creates, views and updates a department-scoped question draft. |
| **Preconditions** | The Lecturer is authorized for the Department Question Bank; category/review operations are restricted to the assigned Subject Leader. The item is in a compatible workflow state. |
| **Postconditions** | **Success:**<br>• The Department Question Bank draft item is persisted with validated answers/content.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Category hidden/missing/outside Department: do not persist and show MSG-021. A2 — Duplicate/malformed row: attach MSG-013/MSG-014. A3 — Concurrent review: show MSG-022 and require reload. |
| **Business Rules** | [BR-004](#br-004), [BR-018](#br-018), [BR-019](#br-019) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-021](#msg-021), [MSG-022](#msg-022) |
| **Implementation Status** | Implemented |

### UC-036 — Preview and confirm Question import from Excel

**Feature:** RFE-08 — Department Question Bank Governance

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Excel parser/import session |
| **Description** | Validates supported rows before persisting the import. |
| **Preconditions** | The Lecturer is authorized for the Department Question Bank; category/review operations are restricted to the assigned Subject Leader. The item is in a compatible workflow state. The file is readable and uses the supported template. |
| **Postconditions** | **Success:**<br>• The confirmed Question import persists valid draft items and diagnostics.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer uploads a question Excel file.<br>2. The system resolves Department categories and validates each row.<br>3. The system displays preview diagnostics without writing questions.<br>4. The Lecturer confirms; valid rows are persisted as drafts under the import session. |
| **Alternative Sequences/Flows** | A1 — Category hidden/missing/outside Department: do not persist and show MSG-021. A2 — Duplicate/malformed row: attach MSG-013/MSG-014. A3 — Concurrent review: show MSG-022 and require reload. |
| **Business Rules** | [BR-004](#br-004), [BR-018](#br-018), [BR-019](#br-019), [BR-010](#br-010) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-021](#msg-021), [MSG-022](#msg-022), [MSG-012](#msg-012), [MSG-013](#msg-013), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-037 — Manage Question draft, review and archive workflow

**Feature:** RFE-08 — Department Question Bank Governance

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer, Subject Leader |
| **Secondary Actors** | Question workflow/audit service |
| **Description** | Submits/revises drafts and moves eligible items through review/archive states. |
| **Preconditions** | The Lecturer is authorized for the Department Question Bank; category/review operations are restricted to the assigned Subject Leader. The item is in a compatible workflow state. Required fields and dependencies have been resolved. |
| **Postconditions** | **Success:**<br>• The Question workflow state/reason is persisted under the allowed transition.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant queue or record.<br>2. The system verifies scope, permission and current state.<br>3. The actor reviews evidence and chooses an allowed decision.<br>4. The system persists the decision/reason, records an audit event and refreshes the queue. |
| **Alternative Sequences/Flows** | A1 — Category hidden/missing/outside Department: do not persist and show MSG-021. A2 — Duplicate/malformed row: attach MSG-013/MSG-014. A3 — Concurrent review: show MSG-022 and require reload. |
| **Business Rules** | [BR-004](#br-004), [BR-018](#br-018), [BR-019](#br-019), [BR-005](#br-005), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-021](#msg-021), [MSG-022](#msg-022), [MSG-008](#msg-008) |
| **Implementation Status** | Implemented — state-dependent |

### UC-038 — Manage Department Question Bank categories

**Feature:** RFE-08 — Department Question Bank Governance

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Department category service |
| **Description** | Creates, views, updates, toggles and deletes department-owned categories. |
| **Preconditions** | The Lecturer is authorized for the Department Question Bank; category/review operations are restricted to the assigned Subject Leader. The item is in a compatible workflow state. |
| **Postconditions** | **Success:**<br>• The Department category tree/state is persisted without violating child/dependency rules.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Category hidden/missing/outside Department: do not persist and show MSG-021. A2 — Duplicate/malformed row: attach MSG-013/MSG-014. A3 — Concurrent review: show MSG-022 and require reload. |
| **Business Rules** | [BR-004](#br-004), [BR-018](#br-018), [BR-019](#br-019), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-021](#msg-021), [MSG-022](#msg-022), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-039 — Review, approve, reject or bulk-archive Questions

**Feature:** RFE-08 — Department Question Bank Governance

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Notification/audit service |
| **Description** | Reviews items individually or in bulk and records the decision/reason. |
| **Preconditions** | The Lecturer is authorized for the Department Question Bank; category/review operations are restricted to the assigned Subject Leader. The item is in a compatible workflow state. Required fields and dependencies have been resolved. |
| **Postconditions** | **Success:**<br>• The review decision/reason is persisted and approved/rejected/archive visibility updates.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Subject Leader opens the review queue.<br>2. The system lists eligible draft/review items.<br>3. The Leader opens evidence and approves, rejects or bulk-archives.<br>4. The system records reason/state and notifies contributors where configured. |
| **Alternative Sequences/Flows** | A1 — Category hidden/missing/outside Department: do not persist and show MSG-021. A2 — Duplicate/malformed row: attach MSG-013/MSG-014. A3 — Concurrent review: show MSG-022 and require reload. |
| **Business Rules** | [BR-004](#br-004), [BR-018](#br-018), [BR-019](#br-019), [BR-006](#br-006) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-021](#msg-021), [MSG-022](#msg-022), [MSG-008](#msg-008), [MSG-009](#msg-009) |
| **Implementation Status** | Implemented |

### UC-040 — Manage Test catalogue and metadata

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Test repository |
| **Description** | Creates, views and updates test identity, type and availability metadata. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. |
| **Postconditions** | **Success:**<br>• The Test metadata draft is persisted and available to the authorized Lecturer.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026) |
| **Implementation Status** | Implemented |

### UC-041 — Compose Test questions, media and timing

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Snapshot service, object storage |
| **Description** | Builds a test from manual input or approved snapshots and configures scoring/duration. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. |
| **Postconditions** | **Success:**<br>• The Test composition/timing/scoring configuration is persisted against valid snapshots.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens Test composition.<br>2. The system loads approved question choices and media references.<br>3. The Lecturer adds/removes/reorders questions and sets timing/scoring.<br>4. The system validates snapshots and persists the composition. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-013](#br-013) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-042 — Generate, review and confirm AI Question drafts

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | AI question-generation provider |
| **Description** | Generates a material-backed preview and appends selected confirmed questions. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. The relevant AI purpose binding is enabled and a budget/credential is available. |
| **Postconditions** | **Success:**<br>• Selected contract-validated AI Question drafts are appended only after Lecturer confirmation.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer supplies eligible source material and requests AI question drafts.<br>2. The system resolves purpose binding, model, budget and idempotency key.<br>3. The provider response is contract-validated and displayed as a preview.<br>4. The Lecturer selects/edits questions and confirms append to the Test. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-029](#br-029), [BR-030](#br-030), [BR-031](#br-031) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — provider-dependent |

### UC-043 — Preview and move a Test through draft/published/archived lifecycle

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Test lifecycle service |
| **Description** | Validates a learner preview and publishes or archives the test. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. |
| **Postconditions** | **Success:**<br>• The Test lifecycle/visibility state is persisted and learner availability follows it.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens Test Preview.<br>2. The system validates required questions, snapshots and availability.<br>3. The Lecturer publishes or archives the Test.<br>4. The system persists lifecycle state and exposes the correct learner visibility. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-005](#br-005), [BR-011](#br-011) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026), [MSG-008](#msg-008), [MSG-015](#msg-015) |
| **Implementation Status** | Implemented |

### UC-044 — Start, take, save and submit a timed Test attempt

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Auto-grading, heartbeat/deadline service |
| **Description** | Runs/resumes an attempt, saves answers, handles timeout and submits. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. The actor has an eligible activity/attempt and the server clock is authoritative. |
| **Postconditions** | **Success:**<br>• The attempt answers/submission state is persisted and grading is completed or queued.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Student starts or resumes an available Test.<br>2. The system creates/loads an attempt and authoritative deadline.<br>3. The Student answers while the client sends autosave/heartbeat requests.<br>4. The Student submits or the server closes at timeout; grading is invoked. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-021](#br-021) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026), [MSG-025](#msg-025) |
| **Implementation Status** | Implemented |

### UC-045 — View and review automatically graded results and answers

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Auto-grading service |
| **Description** | Displays result status, score, submitted/correct answers and explanations. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. |
| **Postconditions** | **Success:**<br>• The permitted result/answer review is displayed; a pending score is not presented as final.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Student opens a submitted result.<br>2. The system verifies ownership and grading state.<br>3. The system displays score, correct count, submitted answers and explanations.<br>4. If grading is pending, the system shows pending state instead of a fabricated score. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-022](#br-022) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026) |
| **Implementation Status** | Implemented |

### UC-046 — Monitor attempts/submissions and inspect history

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Live monitor/activity service |
| **Description** | Watches active attempts, opens submissions and reviews test history. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. |
| **Postconditions** | **Success:**<br>• The permitted attempt/submission/history records are displayed without changing them.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-021](#br-021), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-047 — Create a custom Test and review readiness

**Feature:** RFE-09 — Class Tests and Assessment

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Test sampling/readiness service |
| **Description** | Creates an objective-question practice test and views the derived readiness score. |
| **Preconditions** | The Lecturer is assigned to the Test scope. A Student has eligible enrollment and may access only a published, available Test. Published attempts use immutable snapshots. |
| **Postconditions** | **Success:**<br>• The custom Test snapshot and derived readiness result are persisted/displayed from available evidence.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Student selects topics/difficulty/types for a custom Test.<br>2. The system samples eligible questions and creates a practice snapshot.<br>3. The Student completes or reviews the custom attempt.<br>4. The system calculates and displays readiness using available progress/results. |
| **Alternative Sequences/Flows** | A1 — Unpublished/archived/out-of-window/past deadline: block with MSG-023/MSG-024. A2 — Missing snapshot: mark unavailable and fabricate no score. A3 — Grading pending/fails: show MSG-026 and permit policy-approved retry. |
| **Business Rules** | [BR-004](#br-004), [BR-020](#br-020), [BR-022](#br-022) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-024](#msg-024), [MSG-026](#msg-026) |
| **Implementation Status** | Implemented |

### UC-048 — Manage Assignment drafts, instructions and settings

**Feature:** RFE-10 — Class Assignments, Submissions and Feedback

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Assignment service |
| **Description** | Creates, views and updates assignment instructions, deadline and score settings. |
| **Preconditions** | The Lecturer owns an eligible Class Assignment. Students are enrolled and the Assignment is published; server time and late rules are authoritative. |
| **Postconditions** | **Success:**<br>• The Assignment draft/settings are persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens New Assignment or an owned draft.<br>2. The system checks Class scope and editable state.<br>3. The Lecturer enters instructions, deadline and score settings.<br>4. The system validates and persists the draft. |
| **Alternative Sequences/Flows** | A1 — Closed/late policy blocks: show MSG-027 and accept no new version. A2 — Duplicate submit/upload failure: preserve last confirmed submission and show MSG-028. A3 — Feedback unreleased: show MSG-029. |
| **Business Rules** | [BR-004](#br-004), [BR-023](#br-023) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-027](#msg-027), [MSG-029](#msg-029), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-049 — Publish or close an Assignment

**Feature:** RFE-10 — Class Assignments, Submissions and Feedback

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Assignment lifecycle service |
| **Description** | Publishes a draft or closes a published assignment. |
| **Preconditions** | The Lecturer owns an eligible Class Assignment. Students are enrolled and the Assignment is published; server time and late rules are authoritative. Required fields and dependencies have been resolved. |
| **Postconditions** | **Success:**<br>• The Assignment lifecycle/visibility state is persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens an eligible Assignment.<br>2. The system validates required fields and deadline policy.<br>3. The Lecturer publishes or closes it.<br>4. The system changes learner visibility and records the lifecycle event. |
| **Alternative Sequences/Flows** | A1 — Closed/late policy blocks: show MSG-027 and accept no new version. A2 — Duplicate submit/upload failure: preserve last confirmed submission and show MSG-028. A3 — Feedback unreleased: show MSG-029. |
| **Business Rules** | [BR-004](#br-004), [BR-023](#br-023), [BR-005](#br-005), [BR-011](#br-011) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-027](#msg-027), [MSG-029](#msg-029), [MSG-008](#msg-008), [MSG-015](#msg-015) |
| **Implementation Status** | Implemented |

### UC-050 — View, submit or resubmit Assignment work

**Feature:** RFE-10 — Class Assignments, Submissions and Feedback

| Field | Specification |
|---|---|
| **Primary Actors** | Student |
| **Secondary Actors** | Deadline/late-policy service |
| **Description** | Views an assignment, submits text/link work and resubmits when permitted. |
| **Preconditions** | The Lecturer owns an eligible Class Assignment. Students are enrolled and the Assignment is published; server time and late rules are authoritative. The actor has an eligible activity/attempt and the server clock is authoritative. |
| **Postconditions** | **Success:**<br>• The submission version/timestamp is persisted and the Student sees its status.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Student opens a published Assignment.<br>2. The system checks enrollment, deadline and late policy.<br>3. The Student submits or resubmits text/link work.<br>4. The system stores the version/timestamp and confirms its status. |
| **Alternative Sequences/Flows** | A1 — Closed/late policy blocks: show MSG-027 and accept no new version. A2 — Duplicate submit/upload failure: preserve last confirmed submission and show MSG-028. A3 — Feedback unreleased: show MSG-029. |
| **Business Rules** | [BR-004](#br-004), [BR-023](#br-023) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-027](#msg-027), [MSG-029](#msg-029), [MSG-028](#msg-028) |
| **Implementation Status** | Implemented |

### UC-051 — Review and grade Assignment submissions

**Feature:** RFE-10 — Class Assignments, Submissions and Feedback

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Submission repository |
| **Description** | Lists submissions and enters/updates a grade. |
| **Preconditions** | The Lecturer owns an eligible Class Assignment. Students are enrolled and the Assignment is published; server time and late rules are authoritative. |
| **Postconditions** | **Success:**<br>• The Lecturer grade is validated and persisted for the submission.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer opens Assignment submissions.<br>2. The system lists submissions in the owned Class.<br>3. The Lecturer opens a submission and enters/updates a grade.<br>4. The system validates the score and saves the grading state. |
| **Alternative Sequences/Flows** | A1 — Closed/late policy blocks: show MSG-027 and accept no new version. A2 — Duplicate submit/upload failure: preserve last confirmed submission and show MSG-028. A3 — Feedback unreleased: show MSG-029. |
| **Business Rules** | [BR-004](#br-004), [BR-023](#br-023), [BR-024](#br-024) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-027](#msg-027), [MSG-029](#msg-029), [MSG-028](#msg-028) |
| **Implementation Status** | Implemented |

### UC-052 — Release and view Assignment score/feedback

**Feature:** RFE-10 — Class Assignments, Submissions and Feedback

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer, Student |
| **Secondary Actors** | Notification service |
| **Description** | Releases a lecturer comment/score and lets the student view it. |
| **Preconditions** | The Lecturer owns an eligible Class Assignment. Students are enrolled and the Assignment is published; server time and late rules are authoritative. |
| **Postconditions** | **Success:**<br>• The feedback/release state is persisted and the Student sees it only when released.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer chooses feedback/release for a graded submission.<br>2. The system checks release policy and persists comment/score visibility.<br>3. The Student opens the released result.<br>4. The system displays only feedback permitted by release state. |
| **Alternative Sequences/Flows** | A1 — Closed/late policy blocks: show MSG-027 and accept no new version. A2 — Duplicate submit/upload failure: preserve last confirmed submission and show MSG-028. A3 — Feedback unreleased: show MSG-029. |
| **Business Rules** | [BR-004](#br-004), [BR-023](#br-023), [BR-024](#br-024) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-023](#msg-023), [MSG-027](#msg-027), [MSG-029](#msg-029) |
| **Implementation Status** | Implemented |

### UC-053 — View/open notifications and unread state

**Feature:** RFE-11 — Notifications, Messaging and Durable Email

| Field | Specification |
|---|---|
| **Primary Actors** | Student, Lecturer, Subject Leader, Admin |
| **Secondary Actors** | Notification service |
| **Description** | Lists recent notifications, opens details and displays unread count/state. |
| **Preconditions** | The actor has an active session. Recipient visibility and conversation access are checked before any message is created or displayed. |
| **Postconditions** | **Success:**<br>• Notification read/unread state is displayed and any mark-read change is persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Recipient not permitted/missing: show MSG-031 without directory disclosure. A2 — SMTP unavailable: persist in-app state and outbox retry MSG-032. A3 — Concurrent read/send is de-duplicated. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004), [BR-025](#br-025) |
| **System Messages** | [MSG-001](#msg-001), [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-030](#msg-030), [MSG-031](#msg-031) |
| **Implementation Status** | Implemented |

### UC-054 — Find recipients and start an authorized conversation

**Feature:** RFE-11 — Notifications, Messaging and Durable Email

| Field | Specification |
|---|---|
| **Primary Actors** | All authenticated users |
| **Secondary Actors** | User directory/messaging service |
| **Description** | Resolves permitted recipients and creates a conversation. |
| **Preconditions** | The actor has an active session. Recipient visibility and conversation access are checked before any message is created or displayed. |
| **Postconditions** | **Success:**<br>• An authorized conversation is created and its identifier is returned.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens New Message and searches recipients.<br>2. The system applies role/scope directory policy.<br>3. The actor selects an allowed recipient and starts a conversation.<br>4. The system creates the thread and returns its identifier. |
| **Alternative Sequences/Flows** | A1 — Recipient not permitted/missing: show MSG-031 without directory disclosure. A2 — SMTP unavailable: persist in-app state and outbox retry MSG-032. A3 — Concurrent read/send is de-duplicated. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004), [BR-025](#br-025) |
| **System Messages** | [MSG-001](#msg-001), [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-030](#msg-030), [MSG-031](#msg-031) |
| **Implementation Status** | Implemented |

### UC-055 — Read, reply to and mark internal conversations

**Feature:** RFE-11 — Notifications, Messaging and Durable Email

| Field | Specification |
|---|---|
| **Primary Actors** | All authenticated users |
| **Secondary Actors** | Messaging transport |
| **Description** | Lists conversations, exchanges messages and updates read/unread state. |
| **Preconditions** | The actor has an active session. Recipient visibility and conversation access are checked before any message is created or displayed. |
| **Postconditions** | **Success:**<br>• The permitted message/reply and read cursor are persisted and the latest thread is displayed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens a permitted conversation.<br>2. The system loads messages and the read cursor.<br>3. The actor reads and replies.<br>4. The system persists the message, updates unread state and returns the latest thread. |
| **Alternative Sequences/Flows** | A1 — Recipient not permitted/missing: show MSG-031 without directory disclosure. A2 — SMTP unavailable: persist in-app state and outbox retry MSG-032. A3 — Concurrent read/send is de-duplicated. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004), [BR-025](#br-025) |
| **System Messages** | [MSG-001](#msg-001), [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-030](#msg-030), [MSG-031](#msg-031) |
| **Implementation Status** | Implemented |

### UC-056 — Receive important notifications through durable email

**Feature:** RFE-11 — Notifications, Messaging and Durable Email

| Field | Specification |
|---|---|
| **Primary Actors** | All authenticated users |
| **Secondary Actors** | SMTP durable outbox |
| **Description** | Delivers selected important events when SMTP/outbox configuration is available. |
| **Preconditions** | The actor has an active session. Recipient visibility and conversation access are checked before any message is created or displayed. |
| **Postconditions** | **Success:**<br>• The important event is durably queued/delivered or retained for retry; no event is silently lost.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. A configured important event is emitted.<br>2. The durable outbox records an idempotent email job.<br>3. The worker sends through SMTP or records a retryable failure.<br>4. In-app state remains authoritative and the recipient is notified when delivery succeeds. |
| **Alternative Sequences/Flows** | A1 — Recipient not permitted/missing: show MSG-031 without directory disclosure. A2 — SMTP unavailable: persist in-app state and outbox retry MSG-032. A3 — Concurrent read/send is de-duplicated. |
| **Business Rules** | [BR-001](#br-001), [BR-004](#br-004), [BR-025](#br-025), [BR-026](#br-026) |
| **System Messages** | [MSG-001](#msg-001), [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-030](#msg-030), [MSG-031](#msg-031), [MSG-032](#msg-032) |
| **Implementation Status** | Implemented — selected events only |

### UC-057 — View Lecturer teaching dashboard and class summaries

**Feature:** RFE-12 — Dashboards and Learning Tracking

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Aggregate query services |
| **Description** | Displays assigned classes, counts and teaching summary metrics. |
| **Preconditions** | The actor has the dashboard role and permitted scope. An empty aggregate is valid; unavailable sources must be marked. |
| **Postconditions** | **Success:**<br>• The permitted dashboard aggregates are displayed with their freshness/empty state.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Source aggregate unavailable/stale: show MSG-034 and label incomplete. A2 — Forbidden scope: return only authorized rows. A3 — Refresh uses the same query policy. |
| **Business Rules** | [BR-004](#br-004), [BR-022](#br-022) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-058 — Track learner lesson, test and assignment progress

**Feature:** RFE-12 — Dashboards and Learning Tracking

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Progress/test/assignment aggregates |
| **Description** | Displays learning completion and assessment/submission status by class/student. |
| **Preconditions** | The actor has the dashboard role and permitted scope. An empty aggregate is valid; unavailable sources must be marked. |
| **Postconditions** | **Success:**<br>• The permitted learner progress/assessment summary is displayed with incomplete data marked.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Source aggregate unavailable/stale: show MSG-034 and label incomplete. A2 — Forbidden scope: return only authorized rows. A3 — Refresh uses the same query policy. |
| **Business Rules** | [BR-004](#br-004), [BR-022](#br-022), [BR-015](#br-015) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-059 — View Subject Leader department dashboard/report

**Feature:** RFE-12 — Dashboards and Learning Tracking

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Department report service |
| **Description** | Displays department-level class, enrollment and activity summaries. |
| **Preconditions** | The actor has the dashboard role and permitted scope. An empty aggregate is valid; unavailable sources must be marked. |
| **Postconditions** | **Success:**<br>• The Department dashboard/report is displayed with scope and freshness indicators.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Source aggregate unavailable/stale: show MSG-034 and label incomplete. A2 — Forbidden scope: return only authorized rows. A3 — Refresh uses the same query policy. |
| **Business Rules** | [BR-004](#br-004), [BR-022](#br-022) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-060 — View Admin system-wide dashboard and activity summaries

**Feature:** RFE-12 — Dashboards and Learning Tracking

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Aggregate/audit repositories |
| **Description** | Displays available system-wide user, class, activity and security summaries. |
| **Preconditions** | The actor has the dashboard role and permitted scope. An empty aggregate is valid; unavailable sources must be marked. |
| **Postconditions** | **Success:**<br>• The Admin system dashboard is displayed without exposing restricted secrets.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — Source aggregate unavailable/stale: show MSG-034 and label incomplete. A2 — Forbidden scope: return only authorized rows. A3 — Refresh uses the same query policy. |
| **Business Rules** | [BR-004](#br-004), [BR-022](#br-022), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented — current activity sources only |

### UC-061 — View assigned Department information and Classes

**Feature:** RFE-13 — Subject Leader Department Oversight

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Department resolver/class repository |
| **Description** | Displays the Leader’s department identity and class portfolio. |
| **Preconditions** | The Subject Leader is assigned to the Department and the requested Class/report belongs to that Department. |
| **Postconditions** | **Success:**<br>• The assigned Department/Class portfolio is displayed; no identity data is changed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — No assigned Department: show MSG-006 and record the failure. A2 — Queue state changed: show MSG-008 and refresh. A3 — Incomplete report data: show MSG-034. |
| **Business Rules** | [BR-004](#br-004), [BR-006](#br-006) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-062 — Review Department Class queue and status

**Feature:** RFE-13 — Subject Leader Department Oversight

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Class approval service |
| **Description** | Filters pending/approved/rejected class outcomes for the department. |
| **Preconditions** | The Subject Leader is assigned to the Department and the requested Class/report belongs to that Department. |
| **Postconditions** | **Success:**<br>• The Department Class queue/status records are displayed; no duplicate decision is made.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant queue or record.<br>2. The system verifies scope, permission and current state.<br>3. The actor reviews evidence and chooses an allowed decision.<br>4. The system persists the decision/reason, records an audit event and refreshes the queue. |
| **Alternative Sequences/Flows** | A1 — No assigned Department: show MSG-006 and record the failure. A2 — Queue state changed: show MSG-008 and refresh. A3 — Incomplete report data: show MSG-034. |
| **Business Rules** | [BR-004](#br-004), [BR-006](#br-006), [BR-005](#br-005) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-063 — Assign or reassign Lecturer to a Department Class

**Feature:** RFE-13 — Subject Leader Department Oversight

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | User/class service |
| **Description** | Assigns an eligible Lecturer to a department class. |
| **Preconditions** | The Subject Leader is assigned to the Department and the requested Class/report belongs to that Department. |
| **Postconditions** | **Success:**<br>• The Lecturer assignment is persisted and the Class portfolio reflects it.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — No assigned Department: show MSG-006 and record the failure. A2 — Queue state changed: show MSG-008 and refresh. A3 — Incomplete report data: show MSG-034. |
| **Business Rules** | [BR-004](#br-004), [BR-006](#br-006), [BR-005](#br-005) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-034](#msg-034), [MSG-033](#msg-033) |
| **Implementation Status** | Implemented |

### UC-064 — View Department activity, enrollment and learning report

**Feature:** RFE-13 — Subject Leader Department Oversight

| Field | Specification |
|---|---|
| **Primary Actors** | Subject Leader |
| **Secondary Actors** | Report aggregation service |
| **Description** | Displays department activity and performance summaries. |
| **Preconditions** | The Subject Leader is assigned to the Department and the requested Class/report belongs to that Department. |
| **Postconditions** | **Success:**<br>• The Department activity/report aggregates are displayed with incomplete segments marked.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant list, dashboard or detail view.<br>2. The system checks permission, scope and current visibility.<br>3. The system retrieves and filters permitted records.<br>4. The system renders the result, empty state or scoped error. |
| **Alternative Sequences/Flows** | A1 — No assigned Department: show MSG-006 and record the failure. A2 — Queue state changed: show MSG-008 and refresh. A3 — Incomplete report data: show MSG-034. |
| **Business Rules** | [BR-004](#br-004), [BR-006](#br-006), [BR-022](#br-022) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-008](#msg-008), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-065 — Administer complete User account lifecycle

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | User/audit service |
| **Description** | Creates, views, updates, activates/deactivates, locks/unlocks, resets, deletes or restores accounts. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The eligible User lifecycle change is persisted and audited.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Admin opens Users and selects an account.<br>2. The system checks Admin permission and loads current state.<br>3. The Admin performs an eligible lifecycle action and confirms.<br>4. The system validates dependencies, persists state and records an audit event. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-066 — Assign Roles and per-user permissions

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Permission/audit service |
| **Description** | Assigns a role and manages a justified per-user permission override. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The role/per-user permission change is persisted and audited.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-067 — Manage Role permission matrix and overrides

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Permission/audit service |
| **Description** | Configures role-level permissions and audits changes. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The role permission matrix/override is persisted and audited.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-068 — Manage Departments and assign Subject Leaders

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Department/audit service |
| **Description** | Creates/views/updates/toggles departments and assigns or clears the Leader. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The Department/Leader configuration is persisted and audited.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Admin opens Departments.<br>2. The system lists department records and current Leaders.<br>3. The Admin creates/updates/toggles a Department or assigns a Leader.<br>4. The system validates references and records the resulting configuration. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-069 — Manage generic hierarchical subject Categories

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Category tree service |
| **Description** | Maintains the global `/admin/categories` taxonomy. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The global category tree/state is persisted and dependency-safe.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Admin opens the hierarchical subject taxonomy.<br>2. The system loads parent/child categories and dependencies.<br>3. The Admin creates/updates/toggles/deletes an eligible category.<br>4. The system validates slugs/children and persists the tree. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034) |
| **Implementation Status** | Implemented |

### UC-070 — Configure branding, general, OAuth, SMTP and dictionary integrations

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Google OAuth, SMTP, KRDICT |
| **Description** | Configures general settings and tests supported integrations. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The validated general/integration setting is stored with secrets masked.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Admin opens General/Integration Settings.<br>2. The system loads masked current configuration.<br>3. The Admin changes branding or OAuth/SMTP/dictionary settings and may test them.<br>4. The system validates, stores safe values and records the result. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040), [BR-039](#br-039) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented |

### UC-071 — Configure/test/audit AI providers, prompts and Practice bindings

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | AI providers/control plane |
| **Description** | Manages provider/model settings, prompts, capability tests, logs and Practice purpose bindings. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The validated provider/prompt/binding configuration and capability log are stored.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Admin opens AI Providers, Prompts or Practice Bindings.<br>2. The system loads masked provider metadata and purpose mappings.<br>3. The Admin edits a binding/prompt and runs an allowed capability test.<br>4. The system validates the contract, stores safe configuration and records provider logs. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040), [BR-029](#br-029), [BR-030](#br-030), [BR-039](#br-039) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — provider-dependent |

### UC-072 — Configure/test Storage profiles and managed secrets

**Feature:** RFE-14 — Admin User, Taxonomy and Platform Administration

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Local/R2/object-storage provider |
| **Description** | Configures global/storage profiles and tests them; Google Drive is not a current provider. |
| **Preconditions** | The actor is an authenticated Admin with required permission. Sensitive settings/destructive changes require validation and audit. |
| **Postconditions** | **Success:**<br>• The validated Storage profile/test result is stored; secrets remain protected.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Admin opens Storage Profiles.<br>2. The system loads masked local/R2 configuration and the active profile.<br>3. The Admin edits or tests a profile.<br>4. The system validates connectivity, stores only permitted secrets and reports the active result. |
| **Alternative Sequences/Flows** | A1 — Permission missing: show MSG-033 without revealing secrets. A2 — Invalid setting/provider/secret: keep previous value and show MSG-045. A3 — Dependants prevent deletion: block with MSG-033. |
| **Business Rules** | [BR-004](#br-004), [BR-027](#br-027), [BR-040](#br-040), [BR-039](#br-039) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-033](#msg-033), [MSG-034](#msg-034), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented — constrained |

### UC-073 — Generate AI Question drafts from source material

**Feature:** RFE-15 — AI-Assisted Learning and Evaluation

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer |
| **Secondary Actors** | Configured AI question provider |
| **Description** | Generates a selectable question preview from uploaded/pasted material and confirms selected drafts. |
| **Preconditions** | The learner/author is authorized for the source artifact. An enabled AI purpose binding, model and budget policy exist. The relevant AI purpose binding is enabled and a budget/credential is available. |
| **Postconditions** | **Success:**<br>• A contract-validated AI Question draft artifact is displayed/persisted for review.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Lecturer/author submits source material for an AI question draft.<br>2. The system resolves purpose binding, model, budget and idempotency.<br>3. The provider response is contract-validated and displayed.<br>4. The actor reviews, edits and confirms selected drafts. |
| **Alternative Sequences/Flows** | A1 — Provider timeout/quota/budget: record retryable failure without fabricating score/content. A2 — Malformed/unsafe output: reject through contract validation. A3 — Duplicate retry returns the existing idempotent artifact. |
| **Business Rules** | [BR-004](#br-004), [BR-029](#br-029), [BR-030](#br-030), [BR-031](#br-031) |
| **System Messages** | [MSG-006](#msg-006), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — provider-dependent |

### UC-074 — Generate AI Flashcard drafts

**Feature:** RFE-15 — AI-Assisted Learning and Evaluation

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated deck owner |
| **Secondary Actors** | Configured AI flashcard provider |
| **Description** | Generates, edits and confirms draft cards for an owned deck. |
| **Preconditions** | The learner/author is authorized for the source artifact. An enabled AI purpose binding, model and budget policy exist. The relevant AI purpose binding is enabled and a budget/credential is available. |
| **Postconditions** | **Success:**<br>• A contract-validated AI Flashcard draft artifact is displayed/persisted for owner review.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The deck owner supplies a source/prompt for AI cards.<br>2. The system resolves provider binding and budget.<br>3. The provider response is contract-validated.<br>4. The owner edits/selects cards and confirms persistence. |
| **Alternative Sequences/Flows** | A1 — Provider timeout/quota/budget: record retryable failure without fabricating score/content. A2 — Malformed/unsafe output: reject through contract validation. A3 — Duplicate retry returns the existing idempotent artifact. |
| **Business Rules** | [BR-004](#br-004), [BR-029](#br-029), [BR-030](#br-030), [BR-031](#br-031) |
| **System Messages** | [MSG-006](#msg-006), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — provider-dependent |

### UC-075 — Evaluate Practice Writing/Speaking responses

**Feature:** RFE-15 — AI-Assisted Learning and Evaluation

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Configured AI provider, STT/TTS |
| **Description** | Requests structured evaluation/transcription and displays normalized evidence and rubric state. |
| **Preconditions** | The learner/author is authorized for the source artifact. An enabled AI purpose binding, model and budget policy exist. The relevant AI purpose binding is enabled and a budget/credential is available. |
| **Postconditions** | **Success:**<br>• A normalized Writing/Speaking evaluation artifact or explicit pending state is displayed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner submits a Writing/Speaking response.<br>2. The system creates an idempotent evaluation job and transcribes audio where needed.<br>3. The provider returns structured rubric evidence.<br>4. The system normalizes and displays evaluation or pending/retry state. |
| **Alternative Sequences/Flows** | A1 — Provider timeout/quota/budget: record retryable failure without fabricating score/content. A2 — Malformed/unsafe output: reject through contract validation. A3 — Duplicate retry returns the existing idempotent artifact. |
| **Business Rules** | [BR-004](#br-004), [BR-029](#br-029), [BR-030](#br-030), [BR-031](#br-031), [BR-037](#br-037) |
| **System Messages** | [MSG-006](#msg-006), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038), [MSG-044](#msg-044) |
| **Implementation Status** | Implemented — async/provider-dependent |

### UC-076 — Generate and retry Practice Reading/Listening explanations

**Feature:** RFE-15 — AI-Assisted Learning and Evaluation

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Configured AI provider, execution audit |
| **Description** | Generates an eligible explanation artifact and retries a failed or stale artifact. |
| **Preconditions** | The learner/author is authorized for the source artifact. An enabled AI purpose binding, model and budget policy exist. The relevant AI purpose binding is enabled and a budget/credential is available. |
| **Postconditions** | **Success:**<br>• A normalized Reading/Listening explanation artifact or explicit retryable state is displayed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner requests an explanation for an eligible Reading/Listening item.<br>2. The system checks source/attempt access and creates an idempotent job.<br>3. The provider response is validated and stored as an explanation artifact.<br>4. The learner views it or retries when the prior job failed/staled. |
| **Alternative Sequences/Flows** | A1 — Provider timeout/quota/budget: record retryable failure without fabricating score/content. A2 — Malformed/unsafe output: reject through contract validation. A3 — Duplicate retry returns the existing idempotent artifact. |
| **Business Rules** | [BR-004](#br-004), [BR-029](#br-029), [BR-030](#br-030), [BR-031](#br-031) |
| **System Messages** | [MSG-006](#msg-006), [MSG-035](#msg-035), [MSG-036](#msg-036), [MSG-037](#msg-037), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — async/provider-dependent |

### UC-077 — Look up Korean vocabulary through the shared dictionary

**Feature:** RFE-16 — Shared Korean Dictionary and Flashcard Capture

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated user |
| **Secondary Actors** | Korean Basic Dictionary Open API |
| **Description** | Looks up a Korean word or phrase through the shared KSH helper. |
| **Preconditions** | The actor is authenticated and an Admin has configured a valid KRDICT connection. |
| **Postconditions** | **Success:**<br>• Normalized Korean, pronunciation, Vietnamese meaning, part of speech and provider URL are displayed.<br>**Failure:**<br>• No Flashcard data is changed and the system returns a referenced message. |
| **Normal Sequence/Flow** | 1. The actor selects or enters a Korean word/phrase.<br>2. The system normalizes and validates the Hangul input.<br>3. The system calls the official KRDICT endpoint through the shared outbound client.<br>4. The system validates XML and displays the normalized definition. |
| **Alternative Sequences/Flows** | A1 — Input has no Korean characters: reject before any provider call. A2 — Dictionary is not configured or has no result: show MSG-039. A3 — Provider response/host is rejected: show MSG-040. |
| **Business Rules** | [BR-004](#br-004), [BR-032](#br-032) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-039](#msg-039), [MSG-040](#msg-040) |
| **Implementation Status** | Implemented |

### UC-078 — Select an owned Flashcard deck for a dictionary term

**Feature:** RFE-16 — Shared Korean Dictionary and Flashcard Capture

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated user |
| **Secondary Actors** | Flashcard deck service |
| **Description** | Loads decks owned by the actor so that a dictionary term can be saved safely. |
| **Preconditions** | The actor is authenticated and has at least one accessible personal Flashcard deck. |
| **Postconditions** | **Success:**<br>• The actor can select an owned deck.<br>**Failure:**<br>• No dictionary or Flashcard state is changed. |
| **Normal Sequence/Flow** | 1. The actor opens Save to Flashcards from a dictionary result.<br>2. The system resolves decks by the authenticated owner ID.<br>3. The system returns deck title and card count.<br>4. The actor selects one destination deck. |
| **Alternative Sequences/Flows** | A1 — No owned deck exists: show an empty deck option state. A2 — Session is invalid: show MSG-006 and stop. |
| **Business Rules** | [BR-004](#br-004), [BR-033](#br-033) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019) |
| **Implementation Status** | Implemented |

### UC-079 — Save or reuse a dictionary term as a Flashcard

**Feature:** RFE-16 — Shared Korean Dictionary and Flashcard Capture

| Field | Specification |
|---|---|
| **Primary Actors** | Authenticated user |
| **Secondary Actors** | KRDICT API, flashcard service |
| **Description** | Looks up a term and saves selected vocabulary/meaning into a deck. |
| **Preconditions** | The actor is authenticated, selects a deck they own, and supplies a normalized Korean word and Vietnamese meaning. |
| **Postconditions** | **Success:**<br>• The vocabulary card is created/reused in the permitted deck.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor looks up a Korean term.<br>2. The system returns normalized dictionary data.<br>3. The actor selects a target deck and confirms Save.<br>4. The system creates or reuses the vocabulary card and reports the result. |
| **Alternative Sequences/Flows** | A1 — The target deck is absent or not owned by the actor: reject without creating a card. A2 — Word or meaning is invalid: reject before persistence. A3 — Duplicate saved term returns the existing card and deck link. |
| **Business Rules** | [BR-004](#br-004), [BR-032](#br-032), [BR-033](#br-033) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-019](#msg-019) |
| **Implementation Status** | Implemented |

### UC-080 — Configure the shared KRDICT connection

**Feature:** RFE-16 — Shared Korean Dictionary and Flashcard Capture

| Field | Specification |
|---|---|
| **Primary Actors** | Admin |
| **Secondary Actors** | Korean Basic Dictionary Open API |
| **Description** | Stores the shared API key and official endpoint used by the global dictionary helper. |
| **Preconditions** | The actor is an authenticated Admin. |
| **Postconditions** | **Success:**<br>• The validated settings are persisted in the `DICTIONARY` group and secrets remain masked.<br>**Failure:**<br>• The previous configuration remains active. |
| **Normal Sequence/Flow** | 1. The Admin opens Settings → Korean Dictionary.<br>2. The system loads the masked key and configured endpoint.<br>3. The Admin updates the key and/or endpoint.<br>4. The system accepts only the official HTTPS KRDICT host, persists the values, and invalidates the settings cache. |
| **Alternative Sequences/Flows** | A1 — Non-KRDICT endpoint: reject the update. A2 — Invalid key shape: reject the update. A3 — Missing key: dictionary lookup stays unavailable without affecting Flashcard data. |
| **Business Rules** | [BR-004](#br-004), [BR-032](#br-032), [BR-040](#br-040) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-039](#msg-039), [MSG-040](#msg-040) |
| **Implementation Status** | Implemented |

### UC-081 — Browse/filter accessible Practice catalog

**Feature:** RFE-17 — Independent Practice Hub — Learner Experience

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Practice catalog/access service |
| **Description** | Searches published sets/tests by skill/task and optional class scope. |
| **Preconditions** | The learner has an active account and the Practice set/version is published and accessible under its scope. Device/media checks apply only to relevant skills. |
| **Postconditions** | **Success:**<br>• Only accessible published Practice catalog results are displayed.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner opens Practice catalog.<br>2. The system applies skill/task/query/publication/scope filters.<br>3. The learner opens a result or changes page.<br>4. The system returns only accessible published sets/tests and counts. |
| **Alternative Sequences/Flows** | A1 — Device/media preflight fails: show MSG-041 and do not start the affected task. A2 — Attempt/record locked or expired: reject stale writes with MSG-042/MSG-024. A3 — Score pending: show MSG-026 and expose permitted retry. |
| **Business Rules** | [BR-001](#br-001), [BR-034](#br-034) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-041](#msg-041), [MSG-042](#msg-042), [MSG-043](#msg-043) |
| **Implementation Status** | Implemented — current GLOBAL/CLASS hybrid |

### UC-082 — View set/test details and complete Listening/Speaking preflight

**Feature:** RFE-17 — Independent Practice Hub — Learner Experience

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Media/preflight service |
| **Description** | Opens instructions/materials and completes device/media checks where required. |
| **Preconditions** | The learner has an active account and the Practice set/version is published and accessible under its scope. Device/media checks apply only to relevant skills. |
| **Postconditions** | **Success:**<br>• Preflight readiness/remediation is displayed and no attempt is started.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner opens Practice detail.<br>2. The system displays instructions, sources, media requirements and task metadata.<br>3. For Listening/Speaking, the learner grants/checks required device/media capability.<br>4. The system reports ready or remediation state without starting an attempt. |
| **Alternative Sequences/Flows** | A1 — Device/media preflight fails: show MSG-041 and do not start the affected task. A2 — Attempt/record locked or expired: reject stale writes with MSG-042/MSG-024. A3 — Score pending: show MSG-026 and expose permitted retry. |
| **Business Rules** | [BR-001](#br-001), [BR-034](#br-034), [BR-037](#br-037) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-041](#msg-041), [MSG-042](#msg-042), [MSG-043](#msg-043) |
| **Implementation Status** | Implemented — feature/config guarded |

### UC-083 — Start/resume/autosave/submit/discard a four-skill attempt

**Feature:** RFE-17 — Independent Practice Hub — Learner Experience

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Deadline/media/storage service |
| **Description** | Runs Reading/Listening/Writing/Speaking attempts, saves work and handles interruption/deadline. |
| **Preconditions** | The learner has an active account and the Practice set/version is published and accessible under its scope. Device/media checks apply only to relevant skills. The actor has an eligible activity/attempt and the server clock is authoritative. |
| **Postconditions** | **Success:**<br>• The Practice attempt state, autosaves and final submission/discard state are persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner starts or resumes a published Practice version.<br>2. The system creates/locks the attempt and returns task state/deadlines.<br>3. The client autosaves answers/media while the learner navigates tasks.<br>4. The learner submits or discards; the server finalizes state and queues scoring. |
| **Alternative Sequences/Flows** | A1 — Device/media preflight fails: show MSG-041 and do not start the affected task. A2 — Attempt/record locked or expired: reject stale writes with MSG-042/MSG-024. A3 — Score pending: show MSG-026 and expose permitted retry. |
| **Business Rules** | [BR-001](#br-001), [BR-034](#br-034), [BR-035](#br-035), [BR-036](#br-036), [BR-037](#br-037) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-041](#msg-041), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-024](#msg-024), [MSG-025](#msg-025), [MSG-044](#msg-044) |
| **Implementation Status** | Implemented |

### UC-084 — View Practice result/detail/progress and request re-evaluation

**Feature:** RFE-17 — Independent Practice Hub — Learner Experience

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Scoring/AI retry service |
| **Description** | Views objective/AI result state and requests a permitted retry/re-evaluation. |
| **Preconditions** | The learner has an active account and the Practice set/version is published and accessible under its scope. Device/media checks apply only to relevant skills. |
| **Postconditions** | **Success:**<br>• The Practice result/progress is displayed; a retry is queued or completed under policy.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The learner opens a Practice result/detail/progress view.<br>2. The system verifies attempt ownership and scoring state.<br>3. The system renders objective/AI evidence and progress.<br>4. The learner requests permitted re-evaluation/retry and receives queued or completed status. |
| **Alternative Sequences/Flows** | A1 — Device/media preflight fails: show MSG-041 and do not start the affected task. A2 — Attempt/record locked or expired: reject stale writes with MSG-042/MSG-024. A3 — Score pending: show MSG-026 and expose permitted retry. |
| **Business Rules** | [BR-001](#br-001), [BR-034](#br-034), [BR-022](#br-022), [BR-031](#br-031) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-041](#msg-041), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-026](#msg-026), [MSG-038](#msg-038) |
| **Implementation Status** | Implemented — async/provider-dependent |

### UC-085 — Manage Practice learner preferences

**Feature:** RFE-17 — Independent Practice Hub — Learner Experience

| Field | Specification |
|---|---|
| **Primary Actors** | Student/learner |
| **Secondary Actors** | Preference service |
| **Description** | Updates Practice presentation preferences such as Korean font. |
| **Preconditions** | The learner has an active account and the Practice set/version is published and accessible under its scope. Device/media checks apply only to relevant skills. |
| **Postconditions** | **Success:**<br>• The learner preference is validated and persisted.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The actor opens the relevant management form.<br>2. The system loads defaults and checks scope/ownership.<br>3. The actor submits the requested create/update/assignment data.<br>4. The system validates, persists the aggregate and displays its resulting state. |
| **Alternative Sequences/Flows** | A1 — Device/media preflight fails: show MSG-041 and do not start the affected task. A2 — Attempt/record locked or expired: reject stale writes with MSG-042/MSG-024. A3 — Score pending: show MSG-026 and expose permitted retry. |
| **Business Rules** | [BR-001](#br-001), [BR-034](#br-034) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-041](#msg-041), [MSG-042](#msg-042), [MSG-043](#msg-043) |
| **Implementation Status** | Implemented |

### UC-086 — Create/edit/delete Practice drafts manually

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice author |
| **Secondary Actors** | Draft/lifecycle service |
| **Description** | Creates and edits a draft and deletes an eligible unpublished draft. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. |
| **Postconditions** | **Success:**<br>• The eligible Practice draft is persisted or deleted according to draft policy.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The Practice author opens the authoring workspace.<br>2. The system creates or loads an owned draft.<br>3. The author edits content or confirms deletion of an eligible unpublished draft.<br>4. The system validates and persists the draft state. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035), [BR-028](#br-028) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047) |
| **Implementation Status** | Implemented |

### UC-087 — Autosave, lock, collaborate, review revisions and restore versions

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice author/reviewer |
| **Secondary Actors** | Optimistic-lock/version service |
| **Description** | Preserves edits across authors and restores an eligible prior revision. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. |
| **Postconditions** | **Success:**<br>• The revision/lock history is persisted and an authorized restore creates the expected current revision.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The author opens a draft/revision panel.<br>2. The system obtains a lock/version token and loads the latest revision.<br>3. Collaborators autosave or submit a revision.<br>4. The system detects conflicts, records history and permits an authorized restore. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047) |
| **Implementation Status** | Implemented |

### UC-088 — Import Practice through Excel template/preview/validation

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice author |
| **Secondary Actors** | Excel parser/import service |
| **Description** | Uploads, previews, validates and applies structured Practice rows. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. The file is readable and uses the supported template. |
| **Postconditions** | **Success:**<br>• The confirmed Excel import applies only valid Practice candidates and retains diagnostics.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The author uploads the Practice Excel template.<br>2. The system parses schema, rows and references.<br>3. The system shows preview diagnostics and candidate counts.<br>4. The author confirms; only valid rows are applied to the draft/import session. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035), [BR-010](#br-010), [BR-038](#br-038) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047), [MSG-012](#msg-012), [MSG-013](#msg-013), [MSG-014](#msg-014) |
| **Implementation Status** | Implemented |

### UC-089 — Import PDF with page ranges, extraction, annotations and assets

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice author |
| **Secondary Actors** | PDF extraction/object storage |
| **Description** | Creates an extraction session, annotates extracted material and prepares candidate content/assets. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. The file is readable and uses the supported template. |
| **Postconditions** | **Success:**<br>• The extraction session/assets/candidates are stored without publishing unreviewed content.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The author selects a PDF and page range.<br>2. The system stores the source, extracts text/images and creates an extraction session.<br>3. The author annotates/edits extracted material and selects assets.<br>4. The system prepares candidates without publishing unreviewed content. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035), [BR-010](#br-010), [BR-013](#br-013), [BR-038](#br-038) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047), [MSG-012](#msg-012), [MSG-016](#msg-016) |
| **Implementation Status** | Implemented |

### UC-090 — Review, edit, ready, reject, preview and apply an authoring candidate

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice reviewer |
| **Secondary Actors** | Candidate validation service |
| **Description** | Curates a candidate and applies only an eligible reviewed payload to a draft. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. |
| **Postconditions** | **Success:**<br>• The candidate decision and applied payload are persisted only after validation/review.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The reviewer opens the candidate queue.<br>2. The system displays source evidence, diagnostics and candidate status.<br>3. The reviewer edits, marks ready/rejects or previews the candidate.<br>4. The system applies only a valid reviewed payload to the target draft and records the decision. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035), [BR-030](#br-030), [BR-038](#br-038) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047), [MSG-022](#msg-022) |
| **Implementation Status** | Implemented |

### UC-091 — Publish immutable versions and archive/unarchive Practice Sets

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice author |
| **Secondary Actors** | Lifecycle/version service |
| **Description** | Resolves blockers and manages published version compatibility with attempts. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. Required fields and dependencies have been resolved. |
| **Postconditions** | **Success:**<br>• The immutable version/lifecycle state is persisted while existing attempt references remain valid.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The author opens a ready Practice Set.<br>2. The system validates blockers, assets and version compatibility.<br>3. The author publishes an immutable version or archives/unarchives the set.<br>4. The system updates learner visibility while preserving references used by existing attempts. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035), [BR-005](#br-005), [BR-036](#br-036) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047), [MSG-008](#msg-008) |
| **Implementation Status** | Implemented |

### UC-092 — Manage Practice material assets and speaking media

**Feature:** RFE-18 — Practice Authoring, Publishing, Media and Content Operations

| Field | Specification |
|---|---|
| **Primary Actors** | Lecturer/Practice author |
| **Secondary Actors** | Protected object storage, STT/TTS, ffprobe |
| **Description** | Adds/removes protected assets, authors speaking prompts/audio/transcripts and retries media processing. |
| **Preconditions** | The author/reviewer is authorized for the Practice workspace. Draft/version/candidate operations use optimistic locking; import/media operations require valid files and configured providers. |
| **Postconditions** | **Success:**<br>• Protected assets/media metadata and processing status are persisted and exposed for retry/status.<br>**Failure:**<br>• No unauthorized or partial state change is committed; the system returns a referenced message and records the failure where required. |
| **Normal Sequence/Flow** | 1. The author opens Practice assets/media.<br>2. The system checks protected storage and media format policy.<br>3. The author uploads/removes assets or authors speaking prompt/audio/transcript.<br>4. The system validates or queues STT/TTS/ffprobe processing and exposes retry/status. |
| **Alternative Sequences/Flows** | A1 — Lock/stale revision: preserve latest committed revision and show MSG-042. A2 — Import/extraction/candidate validation fails: apply no invalid candidate and show MSG-046/MSG-047. A3 — Publish/storage/media failure: remain unpublished and expose retry/status. |
| **Business Rules** | [BR-004](#br-004), [BR-035](#br-035), [BR-013](#br-013), [BR-037](#br-037) |
| **System Messages** | [MSG-006](#msg-006), [MSG-007](#msg-007), [MSG-042](#msg-042), [MSG-043](#msg-043), [MSG-046](#msg-046), [MSG-047](#msg-047), [MSG-016](#msg-016), [MSG-044](#msg-044), [MSG-045](#msg-045) |
| **Implementation Status** | Implemented — storage/provider guarded |

## BR/MSG reuse matrix

This reverse index makes the many-to-many relationship explicit: each row lists every UC that references the rule/message.

### Business Rules → Use Cases

| Rule | Referenced by use cases |
|---|---|
| BR-001 | UC-001, UC-002, UC-003, UC-004, UC-053, UC-054, UC-055, UC-056, UC-081, UC-082, UC-083, UC-084, UC-085 |
| BR-002 | UC-001, UC-002 |
| BR-003 | UC-001 |
| BR-004 | UC-001, UC-002, UC-003, UC-004, UC-005, UC-006, UC-007, UC-008, UC-009, UC-010, UC-011, UC-012, UC-013, UC-014, UC-015, UC-016, UC-017, UC-018, UC-019, UC-020, UC-021, UC-022, UC-023, UC-024, UC-025, UC-026, UC-027, UC-028, UC-029, UC-030, UC-031, UC-032, UC-033, UC-034, UC-035, UC-036, UC-037, UC-038, UC-039, UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047, UC-048, UC-049, UC-050, UC-051, UC-052, UC-053, UC-054, UC-055, UC-056, UC-057, UC-058, UC-059, UC-060, UC-061, UC-062, UC-063, UC-064, UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072, UC-073, UC-074, UC-075, UC-076, UC-077, UC-078, UC-079, UC-080, UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| BR-005 | UC-005, UC-006, UC-007, UC-008, UC-016, UC-037, UC-043, UC-049, UC-062, UC-063, UC-091 |
| BR-006 | UC-008, UC-039, UC-061, UC-062, UC-063, UC-064 |
| BR-007 | UC-009, UC-010, UC-011, UC-012, UC-013 |
| BR-008 | UC-010, UC-011 |
| BR-009 | UC-009, UC-010, UC-011, UC-012, UC-013 |
| BR-010 | UC-013, UC-031, UC-036, UC-088, UC-089 |
| BR-011 | UC-016, UC-017, UC-022, UC-023, UC-024, UC-025, UC-026, UC-027, UC-028, UC-043, UC-049 |
| BR-012 | UC-014, UC-015, UC-016, UC-017, UC-018 |
| BR-013 | UC-019, UC-020, UC-021, UC-022, UC-023, UC-030, UC-031, UC-041, UC-089, UC-092 |
| BR-014 | UC-026, UC-027, UC-028 |
| BR-015 | UC-025, UC-058 |
| BR-016 | UC-029, UC-030, UC-031, UC-032, UC-033, UC-034 |
| BR-017 | UC-034 |
| BR-018 | UC-035, UC-036, UC-037, UC-038, UC-039 |
| BR-019 | UC-035, UC-036, UC-037, UC-038, UC-039 |
| BR-020 | UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047 |
| BR-021 | UC-044, UC-046 |
| BR-022 | UC-045, UC-047, UC-057, UC-058, UC-059, UC-060, UC-064, UC-084 |
| BR-023 | UC-048, UC-049, UC-050, UC-051, UC-052 |
| BR-024 | UC-051, UC-052 |
| BR-025 | UC-053, UC-054, UC-055, UC-056 |
| BR-026 | UC-056 |
| BR-027 | UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072 |
| BR-028 | UC-007, UC-023, UC-037, UC-038, UC-065, UC-068, UC-069, UC-086 |
| BR-029 | UC-032, UC-042, UC-071, UC-073, UC-074, UC-075, UC-076 |
| BR-030 | UC-032, UC-042, UC-071, UC-073, UC-074, UC-075, UC-076, UC-090 |
| BR-031 | UC-032, UC-042, UC-073, UC-074, UC-075, UC-076, UC-084 |
| BR-032 | UC-077, UC-078, UC-079, UC-080 |
| BR-033 | UC-079 |
| BR-034 | UC-081, UC-082, UC-083, UC-084, UC-085 |
| BR-035 | UC-083, UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| BR-036 | UC-083, UC-091 |
| BR-037 | UC-075, UC-082, UC-083, UC-092 |
| BR-038 | UC-088, UC-089, UC-090 |
| BR-039 | UC-070, UC-071, UC-072 |
| BR-040 | UC-018, UC-028, UC-046, UC-060, UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072, UC-080 |

### System Messages → Use Cases

| Message | Referenced by use cases |
|---|---|
| MSG-001 | UC-001, UC-002, UC-003, UC-004, UC-053, UC-054, UC-055, UC-056 |
| MSG-002 | UC-001, UC-002, UC-003, UC-004 |
| MSG-003 | UC-001, UC-002, UC-003, UC-004 |
| MSG-004 | UC-001, UC-002, UC-003, UC-004 |
| MSG-005 | UC-001, UC-002, UC-003 |
| MSG-006 | UC-005, UC-006, UC-007, UC-008, UC-009, UC-010, UC-011, UC-012, UC-013, UC-014, UC-015, UC-016, UC-017, UC-018, UC-019, UC-020, UC-021, UC-022, UC-023, UC-024, UC-025, UC-026, UC-027, UC-028, UC-029, UC-030, UC-031, UC-032, UC-033, UC-034, UC-035, UC-036, UC-037, UC-038, UC-039, UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047, UC-048, UC-049, UC-050, UC-051, UC-052, UC-053, UC-054, UC-055, UC-056, UC-057, UC-058, UC-059, UC-060, UC-061, UC-062, UC-063, UC-064, UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072, UC-073, UC-074, UC-075, UC-076, UC-077, UC-078, UC-079, UC-080, UC-081, UC-082, UC-083, UC-084, UC-085, UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| MSG-007 | UC-005, UC-006, UC-007, UC-008, UC-009, UC-010, UC-011, UC-012, UC-013, UC-014, UC-015, UC-016, UC-017, UC-018, UC-019, UC-020, UC-021, UC-022, UC-023, UC-024, UC-025, UC-026, UC-027, UC-028, UC-029, UC-030, UC-031, UC-032, UC-033, UC-034, UC-035, UC-036, UC-037, UC-038, UC-039, UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047, UC-048, UC-049, UC-050, UC-051, UC-052, UC-053, UC-054, UC-055, UC-056, UC-057, UC-058, UC-059, UC-060, UC-061, UC-062, UC-063, UC-064, UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072, UC-077, UC-078, UC-079, UC-080, UC-081, UC-082, UC-083, UC-084, UC-085, UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| MSG-008 | UC-005, UC-006, UC-007, UC-008, UC-014, UC-015, UC-016, UC-017, UC-018, UC-037, UC-039, UC-043, UC-049, UC-061, UC-062, UC-063, UC-064, UC-091 |
| MSG-009 | UC-008, UC-039 |
| MSG-010 | UC-009, UC-010, UC-011, UC-012, UC-013 |
| MSG-011 | UC-009, UC-010, UC-011, UC-012, UC-013 |
| MSG-012 | UC-013, UC-031, UC-036, UC-088, UC-089 |
| MSG-013 | UC-013, UC-031, UC-036, UC-088 |
| MSG-014 | UC-013, UC-014, UC-015, UC-022, UC-031, UC-036, UC-038, UC-041, UC-048, UC-088 |
| MSG-015 | UC-014, UC-015, UC-016, UC-017, UC-018, UC-024, UC-025, UC-026, UC-027, UC-028, UC-043, UC-049 |
| MSG-016 | UC-019, UC-020, UC-021, UC-022, UC-023, UC-030, UC-089, UC-092 |
| MSG-017 | UC-024, UC-025, UC-026, UC-027, UC-028 |
| MSG-018 | UC-025 |
| MSG-019 | UC-029, UC-030, UC-031, UC-032, UC-033, UC-034, UC-079 |
| MSG-020 | UC-031, UC-034 |
| MSG-021 | UC-035, UC-036, UC-037, UC-038, UC-039 |
| MSG-022 | UC-035, UC-036, UC-037, UC-038, UC-039, UC-090 |
| MSG-023 | UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047, UC-048, UC-049, UC-050, UC-051, UC-052 |
| MSG-024 | UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047, UC-083 |
| MSG-025 | UC-044, UC-083 |
| MSG-026 | UC-040, UC-041, UC-042, UC-043, UC-044, UC-045, UC-046, UC-047, UC-084 |
| MSG-027 | UC-048, UC-049, UC-050, UC-051, UC-052 |
| MSG-028 | UC-050, UC-051 |
| MSG-029 | UC-048, UC-049, UC-050, UC-051, UC-052 |
| MSG-030 | UC-053, UC-054, UC-055, UC-056 |
| MSG-031 | UC-053, UC-054, UC-055, UC-056 |
| MSG-032 | UC-056 |
| MSG-033 | UC-063, UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072 |
| MSG-034 | UC-018, UC-046, UC-057, UC-058, UC-059, UC-060, UC-061, UC-062, UC-063, UC-064, UC-065, UC-066, UC-067, UC-068, UC-069, UC-070, UC-071, UC-072 |
| MSG-035 | UC-032, UC-042, UC-071, UC-073, UC-074, UC-075, UC-076 |
| MSG-036 | UC-032, UC-042, UC-071, UC-073, UC-074, UC-075, UC-076 |
| MSG-037 | UC-032, UC-042, UC-071, UC-073, UC-074, UC-075, UC-076 |
| MSG-038 | UC-032, UC-042, UC-071, UC-073, UC-074, UC-075, UC-076, UC-084 |
| MSG-039 | UC-077, UC-078, UC-079, UC-080 |
| MSG-040 | UC-077, UC-078, UC-079, UC-080 |
| MSG-041 | UC-081, UC-082, UC-083, UC-084, UC-085 |
| MSG-042 | UC-081, UC-082, UC-083, UC-084, UC-085, UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| MSG-043 | UC-081, UC-082, UC-083, UC-084, UC-085, UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| MSG-044 | UC-075, UC-083, UC-092 |
| MSG-045 | UC-019, UC-020, UC-021, UC-022, UC-023, UC-070, UC-072, UC-092 |
| MSG-046 | UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |
| MSG-047 | UC-086, UC-087, UC-088, UC-089, UC-090, UC-091, UC-092 |

## Traceability and specification notes

- These boundaries follow current routes, services, entities and lifecycle states. “Manage” groups only operations with the same aggregate, actor and policy; approval, import confirmation, publication, attempts, grading/release and provider evaluation remain separate.
- **Constrained**, **provider-dependent** and **product decision pending** statuses must be called out in later design/test work; they are not silently treated as production-complete.
- Practice learner runtime is specified separately from authoring, media and AI evaluation. Current code supports both `GLOBAL` and `CLASS` scope; the catalogue does not imply that `/practice` must be class-bound.
- Admin global subject taxonomy and Subject Leader Department Question Bank taxonomy are separate concepts and are specified under different features.
