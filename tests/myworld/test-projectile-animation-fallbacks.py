#!/usr/bin/env python3

import re
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANIMATIONS = ROOT / "dev/myworld/assets/runtime/animations"
LEGACY_ANIMATIONS = ROOT / "dev/myworld/assets/runtime/legacy-animations"
CATALOG_PATH = ROOT / "Client_Base/src/orsc/graphics/two/ProjectileAnimationCatalog.java"
CLIENT_PATH = ROOT / "Client_Base/src/orsc/mudclient.java"
SPELL_HANDLER_PATH = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
SPELL_CLASSIFICATION_PATH = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellClassification.java"
NPC_PROFILE_PATH = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java"
BUILD_PATH = ROOT / "Client_Base/build.xml"
EXPORTER_PATH = ROOT / "tools/myworld/ExportBasicProjectileSheets.java"


EXPECTED_DEFINITIONS = {
    "acid-basic": ("acid-basic/Acid VFX 01.png", 16, 1, 0, 10),
    "earth-basic": ("earth-basic/Earth projectile Spritesheet .png", 9, 2, 0, 6),
    "fire-basic": ("fire-basic/Firebolt SpriteSheet.png", 11, 1, 0, 4),
    "ice-basic": ("ice-basic/IceVFX 1 Repeatable.png", 10, 1, 0, 10),
    "thunder-basic": ("thunder-basic/Thunder ball wo blur.png", 9, 2, 0, 9),
    "water-basic": ("water-basic/WaterBall - Startup and Infinite.png", 5, 5, 0, 21),
    "wind-basic": ("wind-basic/Projectile 2.png", 8, 1, 0, 8),
    "wood-basic": ("wood-basic/Wood VFX 01 Repeatable.png", 8, 1, 0, 8),
    "holy-basic": ("holy-basic/Holy VFX 01 Repeatable.png", 8, 1, 0, 8),
    "arrow-basic": ("arrow-basic/arrow.png", 1, 1, 0, 1),
    "bolt-basic": ("bolt-basic/bolt-basic.png", 1, 1, 0, 1),
    "dart-basic": ("dart-basic/dart.png", 1, 1, 0, 1),
    "throwing-knife-basic": ("throwing-knife-basic/throwing-knife-basic.png", 8, 1, 0, 8),
    "shuriken-basic": ("shuriken-basic/shuriken-basic.png", 8, 1, 0, 8),
    "thunder-2": ("thunder-2/Projectile/Projectile 2 wo blur.png", 16, 1, 0, 16),
    "acid-basic-2": ("acid-basic-2/Acid VFX 03(56x48).png", 16, 1, 0, 16),
    "electric-disc-basic": ("Eletric Disc (32x32)/Eletricity Disc wo blur.png", 13, 1, 7, 6),
}

EXPECTED_STARTUP_SEGMENTS = {
    "ice-basic": [("ice-basic/Ice VFX 1 Start.png", 3, 1, 0, 3)],
    "holy-basic": [("holy-basic/Holy VFX 01 Initial.png", 2, 1, 0, 2)],
    "electric-disc-basic": [("Eletric Disc (32x32)/Eletricity Disc wo blur.png", 13, 1, 0, 6)],
}

EXPECTED_IMPACT_SEGMENTS = {
    "acid-basic": [("acid-basic/Acid VFX 01.png", 16, 1, 10, 6)],
    "earth-basic": [("earth-basic/Earth projectile Spritesheet .png", 9, 2, 6, 10)],
    "fire-basic": [("fire-basic/Firebolt SpriteSheet.png", 11, 1, 5, 6)],
    "ice-basic": [("ice-basic/Ice VFX 1 Hit.png", 8, 1, 0, 8)],
    "thunder-basic": [("thunder-basic/Thunder ball wo blur.png", 9, 2, 9, 7)],
    "water-basic": [("water-basic/WaterBall - Impact.png", 4, 4, 0, 16)],
    "wind-basic": [("wind-basic/Projectile 2 impact.png", 6, 1, 0, 6)],
    "wood-basic": [("wood-basic/Wood VFX 01 Hit.png", 7, 1, 0, 7)],
    "holy-basic": [("holy-basic/Holy VFX 01 Impact.png", 7, 1, 0, 7)],
    "thunder-2": [("thunder-2/Hit/Thunder hit wo blur.png", 6, 1, 0, 6)],
}

EXPECTED_FALLBACKS = {
    "FIREBALL": "fire-basic",
    "WIND_ARROW": "wind-basic",
    "ROCK_THROW": "earth-basic",
    "WATER_BALL": "water-basic",
    "THROWING_KNIFE": "throwing-knife-basic",
    "ARROW": "arrow-basic",
    "THROWING_DART": "dart-basic",
    "CLAWS_OF_GUTHIX": "holy-basic",
    "THUNDER_BALL": "thunder-basic",
    "ICICLE_SHOT": "ice-basic",
    "ACID_DROP": "acid-basic",
    "BRANCH_SPORE": "wood-basic",
    "BOLT": "bolt-basic",
    "ENEMY_FIRE_BASIC": "fire-basic",
    "HOLY_MAGIC": "holy-basic",
    "SHURIKEN": "shuriken-basic",
    "ENEMY_AIR_BASIC": "wind-basic",
    "ENEMY_WATER_BASIC": "water-basic",
    "BLUE_DRAGON_MAGIC": "water-basic",
    "CHAIN_LIGHTNING_A": "thunder-basic",
    "CHAIN_LIGHTNING_B": "thunder-basic",
    "CHAIN_LIGHTNING_C": "thunder-basic",
    "THUNDER_BIRD": "thunder-2",
    "EARTH_LEAD_2": "earth-basic",
    "FIRE_LEAD_2": "fire-basic",
    "ICE_LEAD_2": "ice-basic",
    "ACID_LEAD_2": "acid-basic",
    "WOOD_LEAD_2": "wood-basic",
    "ACID_ARMOR_PROC": "acid-basic-2",
    "ELECTRIC_DISC": "electric-disc-basic",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        fail(f"Not a PNG spritesheet: {path.relative_to(ROOT)}")
    return struct.unpack(">II", data[16:24])


def parse_segments(
    catalog: str,
    map_name: str,
    constant_name: str,
) -> dict[str, list[tuple[str, int, int, int, int]]]:
    block = re.search(
        rf"LinkedHashMap<String, Segment\[]> {map_name} =.*?"
        rf"{constant_name} = Collections\.unmodifiableMap\({map_name}\);",
        catalog,
        re.DOTALL,
    )
    if block is None:
        fail(f"missing {map_name} projectile phase map")
    result = {}
    for phase in re.finditer(
        r'phases\([^,]+, "([^"]+)",\s*(.*?)\);', block.group(0), re.DOTALL
    ):
        result[phase.group(1)] = [
            (match.group(1), *(int(match.group(index)) for index in range(2, 6)))
            for match in re.finditer(
                r'segment\("([^"]+)", (\d+), (\d+), (\d+), (\d+)\)', phase.group(2)
            )
        ]
    return result


def validate_segments(
    moving_root: Path,
    segments: dict[str, list[tuple[str, int, int, int, int]]],
    capacity: int,
) -> None:
    for key, key_segments in segments.items():
        total_frames = 0
        for relative_path, columns, rows, first_frame, frame_count in key_segments:
            sheet = moving_root / relative_path
            if not sheet.is_file():
                fail(f"missing {key} phase spritesheet: {sheet.relative_to(ROOT)}")
            width, height = png_size(sheet)
            if width % columns or height % rows:
                fail(f"{key} phase sheet {width}x{height} is not divisible by {columns}x{rows}")
            if first_frame + frame_count > columns * rows:
                fail(f"{key} phase frame range exceeds its declared grid")
            total_frames += frame_count
        if total_frames > capacity:
            fail(f"{key} phases exceed the {capacity}-frame buffer")


def main() -> None:
    if not ANIMATIONS.is_dir() or not LEGACY_ANIMATIONS.is_dir():
        fail("new and temporary legacy animation roots must both exist during migration")
    categories = sorted(path.name for path in ANIMATIONS.iterdir() if path.is_dir())
    if categories != ["on-entity", "projectile-moving", "projectile-static"]:
        fail(f"new animation root must expose exactly the three render categories, got {categories}")

    catalog = CATALOG_PATH.read_text(encoding="utf-8")
    definitions = {
        match.group(1): (
            match.group(2),
            int(match.group(3)),
            int(match.group(4)),
            int(match.group(5)),
            int(match.group(6)),
        )
        for match in re.finditer(
            r'define\(definitions, "([^"]+)", "[^"]+", "([^"]+)", (\d+), (\d+), (\d+), (\d+)\);',
            catalog,
        )
    }
    if definitions != EXPECTED_DEFINITIONS:
        fail(f"projectile fallback definitions changed: {definitions}")

    moving_root = ANIMATIONS / "projectile-moving"
    for key, (relative_path, columns, rows, first_frame, frame_count) in definitions.items():
        if key not in ("thunder-2", "acid-basic-2") and not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*-basic", key):
            fail(f"fallback key must use [type]-basic naming: {key}")
        sheet = moving_root / relative_path
        if not sheet.is_file():
            fail(f"missing {key} spritesheet: {sheet.relative_to(ROOT)}")
        width, height = png_size(sheet)
        if width % columns or height % rows:
            fail(f"{key} sheet {width}x{height} is not divisible by {columns}x{rows}")
        if first_frame + frame_count > columns * rows:
            fail(f"{key} frame range exceeds its declared grid")
        if frame_count > 36:
            fail(f"{key} exceeds current projectile frame capacity")

    startup_segments = parse_segments(catalog, "startupSegments", "STARTUP_SEGMENTS")
    if startup_segments != EXPECTED_STARTUP_SEGMENTS:
        fail(f"projectile startup phases changed: {startup_segments}")
    impact_segments = parse_segments(catalog, "impactSegments", "IMPACT_SEGMENTS")
    if impact_segments != EXPECTED_IMPACT_SEGMENTS:
        fail(f"projectile impact phases changed: {impact_segments}")
    validate_segments(moving_root, startup_segments, 36)
    validate_segments(moving_root, impact_segments, 36)
    for snippet in (
        'impactSize(impactScreenSizes, "fire-basic", 48);',
        'impactSize(impactScreenSizes, "water-basic", 48);',
    ):
        if snippet not in catalog:
            fail(f"projectile catalog is missing impact presentation data: {snippet}")
    for key, key_segments in startup_segments.items():
        startup_frames = sum(segment[4] for segment in key_segments)
        if startup_frames + definitions[key][4] > 36:
            fail(f"{key} startup and travel phases exceed the 36-frame movement buffer")

    fallback_matches = {
        match.group(1): match.group(2)
        for match in re.finditer(
            r'fallback\(fallbacks, PROJECTILE_TYPES\.([A-Z0-9_]+), "([^"]+)"\);',
            catalog,
        )
    }
    if fallback_matches != EXPECTED_FALLBACKS:
        fail(f"projectile id fallback assignments changed: {fallback_matches}")

    for rotating_key in ("throwing-knife-basic", "shuriken-basic"):
        sheet_path = moving_root / EXPECTED_DEFINITIONS[rotating_key][0]
        if png_size(sheet_path) != (512, 64):
            fail(f"{rotating_key} must retain eight 64x64 rotation cells")

    client = CLIENT_PATH.read_text(encoding="utf-8")
    for snippet in (
        "ProjectileAnimationCatalog.getProjectileFallback(projectileId)",
        "loadProjectileAnimationSheet(",
        "ProjectileAnimationCatalog.getStartupSegments(definition.getKey())",
        "loadProjectileImpactAnimationSheets(",
        "ProjectileAnimationCatalog.getImpactSegments(definition.getKey())",
        "frameCount * PROJECTILE_IMPACT_FRAME_TICKS",
        "startGenericProjectileImpact(character);",
        "projectileImpactMirrorSprites",
        "character.projectileMirrored",
        "getMirroredProjectileImpactSprite",
        'getLegacyExternalAnimationFolder("Projectiles", legacyEffectName)',
        "spriteCombatEffectBase + (COMBAT_EFFECT_COUNT * COMBAT_EFFECT_FRAME_SLOTS)",
    ):
        if snippet not in client:
            fail(f"client is missing projectile catalog integration: {snippet}")

    numeric_constant = lambda name: int(re.search(
        rf"(?:public|private) static final int {name} = (\d+);", client
    ).group(1))
    combat_effect_end = (
        numeric_constant("spriteCombatEffectBase")
        + numeric_constant("COMBAT_EFFECT_COUNT") * numeric_constant("COMBAT_EFFECT_FRAME_SLOTS")
    )
    projectile_scene_range = (
        numeric_constant("CUSTOM_PROJECTILE_COUNT") * numeric_constant("PROJECTILE_EFFECT_FRAME_SLOTS")
    )
    projectile_effect_base = combat_effect_end
    projectile_mirror_base = projectile_effect_base + projectile_scene_range
    projectile_static_mirror_base = projectile_mirror_base + projectile_scene_range
    if not (combat_effect_end <= projectile_effect_base
            < projectile_mirror_base < projectile_static_mirror_base):
        fail("combat, original projectile, and mirrored projectile scene ranges must be disjoint")
    if "public static final int spriteProjectileEffectBase = 53000;" in client:
        fail("projectile scene IDs must not overlap the combat-effect range")
    for retired in ("generateThrowingKnifeProjectileFrames", "generateShurikenProjectileFrames",
                    "isRootedProjectile", "isCasterRootedProjectile"):
        if retired in client:
            fail(f"client still contains retired projectile behavior: {retired}")

    direction_guard = re.search(
        r"private boolean shouldMirrorProjectile\(ORSCharacter caster, ORSCharacter victim\) \{(.*?)\n\t\}",
        client,
        re.DOTALL,
    )
    if direction_guard is None:
        fail("client is missing the moving-projectile direction guard")
    direction_body = direction_guard.group(1)
    for snippet in ("return deltaX > 0;", "return screenX > 0;"):
        if snippet not in direction_body:
            fail("projectiles must mirror only when their target is left of the caster")

    static_catalog = (ROOT / "Client_Base/src/orsc/graphics/two/ProjectileStaticAnimationCatalog.java").read_text(
        encoding="utf-8"
    )
    if '12, 1, 0, 12, 32);' not in static_catalog:
        fail("Wind Slash must use all twelve authored 48x32 frames")
    for snippet in (
        "int renderLength = length;",
        "int thickness = Math.max(1, definition.getThickness());",
        "int centerX = (caster.sceneScreenCenterX + victim.sceneScreenCenterX) / 2;",
    ):
        if snippet not in client:
            fail(f"client is missing shared static-projectile placement: {snippet}")
    if "getDirectionalFixedSize" in client or "directionalFixedSizes" in static_catalog:
        fail("Wind Slash must use the same caster-to-target layout as Water Burst")
    if "anchorAnimationFrameToVisibleStart" in client:
        fail("Wind Slash must not rewrite source frames to fit Water Burst's stretch layout")

    if "private static final int BRANCH_SPORE_PROJECTILE_SCENE_SIZE = 96;" not in client:
        fail("wood-basic Spore must retain its half-size 96px scene footprint")
    if not re.search(
        r"projectile\.id == PROJECTILE_TYPES\.BRANCH_SPORE\.id\(\)\) \{\s*"
        r"return BRANCH_SPORE_PROJECTILE_SCENE_SIZE;",
        client,
    ):
        fail("Spore must use its dedicated projectile scene size")

    spell_handler = SPELL_HANDLER_PATH.read_text(encoding="utf-8")
    spell_classification = SPELL_CLASSIFICATION_PATH.read_text(encoding="utf-8")
    for projectile in ("WIND_ARROW", "ROCK_THROW", "WATER_BALL", "FIREBALL",
                       "THUNDER_BALL", "ICICLE_SHOT", "ACID_DROP", "BRANCH_SPORE"):
        if f"return Projectile.{projectile};" not in spell_classification:
            fail(f"player entry spell fallback missing Projectile.{projectile}")
    if "static int getGodSpellProjectileVisual" not in spell_classification \
            or "return Projectile.HOLY_MAGIC;" not in spell_classification:
        fail("god spell projectiles must use holy-basic through HOLY_MAGIC")
    if "getGodSpellProjectileVisual(spellEnum)" not in spell_handler:
        fail("SpellHandler must consume the classified god-spell projectile")

    npc_profile = NPC_PROFILE_PATH.read_text(encoding="utf-8")
    for projectile in ("WIND_ARROW", "WATER_BALL", "ROCK_THROW", "FIREBALL",
                       "THUNDER_BALL", "BRANCH_SPORE", "HOLY_MAGIC"):
        if f"return Projectile.{projectile};" not in npc_profile:
            fail(f"NPC fallback routing missing Projectile.{projectile}")

    build = BUILD_PATH.read_text(encoding="utf-8")
    for include in ('<include name="animations/**/*.png"/>',
                    '<include name="legacy-animations/**/*.png"/>'):
        if include not in build:
            fail(f"client packaging missing temporary animation input: {include}")

    exporter = EXPORTER_PATH.read_text(encoding="utf-8")
    for snippet in ('"80".equals(entry.getID())', "frame * 45.0D", "FRAME_COUNT = 8"):
        if snippet not in exporter:
            fail(f"rotation sheet exporter changed legacy behavior: {snippet}")

    print("PASS: spritesheet-first basic projectile fallbacks validated")


if __name__ == "__main__":
    main()
