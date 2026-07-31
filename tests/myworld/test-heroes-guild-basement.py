#!/usr/bin/env python3
import json
import struct
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_LANDSCAPE = ROOT / "server/conf/server/data/Custom_Landscape.orsc"
CLIENT_LANDSCAPE = ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc"
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocs.json"
NPC_LOCS = ROOT / "server/conf/server/defs/locs/NpcLocs.json"
MYWORLD_NPC_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldNpcLocs.json"
MYWORLD_NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefsMyWorld.json"
LADDERS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/defaults/Ladders.java"
SECTOR = "h3x55y46"
CAVERN_ROWS = {
    3276: (356, 376),
    3277: (352, 377),
    3278: (350, 378),
    3279: (349, 379),
    3280: (348, 380),
    3281: (347, 380),
    3282: (346, 381),
    3283: (345, 381),
    3284: (344, 382),
    3285: (343, 382),
    3286: (342, 381),
    3287: (341, 381),
    3288: (341, 380),
    3289: (342, 380),
    3290: (342, 379),
    3291: (343, 379),
    3292: (344, 378),
    3293: (345, 378),
    3294: (347, 377),
    3295: (350, 375),
}
ORIGINAL_ORE_ROWS = {
    3276: ((356, 356), (364, 376)),
    3277: ((353, 357), (365, 376)),
    3278: ((352, 360), (365, 376)),
    3279: ((352, 376),),
    3280: ((353, 375),),
    3281: ((354, 374),),
    3282: ((357, 367),),
    3283: ((359, 366),),
}
ORIGINAL_ELEVATION_SAMPLES = {
    (356, 3276): 54,
    (358, 3278): 40,
    (359, 3281): 10,
    (360, 3282): 6,
    (366, 3283): 48,
}
DIAGONAL_BOUNDARY_WALLS = {
    (351, 3277): 12001,
    (349, 3278): 12001,
    (347, 3280): 12001,
    (345, 3282): 12001,
    (343, 3284): 12001,
    (340, 3287): 12001,
    (341, 3290): 1,
    (343, 3292): 1,
    (346, 3294): 1,
}
DIAGONAL_CLEAR_HORIZONTAL = {
    (x + 1, y)
    for (x, y), diagonal_wall in DIAGONAL_BOUNDARY_WALLS.items()
}
DIAGONAL_CLEAR_VERTICAL = {
    (x, y + 1) if diagonal_wall < 12000 else (x, y)
    for (x, y), diagonal_wall in DIAGONAL_BOUNDARY_WALLS.items()
}
REMOVED_CAVERN_RESOURCE_IDS = {106, 108, 110, 210}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read_sector(path):
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, f"{path} must be a valid landscape archive")
        return archive.read(SECTOR)


def tile(sector, x, y):
    offset = ((x - 336) * 48 + (y - 3264)) * 10
    return struct.unpack_from(">BBBBBBI", sector, offset)


server_sector = read_sector(SERVER_LANDSCAPE)
client_sector = read_sector(CLIENT_LANDSCAPE)
require(client_sector == server_sector, "Client and server Heroes' Guild terrain must match")

for x in range(365, 377):
    for y in range(3264, 3276):
        elevation, texture, overlay, roof, _, _, diagonal = tile(server_sector, x, y)
        require(
            (elevation, texture, overlay, roof, diagonal) == (128, 70, 3, 0, 0),
            f"Heroes' Guild chamber tile {x},{y} has unexpected terrain",
        )

for y in range(3264, 3276):
    require(tile(server_sector, 365, y)[4] == 1, "West chamber wall is incomplete")
    require(tile(server_sector, 377, y)[4] == 1, "East chamber wall is incomplete")

for x in range(365, 377):
    require(tile(server_sector, x, 3264)[5] == 1, "North chamber wall is incomplete")

for y in range(3264, 3277):
    _, _, overlay, roof, horizontal_wall, vertical_wall, diagonal = tile(
        server_sector, 377, y
    )
    require((overlay, roof, vertical_wall, diagonal) == (8, 0, 0, 0),
            f"East void column contains terrain at 377,{y}")
    require(
        horizontal_wall == 1,
        f"East cage wall is incorrect at 377,{y}",
    )

for y in (3266, 3267, 3272, 3273):
    require(tile(server_sector, 369, y)[4] == 0, f"West gate gap is blocked at y={y}")
    require(tile(server_sector, 373, y)[4] == 0, f"East gate gap is blocked at y={y}")

for y in (3264, 3265, 3268, 3269, 3270, 3271, 3274, 3275):
    require(tile(server_sector, 369, y)[4] == 6, f"West aisle railing is missing at y={y}")

for y in (3264, 3265, 3268, 3269, 3270, 3271, 3274, 3275):
    require(tile(server_sector, 373, y)[4] == 6, f"East aisle railing is missing at y={y}")

for y in (3276,):
    require(tile(server_sector, 369, y)[4] == 0, f"West railing remains at 369,{y}")

for x in (*range(365, 369), *range(373, 377)):
    require(
        tile(server_sector, x, 3270)[5] == 6,
        f"Cage divider railing is missing at {x},3270",
    )
for x in range(370, 373):
    require(
        tile(server_sector, x, 3270)[5] == 0,
        f"Central aisle is blocked at {x},3270",
    )

cavern_tiles = {
    (x, y)
    for y, (minimum_x, maximum_x) in CAVERN_ROWS.items()
    for x in range(minimum_x, maximum_x + 1)
}
chamber_tiles = {
    (x, y)
    for x in range(365, 377)
    for y in range(3264, 3276)
}
occupied_tiles = cavern_tiles | chamber_tiles
original_ore_tiles = {
    (x, y)
    for y, spans in ORIGINAL_ORE_ROWS.items()
    for minimum_x, maximum_x in spans
    for x in range(minimum_x, maximum_x + 1)
}
expanded_only_tiles = cavern_tiles - original_ore_tiles

for x, y in cavern_tiles:
    elevation, texture, overlay, roof, horizontal_wall, vertical_wall, diagonal = tile(
        server_sector, x, y
    )
    require(
        texture == 176 and overlay == 0 and roof == 0 and diagonal == 0,
        f"Cavern floor is missing at {x},{y}",
    )
    require(
        horizontal_wall
        == (1 if (x - 1, y) not in occupied_tiles and (x, y) not in DIAGONAL_CLEAR_HORIZONTAL else 0),
        f"Cavern west wall is incorrect at {x},{y}",
    )
    expected_north_wall = 1 if (x, y - 1) not in occupied_tiles else 0
    if y == 3276 and x in (*range(365, 369), *range(373, 377)):
        expected_north_wall = 6
    if (x, y) in DIAGONAL_CLEAR_VERTICAL:
        expected_north_wall = 0
    require(
        vertical_wall == expected_north_wall,
        f"Cavern north wall is incorrect at {x},{y}",
    )

for point, expected_elevation in ORIGINAL_ELEVATION_SAMPLES.items():
    require(
        tile(server_sector, *point)[0] == expected_elevation,
        f"Original ore elevation changed at {point[0]},{point[1]}",
    )

expanded_elevations = [tile(server_sector, x, y)[0] for x, y in expanded_only_tiles]
require(min(expanded_elevations) < 40, "Expanded cavern never dips like the original mine")
require(max(expanded_elevations) > 100, "Expanded cavern never rises toward its edges")
require(
    sum(1 for elevation in expanded_elevations if elevation == 128) < len(expanded_elevations) // 10,
    "Expanded cavern still has a visible flat 128-elevation plateau",
)

for x, y in cavern_tiles:
    if (x + 1, y) not in occupied_tiles:
        require(
            tile(server_sector, x + 1, y)[4]
            == (0 if (x + 1, y) in DIAGONAL_CLEAR_HORIZONTAL else 1),
            f"Cavern east wall is missing beside {x},{y}",
        )
    if (x, y + 1) not in occupied_tiles:
        require(
            tile(server_sector, x, y + 1)[5]
            == (0 if (x, y + 1) in DIAGONAL_CLEAR_VERTICAL else 1),
            f"Cavern south wall is missing below {x},{y}",
        )

for (x, y), diagonal_wall in DIAGONAL_BOUNDARY_WALLS.items():
    require(
        tile(server_sector, x, y)[6] == diagonal_wall,
        f"Diagonal cavern wall is missing at {x},{y}",
    )

require(tile(server_sector, 369, 3275)[4] == 6, "Greater Demon corner fence is missing")
require(tile(server_sector, 369, 3276)[5] == 0, "Greater Demon south fence protrudes")
require(tile(server_sector, 369, 3269)[4] == 6, "Moss Giant corner fence is missing")
require(tile(server_sector, 369, 3270)[5] == 0, "Moss Giant divider fence protrudes")
require(tile(server_sector, 377, 3276)[4] == 1, "Dragon-side wall has a south gap")

for x in (354, 355):
    _, _, overlay, roof, horizontal_wall, vertical_wall, diagonal = tile(
        server_sector, x, 3276
    )
    require(
        (overlay, roof, horizontal_wall, vertical_wall, diagonal) == (8, 0, 0, 0, 0),
        f"Out-of-bounds floor remains at {x},3276",
    )

sceneries = json.loads(SCENERY_LOCS.read_text())["sceneries"]
resource_sceneries = [
    entry
    for entry in sceneries
    if entry["id"] in REMOVED_CAVERN_RESOURCE_IDS
    and (entry["pos"]["X"], entry["pos"]["Y"]) in cavern_tiles
]
require(not resource_sceneries, "Cavern resource scenery should be removed before repopulation")

room_sceneries = {
    (entry["id"], entry["pos"]["X"], entry["pos"]["Y"], entry["direction"])
    for entry in sceneries
    if 365 <= entry["pos"]["X"] <= 377 and 3264 <= entry["pos"]["Y"] <= 3276
}
require((41, 370, 3264, 4) in room_sceneries, "Central north-wall stairs are missing")
require(
    {(57, 369, 3266, 0), (57, 373, 3266, 0), (57, 369, 3272, 0), (57, 373, 3272, 0)}
    <= room_sceneries,
    "One or more aisle-facing cage gates are missing",
)
require((41, 368, 3270, 4) not in room_sceneries, "Old staircase placement remains")
require((57, 374, 3276, 6) not in room_sceneries, "Old dragon gate placement remains")

npcs = json.loads(NPC_LOCS.read_text())["npclocs"]
require(
    any(
        entry["id"] == 202
        and entry["start"] == {"X": 375, "Y": 3272}
        and entry["min"] == {"X": 374, "Y": 3271}
        and entry["max"] == {"X": 376, "Y": 3275}
        for entry in npcs
    ),
    "The existing Heroes' Guild blue dragon must remain in the lower-right cage",
)

myworld_npcs = json.loads(MYWORLD_NPC_LOCS.read_text())["npclocs"]


def cage_spawns(npc_id, minimum, maximum):
    return [
        entry
        for entry in myworld_npcs
        if entry["id"] == npc_id
        and entry["min"] == minimum
        and entry["max"] == maximum
    ]


require(
    len(cage_spawns(104, {"X": 366, "Y": 3265}, {"X": 368, "Y": 3269})) == 3,
    "Upper-left cage must contain three moss giants",
)
require(
    len(cage_spawns(294, {"X": 374, "Y": 3265}, {"X": 376, "Y": 3269})) == 1,
    "Upper-right cage must contain one hellhound",
)
require(
    len(cage_spawns(184, {"X": 366, "Y": 3271}, {"X": 368, "Y": 3275})) == 2,
    "Lower-left cage must contain two greater demons",
)

green_dragon = next(
    entry
    for entry in json.loads(MYWORLD_NPC_DEFS.read_text())["npcs"]
    if entry["id"] == 196
)
require(green_dragon.get("name") == "Green Dragon", "Earth Dragon must be renamed")
require(
    green_dragon.get("description") == "A powerful and ancient green dragon",
    "Green Dragon description must match its name",
)

ladders = LADDERS.read_text()
require(
    "obj.getID() == 42 && obj.getX() == 368 && obj.getY() == 438" in ladders
    and "player.teleport(371, 3266, false);" in ladders,
    "Surface Heroes' Guild stairs must land by the expanded basement stairs",
)
require(
    "obj.getX() == 370 && obj.getY() == 3264" in ladders
    and "player.teleport(369, 437, false);" in ladders,
    "Expanded basement stairs must return beside the surface stairs without trapping the player",
)

print("Heroes' Guild basement layout checks passed")
