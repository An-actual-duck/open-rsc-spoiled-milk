#!/usr/bin/env python3

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS = ROOT / "server/conf/server/defs"
LOCS = DEFS / "locs"


RETIRED_GROUND_ITEM_IDS = {
    # Generic leather equipment has been replaced by source-specific hide gear.
    15,   # Leather Armour
    16,   # Leather Gloves
    146,  # Fur
    541,  # Grey wolf fur

    # Battlestaff/orb crafting is retired in favor of altar-attuned staffs.
    611, 612, 613, 614, 615, 616, 617, 618, 626, 627, 682, 683, 684, 685,

    # Crowns and the crown mould are scrubbed from active production.
    1502, 1503, 1504, 1505, 1506, 1507, 1508,

    # Direct altar use replaces normal runecrafting talisman progression.
    1300, 1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1311,
    1385, 1386, 1387, 1388, 1389, 1390, 1391, 1392, 1393, 1394, 1395, 1396, 1397,
    1398, 1399, 1400, 1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409,

    # Non-wool wizard clothing is not part of the active robe progression.
    184, 185, 195, 199, 216,
}


def load_item_defs() -> dict[int, str]:
    items: dict[int, str] = {}
    for name in ("ItemDefs.json", "ItemDefsCustom.json", "ItemDefsMyWorld.json"):
        data = json.loads((DEFS / name).read_text(encoding="utf-8"))
        for entry in data.get("item", data.get("items", [])):
            if "id" in entry:
                items[int(entry["id"])] = entry.get("name", items.get(int(entry["id"]), ""))
    return items


def main() -> None:
    item_names = load_item_defs()
    failures: list[str] = []

    for path in sorted(LOCS.glob("GroundItems*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for entry in data.get("grounditems", []):
            item_id = int(entry["id"])
            label = f"{path.relative_to(ROOT)} item {item_id} at {entry.get('pos')}"
            name = item_names.get(item_id)
            if name is None:
                failures.append(f"{label} has no item definition")
            elif name in {"Unobtanium", "Retired item", "Retired potion"}:
                failures.append(f"{label} resolves to {name}")
            if item_id in RETIRED_GROUND_ITEM_IDS:
                failures.append(f"{label} uses a retired MyWorld ground item")

    if failures:
        raise AssertionError("\n".join(failures))

    print("PASS: ground item cleanup validated")


if __name__ == "__main__":
    main()
