#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
POISON_POWER_PATH = ROOT / "server/src/com/openrsc/server/content/PoisonPower.java"
POISON_PROC_CHANCE_PATH = ROOT / "server/src/com/openrsc/server/content/PoisonProcChance.java"
MOB_PATH = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
POISON_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java"
POISON_TARGET_STATE_PATH = ROOT / "server/src/com/openrsc/server/model/combat/dot/PoisonTargetState.java"
POISON_TARGET_POLICY_PATH = ROOT / "server/src/com/openrsc/server/model/combat/dot/PoisonTargetPolicy.java"
PLAYER_PATH = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMBAT_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
RANGE_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java"
THROWING_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
PLAYER_POISON_SCRIPT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/PlayerPoisonScript.java"
NPC_POISON_SCRIPT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/NpcPoisonPlayerScript.java"
SPELL_HANDLER_PATH = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
CORROSIVE_AURA_PATH = ROOT / "server/src/com/openrsc/server/content/CorrosiveAura.java"
SINISTER_CHEST_PATH = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/SinisterChest.java"
ADMIN_COMMANDS_PATH = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
INV_ITEM_POISONING_PATH = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvItemPoisoning.java"
ITEM_HERB_SECOND_PATH = ROOT / "server/conf/server/defs/extras/ItemHerbSecond.xml"


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


def extract_method(path: Path, signature: str, label: str) -> str:
    text = path.read_text()
    signature_start = text.find(signature)
    if signature_start == -1:
        fail(f"{label} method signature `{signature}` missing in {path}")
    body_start = text.find("{", signature_start)
    if body_start == -1:
        fail(f"{label} method body missing in {path}")

    depth = 0
    for index in range(body_start, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[signature_start:index + 1]
    fail(f"{label} method body is unbalanced in {path}")


def expect_method_contains(method: str, needle: str, label: str) -> None:
    normalized_method = " ".join(method.split())
    normalized_needle = " ".join(needle.split())
    if normalized_needle not in normalized_method:
        fail(f"{label} missing `{needle}` in its method scope")


def expect_method_matches(method: str, pattern: str, label: str) -> None:
    if re.search(pattern, method) is None:
        fail(f"{label} missing its formatting-tolerant pattern in method scope")


def expect_method_order(method: str, needles: list[str], label: str) -> None:
    normalized_method = " ".join(method.split())
    position = -1
    for needle in needles:
        normalized_needle = " ".join(needle.split())
        next_position = normalized_method.find(normalized_needle, position + 1)
        if next_position == -1:
            fail(f"{label} missing ordered step `{needle}` in method scope")
        position = next_position


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

    expect_contains(ITEM_HERB_SECOND_PATH, "<secondID>472</secondID>", "weapon poison ground blue dragon scale ingredient")
    expect_contains(ITEM_HERB_SECOND_PATH, "<unfinishedID>457</unfinishedID>", "weapon poison unfinished Harralander potion")
    expect_contains(ITEM_HERB_SECOND_PATH, "<potionID>572</potionID>", "weapon poison potion output")
    expect_contains(INV_ITEM_POISONING_PATH, "ItemId.WEAPON_POISON.id()", "weapon poison item-use trigger")
    expect_contains(INV_ITEM_POISONING_PATH, 'String poisonedVersion = "Poisoned " + name;', "poisoned weapon lookup")
    expect_contains(INV_ITEM_POISONING_PATH, 'String poisonedVersion2 = "Poison " + name;', "poisoned ammunition lookup")
    expect_contains(INV_ITEM_POISONING_PATH, 'player.getCarriedItems().remove(new Item(ItemId.WEAPON_POISON.id()))', "weapon poison consumption")

    expect_contains(MOB_PATH, "private int poisonMaxPower = 0;", "mob poison max state")
    expect_contains(MOB_PATH, "applyPoison(final int appliedPoisonPower, final int maxPoisonPower)", "shared poison application")
    expect_contains(MOB_PATH, 'player.getCache().store("poisoned_max"', "player poison max persistence")
    expect_contains(MOB_PATH, "synchronized (poisonStateLock())", "atomic poison target-state boundary")
    expect_contains(MOB_PATH, "private Object poisonStateLock()", "constructor-bypass poison lock recovery")
    expect_contains(MOB_PATH, "final PoisonTargetState next = current.apply", "poison next-state calculation")
    expect_contains(MOB_PATH, "ensurePoisonEvent(getPoisonDamage(), poisonProvenance);", "poison restore event lookup")
    expect_contains(POISON_TARGET_STATE_PATH, "final long uncappedPower", "poison overflow-safe accumulation")
    expect_contains(POISON_TARGET_POLICY_PATH, "transfersProvenance", "capped provenance transfer policy")
    expect_contains(POISON_EVENT_PATH, "DuplicationStrategy.ONE_PER_MOB", "single poison scheduler stream")
    expect_contains(POISON_EVENT_PATH, 'DamageRequest.SourceCategory.DOT, "generic-poison"', "typed poison tick settlement")
    expect_contains(POISON_EVENT_PATH, "getResolvedDamageTransaction().apply(request)", "typed poison damage transaction")

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

    range_run = extract_method(RANGE_EVENT_PATH, "public void run()", "ranged attack")
    expect_method_contains(range_run, "new ProjectileEvent(", "ranged attack launches an impact event")
    expect_method_matches(
        range_run,
        r"ProjectileLaunchSpecification\s*\.\s*builder\s*\(\s*"
        r"ProjectileLaunchSpecification\s*\.\s*Producer\s*\.\s*PLAYER_BOW\s*,\s*"
        r"damage\s*,\s*2\s*\)",
        "ranged projectile producer and impact style",
    )
    expect_method_contains(range_run, ".chase(true)", "ranged projectile chase policy")
    expect_method_contains(range_run, ".poisonWeaponId(ammoId)", "ranged poison deferred to projectile impact")
    expect_method_contains(
        range_run,
        ".duplicationStrategy(DuplicationStrategy.ONE_PER_MOB)",
        "ranged projectile duplication policy",
    )
    expect_method_contains(range_run, ".build(), resourceLedger)", "ranged typed launch and resource ledger")

    throwing_hit = extract_method(
        THROWING_EVENT_PATH,
        "private void applyThrowingHit(",
        "thrown attack",
    )
    expect_method_contains(throwing_hit, "new ProjectileEvent(", "thrown attack launches an impact event")
    expect_method_matches(
        throwing_hit,
        r"ProjectileLaunchSpecification\s*\.\s*builder\s*\(\s*"
        r"projectileProducer\s*,\s*damage\s*,\s*2\s*\)",
        "thrown projectile producer and impact style",
    )
    expect_method_contains(throwing_hit, ".chase(true)", "thrown projectile chase policy")
    expect_method_contains(throwing_hit, ".poisonWeaponId(throwingID)", "thrown poison deferred to projectile impact")
    expect_method_contains(
        throwing_hit,
        "RangeUtils.SHURIKENS.contains(throwingID) ? DuplicationStrategy.ALLOW_MULTIPLE : DuplicationStrategy.ONE_PER_MOB",
        "shuriken projectiles avoid per-player event de-duplication",
    )
    expect_method_contains(
        throwing_hit,
        ".duplicationStrategy(projectileDuplicationStrategy)",
        "thrown projectile duplication policy",
    )
    expect_method_contains(throwing_hit, ".build(), resourceLedger)", "thrown typed launch and resource ledger")
    expect_not_contains(THROWING_EVENT_PATH, "RangeUtils.poisonTarget(getOwner(), target", "legacy thrown poison pre-impact application")

    expect_contains(PLAYER_POISON_SCRIPT_PATH, "if (attacker.getConfig().WANT_MYWORLD)", "legacy pvp poison disabled for myworld")
    expect_contains(PLAYER_POISON_SCRIPT_PATH, "player.applyPoison(48);", "legacy pvp poison application")
    expect_contains(NPC_POISON_SCRIPT_PATH, "victim.applyPoison(", "npc poison application")
    expect_contains(NPC_POISON_SCRIPT_PATH, "player.isAntidoteProtected()", "npc poison antidote guard")

    guthix_poison = extract_method(
        SPELL_HANDLER_PATH,
        "private void applyGuthixGodSpellPoison(",
        "Guthix god-spell poison",
    )
    expect_method_contains(
        guthix_poison,
        "target.applyPoison(advancedSpell ? 40 : 20, advancedSpell ? 80 : 40, caster);",
        "primary Guthix poison",
    )
    expect_method_contains(
        guthix_poison,
        "target.applyPoison(advancedSpell ? 20 : 10, advancedSpell ? 40 : 20, caster);",
        "secondary Guthix poison",
    )
    expect_contains(CORROSIVE_AURA_PATH, "attacker.applyPoison(poisonPower, attacker.getCurrentPoisonPower() + poisonPower, defender);", "Corrosive Aura poison application")
    sinister_use = extract_method(
        SINISTER_CHEST_PATH,
        "public void onUseLoc(Player player, GameObject obj, Item item)",
        "Sinister Chest use",
    )
    expect_method_contains(
        sinister_use,
        "item.getCatalogId() == ItemId.SINISTER_KEY.id() && obj.getID() == SINISTER_CHEST",
        "Sinister Chest key/chest guard",
    )
    expect_method_order(
        sinister_use,
        [
            "player.getCarriedItems().remove(new Item(ItemId.SINISTER_KEY.id())) == -1) return",
            "give(player, ItemId.UNIDENTIFIED_HARRALANDER.id(), 2);",
            "give(player, ItemId.UNIDENTIFIED_TORSTOL.id(), 1);",
            "player.applyPoison(68);",
        ],
        "Sinister Chest key removal, reward, then poison order",
    )

    admin_poison = extract_method(
        ADMIN_COMMANDS_PATH,
        "private void poisonSelfForTesting(final Player player, final String command, final String[] args)",
        "admin poison diagnostic",
    )
    expect_method_contains(admin_poison, "int poisonPower = 30;", "admin poison default power")
    expect_method_contains(admin_poison, "Integer.parseInt(args[0])", "admin poison numeric parser")
    expect_method_contains(admin_poison, "if (poisonPower <= 0)", "admin poison positive-only guard")
    expect_method_order(
        admin_poison,
        [
            "player.curePoison();",
            "player.applyPoison(poisonPower, poisonPower);",
            "Applied poison power",
        ],
        "admin poison replaces prior state before reporting success",
    )

    print("PASS: poison model uses max/applied power, successful-hit procs, shared impact resolution, and a guarded producer inventory")


if __name__ == "__main__":
    main()
