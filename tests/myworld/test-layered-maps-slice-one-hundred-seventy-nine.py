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
REGION_SOURCE = REGION / "Region.java"
ISOLATED_VERIFIER = (
    REGION / "LayeredPackedRegionIsolatedBlankContainerVerifier.java"
)
VERIFICATION = (
    REGION / "LayeredPackedRegionBlankContainerVerification.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_164 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-four.py"
)))


REQUIREMENTS_FACTORY_METHOD = r'''
    public static LayeredPackedRegionNpcOwnerPreservationRequirements
        requirementsForIsolatedRegionFixture() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 200, 20, 190, 210, 10, 30);
        LayeredAuthoredPlacementIdentity identity =
            manifestBuilder.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 210, 10, 30, 3, 4, 0, 0);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencies.build(),
                LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                    .build(manifest));
        LayeredPackedRegionActiveNpcResidencyObservation census =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 13L,
                Collections.singletonList(new NpcInstanceSnapshot(
                    identity, 10, 4, 0, true)), 1, 1);
        LayeredPackedRegionEventOwnershipInventory inventory = inventory(
            Collections.singletonList(ownerEvent(
                0, 51L, OwnerKind.NPC, ownerIdentity(identity, 10))));
        LayeredPackedRegionNpcOwnerEventContinuityAssessment continuity =
            LayeredPackedRegionNpcOwnerEventContinuityAssessment.assess(
                inventory, census, true, false, 1);
        return LayeredPackedRegionNpcOwnerPreservationRequirements.derive(
            inventory, continuity, 1, 1);
    }
'''


def build_requirements_fixture():
    fixture = SLICE_164["build_fixture"]()
    return fixture.replace(
        "    private static int countClassification(",
        REQUIREMENTS_FACTORY_METHOD
        + "\n    private static int countClassification(",
    )


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.ActiveNpcResidencyFixture;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementManifest;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPopulationOutcome;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredReconstructionRecipe;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.util.rsc.CollisionFlag;
import java.util.Collections;

public final class PackedRegionIsolatedBlankContainerFixture {
    public static void main(String[] args) {
        LayeredPackedRegionReloadRecipe reload;
        Object lifecycleLock = new Object();
        synchronized (lifecycleLock) {
            LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
                ActiveNpcResidencyFixture
                    .requirementsForIsolatedRegionFixture();
            LayeredPackedRegionSourceLifecycleBoundary boundary =
                LayeredPackedRegionSourceLifecycleBoundary.open(
                    requirements, 17L, Thread.holdsLock(lifecycleLock));
            LayeredPackedRegionSourceAbsencePreflight preflight =
                LayeredPackedRegionSourceAbsencePreflight.assess(
                    boundary,
                    Collections.singletonList(
                        LayeredPackedRegionSourceAbsencePreflight
                            .SourceInventory.of(
                                4, 0, true, 1, 1, 1, 0, 0, 5)),
                    14L, false, Thread.holdsLock(lifecycleLock));
            reload = LayeredPackedRegionReloadRecipe.compose(
                boundary, preflight, authoredRecipe(),
                Thread.holdsLock(lifecycleLock));
            boundary.invalidate();
        }

        LayeredPackedRegionBlankContainerPlan plan =
            LayeredPackedRegionBlankContainerPlan.define(reload, 0);
        RegionManager manager = new RegionManager(null);
        LayeredPackedRegionBlankContainerVerification verification =
            LayeredPackedRegionIsolatedBlankContainerVerifier.verify(
                manager, plan);
        check(verification.getGeneration() == 9L
                && verification.getRequirementsObservedAtTick() == 12L
                && verification.getObservedAtTick() == 14L
                && verification.getResidencyMirrorVersion() == 17L
                && verification.getAuthoredGeneration() == 9L
                && verification.getSourceOrdinal() == 0
                && verification.getPackedRegionX() == 4
                && verification.getPackedRegionY() == 0
                && verification.getVerifiedTileSlotCount() == 2304
                && verification.getInitialTraversalMask()
                    == CollisionFlag.FULL_BLOCK,
            "isolated blank verification lost exact contract identity");
        check(verification.isVerificationOnly()
                && verification.isDisposableRegionConstructed()
                && verification.isRegionManagerMatched()
                && verification.isSourceCoordinatesMatched()
                && verification.isCollisionBoundaryCoordinatesMatched()
                && verification.isExpandedTileStorageMatched()
                && verification.isIndependentMutableTilesMatched()
                && verification.isSealedTileDefaultsMatched()
                && verification.isEmptyEntityMembershipMatched(),
            "isolated Region did not satisfy its sealed contract");
        check(!verification.isExecutableReload()
                && !verification.isUsableRegionContainerReturned()
                && !verification.isRuntimeHandleRetained()
                && !verification.isSourceAbsencePerformed()
                && !verification.isSourceReconstructionPerformed()
                && !verification.isTerrainInitialized()
                && !verification.isAuthoredReplayPerformed()
                && !verification.isActiveFamilyPreservationPerformed()
                && !verification.isCollisionRebuildPerformed()
                && !verification.isRegionRegistryMutated()
                && !verification.isResidencyMirrorMutated()
                && !verification.isVisibilityCacheMutated()
                && !verification.isArrivalGate()
                && !verification.isVisibilityReleased()
                && !verification.isLifecycleAuthority(),
            "isolated verification crossed its disposable boundary");
    }

    private static LayeredPackedRegionAuthoredReconstructionRecipe
        authoredRecipe() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifest.recordNpcSpawn(
            4, 0, 10, 200, 20, 190, 210, 10, 30);
        LayeredPackedRegionAuthoredPlacementManifest definitions =
            manifest.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 210, 10, 30, 3, 4, 0, 0);
        return LayeredPackedRegionAuthoredReconstructionRecipe.derive(
            definitions, dependencies.build(),
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(definitions));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSeventyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-isolated-container-"
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
            "PackedRegionIsolatedBlankContainerFixture.java"
        )
        requirements_fixture.parent.mkdir(parents=True, exist_ok=True)
        region_fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements_fixture.write_text(
            build_requirements_fixture(), encoding="utf-8"
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

    def test_actual_region_is_constructed_verified_and_discarded(self):
        result = subprocess.run(
            [
                "java", "-cp", self.runtime_classpath,
                "com.openrsc.server.model.world.region."
                "PackedRegionIsolatedBlankContainerFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_verification_seam_never_enters_runtime_indexes(self):
        verifier = ISOLATED_VERIFIER.read_text(encoding="utf-8")
        self.assertIn("new Region(", verifier)
        self.assertIn("new Region.BlankContainerExpectation(", verifier)
        self.assertIn(
            "isolated.verifyLayeredBlankContainer(expectation)", verifier
        )
        self.assertIn(
            "LayeredPackedRegionBlankContainerVerification.verified(",
            verifier,
        )
        for forbidden in (
            "regions.",
            "regions.put(",
            "regions.remove(",
            "getRegionFromSectorCoordinates(",
            "peekRegionFromSectorCoordinates(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "layeredRegionResidencyMirror",
            "visibleRegionWindowCache",
            "visibleObjectWindowCache",
            "visibleObjectSnapshotCache",
            "layeredRegionLifecycleLock",
            ".unload()",
        ):
            self.assertNotIn(forbidden, verifier)

    def test_region_verification_returns_only_detached_receipt(self):
        region = REGION_SOURCE.read_text(encoding="utf-8")
        method_start = region.index(
            "verifyLayeredBlankContainer("
        )
        method = region[method_start:region.index(
            "\n\tpublic void unload()", method_start
        )]
        for required in (
            "IdentityHashMap<TileValue, Boolean>",
            "value.hasCollisionProductState()",
            "emptyEntityMembershipMatched",
            "new BlankContainerVerificationSnapshot(",
        ):
            self.assertIn(required, method)
        for forbidden in (
            "addEntity(",
            "removeEntity(",
            "addGameObjectUnderTransaction(",
            "removeGameObjectUnderTransaction(",
            "getRegion(",
        ):
            self.assertNotIn(forbidden, method)

        receipt = VERIFICATION.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "getRegion()",
            "getRegionManager()",
        ):
            self.assertNotIn(forbidden, receipt)
        self.assertIn(
            "isUsableRegionContainerReturned() { return false; }", receipt
        )
        self.assertIn(
            "isRuntimeHandleRetained() { return false; }", receipt
        )

    def test_living_plan_records_slice_one_hundred_seventy_nine(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 179: Isolated blank Region verification", plan
        )
        self.assertIn("verification-only", plan)


if __name__ == "__main__":
    unittest.main()
