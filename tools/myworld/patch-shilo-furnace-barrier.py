#!/usr/bin/env python3
"""Remove the obsolete Yohnus furnace barrier from both landscape archives."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import struct
import zipfile
from pathlib import Path


SECTOR = "h0x56y54"
SECTOR_SIZE = 48
TILE_SIZE = 10
WORLD_X = 400
WORLD_Y = 845
SECTOR_ORIGIN_X = 384
SECTOR_ORIGIN_Y = 816
EXPECTED_SOURCE_HASH = "960f4a77f8399da01d52c6b4ac187721108f86503ff71dad1d724b979f818fcb"
EXPECTED_PATCHED_HASH = "011c5443c89887a1c00aaf49fd8e913559a03b8e4f35656902eb621969c99b87"
EXPECTED_VERTICAL_WALL = 166
TARGETS = (
    Path("server/conf/server/data/Custom_Landscape.orsc"),
    Path("Client_Base/Cache/video/Custom_Landscape.orsc"),
)


def load_byte_preserving_rewriter():
    helper_path = Path(__file__).with_name("patch-cosmic-altar-path.py")
    spec = importlib.util.spec_from_file_location("landscape_patch_helper", helper_path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"Unable to load landscape writer from {helper_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.rewrite_archive


def tile_offset() -> int:
    local_x = WORLD_X - SECTOR_ORIGIN_X
    local_y = WORLD_Y - SECTOR_ORIGIN_Y
    return (local_x * SECTOR_SIZE + local_y) * TILE_SIZE


def patched_sector(source: bytes) -> bytes:
    actual_hash = hashlib.sha256(source).hexdigest()
    if actual_hash == EXPECTED_PATCHED_HASH:
        return source
    if actual_hash != EXPECTED_SOURCE_HASH:
        raise SystemExit(f"{SECTOR}: unsupported source hash {actual_hash}")
    offset = tile_offset()
    values = list(struct.unpack_from(">BBBBBBI", source, offset))
    if values[5] != EXPECTED_VERTICAL_WALL:
        raise SystemExit(
            f"{SECTOR} {WORLD_X},{WORLD_Y}: expected vertical wall "
            f"{EXPECTED_VERTICAL_WALL}, found {values[5]}"
        )
    values[5] = 0
    result = bytearray(source)
    struct.pack_into(">BBBBBBI", result, offset, *values)
    return bytes(result)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    sectors = []
    for target in TARGETS:
        with zipfile.ZipFile(target) as archive:
            sectors.append(archive.read(SECTOR))
    if sectors[0] != sectors[1]:
        raise SystemExit("Client/server source landscape sectors differ")

    replacement = patched_sector(sectors[0])
    rewrite_archive = load_byte_preserving_rewriter()
    for target in TARGETS:
        rewrite_archive(target, {SECTOR: replacement}, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
