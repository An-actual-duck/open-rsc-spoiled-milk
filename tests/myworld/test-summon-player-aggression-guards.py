#!/usr/bin/env python3
"""Ensure summons cannot inherit player aggression from their source NPCs."""

import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
NPC_BEHAVIOR = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java"
PVM_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PVP_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    summoning = SUMMONING.read_text(encoding="utf-8")
    mob = MOB.read_text(encoding="utf-8")
    npc_behavior = NPC_BEHAVIOR.read_text(encoding="utf-8")
    pvm_melee = PVM_MELEE.read_text(encoding="utf-8")
    pvp_melee = PVP_MELEE.read_text(encoding="utf-8")
    projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")

    require(
        summoning,
        "public static boolean canSummonAttack(final Mob attacker, final Mob target)",
        "Summoning should expose a shared target guard",
    )
    require(
        summoning,
        "if (isSummon(attacker) && target != null && target.isPlayer())",
        "Summons should not be allowed to attack player targets",
    )
    require(
        summoning,
        "&& !target.isPlayer()",
        "Summon assist target resolution should reject players",
    )
    require(
        mob,
        "victimIsSummon || !Summoning.canSummonAttack(this, victim)",
        "Combat start should reject summon-to-player combat",
    )
    require(
        npc_behavior,
        "if (Summoning.isSummon(npc)) {\n\t\t\treturn false;\n\t\t}",
        "Summon NPC behavior should skip vanilla aggro scans",
    )
    require(
        npc_behavior,
        "if (!Summoning.canSummonAttack(npc, target))",
        "NPC behavior should clear invalid summon player targets",
    )
    require(
        pvm_melee,
        "running && Summoning.canSummonAttack(attackerMob, targetMob)",
        "PvM melee events should stop if a summon has a player target",
    )
    for source, label in (
        (pvm_melee, "PvM melee"),
        (pvp_melee, "generic combat"),
        (projectile_event, "projectile"),
    ):
        require(
            source,
            "if (!Summoning.canSummonAttack(",
            f"{label} damage should be guarded against summon-to-player damage",
        )

    print("PASS: summon player aggression guards validated")


if __name__ == "__main__":
    main()
