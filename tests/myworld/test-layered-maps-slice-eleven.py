#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
CONFIG_SOURCE = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
COMMAND_SOURCE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
LOCAL_CONFIG = ROOT / "server/myworld.conf"
HOST_CONFIG = ROOT / "server/myworld-host.conf"
SCHEMA = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v6.schema.json"


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


LOGGER_STUB = r'''
package org.apache.logging.log4j;

public interface Logger {
    void error(String message, Object... arguments);
}
'''


LOG_MANAGER_STUB = r'''
package org.apache.logging.log4j;

public final class LogManager {
    private static final Logger LOGGER = new Logger() {
        @Override
        public void error(String message, Object... arguments) {
        }
    };

    private LogManager() {
    }

    public static Logger getLogger(Class<?> type) {
        return LOGGER;
    }
}
'''


OBSERVER_FIXTURE = r'''
package com.openrsc.server.diagnostics;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LayeredCoordinateParitySnapshot;

public final class LayeredCoordinateParityObserverFixture {
    public static void main(String[] args) {
        System.setProperty(LayeredCoordinateParityObserver.LOG_ROOT_PROPERTY, args[0]);
        LayeredCoordinateParityObserver.resetForTests();

        int[] packedBoundaries = {0, 943, 944, 1887, 1888, 2831, 2832, 3775};
        int[] expectedLevels = {0, 0, 1, 1, 2, 2, -1, -1};
        int[] expectedY = {0, 943, 0, 943, 0, 943, 0, 943};
        for (int index = 0; index < packedBoundaries.length; index++) {
            LayeredCoordinateParitySnapshot snapshot = LayeredCoordinateParitySnapshot.capture(
                Point.location(100, packedBoundaries[index]));
            check(snapshot.getLocation().getCoordinate().getLevel() == expectedLevels[index],
                "boundary level " + packedBoundaries[index]);
            check(snapshot.getLocation().getCoordinate().getY() == expectedY[index],
                "boundary Y " + packedBoundaries[index]);
            check(snapshot.isRoundTripExact(), "boundary round trip " + packedBoundaries[index]);
            check(snapshot.toCompactString().contains("roundTrip=OK"), "compact round trip");
        }
        expectIllegal(() -> LayeredCoordinateParitySnapshot.capture(Point.location(100, 3776)));
        expectNull(() -> LayeredCoordinateParitySnapshot.capture(null));

        int playerId = 7;
        long usernameHash = 123456789L;
        LayeredCoordinateParityObserver.TileSnapshotSource tileSnapshots =
            key -> key.getLevel() == 0 && key.getRegionY() == 19
                ? LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
                    key, 1, 0, 1536, 2304, false,
                    "0000000000000000000000000000000000000000000000000000000000000000")
                : LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
                    key, key.getLevel() == 1 ? 2 : 1, 0, 2304, 2304, true,
                    "1111111111111111111111111111111111111111111111111111111111111111");
        int[] tileParityCaptures = {0};
        LayeredCoordinateParityObserver.TileParitySource tileParity = current -> {
            tileParityCaptures[0]++;
            return LayeredCoordinateParityObserver.TileParityMetadata.of(
                LayeredCoordinateParitySnapshot.capture(current).getLocation(),
                current, true, false, true, true);
        };
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, null, tileParity));
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, tileSnapshots, null));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 2, 2304, 2304, true,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 0, 1536, 2304, true,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 0, 1536, 2304, false, "not-a-fingerprint"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 0, 1536, 2303, false,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            Point.location(100, 943), true, true, true, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            null, true, false, true, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            Point.location(100, 943), true, false, false, false));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            null, false, false, false, true));
        check(!LayeredCoordinateParityObserver.status(playerId, usernameHash).isEnabled(),
            "initially disabled");
        LayeredCoordinateParityObserver.Status started = LayeredCoordinateParityObserver.start(
            playerId, usernameHash, Point.location(100, 943), 2, tileSnapshots, tileParity);
        check(started.isEnabled() && started.getRecordCount() == 1, "start");
        check(started.getError() == null, "start error");
        check(started.getLastSnapshot().getVisibilityWindow().getRegionCount() == 2L,
            "start visibility window");
        expectIllegal(() -> LayeredCoordinateParityObserver.start(
            playerId, usernameHash, Point.location(100, 943), 3, tileSnapshots, tileParity));

        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 943), Point.location(100, 944), false);
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 944), Point.location(100, 2832), true);
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 2832), Point.location(100, 2832), false);
        LayeredCoordinateParityObserver.mark(
            playerId, usernameHash, Point.location(100, 2832), "after-ladder");
        LayeredCoordinateParityObserver.snapshot(
            playerId, usernameHash, Point.location(100, 2832));
        LayeredCoordinateParityObserver.onSession(
            playerId, usernameHash, Point.location(100, 2832), false);
        LayeredCoordinateParityObserver.onSession(
            playerId, usernameHash, Point.location(100, 2832), true);
        expectIllegal(() -> LayeredCoordinateParityObserver.mark(
            playerId, usernameHash, Point.location(100, 2832), "unsafe label"));

        LayeredCoordinateParityObserver.Status active =
            LayeredCoordinateParityObserver.status(playerId, usernameHash);
        check(active.isEnabled() && active.getRecordCount() == 7, "active record count");
        check(active.getLastSnapshot().getLocation().getCoordinate().getLevel() == -1,
            "active layered level");

        long otherHash = 987654321L;
        LayeredCoordinateParityObserver.Status other = LayeredCoordinateParityObserver.start(
            playerId, otherHash, Point.location(200, 944), 2, tileSnapshots, tileParity);
        check(other.isEnabled() && other.getRecordCount() == 1, "identity-isolated start");
        check(!other.getPath().equals(active.getPath()), "identity-isolated path");
        LayeredCoordinateParityObserver.stop(playerId, otherHash, Point.location(200, 944));

        LayeredCoordinateParityObserver.Status stopped = LayeredCoordinateParityObserver.stop(
            playerId, usernameHash, Point.location(100, 2832));
        check(!stopped.isEnabled() && stopped.getRecordCount() == 8, "stop");
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 2832), Point.location(101, 2832), false);
        check(!LayeredCoordinateParityObserver.status(playerId, usernameHash).isEnabled(),
            "movement after stop ignored");

        LayeredCoordinateParityObserver.Status invalid = LayeredCoordinateParityObserver.start(
            8, 111L, Point.location(100, 3776), 2, tileSnapshots, tileParity);
        check(invalid.isEnabled() && invalid.getRecordCount() == 0, "invalid trace retained");
        check(invalid.getError() != null && invalid.getError().contains("IllegalArgumentException"),
            "invalid trace visible error");
        check(tileParityCaptures[0] == 6, "bounded tile parity capture count");
        LayeredCoordinateParityObserver.resetForTests();
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


class LayeredMapsSliceElevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-eleven-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        sources = {
            "com/openrsc/server/model/Point.java": POINT_STUB,
            "org/apache/logging/log4j/Logger.java": LOGGER_STUB,
            "org/apache/logging/log4j/LogManager.java": LOG_MANAGER_STUB,
            "com/openrsc/server/diagnostics/LayeredCoordinateParityObserverFixture.java":
                OBSERVER_FIXTURE,
        }
        fixture_sources = []
        for relative, content in sources.items():
            path = cls.temp / "src" / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            fixture_sources.append(str(path))

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
                *fixture_sources,
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
                str(OBSERVER),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_snapshot_and_jsonl_observer_cover_boundaries_transitions_and_identity(self):
        with tempfile.TemporaryDirectory(prefix="layered-parity-jsonl-") as log_dir:
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(self.classes),
                    "com.openrsc.server.diagnostics.LayeredCoordinateParityObserverFixture",
                    log_dir,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)

            logs = sorted(Path(log_dir).glob("*.jsonl"))
            self.assertEqual(2, len(logs))
            primary = next(path for path in logs if "123456789" in path.name)
            events = [json.loads(line) for line in primary.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(
                ["start", "move", "teleport", "marker", "snapshot", "logout", "login", "stop"],
                [event["eventType"] for event in events],
            )
            self.assertEqual(list(range(1, 9)), [event["sequence"] for event in events])
            self.assertTrue(all(event["roundTripExact"] for event in events))
            self.assertEqual("after-ladder", events[3]["label"])
            self.assertEqual(1, events[1]["delta"]["level"])
            self.assertEqual(-2, events[2]["delta"]["level"])
            self.assertEqual(-1, events[2]["to"]["layered"]["level"])
            self.assertEqual({"x": 2, "y": 0}, events[2]["to"]["region"])
            self.assertTrue(all(event["schema"] == "layered-map-parity-event-v6" for event in events))
            upper_window = events[1]["to"]["visibilityWindow"]
            self.assertEqual(2, upper_window["gridDistance"])
            self.assertEqual(16, upper_window["tileRadius"])
            self.assertEqual(1, upper_window["level"])
            self.assertEqual({
                "minRegionX": 1,
                "minRegionY": -1,
                "maxRegionX": 2,
                "maxRegionY": 0,
            }, {key: upper_window[key] for key in (
                "minRegionX", "minRegionY", "maxRegionX", "maxRegionY")})
            self.assertEqual(4, upper_window["regionCount"])
            start_coverage = events[0]["packedCoverage"]
            self.assertEqual({
                "packedCellCount": 2,
                "unsupportedPackedCellCount": 0,
                "expectedKeyCount": 2,
                "packedCoverageKeyCount": 4,
                "missingKeyCount": 0,
                "extraKeyCount": 2,
                "exact": False,
            }, {key: start_coverage[key] for key in (
                "packedCellCount",
                "unsupportedPackedCellCount",
                "expectedKeyCount",
                "packedCoverageKeyCount",
                "missingKeyCount",
                "extraKeyCount",
                "exact",
            )})
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": 0},
                start_coverage["extraKeys"][0],
            )
            start_tiles = events[0]["tileSnapshot"]
            self.assertEqual({
                "logicalRegion": {"worldSpace": "global", "level": 0, "x": 2, "y": 19},
                "sourceFragmentCount": 1,
                "missingSourceRegionCount": 0,
                "supportedTileCount": 1536,
                "targetTileCount": 2304,
                "complete": False,
                "fingerprint": "0" * 64,
            }, start_tiles)
            upper_coverage = events[1]["packedCoverage"]
            self.assertEqual(2, upper_coverage["missingKeyCount"])
            self.assertEqual(4, upper_coverage["extraKeyCount"])
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": -1},
                upper_coverage["missingKeys"][0],
            )
            self.assertEqual(
                {"worldSpace": "global", "level": 0, "x": 1, "y": 19},
                upper_coverage["extraKeys"][0],
            )
            upper_tiles = events[1]["tileSnapshot"]
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 2, "y": 0},
                upper_tiles["logicalRegion"],
            )
            self.assertEqual(2, upper_tiles["sourceFragmentCount"])
            self.assertEqual(2304, upper_tiles["supportedTileCount"])
            self.assertTrue(upper_tiles["complete"])
            self.assertTrue(all(event["tileSnapshot"] is not None for event in events))
            self.assertEqual(
                {"start", "teleport", "marker", "stop"},
                {
                    event["eventType"]
                    for event in events
                    if event["tileParity"] is not None
                },
            )
            self.assertTrue(all(
                (event["tileParity"] is not None)
                == (event["eventType"] in {"start", "teleport", "marker", "stop"})
                for event in events
            ))
            start_parity = events[0]["tileParity"]
            self.assertEqual(
                {
                    "logicalLocation": {
                        "worldSpace": "global", "x": 100, "y": 943, "level": 0
                    },
                    "legacyPackedAddress": {"x": 100, "y": 943},
                    "legacyRepresentable": True,
                    "packedSourcePresent": True,
                    "missingPackedSource": False,
                    "comparable": True,
                    "exact": True,
                },
                start_parity,
            )
            upper_interest = events[1]["interestDelta"]
            self.assertEqual({
                "previousRegionCount": 2,
                "currentRegionCount": 4,
                "enteredCount": 4,
                "retainedCount": 0,
                "exitedCount": 2,
                "worldSpaceChanged": False,
                "levelChanged": True,
                "noOp": False,
            }, {key: upper_interest[key] for key in (
                "previousRegionCount",
                "currentRegionCount",
                "enteredCount",
                "retainedCount",
                "exitedCount",
                "worldSpaceChanged",
                "levelChanged",
                "noOp",
            )})
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": -1},
                upper_interest["enteredKeys"][0],
            )
            self.assertEqual(
                {"worldSpace": "global", "level": 0, "x": 1, "y": 19},
                upper_interest["exitedKeys"][0],
            )
            self.assertIsNotNone(events[2]["interestDelta"])
            self.assertTrue(events[2]["interestDelta"]["levelChanged"])
            self.assertTrue(all(
                (event["interestDelta"] is not None)
                == (event["eventType"] in {"move", "teleport"})
                for event in events
            ))
            raw_log = primary.read_text(encoding="utf-8").lower()
            self.assertNotIn('"username":', raw_log)
            self.assertNotIn("password", raw_log)
            self.assertNotIn("ipaddress", raw_log)

            schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
            try:
                import jsonschema
            except ImportError:
                jsonschema = None
            if jsonschema is not None:
                jsonschema.Draft202012Validator.check_schema(schema)
                for event in events:
                    jsonschema.validate(event, schema)

    def test_runtime_wiring_is_opt_in_dev_only_and_observational(self):
        config = CONFIG_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        command = COMMAND_SOURCE.read_text(encoding="utf-8")
        local_config = LOCAL_CONFIG.read_text(encoding="utf-8")
        host_config = HOST_CONFIG.read_text(encoding="utf-8")

        self.assertIn("OPENRSC_LAYERED_MAP_PARITY_OBSERVER", config)
        self.assertIn('"want_layered_map_parity_observer"', config)
        self.assertIn("WANT_LAYERED_MAP_PARITY_OBSERVER", player)
        self.assertIn("LayeredCoordinateParityObserver.onLocationChanged", player)
        self.assertIn("LayeredCoordinateParityObserver.onSession", player)
        self.assertIn("layeredTileSnapshotSource(player)", command)
        self.assertIn("layeredTileParitySource(player)", command)
        self.assertIn("regionManager.getLayeredRegionTileSnapshot", command)
        self.assertIn("regionManager.compareLayeredTileState(current)", command)
        self.assertIn('command.equalsIgnoreCase("layerparity")', command)
        self.assertIn("player.isDev()", command)
        self.assertIn("WANT_LAYERED_MAP_PARITY_OBSERVER", command)
        self.assertIn("[start|status|snapshot|mark LABEL|stop]", command)
        self.assertIn("want_layered_map_parity_observer: false", local_config)
        self.assertIn("want_layered_map_parity_observer: false", host_config)
        self.assertNotIn("LegacyPackedPointAdapter.toLegacyPoint", player)


if __name__ == "__main__":
    unittest.main()
