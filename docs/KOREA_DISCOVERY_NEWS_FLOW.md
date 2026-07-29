# Korea Discovery news flow

## What ships in this phase

`/discover` is an authenticated, source-attributed culture/news flow for KSH.
It ingests public metadata on a fixed five-hour delay, rejects political content,
boosts Vietnam-related stories, and assembles the feed into a lead story, a
three-minute digest, featured cards, latest stories, vocabulary and scholarship
opportunities.

This phase deliberately does **not** call AI. The source title stays unchanged.
For development review, KSH also stores the sanitized full source HTML/text and
source image URLs; image binaries are never copied into MySQL.

- source and external id;
- title and a source-supplied excerpt capped at 480 characters;
- published date, category and canonical source URL;
- ranking/filter evidence;
- sanitized source HTML/text and attachment metadata; and
- verified vocabulary returned by the Korean Basic Dictionary Open API.

The raw preview is admin-gated. Every detail page contains a prominent
“Đọc bài gốc” link and a provenance block.

After the V72 compaction migration, the discovery schema uses four tables:
`news_sources`, `news_articles`, `news_vocabularies` and
`news_ingestion_runs`. Attachment metadata is inline JSON on `news_articles`,
blacklist entries are article tombstones with `BLACKLISTED` status, and the
crawl lease uses MySQL `GET_LOCK()` rather than a lock table.

## Enabled sources

The source rows are seeded by Flyway `V68`:

| Code | Adapter | Scope |
| --- | --- | --- |
| `KBS_WORLD_CULTURE_VI` | RSS | Vietnamese culture |
| `KBS_WORLD_ENTERTAINMENT_VI` | RSS | Vietnamese entertainment |
| `KOREA_NET_FOOD_TRAVEL_VI` | HTML list metadata | Food and travel |
| `KOREA_NET_CULTURE_VI` | HTML list metadata | Culture |
| `STUDY_IN_KOREA_GKS` | Official JSON endpoint | GKS notices |

Outbound requests are HTTPS-only, response-size bounded to 2 MB, and restricted
to the hardcoded host allowlist in `NewsHttpClient`. A MySQL advisory lease
prevents two app instances from running the same ingestion concurrently. URL
SHA-256 deduplication makes retries idempotent.

## Scheduling and operations

Defaults:

```properties
app.news.ingestion.enabled=true
app.news.ingestion.initial-delay=PT2M
app.news.ingestion.fixed-delay=PT5H
```

`fixedDelay=PT5H` means the next run starts five hours after the previous run
finishes, avoiding the uneven midnight gap of a `*/5` cron expression.

Admins can inspect sources and runs or trigger a safe manual refresh at:

```text
/admin/news
```

Each run records fetched, published, rejected, duplicate and error counts.
One failing source does not prevent the remaining sources from being processed.

## Korean Basic Dictionary Open API

Request a key at:

<https://krdict.korean.go.kr/eng/openApi/openApiInfo>

Then set:

```text
KOREAN_DICTIONARY_API_KEY=<your 32-character key>
```

For each high-ranking article without vocabulary, KSH selects at most three
curated Korean lemmas and calls:

```text
GET https://krdict.korean.go.kr/api/search
    ?key=...
    &q=문화
    &method=exact
    &translated=y
    &trans_lang=7
    &num=10
```

`trans_lang=7` requests Vietnamese translation fields. Only successful,
verified API entries are published. The API is word-oriented, not a Korean
paragraph translator: sentence tokenization/morphological analysis belongs to
a later Korean-source phase. No AI fallback is used when the dictionary key is
missing.

## Ranking and editorial policy

The deterministic policy:

1. rejects matches from the Vietnamese, English and Korean political blocklists;
2. gives a strong boost to `Việt Nam`, `Vietnam`, `베트남`, Hanoi, Ho Chi Minh
   City, Da Nang and related terms;
3. combines source trust weight, recency, category and metadata completeness;
4. keeps rejected rows for audit while exposing only `PUBLISHED` rows.

The blocklist is intentionally conservative and should be reviewed from admin
run metrics before new sources are enabled.

## Later AI translation phase

The reserved switch is off:

```text
NEWS_AI_TRANSLATION_ENABLED=false
```

The next phase should add separate translated-title and translated-summary
columns plus status, provider/model, prompt version and review evidence.
Translation must never overwrite the original source fields. The source URL
remains the authority, and generated Vietnamese text must be visibly labeled.

## Verification

Run with the repository-required Java 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -Dtest='com.ksh.features.discovery.**' test
```
