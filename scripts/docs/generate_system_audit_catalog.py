#!/usr/bin/env python3
"""Generate deterministic source, route, and UI-action catalogs for the KSH audit."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "audit"
JAVA = ROOT / "src" / "main" / "java"
TEMPLATES = ROOT / "src" / "main" / "resources" / "templates"
STATIC = ROOT / "src" / "main" / "resources" / "static"
RUNTIME = ROOT / "src" / "main"

MAPPING_PREFIX = r"(?:org\.springframework\.web\.bind\.annotation\.)?"
HTTP_ANNOTATION = re.compile(
    rf"@{MAPPING_PREFIX}(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\s*(?:\((.*?)\))?",
    re.DOTALL,
)
HTTP_ANNOTATION_ON_LINE = re.compile(
    rf"@{MAPPING_PREFIX}(?:Get|Post|Put|Patch|Delete|Request)Mapping\b"
)
REQUEST_METHOD = re.compile(r"\bRequestMethod\.(GET|POST|PUT|PATCH|DELETE)\b")
METHOD = re.compile(
    r"\b(?:public|protected|private)\s+(?:static\s+)?(?:[\w<>?,.\[\] ]+)\s+(\w+)\s*\("
)
CLASS = re.compile(
    r"^\s*(?:public\s+)?(?:abstract\s+|final\s+|sealed\s+)?"
    r"(?:class|interface|record|enum)\s+(\w+)",
    re.MULTILINE,
)
STRING = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def line_count(path: Path) -> int:
    with path.open("rb") as stream:
        return sum(1 for _ in stream)


def runtime_file_catalog() -> list[str]:
    text_suffixes = {
        ".java", ".html", ".js", ".css", ".sql", ".properties", ".yml",
        ".yaml", ".json", ".xml", ".md", ".txt", ".svg", ".example",
    }
    rows: list[str] = []
    for path in sorted(item for item in RUNTIME.rglob("*") if item.is_file()):
        relative = rel(path)
        if path.suffix == ".java":
            kind = "Java production"
        elif TEMPLATES in path.parents:
            kind = "Thymeleaf template"
        elif STATIC in path.parents and path.suffix == ".js":
            kind = "Browser JavaScript"
        elif STATIC in path.parents and path.suffix == ".css":
            kind = "CSS"
        elif "db/migration" in relative and path.suffix == ".sql":
            kind = "Flyway migration"
        elif path.suffix in {".properties", ".yml", ".yaml"}:
            kind = "Runtime config"
        else:
            kind = "Runtime resource/asset"
        lines = str(line_count(path)) if path.suffix.lower() in text_suffixes else "—"
        rows.append(
            f"| `{relative}` | {kind} | {path.stat().st_size} | {lines} |"
        )
    return rows


def one_line(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).replace("|", "\\|")


def annotation_path(args: str | None) -> str:
    if not args:
        return "(empty)"
    literals = STRING.findall(args)
    if literals:
        return ", ".join(literals)
    return one_line(args)


def java_catalog() -> tuple[list[str], list[str]]:
    sources: list[str] = []
    routes: list[str] = []
    for path in sorted(JAVA.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        clazz = CLASS.search(text)
        class_line = text[:clazz.start()].count("\n") if clazz else -1
        class_base = ""
        if clazz:
            prefix = text[:clazz.start()]
            class_mappings = list(HTTP_ANNOTATION.finditer(prefix))
            if class_mappings and class_mappings[-1].group(1) == "RequestMapping":
                class_base = annotation_path(class_mappings[-1].group(2))
                if class_base == "(empty)":
                    class_base = ""
        package = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        sources.append(
            f"| `{rel(path)}` | {line_count(path)} | "
            f"`{package.group(1) if package else ''}` | `{clazz.group(1) if clazz else ''}` |"
        )

        lines = text.splitlines()
        for idx, line in enumerate(lines):
            if not HTTP_ANNOTATION_ON_LINE.search(line):
                continue
            start = idx
            block = line
            balance = line.count("(") - line.count(")")
            while balance > 0 and idx + 1 < len(lines):
                idx += 1
                block += " " + lines[idx]
                balance += lines[idx].count("(") - lines[idx].count(")")
            match = HTTP_ANNOTATION.search(block)
            if not match:
                continue
            kind, args = match.groups()
            # Class-level mappings belong in `class_base`; they are not handler
            # rows. Looking only a few lines ahead fails when security/property
            # annotations sit between @RequestMapping and the class declaration.
            if clazz and start < class_line:
                continue
            signature = "\n".join(lines[idx + 1: idx + 15])
            method = METHOD.search(signature)
            if kind == "RequestMapping":
                request_methods = REQUEST_METHOD.findall(args or "")
                verb = ",".join(dict.fromkeys(request_methods)) if request_methods else "ANY"
            else:
                verb = kind.removesuffix("Mapping").upper()
            child_path = annotation_path(args)
            if child_path == "(empty)":
                full_path = class_base or "(empty)"
            elif class_base:
                full_path = f"{class_base} + {child_path}"
            else:
                full_path = child_path
            routes.append(
                f"| {verb} | `{full_path}` | "
                f"`{clazz.group(1) if clazz else '?'}.{method.group(1) if method else '?'}` | "
                f"[`{rel(path)}:{start + 1}`](../../{rel(path)}#L{start + 1}) |"
            )
    return sources, routes


def ui_catalog() -> list[str]:
    rows: list[str] = []
    patterns = re.compile(
        r"(?:th:action|th:href|action|href)\s*=\s*\"([^\"]+)\"|"
        r"\bfetch\s*\(\s*([^,\n)]+)|\baxios\.(get|post|put|patch|delete)\s*\(\s*([^,\n)]+)"
    )
    files = sorted(TEMPLATES.rglob("*.html")) + sorted(STATIC.rglob("*.js"))
    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        for lineno, line in enumerate(text.splitlines(), 1):
            for match in patterns.finditer(line):
                target = next((g for g in match.groups() if g), "")
                if not target or target.startswith(("#", "javascript:")):
                    continue
                if re.search(r"(?:^|/)(?:css|js|images|fonts|webjars)/", target) or re.search(
                    r"\.(?:css|js|png|jpe?g|svg|ico|woff2?|ttf)(?:[?'\")}]|$)", target
                ):
                    continue
                rows.append(
                    f"| `{one_line(target)}` | [`{rel(path)}:{lineno}`](../../{rel(path)}#L{lineno}) | "
                    f"`{one_line(line)[:180]}` |"
                )
    return rows


def write(name: str, title: str, headers: str, rows: list[str], note: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    body = [
        f"# {title}", "", note, "",
        "Tệp này được sinh bởi `scripts/docs/generate_system_audit_catalog.py`; không sửa thủ công.",
        "", headers, *rows, "",
    ]
    (OUT / name).write_text("\n".join(body), encoding="utf-8")


def main() -> None:
    sources, routes = java_catalog()
    ui = ui_catalog()
    runtime_files = runtime_file_catalog()
    write(
        "RUNTIME_FILE_MANIFEST.md", "KSH complete runtime file manifest",
        "| File | Kind | Bytes | Lines |\n|---|---|---:|---:|",
        runtime_files,
        f"Inventory đầy đủ {len(runtime_files)} file dưới `src/main`: Java, template, JS, CSS, migration, config và runtime asset.",
    )
    write(
        "SOURCE_MANIFEST.md", "KSH full source manifest",
        "| File | Lines | Package | Main type |\n|---|---:|---|---|",
        sources,
        f"Inventory đầy đủ {len(sources)} Java production source files kèm số dòng; manifest mọi runtime file nằm ở `RUNTIME_FILE_MANIFEST.md`.",
    )
    write(
        "HTTP_ENDPOINT_CATALOG.md", "KSH HTTP endpoint catalog",
        "| Verb | Mapping expression/path | Handler | Source |\n|---|---|---|---|",
        routes,
        f"Catalog {len(routes)} handler mapping. Hằng số route được giữ nguyên biểu thức để đối chiếu IConstant/route class.",
    )
    write(
        "UI_ACTION_CATALOG.md", "KSH UI action catalog",
        "| Target/expression | UI source | Context |\n|---|---|---|",
        ui,
        f"Catalog {len(ui)} action/link/fetch reference trong Thymeleaf và JavaScript; gồm cả điều hướng tĩnh để phát hiện bề mặt UI.",
    )


if __name__ == "__main__":
    main()
