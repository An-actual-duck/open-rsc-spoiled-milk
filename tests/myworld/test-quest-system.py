#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
ACTIONSENDER = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "ActionSender.java"
)
QUEST_AUDIT = ROOT / "docs" / "myworld" / "work-items.md"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        fail(message)


def main() -> None:
    actionsender_text = ACTIONSENDER.read_text(encoding="utf-8")
    audit_text = QUEST_AUDIT.read_text(encoding="utf-8")

    if "MyWorldQuestBootstrap.applyIfNeeded(player);" in actionsender_text:
        fail("ActionSender should not globally auto-complete MyWorld quests at login anymore")

    require(
        audit_text,
        "The global login bootstrap has been removed.",
        "Quest audit should record that login-time global quest completion is disabled",
    )
    require(
        audit_text,
        "Quest initiation audit",
        "Quest audit should include quest initiation coverage",
    )
    require(
        audit_text,
        "Quest-unique items with use outside the original quest",
        "Quest audit should include the post-quest item audit",
    )
    require(
        audit_text,
        "I've already done this quest",
        "Quest audit should document the target per-quest shortcut flow",
    )

    print("PASS: quest system audit and bootstrap removal validated")


if __name__ == "__main__":
    main()
