#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
GLYPHS = ROOT / "dev/myworld/assets/sprites/world/rune-altars/glyphs"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    client = CLIENT.read_text()

    require("ALTAR_ELEMENT_COLORS" in client,
            "Rune altar visuals should define fallback element colors")
    require("createProceduralAltarWorldSprite(ALTAR_ELEMENT_COLORS[i], true)" in client,
            "Missing glyph fallback when external altar glyph PNGs are absent")
    require("createProceduralAltarWorldSprite(ALTAR_ELEMENT_COLORS[i], false)" in client,
            "Missing orb fallback when external altar orb PNGs are absent")
    require("private Sprite createProceduralAltarWorldSprite(int color, boolean glyph)" in client,
            "Missing procedural altar world sprite builder")
    require('"law-rune.png"' in client,
            "Law altar glyph filename alias should be supported")

    expected_glyphs = [
        "air-glyph.png", "water-glyph.png", "earth-glyph.png", "fire-glyph.png",
        "mind-glyph.png", "body-glyph.png", "cosmic-glyph.png", "chaos-glyph.png",
        "nature-glyph.png", "law-rune.png", "death-glyph.png",
        "blood-glyph.png", "soul-glyph.png", "life-glyph.png",
    ]
    for glyph in expected_glyphs:
        require((GLYPHS / glyph).is_file(), f"Missing altar glyph asset: {glyph}")

    print("PASS: rune altar glyph/orb visual fallback validated")


if __name__ == "__main__":
    main()
