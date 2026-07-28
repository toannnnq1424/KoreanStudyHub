# KSH Java 17 toolchain contract

Status: repository guard accepted in Phase 13H on 2026-07-28.

The only supported build and runtime JDK for this repository is Java 17.
Java 18 or newer, including Java 26, is rejected intentionally.

## Repository guards

- `.java-version` is exactly `17` followed by one LF. It was a user-owned
  untracked candidate before Phase 13H; Phase 13H verified those bytes and
  accepted it as a repository-owned guard.
- Maven Wrapper resolves Maven 3.9.16.
- `pom.xml` sets `java.version=17`, compiles with `release=17`, and runs Maven
  Enforcer during `validate`. Enforcer accepts Java `[17,18)` and Maven
  `[3.9,4)`.
- `.run/KshApplication_Java_17.run.xml` is the shared Spring Boot run
  configuration. It uses only module `ksh`, main class
  `com.ksh.KshApplication`, and an IntelliJ SDK named `17`.

## CLI

Point `JAVA_HOME` at a Java 17 installation before invoking `./mvnw`. Confirm
agreement with:

```text
java -version
./mvnw -version
./mvnw validate
```

All three commands must report Java 17. Do not work around Enforcer with skip
flags.

## IntelliJ IDEA

1. Register the local Java 17 installation as an SDK named `17`.
2. Set Project SDK and language level to SDK default/17.
3. In Maven settings, set both Importer JDK and Runner JRE to Project SDK
   (`17`), then reload the Maven project from the repository `pom.xml`.
4. Select the shared `KshApplication (Java 17)` configuration.
5. If an old local project has module `ulp`, a manually pinned Lombok 1.18.36
   annotation-processor path, or another generated Maven profile, remove that
   ignored local metadata and reload Maven. The live module is `ksh`; Lombok
   comes from the resolved Maven graph.

The repository intentionally does not commit `.idea` or `.iml` files. Those
files are host-local state, so copying their stale processor/module entries
would not be a portable fix. Maven Enforcer remains the final IDE/CLI
agreement check.

## Migration baseline safety

Flyway validation is enabled and clean is disabled in normal application
configuration. Published V1–V56 files are additionally byte-locked by
`practice-migrations-v1-v56.sha256`; corrections are forward-only.

The ignored schema `ksh_phase13e_result_ui` is noncanonical evidence only. It
contains an old V45 whose DDL later became the current V55, skipped the current
V45, and recorded a failed V55 while validation had been disabled. Never
repair, clean, reuse, or cite it as migration proof.

The user-authorized diagnostic schema `ksh_phase13h_intellij_fresh` was created
empty and reached V56 before Phase 13H validation. Its ignored datasource
configuration and the database itself are not repository artifacts.

V1's cross-schema `CREATE DATABASE IF NOT EXISTS ksh_db` and the published
`TINYINT(1)` spellings are retained to preserve applied checksums. Their MySQL
9.7 warnings are compatibility debt for a separately authorized new baseline,
not permission to rewrite migration history.
