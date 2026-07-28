#!/usr/bin/env python3
"""Guards the legacy static-scene delta envelope used by atomic activation."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = (
    ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
).read_text(encoding="utf-8")
CLIENT = (
    ROOT / "Client_Base/src/orsc/SceneBaselineState.java"
).read_text(encoding="utf-8")


def require(text: str, fragment: str, message: str) -> None:
    if fragment not in text:
        raise AssertionError(message)


require(
    SERVER,
    "private boolean isSceneDeltaSafeOffset(",
    "server scene ownership has no explicit wire-safe envelope",
)
require(
    SERVER,
    "return offsetX > Byte.MIN_VALUE && offsetX < Byte.MAX_VALUE\n"
    "\t\t\t&& offsetY > Byte.MIN_VALUE && offsetY < Byte.MAX_VALUE;",
    "server scene envelope lost its one-tile signed-byte removal margin",
)
if SERVER.count("|| !isSceneDeltaSafeOffset(offsetX, offsetY)") != 3:
    raise AssertionError(
        "scenery, wall, and ground-item retirement must share the safe envelope"
    )
if SERVER.count("if (!isSceneDeltaSafeOffset(offsetX, offsetY))") != 3:
    raise AssertionError(
        "scenery, wall, and ground-item admission must share the safe envelope"
    )
require(
    SERVER,
    "if (!objectLocs.isEmpty()) {",
    "silent local-set correction must not emit an empty scenery packet",
)
require(
    SERVER,
    "if (!itemLocs.isEmpty()) {",
    "silent local-set correction must not emit an empty ground-item packet",
)
require(
    CLIENT,
    "if (offsetX <= Byte.MIN_VALUE\n"
    "\t\t\t|| offsetX >= Byte.MAX_VALUE\n"
    "\t\t\t|| offsetY <= Byte.MIN_VALUE\n"
    "\t\t\t|| offsetY >= Byte.MAX_VALUE) {",
    "client fence pruning does not mirror the server wire-safe envelope",
)


def safe(offset: int) -> bool:
    return -128 < offset < 127


def encodable(offset: int) -> bool:
    return -128 <= offset <= 127


# One-tile ordinary movement cannot jump from the owned range beyond the
# signed-byte removal range. The first rejected coordinate is still encodable.
for offset in range(-127, 127):
    assert safe(offset)
assert not safe(-128) and encodable(-128)
assert not safe(127) and encodable(127)

# The two exact stale records reconstructed from the captured sequence-12 hash
# were still inside the configured 16-grid radius but outside the packet wire
# envelope at player (128,625).
player_x, player_y = 128, 625
for object_x, object_y in ((235, 759), (247, 758)):
    assert abs((object_x >> 3) - (player_x >> 3)) <= 16
    assert abs((object_y >> 3) - (player_y >> 3)) <= 16
    assert not (
        safe(object_x - player_x) and safe(object_y - player_y)
    )

print("PASS: static-scene wire envelope and captured fringe regression are guarded")
