#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
OBJECT_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocs.json"
MYWORLD_SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"

INERT_OBELISK_ID = 1323
ELEMENTAL_OBELISK_IDS = {300, 301, 303, 304}
LEGACY_CHARGING_OBELISKS = {
    (231, 394),
    (414, 509),
    (230, 3248),
    (421, 3336),
}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_locs(path: Path) -> list[dict]:
    return json.loads(path.read_text(encoding="utf-8"))["sceneries"]


def loc_id_by_pos(locs: list[dict]) -> dict[tuple[int, int], int]:
    return {
        (loc["pos"]["X"], loc["pos"]["Y"]): loc["id"]
        for loc in locs
    }


def ensure_inert_obelisk_def() -> None:
    root = ET.parse(OBJECT_DEFS).getroot()
    defs = list(root)
    if len(defs) <= INERT_OBELISK_ID:
        fail(f"GameObjectDef.xml does not contain id {INERT_OBELISK_ID}")

    inert = defs[INERT_OBELISK_ID]
    if inert.findtext("name") != "Inert obelisk":
        fail(f"Object id {INERT_OBELISK_ID} should be Inert obelisk")
    if inert.findtext("command1") != "WalkTo":
        fail("Inert obelisk should not expose an active command")
    if inert.findtext("objectModel") != "obelisk":
        fail("Inert obelisk should reuse the existing obelisk model")


def ensure_legacy_obelisks_are_inert() -> None:
    locs_by_pos = loc_id_by_pos(load_locs(SCENERY_LOCS))
    for pos in LEGACY_CHARGING_OBELISKS:
        actual_id = locs_by_pos.get(pos)
        if actual_id != INERT_OBELISK_ID:
            fail(f"Legacy charging obelisk at {pos} should be inert id {INERT_OBELISK_ID}, found {actual_id}")


def ensure_altar_obelisks_remain_elemental() -> None:
    ids = {loc["id"] for loc in load_locs(MYWORLD_SCENERY_LOCS)}
    missing = ELEMENTAL_OBELISK_IDS - ids
    if missing:
        fail(f"Altar-area elemental obelisks were removed from MyWorldSceneryLocs: {sorted(missing)}")


def main() -> None:
    ensure_inert_obelisk_def()
    ensure_legacy_obelisks_are_inert()
    ensure_altar_obelisks_remain_elemental()
    print("PASS: inert obelisk placements validated")


if __name__ == "__main__":
    main()
