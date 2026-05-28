#!/usr/bin/env python3
"""Validate MyWorld fatigue/sleep removal guardrails."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
MYWORLD_CONF = SERVER / "myworld.conf"
SLEEPING = SERVER / "plugins/com/openrsc/server/plugins/authentic/misc/Sleeping.java"
ACTION_SENDER = SERVER / "src/com/openrsc/server/net/rsc/ActionSender.java"
SLEEP_HANDLER = SERVER / "src/com/openrsc/server/net/rsc/handlers/SleepHandler.java"
PLAYER = SERVER / "src/com/openrsc/server/model/entity/player/Player.java"

ALLOWED_SLEEPING_BAG_REFERENCES = {
    "server/src/com/openrsc/server/constants/ItemId.java",
    "server/conf/server/defs/ItemDefs.json",
    "server/plugins/com/openrsc/server/plugins/authentic/misc/Sleeping.java",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def require_conf_bool(key: str, value: bool) -> None:
    text = read(MYWORLD_CONF)
    expected = "true" if value else "false"
    if not re.search(rf"^\s*{re.escape(key)}:\s*{expected}\b", text, re.MULTILINE):
        fail(f"server/myworld.conf must keep {key}: {expected}")


def require_contains(path: Path, snippet: str) -> None:
    if snippet not in read(path):
        fail(f"{relative(path)} missing expected snippet: {snippet}")


def validate_no_sleeping_bag_acquisition_paths() -> None:
    offenders = []
    for base in (SERVER / "plugins", SERVER / "src"):
        for path in base.rglob("*.java"):
            if "SLEEPING_BAG" not in read(path):
                continue
            rel = relative(path)
            if rel not in ALLOWED_SLEEPING_BAG_REFERENCES:
                offenders.append(rel)
    if offenders:
        fail("Unexpected sleeping bag gameplay reference(s): " + ", ".join(sorted(offenders)))


def main() -> None:
    require_conf_bool("want_fatigue", False)
    require_conf_bool("features_sleep", False)
    require_conf_bool("load_prerendered_sleepwords", False)
    require_conf_bool("load_special_prerendered_sleepwords", False)

    require_contains(SLEEPING, "if (!owner.getConfig().FEATURES_SLEEP)")
    require_contains(SLEEPING, "return player.getConfig().FEATURES_SLEEP &&")
    require_contains(ACTION_SENDER, "if (!player.getConfig().FEATURES_SLEEP)")
    require_contains(SLEEP_HANDLER, "if (!player.getConfig().FEATURES_SLEEP)")
    require_contains(PLAYER, "Use the experience toggle to re-enable it.")

    validate_no_sleeping_bag_acquisition_paths()
    print("PASS: MyWorld fatigue/sleep removal guardrails validated")


if __name__ == "__main__":
    main()
