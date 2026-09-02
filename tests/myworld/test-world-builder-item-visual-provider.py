#!/usr/bin/env python3
"""Regression coverage for Core's neutral item-visual producer contract."""

from __future__ import annotations

import importlib.util
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/item-visual-provider/export-item-visuals.py"
FULL = ROOT / "tools/item-visual-provider/generated/item-visuals-full-v1.json"
COMPATIBILITY = ROOT / "tools/item-visual-provider/generated/item-visuals-3309-3317-v1.json"
SCHEMA = ROOT / "tools/item-visual-provider/item-visual-mapping-v1.schema.json"
NPC = ROOT / "tools/item-visual-provider/generated/npc-definitions-v1.json"
NPC_SCHEMA = ROOT / "tools/item-visual-provider/npc-definitions-v1.schema.json"


def bundle_snapshot(root: Path) -> dict[str, bytes]:
    return {
        path.relative_to(root).as_posix(): path.read_bytes()
        for path in sorted(root.rglob("*"))
        if path.is_file() and not path.is_symlink()
    }


def rewrite_package_record(bundle: Path, relative: str) -> None:
    package_path = bundle / "package-manifest-v1.json"
    package = json.loads(package_path.read_text(encoding="utf-8"))
    payload = (bundle / relative).read_bytes()
    for record in package["files"]:
        if record["path"] == relative:
            record["size"] = len(payload)
            record["sha256"] = hashlib.sha256(payload).hexdigest()
            break
    else:
        raise AssertionError(f"package record not found: {relative}")
    package_path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def expect_bundle_failure(tool, bundle: Path, fragment: str) -> None:
    try:
        tool.validate_bundle(bundle)
    except tool.ExportError as failure:
        assert fragment.lower() in str(failure).lower(), str(failure)
    else:
        raise AssertionError(f"invalid bundle was accepted; expected {fragment!r}")


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
        first_npc = temp / "first-npc.json"
        second_npc = temp / "second-npc.json"
        first_parent = temp / "first-bundle"
        second_parent = temp / "second-bundle"
        tool.export(
            first_full, first_compatibility, skip_client_build=True,
            bundle_output=first_parent, npc_output=first_npc,
        )
        tool.export(
            second_full, second_compatibility, skip_client_build=True,
            bundle_output=second_parent, npc_output=second_npc,
        )
        assert first_full.read_bytes() == second_full.read_bytes(), "full export is nondeterministic"
        assert first_compatibility.read_bytes() == second_compatibility.read_bytes(), (
            "compatibility export is nondeterministic"
        )
        assert first_npc.read_bytes() == second_npc.read_bytes(), "NPC export is nondeterministic"
        assert first_full.read_bytes() == FULL.read_bytes(), "checked-in full artifact is stale"
        assert first_compatibility.read_bytes() == COMPATIBILITY.read_bytes(), (
            "checked-in compatibility artifact is stale"
        )
        assert first_npc.read_bytes() == NPC.read_bytes(), "checked-in NPC artifact is stale"
        first_bundle = first_parent / tool.BUNDLE_DIRECTORY_NAME
        second_bundle = second_parent / tool.BUNDLE_DIRECTORY_NAME
        assert bundle_snapshot(first_bundle) == bundle_snapshot(second_bundle), (
            "portable bundle generation is not byte-identical"
        )
        tool.validate_bundle(first_bundle)
        package = json.loads((first_bundle / tool.BUNDLE_PACKAGE_MANIFEST).read_text(encoding="utf-8"))
        assert [record["path"] for record in package["files"]] == sorted(
            record["path"] for record in package["files"]
        )
        assert len({record["path"].casefold() for record in package["files"]}) == len(package["files"])
        for record in package["files"]:
            asset = first_bundle / record["path"]
            assert asset.resolve().is_relative_to(first_bundle.resolve())
            assert asset.stat().st_size == record["size"]
            assert hashlib.sha256(asset.read_bytes()).hexdigest() == record["sha256"]
        bundled_full = json.loads((first_bundle / tool.BUNDLE_FULL_MANIFEST).read_text(encoding="utf-8"))
        bundled_compatibility = json.loads(
            (first_bundle / tool.BUNDLE_COMPAT_MANIFEST).read_text(encoding="utf-8")
        )
        bundled_schema = json.loads((first_bundle / tool.BUNDLE_SCHEMA).read_text(encoding="utf-8"))
        bundled_npc = json.loads((first_bundle / tool.BUNDLE_NPC_MANIFEST).read_text(encoding="utf-8"))
        bundled_npc_schema = json.loads(
            (first_bundle / tool.BUNDLE_NPC_SCHEMA).read_text(encoding="utf-8")
        )
        Draft202012Validator(bundled_schema).validate(bundled_full)
        Draft202012Validator(bundled_schema).validate(bundled_compatibility)
        Draft202012Validator(bundled_npc_schema).validate(bundled_npc)
        assert next(
            record for record in package["files"] if record["path"] == tool.BUNDLE_NPC_MANIFEST
        )["role"] == "full-npc-definition-manifest"
        external_paths = {
            record["externalPng"]["providerPath"]
            for record in bundled_full["itemVisuals"]
            if record["externalPng"] is not None
        }
        packaged_external_paths = {
            record["path"] for record in package["files"] if record["role"] == "external-png"
        }
        assert external_paths == packaged_external_paths
        for record in bundled_full["itemVisuals"]:
            if record["externalPng"] is not None:
                assert set(record["externalPng"]) == {
                    "specification", "assetName", "targetWidth", "targetHeight", "providerPath", "sha256"
                }
        assert "clientJarPath" not in bundled_full["assetProviders"]["externalPngPackaging"]

        invalid_root = temp / "invalid-cases"
        invalid_root.mkdir()

        traversal = invalid_root / "traversal" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, traversal)
        traversal_package = json.loads((traversal / tool.BUNDLE_PACKAGE_MANIFEST).read_text(encoding="utf-8"))
        traversal_package["files"][0]["path"] = "../escape"
        (traversal / tool.BUNDLE_PACKAGE_MANIFEST).write_text(
            json.dumps(traversal_package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        expect_bundle_failure(tool, traversal, "unsafe provider-relative path")

        duplicate = invalid_root / "duplicate" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, duplicate)
        duplicate_package = json.loads((duplicate / tool.BUNDLE_PACKAGE_MANIFEST).read_text(encoding="utf-8"))
        duplicate_package["files"].append(dict(duplicate_package["files"][0]))
        (duplicate / tool.BUNDLE_PACKAGE_MANIFEST).write_text(
            json.dumps(duplicate_package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        expect_bundle_failure(tool, duplicate, "duplicate")

        case_collision = invalid_root / "case" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, case_collision)
        case_package = json.loads((case_collision / tool.BUNDLE_PACKAGE_MANIFEST).read_text(encoding="utf-8"))
        collision = dict(case_package["files"][0])
        collision["path"] = collision["path"].upper()
        case_package["files"].append(collision)
        (case_collision / tool.BUNDLE_PACKAGE_MANIFEST).write_text(
            json.dumps(case_package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        expect_bundle_failure(tool, case_collision, "case-colliding")

        missing_asset = invalid_root / "missing" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, missing_asset)
        first_external = sorted(external_paths)[0]
        (missing_asset / first_external).unlink()
        expect_bundle_failure(tool, missing_asset, "inventory mismatch")

        hash_mismatch = invalid_root / "hash" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, hash_mismatch)
        changed = bytearray((hash_mismatch / first_external).read_bytes())
        changed[0] ^= 0x01
        (hash_mismatch / first_external).write_bytes(changed)
        expect_bundle_failure(tool, hash_mismatch, "sha-256 mismatch")

        stale = invalid_root / "stale" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, stale)
        stale_path = stale / tool.BUNDLE_COMPAT_MANIFEST
        stale_manifest = json.loads(stale_path.read_text(encoding="utf-8"))
        stale_manifest["itemVisuals"][0]["diagnosticName"] = "stale fixture"
        stale_manifest["selection"]["mappingSha256"] = tool.canonical_hash(stale_manifest["itemVisuals"])
        stale_path.write_text(json.dumps(stale_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        rewrite_package_record(stale, tool.BUNDLE_COMPAT_MANIFEST)
        expect_bundle_failure(tool, stale, "stale compatibility manifest")

        stale_npc = invalid_root / "stale-npc" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, stale_npc)
        stale_npc_path = stale_npc / tool.BUNDLE_NPC_MANIFEST
        stale_npc_manifest = json.loads(stale_npc_path.read_text(encoding="utf-8"))
        stale_npc_manifest["npcDefinitions"][0]["name"] = "stale fixture"
        stale_npc_path.write_text(
            json.dumps(stale_npc_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        rewrite_package_record(stale_npc, tool.BUNDLE_NPC_MANIFEST)
        expect_bundle_failure(tool, stale_npc, "stale NPC definition manifest")

        missing_npc = invalid_root / "missing-npc" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, missing_npc)
        missing_npc_path = missing_npc / tool.BUNDLE_NPC_MANIFEST
        missing_npc_manifest = json.loads(missing_npc_path.read_text(encoding="utf-8"))
        missing_npc_manifest["npcDefinitions"].pop(0)
        missing_npc_manifest["selection"]["definitionsSha256"] = tool.canonical_hash(
            missing_npc_manifest["npcDefinitions"]
        )
        missing_npc_path.write_text(
            json.dumps(missing_npc_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        rewrite_package_record(missing_npc, tool.BUNDLE_NPC_MANIFEST)
        expect_bundle_failure(tool, missing_npc, "placement coverage mismatch")

        duplicate_npc = invalid_root / "duplicate-npc" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, duplicate_npc)
        duplicate_npc_path = duplicate_npc / tool.BUNDLE_NPC_MANIFEST
        duplicate_npc_manifest = json.loads(duplicate_npc_path.read_text(encoding="utf-8"))
        duplicate_npc_manifest["npcDefinitions"].append(
            dict(duplicate_npc_manifest["npcDefinitions"][-1])
        )
        duplicate_npc_manifest["selection"]["definitionsSha256"] = tool.canonical_hash(
            duplicate_npc_manifest["npcDefinitions"]
        )
        duplicate_npc_path.write_text(
            json.dumps(duplicate_npc_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        rewrite_package_record(duplicate_npc, tool.BUNDLE_NPC_MANIFEST)
        expect_bundle_failure(tool, duplicate_npc, "duplicate or out of order")

        unresolved_visual = invalid_root / "unresolved-npc-visual" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, unresolved_visual)
        unresolved_path = unresolved_visual / tool.BUNDLE_NPC_MANIFEST
        unresolved_manifest = json.loads(unresolved_path.read_text(encoding="utf-8"))
        unresolved_manifest["animationDefinitions"][0]["customArchive"]["entry"] = "absent"
        unresolved_manifest["selection"]["animationsSha256"] = tool.canonical_hash(
            unresolved_manifest["animationDefinitions"]
        )
        unresolved_path.write_text(
            json.dumps(unresolved_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        rewrite_package_record(unresolved_visual, tool.BUNDLE_NPC_MANIFEST)
        expect_bundle_failure(tool, unresolved_visual, "missing custom archive visual")

        all_npcs, all_animations = tool.extract_final_npcs()
        custom_entries = tool.load_custom_archive_entries(tool.CUSTOM_ARCHIVE)
        authentic_entries = tool.load_authentic_archive_entries(tool.AUTHENTIC_ARCHIVE)
        original_extension = tool.NPC_EXTENSION_SOURCE
        original_placements = tool.NPC_PLACEMENT_SOURCE
        try:
            duplicate_source = temp / "duplicate-extension-definitions.json"
            extension_payload = json.loads((ROOT / original_extension).read_text(encoding="utf-8"))
            extension_payload["npcs"].append(dict(extension_payload["npcs"][0]))
            duplicate_source.write_text(
                json.dumps(extension_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            tool.NPC_EXTENSION_SOURCE = str(duplicate_source)
            try:
                tool.build_npc_manifest(all_npcs, all_animations, custom_entries, authentic_entries)
            except tool.ExportError as failure:
                assert "Duplicate NPC definitions" in str(failure)
            else:
                raise AssertionError("duplicate authoritative extension definition was accepted")

            undefined_placements = temp / "undefined-extension-placement.json"
            placement_payload = json.loads((ROOT / original_placements).read_text(encoding="utf-8"))
            placement_payload["npclocs"].append({"id": 9999})
            undefined_placements.write_text(
                json.dumps(placement_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            tool.NPC_EXTENSION_SOURCE = original_extension
            tool.NPC_PLACEMENT_SOURCE = str(undefined_placements)
            try:
                tool.build_npc_manifest(all_npcs, all_animations, custom_entries, authentic_entries)
            except tool.ExportError as failure:
                assert "Placed extension NPCs have no authoritative extension definition" in str(failure)
                assert "9999" in str(failure)
            else:
                raise AssertionError("undefined placed extension NPC was accepted")
        finally:
            tool.NPC_EXTENSION_SOURCE = original_extension
            tool.NPC_PLACEMENT_SOURCE = original_placements

        corrupt_archive = invalid_root / "corrupt" / tool.BUNDLE_DIRECTORY_NAME
        shutil.copytree(first_bundle, corrupt_archive)
        corrupt_path = corrupt_archive / tool.BUNDLE_AUTHENTIC_ARCHIVE
        corrupt_path.write_bytes(b"not a zip archive")
        for manifest_name in (tool.BUNDLE_FULL_MANIFEST, tool.BUNDLE_COMPAT_MANIFEST):
            manifest_path = corrupt_archive / manifest_name
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["assetProviders"]["authenticSpriteArchive"]["sha256"] = hashlib.sha256(
                corrupt_path.read_bytes()
            ).hexdigest()
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            rewrite_package_record(corrupt_archive, manifest_name)
        rewrite_package_record(corrupt_archive, tool.BUNDLE_AUTHENTIC_ARCHIVE)
        expect_bundle_failure(tool, corrupt_archive, "cannot read authentic sprite archive")

        if hasattr(os, "symlink"):
            symlink_bundle = invalid_root / "symlink" / tool.BUNDLE_DIRECTORY_NAME
            shutil.copytree(first_bundle, symlink_bundle)
            linked = symlink_bundle / first_external
            linked.unlink()
            linked.symlink_to(FULL)
            expect_bundle_failure(tool, symlink_bundle, "symlink")

    full = json.loads(FULL.read_text(encoding="utf-8"))
    compatibility = json.loads(COMPATIBILITY.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    npc = json.loads(NPC.read_text(encoding="utf-8"))
    npc_schema = json.loads(NPC_SCHEMA.read_text(encoding="utf-8"))
    assert schema["properties"]["schemaVersion"]["const"] == 1
    assert schema["properties"]["manifestType"]["const"] == full["manifestType"]
    Draft202012Validator(schema).validate(full)
    Draft202012Validator(schema).validate(compatibility)
    Draft202012Validator(npc_schema).validate(npc)
    assert_manifest_shape(full, list(range(3318)), "complete-final-catalog")
    assert_manifest_shape(compatibility, list(range(3309, 3318)), "filtered-compatibility")
    assert full["provider"] == compatibility["provider"]
    assert full["assetProviders"] == compatibility["assetProviders"]

    expected_npc_ids = [846, 847, 848, 849, 850, 852, 853, 854, 855, 856, 857, 858, 859, 860, 862]
    assert npc["selection"]["declarativeMaximumNpcId"] == 845
    assert npc["selection"]["placedNpcIds"] == expected_npc_ids
    assert [record["npcId"] for record in npc["npcDefinitions"]] == expected_npc_ids
    assert all(record["definitionId"] == record["npcId"] for record in npc["npcDefinitions"])
    assert npc["selection"]["placementCount"] == 18
    assert 851 not in npc["selection"]["placedNpcIds"]
    referenced_animations = sorted({
        animation_id
        for record in npc["npcDefinitions"]
        for animation_id in record["spriteAnimationIds"]
        if animation_id >= 0
    })
    assert [record["animationId"] for record in npc["animationDefinitions"]] == referenced_animations
    for animation in npc["animationDefinitions"]:
        assert animation["customArchive"]["frameCount"] >= animation["requiredFrameCount"]
        frames = animation["authenticArchive"]["frames"]
        assert len(frames) == animation["requiredFrameCount"]
        assert [frame["spriteId"] for frame in frames] == list(range(
            animation["authenticArchive"]["baseSpriteId"],
            animation["authenticArchive"]["baseSpriteId"] + animation["requiredFrameCount"],
        ))
    assert all("path" not in source for source in npc["provider"]["sources"])
    assert npc["assetProviders"]["customSpriteArchive"]["path"] == tool.BUNDLE_CUSTOM_ARCHIVE
    assert npc["assetProviders"]["authenticSpriteArchive"]["path"] == tool.BUNDLE_AUTHENTIC_ARCHIVE

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
        tool.map_item(missing, {}, {}, {}, 2150)
    except tool.ExportError as failure:
        message = str(failure)
        assert "Item 9999" in message
        assert "Missing visual fixture" in message
        assert "items:not-present" in message
        assert "no authentic entry" in message
    else:
        raise AssertionError("missing selected archive entry did not fail actionably")

    malformed = tool.FinalItem(9998, "Malformed PNG fixture", "external-png:", 0, 0, 0)
    try:
        tool.map_item(malformed, {}, {2150: "authentic-would-exist"}, {}, 2150)
    except tool.ExportError as failure:
        assert "Item 9998" in str(failure) and "malformed external PNG" in str(failure)
    else:
        raise AssertionError("malformed external PNG silently fell back to authentic art")

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
