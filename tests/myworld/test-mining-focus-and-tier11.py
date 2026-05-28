#!/usr/bin/env python3
"""Validate mining focus selector reuse and tier-11 requirement updates."""

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MINING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java"
GEM_MINING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/GemMining.java"
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
COMBAT_STYLE_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/CombatStyleHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ITEM_DEFS = ROOT / "server/conf/server/defs/ItemDefs.json"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_snippets(path: Path, snippets: tuple[str, ...], label: str) -> None:
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in text:
            fail(f"{label} missing expected snippet: {snippet}")


def main() -> None:
    require_snippets(
        MINING_PLUGIN,
        (
            "MINING_FOCUS_SOME_GEMS",
            "MINING_FOCUS_MORE_GEMS",
            "MINING_FOCUS_NO_GEMS",
            "MINING_FOCUS_MOST_GEMS",
            'return "Just the ore";',
            'return "Plenty of gems";',
            'return "Lots of gems";',
            'return "A few gems";',
            "MYWORLD_GEM_REWARD_BASE_CHANCE = 1.0D / 50.0D",
            "private static double getRandomGemChance(Player player)",
            "return baseChance * 1.5D;",
            "return baseChance * 2.0D;",
            "return 0.0D;",
            "You find a gem, but have no room to keep it",
            "You manage to obtain some stone, but have no room to keep it",
            "Any excess falls to the ground because you have no room",
        ),
        "Mining.java",
    )

    require_snippets(
        GEM_MINING_PLUGIN,
        (
            "But you have no room to keep it, so it falls to the ground.",
            "player.incExp(Skill.MINING.id(), 260, true)",
        ),
        "GemMining.java",
    )

    require_snippets(
        COMBAT_FORMULA,
        (
            "if (attacker.isPlayer() && ((Player) attacker).getConfig().WANT_MYWORLD) {",
            "return 0;",
        ),
        "CombatFormula.java",
    )

    require_snippets(
        COMBAT_STYLE_HANDLER,
        (
            "player.message(getGatheringFocusMessage(player, style));",
            'return "Mining focus set to " + miningFocusLabel(style);',
            'return "Just the ore";',
            'return "Lots of gems";',
        ),
        "CombatStyleHandler.java",
    )

    require_snippets(
        CLIENT,
        (
            "hasEquippedMiningTool()",
            '"Select mining focus"',
            '"Just the ore"',
            '"A few gems"',
            '"Plenty of gems"',
            '"Lots of gems"',
        ),
        "mudclient.java",
    )

    require_snippets(
        ITEM_DEFS,
        (
            '"name": "Dragon sword"',
            '"name": "Dragon Hatchet"',
            '"name": "Staff of Zamorak"',
            '"name": "Staff of Guthix"',
            '"name": "Staff of Saradomin"',
        ),
        "ItemDefs.json names",
    )
    item_defs_text = ITEM_DEFS.read_text(encoding="utf-8")
    if item_defs_text.count('"requiredLevel": 80') < 5:
        fail("ItemDefs.json should contain the tier-11 80 requirements for dragon sword/axe and the three god staffs")

    require_snippets(
        ITEM_DEFS_CUSTOM,
        (
            '"name": "Dragon 2-handed Sword"',
            '"name": "Dragon dagger"',
            '"name": "Poisoned dragon dagger"',
            '"name": "Dragon crossbow"',
            '"name": "Dragon longbow"',
        ),
        "ItemDefsCustom.json names",
    )
    item_defs_custom_text = ITEM_DEFS_CUSTOM.read_text(encoding="utf-8")
    if item_defs_custom_text.count('"requiredLevel": 80') < 5:
        fail("ItemDefsCustom.json should contain the tier-11 80 requirements for dragon melee and ranged weapons")

    print("PASS: mining focus and tier-11 requirements look correct")


if __name__ == "__main__":
    main()
