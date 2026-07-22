#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
OBSERVATION = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventTargetObservation.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredNineteenTest(unittest.TestCase):
    def test_region_compares_and_classifies_inside_object_boundary(self):
        source = REGION.read_text(encoding="utf-8")
        start = source.index("captureRestorationTargetBoundarySnapshot(")
        end = source.index(
            "/** Exact-slot detached values", start
        )
        boundary = source[start:end]
        monitor = boundary.index("synchronized (objects)")
        slot = boundary.index("objects.get(location)", monitor)
        scenery = boundary.index(
            "checked.matchesRestorationScenery(object)", slot
        )
        identity = boundary.index(
            "checked.matchesAuthoredIdentity(object)", scenery
        )
        classify = boundary.index("RestorationTargetBoundaryState.classify(")
        held = boundary.index("Thread.holdsLock(objects)", classify)
        returned = boundary.index(
            "new RestorationTargetBoundarySnapshot(", classify
        )
        self.assertLess(monitor, slot)
        self.assertLess(slot, scenery)
        self.assertLess(scenery, identity)
        self.assertLess(identity, classify)
        self.assertLess(classify, returned)
        self.assertLess(returned, held)

    def test_region_boundary_snapshot_retains_no_runtime_handle(self):
        source = REGION.read_text(encoding="utf-8")
        start = source.index(
            "static final class RestorationTargetBoundarySnapshot"
        )
        end = source.index(
            "/** Closed state classified", start
        )
        snapshot = source[start:end]
        for forbidden in (
            "GameObject", "Multimap", "Collection", "RegionManager",
            "Object objects", "Point ", "LayeredAuthoredPlacementIdentity",
        ):
            self.assertNotIn(forbidden, snapshot)
        for required in (
            "slotObjectCount", "exactRestorationSceneryCount",
            "exactAuthoredIdentityCount", "observedTargetState",
            "objectBoundaryHeldDuringClassification",
        ):
            self.assertIn(required, snapshot)

    def test_manager_uses_inner_boundary_without_mutation_or_scheduler_lock(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "captureLayeredPackedRegionEventTargetObservation("
        )
        end = source.index(
            "Captures one strictly newer, same-tick", start
        )
        capture = source[start:end]
        for required in (
            "RestorationTargetMatchRequirement.of(",
            "captureRestorationTargetBoundarySnapshot(",
            "isObjectBoundaryHeldDuringClassification()",
            "boundary.getObservedTargetState().name()",
        ):
            self.assertIn(required, capture)
        for forbidden in (
            "GameTickEventStore", "executionLock", "timingLock",
            "registerGameObject", "unregisterGameObject",
            "replaceGameObject", ".doRun()", "event.stop()",
            "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, capture)

    def test_observation_and_plan_preserve_stale_non_authoritative_boundary(self):
        observation = OBSERVATION.read_text(encoding="utf-8")
        for required in (
            "getObjectBoundaryClassifiedTargetCount()",
            "isAvailableTargetObjectBoundaryClassificationComplete()",
            "isRuntimeTargetClassificationPerformed()",
            "isAtomicWithMutation() { return false; }",
            "isRuntimeRevalidationPerformed() { return false; }",
            "isMutationPerformed() { return false; }",
        ):
            self.assertIn(required, observation)
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 119: Region-boundary target classification", plan
        )
        self.assertIn("stale as soon as the object boundary is released", plan)


if __name__ == "__main__":
    unittest.main()
