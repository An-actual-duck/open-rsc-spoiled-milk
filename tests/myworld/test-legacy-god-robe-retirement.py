#!/usr/bin/env python3
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS = ROOT / "server/conf/server/defs"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
ITEM_IDS = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
UNDERGROUND_DISCIPLE = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/"
    "undergroundpass/npcs/UndergroundPassIbanDisciple.java"
)
UNDERGROUND_TEMPLE = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/"
    "undergroundpass/obstacles/UndergroundPassObstaclesMap3.java"
)
BIOHAZARD = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java"
)
DOOR_ACTION = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java"
)
GRAPES = ROOT / (
    "server/plugins/com/openrsc/server/plugins/custom/myworld/misc/GrapeEmpowerment.java"
)
RETIREMENT_DOC = ROOT / (
    "docs/myworld/completed-work-plans/legacy-god-robe-retirement.md"
)

RETIRED = {
    388: ("MONKS_ROBE_TOP", 3143),
    389: ("MONKS_ROBE_BOTTOM", 3144),
    607: ("DRUIDS_ROBE_TOP", 3148),
    608: ("DRUIDS_ROBE_BOTTOM", 3149),
    702: ("ROBE_OF_ZAMORAK_TOP", 3138),
    703: ("ROBE_OF_ZAMORAK_BOTTOM", 3139),
    807: ("PRIEST_ROBE", 3143),
    808: ("PRIEST_GOWN", 3144),
}
ACTIVE = {
    3138: "Wool robe top blessed by Zamorak",
    3139: "Wool robe bottom blessed by Zamorak",
    3143: "Wool robe top blessed by Saradomin",
    3144: "Wool robe bottom blessed by Saradomin",
    3148: "Wool robe top blessed by Guthix",
    3149: "Wool robe bottom blessed by Guthix",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_effective_items() -> dict[int, dict]:
    items = {
        item["id"]: item.copy()
        for item in json.loads((DEFS / "ItemDefs.json").read_text(encoding="utf-8"))["item"]
    }
    for filename in ("ItemDefsCustom.json", "ItemDefsMyWorld.json"):
        for item in json.loads((DEFS / filename).read_text(encoding="utf-8"))["items"]:
            items.setdefault(item["id"], {}).update(item)
    return items


def require_replacement(source: str, required: tuple[str, ...], label: str) -> None:
    for token in required:
        require(token in source, f"{label} missing active replacement {token}")


def main() -> None:
    items = load_effective_items()
    for item_id in RETIRED:
        item = items[item_id]
        require(item["name"].startswith("Retired "), f"legacy item {item_id} is not labeled retired")
        require("retired compatibility item" in item["description"].lower(),
                f"legacy item {item_id} lacks a compatibility description")
        require(item["isWearable"] == 0, f"legacy item {item_id} remains wearable")
        require(item["appearanceID"] == 0, f"legacy item {item_id} keeps a server appearance")
        require(item["wearableID"] == 0, f"legacy item {item_id} keeps a worn sprite")
        require(item["wearSlot"] == -1, f"legacy item {item_id} keeps an equipment slot")
        require(item["prayerBonus"] == 0, f"legacy item {item_id} keeps a Worship bonus")

    for item_id, name in ACTIVE.items():
        item = items[item_id]
        require(item["name"] == name, f"active robe {item_id} identity changed")
        require(item["isWearable"] == 1, f"active robe {item_id} is no longer wearable")
        require(item["wearSlot"] in (6, 7), f"active robe {item_id} has the wrong slot")
        require(item["prayerBonus"] > 0, f"active robe {item_id} lost its Worship bonus")

    retired_ids = set(RETIRED)
    for path in sorted((DEFS / "locs").glob("GroundItems*.json")):
        ground_data = json.loads(path.read_text(encoding="utf-8"))
        entries = ground_data[next(iter(ground_data))]
        leaked = sorted(entry["id"] for entry in entries if entry["id"] in retired_ids)
        require(not leaked, f"{path.name} still spawns retired robe IDs {leaked}")

    npc_drops = NPC_DROPS.read_text(encoding="utf-8")
    require_replacement(
        npc_drops,
        (
            "ItemId.SARADOMIN_WOOL_ROBE_TOP.id(), 1, 4",
            "ItemId.SARADOMIN_WOOL_ROBE_BOTTOM.id(), 1, 4",
            "ItemId.ZAMORAK_WOOL_ROBE_TOP.id(), 1, 4",
            "ItemId.ZAMORAK_WOOL_ROBE_BOTTOM.id(), 1, 4",
            "ItemId.GUTHIX_WOOL_ROBE_TOP.id(), 1, 6",
            "ItemId.GUTHIX_WOOL_ROBE_BOTTOM.id(), 1, 5",
        ),
        "NPC drops",
    )

    disciple = UNDERGROUND_DISCIPLE.read_text(encoding="utf-8")
    temple = UNDERGROUND_TEMPLE.read_text(encoding="utf-8")
    require_replacement(
        disciple,
        ("ItemId.ZAMORAK_WOOL_ROBE_TOP.id()", "ItemId.ZAMORAK_WOOL_ROBE_BOTTOM.id()"),
        "Underground Pass disciple",
    )
    require_replacement(
        temple,
        ("ItemId.ZAMORAK_WOOL_ROBE_TOP.id()", "ItemId.ZAMORAK_WOOL_ROBE_BOTTOM.id()"),
        "Underground Pass temple",
    )

    for path in (BIOHAZARD, DOOR_ACTION):
        require_replacement(
            path.read_text(encoding="utf-8"),
            ("ItemId.SARADOMIN_WOOL_ROBE_TOP.id()", "ItemId.SARADOMIN_WOOL_ROBE_BOTTOM.id()"),
            path.name,
        )

    grapes = GRAPES.read_text(encoding="utf-8")
    require_replacement(
        grapes,
        (
            "ItemId.SARADOMIN_WOOL_ROBE_TOP.id()",
            "ItemId.SARADOMIN_WOOL_ROBE_BOTTOM.id()",
            "ItemId.ZAMORAK_WOOL_ROBE_TOP.id()",
            "ItemId.ZAMORAK_WOOL_ROBE_BOTTOM.id()",
        ),
        "Grape Empowerment",
    )

    legacy_tokens = tuple(f"ItemId.{constant}" for constant, _ in RETIRED.values())
    for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
        for path in source_root.rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            for token in legacy_tokens:
                require(token not in source, f"{path.relative_to(ROOT)} still uses {token}")

    raw_producer = re.compile(
        r"(?:give|addobject|addItemDrop|new\s+Item)\s*\([^;\n]*\b"
        + r"(?:388|389|607|608|702|703|807|808)\b"
    )
    for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
        for path in source_root.rglob("*.java"):
            require(not raw_producer.search(path.read_text(encoding="utf-8")),
                    f"{path.relative_to(ROOT)} produces a retired robe by raw ID")

    item_ids = ITEM_IDS.read_text(encoding="utf-8")
    retirement_doc = RETIREMENT_DOC.read_text(encoding="utf-8")
    for item_id, (constant, replacement_id) in RETIRED.items():
        require(f"{constant}({item_id}), // Retired compatibility ID;" in item_ids,
                f"ItemId {constant} is not marked as compatibility-only")
        require(str(item_id) in retirement_doc and str(replacement_id) in retirement_doc,
                f"migration mapping {item_id} -> {replacement_id} is undocumented")

    print("PASS: legacy god robes are retired and active sources use blessed wool replacements")


if __name__ == "__main__":
    main()
