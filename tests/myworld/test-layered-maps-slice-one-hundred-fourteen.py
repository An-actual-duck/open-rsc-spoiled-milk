#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REQUIREMENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRequirement.java"
)
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventOwnershipInventory.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
WOODCUTTING = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/skills/"
    "woodcutting/Woodcutting.java"
)
HARVESTING = ROOT / (
    "server/plugins/com/openrsc/server/plugins/custom/skills/"
    "harvesting/Harvesting.java"
)
SCHEMA_V39 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v39.schema.json"
)
SCHEMA_V40 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v40.schema.json"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredFourteenTest(unittest.TestCase):
    def test_existing_resource_replacement_retains_authored_identity(self):
        world = WORLD.read_text(encoding="utf-8")
        start = world.index("public void replaceGameObject(")
        end = world.index("public void sendKilledUpdate(", start)
        replacement = world[start:end]
        loc_transfer = (
            "_new.getLoc().assignAuthoredPlacementIdentity(authoredIdentity);"
        )
        entity_transfer = (
            "_new.assignAuthoredPlacementIdentity(authoredIdentity);"
        )
        self.assertIn(loc_transfer, replacement)
        self.assertIn(entity_transfer, replacement)
        self.assertLess(replacement.index(loc_transfer), replacement.index(
            "unregisterGameObject(old);"
        ))
        self.assertLess(replacement.index(entity_transfer), replacement.index(
            "registerGameObject(_new);"
        ))

        for source_path in (WOODCUTTING, HARVESTING):
            source = source_path.read_text(encoding="utf-8")
            self.assertIn("replaceGameObject(object, newObject);", source)
            self.assertIn("delayedSpawnObject(obj.getLoc()", source)

    def test_requirement_and_inventory_use_correct_spawn_prerequisite(self):
        corrected = "DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT"
        for source_path in (REQUIREMENT, INVENTORY):
            source = source_path.read_text(encoding="utf-8")
            self.assertIn(corrected, source)
            self.assertNotIn("DESTINATION_SLOT_EMPTY", source)
        requirement = REQUIREMENT.read_text(encoding="utf-8")
        for forbidden in (
            "getGameObject", "registerGameObject", "unregisterGameObject",
            "isTargetStateInspected() { return true; }",
            "isMutationPerformed() { return true; }",
        ):
            self.assertNotIn(forbidden, requirement)

    def test_v39_history_is_preserved_and_v40_is_corrective(self):
        v39 = json.loads(SCHEMA_V39.read_text(encoding="utf-8"))
        v40 = json.loads(SCHEMA_V40.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v39",
            v39["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v40",
            v40["properties"]["schema"]["const"],
        )
        old_rules = json.dumps(v39["$defs"]["restorationState"])
        new_rules = json.dumps(v40["$defs"]["restorationState"])
        self.assertIn("DESTINATION_SLOT_EMPTY", old_rules)
        self.assertNotIn(
            "DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT", old_rules
        )
        self.assertNotIn("DESTINATION_SLOT_EMPTY", new_rules)
        self.assertIn(
            "DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT", new_rules
        )
        self.assertIn("EXACT_AUTHORED_ENTITY_PRESENT", new_rules)

    def test_observer_and_docs_publish_only_the_corrected_rule(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v43"', observer
        )
        self.assertIn("schema/layered-map-parity-event-v40.schema.json", readme)
        self.assertIn("### Slice 114: Correct authored-transient", plan)
        for text in (readme, plan):
            self.assertIn(
                "DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT", text
            )


if __name__ == "__main__":
    unittest.main()
