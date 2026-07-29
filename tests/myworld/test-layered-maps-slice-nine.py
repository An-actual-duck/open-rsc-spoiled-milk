#!/usr/bin/env python3
import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_SOURCE = ROOT / "tools/layered-maps/src"
SCHEMA = ROOT / "tools/layered-maps/schema/coordinate-owner-classification-v1.schema.json"
MAIN_CLASS = "com.openrsc.layeredmaps.LayeredMapsCli"


CLASSIFIER_FIXTURE = r"""
package com.openrsc.layeredmaps;

import java.util.Collections;

public final class CoordinateOwnerClassifierFixture {
    private static final String HASH =
        "0000000000000000000000000000000000000000000000000000000000000000";

    public static void main(String[] args) {
        check(classify(
            "client-coordinate-source",
            "Client_Base/src/orsc/graphics/gui/Menu.java",
            "packed-floor-stride",
            "int color = 13684944;").disposition.equals("signal-collision"),
            "substring collision");
        check(classify(
            "server-coordinate-source",
            "server/src/com/openrsc/server/constants/ItemId.java",
            "packed-floor-stride",
            "ITEM(944),").disposition.equals("ambiguous-literal"),
            "standalone ambiguous literal");

        assertOwner(classify(
            "client-coordinate-source",
            "Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java",
            "packed-floor-stride",
            "int plane = Math.floorDiv(worldY, 944);"),
            "client-world-presentation", "high");
        assertOwner(classify(
            "server-coordinate-source",
            "server/src/com/openrsc/server/util/rsc/Formulae.java",
            "packed-floor-stride",
            "return (y / 944) * 944;"),
            "simulation-spatial-runtime", "critical");
        assertOwner(classify(
            "server-coordinate-source",
            "server/src/com/openrsc/server/io/WorldLoader.java",
            "terrain-section-addressing",
            "int region = REGION_SIZE;"),
            "terrain-region-storage", "critical");
        assertOwner(classify(
            "server-coordinate-source",
            "server/src/com/openrsc/server/net/rsc/generators/impl/Payload.java",
            "point-construction",
            "Point.location(x, y);"),
            "protocol-session-boundary", "critical");
        assertOwner(classify(
            "server-coordinate-source",
            "server/src/com/openrsc/server/database/WorldPopulator.java",
            "point-construction",
            "Point.location(x, y);"),
            "persistence-world-bootstrap", "critical");
        assertOwner(classify(
            "server-coordinate-source",
            "server/plugins/example/Travel.java",
            "teleport-call",
            "player.teleport(x, y);"),
            "content-topology", "medium");
        assertOwner(classify(
            "builder-coordinate-source",
            "tools/world-builder/src/example/MapView.java",
            "area-bounds",
            "new Area(x, y, x2, y2);"),
            "builder-authoring", "high");

        CoordinateOwnerClassifier.Classification fallback = classify(
            "server-coordinate-source",
            "server/src/example/Unknown.java",
            "teleport-call",
            "teleport(x, y);");
        assertOwner(fallback, "manual-review", "review");
        check(fallback.confidence.equals("medium"), "fallback confidence");
        check(fallback.document().get("classificationStatus").equals("classified-unparsed"),
            "classification status");

        expectNull(() -> CoordinateOwnerClassifier.classifySource(null, ""));
        expectNull(() -> CoordinateOwnerClassifier.classifySource(source(
            "server-coordinate-source", "server/src/example/Unknown.java",
            "teleport-call", ""), null));
    }

    private static CoordinateOwnerClassifier.Classification classify(
            String role, String path, String signal, String text) {
        return CoordinateOwnerClassifier.classifySource(source(role, path, signal, text), text);
    }

    private static PreflightReport.SourceFile source(
            String role, String path, String signal, String text) {
        return new PreflightReport.SourceFile(
            role, path, text.length(), HASH, Collections.singletonList(signal));
    }

    private static void assertOwner(
            CoordinateOwnerClassifier.Classification classification,
            String family,
            String risk) {
        check(classification.disposition.equals("migration-owner"), family + " disposition");
        check(classification.primaryFamily.equals(family), family + " family");
        check(classification.migrationRisk.equals(risk), family + " risk");
        check(classification.confidence.equals(
            family.equals("manual-review") ? "medium" : "high"), family + " confidence");
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected checked refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredMapsSliceNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-nine-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / "src/com/openrsc/layeredmaps/CoordinateOwnerClassifierFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(CLASSIFIER_FIXTURE, encoding="utf-8")
        sources = sorted(str(path) for path in TOOL_SOURCE.rglob("*.java"))
        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                *sources,
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def run_normalize(self, workspace):
        return subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                "normalize",
                "--root",
                str(ROOT),
                "--workspace",
                str(workspace),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_lexical_classifier_separates_families_and_false_positive_shapes(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "com.openrsc.layeredmaps.CoordinateOwnerClassifierFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_current_owner_classification_is_complete_deterministic_and_schema_valid(self):
        with tempfile.TemporaryDirectory(prefix="layered-owner-classification-a-") as first_dir:
            with tempfile.TemporaryDirectory(prefix="layered-owner-classification-b-") as second_dir:
                status_before = subprocess.run(
                    ["git", "status", "--short"],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=True,
                ).stdout
                first = self.run_normalize(first_dir)
                second = self.run_normalize(second_dir)
                self.assertEqual(0, first.returncode, first.stderr)
                self.assertEqual(0, second.returncode, second.stderr)

                report_name = "coordinate-owner-classification.json"
                markdown_name = "coordinate-owner-classification.md"
                self.assertEqual(
                    (Path(first_dir) / report_name).read_bytes(),
                    (Path(second_dir) / report_name).read_bytes(),
                )
                self.assertEqual(
                    (Path(first_dir) / markdown_name).read_bytes(),
                    (Path(second_dir) / markdown_name).read_bytes(),
                )
                report = json.loads((Path(first_dir) / report_name).read_text(encoding="utf-8"))
                summary = report["summary"]
                self.assertEqual(214, summary["classifiedSourceOwnerCount"])
                self.assertEqual(214, len(report["owners"]))
                for count_map in (
                    summary["sourceOwnerCountByDisposition"],
                    summary["sourceOwnerCountByFamily"],
                    summary["sourceOwnerCountByRisk"],
                    summary["sourceOwnerCountByRole"],
                ):
                    self.assertEqual(214, sum(count_map.values()))
                self.assertTrue(
                    all(owner["classificationStatus"] == "classified-unparsed"
                        for owner in report["owners"])
                )

                owners = {owner["path"]: owner for owner in report["owners"]}
                self.assertEqual(
                    "terrain-region-storage",
                    owners[
                        "server/src/com/openrsc/server/model/world/region/"
                        "RegionObjectCollisionTransactionExecutor.java"
                    ]["primaryFamily"],
                )
                self.assertEqual(
                    "terrain-region-storage",
                    owners[
                        "server/src/com/openrsc/server/model/world/region/"
                        "LayeredPackedRegionBlankContainerPlan.java"
                    ]["primaryFamily"],
                )
                self.assertEqual(
                    "signal-collision",
                    owners["server/src/com/openrsc/server/util/BCrypt.java"]["disposition"],
                )
                self.assertEqual(
                    "ambiguous-literal",
                    owners["server/src/com/openrsc/server/constants/ItemId.java"]["disposition"],
                )
                self.assertEqual(
                    "client-world-presentation",
                    owners["Client_Base/src/com/openrsc/interfaces/misc/WorldEditorInterface.java"]
                    ["primaryFamily"],
                )
                self.assertEqual(
                    "terrain-region-storage",
                    owners["server/src/com/openrsc/server/io/WorldLoader.java"]["primaryFamily"],
                )
                self.assertEqual(
                    "simulation-spatial-runtime",
                    owners[
                        "server/src/com/openrsc/server/event/rsc/"
                        "GameTickEventRestorationCollisionTransactionContract.java"
                    ]["primaryFamily"],
                )
                self.assertEqual(
                    "simulation-spatial-runtime",
                    owners["server/src/com/openrsc/server/model/Point.java"]["primaryFamily"],
                )
                self.assertEqual(
                    "protocol-session-boundary",
                    owners[
                        "server/src/com/openrsc/server/net/rsc/generators/impl/Payload115Generator.java"
                    ]["primaryFamily"],
                )

                fingerprint_body = json.dumps(
                    report["owners"], sort_keys=True, separators=(",", ":"), ensure_ascii=False
                ).encode("utf-8")
                self.assertEqual(
                    hashlib.sha256(fingerprint_body).hexdigest(),
                    report["classificationFingerprintSha256"],
                )
                schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
                try:
                    import jsonschema
                except ImportError:
                    jsonschema = None
                if jsonschema is not None:
                    jsonschema.Draft202012Validator.check_schema(schema)
                    jsonschema.validate(report, schema)

                status_after = subprocess.run(
                    ["git", "status", "--short"],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=True,
                ).stdout
                self.assertEqual(status_before, status_after)


if __name__ == "__main__":
    unittest.main()
