#!/usr/bin/env python3
"""Validate the clickable auto-attack HUD control."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ICON = ROOT / "dev/myworld/assets/runtime/ui/auto-attack.png"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> int:
    client = CLIENT.read_text(encoding="utf-8")
    required = (
        "drawAutoAttackHudButton(x - toggleSize - gap, y + (height - toggleSize) / 2, toggleSize, font)",
        "private void drawAutoAttackHudButton(int x, int y, int size, int font)",
        "Auto attack: ",
        "x - hudPixels(4) - getSurface().stringWidth(font, hoverText)",
        "private void toggleAutoRetaliate()",
        "bufferBits.putByte(47)",
        "C_AUTO_RETALIATE ? 1 : 0",
        'ui/auto-attack.png',
        "size - 2 * inset, size - 2 * inset, 5924);",
    )
    for snippet in required:
        if snippet not in client:
            fail(f"auto-attack HUD missing expected wiring: {snippet}")
    if not ICON.is_file():
        fail("auto-attack HUD icon is missing from embedded MyWorld assets")
    print("PASS: clickable auto-attack HUD control is wired")
    return 0


if __name__ == "__main__":
    sys.exit(main())
