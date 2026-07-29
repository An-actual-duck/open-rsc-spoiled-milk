#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ASSESSMENT = COORDINATES / (
    "LayeredPackedRegionNpcOwnerEventContinuityAssessment.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_71 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-seventy-one.py"
)))


IMPORTS = r'''
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerEventContinuityAssessment.Outcome;
'''


METHODS = r'''
    private static void ownerEventContinuityRemainsPreservationGated(
        LayeredPackedRegionAuthoredReconstructionRecipe recipe,
        LayeredAuthoredPlacementIdentity selectedIdentity) {
        LayeredPackedRegionActiveNpcResidencyObservation clean =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 13L,
                Collections.singletonList(new NpcInstanceSnapshot(
                    selectedIdentity, 10, 4, 0, true)), 1, 1);
        LayeredPackedRegionEventOwnershipInventory mixed = inventory(
            Arrays.asList(
                ownerEvent(0, 11L, OwnerKind.NPC,
                    ownerIdentity(selectedIdentity, 10)),
                ownerEvent(1, 12L, OwnerKind.NPC, null),
                ownerEvent(2, 13L, OwnerKind.PLAYER, null)));
        LayeredPackedRegionNpcOwnerEventContinuityAssessment blocked =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                mixed, clean, true, false, 3);
        check(blocked.getGeneration() == 9L
                && blocked.getEventObservedAtTick() == 12L
                && blocked.getCensusObservedAtTick() == 13L
                && blocked.getSelectedSourceCount() == 1
                && blocked.getProposalRelatedEventCount() == 3
                && blocked.getRelatedOwnerPositionHintEventCount() == 3
                && blocked.getNpcOwnerPositionHintEventCount() == 2
                && blocked.getCapturedNpcOwnerIdentityCount() == 1
                && blocked.getUniquelyMatchedActiveOwnerCount() == 1
                && blocked.getContinuityEligibleEventCount() == 0
                && blocked.getPreservationUnprovedEventCount() == 1
                && blocked.getHardBlockerEventCount() == 2
                && blocked.isExactSelectionAligned()
                && !blocked.isOwnerPreservationProved()
                && blocked.getFirstUnmetRegistrationSequence()
                    .longValue() == 11L
                && blocked.getFirstUnmetOutcome()
                    == Outcome.OWNER_PRESERVATION_UNPROVED
                && blocked.getEvents().get(1).getOutcome()
                    == Outcome.NPC_OWNER_IDENTITY_UNAVAILABLE
                && blocked.getEvents().get(2).getOutcome()
                    == Outcome.NON_NPC_OWNER
                && !blocked.isAllRelatedOwnerContinuityReadyAtObservation(),
            "exact active owner match remains blocked without preservation");

        LayeredPackedRegionNpcOwnerEventContinuityAssessment eligible =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                inventory(Collections.singletonList(ownerEvent(
                    0, 21L, OwnerKind.NPC,
                    ownerIdentity(selectedIdentity, 10)))),
                clean, true, true, 1);
        check(eligible.getContinuityEligibleEventCount() == 1
                && eligible.getPreservationUnprovedEventCount() == 0
                && eligible.getHardBlockerEventCount() == 0
                && eligible.isOwnerPreservationProved()
                && eligible.getFirstUnmetRegistrationSequence() == null
                && eligible.getFirstUnmetOutcome() == null
                && eligible.isAllRelatedOwnerContinuityReadyAtObservation()
                && eligible.isPointInTimeOnly()
                && !eligible.isRuntimeHandleRetained()
                && !eligible.isPreservationPerformed()
                && !eligible.isEventReschedule()
                && !eligible.isEntityRegistry()
                && !eligible.isArrivalGate()
                && !eligible.isLifecycleAuthority(),
            "caller-supplied preservation fact changes policy, not authority");

        expectIllegal(() ->
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                mixed, clean, false, false, 3));
        expectIllegal(() ->
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                mixed, clean, true, false, 2));
    }

    private static void ambiguousAndStaleOwnersRefuse(
        LayeredPackedRegionAuthoredReconstructionRecipe recipe,
        LayeredAuthoredPlacementIdentity selectedIdentity) {
        LayeredPackedRegionActiveNpcResidencyObservation duplicate =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 13L,
                Arrays.asList(
                    new NpcInstanceSnapshot(
                        selectedIdentity, 10, 4, 0, true),
                    new NpcInstanceSnapshot(
                        selectedIdentity, 10, 4, 0, true)),
                2, 2);
        LayeredPackedRegionNpcOwnerEventContinuityAssessment ambiguous =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                inventory(Collections.singletonList(ownerEvent(
                    0, 31L, OwnerKind.NPC,
                    ownerIdentity(selectedIdentity, 10)))),
                duplicate, true, false, 1);
        check(ambiguous.getEvents().get(0).getOutcome()
                    == Outcome.ACTIVE_OWNER_AMBIGUOUS
                && ambiguous.getEvents().get(0)
                    .getActiveIdentityMatchCount() == 2
                && ambiguous.getHardBlockerEventCount() == 1,
            "duplicate active identity is never guessed");

        NpcOwnerIdentity stale = NpcOwnerIdentity.of(
            8L, selectedIdentity.getPackedRegionX(),
            selectedIdentity.getPackedRegionY(),
            selectedIdentity.getSourceOrdinal(), "NPC_SPAWN", 10);
        LayeredPackedRegionActiveNpcResidencyObservation clean =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 13L,
                Collections.singletonList(new NpcInstanceSnapshot(
                    selectedIdentity, 10, 4, 0, true)), 1, 1);
        LayeredPackedRegionNpcOwnerEventContinuityAssessment staleResult =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                inventory(Collections.singletonList(ownerEvent(
                    0, 41L, OwnerKind.NPC, stale))),
                clean, true, false, 1);
        check(staleResult.getEvents().get(0).getOutcome()
                    == Outcome.OWNER_GENERATION_MISMATCH
                && staleResult.getHardBlockerEventCount() == 1,
            "stale owner generation refuses");
    }

    private static LayeredPackedRegionEventOwnershipInventory inventory(
        List<EventState> events) {
        return LayeredPackedRegionEventOwnershipInventory.inventory(
            9L, 12L, "00000000-0000-0000-0000-000000000161",
            Collections.singletonList(PackedSource.of(4, 0)),
            events, 1, events.size(), events.size());
    }

    private static EventState ownerEvent(
        int ordinal,
        long registrationSequence,
        OwnerKind ownerKind,
        NpcOwnerIdentity ownerIdentity) {
        return EventState.of(
            ordinal, registrationSequence, ownerKind, ownerIdentity,
            AttributionKind.OWNER_POSITION_HINT, true, 8L, 0,
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 200, 20)),
            EventRestorationState.unavailable(), false);
    }

    private static NpcOwnerIdentity ownerIdentity(
        LayeredAuthoredPlacementIdentity identity,
        int runtimeNpcId) {
        return NpcOwnerIdentity.of(
            identity.getGeneration(), identity.getPackedRegionX(),
            identity.getPackedRegionY(), identity.getSourceOrdinal(),
            identity.getConstructionKind().name(), runtimeNpcId);
    }
'''


def build_fixture():
    fixture = SLICE_71["FIXTURE"]
    fixture = fixture.replace(
        "import java.util.ArrayList;",
        IMPORTS + "\nimport java.util.ArrayList;\nimport java.util.Arrays;",
    )
    fixture = fixture.replace(
        "        expectIllegal(() ->\n"
        "            LayeredPackedRegionActiveNpcResidencyObservation.observe(\n"
        "                recipe, safety(4), 12L, census, 10, 6));",
        "        expectIllegal(() ->\n"
        "            LayeredPackedRegionActiveNpcResidencyObservation.observe(\n"
        "                recipe, safety(4), 12L, census, 10, 6));\n"
        "        ownerEventContinuityRemainsPreservationGated(\n"
        "            recipe, selectedIdentity);\n"
        "        ambiguousAndStaleOwnersRefuse(recipe, selectedIdentity);",
    )
    fixture = fixture.replace(
        "    private static int countClassification(",
        METHODS + "\n    private static int countClassification(",
    )
    return fixture


class LayeredMapsSliceOneHundredSixtyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-event-continuity-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(SLICE_71["POINT_STUB"], encoding="utf-8")
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

    def test_owner_event_continuity_contract_is_executable(self):
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

    def test_contract_is_bounded_point_in_time_and_handle_free(self):
        source = ASSESSMENT.read_text(encoding="utf-8")
        self.assertIn("maximumDetails", source)
        self.assertIn("OWNER_PRESERVATION_UNPROVED", source)
        self.assertIn("ACTIVE_OWNER_AMBIGUOUS", source)
        self.assertIn("isPreservationPerformed() { return false; }", source)
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "import com.openrsc.server.model.world.region",
            "GameTickEvent ",
            "Region ",
            "event.stop()",
        ):
            self.assertNotIn(forbidden, source)

    def test_living_plan_records_slice_one_hundred_sixty_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 161: NPC owner-event continuity policy",
            plan,
        )
        self.assertIn("OWNER_PRESERVATION_UNPROVED", plan)
        self.assertIn("point-in-time match", plan)


if __name__ == "__main__":
    unittest.main()
