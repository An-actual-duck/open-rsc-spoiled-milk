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
    require(
        text,
        "player.getWorld(), playerLocation, scopedLocation,",
        "exact-layer reachable-tile validation",
    )
    require(text, "player.getTileAtCurrentLevel(", "exact-layer tile lookup")
    require(text, "private static boolean hasMobAt(Player player, Point location)",
            "occupied-tile validation")
    require(text, "player.getViewArea().getPlayersInView()", "nearby-player occupancy lookup")
    require(text, "player.getViewArea().getNpcsInView()", "nearby-NPC occupancy lookup")
    require(text,
            'ItemId.COINS.id(), Skill.WOODCUTTING.id(), ToolBubble.TREE, "money tree", "coins",\n'
            '\t\t\t"A magical tree glittering with coins.", 250, 1000, 0, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,',
            "halved money tree payout range")
    require(text,
            'ItemId.COINS.id(), Skill.HARVESTING.id(), ToolBubble.PLANT, "money plant", "coins",\n'
            '\t\t\t"A magical plant glittering with coins.", 250, 1000, 0, DEFAULT_NODE_YIELDS, DEFAULT_NODE_ACTION_DELAY,',
            "halved money plant payout range")
    require(text, "int coins = player.getSkills().getLevel(definition.skillId) * quantity;",
            "money seed skill-level scaling formula")

    placement_block = text[text.index("private static Point findPlantLocation"):
                           text.index("private static boolean hasMobAt")]
    if "{0, 0}" in placement_block:
        fail("resource seeds must not plant beneath their owner")

    print("PASS: resource seed placement and money seed payouts look correct")


if __name__ == "__main__":
    main()
