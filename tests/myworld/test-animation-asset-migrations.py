#!/usr/bin/env python3
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "dev" / "myworld" / "assets" / "legacy animation folder"
MAGIC_UI_ASSETS = ROOT / "dev" / "myworld" / "assets" / "sprites" / "UI" / "magic"


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def png_size(relative_path):
    data = (ASSETS / relative_path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", relative_path
    return struct.unpack(">II", data[16:24])


def raw_png_size(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    return struct.unpack(">II", data[16:24])


def require(text, fragments, source):
    for fragment in fragments:
        assert fragment in text, f"{source} is missing {fragment!r}"


def main():
    dimensions = {
        "On Enemy/water-burst/water-burst1.png": (64, 64),
        "On Enemy/water-burst/water-burst14.png": (64, 64),
        "On Enemy/water-eruption/water-eruption.png": (330, 308),
        "On Enemy/water-vortex/water-vortex-start.png": (512, 384),
        "On Enemy/water-vortex/water-vortex-end.png": (384, 384),
        "On Enemy/explosion/explosion.png": (576, 48),
        "On Enemy/fire-pillar/fire-pillar1.png": (32, 32),
        "On Enemy/fire-pillar/fire-pillar10.png": (32, 32),
        "On Enemy/hells-fire/halls-fire4.png": (32, 32),
        "On Enemy/hells-fire/halls-fire17.png": (32, 32),
        "On Enemy/hells-blaze/hells-blaze.png": (528, 64),
        "On Enemy/hells-inferno/hells-inferno.png": (832, 64),
        "On Enemy/wind-slash/wind-slash3.png": (32, 32),
        "On Enemy/wind-slash/wind-slash8.png": (32, 32),
        "Projectiles/rock-throw/rock-throw.png": (288, 64),
        "Projectiles/enemy-fire-basic/enemy-fire-basic.png": (528, 48),
        "Projectiles/enemy-air-basic/enemy-air-basic.png": (256, 32),
        "Projectiles/water-ball/water-ball-stgart.png": (320, 320),
        "Projectiles/water-ball/water-ball-end.png": (256, 256),
        "Projectiles/blue-dragon-magic/blue-dragon-magic.png": (480, 240),
        "Projectiles/blow-smoke/blow-smoke1.png": (32, 16),
        "Projectiles/blow-smoke/blow-smoke8.png": (32, 16),
        "Projectiles/holy-magic/holy-magic1.png": (32, 32),
        "Projectiles/holy-magic/holy-magic11.png": (32, 32),
        "Projectiles/summon-bat-vampirism-reverse/summon-bat-vampirism-reverse.png": (256, 32),
        "Projectiles/chain-lightning/A/chain-lightning-A1.png": (64, 32),
        "Projectiles/chain-lightning/A/chain-lightning-A9.png": (64, 32),
        "Projectiles/chain-lightning/B/chain-lightning-B1.png": (64, 32),
        "Projectiles/chain-lightning/B/chain-lightning-B9.png": (64, 32),
        "Projectiles/chain-lightning/C/chain-lightning-C1.png": (64, 32),
        "Projectiles/chain-lightning/C/chain-lightning-C8.png": (64, 32),
        "On Player/dragon-breath/dragon-breath.png": (384, 144),
        "On Player/alchemy/alchemy.png": (192, 16),
        "On Player/divine-grace/divine-grace.png": (816, 48),
        "On Enemy/divine-retribution/divine-retribution.png": (768, 48),
        "On Enemy/corrosive-aura/corrosive-aura.png": (1024, 48),
        "On Player/black-demon-magic/black-demon-magic.png": (640, 48),
        "On Player/balrog-magic/balrog-magic.png": (864, 48),
        "On Player/green-dragon-magic/green-dragon-magic.png": (288, 96),
        "On Player/fire-dragon-magic/fire-dragon-magic.png": (1024, 32),
        "On Player/elder-dragon-fireshot/elder-dragon-fireshot.png": (624, 48),
        "On Player/elder-dragon-burn/elder-dragon-burn.png": (1024, 64),
        "On Player/true-defense/true-defense.png": (1408, 64),
        "On Player/otherworldly-being-magic/otherworldly-being-magic.png": (768, 64),
        "On Player/paladin-magic/paladin-magic.png": (1600, 80),
        "On Player/fire-kin-magic/fire-kin-magic.png": (624, 48),
        "On Player/ice-kin-magic/ice-kin-magic.png": (960, 32),
        "On Player/earth-kin-magic/earth-kin-magic.png": (288, 96),
        "On Enemy/fire-sword/fire-sword1.png": (32, 32),
        "On Enemy/fire-sword/fire-sword13.png": (32, 32),
        "On Enemy/ice-sword/ice-sword.png": (384, 32),
        "On Enemy/earth-sword/earth-sword.png": (192, 32),
    }
    for path, expected in dimensions.items():
        assert png_size(path) == expected, f"{path} changed from expected sheet geometry"

    client = read("Client_Base/src/orsc/mudclient.java")
    require(client, [
        "public static final int PROJECTILE_EFFECT_FRAME_SLOTS = 36;",
		"public static final int CUSTOM_PROJECTILE_COUNT = 34;",
        "public static final int PROJECTILE_EFFECT_SCENE_RANGE = CUSTOM_PROJECTILE_COUNT * PROJECTILE_EFFECT_FRAME_SLOTS;",
        "public static final int spriteProjectileEffectMirrorBase = spriteProjectileEffectBase + PROJECTILE_EFFECT_SCENE_RANGE;",
        "public static final int spriteProjectileStaticMirrorBase =",
        "private static final int IBAN_BLAST_COMBAT_EFFECT_SCENE_SIZE = 336;",
        'if ("water-eruption".equals(animationName))',
        "targetFrames, maxTargetSize, 5, 4, 20, 0);",
        'if ("water-vortex".equals(animationName))',
        "targetFrames, maxTargetSize, 4, 3, 12, 0);",
        "targetFrames, maxTargetSize, 3, 3, 9, loaded);",
        'if ("explosion".equals(animationName))',
        "targetFrames, maxTargetSize, 12, 1, 12, 0);",
        "targetFrames, maxTargetSize, 11, 1, 11, 0);",
        "targetFrames, maxTargetSize, 13, 1, 13, 0);",
        "targetFrames, maxTargetSize, 6, 2, 12, 0);",
        'if ("enemy-fire-basic".equals(animationName))',
        "targetFrames, maxTargetSize, 11, 1, 11, 0);",
        'if ("enemy-air-basic".equals(animationName))',
        "targetFrames, maxTargetSize, 8, 1, 8, 0);",
        '"water-ball-stgart.png"',
        "targetFrames, maxTargetSize, 5, 5, 20, 0);",
        "targetFrames, maxTargetSize, 4, 4, 16, loaded);",
        'if ("blue-dragon-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 5, 5, 25, 0);",
        'if ("green-dragon-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 9, 3, 27, 0);",
        'if ("fire-dragon-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 32, 1, 32, 0);",
        'if ("elder-dragon-fireshot".equals(animationName))',
        "targetFrames, maxTargetSize, 13, 1, 13, 0);",
        'if ("elder-dragon-burn".equals(animationName))',
        "targetFrames, maxTargetSize, 16, 1, 16, 0);",
        'if ("otherworldly-being-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 12, 1, 12, 0);",
        'if ("paladin-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 20, 1, 20, 0);",
        'if ("fire-kin-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 13, 1, 13, 0);",
        'if ("ice-kin-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 30, 1, 30, 0);",
        'if ("earth-kin-magic".equals(animationName))',
        "targetFrames, maxTargetSize, 6, 2, 12, 0);",
        'if ("ice-sword".equals(animationName))',
        "targetFrames, maxTargetSize, 12, 1, 12, 0);",
        'if ("earth-sword".equals(animationName))',
        "targetFrames, maxTargetSize, 6, 1, 6, 0);",
        'if ("dragon-breath".equals(animationName))',
        "targetFrames, maxTargetSize, 8, 3, 0, 3, 0);",
        "targetFrames, maxTargetSize, 8, 3, 8, 4, loaded);",
        "targetFrames, maxTargetSize, 8, 3, 16, 8, loaded);",
        'if ("alchemy".equals(animationName))',
        "targetFrames, maxTargetSize, 12, 1, 12, 0);",
        'if ("divine-grace".equals(animationName))',
        "targetFrames, maxTargetSize, 17, 1, 17, 0);",
        'if ("divine-retribution".equals(animationName))',
        "targetFrames, maxTargetSize, 16, 1, 16, 0);",
        'if ("corrosive-aura".equals(animationName))',
        'if ("true-defense".equals(animationName))',
        "targetFrames, maxTargetSize, 22, 1, 22, 0);",
        "ProjectileAnimationCatalog.getProjectileFallback(projectileId)",
        "loadProjectileAnimationSheet(",
        'getLegacyExternalAnimationFolder("Projectiles", legacyEffectName)',
        "private static final int THROWING_KNIFE_PROJECTILE_SCENE_SIZE = 32;",
        "private static final int SHURIKEN_PROJECTILE_SCENE_SIZE = 64;",
        "projectile.id == PROJECTILE_TYPES.THROWING_KNIFE.id()",
        "THROWING_KNIFE_PROJECTILE_SCENE_SIZE);",
        "projectile.id == PROJECTILE_TYPES.SHURIKEN.id()",
        "SHURIKEN_PROJECTILE_SCENE_SIZE);",
        "effectType == COMBAT_EFFECT_IBAN_BLAST",
        "return IBAN_BLAST_COMBAT_EFFECT_SCENE_SIZE;",
        "return Math.max(1, (baseSize * 3) / 2);",
        "public static final int COMBAT_EFFECT_DRAGON_BREATH = 38;",
        "public static final int COMBAT_EFFECT_DIVINE_GRACE = 39;",
        "public static final int COMBAT_EFFECT_DIVINE_RETRIBUTION = 40;",
        "public static final int COMBAT_EFFECT_CORROSIVE_AURA = 41;",
        "public static final int COMBAT_EFFECT_LESSER_DEMON_MAGIC = 42;",
        "public static final int COMBAT_EFFECT_GREATER_DEMON_MAGIC = 43;",
        "public static final int COMBAT_EFFECT_ENEMY_EARTH_BASIC = 44;",
        "public static final int COMBAT_EFFECT_BLACK_DEMON_MAGIC = 45;",
        "public static final int COMBAT_EFFECT_BALROG_MAGIC = 46;",
        "public static final int COMBAT_EFFECT_BATTLE_MAGE_AIR = 47;",
        "public static final int COMBAT_EFFECT_BATTLE_MAGE_EARTH = 48;",
        "public static final int COMBAT_EFFECT_BATTLE_MAGE_WATER = 49;",
        "public static final int COMBAT_EFFECT_BATTLE_MAGE_FIRE = 50;",
        "public static final int COMBAT_EFFECT_GREEN_DRAGON_MAGIC = 51;",
        "public static final int COMBAT_EFFECT_FIRE_DRAGON_MAGIC = 52;",
        "public static final int COMBAT_EFFECT_OTHERWORLDLY_BEING_MAGIC = 53;",
        "public static final int COMBAT_EFFECT_PALADIN_MAGIC = 54;",
        "public static final int COMBAT_EFFECT_FIRE_KIN_MAGIC = 55;",
        "public static final int COMBAT_EFFECT_ICE_KIN_MAGIC = 56;",
        "public static final int COMBAT_EFFECT_EARTH_KIN_MAGIC = 57;",
        "public static final int COMBAT_EFFECT_DRAGON_WEAPON_BREATH = 58;",
        "public static final int COMBAT_EFFECT_FIRE_SWORD = 59;",
        "public static final int COMBAT_EFFECT_ICE_SWORD = 60;",
        "public static final int COMBAT_EFFECT_EARTH_SWORD = 61;",
        "public static final int COMBAT_EFFECT_ELDER_DRAGON_FIRESHOT = 62;",
        "public static final int COMBAT_EFFECT_ELDER_DRAGON_BURN = 63;",
        "public static final int COMBAT_EFFECT_TRUE_DEFENSE = 64;",
		"public static final int COMBAT_EFFECT_COUNT = 77;",
        '"battering-ram", "dragon-breath",',
        '"divine-grace", "divine-retribution", "corrosive-aura", "lesser-demon-magic", "greater-demon-magic",',
        '"enemy-earth-basic", "black-demon-magic", "balrog-magic"',
        '"battle-mage-air", "battle-mage-earth", "battle-mage-water", "battle-mage-fire"',
        '"green-dragon-magic", "fire-dragon-magic"',
        '"otherworldly-being-magic", "paladin-magic"',
        '"fire-kin-magic", "ice-kin-magic", "earth-kin-magic", "dragon-weapon-breath",',
        '"fire-sword", "ice-sword", "earth-sword", "elder-dragon-fireshot", "elder-dragon-burn",',
        '"true-defense"',
        "drawDragonBreathOverlay(character, effect, x, y, width, height, drawWidth);",
        "return \"dragon-breath\";",
        "shouldMirrorDragonBreath(character.direction)",
        "var3.projectileMirrored = shouldMirrorProjectile(var16, var3);",
        "return screenX > 0;",
        "return mirrorX ? spriteProjectileStaticMirrorBase + projectile.id : projectile.id + spriteProjectile;",
        "private Sprite getMirroredStaticProjectileSprite(int projectileId)",
        "private static final int COMBAT_EFFECT_STANDARD_SCREEN_SIZE = 64;",
        "int drawWidth = getCombatEffectScreenWidth(",
        "int drawHeight = getCombatEffectScreenHeight(",
        "queuedCombatEffectX[queuedCombatEffectCount] = x + (width / 2) - (drawWidth / 2)",
    ], "mudclient.java")
    require(client, [
        '"dev/myworld/assets/sprites/UI/magic"',
        '"spore", "bolt", "enemy-fire-basic", "holy-magic",',
        '"summon-bat-vampirism-reverse", "shuriken", "enemy-air-basic", "enemy-water-basic", "blue-dragon-magic",',
        '"chain-lightning/A", "chain-lightning/B", "chain-lightning/C"',
        'return "zamoraks-void";',
        'return "claws-of-guthix";',
        'return "thunder-wave";',
        'return "varrok-teleport";',
    ], "mudclient.java")
    for icon_name in [
        "alchemy", "wind-slash", "water-eruption", "explosion",
        "water-vortex", "zamoraks-void", "claws-of-guthix",
        "thunder-wave", "varrok-teleport",
    ]:
        assert raw_png_size(MAGIC_UI_ASSETS / f"{icon_name}.png")[0] > 0, \
            f"magic UI asset missing for {icon_name}"

    graphics = read("Client_Base/src/orsc/graphics/two/MudClientGraphics.java")
    require(graphics, [
        "this.mudClientRef.isProjectileEffectSceneIndex(index)",
        "this.mudClientRef.getProjectileEffectSpriteForSceneIndex(index)",
    ], "MudClientGraphics.java")

    spell_names = ["Wind Slash", "Water Eruption", "Explosion", "Water Vortex", "Fire Pillar"]
    entity_handler = read("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java")
    require(entity_handler, spell_names, "EntityHandler.java")
    require(entity_handler, [
        "BOLT(20)",
        "ENEMY_FIRE_BASIC(21)",
        "HOLY_MAGIC(22)",
        "SUMMON_BAT_VAMPIRISM(23)",
        "SHURIKEN(24)",
        "BLUE_DRAGON_MAGIC(27)",
        "CHAIN_LIGHTNING_A(28)",
        "CHAIN_LIGHTNING_B(29)",
        "CHAIN_LIGHTNING_C(30)",
        'new SpriteDef("bolt projectile", mudclient.spriteProjectile + 2, "projectiles:2", 20)',
        'new SpriteDef("enemy fire basic projectile", mudclient.spriteProjectile + 1, "projectiles:1", 21)',
        'new SpriteDef("holy magic projectile", mudclient.spriteProjectile + 1, "projectiles:1", 22)',
        'new SpriteDef("summon bat vampirism projectile", mudclient.spriteProjectile + 1, "projectiles:1", 23)',
        'new SpriteDef("shuriken projectile", mudclient.spriteProjectile + 6, "projectiles:6", 24)',
        'new SpriteDef("blue dragon magic projectile", mudclient.spriteProjectile + 1, "projectiles:1", 27)',
        'new SpriteDef("chain lightning A projectile", mudclient.spriteProjectile + 1, "projectiles:1", 28)',
        'new SpriteDef("chain lightning B projectile", mudclient.spriteProjectile + 1, "projectiles:1", 29)',
        'new SpriteDef("chain lightning C projectile", mudclient.spriteProjectile + 1, "projectiles:1", 30)',
    ], "EntityHandler.java")
    require(read("server/conf/server/defs/SpellDef.xml"), spell_names, "SpellDef.xml")

    effects = read("server/src/com/openrsc/server/model/entity/update/CombatEffect.java")
    require(effects, [
        "public static final int WIND_SLASH = AIR_SLASH;",
        "public static final int WATER_ERUPTION = HURRICANE;",
        "public static final int EXPLOSION = FIRE_BOMB;",
        "public static final int WATER_VORTEX = KRAKEN;",
        "public static final int FIRE_PILLAR = PHOENIX;",
        "public static final int DRAGON_BREATH = 38;",
        "public static final int LESSER_DEMON_MAGIC = 42;",
        "public static final int GREATER_DEMON_MAGIC = 43;",
        "public static final int FIRE_KIN_MAGIC = 55;",
        "public static final int ICE_KIN_MAGIC = 56;",
        "public static final int EARTH_KIN_MAGIC = 57;",
        "public static final int DRAGON_WEAPON_BREATH = 58;",
        "public static final int FIRE_SWORD = 59;",
        "public static final int ICE_SWORD = 60;",
        "public static final int EARTH_SWORD = 61;",
        "public static final int DRAGON_WEAPON_SLASH_2 = 66;",
        "public static final int DIVINE_GRACE = 39;",
        "public static final int DIVINE_RETRIBUTION = 40;",
        "public static final int CORROSIVE_AURA = 41;",
    ], "CombatEffect.java")
    classification = read(
        "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"
    )
    require(classification, [
		"return CombatEffect.WATER_ERUPTION;",
        "return CombatEffect.EXPLOSION;",
        "return CombatEffect.WATER_VORTEX;",
        "return CombatEffect.FIRE_PILLAR;",
    ], "SpellClassification.java")

    print("PASS: legacy magic animations retain their supplied frames and sprite-sheet geometry")


if __name__ == "__main__":
    main()
