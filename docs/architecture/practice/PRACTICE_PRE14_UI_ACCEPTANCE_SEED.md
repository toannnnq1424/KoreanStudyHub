# Practice Pre-14 deterministic UI acceptance seed

Status: `DEV_TEST_ONLY`

This harness reuses the historical Phase-13 result fixture as its immutable
catalog base and overlays only the current Pre-14 contracts. It is not a
production seed, is not a Flyway migration, contains no learner PII or secret,
and makes zero AI, STT, TTS, ingestion or media-provider calls.

The machine-readable authority is
`src/test/resources/practice/pre14-ui-acceptance-scenarios.json`.

## Load and cleanup

Create a fresh localhost catalog named `ksh_test_pre14_ui_<run_id>`, then run:

```text
KSH_PRE14_UI_SEED_ENABLED=true
KSH_PRE14_UI_SEED_LOAD_BASE=true
KSH_PRE14_UI_SEED_JDBC_URL=jdbc:mysql://127.0.0.1:3306/ksh_test_pre14_ui_<run_id>?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
KSH_PRE14_UI_SEED_JDBC_USER=<task-owned-user>
KSH_PRE14_UI_SEED_JDBC_PASSWORD=<task-owned-secret>
./mvnw -q -Dtest=PracticePre14UiAcceptanceSeedTest test
```

The harness refuses non-local hosts and catalog names outside
`ksh_test_pre14_ui_*`. Re-running the command is idempotent. Cleanup drops only
the explicitly named disposable catalog and its task-owned account, after
browser artifacts have been captured.

Use the existing disposable users:

- learner: `student@ksh.edu.vn` (`STUDENT`);
- author: `lecturer@ksh.edu.vn` (`LECTURER`).

Their credentials remain in the local fixture boundary and are deliberately
not recorded here.

The in-progress objective player fixture is
`/practice/attempts/14800`. It uses the same immutable Reading snapshot and
exposes all 14 questions, including matching A–H, multiple-answer and shared
material pin persistence, without changing either graded Result route.

## Stable routes

| Skill | Result Overview | Result Detail | Lecturer editor |
|---|---|---|---|
| Reading | `/practice/attempts/14100/result` | `/practice/attempts/14100/result/detail` with anchors `#objective-question-14101` through `14114` | `/practice/manage/drafts/14501` |
| Listening | `/practice/attempts/14200/result` | `/practice/attempts/14200/result/detail` with anchors `#objective-question-14201` through `14214` | `/practice/manage/drafts/14502` |
| Writing | `/practice/attempts/14301/result` through `14304` | the same attempt plus `questionId=14351`, `14352`, `4` or `14354` | not part of this acceptance pack |
| Speaking | `/practice/attempts/14401/result` | `/practice/attempts/14401/result/detail?questionId=5` and `questionId=14405` | not part of this acceptance pack |

All 28 browser scenarios use a distinct immutable question version with
exactly one active explanation strategy. They exercise all eleven selectable
strategies at least once, both skills, group-source and
typed standalone-source modes, MCQ/TFNG/fill/multiple-answer/matching, and the
four deterministic answer states. Existing IDs `14101`–`14112` and
`14201`–`14212` remain stable; the two new typed cases per skill are appended
at `14113`–`14114` and `14213`–`14214`.

The compact machine-readable matrix expands to exactly 128 verified
compatibility cells:

```text
skill (READING/LISTENING)
× sourceMode (GROUP_SOURCE/STANDALONE_NO_PASSAGE)
× questionType
× selectable compatible strategy
```

The per-question-type cardinalities are 7 MCQ strategies, 7 TFNG strategies,
6 fill strategies, 6 multiple-answer strategies and 6 matching strategies;
`(7 + 7 + 6 + 6 + 6) × 2 × 2 = 128`. The manifest test resolves every cell
through the production registry, so a documentation-only combination cannot
silently become selectable.

## R/L strategy authority

Registry: `rl-explanation-strategy-registry-v2`; strategy schema: `v1`.

| Selectable strategy | Browser fixture |
|---|---|
| `EXACT_EVIDENCE_ONLY` | `14101`, `14112`, `14201`, `14212` |
| `FULL_SOURCE_INLINE_HIGHLIGHT` | `14102`, `14111`, `14202`, `14211` |
| `QUESTION_EVIDENCE_TRANSLATION_TABLE` | `14107`, `14207` |
| `MCQ_OPTION_ELIMINATION` | `14103`, `14203` |
| `EVIDENCE_AND_ELIMINATION` | `14108`, `14208` |
| `TFNG_CONTRADICTION_TABLE` | `14104`, `14204` |
| `NOT_GIVEN_BOUNDARY` | `14110`, `14210` |
| `FILL_SLOT_GRAMMAR_ANALYSIS` | `14105`, `14205` |
| `KEYWORD_PARAPHRASE_BRIDGE` | `14106`, `14206` |
| `BILINGUAL_STEP_BY_STEP` | `14109`, `14209` |
| `MATCHING_MATRIX` | `14113`, `14213` |

The appended typed fixtures are deliberately small and useful to KSH:

- `14113` and `14213` match four targets against eight stable candidates A–H;
  official mappings live in `answer-spec-v1`, and the explanation artifact
  covers every target exactly once.
- `14114` and `14214` require two correct options, score by exact set under
  `ALL_OR_NOTHING`, and use the same typed option-rationale artifact as the
  PREP-style straight explanation grid.
- shared material pinning is owned by learner device + attempt + immutable
  group and persists across refresh; the local helper is static KSH guidance
  with no provider call. Both controls are keyboard operable and expose their
  state through accessible labels.

The editor fixtures at draft `14501` and `14502` each contain twelve questions
covering all eleven selectable strategies, including one typed
multiple-answer and one typed matching A–H question. They remain preview
fixtures with zero provider calls.

The following catalog entries stay visible as explicit non-selectable debt;
the seed does not manufacture missing domain authority:

| Blocked strategy | Gate |
|---|---|
| `PARAGRAPH_PURPOSE_MAP` | Immutable paragraph/speaker-purpose IDs |
| `SEQUENCE_TIMELINE` | Canonical ordering/sequence answer contract |
| `CAUSE_EFFECT_CHAIN` | Typed causal nodes and links |
| `COMPARE_CONTRAST_TABLE` | Typed comparison entities and axes |
| `REFERENCE_RESOLUTION` | Persisted antecedent/reference links |
| `DISTRACTOR_TRAP_ANALYSIS` | Strict `trapCode` allowlist |
| `SPEAKER_INTENT_AND_ATTITUDE` | Trusted audio/acoustic evidence |
| `TIMESTAMP_TURN_MAP` | Verified transcript timestamp alignment |
| `HYBRID_TEACHER_GUIDED` | Persisted ordered block composition |

Writing contains Q51, Q52, Q53 and Q54 in four cases: mixed,
no-diagnostic, partial and full. Optional diagnostic chips with no verified
finding stay visible at the end of their catalogue and open a bounded empty
state; mandatory rubric rows remain authoritative. Q53 uses
the locked Korean sample and exact UTF-16 ledger. The seed is accepted only
after the production normalizer/verifier accepts the immutable artifact.

Premium Writing uses six stable attempts: Q51 all-strength at `14601`, Q52
all-improvement at `14602`, Q53 all-strength at `14603`, Q54 all-improvement
at `14604`, plus the opposite-polarity unique-feature companions at `14605`
and `14606`. Attempt `14604` is also the canonical pair-wise navigation
fixture: its four stable task links expose Q51 all-strength, Q52
all-improvement, Q53 all-strength and Q54 all-improvement together, without
assigning opposing polarities to one task. Normal learner UI keeps
zero-finding chips at the end and identifies their no-diagnostic state without
fabricating a card or span. The Detail
surface exposes the verified chips again as upgrade criteria, keeps inline
text neutral until a chip is selected, and maps selected strength,
improvement and upgrade evidence to straight green, red and blue underlines
without changing line geometry or preserving template indentation between
answer segments. At large viewports the source and feedback
panes have a bounded 35--65 resizable split; the separator is keyboard
operable and collapses to the stacked responsive layout at `1080px` and
below.

Speaking contains a mixed transcript-ledger case, a no-diagnostic case and two
premium paired attempts. Each premium attempt exposes one all-strength task
and one all-improvement task, so the learner can inspect both polarities from
the same compact task switcher without marking the same sentence span as both
good and bad. The improvement task also renders typed KSH upgrade-criterion
cards derived only from its verified transcript findings and corrections.
Acoustic rows remain `NOT_SCORABLE`; the current contract has no verified
acoustic fixture and this seed does not fabricate one.

For Result Overview acceptance, the same Speaking fixture declares the
capabilities independently. `CRITERION_RADAR`, `PART_PERFORMANCE` and
`NAMED_CRITERION_SUBMETRICS` are `AVAILABLE`: the radar uses four
transcript-grounded earned/max axes, question performance uses canonical
published question/group authority, and named submetrics use KSH descriptor
labels plus the backend-derived parent criterion level. `HOLISTIC_SCORE` and
the two acoustic criteria remain `NOT_SCORABLE`; N/A is never seeded as zero.

## UI-dev iteration

Load the seed once, then keep one `spring-boot:run` UI-dev process attached to
the disposable catalog. Template, CSS and JavaScript changes are checked by
browser refresh against the same PID. Java, contract or seed-data changes
require the deliberate compile/seed reload path; they are outside the F5-only
promise.

The presence of a route is not PREP acceptance. A PREP row can be promoted only
when its backend semantic contract, rendered UI and browser artifact all pass.
Phase 15B still owns the premium KO/VI manual browser/device re-verification;
Phase 15D owns live-model calibration.
