#!/usr/bin/env python3
"""Regression coverage for the explicit Mage Arena layered round trip."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARENA = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java"
FUNCTIONS = ROOT / "server/src/com/openrsc/server/plugins/Functions.java"


def require(text, value, label):
    assert value in text, f"missing {label}: {value}"


arena = ARENA.read_text(encoding="utf-8")
functions = FUNCTIONS.read_text(encoding="utf-8")

require(arena, "private static final WorldLocation ARENA_ENTRY = location(229, 130, 0);", "arena entry")
require(arena, "private static final WorldLocation KOLODION_COMBAT_SPAWN = location(227, 130, 0);", "combat spawn")
require(arena, "private static final WorldLocation ARENA_EXIT = location(228, 118, 0);", "arena exit")
require(arena, "private static final WorldLocation ARENA_REENTRY = location(228, 120, 0);", "arena reentry")
require(arena, "private static final WorldLocation KOLODION_CAVE_RETURN = location(446, 538, -1);", "underground return")
require(arena, "teleport(player, ARENA_ENTRY);", "all entry teleports")
require(arena, "addnpc(id, KOLODION_COMBAT_SPAWN,", "explicit Kolodion spawn")
require(arena, "teleport(player, KOLODION_CAVE_RETURN);", "explicit cave return")
require(arena, "teleport(player, ARENA_EXIT);", "explicit barrier exit")
require(arena, "teleport(player, ARENA_REENTRY);", "explicit barrier reentry")
assert "teleport(player, 229, 130)" not in arena
assert "addnpc(id, 227, 130" not in arena
assert "player.teleport(446, 3370)" not in arena
require(functions, "final WorldLocation location,", "layered timed NPC overload")
require(functions, "new Npc(spawnedFor.getWorld(), id, location)", "layered NPC construction")
print("PASS: Mage Arena combat round trip uses explicit layered locations")
