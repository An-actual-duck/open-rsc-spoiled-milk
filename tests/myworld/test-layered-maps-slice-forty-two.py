#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


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

public final class LayeredRegionRetirementEligibilityLedgerFixture {
    public static void main(String[] args) {
        expectIllegal(() -> new LayeredRegionRetirementEligibilityLedger(0L));
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency = new LayeredRegionResidencyMirror();
        for (int x = 4; x <= 7; x++) {
            check(residency.registerPackedRegion(x, 0), "register resident " + x);
        }

        LayeredRegionInterestOwnershipLedger.OpenedOwner first =
            ownership.openOwner(window(4, 5), 2);
        retirement.observeOwnershipChange(first.getChange(), 1L);
        check(snapshot(retirement, ownership, residency, key(4), 1L)
            .getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.PINNED,
            "first owner pins key four");

        LayeredRegionInterestOwnershipLedger.OpenedOwner second =
            ownership.openOwner(window(5, 6), 2);
        retirement.observeOwnershipChange(second.getChange(), 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot shared = snapshot(
            retirement, ownership, residency, key(5), 2L);
        check(shared.getReferenceCount() == 2, "overlap has two pins");
        check(shared.getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.PINNED,
            "shared key remains pinned");

        LayeredRegionInterestOwnershipLedger.Change firstClose =
            ownership.closeOwner(first.getOwnerToken());
        retirement.observeOwnershipChange(firstClose, 10L);
        LayeredRegionRetirementEligibilityLedger.Snapshot cooling = snapshot(
            retirement, ownership, residency, key(4), 10L);
        check(cooling.getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.COOLING_DOWN,
            "last release begins cooldown");
        check(cooling.getReleasedAtTick().longValue() == 10L
            && cooling.getEligibleAtTick().longValue() == 15L
            && cooling.getRemainingCooldownTicks() == 5L,
            "cooldown boundaries are explicit");
        check(snapshot(retirement, ownership, residency, key(4), 14L)
            .getRemainingCooldownTicks() == 1L,
            "cooldown remains in force before expiry");
        LayeredRegionRetirementEligibilityLedger.Snapshot stillShared = snapshot(
            retirement, ownership, residency, key(5), 14L);
        check(stillShared.getReferenceCount() == 1
            && stillShared.getEligibleAtTick() == null,
            "shared release does not begin retirement");

        LayeredRegionInterestOwnershipLedger.OpenedOwner reacquired =
            ownership.openOwner(window(4, 4), 1);
        retirement.observeOwnershipChange(reacquired.getChange(), 14L);
        LayeredRegionRetirementEligibilityLedger.Snapshot repinned = snapshot(
            retirement, ownership, residency, key(4), 14L);
        check(repinned.getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.PINNED,
            "reacquisition cancels cooldown");
        check(repinned.getReleasedAtTick() == null
            && repinned.getEligibleAtTick() == null,
            "reacquisition removes stale release evidence");

        retirement.observeOwnershipChange(
            ownership.closeOwner(reacquired.getOwnerToken()), 20L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(second.getOwnerToken()), 21L);
        LayeredRegionRetirementEligibilityLedger.Snapshot firstEligible = snapshot(
            retirement, ownership, residency, key(4), 25L);
        check(firstEligible.isRetirementEligible(),
            "re-released key becomes eligible after a fresh full cooldown");
        check(firstEligible.getReleasedAtTick().longValue() == 20L,
            "reacquisition reset the release origin");
        check(snapshot(retirement, ownership, residency, key(5), 25L)
            .getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.COOLING_DOWN,
            "later release keeps its own cooldown");
        check(snapshot(retirement, ownership, residency, key(5), 26L)
            .isRetirementEligible(), "later release expires independently");

        check(snapshot(retirement, ownership, residency, key(7), 26L)
            .getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.UNTRACKED,
            "never-owned resident key is conservatively untracked");
        check(snapshot(retirement, ownership, residency, key(8), 26L)
            .getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.NOT_RESIDENT,
            "absent key needs no retirement");
        WorldRegionKey deep = new WorldRegionKey(WorldSpaceId.GLOBAL, -2, 4, 0);
        check(snapshot(retirement, ownership, residency, deep, 26L)
            .getRetirementState()
            == LayeredRegionRetirementEligibilityLedger.RetirementState.UNSUPPORTED,
            "legacy-unsupported key is never eligible");
        expectIllegal(() -> retirement.snapshot(
            ownership.snapshot(key(4)), residency.snapshot(key(5)), 26L));
        expectIllegal(() -> snapshot(
            retirement, ownership, residency, key(4), 25L));
        expectNull(() -> retirement.observeOwnershipChange(null, 26L));
        check(retirement.getReferencedRegionCount() == 0,
            "all pins released");
        check(retirement.getTrackedReleaseCount() == 3,
            "only globally released keys have tracked release records");
        check(retirement.clear(), "clear populated projection");
        check(!retirement.clear(), "clear empty projection is a no-op");
    }

    private static LayeredRegionRetirementEligibilityLedger.Snapshot snapshot(
            LayeredRegionRetirementEligibilityLedger retirement,
            LayeredRegionInterestOwnershipLedger ownership,
            LayeredRegionResidencyMirror residency,
            WorldRegionKey key,
            long tick) {
        return retirement.snapshot(
            ownership.snapshot(key), residency.snapshot(key), tick);
    }

    private static WorldRegionWindow window(int minX, int maxX) {
        return new WorldRegionWindow(WorldSpaceId.GLOBAL, 0, minX, 0, maxX, 0);
    }

    private static WorldRegionKey key(int x) {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, 0, x, 0);
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

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceFortyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-forty-two-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredRegionRetirementEligibilityLedgerFixture.java"
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

    def test_shared_pins_cooldown_reacquisition_and_conservative_states(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredRegionRetirementEligibilityLedgerFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_projection_cannot_load_release_or_evict_regions(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("LAYERED_REGION_RETIREMENT_COOLDOWN_TICKS = 16L", manager)
        self.assertIn("LayeredRegionRetirementEligibilityLedger", manager)
        self.assertIn("observeOwnershipChange(", manager)
        self.assertIn("getLayeredRegionRetirementEligibilitySnapshot(", manager)
        self.assertIn("layeredRegionRetirementEligibilityLedger.clear();", manager)
        self.assertNotIn("LayeredRegionRetirementEligibilityLedger", path_validation)
        self.assertNotIn("LayeredRegionRetirementEligibilityLedger", observer)

        projection = manager.split(
            "/**\n\t * Returns conservative pin/cooldown evidence", 1
        )[1].split(
            "/**\n\t * Compares one bounded logical interest change", 1
        )[0]
        self.assertNotIn("getRegion(", projection)
        self.assertNotIn("registerPackedRegion", projection)
        self.assertNotIn("unregisterPackedRegion", projection)
        self.assertNotIn(".unload(", projection)
        self.assertIn(
            "### Slice 42: Dormant Region retirement cooldown policy",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
