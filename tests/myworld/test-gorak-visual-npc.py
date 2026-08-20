#!/usr/bin/env python3
"""Guard the developer-only Gorak visual-test NPC and sprite contract."""

import hashlib
import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / "dev/myworld/assets/sprites/npcs/gorak/gorak-sprite-sheet.png"
ASSET_README = SHEET.with_name("README.md")
SERVER_DEFS = ROOT / "server/conf/server/defs/VisualTestNpcDefs.json"
BASE_SERVER_DEFS = ROOT / "server/conf/server/defs/NpcDefsCustom.json"
SLAYER_SERVER_DEFS = ROOT / "server/conf/server/defs/MonsterSlayerNpcDefs.json"
SERVER_ENTITY_HANDLER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
NPC_LOCS = ROOT / "server/conf/server/defs/locs"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
ADMIN_COMMANDS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
CLIENT_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT_BUILD = ROOT / "Client_Base/build.xml"
EXPECTED_HASH = "b0fc60f30e055e68b88a4bb194c545de3ba61201a45483f4ca7e360608595e0f"


def require(source: str, expected: str, message: str) -> None:
    if expected not in source:
        raise SystemExit(f"FAIL: {message}: missing {expected!r}")


def main() -> None:
    data = SHEET.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("FAIL: Gorak sheet is not PNG")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (768, 384):
        raise SystemExit(f"FAIL: Gorak sheet must be 768x384, got {width}x{height}")
    if data[25] != 6:
        raise SystemExit("FAIL: Gorak sheet must preserve RGBA transparency")
    if hashlib.sha256(data).hexdigest() != EXPECTED_HASH:
        raise SystemExit("FAIL: Gorak sheet no longer matches its reviewed export")

    npc_defs = json.loads(SERVER_DEFS.read_text(encoding="utf-8"))["npcs"]
    goraks = [npc for npc in npc_defs if npc["id"] == 861]
    if len(goraks) != 1:
        raise SystemExit("FAIL: server must contain exactly one Gorak NPC 861 definition")
    gorak = goraks[0]
    expected_server = {
        "name": "Gorak",
        "attack": 1,
        "strength": 1,
        "hits": 1,
        "defense": 1,
        "combatlvl": 1,
        "attackable": 0,
        "aggressive": 0,
        "camera1": 327,
        "camera2": 240,
        "walkModel": 10,
        "combatModel": 7,
    }
    for key, value in expected_server.items():
        if gorak.get(key) != value:
            raise SystemExit(f"FAIL: Gorak {key} must be {value!r}")
    base_custom_defs = json.loads(BASE_SERVER_DEFS.read_text(encoding="utf-8"))["npcs"]
    slayer_defs = json.loads(SLAYER_SERVER_DEFS.read_text(encoding="utf-8"))["npcs"]
    if base_custom_defs[-1]["id"] != 845 or slayer_defs[0]["id"] != 846:
        raise SystemExit("FAIL: Gorak must not disturb the sequential custom/Monster Slayer boundary")
    if [npc["id"] for npc in slayer_defs] != list(range(846, 861)):
        raise SystemExit("FAIL: Monster Slayer NPC definitions must remain sequential through 860")
    server_handler = SERVER_ENTITY_HANDLER.read_text(encoding="utf-8")
    slayer_load = 'loadNpcs(getServer().getConfig().CONFIG_DIR + "/defs/MonsterSlayerNpcDefs.json");'
    visual_load = 'loadNpcs(getServer().getConfig().CONFIG_DIR + "/defs/VisualTestNpcDefs.json");'
    require(server_handler, slayer_load, "server Monster Slayer definition load")
    require(server_handler, visual_load, "server visual-test definition load")
    if server_handler.index(visual_load) < server_handler.index(slayer_load):
        raise SystemExit("FAIL: visual-test NPC definitions must load after production My World NPCs")

    for location_file in NPC_LOCS.glob("*NpcLocs*.json"):
        if '"id": 861' in location_file.read_text(encoding="utf-8"):
            raise SystemExit(f"FAIL: Gorak must not have a permanent placement: {location_file}")
    drops = NPC_DROPS.read_text(encoding="utf-8")
    if "Gorak" in drops:
        raise SystemExit("FAIL: visual-test Gorak must not have a drop table")

    admin = ADMIN_COMMANDS.read_text(encoding="utf-8")
    for expected in (
        'command.equalsIgnoreCase("spawnnpc")',
        "player.getX() - radius, player.getX() + radius",
        "player.getY() - radius, player.getY() + radius",
        "n.setShouldRespawn(false)",
    ):
        require(admin, expected, "developer spawn/roam contract")

    client_defs = CLIENT_DEFS.read_text(encoding="utf-8")
    for expected in (
        "private static final int GORAK_VISUAL_TEST_NPC_ID = 861;",
        "private static final int GORAK_FRAME_WIDTH = 327;",
        "private static final int GORAK_FRAME_HEIGHT = 240;",
        "setCustomNpcDefinition(861, new NPCDef(",
        "0, 0, 0, 0, 327, 240, 10, 7, 5, 861",
        'animations.add(new AnimationDef("gorak", "npc", 0, 0, true, false, 0));',
        "public static void activateGorakExternalVisual()",
        "gorak.sprites[0] = gorakAnimationId;",
    ):
        require(client_defs, expected, "client Gorak definition")

    client = CLIENT.read_text(encoding="utf-8")
    for expected in (
        "getExternalGorakSpriteSheet()",
        '"dev/myworld/assets/sprites/npcs/gorak"',
        '"gorak-sprite-sheet.png"',
        'getExternalGorakSpriteSheet(), "gorak", 6,',
        "NpcDirectionalAnimationMapping.FRAMES_PER_DIRECTION",
        "EntityHandler.activateGorakExternalVisual();",
        'npcSprites.put("gorak", spriteEntry);',
        '|| "gorak".equalsIgnoreCase(s)',
    ):
        require(client, expected, "client Gorak sprite loading")

    readme = ASSET_README.read_text(encoding="utf-8")
    for expected in ("2009Scape", "AGPL-3.0", "NPC `4418`", EXPECTED_HASH):
        require(readme, expected, "Gorak attribution")

    require(
        CLIENT_BUILD.read_text(encoding="utf-8"),
        '<include name="sprites/**/*.png"/>',
        "client asset packaging",
    )

    print("PASS: Gorak visual-test definition, sprite, spawn roaming, isolation, and attribution guarded")


if __name__ == "__main__":
    main()
