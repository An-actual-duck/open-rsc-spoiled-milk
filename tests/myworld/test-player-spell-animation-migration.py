#!/usr/bin/env python3
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "dev/myworld/assets/runtime/animations/on-entity"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def png_size(relative_path: str) -> tuple[int, int]:
    data = (ASSETS / relative_path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {relative_path}"
    return struct.unpack(">II", data[16:24])


def require(text: str, snippets: tuple[str, ...], source: str) -> None:
    for snippet in snippets:
        assert snippet in text, f"{source} is missing {snippet!r}"


def main() -> int:
    sheets = {
        "wind-3/Tornado.png": (192, 96),
        "water-3/Water Splash 01 - Spritesheet.png": (330, 308),
        "earth-3/Earth Burst (64x48).png": (832, 144),
        "explosions/Explosion VFX 3(48x48).png": (624, 48),
        "thunder-3/Thunderstrike wo blur.png": (832, 64),
        "ice-3/Ice VFX 2(64x64).png": (2176, 64),
        "acid-3/Acid VFX 04(48x48).png": (1104, 48),
        "wood-3/Wood VFX 08(56x56).png": (784, 56),
        "thunder-explosion/Start Explosion wo blur.png": (832, 64),
        "thunder-explosion/Explosion wo blur.png": (896, 64),
        "wind-4/Wind Beam.png": (448, 32),
        "water-4/Water Blast - Start.png": (512, 384),
        "water-4/Water Blast - End.png": (384, 384),
        "earth-4/Earth Impale 64x64.png": (1088, 64),
        "fire-4/Fire Beam.png": (320, 32),
        "dark-10/Dark VFX10 (48x48).png": (1248, 48),
        "dark-11/Dark VFX 11 (32x48).png": (448, 48),
        "dark-7/Dark VFX 7 Repeatable (48x48).png": (576, 48),
        "dark-7/Dark VFX 7 Ending (48x48).png": (816, 48),
        "dark-4/Dark VFX 4 (48x56).png": (1488, 56),
        "dark-12/Dark VFX 12 (48x48).png": (624, 48),
        "dark-6/Dark VFX 6 Diagonal (48x64).png": (768, 64),
    }
    for relative_path, expected in sheets.items():
        assert png_size(relative_path) == expected, f"unexpected sheet geometry: {relative_path}"
    assert not (ASSETS / "darl-12").exists()
    assert not (ASSETS / "Explosions").exists()

    catalog = read("Client_Base/src/orsc/graphics/two/CombatEffectAnimationCatalog.java")
    require(catalog, (
        'define(definitions, 7, "wind-4"',
        'define(definitions, 9, "water-3"',
        'define(definitions, 10, "explosion-vfx-3"',
        'define(definitions, 12, "earth-4"',
        'define(definitions, 14, "fire-4"',
        'define(definitions, 19, "dark-11"',
        'define(definitions, 22, "dark-10"',
        'define(definitions, 23, "dark-4"',
        'define(definitions, 24, "dark-12"',
        'define(definitions, 25, "dark-6-diagonal"',
        'define(definitions, 34, "thunder-3"',
        'define(definitions, 35, "ice-3"',
        'define(definitions, 36, "acid-3"',
        'define(definitions, 37, "wood-3"',
        'defineSequence(definitions, sequences, 4,',
        '"earth-3/Earth Burst (64x48).png", 13, 3, 0, 7, 64',
        '"earth-3/Earth Burst (64x48).png", 13, 3, 13, 10, 64',
        '"earth-3/Earth Burst (64x48).png", 13, 3, 26, 10, 64',
        'defineSequence(definitions, sequences, 11,',
        '"wind-3/Tornado.png", 6, 3, 0, 5, 64',
        '"wind-3/Tornado.png", 6, 3, 6, 6, 64',
        '"wind-3/Tornado.png", 6, 3, 12, 6, 64',
        'defineSequence(definitions, sequences, 13,',
        'defineSequence(definitions, sequences, 20,',
        'defineSequence(definitions, sequences, 21,',
        'public static Definition[] getSequence(int effectType)',
    ), "CombatEffectAnimationCatalog.java")

    client = read("Client_Base/src/orsc/mudclient.java")
    require(client, (
        "CombatEffectAnimationCatalog.getSequence(effectType)",
        "for (CombatEffectAnimationCatalog.Definition definition : sequence)",
        "definition.getFrameCount(), loadedFrames",
        "Math.max(1, (size * 5) / 8)",
        "if (projectileId == PROJECTILE_TYPES.HOLY_MAGIC.id())",
        "return effectType == COMBAT_EFFECT_SARADOMIN_STRIKE",
    ), "mudclient.java")

    spell_classification = read(
        "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"
    )
    require(spell_classification, (
        "return Projectile.HOLY_MAGIC;",
        "return CombatEffect.EYE_OF_GUTHIX;",
        "return CombatEffect.SARADOMIN_STRIKE;",
        "return CombatEffect.ZAMORAKS_VOID;",
        "return CombatEffect.ZAMORAKS_APOCOLYPSE;",
        "return CombatEffect.SARADOMIN_SOUL_SLASH;",
        "return CombatEffect.CLAW_OF_GUTHIX;",
    ), "SpellClassification.java")

    print("PASS: remaining player spell animations use the assigned reusable sheets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
