#!/usr/bin/env python3
"""Guard Underground Pass rock-on-swamp crossings against packed-Y regressions."""
import re
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/"
    "undergroundpass/mechanism/UndergroundPassMechanismMap1.java"
)
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocs.json"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"Unterminated method: {signature}")


class LayeredUndergroundPassSwampShortcutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.use_location = method_body(
            cls.source, "public void onUseLoc(Player player, GameObject obj, Item item)"
        )

    def test_blessed_spider_crossing_is_specific_to_its_layered_locations(self):
        helper = method_body(
            self.source,
            "private static boolean isBlessedSpiderSwampCrossing(final GameObject obj)",
        )
        for snippet in (
            "obj.getID() != BLESSED_SPIDER_SWAMP_CROSS",
            "coordinate.getLevel() == UNDERGROUND_PASS_LEVEL",
            "coordinate.getY() == BLESSED_SPIDER_SWAMP_CROSSING_Y",
            "coordinate.getX() == 714 || coordinate.getX() == 715",
        ):
            self.assertIn(snippet, helper)

        scenery = json.loads(SCENERY_LOCS.read_text(encoding="utf-8"))["sceneries"]
        crossings = {
            (entry["pos"]["X"], entry["pos"]["Y"])
            for entry in scenery
            if entry["id"] == 795
        }
        self.assertEqual(crossings, {(714, 3418), (715, 3418)})
        self.assertEqual(3418 - (3 * 944), 586)

    def test_blessed_spider_crossing_uses_native_y_and_explicit_destinations(self):
        crossing = self.use_location.split(
            "else if (item.getCatalogId() == ItemId.ROCKS.id() && isBlessedSpiderSwampCrossing(obj))",
            1,
        )[1]
        crossing = crossing.split("\n\t}\n", 1)[0]
        self.assertIn(
            "player.getWorldLocation().getCoordinate().getY() >= BLESSED_SPIDER_SWAMP_CROSSING_Y",
            crossing,
        )
        self.assertIn("teleportUnderground(player, 715, 584);", crossing)
        self.assertIn("teleportUnderground(player, 713, 588);", crossing)
        self.assertNotRegex(crossing, r"player\.getY\(\)\s*>=\s*3418")
        self.assertNotRegex(crossing, r"player\.teleport\([^\n]*,\s*34(?:16|20)")

    def test_first_swamp_crossing_preserves_both_directions_with_explicit_layer(self):
        crossing = self.use_location.split(
            "else if (item.getCatalogId() == ItemId.ROCKS.id() && obj.getID() == SWAMP_CROSS)",
            1,
        )[1].split(
            "else if (item.getCatalogId() == ItemId.ROCKS.id() && isBlessedSpiderSwampCrossing(obj))",
            1,
        )[0]
        for destination in (
            "teleportUnderground(player, 698, 609);",
            "teleportUnderground(player, 700, 609);",
            "teleportUnderground(player, 695, 609);",
        ):
            self.assertIn(destination, crossing)
        self.assertNotRegex(crossing, r"player\.teleport\([^\n]*,\s*3441")

    def test_explicit_helper_targets_underground_layer_and_legacy_visuals_stay_legacy_only(self):
        helper = method_body(
            self.source,
            "private static void teleportUnderground(final Player player, final int x, final int y)",
        )
        self.assertIn("player.teleport(x, y, UNDERGROUND_PASS_LEVEL, false);", helper)
        visual = method_body(
            self.source,
            "private static void registerLegacySteppingStone(final Player player, final Point location, final int direction)",
        )
        self.assertIn("if (player.getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY)", visual)
        self.assertIn("return;", visual)
        self.assertIn("player.getWorld().registerGameObject(object);", visual)


if __name__ == "__main__":
    unittest.main()
