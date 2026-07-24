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
ISOLATED_VERIFIER = (
    REGION / "LayeredPackedRegionIsolatedTerrainVerifier.java"
)
VERIFICATION = (
    REGION / "LayeredPackedRegionIsolatedTerrainVerification.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_181 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-one.py"
)))


FIXTURE = SLICE_181["FIXTURE"].replace(
    """        final LayeredPackedRegionTerrainInitializationPlan[] terrain =
            new LayeredPackedRegionTerrainInitializationPlan[1];""",
    """        final LayeredPackedRegionTerrainInitializationPlan[] terrain =
            new LayeredPackedRegionTerrainInitializationPlan[1];
        final LayeredPackedRegionBlankContainerPlan[] containers =
            new LayeredPackedRegionBlankContainerPlan[1];""",
).replace(
    """                    LayeredPackedRegionBlankContainerPlan container =
                        LayeredPackedRegionBlankContainerPlan.define(
                            reload, 0);""",
    """                    LayeredPackedRegionBlankContainerPlan container =
                        LayeredPackedRegionBlankContainerPlan.define(
                            reload, 0);
                    containers[0] = container;""",
).replace(
    """        check(entered && captured[0] != null && terrain[0] != null,
            "real source lifecycle boundary did not produce terrain input");""",
    """        check(entered && captured[0] != null && terrain[0] != null
                && containers[0] != null,
            "real source lifecycle boundary did not produce terrain input");
        LayeredPackedRegionIsolatedTerrainVerification verification =
            LayeredPackedRegionIsolatedTerrainVerifier.verify(
                manager, containers[0], terrain[0]);
        check(verification.getGeneration() == 9L
                && verification.getRequirementsObservedAtTick() == 12L
                && verification.getObservedAtTick() == 14L
                && verification.getResidencyMirrorVersion() >= 1L
                && verification.getAuthoredGeneration() == 9L
                && verification.getSourceOrdinal() == 0
                && verification.getPackedRegionX() == 4
                && verification.getPackedRegionY() == 0
                && verification.getVerifiedTileCount() == 2304
                && verification.getTerrainCollisionMaskTileCount() == 1
                && verification.getTerrainProjectileBlockedTileCount() == 1
                && verification.getSealedBaseTraversalTileCount() == 1
                && verification.getTerrainFingerprintSha256().length() == 64,
            "isolated terrain verification lost exact plan identity");
        check(verification.isVerificationOnly()
                && verification.isDisposableRegionConstructed()
                && verification.isBlankContractMatchedBeforeApply()
                && verification.isTerrainApplyPerformedOnDisposableRegion()
                && verification.isAllTerrainTilesMatchedAfterApply()
                && verification.isDynamicProductsAbsentAfterApply()
                && verification
                    .isEmptyEntityMembershipMatchedAfterApply()
                && !verification.isExecutableReload()
                && !verification.isUsableRegionContainerReturned()
                && !verification.isRuntimeHandleRetained()
                && !verification.isSourceAbsencePerformed()
                && !verification.isSourceReconstructionPerformed()
                && !verification.isAuthoredReplayPerformed()
                && !verification.isDynamicCollisionRebuildPerformed()
                && !verification.isActiveFamilyPreservationPerformed()
                && !verification.isRegionRegistryMutated()
                && !verification.isResidencyMirrorMutated()
                && !verification.isVisibilityCacheMutated()
                && !verification.isArrivalGate()
                && !verification.isVisibilityReleased()
                && !verification.isLifecycleAuthority(),
            "isolated terrain verification crossed runtime authority");""",
)


class LayeredMapsSliceOneHundredEightyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-isolated-terrain-"
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
            SLICE_181["SLICE_179"]["build_requirements_fixture"](),
            encoding="utf-8",
        )
        region_fixture.write_text(FIXTURE, encoding="utf-8")
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
            cwd=ROOT,
            check=True,
        )
        cls.runtime_classpath = os.pathsep.join(
            (str(cls.classes), classpath)
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_isolated_region_applies_and_exactly_verifies_terrain(self):
        result = subprocess.run(
            [
                "java", "-cp", self.runtime_classpath,
                "com.openrsc.server.model.world.region."
                "PackedRegionTerrainBoundaryCaptureFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_isolated_apply_cannot_access_runtime_indexes(self):
        source = ISOLATED_VERIFIER.read_text(encoding="utf-8")
        for required in (
            "Region isolated = new Region(",
            "isolated.verifyLayeredBlankContainer(",
            "isolated.getMutableTileValue(",
            "matchesTerrain(actual, input)",
            "hasNoDynamicProducts(actual)",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "regions.",
            "getRegion(",
            "getRegionFromSectorCoordinates(",
            "peekRegionFromSectorCoordinates(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "layeredRegionResidencyMirror",
            "visibleRegionWindowCache",
            "visibleObjectWindowCache",
            "visibleObjectSnapshotCache",
            "layeredRegionLifecycleLock",
        ):
            self.assertNotIn(forbidden, source)

    def test_receipt_returns_no_container_or_lifecycle_authority(self):
        source = VERIFICATION.read_text(encoding="utf-8")
        for forbidden in (
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "getRegion()",
            "getRegionManager()",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isTerrainApplyPerformedOnDisposableRegion()",
            "isUsableRegionContainerReturned() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_eighty_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 182: Isolated static-terrain application", plan
        )
        self.assertIn("disposable Region", plan)


if __name__ == "__main__":
    unittest.main()
