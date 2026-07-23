#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "tools/layered-maps/schema"
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v49.schema.json"
PREVIOUS_SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v48.schema.json"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PREFLIGHT = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "LayeredPackedRegionSourceAbsencePreflight.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


BLOCKERS = [
    "TILE_STORAGE_UNAVAILABLE",
    "ACTIVE_PLAYER_PRESENT",
    "NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE",
    "AUTHORED_OBJECT_RELOAD_UNAVAILABLE",
    "DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE",
    "GROUND_ITEM_PRESERVATION_UNAVAILABLE",
    "COLLISION_REBUILD_UNAVAILABLE",
    "REGION_RELOAD_PATH_UNAVAILABLE",
]


def source(x, y, players, npcs, authored, dynamic, items, collision, blockers):
    return {
        "packedRegionX": x,
        "packedRegionY": y,
        "tileStorageAvailable": True,
        "playerCount": players,
        "npcCount": npcs,
        "authoredObjectCount": authored,
        "dynamicObjectCount": dynamic,
        "groundItemCount": items,
        "collisionProductTileCount": collision,
        "absenceReadyAtObservation": not blockers,
        "blockers": blockers,
    }


class LayeredMapsSliceOneHundredSeventyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$defs": {
                name: cls.schema["$defs"][name]
                for name in (
                    "npcOwnerPreservationNoOp",
                    "sourceAbsencePreflight",
                    "sourceAbsenceTotals",
                    "sourceAbsenceBlocker",
                    "sourceAbsenceBlockerSummary",
                    "sourceAbsenceSource",
                )
            },
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(isolated)
        cls.preflight = {
            "generation": 1,
            "requirementsObservedAtTick": 914,
            "observedAtTick": 915,
            "residencyMirrorVersion": 37,
            "sourceCount": 2,
            "readySourceCount": 0,
            "blockedSourceCount": 2,
            "absenceReadyAtObservation": False,
            "totals": {
                "players": 1,
                "npcs": 2,
                "authoredObjects": 3,
                "dynamicObjects": 1,
                "groundItems": 0,
                "collisionProductTiles": 5,
            },
            "blockerSummaries": [
                {
                    "blocker": blocker,
                    "blockedSourceCount": (
                        2 if blocker == "REGION_RELOAD_PATH_UNAVAILABLE"
                        else 1 if blocker in {
                            "ACTIVE_PLAYER_PRESENT",
                            "NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE",
                            "AUTHORED_OBJECT_RELOAD_UNAVAILABLE",
                            "DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE",
                            "COLLISION_REBUILD_UNAVAILABLE",
                        }
                        else 0
                    ),
                }
                for blocker in BLOCKERS
            ],
            "pointInTimeOnly": True,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "runtimeHandleRetained": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "lifecycleAuthority": False,
            "sources": [
                source(
                    4, 7, 1, 2, 3, 1, 0, 5,
                    [
                        "ACTIVE_PLAYER_PRESENT",
                        "NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE",
                        "AUTHORED_OBJECT_RELOAD_UNAVAILABLE",
                        "DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE",
                        "COLLISION_REBUILD_UNAVAILABLE",
                        "REGION_RELOAD_PATH_UNAVAILABLE",
                    ],
                ),
                source(
                    5, 7, 0, 0, 0, 0, 0, 0,
                    ["REGION_RELOAD_PATH_UNAVAILABLE"],
                ),
            ],
        }
        cls.evidence = {
            "reason": "SOURCE_LIFECYCLE_UNAVAILABLE",
            "generation": 1,
            "requirementsObservedAtTick": 914,
            "selectedSourceCount": 2,
            "requiredEventLinkCount": 2,
            "requiredOwnerCount": 2,
            "ownerScopeEntered": True,
            "sourceLifecycleInvoked": True,
            "absentSourceCount": 0,
            "reconstructedSourceCount": 0,
            "preservedConsumerInvoked": False,
            "sourceAbsencePreflight": cls.preflight,
            "preservationEstablishedForConsumedWork": False,
            "preservationPerformed": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "regionMutationPerformed": False,
            "runtimeHandleRetained": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
        }

    def test_schema_accepts_bounded_read_only_preflight(self):
        self.validator.validate(self.evidence)

        owner_refused = dict(self.evidence)
        owner_refused["reason"] = "OWNER_SCOPE_REFUSED"
        owner_refused["ownerScopeEntered"] = False
        owner_refused["sourceLifecycleInvoked"] = False
        owner_refused["sourceAbsencePreflight"] = None
        self.validator.validate(owner_refused)

        missing = dict(self.evidence)
        missing["sourceAbsencePreflight"] = None
        self.assertFalse(self.validator.is_valid(missing))

        for field in (
            "sourceAbsencePerformed",
            "sourceReconstructionPerformed",
            "runtimeHandleRetained",
            "regionRegistryMutated",
            "residencyMirrorMutated",
            "visibilityCacheMutated",
            "arrivalGate",
            "lifecycleAuthority",
        ):
            invalid = json.loads(json.dumps(self.evidence))
            invalid["sourceAbsencePreflight"][field] = True
            self.assertFalse(
                self.validator.is_valid(invalid),
                f"schema accepted forbidden preflight {field}",
            )

        invalid_blocker = json.loads(json.dumps(self.evidence))
        invalid_blocker["sourceAbsencePreflight"]["sources"][0][
            "blockers"
        ][0] = "UNKNOWN_BLOCKER"
        self.assertFalse(self.validator.is_valid(invalid_blocker))

    def test_v48_is_immutable_and_v49_extends_only_private_noop(self):
        previous = json.loads(PREVIOUS_SCHEMA.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v48",
            previous["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "sourceAbsencePreflight",
            previous["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v49",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "sourceAbsencePreflight",
            self.schema["$defs"]["npcOwnerPreservationNoOp"]["required"],
        )
        self.assertEqual(
            {
                key: value
                for key, value in previous["properties"].items()
                if key != "schema"
                and key != "packedRegionNpcOwnerPreservationNoOp"
            }.keys(),
            {
                key: value
                for key, value in self.schema["properties"].items()
                if key != "schema"
                and key != "packedRegionNpcOwnerPreservationNoOp"
            }.keys(),
        )

    def test_real_boundary_capture_reaches_metadata_and_serializer(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        preflight = PREFLIGHT.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")

        boundary = handler[handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        ):handler.index(
            "private void requireExactPackedSourceBoundary(",
            handler.index(
                "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
            ),
        )]
        self.assertIn(
            "captureLayeredPackedRegionSourceAbsencePreflight(", boundary
        )
        self.assertLess(
            boundary.index("absencePreflight[0] ="),
            boundary.index("captured[0] ="),
        )
        self.assertIn("absencePreflight[0]", boundary)
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v49"', observer
        )
        self.assertIn(
            "appendPackedRegionSourceAbsencePreflight(", observer
        )
        self.assertIn('\\"sourceAbsencePreflight\\":', observer)
        self.assertIn("getSourceAbsencePreflight()", observer)
        self.assertIn("getBlockerSummaries()", observer)
        self.assertIn("getSources()", observer)
        self.assertIn("isPointInTimeOnly() { return true; }", preflight)
        self.assertIn("layered-map-parity-event-v49.schema.json", readme)
        self.assertIn("sourceAbsencePreflight", readme)

    def test_living_plan_records_slice_one_hundred_seventy_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 175: Private source-absence preflight diagnostics",
            plan,
        )
        self.assertIn("schema-v49", plan)
        self.assertIn("dense and quiet", plan)


if __name__ == "__main__":
    unittest.main()
