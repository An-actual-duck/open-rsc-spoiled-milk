#!/usr/bin/env python3
"""Guard the KBD-only external sprite replacement contract."""

import struct
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SHEET = ROOT / "dev/myworld/assets/sprites/npcs/king-black-dragon/king-black-dragon-sprite-sheet.png"
JAR = ROOT / "Client_Base/Open_RSC_Client.jar"


def require(source: str, expected: str, message: str) -> None:
    if expected not in source:
        raise SystemExit(f"FAIL: {message}: missing {expected!r}")


def main() -> None:
    if not SHEET.is_file():
        raise SystemExit("FAIL: King Black Dragon native sprite sheet is missing")
    data = SHEET.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("FAIL: King Black Dragon sprite sheet is not PNG")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (1465, 489):
        raise SystemExit(f"FAIL: KBD sheet must remain native 1465x489, got {width}x{height}")
    if data[25] != 6:
        raise SystemExit("FAIL: KBD sprite sheet must preserve RGBA transparency")

    client = CLIENT.read_text(encoding="utf-8")
    defs = DEFS.read_text(encoding="utf-8")
    for expected in (
        "private static final int KING_BLACK_DRAGON_NPC_ID = 477;",
        "private static final int KING_BLACK_DRAGON_FRAME_WIDTH = 542;",
        "private static final int KING_BLACK_DRAGON_FRAME_HEIGHT = 391;",
        'animations.add(new AnimationDef("kingblackdragon", "npc", 0, 0, true, false, 0));',
        "public static void activateKingBlackDragonExternalVisual()",
        "kingBlackDragon.sprites[0] = kingBlackDragonAnimationId;",
    ):
        require(defs, expected, "KBD definition/mapping")
    for expected in (
        "getExternalKingBlackDragonSpriteSheet()",
        "loadExternalKingBlackDragonNpcSprite()",
        "final int[] kingBlackDragonColumnWidths = {230, 209, 263, 222, 208, 333};",
        "final int greenGuideRgb = 0x6BEE36;",
        "NpcDirectionalAnimationMapping.FRAMES_PER_DIRECTION, greenGuideRgb",
        'npcSprites.put("kingblackdragon", spriteEntry);',
        "EntityHandler.activateKingBlackDragonExternalVisual();",
        "Missing or invalid King Black Dragon NPC sprite sheet; using legacy sprite",
        '"foundrydragon".equalsIgnoreCase(s) || "kingblackdragon".equalsIgnoreCase(s)',
    ):
        require(client, expected, "KBD client loading/fallback")

    if not JAR.is_file():
        raise SystemExit("FAIL: build client before KBD package check")
    with zipfile.ZipFile(JAR) as jar:
        path = "myworld-assets/sprites/npcs/king-black-dragon/king-black-dragon-sprite-sheet.png"
        if path not in jar.namelist():
            raise SystemExit("FAIL: KBD sprite sheet was not packaged in client JAR")
    print("PASS: King Black Dragon native sprite extraction, mapping, fallback, and packaging guarded")


if __name__ == "__main__":
    main()
