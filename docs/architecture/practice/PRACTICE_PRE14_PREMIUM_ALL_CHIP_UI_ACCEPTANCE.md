# Practice Pre-14 premium all-chip UI acceptance

Status: **inventory and coverage gap locked before seed mutation**

This document is the KSH UI-acceptance authority for the premium diagnostic
catalogue. It inventories the current typed contracts and the browser-observed
gaps. It does not treat the existing six Writing lifecycle cases as all-chip
coverage, does not introduce IELTS terminology, and does not authorize a
provider call.

## 1. Authority and non-authority

The active names and identities come from:

- `WritingRubricCriterion`,
  `WritingDiagnosticDescriptorRegistry`, `WritingDiagnosticContract`,
  `WritingScoringPolicy`, `WritingScoreAnchorPolicy` and
  `WritingTaskRequirementPolicy`;
- `SpeakingRubricCriterion` and `ResultDetailDescriptorRegistry`;
- `WritingResultPresenter`, `SpeakingResultPresenter`,
  `PracticeDtos`, `result/writing.html`, `result/speaking.html`,
  `result-detail-writing.html` and `result-detail-speaking.html`.

Provider display strings, generic IELTS chip names and browser-inferred
criteria are not authority. Chip counts are navigation metadata and are never
summed into a score.

## 2. Surface and tab contract

| Skill/task | Surface | Current UI | Premium acceptance |
|---|---|---|---|
| Q51–Q54 | Result Overview | Overall attempt score; per-task score, official rubric rows and lifecycle state. No diagnostic chips. | Keep Overview chip-free. Prove score/level and evaluated/empty/pending/unavailable states. |
| Q51–Q54 | Detail · Tổng quan | Task score, 6 cloze rows or 3 long-form rows, and task coverage. | Keep score rows and task coverage authoritative; show `LIMITED/MODEST/GOOD/EXCELLENT` only when scored. |
| Q51–Q54 | Detail · Điểm mạnh | Evidence-backed findings and non-additive filter chips. | Render every chip assigned to the premium strength case; zero-count chips stay hidden. |
| Q51–Q54 | Detail · Cần cải thiện | Evidence-backed findings, correction and non-additive filter chips. | Render every chip assigned to the premium improvement case; prove `MISSING`, `REPLACE` and `REDUNDANT`. |
| Q51–Q54 | Detail · Bài nâng cấp | Learner-derived evaluator output plus exact-span rewrites. Evaluator sample is currently nested here. | Keep learner-derived upgrade and exact original→replacement mapping. Move samples out of this tab. |
| Q51–Q54 | Detail · Mẫu | **Missing.** There is no fifth tab. Current provenance only supports evaluator-generated, explicitly non-teacher samples. | Add a fifth `Mẫu` tab. A teacher-authored sample must carry persisted KSH teacher authority; evaluator sample remains separately labelled and cannot impersonate it. |
| Speaking | Result Overview | Profile/evidence state, no aggregate score by default, generic strength/improvement lists, action plan and six criterion states. No filter chips. | Keep Overview chip-free. Prove four transcript rows and two acoustic rows without creating an aggregate score. |
| Speaking | Detail · Tổng quan | Six canonical criterion rows and evidence provenance. | Four transcript-grounded rows may be scored; Fluency and Pronunciation/Delivery are `NOT_SCORABLE` unless governed direct-audio evidence exists. |
| Speaking | Detail · Điểm mạnh | Transcript-grounded findings and chips only. | Cover all 16 transcript subcriteria as strength descriptors across deterministic fixtures. |
| Speaking | Detail · Cần cải thiện | Transcript-grounded findings, correction and chips only. | Cover all 16 transcript subcriteria as improvement descriptors across deterministic fixtures. |
| Speaking | Detail · Bài nâng cấp | Transcript-derived upgrade, exact-span rewrites, evaluator sample nested here. | Keep transcript provenance and exact rewrite mapping. |
| Speaking | Detail · Mẫu | **Missing.** There is no fifth tab or teacher-authored provenance. | Add the fifth tab only with explicit teacher authority; never derive pronunciation, fluency or a teacher sample from transcript text. |

## 3. Score, level and lifecycle states

Scored levels are backend-derived from accepted anchors:

| Code | Vietnamese | Korean | Visibility |
|---|---|---|---|
| `LIMITED` | Hạn chế | 제한적 | Only with a valid numeric score anchor. |
| `MODEST` | Đang phát triển | 보통 | Only with a valid numeric score anchor. |
| `GOOD` | Tốt | 좋음 | Only with a valid numeric score anchor. |
| `EXCELLENT` | Xuất sắc | 우수 | Only with a valid numeric score anchor. |

Non-score states stay distinct:

| State | Rule |
|---|---|
| empty / no diagnostic | Score rows may remain authoritative; no chip is rendered without a finding. |
| `PENDING` | No score, level, finding, upgrade or sample is inferred. |
| `UNAVAILABLE` / failed | No replacement score or level is fabricated. |
| `NOT_SCORABLE` | A criterion exists but current evidence cannot score it; this is not zero. |
| `LEGACY_UNVERIFIED` | Historical data remains visible only through its explicit compatibility label. |
| unsupported | The unavailable capability is named; no chip is created. |

Writing score rows are fixed at six cloze criteria for Q51/Q52, three
criteria with maxima `12/9/9` for Q53, and three criteria with maxima
`20/15/15` for Q54. Speaking Detail always preserves all six canonical rows:
four transcript-grounded rows plus two acoustic rows.

## 4. Finding, chip and span-card payload

### 4.1 Writing

Every rendered Writing finding requires:

```text
questionId
findingId
evidenceId when TEXT_SPAN
startOffset/endOffset when TEXT_SPAN
occurrenceIndex/occurrenceCount when TEXT_SPAN
operation = KEEP|MISSING|REPLACE|REDUNDANT
errorCategory
requirementIds
categoryCode/category labels/order
featureCode/subtype/feature labels/order
polarity
parentCriterionId + scoreEffect, or DIAGNOSTIC_ONLY with no parent
applicability
target = WHOLE_ANSWER|TEXT_SPAN|BLANK
blankId/blankIndex when target=BLANK
evidenceAvailability/evidenceScope/evidence
explanationVi
correctionKo when the operation requires a replacement
impact
frequency >= 1
confidence in [0,1]
observability
```

`TEXT_SPAN` must preserve the exact NFC UTF-16 slice and the declared repeated
occurrence. `WHOLE_ANSWER` must not manufacture a highlight. Q51/Q52 must use
the immutable `q51-b1/q51-b2` or `q52-b1/q52-b2` identity; a finding without
that identity fails closed.

A chip is derived only from rendered findings and requires descriptor id,
KSH VI/KO labels, polarity, parent/score effect, applicability, stable order,
count and evidence availability. `countedSeparately=false` is invariant.
Zero-count definitions are accepted by catalogue tests but absent from the
normal learner UI.

### 4.2 Speaking

The stored current-evidence item requires:

```text
findingId/evidenceId
parent criterion + owned subCriterionId
evidenceSource=TRANSCRIPT
evidenceScope/evidence
startOffset/endOffset and occurrence identity for TEXT_SPAN
normalization/sourceHash
operation/category
explanationVi
correction for an improvement span
```

Transcript annotations additionally carry severity and confidence. The
current Speaking filter-chip projection deliberately has no independent
frequency, impact or score field: count is derived from matching findings,
and the chip remains non-additive. Acoustic subcriteria are not in the
transcript registry.

For both skills, every annotated span carries the same stable finding and
descriptor identity as its card. Filtering a chip must reveal the exact cards
and inline spans represented by its count.

## 5. Writing chip taxonomy

For Q51/Q52, each feature can resolve separately to blank 1 and blank 2.
Therefore seven feature labels mean fourteen stable blank-target chip ids;
nine improvement labels mean eighteen ids.

### 5.1 Q51 premium strengths — 14 chip ids

All seven labels are shared with Q52; there is no strength-only Q51 label.

| Feature code | KSH Vietnamese label | Scope | Parent per blank |
|---|---|---|---|
| `W_ACCURATE_SPELLING_SPACING` | Chính tả và cách chữ chính xác | text span | expression |
| `W_FORMAL_REGISTER_CONSISTENCY` | Đồng nhất văn phong viết | text span or whole answer | expression |
| `W_FORMAL_VOCABULARY_USAGE` | Từ vựng văn viết phù hợp | text span | expression |
| `W_NATURAL_KOREAN_EXPRESSIONS` | Diễn đạt tiếng Hàn tự nhiên | text span | expression |
| `W_CLOZE_CONTEXT_FIT` | Phù hợp ngữ cảnh chỗ trống | text span | context |
| `W_CONNECTIVE_ENDING_ACCURACY` | Vĩ tố liên kết chính xác | text span | grammar |
| `W_SENTENCE_COMPLETION_NATURALNESS` | Câu hoàn thành tự nhiên | text span | expression |

Every strength uses `KEEP`. Descriptor ids end in
`_WRITING_Q51_BLANK_1` or `_WRITING_Q51_BLANK_2`.

### 5.2 Q52 premium improvements — 18 chip ids

All nine labels are shared with Q51; there is no improvement-only Q52 label.

| Feature code | KSH Vietnamese label | Scope | Parent per blank |
|---|---|---|---|
| `W_CLOZE_GRAMMAR_COMPATIBILITY` | Ngữ pháp tương thích ngữ cảnh | text span | grammar |
| `W_CLOZE_REGISTER_MATCH` | Văn phong chỗ trống không phù hợp | text span | expression |
| `W_VOCABULARY_ERRORS` | Lỗi từ vựng | text span | expression |
| `W_GRAMMAR_ERRORS` | Lỗi ngữ pháp | text span | grammar |
| `W_PARTICLE_ERRORS` | Lỗi tiểu từ | text span | grammar |
| `W_AWKWARD_UNNATURAL_EXPRESSIONS` | Diễn đạt gượng gạo | text span | expression |
| `W_SENTENCE_STRUCTURE_ISSUES` | Vấn đề cấu trúc câu | text span | grammar |
| `W_REGISTER_CONSISTENCY_ISSUES` | Bất nhất quán văn phong | text span or whole answer | expression |
| `W_SPELLING_SPACING_ERRORS` | Lỗi chính tả và cách chữ | text span | expression |

The premium case distributes `MISSING`, `REPLACE` and `REDUNDANT` without
requiring a single span to represent contradictory operations. Descriptor ids
end in `_WRITING_Q52_BLANK_1` or `_WRITING_Q52_BLANK_2`.

### 5.3 Q53 premium strengths — 12 chips

Eleven are shared with Q54; `W_ACCURATE_DATA_DESCRIPTION` is Q53-specific.

| Feature code | KSH Vietnamese label | Shared/unique | Scope | Score parent |
|---|---|---|---|---|
| `W_ADVANCED_GRAMMAR_STRUCTURES` | Ngữ pháp nâng cao chính xác | shared | text span | language |
| `W_ACCURATE_SPELLING_SPACING` | Chính tả và cách chữ chính xác | shared | text span | language |
| `W_FORMAL_REGISTER_CONSISTENCY` | Đồng nhất văn phong viết | shared | text span or whole answer | language |
| `W_FORMAL_VOCABULARY_USAGE` | Từ vựng văn viết phù hợp | shared | text span | language |
| `W_TOPIC_SPECIFIC_EXPRESSIONS` | Từ vựng và cụm từ theo chủ đề | shared | text span | language |
| `W_NATURAL_KOREAN_EXPRESSIONS` | Diễn đạt tiếng Hàn tự nhiên | shared | text span | language |
| `W_LENGTH_REQUIREMENT_MET` | Dung lượng bài phù hợp | shared | whole answer | diagnostic only |
| `W_SENTENCE_PATTERN_VARIETY` | Sự đa dạng cấu trúc câu | shared | text span or whole answer | language |
| `W_TASK_REQUIREMENT_COVERAGE` | Bao phủ yêu cầu đề bài | shared | whole answer | content |
| `W_LOGICAL_ORGANIZATION` | Tổ chức bài logic | shared | whole answer | organization |
| `W_EFFECTIVE_TRANSITIONS` | Chuyển ý hiệu quả | shared | text span or whole answer | organization |
| `W_ACCURATE_DATA_DESCRIPTION` | Mô tả dữ liệu rõ ràng | Q53 unique | text span or whole answer | content |

Q53 task coverage remains a separate, non-chip ledger with six rows:
four transport modes, data 2024, data 2026, main changes, plausible cause and
length 200–300.

### 5.4 Q54 premium improvements — 14 chips

Eleven are shared with Q53. The three Q54-specific chips are insufficient
development, unsupported claim and weak paragraph organization.

| Feature code | KSH Vietnamese label | Shared/unique | Scope | Score parent |
|---|---|---|---|---|
| `W_OFF_TOPIC_OR_WEAK_RELEVANCE` | Lạc đề hoặc liên quan yếu | shared | whole answer | content |
| `W_LOGICAL_FLOW_ISSUES` | Mạch logic chưa rõ | shared | whole answer | organization |
| `W_TRANSITION_DEVICE_ISSUES` | Chuyển ý chưa phù hợp | shared | text span or whole answer | organization |
| `W_VOCABULARY_ERRORS` | Lỗi từ vựng | shared | text span | language |
| `W_GRAMMAR_ERRORS` | Lỗi ngữ pháp | shared | text span | language |
| `W_PARTICLE_ERRORS` | Lỗi tiểu từ | shared | text span | language |
| `W_REPETITIVE_WORDS_EXPRESSIONS` | Lặp từ và cụm từ | shared | text span or whole answer | language |
| `W_AWKWARD_UNNATURAL_EXPRESSIONS` | Diễn đạt gượng gạo | shared | text span | language |
| `W_SENTENCE_STRUCTURE_ISSUES` | Vấn đề cấu trúc câu | shared | text span | language |
| `W_REGISTER_CONSISTENCY_ISSUES` | Bất nhất quán văn phong | shared | text span or whole answer | language |
| `W_SPELLING_SPACING_ERRORS` | Lỗi chính tả và cách chữ | shared | text span | language |
| `W_INSUFFICIENT_IDEA_DEVELOPMENT` | Ý chưa được phát triển | Q54 unique | whole answer | content |
| `W_UNSUPPORTED_CLAIM` | Nhận định thiếu hỗ trợ | Q54 unique | whole answer | content |
| `W_WEAK_PARAGRAPH_ORGANIZATION` | Bố cục đoạn văn yếu | Q54 unique | whole answer | organization |

Q54 task coverage remains a separate five-row ledger: position, prompt
coverage, support, logical development and length 600–700.

### 5.5 Required opposite-polarity companions

The pair-wise primary cases do not cover these question-specific descriptors:

| Companion | Required chips |
|---|---|
| Q54 strength companion | `W_CLEAR_THESIS_OR_MAIN_IDEA` (Luận điểm hoặc ý chính rõ ràng); `W_RELEVANT_EXAMPLES_OR_REASONS` (Lý do hoặc ví dụ phù hợp) |
| Q53 improvement companion | `W_TASK_REQUIREMENT_MISSING` (Thiếu yêu cầu đề bài); `W_Q53_DATA_FLOW_ISSUES` (Trình tự mô tả dữ liệu chưa rõ) |

`W_FABRICATED_OR_INACCURATE_DATA` remains inactive because structured task
data authority is not available. Legacy aliases and Won-go-ji are not premium
chips.

## 6. Speaking transcript-chip taxonomy

Each subcriterion owns one strength descriptor and one improvement descriptor:
`D_<subcriterion>_STRENGTH` and
`D_<subcriterion>_NEEDS_IMPROVEMENT`. Total: **32 chips**.

| Family | Parent criterion | Subcriterion | KSH Vietnamese label |
|---|---|---|---|
| Task response & relevance | `S_CONTENT_TASK_FULFILLMENT` | `S_CONTENT_RELEVANCE` | Bám sát đề |
| Task response & relevance | `S_CONTENT_TASK_FULFILLMENT` | `S_CONTENT_PROMPT_COVERAGE` | Bao phủ yêu cầu |
| Task response & relevance | `S_CONTENT_TASK_FULFILLMENT` | `S_CONTENT_SPECIFICITY_EXAMPLES` | Mức độ cụ thể và ví dụ |
| Morphosyntax | `S_GRAMMAR_SENTENCE_CONTROL` | `S_GRAMMAR_PARTICLES` | Tiểu từ |
| Morphosyntax | `S_GRAMMAR_SENTENCE_CONTROL` | `S_GRAMMAR_TENSE_ASPECT` | Thì và thể |
| Morphosyntax | `S_GRAMMAR_SENTENCE_CONTROL` | `S_GRAMMAR_ENDINGS` | Đuôi câu và vĩ tố |
| Morphosyntax | `S_GRAMMAR_SENTENCE_CONTROL` | `S_GRAMMAR_SENTENCE_STRUCTURE` | Cấu trúc câu |
| Register & pragmatics | `S_GRAMMAR_SENTENCE_CONTROL` | `S_GRAMMAR_HONORIFIC_REGISTER` | Kính ngữ và văn phong |
| Morphosyntax | `S_GRAMMAR_SENTENCE_CONTROL` | `S_GRAMMAR_CONNECTORS` | Liên kết |
| Lexicon & collocation | `S_VOCABULARY_EXPRESSIONS` | `S_VOCAB_TOPIC_WORDS` | Từ vựng theo chủ đề |
| Lexicon & collocation | `S_VOCABULARY_EXPRESSIONS` | `S_VOCAB_NATURAL_EXPRESSIONS` | Diễn đạt tự nhiên |
| Lexicon & collocation | `S_VOCABULARY_EXPRESSIONS` | `S_VOCAB_REPETITION_CONTROL` | Kiểm soát lặp từ |
| Lexicon & collocation | `S_VOCABULARY_EXPRESSIONS` | `S_VOCAB_WORD_CHOICE` | Lựa chọn từ |
| Discourse & organization | `S_COHERENCE_ORGANIZATION` | `S_COHERENCE_ORGANIZATION` | Tổ chức ý |
| Discourse & organization | `S_COHERENCE_ORGANIZATION` | `S_COHERENCE_LOGICAL_FLOW` | Mạch logic |
| Discourse & organization | `S_COHERENCE_ORGANIZATION` | `S_COHERENCE_DISCOURSE_MARKERS` | Dấu hiệu diễn ngôn |

Fluency and Pronunciation/Delivery have no transcript subcriteria. Their two
score rows stay visible as `NOT_SCORABLE` in transcript-only fixtures. A
future direct-audio catalogue requires verified media identity, timestamps
and governed acoustic evidence; this acceptance pack must not infer it.

## 7. Browser-observed current coverage gap

Raw inventory:
`target/pre14-ui-acceptance-current/premium-all-chip-current-browser-inventory.json`.

| Premium target | Expected | Rendered by current seed | Missing | Verdict |
|---|---:|---:|---:|---|
| Q51 strengths | 14 blank-target chips | 0 | 14 | OPEN |
| Q52 improvements | 18 blank-target chips | 0 | 18 | OPEN |
| Q53 strengths | 12 | 3 distinct chips | 9 | OPEN |
| Q54 improvements | 14 | 1 distinct chip | 13 | OPEN |
| Q54 unique-strength companion | 2 | 0 | 2 | OPEN |
| Q53 unique-improvement companion | 2 | 0 | 2 | OPEN |
| Speaking transcript chips | 32 | 2 | 30 | OPEN |
| Speaking acoustic state rows | 2 `NOT_SCORABLE` | 2 | 0 | CLOSED for transcript-only truthfulness |
| Detail tabs | 5 | 4 | `Mẫu` | OPEN |
| Teacher-authored sample provenance | 1 explicit authority path per skill | 0 | all | OPEN |

Additional browser facts:

- all current Q51/Q52 pages fail closed with no authoritative blank identity;
- current Writing lifecycle cases do cover payload operations
  `KEEP/MISSING/REPLACE/REDUNDANT`, but `MISSING` and `REDUNDANT` do not
  currently survive to rendered premium cards;
- the Q53 mixed case has six findings collapsed into four chips and one
  rewrite; the Q54 mixed payload contains all operation families but renders
  no diagnostic card;
- Speaking currently renders only Topic vocabulary strength and Connector
  improvement; both acoustic rows remain honestly `NOT_SCORABLE`;
- zero/no-diagnostic, pending and unavailable states exist, but they are
  lifecycle evidence, not all-chip evidence.

## 8. Seed mutation gate

Seed mutation may begin only after this inventory is accepted as the baseline.
The deterministic premium pack must use separate pair-wise cases rather than
forcing one answer to be simultaneously excellent and defective:

```text
Q51_ALL_STRENGTHS: 14 blank-target chip ids
Q52_ALL_IMPROVEMENTS: 18 blank-target chip ids
Q53_ALL_STRENGTHS: 12 chip ids
Q54_ALL_IMPROVEMENTS: 14 chip ids
Q54_UNIQUE_STRENGTH_COMPANION: 2 chip ids
Q53_UNIQUE_IMPROVEMENT_COMPANION: 2 chip ids
SPEAKING_ALL_STRENGTHS: 16 transcript chip ids
SPEAKING_ALL_IMPROVEMENTS: 16 transcript chip ids
SPEAKING_NO_DIAGNOSTIC
SPEAKING_TRANSCRIPT_ONLY_ACOUSTIC_UNSUPPORTED
WRITING/SPEAKING_PENDING
WRITING/SPEAKING_UNAVAILABLE
```

Across these fixtures, UI proof must include minimal and repeated spans,
frequency/count, long/short/wrapped content, all four Writing operations,
correction, learner-derived upgrade, exact rewrite mapping, task coverage,
rubric anchors, Overview score/level, zero-count hidden behavior and the
separate sample provenance. The fixtures are typed deterministic JSON/DTO
data, idempotent, local disposable and provider-free.
