#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REASSESSMENT = COORDINATES / (
    "LayeredPackedRegionRetirementRefinementReassessment.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public static Point location(int x, int y) {
        if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalArgumentException("packed point out of range");
        }
        return new Point(x, y);
    }
    public int getX() { return x; }
    public int getY() { return y; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RetirementRefinementReassessmentFixture {
    private static final long GENERATION = 9L;
    private static LayeredPackedRegionAuthoredReconstructionRecipe recipe;
    private static LayeredAuthoredPlacementIdentity sourceFour;
    private static LayeredAuthoredPlacementIdentity sourceFive;
    private static LayeredAuthoredPlacementIdentity sourceSeven;
    private static LayeredAuthoredPlacementIdentity sourceEight;

    public static void main(String[] args) {
        buildRecipe();
        LayeredPackedRegionRetirementRefinementProposal previous =
            initialProposal();
        check(previous.getCandidateSourceCount() == 3,
            "initial proposal has three candidates");
        check(!LayeredPackedRegionRetirementRefinementReassessment
                .isFreshObservationTick(previous, 13L)
            && LayeredPackedRegionRetirementRefinementReassessment
                .isFreshObservationTick(previous, 14L),
            "shared reassessment tick must be newer than both parents");
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment
                .isFreshObservationTick(previous, -1L));
        expectNullFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment
                .isFreshObservationTick(null, 14L));

        LayeredPackedRegionRetirementSafetyAssessment stableSafety =
            safety(new int[] {4, 5, 7}, 20L);
        LayeredPackedRegionAuthoredReconstructionCohortAnalysis stableCohort =
            cohort(stableSafety);
        LayeredPackedRegionActiveNpcBoundaryRequirementProjection stableActive =
            active(stableSafety, 21L, Arrays.asList(
                new NpcInstanceSnapshot(sourceFour, 10, 4, 0, true),
                new NpcInstanceSnapshot(sourceFive, 12, 5, 0, true),
                new NpcInstanceSnapshot(sourceSeven, 11, 7, 0, true)));
        LayeredPackedRegionRetirementRefinementReassessment stable =
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, stableSafety, stableCohort, stableActive, 4, 2);

        check(stable.getGeneration() == GENERATION
            && stable.getPreviousSafetyObservedAtTick() == 8L
            && stable.getPreviousCensusObservedAtTick() == 13L
            && stable.getReassessmentSafetyObservedAtTick() == 20L
            && stable.getReassessmentCensusObservedAtTick() == 21L,
            "strictly newer observation identity is retained");
        check(stable.getPreviousCandidateSourceCount() == 3
            && stable.getReassessedSourceCount() == 3
            && stable.getRetainedCandidateSourceCount() == 3
            && stable.getNextCandidateSourceCount() == 3
            && stable.getNewCandidateSourceCount() == 0,
            "stable candidate arithmetic is exact");
        check(stable.isFreshEvidenceAligned()
            && stable.isCandidateSetStableAtObservation()
            && !stable.isFurtherRefinementRequired()
            && !stable.hasNonExpandableHardBlockers()
            && stable.isRefinementConvergedAtObservation(),
            "fresh stable evidence converges only the refinement set");
        check(stable.getNextExternalSupportRequirementSourceCount() == 1
            && stable.getNextProposal().getCandidates().get(0)
                .isOriginalSafetySource(),
            "stable reassessment retains support and rebuilt proposal");
        check(stable.getLifecycleReadyEvidenceSourceCount() == 0
            && !stable.isAllReassessedSourcesLifecycleReadyEvidence()
            && stable.isPointInTimeOnly()
            && !stable.isCandidateSelectionMutated()
            && !stable.isFixedPointLifecycleClosureProved()
            && !stable.isLoadRequest()
            && !stable.isEntityRegistry()
            && !stable.isArrivalGate()
            && !stable.isRetirementCommitToken()
            && !stable.isLifecycleAuthority(),
            "candidate convergence grants no lifecycle authority");
        expectImmutable(stable.getNewCandidates());

        LayeredPackedRegionActiveNpcBoundaryRequirementProjection expandedActive =
            active(stableSafety, 22L, Arrays.asList(
                new NpcInstanceSnapshot(sourceFour, 10, 4, 0, true),
                new NpcInstanceSnapshot(sourceEight, 14, 4, 0, true)));
        LayeredPackedRegionRetirementRefinementReassessment expanded =
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, stableSafety, stableCohort, expandedActive, 4, 2);
        check(!expanded.isCandidateSetStableAtObservation()
            && expanded.isFurtherRefinementRequired()
            && !expanded.isRefinementConvergedAtObservation()
            && expanded.getNextCandidateSourceCount() == 4
            && expanded.getNewCandidateSourceCount() == 1,
            "new active boundary evidence requests another refinement");
        LayeredPackedRegionRetirementRefinementProposal.CandidateSource added =
            expanded.getNewCandidates().get(0);
        check(added.getPackedRegionX() == 8
            && added.getPackedRegionY() == 0
            && !added.isAuthoredCohortSource()
            && added.getExternalOwnerAuthoredSourceInstanceCount() == 1
            && added.isFreshSafetyEvidenceRequired()
            && added.isFreshNpcCensusRequired(),
            "new candidate retains exact active reason and freshness burden");
        expectImmutable(expanded.getNewCandidates());

        LayeredPackedRegionActiveNpcBoundaryRequirementProjection blockedActive =
            active(stableSafety, 23L, Arrays.asList(
                new NpcInstanceSnapshot(null, 99, 4, 0, true)));
        LayeredPackedRegionRetirementRefinementReassessment blocked =
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, stableSafety, stableCohort, blockedActive, 4, 2);
        check(blocked.isCandidateSetStableAtObservation()
            && blocked.hasNonExpandableHardBlockers()
            && !blocked.isRefinementConvergedAtObservation()
            && blocked.getHardBlockingConditionCount() == 1
            && blocked.getHardBlockingEvidenceCount() == 1,
            "stable source set cannot erase a non-expandable blocker");

        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, safety(new int[] {4, 5, 7}, 8L),
                cohort(safety(new int[] {4, 5, 7}, 8L)), stableActive, 4, 2));
        LayeredPackedRegionRetirementSafetyAssessment incomplete =
            safety(new int[] {4, 5}, 24L);
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, incomplete, cohort(incomplete),
                active(incomplete, 25L, new ArrayList<NpcInstanceSnapshot>()),
                4, 2));
        LayeredPackedRegionRetirementSafetyAssessment reordered =
            safety(new int[] {5, 4, 7}, 26L);
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, reordered, cohort(reordered),
                active(reordered, 27L, new ArrayList<NpcInstanceSnapshot>()),
                4, 2));
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                previous, stableSafety, stableCohort, expandedActive, 3, 2));
        expectNullFailure(() ->
            LayeredPackedRegionRetirementRefinementReassessment.reassess(
                null, stableSafety, stableCohort, stableActive, 4, 2));
    }

    private static void buildRecipe() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(GENERATION);
        manifest.recordNpcSpawn(4, 0, 10, 210, 20, 190, 250, 10, 30);
        sourceFour = manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(5, 0, 12, 260, 20, 250, 300, 10, 30);
        sourceFive = manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(7, 0, 11, 350, 20, 340, 360, 10, 30);
        sourceSeven = manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(8, 0, 14, 400, 20, 390, 410, 10, 30);
        sourceEight = manifest.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest builtManifest =
            manifest.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder deps =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(
                GENERATION);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 250, 10, 30, 4, 5, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            5, 0, 250, 300, 10, 30, 5, 6, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            7, 0, 340, 360, 10, 30, 7, 7, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            8, 0, 390, 410, 10, 30, 8, 8, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(GENERATION)
                .build(builtManifest);
        recipe = LayeredPackedRegionAuthoredReconstructionRecipe.derive(
            builtManifest, deps.build(), outcome);
    }

    private static LayeredPackedRegionRetirementRefinementProposal
        initialProposal() {
        LayeredPackedRegionRetirementSafetyAssessment initialSafety =
            safety(new int[] {4}, 8L);
        LayeredPackedRegionAuthoredReconstructionCohortAnalysis initialCohort =
            cohort(initialSafety);
        LayeredPackedRegionActiveNpcBoundaryRequirementProjection initialActive =
            active(initialSafety, 13L, Arrays.asList(
                new NpcInstanceSnapshot(sourceFour, 10, 5, 0, true),
                new NpcInstanceSnapshot(sourceSeven, 11, 4, 0, true)));
        return LayeredPackedRegionRetirementRefinementProposal.propose(
            initialSafety, initialCohort, initialActive, 3, 1);
    }

    private static LayeredPackedRegionAuthoredReconstructionCohortAnalysis
        cohort(LayeredPackedRegionRetirementSafetyAssessment safety) {
        return LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
            recipe, safety, 8, 8);
    }

    private static LayeredPackedRegionActiveNpcBoundaryRequirementProjection
        active(
            LayeredPackedRegionRetirementSafetyAssessment safety,
            long censusTick,
            List<NpcInstanceSnapshot> census) {
        LayeredPackedRegionActiveNpcResidencyObservation observation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety, censusTick, census, 16, 16);
        return LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
            observation, 16);
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety(
        int[] sourceXs,
        long safetyTick) {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(2L);
        LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        long changeTick = 0L;
        for (int sourceX : sourceXs) {
            check(residency.registerPackedRegion(sourceX, 0),
                "register packed source");
            WorldRegionWindow window = new WorldRegionWindow(
                WorldSpaceId.GLOBAL, 0, sourceX, 0, sourceX, 0);
            LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
                ownership.openOwner(window, 1);
            retirement.observeOwnershipChange(
                opened.getChange(), ++changeTick);
            retirement.observeOwnershipChange(
                ownership.closeOwner(opened.getOwnerToken()), ++changeTick);
        }
        long decisionTick = safetyTick - 1L;
        List<LayeredRegionRetirementDecisionArbiter.Decision> decisions =
            new ArrayList<LayeredRegionRetirementDecisionArbiter.Decision>();
        List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
            contents = new ArrayList<
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents>();
        for (int sourceX : sourceXs) {
            WorldRegionKey key = new WorldRegionKey(
                WorldSpaceId.GLOBAL, 0, sourceX, 0);
            LayeredRegionRetirementEligibilityLedger.Snapshot snapshot =
                retirement.snapshot(
                    ownership.snapshot(key), residency.snapshot(key),
                    decisionTick);
            decisions.add(arbiter.evaluate(snapshot, snapshot));
            contents.add(LayeredPackedRegionRetirementSafetyAssessment
                .PackedSourceContents.of(
                    sourceX, 0, true, true, false, 0, 0, 0, 0));
        }
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                decisions, sourceXs.length, sourceXs.length);
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, contents, safetyTick, sourceXs.length);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(java.util.List values) {
        try {
            values.add(new Object());
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNullFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceSeventyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-seventy-nine-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "RetirementRefinementReassessmentFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
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

    def test_fresh_reassessment_distinguishes_stable_expanded_and_blocked(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "RetirementRefinementReassessmentFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_reassessment_is_detached_and_never_authorizes_retirement(self):
        source = REASSESSMENT.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("strictly newer", source)
        self.assertIn("isPointInTimeOnly() { return true; }", source)
        self.assertIn("isCandidateSelectionMutated() { return false; }", source)
        self.assertIn(
            "isFixedPointLifecycleClosureProved() { return false; }", source
        )
        self.assertIn("isRetirementCommitToken() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_seventy_nine_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 79: Fresh retirement-refinement reassessment", plan
        )
        self.assertIn("candidate-set convergence", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
