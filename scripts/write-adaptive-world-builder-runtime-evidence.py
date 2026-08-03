#!/usr/bin/env python3
"""Emit strict discovery evidence for this adaptive Builder runtime."""

import argparse
import hashlib
import json
import re
import stat
import sys
from pathlib import Path


IDENTIFIER = re.compile(r"[a-z0-9][a-z0-9._-]{0,127}\Z")
MAX_CATALOG_BYTES = 1024 * 1024 * 1024
SERVER_BUILD_ID = "core-framework-adaptive-builder-server-v1"
CLIENT_BUILD_ID = "core-framework-adaptive-builder-client-v1"
LOADER_ID = "generic-signed-layered-loader-v1"
PROTOCOL_ID = "world-builder-native-layered-protocol-v1"


def safe_catalog(requested: str) -> Path:
    path = Path(requested).absolute()
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current /= part
        if current.is_symlink():
            raise ValueError("definition catalog path contains a symbolic link")
    info = path.stat(follow_symlinks=False)
    if not stat.S_ISREG(info.st_mode):
        raise ValueError("definition catalog is not a regular file")
    if info.st_size < 1 or info.st_size > MAX_CATALOG_BYTES:
        raise ValueError("definition catalog size is outside its bound")
    if getattr(info, "st_nlink", 1) != 1:
        raise ValueError("definition catalog is hard linked")
    return path.resolve(strict=True)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(64 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Write canonical world-builder-runtime-evidence v1 JSON to stdout."
        )
    )
    parser.add_argument("--side", required=True, choices=("server", "client"))
    parser.add_argument("--definition-catalog", required=True)
    parser.add_argument("--definition-catalog-id", required=True)
    args = parser.parse_args()

    if not IDENTIFIER.fullmatch(args.definition_catalog_id):
        parser.error("--definition-catalog-id is not a portable identifier")
    try:
        catalog = safe_catalog(args.definition_catalog)
        catalog_sha256 = sha256(catalog)
    except (OSError, ValueError) as failure:
        parser.error(str(failure))

    evidence = {
        "schemaVersion": 1,
        "manifestType": "world-builder-runtime-evidence",
        "side": args.side,
        "buildId": SERVER_BUILD_ID if args.side == "server" else CLIENT_BUILD_ID,
        "loaderId": LOADER_ID,
        "protocolId": PROTOCOL_ID,
        "definitionCatalogId": args.definition_catalog_id,
        "definitionCatalogSha256": catalog_sha256,
        "mapFormatId": "signed-layered-v1",
        "packageSchemaId": "layered-world-package-v1",
        "encodingVersions": [1, 3],
        "authoring": {
            "editExistingLevels": True,
            "createLevels": True,
            "placementFamilies": [
                "boundary",
                "ground-item",
                "npc",
                "scenery",
            ],
        },
    }
    json.dump(evidence, sys.stdout, sort_keys=True, separators=(",", ":"))
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
