#!/usr/bin/env python3
"""Generate the static runtime-configuration inventory for this repository.

No Spring context is started.  The catalog intentionally reports what can be
proven from committed ``application*.properties|yml`` files and Java source:
property declarations, placeholders, configuration binding prefixes, condition
switches, and scheduler placeholders.  It is a documentation aid, not an
effective-environment dump (profiles, Config Server and environment precedence
remain Spring runtime concerns).
"""

from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "docs/audit/RUNTIME_CONFIGURATION_CATALOG.md"
CONFIG_SUFFIXES = (".properties", ".yml", ".yaml")
SENSITIVE_LEAF = re.compile(
    r"(?:password|secret|api[._-]?key|access[._-]?key|credential|token)",
    re.I,
)
PLACEHOLDER_START = "${"


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def md(value: str) -> str:
    return value.replace("|", "\\|").replace("`", "'").replace("\n", " ").strip()


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def kebab(name: str) -> str:
    return re.sub(r"(?<!^)([A-Z])", r"-\1", name).replace("_", "-").lower()


def sensitive_key(key: str) -> bool:
    """Redact credential values, not unrelated prefixes such as password-reset."""
    leaf = key.rsplit(".", 1)[-1]
    return bool(SENSITIVE_LEAF.search(leaf))


def collapse_java_string_concatenation(text: str) -> str:
    """Join adjacent Java string literals while preserving newline positions.

    Configuration annotations occasionally split one placeholder as
    ``"${prefix." + "suffix:default}"``. Replacing only the quote/plus bridge
    with its newline characters keeps line-number calculations stable and
    exposes the effective placeholder key to the static scanner.
    """
    bridge = re.compile(r'"\s*\+\s*"')
    while bridge.search(text):
        text = bridge.sub(lambda match: "\n" * match.group(0).count("\n"), text)
    return text


def placeholders(text: str):
    """Return (key, default) pairs, including nested defaults, without regex truncation."""
    found = []
    index = 0
    while True:
        start = text.find(PLACEHOLDER_START, index)
        if start < 0:
            break
        cursor, depth = start + 2, 1
        while cursor < len(text) and depth:
            if text.startswith(PLACEHOLDER_START, cursor):
                depth += 1
                cursor += 2
                continue
            if text[cursor] == "}":
                depth -= 1
            cursor += 1
        if depth:
            break
        body = text[start + 2 : cursor - 1]
        split, nesting = None, 0
        for position, char in enumerate(body):
            if body.startswith(PLACEHOLDER_START, position):
                nesting += 1
            elif char == "}" and nesting:
                nesting -= 1
            elif char == ":" and nesting == 0:
                split = position
                break
        key = body if split is None else body[:split]
        default = "" if split is None else body[split + 1 :]
        # Whitespace can be introduced only by Java source formatting around a
        # collapsed adjacent-string bridge; Spring property keys themselves do
        # not contain it.
        key = re.sub(r"\s+", "", key)
        if key:
            found.append((key.strip(), default.strip()))
        if default:
            found.extend(placeholders(default))
        index = cursor
    return found


def config_files():
    files = []
    for base in (ROOT / "src/main/resources", ROOT / "src/test/resources"):
        if not base.exists():
            continue
        files.extend(sorted(path for path in base.rglob("application*") if path.suffix in CONFIG_SUFFIXES))
    return files


def properties(path: Path):
    records = []
    logical, first_line = "", 0
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if logical:
            logical += raw.lstrip()
        else:
            logical, first_line = raw, number
        if logical.endswith("\\") and not logical.endswith("\\\\"):
            logical = logical[:-1]
            continue
        stripped = logical.strip()
        logical = ""
        if not stripped or stripped.startswith(("#", "!")):
            continue
        match = re.match(r"\s*([^:=\s]+)\s*[:=]\s*(.*)", stripped)
        if match:
            records.append((match.group(1), match.group(2), first_line))
    return records


def simple_yaml(path: Path):
    """Small scalar YAML reader; complex YAML is reported by the limitation note."""
    records, parents = [], []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#") or raw.lstrip().startswith("-"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        match = re.match(r"\s*([^:#][^:]*):\s*(.*?)\s*(?:#.*)?$", raw)
        if not match:
            continue
        key, value = match.group(1).strip().strip('"\''), match.group(2)
        while parents and indent <= parents[-1][0]:
            parents.pop()
        full = ".".join([name for _, name in parents] + [key])
        if value:
            records.append((full, value.strip().strip('"\''), number))
        else:
            parents.append((indent, key))
    return records


def display_config_value(key: str, raw: str, source: str) -> str:
    if Path(source).name == "application-local.properties":
        return "redacted (local override file)"
    if sensitive_key(key):
        return "redacted (sensitive key)"
    refs = placeholders(raw)
    if len(refs) == 1 and raw.strip().startswith("${") and raw.strip().endswith("}"):
        env, fallback = refs[0]
        return "env `{}`; fallback `{}`".format(env, fallback or "(empty)")
    return "literal `{}`".format(raw or "(empty)")


def display_consumer_fallback(key: str, fallback: str) -> str:
    if not fallback:
        return ""
    if sensitive_key(key):
        return "; fallback redacted (sensitive key)"
    return f"; Java fallback `{fallback}`"


def enclosing_class(text: str, offset: int) -> str:
    before = text[:offset]
    matches = list(re.finditer(r"\b(?:class|interface|record|enum)\s+(\w+)", before))
    return matches[-1].group(1) if matches else "(top-level)"


def owner_for_offset(text: str, offset: int) -> str:
    """Annotations normally precede a class, so look forward when needed."""
    owner = enclosing_class(text, offset)
    if owner != "(top-level)":
        return owner
    upcoming = re.search(r"\b(?:class|interface|record|enum)\s+(\w+)", text[offset : offset + 800])
    return upcoming.group(1) if upcoming else owner


def in_java_comment(text: str, offset: int) -> bool:
    """Conservative single-line/block comment filter for annotation scanning."""
    line_start = text.rfind("\n", 0, offset) + 1
    line = text[line_start:offset]
    if "//" in line:
        return True
    return text.rfind("/*", 0, offset) > text.rfind("*/", 0, offset)


def scheduled_method(text: str, annotation_end: int) -> str:
    tail = text[annotation_end : annotation_end + 600]
    match = re.search(r"\b(?:public|protected|private)?\s*(?:static\s+)?[\w<>?, \[\]]+\s+(\w+)\s*\(", tail)
    return match.group(1) if match else "(scheduled method)"


def java_sources():
    return sorted((ROOT / "src/main/java").rglob("*.java"))


def scan_java(path: Path, consumers, prefixes):
    text = collapse_java_string_concatenation(path.read_text(encoding="utf-8"))
    source = rel(path)
    # Production Java follows one primary top-level type per file. Using the
    # filename is more reliable than searching arbitrary preceding prose,
    # where Javadocs may contain words such as "class" or "interface".
    owner_name = path.stem

    # Every placeholder is indexed, so @Value and @Scheduled are covered even
    # when annotations span lines.  The surrounding annotation decides label.
    for match in re.finditer(r"\$\{", text):
        start = match.start()
        if in_java_comment(text, start):
            continue
        values = placeholders(text[start : start + 1000])
        if not values:
            continue
        key, default = values[0]
        line = line_number(text, start)
        nearby = text[max(0, start - 260) : start]
        owner = owner_name
        if "@Scheduled" in nearby:
            consumer = f"{owner}.{scheduled_method(text, start)} (@Scheduled)"
        elif "@Value" in nearby:
            consumer = f"{owner} (@Value)"
        else:
            consumer = f"{owner} (placeholder)"
        consumers[key].append((source, line, default, consumer))

    for match in re.finditer(r"@ConfigurationProperties\s*\((.*?)\)", text, re.S):
        argument = match.group(1)
        prefix_match = re.search(r"\bprefix\s*=\s*\"([^\"]+)\"", argument)
        if not prefix_match:
            prefix_match = re.search(r"\"([^\"]+)\"", argument)
        if prefix_match:
            prefix = prefix_match.group(1)
            prefixes.append((prefix, source, line_number(text, match.start()), owner_name))

    for match in re.finditer(r"@ConditionalOnProperty\s*\((.*?)\)", text, re.S):
        argument = match.group(1)
        prefix_match = re.search(r"\bprefix\s*=\s*\"([^\"]+)\"", argument)
        names = re.findall(r"\bname\s*=\s*(?:\{\s*)?\"([^\"]+)\"", argument)
        if not names:
            continue
        prefix = prefix_match.group(1) if prefix_match else ""
        having = re.search(r"\bhavingValue\s*=\s*\"([^\"]*)\"", argument)
        missing = re.search(r"\bmatchIfMissing\s*=\s*(true|false)", argument)
        detail = "@ConditionalOnProperty"
        if having:
            detail += f" havingValue={having.group(1)}"
        if missing:
            detail += f" matchIfMissing={missing.group(1)}"
        for name in names:
            key = f"{prefix}.{name}" if prefix else name
            consumers[key].append((source, line_number(text, match.start()), "", f"{owner_name} ({detail})"))


def build_catalog():
    config = defaultdict(list)
    for path in config_files():
        records = properties(path) if path.suffix == ".properties" else simple_yaml(path)
        for key, value, line in records:
            config[key].append((rel(path), line, value))

    consumers, prefixes = defaultdict(list), []
    # Configuration files can consume other properties too (for example
    # app.upload.dir -> app.data.dir), even though no Java annotation names the
    # inner key.  Index that static expansion before scanning Java consumers.
    for configured_key, declarations in config.items():
        for source, line, raw in declarations:
            for referenced_key, fallback in placeholders(raw):
                consumers[referenced_key].append((
                    source, line, fallback,
                    f"configuration expansion for `{configured_key}`"))
    for path in java_sources():
        scan_java(path, consumers, prefixes)

    # A configuration binding prefix is a consumer of all declared descendants;
    # preserve an explicit ``prefix.*`` row when no source file declares one.
    for prefix, source, line, owner in prefixes:
        matched = [key for key in config if key == prefix or key.startswith(prefix + ".")]
        if not matched:
            matched = [prefix + ".*"]
        for key in matched:
            consumers[key].append((source, line, "", f"{owner} (@ConfigurationProperties prefix `{prefix}`)"))

    keys = sorted(set(config) | set(consumers))
    rows = []
    for key in keys:
        config_refs = config.get(key, [])
        declarations = "<br>".join(
            f"`{source}:{line}` — {display_config_value(key, raw, source)}"
            for source, line, raw in config_refs
        ) or "—"
        seen, consumer_refs = set(), []
        for source, line, default, consumer in consumers.get(key, []):
            item = (source, line, default, consumer)
            if item in seen:
                continue
            seen.add(item)
            suffix = display_consumer_fallback(key, default)
            consumer_refs.append(f"`{source}:{line}` — {md(consumer)}{suffix}")
        uses = "<br>".join(consumer_refs) or "—"
        rows.append((key, declarations, uses))
    return rows, config_files(), prefixes


def self_check(rows, files):
    """Check that emitted keys are sane and every static declaration is present."""
    errors = []
    keys = [row[0] for row in rows]
    key_set = set(keys)
    if len(keys) != len(key_set):
        errors.append("duplicate key rows were emitted")
    for key in keys:
        if not key or re.search(r"\s|[\"']\s*\+\s*[\"']", key):
            errors.append(f"malformed property key: {key!r}")
    for path in files:
        records = properties(path) if path.suffix == ".properties" else simple_yaml(path)
        for key, _, line in records:
            if key not in key_set:
                errors.append(f"missing configured key {key!r} from {rel(path)}:{line}")
    for path in java_sources():
        text = collapse_java_string_concatenation(path.read_text(encoding="utf-8"))
        for match in re.finditer(r"\$\{", text):
            if in_java_comment(text, match.start()):
                continue
            found = placeholders(text[match.start():match.start() + 1000])
            if found and found[0][0] not in key_set:
                errors.append(
                    f"missing Java placeholder {found[0][0]!r} from "
                    f"{rel(path)}:{line_number(text, match.start())}"
                )
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    rows, files, prefixes = build_catalog()
    errors = self_check(rows, files) if args.self_check else []
    if errors:
        raise SystemExit("RUNTIME CONFIG CATALOG SELF-CHECK FAILED:\n" + "\n".join(errors))
    lines = [
        "# Runtime configuration catalog",
        "",
        "Generated by `scripts/docs/generate_runtime_config_catalog.py`; do not hand-edit the table.",
        "",
        "## Scope and method",
        "",
        "The generator statically reads every `application*.properties`, `.yml`, and `.yaml` under `src/main/resources` and `src/test/resources`, plus every Java file under `src/main/java`. It records property declarations, `${...}` placeholders, `@Value`, `@ConfigurationProperties`, `@ConditionalOnProperty`, and property-based `@Scheduled` values. A configuration-properties prefix is mapped to each declared descendant key; if none is declared, it appears as `prefix.*`.",
        "",
        "All values from `application-local.properties`, and values of keys whose names look secret-bearing (`password`, `secret`, `api-key`, access key, credential, token), are redacted. `env X; fallback Y` is parsed from a direct `${X:Y}` declaration; it is not evidence that the environment currently has X.",
        "",
        "**Source self-check:** " + (
            "passed (declared keys and Java placeholders are present; emitted keys contain no whitespace/string-concatenation artifacts)."
            if args.self_check else
            "not requested; run with `--self-check` before relying on changed output."
        ),
        "",
        "## Limitations",
        "",
        "This is static source inventory, not Spring's resolved `Environment`. It cannot prove profile activation, precedence, imported/remote config, command-line overrides, dynamic database settings, reflection, arbitrary `Environment.getProperty` construction, or values passed by deployment tooling. YAML parsing intentionally supports scalar nested mappings only; lists, anchors, multiline scalars and advanced YAML syntax need manual review. Consumer method names are emitted only when statically unambiguous (notably scheduled methods); other uses are class-level.",
        "",
        f"Scanned configuration files: {', '.join(f'`{rel(path)}`' for path in files)}.",
        "",
        "## Key inventory",
        "",
        "| Key / prefix | Declaration, environment fallback, source | Static Java consumer |",
        "|---|---|---|",
    ]
    for key, declaration, consumer in rows:
        lines.append(f"| `{md(key)}` | {declaration} | {consumer} |")
    lines.extend([
        "",
        f"Generated {len(rows)} key/prefix rows from {len(files)} config files and {len(prefixes)} `@ConfigurationProperties` declarations.",
        "",
    ])
    target = args.output if args.output.is_absolute() else ROOT / args.output
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(lines), encoding="utf-8")
    suffix = "; source self-check passed" if args.self_check else ""
    print(f"wrote {target.relative_to(ROOT)} ({len(rows)} rows{suffix})")


if __name__ == "__main__":
    main()
