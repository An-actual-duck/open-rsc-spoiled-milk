#!/usr/bin/env python3
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ITEM_DEFS = ROOT / "server/conf/server/defs/ItemDefs.json"
CUSTOM_ITEM_DEFS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
EQUIPMENT_ASSETS = ROOT / "dev/myworld/assets/sprites/equipment"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_items(path: Path) -> dict[int, dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    items = data.get("items", data.get("item"))
    require(items is not None, f"{path} should contain item definitions")
    return {item["id"]: item for item in items}


def require_frames(folder_name: str) -> None:
    numbered = EQUIPMENT_ASSETS / folder_name / "numbered"
    require(numbered.is_dir(), f"Missing external equipment folder: {numbered}")
    missing = [frame for frame in range(18) if not (numbered / f"{frame:02}.png").is_file()]
    require(not missing, f"{folder_name} is missing numbered frames: {missing}")


def main() -> None:
    client = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    base_items = load_items(ITEM_DEFS)
    custom_items = load_items(CUSTOM_ITEM_DEFS)

    for family in ("gauntlets", "greaves", "hidegloves", "hideboots", "woolgloves", "woolboots"):
        require_frames(family)
        require(
            f'loadExternalLayeredEquipmentSprite("{family}", getExternalEquipmentNumberedFolder("{family}")' in mudclient,
            f"{family} should be loaded from external equipment sprites",
        )
        require(f'new AnimationDef("{family}", "equipment"' in client, f"{family} should have client animation definitions")

    for family, layer in (
        ("gauntlets", "GLOVES"),
        ("hidegloves", "GLOVES"),
        ("woolgloves", "GLOVES"),
        ("greaves", "BOOTS"),
        ("hideboots", "BOOTS"),
        ("woolboots", "BOOTS"),
    ):
        require(
            f'loadExternalLayeredEquipmentSprite("{family}", getExternalEquipmentNumberedFolder("{family}"),\n\t\t\torsc.graphics.two.SpriteArchive.Frame.LAYER.{layer}' in mudclient,
            f"{family} should load on the {layer} player layer",
        )

    expected_base_appearances = {
        698: 1001,  # Steel gauntlets
        699: 1007,  # gauntlets of goldsmithing
        700: 1008,  # gauntlets of cooking
        701: 1009,  # gauntlets of chaos
        1006: 1010,  # Klank's gauntlets
    }
    for item_id, appearance_id in expected_base_appearances.items():
        item = base_items[item_id]
        require(item["appearanceID"] == appearance_id, f"{item['name']} should use appearance {appearance_id}")

    expected_custom_appearances = {
        1960: 997, 1966: 998, 1983: 999, 1985: 1000, 1989: 1002,
        1972: 1003, 1991: 1004, 1978: 1005, 1993: 1006,
        3131: 990, 3132: 991, 3133: 992, 3134: 993, 3135: 994, 3136: 995,
        3137: 1011, 3138: 1012, 3139: 1013, 3140: 1014, 3141: 1015,
        3142: 1016, 3143: 1017, 3144: 1018, 3145: 1019, 3146: 1020,
        3147: 1021, 3148: 1022, 3149: 1023, 3150: 1024, 3151: 1025,
    }
    for item_id, appearance_id in expected_custom_appearances.items():
        item = custom_items[item_id]
        require(item["appearanceID"] == appearance_id, f"{item['name']} should use appearance {appearance_id}")

    for appearance_id in range(1011, 1026):
        item = next((candidate for candidate in custom_items.values() if candidate["appearanceID"] == appearance_id), None)
        require(item is not None, f"God wool appearance {appearance_id} should be assigned")

    print("PASS: worn hand and foot baseline sprites are mapped across armour sets")


if __name__ == "__main__":
    main()
