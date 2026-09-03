#!/usr/bin/env python3
"""Validate the Mining Guild elite gate terrain, boundary, and dialogue wiring."""

import json
import struct
from pathlib import Path

from installed_world_package import read_legacy_sector


ROOT = Path(__file__).resolve().parents[2]
BOUNDARY_LOCS = ROOT / "server/conf/server/defs/locs/BoundaryLocsCustomQuest.json"
DOOR_ACTION = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/mining-guild-and-smithing-expansion-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def sector_tile(side: str, sector: str, tile_id: int) -> tuple[int, int, int, int, int, int, int]:
    data = read_legacy_sector(side, sector, level=-1)
    return struct.unpack_from(">BBBBBBI", data, tile_id * 10)


def main() -> None:
    server_tile = sector_tile("server", "h3x53y48", 1385)
    client_tile = sector_tile("client", "h3x53y48", 1385)
    expected_tile = (60, 180, 0, 0, 0, 0, 0)
    require(
        server_tile == expected_tile,
        f"Server elite gate tile 1385 should keep the protruding landscape wall clear: expected {expected_tile}, got {server_tile}",
    )
    require(
        client_tile == expected_tile,
        f"Client elite gate tile 1385 should keep the protruding landscape wall clear: expected {expected_tile}, got {client_tile}",
    )

    boundaries = json.loads(BOUNDARY_LOCS.read_text(encoding="utf-8"))["boundaries"]
    require(
        any(
            boundary["id"] == 43
            and boundary["pos"] == {"X": 268, "Y": 3401}
            and boundary["direction"] == 0
            for boundary in boundaries
        ),
        "Elite Mining Guild gate should be registered as a clickable boundary at 268,3401",
    )

    door_action = DOOR_ACTION.read_text(encoding="utf-8")
    require("MINING_GUILD_ELITE_MINING_LEVEL = 80" in door_action, "Elite gate should require 80 Mining")
    require("NpcId.NURMOF.id()" in door_action, "Elite gate should use Nurmof for the warning dialogue")
    require('"Woah, hold your horses."' in door_action, "Elite gate should include Nurmof warning line 1")
    require('"You\'re a part of the mining guild, sure."' in door_action, "Elite gate should include Nurmof warning line 2")
    require('"But that area is still off limits except for the ultra elite miners."' in door_action, "Elite gate should include Nurmof warning line 3")
    require('"It\'s dangerous down there."' in door_action, "Elite gate should include Nurmof warning line 4")
    require('"You need level 80 Mining to proceed further."' in door_action, "Elite gate should include the system-level requirement message")
    require("doDoor(obj, player);" in door_action, "Elite gate should use normal door open/close behavior when allowed")

    plan = PLAN.read_text(encoding="utf-8")
    require("- [x] Confirm final Mining gate level." in plan, "Plan should mark Mining gate level confirmed")
    require("- [x] Add Mining Guild level-gate interaction." in plan, "Plan should mark level-gate interaction complete")

    print("PASS: Mining Guild elite gate wiring validated")


if __name__ == "__main__":
    main()
