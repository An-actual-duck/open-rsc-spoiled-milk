#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASE_NPC_DEFS = ROOT / "server" / "conf" / "server" / "defs" / "NpcDefs.json"
CUSTOM_NPC_DEFS = ROOT / "server" / "conf" / "server" / "defs" / "NpcDefsCustom.json"
MYWORLD_NPC_DEFS = ROOT / "server" / "conf" / "server" / "defs" / "NpcDefsMyWorld.json"
NPC = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "Npc.java"
NPC_BEHAVIOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcBehavior.java"
NPC_ATTACK_STYLE_PROFILE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcAttackStyleProfile.java"
NPC_COMBAT_PROFILE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcCombatProfile.java"
NPC_MAGIC_ATTACK = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcMagicAttack.java"
COMBAT_EFFECT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "update" / "CombatEffect.java"

EXPECTED_PROFILES = {
    "PURE_MAGIC": {
        "darkwizard",
        "wizard",
        "monk",
        "chaos druid",
        "druid",
        "witch",
        "necromancer",
        "skeleton mage",
        "battle mage",
    },
    "PURE_RANGED": {
        "guard",
        "jailguard",
        "carnillean guard",
        "goblin guard",
        "ogre guard",
        "bedabin nomad guard",
        "draft mercenary guard",
        "rowdy guard",
        "shantay pass guard",
        "gnome guard",
        "thief",
        "rogue",
        "head thief",
    },
    "MELEE_MAGIC": {
        "monk of zamorak",
        "chaos druid warrior",
        "paladin",
        "lesser demon",
        "greater demon",
        "black demon",
        "moss giant",
        "ice giant",
        "fire giant",
        "fire warrior",
        "delrith",
        "lucien",
        "ghost",
        "tree spirit",
        "ice warrior",
        "ice queen",
        "the fire warrior of lesarkus",
        "chronozon",
        "nazastarool ghost",
        "otherworldly being",
        "salarin the twisted",
    },
    "MELEE_RARE_MAGIC": {
        "black knight",
        "white knight",
        "grey knight",
        "black knight titan",
    },
    "MELEE_RANGED": {
        "mercenary",
        "mercenary captain",
        "khazard troop",
        "gnome troop",
        "pirate",
        "bandit",
        "tribesman",
        "yanille watchman",
    },
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_contains(path: Path, needle: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle not in text:
        fail(f"{path.name} missing expected text: {needle}")


def require_not_contains(path: Path, needle: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle in text:
        fail(f"{path.name} still contains retired text: {needle}")


def load_json_array(path: Path, key: str) -> list[dict]:
    if not path.exists():
        fail(f"Missing file: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data.get(key)
    if not isinstance(entries, list):
        fail(f"{path.name} must contain a top-level '{key}' array")
    return entries


def load_merged_npcs() -> dict[int, dict]:
    merged: dict[int, dict] = {}
    for entry in load_json_array(BASE_NPC_DEFS, "npcs"):
        merged[int(entry["id"])] = dict(entry)
    for entry in load_json_array(CUSTOM_NPC_DEFS, "npcs"):
        merged[int(entry["id"])] = dict(entry)
    for entry in load_json_array(MYWORLD_NPC_DEFS, "npcs"):
        npc_id = int(entry["id"])
        if npc_id not in merged:
            fail(f"NpcDefsMyWorld.json overrides unknown npc id {npc_id}")
        merged[npc_id].update(entry)
    return merged


def require_profile_case(profile_text: str, npc_name: str, profile: str) -> None:
    if npc_name == "wizard" and profile == "PURE_MAGIC":
        if 'name.contains("wizard")' not in profile_text or "return PURE_MAGIC;" not in profile_text:
            fail("NpcAttackStyleProfile.java does not map wizard-named NPCs to PURE_MAGIC")
        return
    if npc_name == "monk" and profile == "PURE_MAGIC":
        if "prefersHolyMagicOnly(name)" not in profile_text or '"monk".equals(name)' not in profile_text:
            fail("NpcAttackStyleProfile.java does not map Saradomin-style monks to PURE_MAGIC")
        return
    if profile == "MELEE_RARE_MAGIC":
        if "isGodKnightNpcName(name)" not in profile_text or "return MELEE_RARE_MAGIC;" not in profile_text:
            fail("NpcAttackStyleProfile.java does not map god knights to MELEE_RARE_MAGIC")
        return
    pattern = rf'case "{re.escape(npc_name)}":(?:(?!return [A-Z_]+;).)*return {profile};'
    if not re.search(pattern, profile_text, re.DOTALL):
        fail(f"NpcAttackStyleProfile.java does not map '{npc_name}' to {profile}")


def ensure_profiled_npcs_have_offense(npcs: dict[int, dict]) -> None:
    profiled_names = {
        npc_name
        for names in EXPECTED_PROFILES.values()
        for npc_name in names
    }
    seen_attackable_names: set[str] = set()
    for npc_id, npc in sorted(npcs.items()):
        name = str(npc.get("name", "")).lower()
        if name not in profiled_names or int(npc.get("attackable", 0)) != 1:
            continue
        seen_attackable_names.add(name)
        offense = max(int(npc.get("attack", 0)), int(npc.get("strength", 0)))
        if offense <= 0:
            fail(f"Profiled attackable npc {npc_id} '{npc.get('name')}' has no ranged/magic offense source")

    missing_attackable = sorted(profiled_names - seen_attackable_names)
    if missing_attackable:
        fail(f"Profile has no attackable npc definitions for: {', '.join(missing_attackable)}")


def main() -> None:
    profile_text = NPC_ATTACK_STYLE_PROFILE.read_text(encoding="utf-8")
    for profile, names in EXPECTED_PROFILES.items():
        for npc_name in names:
            require_profile_case(profile_text, npc_name, profile)

    require_contains(NPC_ATTACK_STYLE_PROFILE, "npc.getWorld().getServer().getCombatRandom().nextInt(100) < 65")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "npc.getWorld().getServer().getCombatRandom().nextInt(100) < 10")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "MELEE_RARE_MAGIC")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "Math.max(1, Math.max(npc.getDef().getAtt(), npc.getDef().getStr()))")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return Math.max(1.0D, npc.getMagicOffense() / 12.0D);")

    require_contains(NPC, "int explicitOffense = getDef().getMeleeOffense();")
    require_contains(NPC, "int explicitOffense = getDef().getRangedOffense();")
    require_contains(NPC, "int explicitOffense = getDef().getMagicOffense();")
    require_contains(NPC, "NpcCombatProfile.resolve(this).getRangedOffense()")
    require_contains(NPC, "return getDef().getRanged();")
    require_contains(NPC, "return NpcCombatProfile.resolve(this).getMagicOffense();")

    require_contains(NPC_COMBAT_PROFILE, "NpcAttackStyleProfile.forNpc(npc)")
    require_contains(NPC_BEHAVIOR, "final NpcCombatProfile profile = NpcCombatProfile.resolve(npc);")
    require_contains(NPC_BEHAVIOR, "profile.prefersProjectileAtDistance(distance)")
    require_contains(NPC_BEHAVIOR, "else if (npc.inCombat())")
    require_contains(NPC_BEHAVIOR, "target = npc.getOpponent();")
    require_contains(NPC_BEHAVIOR, "tryProjectileAttack(now);")
    require_contains(NPC_BEHAVIOR, "PathValidation.checkCombatProjectilePath(")
    require_contains(NPC_BEHAVIOR, "npc.getWorldLocation()")
    require_contains(NPC_BEHAVIOR, "target.getWorldLocation()")
    require_contains(NPC_BEHAVIOR, "CombatFormula.doRangedDamage(npc, ItemId.LONGBOW.id(), ItemId.BRONZE_ARROWS.id(), target, false)")
    require_contains(NPC_BEHAVIOR, "profile.getRangedProjectileVisual()")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return Projectile.THROWING_KNIFE;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return Projectile.THROWING_DART;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return Projectile.BOLT;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return Projectile.FIREBALL;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return Projectile.HOLY_MAGIC;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("wizard")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("zamorak")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("saradomin")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("druid")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("priest")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("black knight")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("white knight")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, 'name.contains("grey knight")')
    require_contains(NPC_ATTACK_STYLE_PROFILE, '"monk".equals(name)')
    require_not_contains(NPC_ATTACK_STYLE_PROFILE, 'case "gnome baller":')
    require_contains(NPC_BEHAVIOR, "profile.getMagicSpellPower())")
    require_contains(NPC_BEHAVIOR, "ProjectileLaunchSpecification.Producer.NPC_RANGED")
    require_contains(NPC_BEHAVIOR, ".presentation(profile.getRangedProjectileVisual(), 0, true)")
    require_contains(NPC_BEHAVIOR, "final NpcMagicAttack magicAttack = profile.selectMagicAttack();")
    require_contains(NPC_COMBAT_PROFILE, "style.getMagicElement(npc)")
    require_contains(NPC_MAGIC_ATTACK, "private final NpcMagicElement element;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "CombatEffect.enemyMagicAttackEffect(name)")
    require_contains(NPC_BEHAVIOR, "int fireDefenseDebuffPercent = magicAttack.getFireDefenseDebuffPercent();")
    require_contains(NPC_BEHAVIOR, "ProjectileLaunchSpecification.Producer.NPC_MAGIC")
    require_contains(NPC_BEHAVIOR, ".elementalDebuffs(0, 0, 0, fireDefenseDebuffPercent)")
    require_contains(NPC_BEHAVIOR, ".magicElement(magicAttack.getElement())")
    require_contains(COMBAT_EFFECT, 'case "lesser demon":')
    require_contains(COMBAT_EFFECT, "return DEMON_EXPLOSION;")
    require_contains(COMBAT_EFFECT, 'case "greater demon":')
    require_contains(COMBAT_EFFECT, "public static final int DEMON_EXPLOSION = LESSER_DEMON_MAGIC;")
    require_contains(COMBAT_EFFECT, "return NONE;")
    require_contains(NPC_BEHAVIOR, "Prayers.PROTECT_FROM_MISSILES")
    require_contains(NPC_BEHAVIOR, "npc.setKillType(KillType.RANGED)")
    require_contains(NPC_BEHAVIOR, "npc.setKillType(KillType.MAGIC)")
    require_not_contains(NPC_BEHAVIOR, "DEVDEBUG")

    ensure_profiled_npcs_have_offense(load_merged_npcs())
    print("PASS: NPC ranged/magic attack styles validated")


if __name__ == "__main__":
    main()
