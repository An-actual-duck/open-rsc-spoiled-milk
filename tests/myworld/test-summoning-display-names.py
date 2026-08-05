#!/usr/bin/env python3
import re
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/summoning-plan.md"
SUMMON_ICON_DIR = ROOT / "dev/myworld/assets/sprites/UI/summon"

SUMMON_NAMES = [
    "Broodling Spider",
    "Mischief Imp",
    "Loot Goblin",
    "Ironhide Bear",
    "Sacred Unicorn",
    "Duskwind Bat",
    "Pack Rat",
    "Bound Battleaxe",
    "Mourning Unicorn",
    "Restless Shade",
    "Guard Dog",
    "Delivery Camel",
    "Foundry Dragon",
    "Astral Wraith",
    "Abyssal Demon",
]


def asset_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def main() -> int:
    failures: list[str] = []
    client = CLIENT.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    for name in SUMMON_NAMES:
        if name not in client:
            failures.append(f"client summon menu missing {name}")
        if name not in summoning:
            failures.append(f"server summon profile missing {name}")
        if name not in guide:
            failures.append(f"summoning skill guide missing {name}")
        if name not in plan:
            failures.append(f"summoning plan missing {name}")

    if "dev/myworld/assets/sprites/UI/summon" not in client:
        failures.append("client must load summon icons from the current UI summon folder")
    if "getExternalSummoningIconFile(iconName)" not in client:
        failures.append("client must use a dedicated summon icon lookup")
    if "getDisplayNameAssetName(SUMMONING_NAMES[summonIndex])" not in client:
        failures.append("summon icon filenames must be derived from dashed summon names")
    if "drawSprite(summonIcon" not in client:
        failures.append("summon menu must draw summon icon sprites when present")
    initials_only_names = {"Foundry Dragon"}
    expected_icon_names = [(name, asset_name(name) + ".png") for name in SUMMON_NAMES]
    for name, filename in expected_icon_names:
        if filename not in plan:
            failures.append(f"summoning plan should document icon filename {filename}")
        icon_path = SUMMON_ICON_DIR / filename
        if not icon_path.exists():
            if name not in initials_only_names:
                failures.append(f"missing summon icon {filename}")
            continue
        data = icon_path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            failures.append(f"summon icon must be a PNG: {filename}")
            continue
        width, height = struct.unpack(">II", data[16:24])
        if width <= 0 or height <= 0:
            failures.append(f"summon icon must have positive geometry: {filename}")
    if "this.getSpellInitials(SUMMONING_NAMES[summonIndex])" not in client:
        failures.append("client must retain initials fallback for summons without icon art")
    if "built-in `FD` initials" not in plan:
        failures.append("summoning plan must document the Foundry Dragon icon fallback")

    if failures:
        print("FAIL:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("PASS: summoning display names and icon path are wired")
    return 0


if __name__ == "__main__":
    sys.exit(main())
