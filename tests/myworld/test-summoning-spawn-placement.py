#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"


def main() -> int:
    failures: list[str] = []
    summoning = SUMMONING.read_text(encoding="utf-8")

    manual_spawn = re.search(
        r"private static void spawnManualSummon\(final Player owner, final SummonProfile profile\) \{(?P<body>.*?)"
        r"\n\tprivate static int getSummonArrivalEffect",
        summoning,
        re.S,
    )
    armor_spawn = re.search(
        r"private static void spawnArmorSummon\(final Player owner, final SummonProfile profile\) \{(?P<body>.*?)"
        r"\n\tprivate static String getSummonDisplayName",
        summoning,
        re.S,
    )

    for label, match in (("manual", manual_spawn), ("armor", armor_spawn)):
        if match is None:
            failures.append(f"Could not find {label} summon spawn block")
            continue
        body = match.group("body")
        if "final Point spawnLocation = adjacentTo(owner);" not in body:
            failures.append(f"{label} summons should choose an adjacent spawn location")
        if "new Npc(owner.getWorld(), profile.npcId, owner.getX(), owner.getY())" in body:
            failures.append(f"{label} summons must not spawn directly on the owner tile")

    if "final int initialOffset = DataConversions.random(0, offsets.length - 1);" not in summoning:
        failures.append("adjacent summon placement should randomize its first candidate")
    if "private static boolean isValidAdjacentSummonTile(final Player owner, final int x, final int y)" not in summoning:
        failures.append("adjacent summon placement should validate candidate tiles")
    if "CollisionFlag.FULL_BLOCK" not in summoning:
        failures.append("summon spawn validation should reject blocked tiles")
    if "PathValidation.checkAdjacentDistance(owner.getWorld(), owner.getX(), owner.getY(), x, y, true, false)" not in summoning:
        failures.append("summon spawn validation should check movement from owner to candidate tile")
    if "private static boolean isSummonSpawnTileOccupied(final Player owner, final int x, final int y)" not in summoning:
        failures.append("summon spawn validation should avoid occupied adjacent tiles")
    if "owner.getViewArea().getPlayersInView()" not in summoning or "owner.getViewArea().getNpcsInView()" not in summoning:
        failures.append("summon spawn occupancy checks should use nearby entities")
    if "owner.getWorld().getPlayers()" in summoning or "owner.getWorld().getNpcs()" in summoning:
        failures.append("summon spawn occupancy checks must not scan whole-world entity lists")

    if failures:
        print("FAIL:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("PASS: summons spawn on validated adjacent tiles")
    return 0


if __name__ == "__main__":
    sys.exit(main())
