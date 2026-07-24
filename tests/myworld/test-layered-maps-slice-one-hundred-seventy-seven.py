#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "tools/layered-maps/schema"
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v50.schema.json"
PREVIOUS_SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v49.schema.json"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
RELOAD_RECIPE = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "LayeredPackedRegionReloadRecipe.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


def absence_source(x, y, players, npcs, authored, collision, blockers):
    return {
        "packedRegionX": x,
        "packedRegionY": y,
        "tileStorageAvailable": True,
        "playerCount": players,
        "npcCount": npcs,
        "authoredObjectCount": authored,
        "dynamicObjectCount": 0,
        "groundItemCount": 0,
        "collisionProductTileCount": collision,
        "absenceReadyAtObservation": not blockers,
        "blockers": blockers,
    }


def reload_source(x, y, declared, placements, collision):
    return {
        "packedRegionX": x,
        "packedRegionY": y,
        "tileStorageAvailableAtObservation": True,
        "playerCountAtObservation": 1 if x == 4 else 0,
        "npcCountAtObservation": 2 if x == 4 else 0,
        "dynamicObjectCountAtObservation": 0,
        "groundItemCountAtObservation": 0,
        "collisionProductTileCountAtObservation": collision,
        "authoredSourceDeclared": declared,
        "emptyAuthoredReplay": placements == 0,
        "manifestPlacementCount": placements,
        "supersededPlacementCount": 0,
        "authoredPlacementCount": placements,
        "affectedSourceReferenceCount": placements,
    }


class LayeredMapsSliceOneHundredSeventySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.previous = json.loads(
            PREVIOUS_SCHEMA.read_text(encoding="utf-8")
        )
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": (
                "https://spoiled-milk.invalid/schema/"
                "layered-map-parity-event-v50-isolated.schema.json"
            ),
            "$defs": {
                name: cls.schema["$defs"][name]
                for name in (
                    "npcOwnerPreservationNoOp",
                    "sourceReloadRecipe",
                    "sourceReloadTotals",
                    "sourceReloadSource",
                )
            },
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        registry = Registry().with_resources([
            (
                cls.previous["$id"],
                Resource.from_contents(cls.previous),
            ),
        ])
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )
        blockers = [
            "ACTIVE_PLAYER_PRESENT",
            "NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE",
            "AUTHORED_OBJECT_RELOAD_UNAVAILABLE",
            "COLLISION_REBUILD_UNAVAILABLE",
            "REGION_RELOAD_PATH_UNAVAILABLE",
        ]
        preflight = {
            "generation": 9,
            "requirementsObservedAtTick": 12,
            "observedAtTick": 14,
            "residencyMirrorVersion": 17,
            "sourceCount": 2,
            "readySourceCount": 0,
            "blockedSourceCount": 2,
            "absenceReadyAtObservation": False,
            "totals": {
                "players": 1,
                "npcs": 2,
                "authoredObjects": 1,
                "dynamicObjects": 0,
                "groundItems": 0,
                "collisionProductTiles": 5,
            },
            "blockerSummaries": [
                {
                    "blocker": blocker,
                    "blockedSourceCount": (
                        2 if blocker == "REGION_RELOAD_PATH_UNAVAILABLE"
                        else 1 if blocker in blockers else 0
                    ),
                }
                for blocker in (
                    "TILE_STORAGE_UNAVAILABLE",
                    "ACTIVE_PLAYER_PRESENT",
                    "NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE",
                    "AUTHORED_OBJECT_RELOAD_UNAVAILABLE",
                    "DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE",
                    "GROUND_ITEM_PRESERVATION_UNAVAILABLE",
                    "COLLISION_REBUILD_UNAVAILABLE",
                    "REGION_RELOAD_PATH_UNAVAILABLE",
                )
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
                absence_source(4, 7, 1, 2, 1, 5, blockers),
                absence_source(
                    5, 7, 0, 0, 0, 0,
                    ["REGION_RELOAD_PATH_UNAVAILABLE"],
                ),
            ],
        }
        recipe = {
            "generation": 9,
            "requirementsObservedAtTick": 12,
            "observedAtTick": 14,
            "residencyMirrorVersion": 17,
            "authoredGeneration": 9,
            "sourceCount": 2,
            "authoredSourceCount": 1,
            "emptyAuthoredSourceCount": 1,
            "authoredPlacementCount": 1,
            "manifestPlacementCount": 1,
            "supersededPlacementCount": 0,
            "affectedSourceReferenceCount": 1,
            "unresolvedTotals": {
                "players": 1,
                "npcs": 2,
                "dynamicObjects": 0,
                "groundItems": 0,
                "collisionProductTiles": 5,
            },
            "pointInTimeOnly": True,
            "detachedDefinitionComplete": True,
            "executableReload": False,
            "regionContainerCreated": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "authoredReplayPerformed": False,
            "collisionRebuildPerformed": False,
            "runtimeHandleRetained": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "lifecycleAuthority": False,
            "sources": [
                reload_source(4, 7, True, 1, 5),
                reload_source(5, 7, False, 0, 0),
            ],
        }
        cls.evidence = {
            "reason": "SOURCE_LIFECYCLE_UNAVAILABLE",
            "generation": 9,
            "requirementsObservedAtTick": 12,
            "selectedSourceCount": 2,
            "requiredEventLinkCount": 3,
            "requiredOwnerCount": 2,
            "ownerScopeEntered": True,
            "sourceLifecycleInvoked": True,
            "absentSourceCount": 0,
            "reconstructedSourceCount": 0,
            "preservedConsumerInvoked": False,
            "sourceAbsencePreflight": preflight,
            "sourceReloadRecipe": recipe,
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

    def test_schema_accepts_only_inert_aligned_recipe_shape(self):
        self.validator.validate(self.evidence)

        owner_refused = dict(self.evidence)
        owner_refused["reason"] = "OWNER_SCOPE_REFUSED"
        owner_refused["ownerScopeEntered"] = False
        owner_refused["sourceLifecycleInvoked"] = False
        owner_refused["sourceAbsencePreflight"] = None
        owner_refused["sourceReloadRecipe"] = None
        self.validator.validate(owner_refused)

        missing = dict(self.evidence)
        missing["sourceReloadRecipe"] = None
        self.assertFalse(self.validator.is_valid(missing))

        for field in (
            "executableReload",
            "regionContainerCreated",
            "sourceAbsencePerformed",
            "sourceReconstructionPerformed",
            "authoredReplayPerformed",
            "collisionRebuildPerformed",
            "runtimeHandleRetained",
            "regionRegistryMutated",
            "residencyMirrorMutated",
            "visibilityCacheMutated",
            "arrivalGate",
            "lifecycleAuthority",
        ):
            invalid = json.loads(json.dumps(self.evidence))
            invalid["sourceReloadRecipe"][field] = True
            self.assertFalse(
                self.validator.is_valid(invalid),
                f"schema accepted forbidden reload-recipe {field}",
            )

        invalid_empty = json.loads(json.dumps(self.evidence))
        invalid_empty["sourceReloadRecipe"]["sources"][1][
            "authoredPlacementCount"
        ] = 1
        self.assertFalse(self.validator.is_valid(invalid_empty))

    def test_v49_is_immutable_and_v50_extends_only_private_noop(self):
        self.assertEqual(
            "layered-map-parity-event-v49",
            self.previous["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "sourceReloadRecipe",
            self.previous["$defs"]["npcOwnerPreservationNoOp"][
                "properties"
            ],
        )
        self.assertEqual(
            "layered-map-parity-event-v50",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "sourceReloadRecipe",
            self.schema["$defs"]["npcOwnerPreservationNoOp"]["required"],
        )
        current_keys = set(self.schema["properties"])
        previous_keys = set(self.previous["properties"])
        self.assertEqual(previous_keys, current_keys)

    def test_real_boundary_recipe_reaches_metadata_and_serializer(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        recipe = RELOAD_RECIPE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertIn(
            "captureLayeredPackedRegionReloadRecipe(", boundary
        )
        self.assertLess(
            boundary.index("absencePreflight[0] ="),
            boundary.index("reloadRecipe[0] ="),
        )
        self.assertLess(
            boundary.index("reloadRecipe[0] ="),
            boundary.index("captured[0] ="),
        )
        self.assertIn("reloadRecipe[0]", boundary)
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v52"', observer
        )
        self.assertIn(
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v51"',
            observer,
        )
        self.assertIn(
            "appendPackedRegionSourceReloadRecipe(", observer
        )
        self.assertIn('\\"sourceReloadRecipe\\":', observer)
        self.assertIn("getSourceReloadRecipe()", observer)
        self.assertIn("getSources()", observer)
        self.assertIn("isExecutableReload() { return false; }", recipe)
        self.assertIn("layered-map-parity-event-v50.schema.json", readme)
        self.assertIn("sourceReloadRecipe", readme)

    def test_living_plan_records_slice_one_hundred_seventy_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 177: Private exact reload-recipe diagnostics",
            plan,
        )
        self.assertIn("schema-v50", plan)


if __name__ == "__main__":
    unittest.main()
