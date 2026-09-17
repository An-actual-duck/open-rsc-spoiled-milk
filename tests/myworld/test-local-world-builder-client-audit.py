#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "local_client_audit", ROOT / "scripts/audit-local-world-builder-client.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class AuditTest(unittest.TestCase):
    def record(self):
        return {"constants": {key: "same" for key in audit.REQUIRED}, "entries": set()}

    def test_equal_metadata_does_not_report_direct_blocker(self):
        self.assertEqual([], audit.compare(self.record(), self.record()))

    def test_missing_constants_fail_closed(self):
        candidate = self.record()
        del candidate["constants"]["CLIENT_VERSION"]
        self.assertEqual("CLIENT_VERSION", audit.compare(self.record(), candidate)[0]["check"])

    def test_current_core_replacement_blockers(self):
        installed, candidate = self.record(), self.record()
        installed["constants"].update(CLIENT_VERSION="10048", BLOCKED_VOID_PLACEMENT_ENCODING="v5")
        candidate["constants"].update(CLIENT_VERSION="10052", PLACEMENT_ENCODING="v4")
        installed["entries"].add("orsc/CurrentBaseInstalledClient.class")
        findings = audit.compare(installed, candidate)
        self.assertEqual(["CLIENT_VERSION", "placement-encoding-missing", "managed-entrypoint-missing"],
                         [item["check"] for item in findings])


if __name__ == "__main__":
    unittest.main()
