#!/usr/bin/env python3
"""Statically catalog Java repository/DAO/JDBC declarations; never executes application code."""
from __future__ import annotations
import argparse, re, sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUFFIXES = ("Repository.java", "JdbcStore.java", "Dao.java")
DIRECT_CLIENT_RE = re.compile(r"\b(?:JdbcTemplate|EntityManager)\b")
TYPE_RE = re.compile(r"\b(?:public\s+)?(?:abstract\s+)?(?:class|interface)\s+(\w+)")
METHOD_RE = re.compile(r"^(?:public|protected|private|default|static|abstract|final|synchronized|\s)*[\w<>?,.@\[\] ]+\s+(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[\w., ]+)?\s*([;{])")
BARE_SIGNATURE_PART_RE = re.compile(r"^(?:public|protected|private|default|static|abstract|final|synchronized|\s)*[\w<>?,.@\[\] ]+$")
QUERY_RE = re.compile(r"@Query\s*\(\s*(?:value\s*=\s*)?(?:\"\"\"(.*?)\"\"\"|\"((?:\\.|[^\"])*)\"|\'((?:\\.|[^\'])*)\')", re.S)
STRING_RE = re.compile(r"\"\"\"(.*?)\"\"\"|\"((?:\\.|[^\"])*)\"|\'((?:\\.|[^\'])*)\'", re.S)
JDBC_CALL_RE = re.compile(r"\b(?:namedParameterJdbcTemplate|jdbcTemplate|jdbc)\w*\s*\.\s*(?:query|update|execute|batchUpdate)\s*\(\s*(?:\"\"\"(.*?)\"\"\"|\"((?:\\.|[^\"])*)\"|\'((?:\\.|[^\'])*)\')", re.S)
ENTITY_CALL_RE = re.compile(r"\b\w+\s*\.\s*(?:createQuery|createNativeQuery|createNamedQuery)\s*\(\s*(?:\"\"\"(.*?)\"\"\"|\"((?:\\.|[^\"])*)\"|\'((?:\\.|[^\'])*)\')", re.S)

@dataclass
class Method:
    line: int
    name: str
    kind: str
    query: str

def compact(value: str, limit: int = 260) -> str:
    value = re.sub(r"\s+", " ", value.replace("\\n", " ").replace('\\"', '"')).strip()
    return value if len(value) <= limit else value[:limit - 1].rstrip() + "…"

def braces(line: str) -> int:
    line = re.sub(r"\"(?:\\.|[^\"])*\"|'(?:\\.|[^'])*'", "", line)
    line = line.split("//", 1)[0]
    return line.count("{") - line.count("}")

def annotation_query(annotations: str) -> tuple[str, str]:
    if not re.search(r"@Query\b", annotations):
        return "", ""
    hit = QUERY_RE.search(annotations)
    # Spring Data commonly builds JPQL from adjacent Java string literals.
    # Starting at @Query avoids strings from earlier annotations such as @QueryHints.
    query_block = annotations[hit.start():] if hit else ""
    next_annotation = re.search(r"\s+@\w+", query_block[1:])
    if next_annotation:
        query_block = query_block[:next_annotation.start() + 1]
    literals = [next((x for x in found if x), "") for found in STRING_RE.findall(query_block)]
    text = compact("".join(literals))
    return ("native @Query" if re.search(r"nativeQuery\s*=\s*true", annotations) else "@Query"), text or "[annotation query is dynamic/unparsed]"

def jdbc_query(body: str) -> str:
    hit = JDBC_CALL_RE.search(body)
    if hit:
        return compact(next((x for x in hit.groups() if x), ""))
    if not re.search(r"\b(?:namedParameterJdbcTemplate|jdbcTemplate|jdbc)\w*\s*\.\s*(?:query|update|execute|batchUpdate)\s*\(", body):
        return ""
    return "[SQL is dynamic/unparsed]"

def entity_manager_query(body: str) -> str:
    hit = ENTITY_CALL_RE.search(body)
    if hit:
        return compact(next((x for x in hit.groups() if x), ""))
    if not re.search(r"\b\w+\s*\.\s*(?:createQuery|createNativeQuery|createNamedQuery|find|persist|merge|remove|flush)\s*\(", body):
        return ""
    return "[JPQL/SQL is dynamic or operation has no literal query]"

def methods_in(path: Path) -> tuple[str, list[Method]]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    type_name = path.stem
    class_depth, depth, pending_type = None, 0, None
    annotations, annotation_parens, sig, sig_line, body, body_depth, out = [], 0, [], 0, [], None, []
    for number, raw in enumerate(lines, 1):
        if class_depth is None:
            found = TYPE_RE.search(raw)
            before = depth
            depth += braces(raw)
            if found:
                type_name = found.group(1)
                if depth > before:
                    class_depth = depth
                else:
                    pending_type = type_name
            elif pending_type is not None and depth > before:
                class_depth = depth
            continue
        if body_depth is not None:
            body.append(raw)
            depth += braces(raw)
            if depth < body_depth:
                sql = jdbc_query("\n".join(body))
                old = out[-1]
                if sql:
                    out[-1] = Method(old.line, old.name, "Direct JDBC", sql)
                else:
                    query = entity_manager_query("\n".join(body))
                    if query:
                        out[-1] = Method(old.line, old.name, "EntityManager", query)
                body_depth = None
            continue
        stripped = raw.strip()
        if depth == class_depth:
            if annotation_parens:
                annotations.append(stripped)
                annotation_parens += raw.count("(") - raw.count(")")
            elif sig or ((annotations or "(" in stripped or BARE_SIGNATURE_PART_RE.fullmatch(stripped))
                          and not stripped.startswith(("@", "//", "/*", "*"))):
                if not sig:
                    sig_line = number
                sig.append(stripped)
                joined = " ".join(sig)
                match = None if re.match(r"^(?:public|protected|private|static|final|\s)*record\s+\w+\s*\(", joined) else METHOD_RE.match(joined)
                if match:
                    category, query = annotation_query(" ".join(annotations))
                    default = "derived/other repository" if path.name.endswith("Repository.java") else "custom declared"
                    is_constructor = match.group(1) == type_name
                    if not is_constructor:  # Constructors are not data-access methods.
                        out.append(Method(sig_line, match.group(1), category or default, query))
                    if match.group(2) == "{" and not is_constructor:
                        body, body_depth = [raw], depth + braces(raw)
                        if depth >= body_depth:
                            body_depth = None
                    annotations, annotation_parens, sig = [], 0, []
                elif "{" in joined or ";" in joined:
                    annotations, annotation_parens, sig = [], 0, []
            elif stripped.startswith("@"):
                annotations.append(stripped)
                annotation_parens = raw.count("(") - raw.count(")")
            elif stripped and not stripped.startswith(("//", "/*", "*")):
                annotations, annotation_parens = [], 0
        depth += braces(raw)
    return type_name, out

def link(path: Path, line: int) -> str:
    return f"[{path.relative_to(ROOT)}]({path}:{line})"

def file_kind(path: Path) -> str:
    if path.name.endswith("JdbcStore.java"):
        return "JdbcStore"
    if path.name.endswith("Dao.java"):
        return "Dao"
    if path.name.endswith("Repository.java"):
        return "Repository"
    return "Direct JDBC/EntityManager"

def source_self_check(records: list[tuple[Path, str, list[Method]]]) -> list[str]:
    """Independently re-match each emitted signature and account for @Query annotations."""
    errors = []
    for path, _, declared in records:
        raw = path.read_text(encoding="utf-8", errors="replace")
        lines = raw.splitlines()
        for method in declared:
            candidate = " ".join(line.strip() for line in lines[method.line - 1:method.line + 14])
            match = METHOD_RE.match(candidate)
            if not match or match.group(1) != method.name:
                errors.append(f"{path}:{method.line}: emitted {method.name}() does not re-match a Java declaration")
        expected_queries = len(re.findall(r"@Query\b", raw))
        actual_queries = sum("@Query" in method.kind for method in declared)
        if expected_queries != actual_queries:
            errors.append(f"{path}: @Query annotations={expected_queries}, catalogued @Query methods={actual_queries}")
    return errors

def render(records: list[tuple[Path, str, list[Method]]], checked: bool) -> str:
    files = Counter(file_kind(p) for p, _, _ in records)
    methods = [m for _, _, ms in records for m in ms]
    lines = [
        "# Data-access query catalog", "",
        "> Generated by scripts/docs/generate_data_access_audit_catalog.py; regenerate after source changes.", "",
        "This is a **static supplemental inventory**, not a semantic screen/API walkthrough. It does not establish authorization, transaction scope, call order, feature reachability, or runtime SQL after ORM/provider rewriting; use the workflow audit documents for those facts.", "",
        f"**Scope:** {len(records)} source files; **declared methods recognized:** {len(methods)}. Files: " + ", ".join(f"{k} {v}" for k, v in sorted(files.items())) + ".", "",
        "Selection includes every *Repository.java, *JdbcStore.java, and *Dao.java, plus every Java source that statically mentions JdbcTemplate or EntityManager. A direct-client file is included in full even when a listed method delegates rather than issuing SQL itself.", "",
        "**Source self-check:** " + ("passed (each emitted method re-matched a declaration and @Query annotation counts agree)." if checked else "not requested; run with --self-check before relying on changed output.") , "",
        "## How classifications work", "",
        "- **derived/other repository** — a repository declaration without @Query; its actual SQL is generated by Spring Data/provider conventions or inherited behavior.",
        "- **@Query / native @Query** — declaration annotation; query text is compacted to a single line.",
        "- **Direct JDBC** — method body contains a recognizable JDBC-template query/update/execute call; SQL is shown only when a literal was statically recoverable.",
        "- **EntityManager** — method body calls a common EntityManager operation; literal JPQL/native SQL is shown where recoverable.",
        "- **custom declared** — a declared method with no recognized direct JDBC/EntityManager call (it can still delegate to a repository or another data-access component).", "",
        "## Static-parser limitations", "",
        "- This is source-text parsing, not Java compilation or framework introspection. It can miss unusual multiline declarations, generated sources, inherited CRUD methods, indirect helper SQL, and annotation constants.",
        "- Line links point to method signatures. Query snippets are truncated to 260 characters and may be parameterized, concatenated, or provider-transformed at runtime.",
        "- @Query parsing covers direct literal annotations; named/constant/expression queries are marked unparsed. JDBC/EntityManager detection looks for common calls only.", "",
    ]
    for path, cls, declared in records:
        lines += [f"## {cls}", "", f"Source: {link(path, 1)}", ""]
        if not declared:
            lines += ["No method declaration was recognized by the conservative parser; inspect source manually.", ""]
            continue
        lines += ["| Method | Classification | Query / SQL (if statically available) |", "|---|---|---|"]
        for m in declared:
            query = m.query.replace("|", "\\|") or "—"
            lines.append(f"| {link(path, m.line)} {m.name}() | {m.kind} | {query} |")
        lines.append("")
    return "\n".join(lines)

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=ROOT / "src/main/java")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/audit/DATA_ACCESS_QUERY_CATALOG.md")
    parser.add_argument("--self-check", action="store_true", help="verify emitted names against source declarations and @Query counts")
    args = parser.parse_args()
    paths = sorted(p for p in args.source_root.rglob("*.java")
                   if p.name.endswith(SUFFIXES) or DIRECT_CLIENT_RE.search(p.read_text(encoding="utf-8", errors="replace")))
    records = [(p, *methods_in(p)) for p in paths]
    errors = source_self_check(records) if args.self_check else []
    if errors:
        print("DATA-ACCESS CATALOG SELF-CHECK FAILED:", *errors, sep="\n", file=sys.stderr)
        raise SystemExit(1)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(records, args.self_check), encoding="utf-8")
    counts = Counter(file_kind(p) for p in paths)
    print(f"Wrote {args.output} ({len(paths)} files, {sum(len(ms) for _, _, ms in records)} methods; " + ", ".join(f"{k}={v}" for k, v in sorted(counts.items())) + ("); source self-check passed" if args.self_check else ")"))

if __name__ == "__main__":
    main()
