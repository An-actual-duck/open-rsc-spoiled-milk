#!/usr/bin/env python3
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
MASTER_CRAFTER = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/npcs/rimmington/MasterCrafter.java"
)

STAIRS_ID = 42
STAIRS_POSITION = (338, 614)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label} missing expected snippet: {snippet}")


def main() -> None:
    sceneries = json.loads(SCENERY_LOCS.read_text(encoding="utf-8"))["sceneries"]
    stairs = next(
        (
            loc
            for loc in sceneries
            if loc.get("id") == STAIRS_ID
            and (loc.get("pos", {}).get("X"), loc.get("pos", {}).get("Y")) == STAIRS_POSITION
        ),
        None,
    )
    if stairs is None:
        raise SystemExit("FAIL: missing Crafting Guild extension stairs at 338,614")
    if stairs.get("direction") != 2:
        raise SystemExit("FAIL: Crafting Guild extension stairs should use direction 2")

    plugin = MASTER_CRAFTER.read_text(encoding="utf-8")
    require(plugin, "implements TalkNpcTrigger, OpLocTrigger", "Master Crafter object trigger")
    require(plugin, "private static final int CRAFTING_GUILD_EXTENSION_STAIRS = 42;", "stair id")
    require(plugin, "WorldLocation.global(new WorldCoordinate(338, 614, 0))", "exact surface stair location")
    require(plugin, "obj.getID() == CRAFTING_GUILD_EXTENSION_STAIRS", "exact stair guard")
    require(plugin, "isCaveStairLocation(obj.getWorldLocation())", "signed-location guard")
    require(plugin, 'command.equals("go down")', "use-action guard")
    require(plugin, "CRAFTING_GUILD_EXTENSION_STAIRS_LOCATION.equals(location)", "worldspace-qualified guard")
    require(plugin, "CRAFTING_GUILD_CAVES_LEVEL = 70", "advanced cave Crafting requirement")
    require(plugin, "getMaxLevel(player, Skill.CRAFTING.id()) >= CRAFTING_GUILD_CAVES_LEVEL", "permanent level gate")
    require(plugin, '"You need level 70 Crafting to enter the caves below the guild"', "level refusal")
    require(plugin, '"Gem dragons lurk down there. Be prepared for a dangerous fight"', "danger warning")
    require(plugin, "npcsay(player, master, CAVES_REQUIREMENT, CAVES_WARNING)", "Master Crafter refusal")
    require(plugin, "player.message(CAVES_REQUIREMENT)", "fallback level guidance")
    require(plugin, "player.message(CAVES_WARNING)", "fallback danger guidance")
    require(plugin, "LADDERS.onObjectAction(obj, command, player)", "normal stair transport")
    for retired in ("currently off limits", "still preparing the guild extension"):
        if retired in plugin:
            raise SystemExit(f"FAIL: retired permanent closure remains: {retired}")

    print("PASS: Crafting Guild cave stairs require 70 Crafting and retain normal transport with danger guidance")


if __name__ == "__main__":
    main()
