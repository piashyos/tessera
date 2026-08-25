#!/usr/bin/env python3
"""graph_guard.py — graph-first code navigation for Claude Code hooks (notes/Tessera).

Ported from the WinScore project (meta-b2b/WinScore/.claude/hooks/graph_guard.py)
and adapted: TS/JS extensions → Kotlin/Java, and the built-in Grep tool is denied
outright (full ban) rather than only when scoped to source files.

Subcommands wired from .claude/settings.json:

  bash-guard     PreToolUse(Bash)      — deny grep/regex search over .kt/.java;
                                          deny tree / ls -R / git log -S|-G;
                                          soft-nudge general grep/find/rg over other text.
  grep-guard     PreToolUse(Grep|Glob) — Grep tool: DENIED always; Glob: soft nudge.
  session-start  SessionStart          — inject navigation context + a CBM index
                                          health line (.kt-file coverage vs disk).

Design notes
------------
* Pure stdlib, no third-party deps.
* "Fail open": any internal error prints nothing and exits 0.
* Enforcement is CONDITIONAL on the replacement existing: the deny only fires when
  the codebase-memory-mcp binary is on PATH *and* registered as an MCP server.
  Otherwise the guard steps aside with setup instructions.

ESCAPE HATCH: prefix a Bash command with `CBM_BYPASS=1` to run a denied search anyway.
For when codebase-memory genuinely came up empty (brand-new unindexed file, raw
string literal, stale index). Deliberate and visible in the transcript.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys

# ── shared copy ──────────────────────────────────────────────────────────────

KT_DENY_MSG = (
    "⛔ grep/regex/Grep search over Kotlin/Java source is DENIED in this project. "
    "Text search finds the string, not the symbol — it can't distinguish a "
    "definition from an override, or an import from a call site. Use "
    "codebase-memory-mcp instead:\n"
    "   • search_code — any symbol, usage or concept → gives you file:line\n"
    "   • search_graph — find functions/classes by name pattern\n"
    "   • query_graph / trace_path — callers, callees, import chains, blast radius\n"
    "   • get_architecture — module/package layout instead of `tree` or `ls -R`\n"
    "   • get_code_snippet — read the body once you have the position\n"
    "Text search over NON-code files (XML, YAML, JSON, Gradle, properties, logs) "
    "is fine.\n"
    "If codebase-memory genuinely came up empty (new unindexed file, raw string "
    "literal, stale index), re-run the command prefixed with CBM_BYPASS=1, or "
    "refresh the index with detect_changes / index_repository first."
)

# Full protocol reminder, attached as additionalContext on the soft nudge.
NUDGE_MSG = (
    "⚠️ Если это навигация по КОДУ (символы, вызовы, импорты, blast radius) — "
    "начни с codebase-memory-mcp (search_code / search_graph / query_graph / "
    "trace_path / get_architecture), чтобы найти символ и его file:line, а не с "
    "текстового поиска. Для текста/конфигов/логов/XML/Gradle grep/find/Glob "
    "уместен — можно продолжать."
)

# Shown instead of KT_DENY_MSG when codebase-memory is not actually usable.
CBM_MISSING_MSG = (
    "⚠️ code search via grep is normally DENIED here in favour of "
    "codebase-memory-mcp, but {reason} — so this search is ALLOWED.\n"
    "   Install the binary to ~/.local/bin, then register the MCP server:\n"
    "     claude mcp add codebase-memory-mcp -- codebase-memory-mcp\n"
    "   Until then text search is all there is."
)

SESSION_CTX = (
    "CODE NAVIGATION (notes/Tessera) — codebase-memory-mcp is the entry point.\n"
    "Use search_code / search_graph / query_graph / trace_path / get_architecture / "
    "get_code_snippet to locate a symbol and its file:line.\n"
    "The graph is an index, not a compiler: it can miss a brand-new file or lag a "
    "just-made edit — refresh with detect_changes after edits, and confirm anything "
    "load-bearing by reading the file at the file:line it gave you.\n"
    "The built-in Grep tool is DENIED entirely. Bash grep/rg/find over "
    "*.kt/*.java is DENIED (escape hatch: prefix the Bash command with "
    "CBM_BYPASS=1). grep over non-code text (XML, Gradle, YAML, logs) is allowed "
    "with a soft nudge. DISABLED: tree, ls -R, git log -S/-G."
)

# ── stdout helpers (Claude Code PreToolUse protocol) ─────────────────────────


def _emit(obj: dict) -> None:
    sys.stdout.write(json.dumps(obj, ensure_ascii=False))


def deny(reason: str) -> None:
    _emit({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }})
    sys.exit(0)


def nudge(context: str) -> None:
    _emit({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "additionalContext": context,
    }})
    sys.exit(0)


def _read_stdin_json() -> dict:
    try:
        raw = sys.stdin.read()
        return json.loads(raw) if raw.strip() else {}
    except Exception:
        return {}


# ── Kotlin/Java-target detection ─────────────────────────────────────────────

# The extensions that belong to codebase-memory rather than to grep.
# .kts (Gradle build scripts) is deliberately excluded — that's config.
_KT_EXTS = "kt|java"

_KT_TARGET_PATTERNS = [
    # a path/glob token ending in .kt/.java, preceded by a separator and
    # followed by a word boundary — matches `x.kt`, `app/src/A.java`,
    # `-name '*.kt'`; NOT `.kts`, NOT `foo.json`.
    re.compile(r"[\s=/][^\s'\"]*\.(?:%s)(?=[\s:'\"]|$)" % _KT_EXTS),
    # grep file-type include: --include=*.kt, --include='*.java'
    re.compile(r"--include=['\"]?\*?\.?(?:%s)\b" % _KT_EXTS),
    # ripgrep glob: -g '*.kt', --glob '*.java'
    re.compile(r"(?:-g|--glob)[\s=]['\"]?\*?\.?(?:%s)\b" % _KT_EXTS),
    # ripgrep type filter: --type kotlin, -t java
    re.compile(r"(?:--type[\s=]|(?<![\w-])-t\s?)(?:kotlin|kt|java)\b"),
    # find -name/-iname/-path '*.kt'
    re.compile(r"-(?:i?name|i?path)\s+['\"]?\*?\.?(?:%s)\b" % _KT_EXTS),
]

_SEARCH_CMD = re.compile(
    r"(?:^|[\s|&;(])(?:grep|egrep|fgrep|rg|ag|ack|fd|find)(?:\s|$)"
)

# Deliberate override, documented in KT_DENY_MSG. Must lead the command so it reads
# as an explicit decision in the transcript, not something buried in a pipeline.
_BYPASS = re.compile(r"^\s*CBM_BYPASS=1\s+")


def cmd_targets_kt(cmd: str) -> bool:
    return any(p.search(cmd) for p in _KT_TARGET_PATTERNS)


def is_search_cmd(cmd: str) -> bool:
    return bool(_SEARCH_CMD.search(cmd))


# Other denials: recursive listings and pickaxe search, all better served by the graph.
_OTHER_DENIES = [
    (re.compile(r"git\s+log\s+(?:[^&|;]*\s)?-[SG]\b"),
     "git log -S/-G (pickaxe code search) is disabled. Use codebase-memory-mcp "
     "search_code to find the symbol, then `git log -- <file>` on the file it names."),
    (re.compile(r"(?:^|[|&;(]\s*)tree(?:\s|$)"),
     "tree (recursive listing) is disabled. Use codebase-memory-mcp get_architecture."),
    (re.compile(r"(?:^|[|&;(]\s*)ls\s+[^|&;]*-[a-zA-Z]*R"),
     "ls -R (recursive listing) is disabled. Use codebase-memory-mcp get_architecture."),
]


def _deny_or_explain(msg: str) -> None:
    """Deny — but only if codebase-memory can replace the search."""
    reason = _cbm_unavailable_reason(_repo())
    if reason is None:
        deny(msg)
    nudge(CBM_MISSING_MSG.format(reason=reason))


# ── subcommand: bash-guard ───────────────────────────────────────────────────


def run_bash_guard() -> None:
    data = _read_stdin_json()
    cmd = (data.get("tool_input") or {}).get("command", "") or ""
    if not cmd:
        return

    if _BYPASS.match(cmd):
        return

    if is_search_cmd(cmd):
        if cmd_targets_kt(cmd):
            _deny_or_explain(KT_DENY_MSG)
        nudge(NUDGE_MSG)

    for pat, reason in _OTHER_DENIES:
        if pat.search(cmd):
            deny(reason)
    # falls through → no output → command allowed


# ── subcommand: grep-guard (built-in Grep/Glob tool) ─────────────────────────


def run_grep_guard() -> None:
    data = _read_stdin_json()
    tool = str(data.get("tool_name") or "")
    if tool == "Grep":
        # Full ban: the Grep tool is always denied in favour of codebase-memory.
        _deny_or_explain(KT_DENY_MSG)
    # Glob (file listing by pattern) stays allowed with a soft nudge.
    nudge(NUDGE_MSG)


# ── CBM availability (shared by the guards and session-start) ────────────────


def _which(binary: str) -> str | None:
    # The installer puts the binary in ~/.local/bin, which is not always on the PATH
    # of the process that spawns the hook — check it explicitly as a fallback.
    dirs = os.environ.get("PATH", "").split(os.pathsep)
    dirs.append(os.path.join(os.path.expanduser("~"), ".local", "bin"))
    for d in dirs:
        p = os.path.join(d, binary)
        if os.path.isfile(p) and os.access(p, os.X_OK):
            return p
    return None


_MCP_CONFIG_MAX_BYTES = 4 * 1024 * 1024


def _mcp_registered(repo: str) -> bool:
    """Is codebase-memory wired up as an MCP server for this project?"""
    candidates = (
        os.path.join(repo, ".mcp.json"),
        os.path.join(os.path.expanduser("~"), ".claude.json"),
    )
    for path in candidates:
        try:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                if "codebase-memory" in fh.read(_MCP_CONFIG_MAX_BYTES):
                    return True
        except Exception:
            continue
    return False


def _cbm_unavailable_reason(repo: str) -> str | None:
    """None when codebase-memory is usable, else why it is not."""
    if not _which("codebase-memory-mcp"):
        return "the codebase-memory-mcp binary is not on PATH"
    if not _mcp_registered(repo):
        return (
            "codebase-memory-mcp is installed but not registered as an MCP "
            "server, so its search tools are not available in this session"
        )
    return None


def _repo() -> str:
    return os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()


# ── subcommand: session-start ────────────────────────────────────────────────


def _project_name(repo: str) -> str:
    # codebase-memory-mcp derives the project name from the abs repo path:
    # leading slash dropped, remaining slashes → dashes.
    return repo.lstrip("/").replace("/", "-")


def _cache_dir() -> str:
    base = os.environ.get("XDG_CACHE_HOME") or os.path.join(
        os.path.expanduser("~"), ".cache")
    return os.path.join(base, "codebase-memory-mcp")


def _disk_kt_count(repo: str) -> int:
    """Tracked Kotlin/Java files. `git ls-files` so build output never counts."""
    try:
        out = subprocess.run(
            ["git", "-C", repo, "ls-files", "*.kt", "*.java"],
            capture_output=True, text=True, timeout=20,
        ).stdout
        return sum(1 for line in out.splitlines() if line.strip())
    except Exception:
        return 0


def _graph_kt_count(binary: str, project: str) -> int | None:
    """distinct .kt/.java file_paths in the graph, via the standalone CLI."""
    try:
        out = subprocess.run(
            [binary, "cli", "query_graph", "--project", project, "--query",
             "MATCH (n) WHERE n.file_path ENDS WITH '.kt' OR n.file_path ENDS WITH '.java' "
             "RETURN count(DISTINCT n.file_path) AS c"],
            capture_output=True, text=True, timeout=20,
        ).stdout
        for line in reversed(out.splitlines()):
            line = line.strip()
            if line.startswith("{"):
                obj = json.loads(line)
                rows = obj.get("rows") or []
                if rows and rows[0]:
                    return int(rows[0][0])
        return None
    except Exception:
        return None


def _health_line(repo: str) -> str:
    """One-line CBM index health verdict, or '' if it can't be assessed."""
    binary = _which("codebase-memory-mcp")
    if not binary:
        return ""
    project = _project_name(repo)
    cache = _cache_dir()
    corrupt = os.path.join(cache, project + ".db.corrupt")

    disk = _disk_kt_count(repo)
    graph = _graph_kt_count(binary, project)

    if graph is None:
        if os.path.exists(corrupt):
            return _fix_hint(repo, project, cache,
                             "⚠️ CBM index unreadable and a .db.corrupt sidecar is present")
        return ""  # not indexed yet / CLI unavailable — nothing to assert

    if disk > 0 and graph < disk * 0.9:
        return _fix_hint(
            repo, project, cache,
            f"⚠️ CBM index looks TRUNCATED: {graph}/{disk} .kt/.java files in graph")
    return f"✅ CBM index healthy ({graph}/{disk} .kt/.java files)"


def _fix_hint(repo: str, project: str, cache: str, headline: str) -> str:
    binary = _which("codebase-memory-mcp") or "codebase-memory-mcp"
    db = os.path.join(cache, project + ".db")
    return (
        f"{headline}. Fix (full reindex via standalone CLI):\n"
        f"  rm -f {db}{{,-shm,-wal,.corrupt}}\n"
        f"  {binary} cli index_repository --repo-path {repo} "
        f"--mode full --name {project}"
    )


def run_session_start() -> None:
    repo = _repo()

    reason = _cbm_unavailable_reason(repo)
    if reason is not None:
        msg = (f"⚠️ Graph-first navigation is NOT enforced: {reason}. "
               f"grep stays allowed until it is set up:\n"
               f"  claude mcp add codebase-memory-mcp -- codebase-memory-mcp")
        health = ""
    else:
        msg = ("🔒 Code navigation: codebase-memory-mcp first; Grep tool denied, "
               "Bash grep over *.kt/*.java denied (CBM_BYPASS=1 to override), "
               "other grep discouraged; tree / ls -R / git log -S disabled")
        health = _health_line(repo)

    if health:
        msg = msg + "\n" + health

    _emit({
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": SESSION_CTX,
        },
        "systemMessage": msg,
    })


# ── dispatch ─────────────────────────────────────────────────────────────────

_SUBCOMMANDS = {
    "bash-guard": run_bash_guard,
    "grep-guard": run_grep_guard,
    "session-start": run_session_start,
}


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in _SUBCOMMANDS:
        sys.stderr.write(
            "usage: graph_guard.py {bash-guard|grep-guard|session-start}\n")
        sys.exit(2)
    try:
        _SUBCOMMANDS[sys.argv[1]]()
    except Exception:
        # Fail open: never wedge the session on a guard bug.
        sys.exit(0)


if __name__ == "__main__":
    main()
