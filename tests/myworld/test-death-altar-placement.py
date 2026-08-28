#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNECRAFT_LOCS = (
    ROOT / "server/conf/server/defs/locs/SceneryLocsRunecraft.json"
)
MYWORLD_LOCS = (
    ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
)
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
EXIT_PORTAL = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/misc/ExitPortal.java"
)

DEATH_ALTAR_ID = 1211
DEATH_PORTAL_ID = 1224
DEATH_OBELISK_ID = 1304
EXPECTED_ALTAR = (392, 3540)
EXPECTED_OBELISKS = {
    (390, 3543),
    (395, 3543),
    (395, 3538),
    (390, 3538),
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def locations(path: Path, object_id: int) -> set[tuple[int, int]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {
        (entry["pos"]["X"], entry["pos"]["Y"])
        for entry in data["sceneries"]
        if entry["id"] == object_id
    }


def main() -> None:
    altar_locations = locations(RUNECRAFT_LOCS, DEATH_ALTAR_ID)
    if altar_locations != {EXPECTED_ALTAR}:
        fail(f"Death Altar locations were {sorted(altar_locations)}")

    portal_locations = locations(RUNECRAFT_LOCS, DEATH_PORTAL_ID)
    if portal_locations:
        fail(f"Legacy Death Altar portals remain at {sorted(portal_locations)}")

    obelisk_locations = locations(MYWORLD_LOCS, DEATH_OBELISK_ID)
    if obelisk_locations != EXPECTED_OBELISKS:
        fail(f"Death obelisks were {sorted(obelisk_locations)}")

    client = CLIENT.read_text(encoding="utf-8")
    if "altarAnchor(392, 3540)" not in client:
        fail("Client Death Altar visual anchor is not at 392,3540")
    if (
        "{altarAnchor(390, 3543), altarAnchor(395, 3543), "
        "altarAnchor(395, 3538), altarAnchor(390, 3538)}"
        not in client
    ):
        fail("Client Death obelisk visual anchors do not match server locations")
    for obsolete in (
        "{151, 212}", "{149, 215}", "{154, 215}", "{154, 210}", "{150, 210}",
        "{421, 3546}", "{419, 3549}", "{424, 3549}", "{424, 3544}", "{419, 3544}",
    ):
        if obsolete in client:
            fail(f"Client still contains obsolete Death Altar coordinate {obsolete}")

    exit_portal = EXIT_PORTAL.read_text(encoding="utf-8")
    if "case 1224" in exit_portal or "teleport(151, 212" in exit_portal:
        fail("Legacy Death Altar return portal behavior remains enabled")

    print("PASS: Death Altar is overworld-only at 392,3540")


if __name__ == "__main__":
    main()
