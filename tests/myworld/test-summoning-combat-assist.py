#!/usr/bin/env python3
"""Validate summon combat assist target selection."""

import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PVP_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    summoning = SUMMONING.read_text(encoding="utf-8")
    npc = NPC.read_text(encoding="utf-8")
    projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    pvp_melee = PVP_MELEE.read_text(encoding="utf-8")
    pvm_melee = PVM_MELEE.read_text(encoding="utf-8")
    mob = MOB.read_text(encoding="utf-8")

    require(
        summoning,
        "final Mob target = resolveSummonAssistTarget(owner, summon);",
        "Summon assist should use the resolver instead of only owner.getOpponent()",
    )
    require(
        summoning,
        "private static Mob findOwnerAttacker",
        "Summon assist should be able to find mobs attacking the owner",
    )
    require(
        summoning,
        "npc.getOpponent() == owner",
        "Summon assist should detect melee NPC attackers",
    )
    require(
        summoning,
        "&& ownerHasTakenDamageFrom(owner, npc)",
        "Defensive summon assist should require the owner to have taken or blocked damage from the NPC",
    )
    require(
        summoning,
        "SUMMON_ASSIST_ENGAGEMENT_COOLDOWN_MS = 8000L",
        "Summon assist should keep a short cooldown after owner damage participation",
    )
    require(
        summoning,
        "SUMMON_CROWDED_ASSIST_RANGE = 2",
        "Summons should have a tight crowded-combat assist fallback range",
    )
    require(
        summoning,
        "private static Mob getOwnerActiveAttackTarget",
        "Summon assist should still support owner-initiated ranged, thrown, magic, and melee attacks",
    )
    for snippet in (
        "owner.getRangeEvent()",
        "owner.getThrowingEvent()",
        "owner.getMagicCombatEvent()",
        "owner.getPvmMeleeEvent()",
    ):
        require(
            summoning,
            snippet,
            f"Summon assist active-target resolver missing {snippet}",
        )

    for snippet in (
        "&& ownerHasDamagedTarget(owner, activeTarget)",
        "private static boolean ownerHasDamagedTarget",
        "return ((Npc) target).hasDamageFrom(owner);",
        "private static boolean ownerHasTakenDamageFrom",
        "return owner.getTrackedDamage(attacker) > 0 || owner.getTrackedBlockedDamage(attacker) > 0;",
        "private static boolean ownerHasRecentSummonAssistEngagement",
        "owner.hasRecentSummonAssistEngagement(target, SUMMON_ASSIST_ENGAGEMENT_COOLDOWN_MS)",
        "private static boolean ownerIsEngagedForSummonAssist",
        "public static boolean canSummonUseCrowdedAssistReach",
        "moveSummonTowardAssistTarget(summon, target);",
        "summon.walkAdjacentToEntity(target);",
        "if (!summon.inCombat() && !summon.withinRange(owner, FOLLOW_RADIUS))",
        "private static Mob getSummonCurrentAssistTarget",
        "final Mob currentTarget = getSummonCurrentAssistTarget(summon);",
        "&& ownerHasRecentSummonAssistEngagement(owner, currentTarget)",
        "&& ownerHasRecentSummonAssistEngagement(owner, activeTarget)",
        "&& ownerHasRecentSummonAssistEngagement(owner, npc)",
    ):
        require(
            summoning,
            snippet,
            f"Summon assist damage-participation resolver missing {snippet}",
        )

    if "npc.getBehavior().getChaseTarget() == owner" in summoning:
        fail("Summon assist should not attack NPCs that are only chasing or aggro to the owner")

    assist_body_start = summoning.index("private static void assistOwnerTarget")
    assist_body_end = summoning.index("private static Mob resolveSummonAssistTarget")
    assist_body = summoning[assist_body_start:assist_body_end]
    if "ownerIsActivelyAttacking(owner, target)" in assist_body:
        fail("Defensive summon assist should not require the owner to be actively attacking the attacker")

    for snippet in (
        "public boolean hasDamageFrom(final Player player)",
        "getCombatDamageInfoBy(id).getLeft() > 0",
        "getRangeDamageInfoBy(id).getLeft() > 0",
        "getMageDamageInfoBy(id).getLeft() > 0",
        "getSummonDamageInfoBy(id).getLeft() > 0",
    ):
        require(npc, snippet, f"NPC damage participation helper missing {snippet}")

    require(
        projectile_event,
        "caster, damageDealt, clericPreventedDamage);",
        "NPC projectile damage should count as owner taking damage for summon assist",
    )
    require(
        summoning,
        "owner.recordSummonAssistEngagement(attacker);",
        "Summon damage absorption should keep defensive assist engaged after blocked damage",
    )
    require(
        summoning,
        "public static boolean scareAwayImpForElderGreenDragon",
        "Elder Green Dragon should have a scoped Mischief Imp scare-away helper",
    )
    require(
        summoning,
        "attacker.getID() != NpcId.ELDER_GREEN_DRAGON.id()",
        "Mischief Imp scare-away should only apply to Elder Green Dragon",
    )
    require(
        summoning,
        "@red@Your imp flees from the Elder Green Dragon.",
        "Elder Green Dragon imp scare-away should message the player",
    )
    npc_behavior = (ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java").read_text(encoding="utf-8")
    require(
        npc_behavior,
        "&& !Summoning.scareAwayImpForElderGreenDragon((Player) target, npc)",
        "Active Elder Green Dragon aggro should dismiss Mischief Imp instead of dropping target",
    )
    require(
        npc_behavior,
        "protectedByImp && Summoning.scareAwayImpForElderGreenDragon((Player) target, npc)",
        "Elder Green Dragon aggro scans should dismiss Mischief Imp and continue targeting",
    )
    require(
        summoning,
        "public static void recordOwnerCombatSummonDamage",
        "Owner damage should refresh summon assist engagement",
    )
    require(
        summoning,
        "summon.setOpponent(target);",
        "Projectile summons should keep visible combat state while firing",
    )
    require(
        summoning,
        "summon.setLastOpponent(target);",
        "Projectile summons should retain last opponent while firing",
    )
    require(
        npc_behavior,
        "if (Summoning.isSummon(npc))",
        "Summons should not use vanilla random roaming; summon runtime keeps them near the owner",
    )
    require(
        mob,
        "if (victim.isPlayer() && !victimShouldAvoidCombat && !attackerIsSummon)",
        "Summon melee attempts should not send the player under-attack warning",
    )
    require(
        mob,
        "if (gotUnderAttack && !Summoning.isSummon(this))",
        "Summon reciprocal combat should not send the player under-attack warning",
    )
    for source, label in ((pvp_melee, "PvP melee"), (pvm_melee, "PvM melee"), (projectile_event, "projectile")):
        require(
            source,
            "Summoning.recordOwnerCombatSummonDamage",
            f"{label} owner damage should refresh summon assist engagement",
        )
    require(
        pvm_melee,
        "boolean crowdedSummonAssistReach = Summoning.canSummonUseCrowdedAssistReach(attackerMob, targetMob);",
        "Summon melee should allow tight crowded assist reach when pathing cannot claim an adjacent tile",
    )
    require(
        pvm_melee,
        "attackerMob.isNpc() && !Summoning.isSummon(attackerMob) && targetOutsideNpcLeash",
        "Summons should not use normal NPC spawn leash while assisting their owner",
    )
    for snippet in (
        "summonAssistEngagementAt",
        "recordSummonAssistEngagement(mob);",
        "public boolean hasRecentSummonAssistEngagement",
        "System.currentTimeMillis() - lastEngagement <= cooldownMs",
        "summonAssistEngagementAt.remove(damageInflictingMob.getUUID());",
    ):
        require(player, snippet, f"Player summon assist cooldown tracking missing {snippet}")

    print("PASS: summon combat assist contracts validated")


if __name__ == "__main__":
    main()
