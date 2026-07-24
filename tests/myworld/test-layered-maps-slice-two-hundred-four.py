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
BATCH = REGION / (
    "LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_203 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-three.py"
)))


DECLARATION = r'''
        final LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch[]
            disposableDetachmentBatches =
                new
                    LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch[1];
'''


CAPTURE = r'''
                    disposableDetachmentBatches[0] =
                        LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch
                            .captureWithCollisionPlanFactory(
                                manager, boundary, reload,
                                authoredObjectDetachmentPlans[0],
                                LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch
                                    .MAXIMUM_VERIFICATION_SOURCES,
                                (replay, membership) ->
                                    retainedCollisionPlans[0]);
                    expectIllegalArgument(() ->
                        LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch
                            .captureWithCollisionPlanFactory(
                                manager, boundary, reload,
                                authoredObjectDetachmentPlans[0], 0,
                                (replay, membership) -> {
                                    throw new AssertionError(
                                        "invalid detachment budget reached factory");
                                }));
'''


ASSERTIONS = r'''
        LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch
            roundTripBatch = disposableDetachmentBatches[0];
        LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch
            .SourceVerification roundTripSource =
                roundTripBatch.getSources().get(0);
        check(roundTripBatch.getGeneration() == 9L
                && roundTripBatch.getRequirementsObservedAtTick() == 12L
                && roundTripBatch.getObservedAtTick() == 14L
                && roundTripBatch.getRuntimeObservedAtTick() == 15L
                && roundTripBatch.getResidencyMirrorVersion() >= 1L
                && roundTripBatch.getAuthoredGeneration()
                    == authoredObjectDetachmentPlans[0]
                        .getAuthoredGeneration()
                && roundTripBatch.getSourceCount() == 1
                && roundTripBatch.getReplayPlacementCount() == 5L
                && roundTripBatch.getAuthoredObjectCount() == 3L
                && roundTripBatch
                    .getDisposableRegionConstructionCount() == 2L
                && roundTripBatch.getSupportRegionCount() == 1L
                && roundTripBatch.getReconstructionTransactionCount() == 3L
                && roundTripBatch.getReconstructionBoundaryCount() == 4L
                && roundTripBatch
                    .getReconstructionCacheInvalidationCount() == 3L
                && roundTripBatch.getDetachmentTransactionCount() == 3L
                && roundTripBatch.getDetachmentBoundaryCount() == 4L
                && roundTripBatch
                    .getDetachmentCacheInvalidationCount() == 3L
                && roundTripBatch.getCollisionRegistrationCount() == 3L
                && roundTripBatch
                    .getCollisionRegistrationClearedCount() == 3L
                && roundTripBatch
                    .getCollisionContributionReferenceCount() == 4L
                && roundTripBatch.getCollisionRegionReferenceCount() == 4L
                && roundTripBatch.getVerifiedRegionTileCount() == 4608L,
            "disposable detachment batch totals drifted");
        check(roundTripBatch
                    .getDetachmentPlanFingerprintSha256().equals(
                        authoredObjectDetachmentPlans[0]
                            .getFingerprintSha256())
                && roundTripBatch.getFingerprintSha256().length() == 64
                && roundTripSource.getSourceOrdinal() == 0
                && roundTripSource.getPackedRegionX() == 4
                && roundTripSource.getPackedRegionY() == 0
                && roundTripSource.getReplayPlacementCount() == 5
                && roundTripSource.getAuthoredObjectCount() == 3
                && roundTripSource.getFingerprintSha256().length() == 64
                && roundTripSource
                    .getDetachmentPlanFingerprintSha256().equals(
                        authoredObjectDetachmentPlans[0].getSources().get(0)
                            .getFingerprintSha256())
                && roundTripSource
                    .getPreDetachmentRegistrationFingerprintSha256().equals(
                        authoredObjectDetachmentPlans[0].getSources().get(0)
                            .getRuntimeRegistrationFingerprintSha256())
                && !roundTripSource
                    .getPreDetachmentStateFingerprintSha256().equals(
                        roundTripSource
                            .getPostDetachmentStateFingerprintSha256()),
            "disposable detachment batch fingerprints drifted");
        expectUnsupported(() -> roundTripBatch.getSources().clear());
        check(roundTripBatch.areAllSourcesVerified()
                && roundTripBatch.isPointInTimeOnly()
                && roundTripBatch.isDetachedSummaryOnly()
                && roundTripBatch.isDisposableReconstructionPerformed()
                && roundTripBatch.isDisposableDetachmentPerformed()
                && !roundTripBatch.isRuntimeHandleRetained()
                && !roundTripBatch.isRuntimeSourceMutated()
                && !roundTripBatch.isRuntimeCollisionMutated()
                && !roundTripBatch.isRuntimeCacheInvalidated()
                && !roundTripBatch.isSourceAbsencePerformed()
                && !roundTripBatch.isSourceReconstructionPerformed()
                && !roundTripBatch.isSchedulerCorrelationPerformed()
                && !roundTripBatch.isActiveFamilyPreservationPerformed()
                && !roundTripBatch.isRegionRegistryMutated()
                && !roundTripBatch.isResidencyMirrorMutated()
                && !roundTripBatch.isVisibilityCacheMutated()
                && !roundTripBatch.isArrivalGate()
                && !roundTripBatch.isVisibilityReleased()
                && !roundTripBatch.isLifecycleAuthority(),
            "disposable detachment batch crossed runtime authority");
        System.out.println("disposable-authored-object-detachment-batch-ok");
'''


def build_fixture():
    fixture = SLICE_203["build_fixture"]()
    declaration_marker = (
        "        final\n"
        "            LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification[]\n"
    )
    if fixture.count(declaration_marker) != 1:
        raise AssertionError("detachment batch declaration marker changed")
    fixture = fixture.replace(
        declaration_marker, DECLARATION + declaration_marker, 1
    )
    capture_marker = (
        "                    expectIllegalArgument(() ->\n"
        "                        LayeredPackedRegionAuthoredObjectDetachmentPlan"
        ".define(\n"
    )
    if fixture.count(capture_marker) != 1:
        raise AssertionError("detachment batch capture marker changed")
    fixture = fixture.replace(
        capture_marker, CAPTURE + capture_marker, 1
    )
    assertion_marker = (
        '        System.out.println('
        '"disposable-authored-object-detachment-ok");\n'
    )
    if fixture.count(assertion_marker) != 1:
        raise AssertionError("detachment batch assertion marker changed")
    fixture = fixture.replace(
        assertion_marker, ASSERTIONS + assertion_marker, 1
    )
    return fixture


class LayeredMapsSliceTwoHundredFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-disposable-authored-detachment-batch-"
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
            SLICE_203["SLICE_202"]["SLICE_200"]["SLICE_199"]
            ["SLICE_197"]["SLICE_194"]["SLICE_191"]["SLICE_188"]
            ["SLICE_185"]["SLICE_182"]["SLICE_181"]["SLICE_179"]
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

    def test_batch_reduces_disposable_round_trip_per_exact_source(self):
        self.assertIn(
            "disposable-authored-object-detachment-batch-ok",
            self.fixture_run.stdout,
        )

    def test_batch_retains_counts_and_fingerprints_only(self):
        source = BATCH.read_text(encoding="utf-8")
        fields = source.split(
            "LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch {",
            1,
        )[1].split(
            "private\n"
            "\t\tLayeredPackedRegionAuthoredObjectDetachmentVerificationBatch(",
            1,
        )[0]
        self.assertNotIn("Region ", fields)
        self.assertNotIn("GameObject", fields)
        self.assertNotIn("TileValue", fields)
        self.assertIn(
            "isRuntimeSourceMutated() { return false; }", source
        )
        self.assertIn(
            "isSchedulerCorrelationPerformed() { return false; }", source
        )

    def test_public_capture_uses_existing_definition_seam(self):
        source = BATCH.read_text(encoding="utf-8")
        capture = source.split(
            "public static\n"
            "\t\tLayeredPackedRegionAuthoredObjectDetachmentVerificationBatch capture(",
            1,
        )[1].split(
            "static\n"
            "\t\tLayeredPackedRegionAuthoredObjectDetachmentVerificationBatch",
            1,
        )[0]
        self.assertIn(
            "defineLayeredPackedRegionAuthoredCollisionFootprints", capture
        )
        self.assertNotIn("peekRegionFromSectorCoordinates", capture)

    def test_plan_records_slice_204_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 204: Bounded disposable detachment batch", plan
        )
        self.assertIn("complete selected-source set", plan)
        self.assertIn("private exposure", plan)


if __name__ == "__main__":
    unittest.main()
