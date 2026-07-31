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
        "obj.getID() == 42\n\t\t\t&& matchesLegacyPackedLocation(obj, 368, 438)",
        "Heroes' Guild surface stairs trigger",
    )
    require(
        ladders,
        "player.teleportLegacy(371, 3266, false);",
        "Heroes' Guild basement landing",
    )
    require(
        ladders,
        "matchesLegacyPackedLocation(obj, 370, 3264)",
        "Heroes' Guild basement stairs trigger",
    )
    require(
        ladders,
        "player.teleportLegacy(369, 437, false);",
        "Heroes' Guild surface return landing",
    )
    require(
        ladders,
        "matchesLegacyPackedLocation(obj, 516, 1479)",
        "Legends' Guild upper-floor stairs trigger",
    )
    require(
        ladders,
        "player.teleportLegacy(516, 2426, false);",
        "Legends' Guild top-floor landing",
    )
    print("PASS: guild stairs use level-qualified triggers and safe landings")


if __name__ == "__main__":
    main()
