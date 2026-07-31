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
        if "final WorldLocation spawnLocation = adjacentWorldLocation(owner);" not in body:
            failures.append(
                f"{label} summons should choose an owner-scoped adjacent location"
            )
        if "new Npc(owner.getWorld(), profile.npcId, spawnLocation)" not in body:
            failures.append(
                f"{label} summons should initialize at the authoritative location"
            )
        if "new Npc(owner.getWorld(), profile.npcId, owner.getX(), owner.getY())" in body:
            failures.append(f"{label} summons must not spawn directly on the owner tile")

    if "final int initialOffset = DataConversions.random(0, offsets.length - 1);" not in summoning:
        failures.append("adjacent summon placement should randomize its first candidate")
    if "private static boolean isValidAdjacentSummonTile(final Player owner, final int x, final int y)" not in summoning:
        failures.append("adjacent summon placement should validate candidate tiles")
    if "CollisionFlag.FULL_BLOCK" not in summoning:
        failures.append("summon spawn validation should reject blocked tiles")
    if "owner.getWorld().getTile(destination)" not in summoning:
        failures.append("summon spawn validation should read the scoped candidate tile")
    if re.search(
        r"PathValidation\.checkAdjacentDistance\(\s*owner\.getWorld\(\),"
        r"\s*owner\.getWorldLocation\(\),\s*destination,",
        summoning,
    ) is None:
        failures.append(
            "summon spawn validation should check scoped movement to the candidate"
        )
    if "private static boolean isSummonSpawnTileOccupied(final Player owner, final int x, final int y)" not in summoning:
        failures.append("summon spawn validation should avoid occupied adjacent tiles")
    if "owner.getViewArea().getPlayersInView()" not in summoning or "owner.getViewArea().getNpcsInView()" not in summoning:
        failures.append("summon spawn occupancy checks should use nearby entities")
    if "owner.getWorld().getPlayers()" in summoning or "owner.getWorld().getNpcs()" in summoning:
        failures.append("summon spawn occupancy checks must not scan whole-world entity lists")
    if "owner.sharesSpatialDomain(player)" not in summoning or "owner.sharesSpatialDomain(npc)" not in summoning:
        failures.append("summon occupancy checks should remain on the owner's level")
    if re.search(
        r"PathValidation\.checkPath\(\s*summon\.getWorld\(\),"
        r"\s*summon\.getWorldLocation\(\),\s*target\.getWorldLocation\(\),",
        summoning,
    ) is None:
        failures.append("summon projectile paths should use authoritative locations")
    if summoning.count("summon.teleport(adjacentWorldLocation(owner));") != 2:
        failures.append(
            "summon catch-up paths should relocate to the owner's exact layered scope"
        )
    if "summon.teleport(destination.getX(), destination.getY())" in summoning:
        failures.append("summon catch-up paths must not discard the owner's level")

    if failures:
        print("FAIL:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("PASS: summons spawn on validated adjacent tiles")
    return 0


if __name__ == "__main__":
    sys.exit(main())
