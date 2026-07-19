#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
COMMAND = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceThirtyEightTest(unittest.TestCase):
    def test_v10_adds_bounded_region_residency_and_retains_v9_fields(self):
        v9 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v9.schema.json").read_text(
                encoding="utf-8"
            )
        )
        v10 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v10.schema.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(set(v9["required"]) | {"regionResidency"}, set(v10["required"]))
        for field in v9["required"]:
            self.assertIn(field, v10["properties"])
        self.assertEqual(
            "layered-map-parity-event-v10",
            v10["properties"]["schema"]["const"],
        )
        residency = v10["$defs"]["regionResidency"]
        self.assertEqual(
            {
                "mirrorVersion", "previousRegionCount", "currentRegionCount",
                "enteredCount", "retainedCount", "exitedCount",
                "worldSpaceChanged", "levelChanged", "noOp",
                "residentCurrentCount", "partialCurrentCount",
                "missingCurrentCount", "unsupportedCurrentCount",
                "loadCandidateCount", "releaseCandidateCount",
                "loadCandidates", "releaseCandidates", "unsupportedCurrent",
            },
            set(residency["required"]),
        )
        for field in ("loadCandidates", "releaseCandidates", "unsupportedCurrent"):
            self.assertEqual(4096, residency["properties"][field]["maxItems"])
        candidate = v10["$defs"]["regionResidencyCandidate"]
        self.assertEqual(
            {"ENTERED", "RETAINED", "EXITED"},
            set(candidate["properties"]["interestState"]["enum"]),
        )
        self.assertEqual(
            {"RESIDENT", "PARTIAL", "MISSING", "UNSUPPORTED"},
            set(candidate["properties"]["residencyState"]["enum"]),
        )

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(v10)

    def test_region_residency_capture_is_private_bounded_and_non_authoritative(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        command = COMMAND.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v11"', observer)
        self.assertIn("RegionResidencySource regionResidencySource", observer)
        self.assertIn("state.regionResidencySource.capture(", observer)
        self.assertIn("MAX_TRACE_REGIONS_PER_WINDOW", observer)
        self.assertIn('out.append(",\\\"regionResidency\\\":")', observer)
        capture = observer.split("private static boolean capturesRegionResidency", 1)[1].split(
            "private static String safeMessage", 1
        )[0]
        self.assertIn('!"move".equals(eventType)', capture)
        self.assertIn("!interestDelta.isNoOp()", capture)
        self.assertIn("layeredRegionResidencySource(player)", command)
        self.assertIn("regionManager.compareLayeredRegionInterestResidency(", command)
        self.assertIn("LayeredRegionInterestResidencyComparison", manager)
        self.assertNotIn("LayeredRegionInterestResidencyComparison", path_validation)
        self.assertNotIn("RegionResidencyMetadata", path_validation)
        self.assertNotIn("RegionResidencyMetadata", player)
        self.assertIn("layered-map-parity-event-v11.schema.json", readme)
        self.assertIn(
            "### Slice 38: Private Region residency diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
