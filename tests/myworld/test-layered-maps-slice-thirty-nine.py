#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
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

public final class LayeredRegionInterestOwnershipLedgerFixture {
    public static void main(String[] args) {
        LayeredRegionInterestOwnershipLedger ledger =
            new LayeredRegionInterestOwnershipLedger();
        check(ledger.getVersion() == 0L, "initial version");
        LayeredRegionInterestOwnershipLedger.OwnerToken first = ledger.openOwner();
        LayeredRegionInterestOwnershipLedger.OwnerToken second = ledger.openOwner();
        check(first.getSequence() > 0L
            && second.getSequence() > first.getSequence(),
            "monotonic owner tokens");
        check(!first.isClosed() && !second.isClosed(), "new owners are open");
        check(ledger.getOpenOwnerCount() == 2, "two open owners");

        LayeredRegionInterestOwnershipLedger.Change firstInitial =
            ledger.synchronizeOwner(first, window(WorldSpaceId.GLOBAL, 0, 4, 5), 2);
        check(firstInitial.getGloballyAcquired().size() == 2,
            "first owner globally acquires both keys");
        check(firstInitial.getOwnerSequence() == first.getSequence(),
            "change exposes diagnostic sequence without the handle");
        check(firstInitial.getSharedAcquisitions().isEmpty(),
            "first owner has no shared acquisition");
        check(firstInitial.getEntries().get(0).getLogicalRegionKey().getRegionX() == 4,
            "deterministic entered order");
        check(ledger.getReferencedRegionCount() == 2, "two distinct keys");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, 0, 4) == 1,
            "first key count one");

        LayeredRegionInterestOwnershipLedger.Change secondInitial =
            ledger.synchronizeOwner(second, window(WorldSpaceId.GLOBAL, 0, 5, 6), 2);
        check(secondInitial.getGloballyAcquired().size() == 1,
            "second owner globally acquires one key");
        check(secondInitial.getSharedAcquisitions().size() == 1,
            "second owner shares one key");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, 0, 5) == 2,
            "overlap count two");
        check(ledger.getReferencedRegionCount() == 3, "three distinct keys");

        LayeredRegionInterestOwnershipLedger.Change firstMove =
            ledger.synchronizeOwner(first, window(WorldSpaceId.GLOBAL, 0, 5, 6), 2);
        check(firstMove.getGloballyAcquired().isEmpty(),
            "already-owned entry is not globally acquired");
        check(firstMove.getSharedAcquisitions().size() == 1,
            "entry into shared key increments its count");
        check(firstMove.getGloballyReleased().size() == 1,
            "last exit globally releases old key");
        check(firstMove.getSharedReleases().isEmpty(),
            "old key was not shared");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, 0, 4) == 0,
            "old key released");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, 0, 5) == 2,
            "retained overlap stays two");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, 0, 6) == 2,
            "new overlap becomes two");

        long beforeNoOp = ledger.getVersion();
        LayeredRegionInterestOwnershipLedger.Change noOp =
            ledger.synchronizeOwner(first, window(WorldSpaceId.GLOBAL, 0, 5, 6), 2);
        check(noOp.isNoOp(), "same window is a no-op");
        check(noOp.getEntries().size() == 2, "same window retains both keys");
        check(ledger.getVersion() == beforeNoOp, "no-op preserves version");

        LayeredRegionInterestOwnershipLedger.Change secondClose =
            ledger.closeOwner(second);
        check(secondClose.isOwnerClosed(), "second owner closes");
        check(secondClose.getSharedReleases().size() == 2,
            "shared exits remain globally referenced");
        check(secondClose.getGloballyReleased().isEmpty(),
            "shared exits are not global releases");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, 0, 5) == 1,
            "first owner retains overlap after second closes");

        long beforeDuplicateClose = ledger.getVersion();
        LayeredRegionInterestOwnershipLedger.Change duplicateClose =
            ledger.closeOwner(second);
        check(duplicateClose.isNoOp(), "duplicate close is idempotent");
        check(ledger.getVersion() == beforeDuplicateClose,
            "duplicate close preserves version");
        expectIllegal(() -> ledger.synchronizeOwner(
            second, window(WorldSpaceId.GLOBAL, 0, 5, 6), 2));
        LayeredRegionInterestOwnershipLedger foreignLedger =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionInterestOwnershipLedger.OwnerToken foreign =
            foreignLedger.openOwner();
        expectIllegal(() -> ledger.closeOwner(foreign));
        expectNull(() -> ledger.closeOwner(null));

        LayeredRegionInterestOwnershipLedger.OwnerToken third = ledger.openOwner();
        WorldSpaceId instance = new WorldSpaceId("instance.test-1");
        ledger.synchronizeOwner(third, window(instance, 0, 5, 5), 1);
        check(snapshot(ledger, instance, 0, 5) == 1,
            "world-space ownership is independent");
        check(snapshot(ledger, WorldSpaceId.GLOBAL, -1, 5) == 0,
            "level ownership is independent");

        expectIllegal(() -> ledger.synchronizeOwner(
            first, window(WorldSpaceId.GLOBAL, 0, 4, 6), 2));
        expectUnsupported(() -> firstMove.getEntries().clear());
        expectUnsupported(() -> firstMove.getGloballyReleased().clear());
        check(ledger.clear(), "clear active ownership");
        check(!ledger.clear(), "clear empty ownership is no-op");
        check(ledger.getOpenOwnerCount() == 0, "clear owners");
        check(ledger.getReferencedRegionCount() == 0, "clear references");
        check(ledger.closeOwner(first).isNoOp(),
            "owner cleared by world unload is already closed");
        check(first.isClosed() && second.isClosed() && third.isClosed(),
            "closed state remains visible on opaque handles");
        expectNull(() -> ledger.snapshot(null));
    }

    private static WorldRegionWindow window(
            WorldSpaceId worldSpace, int level, int minX, int maxX) {
        return new WorldRegionWindow(worldSpace, level, minX, 0, maxX, 0);
    }

    private static int snapshot(
            LayeredRegionInterestOwnershipLedger ledger,
            WorldSpaceId worldSpace,
            int level,
            int regionX) {
        LayeredRegionInterestOwnershipLedger.Snapshot snapshot = ledger.snapshot(
            new WorldRegionKey(worldSpace, level, regionX, 0));
        check(snapshot.getLogicalRegionKey().getRegionX() == regionX,
            "snapshot identity");
        check(snapshot.getLedgerVersion() == ledger.getVersion(),
            "snapshot version");
        check(snapshot.isReferenced() == (snapshot.getReferenceCount() > 0),
            "snapshot referenced flag");
        return snapshot.getReferenceCount();
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

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable result.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceThirtyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-thirty-nine-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredRegionInterestOwnershipLedgerFixture.java"
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

    def test_overlapping_owners_gate_global_release_without_identity_reuse(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredRegionInterestOwnershipLedgerFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_ledger_consumers_remain_checked_and_outside_gameplay_authority(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("LayeredRegionInterestOwnershipLedger", manager)
        self.assertNotIn("LayeredRegionInterestOwnershipLedger", path_validation)
        self.assertIn("LayeredRegionInterestOwnershipLedger", player)
        self.assertIn(
            "### Slice 39: Global logical-interest ownership model",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
