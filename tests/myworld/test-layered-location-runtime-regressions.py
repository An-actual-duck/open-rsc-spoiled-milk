#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
PLUGINS = ROOT / "server/plugins/com/openrsc/server/plugins"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    require(start >= 0, f"missing method {signature}")
    brace = source.find("{", start)
    require(brace >= 0, f"missing body for {signature}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace : index + 1]
    raise AssertionError(f"unterminated body for {signature}")


def invocation_bodies(source: str, call: str):
    offset = 0
    while True:
        start = source.find(call, offset)
        if start < 0:
            return
        open_paren = source.find("(", start + len(call) - 1)
        depth = 0
        for index in range(open_paren, len(source)):
            if source[index] == "(":
                depth += 1
            elif source[index] == ")":
                depth -= 1
                if depth == 0:
                    yield source[start : index + 1]
                    offset = index + 1
                    break
        else:
            raise AssertionError(f"unterminated invocation of {call}")


def invocation_arguments(invocation: str) -> list[str]:
    open_paren = invocation.find("(")
    require(open_paren >= 0 and invocation.endswith(")"), "invalid invocation")
    arguments = []
    start = open_paren + 1
    depth = 0
    for index in range(start, len(invocation) - 1):
        character = invocation[index]
        if character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
        elif character == "," and depth == 0:
            arguments.append(invocation[start:index].strip())
            start = index + 1
    arguments.append(invocation[start:-1].strip())
    return arguments


def test_teleport_contract() -> None:
    player = read(SERVER / "model/entity/player/Player.java")
    teleport = method_body(
        player,
        "public void teleport(final int x, final int y, final boolean bubble)",
    )
    current_scope = method_body(
        player,
        "public void teleportCurrentScope(\n\t\tfinal int x,",
    )
    legacy_alias = method_body(player, "public void teleportLegacy(")

    require(
        "LegacyPackedPointAdapter.fromPackedValues(x, y)" in teleport,
        "integer teleport must keep the historical packed-Y contract",
    )
    require(
        ".fromRuntimeCompatibilityPoint(" in current_scope
        and "Point.location(x, y), current, false" in current_scope,
        "current-scope teleport must resolve locally and fail closed",
    )
    require(
        "teleport(x, packedY, bubble);" in legacy_alias,
        "explicit legacy alias must delegate to the restored integer contract",
    )

    summon_player = method_body(player, "public Point summon(final Player summonTo)")
    summon_store = method_body(player, "public void setSummonReturnPoint()")
    summon_return = method_body(player, "public Point returnFromSummon()")
    jail_store = method_body(player, "private void setJailReturnPoint()")
    jail_return = method_body(player, "public Point releaseFromJail()")
    legacy_return_store = method_body(
        player,
        "private void storeLegacyReturnLocation(\n\t\tfinal String xKey,",
    )
    require(
        "teleportLayered(summonTo.getWorldLocation(), true);" in summon_player,
        "player-to-player summons must retain the destination's exact layer",
    )
    require(
        'storeLayeredReturnLocation("return")' in player
        and 'teleportToLayeredReturnLocation("return", true)' in summon_return,
        "summon returns must retain the player's exact layered origin",
    )
    require(
        'storeLayeredReturnLocation("jail_return")' in player
        and 'teleportToLayeredReturnLocation("jail_return", true)' in jail_return,
        "jail returns must retain the player's exact layered origin",
    )
    legacy_return = method_body(player, "private void storeLegacyReturnLocation(")
    require(
        "LegacyPackedPointAdapter.toLegacyPoint(" in legacy_return
        and "getCache().remove(xKey, yKey);" in legacy_return
        and 'storeLegacyReturnLocation("return_x", "return_y")' in player
        and 'storeLegacyReturnLocation("jail_return_x", "jail_return_y")' in player,
        "rollback return caches must store packed legacy coordinates or no fallback",
    )
    require(
        'storeLegacyReturnLocation("return_x", "return_y")' in summon_store
        and 'storeLegacyReturnLocation("jail_return_x", "jail_return_y")'
        in jail_store
        and "LegacyPackedPointAdapter.toLegacyPoint(" in legacy_return_store
        and "getLayeredLocation()" in legacy_return_store,
        "legacy return keys must be packed from the exact layered origin",
    )
    require(
        "getCache().remove(xKey, yKey);" in legacy_return_store
        and 'hasLegacyReturnLocation("return_x", "return_y")' in summon_return
        and 'hasLegacyReturnLocation(\n\t\t\t\t"jail_return_x", "jail_return_y")'
        in jail_return,
        "unrepresentable layered returns must not fabricate surface fallback keys",
    )

    runtime_files = (
        PLUGINS / "authentic/misc/RandomObjects.java",
        PLUGINS / "authentic/quests/free/ShieldOfArrav.java",
        PLUGINS / "authentic/quests/members/legendsquest/npcs/LegendsQuestViyeldi.java",
        PLUGINS / "authentic/quests/members/legendsquest/obstacles/LegendsQuestCaveAgility.java",
        PLUGINS / "authentic/quests/members/legendsquest/obstacles/LegendsQuestGameObjects.java",
        PLUGINS / "authentic/quests/members/touristtrap/TouristTrap.java",
        PLUGINS / "authentic/quests/members/touristtrap/Tourist_Trap_Mechanism.java",
        PLUGINS / "authentic/quests/members/undergroundpass/obstacles/UndergroundPassAgilityObstacles.java",
        PLUGINS / "authentic/quests/members/undergroundpass/obstacles/UndergroundPassDungeonFloor.java",
        PLUGINS / "authentic/quests/members/undergroundpass/obstacles/UndergroundPassObstaclesMap1.java",
        PLUGINS / "authentic/quests/members/undergroundpass/obstacles/UndergroundPassObstaclesMap2.java",
        PLUGINS / "authentic/quests/members/watchtower/WatchTowerObstacles.java",
        PLUGINS / "authentic/skills/woodcutting/WoodcutJungle.java",
        PLUGINS / "custom/misc/RangersGuildDoor.java",
        SERVER / "model/WalkingQueue.java",
        SERVER / "net/rsc/handlers/BlinkHandler.java",
        SERVER / "net/rsc/handlers/GameObjectWallAction.java",
        SERVER / "plugins/RuneScript.java",
    )
    missing = [str(path.relative_to(ROOT)) for path in runtime_files if not path.exists()]
    require(not missing, f"runtime teleport audit paths missing: {missing}")
    runtime_calls = sum(read(path).count(".teleportCurrentScope(") for path in runtime_files)
    require(runtime_calls == 53, f"expected 53 audited current-scope teleports, found {runtime_calls}")

    rune_script = read(SERVER / "plugins/RuneScript.java")
    rune_teleport = method_body(rune_script, "public static void teleport()")
    require(
        "player.teleportLegacy(" in rune_teleport,
        "RuneScript's fixed active-coordinate teleport must remain absolute",
    )

    wall_action = read(SERVER / "net/rsc/handlers/GameObjectWallAction.java")
    ladders = read(PLUGINS / "authentic/defaults/Ladders.java")
    game_object = read(SERVER / "model/entity/GameObject.java")
    entity_handler = read(SERVER / "external/EntityHandler.java")
    require(
        "getObjectTelePoint(" in wall_action
        and "object.getWorldLocation(), command" in wall_action
        and "getPlayer().teleportLegacy(" in wall_action,
        "configured object telepoints must retain their packed destination contract",
    )
    require(
        "getObjectTelePoint(obj.getWorldLocation(), command)" in ladders
        and "getObjectTelePoint(getWorldLocation(), null)" in game_object
        and "LegacyPackedPointAdapter.toLegacyPoint(location)" in entity_handler,
        "configured telepoint sources must be projected from their exact layered locations",
    )

    functions = read(SERVER / "plugins/Functions.java")
    underground_pass = read(
        PLUGINS
        / "authentic/quests/members/undergroundpass/obstacles/UndergroundPassObstaclesMap1.java"
    )
    require(
        "public static void teleportCurrentScope(Player player, int x, int y)" in functions
        and "player.teleportCurrentScope(x, y);" in functions,
        "plugin helpers must expose an explicit current-scope movement contract",
    )
    require(
        functions.count("teleportCurrentScope(player, object.get") == 46
        and underground_pass.count("teleportCurrentScope(player, object.get") == 24
        and "teleport(player, object.get" not in functions
        and "teleport(player, object.get" not in underground_pass,
        "door and obstacle helpers must not decode runtime object coordinates as packed teleports",
    )

    staff = read(PLUGINS / "authentic/commands/Event.java")
    staff_teleport = method_body(staff, "private void teleportCommand(")
    scoped_radius = method_body(
        staff,
        "private static WorldLocation furthestWalkableTileInScope(",
    )
    require(
        "private static void teleportStaffDestination(" in staff
        and "target.teleportLayered(scopedDestination, bubble);" in staff
        and "target.teleport(" in staff,
        "staff teleports must distinguish Player locations from typed legacy coordinates",
    )
    scoped_staff_radius = method_body(
        staff, "private static WorldLocation furthestWalkableTileInScope("
    )
    require(
        "PathValidation.checkPath(" in staff
        and "anchor.getWorld().getTile(candidate)" in staff
        and "return center;" in scoped_staff_radius,
        "radius staff teleports must search and fail closed in the target's exact scope",
    )
    require(
        "furthestWalkableTileInScope(" in staff_teleport
        and ".furthestWalkableTile(player.getWorld(), radius)" in staff_teleport
        and ".fromRuntimeCompatibilityPoint(" not in staff_teleport
        and "scopedTeleportTo = tpTo.getWorldLocation();" in staff_teleport,
        "staff radius teleports must use scoped collision while exact Player and legacy paths remain intact",
    )
    require(
        "new WorldLocation(" in scoped_radius
        and "coordinate.getLevel()" in scoped_radius
        and "isWalkableTileInScope(anchor, center, candidate)" in scoped_radius
        and "return center;" in scoped_radius,
        "staff radius candidates must retain the target's exact world space and level",
    )
    scoped_walkable = method_body(
        staff,
        "private static boolean isWalkableTileInScope(",
    )
    require(
        "PathValidation.checkPath(" in scoped_walkable
        and "getTile(candidate)" in scoped_walkable
        and "CollisionFlag.FULL_BLOCK" in scoped_walkable
        and scoped_walkable.count("return false;") >= 3,
        "staff radius collision must reject blocked, missing, and out-of-scope tiles",
    )


def test_scoped_collision_consumers() -> None:
    path_validation = read(SERVER / "model/PathValidation.java")
    require(
        "return checkDistancePath(" in path_validation
        and "nativeLayeredTileLookup(world, src)" in path_validation,
        "native paths must execute the collision algorithm",
    )
    require(
        "hasNativeLayeredTerrain(candidate)" in path_validation
        and "failClosedOnMissingTile()" in path_validation,
        "scoped tile lookup must reject missing native terrain",
    )
    require(
        "syntheticDeepTileLookup(world)" in path_validation,
        "the gated synthetic fixture must retain bounded collision checks",
    )
    require(
        "return world.getRegionManager().hasNativeLayeredTerrain(dest);"
        not in path_validation,
        "native path validation must not accept endpoint existence as reachability",
    )
    mob_blocking = method_body(
        path_validation,
        "private static boolean checkBlocking(Mob mob, int x, int y, int bit, boolean isCurrentTile)",
    )
    require(
        "hasNativeLayeredTerrain(candidate)" in mob_blocking
        and "if (t == null)" in mob_blocking,
        "mob-scoped walking must fail closed at native terrain holes",
    )

    offenders = []
    for source_root in (SERVER, PLUGINS):
        for path in source_root.rglob("*.java"):
            if path.name in {"PathValidation.java", "Point.java"}:
                continue
            for invocation in invocation_bodies(read(path), "PathValidation.checkPath("):
                if ".getLocation()" in invocation:
                    offenders.append(str(path.relative_to(ROOT)))
    require(
        not offenders,
        "entity-originated paths still discard level data: "
        + ", ".join(sorted(set(offenders))),
    )

    mob = read(SERVER / "model/entity/Mob.java")
    astar = read(SERVER / "model/AStarPathfinder.java")
    npc_behavior = read(SERVER / "model/entity/npc/NpcBehavior.java")
    npc = read(SERVER / "model/entity/npc/Npc.java")
    pvm = read(SERVER / "event/rsc/impl/combat/PvmMeleeEvent.java")
    require(
        "if (!sharesSpatialDomain(o))" in mob
        and "isTraversalClearAtCurrentLevel(" in mob,
        "object reach must use the object's domain and current-level collision",
    )
    require(
        ".hasNativeLayeredTerrain(candidate)" in mob
        and "return null;" in method_body(mob, "public final TileValue getTileAtCurrentLevel("),
        "current-level tile reads must not fall through package holes",
    )
    require(
        "tile == null || (tile.traversalMask" in astar,
        "A* must treat missing scoped tiles as blocked",
    )
    require(
        "canEngageTargetFrom" in npc_behavior
        and "target.getWorldLocation()" in npc_behavior,
        "NPC aggression must qualify collision checks to the target level",
    )
    can_aggro = method_body(
        npc_behavior,
        "private boolean canAggro(final Mob target, final long now, final boolean forceAggressive)",
    )
    preferred_threat = method_body(
        npc,
        "private boolean isPreferredThreatEligible(",
    )
    require(
        "!npc.sharesSpatialDomain(target)" in can_aggro
        and "!sharesSpatialDomain(player)" in preferred_threat,
        "NPC aggro and retained damage threat must reject targets from another level",
    )
    require(
        "attackerMob.sharesSpatialDomain(targetMob)" in pvm
        and "attackerMob, targetMob, true, false" in pvm,
        "PvM melee must reject cross-level adjacency",
    )


def test_summons_and_stairs() -> None:
    summoning = read(SERVER / "content/Summoning.java")
    npc = read(SERVER / "model/entity/npc/Npc.java")
    ladders = read(PLUGINS / "authentic/defaults/Ladders.java")

    require(
        summoning.count("final WorldLocation spawnLocation = adjacentWorldLocation(owner);") == 2
        and summoning.count("new Npc(owner.getWorld(), profile.npcId, spawnLocation)") == 2,
        "manual and armor summons must spawn directly in the owner's scope",
    )
    require(
        "public Npc(" in npc
        and "final WorldLocation location" in npc
        and "super.setWorldLocation(initialWorldLocation, true);" in npc,
        "NPC construction must install authoritative location before registration",
    )
    require(
        "public void teleport(final WorldLocation location)" in npc
        and "prepareNativeLayeredTransition(" in npc
        and "super.setWorldLocation(destination, true);" in npc,
        "NPC cross-scope relocation must use an explicit signed destination",
    )
    require(
        "owner.sharesSpatialDomain(player)" in summoning
        and "owner.sharesSpatialDomain(npc)" in summoning
        and "summon.getWorldLocation()" in summoning,
        "summon visibility, occupancy, and projectiles must retain the owner level",
    )
    require(
        summoning.count("summon.teleport(adjacentWorldLocation(owner));") == 2
        and "summon.teleport(destination.getX(), destination.getY())" not in summoning,
        "active summons must catch up across levels with an exact owner-scoped destination",
    )
    require(
        "matchesLegacyPackedLocation(obj, 516, 1479)" in ladders
        and "player.teleportLegacy(516, 2426, false);" in ladders,
        "Legends' Guild stairs must compare and teleport with packed levels",
    )


def test_scoped_dynamic_npc_factories_and_quest_triggers() -> None:
    functions = read(SERVER / "plugins/Functions.java")
    npc = read(SERVER / "model/entity/npc/Npc.java")
    require(
        functions.count("resolveCurrentScopeLocation(") >= 5
        and "public static Npc addnpc(Mob scope, int id, int x, int y)" in functions,
        "runtime NPC factories must resolve coordinates inside an owner scope",
    )
    scoped_factory = method_body(
        functions, "private static WorldLocation resolveCurrentScopeLocation("
    )
    require(
        "!scope.getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY" in scoped_factory
        and "LegacyPackedPointAdapter.fromPackedValues(x, y)" in scoped_factory,
        "scoped NPC factories must preserve packed-Y behavior with authority disabled",
    )
    require(
        "final WorldLocation location," in npc
        and "final int radius)" in npc
        and "x - radius, x + radius" in npc,
        "authoritative NPC construction must preserve requested roaming radius",
    )
    npc_legacy_teleport = method_body(
        npc, "public void teleport(final int x, final int y)"
    )
    npc_current_teleport = method_body(
        npc, "public void teleportCurrentScope(final int x, final int y)"
    )
    require(
        "LegacyPackedPointAdapter.fromPackedValues(x, y)" in npc_legacy_teleport
        and ".fromRuntimeCompatibilityPoint(" in npc_current_teleport
        and "Point.location(x, y), getWorldLocation(), false" in npc_current_teleport,
        "NPC teleports must separate fixed packed destinations from current-scope movement",
    )

    npc_runtime_files = (
        PLUGINS / "authentic/quests/members/digsite/DigsiteWinch.java",
        PLUGINS / "authentic/quests/members/touristtrap/TouristTrap.java",
        PLUGINS / "authentic/quests/members/touristtrap/Tourist_Trap_Mechanism.java",
        PLUGINS / "authentic/quests/members/shilovillage/ShiloVillageTombDolmen.java",
        PLUGINS
        / "authentic/quests/members/legendsquest/mechanism/LegendsQuestBullRoarer.java",
    )
    require(
        sum(read(path).count(".teleportCurrentScope(") for path in npc_runtime_files)
        >= 7,
        "audited runtime-relative NPC movement must use the explicit current-scope API",
    )

    scoped_first = 0
    scoped_last = 0
    legacy_runtime_offenders = []
    numeric = re.compile(r"-?\d+")
    for path in PLUGINS.rglob("*.java"):
        for invocation in invocation_bodies(read(path), "addnpc("):
            arguments = invocation_arguments(invocation)
            if len(arguments) < 4:
                continue
            first = arguments[0]
            if "getWorld()" in first:
                if len(arguments) >= 4 and not (
                    numeric.fullmatch(arguments[2])
                    and numeric.fullmatch(arguments[3])
                ):
                    legacy_runtime_offenders.append(str(path.relative_to(ROOT)))
                continue
            if first in {"player", "requestPlayer", "necromancer"}:
                if not (
                    numeric.fullmatch(arguments[2])
                    and numeric.fullmatch(arguments[3])
                ):
                    scoped_first += 1
                continue
            if arguments[-1] == "player" and not (
                numeric.fullmatch(arguments[1])
                and numeric.fullmatch(arguments[2])
            ):
                scoped_last += 1
    require(
        not legacy_runtime_offenders,
        "dynamic NPC spawns still discard scope: "
        + ", ".join(sorted(set(legacy_runtime_offenders))),
    )
    require(
        scoped_first == 60 and scoped_last == 11,
        f"expected 71 audited scoped NPC spawns, found {scoped_first + scoped_last}",
    )

    viyeldi = read(
        PLUGINS / "authentic/quests/members/legendsquest/npcs/LegendsQuestViyeldi.java"
    )
    shield = read(PLUGINS / "authentic/quests/free/ShieldOfArrav.java")
    require(
        "LegacyPackedPointAdapter.fromPackedValues(426, 3708)" in viyeldi
        and "VIYELDI_HAT_LOCATION.equals(item.getWorldLocation())" in viyeldi
        and "i.getY() == 3708" not in viyeldi,
        "Viyeldi's underground trigger must match an exact layered item location",
    )
    require(
        "LegacyPackedPointAdapter.fromPackedValues(110, 3370)" in shield
        and "PHOENIX_GANG_ENTRANCE.equals(object.getWorldLocation())" in shield
        and shield.count("player.getY() < obj.getY()") == 2
        and "obj.getY() == 3370" not in shield,
        "Shield of Arrav's underground door must use a layered trigger and relative side test",
    )


def main() -> int:
    tests = (
        test_teleport_contract,
        test_scoped_collision_consumers,
        test_summons_and_stairs,
        test_scoped_dynamic_npc_factories_and_quest_triggers,
    )
    for test in tests:
        test()
    print("PASS: layered location runtime regressions are guarded")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as error:
        print(f"FAIL: {error}")
        sys.exit(1)
