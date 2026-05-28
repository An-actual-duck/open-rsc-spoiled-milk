#!/usr/bin/env python3
"""Report live debug output that can leak into field-testing logs.

Default mode is advisory so the pre-field-test stack can surface known noise
without blocking unrelated verification. Set MYWORLD_DEBUG_AUDIT_STRICT=1 to
make any live diagnostic output fail the audit.

Set MYWORLD_DEBUG_AUDIT_INCLUDE_STDOUT=1 to include all System.out.println
calls. The default audit intentionally focuses on explicit debug/trace output;
the legacy client still contains many one-off status/error prints that are not
field-test diagnostics.
"""

import os
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCAN_ROOTS = [
    ROOT / "Client_Base/src",
    ROOT / "server/src",
    ROOT / "server/plugins",
]

DEBUG_PATTERNS = [
    re.compile(r"\bSystem\.out\.println\s*\(\s*\"[^\"]*(DEBUG|DEVDEBUG|TRACE|LOCAL_WALK|RECEIVED|_ASSET|POISON_)"),
    re.compile(r"\bLOGGER\.(info|debug|warn|error)\s*\(\s*\"[^\"]*DEVDEBUG"),
    re.compile(r"\bplayer\.message\s*\(\s*\"[^\"]*DEBUG"),
    re.compile(r"\b[a-zA-Z0-9_]+\.message\s*\(\s*\"[^\"]*DEBUG"),
    re.compile(r"\b[a-zA-Z0-9_]+\.message\s*\(\s*\"[^\"]*[A-Z0-9]+_[A-Z0-9_]*_DEBUG"),
]

if os.environ.get("MYWORLD_DEBUG_AUDIT_INCLUDE_STDOUT") == "1":
    DEBUG_PATTERNS.insert(0, re.compile(r"\bSystem\.out\.println\s*\("))

ALLOWLIST_MARKERS = [
    "if (Config.DEBUG)",
    "if (config().DEBUG)",
    "if (getConfig().DEBUG)",
    "if (server.getConfig().DEBUG)",
    "if (DEBUG",
    "DEBUG_LOCAL_WALK",
    "PathValidation.DEBUG",
    "PathValidation.DEBUG_DISTANCE",
]


def strip_block_comments(source: str) -> str:
    return re.sub(r"/\*.*?\*/", "", source, flags=re.S)


def is_comment(line: str) -> bool:
    return line.strip().startswith("//")


def is_allowlisted(lines: list[str], index: int) -> bool:
    window = "\n".join(lines[max(0, index - 8): index + 1])
    return any(marker in window for marker in ALLOWLIST_MARKERS)


def main() -> int:
    findings: list[tuple[Path, int, str, bool]] = []

    for root in SCAN_ROOTS:
        for path in sorted(root.rglob("*.java")):
            source = strip_block_comments(path.read_text(encoding="utf-8", errors="ignore"))
            lines = source.splitlines()
            for index, line in enumerate(lines):
                if is_comment(line):
                    continue
                if any(pattern.search(line) for pattern in DEBUG_PATTERNS):
                    findings.append((path, index + 1, line.strip(), is_allowlisted(lines, index)))

    live = [finding for finding in findings if not finding[3]]
    gated = [finding for finding in findings if finding[3]]

    print(f"Debug-output findings: {len(findings)}")
    print(f"Live findings: {len(live)}")
    print(f"Gated findings: {len(gated)}")

    if live:
        limit = len(live) if os.environ.get("MYWORLD_DEBUG_AUDIT_VERBOSE") == "1" else 25
        print("\nLive debug-output candidates:")
        for path, line, snippet, _ in live[:limit]:
            print(f"{path.relative_to(ROOT)}:{line}: {snippet[:180]}")
        if len(live) > limit:
            print(f"... and {len(live) - limit} more")

    if os.environ.get("MYWORLD_DEBUG_AUDIT_STRICT") == "1" and live:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
