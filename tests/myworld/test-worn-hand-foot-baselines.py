#!/usr/bin/env python3
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ITEM_DEFS = ROOT / "server/conf/server/defs/ItemDefs.json"
CUSTOM_ITEM_DEFS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
EQUIPMENT_ASSETS = ROOT / "dev/myworld/assets/runtime/sprites/equipment"


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
        698: 1000,  # Steel gauntlets
        699: 1006,  # gauntlets of goldsmithing
        700: 1007,  # gauntlets of cooking
        701: 1008,  # gauntlets of chaos
        1006: 1009,  # Klank's gauntlets
    }
    for item_id, appearance_id in expected_base_appearances.items():
        item = base_items[item_id]
        require(item["appearanceID"] == appearance_id, f"{item['name']} should use appearance {appearance_id}")

    expected_custom_appearances = {
        1960: 996, 1966: 997, 1983: 998, 1985: 999, 1989: 1001,
        1972: 1002, 1991: 1003, 1978: 1004, 1993: 1005,
        3131: 989, 3132: 990, 3133: 991, 3134: 992, 3135: 993, 3136: 994,
        3137: 1010, 3138: 1011, 3139: 1012, 3140: 1013, 3141: 1014,
        3142: 1015, 3143: 1016, 3144: 1017, 3145: 1018, 3146: 1019,
        3147: 1020, 3148: 1021, 3149: 1022, 3150: 1023, 3151: 1024,
    }
    for item_id, appearance_id in expected_custom_appearances.items():
        item = custom_items[item_id]
        require(item["appearanceID"] == appearance_id, f"{item['name']} should use appearance {appearance_id}")

    for appearance_id in range(1010, 1025):
        item = next((candidate for candidate in custom_items.values() if candidate["appearanceID"] == appearance_id), None)
        require(item is not None, f"God wool appearance {appearance_id} should be assigned")

    print("PASS: worn hand and foot baseline sprites are mapped across armour sets")


if __name__ == "__main__":
    main()
