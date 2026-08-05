#!/usr/bin/env python3
"""Validate Guard Dog targeting, AoE suppression, lifecycle, and cannon rules."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
NPC_BEHAVIOR = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
PVM = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
COMBAT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PROJECTILE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
THROWING = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
CANNON = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/FireCannonEvent.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
SPELLS = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/summoning-plan.md"
ICON = ROOT / "dev/myworld/assets/sprites/UI/summon/guard-dog.png"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(source: str, snippet: str, message: str) -> None:
    if snippet not in source:
        fail(message)


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start == -1:
        fail(f"missing method: {signature}")
    brace = source.find("{", start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    fail(f"unclosed method: {signature}")


def require_guard(source: str, signature: str) -> None:
    body = method_body(source, signature)
    require(
        body,
        "Summoning.isPlayerAreaEffectSuppressed",
        f"{signature} must be suppressed while Guard Dog is active",
    )


def main() -> int:
    summoning = SUMMONING.read_text(encoding="utf-8")
    npc_behavior = NPC_BEHAVIOR.read_text(encoding="utf-8")
    mob = MOB.read_text(encoding="utf-8")
    pvm = PVM.read_text(encoding="utf-8")
    combat = COMBAT.read_text(encoding="utf-8")
    projectile = PROJECTILE.read_text(encoding="utf-8")
    throwing = THROWING.read_text(encoding="utf-8")
    cannon = CANNON.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    spells = SPELLS.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    profile = re.search(
        r"GUARD_DOG_PROFILE = supportProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if profile is None:
        fail("Guard Dog support profile is missing")
    profile_body = profile.group("body")
    for snippet in (
        '"Guard Dog", 55, 310',
        "NpcId.GUARD_DOG_SINCLAIR_MANSION.id()",
        "cost(ItemId.LIFE_RUNE.id(), 2)",
        "cost(ItemId.BODY_RUNE.id(), 2)",
        "cost(ItemId.SOUL_RUNE.id(), 1)",
        "cost(ItemId.BONES.id(), 1)",
    ):
        require(profile_body, snippet, f"Guard Dog profile is missing {snippet}")
    require(
        summoning,
        "GHOST_PROFILE, GUARD_DOG_PROFILE, CAMEL_PROFILE",
        "Guard Dog must keep client/server summon indexes aligned",
    )
    require(client, '"Restless Shade", "Guard Dog", "Delivery Camel"', "client summon order is wrong")
    require(client, "1, 7, 12, 14, 20, 26, 33, 39, 45, 51, 55, 58, 61, 64, 70", "client level is wrong")
    require(client, "{37, 36, 825, 20}", "client Guard Dog cost item IDs are wrong")
    require(client, "{2, 2, 1, 1}", "client Guard Dog cost amounts are wrong")
    require(client, "GUARD_DOG_SUMMON_NPC_ID = 748", "client Guard Dog NPC identity is missing")
    examine_body = method_body(client, "private void handleMenuItemClicked")
    for snippet in (
        "case NPC_EXAMINE:",
        "this.getServerNPC(indexOrX)",
        "character.npcId == GUARD_DOG_SUMMON_NPC_ID",
        "character.suppressAttackOption",
        '"He\'s a good boy"',
        "EntityHandler.getNpcDef(idOrZ).getDescription()",
    ):
        require(examine_body, snippet, f"Guard Dog summon examine handling is missing {snippet}")
    require(guide, 'addSummonGuide(748, "55", "Guard Dog - Support;', "skill guide entry is missing")
    require(plan, "purpose-made `guard-dog.png` icon", "Guard Dog icon is undocumented")
    if not ICON.is_file():
        fail("Guard Dog summon icon is missing")
    if ICON.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        fail("Guard Dog summon icon must be a PNG")

    active_guard = method_body(summoning, "private static Npc getActiveGuardDog")
    for snippet in (
        "player.isRemoved()",
        "!player.loggedIn()",
        "player.getSkills().getLevel(Skill.HITS.id()) <= 0",
        "summon.isRemoved()",
        "getSummonCurrentHits(summon) > 0",
        "isOwnedSummon(player, summon)",
        "KIND_GUARD_DOG.equals",
    ):
        require(active_guard, snippet, f"Guard Dog lifecycle check is missing {snippet}")
    require(
        summoning,
        "reconcileGuardDogEnemies(owner, summon);",
        "casting Guard Dog must reconcile already engaged enemies",
    )
    require(
        method_body(summoning, "private static void setGuardDogPrimaryEnemy"),
        "previousEnemy.getBehavior().disengageFrom(player);",
        "switching primary targets must safely disengage the previous enemy",
    )
    reconcile = method_body(summoning, "private static void reconcileGuardDogEnemies")
    require(reconcile, "npc.getBehavior().disengageFrom(player);", "pre-existing extra attackers must be released")
    require(
        method_body(summoning, "private static boolean guardDogAllowsEnemy"),
        "return claimedEnemy == attacker;",
        "only the claimed enemy may keep attacking",
    )
    require(
        method_body(summoning, "public static boolean canSummonAttack"),
        "return true;",
        "combat without Guard Dog must remain permitted",
    )
    for source, signature in (
        (npc_behavior, "private boolean canAggro(final Mob target, final long now, final boolean forceAggressive)"),
        (npc_behavior, "public void setChasing(final Player player)"),
        (npc_behavior, "private void handleCombat"),
        (mob, "private void startPvmCombat"),
        (pvm, "private boolean combatCanContinue"),
    ):
        require(
            method_body(source, signature),
            "Summoning.canSummonAttack",
            f"{signature} must enforce the shared one-enemy policy",
        )
    disengage = method_body(npc_behavior, "public void disengageFrom")
    for snippet in (
        "resetCombat(false)",
        "npc.getOpponent() == player",
        "npc.getLastOpponent() == player",
        "npc.isHostileToward(player)",
        "target == player",
    ):
        require(disengage, snippet, f"safe disengagement is missing {snippet}")

    for source, signature in (
        (pvm, "private int applyScytheNpcCleave"),
        (pvm, "private void applyChaosAmuletChainLightning"),
        (pvm, "private void applyDeathRobeOverkillSplash"),
        (combat, "private void applyChaosAmuletChainLightning"),
        (combat, "private void applyDeathRobeOverkillSplash"),
        (projectile, "private void applyChaosAmuletChainLightning"),
        (projectile, "private void applySplinter"),
        (projectile, "private void applyBloodRobeSplash"),
        (projectile, "private void applyDeathRobeOverkillSplash"),
        (throwing, "private List<Mob> selectThrowingTargets"),
        (player, "public void applyDeathAmuletBurst"),
        (spells, "private void applyGodSpellAreaEffects"),
        (spells, "private void applyIbanBlastAreaEffects"),
    ):
        require_guard(source, signature)

    god_spell = method_body(spells, "private void applyGodSpellAreaEffects")
    require(
        god_spell,
        "applyGodSpellSpecialEffect(caster, primaryTarget, spellEnum, primaryDamage, true);",
        "Guard Dog must preserve the god spell's primary-target effect",
    )
    require(
        god_spell,
        "applyGodSpellLifesteal(caster, spellEnum, Math.max(0, primaryDamage));",
        "Guard Dog must preserve primary-target god spell lifesteal",
    )
    shuriken = method_body(throwing, "private List<Mob> selectThrowingTargets")
    require(shuriken, "targets.add(target);", "Guard Dog must preserve the primary thrown target")

    require(cannon, "!Summoning.isSummon(npc)", "multicannon must never target summons")
    require(cannon, "final boolean singleTarget = Summoning.isPlayerAreaEffectSuppressed", "Guard Dog cannon lock is missing")
    require(cannon, "Summoning.getGuardDogPrimaryEnemy", "cannon must reuse the one-enemy claim")
    require(cannon, "Summoning.selectGuardDogPrimaryEnemy", "cannon must reserve its chosen enemy")
    require(
        cannon,
        "(allowPreviousTarget || this.targetNpc == null",
        "normal cannon rotation and Guard Dog target retention must remain distinct",
    )

    print("PASS: Guard Dog targeting, AoE suppression, lifecycle, and cannon contracts validated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
