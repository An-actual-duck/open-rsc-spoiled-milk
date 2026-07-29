#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def extract(text: str, start: str, end: str) -> str:
    start_index = text.find(start)
    end_index = text.find(end, start_index)
    if start_index < 0 or end_index < 0:
        fail(f"could not extract {start!r} through {end!r}")
    return text[start_index:end_index]


def apply_transition(client_items, *, hard_area_load, terrain_reloaded, server_announcements):
    """Model the client/server contract exercised by loadNextRegion."""
    retained = [] if hard_area_load else list(client_items)
    if terrain_reloaded and not hard_area_load:
        retained = [item for item in retained if item[1] == "loaded"]
    retained.extend(server_announcements)
    return retained


def verify_transition_model() -> None:
    authored = (14, "loaded", 1)
    player_drop = (14, "loaded", 2)

    # A same-region teleport resets the server's known set and re-announces both
    # real entities. The client must reset even though terrain is not reloaded.
    result = apply_transition(
        [authored, player_drop],
        hard_area_load=True,
        terrain_reloaded=False,
        server_announcements=[authored, player_drop],
    )
    require(result == [authored, player_drop],
            "same-region hard reset must not duplicate re-announced items")

    # Ordinary movement does not reset the server set, so existing items stay.
    result = apply_transition(
        [authored, player_drop],
        hard_area_load=False,
        terrain_reloaded=False,
        server_announcements=[],
    )
    require(result == [authored, player_drop],
            "ordinary movement must retain ground items")

    # Separate legitimate entities sharing an id and tile are not deduplicated.
    require(len(result) == 2 and result[0][0:2] == result[1][0:2],
            "legitimate same-tile items must remain independent")


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    region_load = extract(client, "public final boolean loadNextRegion", "private void loadSounds()")

    same_region_guard = """if (!hardAreaLoad
\t\t\t\t\t&& this.hasCompletedInitialRegionLoad
\t\t\t\t\t&& this.lastHeightOffset == this.requestedPlane"""
    require(same_region_guard in region_load,
            "hard area loads must bypass the same-region early return and reach the unified reset")
    require("if (hardAreaLoad || heightOffsetChanged) {\n\t\t\t\t\t\tthis.resetGroundItemsForHardAreaLoad();" in region_load,
            "terrain/plane hard loads must use the same ground-item reset")
    require("private void resetGroundItemsForHardAreaLoad() {\n\t\tthis.groundItemCount = 0;" in client,
            "hard area reset must clear the client ground-item list")
    require("boolean removed = false;" in packet_handler,
            "packet removals must still remove only one real ground-item instance")
    require("removeGroundItemsAt(groundItemX, groundItemY, groundItemID);\n\t\t\t\t\tmc.setGroundItemX" not in packet_handler,
            "fix must not deduplicate legitimate same-tile adds")

    verify_transition_model()
    print("PASS: hard area resets reconcile ground items without collapsing legitimate drops")


if __name__ == "__main__":
    main()
