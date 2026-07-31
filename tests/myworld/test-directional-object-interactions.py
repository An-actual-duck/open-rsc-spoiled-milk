#!/usr/bin/env python3
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
FUNCTIONS = ROOT / "server/src/com/openrsc/server/plugins/Functions.java"


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
                return source[opening + 1 : index]
    raise AssertionError(f"Unterminated method: {signature}")


def nested_block(source: str, marker: str) -> str:
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"Unterminated block: {marker}")


def compact(source: str) -> str:
    return re.sub(r"\s+", "", source)


class DirectionalObjectInteractionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mob = MOB.read_text(encoding="utf-8")
        cls.functions = FUNCTIONS.read_text(encoding="utf-8")

    def test_four_cardinals_are_immediate_and_four_diagonals_wait(self):
        at_object = method_body(
            self.mob, "public final boolean atObject(final GameObject o)"
        )
        cardinal = method_body(
            self.mob,
            "private boolean canReach(int minX, int maxX, int minY, int maxY)",
        )
        diagonal = method_body(
            self.mob,
            "private boolean canReachDiagonal(int minX, int maxX, int minY, int maxY)",
        )

        self.assertIn(
            "finishedPath() && canReachDiagonal(", at_object,
            "true diagonals must remain gated until path completion",
        )
        cardinal_flags = re.findall(r"CollisionFlag\.(WALL_[A-Z_]+)", cardinal)
        diagonal_flags = re.findall(r"CollisionFlag\.(WALL_[A-Z_]+)", diagonal)
        self.assertEqual(
            ["WALL_WEST", "WALL_EAST", "WALL_SOUTH", "WALL_NORTH"],
            cardinal_flags,
        )
        self.assertEqual(
            [
                "WALL_SOUTH_WEST",
                "WALL_SOUTH_EAST",
                "WALL_NORTH_WEST",
                "WALL_NORTH_EAST",
            ],
            diagonal_flags,
        )

        compact_cardinal = compact(cardinal)
        for tile in (
            "objectReachTile(getX()-1,getY())",
            "objectReachTile(getX()+1,getY())",
            "objectReachTile(getX(),getY()-1)",
            "objectReachTile(getX(),getY()+1)",
        ):
            self.assertIn(tile, compact_cardinal)
        compact_diagonal = compact(diagonal)
        for tile in (
            "objectReachTile(getX()-1,getY()-1)",
            "objectReachTile(getX()+1,getY()-1)",
            "objectReachTile(getX()-1,getY()+1)",
            "objectReachTile(getX()+1,getY()+1)",
        ):
            self.assertIn(tile, compact_diagonal)

    def test_object_collision_reads_preserve_world_space_and_signed_level(self):
        cardinal = method_body(
            self.mob,
            "private boolean canReach(int minX, int maxX, int minY, int maxY)",
        )
        diagonal = method_body(
            self.mob,
            "private boolean canReachDiagonal(int minX, int maxX, int minY, int maxY)",
        )
        resolver = method_body(
            self.mob,
            "private TileValue objectReachTile(final int x, final int y)",
        )

        self.assertNotIn("getWorld().getTile(", cardinal)
        self.assertNotIn("getWorld().getTile(", diagonal)
        for snippet in (
            "WorldLocation current = getWorldLocation();",
            "new WorldLocation(",
            "current.getWorldSpace(),",
            "new WorldCoordinate(",
            "current.getCoordinate().getLevel()",
            "getWorld().getTile(reachabilityLocation)",
        ):
            self.assertIn(snippet, resolver)

    def test_diagonal_doors_accept_all_six_positions_per_direction(self):
        do_door = method_body(
            self.functions,
            (
                "public static void doDoor(final GameObject object, "
                "final Player player, int replaceID)"
            ),
        )
        direction_two = nested_block(
            do_door, "if (object.getDirection() == 2)"
        )
        direction_three = nested_block(
            do_door, "if (object.getDirection() == 3)"
        )

        direction_two_cases = (
            ("object.getX()==player.getX()&&object.getY()==player.getY()+1",
             "teleport(player,object.getX(),object.getY()+1);"),
            ("object.getX()==player.getX()-1&&object.getY()==player.getY()",
             "teleport(player,object.getX()-1,object.getY());"),
            ("object.getX()==player.getX()&&object.getY()==player.getY()-1",
             "teleport(player,object.getX(),object.getY()-1);"),
            ("object.getX()==player.getX()+1&&object.getY()==player.getY()",
             "teleport(player,object.getX()+1,object.getY());"),
            ("object.getX()==player.getX()+1&&object.getY()==player.getY()+1",
             "teleport(player,object.getX()+1,object.getY()+1);"),
            ("object.getX()==player.getX()-1&&object.getY()==player.getY()-1",
             "teleport(player,object.getX()-1,object.getY()-1);"),
        )
        direction_three_cases = (
            ("object.getX()==player.getX()&&object.getY()==player.getY()-1",
             "teleport(player,object.getX(),object.getY()-1);"),
            ("object.getX()==player.getX()+1&&object.getY()==player.getY()",
             "teleport(player,object.getX()+1,object.getY());"),
            ("object.getX()==player.getX()&&object.getY()==player.getY()+1",
             "teleport(player,object.getX(),object.getY()+1);"),
            ("object.getX()==player.getX()-1&&object.getY()==player.getY()",
             "teleport(player,object.getX()-1,object.getY());"),
            ("object.getX()==player.getX()-1&&object.getY()==player.getY()+1",
             "teleport(player,object.getX()-1,object.getY()+1);"),
            ("object.getX()==player.getX()+1&&object.getY()==player.getY()-1",
             "teleport(player,object.getX()+1,object.getY()-1);"),
        )

        for block, cases in (
            (direction_two, direction_two_cases),
            (direction_three, direction_three_cases),
        ):
            compact_block = compact(block)
            self.assertEqual(6, compact_block.count("teleport(player,"))
            for condition, destination in cases:
                self.assertIn(
                    f"({condition}){{{destination}",
                    compact_block,
                    f"door case must preserve its paired destination: {condition}",
                )


if __name__ == "__main__":
    unittest.main()
