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
VERIFIER = REGION / (
    "LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier.java"
)
RECEIPT = REGION / (
    "LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification.java"
)
COLLISION_PLAN = REGION / (
    "LayeredPackedRegionAuthoredCollisionFootprintPlan.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_202 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-two.py"
)))


DECLARATION = r'''
        final
            LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification[]
                disposableDetachmentVerifications =
                    new
                        LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification[1];
'''


CAPTURE = r'''
                    LayeredPackedRegionBlankContainerPlan detachmentContainer =
                        LayeredPackedRegionBlankContainerPlan.define(reload, 0);
                    LayeredPackedRegionTerrainInitializationPlan
                        detachmentTerrain =
                            LayeredPackedRegionTerrainInitializationPlan
                                .defineFromResidentTileStates(
                                    detachmentContainer, boundary,
                                    manager
                                        .captureLayeredPackedRegionTerrainTileStates(
                                            boundary, 0));
                    LayeredPackedRegionIsolatedTerrainVerification
                        detachmentTerrainVerification =
                            LayeredPackedRegionIsolatedTerrainVerifier.verify(
                                manager, detachmentContainer,
                                detachmentTerrain);
                    LayeredPackedRegionAuthoredReplayPlan detachmentReplay =
                        LayeredPackedRegionAuthoredReplayPlan.define(
                            reload, 0, detachmentTerrainVerification);
                    LayeredPackedRegionIsolatedAuthoredObjectVerification
                        detachmentMembership =
                            LayeredPackedRegionIsolatedAuthoredObjectVerifier
                                .verify(
                                    manager, detachmentContainer,
                                    detachmentTerrain, detachmentReplay);
                    disposableDetachmentVerifications[0] =
                        LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier
                            .verifyDetachment(
                                manager, detachmentContainer,
                                detachmentTerrain, detachmentReplay,
                                detachmentMembership,
                                retainedCollisionPlans[0],
                                authoredObjectDetachmentPlans[0], 0);
'''


ASSERTIONS = r'''
        LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification
            roundTrip = disposableDetachmentVerifications[0];
        check(roundTrip.getGeneration() == 9L
                && roundTrip.getRequirementsObservedAtTick() == 12L
                && roundTrip.getObservedAtTick() == 14L
                && roundTrip.getRuntimeObservedAtTick() == 15L
                && roundTrip.getResidencyMirrorVersion() >= 1L
                && roundTrip.getAuthoredGeneration()
                    == authoredObjectDetachmentPlans[0]
                        .getAuthoredGeneration()
                && roundTrip.getSourceOrdinal() == 0
                && roundTrip.getPackedRegionX() == 4
                && roundTrip.getPackedRegionY() == 0
                && roundTrip.getAuthoredObjectCount() == 3
                && roundTrip.getDisposableRegionConstructionCount() == 2
                && roundTrip.getSupportRegionCount() == 1
                && roundTrip.getReconstructionTransactionCount() == 3
                && roundTrip.getReconstructionBoundaryCount() == 4
                && roundTrip
                    .getReconstructionCacheInvalidationCount() == 3
                && roundTrip.getDetachmentTransactionCount() == 3
                && roundTrip.getDetachmentBoundaryCount() == 4
                && roundTrip.getDetachmentCacheInvalidationCount() == 3
                && roundTrip.getCollisionRegistrationCount() == 3
                && roundTrip.getCollisionRegistrationClearedCount() == 3
                && roundTrip.getCollisionContributionReferenceCount() == 4
                && roundTrip.getCollisionRegionReferenceCount() == 4
                && roundTrip.getVerifiedRegionTileCount() == 4608,
            "disposable authored-object detachment totals drifted");
        check(roundTrip.getTerrainFingerprintSha256().length() == 64
                && roundTrip
                    .getAuthoredReplayFingerprintSha256().length() == 64
                && roundTrip
                    .getCollisionFootprintFingerprintSha256().length() == 64
                && roundTrip
                    .getDetachmentPlanFingerprintSha256().equals(
                        authoredObjectDetachmentPlans[0].getSources().get(0)
                            .getFingerprintSha256())
                && roundTrip
                    .getPreDetachmentRegistrationFingerprintSha256().equals(
                        authoredObjectDetachmentPlans[0].getSources().get(0)
                            .getRuntimeRegistrationFingerprintSha256())
                && roundTrip
                    .getPreDetachmentStateFingerprintSha256().length() == 64
                && roundTrip
                    .getPostDetachmentStateFingerprintSha256().length() == 64
                && !roundTrip
                    .getPreDetachmentStateFingerprintSha256().equals(
                        roundTrip
                            .getPostDetachmentStateFingerprintSha256()),
            "disposable authored-object detachment fingerprints drifted");
        check(roundTrip.isVerificationOnly()
                && roundTrip.isDisposableReconstructionPerformed()
                && roundTrip.isDisposableDetachmentPerformed()
                && roundTrip
                    .isExactRegistrationSequenceMatchedBeforeDetachment()
                && roundTrip.isReverseDetachmentOrderMatched()
                && roundTrip.isAllRegistrationsClearedAfterDetachment()
                && roundTrip.isTerrainMatchedAfterDetachment()
                && roundTrip.isCollisionProductsClearedAfterDetachment()
                && roundTrip.isObjectMembershipEmptyAfterDetachment()
                && roundTrip.isSupportRegionsRemainedStaticallyBlank()
                && roundTrip.isEntityFamiliesEmptyAfterDetachment()
                && !roundTrip.isUsableRegionContainerReturned()
                && !roundTrip.isRuntimeHandleRetained()
                && !roundTrip.isRuntimeSourceMutated()
                && !roundTrip.isRuntimeCollisionMutated()
                && !roundTrip.isRuntimeCacheInvalidated()
                && !roundTrip.isSourceAbsencePerformed()
                && !roundTrip.isSourceReconstructionPerformed()
                && !roundTrip.isSchedulerCorrelationPerformed()
                && !roundTrip.isActiveFamilyPreservationPerformed()
                && !roundTrip.isRegionRegistryMutated()
                && !roundTrip.isResidencyMirrorMutated()
                && !roundTrip.isVisibilityCacheMutated()
                && !roundTrip.isArrivalGate()
                && !roundTrip.isVisibilityReleased()
                && !roundTrip.isLifecycleAuthority(),
            "disposable authored-object detachment crossed runtime authority");
        System.out.println("disposable-authored-object-detachment-ok");
'''


def build_fixture():
    fixture = SLICE_202["build_fixture"]()
    declaration_marker = (
        "        final LayeredPackedRegionAuthoredObjectDetachmentPlan[]\n"
    )
    if fixture.count(declaration_marker) != 1:
        raise AssertionError("disposable detachment declaration marker changed")
    fixture = fixture.replace(
        declaration_marker, DECLARATION + declaration_marker, 1
    )
    capture_marker = (
        "                    expectIllegalArgument(() ->\n"
        "                        LayeredPackedRegionAuthoredObjectDetachmentPlan"
        ".define(\n"
    )
    if fixture.count(capture_marker) != 1:
        raise AssertionError("disposable detachment capture marker changed")
    fixture = fixture.replace(
        capture_marker, CAPTURE + capture_marker, 1
    )
    assertion_marker = (
        '        System.out.println("authored-object-detachment-plan-ok");\n'
    )
    if fixture.count(assertion_marker) != 1:
        raise AssertionError("disposable detachment assertion marker changed")
    fixture = fixture.replace(
        assertion_marker, ASSERTIONS + assertion_marker, 1
    )
    return fixture


class LayeredMapsSliceTwoHundredThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-disposable-authored-object-detachment-"
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
            SLICE_202["SLICE_200"]["SLICE_199"]["SLICE_197"]
            ["SLICE_194"]["SLICE_191"]["SLICE_188"]["SLICE_185"]
            ["SLICE_182"]["SLICE_181"]["SLICE_179"]
            ["build_requirements_fixture"](),
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

    def test_disposable_reconstruction_detaches_to_terrain_only_state(self):
        self.assertIn(
            "disposable-authored-object-detachment-ok",
            self.fixture_run.stdout,
        )

    def test_inverse_footprint_is_strict_and_non_legacy(self):
        source = COLLISION_PLAN.read_text(encoding="utf-8")
        method = source.split(
            "Result recreateVerifiedUnregisterPlannerResult(", 1
        )[1].split("private Result recreateVerifiedPlannerResult(", 1)[0]
        self.assertIn("Operation.UNREGISTER", method)
        shared = source.split(
            "private Result recreateVerifiedPlannerResult(", 1
        )[1].split("private boolean matches(", 1)[0]
        self.assertIn("definition, false", shared)

    def test_receipt_and_verifier_remain_disposable_only(self):
        receipt = RECEIPT.read_text(encoding="utf-8")
        verifier = VERIFIER.read_text(encoding="utf-8")
        fields = receipt.split(
            "LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification {",
            1,
        )[1].split(
            "private\n"
            "\t\tLayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification(",
            1,
        )[0]
        self.assertNotIn("Region ", fields)
        self.assertNotIn("GameObject", fields)
        self.assertIn(
            "isRuntimeSourceMutated() { return false; }", receipt
        )
        self.assertIn(
            "isSchedulerCorrelationPerformed() { return false; }", receipt
        )
        detachment_method = verifier.split("verifyDetachment(", 1)[1].split(
            "private static int findObject(", 1
        )[0]
        self.assertIn(
            "constructDisposableUnion(manager, collision)",
            detachment_method,
        )
        self.assertNotIn("peekRegionFromSectorCoordinates", detachment_method)
        self.assertNotIn("getRegion(", detachment_method)

    def test_plan_records_slice_203_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 203: Disposable authored-object detachment", plan
        )
        self.assertIn("terrain-only state", plan)
        self.assertIn("runtime detachment", plan)


if __name__ == "__main__":
    unittest.main()
