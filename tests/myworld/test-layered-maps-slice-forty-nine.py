#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ASSESSMENT = SERVER_COORDINATES / (
    "LayeredPackedRegionRetirementSafetyAssessment.java"
)
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LayeredPackedRegionRetirementSafetyAssessmentFixture {
    public static void main(String[] args) {
        quiescenceDoesNotBypassMissingReload();
        entityAndStorageBlockersRemainExplicit();
        readinessRefusalRemainsABlocker();
        invalidInputsAndCollectionsRemainBounded();
    }

    private static void quiescenceDoesNotBypassMissingReload() {
        Scenario scenario = new Scenario();
        LayeredPackedRegionRetirementReadiness readiness =
            scenario.readyReadiness();
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        LayeredPackedRegionRetirementSafetyAssessment assessment = assess(
            readiness,
            contents(source, true, true, false, 0, 0, 0, 0),
            8L);
        check(assessment.getObservedAtTick() == 8L
            && assessment.getReadinessObservedAtTick() == 7L
            && assessment.getOwnershipVersion()
                == readiness.getOwnershipVersion()
            && assessment.getResidencyMirrorVersion()
                == readiness.getResidencyMirrorVersion(),
            "assessment preserves versioned evidence");
        check(assessment.getSourceCount() == 1
            && assessment.getContentQuiescentSourceCount() == 1
            && assessment.getLifecycleReadySourceCount() == 0
            && assessment.getBlockedSourceCount() == 1,
            "quiescent source remains lifecycle blocked");
        LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment result =
            assessment.getSources().get(0);
        check(result.isContentQuiescent()
            && !result.isLifecycleReady()
            && result.getBlockers().equals(Collections.singletonList(
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .RELOAD_PATH_UNAVAILABLE)),
            "missing reload path is the exact quiescent blocker");

        LayeredPackedRegionRetirementSafetyAssessment future = assess(
            readiness,
            contents(source, true, true, true, 0, 0, 0, 0),
            8L);
        check(future.getLifecycleReadySourceCount() == 1
            && future.getBlockedSourceCount() == 0
            && future.getSources().get(0).getBlockers().isEmpty(),
            "assessment can prove a future reload-capable source");
    }

    private static void entityAndStorageBlockersRemainExplicit() {
        Scenario scenario = new Scenario();
        LayeredPackedRegionRetirementReadiness readiness =
            scenario.readyReadiness();
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        LayeredPackedRegionRetirementSafetyAssessment occupied = assess(
            readiness,
            contents(source, true, true, false, 1, 2, 3, 4),
            8L);
        LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment result =
            occupied.getSources().get(0);
        check(!result.isContentQuiescent() && !result.isLifecycleReady()
            && result.getPlayerCount() == 1
            && result.getNpcCount() == 2
            && result.getObjectCount() == 3
            && result.getGroundItemCount() == 4,
            "all content counts remain exact");
        check(result.getBlockers().equals(Arrays.asList(
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .PLAYERS_PRESENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .NPCS_PRESENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .OBJECTS_PRESENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .GROUND_ITEMS_PRESENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .RELOAD_PATH_UNAVAILABLE)),
            "every occupied-content blocker remains explicit");

        LayeredPackedRegionRetirementSafetyAssessment absent = assess(
            readiness,
            contents(source, false, false, false, 0, 0, 0, 0),
            8L);
        check(absent.getSources().get(0).getBlockers().equals(Arrays.asList(
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .SOURCE_NOT_RESIDENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .TILE_STORAGE_UNAVAILABLE,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .RELOAD_PATH_UNAVAILABLE)),
            "absent source cannot resemble a safe empty source");
    }

    private static void readinessRefusalRemainsABlocker() {
        Scenario scenario = new Scenario();
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            scenario.readyCandidate();
        scenario.open(key(), 7L);
        LayeredPackedRegionRetirementReadiness blocked =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(
                    scenario.currentDecision(candidate, 7L)), 1, 2);
        check(!blocked.getSources().get(0).isReady(),
            "fixture repin refuses readiness");
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            blocked.getSources().get(0);
        LayeredPackedRegionRetirementSafetyAssessment assessment = assess(
            blocked,
            contents(source, true, true, true, 0, 0, 0, 0),
            8L);
        check(assessment.getSources().get(0).isContentQuiescent()
            && !assessment.getSources().get(0).isLifecycleReady()
            && assessment.getSources().get(0).getBlockers().equals(
                Collections.singletonList(
                    LayeredPackedRegionRetirementSafetyAssessment.Blocker
                        .READINESS_NOT_READY)),
            "content quiescence cannot bypass refused logical readiness");
    }

    private static void invalidInputsAndCollectionsRemainBounded() {
        Scenario scenario = new Scenario();
        LayeredPackedRegionRetirementReadiness readiness =
            scenario.readyReadiness();
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents good =
            contents(source, true, true, false, 0, 0, 0, 0);
        LayeredPackedRegionRetirementSafetyAssessment assessment =
            LayeredPackedRegionRetirementSafetyAssessment.assess(
                readiness, Collections.singletonList(good), 8L, 1);
        expectImmutable(assessment.getSources());
        expectImmutable(assessment.getSources().get(0).getBlockers());
        expectNull(() -> LayeredPackedRegionRetirementSafetyAssessment.assess(
            null, Collections.singletonList(good), 8L, 1));
        expectNull(() -> LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, null, 8L, 1));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, Collections.singletonList(good), 6L, 1));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, Collections.singletonList(good), 8L, 0));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, Collections.emptyList(), 8L, 1));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, Collections.singletonList(
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents.of(
                        source.getPackedRegionX() + 1,
                        source.getPackedRegionY(), true, true, false,
                        0, 0, 0, 0)), 8L, 1));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents.of(4, 0, false, true, false, 0, 0, 0, 0));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents.of(4, 0, false, false, false, 1, 0, 0, 0));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents.of(4, 0, true, true, false, -1, 0, 0, 0));
    }

    private static LayeredPackedRegionRetirementSafetyAssessment assess(
            LayeredPackedRegionRetirementReadiness readiness,
            LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents
                contents,
            long tick) {
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, Collections.singletonList(contents), tick, 1);
    }

    private static LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents contents(
                LayeredPackedRegionRetirementReadiness.SourceReadiness source,
                boolean resident, boolean tiles, boolean reload,
                int players, int npcs, int objects, int groundItems) {
        return LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents.of(
                source.getPackedRegionX(), source.getPackedRegionY(),
                resident, tiles, reload, players, npcs, objects, groundItems);
    }

    private static WorldRegionKey key() {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, 0, 4, 0);
    }

    private static WorldRegionWindow window(WorldRegionKey key) {
        return new WorldRegionWindow(
            key.getWorldSpace(), key.getLevel(), key.getRegionX(),
            key.getRegionY(), key.getRegionX(), key.getRegionY());
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(List values) {
        try {
            values.add(new Object());
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static final class Scenario {
        private final LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        private final LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        private final LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        private final LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();

        private LayeredRegionInterestOwnershipLedger.OpenedOwner open(
                WorldRegionKey key, long tick) {
            LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
                ownership.openOwner(window(key), 1);
            retirement.observeOwnershipChange(opened.getChange(), tick);
            return opened;
        }

        private LayeredRegionRetirementEligibilityLedger.Snapshot
                readyCandidate() {
            check(residency.registerPackedRegion(4, 0),
                "register packed source");
            LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
                open(key(), 1L);
            retirement.observeOwnershipChange(
                ownership.closeOwner(opened.getOwnerToken()), 2L);
            return retirement.snapshot(
                ownership.snapshot(key()), residency.snapshot(key()), 7L);
        }

        private LayeredPackedRegionRetirementReadiness readyReadiness() {
            LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
                readyCandidate();
            return LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(currentDecision(candidate, 7L)),
                1, 2);
        }

        private LayeredRegionRetirementDecisionArbiter.Decision currentDecision(
                LayeredRegionRetirementEligibilityLedger.Snapshot candidate,
                long tick) {
            return arbiter.evaluate(candidate, retirement.snapshot(
                ownership.snapshot(candidate.getLogicalRegionKey()),
                residency.snapshot(candidate.getLogicalRegionKey()), tick));
        }
    }
}
'''


class LayeredMapsSliceFortyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-forty-nine-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionRetirementSafetyAssessmentFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_contents_assessment_stays_conservative_and_immutable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredPackedRegionRetirementSafetyAssessmentFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_uses_noncreating_snapshot_and_grants_no_authority(self):
        assessment = ASSESSMENT.read_text(encoding="utf-8")
        region = REGION.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "assessLayeredPackedRegionRetirementSafety(", manager
        )
        self.assertIn("LAYERED_PACKED_REGION_RELOAD_SUPPORTED = false", manager)
        self.assertIn("synchronized (layeredRegionLifecycleLock)", manager)
        self.assertIn("peekRegionFromSectorCoordinates(", manager)
        self.assertIn("captureRetirementContentsSnapshot()", region)
        for collection in ("players", "npcs", "objects", "items"):
            self.assertIn(f"synchronized ({collection})", region)

        boundary = manager.split(
            "/**\n\t * Captures read-only contents and quiescence evidence", 1
        )[1].split(
            "/**\n\t * Compares one bounded logical interest change", 1
        )[0]
        self.assertNotIn("getRegion(", boundary)
        self.assertNotIn("registerPackedRegion", boundary)
        self.assertNotIn("unregisterPackedRegion", boundary)
        self.assertNotIn(".unload(", boundary)
        self.assertNotIn("regions.remove", boundary)
        self.assertNotIn("invalidate", boundary)
        self.assertNotIn(
            "com.openrsc.server.model.world.region.Region", assessment
        )
        self.assertNotIn("LayeredPackedRegionRetirementSafetyAssessment", observer)
        self.assertNotIn("LayeredPackedRegionRetirementSafetyAssessment", path_validation)
        self.assertIn(
            "### Slice 49: Dormant packed-source contents safety assessment",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
