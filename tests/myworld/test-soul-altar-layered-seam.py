#!/usr/bin/env python3

import hashlib
import struct
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
AUTHENTIC = ROOT / "server/conf/server/data/Authentic_Landscape.orsc"
SERVER_CUSTOM = ROOT / "server/conf/server/data/Custom_Landscape.orsc"
CLIENT_CUSTOM = ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc"
SECTORS = {
    "h3x60y52": (576, 720, 285, "35571858394a321c0d27ab94e8261f1decdb6662d8bb066e64f4c34aa9e6d9ba"),
    "h3x60y53": (576, 768, 185, "9cd1b174a16bb0bbfa7f014791001d73b30cb22e00485c9128de62cc2a8ad86b"),
}
EXPECTED_FIELD_CHANGES = (211, 127, 342, 0, 46, 52, 0)
EXPECTED_BOUNDS = (599, 622, 748, 775)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def archive_entries(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, f"Invalid landscape archive: {path}")
        return {name: archive.read(name) for name in SECTORS}


def layered_raw_bytes(legacy_sector: bytes) -> bytes:
    # The package format names walls by compass direction. Its two one-byte
    # wall fields intentionally reverse the old archive's historical labels.
    raw = bytearray(legacy_sector)
    for offset in range(0, len(raw), 10):
        raw[offset + 4], raw[offset + 5] = raw[offset + 5], raw[offset + 4]
    return bytes(raw)


def main() -> None:
    authentic = archive_entries(AUTHENTIC)
    server = archive_entries(SERVER_CUSTOM)
    client = archive_entries(CLIENT_CUSTOM)
    require(server == client, "Client and server Soul Altar terrain sectors differ")

    changed_points: set[tuple[int, int]] = set()
    field_changes = [0] * 7
    seam_counts = {767: 0, 768: 0}

    for name, (origin_x, origin_y, expected_changes, raw_sha) in SECTORS.items():
        source = authentic[name]
        custom = server[name]
        require(len(source) == len(custom) == 48 * 48 * 10, f"Unexpected size for {name}")
        require(
            hashlib.sha256(layered_raw_bytes(custom)).hexdigest() == raw_sha,
            f"{name} no longer generates the reviewed layered Soul Altar sector",
        )

        sector_changes = 0
        for local_x in range(48):
            for local_y in range(48):
                offset = (local_x * 48 + local_y) * 10
                before = struct.unpack_from(">BBBBBBI", source, offset)
                after = struct.unpack_from(">BBBBBBI", custom, offset)
                if before == after:
                    continue
                sector_changes += 1
                world_point = (origin_x + local_x, origin_y + local_y)
                changed_points.add(world_point)
                if world_point[1] in seam_counts:
                    seam_counts[world_point[1]] += 1
                for field, (old_value, new_value) in enumerate(zip(before, after)):
                    if old_value != new_value:
                        field_changes[field] += 1

        require(
            sector_changes == expected_changes,
            f"{name} has {sector_changes} authored changes, expected {expected_changes}",
        )

    require(len(changed_points) == 470, f"Soul Altar footprint has {len(changed_points)} changed tiles")
    bounds = (
        min(x for x, _ in changed_points),
        max(x for x, _ in changed_points),
        min(y for _, y in changed_points),
        max(y for _, y in changed_points),
    )
    require(bounds == EXPECTED_BOUNDS, f"Soul Altar footprint bounds changed: {bounds}")
    require(tuple(field_changes) == EXPECTED_FIELD_CHANGES, f"Terrain field changes differ: {field_changes}")
    require(all(count > 0 for count in seam_counts.values()), f"The Y 767/768 seam is no longer covered: {seam_counts}")

    # Center sector (12,15) owns a radius-one 3x3 window. Both storage sectors
    # and every changed tile must fit its 144x144 client application window.
    active_base_x = (12 - 1) * 48
    active_base_y = (15 - 1) * 48
    local_points = {(x - active_base_x, y - active_base_y) for x, y in changed_points}
    require(
        all(0 <= x < 144 and 0 <= y < 144 for x, y in local_points),
        "Soul Altar terrain escaped its initial native active window",
    )
    require(
        {y // 48 for _, y in changed_points} == {15, 16},
        "Soul Altar no longer spans exactly layered sectors Y15 and Y16",
    )
    require(
        (min(x for x, _ in local_points), max(x for x, _ in local_points),
         min(y for _, y in local_points), max(y for _, y in local_points))
        == (71, 94, 76, 103),
        "Soul Altar local active-window bounds changed",
    )

    print("PASS: Soul Altar terrain spans both reviewed layered sectors inside one active window")


if __name__ == "__main__":
    main()
