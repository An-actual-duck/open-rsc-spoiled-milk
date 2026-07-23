#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVATION = COORDINATES / (
    "LayeredPackedRegionNpcOwnerPreservationBoundaryObservation.java"
)
GAME_TICK_EVENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
)
EVENT_STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
BOUNDARY_ADAPTER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventNpcOwnerPreservationBoundary.java"
)
EVENT_HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PARITY_OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_164 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-four.py"
)))


METHODS = r'''
    private static void preservationBoundaryRemainsFailClosed(
        LayeredPackedRegionAuthoredReconstructionRecipe recipe,
        LayeredAuthoredPlacementIdentity selectedIdentity) {
        LayeredPackedRegionActiveNpcResidencyObservation clean =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 13L,
                Collections.singletonList(new NpcInstanceSnapshot(
                    selectedIdentity, 10, 4, 0, true)), 1, 1);
        LayeredPackedRegionEventOwnershipInventory inventory = inventory(
            Arrays.asList(
                ownerEvent(0, 51L, OwnerKind.NPC,
                    ownerIdentity(selectedIdentity, 10)),
                ownerEvent(1, 52L, OwnerKind.NPC,
                    ownerIdentity(selectedIdentity, 10)),
                ownerEvent(2, 53L, OwnerKind.PLAYER, null)));
        LayeredPackedRegionNpcOwnerEventContinuityAssessment continuity =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                inventory, clean, true, false, 3);
        LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
            LayeredPackedRegionNpcOwnerPreservationRequirements.derive(
                inventory, continuity, 1, 2);
        LayeredPackedRegionNpcOwnerPreservationRequirements.OwnerRequirement
            owner = requirements.getOwners().get(0);
        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
            .OwnerBoundaryState exact =
                LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                    .OwnerBoundaryState.observe(
                        owner, 2, true, true, 1, true, true);

        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
            lifecycleMissing =
                LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                    .observe(
                        requirements, 14L, true, true, 2, 2, true,
                        0, false, Collections.singletonList(exact), 1);
        check(lifecycleMissing.isReferenceBoundaryComplete()
                && lifecycleMissing.getExactReferenceOwnerCount() == 1
                && lifecycleMissing.getReason()
                    == LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                        .Reason.NPC_LIFECYCLE_BOUNDARY_INCOMPLETE
                && !lifecycleMissing.isPreservationScopeReadyAtBoundary(),
            "event and World reference fencing cannot imply NPC lifecycle fencing");

        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
            quiescenceMissing =
                LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                    .observe(
                        requirements, 14L, true, true, 2, 2, true,
                        1, false, Collections.singletonList(exact), 1);
        check(quiescenceMissing.getReason()
                    == LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                        .Reason.REGION_ABSENCE_QUIESCENCE_UNPROVED
                && !quiescenceMissing.isPreservationScopeReadyAtBoundary(),
            "owner lifecycle exclusion cannot imply Region-absence quiescence");

        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation ready =
            LayeredPackedRegionNpcOwnerPreservationBoundaryObservation.observe(
                requirements, 14L, true, true, 2, 2, true,
                1, true, Collections.singletonList(exact), 1);
        check(ready.getReason()
                    == LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                        .Reason.PRESERVATION_SCOPE_READY
                && ready.isPreservationScopeReadyAtBoundary()
                && ready.isPointInTimeOnly()
                && !ready.isPreservationFactEstablished()
                && !ready.isRuntimeHandleRetained()
                && !ready.isPreservationPerformed()
                && !ready.isEventReschedule()
                && !ready.isEntityRegistry()
                && !ready.isArrivalGate()
                && !ready.isLifecycleAuthority(),
            "even a scope-ready detached result grants no durable authority");

        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
            .OwnerBoundaryState absent =
                LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                    .OwnerBoundaryState.observe(
                        owner, 2, true, true, 0, false, false);
        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation missing =
            LayeredPackedRegionNpcOwnerPreservationBoundaryObservation.observe(
                requirements, 14L, true, true, 2, 2, true,
                0, false, Collections.singletonList(absent), 1);
        check(missing.getReason()
                    == LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                        .Reason.OWNER_REFERENCE_CORRELATION_INCOMPLETE
                && missing.getOwners().get(0).getOutcome()
                    == LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                        .OwnerOutcome.WORLD_OWNER_NOT_FOUND,
            "an absent World owner refuses before lifecycle claims");

        LayeredPackedRegionNpcOwnerPreservationBoundaryObservation mismatch =
            LayeredPackedRegionNpcOwnerPreservationBoundaryObservation.observe(
                requirements, 14L, false, false, 0, 0, false,
                0, false, Collections.emptyList(), 1);
        check(mismatch.getReason()
                    == LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
                        .Reason.SCHEDULER_INSTANCE_MISMATCH,
            "scheduler lifetime mismatch refuses all registration evidence");
        expectIllegal(() ->
            LayeredPackedRegionNpcOwnerPreservationBoundaryObservation.observe(
                requirements, 14L, true, true, 2, 2, true,
                0, false, Collections.singletonList(exact), 0));
    }
'''


def build_fixture():
    fixture = SLICE_164["build_fixture"]()
    fixture = fixture.replace(
        "        incompleteNpcEvidenceRemainsIncomplete(\n"
        "            recipe, selectedIdentity);",
        "        incompleteNpcEvidenceRemainsIncomplete(\n"
        "            recipe, selectedIdentity);\n"
        "        preservationBoundaryRemainsFailClosed(\n"
        "            recipe, selectedIdentity);",
    )
    fixture = fixture.replace(
        "    private static int countClassification(",
        METHODS + "\n    private static int countClassification(",
    )
    return fixture


class LayeredMapsSliceOneHundredSixtyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-preservation-boundary-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(
            SLICE_164["SLICE_161"]["SLICE_71"]["POINT_STUB"],
            encoding="utf-8",
        )
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcResidencyFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(build_fixture(), encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_owner_preservation_boundary_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "ActiveNpcResidencyFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_boundary_nests_and_revalidates_without_authority(self):
        observation = OBSERVATION.read_text(encoding="utf-8")
        event = GAME_TICK_EVENT.read_text(encoding="utf-8")
        store = EVENT_STORE.read_text(encoding="utf-8")
        adapter = BOUNDARY_ADAPTER.read_text(encoding="utf-8")
        handler = EVENT_HANDLER.read_text(encoding="utf-8")
        parity = PARITY_OBSERVER.read_text(encoding="utf-8")

        self.assertIn("withinRunningOwnerPreservationLifecycleBoundary", event)
        self.assertIn("synchronized (timingLock)", event)
        self.assertIn("isExecutionBoundaryHeldByCurrentThread()", event)
        self.assertIn("if (!running)", event)
        self.assertIn("withinExecutionBoundary", store)
        self.assertIn(
            "withinRunningOwnerPreservationLifecycleBoundary",
            adapter,
        )
        self.assertIn("withValidatedRegistrationFence", adapter)
        self.assertIn("getTrackedEventRegistrationSnapshot", adapter)
        self.assertIn("synchronized (worldNpcs)", adapter)
        self.assertIn("Thread.holdsLock(worldNpcs)", adapter)
        self.assertIn("captureOwnerCorrelation", adapter)
        self.assertNotIn(
            "com.openrsc.server.model.world.coordinate",
            store,
        )
        self.assertNotIn("EntityList<Npc>", store)
        self.assertIn(
            "captureLayeredPackedRegionNpcOwnerPreservationBoundary",
            handler,
        )
        self.assertNotIn(
            "captureLayeredPackedRegionNpcOwnerPreservationBoundary",
            parity,
        )
        for source in (observation, event, store, adapter):
            self.assertNotIn("event.run()", source)
            self.assertNotIn("registerNpc(", source)
            self.assertNotIn("unregisterNpc(", source)
        self.assertIn(
            "isPreservationFactEstablished() { return false; }",
            observation,
        )
        self.assertIn(
            "isRuntimeHandleRetained() { return false; }",
            observation,
        )

    def test_living_plan_records_slice_one_hundred_sixty_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 165: Nested NPC owner reference boundary",
            plan,
        )
        self.assertIn("stable registration order", plan)
        self.assertIn("NPC lifecycle", plan)


if __name__ == "__main__":
    unittest.main()
