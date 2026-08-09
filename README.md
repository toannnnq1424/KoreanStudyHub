# Korean Study Hub (KSH)

Korean Study Hub is a server-rendered learning platform for Korean study. It
supports subject governance, class-based teaching, shared learning materials,
question and test banks, assignments, flashcards, messaging, and an independent
`/practice` learning boundary.

> This repository is a Spring Boot application, not a separated SPA plus API
> backend. Thymeleaf renders the primary HTML on the server; JavaScript enhances
> selected interactions and real-time messaging.

## Product workflow at a glance

The current product model uses a flat **Subject** catalog. The historical
Department/Course/Category hierarchy is retired from the target workflow.

1. **Subject governance** — an Admin manages active subjects and assigns one
   Subject Leader to each subject. A Leader can own multiple subjects.
2. **Class lifecycle** — a Lecturer creates a `DRAFT` class with a required
   subject; its Subject Leader approves it to `ACTIVE` or records a rejection
   note. An optional end date archives active classes automatically.
3. **Membership and co-teaching** — students request entry to active classes;
   the class owner approves or rejects. Subject Leaders may assign
   co-lecturers, without changing the class owner or creator.
4. **Library distribution** — learning content is authored under
   Subject → Chapter → Lesson, then immutable snapshots are distributed to
   active classes.
5. **Question bank** — lecturers contribute lesson-linked questions; Leaders
   review them for approval, rejection, archive, or restoration.
6. **Test bank and distribution** — a test can be authored directly, generated
   from approved questions, and distributed as independent snapshots to active
   same-subject classes.
7. **Class learning** — students consume distributed lessons, take tests,
   submit assignments, study flashcards, and use the class member directory for
   direct messages.
8. **Independent practice** — `/practice` has its own authoring, catalog,
   attempt, media, and AI-evaluation boundaries. It is not a class test bank.

For the complete current-state workflow and medium-grain use-case inventory,
see `docs/CURRENT_STATE_FEATURE_AND_USE_CASE_CATALOG_2026-08-04.md` when that
audit artifact is present in the working tree.

## Architecture

| Layer | Technology / responsibility |
| --- | --- |
| Web | Spring MVC, Thymeleaf SSR, static CSS/JavaScript |
| Security | Spring Security, session authentication, role/subject/class scope checks, Google OAuth client |
| Domain & data | Java 17, Spring Data JPA / Hibernate, MySQL 8, Flyway-owned schema |
| Realtime | Spring WebSocket with STOMP/SockJS for direct messaging |
| Files | Local development storage or S3-compatible Cloudflare R2 profiles |
| Integrations | SMTP, optional Google OAuth, Korean Basic Dictionary, bounded optional AI providers |

Flyway owns schema changes. Hibernate runs with `ddl-auto=validate`; do not
use Hibernate to create or mutate a shared schema.

## Prerequisites

- JDK 17
- Maven 3.9+ (use the checked-in Maven wrapper)
- MySQL 8
- A local configuration file at
  `src/main/resources/application-local.properties` or equivalent environment
  variables

The local configuration file is ignored by Git. Keep database and provider
credentials out of tracked files.

## Run locally

1. Create a MySQL database, for example `ksh_db`.
2. Configure local credentials. Minimal example:

   ```properties
   DB_URL=jdbc:mysql://localhost:3306/ksh_db?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
   DB_USERNAME=root
   DB_PASSWORD=your-local-password
   ```

3. Start the application:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

4. Open [http://localhost:8080](http://localhost:8080).

Use a different port without editing tracked configuration:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"
```

For UI-only iteration, run with the development profile and refresh the
browser after editing templates or static assets:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,ui-dev"
```

The Maven configuration enables `addResources`, and the `ui-dev` profile turns
off template/static resource caches. Java/service/configuration changes still
need a restart or DevTools restart.

## Build and test

```powershell
# Compile only
.\mvnw.cmd -DskipTests compile

# Compile test sources
.\mvnw.cmd -DskipTests test-compile

# Run a focused test class
.\mvnw.cmd "-Dtest=StudentTestFlowIntegrationTest" test
```

Database-backed tests must point to an explicit disposable test database using
`TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`; never run a test
suite against a shared development or production schema.

## Integration-test strategy

KSH's main integration boundary is **MVC + security + service + JPA + MySQL**,
not only a JSON API contract. Most integration tests should use
`@SpringBootTest`, `@AutoConfigureMockMvc`, Spring Security test support, and
a disposable MySQL/Flyway database to assert:

- status, redirect, rendered view/model, and CSRF/session behaviour;
- role, subject, class-owner, co-lecturer, and student enrollment scope;
- persisted state, audit/outbox side effects, and transaction atomicity;
- JSON/multipart endpoints with MockMvc when a feature exposes an API;
- scheduled workers with a controlled clock or worker trigger; and
- a small Browser/Playwright smoke suite for critical end-to-end journeys.

Postman or an API runner remains useful for JSON/multipart debugging, but it is
not sufficient evidence for server-rendered HTML flows or database side
effects. The repository-wide proposed case inventory is delivered in
`Report5.2_Integration_Test_KSH_2026-08-04.xlsx` when generated for a review.

### Agile incremental and iterative testing

KSH follows an **Agile incremental and iterative** delivery model. Integration
testing is therefore maintained as a living regression asset rather than a
single final-test document:

1. Each increment/sprint adds or revises cases for the affected workflow,
   permissions, persistence effects, and integration adapters.
2. The feature's focused integration suite runs in the same iteration as the
   change; impacted cross-feature cases form its regression set.
3. The workbook's Round 1–3 fields record repeat execution. A failed case is
   fixed and rerun in a later round; unexecuted scope remains `Pending` instead
   of being reported as passed.
4. A release candidate executes the accumulated critical-flow regression set
   against a disposable migrated database, followed by browser smoke journeys.

## Deployment outline

1. Build a release JAR with JDK 17 and run the focused/full validation required
   for the release.
2. Provision MySQL, storage (R2 or the approved provider), SMTP/OAuth and any
   optional AI credentials through environment variables or a secret manager.
3. Set `APP_BASE_URL`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and storage
   configuration. Do not commit secrets.
4. Start the JAR behind HTTPS/reverse proxy. Flyway validates and migrates the
   target schema at startup; back up the database before deployment.
5. Keep production storage profiles fail-closed: local storage is an explicit
   development/test opt-in.

## Boundaries and current technical debt

- `/practice` has deliberately separate storage and AI configuration; do not
  couple it to the class/test bank workflow.
- Java package and some technical names may still say `Department` while the
  operational catalog is `subjects`; treat this as technical debt, not proof
  of the retired hierarchy workflow.
- Current-state documentation records known deviations (for example, lesson
  distribution eligibility and class rejection resubmission notification) so
  they can be tracked explicitly rather than accidentally becoming behaviour.
