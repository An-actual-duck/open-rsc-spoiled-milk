#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "player" / "Player.java"
DRINKABLES = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "itemactions" / "Drinkables.java"
STAT_RESTORE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "StatRestorationEvent.java"
COMBAT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "combat" / "CombatEvent.java"
PVM_MELEE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "combat" / "PvmMeleeEvent.java"
RANGE_UTILS = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeUtils.java"
PROJECTILE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ProjectileEvent.java"
DROPTABLE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "DropTable.java"
SPELL_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellHandler.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    player_text = PLAYER.read_text(encoding="utf-8")
    drinkables_text = DRINKABLES.read_text(encoding="utf-8")
    stat_restore_text = STAT_RESTORE.read_text(encoding="utf-8")
    combat_text = COMBAT.read_text(encoding="utf-8")
    pvm_melee_text = PVM_MELEE.read_text(encoding="utf-8")
    range_utils_text = RANGE_UTILS.read_text(encoding="utf-8")
    projectile_text = PROJECTILE.read_text(encoding="utf-8")
    drop_table_text = DROPTABLE.read_text(encoding="utf-8")
    spell_handler_text = SPELL_HANDLER.read_text(encoding="utf-8")

    for snippet in (
        "activatePotionOfInsight",
        "getPotionOfInsightBonusPercent",
        "activatePotionOfRegeneration",
        "getPotionOfRegenerationMultiplier",
        "getPotionAttackSpeedMultiplier",
        "activatePotionOfLuck",
        "getRareTableWeightMultiplier",
        "activatePotionOfNotation",
        "isPotionOfNotationActive",
        "activateMagicResistancePotion",
        "activateMeleeResistancePotion",
        "activateRangedResistancePotion",
        "applyPotionMagicDamageReduction",
        "applyPotionMeleeDamageReduction",
        "applyPotionRangedDamageReduction",
        "setPoisonProtection(final long durationMs)",
    ):
        require(player_text, snippet, f"Player missing potion runtime hook: {snippet}")

    for snippet in (
        "useInsightPotion(player, item, ItemId.TWO_ATTACK_POTION.id(), 2, false);",
        "useRegenerationPotion(player, item, ItemId.TWO_STAT_RESTORATION_POTION.id(), 2, false);",
        "useSpeedPotion(player, item, ItemId.TWO_DEFENSE_POTION.id(), 2);",
        "useLuckPotion(player, item, ItemId.TWO_RESTORE_PRAYER_POTION.id(), 2);",
        "useMagicResistancePotion(player, item, ItemId.TWO_SUPER_ATTACK_POTION.id(), 2, false);",
        "useMeleeResistancePotion(player, item, ItemId.TWO_FISHING_POTION.id(), 2, false);",
        "useRangedResistancePotion(player, item, ItemId.TWO_SUPER_STRENGTH_POTION.id(), 2, false);",
        "useNotationPotion(player, item, ItemId.TWO_SUPER_DEFENSE_POTION.id(), 2);",
        "useInsightPotion(player, item, ItemId.TWO_POISON_ANTIDOTE.id(), 2, true);",
        "useRegenerationPotion(player, item, ItemId.TWO_POTION_OF_ZAMORAK.id(), 2, true);",
        "useMagicResistancePotion(player, item, ItemId.TWO_MAGIC_POTION.id(), 2, true);",
        "useMeleeResistancePotion(player, item, ItemId.TWO_POTION_OF_SARADOMIN.id(), 2, true);",
        "useRangedResistancePotion(player, item, ItemId.TWO_SUPER_RANGING_POTION.id(), 2, true);",
        "player.setPoisonProtection(TimeUnit.MINUTES.toMillis(15));",
    ):
        require(drinkables_text, snippet, f"Drinkables missing remapped potion behavior: {snippet}")

    require(stat_restore_text, "player.getPotionOfRegenerationMultiplier()", "Regeneration potion should affect hit restoration")
    require(combat_text, "player.getPotionAttackSpeedMultiplier()", "Melee combat should use potion speed multiplier")
    require(combat_text, "applyPotionMeleeDamageReduction", "Melee combat should apply melee resistance")
    require(pvm_melee_text, "player.getPotionAttackSpeedMultiplier()", "PvM melee should use potion speed multiplier")
    require(pvm_melee_text, "applyPotionMeleeDamageReduction", "PvM melee should apply melee resistance")
    require(range_utils_text, "player.getPotionAttackSpeedMultiplier()", "Ranged combat should use potion speed multiplier")
    require(projectile_text, "applyPotionMagicDamageReduction", "Projectile damage should apply magic resistance")
    require(projectile_text, "applyPotionRangedDamageReduction", "Projectile damage should apply ranged resistance")
    require(drop_table_text, "getRareTableWeightMultiplier()", "DropTable should use potion luck on rare tables")
    require(spell_handler_text, "applyPotionMagicDamageReduction", "Direct magic damage should apply magic resistance")

    print("PASS: potion runtime wiring validated")


if __name__ == "__main__":
    main()
