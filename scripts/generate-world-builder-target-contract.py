#!/usr/bin/env python3
"""Generate Core's deterministic RSC World Editor target contract.

This is intentionally a Core-owned generator.  It derives compatibility
evidence from the authoritative definitions and packed placement sources in
this checkout; it does not inspect or import the World Editor repository.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


FAMILIES = ["boundary", "ground-item", "npc", "scenery"]
CATALOG_ID = "spoiled-milk-definition-catalog-v1"
LOADER_ID = "spoiled-milk-native-layered-loader-v1"
PROTOCOL_ID = "native-layered-package-v1"
CAPABILITY_ID = "spoiled-milk-layered-install-capability-v1"


def encoded(value: object) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encoded(value))


def ids_from_json(paths: list[Path], root_keys: tuple[str, ...]) -> list[int]:
    found: set[int] = set()
    for path in paths:
        document = json.loads(path.read_text(encoding="utf-8"))
        records = next(
            (document[key] for key in root_keys if isinstance(document.get(key), list)),
            None,
        )
        if records is None:
            raise ValueError(f"no authoritative definition array in {path}")
        for record in records:
            identifier = record.get("id") if isinstance(record, dict) else None
            if not isinstance(identifier, int) or identifier < 0:
                raise ValueError(f"invalid authoritative definition ID in {path}")
            found.add(identifier)
    return sorted(found)


def xml_count(path: Path, tag: str) -> int:
    return sum(1 for element in ET.parse(path).getroot() if element.tag == tag)


def effective_key(record: dict, family: str) -> tuple[int, ...]:
    point = record["start"] if family == "npc" else record["pos"]
    packed_y = point["Y"]
    level = (0, 1, 2, -1)[packed_y // 944]
    y = packed_y % 944
    if family == "boundary":
        return (level, point["X"], y, record["direction"])
    if family == "npc":
        return (level, record["id"], point["X"], y)
    return (level, point["X"], y)


def source_document(
    path: Path, source_key: str, target_key: str, family: str
) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if set(value) != {source_key} or not isinstance(value[source_key], list):
        raise ValueError(f"unexpected placement root in {path}")
    records = value[source_key]
    removal = target_key.endswith("removals")
    fields = {
        "boundary": ("direction", "pos") if removal else ("direction", "id", "pos"),
        "ground-item": ("id", "pos") if removal else ("amount", "id", "pos", "respawn"),
        "npc": ("id", "max", "min", "start"),
        "scenery": ("pos",) if removal else ("direction", "id", "pos"),
    }[family]
    normalized_fields = []
    for record in records:
        normalized = {key: record[key] for key in fields}
        for key in ("pos", "start", "min", "max"):
            if key in normalized:
                normalized[key] = {
                    "X": normalized[key]["X"],
                    "Y": normalized[key]["Y"],
                }
        normalized_fields.append(normalized)
    records = normalized_fields
    if path.name == "NpcLocs.json" and source_key == "npclocs":
        normalized = []
        corrected = 0
        for record in value[source_key]:
            if (
                isinstance(record, dict)
                and record.get("id") == 67
                and record.get("start") == {"X": 647, "Y": 3534}
                and record.get("min") == {"X": 632, "Y": 3519}
                and record.get("max") == {"X": 662, "Y": 6549}
            ):
                record = dict(record)
                record["max"] = {"X": 662, "Y": 3549}
                corrected += 1
            normalized.append(record)
        if corrected != 1:
            raise ValueError("expected exactly one known NPC 67 roam-bound correction")
        records = normalized
    # The legacy population contains repeated byte-identical records in a few
    # sources.  Core's effective population is set-like at those identities;
    # publish each once, but refuse to guess if colliding records differ.
    deduplicated: list[dict] = []
    seen: dict[tuple[int, ...], dict] = {}
    for record in records:
        key = effective_key(record, family)
        previous = seen.get(key)
        if previous is None:
            seen[key] = record
            deduplicated.append(record)
        elif previous != record:
            raise ValueError(f"distinct {family} placements collide in {path}: {key}")
    records = deduplicated
    return {target_key: records}


def placement(role: str, family: str, kind: str, relative: str, order: int) -> dict:
    suffix = "removals" if kind == "removal" else "locations"
    return {
        "compositionOrder": order,
        "encoding": f"packed-{family}-{suffix}-v1",
        "family": family,
        "kind": kind,
        "relativePath": relative,
        "role": role,
    }


def generate(root: Path) -> None:
    definitions = root / "server/conf/server/defs"
    fallback = root / "server/world-builder-fallback"
    client_fallback = root / "Client_Base/world-builder-fallback"

    item_ids = ids_from_json(
        sorted(definitions.glob("ItemDefs*.json")), ("item", "items")
    )
    catalog = {
        "boundaries": list(range(xml_count(definitions / "DoorDef.xml", "DoorDef"))),
        "catalogId": CATALOG_ID,
        "groundItems": item_ids,
        "manifestType": "world-builder-definition-catalog",
        "npcs": ids_from_json(
            sorted(definitions.glob("*NpcDefs*.json")), ("npcs", "npc")
        ),
        "scenery": list(
            range(xml_count(definitions / "GameObjectDef.xml", "GameObjectDef"))
        ),
        "schemaVersion": 1,
        "tiles": list(range(xml_count(definitions / "TileDef.xml", "TileDef"))),
    }
    catalog_bytes = encoded(catalog)
    catalog_hash = hashlib.sha256(catalog_bytes).hexdigest()
    server_catalog = fallback / "definitions.json"
    client_catalog = client_fallback / "definitions.json"
    server_catalog.parent.mkdir(parents=True, exist_ok=True)
    client_catalog.parent.mkdir(parents=True, exist_ok=True)
    server_catalog.write_bytes(catalog_bytes)
    client_catalog.write_bytes(catalog_bytes)

    authoring = {
        "createLevels": True,
        "editExistingLevels": True,
        "placementFamilies": FAMILIES,
    }
    for side, build_id, destination in (
        ("server", "spoiled-milk-server-native-layered-v1", fallback / "runtime.json"),
        ("client", "spoiled-milk-client-native-layered-v1", client_fallback / "runtime.json"),
    ):
        write_json(
            destination,
            {
                "authoring": authoring,
                "buildId": build_id,
                "definitionCatalogId": CATALOG_ID,
                "definitionCatalogSha256": catalog_hash,
                "encodingVersions": [1],
                "loaderId": LOADER_ID,
                "manifestType": "world-builder-runtime-evidence",
                "mapFormatId": "legacy-packed-orsc-v1",
                "packageSchemaId": "layered-world-package-v1",
                "protocolId": PROTOCOL_ID,
                "schemaVersion": 1,
                "side": side,
            },
        )

    source_locs = definitions / "locs"
    bases = (
        ("BoundaryLocs.json", "boundaries", "boundaries", "boundary", "boundaries.json"),
        ("GroundItems.json", "grounditems", "ground_items", "ground-item", "ground-items.json"),
        ("NpcLocs.json", "npclocs", "npclocs", "npc", "npcs.json"),
        ("SceneryLocs.json", "sceneries", "sceneries", "scenery", "scenery.json"),
    )
    for filename, source_key, target_key, family, output in bases:
        write_json(
            fallback / output,
            source_document(source_locs / filename, source_key, target_key, family),
        )

    library = root / "Client_Base/Cache/video/library.orsc"
    fallback.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(library, fallback / "library.orsc")

    sources: list[dict] = []
    for role, family, relative in (
        ("boundary-base", "boundary", "server/world-builder-fallback/boundaries.json"),
        ("ground-item-base", "ground-item", "server/world-builder-fallback/ground-items.json"),
        ("npc-base", "npc", "server/world-builder-fallback/npcs.json"),
        ("scenery-base", "scenery", "server/world-builder-fallback/scenery.json"),
    ):
        sources.append(placement(role, family, "base", relative, len(sources)))

    auxiliary = (
        ("scenery-auxiliary-discontinued", "SceneryLocsDiscontinued.json"),
        ("scenery-auxiliary-mod-room", "SceneryLocsModRoom.json"),
        ("scenery-auxiliary-runecraft", "SceneryLocsRunecraft.json"),
        ("scenery-auxiliary-harvesting", "SceneryLocsHarvesting.json"),
        ("scenery-auxiliary-custom-quest", "SceneryLocsCustomQuest.json"),
        ("scenery-auxiliary-expansion", "SceneryLocsExpansion.json"),
        ("scenery-auxiliary-woodcutting-guild", "SceneryLocsWoodcuttingGuild.json"),
        ("scenery-auxiliary-other", "SceneryLocsOther.json"),
    )
    for role, filename in auxiliary:
        path = source_locs / filename
        if path.is_file():
            relative = f"server/world-builder-fallback/placements/{filename}"
            write_json(
                root / relative,
                source_document(path, "sceneries", "sceneries", "scenery"),
            )
            sources.append(
                placement(
                    role,
                    "scenery",
                    "overlay",
                    relative,
                    len(sources),
                )
            )
    for role, family, kind, filename in (
        ("ground-item-overlay", "ground-item", "overlay", "MyWorldGroundItemLocs.json"),
        ("scenery-overlay", "scenery", "overlay", "MyWorldSceneryLocs.json"),
        ("scenery-removal", "scenery", "removal", "MyWorldSceneryRemovals.json"),
        ("npc-overlay", "npc", "overlay", "MyWorldNpcLocs.json"),
        ("npc-removal", "npc", "removal", "MyWorldNpcRemovals.json"),
    ):
        path = source_locs / filename
        if path.is_file():
            root_key = {
                ("ground-item", "overlay"): "ground_items",
                ("scenery", "overlay"): "sceneries",
                ("scenery", "removal"): "scenery_removals",
                ("npc", "overlay"): "npclocs",
                ("npc", "removal"): "npc_removals",
            }[(family, kind)]
            source_key = next(iter(json.loads(path.read_text(encoding="utf-8"))))
            relative = f"server/world-builder-fallback/placements/{filename}"
            write_json(
                root / relative,
                source_document(path, source_key, root_key, family),
            )
            sources.append(
                placement(
                    role,
                    family,
                    kind,
                    relative,
                    len(sources),
                )
            )

    # Normalize stale legacy removals to Core's actual effective composition.
    # The live loader already treats a removal of an absent placement as a
    # no-op; the strict contract records only mutations that have an owner.
    effective: dict[str, set[tuple[int, ...]]] = {family: set() for family in FAMILIES}
    for source in sources:
        path = root / source["relativePath"]
        document = json.loads(path.read_text(encoding="utf-8"))
        root_key = next(iter(document))
        records = document[root_key]
        keys = [(effective_key(record, source["family"]), record) for record in records]
        if source["kind"] == "removal":
            kept = []
            for key, record in keys:
                if key in effective[source["family"]]:
                    effective[source["family"]].remove(key)
                    kept.append(record)
            write_json(path, {root_key: kept})
        else:
            for key, _record in keys:
                effective[source["family"]].add(key)

    configuration = {
        "active": True,
        "assets": [
            {
                "clientRelativePath": "Client_Base/Cache/video/library.orsc",
                "role": "library",
                "serverRelativePath": "server/world-builder-fallback/library.orsc",
            }
        ],
        "clientDefinitionCatalogRelativePath": "Client_Base/world-builder-fallback/definitions.json",
        "clientMapRelativePath": "Client_Base/Cache/video/Custom_Landscape.orsc",
        "clientRuntimeRelativePath": "Client_Base/world-builder-fallback/runtime.json",
        "configurationId": "primary",
        "manifestType": "world-builder-map-configuration",
        "placements": sources,
        "representation": "packed",
        "schemaVersion": 1,
        "serverDefinitionCatalogRelativePath": "server/world-builder-fallback/definitions.json",
        "serverMapRelativePath": "server/conf/server/data/Custom_Landscape.orsc",
        "serverRuntimeRelativePath": "server/world-builder-fallback/runtime.json",
    }
    configuration_path = root / "server/world-builder-configs/primary.json"
    existing_configuration = None
    if configuration_path.is_file():
        existing_configuration = json.loads(
            configuration_path.read_text(encoding="utf-8")
        )
    if not (
        isinstance(existing_configuration, dict)
        and existing_configuration.get("active") is True
        and existing_configuration.get("representation") == "layered"
    ):
        write_json(configuration_path, configuration)

    source_roles = sorted(
        [
            "client-asset.library",
            "client-definition-catalog",
            "client-runtime",
            "client-terrain",
            "server-asset.library",
            "server-definition-catalog",
            "server-runtime",
            "server-terrain",
        ]
        + [f"placement.{item['role']}" for item in sources]
    )
    capability = {
        "adapterId": "spoiled-milk-packed-v1",
        "authoring": authoring,
        "capabilityId": CAPABILITY_ID,
        "client": {
            "buildId": "spoiled-milk-client-native-layered-v1",
            "loaderId": LOADER_ID,
            "protocolId": PROTOCOL_ID,
        },
        "definitions": {
            "catalogId": CATALOG_ID,
            "catalogSha256": catalog_hash,
        },
        "discovery": {
            "configurationRoles": ["primary"],
            "sourceRepresentations": ["packed"],
            "sourceRoles": source_roles,
        },
        "install": {
            "clientRoles": ["layered-package"],
            "configurationRoles": ["primary"],
            "enabled": True,
            "mutationProfileId": "spoiled-milk-layered-install-v1",
            "offlineEvidence": [
                "configuration-lock",
                "pid-file",
                "port-bind",
                "process-scan",
            ],
            "serverRoles": ["layered-package"],
        },
        "manifestType": "world-builder-target-capability",
        "map": {
            "encodingVersions": [1],
            "formatId": "legacy-packed-orsc-v1",
            "packageSchemaId": "layered-world-package-v1",
        },
        "schemaVersion": 1,
        "server": {
            "buildId": "spoiled-milk-server-native-layered-v1",
            "loaderId": LOADER_ID,
        },
    }
    write_json(root / "server/world-builder-capabilities.json", capability)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    generate(args.root.resolve())


if __name__ == "__main__":
    main()
