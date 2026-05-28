#!/usr/bin/env python3
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "dev" / "myworld" / "assets" / "animations"
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
        "On Enemy/explosion/explosion.png": (864, 48),
        "On Enemy/fire-pillar/fire-pillar1.png": (32, 32),
        "On Enemy/fire-pillar/fire-pillar10.png": (32, 32),
        "On Enemy/hells-fire/halls-fire4.png": (32, 32),
        "On Enemy/hells-fire/halls-fire17.png": (32, 32),
        "On Enemy/hells-blaze/hells-blaze.png": (528, 64),
        "On Enemy/hells-inferno/hells-inferno.png": (832, 64),
        "On Enemy/wind-slash/wind-slash3.png": (32, 32),
        "On Enemy/wind-slash/wind-slash8.png": (32, 32),
        "Projectiles/rock-throw/rock-throw.png": (288, 64),
        "Projectiles/water-ball/water-ball-stgart.png": (320, 320),
        "Projectiles/water-ball/water-ball-end.png": (256, 256),
        "Projectiles/blow-smoke/blow-smoke1.png": (32, 16),
        "Projectiles/blow-smoke/blow-smoke8.png": (32, 16),
        "On Player/dragon-breath/dragon-breath.png": (384, 144),
        "On Player/alchemy/alchemy.png": (192, 16),
    }
    for path, expected in dimensions.items():
        assert png_size(path) == expected, f"{path} changed from expected sheet geometry"

    client = read("Client_Base/src/orsc/mudclient.java")
    require(client, [
        "public static final int PROJECTILE_EFFECT_FRAME_SLOTS = 36;",
        'if ("water-eruption".equals(animationName))',
        "targetFrames, maxTargetSize, 5, 4, 20, 0);",
        'if ("water-vortex".equals(animationName))',
        "targetFrames, maxTargetSize, 4, 3, 12, 0);",
        "targetFrames, maxTargetSize, 3, 3, 9, loaded);",
        'if ("explosion".equals(animationName))',
        "targetFrames, maxTargetSize, 18, 1, 18, 0);",
        "targetFrames, maxTargetSize, 11, 1, 11, 0);",
        "targetFrames, maxTargetSize, 13, 1, 13, 0);",
        "targetFrames, maxTargetSize, 6, 2, 12, 0);",
        '"water-ball-stgart.png"',
        "targetFrames, maxTargetSize, 5, 5, 20, 0);",
        "targetFrames, maxTargetSize, 4, 4, 16, loaded);",
        'if ("dragon-breath".equals(animationName))',
        "targetFrames, maxTargetSize, 8, 3, 0, 3, 0);",
        "targetFrames, maxTargetSize, 8, 3, 8, 4, loaded);",
        "targetFrames, maxTargetSize, 8, 3, 16, 8, loaded);",
        'if ("alchemy".equals(animationName))',
        "targetFrames, maxTargetSize, 12, 1, 12, 0);",
        "final int throwingKnifeFrameCount = 8;",
        "public static final int COMBAT_EFFECT_DRAGON_BREATH = 38;",
        '"battering-ram", "dragon-breath"',
        "drawDragonBreathOverlay(character, effect, x, y, width, height, size);",
        "shouldMirrorDragonBreath(character.direction)",
    ], "mudclient.java")
    require(client, [
        '"dev/myworld/assets/sprites/UI/magic"',
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

    spell_names = ["Wind Slash", "Water Eruption", "Explosion", "Water Vortex", "Fire Pillar"]
    require(read("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"),
            spell_names, "EntityHandler.java")
    require(read("server/conf/server/defs/SpellDef.xml"), spell_names, "SpellDef.xml")

    effects = read("server/src/com/openrsc/server/model/entity/update/CombatEffect.java")
    require(effects, [
        "public static final int WIND_SLASH = AIR_SLASH;",
        "public static final int WATER_ERUPTION = HURRICANE;",
        "public static final int EXPLOSION = FIRE_BOMB;",
        "public static final int WATER_VORTEX = KRAKEN;",
        "public static final int FIRE_PILLAR = PHOENIX;",
        "public static final int DRAGON_BREATH = 38;",
    ], "CombatEffect.java")
    handler = read("server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java")
    require(handler, [
        "return CombatEffect.WIND_SLASH;",
        "return CombatEffect.WATER_ERUPTION;",
        "return CombatEffect.EXPLOSION;",
        "return CombatEffect.WATER_VORTEX;",
        "return CombatEffect.FIRE_PILLAR;",
    ], "SpellHandler.java")

    print("PASS: renamed magic animations use the supplied frames and sprite-sheet geometry")


if __name__ == "__main__":
    main()
