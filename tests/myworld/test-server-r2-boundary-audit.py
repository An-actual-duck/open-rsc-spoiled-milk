#!/usr/bin/env python3

import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "audit-server-r2.py"
CONFIG = ROOT / "config" / "server-r2" / "ownership-rules.json"
REPORT = ROOT / "docs" / "myworld" / "info" / "server-r2-ownership-inventory.json"


def load_audit():
    spec = importlib.util.spec_from_file_location("audit_server_r2", str(SCRIPT))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ServerR2BoundaryAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit = load_audit()
        cls.config = json.loads(CONFIG.read_text(encoding="utf-8"))
        cls.report = cls.audit.build_report(cls.config)

    def test_reports_are_deterministic_and_checked_in(self):
        first = self.audit.json_text(self.report)
        second = self.audit.json_text(self.audit.build_report(self.config))
        self.assertEqual(first, second)
        self.assertEqual(first, REPORT.read_text(encoding="utf-8"))

        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--check"],
            cwd=str(ROOT),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_every_shipped_input_has_exactly_one_allowed_owner(self):
        allowed = set(self.config["categories"])
        records = self.report["inputs"]
        self.assertTrue(records)
        self.assertEqual(len(records), len({record["path"] for record in records}))
        self.assertTrue(all(record["category"] in allowed for record in records))
        self.assertEqual(
            len(records),
            sum(self.report["summary"]["categoryCounts"].values()),
        )

    def test_classification_uses_owned_paths_not_suspicious_words(self):
        classify = self.audit.classify_path
        category, _, _ = classify(
            "server/src/com/openrsc/server/net/rsc/CustomProtocol.java", self.config
        )
        self.assertEqual("foundation", category)
        category, _, _ = classify(
            "server/src/com/openrsc/server/content/production/ProductionRecipe.java",
            self.config,
        )
        self.assertEqual("unresolved", category)
        category, _, _ = classify(
            "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/Test.java",
            self.config,
        )
        self.assertEqual("spoiled-milk-content", category)

    def test_brand_signals_do_not_reclassify_foundation(self):
        server = next(
            record for record in self.report["inputs"]
            if record["path"] == "server/src/com/openrsc/server/Server.java"
        )
        self.assertEqual("foundation", server["category"])
        self.assertTrue(server["brandSignals"])

    def test_ant_build_inventory_is_the_shared_authority(self):
        build, errors = self.audit.build_audit_report()
        self.assertEqual([], errors)
        self.assertEqual(build["ant"]["source_roots"], self.report["build"]["sourceRoots"])
        self.assertEqual(build["ant"]["artifacts"], self.report["build"]["artifacts"])
        self.assertEqual(
            [library["file"] for library in build["libraries"]],
            [library["file"] for library in self.report["build"]["libraries"]],
        )
        self.assertEqual(21, self.report["summary"]["libraryJars"])

    def test_expected_server_input_families_are_present(self):
        summary = self.report["summary"]
        self.assertEqual(1078, summary["coreJavaFiles"])
        self.assertEqual(494, summary["pluginJavaFiles"])
        self.assertEqual(
            {"authentic": 389, "custom": 87, "retro": 3, "shared": 15},
            summary["pluginFamilies"],
        )
        self.assertGreater(summary["definitionInputs"], 0)
        self.assertGreater(summary["populationInputs"], 0)
        self.assertGreater(summary["databasePatchInputs"], 0)
        self.assertIn("spoiled-milk-replacement", self.report["layeredProfiles"]["profiles"])


if __name__ == "__main__":
    unittest.main()
