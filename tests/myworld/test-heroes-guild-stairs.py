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
        "obj.getID() == 42 && obj.getX() == 368 && obj.getY() == 438",
        "Heroes' Guild surface stairs trigger",
    )
    require(
        ladders,
        "player.teleport(371, 3266, false);",
        "Heroes' Guild basement landing",
    )
    require(
        ladders,
        "obj.getX() == 370 && obj.getY() == 3264",
        "Heroes' Guild basement stairs trigger",
    )
    require(
        ladders,
        "player.teleport(369, 437, false);",
        "Heroes' Guild surface return landing",
    )
    print("PASS: Heroes' Guild stairs use the moved safe landing tiles")


if __name__ == "__main__":
    main()
