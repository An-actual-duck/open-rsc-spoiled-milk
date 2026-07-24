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
DETACHMENT = REGION / (
    "LayeredPackedRegionAuthoredObjectDetachmentPlan.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_200 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred.py"
)))


DECLARATION = r'''
        final LayeredPackedRegionAuthoredObjectDetachmentPlan[]
            authoredObjectDetachmentPlans =
                new LayeredPackedRegionAuthoredObjectDetachmentPlan[1];
'''


CAPTURE = r'''
                    authoredObjectDetachmentPlans[0] =
                        LayeredPackedRegionAuthoredObjectDetachmentPlan.define(
                            reload, exactBaselineComparisons[0]);
                    expectIllegalArgument(() ->
                        LayeredPackedRegionAuthoredObjectDetachmentPlan.define(
                            reload, nonFinalComparisons[0]));
'''


ASSERTIONS = r'''
        LayeredPackedRegionAuthoredObjectDetachmentPlan detachment =
            authoredObjectDetachmentPlans[0];
        LayeredPackedRegionAuthoredObjectDetachmentPlan.SourcePlan
            detachmentSource = detachment.getSources().get(0);
        check(detachment.getGeneration() == 9L
                && detachment.getRequirementsObservedAtTick() == 12L
                && detachment.getRecipeObservedAtTick() == 14L
                && detachment.getRuntimeObservedAtTick() == 15L
                && detachment.getResidencyMirrorVersion() >= 1L
                && detachment.getAuthoredGeneration()
                    == reloads[0].getAuthoredGeneration()
                && detachment.getSourceCount() == 1
                && detachment.getAuthoredObjectCount() == 3L
                && detachment.getPlayerCountAtObservation() == 0L
                && detachment.getNpcCountAtObservation() == 1L
                && detachment.getIdentitylessDynamicObjectCount() == 0L
                && detachment.getGroundItemCountAtObservation() == 1L
                && detachment
                    .getRuntimeComparisonFingerprintSha256().equals(
                        exactBaselineComparisons[0].getFingerprintSha256())
                && detachment.getFingerprintSha256().length() == 64,
            "detachment plan did not retain its exact inert baseline");
        check(detachmentSource.getSelectedSourceOrdinal() == 0
                && detachmentSource.getPackedRegionX() == 4
                && detachmentSource.getPackedRegionY() == 0
                && detachmentSource.getObjectCount() == 3
                && detachmentSource.getNpcCountAtObservation() == 1
                && detachmentSource.getGroundItemCountAtObservation() == 1
                && detachmentSource
                    .getRuntimeRegistrationFingerprintSha256().equals(
                        detachmentSource
                            .getBaselineRegistrationFingerprintSha256())
                && detachmentSource.getFingerprintSha256().length() == 64,
            "detachment source did not preserve baseline identity");
        java.util.List<
            LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment>
                reverseObjects = detachmentSource.getObjects();
        check(reverseObjects.get(0).getDetachmentOrdinal() == 0
                && reverseObjects.get(1).getDetachmentOrdinal() == 1
                && reverseObjects.get(2).getDetachmentOrdinal() == 2
                && reverseObjects.get(0).getAuthoredSourceOrdinal()
                    > reverseObjects.get(1).getAuthoredSourceOrdinal()
                && reverseObjects.get(1).getAuthoredSourceOrdinal()
                    > reverseObjects.get(2).getAuthoredSourceOrdinal(),
            "authored objects were not planned in reverse stable order");
        for (LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment
                object : reverseObjects) {
            check(object.getAuthoredGeneration()
                        == detachment.getAuthoredGeneration()
                    && object.getSourcePackedRegionX() == 4
                    && object.getSourcePackedRegionY() == 0
                    && object.getPackedX() / 48 == 4
                    && object.getPackedY() / 48 == 0
                    && object.getObjectId() >= 0
                    && object.getPermanentObjectId() >= 0
                    && object.getDirection() >= 0
                    && object.getDirection() <= 7
                    && (object.getObjectType() == 0
                        || object.getObjectType() == 1),
                "detachment constructor or authored identity drifted");
        }
        expectUnsupported(() -> detachment.getSources().clear());
        expectUnsupported(() -> detachmentSource.getObjects().clear());
        check(detachment.isExactRuntimeBaselineRequired()
                && detachment.isReverseStableAuthoredOrder()
                && detachment.isSchedulerCorrelationRequired()
                && detachment.isFreshAtomicRuntimeRevalidationRequired()
                && detachment.isCollisionDetachmentRequired()
                && !detachment.isPlayerPreservationRequired()
                && detachment.isNpcPreservationRequired()
                && !detachment.isDynamicObjectPreservationRequired()
                && detachment.isGroundItemPreservationRequired()
                && detachment.isRollbackRequired()
                && detachment.isArrivalGateRequired()
                && detachment.isVisibilityGateRequired()
                && detachment.isPointInTimeOnly()
                && detachment.isDetachedDefinitionOnly()
                && !detachment.isExecutableDetachment()
                && !detachment.isRuntimeLookupPerformed()
                && !detachment.isSharedCollisionTileReadPerformed()
                && !detachment.isSchedulerCorrelationPerformed()
                && !detachment.isRuntimeMutationAuthorized()
                && !detachment.isRuntimeMutationPerformed()
                && !detachment.isRuntimeCacheInvalidated()
                && !detachment.isSourceAbsencePerformed()
                && !detachment.isSourceReconstructionPerformed()
                && !detachment.isRegionRegistryMutated()
                && !detachment.isResidencyMirrorMutated()
                && !detachment.isVisibilityCacheMutated()
                && !detachment.isRuntimeHandleRetained()
                && !detachment.isArrivalGate()
                && !detachment.isVisibilityReleased()
                && !detachment.isLifecycleAuthority(),
            "inert detachment definition crossed runtime authority");
        System.out.println("authored-object-detachment-plan-ok");
'''


def build_fixture():
    fixture = SLICE_200["build_fixture"]()
    declaration_marker = (
        "        final LayeredPackedRegionAuthoredCollisionFootprintPlan[]\n"
    )
    if fixture.count(declaration_marker) != 1:
        raise AssertionError("detachment declaration marker changed")
    fixture = fixture.replace(
        declaration_marker, DECLARATION + declaration_marker, 1
    )
    capture_marker = "                    expectIllegalArgument(() ->\n"
    if fixture.count(capture_marker) < 1:
        raise AssertionError("detachment capture marker changed")
    fixture = fixture.replace(capture_marker, CAPTURE + capture_marker, 1)
    assertion_marker = (
        '        System.out.println('
        '"runtime-authored-object-baseline-comparison-ok");\n'
    )
    if fixture.count(assertion_marker) != 1:
        raise AssertionError("detachment assertion marker changed")
    fixture = fixture.replace(
        assertion_marker, ASSERTIONS + assertion_marker, 1
    )
    return fixture


class LayeredMapsSliceTwoHundredTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-authored-object-detachment-plan-"
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
            SLICE_200["SLICE_199"]["SLICE_197"]["SLICE_194"]
            ["SLICE_191"]["SLICE_188"]["SLICE_185"]["SLICE_182"]
            ["SLICE_181"]["SLICE_179"]["build_requirements_fixture"](),
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

    def test_exact_runtime_baseline_defines_reverse_detachment_order(self):
        self.assertIn(
            "authored-object-detachment-plan-ok",
            self.fixture_run.stdout,
        )

    def test_plan_retains_detached_authored_scalars_only(self):
        source = DETACHMENT.read_text(encoding="utf-8")
        fields = source.split(
            "public final class LayeredPackedRegionAuthoredObjectDetachmentPlan {",
            1,
        )[1].split(
            "private LayeredPackedRegionAuthoredObjectDetachmentPlan(", 1
        )[0]
        self.assertNotIn("GameObject", fields)
        self.assertNotIn("Region ", fields)
        self.assertNotIn("RegistrationSnapshot", fields)
        self.assertIn(
            "isSchedulerCorrelationRequired() { return true; }", source
        )
        self.assertIn(
            "isExecutableDetachment() { return false; }", source
        )
        self.assertIn(
            "isRuntimeMutationPerformed() { return false; }", source
        )

    def test_plan_records_slice_202_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 202: Inert authored-object detachment plan", plan
        )
        self.assertIn("reverse stable", plan)
        self.assertIn("construction order", plan)
        self.assertIn("scheduler correlation", plan)


if __name__ == "__main__":
    unittest.main()
