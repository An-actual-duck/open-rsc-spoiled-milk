#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PLAYER_SERVICE = ROOT / "server/src/com/openrsc/server/service/PlayerService.java"


FIXTURE = r"""
package com.openrsc.server.model.world.coordinate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class LayeredPlayerLoginRecoveryFixture {
    public static void main(String[] args) {
        WorldLocation valid = location(312, 552, -1);
        WorldLocation voidLocation = location(313, 552, -1);
        WorldLocation missing = location(314, 552, -1);
        WorldLocation respawn = location(120, 648, 0);

        Map<WorldLocation, Integer> overlays = new HashMap<>();
        overlays.put(valid, 2);
        overlays.put(voidLocation, 8);
        overlays.put(respawn, 1);
        Function<WorldLocation, Integer> lookup = overlays::get;

        LayeredPlayerLoginRecovery.Decision retained =
            LayeredPlayerLoginRecovery.resolve(
                valid, "existing-origin", respawn, lookup);
        check(!retained.isRecovered(), "valid location retained");
        check(retained.getLocation().equals(valid), "valid destination");
        check("existing-origin".equals(retained.getOrigin()),
            "valid origin retained");

        LayeredPlayerLoginRecovery.Decision recoveredVoid =
            LayeredPlayerLoginRecovery.resolve(
                voidLocation, "existing-origin", respawn, lookup);
        check(recoveredVoid.isRecovered(), "void location recovered");
        check(recoveredVoid.getLocation().equals(respawn),
            "void recovery destination");
        check(LayeredPlayerLoginRecovery.RECOVERY_ORIGIN.equals(
                recoveredVoid.getOrigin()), "void recovery origin");
        check("explicit-void-overlay".equals(recoveredVoid.getReason()),
            "void recovery reason");

        LayeredPlayerLoginRecovery.Decision recoveredMissing =
            LayeredPlayerLoginRecovery.resolve(
                missing, "existing-origin", respawn, lookup);
        check(recoveredMissing.isRecovered(), "missing terrain recovered");
        check("missing-terrain".equals(recoveredMissing.getReason()),
            "missing terrain reason");

        LayeredPlayerLoginRecovery.Decision recoveredNull =
            LayeredPlayerLoginRecovery.resolve(
                null, "existing-origin", respawn, lookup);
        check(recoveredNull.isRecovered(), "null location recovered");
        check("missing-location".equals(recoveredNull.getReason()),
            "null location reason");

        Map<WorldLocation, Integer> invalidRespawnOverlays =
            new HashMap<>(overlays);
        invalidRespawnOverlays.put(respawn, 8);
        expectState(() -> LayeredPlayerLoginRecovery.resolve(
            voidLocation,
            "existing-origin",
            respawn,
            invalidRespawnOverlays::get));
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
"""


class LayeredPlayerLoginRecoveryTest(unittest.TestCase):
    def test_valid_void_missing_and_respawn_validation(self):
        with tempfile.TemporaryDirectory(
            prefix="layered-player-login-recovery-"
        ) as temporary:
            output = Path(temporary)
            fixture = output / "LayeredPlayerLoginRecoveryFixture.java"
            fixture.write_text(FIXTURE, encoding="utf-8")
            sources = [
                COORDINATES / "WorldSpaceId.java",
                COORDINATES / "WorldCoordinate.java",
                COORDINATES / "WorldLocation.java",
                COORDINATES / "LayeredPlayerLoginRecovery.java",
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
                    str(output),
                    *(str(source) for source in sources),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(output),
                    (
                        "com.openrsc.server.model.world.coordinate."
                        "LayeredPlayerLoginRecoveryFixture"
                    ),
                ],
                cwd=ROOT,
                check=True,
            )

    def test_restore_seam_uses_explicit_respawn_and_consistent_receipts(self):
        source = PLAYER_SERVICE.read_text(encoding="utf-8")
        restore = source.split(
            "private void restorePlayerLayeredLocation", 1
        )[1].split("private void loadPlayerData", 1)[0]

        self.assertIn("LegacyPackedPointAdapter.fromPackedValues(", restore)
        self.assertIn("configuration.RESPAWN_LOCATION_X", restore)
        self.assertIn("configuration.RESPAWN_LOCATION_Y", restore)
        self.assertIn("LayeredPlayerLoginRecovery.resolve(", restore)
        self.assertLess(
            restore.index("LayeredPlayerLoginRecovery.resolve("),
            restore.index("player.setInitialLayeredLocation(restoredLocation);"),
        )
        self.assertIn("TileValue tile =", restore)
        self.assertIn("tile.overlay & 0xff", restore)
        self.assertIn("restoredOrigin = loginRecovery.getOrigin();", restore)
        self.assertIn("rewriteRequired = true;", restore)
        self.assertIn("LayeredPlayerLocationPersistence.write(", restore)
        self.assertIn("player.getLayeredLocation(),", restore)
        self.assertIn("player.getLocation(),", restore)

        recovery_log = source.split(
            '"layered-player-location recovery ', 1
        )[1].split(");", 1)[0]
        self.assertIn("player.getDatabaseID()", recovery_log)
        self.assertNotIn("getUsername", recovery_log)


if __name__ == "__main__":
    unittest.main()
