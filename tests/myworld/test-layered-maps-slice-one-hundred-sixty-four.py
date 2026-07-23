#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REQUIREMENTS = COORDINATES / (
    "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_161 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-one.py"
)))


METHODS = r'''
    private static void preservationRequirementsDeduplicateOwners(
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
        check(requirements.getGeneration() == 9L
                && requirements.getEventObservedAtTick() == 12L
                && requirements.getCensusObservedAtTick() == 13L
                && requirements.getSchedulerInstanceIdentity().equals(
                    "00000000-0000-0000-0000-000000000161")
                && requirements.getSelectedSourceCount() == 1
                && requirements.getProposalRelatedEventCount() == 3
                && requirements.getRelatedOwnerPositionHintEventCount() == 3
                && requirements.getNpcOwnerEventCount() == 2
                && requirements.getSeparateNonNpcOwnerEventCount() == 1
                && requirements.getPreservationRequiredEventCount() == 2
                && requirements.getPreviouslyEligibleEventCount() == 0
                && requirements.getNpcHardBlockerEventCount() == 0
                && requirements.getUniqueNpcOwnerCount() == 1
                && requirements.getEventLinkCount() == 2
                && requirements.isNpcRequirementSetComplete()
                && requirements.hasSeparateNonNpcBlockers(),
            "callback evidence deduplicates to one exact NPC owner");
        LayeredPackedRegionNpcOwnerPreservationRequirements.OwnerRequirement
            owner = requirements.getOwners().get(0);
        check(owner.getGeneration() == selectedIdentity.getGeneration()
                && owner.getPackedRegionX()
                    == selectedIdentity.getPackedRegionX()
                && owner.getPackedRegionY()
                    == selectedIdentity.getPackedRegionY()
                && owner.getSourceOrdinal()
                    == selectedIdentity.getSourceOrdinal()
                && owner.getRuntimeNpcId() == 10
                && owner.getPreservationRequiredEventCount() == 2
                && owner.getPreviouslyEligibleEventCount() == 0
                && owner.getFirstRegistrationSequence() == 51L
                && owner.getEventRegistrationSequences().equals(
                    Arrays.asList(Long.valueOf(51L), Long.valueOf(52L)))
                && owner.isSameRuntimeInstanceRequired()
                && owner.isWorldRegistrationContinuityRequired()
                && owner.isEventOwnerReferenceContinuityRequired(),
            "one owner retains every stable event registration link");
        check(requirements.isSameRuntimeInstanceRequired()
                && requirements.isWorldRegistrationContinuityRequired()
                && requirements.isEventOwnerReferenceContinuityRequired()
                && requirements.isRegionAbsenceQuiescenceRequired()
                && !requirements.isPreservationFactEstablished()
                && !requirements.isRuntimeHandleRetained()
                && !requirements.isPreservationPerformed()
                && !requirements.isEventReschedule()
                && !requirements.isEntityRegistry()
                && !requirements.isArrivalGate()
                && !requirements.isLifecycleAuthority(),
            "detached requirements grant no runtime authority");

        expectIllegal(() ->
            LayeredPackedRegionNpcOwnerPreservationRequirements.derive(
                inventory, continuity, 0, 2));
        expectIllegal(() ->
            LayeredPackedRegionNpcOwnerPreservationRequirements.derive(
                inventory, continuity, 1, 1));
    }

    private static void incompleteNpcEvidenceRemainsIncomplete(
        LayeredPackedRegionAuthoredReconstructionRecipe recipe,
        LayeredAuthoredPlacementIdentity selectedIdentity) {
        LayeredPackedRegionActiveNpcResidencyObservation clean =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 13L,
                Collections.singletonList(new NpcInstanceSnapshot(
                    selectedIdentity, 10, 4, 0, true)), 1, 1);
        LayeredPackedRegionEventOwnershipInventory inventory = inventory(
            Arrays.asList(
                ownerEvent(0, 61L, OwnerKind.NPC,
                    ownerIdentity(selectedIdentity, 10)),
                ownerEvent(1, 62L, OwnerKind.NPC, null),
                ownerEvent(2, 63L, OwnerKind.PLAYER, null)));
        LayeredPackedRegionNpcOwnerEventContinuityAssessment continuity =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                inventory, clean, true, false, 3);
        LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
            LayeredPackedRegionNpcOwnerPreservationRequirements.derive(
                inventory, continuity, 1, 1);
        check(requirements.getUniqueNpcOwnerCount() == 1
                && requirements.getPreservationRequiredEventCount() == 1
                && requirements.getNpcHardBlockerEventCount() == 1
                && requirements.getSeparateNonNpcOwnerEventCount() == 1
                && !requirements.isNpcRequirementSetComplete(),
            "one exact owner cannot hide an unresolved NPC owner");
    }
'''


def build_fixture():
    fixture = SLICE_161["build_fixture"]()
    fixture = fixture.replace(
        "        ambiguousAndStaleOwnersRefuse(recipe, selectedIdentity);",
        "        ambiguousAndStaleOwnersRefuse(recipe, selectedIdentity);\n"
        "        preservationRequirementsDeduplicateOwners(\n"
        "            recipe, selectedIdentity);\n"
        "        incompleteNpcEvidenceRemainsIncomplete(\n"
        "            recipe, selectedIdentity);",
    )
    fixture = fixture.replace(
        "    private static int countClassification(",
        METHODS + "\n    private static int countClassification(",
    )
    return fixture


class LayeredMapsSliceOneHundredSixtyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-preservation-requirements-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(SLICE_161["SLICE_71"]["POINT_STUB"], encoding="utf-8")
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

    def test_owner_preservation_requirements_are_executable(self):
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

    def test_requirements_are_bounded_deduplicated_and_handle_free(self):
        source = REQUIREMENTS.read_text(encoding="utf-8")
        self.assertIn("maximumOwnerRequirements", source)
        self.assertIn("maximumEventLinks", source)
        self.assertIn("Map<OwnerKey, OwnerRequirementBuilder>", source)
        self.assertIn(
            "isPreservationFactEstablished() { return false; }",
            source,
        )
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "import com.openrsc.server.model.world.region",
            "GameTickEvent ",
            "Npc ",
            "event.stop()",
            "registerNpc(",
            "unregisterNpc(",
        ):
            self.assertNotIn(forbidden, source)

    def test_living_plan_records_slice_one_hundred_sixty_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 164: Exact NPC owner preservation requirements",
            plan,
        )
        self.assertIn("same runtime NPC instance", plan)
        self.assertIn("player-owned callbacks remain separate", plan)


if __name__ == "__main__":
    unittest.main()
