#!/usr/bin/env python3
"""Regression coverage for World Builder 2's editor-only definition catalog."""

from __future__ import annotations

import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GENERATOR = ROOT / "tools/world-builder/generate-definition-catalog.py"
CATALOG = ROOT / "dev/myworld/assets/ui/world-editor/definition-catalog-v1.tsv"
AUDIT = ROOT / "docs/myworld/info/world-builder-definition-catalog.md"
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
SCENERY_XML = ROOT / "server/conf/server/defs/GameObjectDef.xml"
BOUNDARY_XML = ROOT / "server/conf/server/defs/DoorDef.xml"


def unescape(value: str) -> str:
    output: list[str] = []
    index = 0
    replacements = {"\\": "\\", "t": "\t", "r": "\r", "n": "\n"}
    while index < len(value):
        if value[index] != "\\":
            output.append(value[index])
            index += 1
            continue
        index += 1
        if index >= len(value) or value[index] not in replacements:
            raise AssertionError(f"invalid catalog escape in {value!r}")
        output.append(replacements[value[index]])
        index += 1
    return "".join(output)


def load_catalog() -> dict[tuple[str, int], dict[str, str]]:
    lines = CATALOG.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != "# world-editor-definition-catalog-v1":
        raise AssertionError("definition catalog schema marker is missing")
    result: dict[tuple[str, int], dict[str, str]] = {}
    names = ("kind", "id", "canonical", "label", "source", "tags", "search")
    for line in lines[1:]:
        if not line or line.startswith("#"):
            continue
        fields = [unescape(field) for field in line.split("\t")]
        if len(fields) != len(names):
            raise AssertionError(f"catalog row has {len(fields)} fields instead of 7")
        row = dict(zip(names, fields))
        key = (row["kind"], int(row["id"]))
        if key in result:
            raise AssertionError(f"duplicate definition catalog row {key}")
        result[key] = row
    return result


class WorldEditorDefinitionCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rows = load_catalog()

    def test_generated_catalog_and_audit_are_current(self) -> None:
        result = subprocess.run(
            ["python3", str(GENERATOR), "--check"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Validated dev/myworld/assets/ui/world-editor/definition-catalog-v1.tsv", result.stdout)
        self.assertIn("Explicit ID fallback rows still needing semantic review", AUDIT.read_text(encoding="utf-8"))

    def test_catalog_covers_every_stable_scenery_and_boundary_id(self) -> None:
        scenery_ids = sorted(key[1] for key in self.rows if key[0] == "scenery")
        boundary_ids = sorted(key[1] for key in self.rows if key[0] == "boundary")
        self.assertEqual(list(range(1332)), scenery_ids)
        self.assertEqual(list(range(214)), boundary_ids)
        for row in self.rows.values():
            self.assertTrue(row["canonical"].strip())
            self.assertTrue(row["label"].strip())
            self.assertTrue(row["source"].strip())
            self.assertIn(row["canonical"].casefold(), row["search"].casefold())

    def test_authoritative_behavior_and_constant_examples_are_semantic(self) -> None:
        expected = {
            ("scenery", 17): ("Chest (generic, open)", "constant"),
            ("scenery", 100): ("Rock (copper)", "behavior:mining"),
            ("scenery", 104): ("Rock (tin)", "behavior:mining"),
            ("scenery", 105): ("Rock (tin)", "behavior:mining"),
            ("scenery", 193): ("Fishing spot (net / bait)", "behavior:fishing"),
            ("scenery", 223): ("Ladder (Mining Guild, down)", "constant"),
            ("scenery", 1190): ("Mysterious Ruins (air)", "behavior:runecrafting"),
            ("scenery", 1240): ("Pine Tree (festive)", "override"),
            ("boundary", 8): ("Door (Gray Bricks)", "constant"),
            ("boundary", 101): ("Fence (loose panels)", "override"),
        }
        for key, (label, source) in expected.items():
            self.assertEqual(label, self.rows[key]["label"], key)
            self.assertEqual(source, self.rows[key]["source"], key)

    def test_catalog_is_editor_only_and_runtime_fallback_is_guarded(self) -> None:
        catalog_source = (
            ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorDefinitionCatalog.java"
        ).read_text(encoding="utf-8")
        editor = (
            ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java"
        ).read_text(encoding="utf-8")
        client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")
        self.assertIn("Editor-only semantic labels", catalog_source)
        self.assertIn("!entry.canonicalName.equals(canonical)", catalog_source)
        self.assertIn("WorldEditorDefinitionCatalog.sceneryLabel(sceneryId)", editor)
        self.assertIn("WorldEditorDefinitionCatalog.boundaryLabel(raw-1)", editor)
        self.assertIn("WorldEditorDefinitionCatalog.sceneryReference", client)
        self.assertIn("WorldEditorDefinitionCatalog.boundaryReference", client)
        self.assertIn("EntityHandler.getObjectDef(id).getCommand1()", client)
        self.assertIn("EntityHandler.getDoorDef(id).getCommand1()", client)
        self.assertNotIn("WorldEditorDefinitionCatalog", SCENERY_XML.read_text(encoding="utf-8"))
        self.assertNotIn("WorldEditorDefinitionCatalog", BOUNDARY_XML.read_text(encoding="utf-8"))

    def test_floor_texture_and_wall_descriptions_are_author_facing(self) -> None:
        catalog_source = (
            ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorDefinitionCatalog.java"
        ).read_text(encoding="utf-8")
        editor = (
            ROOT / "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java"
        ).read_text(encoding="utf-8")
        self.assertIn('"Grey Road"', catalog_source)
        self.assertIn('"Invisible Path"', catalog_source)
        self.assertIn("floorTextureVisualName()", editor)
        self.assertIn("floorTextureTraversal()", editor)
        self.assertIn('return "Walkable"', editor)
        self.assertIn('?"Not Walkable":"Walkable"', editor)
        self.assertNotIn('"Floor Texture "+terrainFloorTexture', editor)
        self.assertNotIn('raw==0?"none":"#"', editor)
        self.assertNotIn('id<0?"none":"#"', editor)

    def test_compiled_client_packages_and_loads_the_catalog(self) -> None:
        self.assertTrue(CLIENT_JAR.is_file(), "build the client before running catalog runtime coverage")
        with zipfile.ZipFile(CLIENT_JAR) as archive:
            asset = "myworld-assets/ui/world-editor/definition-catalog-v1.tsv"
            self.assertIn(asset, archive.namelist())
            self.assertEqual(CATALOG.read_bytes(), archive.read(asset))

        fixture = r"""
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.interfaces.misc.WorldEditorDefinitionCatalog;

public final class WorldEditorDefinitionCatalogFixture {
    public static void main(String[] args) {
        EntityHandler.load(true);
        expect("Rock (tin)", WorldEditorDefinitionCatalog.sceneryLabel(104));
        expect("Rock (tin) [#104]", WorldEditorDefinitionCatalog.sceneryReference(104));
        expect("Ladder (Mining Guild, down)", WorldEditorDefinitionCatalog.sceneryLabel(223));
        expect("Door (Gray Bricks)", WorldEditorDefinitionCatalog.boundaryLabel(8));
        expect("Grey Road", WorldEditorDefinitionCatalog.floorTextureLabel(1));
        expect("Lava", WorldEditorDefinitionCatalog.floorTextureLabel(11));
        expect("Invisible Path", WorldEditorDefinitionCatalog.floorTextureLabel(26));
        expect("Bridge Transition", WorldEditorDefinitionCatalog.floorTextureLabel(250));
        expect("Undefined Texture", WorldEditorDefinitionCatalog.floorTextureLabel(27));
        expect("runtime mismatch", WorldEditorDefinitionCatalog.sceneryLabel(104, "runtime mismatch"));
        if (WorldEditorDefinitionCatalog.sceneryEntries().size() != 1332) {
            throw new AssertionError("wrong scenery catalog size");
        }
        if (WorldEditorDefinitionCatalog.boundaryEntries().size() != 214) {
            throw new AssertionError("wrong boundary catalog size");
        }
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
"""
        with tempfile.TemporaryDirectory(prefix="world-editor-catalog-") as temporary:
            directory = Path(temporary)
            source = directory / "WorldEditorDefinitionCatalogFixture.java"
            source.write_text(fixture, encoding="utf-8")
            compiled = subprocess.run(
                ["javac", "-cp", str(CLIENT_JAR), str(source)],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            executed = subprocess.run(
                ["java", "-cp", f"{directory}:{CLIENT_JAR}", "WorldEditorDefinitionCatalogFixture"],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, executed.returncode, executed.stdout + executed.stderr)


if __name__ == "__main__":
    unittest.main()
