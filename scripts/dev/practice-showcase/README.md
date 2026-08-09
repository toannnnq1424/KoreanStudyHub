# KSH Practice Showcase seed

Dev-only fixture for presenting the complete `/practice` flow on a fresh
database. It does not modify Flyway, Practice AI configuration, or object
storage configuration.

## Dataset

- 13 public `GLOBAL` mixed-skill sets; no set is attached to a class.
- 3 tests per set.
- 4 sections per test: Reading, Listening, Writing, and Speaking.
- 156 sections, 156 question groups, and 936 questions in total.
- Complete immutable published-version graph for every live row.
- 37 governed local assets:
  - 13 cover images;
  - 8 question-stimulus images;
  - 8 Korean Listening clips;
  - 8 Korean Speaking prompt clips.
- 231 published-version material references.
- Teacher guidance in every group plus a durable explanation on every
  question.

All Korean prompts, images, and audio are original KSH demo material. They are
not official TOPIK questions or media.

## Safety rules

`PracticeShowcaseSeeder` refuses to run unless the JDBC catalog starts with
`ksh_practice_demo_`. It uses a MySQL named lock, a single database
transaction, immutable snapshot checks, governed-media checks, and physical
file hash verification. A complete existing fixture is verified and skipped;
a partial fixture is rejected.

Keep the normal development database intact. Create a separate schema, point
the application at it, and let the application's existing Flyway chain finish
before importing.

Example schema name:

```text
ksh_practice_demo_20260729
```

## Generate original media

Run from the repository root in Windows PowerShell:

```powershell
powershell.exe -ExecutionPolicy Bypass -File `
  scripts\dev\practice-showcase\New-PracticeShowcaseAssets.ps1
```

The committed assets are already generated. Run this only when the fixture
media needs to be rebuilt.

## Compile and import

Use JDK 17 and the MySQL Connector/J already resolved by Maven:

```powershell
$connector = Get-ChildItem `
  "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j" `
  -Filter "mysql-connector-j-*.jar" -Recurse |
  Sort-Object FullName -Descending |
  Select-Object -First 1

$classes = ".artifact-work\practice-showcase-classes-j17"
New-Item -ItemType Directory -Force -Path $classes | Out-Null

& "$env:JAVA_HOME\bin\javac.exe" --release 17 -encoding UTF-8 `
  -cp $connector.FullName -d $classes `
  scripts\dev\practice-showcase\PracticeShowcaseSeeder.java

$env:DB_URL = "jdbc:mysql://localhost:3306/ksh_practice_demo_20260729?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<local-dev-password>"
$env:PRACTICE_SHOWCASE_ASSET_DIR = `
  (Resolve-Path "scripts\dev\practice-showcase\assets").Path
$env:UPLOAD_DIR = (Resolve-Path "uploads").Path

& "$env:JAVA_HOME\bin\java.exe" `
  -cp "$classes;$($connector.FullName)" PracticeShowcaseSeeder
```

Expected first-run summary:

```text
Imported 13 sets, 39 tests, 156 sections, 156 groups, 936 questions,
37 assets and 231 material references.
```

The known local demo accounts are `lecturer@ksh.edu.vn` and
`student@ksh.edu.vn`; their development-only password is `123456`.

