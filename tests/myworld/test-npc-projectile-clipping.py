#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
NPC_BEHAVIOR = ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java"
NPC_RANGE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEventNpc.java"
PLAYER_PROJECTILES = (
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java",
)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


path_validation = PATH_VALIDATION.read_text()
require(
    "DistanceCollisionMode.COMBAT_PROJECTILE"
    in path_validation,
    "Combat projectile path validation must use its semantic collision mode",
)

require(
    "PathValidation.checkCombatProjectilePath("
    in NPC_BEHAVIOR.read_text(),
    "Modern hostile NPC projectiles must use semantic hostile collision",
)
require(
    "PathValidation.checkCombatProjectilePath("
    in NPC_RANGE.read_text(),
    "Legacy hostile NPC ranged attacks must use semantic hostile collision",
)

for path in PLAYER_PROJECTILES:
    source = path.read_text()
    require(
        "PathValidation.checkCombatProjectilePath(" in source,
        f"{path.name} must share semantic combat projectile collision",
    )

print("Combat projectile clipping checks passed")
