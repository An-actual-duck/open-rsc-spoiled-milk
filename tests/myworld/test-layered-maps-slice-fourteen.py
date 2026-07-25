#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
DATABASE_SOURCE = ROOT / "server/src/com/openrsc/server/database/GameDatabase.java"
PLAYER_SERVICE_SOURCE = ROOT / "server/src/com/openrsc/server/service/PlayerService.java"
PLAYER_DATA_SOURCE = ROOT / "server/src/com/openrsc/server/database/struct/PlayerData.java"
SNAPSHOT_SOURCE = SERVER_COORDINATES / "LegacyPlayerLocationPersistenceSnapshot.java"
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
        return new Point(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
'''


PERSISTENCE_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

public final class LegacyPlayerLocationPersistenceSnapshotFixture {
    public static void main(String[] args) {
        check("legacy-player-location-shadow-v1".equals(
            LegacyPlayerLocationPersistenceSnapshot.ID), "format ID");

        int[] packedY = {0, 943, 944, 1887, 1888, 2831, 2832, 3775};
        int[] levels = {0, 0, 1, 1, 2, 2, -1, -1};
        int[] geographicY = {0, 943, 0, 943, 0, 943, 0, 943};
        for (int index = 0; index < packedY.length; index++) {
            Point persisted = Point.location(100 + index, packedY[index]);
            LegacyPlayerLocationPersistenceSnapshot snapshot =
                LegacyPlayerLocationPersistenceSnapshot.capture(persisted);
            check(snapshot.getPackedX() == 100 + index, "packed X");
            check(snapshot.getPackedY() == packedY[index], "packed Y");
            check(snapshot.toLegacyPoint().getX() == persisted.getX(), "written X");
            check(snapshot.toLegacyPoint().getY() == persisted.getY(), "written Y");
            WorldCoordinate coordinate = snapshot.getLayeredLocation().getCoordinate();
            check(coordinate.getX() == 100 + index, "layered X");
            check(coordinate.getY() == geographicY[index], "layered Y");
            check(coordinate.getLevel() == levels[index], "layered level");
            check(snapshot.requireLayeredLocation(snapshot.getLayeredLocation())
                .equals(snapshot.getLayeredLocation()), "matching mirror");
        }

        LegacyPlayerLocationPersistenceSnapshot underground =
            LegacyPlayerLocationPersistenceSnapshot.capture(Point.location(216, 3300));
        check(underground.getLayeredLocation().equals(
            WorldLocation.global(new WorldCoordinate(216, 468, -1))),
            "aligned underground snapshot");
        expectState(() -> underground.requireLayeredLocation(
            WorldLocation.global(new WorldCoordinate(216, 468, 0))));

        WorldLocation deep =
            LayeredCompatibilityPointAdapter.syntheticDeepEntry();
        LegacyPlayerLocationPersistenceSnapshot deepSnapshot =
            LegacyPlayerLocationPersistenceSnapshot.capture(
                Point.location(450, 600), deep, true);
        check(deepSnapshot.getLayeredLocation().equals(deep),
            "named deep projection remains authoritative");
        check(deepSnapshot.getPackedX() == 450
            && deepSnapshot.getPackedY() == 600,
            "named deep compatibility receipt");
        expectIllegal(() ->
            LegacyPlayerLocationPersistenceSnapshot.capture(
                Point.location(450, 600), deep, false));
        expectIllegal(() ->
            LegacyPlayerLocationPersistenceSnapshot.capture(
                Point.location(451, 600), deep, true));

        expectIllegal(() -> LegacyPlayerLocationPersistenceSnapshot.capture(
            Point.location(-1, 0)));
        expectIllegal(() -> LegacyPlayerLocationPersistenceSnapshot.capture(
            Point.location(32768, 0)));
        expectIllegal(() -> LegacyPlayerLocationPersistenceSnapshot.capture(
            Point.location(0, -1)));
        expectIllegal(() -> LegacyPlayerLocationPersistenceSnapshot.capture(
            Point.location(0, 3776)));
        expectNull(() -> LegacyPlayerLocationPersistenceSnapshot.capture(null));
        expectNull(() -> underground.requireLayeredLocation(null));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
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


class LayeredMapsSliceFourteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-fourteen-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LegacyPlayerLocationPersistenceSnapshotFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(PERSISTENCE_FIXTURE, encoding="utf-8")

        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                str(point),
                str(fixture),
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_legacy_persistence_snapshot_is_exact_and_capability_bounded(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LegacyPlayerLocationPersistenceSnapshotFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_load_and_save_use_shadow_without_changing_database_shape(self):
        database = DATABASE_SOURCE.read_text(encoding="utf-8")
        player_service = PLAYER_SERVICE_SOURCE.read_text(encoding="utf-8")
        player_data = PLAYER_DATA_SOURCE.read_text(encoding="utf-8")
        snapshot = SNAPSHOT_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("player.getLayeredLocation()", database)
        self.assertIn("WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE", database)
        self.assertIn(
            "LegacyPlayerLocationPersistenceSnapshot.capture(\n"
            "\t\t\t\t\tplayer.getLocation(),",
            database,
        )
        self.assertIn("playerData.xLocation = locationSnapshot.getPackedX();", database)
        self.assertIn("playerData.yLocation = locationSnapshot.getPackedY();", database)
        self.assertIn("queryUpdatePlayerLocation(playerId, locationSnapshot.toLegacyPoint())", database)
        self.assertIn("Point.location(playerData.xLocation, playerData.yLocation)", player_service)
        self.assertIn("locationSnapshot.requireLayeredLocation(player.getLayeredLocation())", player_service)
        self.assertIn("public int xLocation;", player_data)
        self.assertIn("public int yLocation;", player_data)
        self.assertNotIn("worldSpace", player_data)
        self.assertNotIn("level", player_data)
        self.assertNotIn("fromLayered", snapshot)
        self.assertIn("### Slice 14: Checked legacy Player persistence shadow", plan)


if __name__ == "__main__":
    unittest.main()
