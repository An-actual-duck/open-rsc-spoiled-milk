#!/usr/bin/env python3
import os
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE_JAR = ROOT / "server/core.jar"
DOOR_ACTION = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java"
)


class RestrictedDoorLayeredCoordinatesTest(unittest.TestCase):
    def test_legacy_door_coordinates_resolve_to_signed_layers(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.model.Point;
            import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
            import com.openrsc.server.model.world.coordinate.WorldCoordinate;
            import com.openrsc.server.model.world.coordinate.WorldLocation;

            public final class RestrictedDoorCoordinateHarness {
                private static void require(boolean value, String message) {
                    if (!value) throw new AssertionError(message);
                }

                private static WorldLocation location(int x, int y, int level) {
                    return WorldLocation.global(new WorldCoordinate(x, y, level));
                }

                public static void main(String[] arguments) {
                    WorldLocation miningEntrance = location(268, 549, -1);
                    WorldLocation elderDragonGate = location(268, 569, -1);
                    WorldLocation sickMournerExit = location(0, 569, 1);

                    require(
                        LegacyPackedPointAdapter.fromPackedValues(268, 3381)
                            .equals(miningEntrance),
                        "Mining Guild entrance did not resolve to level -1");
                    require(
                        LegacyPackedPointAdapter.fromPackedValues(268, 3401)
                            .equals(elderDragonGate),
                        "Elder Dragon gate did not resolve to level -1");
                    require(
                        LegacyPackedPointAdapter.toLegacyPoint(miningEntrance)
                            .equals(Point.location(268, 3381)),
                        "Mining Guild entrance did not round-trip");
                    require(
                        LegacyPackedPointAdapter.toLegacyPoint(elderDragonGate)
                            .equals(Point.location(268, 3401)),
                        "Elder Dragon gate did not round-trip");
                    require(
                        LegacyPackedPointAdapter.toLegacyPoint(sickMournerExit)
                            .getY() == 1513,
                        "upper-floor restricted-door side did not round-trip");
                }
            }
            """
        )
        with tempfile.TemporaryDirectory(
            prefix="restricted-door-coordinates-"
        ) as temp:
            temp_path = Path(temp)
            source = temp_path / "RestrictedDoorCoordinateHarness.java"
            source.write_text(harness, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-cp",
                    str(CORE_JAR),
                    "-d",
                    str(temp_path),
                    str(source),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    os.pathsep.join((str(temp_path), str(CORE_JAR))),
                    "RestrictedDoorCoordinateHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_restricted_doors_use_legacy_coordinate_adapters(self):
        source = DOOR_ACTION.read_text(encoding="utf-8")
        required = (
            "matchesLegacyPackedLocation(obj, 268, 3381)",
            "legacyPackedY(player) < 3381",
            "MINING_GUILD_ELITE_DOOR_Y)",
            "legacyPackedY(player) < MINING_GUILD_ELITE_DOOR_Y",
            "findNpcInPlayerDomain(\n\t\t\t\t\t\t\tplayer, NpcId.DWARF_MINING_GUILD.id()",
            "findNpcInPlayerDomain(\n\t\t\t\tplayer, NpcId.NURMOF.id()",
            "matchesLegacyPackedLocation(obj, 360, 3428)",
            "matchesLegacyPackedLocation(obj, 360, 3425)",
            "matchesLegacyPackedLocation(obj, 355, 3353)",
            "player.teleportLegacyPacked(544, 3330, false)",
        )
        for snippet in required:
            self.assertIn(snippet, source)

        raw_packed_y = re.findall(
            r"getY\(\)[^\n;]*(?:[1-9][0-9]{3,})|"
            r"(?:[1-9][0-9]{3,})[^\n;]*getY\(\)",
            source,
        )
        self.assertEqual(
            [],
            raw_packed_y,
            f"DoorAction still compares runtime Y to packed Y: {raw_packed_y}",
        )


if __name__ == "__main__":
    unittest.main()
