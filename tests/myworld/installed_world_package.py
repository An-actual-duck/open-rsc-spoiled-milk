#!/usr/bin/env python3
"""Shared accessors for the installed World Builder map used by Core tests."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIGURATION = ROOT / "server/world-builder-configs/primary.json"
LEGACY_SECTOR = re.compile(r"h([0-3])x(-?\d+)y(-?\d+)")
LEGACY_HEIGHT_TO_LEVEL = {0: 0, 1: 1, 2: 2}


def package_root(side: str) -> Path:
    if side not in {"server", "client"}:
        raise ValueError(f"Unsupported installed-package side: {side}")
    configuration = json.loads(CONFIGURATION.read_text(encoding="utf-8"))
    key = "serverMapRelativePath" if side == "server" else "clientMapRelativePath"
    relative = configuration[key]
    root = (ROOT / relative).resolve()
    if not root.is_dir() or ROOT not in root.parents:
        raise AssertionError(f"Installed {side} package is missing or unsafe: {root}")
    return root


def manifest(side: str) -> dict:
    path = package_root(side) / "manifest.json"
    return json.loads(path.read_text(encoding="utf-8"))


def file_inventory(side: str) -> dict[str, str]:
    root = package_root(side)
    return {
        path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


def read_sector(side: str, level: int, sector_x: int, sector_y: int) -> bytes:
    root = package_root(side)
    document = manifest(side)
    matches = [
        entry
        for entry in document["terrainSectors"]
        if entry["worldSpace"] == "global"
        and entry["level"] == level
        and entry["sectorX"] == sector_x
        and entry["sectorY"] == sector_y
    ]
    if len(matches) != 1:
        raise AssertionError(
            f"Expected one installed terrain sector global:{level}:{sector_x}:{sector_y}, "
            f"found {len(matches)}"
        )
    entry = matches[0]
    if entry["encoding"] != "raw-layered-sector-v1":
        raise AssertionError(
            f"Legacy-shaped fixture requires raw-layered-sector-v1, found {entry['encoding']}"
        )
    payload = (root / entry["path"]).read_bytes()
    digest = hashlib.sha256(payload).hexdigest()
    if digest != entry["sha256"]:
        raise AssertionError(f"Installed terrain sector hash mismatch: {entry['path']}")
    return payload


def read_legacy_sector(
    side: str, sector_name: str, *, level: int | None = None
) -> bytes:
    match = LEGACY_SECTOR.fullmatch(sector_name)
    if match is None:
        raise ValueError(f"Invalid legacy sector name: {sector_name}")
    height, legacy_x, legacy_y = map(int, match.groups())
    if level is None:
        if height not in LEGACY_HEIGHT_TO_LEVEL:
            raise ValueError(
                f"Legacy height {height} requires an explicit installed level"
            )
        level = LEGACY_HEIGHT_TO_LEVEL[height]
    return read_sector(
        side,
        level,
        legacy_x - 48,
        legacy_y - 37,
    )
