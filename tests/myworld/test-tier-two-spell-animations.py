#!/usr/bin/env python3

import re
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANIMATIONS = ROOT / "dev/myworld/assets/animations"


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def png_size(relative_path: str) -> tuple[int, int]:
    path = ANIMATIONS / relative_path
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"not a PNG spritesheet: {path.relative_to(ROOT)}")
    return struct.unpack(">II", data[16:24])


def require(text: str, snippets: tuple[str, ...], source: str) -> None:
    for snippet in snippets:
        if snippet not in text:
            raise AssertionError(f"{source} is missing {snippet!r}")


def main() -> int:
    sheets = {
        "on-entity/teleport/Buff n Debuff P1 04.png": (1152, 64),
        "on-entity/lesser-heal/Buff n Debuff P1 03.png": (768, 64),
        "on-entity/greater-heal/Buff n Debuff P07 04.png": (1216, 64),
        "on-entity/Holy VFX 09/Holy Effect 09(16x16).png": (192, 16),
        "projectile-static/wind-2/Wind Breath.png": (576, 32),
        "projectile-static/water-2/Water Beam.png": (480, 240),
        "on-entity/earth-2/Earth Hammer (48x48).png": (240, 240),
        "on-entity/fire-2/Fire Claw.png": (288, 32),
        "projectile-moving/thunder-2/Projectile/Projectile 2 wo blur.png": (768, 48),
        "projectile-moving/thunder-2/Hit/Thunder hit wo blur.png": (192, 32),
        "on-entity/ice-2/Ice VFX 3(48x48).png": (1056, 48),
        "on-entity/acid-2/Acid VFX 09(72x80).png": (1656, 80),
        "on-entity/wood-2/Wood VFX 04(32x48).png": (512, 48),
    }
    for relative_path, expected in sheets.items():
        assert png_size(relative_path) == expected, f"unexpected sheet geometry: {relative_path}"

    combat_catalog = read("Client_Base/src/orsc/graphics/two/CombatEffectAnimationCatalog.java")
    require(combat_catalog, (
        'define(definitions, 6, "fire-2", ON_ENTITY, "fire-2/Fire Claw.png", 9, 1, 0, 9, 64);',
        'define(definitions, 8, "earth-2", ON_ENTITY, "earth-2/Earth Hammer (48x48).png", 5, 5, 0, 21, 64);',
        'define(definitions, 16, "lesser-heal", ON_ENTITY,',
        '"lesser-heal/Buff n Debuff P1 03.png", 16, 1, 0, 16, 64);',
        'define(definitions, 17, "greater-heal", ON_ENTITY,',
        'define(definitions, 18, "holy-vfx-09", ON_ENTITY,',
        '"Holy VFX 09/Holy Effect 09(16x16).png", 12, 1, 0, 12, 32);',
        'define(definitions, 30, "thunder-2-hit", PROJECTILE_MOVING,',
        'define(definitions, 31, "ice-2", ON_ENTITY, "ice-2/Ice VFX 3(48x48).png", 22, 1, 0, 20, 64);',
        'define(definitions, 32, "acid-2", ON_ENTITY, "acid-2/Acid VFX 09(72x80).png", 23, 1, 0, 23, 64);',
        'define(definitions, 33, "wood-2", ON_ENTITY, "wood-2/Wood VFX 04(32x48).png", 16, 1, 0, 15, 64);',
        'define(definitions, 65, "teleport", ON_ENTITY,',
        '"teleport/Buff n Debuff P1 04.png", 24, 1, 0, 24, 64);',
    ), "CombatEffectAnimationCatalog.java")
    assert "VisibleCenterAnchored" not in combat_catalog, \
        "on-entity sheets must retain their authored frame placement"
    assert "HorizontallyCentered" not in combat_catalog

    static_catalog = read("Client_Base/src/orsc/graphics/two/ProjectileStaticAnimationCatalog.java")
    require(static_catalog, (
        'PROJECTILE_TYPES.WIND_STATIC_2, "wind-2", "wind-2/Wind Breath.png"',
        '12, 1, 0, 12, 32);',
        'PROJECTILE_TYPES.WATER_STATIC_2, "water-2", "water-2/Water Beam.png"',
        '5, 5, 0, 25, 48);',
    ), "ProjectileStaticAnimationCatalog.java")

    projectile_catalog = read("Client_Base/src/orsc/graphics/two/ProjectileAnimationCatalog.java")
    require(projectile_catalog, (
        'define(definitions, "thunder-2", "Thunder bird", "thunder-2/Projectile/Projectile 2 wo blur.png", 16, 1, 0, 16);',
        'fallback(fallbacks, PROJECTILE_TYPES.THUNDER_BIRD, "thunder-2");',
        'fallback(fallbacks, PROJECTILE_TYPES.EARTH_LEAD_2, "earth-basic");',
        'fallback(fallbacks, PROJECTILE_TYPES.FIRE_LEAD_2, "fire-basic");',
        'fallback(fallbacks, PROJECTILE_TYPES.ICE_LEAD_2, "ice-basic");',
        'fallback(fallbacks, PROJECTILE_TYPES.ACID_LEAD_2, "acid-basic");',
        'fallback(fallbacks, PROJECTILE_TYPES.WOOD_LEAD_2, "wood-basic");',
        'segment("fire-basic/Firebolt SpriteSheet.png", 11, 1, 5, 6)',
        'segment("water-basic/WaterBall - Impact.png", 4, 4, 0, 16)',
        'segment("thunder-2/Hit/Thunder hit wo blur.png", 6, 1, 0, 6)',
    ), "ProjectileAnimationCatalog.java")

    spell_handler = read("server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java")
    spell_classification = read(
        "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"
    )
    projectile_assignments = {
        "WIND_BOLT": "WIND_STATIC_2",
        "WATER_BOLT": "WATER_STATIC_2",
        "EARTH_BOLT": "EARTH_LEAD_2",
        "FIRE_BOLT": "FIRE_LEAD_2",
        "THUNDER_SPLASH": "THUNDER_BIRD",
        "ICE_BURST": "ICE_LEAD_2",
        "ACID_FROG": "ACID_LEAD_2",
        "WOOD_DRILL": "WOOD_LEAD_2",
    }
    for spell, projectile in projectile_assignments.items():
        pattern = rf"case {spell}:\s+return Projectile\.{projectile};"
        assert re.search(pattern, spell_classification), \
            f"{spell} is not assigned Projectile.{projectile}"

    impact_method = re.search(
        r"static int getSpellImpactEffect.*?\n\t\}", spell_classification, re.DOTALL
    )
    assert impact_method is not None
    assert "case WIND_BOLT:" not in impact_method.group(0)
    assert "case WATER_BOLT:" not in impact_method.group(0)
    require(impact_method.group(0), (
        "case EARTH_BOLT:", "return CombatEffect.EARTH_HAMMER;",
        "case FIRE_BOLT:", "return CombatEffect.FIRE_CLAW;",
        "case THUNDER_SPLASH:", "return CombatEffect.THUNDER_SPLASH;",
        "case ICE_BURST:", "return CombatEffect.ICE_BURST;",
        "case ACID_FROG:", "return CombatEffect.ACID_FROG;",
        "case WOOD_DRILL:", "return CombatEffect.WOOD_DRILL;",
    ), "getSpellImpactEffect")
    require(spell_classification, (
        "return CombatEffect.LESSER_HEAL;",
        "return CombatEffect.GREATER_HEAL;",
    ), "SpellClassification.java")
    require(spell_handler, (
        "new CombatEffect(player, CombatEffect.ALCHEMY)",
        "new CombatEffect(player, CombatEffect.TELEPORT)",
        'TELEPORT_CHARGE_MS, "Teleport spell charge"',
        "shouldShowSpellProjectile(spellEnum, impactEffect)",
    ), "SpellHandler.java")
    for composite_spell in (
        "EARTH_BOLT", "FIRE_BOLT", "THUNDER_SPLASH", "ICE_BURST", "ACID_FROG", "WOOD_DRILL"
    ):
        assert f"spellEnum == Spells.{composite_spell}" in spell_classification, \
            f"{composite_spell} must retain its projectile before the impact effect"
    telegrab_case = re.search(
        r"case TELEKINETIC_GRAB:.*?\n\t\t\t\t\t\tbreak;", spell_handler, re.DOTALL
    )
    assert telegrab_case and "sendTeleBubble" not in telegrab_case.group(0), \
        "telekinetic grab must not emit a visual animation"

    teleport_charge = re.search(
        r"private void handleTeleport.*?new MiniEvent", spell_handler, re.DOTALL
    )
    assert teleport_charge and teleport_charge.group(0).index("CombatEffect.TELEPORT") \
        < teleport_charge.group(0).index("new MiniEvent"), \
        "teleport effect must play when charging begins"

    client = read("Client_Base/src/orsc/mudclient.java")
    require(client, (
        "public static final int COMBAT_EFFECT_TELEPORT = 65;",
        "public static final int COMBAT_EFFECT_COUNT = 66;",
        '"true-defense", "teleport"',
        "ProjectileStaticAnimationCatalog.getDefinition(projectile.id)",
        "drawQueuedStaticProjectiles()",
        "getScaledStaticProjectileSprite",
        "getTierTwoLeadInBasicProjectileId",
        "getProjectileSceneSize(EntityHandler.projectiles.get(leadInBasicProjectileId), false) / 2",
        'if ("Thunder Bird".equalsIgnoreCase(spellName))',
        'if ("Ice Slash".equalsIgnoreCase(spellName))',
        'if ("Acid Splash".equalsIgnoreCase(spellName))',
        "public static final int COMBAT_EFFECT_FRAME_SLOTS = 64;",
        "private static final int COMBAT_EFFECT_FRAME_TICKS = 3;",
        "private static final int LESSER_HEAL_FRAME_TICKS = 4;",
        "private static final int TELEPORT_EFFECT_TICKS = 250;",
        "private static final int TELEPORT_OPENING_FRAME_COUNT = 8;",
        "private static final int TELEPORT_LOOP_START_FRAME = 8;",
        "private static final int TELEPORT_LOOP_END_FRAME = 10;",
        "private static final int TELEPORT_LOOP_FRAME_TICKS = 10;",
        "frameCount * getCombatEffectFrameTicks(effectType)",
        "if (effectType == COMBAT_EFFECT_LESSER_HEAL)",
        "return LESSER_HEAL_FRAME_TICKS;",
        "if (effectType == COMBAT_EFFECT_TELEPORT)",
        "return TELEPORT_EFFECT_TICKS;",
        "getTeleportCurrentFrame(effectTime, duration, frameCount)",
        "int finishStartFrame = TELEPORT_LOOP_END_FRAME + 1;",
        "return TELEPORT_LOOP_START_FRAME + loopFrame;",
        "effectType == COMBAT_EFFECT_TELEPORT ? Math.max(1, (size * 5) / 8) : size",
        "getCombatEffectScreenWidth",
        "getCombatEffectScreenHeight",
        "private static final int PROJECTILE_IMPACT_FRAME_TICKS = 3;",
        "loadProjectileImpactAnimationSheets",
        "drawQueuedProjectileImpactOverlays()",
        "public void applyCombatEffectUpdate(ORSCharacter character, int effectType)",
        "shouldDeferCombatEffectUntilProjectileArrival",
        "startGenericProjectileImpact(character);",
        "scaled.setRequiresShift(true);",
    ), "mudclient.java")
    assert "anchorAnimationFrameToVisibleCenter" not in client
    complete_teleport = re.search(
        r"private void completeTeleport.*?\n\t\}", spell_handler, re.DOTALL
    )
    assert complete_teleport and "teleport(120, 504, 0, false)" in complete_teleport.group(0)
    assert "teleport(120, 504, 0, true)" not in complete_teleport.group(0), \
        "spell teleports must not append the legacy teleport bubble"
    assert "COMBAT_EFFECT_TICKS" not in client, \
        "combat effects must derive total playback time from their loaded frame count"
    assert "centerAnimationFrameHorizontally" not in client, \
        "lesser heal must not horizontally sweep a directional spritesheet"

    packet_handler = read("Client_Base/src/orsc/PacketHandler.java")
    require(packet_handler, (
        "mc.applyCombatEffectUpdate(npc, effectType);",
        "mc.applyCombatEffectUpdate(player, effectType);",
        "pendingCombatEffectType = 0;",
    ), "PacketHandler.java")

    character = read("Client_Base/src/orsc/ORSCharacter.java")
    require(character, (
        "public int pendingCombatEffectType = 0;",
        "public int projectileImpactId = -1;",
        "public int projectileImpactTime = 0;",
        "public boolean projectileMirrored = false;",
    ), "ORSCharacter.java")

    assert "Projectile.SKULL, CombatEffect.IBAN_BLAST, true" in spell_handler, \
        "Iban blast must render its projectile before its impact"
    assert re.search(
        r"godSpellProjectile, godSpellImpact, true\)\);", spell_handler
    ), "god spells must render their holy projectile before their impact"

    migration_plan = read("docs/myworld/in-progress-work-plans/animation-asset-migration-plan.md")
    require(migration_plan, (
        "Deferred Water Burst projection note:",
        "perspective-aware warping",
        "current polish wave into that renderer experiment.",
    ), "animation-asset-migration-plan.md")
    legacy_root = ROOT / "dev/myworld/assets/legacy animation folder/On Enemy"
    assert len(list((legacy_root / "ice-burst").glob("*.png"))) == 35
    assert len(list((legacy_root / "ice-crystal").glob("*.png"))) == 34

    spell_defs = read("server/conf/server/defs/SpellDef.xml")
    entity_handler = read("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java")
    skill_guide = read("Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java")
    for new_name in ("Thunder Bird", "Ice Slash", "Acid Splash"):
        require(spell_defs, (f"<name>{new_name}</name>",), "SpellDef.xml")
        require(entity_handler, (f'new SpellDef("{new_name}"',), "EntityHandler.java")
    require(skill_guide, (
        "ElementalSpellDisplayMetadata.resolve(spellIndex, spell)",
        "metadata.getSpellName()",
        "metadata.getGuideTooltip()",
    ), "SkillGuideInterface.java")
    for retired_name in ("Thunder Splash", "Ice Burst", "Acid Frog"):
        assert f"<name>{retired_name}</name>" not in spell_defs
        assert f'new SpellDef("{retired_name}"' not in entity_handler
        assert retired_name not in skill_guide

    print("PASS: tier-two spell animation mappings, names, geometry, and teleport timing validated")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError) as error:
        print(f"FAIL: {error}")
        raise SystemExit(1)
