#!/usr/bin/env python3
import copy
import json
import runpy
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "tools/layered-maps/schema"
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v60.schema.json"
SCHEMA_V59 = SCHEMA_DIR / "layered-map-parity-event-v59.schema.json"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_211 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-eleven.py"
)))


class LayeredMapsSliceTwoHundredFourteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v59 = json.loads(SCHEMA_V59.read_text(encoding="utf-8"))
        resources = []
        for path in SCHEMA_DIR.glob("*.schema.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            if "$id" in data and path != SCHEMA:
                resources.append(
                    (data["$id"], Resource.from_contents(data))
                )
        registry = Registry().with_resources(resources)
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": (
                "https://spoiled-milk.invalid/schema/"
                "layered-map-parity-event-v60-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )

        previous_test = SLICE_211[
            "LayeredMapsSliceTwoHundredElevenTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        inventory = cls.evidence[
            "sourceSchedulerBlockerFamilyInventory"
        ]
        inventory["eventExecutionContextIdentityComplete"] = True
        for family in inventory["families"]:
            family["executionContextKind"] = "NONE"
            family["executionContextName"] = None
            family["walkToActionBound"] = False
        plugin = inventory["families"][1]
        plugin["runtimeTypeName"] = (
            "com.openrsc.server.event.rsc.PluginTickEvent"
        )
        plugin["familyTypeName"] = plugin["runtimeTypeName"]
        plugin["executionContextKind"] = "PLUGIN_ENTRY_POINT"
        plugin["executionContextName"] = "Development.onCommand"

    def test_schema_v60_refines_only_private_blocker_families(self):
        self.validator.validate(self.evidence)
        self.assertEqual(
            "layered-map-parity-event-v60",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertEqual(
            set(self.v59["properties"]), set(self.schema["properties"])
        )
        old_inventory = self.v59["$defs"][
            "schedulerBlockerFamilyInventory"
        ]
        new_inventory = self.schema["$defs"][
            "schedulerBlockerFamilyInventory"
        ]
        self.assertNotIn(
            "eventExecutionContextIdentityComplete",
            old_inventory["properties"],
        )
        self.assertIn(
            "eventExecutionContextIdentityComplete",
            new_inventory["properties"],
        )
        self.assertNotIn(
            "executionContextKind",
            self.v59["$defs"]["schedulerBlockerFamily"]["properties"],
        )

    def test_execution_context_contract_is_fail_closed(self):
        invalid = copy.deepcopy(self.evidence)
        invalid["sourceSchedulerBlockerFamilyInventory"][
            "eventExecutionContextIdentityComplete"
        ] = False
        with self.assertRaises(Exception):
            self.validator.validate(invalid)

        invalid = copy.deepcopy(self.evidence)
        ordinary = invalid["sourceSchedulerBlockerFamilyInventory"][
            "families"
        ][0]
        ordinary["executionContextName"] = "unexpected"
        with self.assertRaises(Exception):
            self.validator.validate(invalid)

        invalid = copy.deepcopy(self.evidence)
        ordinary = invalid["sourceSchedulerBlockerFamilyInventory"][
            "families"
        ][0]
        ordinary["walkToActionBound"] = True
        with self.assertRaises(Exception):
            self.validator.validate(invalid)

        invalid = copy.deepcopy(self.evidence)
        plugin = invalid["sourceSchedulerBlockerFamilyInventory"][
            "families"
        ][1]
        plugin["executionContextName"] = None
        with self.assertRaises(Exception):
            self.validator.validate(invalid)

    def test_json_exposes_context_without_plugin_payloads(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v60"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v59"',
            '\\"eventExecutionContextIdentityComplete\\":',
            'field(out, "executionContextKind"',
            '\\"executionContextName\\":',
            '\\"walkToActionBound\\":',
            "getExecutionContextName()",
        ):
            self.assertIn(required, observer)
        serializer = observer.split(
            "appendPackedRegionSchedulerBlockerFamilyInventory(", 1
        )[1].split(
            "private static void appendPackedRegionEventTargets(", 1
        )[0]
        for forbidden in (
            "PluginTask ", "WalkToAction ", "ScriptContext",
            "GameTickEvent ", "Object[]", "ownerHandle",
            "schedulerHandle",
        ):
            self.assertNotIn(forbidden, serializer)

    def test_readme_and_plan_record_slice_214(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v60.schema.json", readme
        )
        self.assertIn("V60 refines those same blocker families", readme)
        self.assertIn(
            "### Slice 214: Private plugin-context blocker diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
