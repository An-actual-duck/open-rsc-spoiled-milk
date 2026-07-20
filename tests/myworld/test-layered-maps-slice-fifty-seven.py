#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVATION = COORDINATES / (
    "LayeredPackedRegionAuthoredProvenanceObservation.java"
)
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
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

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

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

import java.util.Collections;

public final class AuthoredProvenanceObservationFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest manifest = manifest();
        LayeredPackedRegionRetirementSafetyAssessment safety = safety();
        LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest source =
            manifest.findSource(4, 0);

        LayeredPackedRegionAuthoredProvenanceObservation.Builder builder =
            LayeredPackedRegionAuthoredProvenanceObservation.builder(
                manifest, safety, 9L);
        LayeredAuthoredPlacementIdentity scenery =
            source.getPlacements().get(0).getIdentity();
        LayeredAuthoredPlacementIdentity firstNpc =
            source.getPlacements().get(2).getIdentity();
        LayeredAuthoredPlacementIdentity secondNpc =
            source.getPlacements().get(3).getIdentity();

        builder.recordRuntimeInstance(scenery, 101, 4, 0, true);
        builder.recordRuntimeInstance(scenery, 100, 4, 0, true);
        builder.recordRuntimeInstance(firstNpc, 7, 5, 0, true);
        builder.recordRuntimeInstance(secondNpc, 8, 4, 0, false);
        builder.recordRuntimeInstance(
            identity(4L, 4, 0, 1, "SCENERY"), 100, 4, 0, true);
        builder.recordRuntimeInstance(
            identity(5L, 4, 0, 99, "SCENERY"), 100, 4, 0, true);

        LayeredPackedRegionAuthoredProvenanceObservation result =
            builder.build();
        check(result.getGeneration() == 5L
            && result.getSafetyObservedAtTick() == 8L
            && result.getRuntimeObservedAtTick() == 9L
            && result.getSourceCount() == 1,
            "observation preserves generation, ticks, and bounded sources");
        check(result.getExpectedPlacementCount() == 5
            && result.getMatchedIdentityCount() == 2
            && result.getAbsentIdentityCount() == 2
            && result.getDuplicateIdentityCount() == 1,
            "expected identities distinguish match, absence, and duplicate");
        check(result.getRuntimeInstanceCount() == 4
            && result.getActiveRuntimeInstanceCount() == 3
            && result.getInactiveRuntimeInstanceCount() == 1
            && result.getAtAuthoredSourceInstanceCount() == 2
            && result.getAwayFromAuthoredSourceInstanceCount() == 1,
            "runtime identities distinguish active, inactive, origin, and roam");
        check(result.getReplacementObjectInstanceCount() == 1
            && result.getStaleGenerationInstanceCount() == 1
            && result.getUnrecognizedIdentityInstanceCount() == 1,
            "replacement and refused identities remain explicit");
        check(result.getExpectedSceneryCount() == 1
            && result.getExpectedBoundaryCount() == 1
            && result.getExpectedNpcSpawnCount() == 2
            && result.getExpectedGroundItemSpawnCount() == 1
            && result.getExpectedHarvestingSceneryCount() == 0,
            "expected family totals are exact");
        check(result.getRuntimeSceneryCount() == 2
            && result.getRuntimeBoundaryCount() == 0
            && result.getRuntimeNpcSpawnCount() == 2
            && result.getRuntimeGroundItemSpawnCount() == 0,
            "runtime family totals are exact");
        expectImmutable(result.getSources());
        expectState(builder::build);
        expectNull(() -> LayeredPackedRegionAuthoredProvenanceObservation
            .builder(null, safety, 9L));
        expectNull(() -> LayeredPackedRegionAuthoredProvenanceObservation
            .builder(manifest, null, 9L));
        expectIllegal(() -> LayeredPackedRegionAuthoredProvenanceObservation
            .builder(manifest, safety, -1L));
    }

    private static LayeredPackedRegionAuthoredPlacementManifest manifest() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder builder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(5L);
        builder.recordScenery(4, 0, 100, 100, 200, 20, 0, 0, null);
        builder.recordBoundary(4, 0, 5, 5, 201, 20, 0, 1, null);
        builder.recordNpcSpawn(4, 0, 7, 202, 20, 201, 203, 19, 21);
        builder.recordNpcSpawn(4, 0, 8, 203, 20, 202, 204, 19, 21);
        builder.recordGroundItemSpawn(4, 0, 10, 204, 20, 1, 30, 0);
        return builder.build();
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety() {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        WorldRegionKey key = new WorldRegionKey(
            WorldSpaceId.GLOBAL, 0, 4, 0);
        WorldRegionWindow window = new WorldRegionWindow(
            key.getWorldSpace(), key.getLevel(), key.getRegionX(),
            key.getRegionY(), key.getRegionX(), key.getRegionY());
        check(residency.registerPackedRegion(4, 0), "register packed source");
        LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
            ownership.openOwner(window, 1);
        retirement.observeOwnershipChange(opened.getChange(), 1L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(opened.getOwnerToken()), 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L);
        LayeredRegionRetirementDecisionArbiter.Decision decision =
            arbiter.evaluate(candidate, retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L));
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(decision), 1, 2);
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness,
            Collections.singletonList(
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents.of(
                        source.getPackedRegionX(), source.getPackedRegionY(),
                        true, true, false, 0, 0, 0, 0)),
            8L, 1);
    }

    private static LayeredAuthoredPlacementIdentity identity(
            long generation, int x, int y, int ordinal, String kind) {
        return new LayeredAuthoredPlacementIdentity(
            generation, x, y, ordinal,
            LayeredPackedRegionAuthoredConstructionInventory
                .ConstructionKind.valueOf(kind));
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

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
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


class LayeredMapsSliceFiftySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-fifty-seven-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredProvenanceObservationFixture.java"
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

    def test_count_only_provenance_states_are_exact_and_immutable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredProvenanceObservationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_scan_is_bounded_observational_and_documented(self):
        observation = OBSERVATION.read_text(encoding="utf-8")
        region = REGION.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("MAXIMUM_RUNTIME_INSTANCES", observation)
        self.assertNotIn("com.openrsc.server.model.entity", observation)
        self.assertNotIn("com.openrsc.server.model.world.region", observation)
        self.assertIn("recordAuthoredProvenance(", region)
        self.assertIn("captureAuthoredProvenance(", manager)
        self.assertIn("synchronized (layeredRegionLifecycleLock)", manager)
        self.assertNotIn("registerPackedRegion", observation)
        self.assertNotIn("unregisterPackedRegion", observation)
        self.assertNotIn(".unload(", observation)
        self.assertIn("PackedRegionAuthoredProvenanceSource", observer)
        self.assertIn("packedRegionAuthoredProvenance", observer)
        self.assertIn(
            "### Slice 57: Bounded authored runtime provenance census",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
