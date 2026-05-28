#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TANNER = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/alkharid/Tanner.java"
LEGACY_LEATHER_TANNING = (
    ROOT / "server/plugins/com/openrsc/server/plugins/custom/itemactions/LeatherTanning.java"
)
NPC_PLUGIN_DIRS = (
    ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs",
    ROOT / "server/plugins/com/openrsc/server/plugins/custom/npcs",
    ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/npcs",
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    tanner_text = TANNER.read_text(encoding="utf-8")

    if LEGACY_LEATHER_TANNING.exists():
        fail("Legacy hammer/fat/fire leather tanning should stay retired; use tanning racks instead")

    required_tanner_signpost = (
        "Use a tanning rack and work the hides yourself",
        "It is proper Crafting work now, not a simple swap",
    )
    for snippet in required_tanner_signpost:
        if snippet not in tanner_text:
            fail(f"Tanner should redirect players to tanning racks: {snippet}")

    retired_tanner_processing = (
        "How many hides would you like me to tan",
        "soft leather",
        "hard leather",
        "ItemId.HARD_LEATHER",
        "ItemId.COINS.id(), 1",
        "player.getCarriedItems().remove(new Item(ItemId.COW_HIDE",
        "player.getCarriedItems().remove(new Item(ItemId.COINS",
    )
    for snippet in retired_tanner_processing:
        if snippet in tanner_text:
            fail(f"Tanner should not perform paid hide processing anymore: {snippet}")

    direct_hide_conversions = (
        "remove(new Item(ItemId.COW_HIDE",
        "remove(new Item(ItemId.GOBLIN_HIDE",
        "remove(new Item(ItemId.UNICORN_HIDE",
        "remove(new Item(ItemId.BEAR_HIDE",
        "remove(new Item(ItemId.WOLF_HIDE",
        "remove(new Item(ItemId.DRAGON_HIDE",
    )
    leather_outputs = (
        "give(player, ItemId.LEATHER",
        "give(player, ItemId.HARD_LEATHER",
        "new Item(ItemId.LEATHER.id()",
        "new Item(ItemId.HARD_LEATHER.id()",
        "new Item(ItemId.GOBLIN_LEATHER.id()",
        "new Item(ItemId.UNICORN_LEATHER.id()",
        "new Item(ItemId.BEAR_LEATHER.id()",
        "new Item(ItemId.WOLF_LEATHER.id()",
        "new Item(ItemId.DRAGON_LEATHER.id()",
    )

    for directory in NPC_PLUGIN_DIRS:
        if not directory.exists():
            continue
        for path in directory.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if any(source in text for source in direct_hide_conversions) and any(
                output in text for output in leather_outputs
            ):
                fail(f"NPC plugin appears to perform direct hide-to-leather processing: {path.relative_to(ROOT)}")

    print("PASS: NPC processing cleanup guardrails look correct")


if __name__ == "__main__":
    main()
