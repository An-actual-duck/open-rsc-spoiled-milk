#!/usr/bin/env python3
import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
SPELL_HANDLER = ROOT / (
    "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
)
PLUGIN_ROOT = ROOT / "server/plugins/com/openrsc/server/plugins"


POINT_STUB = r"""
package com.openrsc.server.model;

public final class Point {
    private final int x;
    private final int y;

    private Point(int x, int y) {
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
"""


FIXTURE = r"""
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

public final class LayeredSpellTeleportDestinationFixture {
    public static void main(String[] args) {
        checkLegacy(location(120, 504, 0), 120, 504, "Varrock");
        checkLegacy(location(120, 648, 0), 120, 648, "Lumbridge");
        checkLegacy(location(312, 552, 0), 312, 552, "Falador");
        checkLegacy(location(456, 456, 0), 456, 456, "Camelot");
        checkLegacy(location(588, 621, 0), 588, 621, "Ardougne");
        checkLegacy(location(493, 693, -1), 493, 3525, "Watchtower");
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void checkLegacy(
        WorldLocation destination,
        int expectedX,
        int expectedPackedY,
        String label) {
        Point legacy = LegacyPackedPointAdapter.toLegacyPoint(destination);
        check(legacy.getX() == expectedX, label + " X projection");
        check(legacy.getY() == expectedPackedY, label + " Y projection");
        check(LegacyPackedPointAdapter.fromLegacyPoint(legacy).equals(destination),
            label + " round trip");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
"""


class LayeredSpellTeleportDestinationTest(unittest.TestCase):
    def test_explicit_destinations_preserve_legacy_locations(self):
        with tempfile.TemporaryDirectory(
            prefix="layered-spell-teleport-destinations-"
        ) as temporary:
            output = Path(temporary)
            point = output / "Point.java"
            point.write_text(POINT_STUB, encoding="utf-8")
            fixture = output / "LayeredSpellTeleportDestinationFixture.java"
            fixture.write_text(FIXTURE, encoding="utf-8")
            sources = [
                point,
                COORDINATES / "WorldSpaceId.java",
                COORDINATES / "WorldCoordinate.java",
                COORDINATES / "WorldLocation.java",
                COORDINATES / "LegacyPackedPointAdapter.java",
                fixture,
            ]
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
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
                        "LayeredSpellTeleportDestinationFixture"
                    ),
                ],
                cwd=ROOT,
                check=True,
            )

    def test_spell_teleports_supply_destination_levels(self):
        player = PLAYER.read_text(encoding="utf-8")
        spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")

        for snippet in (
            "final int level,",
            "new WorldCoordinate(x, y, level)",
            "LegacyPackedPointAdapter.toLegacyPoint(",
        ):
            self.assertIn(snippet, player)

        for destination in (
            "player.teleport(120, 504, 0, false);",
            "player.teleport(120, 648, 0, false);",
            "player.teleport(312, 552, 0, false);",
            "player.teleport(456, 456, 0, false);",
            "player.teleport(588, 621, 0, false);",
            "player.teleport(493, 693, -1, false);",
        ):
            self.assertIn(destination, spell_handler)

        complete_teleport = spell_handler.split(
            "private void completeTeleport", 1
        )[1].split("private void handleBoost", 1)[0]
        self.assertNotIn("player.teleport(120, 504, false);", complete_teleport)
        self.assertNotIn("player.teleport(493, 3525, false);", complete_teleport)

    def test_portable_and_selectable_teleports_supply_destination_levels(self):
        portable_destinations = {
            "custom/misc/AgilityCape.java": (
                "player.teleport(591, 765, 0, true);",
            ),
            "custom/misc/CraftingCape.java": (
                "player.teleport(347, 599, 0, true);",
            ),
            "custom/misc/FishingCape.java": (
                "player.teleport(586, 522, 0, true);",
            ),
            "custom/misc/TeleportStone.java": (
                "player.teleport(125, 648, 0, false);",
                "player.teleport(703, 481, 0, false);",
            ),
            "custom/misc/MagicalPoolCustom.java": (
                "player.teleport(218, 456, 0, false);",
                "player.teleport(471, 553, -1, false);",
                "player.teleport(447, 541, -1, false);",
            ),
            "authentic/misc/MagicalPool.java": (
                "player.teleport(471, 553, -1, false);",
                "player.teleport(447, 541, -1, false);",
            ),
        }
        for relative_path, destinations in portable_destinations.items():
            source = (PLUGIN_ROOT / relative_path).read_text(encoding="utf-8")
            for destination in destinations:
                self.assertIn(destination, source, relative_path)
            calls = re.findall(r"player\.teleport\(([^;\n]+)\);", source)
            self.assertTrue(calls, relative_path)
            for arguments in calls:
                self.assertEqual(
                    len(arguments.split(",")),
                    4,
                    f"{relative_path} has an ambiguous teleport: {arguments}",
                )

        law_jewelry = (
            PLUGIN_ROOT / "custom/myworld/skills/enchanting/LawJewelry.java"
        ).read_text(encoding="utf-8")
        for snippet in (
            'CRAFTING_GUILD("Crafting Guild", 347, 599, 0)',
            "COSMIC(ItemId.COSMIC_RUNE.id(), 104, 724, -1)",
            "SOUL(ItemId.SOUL_RUNE.id(), 610, 767, -1)",
            "player.teleport(destination.x, destination.y, destination.level, true);",
        ):
            self.assertIn(snippet, law_jewelry)
        self.assertEqual(
            law_jewelry.count(
                "player.teleport(destination.x, destination.y, destination.level, true);"
            ),
            2,
        )


if __name__ == "__main__":
    unittest.main()
