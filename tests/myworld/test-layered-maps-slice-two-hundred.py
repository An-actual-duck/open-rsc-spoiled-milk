#!/usr/bin/env python3
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_SOURCE = ROOT / "server/src"
REGION = SERVER_SOURCE / "com/openrsc/server/model/world/region"
COMPARISON = REGION / (
    "LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison.java"
)
OBSERVATION = REGION / (
    "LayeredPackedRegionRuntimeAuthoredObjectObservation.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_199 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-nine.py"
)))


DECLARATIONS = r'''
        final LayeredPackedRegionAuthoredCollisionFootprintPlan[]
            retainedCollisionPlans =
                new LayeredPackedRegionAuthoredCollisionFootprintPlan[1];
        final LayeredPackedRegionRuntimeAuthoredObjectObservation[]
            plannedRuntimeObservations =
                new LayeredPackedRegionRuntimeAuthoredObjectObservation[1];
        final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[]
            exactBaselineComparisons =
                new
                    LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[1];
        final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[]
            stableMismatchComparisons =
                new
                    LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[1];
        final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[]
            identityConflictComparisons =
                new
                    LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[1];
        final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[]
            nonFinalComparisons =
                new
                    LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison[1];
'''


PLAN_CAPTURE_OLD = r'''                                        return
                                            LayeredPackedRegionAuthoredCollisionFootprintPlan
                                                .define(
                                                    replay, membership,
                                                    capture,
                                                    new String[]{"gate"},
                                                    1008, 4032);'''


PLAN_CAPTURE_NEW = r'''                                        retainedCollisionPlans[0] =
                                            LayeredPackedRegionAuthoredCollisionFootprintPlan
                                                .define(
                                                    replay, membership,
                                                    capture,
                                                    new String[]{"gate"},
                                                    1008, 4032);
                                        return retainedCollisionPlans[0];'''


COMPARISON_CAPTURE = r'''
                    plannedRuntimeObservations[0] =
                        plannedRuntimeObservation(
                            reload, retainedCollisionPlans[0]);
                    exactBaselineComparisons[0] =
                        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                            .compare(
                                plannedRuntimeObservations[0],
                                transactionalBatches[0]);
                    stableMismatchComparisons[0] =
                        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                            .compare(
                                exactRuntimeObservations[0],
                                transactionalBatches[0]);
                    identityConflictComparisons[0] =
                        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                            .compare(
                                mixedRuntimeObservations[0],
                                transactionalBatches[0]);
                    nonFinalComparisons[0] =
                        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                            .compare(
                                emptyRuntimeObservations[0],
                                transactionalBatches[0]);
'''


ASSERTIONS = r'''
        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
            exactBaseline = exactBaselineComparisons[0];
        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
            .SourceComparison exactBaselineSource =
                exactBaseline.getSources().get(0);
        check(exactBaseline.getGeneration() == 9L
                && exactBaseline.getRequirementsObservedAtTick() == 12L
                && exactBaseline.getRecipeObservedAtTick() == 14L
                && exactBaseline.getRuntimeObservedAtTick() == 15L
                && exactBaseline.getResidencyMirrorVersion() >= 1L
                && exactBaseline.getSourceCount() == 1
                && exactBaseline.getExactBaselineMatchSourceCount() == 1
                && exactBaseline.getNonFinalAuthoredStateSourceCount() == 0
                && exactBaseline.getIdentityConflictSourceCount() == 0
                && exactBaseline
                    .getRegistrationProvenanceInvalidSourceCount() == 0
                && exactBaseline.getStableBaselineMismatchSourceCount() == 0
                && exactBaseline.getExpectedAuthoredObjectCount() == 3L
                && exactBaseline.getIdentitylessDynamicObjectCount() == 0L
                && exactBaseline.areAllSourcesExactBaselineMatches()
                && exactBaseline
                    .getRuntimeObservationFingerprintSha256().length() == 64
                && exactBaseline
                    .getTransactionalBaselineFingerprintSha256().equals(
                        transactionalBatches[0].getFingerprintSha256())
                && exactBaseline.getFingerprintSha256().length() == 64,
            "exact runtime registrations did not match disposable baseline");
        check(exactBaselineSource.getSourceOrdinal() == 0
                && exactBaselineSource.getPackedRegionX() == 4
                && exactBaselineSource.getPackedRegionY() == 0
                && exactBaselineSource.getExpectedAuthoredObjectCount() == 3
                && exactBaselineSource.getExactFinalLiveInstanceCount() == 3
                && exactBaselineSource.getAuthoredTransientInstanceCount()
                    == 0
                && exactBaselineSource.getMissingExpectedIdentityCount() == 0
                && exactBaselineSource
                    .getDuplicateRecognizedIdentityInstanceCount() == 0
                && exactBaselineSource
                    .getUnrecognizedAuthoredInstanceCount() == 0
                && exactBaselineSource
                    .getCollisionRegistrationPresentCount() == 3
                && exactBaselineSource
                    .getCollisionRegistrationContributionCount() == 4
                && exactBaselineSource
                    .getCollisionRegistrationRegionReferenceCount() == 4
                && exactBaselineSource.isRegistrationFingerprintMatched()
                && exactBaselineSource.getOutcome()
                    == LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                        .Outcome.EXACT_BASELINE_MATCH,
            "exact source comparison lost baseline identity");

        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
            stableMismatch = stableMismatchComparisons[0];
        check(stableMismatch.getStableBaselineMismatchSourceCount() == 1
                && stableMismatch.getSources().get(0).getOutcome()
                    == LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                        .Outcome.STABLE_BASELINE_MISMATCH
                && !stableMismatch.getSources().get(0)
                    .isRegistrationFingerprintMatched()
                && !stableMismatch.areAllSourcesExactBaselineMatches(),
            "stable registration drift did not fail separately");

        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
            identityConflict = identityConflictComparisons[0];
        check(identityConflict.getIdentityConflictSourceCount() == 1
                && identityConflict.getSources().get(0).getOutcome()
                    == LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                        .Outcome.IDENTITY_CONFLICT
                && identityConflict.getIdentitylessDynamicObjectCount() == 1L,
            "identity conflict was conflated with dynamic-object presence");
        LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison nonFinal =
            nonFinalComparisons[0];
        check(nonFinal.getNonFinalAuthoredStateSourceCount() == 1
                && nonFinal.getSources().get(0).getOutcome()
                    == LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
                        .Outcome.NON_FINAL_AUTHORED_STATE
                && nonFinal.getSources().get(0)
                    .getMissingExpectedIdentityCount() == 3,
            "missing authored state was mislabeled as collision corruption");
        expectUnsupported(() -> exactBaseline.getSources().clear());
        check(exactBaseline.isPointInTimeOnly()
                && exactBaseline.isDetachedSummaryOnly()
                && !exactBaseline.isSharedCollisionTileComparisonPerformed()
                && !exactBaseline.isRuntimeHandleRetained()
                && !exactBaseline.isSourceAbsencePerformed()
                && !exactBaseline.isSourceReconstructionPerformed()
                && !exactBaseline.isRuntimeMutationAuthorized()
                && !exactBaseline.isRuntimeMutationPerformed()
                && !exactBaseline.isRuntimeCacheInvalidated()
                && !exactBaseline.isRegionRegistryMutated()
                && !exactBaseline.isResidencyMirrorMutated()
                && !exactBaseline.isVisibilityCacheMutated()
                && !exactBaseline.isSchedulerCorrelationPerformed()
                && !exactBaseline.isArrivalGate()
                && !exactBaseline.isVisibilityReleased()
                && !exactBaseline.isLifecycleAuthority(),
            "runtime authored baseline comparison crossed authority");
        System.out.println("runtime-authored-object-baseline-comparison-ok");
'''


HELPER = r'''
    private static LayeredPackedRegionRuntimeAuthoredObjectObservation
        plannedRuntimeObservation(
            final LayeredPackedRegionReloadRecipe reload,
            final LayeredPackedRegionAuthoredCollisionFootprintPlan plan) {
        java.util.List<
            LayeredPackedRegionRuntimeAuthoredObjectObservation.ObjectSnapshot>
                objects = new java.util.ArrayList<
                    LayeredPackedRegionRuntimeAuthoredObjectObservation
                        .ObjectSnapshot>();
        int footprintIndex = 0;
        for (com.openrsc.server.model.world.coordinate
                .LayeredPackedRegionAuthoredReconstructionRecipe
                    .ReconstructionPlacement placement
                : reload.getSources().get(0).getAuthoredPlacements()) {
            if (!isObjectKind(placement.getKind())) {
                continue;
            }
            LayeredPackedRegionAuthoredCollisionFootprintPlan
                .AuthoredObjectCollisionFootprint footprint =
                    plan.getFootprints().get(footprintIndex++);
            java.util.List<
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .ContributionSnapshot> contributions =
                        new java.util.ArrayList<
                            LayeredPackedRegionRuntimeAuthoredObjectObservation
                                .ContributionSnapshot>();
            for (LayeredPackedRegionAuthoredCollisionFootprintPlan.Contribution
                    contribution : footprint.getContributions()) {
                contributions.add(
                    new LayeredPackedRegionRuntimeAuthoredObjectObservation
                        .ContributionSnapshot(
                            contribution.getPackedX(),
                            contribution.getPackedY(),
                            contribution.getBlockingSceneryCount(),
                            contribution.getDynamicCollisionCounts(),
                            contribution.getDynamicProjectileCount()));
            }
            java.util.List<
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .RegionSnapshot> regions =
                        new java.util.ArrayList<
                            LayeredPackedRegionRuntimeAuthoredObjectObservation
                                .RegionSnapshot>();
            for (LayeredPackedRegionAuthoredCollisionFootprintPlan
                    .RequiredPackedRegion region
                    : footprint.getRequiredRegions()) {
                regions.add(
                    new LayeredPackedRegionRuntimeAuthoredObjectObservation
                        .RegionSnapshot(
                            region.getPackedRegionX(),
                            region.getPackedRegionY()));
            }
            com.openrsc.server.model.world.coordinate
                .LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
                    definition = placement.getPlacement();
            LayeredPackedRegionRuntimeAuthoredObjectObservation
                .RegistrationSnapshot registration =
                    LayeredPackedRegionRuntimeAuthoredObjectObservation
                        .RegistrationSnapshot.declare(
                            definition.getConstructedEntityId(),
                            definition.getPermanentObjectId(),
                            definition.getPackedX(), definition.getPackedY(),
                            definition.getDirection(),
                            definition.getObjectType(),
                            contributions, regions);
            objects.add(
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .ObjectSnapshot.declare(
                        definition.getConstructedEntityId(),
                        definition.getPermanentObjectId(),
                        definition.getPackedX(), definition.getPackedY(),
                        definition.getDirection(),
                        definition.getObjectType(),
                        definition.getObjectOwner(), 0,
                        placement.getIdentity(), registration));
        }
        check(footprintIndex == plan.getFootprints().size(),
            "planned runtime fixture did not consume every footprint");
        return LayeredPackedRegionRuntimeAuthoredObjectObservation.observe(
            reload, 15L,
            Collections.singletonList(
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .SourceCapture.capture(4, 0, objects, true)),
            LayeredPackedRegionRuntimeAuthoredObjectObservation
                .MAXIMUM_OBJECT_INSTANCES);
    }

'''


def build_fixture():
    fixture = SLICE_199["build_fixture"]()
    fixture = fixture.replace(
        "        final LayeredPackedRegionRuntimeAuthoredObjectObservation[]\n"
        "            exactRuntimeObservations =\n",
        DECLARATIONS
        + "        final LayeredPackedRegionRuntimeAuthoredObjectObservation[]\n"
        "            exactRuntimeObservations =\n",
        1,
    )
    if fixture.count(PLAN_CAPTURE_OLD) < 1:
        raise AssertionError("collision-plan capture marker changed")
    fixture = fixture.replace(PLAN_CAPTURE_OLD, PLAN_CAPTURE_NEW, 1)
    fixture = fixture.replace(
        "                    expectIllegalArgument(() ->\n",
        COMPARISON_CAPTURE + "                    expectIllegalArgument(() ->\n",
        1,
    )
    fixture = fixture.replace(
        "        LayeredPackedRegionRuntimeAuthoredObjectObservation exactRuntime =\n",
        ASSERTIONS
        + "        LayeredPackedRegionRuntimeAuthoredObjectObservation exactRuntime =\n",
        1,
    )
    fixture = fixture.replace(
        "    private static LayeredPackedRegionRuntimeAuthoredObjectObservation\n"
        "        exactRuntimeObservation(\n",
        HELPER
        + "    private static LayeredPackedRegionRuntimeAuthoredObjectObservation\n"
        "        exactRuntimeObservation(\n",
        1,
    )
    return fixture


class LayeredMapsSliceTwoHundredTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-runtime-authored-baseline-comparison-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        requirements_fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcResidencyFixture.java"
        )
        region_fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "PackedRegionTerrainBoundaryCaptureFixture.java"
        )
        requirements_fixture.parent.mkdir(parents=True, exist_ok=True)
        region_fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements_fixture.write_text(
            SLICE_199["SLICE_197"]["SLICE_194"]["SLICE_191"]
            ["SLICE_188"]["SLICE_185"]["SLICE_182"]["SLICE_181"]
            ["SLICE_179"]["build_requirements_fixture"](),
            encoding="utf-8",
        )
        region_fixture.write_text(build_fixture(), encoding="utf-8")
        classpath = os.pathsep.join(
            str(path) for path in sorted((ROOT / "server/lib").glob("*.jar"))
        )
        subprocess.run(
            [
                "javac", "-Xlint:none", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-cp", classpath,
                "-sourcepath", os.pathsep.join(
                    (str(cls.temp / "src"), str(SERVER_SOURCE))
                ),
                "-d", str(cls.classes), str(requirements_fixture),
                str(region_fixture),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )
        cls.fixture_run = subprocess.run(
            [
                "java", "-cp", os.pathsep.join((str(cls.classes), classpath)),
                (
                    "com.openrsc.server.model.world.region."
                    "PackedRegionTerrainBoundaryCaptureFixture"
                ),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_exact_stable_mismatch_and_identity_conflict_are_distinct(self):
        self.assertIn(
            "runtime-authored-object-baseline-comparison-ok",
            self.fixture_run.stdout,
        )

    def test_comparison_is_count_and_fingerprint_only(self):
        source = COMPARISON.read_text(encoding="utf-8")
        fields = source.split(
            "public final class\n"
            "\tLayeredPackedRegionRuntimeAuthoredObjectBaselineComparison {",
            1,
        )[1].split(
            "private\n"
            "\t\tLayeredPackedRegionRuntimeAuthoredObjectBaselineComparison(",
            1,
        )[0]
        self.assertNotIn("GameObject", fields)
        self.assertNotIn("SourceCapture", fields)
        self.assertNotIn("RegistrationSnapshot", fields)
        self.assertIn(
            "isSharedCollisionTileComparisonPerformed() { return false; }",
            source,
        )
        self.assertIn(
            "isSchedulerCorrelationPerformed() { return false; }", source
        )

    def test_runtime_registration_fingerprint_is_baseline_compatible(self):
        source = OBSERVATION.read_text(encoding="utf-8")
        method = source.split(
            "private static String fingerprintRegistrations(", 1
        )[1].split("private static String fingerprintSource(", 1)[0]
        self.assertIn("updateInt(digest, ordered.size())", method)
        self.assertIn("registration.updateFingerprint(digest)", method)
        self.assertNotIn("updateBoolean", method)
        self.assertIn("updateInt(digest, -1)", method)

    def test_plan_records_slice_200_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 200: Runtime authored registration baseline comparison",
            plan,
        )
        self.assertIn("scheduler correlation", plan)
        self.assertIn("STABLE_BASELINE_MISMATCH", plan)


if __name__ == "__main__":
    unittest.main()
