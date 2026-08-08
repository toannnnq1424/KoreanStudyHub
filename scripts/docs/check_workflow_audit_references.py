#!/usr/bin/env python3
"""Validate local links and source-file references in the workflow audit."""

from __future__ import annotations

import re
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "docs" / "audit"
OUT = AUDIT / "REFERENCE_INTEGRITY_GATE.md"

MARKDOWN_LINK = re.compile(r"\[[^\]]*]\(([^)]+)\)")
ROOTED_SOURCE_PATH = re.compile(
    r"(?<![A-Za-z0-9_])((?:src|scripts)/[A-Za-z0-9_.$/@+-]+(?:/[A-Za-z0-9_.$@+-]+)*"
    r"\.(?:properties|java|html|json|js|css|sql|xml|py|png|jpe?g|webp|svg|woff2?|ttf|pdf|docx|xlsx))"
    r"(?![A-Za-z0-9.])"
)
INLINE_SOURCE_BASENAME = re.compile(
    r"`([A-Za-z0-9_$@+-]+\.(?:properties|java|html|json|js|css|sql|xml|py|png|jpe?g|webp|svg|woff2?|ttf|pdf|docx|xlsx))"
    r"(?::[^`]*)?`"
)
ROOTED_LINE_REFERENCE = re.compile(
    r"(?<![A-Za-z0-9_])((?:src|scripts)/[A-Za-z0-9_.$/@+-]+(?:/[A-Za-z0-9_.$@+-]+)*"
    r"\.(?:properties|java|html|json|js|css|sql|xml|py))"
    r":(\d+)(?:[–-](\d+))?"
)
BASENAME_LINE_REFERENCE = re.compile(
    r"(?<![A-Za-z0-9_])([A-Za-z0-9_$@+-]+\.(?:properties|java|html|json|js|css|sql|xml|py))"
    r":(\d+)(?:[–-](\d+))?"
)


def markdown_documents() -> list[Path]:
    # Do not let a previous failing generated report make the next run
    # self-referential. Every other audit artifact, including generated
    # manifests/gates, remains in scope.
    return sorted(path for path in AUDIT.rglob("*.md") if path != OUT)


def source_files() -> list[Path]:
    roots = (ROOT / "src", ROOT / "scripts")
    return sorted(
        path
        for base in roots
        if base.exists()
        for path in base.rglob("*")
        if path.is_file()
    )


def display(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def main() -> int:
    docs = markdown_documents()
    known_sources = source_files()
    by_basename: dict[str, list[Path]] = defaultdict(list)
    for path in known_sources:
        by_basename[path.name].append(path)
    line_counts = {
        path: len(path.read_text(encoding="utf-8", errors="replace").splitlines())
        for path in known_sources
        if path.suffix.lower() in {
            ".properties", ".java", ".html", ".json", ".js", ".css",
            ".sql", ".xml", ".py",
        }
    }

    local_link_count = 0
    rooted_source_count = 0
    basename_count = 0
    line_reference_count = 0
    missing: list[tuple[Path, str, str]] = []

    for doc in docs:
        body = doc.read_text(encoding="utf-8", errors="replace")

        for match in MARKDOWN_LINK.finditer(body):
            raw = match.group(1).strip()
            if raw.startswith("<") and raw.endswith(">"):
                raw = raw[1:-1]
            destination = raw.split("#", 1)[0].strip()
            if not destination or re.match(r"^[a-z][a-z0-9+.-]*:", destination, re.I):
                continue
            # Markdown optional link titles are not used by generated audit
            # artifacts; removing a quoted title still makes the checker safe
            # for a hand-written local link.
            destination = re.sub(r'\s+["\'][^"\']*["\']$', "", destination)
            target_text = unquote(destination)
            target = Path(target_text)
            if not target.is_absolute():
                target = (doc.parent / target).resolve()
            # Codex local-file links use `/absolute/file.java:123` for a
            # clickable line anchor. The `:123` suffix is UI metadata, not part
            # of the filesystem name.
            if not target.exists():
                line_suffix = re.search(r":\d+(?::\d+)?$", target_text)
                if line_suffix:
                    target_text = target_text[:line_suffix.start()]
                    target = Path(target_text)
                    if not target.is_absolute():
                        target = (doc.parent / target).resolve()
            local_link_count += 1
            if not target.exists():
                missing.append((doc, "Markdown link", destination))

        for match in ROOTED_SOURCE_PATH.finditer(body):
            source_ref = match.group(1)
            rooted_source_count += 1
            if not (ROOT / source_ref).is_file():
                missing.append((doc, "Source path", source_ref))

        for match in INLINE_SOURCE_BASENAME.finditer(body):
            basename = match.group(1)
            basename_count += 1
            if basename not in by_basename:
                missing.append((doc, "Source basename", basename))

        rooted_line_spans: set[tuple[int, int]] = set()
        for match in ROOTED_LINE_REFERENCE.finditer(body):
            rooted_line_spans.add(match.span())
            source_ref = match.group(1)
            end_line = int(match.group(3) or match.group(2))
            line_reference_count += 1
            target = ROOT / source_ref
            if target in line_counts and end_line > line_counts[target]:
                missing.append((
                    doc,
                    "Out-of-range source line",
                    f"{source_ref}:{end_line} (file has {line_counts[target]})",
                ))

        for match in BASENAME_LINE_REFERENCE.finditer(body):
            # A rooted path also ends in a basename:line token; count/check it
            # only once using its unambiguous rooted-path branch above.
            if any(start <= match.start() and match.end() <= end
                   for start, end in rooted_line_spans):
                continue
            basename = match.group(1)
            end_line = int(match.group(3) or match.group(2))
            candidates = [path for path in by_basename.get(basename, []) if path in line_counts]
            if not candidates:
                continue
            line_reference_count += 1
            if all(end_line > line_counts[path] for path in candidates):
                largest = max(line_counts[path] for path in candidates)
                missing.append((
                    doc,
                    "Out-of-range source line",
                    f"{basename}:{end_line} (largest matching file has {largest})",
                ))

    # A missing reference may occur repeatedly in generated tables. Report one
    # row per document/kind/reference while keeping all occurrences counted.
    unique_missing = sorted(
        set(missing),
        key=lambda row: (row[0].as_posix(), row[1], row[2]),
    )
    lines = [
        "# KSH workflow-audit reference integrity gate",
        "",
        "Gate này kiểm bốn lớp tham chiếu trong toàn bộ `docs/audit/**/*.md`: local "
        "Markdown link, đường dẫn source bắt đầu bằng `src/` hoặc `scripts/`, tên "
        "file source đứng độc lập trong inline code, và line/range không vượt EOF. "
        "Nó phân biệt rõ **broken "
        "documentation reference** với lỗi runtime: file/link sai làm gate tài liệu fail, "
        "nhưng chỉ source call/render/import thật tới tài nguyên sai mới là code defect.",
        "",
        "| Inventory | Checked | Missing |",
        "|---|---:|---:|",
        f"| Audit Markdown files | {len(docs)} | 0 |",
        f"| Local Markdown links | {local_link_count} | {sum(1 for _, kind, _ in missing if kind == 'Markdown link')} |",
        f"| Rooted source paths | {rooted_source_count} | {sum(1 for _, kind, _ in missing if kind == 'Source path')} |",
        f"| Inline source basenames | {basename_count} | {sum(1 for _, kind, _ in missing if kind == 'Source basename')} |",
        f"| Source line/range references | {line_reference_count} | {sum(1 for _, kind, _ in missing if kind == 'Out-of-range source line')} |",
        "",
    ]
    if unique_missing:
        lines.extend([
            "## Missing references",
            "",
            "| Document | Kind | Missing reference |",
            "|---|---|---|",
        ])
        for doc, kind, reference in unique_missing:
            lines.append(f"| `{display(doc)}` | {kind} | `{reference}` |")
        lines.extend([
            "",
            "**FAIL** — tài liệu còn trỏ tới file/link không tồn tại hoặc line vượt EOF.",
            "",
        ])
    else:
        lines.extend([
            "## Status",
            "",
            "**PASS** — không có local link/rooted source path/inline source basename "
            "nào trỏ tới file không tồn tại, và không có line/range nào vượt EOF.",
            "",
        ])

    OUT.write_text("\n".join(lines), encoding="utf-8")
    return 1 if unique_missing else 0


if __name__ == "__main__":
    raise SystemExit(main())
