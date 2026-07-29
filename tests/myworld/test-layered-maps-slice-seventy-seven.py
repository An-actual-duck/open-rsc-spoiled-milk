#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PROPOSAL = COORDINATES / (
    "LayeredPackedRegionRetirementRefinementProposal.java"
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

public final class RetirementRefinementProposalFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifest.recordNpcSpawn(4, 0, 10, 210, 20, 190, 250, 10, 30);
        LayeredAuthoredPlacementIdentity selected =
            manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(5, 0, 12, 260, 20, 250, 300, 10, 30);
        manifest.recordNpcSpawn(7, 0, 11, 350, 20, 340, 360, 10, 30);
        LayeredAuthoredPlacementIdentity external =
            manifest.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest builtManifest =
            manifest.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder deps =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(9L);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 250, 10, 30, 4, 5, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            5, 0, 250, 300, 10, 30, 5, 6, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            7, 0, 340, 360, 10, 30, 7, 7, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(builtManifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                builtManifest, deps.build(), outcome);

        LayeredPackedRegionRetirementSafetyAssessment safety = safety(4);
        LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety, 3, 4);
        List<NpcInstanceSnapshot> census = Arrays.asList(
            new NpcInstanceSnapshot(selected, 10, 5, 0, true),
            new NpcInstanceSnapshot(external, 11, 4, 0, true),
            new NpcInstanceSnapshot(null, 20, 4, 0, true),
            new NpcInstanceSnapshot(selected, 10, 4, 0, false));
        LayeredPackedRegionActiveNpcResidencyObservation observation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety, 13L, census, 4, 3);
        LayeredPackedRegionActiveNpcBoundaryRequirementProjection active =
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                observation, 2);
        LayeredPackedRegionRetirementRefinementProposal proposal =
            LayeredPackedRegionRetirementRefinementProposal.propose(
                safety, cohort, active, 3, 1);

        check(proposal.getGeneration() == 9L
            && proposal.getSafetyObservedAtTick() == 8L
            && proposal.getCensusObservedAtTick() == 13L,
            "atomic generation and observation ticks are retained");
        check(proposal.getOriginalSafetySourceCount() == 1
            && proposal.getAuthoredCohortSourceCount() == 2
            && proposal.getExpandedAuthoredSourceCount() == 1
            && proposal.getActiveNpcRequirementSourceCount() == 2,
            "input source families remain separately counted");
        check(proposal.getCandidateSourceCount() == 3
            && proposal.getAddedCandidateSourceCount() == 2
            && proposal.getActiveNpcAndAuthoredOverlapSourceCount() == 1,
            "candidate union deduplicates authored and active overlap");
        check(proposal.getExternalSupportRequirementSourceCount() == 1
            && proposal.getSupportPromotedToCandidateSourceCount() == 0,
            "empty static support remains outside the candidate union");
        check(!proposal.isBoundaryContainedAtInput()
            && proposal.getHardBlockingConditionCount() == 2
            && proposal.getHardBlockingEvidenceCount() == 2
            && proposal.hasNonExpandableHardBlockers(),
            "non-expandable blockers survive source refinement");

        LayeredPackedRegionRetirementRefinementProposal.CandidateSource seed =
            proposal.getCandidates().get(0);
        LayeredPackedRegionRetirementRefinementProposal.CandidateSource overlap =
            proposal.getCandidates().get(1);
        LayeredPackedRegionRetirementRefinementProposal.CandidateSource activeOnly =
            proposal.getCandidates().get(2);
        check(seed.getPackedRegionX() == 4
            && seed.isOriginalSafetySource()
            && seed.isAuthoredCohortSource()
            && seed.getAuthoredExpansionRound().intValue() == 0
            && !seed.isAddedBeyondOriginalSafety()
            && !seed.isFreshSafetyEvidenceRequired()
            && !seed.isFreshNpcCensusRequired(),
            "original safety seed remains identified as already observed");
        check(overlap.getPackedRegionX() == 5
            && !overlap.isOriginalSafetySource()
            && overlap.isAuthoredCohortSource()
            && overlap.getAuthoredExpansionRound().intValue() == 1
            && overlap.getSelectedOwnerCurrentSourceInstanceCount() == 1
            && overlap.getExternalOwnerAuthoredSourceInstanceCount() == 0
            && overlap.isActiveNpcBoundarySource()
            && overlap.isFreshSafetyEvidenceRequired()
            && overlap.isFreshNpcCensusRequired(),
            "authored expansion and selected-owner movement retain both reasons");
        check(activeOnly.getPackedRegionX() == 7
            && !activeOnly.isAuthoredCohortSource()
            && activeOnly.getAuthoredExpansionRound() == null
            && activeOnly.getExternalOwnerAuthoredSourceInstanceCount() == 1
            && activeOnly.getActiveNpcBoundaryInstanceCount() == 1
            && activeOnly.isAddedBeyondOriginalSafety(),
            "external owner requirement remains an active-only candidate");

        LayeredPackedRegionRetirementRefinementProposal.SupportRequirement
            support = proposal.getExternalSupportRequirements().get(0);
        check(support.getPackedRegionX() == 6
            && support.getPackedRegionY() == 0
            && support.getOwnerSourceCount() == 1
            && support.getPlacementReferenceCount() == 1
            && !support.isCandidateSource()
            && support.isExternalStaticSupportRequired(),
            "support-only coordinate retains static reference evidence");
        check(proposal.isFreshSafetyAssessmentRequired()
            && proposal.isFreshNpcCensusRequired()
            && proposal.isReassessmentRequired()
            && !proposal.isCandidateSelectionMutated()
            && !proposal.isFixedPointClosureProved()
            && !proposal.isLoadRequest()
            && !proposal.isEntityRegistry()
            && !proposal.isArrivalGate()
            && !proposal.isLifecycleAuthority(),
            "proposal remains inert and explicitly requires reassessment");
        expectImmutable(proposal.getCandidates());
        expectImmutable(proposal.getExternalSupportRequirements());

        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementProposal.propose(
                safety, cohort, active, 2, 1));
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementProposal.propose(
                safety, cohort, active, 3, 0));
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementProposal.propose(
                safety,
                LayeredPackedRegionAuthoredReconstructionCohortAnalysis
                    .analyze(recipe, safety(7), 2, 2),
                active, 3, 1));
        expectFailure(() ->
            LayeredPackedRegionRetirementRefinementProposal.propose(
                safety, cohort, active,
                LayeredPackedRegionRetirementRefinementProposal
                    .MAXIMUM_CANDIDATE_SOURCES + 1,
                1));
        expectNullFailure(() ->
            LayeredPackedRegionRetirementRefinementProposal.propose(
                null, cohort, active, 3, 1));
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety(
        int sourceX) {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        check(residency.registerPackedRegion(sourceX, 0),
            "register packed source");
        WorldRegionWindow window = new WorldRegionWindow(
            WorldSpaceId.GLOBAL, 0, sourceX, 0, sourceX, 0);
        LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
            ownership.openOwner(window, 1);
        retirement.observeOwnershipChange(opened.getChange(), 1L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(opened.getOwnerToken()), 2L);
        WorldRegionKey key = new WorldRegionKey(
            WorldSpaceId.GLOBAL, 0, sourceX, 0);
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L);
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Arrays.asList(arbiter.evaluate(candidate, retirement.snapshot(
                    ownership.snapshot(key), residency.snapshot(key), 7L))),
                1, 1);
        List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
            contents = new ArrayList<
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents>();
        contents.add(LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents.of(
                sourceX, 0, true, true, false, 0, 0, 0, 0));
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, contents, 8L, 1);
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


class LayeredMapsSliceSeventySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-seventy-seven-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "RetirementRefinementProposalFixture.java"
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

    def test_candidate_union_preserves_static_active_and_hard_evidence(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "RetirementRefinementProposalFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_proposal_is_detached_and_never_mutates_or_loads(self):
        source = PROPOSAL.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("never mutates a selection", source)
        self.assertIn("isFreshSafetyAssessmentRequired() { return true; }", source)
        self.assertIn("isFreshNpcCensusRequired() { return true; }", source)
        self.assertIn("isCandidateSelectionMutated() { return false; }", source)
        self.assertIn("isFixedPointClosureProved() { return false; }", source)
        self.assertIn("isLoadRequest() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_seventy_seven_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 77: Retirement source refinement proposal", plan
        )
        self.assertIn("provenance-tagged candidate union", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
