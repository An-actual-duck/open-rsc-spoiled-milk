#!/usr/bin/env python3
import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_SOURCE = ROOT / "tools/layered-maps/src"
SCHEMA = ROOT / "tools/layered-maps/schema/java-coordinate-occurrence-inventory-v1.schema.json"
MAIN_CLASS = "com.openrsc.layeredmaps.LayeredMapsCli"


SCANNER_FIXTURE = r'''
package com.openrsc.layeredmaps;

import java.util.List;

public final class JavaCoordinateOccurrenceFixture {
    public static void main(String[] args) throws Exception {
        String source =
            "class Fixture {\n"
            + "  // teleport(999, 999);\n"
            + "  String ignored = \"Point.location(8, 9)\";\n"
            + "  void run() {\n"
            + "    player.teleport(100, 400);\n"
            + "    Point.location(foo(1, 2), ys[3]);\n"
            + "    new Area(-10, 20, 30, 40);\n"
            + "    area.inBounds(Point.location(1, 2));\n"
            + "  }\n"
            + "  void teleport() {}\n"
            + "}\n";
        List<JavaCoordinateOccurrenceInventory.Occurrence> occurrences =
            JavaCoordinateOccurrenceInventory.scan(source);
        check(occurrences.size() == 6, "occurrence count");
        check(count(occurrences, "teleport") == 2, "teleport count");
        check(count(occurrences, "point-construction") == 2, "point count");
        check(count(occurrences, "area-construction") == 1, "area construction count");
        check(count(occurrences, "area-check") == 1, "area check count");

        JavaCoordinateOccurrenceInventory.Occurrence literalTeleport = occurrences.get(0);
        check(literalTeleport.line == 5, "literal teleport line");
        check(literalTeleport.argumentShape.equals("all-integer-literals"), "literal shape");
        check(literalTeleport.arguments.get(0).equals("100"), "first literal");
        check(literalTeleport.arguments.get(1).equals("400"), "second literal");

        JavaCoordinateOccurrenceInventory.Occurrence expressionPoint = occurrences.get(1);
        check(expressionPoint.argumentShape.equals("expression-bearing"), "expression shape");
        check(expressionPoint.arguments.size() == 2, "nested comma split");
        check(expressionPoint.arguments.get(0).equals("foo(1, 2)"), "nested call preserved");
        check(expressionPoint.arguments.get(1).equals("ys[3]"), "array expression preserved");

        JavaCoordinateOccurrenceInventory.Occurrence declaration = occurrences.get(5);
        check(declaration.argumentShape.equals("no-arguments"), "declaration remains lexical");
        check(declaration.line == 10, "declaration line");

        check(JavaCoordinateOccurrenceInventory.scan(
            "class Empty { String value = \"teleport(1,2)\"; /* new Area(1,2,3,4) */ }")
            .isEmpty(), "comments and strings masked");
        expectRefusal(() -> JavaCoordinateOccurrenceInventory.scan(
            "class Broken { void x() { teleport(1, 2); /*"));
        expectNull(() -> JavaCoordinateOccurrenceInventory.scan(null));
    }

    private static int count(
            List<JavaCoordinateOccurrenceInventory.Occurrence> occurrences, String kind) {
        int count = 0;
        for (JavaCoordinateOccurrenceInventory.Occurrence occurrence : occurrences) {
            if (occurrence.kind.equals(kind)) {
                count++;
            }
        }
        return count;
    }

    private static void expectRefusal(CheckedOperation operation) {
        try {
            operation.run();
            throw new AssertionError("Expected PreflightException");
        } catch (PreflightException expected) {
            // Expected lexical refusal.
        }
    }

    private static void expectNull(CheckedOperation operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected checked refusal.
        } catch (PreflightException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private interface CheckedOperation {
        void run() throws PreflightException;
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceTenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-ten-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / "src/com/openrsc/layeredmaps/JavaCoordinateOccurrenceFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(SCANNER_FIXTURE, encoding="utf-8")
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
                *(str(path) for path in sorted(TOOL_SOURCE.rglob("*.java"))),
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

    def test_scanner_preserves_balanced_arguments_and_masks_non_code(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "com.openrsc.layeredmaps.JavaCoordinateOccurrenceFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_real_content_inventory_is_deterministic_complete_and_schema_valid(self):
        with tempfile.TemporaryDirectory(prefix="layered-occurrences-a-") as first_dir:
            with tempfile.TemporaryDirectory(prefix="layered-occurrences-b-") as second_dir:
                status_before = subprocess.run(
                    ["git", "status", "--short"], cwd=ROOT, text=True,
                    capture_output=True, check=True
                ).stdout
                first = self.run_normalize(first_dir)
                second = self.run_normalize(second_dir)
                self.assertEqual(0, first.returncode, first.stderr)
                self.assertEqual(0, second.returncode, second.stderr)
                json_name = "java-coordinate-occurrences.json"
                markdown_name = "java-coordinate-occurrences.md"
                self.assertEqual(
                    (Path(first_dir) / json_name).read_bytes(),
                    (Path(second_dir) / json_name).read_bytes(),
                )
                self.assertEqual(
                    (Path(first_dir) / markdown_name).read_bytes(),
                    (Path(second_dir) / markdown_name).read_bytes(),
                )

                report = json.loads((Path(first_dir) / json_name).read_text(encoding="utf-8"))
                summary = report["summary"]
                self.assertEqual(135, summary["contentTopologySourceCount"])
                self.assertEqual(135, summary["sourceWithOccurrenceCount"])
                self.assertEqual(0, summary["sourceWithoutOccurrenceCount"])
                self.assertEqual(1286, summary["occurrenceCount"])
                self.assertEqual(
                    {"area-check": 37, "area-construction": 5,
                     "point-construction": 341, "teleport": 903},
                    summary["occurrenceCountByKind"],
                )
                self.assertEqual(
                    {"all-integer-literals": 783, "expression-bearing": 499,
                     "no-arguments": 4},
                    summary["occurrenceCountByArgumentShape"],
                )
                self.assertEqual(135, len(report["sources"]))
                self.assertEqual(
                    1286,
                    sum(source["occurrenceCount"] for source in report["sources"]),
                )

                flat = [
                    occurrence
                    for source in report["sources"]
                    for occurrence in source["occurrences"]
                ]
                fingerprint_body = json.dumps(
                    flat, sort_keys=True, separators=(",", ":"), ensure_ascii=False
                ).encode("utf-8")
                self.assertEqual(
                    hashlib.sha256(fingerprint_body).hexdigest(),
                    report["occurrenceFingerprintSha256"],
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
                    ["git", "status", "--short"], cwd=ROOT, text=True,
                    capture_output=True, check=True
                ).stdout
                self.assertEqual(status_before, status_after)


if __name__ == "__main__":
    unittest.main()
