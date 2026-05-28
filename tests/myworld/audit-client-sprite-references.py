#!/usr/bin/env python3
import gzip
import re
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CUSTOM_SPRITES = ROOT / "Client_Base/Cache/video/Custom_Sprites.osar"
SUPPORTED_TYPES_WITH_LAYER = {1, 2, 3}


def read_null_string(data: bytes, pos: int) -> tuple[str, int]:
    start = pos
    while pos < len(data) and data[pos] != 0:
        pos += 1
    if pos >= len(data):
        raise ValueError("Unterminated string while reading Custom_Sprites.osar")
    return data[start:pos].decode("latin1"), pos + 1


def load_custom_sprite_index(path: Path) -> dict[str, set[str]]:
    data = gzip.decompress(path.read_bytes())
    pos = 0
    subspace_count = data[pos]
    pos += 1
    subspaces: dict[str, set[str]] = {}

    for _ in range(subspace_count):
        subspace_name, pos = read_null_string(data, pos)
        entry_count = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        entries: set[str] = set()
        for _ in range(entry_count):
            entry_id, pos = read_null_string(data, pos)
            entry_type = data[pos]
            pos += 1
            if entry_type in SUPPORTED_TYPES_WITH_LAYER:
                pos += 1
            frame_count = data[pos]
            pos += 1
            color_table_size = data[pos] + 1
            pos += 1 + color_table_size * 3
            for _ in range(frame_count):
                width, height = struct.unpack_from(">HH", data, pos)
                pos += 4
                pos += 1  # use shift
                pos += 8  # offsets and bounds
                pos += width * height
            entries.add(entry_id)
        subspaces[subspace_name] = entries

    if pos != len(data):
        raise ValueError(f"Custom sprite archive parse ended at {pos}, expected {len(data)}")
    return subspaces


def parse_sprite_refs(source: str) -> set[tuple[str, str]]:
    refs: set[tuple[str, str]] = set()
    for match in re.finditer(r'"([A-Za-z_\-]+):([^"\\]+)"', source):
        category = match.group(1)
        entry_id = match.group(2)
        if category == "external-png":
            continue
        refs.add((category, entry_id))
    return refs


def main() -> int:
    source = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    refs = sorted(parse_sprite_refs(source))
    sprite_index = load_custom_sprite_index(CUSTOM_SPRITES)
    failures: list[str] = []

    for category, entry_id in refs:
        if category not in sprite_index:
            failures.append(f"Missing custom sprite subspace {category!r} for reference {category}:{entry_id}")
            continue
        if entry_id not in sprite_index[category]:
            failures.append(f"Missing custom sprite entry {category}:{entry_id}")

    print(f"Custom sprite subspaces: {len(sprite_index)}")
    print(f"Client sprite references checked: {len(refs)}")

    if failures:
        print("\nFAIL:")
        for failure in failures[:120]:
            print(failure)
        if len(failures) > 120:
            print(f"... and {len(failures) - 120} more")
        return 1

    print("\nPASS: every client sprite reference resolves in Custom_Sprites.osar")
    return 0


if __name__ == "__main__":
    sys.exit(main())
