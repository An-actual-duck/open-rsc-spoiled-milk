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
OBSERVATION = REGION / (
    "LayeredPackedRegionRuntimeAuthoredObjectObservation.java"
)
REGION_SOURCE = REGION / "Region.java"
MANAGER_SOURCE = REGION / "RegionManager.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_197 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-seven.py"
)))


CAPTURE_DECLARATIONS = r'''
        final LayeredPackedRegionRuntimeAuthoredObjectObservation[]
            exactRuntimeObservations =
                new LayeredPackedRegionRuntimeAuthoredObjectObservation[1];
        final LayeredPackedRegionRuntimeAuthoredObjectObservation[]
            mixedRuntimeObservations =
                new LayeredPackedRegionRuntimeAuthoredObjectObservation[1];
        final LayeredPackedRegionRuntimeAuthoredObjectObservation[]
            emptyRuntimeObservations =
                new LayeredPackedRegionRuntimeAuthoredObjectObservation[1];
'''


CAPTURE_INVOCATION = r'''
                    exactRuntimeObservations[0] =
                        exactRuntimeObservation(reload);
                    mixedRuntimeObservations[0] =
                        mixedRuntimeObservation(reload);
                    emptyRuntimeObservations[0] =
                        LayeredPackedRegionRuntimeAuthoredObjectObservation
                            .observe(
                                reload, 15L,
                                Collections.singletonList(
                                    region.captureRuntimeAuthoredObjectSource()),
                                LayeredPackedRegionRuntimeAuthoredObjectObservation
                                    .MAXIMUM_OBJECT_INSTANCES);
'''


OBSERVATION_ASSERTIONS = r'''
        LayeredPackedRegionRuntimeAuthoredObjectObservation exactRuntime =
            exactRuntimeObservations[0];
        LayeredPackedRegionRuntimeAuthoredObjectObservation
            .SourceObservation exactSource =
                exactRuntime.getSources().get(0);
        check(exactRuntime.getGeneration() == 9L
                && exactRuntime.getRequirementsObservedAtTick() == 12L
                && exactRuntime.getRecipeObservedAtTick() == 14L
                && exactRuntime.getRuntimeObservedAtTick() == 15L
                && exactRuntime.getResidencyMirrorVersion() >= 1L
                && exactRuntime.getSourceCount() == 1
                && exactRuntime.getExpectedAuthoredObjectCount() == 3L
                && exactRuntime.getObservedObjectCount() == 3L
                && exactRuntime.getIdentitylessDynamicObjectCount() == 0L
                && exactRuntime.getAuthoredIdentityObjectCount() == 3L
                && exactRuntime.getRecognizedAuthoredInstanceCount() == 3L
                && exactRuntime.getUnrecognizedAuthoredInstanceCount() == 0L
                && exactRuntime.getUniqueRecognizedIdentityCount() == 3L
                && exactRuntime
                    .getDuplicateRecognizedIdentityInstanceCount() == 0L
                && exactRuntime.getMissingExpectedIdentityCount() == 0L
                && exactRuntime.getExactFinalLiveInstanceCount() == 3L
                && exactRuntime.getAuthoredTransientInstanceCount() == 0L
                && exactRuntime.getCollisionRegistrationPresentCount() == 3L
                && exactRuntime.getCollisionRegistrationMissingCount() == 0L
                && exactRuntime
                    .getCollisionRegistrationConstructorMismatchCount() == 0L
                && exactRuntime
                    .getCollisionRegistrationContributionCount() == 0L
                && exactRuntime
                    .getCollisionRegistrationRegionReferenceCount() == 3L
                && exactRuntime.getFingerprintSha256().length() == 64,
            "exact runtime authored-object census drifted");
        check(exactSource.getSourceOrdinal() == 0
                && exactSource.getPackedRegionX() == 4
                && exactSource.getPackedRegionY() == 0
                && exactSource.getExpectedAuthoredObjectCount() == 3
                && exactSource.getObservedObjectCount() == 3
                && exactSource.isFinalLiveAuthoredSetPresent()
                && exactSource
                    .areRecognizedRegistrationsConstructorMatched()
                && exactSource
                    .getCollisionRegistrationFingerprintSha256().length()
                        == 64
                && exactSource.getFingerprintSha256().length() == 64,
            "exact runtime source did not preserve its closed summary");

        LayeredPackedRegionRuntimeAuthoredObjectObservation mixedRuntime =
            mixedRuntimeObservations[0];
        LayeredPackedRegionRuntimeAuthoredObjectObservation
            .SourceObservation mixedSource =
                mixedRuntime.getSources().get(0);
        check(mixedRuntime.getExpectedAuthoredObjectCount() == 3L
                && mixedRuntime.getObservedObjectCount() == 7L
                && mixedRuntime.getIdentitylessDynamicObjectCount() == 1L
                && mixedRuntime.getAuthoredIdentityObjectCount() == 6L
                && mixedRuntime.getRecognizedAuthoredInstanceCount() == 3L
                && mixedRuntime.getUnrecognizedAuthoredInstanceCount() == 3L
                && mixedRuntime.getUniqueRecognizedIdentityCount() == 2L
                && mixedRuntime
                    .getDuplicateRecognizedIdentityInstanceCount() == 1L
                && mixedRuntime.getMissingExpectedIdentityCount() == 1L
                && mixedRuntime.getExactFinalLiveInstanceCount() == 2L
                && mixedRuntime.getAuthoredTransientInstanceCount() == 1L
                && mixedRuntime.getCollisionRegistrationPresentCount() == 4L
                && mixedRuntime.getCollisionRegistrationMissingCount() == 1L
                && mixedRuntime
                    .getCollisionRegistrationConstructorMismatchCount() == 1L
                && mixedRuntime
                    .getCollisionRegistrationRegionReferenceCount() == 4L,
            "mixed runtime classifications were conflated");
        check(mixedSource.getStaleGenerationInstanceCount() == 1
                && mixedSource.getNonObjectIdentityInstanceCount() == 1
                && mixedSource.getUnknownRecipeIdentityInstanceCount() == 1
                && !mixedSource.isFinalLiveAuthoredSetPresent()
                && !mixedSource
                    .areRecognizedRegistrationsConstructorMatched(),
            "mixed runtime source lost typed identity or registration evidence");

        LayeredPackedRegionRuntimeAuthoredObjectObservation emptyRuntime =
            emptyRuntimeObservations[0];
        check(emptyRuntime.getObservedObjectCount() == 0L
                && emptyRuntime.getExpectedAuthoredObjectCount() == 3L
                && emptyRuntime.getMissingExpectedIdentityCount() == 3L
                && emptyRuntime.areAllObjectBoundariesHeldDuringCapture(),
            "real Region object-boundary capture did not report absence");
        expectUnsupported(() -> exactRuntime.getSources().clear());
        expectIllegalArgument(() ->
            LayeredPackedRegionRuntimeAuthoredObjectObservation.observe(
                reloads[0], 15L, Collections.emptyList(),
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .MAXIMUM_OBJECT_INSTANCES));
        check(exactRuntime.isPointInTimeOnly()
                && exactRuntime.isDetachedSummaryOnly()
                && !exactRuntime.isSharedCollisionTileComparisonPerformed()
                && !exactRuntime.isRuntimeHandleRetained()
                && !exactRuntime.isSourceAbsencePerformed()
                && !exactRuntime.isSourceReconstructionPerformed()
                && !exactRuntime.isRuntimeMutationAuthorized()
                && !exactRuntime.isRuntimeMutationPerformed()
                && !exactRuntime.isRuntimeCacheInvalidated()
                && !exactRuntime.isRegionRegistryMutated()
                && !exactRuntime.isResidencyMirrorMutated()
                && !exactRuntime.isVisibilityCacheMutated()
                && !exactRuntime.isArrivalGate()
                && !exactRuntime.isVisibilityReleased()
                && !exactRuntime.isLifecycleAuthority(),
            "runtime authored-object census crossed lifecycle authority");
        System.out.println("runtime-authored-object-observation-ok");
'''


HELPERS = r'''
    private static LayeredPackedRegionRuntimeAuthoredObjectObservation
        exactRuntimeObservation(
            final LayeredPackedRegionReloadRecipe reload) {
        java.util.List<
            LayeredPackedRegionRuntimeAuthoredObjectObservation.ObjectSnapshot>
                objects = new java.util.ArrayList<
                    LayeredPackedRegionRuntimeAuthoredObjectObservation
                        .ObjectSnapshot>();
        for (com.openrsc.server.model.world.coordinate
                .LayeredPackedRegionAuthoredReconstructionRecipe
                    .ReconstructionPlacement placement
                : reload.getSources().get(0).getAuthoredPlacements()) {
            if (isObjectKind(placement.getKind())) {
                objects.add(runtimeObject(
                    placement,
                    placement.getPlacement().getConstructedEntityId(),
                    placement.getIdentity(), 0));
            }
        }
        return LayeredPackedRegionRuntimeAuthoredObjectObservation.observe(
            reload, 15L,
            Collections.singletonList(
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .SourceCapture.capture(4, 0, objects, true)),
            LayeredPackedRegionRuntimeAuthoredObjectObservation
                .MAXIMUM_OBJECT_INSTANCES);
    }

    private static LayeredPackedRegionRuntimeAuthoredObjectObservation
        mixedRuntimeObservation(
            final LayeredPackedRegionReloadRecipe reload) {
        java.util.List<com.openrsc.server.model.world.coordinate
            .LayeredPackedRegionAuthoredReconstructionRecipe
                .ReconstructionPlacement> placements =
                    reload.getSources().get(0).getAuthoredPlacements();
        com.openrsc.server.model.world.coordinate
            .LayeredPackedRegionAuthoredReconstructionRecipe
                .ReconstructionPlacement scenery = placements.get(0);
        com.openrsc.server.model.world.coordinate
            .LayeredPackedRegionAuthoredReconstructionRecipe
                .ReconstructionPlacement boundary = placements.get(1);
        com.openrsc.server.model.world.coordinate
            .LayeredPackedRegionAuthoredReconstructionRecipe
                .ReconstructionPlacement npc = placements.get(2);
        java.util.List<
            LayeredPackedRegionRuntimeAuthoredObjectObservation.ObjectSnapshot>
                objects = new java.util.ArrayList<
                    LayeredPackedRegionRuntimeAuthoredObjectObservation
                        .ObjectSnapshot>();
        objects.add(runtimeObject(
            scenery, scenery.getPlacement().getConstructedEntityId(),
            scenery.getIdentity(), 0));
        objects.add(runtimeObject(
            boundary, 40, boundary.getIdentity(), 0));
        objects.add(runtimeObject(
            scenery, scenery.getPlacement().getConstructedEntityId(),
            scenery.getIdentity(), 0));
        objects.add(
            LayeredPackedRegionRuntimeAuthoredObjectObservation.ObjectSnapshot
                .declare(90, 90, 205, 15, 0, 0, null, 0, null, null));
        objects.add(runtimeObject(
            scenery, 51,
            new com.openrsc.server.model.world.coordinate
                .LayeredAuthoredPlacementIdentity(
                    8L, 4, 0, 1, ConstructionKind.SCENERY),
            1));
        objects.add(runtimeObject(
            scenery, 52, npc.getIdentity(), 2));
        objects.add(runtimeObject(
            scenery, 53,
            new com.openrsc.server.model.world.coordinate
                .LayeredAuthoredPlacementIdentity(
                    9L, 4, 0, 99, ConstructionKind.SCENERY),
            0));
        return LayeredPackedRegionRuntimeAuthoredObjectObservation.observe(
            reload, 15L,
            Collections.singletonList(
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .SourceCapture.capture(4, 0, objects, true)),
            LayeredPackedRegionRuntimeAuthoredObjectObservation
                .MAXIMUM_OBJECT_INSTANCES);
    }

    private static
        LayeredPackedRegionRuntimeAuthoredObjectObservation.ObjectSnapshot
            runtimeObject(
                final com.openrsc.server.model.world.coordinate
                    .LayeredPackedRegionAuthoredReconstructionRecipe
                        .ReconstructionPlacement placement,
                final int objectId,
                final com.openrsc.server.model.world.coordinate
                    .LayeredAuthoredPlacementIdentity identity,
                final int registrationMode) {
        com.openrsc.server.model.world.coordinate
            .LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
                definition = placement.getPlacement();
        LayeredPackedRegionRuntimeAuthoredObjectObservation
            .RegistrationSnapshot registration = null;
        if (registrationMode != 1) {
            int registeredId = registrationMode == 2
                ? objectId + 1 : objectId;
            registration =
                LayeredPackedRegionRuntimeAuthoredObjectObservation
                    .RegistrationSnapshot.declare(
                        registeredId, definition.getPermanentObjectId(),
                        definition.getPackedX(), definition.getPackedY(),
                        definition.getDirection(), definition.getObjectType(),
                        Collections.emptyList(),
                        Collections.singletonList(
                            new LayeredPackedRegionRuntimeAuthoredObjectObservation
                                .RegionSnapshot(4, 0)));
        }
        return LayeredPackedRegionRuntimeAuthoredObjectObservation
            .ObjectSnapshot.declare(
                objectId, definition.getPermanentObjectId(),
                definition.getPackedX(), definition.getPackedY(),
                definition.getDirection(), definition.getObjectType(),
                definition.getObjectOwner(), 0, identity, registration);
    }

    private static boolean isObjectKind(final ConstructionKind kind) {
        return kind == ConstructionKind.SCENERY
            || kind == ConstructionKind.BOUNDARY
            || kind == ConstructionKind.HARVESTING_SCENERY;
    }

'''


def build_fixture():
    fixture = SLICE_197["build_fixture"]()
    fixture = fixture.replace(
        "        boolean entered =\n",
        CAPTURE_DECLARATIONS + "        boolean entered =\n",
        1,
    )
    fixture = fixture.replace(
        "                    reloads[0] = reload;\n",
        "                    reloads[0] = reload;\n" + CAPTURE_INVOCATION,
        1,
    )
    fixture = fixture.replace(
        "        LayeredPackedRegionAuthoredCollisionVerificationBatch collisionBatch =\n",
        OBSERVATION_ASSERTIONS
        + "        LayeredPackedRegionAuthoredCollisionVerificationBatch collisionBatch =\n",
        1,
    )
    fixture = fixture.replace(
        "    private static LayeredPackedRegionAuthoredReconstructionRecipe\n"
        "        authoredRecipe() {\n",
        HELPERS
        + "    private static LayeredPackedRegionAuthoredReconstructionRecipe\n"
        "        authoredRecipe() {\n",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-runtime-authored-object-observation-"
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
            SLICE_197["SLICE_194"]["SLICE_191"]["SLICE_188"]
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

    def test_exact_mixed_and_absent_runtime_states_are_classified(self):
        self.assertIn(
            "runtime-authored-object-observation-ok",
            self.fixture_run.stdout,
        )

    def test_region_capture_holds_the_real_object_boundary(self):
        source = REGION_SOURCE.read_text(encoding="utf-8")
        self.assertIn("captureRuntimeAuthoredObjectSource()", source)
        self.assertIn("synchronized (objects)", source)
        self.assertIn("Thread.holdsLock(objects)", source)
        self.assertIn("ObjectSnapshot.capture(object)", source)

    def test_manager_requires_the_existing_source_lifecycle_boundary(self):
        source = MANAGER_SOURCE.read_text(encoding="utf-8")
        self.assertIn(
            "captureLayeredPackedRegionRuntimeAuthoredObjects(", source
        )
        self.assertIn(
            "!Thread.holdsLock(layeredRegionLifecycleLock)", source
        )
        self.assertIn("peekRegionFromSectorCoordinates(", source)
        self.assertIn("captureRuntimeAuthoredObjectSource()", source)
        self.assertNotIn(
            "getRegionFromSectorCoordinates(\n"
            "\t\t\t\tsource.getPackedRegionX()",
            source,
        )

    def test_result_retains_summary_not_temporary_runtime_captures(self):
        source = OBSERVATION.read_text(encoding="utf-8")
        result_fields = source.split(
            "public final class "
            "LayeredPackedRegionRuntimeAuthoredObjectObservation {", 1
        )[1].split(
            "private LayeredPackedRegionRuntimeAuthoredObjectObservation(", 1
        )[0]
        self.assertNotIn("List<SourceCapture>", result_fields)
        self.assertNotIn("List<ObjectSnapshot>", result_fields)
        self.assertNotIn("GameObject ", result_fields)
        self.assertIn(
            "isSharedCollisionTileComparisonPerformed() { return false; }",
            source,
        )
        self.assertIn(
            "isRuntimeMutationPerformed() { return false; }", source
        )

    def test_plan_records_slice_199_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 199: Runtime authored-object observation", plan
        )
        self.assertIn("identity-less dynamic objects", plan)
        self.assertIn("shared live collision", plan)


if __name__ == "__main__":
    unittest.main()
