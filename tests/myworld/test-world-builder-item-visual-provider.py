#!/usr/bin/env python3
"""Regression coverage for Core's neutral item-visual producer contract."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/item-visual-provider/export-item-visuals.py"
FULL = ROOT / "tools/item-visual-provider/generated/item-visuals-full-v1.json"
COMPATIBILITY = ROOT / "tools/item-visual-provider/generated/item-visuals-3309-3317-v1.json"
SCHEMA = ROOT / "tools/item-visual-provider/item-visual-mapping-v1.schema.json"


def load_tool():
    spec = importlib.util.spec_from_file_location("item_visual_provider", TOOL)
    if spec is None or spec.loader is None:
        raise AssertionError(f"Unable to load exporter module {TOOL}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def assert_manifest_shape(payload: dict, expected_ids: list[int], expected_kind: str) -> None:
    assert payload["schemaVersion"] == 1
    assert payload["manifestType"] == "world-builder-item-visual-mapping"
    assert payload["selection"]["kind"] == expected_kind
    assert payload["selection"]["itemCount"] == len(expected_ids)
    visuals = payload["itemVisuals"]
    assert [entry["itemId"] for entry in visuals] == expected_ids
    assert payload["provider"]["catalogItemCount"] == 3318
    assert len(payload["provider"]["catalogSha256"]) == 64
    assert len(payload["selection"]["mappingSha256"]) == 64
    for entry in visuals:
        assert list(entry) == [
            "itemId",
            "diagnosticName",
            "spriteLocation",
            "spriteId",
            "resolvedBaseSourceRole",
            "authenticArchive",
            "customOrSpritepack",
            "externalPng",
            "pictureMask",
            "blueMask",
        ]
        assert isinstance(entry["pictureMask"], int)
        assert isinstance(entry["blueMask"], int)
        assert entry["resolvedBaseSourceRole"] in {
            "custom-sprite-archive",
            "external-png",
            "authentic-archive-fallback",
        }


def main() -> None:
    tool = load_tool()
    subprocess.run([str(ROOT / "scripts/build-client.sh")], cwd=ROOT, check=True)
    with tempfile.TemporaryDirectory(prefix="item-visual-provider-test-") as temporary:
        temp = Path(temporary)
        first_full = temp / "first-full.json"
        first_compatibility = temp / "first-compatibility.json"
        second_full = temp / "second-full.json"
        second_compatibility = temp / "second-compatibility.json"
        tool.export(first_full, first_compatibility, skip_client_build=True)
        tool.export(second_full, second_compatibility, skip_client_build=True)
        assert first_full.read_bytes() == second_full.read_bytes(), "full export is nondeterministic"
        assert first_compatibility.read_bytes() == second_compatibility.read_bytes(), (
            "compatibility export is nondeterministic"
        )
        assert first_full.read_bytes() == FULL.read_bytes(), "checked-in full artifact is stale"
        assert first_compatibility.read_bytes() == COMPATIBILITY.read_bytes(), (
            "checked-in compatibility artifact is stale"
        )

    full = json.loads(FULL.read_text(encoding="utf-8"))
    compatibility = json.loads(COMPATIBILITY.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    assert schema["properties"]["schemaVersion"]["const"] == 1
    assert schema["properties"]["manifestType"]["const"] == full["manifestType"]
    assert_manifest_shape(full, list(range(3318)), "complete-final-catalog")
    assert_manifest_shape(compatibility, list(range(3309, 3318)), "filtered-compatibility")
    assert full["provider"] == compatibility["provider"]
    assert full["assetProviders"] == compatibility["assetProviders"]

    compat_by_id = {entry["itemId"]: entry for entry in compatibility["itemVisuals"]}
    dagger = compat_by_id[3309]
    assert dagger["itemId"] != dagger["spriteId"] == 80
    assert dagger["spriteLocation"] == "items:80"
    assert dagger["authenticArchive"]["archiveId"] == 2230
    assert dagger["customOrSpritepack"]["subspace"] == "items"
    assert dagger["customOrSpritepack"]["entry"] == "80"
    assert dagger["pictureMask"] == 14221311
    assert dagger["blueMask"] == 0
    chaps = compat_by_id[3316]
    assert chaps["spriteId"] == 590
    assert chaps["customOrSpritepack"]["entry"] == "590"
    assert chaps["authenticArchive"] is None

    external = full["itemVisuals"][3228]
    assert external["diagnosticName"] == "Dragon Metal Scrap"
    assert external["resolvedBaseSourceRole"] == "external-png"
    assert external["externalPng"]["specification"] == "raw-dragon-metal@43x27"
    assert external["externalPng"]["targetWidth"] == 43
    assert external["externalPng"]["targetHeight"] == 27
    assert external["externalPng"]["packagedResource"].endswith("/raw-dragon-metal.png")
    assert len(external["externalPng"]["sha256"]) == 64

    clamped = tool.parse_external_spec("external-png:geode@999x0")
    assert (clamped.target_width, clamped.target_height) == (46, 1)

    missing = tool.FinalItem(9999, "Missing visual fixture", "items:not-present", -1, 0, 0)
    try:
        tool.map_item(missing, {}, {}, {})
    except tool.ExportError as failure:
        message = str(failure)
        assert "Item 9999" in message
        assert "Missing visual fixture" in message
        assert "items:not-present" in message
        assert "no authentic entry" in message
    else:
        raise AssertionError("missing selected archive entry did not fail actionably")

    missing_png = tool.ExternalSpec("absent@12x12", "absent", "absent.png", 12, 12)
    try:
        tool.resolve_external_asset(missing_png, {})
    except tool.ExportError as failure:
        assert "absent@12x12" in str(failure) and "Searched:" in str(failure)
    else:
        raise AssertionError("missing external PNG did not fail actionably")

    print("PASS: final client item visuals export deterministically with validated provider assets")


if __name__ == "__main__":
    main()
