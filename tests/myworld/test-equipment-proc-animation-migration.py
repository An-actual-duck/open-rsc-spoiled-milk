#!/usr/bin/env python3
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANIMATIONS = ROOT / "dev/myworld/assets/animations"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def png_size(relative_path: str) -> tuple[int, int]:
    data = (ANIMATIONS / relative_path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {relative_path}"
    return struct.unpack(">II", data[16:24])


def require(text: str, snippets: tuple[str, ...], source: str) -> None:
    for snippet in snippets:
        assert snippet in text, f"{source} is missing {snippet!r}"


def main() -> int:
    expected_sheets = {
        "on-entity/Fire combo/Fire Hit 3.png": (544, 32),
        "on-entity/fire-slashes/Slash 1.png": (416, 32),
        "on-entity/fire-slashes/Slash 2.png": (384, 32),
        "on-entity/bubble-shield/true-defense.png": (1408, 64),
        "on-entity/explosions/Explosion VFX 1(32x32).png": (320, 32),
        "on-entity/fire-orb-explosion/Fire Orb Explosion(48x48).png": (864, 48),
        "on-entity/wood-6/Wood VFX 05(48x48).png": (816, 48),
        "projectile-moving/acid-basic-2/Acid VFX 03(56x48).png": (896, 48),
        "projectile-static/ice-stab/Ice VFX 10(64x32).png": (1408, 32),
    }
    for sheet, size in expected_sheets.items():
        assert png_size(sheet) == size, f"unexpected spritesheet geometry: {sheet}"

    catalog = read("Client_Base/src/orsc/graphics/two/CombatEffectAnimationCatalog.java")
    require(catalog, (
        'define(definitions, 1, "explosion-vfx-15"',
        'define(definitions, 2, "explosion-vfx-17"',
        'define(definitions, 15, "explosion-vfx-1"',
        '"explosions/Explosion VFX 1(32x32).png", 10, 1, 0, 9, 64);',
        'define(definitions, 46, "fire-orb-explosion"',
        '"fire-orb-explosion/Fire Orb Explosion(48x48).png", 18, 1, 0, 18, 64);',
        'define(definitions, 58, "fire-slash-1"',
        '"fire-slashes/Slash 1.png", 13, 1, 0, 13, 64);',
        'define(definitions, 59, "fire-hit-3"',
        '"Fire combo/Fire Hit 3.png", 17, 1, 0, 17, 64);',
        'define(definitions, 61, "wood-6"',
        'define(definitions, 64, "bubble-shield"',
        'define(definitions, 66, "fire-slash-2"',
        '"fire-slashes/Slash 2.png", 12, 1, 0, 12, 64);',
    ), "CombatEffectAnimationCatalog.java")

    moving = read("Client_Base/src/orsc/graphics/two/ProjectileAnimationCatalog.java")
    require(moving, (
        'define(definitions, "acid-basic-2", "Acid armor proc"',
        '"acid-basic-2/Acid VFX 03(56x48).png", 16, 1, 0, 16);',
        'fallback(fallbacks, PROJECTILE_TYPES.ACID_ARMOR_PROC, "acid-basic-2");',
    ), "ProjectileAnimationCatalog.java")

    static = read("Client_Base/src/orsc/graphics/two/ProjectileStaticAnimationCatalog.java")
    require(static, (
        'PROJECTILE_TYPES.ICE_SWORD_STAB, "ice-stab"',
        '"ice-stab/Ice VFX 10(64x32).png",',
        "22, 1, 0, 22, 32);",
    ), "ProjectileStaticAnimationCatalog.java")

    server_effect = read("server/src/com/openrsc/server/model/entity/update/CombatEffect.java")
    server_projectile = read("server/src/com/openrsc/server/model/entity/update/Projectile.java")
    client_handler = read("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java")
    client = read("Client_Base/src/orsc/mudclient.java")
    asset_loader = read("Client_Base/src/orsc/ClientExternalAssetLoader.java")
    require(server_effect, ("DRAGON_WEAPON_SLASH_2 = 66",), "CombatEffect.java")
    require(server_projectile, (
        "ACID_ARMOR_PROC = 39",
        "ICE_SWORD_STAB = 40",
    ), "Projectile.java")
    require(client_handler, (
        "ACID_ARMOR_PROC(39)",
        "ICE_SWORD_STAB(40)",
    ), "EntityHandler.java")
    require(client, (
        "COMBAT_EFFECT_DRAGON_WEAPON_SLASH_2 = 66",
        "COMBAT_EFFECT_COUNT = 77",
        "CUSTOM_PROJECTILE_COUNT = 34",
        "if (effectType == COMBAT_EFFECT_HELLS_INFERNO)",
        "return 336;",
        "return Math.max(1, (baseSize * 3) / 2);",
        "getCombatEffectTargetScreenXOffset(character,",
        "int correction = Math.max(1, size / 6);",
        "useCombatBFrames(character.direction) ? -correction : correction",
    ), "mudclient.java")
    require(asset_loader, (
        "int boundedTargetSize = Math.max(1, Math.min(64, maxTargetSize));",
        "source.getWidth() * boundedTargetSize",
        "source.getHeight() * boundedTargetSize",
    ), "ClientExternalAssetLoader.java")

    for source in (
        "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java",
    ):
        event = read(source)
        require(event, (
            "DragonMeleeBreathFollowup.tryApply",
            "ElementalSwordProc.tryApply",
            "Projectile.ACID_ARMOR_PROC",
        ), source)

    dragon_melee_breath = read(
        "server/src/com/openrsc/server/model/combat/DragonMeleeBreathFollowup.java"
    )
    require(dragon_melee_breath, (
        "random.nextIntInclusive(0, 1) == 0",
        "CombatEffect.DRAGON_WEAPON_SLASH_2",
        "new CombatEffect(target, effect)",
    ), "DragonMeleeBreathFollowup.java")

    elemental_sword = read(
        "server/src/com/openrsc/server/model/combat/ElementalSwordProc.java"
    )
    require(elemental_sword, (
        "effect == CombatEffect.ICE_SWORD",
        "Projectile.ICE_SWORD_STAB",
    ), "ElementalSwordProc.java")

    projectile_event = read("server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java")
    require(projectile_event, (
        "magicArmorPoisonProc || rangedArmorPoisonProc",
        "Projectile.ACID_ARMOR_PROC",
        "DataConversions.random(0, 1) == 0",
        "CombatEffect.DRAGON_WEAPON_SLASH_2",
        "new CombatEffect(opponent, slashEffect)",
    ), "ProjectileEvent.java")

    print("PASS: weapon and armor proc animations use assigned reusable spritesheets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
