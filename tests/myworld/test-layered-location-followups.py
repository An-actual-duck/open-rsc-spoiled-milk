#!/usr/bin/env python3
import re
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
PLUGINS = ROOT / "server/plugins/com/openrsc/server/plugins"
CORE = ROOT / "server/core.jar"
LIB = ROOT / "server/lib/*"


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


SERVER_CONFIGURATION_STUB = r"""
package com.openrsc.server;

public final class ServerConfiguration {
    public boolean WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
    public int NPC_BLOCKING = 0;
    public int PLAYER_BLOCKING = 0;
}
"""


SERVER_STUB = r"""
package com.openrsc.server;

public final class Server {
    private final ServerConfiguration configuration = new ServerConfiguration();

    public ServerConfiguration getConfig() {
        return configuration;
    }
}
"""


WORLD_STUB = r"""
package com.openrsc.server.model.world;

import com.openrsc.server.Server;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;

import java.util.HashMap;
import java.util.Map;

public final class World {
    private final Map<String, TileValue> tiles = new HashMap<String, TileValue>();
    private final Server server = new Server();
    private final RegionManager regionManager = new RegionManager(this);

    public World() {
        for (int level : new int[] {0, 1, -1}) {
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    setTile(location(x, y, level), initializedTile());
                }
            }
        }
    }

    public Server getServer() {
        return server;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public TileValue getTile(int x, int y) {
        return getTile(location(x, y, 0));
    }

    public TileValue getTile(WorldLocation location) {
        return tiles.get(key(location));
    }

    public void setTile(WorldLocation location, TileValue tile) {
        tiles.put(key(location), tile);
    }

    public void removeTile(WorldLocation location) {
        tiles.remove(key(location));
    }

    public boolean hasNativeTerrain(WorldLocation location) {
        int level = location.getCoordinate().getLevel();
        int x = location.getCoordinate().getX();
        int y = location.getCoordinate().getY();
        return WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
            && (level == 0 || level == 1 || level == -1)
            && x >= 0 && x < 8 && y >= 0 && y < 8;
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(
            new com.openrsc.server.model.world.coordinate.WorldCoordinate(
                x, y, level));
    }

    private static String key(WorldLocation location) {
        return location.getWorldSpace().getValue() + ":"
            + location.getCoordinate().getLevel() + ":"
            + location.getCoordinate().getX() + ":"
            + location.getCoordinate().getY();
    }

    private static TileValue initializedTile() {
        TileValue tile = new TileValue();
        tile.initializeTerrainCollision();
        tile.overlay = 1;
        return tile;
    }
}
"""


REGION_MANAGER_STUB = r"""
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;

public final class RegionManager {
    private final World world;

    public RegionManager(World world) {
        this.world = world;
    }

    public boolean hasNativeLayeredTerrain(WorldLocation location) {
        return world.hasNativeTerrain(location);
    }

    public Point toRuntimeCompatibilityPoint(WorldLocation location) {
        return Point.location(
            location.getCoordinate().getX(),
            location.getCoordinate().getY());
    }

    public WorldLocation fromRuntimeCompatibilityPoint(
        Point point,
        WorldLocation scope,
        boolean allowExplicitScopeExit) {
        return new WorldLocation(
            scope.getWorldSpace(),
            new WorldCoordinate(
                point.getX(), point.getY(),
                scope.getCoordinate().getLevel()));
    }

    public boolean isNpcBlockedByScenery(Npc npc, int x, int y) {
        return false;
    }

    public Npc findInteractionNpc(Point point, Entity observer) {
        return null;
    }

    public Player findInteractionPlayer(
        int x, int y, Entity observer, boolean includeRemoved) {
        return null;
    }
}
"""


HARNESS = r"""
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.PlayerReturnLocationStore;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class LayeredLocationFollowupsHarness {
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void requireLocation(
        WorldLocation actual,
        WorldLocation expected,
        String message) {
        require(expected.equals(actual), message + ": " + actual);
    }

    private static void testReturnLocations() {
        Cache cache = new Cache();
        WorldLocation surface = location(320, 440, 0);
        WorldLocation upper = location(516, 535, 1);
        WorldLocation underground = location(370, 432, -1);

        PlayerReturnLocationStore.storeExact(cache, "return", upper);
        PlayerReturnLocationStore.storeLegacyProjection(
            cache, "return_x", "return_y", upper);
        requireLocation(
            PlayerReturnLocationStore.readExact(cache, "return").get(),
            upper,
            "summon return lost its upper level");
        Point upperFallback = PlayerReturnLocationStore.readLegacy(
            cache, "return_x", "return_y").get();
        require(upperFallback.getX() == 516 && upperFallback.getY() == 1479,
            "summon rollback fallback was not packed");

        PlayerReturnLocationStore.storeExact(
            cache, "jail_return", underground);
        PlayerReturnLocationStore.storeLegacyProjection(
            cache, "jail_return_x", "jail_return_y", underground);
        requireLocation(
            PlayerReturnLocationStore.readExact(cache, "jail_return").get(),
            underground,
            "jail return lost its underground level");
        Point undergroundFallback = PlayerReturnLocationStore.readLegacy(
            cache, "jail_return_x", "jail_return_y").get();
        require(undergroundFallback.getX() == 370
                && undergroundFallback.getY() == 3264,
            "jail rollback fallback was not packed");

        PlayerReturnLocationStore.storeExact(cache, "surface", surface);
        PlayerReturnLocationStore.storeLegacyProjection(
            cache, "surface_x", "surface_y", surface);
        require(PlayerReturnLocationStore.readLegacy(
                cache, "surface_x", "surface_y").get().getY() == 440,
            "surface fallback changed plane");

        Cache fallbackOnly = new Cache();
        fallbackOnly.set("return_x", 371);
        fallbackOnly.set("return_y", 3266);
        require(!PlayerReturnLocationStore.readExact(
                fallbackOnly, "return").isPresent(),
            "legacy-only return fabricated exact metadata");
        requireLocation(
            LegacyPackedPointAdapter.fromLegacyPoint(
                PlayerReturnLocationStore.readLegacy(
                    fallbackOnly, "return_x", "return_y").get()),
            location(371, 434, -1),
            "legacy return fallback decoded to the wrong level");

        Cache partial = new Cache();
        partial.store("return_layered_space", "global");
        partial.set("return_layered_x", 10);
        require(!PlayerReturnLocationStore.readExact(partial, "return").isPresent(),
            "partial exact metadata was accepted");
        partial.set("return_layered_y", 20);
        partial.put("return_layered_level", "not-an-integer");
        require(!PlayerReturnLocationStore.readExact(partial, "return").isPresent(),
            "corrupt exact metadata was accepted");

        Cache unrepresentable = new Cache();
        unrepresentable.set("return_x", 1);
        unrepresentable.set("return_y", 2);
        WorldLocation instanceLocation = new WorldLocation(
            new WorldSpaceId("instance-test"),
            new WorldCoordinate(12, 34, 7));
        PlayerReturnLocationStore.storeExact(
            unrepresentable, "return", instanceLocation);
        PlayerReturnLocationStore.storeLegacyProjection(
            unrepresentable, "return_x", "return_y", instanceLocation);
        requireLocation(
            PlayerReturnLocationStore.readExact(
                unrepresentable, "return").get(),
            instanceLocation,
            "unrepresentable exact return was discarded");
        require(!unrepresentable.hasKey("return_x")
                && !unrepresentable.hasKey("return_y"),
            "unrepresentable return fabricated a legacy surface fallback");
        PlayerReturnLocationStore.clearExact(unrepresentable, "return");
        require(!PlayerReturnLocationStore.readExact(
                unrepresentable, "return").isPresent(),
            "exact return cleanup was not idempotent");
        PlayerReturnLocationStore.clearExact(unrepresentable, "return");
    }

    private static void testPackedStairsAndTelepoints() {
        requireLocation(
            LegacyPackedPointAdapter.fromPackedValues(368, 438),
            location(368, 438, 0),
            "Heroes surface trigger decoded incorrectly");
        requireLocation(
            LegacyPackedPointAdapter.fromPackedValues(371, 3266),
            location(371, 434, -1),
            "Heroes basement destination decoded incorrectly");
        requireLocation(
            LegacyPackedPointAdapter.fromPackedValues(370, 3264),
            location(370, 432, -1),
            "Heroes basement trigger decoded incorrectly");
        requireLocation(
            LegacyPackedPointAdapter.fromPackedValues(516, 1479),
            location(516, 535, 1),
            "Legends upper trigger decoded incorrectly");
        requireLocation(
            LegacyPackedPointAdapter.fromPackedValues(516, 2426),
            location(516, 538, 2),
            "Legends second-floor destination decoded incorrectly");

        for (WorldLocation source : new WorldLocation[] {
                location(10, 20, 0),
                location(10, 20, 1),
                location(10, 20, -1)}) {
            Point packed = LegacyPackedPointAdapter.toLegacyPoint(source);
            requireLocation(
                LegacyPackedPointAdapter.fromLegacyPoint(packed),
                source,
                "telepoint source did not round trip");
        }
    }

    private static void testScopedCollision() {
        World world = new World();
        WorldLocation surfaceStart = location(1, 1, 0);
        WorldLocation surfaceEnd = location(4, 1, 0);
        WorldLocation upperStart = location(1, 1, 1);
        WorldLocation upperEnd = location(4, 1, 1);
        WorldLocation undergroundStart = location(1, 1, -1);
        WorldLocation undergroundEnd = location(4, 1, -1);

        require(PathValidation.checkPath(
                world, surfaceStart, surfaceEnd, false),
            "clear surface path was rejected");
        require(PathValidation.checkPath(world, upperStart, upperEnd, false),
            "clear upper path was rejected");
        require(PathValidation.checkPath(
                world, undergroundStart, undergroundEnd, false),
            "clear underground path was rejected");
        require(!PathValidation.checkPath(
                world, surfaceStart, upperEnd, false),
            "cross-level path was accepted");
        require(!PathValidation.checkCombatProjectilePath(
                world, surfaceStart, upperEnd),
            "cross-level combat projectile path was accepted");
        require(!PathValidation.checkPath(
                world,
                surfaceStart,
                new WorldLocation(
                    new WorldSpaceId("instance-test"),
                    new WorldCoordinate(4, 1, 0)),
                false),
            "cross-space path was accepted");
        require(!PathValidation.checkCombatProjectilePath(
                world,
                surfaceStart,
                new WorldLocation(
                    new WorldSpaceId("instance-test"),
                    new WorldCoordinate(4, 1, 0))),
            "cross-space combat projectile path was accepted");

        WorldLocation upperMiddle = location(2, 1, 1);
        TileValue upperBlocker = world.getTile(upperMiddle);
        upperBlocker.setTerrainBlocked(true);
        upperBlocker.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require(!PathValidation.checkPath(world, upperStart, upperEnd, false),
            "upper-layer wall did not block its own path");
        require(PathValidation.checkPath(
                world, surfaceStart, surfaceEnd, false),
            "upper-layer wall leaked onto the surface");
        require(PathValidation.checkPath(
                world, undergroundStart, undergroundEnd, false),
            "upper-layer wall leaked underground");
        require(!PathValidation.checkCombatProjectilePath(
                world, upperStart, upperEnd),
            "upper-layer wall did not block hostile line of fire");

        upperBlocker.setTerrainBlocked(false);
        upperBlocker.removeCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        upperBlocker.addTerrainCollision(CollisionFlag.FULL_BLOCK_C);
        upperBlocker.addEnemyProjectileFenceCollision(
            CollisionFlag.FULL_BLOCK_C);
        require(PathValidation.checkCombatProjectilePath(
                world, upperStart, upperEnd),
            "layered authored fence blocked a player-allied projectile");
        require(!PathValidation.checkEnemyCombatProjectilePath(
                world, upperStart, upperEnd),
            "layered authored fence allowed an enemy projectile");
        upperBlocker.removeEnemyProjectileFenceCollision(
            CollisionFlag.FULL_BLOCK_C);
        upperBlocker.removeTerrainCollision(CollisionFlag.FULL_BLOCK_C);
        world.removeTile(upperMiddle);
        require(!PathValidation.checkPath(world, upperStart, upperEnd, false),
            "missing native tile did not fail closed");
        require(!PathValidation.checkAdjacentDistance(
                world,
                location(1, 1, 1),
                upperMiddle,
                false,
                true),
            "missing adjacent native tile did not fail closed");
        require(PathValidation.checkPath(
                world, surfaceStart, surfaceEnd, false),
            "missing upper tile contaminated surface collision");
    }

    private static void testLegacyMissingTerrainFailsClosed() {
        World world = new World();
        world.getServer().getConfig()
            .WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = false;
        Point start = Point.location(1, 1);
        Point end = Point.location(4, 1);

        require(PathValidation.checkPath(world, start, end, false),
            "clear legacy path was rejected");
        require(PathValidation.checkCombatProjectilePath(world, start, end),
            "clear legacy projectile path was rejected");

        world.removeTile(location(2, 1, 0));
        require(!PathValidation.checkPath(world, start, end, false),
            "missing legacy tile allowed line travel");
        require(!PathValidation.checkCombatProjectilePath(world, start, end),
            "missing legacy tile allowed combat projectile travel");
        require(!PathValidation.checkAdjacentDistance(
                world, 1, 1, 2, 1, false, true),
            "missing legacy tile allowed adjacent movement");
        require(!PathValidation.checkAdjacentDistance(
                world, 7, 1, 8, 1, false, true),
            "out-of-world legacy destination allowed adjacent movement");
    }

    public static void main(String[] args) {
        testReturnLocations();
        testPackedStairsAndTelepoints();
        testScopedCollision();
        testLegacyMissingTerrainFailsClosed();
    }
}
"""


def compile_and_run_fixture() -> None:
    require(CORE.exists(), "missing server/core.jar; run ./scripts/build-server.sh first")
    with tempfile.TemporaryDirectory(prefix="layered-location-followups-") as temp:
        temp_path = Path(temp)
        sources: list[Path] = []
        for relative, content in (
            ("com/openrsc/server/ServerConfiguration.java", SERVER_CONFIGURATION_STUB),
            ("com/openrsc/server/Server.java", SERVER_STUB),
            ("com/openrsc/server/model/world/World.java", WORLD_STUB),
            (
                "com/openrsc/server/model/world/region/RegionManager.java",
                REGION_MANAGER_STUB,
            ),
            ("LayeredLocationFollowupsHarness.java", HARNESS),
        ):
            path = temp_path / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(path)

        coordinate_sources = [
            SERVER / "model/world/coordinate/WorldSpaceId.java",
            SERVER / "model/world/coordinate/WorldCoordinate.java",
            SERVER / "model/world/coordinate/WorldLocation.java",
            SERVER / "model/world/coordinate/LegacyPackedPointAdapter.java",
            SERVER / "model/world/coordinate/LayeredCompatibilityPointAdapter.java",
        ]
        current_sources = [
            SERVER / "model/Cache.java",
            SERVER / "model/PlayerReturnLocationStore.java",
            SERVER / "model/PathValidation.java",
            SERVER / "model/world/region/TileValue.java",
            SERVER / "util/rsc/CollisionFlag.java",
        ]
        classpath = f"{CORE}:{LIB}"
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                classpath,
                "-d",
                temp,
                *(str(path) for path in sources + coordinate_sources + current_sources),
            ],
            check=True,
        )
        subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{classpath}",
                "LayeredLocationFollowupsHarness",
            ],
            check=True,
        )


def check_integration_contracts() -> None:
    player = (SERVER / "model/entity/player/Player.java").read_text(encoding="utf-8")
    npc = (SERVER / "model/entity/npc/Npc.java").read_text(encoding="utf-8")
    summoning = (SERVER / "content/Summoning.java").read_text(encoding="utf-8")
    ladders = (PLUGINS / "authentic/defaults/Ladders.java").read_text(
        encoding="utf-8"
    )
    event = (PLUGINS / "authentic/commands/Event.java").read_text(encoding="utf-8")
    functions = (SERVER / "plugins/Functions.java").read_text(encoding="utf-8")

    player_legacy = method_body(
        player, "public void teleport(final int x, final int y, final boolean bubble)"
    )
    npc_legacy = method_body(npc, "public void teleport(final int x, final int y)")
    require(
        "setLocation(Point.location(x, y), true);" in player_legacy
        and "LegacyPackedPointAdapter" not in player_legacy,
        "Player integer teleport contract was inverted",
    )
    require(
        "setLocation(Point.location(x, y), true);" in npc_legacy
        and "LegacyPackedPointAdapter" not in npc_legacy,
        "NPC integer teleport contract was inverted",
    )
    require(
        "public void teleportLegacyPacked(" in player
        and "LegacyPackedPointAdapter.fromPackedValues(x, packedY)" in player,
        "explicit packed Player teleport contract is missing",
    )
    require(
        "teleportLayered(summonTo.getWorldLocation(), true);" in player
        and "PlayerReturnLocationStore.storeExact(" in player
        and "PlayerReturnLocationStore.readExact(" in player,
        "summon/jail return paths do not preserve exact locations",
    )
    require(
        summoning.count("final WorldLocation spawnLocation = adjacentWorldLocation(owner);")
        == 2
        and summoning.count("new Npc(owner.getWorld(), profile.npcId, spawnLocation)")
        == 2
        and "owner.sharesSpatialDomain(player)" in summoning
        and "owner.sharesSpatialDomain(npc)" in summoning,
        "summons are not scoped to the owner's exact location",
    )
    require(
        "matchesLegacyPackedLocation(obj, 368, 438)" in ladders
        and "matchesLegacyPackedLocation(obj, 370, 3264)" in ladders
        and "matchesLegacyPackedLocation(obj, 516, 1479)" in ladders
        and ladders.count("teleportLegacyPacked(") >= 4,
        "migrated Heroes/Legends stairs do not use explicit packed contracts",
    )
    require(
        "getObjectTelePoint(obj.getWorldLocation(), command)" in ladders,
        "configured telepoint lookup discarded its source layer",
    )
    require(
        "scopedTeleportTo = tpTo.getWorldLocation();" in event
        and "target.teleportLayered(scopedDestination, bubble);" in event,
        "staff Player destinations do not preserve exact layers",
    )
    require(
        "teleportCurrentScope" not in functions,
        "rejected broad current-scope compatibility facade was reintroduced",
    )

    projectile_paths = (
        SERVER / "event/rsc/impl/projectile/FireCannonEvent.java",
        SERVER / "event/rsc/impl/projectile/MagicCombatEvent.java",
        SERVER / "event/rsc/impl/projectile/RangeEvent.java",
        SERVER / "event/rsc/impl/projectile/ThrowingEvent.java",
    )
    for path in projectile_paths:
        source = path.read_text(encoding="utf-8")
        require(
            "getWorldLocation()" in source
            and "PathValidation.checkCombatProjectilePath(" in source,
            f"{path.name} discarded layer identity during path validation",
        )

    overbroad_paths = (
        PLUGINS / "authentic/quests/free/ShieldOfArrav.java",
        PLUGINS / "authentic/quests/members/touristtrap/TouristTrap.java",
        PLUGINS
        / "authentic/quests/members/undergroundpass/obstacles/UndergroundPassObstaclesMap1.java",
        SERVER / "plugins/RuneScript.java",
    )
    for path in overbroad_paths:
        source = path.read_text(encoding="utf-8")
        require(
            "teleportCurrentScope" not in source,
            f"rejected broad migration returned in {path.relative_to(ROOT)}",
        )

    path_validation = (SERVER / "model/PathValidation.java").read_text(
        encoding="utf-8"
    )
    require(
        "nativeLayeredTileLookup(world, src)" in path_validation
        and "failClosedOnMissingTile" not in path_validation
        and re.search(r"if \(t == null\) \{\s*return true;", path_validation),
        "collision lookup does not fail closed consistently for missing terrain",
    )
    require(
        not re.search(
            r"return\s+world\.getRegionManager\(\)\s*\.hasNativeLayeredTerrain\(dest",
            path_validation,
        ),
        "layered collision regressed to endpoint-existence validation",
    )


def main() -> None:
    compile_and_run_fixture()
    check_integration_contracts()
    print("PASS: bounded layered location behavior and contracts validated")


if __name__ == "__main__":
    main()
