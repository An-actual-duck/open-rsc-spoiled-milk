#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
LADDERS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/defaults/Ladders.java"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label} missing expected snippet: {snippet}")


def main() -> None:
    ladders = LADDERS.read_text(encoding="utf-8")
    require(
        ladders,
        "matchesLegacyPackedLocation(obj, 368, 438)",
        "Heroes' Guild exact surface stairs trigger",
    )
    require(
        ladders,
        "player.teleportLegacyPacked(371, 3266, false);",
        "Heroes' Guild explicit packed basement landing",
    )
    require(
        ladders,
        "matchesLegacyPackedLocation(obj, 370, 3264)",
        "Heroes' Guild exact basement stairs trigger",
    )
    require(
        ladders,
        "player.teleportLegacyPacked(369, 437, false);",
        "Heroes' Guild explicit packed surface return landing",
    )
    print("PASS: Heroes' Guild stairs use the moved safe landing tiles")


if __name__ == "__main__":
    main()
