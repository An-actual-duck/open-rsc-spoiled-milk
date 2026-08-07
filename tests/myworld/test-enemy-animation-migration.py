#!/usr/bin/env python3
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "dev/myworld/assets/animations/on-entity"


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
    assert png_size("explosions/Explosion VFX 15(32x32).png") == (416, 32)
    assert png_size("explosions/Explosion VFX 17(48x64).png") == (528, 64)
    assert png_size("burn/Buff n Debuff P07 03.png") == (1024, 64)

    catalog = read("Client_Base/src/orsc/graphics/two/CombatEffectAnimationCatalog.java")
    require(catalog, (
        'define(definitions, 42, "explosion-vfx-15"',
        '"explosions/Explosion VFX 15(32x32).png", 13, 1, 0, 13, 64);',
        'define(definitions, 45, "explosion-vfx-17"',
        '"explosions/Explosion VFX 17(48x64).png", 11, 1, 0, 11, 64);',
        'define(definitions, 63, "burn"',
        '"burn/Buff n Debuff P07 03.png", 16, 1, 0, 16, 64);',
    ), "CombatEffectAnimationCatalog.java")

    effect = read("server/src/com/openrsc/server/model/entity/update/CombatEffect.java")
    require(effect, (
        "public static final int DEMON_EXPLOSION = LESSER_DEMON_MAGIC;",
        'case "lesser demon":',
        'case "greater demon":',
        'case "chronozon":',
        "return DEMON_EXPLOSION;",
        'case "black demon":',
        "return BLACK_DEMON_MAGIC;",
        'case "balrog":',
        "return BALROG_MAGIC;",
    ), "CombatEffect.java")
    for retired in ('case "otherworldly being":', 'case "paladin":'):
        assert retired not in effect, f"legacy enemy impact routing remains: {retired}"

    profile = read("server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java")
    require(profile, (
        "return Projectile.WIND_ARROW;",
        "return Projectile.WATER_BALL;",
        "return Projectile.ROCK_THROW;",
        "return Projectile.FIREBALL;",
        "return Projectile.THUNDER_BALL;",
        "return Projectile.BRANCH_SPORE;",
        "return CombatEffect.EARTH_BURST;",
        "return CombatEffect.WATER_ERUPTION;",
        "return CombatEffect.EXPLOSION;",
    ), "NpcAttackStyleProfile.java")
    for retired in ("return Projectile.BLANK;", "getBattleMageImpactEffect",
                    "usesBasicCasterEarthImpact", "usesFireKinMagic",
                    "usesIceKinMagic", "usesEarthKinMagic"):
        assert retired not in profile, f"enemy fallback exception remains: {retired}"

    client = read("Client_Base/src/orsc/mudclient.java")
    require(client, (
        "COMBAT_EFFECT_DEMON_EXPLOSION = COMBAT_EFFECT_LESSER_DEMON_MAGIC",
        "projectileId == PROJECTILE_TYPES.ROCK_THROW.id()",
        "effectType == COMBAT_EFFECT_EARTH_BURST",
        "projectileId == PROJECTILE_TYPES.WATER_BALL.id()",
        "effectType == COMBAT_EFFECT_WATER_ERUPTION",
        "projectileId == PROJECTILE_TYPES.FIREBALL.id()",
        "effectType == COMBAT_EFFECT_EXPLOSION",
        "effectType == COMBAT_EFFECT_DEMON_EXPLOSION",
        "effectType == COMBAT_EFFECT_BLACK_DEMON_MAGIC",
        "effectType == COMBAT_EFFECT_ELDER_DRAGON_BURN",
    ), "mudclient.java")

    elder = read("server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java")
    require(elder, (
        "Projectile.FIREBALL",
        "CombatEffect.ELDER_DRAGON_BURN",
    ), "ElderGreenDragonSpecialAttacks.java")

    print("PASS: enemy magic uses basic fallbacks and assigned reusable impacts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
