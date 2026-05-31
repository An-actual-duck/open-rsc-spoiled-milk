#!/usr/bin/env python3
"""Validate MyWorld combat runtime invariants that need scripted playtest coverage.

This is intentionally source-backed rather than client-backed. The repo does not
currently have a Java integration test harness, so this pins the server-side
behavior that would otherwise need repeated manual playtests.
"""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NPC = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "Npc.java"
NPC_BEHAVIOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcBehavior.java"
NPC_ATTACK_STYLE_PROFILE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcAttackStyleProfile.java"
GROUND_ITEM = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "GroundItem.java"
DROP_TABLE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "DropTable.java"
ATTACK_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "AttackHandler.java"
SPELL_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellHandler.java"
RANGE_UTILS = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeUtils.java"
RANGE_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeEvent.java"
THROWING_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ThrowingEvent.java"
MAGIC_COMBAT_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "MagicCombatEvent.java"
CLIENT = ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"Missing file: {path}")
    return path.read_text(encoding="utf-8")


def require_contains(path: Path, needle: str) -> None:
    text = read(path)
    if needle not in text:
        fail(f"{path.name} missing expected text: {needle}")


def require_not_contains(path: Path, needle: str) -> None:
    text = read(path)
    if needle in text:
        fail(f"{path.name} still contains retired text: {needle}")


def require_regex(path: Path, pattern: str, description: str) -> None:
    text = read(path)
    if not re.search(pattern, text, re.DOTALL):
        fail(f"{path.name} missing expected invariant: {description}")


def require_order(path: Path, before: str, after: str) -> None:
    text = read(path)
    before_index = text.find(before)
    after_index = text.find(after)
    if before_index == -1 or after_index == -1:
        fail(f"{path.name} cannot verify order for: {before} -> {after}")
    if before_index >= after_index:
        fail(f"{path.name} has unexpected order: {before} should appear before {after}")


def damage_share_xp(total_xp: int, npc_hits: int, damage: int) -> int:
    if damage <= 0 or npc_hits <= 0:
        return 0
    return int((total_xp / npc_hits) * damage)


def validate_damage_share_math() -> None:
    total_combat_xp = 220
    npc_hits = 100
    melee_damage = 25
    ranged_damage = 35
    magic_damage = 40

    melee_base = damage_share_xp(total_combat_xp, npc_hits, melee_damage)
    ranged_xp = damage_share_xp(total_combat_xp * 4, npc_hits, ranged_damage)
    magic_xp = damage_share_xp(total_combat_xp * 4, npc_hits, magic_damage)

    if melee_base != 55:
        fail(f"Expected melee base share 55, found {melee_base}")
    if ranged_xp != 308:
        fail(f"Expected ranged share 308, found {ranged_xp}")
    if magic_xp != 352:
        fail(f"Expected magic share 352, found {magic_xp}")

    melee_skill_awards = {
        "melee": melee_base * 3,
        "hits": melee_base,
    }
    if melee_skill_awards != {"melee": 165, "hits": 55}:
        fail(f"Unexpected melee skill distribution: {melee_skill_awards}")


def main() -> None:
    validate_damage_share_math()

    require_contains(NPC_ATTACK_STYLE_PROFILE, "private static final int DEFAULT_PROJECTILE_RANGE = 5;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return distance > 1 || rollsPreferredProjectileAttack();")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "DataConversions.getRandom().nextInt(100) < 65")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return this == PURE_RANGED || this == PURE_MAGIC;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return this == PURE_RANGED || this == MELEE_RANGED;")
    require_contains(NPC_ATTACK_STYLE_PROFILE, "return this == PURE_MAGIC || this == MELEE_MAGIC;")

    require_contains(NPC_BEHAVIOR, "profile.prefersProjectileAtDistance(distance)")
    require_contains(NPC_BEHAVIOR, "!npc.withinRange(target, profile.getProjectileRange())")
    require_contains(NPC_BEHAVIOR, "PathValidation.checkPath(npc.getWorld(), npc.getLocation(), target.getLocation())")
    require_contains(NPC_BEHAVIOR, "new ProjectileEvent(npc.getWorld(), npc, target, damage, 2)")
    require_contains(NPC_BEHAVIOR, "new ProjectileEvent(npc.getWorld(), npc, target, damage, 1)")

    require_contains(RANGE_UTILS, "public static final int PLAYER_COMBAT_RANGE_BONUS = 2;")
    require_contains(RANGE_UTILS, "return Math.max(1, attackRadius - PLAYER_POSITIONING_RANGE_REDUCTION);")
    require_contains(RANGE_UTILS, "return baseRadius + PLAYER_COMBAT_RANGE_BONUS;")
    require_contains(ATTACK_HANDLER, "int attackRadius = radius + RangeUtils.PLAYER_COMBAT_RANGE_BONUS;")
    require_contains(ATTACK_HANDLER, "int walkRadius = player.withinRange(affectedMob, attackRadius) ? attackRadius : approachRadius;")
    require_contains(ATTACK_HANDLER, "int walkRadius = player.withinRange(affectedMob, radius) ? radius : approachRadius;")
    require_contains(SPELL_HANDLER, "player.getConfig().SPELL_RANGE_DISTANCE + RangeUtils.PLAYER_COMBAT_RANGE_BONUS")
    require_contains(MAGIC_COMBAT_EVENT, "final int spellRange = player.getConfig().SPELL_RANGE_DISTANCE + RangeUtils.PLAYER_COMBAT_RANGE_BONUS;")
    require_contains(MAGIC_COMBAT_EVENT, "final int approachRange = RangeUtils.getApproachRadius(spellRange);")
    require_contains(RANGE_EVENT, "final int radius = RangeUtils.getBowAttackRadius(weaponId);")
    require_contains(RANGE_EVENT, "final int approachRadius = RangeUtils.getApproachRadius(radius);")
    require_contains(THROWING_EVENT, "return RangeUtils.getThrowingAttackRadius(throwingEquip);")
    require_contains(THROWING_EVENT, "RangeUtils.getApproachRadius(attackRadius)")
    require_contains(CLIENT, "private boolean isSpellProjectile(SpriteDef projectile)")
    require_contains(CLIENT, "return isSpellProjectile(projectile) ? size * 2 : size;")
    require_contains(CLIENT, "return 192;")

    require_contains(NPC, "Player meleeRangeThreat = getLowestCombatLevelThreat(true);")
    require_contains(NPC, "return getLowestCombatLevelThreat(false);")
    require_order(NPC, "Player meleeRangeThreat = getLowestCombatLevelThreat(true);", "return getLowestCombatLevelThreat(false);")
    require_contains(NPC, "requireMeleeRange && !player.withinRange(this, 1)")
    require_contains(NPC, "playerCombatLevel < bestCombatLevel")

    require_contains(NPC, "private Pair<UUID, Long> handleXpDistribution(final Mob attacker)")
    require_contains(NPC, "for (UUID id : getAllDamageDealerIds())")
    require_contains(NPC, "awardDamageShareXp(")
    require_contains(NPC, "getDamageShareXp(totalCombatXP, damage)")
    require_contains(NPC, "getDamageShareXp(totalCombatXP * 4, damage)")
    require_contains(NPC, "awardCombatXpWithHitsFocus(player, Skill.MAGIC, magicXpShare)")
    require_not_contains(NPC, "WANTS_KILL_STEALING && attacker.isPlayer()")

    require_regex(
        NPC,
        r"private void dropPersonalItems\(Map<Player, Double> recipients, Player fallbackOwner\).*?for \(Map.Entry<Player, Double> entry : recipients.entrySet\(\)\).*?dropItems\(entry.getKey\(\), entry.getValue\(\), true\);",
        "personal loot loops over every contributor",
    )
    require_contains(NPC, "ArrayList<Item> items = personalDrop ? drops.rollPersonalLoot(owner, contributionScale) : drops.rollItem(owner);")
    require_contains(NPC, "groundItem.setAttribute(\"personalNpcDrop\", true);")

    require_regex(
        GROUND_ITEM,
        r"public boolean belongsTo\(final Player player\) \{\s*if \(getAttribute\(\"personalNpcDrop\", false\)\) \{\s*return player.getUsernameHash\(\) == ownerUsernameHash;",
        "personal NPC drops bypass party sharing and only belong to their owner",
    )
    require_regex(
        GROUND_ITEM,
        r"public boolean isInvisibleTo\(final Player player\) \{\s*if \(getAttribute\(\"personalNpcDrop\", false\) && !belongsTo\(player\)\) \{\s*return true;",
        "personal NPC drops remain invisible to non-owners before public-drop timing logic",
    )

    require_contains(DROP_TABLE, "public ArrayList<Item> rollPersonalLoot(Player owner, double contributionScale)")
    require_contains(DROP_TABLE, "drop.table.isRare() && (suppressRareTables || !passesContributionGate(contributionScale))")
    require_contains(DROP_TABLE, "scaleRareNormalDrops && isRareNormalDrop(drop) && !passesContributionGate(contributionScale)")
    require_contains(DROP_TABLE, "private static final double MINIMUM_RARE_CONTRIBUTION_SCALE = 0.05D;")

    print("PASS: combat runtime invariants validated")


if __name__ == "__main__":
    main()
