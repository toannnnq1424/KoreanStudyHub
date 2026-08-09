#!/usr/bin/env python3
"""Generate the forward-only TOPIK 35 premium learner seed migration.

The checked-in JSON package remains the content authority. This generator is
deterministic so the large 104-question SQL migration is reviewable without
hand-copying Korean text or answer identities.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OPS = ROOT / "docs" / "operations"
OUTPUT = (ROOT / "src" / "main" / "resources" / "db" / "migration"
          / "V119__practice_topik35_premium_canonical_catalog.sql")
BUNDLE = "topik35-premium-canonical-v1"
RUNTIME_ASSETS = json.loads(
    (OPS / "practice-topik35-runtime-assets.json").read_text(encoding="utf-8"))
RUNTIME_BY_REVIEWED_SHA = {
    item["reviewedSha256"]: item["reference"]
    for item in RUNTIME_ASSETS["assets"]
}
RUNTIME_BY_ASSET_ID = {
    item["assetId"]: item["reference"]
    for item in RUNTIME_ASSETS["assets"]
}
AUDIO_REFERENCE = RUNTIME_BY_ASSET_ID["topik35-listening-program-mp3"]


def load(name: str) -> dict:
    return json.loads((OPS / name).read_text(encoding="utf-8"))


def sql_text(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_json(value: object | None) -> str:
    if value is None:
        return "NULL"
    return "CAST(" + sql_text(json.dumps(
        value, ensure_ascii=False, separators=(",", ":"))) + " AS JSON)"


def static_image(reference: str | None) -> str | None:
    if not reference:
        return None
    reviewed_sha = Path(reference).stem
    try:
        return RUNTIME_BY_REVIEWED_SHA[reviewed_sha]
    except KeyError as exc:
        raise ValueError(
            f"No restored runtime asset for reviewed image {reference}") from exc


def tuple_sql(values: list[str]) -> str:
    return "    (" + ", ".join(values) + ")"


def canonical_content(raw: dict) -> dict:
    content = copy.deepcopy(raw)
    if content.get("imageReference"):
        content["imageReference"] = static_image(content["imageReference"])
    for option in content.get("options", []):
        if option.get("imageReference"):
            option["imageReference"] = static_image(option["imageReference"])
    return content


def objective_row(set_var: str, group_var: str, question: dict,
                  display_order: int) -> str:
    content = canonical_content(question["questionContent"])
    options = [item.get("text", "") for item in content.get("options", [])]
    correct = question["answerSpec"].get("correctOptionIds", [])
    answer_key = None
    if len(correct) == 1 and correct[0].startswith("opt_"):
        answer_key = correct[0].split("_", 1)[1]
    provenance = question.get("provenance", {})
    explanation = (
        "Đáp án nguồn đã được đối chiếu trực quan; provenance: "
        + json.dumps(provenance, ensure_ascii=False, separators=(",", ":")))
    return tuple_sql([
        set_var, group_var, str(question["questionNumber"]),
        sql_text(question["questionType"]), sql_text(question["prompt"]),
        sql_json(options), sql_json(content), sql_text(answer_key),
        sql_json(question["answerSpec"]), sql_text(explanation),
        sql_text("rl-explanation-strategy-registry-v2"),
        sql_text("MCQ_OPTION_ELIMINATION"), sql_text("v1"),
        f"{question['points']:.2f}", str(display_order), "NULL"
    ])


def writing_content(question: dict) -> tuple[dict, dict]:
    task = question["taskType"]
    if task in {"Q51", "Q52"}:
        blanks = [{
            "blankId": item["blankId"],
            "ordinal": item["ordinal"],
            "context": item.get("sourceMarker", "")
        } for item in question["blankDefinitions"]]
        content = {
            "schemaVersion": "question-content-v3",
            "options": [], "blanks": [],
            "writingResponse": {
                "responseSchemaVersion": "writing-blanks.v1",
                "responseMode": "STRUCTURED_BLANKS",
                "taskType": task, "blanks": blanks
            },
            "languageTag": "ko"
        }
        models = {item["blankId"]: item["text"]
                  for item in question["answerExpectation"]["modelAnswers"]}
        authority_blanks = [{
            "blankId": item["blankId"], "ordinal": item["ordinal"],
            "acceptedAnswers": [{
                "text": models[item["blankId"]],
                "equivalence": "EXACT", "evidenceIds": []
            }]
        } for item in question["blankDefinitions"]]
        answer = {
            "schemaVersion": "answer-spec-v1", "questionType": "ESSAY",
            "correctOptionIds": [], "blanks": [],
            "scoringPolicyCode": "PROFILE_BASED",
            "writingBlankAuthority": {
                "contractVersion": "writing-blank-authority.v1",
                "taskType": task, "normalization": "NFC",
                "whitespacePolicy": "TRIM_COLLAPSE",
                "blanks": authority_blanks
            }
        }
        return content, answer
    content = {
        "schemaVersion": "question-content-v3", "options": [], "blanks": [],
        "imageReference": (
            RUNTIME_BY_ASSET_ID["topik35-writing-q53-chart"]
            if task == "Q53" else None),
        "languageTag": "ko"
    }
    answer = {
        "schemaVersion": "answer-spec-v1", "questionType": "ESSAY",
        "correctOptionIds": [], "blanks": [],
        "scoringPolicyCode": "PROFILE_BASED"
    }
    return content, answer


def main() -> None:
    reading = load("practice-topik35-reading-question-payload.json")
    listening_questions = load(
        "practice-topik35-listening-question-payload.json")
    listening_transcripts = load(
        "practice-topik35-listening-transcript-payload.json")
    writing = load("practice-topik35-writing-import-audit.json")

    lines: list[str] = [
        "-- Generated by scripts/practice/generate_topik35_premium_migration.py.",
        "-- Full canonical TOPIK 35 experimental premium catalog: 50 Reading,",
        "-- 50 continuous-program Listening and Writing Q51-Q54.",
        "-- No table is created; binary assets are checked-in static resources.",
        "SET @topik35_bundle = 'topik35-premium-canonical-v1';",
        "SET @topik35_author = COALESCE(",
        "    (SELECT id FROM users WHERE email='admin@ksh.edu.vn' ORDER BY id LIMIT 1),",
        "    (SELECT id FROM users ORDER BY id LIMIT 1));",
        "",
        "INSERT INTO practice_sets (title,description,skill,scope,metadata_json,status,created_by,creation_method) VALUES",
        ",\n".join([
            tuple_sql([sql_text("Premium TOPIK 35 · Đọc 50 câu"),
                sql_text("Bộ Đọc 50 câu đầy đủ; experimental practice, không phải kỳ thi chính thức."),
                sql_text("READING"), sql_text("GLOBAL"),
                "JSON_OBJECT('seedBundle',@topik35_bundle,'seedKey','topik35-reading','premium',TRUE,'releaseScope','EXPERIMENTAL_DEMO','questionCount',50)",
                sql_text("PUBLISHED"), "@topik35_author", sql_text("CANONICAL_SEED")]),
            tuple_sql([sql_text("Premium TOPIK 35 · Nghe 50 câu liên tục"),
                sql_text("Bộ Nghe 50 câu với một chương trình audio liên tục; chỉ phát một lần trong test."),
                sql_text("LISTENING"), sql_text("GLOBAL"),
                "JSON_OBJECT('seedBundle',@topik35_bundle,'seedKey','topik35-listening','premium',TRUE,'releaseScope','EXPERIMENTAL_DEMO','questionCount',50,'continuousPlayback',TRUE,'startOnce',TRUE)",
                sql_text("PUBLISHED"), "@topik35_author", sql_text("CANONICAL_SEED")]),
            tuple_sql([sql_text("Premium TOPIK 35 · Viết Q51–Q54"),
                sql_text("Bộ Viết đủ Q51, Q52, Q53 và Q54; feedback AI chỉ mang tính thử nghiệm."),
                sql_text("WRITING"), sql_text("GLOBAL"),
                "JSON_OBJECT('seedBundle',@topik35_bundle,'seedKey','topik35-writing','premium',TRUE,'releaseScope','EXPERIMENTAL_DEMO','questionCount',4)",
                sql_text("PUBLISHED"), "@topik35_author", sql_text("CANONICAL_SEED")])
        ]) + ";",
        "SET @rset=(SELECT id FROM practice_sets WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.seedKey'))='topik35-reading' LIMIT 1);",
        "SET @lset=(SELECT id FROM practice_sets WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.seedKey'))='topik35-listening' LIMIT 1);",
        "SET @wset=(SELECT id FROM practice_sets WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.seedKey'))='topik35-writing' LIMIT 1);",
        "INSERT INTO practice_tests (set_id,title,description,display_order,estimated_minutes) VALUES",
        "    (@rset,'TOPIK 35 Đọc','50 câu Đọc canonical',0,70),",
        "    (@lset,'TOPIK 35 Nghe','50 câu Nghe, một audio liên tục',0,60),",
        "    (@wset,'TOPIK 35 Viết','Q51–Q54 canonical',0,50);",
        "SET @rtest=(SELECT id FROM practice_tests WHERE set_id=@rset LIMIT 1);",
        "SET @ltest=(SELECT id FROM practice_tests WHERE set_id=@lset LIMIT 1);",
        "SET @wtest=(SELECT id FROM practice_tests WHERE set_id=@wset LIMIT 1);",
        "INSERT INTO practice_sections (set_id,test_id,title,skill,section_type,instructions,delivery_json,duration_minutes,total_points,display_order) VALUES",
        "    (@rset,@rtest,'Phần Đọc','READING','MAIN','Hoàn thành 50 câu Đọc.',NULL,70,100,0),",
        "    (@lset,@ltest,'Phần Nghe','LISTENING','MAIN','Audio chạy liên tục một lần; không tua hoặc phát lại.',"
        + sql_json({
            "schemaVersion": "practice-section-delivery-v1",
            "listeningDelivery": {
                "checkAudioReference": "/audio/practice/listening-speaker-check.wav",
                "programAudioReference": AUDIO_REFERENCE,
                "startOnce": True, "continuousPlayback": True,
                "seekAllowed": False, "replayAllowed": False
            }
        }) + ",60,100,0),",
        "    (@wset,@wtest,'Phần Viết','WRITING','MAIN','Hoàn thành Q51–Q54. Feedback AI là thử nghiệm.',NULL,50,100,0);",
        "SET @rsec=(SELECT id FROM practice_sections WHERE set_id=@rset LIMIT 1);",
        "SET @lsec=(SELECT id FROM practice_sections WHERE set_id=@lset LIMIT 1);",
        "SET @wsec=(SELECT id FROM practice_sections WHERE set_id=@wset LIMIT 1);",
        ""
    ]

    reading_assets = {item["assetId"]: static_image(item["logicalKey"])
                      for item in reading.get("visualAssets", [])}
    group_rows = []
    for index, group in enumerate(reading["passageGroups"]):
        image = next((reading_assets[key] for key in group.get("visualAssetIds", [])), None)
        group_rows.append(tuple_sql([
            "@rset", "@rsec", sql_text(group["groupId"]),
            str(min(group["questionNumbers"])), str(max(group["questionNumbers"])),
            sql_text(group["instruction"]), sql_text("READING_PASSAGE"),
            sql_text("ko"), sql_text("ko"), sql_text(group.get("passageText")),
            "NULL", sql_text(image),
            sql_json({"source": "TOPIK35_REPO_PACKAGE", "qaStatus": group["qaStatus"]}),
            "NULL", "NULL", str(index)
        ]))
    lines += [
        "INSERT INTO practice_question_groups (set_id,section_id,group_label,question_from,question_to,instruction,stimulus_type,instruction_language_tag,stimulus_language_tag,passage_text,transcript_text,image_url,stimulus_provenance_json,audio_url,example_json,display_order) VALUES",
        ",\n".join(group_rows) + ";"
    ]

    listening_group_rows = []
    for index, group in enumerate(listening_transcripts["groups"]):
        listening_group_rows.append(tuple_sql([
            "@lset", "@lsec", sql_text(group["groupId"]),
            str(group["questionFrom"]), str(group["questionTo"]),
            sql_text(group["stimulus"]["instruction"]), sql_text("LISTENING_AUDIO"),
            sql_text("ko"), sql_text("ko"), "NULL",
            sql_text(group["stimulus"]["transcriptText"]), "NULL",
            sql_json({"source": "TOPIK35_REPO_PACKAGE", "qaStatus": group["transcriptQaStatus"],
                      "timingStatus": group["timingStatus"]}),
            sql_text(AUDIO_REFERENCE), "NULL", str(index)
        ]))
    lines += [
        "INSERT INTO practice_question_groups (set_id,section_id,group_label,question_from,question_to,instruction,stimulus_type,instruction_language_tag,stimulus_language_tag,passage_text,transcript_text,image_url,stimulus_provenance_json,audio_url,example_json,display_order) VALUES",
        ",\n".join(listening_group_rows) + ";"
    ]

    writing_group_rows = []
    for index, question in enumerate(writing["questions"]):
        writing_group_rows.append(tuple_sql([
            "@wset", "@wsec", sql_text(question["taskType"]),
            str(question["questionNumber"]), str(question["questionNumber"]),
            sql_text(question["promptInstruction"]), sql_text("NONE"),
            sql_text("ko"), sql_text("ko"), "NULL", "NULL", "NULL",
            sql_json({"source": "TOPIK35_REPO_PACKAGE", "qaStatus": question["sourceQaStatus"]}),
            "NULL", "NULL", str(index)
        ]))
    lines += [
        "INSERT INTO practice_question_groups (set_id,section_id,group_label,question_from,question_to,instruction,stimulus_type,instruction_language_tag,stimulus_language_tag,passage_text,transcript_text,image_url,stimulus_provenance_json,audio_url,example_json,display_order) VALUES",
        ",\n".join(writing_group_rows) + ";",
        ""
    ]

    for prefix, set_var, payload in [
        ("r", "@rset", reading),
        ("l", "@lset", listening_questions)
    ]:
        rows = []
        for index, question in enumerate(payload["questions"]):
            group_var = ("(SELECT id FROM practice_question_groups WHERE set_id="
                         + set_var + " AND group_label=" + sql_text(question["groupId"]) + " LIMIT 1)")
            rows.append(objective_row(set_var, group_var, question, index))
        lines += [
            "INSERT INTO practice_questions (set_id,group_id,question_no,question_type,prompt,options_json,question_content_json,answer_key,answer_spec_json,explanation,explanation_strategy_registry_version,explanation_strategy_code,explanation_strategy_version,points,display_order,writing_task_type) VALUES",
            ",\n".join(rows) + ";"
        ]

    writing_rows = []
    for index, question in enumerate(writing["questions"]):
        content, answer = writing_content(question)
        prompt = question["promptInstruction"] + "\n" + question["promptText"]
        writing_rows.append(tuple_sql([
            "@wset",
            "(SELECT id FROM practice_question_groups WHERE set_id=@wset AND group_label="
                + sql_text(question["taskType"]) + " LIMIT 1)",
            str(question["questionNumber"]), sql_text("ESSAY"), sql_text(prompt),
            "NULL", sql_json(content), "NULL", sql_json(answer),
            sql_text("Nguồn và đáp án mẫu đã được QA; scoring vẫn mang nhãn experimental."),
            "NULL", "NULL", "NULL", f"{question['points']:.2f}", str(index),
            sql_text(question["taskType"])
        ]))
    lines += [
        "INSERT INTO practice_questions (set_id,group_id,question_no,question_type,prompt,options_json,question_content_json,answer_key,answer_spec_json,explanation,explanation_strategy_registry_version,explanation_strategy_code,explanation_strategy_version,points,display_order,writing_task_type) VALUES",
        ",\n".join(writing_rows) + ";",
        "",
        "INSERT INTO practice_published_versions (set_id,version_number,status,content_hash,published_by,published_at)",
        "SELECT s.id,1,'PUBLISHED',SHA2(CONCAT(@topik35_bundle,':',JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedKey'))),256),@topik35_author,CURRENT_TIMESTAMP FROM practice_sets s WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedBundle'))=@topik35_bundle;",
        "INSERT INTO practice_set_versions (published_version_id,set_id,title,description,skill,scope,class_id,metadata_json,creation_method,cover_image_url)",
        "SELECT pv.id,s.id,s.title,s.description,s.skill,s.scope,s.class_id,s.metadata_json,s.creation_method,s.cover_image_url FROM practice_published_versions pv JOIN practice_sets s ON s.id=pv.set_id WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedBundle'))=@topik35_bundle;",
        "INSERT INTO practice_test_versions (published_version_id,set_version_id,test_id,title,description,display_order,estimated_minutes)",
        "SELECT pv.id,sv.id,t.id,t.title,t.description,t.display_order,t.estimated_minutes FROM practice_published_versions pv JOIN practice_set_versions sv ON sv.published_version_id=pv.id JOIN practice_tests t ON t.set_id=pv.set_id JOIN practice_sets s ON s.id=pv.set_id WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedBundle'))=@topik35_bundle;",
        "INSERT INTO practice_section_versions (published_version_id,test_version_id,section_id,title,skill,section_type,instructions,delivery_json,duration_minutes,total_points,display_order)",
        "SELECT pv.id,tv.id,sec.id,sec.title,sec.skill,sec.section_type,sec.instructions,sec.delivery_json,sec.duration_minutes,sec.total_points,sec.display_order FROM practice_published_versions pv JOIN practice_test_versions tv ON tv.published_version_id=pv.id JOIN practice_sections sec ON sec.test_id=tv.test_id JOIN practice_sets s ON s.id=pv.set_id WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedBundle'))=@topik35_bundle;",
        "INSERT INTO practice_question_group_versions (published_version_id,section_version_id,group_id,group_label,question_from,question_to,instruction,stimulus_type,instruction_language_tag,stimulus_language_tag,passage_text,transcript_text,image_url,stimulus_provenance_json,audio_url,example_json,display_order)",
        "SELECT pv.id,secv.id,g.id,g.group_label,g.question_from,g.question_to,g.instruction,g.stimulus_type,g.instruction_language_tag,g.stimulus_language_tag,g.passage_text,g.transcript_text,g.image_url,g.stimulus_provenance_json,g.audio_url,g.example_json,g.display_order FROM practice_published_versions pv JOIN practice_section_versions secv ON secv.published_version_id=pv.id JOIN practice_question_groups g ON g.section_id=secv.section_id JOIN practice_sets s ON s.id=pv.set_id WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedBundle'))=@topik35_bundle;",
        "INSERT INTO practice_question_versions (published_version_id,section_version_id,group_version_id,question_id,question_no,question_type,prompt,options_json,question_content_json,answer_key,answer_spec_json,explanation,explanation_strategy_registry_version,explanation_strategy_code,explanation_strategy_version,points,display_order,writing_task_type)",
        "SELECT pv.id,gv.section_version_id,gv.id,q.id,q.question_no,q.question_type,q.prompt,q.options_json,q.question_content_json,q.answer_key,q.answer_spec_json,q.explanation,q.explanation_strategy_registry_version,q.explanation_strategy_code,q.explanation_strategy_version,q.points,q.display_order,q.writing_task_type FROM practice_published_versions pv JOIN practice_question_group_versions gv ON gv.published_version_id=pv.id JOIN practice_questions q ON q.group_id=gv.group_id JOIN practice_sets s ON s.id=pv.set_id WHERE JSON_UNQUOTE(JSON_EXTRACT(s.metadata_json,'$.seedBundle'))=@topik35_bundle;",
        ""
    ]
    OUTPUT.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
