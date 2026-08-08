#!/usr/bin/env python3
"""Fail when a production HTTP/framework/UI entry point lacks an audit anchor."""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from generate_system_audit_catalog import java_catalog


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src" / "main" / "java"
TEMPLATES = ROOT / "src" / "main" / "resources" / "templates"
STATIC_JS = ROOT / "src" / "main" / "resources" / "static" / "js"
AUDIT = ROOT / "docs" / "audit"
WORKFLOWS = AUDIT / "workflows"
OUT = AUDIT / "SEMANTIC_COVERAGE_GATE.md"
SCREEN_INDEX = AUDIT / "SCREEN_QUERY_AND_CLIENT_STATE_INDEX.md"

HANDLER = re.compile(r"`([A-Za-z_$][\w$]*)\.([A-Za-z_$][\w$]*)`")
SCREEN_METHOD = re.compile(
    r"`([A-Z][A-Za-z0-9_]*(?:Controller|Service|Repository|Resolver|Assembler|"
    r"Support|Builder|Queries|Access|Advice|Writer|Store))\."
    r"([A-Za-z_$][\w$]*)"
)
INHERITED_REPOSITORY_METHODS = {
    "count",
    "delete",
    "deleteAll",
    "deleteById",
    "existsById",
    "findAll",
    "findAllById",
    "findById",
    "flush",
    "getReferenceById",
    "save",
    "saveAll",
    "saveAndFlush",
}


@dataclass(frozen=True)
class RuntimeHook:
    kind: str
    anchor: str
    path: Path


ANNOTATED_HOOKS = (
    ("@Scheduled method", "Scheduled"),
    ("Async dispatch boundary", "Async"),
    ("Transaction event listener", "TransactionalEventListener"),
    ("Application event listener", "EventListener"),
    ("WebSocket message handler", "MessageMapping"),
    ("WebSocket subscription handler", "SubscribeMapping"),
    ("Kafka message listener", "KafkaListener"),
    ("RabbitMQ message listener", "RabbitListener"),
    ("JMS message listener", "JmsListener"),
    ("SQS message listener", "SqsListener"),
    ("Bean post-construction", "PostConstruct"),
    ("Bean pre-destruction", "PreDestroy"),
    ("JPA pre-persist callback", "PrePersist"),
    ("JPA pre-update callback", "PreUpdate"),
    ("JPA post-load callback", "PostLoad"),
    ("JPA pre-remove callback", "PreRemove"),
    ("JPA post-persist callback", "PostPersist"),
    ("JPA post-update callback", "PostUpdate"),
    ("JPA post-remove callback", "PostRemove"),
)


def documents() -> dict[Path, str]:
    return {
        path: path.read_text(encoding="utf-8", errors="replace")
        for path in sorted(WORKFLOWS.rglob("*.md"))
    }


def anchors(name: str, docs: dict[Path, str]) -> list[Path]:
    return [path for path, body in docs.items() if name in body]


def exact_handler_anchors(handler: str, docs: dict[Path, str]) -> list[Path]:
    """A route is covered only when its exact `Controller.method` is named."""
    return [path for path, body in docs.items() if handler in body]


def exact_runtime_hook_anchors(hook: RuntimeHook, docs: dict[Path, str]) -> list[Path]:
    """A framework hook is covered only when exact ``Class.method`` is named."""
    return [path for path, body in docs.items() if hook.anchor in body]


def controller_files() -> list[Path]:
    result = []
    for path in sorted(JAVA.rglob("*Controller.java")):
        body = path.read_text(encoding="utf-8", errors="replace")
        if re.search(r"@(RestController|Controller)\b", body):
            result.append(path)
    return result


def advice_files() -> list[Path]:
    result = []
    for path in sorted(JAVA.rglob("*.java")):
        body = path.read_text(encoding="utf-8", errors="replace")
        if re.search(r"@(RestControllerAdvice|ControllerAdvice)\b", body):
            result.append(path)
    return result


def background_files() -> tuple[list[Path], list[Path]]:
    scheduled: list[Path] = []
    lifecycle: list[Path] = []
    for path in sorted(JAVA.rglob("*.java")):
        body = path.read_text(encoding="utf-8", errors="replace")
        if "@Scheduled" in body:
            scheduled.append(path)
        if "implements SmartLifecycle" in body:
            lifecycle.append(path)
    return scheduled, lifecycle


def _skip_trivia(body: str, cursor: int) -> int:
    """Skip whitespace and Java comments from ``cursor``."""
    while cursor < len(body):
        whitespace = re.match(r"\s+", body[cursor:])
        if whitespace:
            cursor += whitespace.end()
            continue
        if body.startswith("//", cursor):
            newline = body.find("\n", cursor + 2)
            return len(body) if newline < 0 else _skip_trivia(body, newline + 1)
        if body.startswith("/*", cursor):
            end = body.find("*/", cursor + 2)
            return len(body) if end < 0 else _skip_trivia(body, end + 2)
        return cursor
    return cursor


def _skip_parenthesized(body: str, cursor: int) -> int:
    """Skip one balanced Java annotation argument list, including strings."""
    if cursor >= len(body) or body[cursor] != "(":
        return cursor
    depth = 0
    quote: str | None = None
    escaped = False
    while cursor < len(body):
        char = body[cursor]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char in ('"', "'"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return cursor + 1
        cursor += 1
    return len(body)


def _method_after_annotation(body: str, annotation_end: int) -> str | None:
    """Resolve the declaration immediately following a runtime annotation."""
    cursor = _skip_trivia(body, annotation_end)
    if cursor < len(body) and body[cursor] == "(":
        cursor = _skip_parenthesized(body, cursor)

    # A hook often also has @Transactional/@Override between its trigger
    # annotation and method declaration. Skip every adjacent annotation before
    # reading the declaration header.
    while True:
        cursor = _skip_trivia(body, cursor)
        annotation = re.match(r"@[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*", body[cursor:])
        if not annotation:
            break
        cursor += annotation.end()
        cursor = _skip_trivia(body, cursor)
        if cursor < len(body) and body[cursor] == "(":
            cursor = _skip_parenthesized(body, cursor)

    cursor = _skip_trivia(body, cursor)
    header_end = body.find("{", cursor)
    if header_end < 0 or header_end - cursor > 2000:
        return None
    header = re.sub(r"/\*.*?\*/|//[^\n]*", " ", body[cursor:header_end], flags=re.S)
    method = re.search(r"\b([A-Za-z_$][\w$]*)\s*\(", header)
    return method.group(1) if method else None


def _declared_classes(body: str, relationship: str, marker: str) -> list[str]:
    pattern = re.compile(
        rf"\bclass\s+([A-Za-z_$][\w$]*)\b[^{{;]*?\b{relationship}\b[^{{;]*?\b{marker}\b",
        re.S,
    )
    return [match.group(1) for match in pattern.finditer(body)]


def runtime_hooks() -> list[RuntimeHook]:
    """Inventory exact non-controller methods invoked by a runtime framework."""
    hooks: set[RuntimeHook] = set()
    for path in sorted(JAVA.rglob("*.java")):
        body = path.read_text(encoding="utf-8", errors="replace")

        for kind, annotation_name in ANNOTATED_HOOKS:
            for annotation in re.finditer(rf"@{annotation_name}\b", body):
                method = _method_after_annotation(body, annotation.end())
                if method:
                    hooks.add(RuntimeHook(kind, f"{path.stem}.{method}", path))

        for class_name in _declared_classes(body, "implements", "SmartLifecycle"):
            hooks.add(RuntimeHook("SmartLifecycle start", f"{class_name}.start", path))
            hooks.add(RuntimeHook("SmartLifecycle stop", f"{class_name}.stop", path))
        for class_name in _declared_classes(body, "implements", "InitializingBean"):
            hooks.add(RuntimeHook("InitializingBean", f"{class_name}.afterPropertiesSet", path))
        for interface in ("ApplicationRunner", "CommandLineRunner"):
            for class_name in _declared_classes(body, "implements", interface):
                hooks.add(RuntimeHook(interface, f"{class_name}.run", path))

        for class_name in _declared_classes(body, "implements", "UserDetailsService"):
            hooks.add(RuntimeHook(
                "Form-login principal loader",
                f"{class_name}.loadUserByUsername",
                path,
            ))
        for class_name in _declared_classes(body, "implements", "ClientRegistrationRepository"):
            hooks.add(RuntimeHook(
                "OAuth client-registration lookup",
                f"{class_name}.findByRegistrationId",
                path,
            ))
        for class_name in _declared_classes(body, "extends", "OidcUserService"):
            hooks.add(RuntimeHook("OIDC principal loader", f"{class_name}.loadUser", path))
        for class_name in _declared_classes(
            body, "implements", "WebSocketMessageBrokerConfigurer"
        ):
            hooks.add(RuntimeHook(
                "WebSocket endpoint bootstrap",
                f"{class_name}.registerStompEndpoints",
                path,
            ))
            hooks.add(RuntimeHook(
                "WebSocket broker bootstrap",
                f"{class_name}.configureMessageBroker",
                path,
            ))

        for class_name in _declared_classes(body, "extends", "OncePerRequestFilter"):
            hooks.add(RuntimeHook("Servlet request filter", f"{class_name}.doFilterInternal", path))
            if re.search(r"\bshouldNotFilter\s*\(", body):
                hooks.add(RuntimeHook("Servlet filter route guard", f"{class_name}.shouldNotFilter", path))

        for base in (
            "SimpleUrlAuthenticationSuccessHandler",
            "SavedRequestAwareAuthenticationSuccessHandler",
        ):
            for class_name in _declared_classes(body, "extends", base):
                hooks.add(RuntimeHook(
                    "Authentication success handler",
                    f"{class_name}.onAuthenticationSuccess",
                    path,
                ))

        # These @Bean methods either build the live security chain or return a
        # request/session callback consumed by Spring Security. Other ordinary
        # bean factories are source inventory, not independent workflow hooks.
        security_bean_types = (
            "AuthenticationFailureHandler",
            "AuthenticationSuccessHandler",
            "RequestCache",
            "SessionRegistry",
            "HttpSessionEventPublisher",
            "SecurityFilterChain",
        )
        for bean in re.finditer(r"@Bean\b", body):
            method = _method_after_annotation(body, bean.end())
            body_start = body.find("{", bean.end())
            if method and body_start >= 0:
                declaration = body[bean.end():body_start]
                if any(re.search(rf"\b{type_name}\b", declaration)
                       for type_name in security_bean_types):
                    hooks.add(RuntimeHook(
                        "Security runtime bean",
                        f"{path.stem}.{method}",
                        path,
                    ))

        if "@SpringBootApplication" in body and re.search(r"\bstatic\s+void\s+main\s*\(", body):
            hooks.add(RuntimeHook("Application bootstrap", f"{path.stem}.main", path))

    return sorted(hooks, key=lambda hook: (hook.kind, hook.anchor, hook.path.as_posix()))


def screen_files() -> list[Path]:
    """Return controller-rendered pages; reusable Thymeleaf fragments are not screens."""
    java_view_literals = "\n".join(
        path.read_text(encoding="utf-8", errors="replace")
        for path in JAVA.rglob("*.java")
    )
    result: list[Path] = []
    for path in sorted(TEMPLATES.rglob("*.html")):
        relative = path.relative_to(TEMPLATES).as_posix()
        if relative.startswith("fragments/") or "/fragments/" in relative:
            continue
        if relative == "assignments/fragments.html":
            continue
        # A direct MVC view name appears as a Java string literal, either in a
        # controller or a shared view/constants class. Files referenced only by
        # `th:replace` are presentation fragments even when they live outside a
        # directory named `fragments` (for example Practice result panels).
        view_name = relative.removesuffix(".html")
        if f'"{view_name}"' not in java_view_literals:
            continue
        result.append(path)
    return result


def browser_workflow_files() -> list[Path]:
    return sorted(STATIC_JS.rglob("*.js"))


def source_anchor_names(path: Path) -> tuple[str, ...]:
    """Accept an exact resource path or filename, never a coincidental stem."""
    relative = path.relative_to(ROOT / "src" / "main" / "resources").as_posix()
    return relative, path.name


def resource_anchors(path: Path, docs: dict[Path, str]) -> list[Path]:
    names = source_anchor_names(path)
    return [doc for doc, body in docs.items() if any(name in body for name in names)]


def screen_method_references(body: str) -> list[tuple[str, str]]:
    """Exact ``Class.method`` references used by the central screen-query index."""
    return sorted(set(SCREEN_METHOD.findall(body)))


def missing_screen_method_references(
        references: list[tuple[str, str]],
) -> list[str]:
    """Reject stale class/method names while allowing inherited Spring Data CRUD."""
    sources = {
        path.stem: path.read_text(encoding="utf-8", errors="replace")
        for path in JAVA.rglob("*.java")
    }
    missing: list[str] = []
    for owner, method in references:
        source = sources.get(owner)
        if source is None:
            missing.append(f"{owner}.{method}")
            continue
        if re.search(rf"\b{re.escape(method)}\s*\(", source):
            continue
        if (owner.endswith("Repository")
                and method in INHERITED_REPOSITORY_METHODS
                and re.search(r"\bextends\s+[A-Za-z_$][\w$]*(?:Repository)", source)):
            continue
        missing.append(f"{owner}.{method}")
    return missing


def md_link(path: Path) -> str:
    relative = path.relative_to(AUDIT).as_posix()
    return f"[`{relative}`]({relative})"


def source_link(path: Path) -> str:
    relative = path.relative_to(ROOT).as_posix()
    return f"[`{relative}`](../../{relative})"


def anchor_cell(found: list[Path]) -> str:
    if not found:
        return "**MISSING**"
    return "<br>".join(md_link(path) for path in found[:3])


def main() -> int:
    docs = documents()
    _, route_rows = java_catalog()
    route_counts: Counter[str] = Counter()
    handlers: list[str] = []
    for row in route_rows:
        found = HANDLER.search(row)
        if found:
            route_counts[found.group(1)] += 1
            handlers.append(f"{found.group(1)}.{found.group(2)}")

    controllers = controller_files()
    advice = advice_files()
    scheduled, lifecycle = background_files()
    hooks = runtime_hooks()
    screens = screen_files()
    browser_workflows = browser_workflow_files()
    screen_index_body = SCREEN_INDEX.read_text(encoding="utf-8", errors="replace")
    screen_methods = screen_method_references(screen_index_body)

    missing_controllers = [p for p in controllers if not anchors(p.stem, docs)]
    missing_advice = [p for p in advice if not anchors(p.stem, docs)]
    missing_scheduled = [p for p in scheduled if not anchors(p.stem, docs)]
    missing_lifecycle = [p for p in lifecycle if not anchors(p.stem, docs)]
    missing_handlers = [h for h in handlers if not exact_handler_anchors(h, docs)]
    missing_hooks = [h for h in hooks if not exact_runtime_hook_anchors(h, docs)]
    missing_screens = [p for p in screens if not resource_anchors(p, docs)]
    missing_screen_index = [
        path for path in screens
        if not any(name in screen_index_body for name in source_anchor_names(path))
    ]
    missing_screen_methods = missing_screen_method_references(screen_methods)
    missing_browser_workflows = [
        p for p in browser_workflows if not resource_anchors(p, docs)
    ]

    controller_route_total = sum(route_counts[p.stem] for p in controllers)
    anchored_route_total = len(handlers) - len(missing_handlers)
    jpa_hooks = [hook for hook in hooks if hook.kind.startswith("JPA ")]
    missing_jpa_hooks = [hook for hook in missing_hooks if hook.kind.startswith("JPA ")]

    lines = [
        "# KSH semantic workflow coverage gate",
        "",
        "Gate này được sinh bởi `scripts/docs/check_workflow_audit_coverage.py`. "
        "Nó chặn việc tuyên bố audit hoàn tất nếu một controller, controller advice, "
        "framework runtime hook, entity lifecycle callback, page template hoặc browser "
        "JavaScript không được ít nhất một walkthrough semantic gọi đúng tên. Với HTTP "
        "handler và runtime hook, gate yêu cầu exact `Class.method`, không chỉ tên class. "
        "Gate còn kiểm mọi page template và browser JavaScript có "
        "walkthrough gọi đúng tên file; mọi `Class.method` trong screen-query index phải "
        "rematch source (Spring Data CRUD kế thừa được nhận diện); fragment thuần trình bày "
        "được loại khỏi inventory màn hình. "
        "Catalog endpoint/UI/source đầy đủ vẫn nằm ở các catalog riêng.",
        "",
        "> Gate là bằng chứng **entry point đã được gắn vào tài liệu**, không tự chứng minh "
        "mọi câu prose đúng. Việc đúng method/line/request/response vẫn được review trong "
        "từng walkthrough.",
        "",
        "## Kết quả",
        "",
        "| Inventory | Anchored | Total | Missing |",
        "|---|---:|---:|---:|",
        f"| Controller classes | {len(controllers) - len(missing_controllers)} | {len(controllers)} | {len(missing_controllers)} |",
        f"| Handler mappings có exact `Controller.method` anchor | {anchored_route_total} | {controller_route_total} | {controller_route_total - anchored_route_total} |",
        f"| Controller advice classes | {len(advice) - len(missing_advice)} | {len(advice)} | {len(missing_advice)} |",
        f"| Classes chứa `@Scheduled` | {len(scheduled) - len(missing_scheduled)} | {len(scheduled)} | {len(missing_scheduled)} |",
        f"| `SmartLifecycle` workers | {len(lifecycle) - len(missing_lifecycle)} | {len(lifecycle)} | {len(missing_lifecycle)} |",
        f"| Exact non-controller runtime hooks | {len(hooks) - len(missing_hooks)} | {len(hooks)} | {len(missing_hooks)} |",
        f"| Trong đó: JPA entity callbacks | {len(jpa_hooks) - len(missing_jpa_hooks)} | {len(jpa_hooks)} | {len(missing_jpa_hooks)} |",
        f"| Rendered page templates | {len(screens) - len(missing_screens)} | {len(screens)} | {len(missing_screens)} |",
        f"| Screen initial-query/client-state index | {len(screens) - len(missing_screen_index)} | {len(screens)} | {len(missing_screen_index)} |",
        f"| Screen-index exact source `Class.method` refs | {len(screen_methods) - len(missing_screen_methods)} | {len(screen_methods)} | {len(missing_screen_methods)} |",
        f"| Browser JavaScript files | {len(browser_workflows) - len(missing_browser_workflows)} | {len(browser_workflows)} | {len(missing_browser_workflows)} |",
        "",
        "## Controller → walkthrough",
        "",
        "| Controller | Mappings | Source | Semantic anchor |",
        "|---|---:|---|---|",
    ]
    for path in controllers:
        found = anchors(path.stem, docs)
        lines.append(
            f"| `{path.stem}` | {route_counts[path.stem]} | {source_link(path)} | {anchor_cell(found)} |"
        )

    lines.extend([
        "",
        "## Exact handler method → walkthrough",
        "",
        "| Handler | Semantic anchor |",
        "|---|---|",
    ])
    for handler in handlers:
        lines.append(
            f"| `{handler}` | {anchor_cell(exact_handler_anchors(handler, docs))} |"
        )

    lines.extend([
        "",
        "## Exact non-controller runtime hook → walkthrough",
        "",
        "Inventory này gồm bootstrap, scheduler method, event listener, bean/JVM lifecycle, "
        "request security hook và JPA persistence callback. JPA callback chỉ chạy khi "
        "entity được persist/update/flush; nó không phải autonomous background worker.",
        "",
        "| Kind | Hook | Source | Semantic anchor |",
        "|---|---|---|---|",
    ])
    for hook in hooks:
        lines.append(
            f"| {hook.kind} | `{hook.anchor}` | {source_link(hook.path)} | "
            f"{anchor_cell(exact_runtime_hook_anchors(hook, docs))} |"
        )

    lines.extend([
        "",
        "## Advice và background runtime → walkthrough",
        "",
        "| Kind | Class | Source | Semantic anchor |",
        "|---|---|---|---|",
    ])
    seen: set[tuple[str, Path]] = set()
    for kind, paths in (
        ("Controller advice", advice),
        ("@Scheduled", scheduled),
        ("SmartLifecycle", lifecycle),
    ):
        for path in paths:
            key = (kind, path)
            if key in seen:
                continue
            seen.add(key)
            lines.append(
                f"| {kind} | `{path.stem}` | {source_link(path)} | "
                f"{anchor_cell(anchors(path.stem, docs))} |"
            )

    lines.extend([
        "",
        "## Màn hình và browser workflow → walkthrough",
        "",
        "| Kind | Resource | Semantic anchor |",
        "|---|---|---|",
    ])
    for kind, paths in (("Screen", screens), ("Browser JS", browser_workflows)):
        for path in paths:
            relative = path.relative_to(ROOT).as_posix()
            lines.append(
                f"| {kind} | [`{relative}`](../../{relative}) | "
                f"{anchor_cell(resource_anchors(path, docs))} |"
            )

    missing_paths = (
        missing_controllers + missing_advice + missing_scheduled + missing_lifecycle
        + missing_screens + missing_screen_index + missing_browser_workflows
    )
    missing_labels = (
        [path.stem for path in missing_paths]
        + missing_handlers
        + [hook.anchor for hook in missing_hooks]
        + missing_screen_methods
    )
    lines.extend([
        "",
        "## Trạng thái gate",
        "",
        "**PASS** — không còn entry point runtime chưa có semantic anchor."
        if not missing_labels
        else "**FAIL** — còn entry point chưa có semantic anchor: "
             + ", ".join(f"`{label}`" for label in missing_labels),
        "",
    ])
    OUT.write_text("\n".join(lines), encoding="utf-8")
    return 1 if missing_labels else 0


if __name__ == "__main__":
    raise SystemExit(main())
