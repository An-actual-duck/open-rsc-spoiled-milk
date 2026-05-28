#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RESOURCE_SEEDS = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/gathering/ResourceSeeds.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    text = RESOURCE_SEEDS.read_text(encoding="utf-8")

    require(text, "private static Point findPlantLocation(Player player)", "resource seed placement helper")
    require(text, "CollisionFlag.FULL_BLOCK", "blocked-tile rejection")
    require(text, "PathValidation.checkAdjacentDistance(player.getWorld(), player.getX(), player.getY(),",
            "reachable-tile validation")
    require(text, "private static boolean hasMobAt(Player player, Point location)",
            "occupied-tile validation")
    require(text, "player.getViewArea().getPlayersInView()", "nearby-player occupancy lookup")
    require(text, "player.getViewArea().getNpcsInView()", "nearby-NPC occupancy lookup")

    placement_block = text[text.index("private static Point findPlantLocation"):
                           text.index("private static boolean hasMobAt")]
    if "{0, 0}" in placement_block:
        fail("resource seeds must not plant beneath their owner")

    print("PASS: resource seeds only plant on reachable, unoccupied nearby tiles")


if __name__ == "__main__":
    main()
