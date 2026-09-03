#!/usr/bin/env python3
import json
import re
import struct
from pathlib import Path

from installed_world_package import read_legacy_sector


ROOT = Path(__file__).resolve().parents[2]
SERVER_TILE_DEFS = ROOT / "server/conf/server/defs/TileDef.xml"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
RUNECRAFT_SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocsRunecraft.json"
MYWORLD_SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
SECTORS = ("h3x50y51", "h3x50y52", "h3x51y51", "h3x51y52")
INVISIBLE_OVERLAY = 26
INVISIBLE_TILE_ELEVATION = 86
INVISIBLE_TILE_TEXTURE = 8
COSMIC_ALTAR_ID = 1203
COSMIC_OBELISK_ID = 1300


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read_sectors(side):
    return {
        sector: read_legacy_sector(side, sector, level=10)
        for sector in SECTORS
    }


def sector_origin(sector):
    height = int(sector[1])
    section_x = int(sector[3:5])
    section_y = int(sector[6:8])
    return (section_x - 48) * 48, height * 944 + (section_y - 37) * 48


def invisible_path_tiles():
    rows = {
        3542: ((149, 150),),
        3543: ((149, 150),),
        3544: ((149, 150),),
        3545: ((149, 150),),
        3546: ((149, 150),),
        3547: ((149, 150),),
        3548: ((149, 150),),
        3549: ((149, 150),),
        3550: ((149, 150),),
        3551: ((149, 150),),
        3552: ((149, 150),),
        3553: ((149, 150),),
        3554: ((102, 107), (149, 150)),
        3555: ((102, 107), (149, 150)),
        3556: ((102, 150),),
        3557: ((102, 150),),
        3558: ((102, 107),),
        3559: ((102, 107),),
    }
    return {
        (x, y)
        for y, spans in rows.items()
        for start_x, end_x in spans
        for x in range(start_x, end_x + 1)
    }


def platform_tiles():
    return {(x, y) for x in range(102, 108) for y in range(3554, 3560)}


def ensure_transparent_walkable_tile_def():
    tile_defs = SERVER_TILE_DEFS.read_text()
    require(
        re.search(
            r"<colour>12345678</colour>\s*<unknown>5</unknown>\s*<objectType>0</objectType>",
            tile_defs,
        ),
        "Server must define a transparent, non-blocking overlay tile",
    )
    client_defs = CLIENT_ENTITY_HANDLER.read_text()
    require(
        "tiles.add(new TileDef(12345678, 5, 0));" in client_defs,
        "Client must define the same transparent, non-blocking overlay tile",
    )


def ensure_path_shape(sectors):
    expected_path = invisible_path_tiles()
    actual_path = set()

    for sector, data in sectors.items():
        origin_x, origin_y = sector_origin(sector)
        for local_x in range(48):
            for local_y in range(48):
                offset = (local_x * 48 + local_y) * 10
                elevation, texture, overlay, roof, east_wall, north_wall, diagonal_wall = struct.unpack_from(
                    ">BBBBBBI",
                    data,
                    offset,
                )
                if overlay == INVISIBLE_OVERLAY:
                    point = (origin_x + local_x, origin_y + local_y)
                    actual_path.add(point)
                    require(
                        (elevation, texture) == (INVISIBLE_TILE_ELEVATION, INVISIBLE_TILE_TEXTURE),
                        f"{point} should be level invisible path terrain",
                    )
                    require(
                        (roof, east_wall, north_wall, diagonal_wall) == (0, 0, 0, 0),
                        f"{point} should not have roof or wall blockers on the invisible path",
                    )

    require(actual_path == expected_path, f"Unexpected overlay 26 footprint: {sorted(actual_path ^ expected_path)}")

    for point in platform_tiles():
        require(point in actual_path, f"{point} should be part of the open cosmic altar platform")

    require((149, 3542) in expected_path and (150, 3542) in expected_path, "Path should start between the torches")
    require((102, 3556) in expected_path and (150, 3556) in expected_path, "Path should connect to the platform")
    require((101, 3556) not in expected_path and (108, 3558) not in expected_path, "Platform should stay within bounds")


def load_locs(path):
    return json.loads(path.read_text(encoding="utf-8"))["sceneries"]


def locs_by_id(path, object_id):
    return [loc for loc in load_locs(path) if loc["id"] == object_id]


def ensure_object_locations():
    altar_locs = [
        loc for loc in locs_by_id(RUNECRAFT_SCENERY_LOCS, COSMIC_ALTAR_ID)
        if loc["pos"]["Y"] > 3000
    ]
    require(len(altar_locs) == 1, f"Expected one high-level cosmic altar loc, found {altar_locs}")
    altar = altar_locs[0]
    require(
        (altar["pos"]["X"], altar["pos"]["Y"], altar["direction"]) == (104, 3556, 2),
        f"Cosmic altar should be centered on the new platform, found {altar}",
    )

    expected_obelisks = {(102, 3559), (107, 3559), (102, 3554), (107, 3554)}
    actual_obelisks = {
        (loc["pos"]["X"], loc["pos"]["Y"])
        for loc in locs_by_id(MYWORLD_SCENERY_LOCS, COSMIC_OBELISK_ID)
        if 3500 <= loc["pos"]["Y"] <= 3600
    }
    require(actual_obelisks == expected_obelisks, f"Cosmic obelisks should be at platform corners: {actual_obelisks}")


def main():
    ensure_transparent_walkable_tile_def()
    server_sectors = read_sectors("server")
    client_sectors = read_sectors("client")
    require(client_sectors == server_sectors, "Client and server Cosmic Altar terrain must match")
    ensure_path_shape(server_sectors)
    ensure_object_locations()
    print("PASS: cosmic altar invisible path terrain validated")


if __name__ == "__main__":
    main()
