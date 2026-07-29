#!/usr/bin/env python3
"""Validate projectile attack-distance behavior and the Elder range override.

This test pins the current server contract, including default NPC behavior and
the approved definition-backed Elder Green Dragon exception.
"""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RANGE_UTILS = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java"
RANGE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java"
THROWING_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
MAGIC_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
LEGACY_NPC_RANGE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEventNpc.java"
NPC_PROFILE = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java"
NPC_BEHAVIOR = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java"
NPC_DEF = ROOT / "server/src/com/openrsc/server/external/NPCDef.java"
ENTITY_HANDLER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
ELDER_SPECIALS = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java"
CANNON = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/FireCannonEvent.java"
HOSTILE_PATH = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
MYWORLD_CONF = ROOT / "server/myworld.conf"
MYWORLD_HOST_CONF = ROOT / "server/myworld-host.conf"
MYWORLD_NPC_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldNpcLocs.json"
MYWORLD_NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefsMyWorld.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"Missing file: {path}")
    return path.read_text(encoding="utf-8")


def require(source: str, needle: str, description: str) -> None:
    if needle not in source:
        fail(f"Missing {description}: {needle}")


def int_constant(source: str, name: str) -> int:
    match = re.search(rf"\b{name}\s*=\s*(\d+)\s*;", source)
    if not match:
        fail(f"Could not read integer constant {name}")
    return int(match.group(1))


def config_int(path: Path, key: str) -> int:
    match = re.search(rf"^\s*{re.escape(key)}\s*:\s*(\d+)\b", read(path), re.MULTILINE)
    if not match:
        fail(f"Could not read {key} from {path.name}")
    return int(match.group(1))


def method_body(source: str, signature_fragment: str) -> str:
    start = source.find(signature_fragment)
    if start == -1:
        fail(f"Could not find method: {signature_fragment}")
    brace = source.find("{", start)
    if brace == -1:
        fail(f"Could not find method body: {signature_fragment}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    fail(f"Unclosed method body: {signature_fragment}")
    return ""


def chebyshev_in_range(dx: int, dy: int, radius: int) -> bool:
    return abs(dx) <= radius and abs(dy) <= radius


def assert_boundary(label: str, radius: int) -> None:
    if not chebyshev_in_range(radius, 0, radius):
        fail(f"{label} rejected its cardinal boundary")
    if not chebyshev_in_range(radius, radius, radius):
        fail(f"{label} rejected its diagonal boundary")
    if chebyshev_in_range(radius + 1, 0, radius):
        fail(f"{label} accepted a cardinal tile beyond its boundary")
    if chebyshev_in_range(radius + 1, radius + 1, radius):
        fail(f"{label} accepted a diagonal tile beyond its boundary")


def find_elder_location() -> dict:
    data = json.loads(read(MYWORLD_NPC_LOCS))
    for location in data["npclocs"]:
        if int(location["id"]) == 844:
            return location
    fail("MyWorldNpcLocs.json has no Elder Green Dragon (844)")
    return {}


def find_elder_override() -> dict:
    data = json.loads(read(MYWORLD_NPC_DEFS))
    for definition in data["npcs"]:
        if int(definition["id"]) == 844:
            return definition
    fail("NpcDefsMyWorld.json has no Elder Green Dragon (844)")
    return {}


def main() -> None:
    range_utils = read(RANGE_UTILS)
    npc_profile = read(NPC_PROFILE)
    npc_behavior = read(NPC_BEHAVIOR)
    entity_handler = read(ENTITY_HANDLER)
    projectile_event = read(PROJECTILE_EVENT)
    entity = read(ENTITY)
    elder_specials = read(ELDER_SPECIALS)

    bonus = int_constant(range_utils, "PLAYER_COMBAT_RANGE_BONUS")
    longbow = int_constant(range_utils, "DEFAULT_BOW_RANGE") + bonus
    shortbow_or_crossbow = int_constant(range_utils, "SHORT_BOW_RANGE") + bonus
    throwing_default = int_constant(range_utils, "DEFAULT_THROWING_RANGE") + bonus
    dart = int_constant(range_utils, "THROWING_DART_RANGE") + bonus
    spell = config_int(MYWORLD_CONF, "spell_range_distance") + bonus
    hosted_spell = config_int(MYWORLD_HOST_CONF, "spell_range_distance") + bonus
    npc_default = int_constant(npc_profile, "DEFAULT_PROJECTILE_RANGE")
    elder_projectile = int(find_elder_override().get("projectileRange", 0))
    elder_aoe = int_constant(elder_specials, "AOE_RADIUS")
    cannon = int_constant(read(CANNON), "MAX_DISTANCE")

    expected = {
        "longbow": (longbow, 7),
        "shortbow/crossbow": (shortbow_or_crossbow, 6),
        "throwing knife/shuriken": (throwing_default, 5),
        "throwing dart": (dart, 6),
        "manual/autocast spell": (spell, 6),
        "hosted spell": (hosted_spell, 6),
        "default modern NPC projectile": (npc_default, 5),
        "Elder normal projectile": (elder_projectile, 7),
        "Elder projectile AOE": (elder_aoe, 6),
        "multicannon targeting": (cannon, 8),
    }
    for label, (actual, wanted) in expected.items():
        if actual != wanted:
            fail(f"{label} range changed: expected characterized value {wanted}, found {actual}")
        assert_boundary(label, actual)

    require(
        range_utils,
        "isCrossbow(weaponId) || isShortBow(weaponId) ? SHORT_BOW_RANGE : DEFAULT_BOW_RANGE",
        "bow-family range selection",
    )
    require(
        range_utils,
        "THROWING_DARTS.contains(throwingEquip) ? THROWING_DART_RANGE : DEFAULT_THROWING_RANGE",
        "throwing-family range selection",
    )
    require(read(RANGE_EVENT), "player.withinRange(target, radius)", "bow per-tick range check")
    require(read(RANGE_EVENT), "PathValidation.checkPath(", "bow launch collision check")
    require(read(THROWING_EVENT), "player.withinRange(target, attackRadius)", "thrown per-tick range check")
    require(read(THROWING_EVENT), "PathValidation.checkPath(", "thrown launch collision check")
    require(read(MAGIC_EVENT), "player.withinRange(target, spellRange)", "magic per-tick range check")
    require(read(MAGIC_EVENT), "PathValidation.checkPath(", "magic launch collision check")

    require(read(NPC_DEF), "public int projectileRange;", "NPC projectile range field")
    require(read(NPC_DEF), "public int getProjectileRange()", "NPC projectile range getter")
    require(
        entity_handler,
        'npc.has("projectileRange")',
        "optional NPC projectile range definition loading",
    )
    if entity_handler.count("readProjectileRange(npc,") != 3:
        fail("base, patch, and My World NPC definition paths must share projectile-range validation")
    require(
        entity_handler,
        "projectileRange must be between 1 and 15",
        "NPC projectile range loader bounds",
    )
    require(
        npc_profile,
        "npc.getDef().getProjectileRange() > 0",
        "positive NPC range override selection",
    )
    require(npc_profile, "return DEFAULT_PROJECTILE_RANGE;", "default modern NPC projectile range")
    require(npc_profile, "if (isDragon(npc))", "dragon profile selection")
    require(npc_profile, "return MELEE_MAGIC;", "dragon melee/magic profile")
    require(npc_behavior, "Math.max(Math.abs(npc.getX() - target.getX())", "NPC Chebyshev distance")
    require(npc_behavior, "profile.prefersProjectileAtDistance(npc, distance)", "NPC distance preference gate")
    require(npc_behavior, "npc.withinRange(target, profile.getProjectileRange(npc))", "NPC firing-range gate")
    require(
        npc_behavior,
        "PathValidation.checkHostileProjectilePath(",
        "modern hostile launch collision gate",
    )
    require(
        entity,
        "return xDiff <= radius && yDiff <= radius;",
        "entity square/Chebyshev range formula",
    )

    legacy_body = method_body(read(LEGACY_NPC_RANGE), "private boolean isUnreachable")
    require(legacy_body, "int radius = 5;", "legacy NPC range boundary")
    require(
        read(LEGACY_NPC_RANGE),
        "PathValidation.checkHostileProjectilePath(",
        "legacy hostile launch collision gate",
    )
    require(
        read(HOSTILE_PATH),
        "public static boolean checkHostileProjectilePath",
        "semantic hostile-projectile collision API",
    )

    action = method_body(projectile_event, "public void action()")
    require(action, "caster.withinRange(opponent, 15)", "delayed projectile delivery cap")
    if "PathValidation" in action or "getProjectileRange" in action:
        fail("Projectile delivery unexpectedly rechecks launch collision or attack range")
    assert_boundary("delayed projectile delivery", 15)

    require(
        elder_specials,
        "isValidProjectilePlayerTarget(dragon, player, AOE_RADIUS)",
        "Elder AOE launch range/collision gate",
    )
    fireshot_action = method_body(elder_specials, "public void action()")
    require(
        fireshot_action,
        "isValidPlayerTarget(dragon, player, AOE_RADIUS)",
        "Elder fireshot delayed range recheck",
    )
    if "isValidProjectilePlayerTarget" in fireshot_action:
        fail("Elder fireshot delivery unexpectedly rechecks launch collision")

    elder = find_elder_location()
    expected_elder = {
        "start": (263, 3430),
        "min": (249, 3416),
        "max": (277, 3444),
    }
    for key, (x, y) in expected_elder.items():
        point = elder[key]
        if (int(point["X"]), int(point["Y"])) != (x, y):
            fail(f"Elder {key} changed: expected {(x, y)}, found {(point['X'], point['Y'])}")
    expanded = (
        int(elder["min"]["X"]) - 4,
        int(elder["min"]["Y"]) - 4,
        int(elder["max"]["X"]) + 4,
        int(elder["max"]["Y"]) + 4,
    )
    if expanded != (245, 3412, 281, 3448):
        fail(f"Unexpected Elder expanded target/leash bounds: {expanded}")
    require(
        npc_behavior,
        "target.getX() < (npc.getLoc().minX() - 4)",
        "modern NPC horizontal leash check",
    )
    require(
        npc_behavior,
        "target.getY() < (npc.getLoc().minY() - 4)",
        "modern NPC vertical leash check",
    )

    print("PASS: projectile attack-distance audit characterization validated")


if __name__ == "__main__":
    main()
