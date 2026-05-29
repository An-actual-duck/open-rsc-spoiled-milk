#!/usr/bin/env python3
"""Validate MyWorld first-login spawn policy."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MYWORLD_CONF = ROOT / "server" / "myworld.conf"
LOGIN_HANDLER = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "net"
    / "rsc"
    / "LoginPacketHandler.java"
)
ACTION_SENDER = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "net"
    / "rsc"
    / "ActionSender.java"
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_conf_bool(key: str, value: bool) -> None:
    text = MYWORLD_CONF.read_text(encoding="utf-8")
    expected = "true" if value else "false"
    if not re.search(rf"^\s*{re.escape(key)}:\s*{expected}\b", text, re.MULTILINE):
        fail(f"server/myworld.conf must keep {key}: {expected}")


def main() -> None:
    require_conf_bool("arrive_lumbridge", True)

    login_text = LOGIN_HANDLER.read_text(encoding="utf-8")
    required = (
        "firstTimeLocation = Point.location(server.getConfig().RESPAWN_LOCATION_X, "
        "server.getConfig().RESPAWN_LOCATION_Y);"
    )
    if required not in login_text:
        fail("LoginPacketHandler no longer sends arrive_lumbridge players to the respawn point")

    if "firstTimeLocation = Point.location(216, 744);" not in login_text:
        fail("LoginPacketHandler tutorial-island fallback changed; review Tutorial Island repurpose assumptions")

    action_sender_text = ACTION_SENDER.read_text(encoding="utf-8")
    for required in (
        "sendMyWorldFirstLoginIntro",
        "There is no Tutorial Island here",
        "Check your bank for your starting tools, runes, arrows, and coins.",
        "open the stats menu and click a skill",
        "github.com/An-actual-duck/open-rsc-spoiled-milk",
        'MYWORLD_STARTER_BANK_CACHE_KEY = "myworld_starter_bank_v1"',
        "shouldUseMyWorldStarterLoadout",
        "ensureMyWorldStarterLoadout",
        "addMissingMyWorldStarterBankItem",
        "addMyWorldStarterLoadout",
        "ItemId.TIN_SHORT_SWORD.id()",
        "ItemId.TIN_SQUARE_SHIELD.id()",
        "ItemId.TIN_AXE.id()",
        "ItemId.TIN_PICKAXE.id()",
        "ItemId.SHORTBOW.id()",
        "ItemId.STAFF.id()",
        "ItemId.AIR_RUNE.id(), 100",
        "ItemId.WATER_RUNE.id(), 100",
        "ItemId.EARTH_RUNE.id(), 100",
        "ItemId.FIRE_RUNE.id(), 100",
        "ItemId.MIND_RUNE.id(), 100",
        "ItemId.LIFE_RUNE.id(), 100",
        "ItemId.TIN_ARROWS.id(), 100",
        "ItemId.TINDERBOX.id()",
        "ItemId.HAMMER.id()",
        "ItemId.CHISEL.id()",
        "ItemId.KNIFE.id()",
        "ItemId.RING_MOULD.id()",
        "ItemId.NECKLACE_MOULD.id()",
        "ItemId.AMULET_MOULD.id()",
        "ItemId.HOLY_SYMBOL_MOULD.id()",
        "ItemId.BOLT_MOULD.id()",
        "ItemId.DART_MOULD.id()",
        "ItemId.THROWING_KNIFE_MOULD.id()",
        "ItemId.ARROWHEAD_MOULD.id()",
        "ItemId.COINS.id(), 500",
        "player.getBank().countId(itemId)",
        "player.getCache().store(MYWORLD_STARTER_BANK_CACHE_KEY, true)",
        "player.getConfig().WANT_MYWORLD",
        "player.getConfig().ARRIVE_LUMBRIDGE",
        "!playerInTutorialLanding",
    ):
        if required not in action_sender_text:
            fail(f"MyWorld first-login intro missing guard or copy: {required}")

    if "else if (!player.getConfig().USES_CLASSES)" not in action_sender_text:
        fail("MyWorld starter loadout should bypass the default configured starter items")

    for removed in (
        "ItemId.NET.id()",
        "ItemId.FLY_FISHING_ROD.id()",
        "ItemId.LOBSTER_POT.id()",
        "ItemId.HARPOON.id()",
    ):
        if removed in action_sender_text:
            fail(f"MyWorld starter bank should only include the standard fishing rod, not {removed}")

    print("PASS: MyWorld first-login spawn policy validated")


if __name__ == "__main__":
    main()
