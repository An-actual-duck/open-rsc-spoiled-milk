#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAYER_SERVICE = ROOT / "server/src/com/openrsc/server/service/PlayerService.java"
DATABASE = ROOT / "server/src/com/openrsc/server/database/GameDatabase.java"
CONFIGURATION = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
DEVELOPMENT = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
PLAN = (
    ROOT
    / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r"""
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("negative");
        }
        return new Point(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
"""


FIXTURE = r"""
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;
import java.util.HashMap;
import java.util.Map;

public final class LayeredPlayerLocationAuthorityFixture {
    public static void main(String[] args) {
        LayeredPlayerLocationAuthority authority =
            new LayeredPlayerLocationAuthority();
        expectState(() -> authority.requireCurrent(Point.location(100, 620)));

        Point surface = authority.initializeFromLegacy(Point.location(100, 620));
        check(surface.getX() == 100 && surface.getY() == 620,
            "surface compatibility projection");
        check(authority.requireCurrent(surface).equals(
            WorldLocation.global(new WorldCoordinate(100, 620, 0))),
            "surface authority");

        Point underground = authority.move(
            WorldLocation.global(new WorldCoordinate(100, 620, -1)));
        check(underground.getX() == 100 && underground.getY() == 3452,
            "underground compatibility projection");
        check(authority.requireCurrent(underground).getCoordinate().getLevel() == -1,
            "underground authority");
        expectState(() -> authority.requireCurrent(surface));

        expectIllegal(() -> authority.move(
            WorldLocation.global(new WorldCoordinate(100, 620, -2))));
        check(authority.requireCurrent(underground).getCoordinate().getLevel() == -1,
            "unsupported projection preserves authority");

        LayeredPlayerLocationAuthority deepAuthority =
            new LayeredPlayerLocationAuthority();
        deepAuthority.initializeFromLegacy(Point.location(450, 600));
        WorldLocation deepLocation =
            LayeredCompatibilityPointAdapter.syntheticDeepEntry();
        Point deepProjection = deepAuthority.move(deepLocation, true);
        check(deepProjection.getX() == 450 && deepProjection.getY() == 600,
            "deep compatibility projection");
        check(deepAuthority.requireCurrent(deepProjection, true).equals(
            deepLocation), "deep authority");
        expectIllegal(() -> deepAuthority.requireCurrent(
            deepProjection, false));

        Map<String, Object> cache = new HashMap<String, Object>();
        LayeredPlayerLocationPersistence.RestoreResult bootstrap =
            LayeredPlayerLocationPersistence.restore(
                cache, Point.location(120, 620));
        check(bootstrap.isRewriteRequired(), "bootstrap rewrite");
        check(LayeredPlayerLocationPersistence.LEGACY_BOOTSTRAP.equals(
            bootstrap.getOrigin()), "bootstrap origin");
        LayeredPlayerLocationPersistence.write(
            cache,
            bootstrap.getLocation(),
            Point.location(120, 620),
            bootstrap.getOrigin());
        check(cache.size() == 9, "complete persistence field set");

        LayeredPlayerLocationPersistence.RestoreResult exact =
            LayeredPlayerLocationPersistence.restore(
                cache, Point.location(120, 620));
        check(!exact.isRewriteRequired(), "exact record");
        check(exact.getLocation().equals(bootstrap.getLocation()),
            "exact location");

        LayeredPlayerLocationPersistence.RestoreResult rebased =
            LayeredPlayerLocationPersistence.restore(
                cache, Point.location(132, 502));
        check(rebased.isRewriteRequired(), "legacy mismatch rewrite");
        check(LayeredPlayerLocationPersistence.LEGACY_REBASE.equals(
            rebased.getOrigin()), "legacy mismatch origin");
        check(rebased.getLocation().equals(
            WorldLocation.global(new WorldCoordinate(132, 502, 0))),
            "legacy mismatch wins");

        Map<String, Object> partial = new HashMap<String, Object>();
        partial.put(LayeredPlayerLocationPersistence.KEY_FORMAT,
            LayeredPlayerLocationPersistence.FORMAT);
        expectState(() -> LayeredPlayerLocationPersistence.restore(
            partial, Point.location(120, 620)));

        Map<String, Object> malformed =
            new HashMap<String, Object>(cache);
        malformed.put(LayeredPlayerLocationPersistence.KEY_LEVEL, "zero");
        expectState(() -> LayeredPlayerLocationPersistence.restore(
            malformed, Point.location(120, 620)));

        Map<String, Object> divergent =
            new HashMap<String, Object>();
        expectState(() -> LayeredPlayerLocationPersistence.write(
            divergent,
            WorldLocation.global(new WorldCoordinate(120, 620, 0)),
            Point.location(121, 620),
            LayeredPlayerLocationPersistence.LEGACY_BOOTSTRAP));
        check(divergent.isEmpty(), "divergent write is atomic");

        Map<String, Object> deepCache = new HashMap<String, Object>();
        LayeredPlayerLocationPersistence.write(
            deepCache,
            deepLocation,
            deepProjection,
            LayeredPlayerLocationPersistence.LEGACY_BOOTSTRAP,
            true);
        check(LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_FIXTURE_ID
            .equals(deepCache.get(
                LayeredPlayerLocationPersistence.KEY_ADAPTER)),
            "deep adapter persistence");
        LayeredPlayerLocationPersistence.RestoreResult deepExact =
            LayeredPlayerLocationPersistence.restore(
                deepCache, deepProjection, true);
        check(!deepExact.isRewriteRequired()
            && deepExact.getLocation().equals(deepLocation),
            "deep exact restore");
        LayeredPlayerLocationPersistence.RestoreResult deepDisabled =
            LayeredPlayerLocationPersistence.restore(
                deepCache, deepProjection, false);
        check(deepDisabled.isRewriteRequired(),
            "disabled deep requires safe rewrite");
        check(LayeredPlayerLocationPersistence.SYNTHETIC_DISABLED_REBASE
            .equals(deepDisabled.getOrigin()),
            "disabled deep safe-rebase origin");
        check(deepDisabled.getLocation().equals(
            WorldLocation.global(new WorldCoordinate(450, 600, 0))),
            "disabled deep safely rebases to compatibility receipt");

        LayeredPlayerLocationAuthority reconnect =
            new LayeredPlayerLocationAuthority();
        Point reconnectProjection = reconnect.initialize(exact.getLocation());
        check(reconnect.requireCurrent(reconnectProjection).equals(
            authorityFor(120, 620, 0)), "reconnect authority");
    }

    private static WorldLocation authorityFor(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredPlayerLocationAuthorityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-player-location-authority-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPlayerLocationAuthorityFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")

        sources = [
            point,
            COORDINATES / "WorldCoordinate.java",
            COORDINATES / "WorldSpaceId.java",
            COORDINATES / "WorldLocation.java",
            COORDINATES / "LegacyPackedPointAdapter.java",
            COORDINATES / "LayeredCompatibilityPointAdapter.java",
            COORDINATES / "LayeredPlayerLocationAuthority.java",
            COORDINATES / "LayeredPlayerLocationPersistence.java",
            fixture,
        ]
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
                *(str(path) for path in sources),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_authority_persistence_bootstrap_rebase_and_reconnect(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredPlayerLocationAuthorityFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_private_gate_and_runtime_seams_are_explicit(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        player_service = PLAYER_SERVICE.read_text(encoding="utf-8")
        database = DATABASE.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")

        self.assertIn("WANT_LAYERED_PLAYER_LOCATION_AUTHORITY", configuration)
        self.assertIn("OPENRSC_LAYERED_PLAYER_LOCATION_AUTHORITY", configuration)
        self.assertIn('"want_layered_player_location_authority"', configuration)
        self.assertIn(
            "private final LayeredPlayerLocationAuthority layeredLocationAuthority",
            player,
        )
        self.assertIn("public void setInitialLayeredLocation", player)
        self.assertIn("public void setLayeredLocation", player)
        self.assertIn("layeredLocationAuthority.requireCurrent(", player)
        self.assertIn("WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE", player)
        self.assertIn("restorePlayerLayeredLocation(loaded);", player_service)
        self.assertLess(
            player_service.index("loadPlayerCache(loaded);"),
            player_service.index("restorePlayerLayeredLocation(loaded);"),
        )
        self.assertIn("LayeredPlayerLocationPersistence.write(", player_service)
        self.assertIn(
            "WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE", player_service
        )
        self.assertIn(
            "locationSnapshot.requireLayeredLocation(player.getLayeredLocation())",
            database,
        )
        self.assertIn('command.equalsIgnoreCase("layerloc")', development)
        self.assertIn("persistenceOrigin=", development)

    def test_cache_keys_fit_existing_cross_database_limit(self):
        source = (
            COORDINATES / "LayeredPlayerLocationPersistence.java"
        ).read_text(encoding="utf-8")
        keys = [
            line.split('"')[1]
            for line in source.splitlines()
            if "public static final String KEY_" in line
        ]
        self.assertEqual(9, len(keys))
        self.assertTrue(all(len(key) <= 32 for key in keys))

    def test_plan_records_the_coarse_authority_milestone(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Phase 5 Authority Milestone A", plan)
        self.assertIn("OPENRSC_LAYERED_PLAYER_LOCATION_AUTHORITY", plan)
        self.assertIn("legacy-bootstrap-v1", plan)
        self.assertIn("legacy-rebase-v1", plan)


if __name__ == "__main__":
    unittest.main()
