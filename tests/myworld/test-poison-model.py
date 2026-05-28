#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
POISON_POWER_PATH = ROOT / "server/src/com/openrsc/server/content/PoisonPower.java"
POISON_PROC_CHANCE_PATH = ROOT / "server/src/com/openrsc/server/content/PoisonProcChance.java"
MOB_PATH = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
POISON_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java"
PLAYER_PATH = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMBAT_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
RANGE_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java"
THROWING_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
PLAYER_POISON_SCRIPT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/PlayerPoisonScript.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def expect_contains(path: Path, needle: str, label: str) -> None:
    text = path.read_text()
    if needle not in text:
        fail(f"{label} missing `{needle}` in {path}")


def expect_not_contains(path: Path, needle: str, label: str) -> None:
    text = path.read_text()
    if needle in text:
        fail(f"{label} still contains `{needle}` in {path}")


def main() -> None:
    expect_contains(POISON_POWER_PATH, "getWeaponMaxPoisonPower", "weapon poison max power")
    expect_contains(POISON_POWER_PATH, "getWeaponAppliedPoisonPower", "weapon poison applied power")
    expect_contains(POISON_PROC_CHANCE_PATH, "WEAPON_START_PERCENT = 100", "weapon poison opening proc chance")
    expect_contains(POISON_PROC_CHANCE_PATH, "WEAPON_FIRST_SUCCESS_PERCENT = 50", "weapon poison first success drop")
    expect_contains(POISON_PROC_CHANCE_PATH, "WEAPON_FLOOR_PERCENT = 20", "weapon poison proc floor")
    expect_contains(POISON_PROC_CHANCE_PATH, "ARMOR_START_PERCENT = 50", "armor poison opening proc chance")
    expect_contains(POISON_PROC_CHANCE_PATH, "ARMOR_FLOOR_PERCENT = 10", "armor poison proc floor")
    expect_contains(POISON_PROC_CHANCE_PATH, "FAILURE_RECHARGE_ATTEMPTS = 5", "poison proc failure recharge")
    expect_contains(POISON_EVENT_PATH, "TICK_DELAY = 8", "poison tick interval")

    expect_contains(MOB_PATH, "private int poisonMaxPower = 0;", "mob poison max state")
    expect_contains(MOB_PATH, "applyPoison(final int appliedPoisonPower, final int maxPoisonPower)", "shared poison application")
    expect_contains(MOB_PATH, 'player.getCache().store("poisoned_max"', "player poison max persistence")
    expect_contains(MOB_PATH, 'final PoisonEvent existingPoisonEvent = getAttribute("poisonEvent", null);', "poison event lookup on reapply")
    expect_contains(MOB_PATH, "existingPoisonEvent.setPoisonPower(getPoisonDamage());", "poison reapply updates existing event without curing")

    expect_contains(PLAYER_PATH, 'getCache().hasKey("poisoned_max") ? getCache().getInt("poisoned_max") : getCache().getInt("poisoned")', "player poison max restore")
    expect_contains(PLAYER_PATH, "getMeleePoisonArmorMaxPower()", "player melee poison armor access")
    expect_contains(PLAYER_PATH, "getRangedPoisonArmorMaxPower()", "player ranged poison armor access")
    expect_contains(PLAYER_PATH, "getMagicPoisonArmorMaxPower()", "player magic poison armor access")

    expect_contains(MOB_PATH, "applyPoison(final int appliedPoisonPower, final int maxPoisonPower)", "shared poison application")

    expect_contains(COMBAT_EVENT_PATH, "applyWeaponPoison(hitter, target, damage);", "melee poison on successful hit")
    expect_contains(COMBAT_EVENT_PATH, "player.getMeleePoisonArmorMaxPower()", "melee armor poison contribution")
    expect_contains(PVM_MELEE_PATH, "applyWeaponPoison(attackerMob, targetMob, damage);", "pvm melee poison on successful hit")
    expect_contains(PVM_MELEE_PATH, "player.getMeleePoisonArmorMaxPower()", "pvm melee armor poison contribution")

    expect_contains(PROJECTILE_EVENT_PATH, "protected int poisonWeaponId;", "projectile poison source tracking")
    expect_contains(PROJECTILE_EVENT_PATH, "applyWeaponPoison();", "projectile poison on impact")
    expect_contains(PROJECTILE_EVENT_PATH, "PoisonProcChance.rollWeapon", "projectile poison ramping proc")
    expect_contains(PROJECTILE_EVENT_PATH, "casterPlayer.getMagicPoisonArmorMaxPower()", "magic armor poison contribution")
    expect_contains(PROJECTILE_EVENT_PATH, "casterPlayer.getRangedPoisonArmorMaxPower()", "ranged armor poison contribution")

    expect_contains(RANGE_EVENT_PATH, "true, ammoId, 0, 0, 0, 0, DuplicationStrategy.ONE_PER_MOB", "ranged poison deferred to projectile impact")
    expect_contains(THROWING_EVENT_PATH, "true, throwingID, 0, 0, 0, 0, DuplicationStrategy.ONE_PER_MOB", "thrown poison deferred to projectile impact")
    expect_not_contains(THROWING_EVENT_PATH, "RangeUtils.poisonTarget(getOwner(), target", "legacy thrown poison pre-impact application")

    expect_contains(PLAYER_POISON_SCRIPT_PATH, "if (attacker.getConfig().WANT_MYWORLD)", "legacy pvp poison disabled for myworld")

    print("PASS: poison model uses max/applied power, successful-hit procs, and shared impact resolution")


if __name__ == "__main__":
    main()
