#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
ATTACK_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/AttackHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"Missing {label}: {snippet}")


def main() -> None:
    summoning = SUMMONING.read_text(encoding="utf-8")
    npc = NPC.read_text(encoding="utf-8")
    attack_handler = ATTACK_HANDLER.read_text(encoding="utf-8")

    require(summoning, "SUPPORT_LIFE_RUNE_UPKEEP_DISPLAYED_XP = 10", "support command pulse XP base")
    require(summoning, "SUPPORT_COMMAND_XP_PULSE_MS = 60000", "support command one-minute pulse")
    require(summoning, "PACK_RAT_UTILITY_BASE_DISPLAYED_XP = 75", "pack rat base XP")
    require(summoning, "PACK_RAT_UTILITY_PER_ITEM_DISPLAYED_XP = 5", "pack rat per-item XP")
    require(summoning, "PACK_RAT_UTILITY_MAX_DISPLAYED_XP = 150", "pack rat XP cap")
    require(summoning, "DELIVERY_CAMEL_UTILITY_DISPLAYED_XP = 225", "camel flat XP")
    require(summoning, "COMBAT_SUMMON_CREDIT_TIMEOUT_MS = 120000", "combat summon XP timeout")
    require(summoning, "getCombatSummonCreditTimeoutTicks(owner)", "combat summon XP timeout conversion")
    require(
        summoning,
        "target.recordPendingSummoningExperience(owner, internalExperience(displayedExperience), expiresTick);",
        "pending combat summon XP credit recording",
    )
    require(
        summoning,
        "if (owner.getCarriedItems().getInventory().countId(ItemId.LIFE_RUNE.id(), Optional.of(false)) < amount) {\n\t\t\treturn false;\n\t\t}\n\t\tif (shouldPreserveRuneCost(owner, ItemId.LIFE_RUNE.id())) {\n\t\t\treturn true;\n\t\t}",
        "life rune preservation should not award support command XP",
    )
    if "awardDisplayedSummoningExperience(owner, getSupportCommandDisplayedExperience(owner));" not in summoning:
        fail("support command XP must use independent active-time pulse")
    consume_start = summoning.index("private static boolean consumeLifeRunes")
    consume_end = summoning.index("private static int getSupportCommandDisplayedExperience")
    if "awardDisplayedSummoningExperience" in summoning[consume_start:consume_end]:
        fail("support command XP must not be tied to life-rune consumption")
    require(
        summoning,
        "awardDisplayedSummoningExperience(player, DELIVERY_CAMEL_UTILITY_DISPLAYED_XP);",
        "camel utility XP award",
    )
    require(
        npc,
        "private Map<Long, PendingSummoningExperience> pendingSummoningExperience",
        "per-player pending summoning XP map",
    )
    require(
        npc,
        "pendingSummoningExperience.put(player.getUsernameHash(), new PendingSummoningExperience(experience, expiresTick));",
        "per-player pending summoning XP key",
    )
    require(npc, "public boolean hasDamageBy(final Player player)", "damage participation guard")
    require(npc, "player.incExp(Skill.SUMMONING.id(), pending.experience, true);", "pending XP payout")
    require(npc, "awardPendingSummoningExperience();", "pending XP payout on NPC death")
    if re.search(
        r"Summoning\.recordCombatSummonEngagement\(\s*"
        r"getPlayer\(\),\s*\(Npc\) getMob\(\)\s*\);",
        attack_handler,
    ) is None:
        fail("Missing combat initiation hook")

    print("PASS: summoning experience rewards are wired")


if __name__ == "__main__":
    main()
