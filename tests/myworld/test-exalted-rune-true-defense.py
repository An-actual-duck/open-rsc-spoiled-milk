#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, needle: str, label: str, failures: list[str]) -> None:
    if needle not in text:
        failures.append(label)


def main() -> int:
    failures: list[str] = []

    combat_effect = read("server/src/com/openrsc/server/model/entity/update/CombatEffect.java")
    client = read("Client_Base/src/orsc/mudclient.java")
    equipment = read("server/src/com/openrsc/server/model/container/Equipment.java")
    player = read("server/src/com/openrsc/server/model/entity/player/Player.java")
    true_defense = read("server/src/com/openrsc/server/content/TrueDefense.java")
    combat_event = read("server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java")
    pvm_melee = read("server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java")
    projectile_event = read("server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java")
    elder_green = read("server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java")
    npc_drops = read("server/src/com/openrsc/server/constants/NpcDrops.java")
    item_defs = read("server/conf/server/defs/ItemDefsCustom.json")
    client_items = read("Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java")

    require(combat_effect, "public static final int TRUE_DEFENSE = 64;", "server combat effect id", failures)
    require(client, "public static final int COMBAT_EFFECT_TRUE_DEFENSE = 64;", "client combat effect id", failures)
    require(client, "public static final int COMBAT_EFFECT_COUNT = 77;", "client combat effect count", failures)
    require(client, '"true-defense"', "client combat effect name", failures)
    require(client, 'if ("true-defense".equals(animationName))', "client true-defense sheet loader", failures)
    require(client, "targetFrames, maxTargetSize, 22, 1, 22, 0);", "client true-defense frame geometry", failures)
    require(client, "|| effectType == COMBAT_EFFECT_TRUE_DEFENSE", "client true-defense sizing", failures)

    for item_id in (
        "EXALTED_RUNE_HELMET",
        "EXALTED_RUNE_PLATE_MAIL_BODY",
        "EXALTED_RUNE_PLATE_MAIL_LEGS",
        "EXALTED_RUNE_GAUNTLETS",
        "EXALTED_RUNE_GREAVES",
        "EXALTED_RUNE_SQUARE_SHIELD",
        "EXALTED_RUNE_PALADIN_SHIELD",
    ):
        require(equipment, f"MyWorldItemId.{item_id}", f"equipment counts {item_id}", failures)
    require(
        equipment,
        "hasEquipped(MyWorldItemId.EXALTED_RUNE_SQUARE_SHIELD)\n\t\t\t|| hasEquipped(MyWorldItemId.EXALTED_RUNE_PALADIN_SHIELD)",
        "shield slot should count either Exalted Rune shield once",
        failures,
    )
    require(player, "public int getExaltedRuneTrueDefensePieces()", "player exposes true-defense piece count", failures)

    require(true_defense, "PROC_CHANCE_PER_PIECE = 0.05D", "5 percent per armor piece", failures)
    require(true_defense, "VISUAL_DURATION_TICKS = 40", "visual replay cooldown matches combat effect duration", failures)
    require(true_defense, "pieces * PROC_CHANCE_PER_PIECE", "proc chance scales by equipped pieces", failures)
    require(true_defense, "DataConversions.getRandom().nextDouble() >= chance", "random proc roll", failures)
    require(true_defense, "new CombatEffect(defender, CombatEffect.TRUE_DEFENSE)", "true-defense visual emitted", failures)
    require(true_defense, "currentTick < visualUntilTick", "visual does not replay while active", failures)
    require(true_defense, "return 0;", "proc negates all incoming damage", failures)

    require(combat_event, "damage = TrueDefense.apply((Player) target, damage);", "pvp melee true-defense hook", failures)
    require(pvm_melee, "damage = TrueDefense.apply((Player) target, damage);", "pvm melee true-defense hook", failures)
    require(projectile_event, "damage = TrueDefense.apply((Player) opponent, damage);", "projectile true-defense hook", failures)
    require(projectile_event, "impactEffectType > 0 && !trueDefenseBlocked", "projectile visual should not overwrite true-defense", failures)
    require(projectile_event, "private boolean isPrimaryProjectileAttackType()", "projectile hook limited to primary attack types", failures)
    require(elder_green, "damage = TrueDefense.apply(player, damage);", "elder green dragon true-defense hook", failures)
    require(elder_green, "style == DamageStyle.MELEE || style == DamageStyle.RANGED || style == DamageStyle.MAGIC", "elder burn excluded", failures)
    require(
        npc_drops,
        "addHiddenUniqueDrop(NpcId.ELDER_GREEN_DRAGON.id(), MyWorldItemId.EXALTED_RUNE_HELMET, 1, HiddenUniqueRarity.VERY_RARE_UNIQUE);",
        "elder green dragon hidden unique helmet drop",
        failures,
    )

    require(item_defs, "5% True Defense chance", "server item descriptions mention true defense", failures)
    require(client_items, "5% True Defense chance", "client item descriptions mention true defense", failures)

    asset = ROOT / "dev/myworld/assets/legacy animation folder/On Player/true-defense/true-defense.png"
    if not asset.exists():
        failures.append("true-defense animation asset must exist")

    if failures:
        print("FAIL: Exalted Rune True Defense checks failed")
        for failure in failures:
            print(f" - {failure}")
        return 1
    print("PASS: Exalted Rune True Defense validated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
