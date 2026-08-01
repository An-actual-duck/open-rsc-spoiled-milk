#!/usr/bin/env python3
import json
import struct
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_LANDSCAPE = ROOT / "server/conf/server/data/Custom_Landscape.orsc"
CLIENT_LANDSCAPE = ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc"
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocs.json"
MYWORLD_SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
BOUNDARY_LOCS = ROOT / "server/conf/server/defs/locs/BoundaryLocs.json"
NPC_LOCS = ROOT / "server/conf/server/defs/locs/NpcLocs.json"
NPC_LOCS_14 = ROOT / "server/conf/server/defs/locs/NpcLocs14.json"
NPC_LOCS_27 = ROOT / "server/conf/server/defs/locs/NpcLocs27.json"
MYWORLD_NPC_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldNpcLocs.json"
NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefs.json"
NPC_DEFS_PATCH18 = ROOT / "server/conf/server/defs/NpcDefsPatch18.json"
NPC_DEFS_CUSTOM = ROOT / "server/conf/server/defs/NpcDefsCustom.json"
OBJECT_TELEPOINTS = ROOT / "server/conf/server/defs/extras/ObjectTelePoints.xml"
NPC_ID = ROOT / "server/src/com/openrsc/server/constants/NpcId.java"
SERVER_ENTITY_HANDLER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
DOOR_ACTION = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java"
RANGERS_GUILD_DOOR = ROOT / "server/plugins/com/openrsc/server/plugins/custom/misc/RangersGuildDoor.java"
LOWES_ARCHERY = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/LowesArchery.java"
LOWES_ARCHERY_OPENPK = ROOT / "server/plugins/com/openrsc/server/plugins/custom/npcs/LowesArcheryOpenPk.java"
RANGERS_GUILD_RANGER = ROOT / "server/plugins/com/openrsc/server/plugins/custom/npcs/RangersGuildRanger.java"
RANGERS_GUILD_DRAGON_SHOP = ROOT / "server/plugins/com/openrsc/server/plugins/custom/npcs/RangersGuildDragonShop.java"
RANGERS_GUILD_POINTS_VENDOR = ROOT / "server/plugins/com/openrsc/server/plugins/custom/npcs/RangersGuildPointsVendor.java"
RANGERS_GUILD_AREA = ROOT / "server/src/com/openrsc/server/content/RangersGuildArea.java"
RANGERS_GUILD_POINTS = ROOT / "server/src/com/openrsc/server/content/RangersGuildPoints.java"
SKILLS = ROOT / "server/src/com/openrsc/server/model/Skills.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT_MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
BASEMENT_SECTOR = "h3x58y46"
BASEMENT_SECTOR_ORIGIN_X = 480
BASEMENT_SECTOR_ORIGIN_Y = 3264
GROUND_SECTOR = "h0x58y46"
GROUND_SECTOR_ORIGIN_X = 480
GROUND_SECTOR_ORIGIN_Y = 432
BASEMENT_REQUIRED_WALKABLE_TILES = {
    (498, 3296),
    (499, 3295),
    (499, 3296),
}
GROUND_DOWN_STAIR_TILES = {
    (x, y)
    for x in range(499, 501)
    for y in range(469, 472)
}
GROUND_RESTORED_FLOOR_TILES = {
    (498, y)
    for y in range(469, 472)
}
BASEMENT_STAIR_SQUARE_TILES = {
    (x, y)
    for x in range(494, 505)
    for y in range(3292, 3302)
}
RANGERS_GUILD_BASEMENT_NPCS = {
    68: 12,   # level-32 zombie
    45: 12,   # level-31 skeleton
    135: 4,   # ice giant
    22: 4,    # lesser demon
    190: 8,   # chaos dwarf
}
RANGERS_GUILD_BASEMENT_NPC_BOUNDS = {
    68: (488, 3282, 509, 3290),
    45: (488, 3303, 510, 3309),
    135: (506, 3286, 514, 3295),
    22: (506, 3297, 514, 3306),
    190: (485, 3287, 492, 3306),
}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read_sector(path, sector_name):
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, f"{path} must be a valid landscape archive")
        return archive.read(sector_name)


def tile(sector, x, y, *, origin_x, origin_y):
    offset = ((x - origin_x) * 48 + (y - origin_y)) * 10
    return struct.unpack_from(">BBBBBBI", sector, offset)


def load_scenery(path):
    return json.loads(path.read_text(encoding="utf-8"))["sceneries"]


def load_boundaries(path):
    return json.loads(path.read_text(encoding="utf-8"))["boundaries"]


def scenery_tuple(loc):
    return (loc["id"], loc["pos"]["X"], loc["pos"]["Y"], loc["direction"])


def scenery_set(path):
    return {scenery_tuple(loc) for loc in load_scenery(path)}


def boundary_set(path):
    return {scenery_tuple(loc) for loc in load_boundaries(path)}


def load_npcs(path):
    with path.open() as handle:
        return json.load(handle)["npclocs"]


def npc_location_tuple(loc):
    return (
        int(loc["id"]),
        int(loc["start"]["X"]),
        int(loc["start"]["Y"]),
        int(loc["min"]["X"]),
        int(loc["min"]["Y"]),
        int(loc["max"]["X"]),
        int(loc["max"]["Y"]),
    )


def npc_def_by_id(path, npc_id):
    npcs = json.loads(path.read_text(encoding="utf-8"))["npcs"]
    return next((npc for npc in npcs if int(npc["id"]) == npc_id), None)


def ensure_basement_terrain():
    server_sector = read_sector(SERVER_LANDSCAPE, BASEMENT_SECTOR)
    client_sector = read_sector(CLIENT_LANDSCAPE, BASEMENT_SECTOR)
    require(client_sector == server_sector, "Client and server Rangers Guild basement terrain must match")

    edited_tiles = []
    for x in range(BASEMENT_SECTOR_ORIGIN_X, BASEMENT_SECTOR_ORIGIN_X + 48):
        for y in range(BASEMENT_SECTOR_ORIGIN_Y, BASEMENT_SECTOR_ORIGIN_Y + 48):
            elevation, texture, overlay, roof, east_wall, north_wall, diagonal_wall = tile(
                server_sector,
                x,
                y,
                origin_x=BASEMENT_SECTOR_ORIGIN_X,
                origin_y=BASEMENT_SECTOR_ORIGIN_Y,
            )
            if overlay != 8 or east_wall or north_wall or diagonal_wall:
                edited_tiles.append((x, y, overlay, texture, elevation))

    require(
        len(edited_tiles) >= 500,
        "Rangers Guild basement terrain should contain the imported first-draft edit, not only the small seed",
    )
    xs = [entry[0] for entry in edited_tiles]
    ys = [entry[1] for entry in edited_tiles]
    require(
        min(xs) <= 484 and max(xs) >= 515 and min(ys) <= 3281 and max(ys) >= 3310,
        "Rangers Guild basement terrain edit no longer covers the expected first-draft footprint",
    )

    for x, y in BASEMENT_REQUIRED_WALKABLE_TILES:
        require(
            tile(
                server_sector,
                x,
                y,
                origin_x=BASEMENT_SECTOR_ORIGIN_X,
                origin_y=BASEMENT_SECTOR_ORIGIN_Y,
            )[2] != 8,
            f"Rangers Guild basement stair/landing tile should be walkable at {x},{y}",
        )
    for x, y in BASEMENT_STAIR_SQUARE_TILES:
        elevation, texture, overlay, roof, east_wall, north_wall, diagonal_wall = tile(
            server_sector,
            x,
            y,
            origin_x=BASEMENT_SECTOR_ORIGIN_X,
            origin_y=BASEMENT_SECTOR_ORIGIN_Y,
        )
        require(
            texture == 0 and overlay == 5,
            f"Rangers Guild basement stair square should be solid grey at {x},{y}",
        )

    server_sector = read_sector(SERVER_LANDSCAPE, GROUND_SECTOR)
    client_sector = read_sector(CLIENT_LANDSCAPE, GROUND_SECTOR)
    require(client_sector == server_sector, "Client and server Rangers Guild ground terrain must match")

    for x, y in GROUND_DOWN_STAIR_TILES:
        require(
            tile(
                server_sector,
                x,
                y,
                origin_x=GROUND_SECTOR_ORIGIN_X,
                origin_y=GROUND_SECTOR_ORIGIN_Y,
            )
            == (152, 70, 8, 0, 0, 0, 0),
            f"Ground-floor down-stair opening is wrong at {x},{y}",
        )
    for x, y in GROUND_RESTORED_FLOOR_TILES:
        require(
            tile(
                server_sector,
                x,
                y,
                origin_x=GROUND_SECTOR_ORIGIN_X,
                origin_y=GROUND_SECTOR_ORIGIN_Y,
            )
            == (152, 70, 5, 0, 0, 0, 0),
            f"Old ground-floor down-stair opening should be restored to floor at {x},{y}",
        )


def ensure_scenery_layout():
    base = scenery_set(SCENERY_LOCS)
    myworld = scenery_set(MYWORLD_SCENERY_LOCS)

    for expected in {
        (64, 495, 463, 2),
        (272, 500, 467, 2),
        (272, 500, 464, 2),
        (274, 496, 471, 0),
        (41, 490, 466, 0),
        (42, 490, 1410, 0),
    }:
        require(expected in base, f"Missing base Rangers Guild scenery {expected}")

    for removed in {
        (63, 495, 463, 2),
        (272, 500, 466, 2),
        (272, 500, 465, 2),
        (272, 500, 468, 2),
        (274, 497, 471, 0),
    }:
        require(removed not in base, f"Old base scenery still present {removed}")

    for expected in {
        (145, 490, 464, 6),
        (47, 491, 471, 2),
        (279, 494, 471, 0),
        (42, 499, 469, 4),
        (41, 498, 3296, 0),
        (31, 496, 1408, 0),
        (31, 498, 1408, 0),
    }:
        require(expected in myworld, f"Missing MyWorld Rangers Guild scenery {expected}")

    for removed in {
        (145, 491, 464, 6),
        (47, 491, 470, 6),
        (279, 493, 471, 0),
        (42, 498, 469, 4),
        (42, 499, 469, 0),
        (41, 499, 3296, 0),
        (31, 496, 1408, 4),
        (31, 498, 1408, 4),
    }:
        require(removed not in myworld, f"Old MyWorld scenery still present {removed}")


def ensure_stair_telepoints():
    root = ET.parse(OBJECT_TELEPOINTS).getroot()
    telepoints = {}
    for entry in root.findall("entry"):
        point = entry.find("Point")
        telepoint = entry.find("TelePoint")
        telepoints[
            (
                int(point.findtext("x")),
                int(point.findtext("y")),
                telepoint.findtext("command"),
            )
        ] = (int(telepoint.findtext("x")), int(telepoint.findtext("y")))

    require(
        telepoints.get((499, 469, "Go down")) == (499, 3295),
        "Ground-floor Rangers Guild stairs should lead to the basement seed",
    )
    require(
        (498, 469, "Go down") not in telepoints,
        "Old ground-floor Rangers Guild stair telepoint should be removed",
    )
    require(
        telepoints.get((498, 3296, "Go up")) == (499, 468),
        "Basement Rangers Guild stairs should return to the ground floor",
    )
    require(
        (499, 3296, "Go up") not in telepoints,
        "Old basement Rangers Guild stair telepoint should be removed",
    )


def ensure_basement_npcs():
    server_sector = read_sector(SERVER_LANDSCAPE, BASEMENT_SECTOR)
    counts = {}
    for loc in load_npcs(MYWORLD_NPC_LOCS):
        start = loc["start"]
        x = int(start["X"])
        y = int(start["Y"])
        if 484 <= x <= 515 and 3281 <= y <= 3310:
            npc_id = int(loc["id"])
            counts[npc_id] = counts.get(npc_id, 0) + 1
            require(
                npc_id in RANGERS_GUILD_BASEMENT_NPCS,
                f"Unexpected Rangers Guild basement NPC id {npc_id}",
            )
            min_x, min_y, max_x, max_y = RANGERS_GUILD_BASEMENT_NPC_BOUNDS[npc_id]
            require(
                min_x <= x <= max_x and min_y <= y <= max_y,
                f"Rangers Guild basement NPC {npc_id} is in the wrong cage at {x},{y}",
            )
            require(
                int(loc["min"]["X"]) <= x <= int(loc["max"]["X"])
                and int(loc["min"]["Y"]) <= y <= int(loc["max"]["Y"]),
                f"Rangers Guild basement NPC {npc_id} has start outside movement bounds",
            )
            _, _, overlay, _, east_wall, north_wall, diagonal_wall = tile(
                server_sector,
                x,
                y,
                origin_x=BASEMENT_SECTOR_ORIGIN_X,
                origin_y=BASEMENT_SECTOR_ORIGIN_Y,
            )
            require(
                overlay != 8 and not east_wall and not north_wall and not diagonal_wall,
                f"Rangers Guild basement NPC {npc_id} starts on a blocked tile at {x},{y}",
            )

    require(
        counts == RANGERS_GUILD_BASEMENT_NPCS,
        f"Unexpected Rangers Guild basement NPC counts: {counts}",
    )


def ensure_rangers_guild_entrance():
    expected_ranger_loc = (840, 497, 463, 497, 462, 498, 464)
    myworld_locs = {npc_location_tuple(loc) for loc in load_npcs(MYWORLD_NPC_LOCS)}
    require(expected_ranger_loc in myworld_locs, "Ranger should stand outside the Rangers Guild entrance")

    boundaries = boundary_set(BOUNDARY_LOCS)
    for removed in {
        (146, 495, 464, 0),
        (146, 496, 464, 0),
    }:
        require(removed not in boundaries, f"Rangers Guild should not retain overlapping boundary door {removed}")

    door_action = RANGERS_GUILD_DOOR.read_text(encoding="utf-8")
    for fragment in (
        "class RangersGuildDoor implements OpLocTrigger",
        "CLOSED_DOUBLE_DOORS = 64",
        "OPEN_DOUBLE_DOORS = 63",
        "DOOR_X = 495",
        "DOOR_Y = 463",
        "getCurrentLevel(player, Skill.RANGED.id()) < 66",
        "NpcId.RANGERS_GUILD_RANGER.id()",
        "You need a ranged level of 66 to enter the guild",
        "doDoor(obj, player, OPEN_DOUBLE_DOORS);",
        "player.teleport(targetX, entering ? DOOR_Y + 1 : DOOR_Y - 1);",
    ):
        require(fragment in door_action, f"Rangers Guild door gate is missing: {fragment}")

    npc_id = NPC_ID.read_text(encoding="utf-8")
    require("RANGERS_GUILD_RANGER(840)" in npc_id, "NpcId should reserve id 840 for the entrance Ranger")

    ranger = npc_def_by_id(NPC_DEFS_CUSTOM, 840)
    require(ranger is not None, "Custom NPC defs should define the Rangers Guild Ranger id 840")
    require(ranger["name"] == "Ranger", "Rangers Guild Ranger should have the expected display name")
    require(ranger["command"] == "" and ranger["command2"] == "", "Rangers Guild Ranger should not have shop commands")
    expected_sprites = {
        "sprites5": 107,
        "sprites6": 559,
        "sprites7": 565,
        "sprites8": 571,
        "sprites9": 577,
        "sprites10": 583,
        "sprites12": 66,
    }
    for key, value in expected_sprites.items():
        require(ranger[key] == value, f"Rangers Guild Ranger {key} should be {value}")

    ranger_plugin = RANGERS_GUILD_RANGER.read_text(encoding="utf-8")
    for fragment in (
        "n.getID() == NpcId.RANGERS_GUILD_RANGER.id()",
        "getCurrentLevel(player, Skill.RANGED.id()) < 66",
        "Hello, only skilled rangers are allowed in here",
        "Hello, welcome to the Rangers Guild",
        "The basement is set up for ranged practice",
    ):
        require(fragment in ranger_plugin, f"Rangers Guild Ranger dialogue is missing: {fragment}")


def ensure_ranged_master_and_archery_shopkeeper():
    expected_lowe_loc = (58, 493, 466, 492, 465, 495, 468)
    myworld_locs = {npc_location_tuple(loc) for loc in load_npcs(MYWORLD_NPC_LOCS)}
    require(expected_lowe_loc in myworld_locs, "Lowe should spawn on the Rangers Guild ground floor")

    expected_varrock_loc = (839, 115, 515, 113, 512, 116, 516)
    for path in (NPC_LOCS, NPC_LOCS_14, NPC_LOCS_27):
        locs = {npc_location_tuple(loc) for loc in load_npcs(path)}
        require(expected_varrock_loc in locs, f"Arlen should replace Lowe in {path.name}")
        require(
            (58, 115, 515, 113, 512, 116, 516) not in locs,
            f"Lowe should no longer spawn in the Varrock archery shop in {path.name}",
        )

    for path in (NPC_DEFS, NPC_DEFS_PATCH18):
        lowe = npc_def_by_id(path, 58)
        require(lowe is not None, f"{path.name} should define Lowe")
        require(lowe["name"] == "Lowe, Ranged Master", f"{path.name} should rename Lowe")
        require(lowe["description"] == "The Ranged master of the Rangers Guild", f"{path.name} should update Lowe's description")
        require(lowe["command"] == "", f"{path.name} should remove Lowe's shop command")

    arlen = npc_def_by_id(NPC_DEFS_CUSTOM, 839)
    require(arlen is not None, "Custom NPC defs should define Arlen id 839")
    require(arlen["name"] == "Arlen", "Arlen should have the expected display name")
    require(arlen["command"] == "Trade" and arlen["command2"] == "Shop", "Arlen should have direct shop commands")

    npc_id = NPC_ID.read_text(encoding="utf-8")
    require("LOWES_ARCHERY_SHOPKEEPER(839)" in npc_id, "NpcId should reserve id 839 for Arlen")

    entity_handler = SERVER_ENTITY_HANDLER.read_text(encoding="utf-8")
    quick_trade_start = entity_handler.find("private int[] quickTradeNpcs = new int[] {")
    quick_trade_end = entity_handler.find("\n\t};", quick_trade_start)
    require(quick_trade_start >= 0 and quick_trade_end > quick_trade_start, "Server quick trade NPC list should be present")
    quick_trade_body = entity_handler[quick_trade_start:quick_trade_end]
    require("NpcId.LOWES_ARCHERY_SHOPKEEPER.id()" in quick_trade_body, "Arlen should be in the quick trade NPC list")
    require("NpcId.LOWE.id()" not in quick_trade_body, "Lowe should not remain in the quick trade NPC list")

    lowes_archery = LOWES_ARCHERY.read_text(encoding="utf-8")
    for fragment in (
        "npc.getID() == NpcId.LOWE.id()",
        "talkRangedMaster(player);",
        "npc.getID() == NpcId.LOWES_ARCHERY_SHOPKEEPER.id()",
        "isDirectShopCommand(player, npc, command)",
        "You earn Rangers Guild points from ranged experience down there",
    ):
        require(fragment in lowes_archery, f"LowesArchery split is missing: {fragment}")

    openpk = LOWES_ARCHERY_OPENPK.read_text(encoding="utf-8")
    require("NpcId.LOWES_ARCHERY_SHOPKEEPER.id()" in openpk, "OpenPK archery shop should target Arlen")
    require("NpcId.LOWE.id()" not in openpk, "OpenPK archery shop should not target Lowe")

    client = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    for fragment in (
        '"Lowe, Ranged Master", "The Ranged master of the Rangers Guild", ""',
        "setCustomNpcDefinition(839, new NPCDef(",
        '"Arlen", "He runs the Varrock archery shop", Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""',
        'Config.S_RIGHT_CLICK_TRADE ? "Shop" : null',
        "setCustomNpcDefinition(840, new NPCDef(",
        '"Ranger", "He watches the Rangers Guild entrance", ""',
        "new int[]{0, 1, 2, -1, 107, 559, 565, 571, 577, 583, -1, 66}",
    ):
        require(fragment in client, f"Client NPC definitions are missing: {fragment}")


def ensure_rangers_guild_dragon_vendor():
    expected_vendor_loc = (841, 493, 1414, 492, 1413, 494, 1414)
    myworld_locs = {npc_location_tuple(loc) for loc in load_npcs(MYWORLD_NPC_LOCS)}
    require(expected_vendor_loc in myworld_locs, "Aeron should stand by the upstairs Rangers Guild vendor counter")

    npc_id = NPC_ID.read_text(encoding="utf-8")
    require("RANGERS_GUILD_DRAGON_VENDOR(841)" in npc_id, "NpcId should reserve id 841 for Aeron")

    vendor = npc_def_by_id(NPC_DEFS_CUSTOM, 841)
    require(vendor is not None, "Custom NPC defs should define Aeron id 841")
    require(vendor["name"] == "Aeron", "Aeron should have the expected display name")
    require(vendor["command"] == "Trade" and vendor["command2"] == "", "Aeron should have a Trade shortcut without a duplicate Shop command")
    require(vendor["sprites12"] == 66, "Aeron should wear a green cape")
    require(vendor["topColour"] == 8409120 and vendor["bottomColour"] == 2, "Aeron should not visually match Talia")
    for key in ("sprites5", "sprites6", "sprites7", "sprites8", "sprites9", "sprites10", "sprites11"):
        require(vendor[key] == -1, f"Aeron should not have special equipment in {key}")

    entity_handler = SERVER_ENTITY_HANDLER.read_text(encoding="utf-8")
    quick_trade_start = entity_handler.find("private int[] quickTradeNpcs = new int[] {")
    quick_trade_end = entity_handler.find("\n\t};", quick_trade_start)
    require(quick_trade_start >= 0 and quick_trade_end > quick_trade_start, "Server quick trade NPC list should be present")
    quick_trade_body = entity_handler[quick_trade_start:quick_trade_end]
    require("NpcId.RANGERS_GUILD_DRAGON_VENDOR.id()" in quick_trade_body, "Aeron should be in the quick trade NPC list")

    dragon_shop = RANGERS_GUILD_DRAGON_SHOP.read_text(encoding="utf-8")
    for fragment in (
        "n.getID() == NpcId.RANGERS_GUILD_DRAGON_VENDOR.id()",
        "new Shop(false, 60000, 100, 55, 3,",
        "new Item(ItemId.DRAGON_LONGBOW.id(), 1)",
        "new Item(ItemId.DRAGON_CROSSBOW.id(), 1)",
        "Welcome to the Rangers Guild specialist shop",
    ):
        require(fragment in dragon_shop, f"Rangers Guild dragon shop is missing: {fragment}")
    for fragment in (
        "ItemId.DRAGON_ARROWS",
        "ItemId.POISON_DRAGON_ARROWS",
        "ItemId.DRAGON_BOLTS",
        "ItemId.POISON_DRAGON_BOLTS",
    ):
        require(fragment not in dragon_shop, f"Rangers Guild dragon shop should not sell points-only ammo: {fragment}")

    client = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    for fragment in (
        "setCustomNpcDefinition(841, new NPCDef(",
        '"Aeron", "He sells specialist ranged gear", Config.S_RIGHT_CLICK_TRADE ? "Trade" : ""',
        "null, 0, 0, 3, 0, false,",
        "new int[]{0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, 66}",
        "16761440, 8409120, 2, 15523536",
    ):
        require(fragment in client, f"Client Aeron definition is missing: {fragment}")


def ensure_rangers_guild_points_vendor():
    expected_vendor_loc = (842, 496, 1414, 495, 1413, 497, 1414)
    myworld_locs = {npc_location_tuple(loc) for loc in load_npcs(MYWORLD_NPC_LOCS)}
    require(expected_vendor_loc in myworld_locs, "Talia should stand by the upstairs Rangers Guild rewards counter")

    npc_id = NPC_ID.read_text(encoding="utf-8")
    require("RANGERS_GUILD_POINTS_VENDOR(842)" in npc_id, "NpcId should reserve id 842 for Talia")

    vendor = npc_def_by_id(NPC_DEFS_CUSTOM, 842)
    require(vendor is not None, "Custom NPC defs should define Talia id 842")
    require(vendor["name"] == "Talia", "Talia should have the expected display name")
    require(vendor["command"] == "Redeem" and vendor["command2"] == "", "Talia should expose Redeem, not a normal shop command")
    require(vendor["sprites12"] == 66, "Talia should wear a green cape")
    require(vendor["sprites1"] == 3 and vendor["sprites2"] == 4, "Talia should use the female body sprites")
    require(vendor["topColour"] == 3211263 and vendor["bottomColour"] == 8409120, "Talia should have distinct clothing colors")

    entity_handler = SERVER_ENTITY_HANDLER.read_text(encoding="utf-8")
    quick_trade_start = entity_handler.find("private int[] quickTradeNpcs = new int[] {")
    quick_trade_end = entity_handler.find("\n\t};", quick_trade_start)
    require(quick_trade_start >= 0 and quick_trade_end > quick_trade_start, "Server quick trade NPC list should be present")
    quick_trade_body = entity_handler[quick_trade_start:quick_trade_end]
    require(
        "NpcId.RANGERS_GUILD_POINTS_VENDOR.id()" not in quick_trade_body,
        "Talia should not be in the quick trade list because it would relabel Redeem as Trade",
    )

    points_vendor = RANGERS_GUILD_POINTS_VENDOR.read_text(encoding="utf-8")
    for fragment in (
        "npc.getID() == NpcId.RANGERS_GUILD_POINTS_VENDOR.id()",
        'command.equalsIgnoreCase("Redeem")',
        "ProductionSession.TYPE_RANGERS_REDEMPTION_CATEGORY",
        "ProductionSession.TYPE_RANGERS_REDEMPTION",
        "ActionSender.showProductionInterface(player, session)",
        "RangersGuildPointsVendor::beginRedemptionFromInterface",
        "RangersGuildPoints.getPoints(player)",
        "RangersGuildPoints.spendPoints(player, (int) totalCost)",
        "player.getCarriedItems().getInventory().canHold(item)",
        "RangersGuildPoints.addPoints(player, (int) totalCost)",
        'new Category("Longbows", ItemId.LONGBOW',
        'new Category("Shortbows", ItemId.SHORTBOW',
        'new Category("Crossbows", ItemId.CROSSBOW',
        'new Category("Throwing Knives", ItemId.BRONZE_THROWING_KNIFE',
        'new Category("Darts", ItemId.BRONZE_THROWING_DART',
        'new Category("Arrows", ItemId.BRONZE_ARROWS',
        'new Category("Bolts", ItemId.BRONZE_BOLTS',
        'new Category("Shuriken", ItemId.BRONZE_SHURIKEN',
        "ItemId.MAGIC_SHORTBOW",
        "ItemId.RUNE_ARROWS",
        "ItemId.RUNE_BOLTS",
        "ItemId.ORICHALCUM_SHURIKEN",
        "new Reward(ItemId.BLOOD_LONGBOW, 4000)",
        "new Reward(ItemId.BLOOD_SHORTBOW, 3500)",
        "new Reward(ItemId.BLOOD_CROSSBOW, 4250)",
        "new Reward(ItemId.COPPER_THROWING_KNIFE, 2)",
        "new Reward(ItemId.MITHRIL_THROWING_DART, 6)",
        "new Reward(ItemId.TITAN_STEEL_ARROWS, 8)",
        "new Reward(ItemId.ADAMANTITE_BOLTS, 10)",
        "new Reward(ItemId.DRAGON_ARROWS, 30)",
        "new Reward(ItemId.POISON_DRAGON_ARROWS, 30)",
        "new Reward(ItemId.DRAGON_BOLTS, 30)",
        "new Reward(ItemId.POISON_DRAGON_BOLTS, 30)",
        "new Reward(ItemId.ORICHALCUM_SHURIKEN, 14)",
        "new Reward(ItemId.RUNE_SHURIKEN, 20)",
        "Rangers Guild points come from ranged experience in the basement",
    ):
        require(fragment in points_vendor, f"Rangers Guild points vendor is missing: {fragment}")
    require("ItemId.DRAGON_LONGBOW" not in points_vendor, "Rangers Guild points vendor should not sell dragon longbows")
    require("ItemId.DRAGON_CROSSBOW" not in points_vendor, "Rangers Guild points vendor should not sell dragon crossbows")

    client = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    for fragment in (
        "setCustomNpcDefinition(842, new NPCDef(",
        '"Talia", "She handles Rangers Guild rewards", Config.S_RIGHT_CLICK_TRADE ? "Redeem" : ""',
        "new int[]{3, 4, 2, -1, -1, -1, -1, -1, -1, -1, -1, 66}",
        "16753488, 3211263, 8409120, 15523536",
    ):
        require(fragment in client, f"Client Talia definition is missing: {fragment}")

    mudclient = CLIENT_MUDCLIENT.read_text(encoding="utf-8")
    require('normalizedLabel.equals("redeem")' in mudclient, "Ctrl-click NPC shortcut should recognize Redeem")


def ensure_rangers_guild_points_system():
    area = RANGERS_GUILD_AREA.read_text(encoding="utf-8")
    for fragment in (
        "legacyPackedBounds(",
        "484, 3281, 515, 3310",
        "BASEMENT.contains(location)",
        "ENTRANCE_DOOR.equals(location)",
    ):
        require(fragment in area, f"Rangers Guild layered area is missing: {fragment}")

    points = RANGERS_GUILD_POINTS.read_text(encoding="utf-8")
    for fragment in (
        'POINTS_CACHE_KEY = "rangers_guild_points"',
        'REMAINDER_CACHE_KEY = "rangers_guild_point_remainder"',
        "XP_PER_POINT = 10",
        "RangersGuildArea.containsBasement(player.getWorldLocation())",
        "skill != Skill.RANGED.id()",
        "total / XP_PER_POINT",
        "total % XP_PER_POINT",
        "player.getCache().set(REMAINDER_CACHE_KEY, newRemainder)",
    ):
        require(fragment in points, f"Rangers Guild points system is missing: {fragment}")

    skills = SKILLS.read_text(encoding="utf-8")
    for fragment in (
        "import com.openrsc.server.content.RangersGuildPoints;",
        "int creditedExperience = Math.max(0, exps[skill] - oldExp);",
        "RangersGuildPoints.awardFromExperience((Player) getMob(), skill, creditedExperience);",
    ):
        require(fragment in skills, f"Skills XP hook is missing: {fragment}")


def main():
    ensure_basement_terrain()
    ensure_scenery_layout()
    ensure_stair_telepoints()
    ensure_basement_npcs()
    ensure_rangers_guild_entrance()
    ensure_ranged_master_and_archery_shopkeeper()
    ensure_rangers_guild_dragon_vendor()
    ensure_rangers_guild_points_vendor()
    ensure_rangers_guild_points_system()
    print("PASS: Rangers Guild first-pass layout validated")


if __name__ == "__main__":
    main()
