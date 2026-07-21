#!/usr/bin/env python3
"""Generate paste-ready Mermaid class and sequence diagrams for Practice."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from generate_practice_architecture_artifacts import CAPABILITIES, DOCX_CAPABILITIES
from practice_use_case_english import DOCUMENT_GROUPS


ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIR = ROOT / "docs" / "architecture" / "practice" / "mermaid"
CLASS_OUTPUT = OUTPUT_DIR / "KSH_PRACTICE_CLASS_DIAGRAMS.md"
SEQUENCE_OUTPUT = OUTPUT_DIR / "KSH_PRACTICE_SEQUENCE_DIAGRAMS.md"


GROUP_DIRECTIONS = {
    "MGT": "LR",
    "ATT": "LR",
    "RSL": "TB",
    "PRG": "LR",
}


EXTRA_RELATIONS: dict[str, list[tuple[str, str, str]]] = {
    "MGT": [
        ("PracticeImportDraftService", "PracticeDraftService", "creates canonical draft"),
        ("PracticePdfAiOrchestrator", "PracticeImportDraftService", "creates canonical draft"),
        ("PracticeImportDraftService", "PracticeDraftValidator", "reuses publish checks"),
    ],
    "ATT": [
        ("PracticeDetailPageService", "PracticeService", "supplies start context"),
        ("PracticeAttemptVersionLock", "PracticeVersionSnapshot", "binds delivery"),
        ("PracticeAttempt", "PracticeVersionSnapshot", "uses locked version"),
    ],
    "RSL": [
        ("QuestionExplanationReadService", "PracticeResultDetailAssembler", "supplies objective evidence"),
        ("WritingEvaluationCacheService", "PracticeResultAssembler", "supplies writing state"),
        ("SpeakingEvaluationOrchestrator", "PracticeResultAssembler", "supplies speaking state"),
    ],
    "PRG": [],
}


PARTICIPANT_LABELS = {
    "Learner": "Student",
    "Learner/Operator": "Student or Operator",
    "Learner/Reviewer": "Student or Reviewer",
    "Owner/Collaborator": "Owner or Collaborator",
    "Worker/Operator": "Worker or Operator",
    "Browser/View": "Browser View",
    "Retry/Re-evaluate Service": "Retry or Re-evaluate Service",
}


HUMAN_PARTICIPANTS = {
    "Learner",
    "Learner/Operator",
    "Learner/Reviewer",
    "Lecturer",
    "Owner/Collaborator",
}


def mermaid_id(value: str) -> str:
    identifier = re.sub(r"[^A-Za-z0-9_-]+", "_", value).strip("_-")
    if not identifier:
        raise ValueError(f"Cannot derive Mermaid identifier from {value!r}")
    if identifier[0].isdigit():
        identifier = f"N_{identifier}"
    return identifier


def operation_name(value: str) -> str:
    words = re.findall(r"[A-Za-z0-9]+", value)
    if not words:
        return "handle()"
    first, *rest = words
    name = first.lower() + "".join(word[:1].upper() + word[1:] for word in rest)
    if name[0].isdigit():
        name = f"handle{name}"
    return f"{name}()"


def annotation_name(stereotype: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "", stereotype) or "Component"


def status_style(status: str) -> str:
    return {
        "CURRENT": "current",
        "PLANNED 13E": "planned13e",
        "PLANNED 13F": "planned13f",
    }.get(status, "deferred")


def module_namespace(code: str, name: str) -> str:
    compact_name = re.sub(r"[^A-Za-z0-9]+", "_", name).strip("_")
    return f"{code}_{compact_name}"


def class_definition(
    name: str,
    stereotype: str,
    responsibilities: list[str],
) -> list[str]:
    identifier = mermaid_id(name)
    label = f'["{name}"]' if identifier != name else ""
    lines = [f"        class {identifier}{label} {{", f"            <<{annotation_name(stereotype)}>>"]
    for responsibility in responsibilities:
        lines.append(f"            +{operation_name(responsibility)}")
    lines.append("        }")
    return lines


def build_class_diagram(group: dict[str, Any], capabilities: dict[str, dict[str, Any]]) -> str:
    lines = [
        "classDiagram",
        f"    direction {GROUP_DIRECTIONS[group['code']]}",
        "    note \"Status: green = CURRENT; amber dashed = PLANNED 13E; blue dashed = PLANNED 13F\"",
    ]
    defined: dict[str, tuple[str, str]] = {}
    status_members: dict[str, list[str]] = {
        "current": [],
        "planned13e": [],
        "planned13f": [],
        "deferred": [],
    }

    for code in group["capabilities"]:
        capability = capabilities[code]
        namespace = module_namespace(code, capability["short"])
        lines.append(f"    namespace {namespace} {{")
        for name, stereotype, status, responsibilities in capability["classes"]:
            identifier = mermaid_id(name)
            if identifier in defined:
                continue
            defined[identifier] = (name, status)
            status_members[status_style(status)].append(identifier)
            lines.extend(class_definition(name, stereotype, responsibilities))
        lines.append("    }")

    relation_rows: list[tuple[str, str, str]] = []
    for code in group["capabilities"]:
        relation_rows.extend(capabilities[code]["relations"])
    relation_rows.extend(EXTRA_RELATIONS[group["code"]])

    seen_relations: set[tuple[str, str, str]] = set()
    for source, target, label in relation_rows:
        relation = (mermaid_id(source), mermaid_id(target), label)
        if relation in seen_relations:
            continue
        if relation[0] not in defined or relation[1] not in defined:
            raise ValueError(f"Unknown class relation in {group['code']}: {relation}")
        seen_relations.add(relation)
        lines.append(f"    {relation[0]} --> {relation[1]} : {label}")

    lines.extend(
        [
            "    classDef current fill:#E8F5E9,stroke:#2E7D32,color:#0B2545,stroke-width:1.5px;",
            "    classDef planned13e fill:#FFF8E1,stroke:#F9A825,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;",
            "    classDef planned13f fill:#E3F2FD,stroke:#1976D2,color:#0B2545,stroke-width:1.5px,stroke-dasharray:5 3;",
            "    classDef deferred fill:#F5F5F5,stroke:#616161,color:#263238,stroke-dasharray:3 3;",
        ]
    )
    for style, identifiers in status_members.items():
        if identifiers:
            lines.append(f"    cssClass \"{','.join(identifiers)}\" {style}")
    return "\n".join(lines)


def participant_display_name(name: str) -> str:
    display = PARTICIPANT_LABELS.get(name, name)
    display = re.sub(r"\s*\[(13[EF])\]", r" (\1)", display)
    return display


def build_sequence_diagram(item: dict[str, Any]) -> str:
    aliases = {name: f"P{index:02d}" for index, name in enumerate(item["participants"], start=1)}
    lines = ["sequenceDiagram", "    autonumber"]
    for name in item["participants"]:
        keyword = "actor" if name in HUMAN_PARTICIPANTS else "participant"
        lines.append(f"    {keyword} {aliases[name]} as {participant_display_name(name)}")
    first = aliases[item["participants"][0]]
    last = aliases[item["participants"][-1]]
    lines.append(f"    Note over {first},{last}: {item['status']} - {item['id']} - {item['title']}")
    for source, target, message, kind in item["sequence"]:
        arrow = "-->>" if kind == "return" else "->>"
        lines.append(f"    {aliases[source]}{arrow}{aliases[target]}: {message}")
    return "\n".join(lines)


def render_class_document(groups: list[dict[str, Any]], capabilities: dict[str, dict[str, Any]]) -> str:
    lines = [
        "# KSH Practice Mermaid Class Diagrams",
        "",
        "Status: `PRE_13E_ARCHITECTURE_BASELINE`",
        "",
        "Each fenced block is a standalone Mermaid diagram. Copy only the code inside one block into Mermaid Live Editor.",
        "The four reader-facing areas preserve the ten internal module codes used by code, Draw.io and Jira.",
        "",
    ]
    for index, group in enumerate(groups, start=1):
        module_codes = ", ".join(group["capabilities"])
        lines.extend(
            [
                f"## {index}. {group['name']}",
                "",
                f"Modules: `{module_codes}`",
                "",
                "```mermaid",
                build_class_diagram(group, capabilities),
                "```",
                "",
            ]
        )
    return "\n".join(lines).rstrip() + "\n"


def render_sequence_document(
    groups: list[dict[str, Any]],
    capabilities: dict[str, dict[str, Any]],
    english_items: dict[str, dict[str, Any]],
) -> str:
    lines = [
        "# KSH Practice Mermaid Sequence Diagrams",
        "",
        "Status: `PRE_13E_ARCHITECTURE_BASELINE`",
        "",
        "This document contains exactly 30 standalone Mermaid sequence diagrams, one for each formal Practice Use Case.",
        "Copy only the code inside one fenced block into Mermaid Live Editor.",
        "",
    ]
    diagram_count = 0
    for group_index, group in enumerate(groups, start=1):
        lines.extend([f"## {group_index}. {group['name']}", ""])
        for code in group["capabilities"]:
            capability = capabilities[code]
            lines.extend([f"### Module {code} - {capability['short']}", ""])
            for source_item in capability["use_cases"]:
                diagram_count += 1
                item = dict(source_item)
                item["title"] = english_items[item["id"]]["title"]
                lines.extend(
                    [
                        f"#### {item['id']} - {item['title']}",
                        "",
                        f"Status: `{item['status']}`",
                        "",
                        "```mermaid",
                        build_sequence_diagram(item),
                        "```",
                        "",
                    ]
                )
    if diagram_count != 30:
        raise ValueError(f"Expected 30 sequence diagrams, got {diagram_count}")
    return "\n".join(lines).rstrip() + "\n"


def validate_outputs(class_text: str, sequence_text: str) -> None:
    if class_text.count("```mermaid") != 4:
        raise ValueError("Class document must contain exactly four Mermaid blocks")
    if sequence_text.count("```mermaid") != 30:
        raise ValueError("Sequence document must contain exactly 30 Mermaid blocks")
    if sequence_text.count("sequenceDiagram") != 30:
        raise ValueError("Every sequence block must declare sequenceDiagram")
    if class_text.count("classDiagram") != 4:
        raise ValueError("Every class block must declare classDiagram")
    for forbidden in ("Learner as", " as Learner", "Learner/Operator", "Learner/Reviewer"):
        if forbidden in sequence_text:
            raise ValueError(f"Reader-facing Mermaid copy still contains {forbidden!r}")


def main() -> None:
    capabilities = {capability["code"]: capability for capability in CAPABILITIES}
    english_items = {
        item["id"]: item
        for capability in DOCX_CAPABILITIES
        for item in capability["use_cases"]
    }
    groups = [dict(group) for group in DOCUMENT_GROUPS]
    class_text = render_class_document(groups, capabilities)
    sequence_text = render_sequence_document(groups, capabilities, english_items)
    validate_outputs(class_text, sequence_text)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    CLASS_OUTPUT.write_text(class_text, encoding="utf-8")
    SEQUENCE_OUTPUT.write_text(sequence_text, encoding="utf-8")
    print(f"Generated {CLASS_OUTPUT}")
    print(f"Generated {SEQUENCE_OUTPUT}")


if __name__ == "__main__":
    main()
