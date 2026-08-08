#!/usr/bin/env python3
"""Ensure incidental area effects select the intended entity types."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
THROWING_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
COMBAT_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAYER_NPC_RADIUS_SELECTION = ROOT / "server/src/com/openrsc/server/model/combat/PlayerOwnedNpcRadiusSelection.java"
CHAIN_TRAVERSAL = ROOT / "server/src/com/openrsc/server/model/combat/ChainLightningTraversalPolicy.java"
SPLINTER_SELECTION = ROOT / "server/src/com/openrsc/server/model/combat/SplinterTargetSelectionPolicy.java"
DEATH_ROBE_SPLASH = ROOT / "server/src/com/openrsc/server/model/combat/DeathRobeOverkillSplash.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start == -1:
        fail(f"Missing method signature: {signature}")
    brace = source.find("{", start)
    if brace == -1:
        fail(f"Missing method body for: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    fail(f"Unclosed method body for: {signature}")


def require_contains(source: str, snippet: str, message: str) -> None:
    if snippet not in source:
        fail(message)


def require_absent(source: str, snippet: str, message: str) -> None:
    if snippet in source:
        fail(message)


def require_npc_damage_area(name: str, body: str, attackable_required: bool = True) -> None:
    if any(policy in body for policy in (
        "PlayerOwnedNpcRadiusSelection.",
        "ChainLightningTraversalPolicy.",
        "SplinterTargetSelectionPolicy.",
    )):
        require_absent(body, "getPlayersInView()", f"{name} must not select player targets")
        return
    require_contains(body, "getNpcsInView()", f"{name} should select NPC area targets")
    require_absent(body, "getPlayersInView()", f"{name} must not select player targets")
    require_contains(body, "Summoning.isSummon", f"{name} must exclude summons")
    if attackable_required:
        require_contains(body, "getDef().isAttackable()", f"{name} must exclude non-attackable NPCs")


def main() -> int:
    spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")
    projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")
    throwing_event = THROWING_EVENT.read_text(encoding="utf-8")
    combat_event = COMBAT_EVENT.read_text(encoding="utf-8")
    pvm_melee_event = PVM_MELEE_EVENT.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    player_npc_radius_selection = PLAYER_NPC_RADIUS_SELECTION.read_text(encoding="utf-8")
    chain_traversal = CHAIN_TRAVERSAL.read_text(encoding="utf-8")
    splinter_selection = SPLINTER_SELECTION.read_text(encoding="utf-8")
    death_robe_splash = DEATH_ROBE_SPLASH.read_text(encoding="utf-8")

    require_npc_damage_area(
        "Shared player-owned NPC radius selection",
        player_npc_radius_selection,
    )
    require_npc_damage_area("Chain traversal policy", chain_traversal)
    require_npc_damage_area("Splinter selection policy", splinter_selection)
    require_npc_damage_area("Death robe splash selection", death_robe_splash)

    if "isValidIbanBlastAreaTarget(primaryTarget, npc)" not in spell_handler:
        fail("Iban Blast area selection should use its summon-aware target guard")
    if spell_handler.count("Summoning.isSummon(possibleTarget)") < 2:
        fail("Iban Blast and god-spell area effects must both exclude summons")
    if "caster.getLocation().inWilderness()" in spell_handler:
        fail("God-spell area effects must not include incidental Wilderness player targets")
    if (projectile_event + chain_traversal + splinter_selection).count(
        "!Summoning.isSummon(npc)"
    ) < 2:
        fail("Projectile splash and Splinter effects must exclude summons")
    if "|| Summoning.isSummon(npc)" not in throwing_event:
        fail("Shuriken splash target selection must exclude summons")
    for source_name, source in (
        ("projectile", projectile_event),
        ("combat", combat_event),
        ("PvM melee", pvm_melee_event),
    ):
        if ("if (!primaryTarget.isNpc()) {\n\t\t\treturn null;" not in source
                and "ChainLightningTraversalPolicy.selectNext" not in source):
            fail(f"{source_name} splash effects must not apply a second hit to player targets")
    if "!Summoning.isSummon(npc)" not in (combat_event + chain_traversal):
        fail("Combat splash effects must exclude summons")
    if "!Summoning.isSummon(npc)" not in (pvm_melee_event + chain_traversal):
        fail("PvM melee splash effects must exclude summons")
    if "npc.getSkills().getLevel(Skill.HITS.id()) <= 0 || Summoning.isSummon(npc)" not in pvm_melee_event:
        fail("Scythe cleave target validation and aggro must exclude summons")
    if "private void inflictScytheCleaveDamage" not in pvm_melee_event or "if (Summoning.isSummon(npc)) {\n\t\t\treturn;\n\t\t}" not in pvm_melee_event:
        fail("Scythe cleave damage helper must refuse summons defensively")

    require_contains(
        method_body(spell_handler, "private boolean isValidIbanBlastAreaTarget"),
        "((Npc) possibleTarget).getDef().isAttackable()",
        "Iban Blast area effects must exclude non-attackable NPCs",
    )
    require_contains(
        method_body(spell_handler, "private boolean isValidGodSpellAreaTarget"),
        "((Npc) possibleTarget).getDef().isAttackable()",
        "God spell area effects must exclude non-attackable NPCs",
    )

    require_npc_damage_area("Death amulet Burst", method_body(player, "public void applyDeathAmuletBurst"))
    require_npc_damage_area("Chaos robe surrounded bonus", method_body(player, "public double getChaosRobeSurroundedDamageMultiplier"))
    require_npc_damage_area("Projectile blood-robe splash", method_body(projectile_event, "private void applyBloodRobeSplash"))
    require_contains(method_body(projectile_event, "private void applyDeathRobeOverkillSplash"),
                     "DeathRobeOverkillSplash.apply", "Projectile death robe must use shared selection")
    require_npc_damage_area("Projectile chaos chain lightning", method_body(projectile_event, "private Mob selectChaosChainLightningTarget"))
    require_npc_damage_area("Splinter projectile proc", method_body(projectile_event, "private Npc selectSplinterTarget"))
    require_contains(method_body(combat_event, "private void applyDeathRobeOverkillSplash"),
                     "DeathRobeOverkillSplash.apply", "Combat death robe must use shared selection")
    require_npc_damage_area("Generic combat chaos chain lightning", method_body(combat_event, "private Mob selectChaosChainLightningTarget"))
    require_contains(method_body(pvm_melee_event, "private void applyDeathRobeOverkillSplash"),
                     "DeathRobeOverkillSplash.apply", "PvM death robe must use shared selection")
    require_npc_damage_area("PvM melee chaos chain lightning", method_body(pvm_melee_event, "private Mob selectChaosChainLightningTarget"))
    require_contains(
        method_body(pvm_melee_event, "private boolean isValidScytheCleaveTarget"),
        "!npc.getDef().isAttackable()",
        "Scythe cleave must exclude non-attackable NPCs",
    )
    require_npc_damage_area("Shuriken multi-target selection", method_body(throwing_event, "private List<Npc> getValidShurikenTargets"))

    soul_burst = method_body(player, "public void applySoulAmuletBurst")
    require_contains(soul_burst, "getPlayersInView()", "Soul amulet Burst should select nearby players to heal")
    require_absent(soul_burst, "getNpcsInView()", "Soul amulet Burst must not select NPC heal targets")
    require_contains(
        method_body(player, "private void healSoulBurstTarget"),
        "final Player target",
        "Soul amulet Burst heal helper should only accept player targets",
    )

    print("PASS: area effects target NPC enemies and player healing correctly")
    return 0


if __name__ == "__main__":
    sys.exit(main())
