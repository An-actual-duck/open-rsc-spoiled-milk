#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
LEDGER = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredRegionInterestOwnershipLedger.java"
)
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceFortyOneTest(unittest.TestCase):
    def test_v11_adds_bounded_ownership_evidence_and_retains_v10(self):
        v10_path = SCHEMA_ROOT / "layered-map-parity-event-v10.schema.json"
        v11_path = SCHEMA_ROOT / "layered-map-parity-event-v11.schema.json"
        self.assertTrue(v10_path.is_file())
        self.assertTrue(v11_path.is_file())
        v10 = json.loads(v10_path.read_text(encoding="utf-8"))
        v11 = json.loads(v11_path.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(v10)
        Draft202012Validator.check_schema(v11)

        self.assertEqual("layered-map-parity-event-v10", v10["properties"]["schema"]["const"])
        self.assertEqual("layered-map-parity-event-v11", v11["properties"]["schema"]["const"])
        self.assertNotIn("interestOwnership", v10["required"])
        expected_required = list(v10["required"])
        expected_required.insert(
            expected_required.index("roundTripExact"), "interestOwnership"
        )
        self.assertEqual(expected_required, v11["required"])
        self.assertIn("interestOwnership", v11["properties"])
        ownership = v11["$defs"]["interestOwnership"]
        self.assertFalse(ownership["additionalProperties"])
        self.assertEqual(4096, ownership["properties"]["ownedRegionCount"]["maximum"])
        self.assertEqual(8192, ownership["properties"]["transitions"]["maxItems"])

    def test_observer_carries_exact_player_changes_without_region_authority(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        ledger = LEDGER.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v18"', observer)
        self.assertIn("InterestOwnershipMetadata.fromChange(ownershipChange)", observer)
        self.assertIn(
            "state.interestOwnershipSource = currentInterestOwnershipSource;",
            observer,
        )
        self.assertIn("fromOwnerSnapshot(snapshot)", development)
        self.assertIn("player.getLayeredInterestOwnerSnapshot();", development)
        self.assertIn("OpenedOwner openOwner(", ledger)
        self.assertIn("List<OwnerReference> getReferences()", ledger)
        self.assertIn("LayeredRegionInterestOwnershipLedger.OpenedOwner", manager)
        self.assertIn("opened.getChange()", player)
        self.assertIn("ownershipChange,", player)
        self.assertIn("loggedIn ? layeredInterestOwnershipSource() : null", player)
        self.assertNotIn("LayeredRegionInterestOwnershipLedger", path_validation)

        observer_write = observer.split("private static Status write(", 1)[1]
        self.assertNotIn("getRegion(", observer_write)
        self.assertNotIn(".unload(", observer_write)
        self.assertIn("diagnostic candidates only", readme)
        self.assertIn(
            "### Slice 41: Private global-interest ownership diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
