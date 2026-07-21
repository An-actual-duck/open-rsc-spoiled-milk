#!/usr/bin/env python3
import json
import struct
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ACCESS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/ShiloFurnaceAccess.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
CRAFTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java"
YOHNUS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/shilo/Yohnus.java"
BOUNDARIES = ROOT / "server/conf/server/defs/locs/BoundaryLocs.json"
SERVER_LANDSCAPE = ROOT / "server/conf/server/data/Custom_Landscape.orsc"
CLIENT_LANDSCAPE = ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc"
SECTOR = "h0x56y54"
TILE_OFFSET = ((400 - 384) * 48 + (845 - 816)) * 10


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    access = ACCESS.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    crafting = CRAFTING.read_text(encoding="utf-8")
    yohnus = YOHNUS.read_text(encoding="utf-8")

    require(access, "Point.location(399, 840)", "unlock is not tied to the actual Shilo furnace")
    require(access, 'UNLOCK_CACHE_KEY = "myworld_shilo_furnace_unlocked"',
            "unlock lacks durable player-cache state")
    require(access, "ItemId.RED_TOPAZ.id()", "unlock does not use cut red topaz")
    require(access, "Optional.of(false)", "unlock can accept a noted topaz")
    require(access, "new Item(ItemId.RED_TOPAZ.id(), 1)", "unlock does not remove exactly one topaz")
    require(access, 'multi(player, "Give Yohnus one cut red topaz", "Not now")',
            "furnace use does not ask before payment")
    remove_index = access.index("player.getCarriedItems().remove")
    store_index = access.index("player.getCache().store(UNLOCK_CACHE_KEY, true)")
    if store_index < remove_index:
        fail("unlock is stored before inventory removal succeeds")

    if smelting.count("ShiloFurnaceAccess.ensureUnlocked(player)") < 2:
        fail("smithing direct and item-on-furnace paths are not both gated")
    require(crafting, "ShiloFurnaceAccess.ensureUnlocked(player)",
            "crafting item-on-furnace path bypasses the unlock")
    require(yohnus, "ShiloFurnaceAccess.explainAndOffer(player, n)",
            "Yohnus no longer explains or collects the unlock payment")
    for obsolete in ("Use Furnace - 20 Gold", "fastYohnus", "fast_yohnus", "OpBoundTrigger"):
        if obsolete in yohnus:
            fail(f"obsolete repeat-payment path remains: {obsolete}")

    boundary_data = json.loads(BOUNDARIES.read_text(encoding="utf-8"))
    for boundary in boundary_data["boundaries"]:
        position = boundary["pos"]
        if boundary["id"] == 165 and position["X"] == 400 and position["Y"] == 845:
            fail("obsolete Yohnus boundary remains in authored locations")

    if SERVER_LANDSCAPE.read_bytes() != CLIENT_LANDSCAPE.read_bytes():
        fail("client/server custom landscapes differ")
    with zipfile.ZipFile(SERVER_LANDSCAPE) as archive:
        sector = archive.read(SECTOR)
    _, _, _, _, horizontal_wall, vertical_wall, diagonal = struct.unpack_from(
        ">BBBBBBI", sector, TILE_OFFSET
    )
    if horizontal_wall != 0 or vertical_wall != 0 or diagonal != 0:
        fail("obsolete furnace barrier still exists in landscape/collision data")

    print("PASS: Shilo furnace uses one durable topaz unlock and has no physical barrier")


if __name__ == "__main__":
    main()
