#!/usr/bin/env python3

import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "audit-server-r2.py"
BASELINE = ROOT / "config" / "server-r2" / "foundation-content-dependencies.json"


def load_audit():
    spec = importlib.util.spec_from_file_location("audit_server_r2_guard", str(SCRIPT))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ServerR2DependencyGuardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit = load_audit()

    def test_exact_baseline_accepts_existing_debt_and_removals(self):
        old = {"source": "foundation/A.java", "target": "content/B.java", "import": "content.B"}
        self.assertEqual([], self.audit.new_foundation_content_dependencies([old], [old]))
        self.assertEqual([], self.audit.new_foundation_content_dependencies([], [old]))

    def test_new_or_redirected_content_dependency_fails(self):
        old = {"source": "foundation/A.java", "target": "content/B.java", "import": "content.B"}
        added = {"source": "foundation/A.java", "target": "content/C.java", "import": "content.C"}
        self.assertEqual(
            [added],
            self.audit.new_foundation_content_dependencies([old, added], [old]),
        )

    def test_checked_baseline_matches_current_exact_edges(self):
        report = self.audit.build_report()
        baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
        self.assertEqual(
            [],
            self.audit.new_foundation_content_dependencies(
                report["foundationToSpoiledMilkDependencies"],
                baseline["dependencies"],
            ),
        )
        self.assertGreater(len(baseline["dependencies"]), 0)
        self.assertEqual(report["evidenceBaseCommit"], baseline["evidenceBaseCommit"])


if __name__ == "__main__":
    unittest.main()
