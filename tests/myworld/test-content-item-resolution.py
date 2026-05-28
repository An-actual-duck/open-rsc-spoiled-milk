#!/usr/bin/env python3
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_ITEM_PATHS = [
    ROOT / "server/conf/server/defs/ItemDefs.json",
    ROOT / "server/conf/server/defs/ItemDefsCustom.json",
    ROOT / "server/conf/server/defs/ItemDefsMyWorld.json",
]
ITEM_ID_ENUM = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
PLUGIN_ROOT = ROOT / "server/plugins"
GROUND_ITEM_ROOT = ROOT / "server/conf/server/defs/locs"
EXTRAS_ROOT = ROOT / "server/conf/server/defs/extras"
XML_ITEM_ID_TAGS = {
    "arrowID",
    "baitId",
    "barId",
    "bowID",
    "burnedId",
    "certID",
    "cookedId",
    "dartID",
    "fishId",
    "gemID",
    "itemID",
    "logId",
    "longbowID",
    "netId",
    "newId",
    "oreId",
    "potionID",
    "potionId",
    "prodId",
    "runeId",
    "secondID",
    "shortbowID",
    "unfinishedID",
}


def load_entries(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, dict) and isinstance(payload.get("items"), list):
        return payload["items"]
    if isinstance(payload, dict) and isinstance(payload.get("item"), list):
        return payload["item"]
    raise ValueError(f"Unknown item definition shape: {path}")


def load_server_items() -> dict[int, dict]:
    items: dict[int, dict] = {}
    for path in SERVER_ITEM_PATHS:
        for entry in load_entries(path):
            item_id = int(entry["id"])
            merged = dict(items.get(item_id, {}))
            merged.update(entry)
            items[item_id] = merged
    return items


def parse_item_id_enum() -> dict[str, int]:
    source = ITEM_ID_ENUM.read_text(encoding="utf-8")
    result: dict[str, int] = {}
    for match in re.finditer(r"\b([A-Z0-9_]+)\((-?\d+)\)", source):
        result[match.group(1)] = int(match.group(2))
    return result


def iter_java_files() -> list[Path]:
    return [NPC_DROPS, *sorted(PLUGIN_ROOT.rglob("*.java"))]


def referenced_item_names(path: Path) -> set[str]:
    source = path.read_text(encoding="utf-8")
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"//.*", "", source)
    return set(re.findall(r"ItemId\.([A-Z0-9_]+)\.id\(\)", source))


def stripped_source(path: Path) -> str:
    source = path.read_text(encoding="utf-8", errors="ignore")
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"//.*", "", source)
    return source


def direct_numeric_item_refs(path: Path) -> list[tuple[int, int]]:
    source = stripped_source(path)
    refs: list[tuple[int, int]] = []
    for match in re.finditer(r"\bnew\s+Item\s*\(\s*(-?\d+)", source):
        line = source.count("\n", 0, match.start()) + 1
        refs.append((line, int(match.group(1))))
    return refs


def iter_ground_item_refs() -> list[tuple[Path, int]]:
    refs: list[tuple[Path, int]] = []
    for path in sorted(GROUND_ITEM_ROOT.glob("GroundItems*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        for entry in payload.get("grounditems", []):
            refs.append((path, int(entry["id"])))
    return refs


def iter_extra_xml_item_refs() -> list[tuple[Path, str, int]]:
    refs: list[tuple[Path, str, int]] = []
    for path in sorted(EXTRAS_ROOT.glob("*.xml")):
        tree = ET.parse(path)
        for element in tree.iter():
            if element.tag not in XML_ITEM_ID_TAGS:
                continue
            text = (element.text or "").strip()
            if not text or not text.lstrip("-").isdigit():
                continue
            item_id = int(text)
            if item_id < 0:
                continue
            refs.append((path, element.tag, item_id))
    return refs


def validate_item_ref(server_items: dict[int, dict], context: str, item_id: int, failures: list[str]) -> None:
    if item_id < 0:
        return
    item_def = server_items.get(item_id)
    if item_def is None:
        failures.append(f"{context} references item {item_id} without a server item definition")
        return
    item_label = str(item_def.get("name", "")).strip()
    if item_label.lower() == "unobtanium":
        failures.append(f"{context} references item {item_id} which resolves to Unobtanium")


def main() -> int:
    server_items = load_server_items()
    enum_map = parse_item_id_enum()
    failures: list[str] = []
    files_checked = 0
    refs_checked = 0

    for path in iter_java_files():
        files_checked += 1
        for item_name in sorted(referenced_item_names(path)):
            refs_checked += 1
            item_id = enum_map.get(item_name)
            if item_name == "NOTHING":
                continue
            if item_id is None:
                failures.append(f"{path.relative_to(ROOT)} references unknown ItemId.{item_name}")
                continue
            validate_item_ref(
                server_items,
                f"{path.relative_to(ROOT)} references ItemId.{item_name} ({item_id})",
                item_id,
                failures,
            )

        for line, item_id in direct_numeric_item_refs(path):
            refs_checked += 1
            validate_item_ref(
                server_items,
                f"{path.relative_to(ROOT)}:{line} directly constructs new Item({item_id})",
                item_id,
                failures,
            )

    for path, item_id in iter_ground_item_refs():
        refs_checked += 1
        validate_item_ref(
            server_items,
            f"{path.relative_to(ROOT)} ground item",
            item_id,
            failures,
        )

    for path, tag, item_id in iter_extra_xml_item_refs():
        refs_checked += 1
        validate_item_ref(
            server_items,
            f"{path.relative_to(ROOT)} <{tag}>",
            item_id,
            failures,
        )

    print(f"Files checked: {files_checked}")
    print(f"Item references checked: {refs_checked}")

    if failures:
        print("\nFAIL:")
        for failure in failures[:100]:
            print(failure)
        if len(failures) > 100:
            print(f"... and {len(failures) - 100} more")
        return 1

    print("\nPASS: plugin item references, drop tables, ground items, and production defs resolve to defined non-placeholder items")
    return 0


if __name__ == "__main__":
    sys.exit(main())
