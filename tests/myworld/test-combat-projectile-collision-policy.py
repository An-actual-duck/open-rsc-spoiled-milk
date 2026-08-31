#!/usr/bin/env python3
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ENTITY_DEF = ROOT / "server/src/com/openrsc/server/external/EntityDef.java"
OBJECT_DEF = ROOT / "server/src/com/openrsc/server/external/GameObjectDef.java"
DOOR_DEF = ROOT / "server/src/com/openrsc/server/external/DoorDef.java"
CLASSIFIER = ROOT / "server/src/com/openrsc/server/model/CombatProjectileCollision.java"
TILE = ROOT / "server/src/com/openrsc/server/model/world/region/TileValue.java"
FLAGS = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
WORLD_LOADER = ROOT / "server/src/com/openrsc/server/io/WorldLoader.java"
IMPACT_POLICY = ROOT / "server/src/com/openrsc/server/model/combat/ProjectileImpactPolicy.java"
IMPACT_VALIDATOR = ROOT / "server/src/com/openrsc/server/model/combat/ProjectileImpactValidator.java"
MELEE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
WALK_TO_MOB = ROOT / "server/src/com/openrsc/server/model/action/WalkToMobAction.java"
CORE = ROOT / "server/core.jar"
LIB = ROOT / "server/lib/*"

ENEMY_CALL_SITES = (
    ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEventNpc.java",
)
NPC_DRAGON_BREATH_CALL_SITES = (
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/DragonFireBreath.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java",
    ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java",
)
PLAYER_ALLIED_CALL_SITES = (
    ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java",
    ROOT / "server/src/com/openrsc/server/content/Summoning.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/FireCannonEvent.java",
)


def require(condition, message):
    if not condition:
        raise AssertionError(message)


definitions = ET.parse(
    ROOT / "server/conf/server/defs/GameObjectDef.xml"
).getroot().findall("GameObjectDef")
boundary_definitions = ET.parse(
    ROOT / "server/conf/server/defs/DoorDef.xml"
).getroot().findall("DoorDef")


def field(definition, name):
    return definition.findtext(name) or ""


def java_definition(definition):
    values = {
        "name": field(definition, "name"),
        "description": field(definition, "description"),
        "command1": field(definition, "command1"),
        "command2": field(definition, "command2"),
        "objectModel": field(definition, "objectModel"),
    }
    statements = ["GameObjectDef definition = new GameObjectDef();"]
    statements.extend(
        f"definition.{name} = {json.dumps(value)};" for name, value in values.items()
    )
    statements.append(f"definition.type = {int(field(definition, 'type'))};")
    return "\n        ".join(statements)


def fixture(object_id, expected):
    return f"""
        {{
        {java_definition(definitions[object_id])}
        require(CombatProjectileCollision.sceneryCover(definition)
                == CombatProjectileCollision.Cover.{expected},
            "object {object_id} ({field(definitions[object_id], 'name')}) classification changed");
        }}
"""


def boundary_fixture(object_id, expected):
    definition = boundary_definitions[object_id]
    return f"""
        {{
        DoorDef definition = new DoorDef();
        definition.name = {json.dumps(field(definition, 'name'))};
        definition.description = {json.dumps(field(definition, 'description'))};
        definition.doorType = {int(field(definition, 'doorType'))};
        require(CombatProjectileCollision.boundaryCover(definition)
                == CombatProjectileCollision.Cover.{expected},
            "boundary {object_id} ({field(definition, 'name')}) classification changed");
        }}
"""


fence_ids = {
    object_id
    for object_id, definition in enumerate(definitions)
    if "fence" in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    )
    or any(word in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    ) for word in ("palisade", "railing"))
}
require(
    fence_ids == {45, 597, 691, 718, 951},
    f"Fence definition inventory changed; review classifier fixtures: {sorted(fence_ids)}",
)
boundary_fence_ids = {
    object_id
    for object_id, definition in enumerate(boundary_definitions)
    if "fence" in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    )
    or any(word in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    ) for word in ("palisade", "railing"))
}
require(
    boundary_fence_ids
    == {4, 5, 62, 101, 127, 166, 167, 168, 169, 170, 171, 172,
        181, 182, 183, 184, 185, 186, 193, 199},
    "Boundary-fence definition inventory changed; review hard-cover ownership: "
    f"{sorted(boundary_fence_ids)}",
)
require(
    all(
        int(field(boundary_definitions[object_id], "doorType")) == 1
        for object_id in boundary_fence_ids
    ),
    "Every boundary fence must remain a collision-registering doorType 1",
)
require(
    field(boundary_definitions[5], "name").lower() == "railings",
    "Rangers Guild railing boundary definition changed",
)

classification_fixtures = "".join(
    [
        fixture(0, "NONE"),  # solid pine tree
        fixture(179, "NONE"),  # solid pottery wheel
        fixture(366, "NONE"),  # wallclockface is not a structural wall
        fixture(57, "STRUCTURAL"),  # closed gate
        fixture(58, "NONE"),  # open gate
        fixture(180, "STRUCTURAL"),  # closed Lumbridge/Al Kharid gate
        fixture(393, "STRUCTURAL"),  # structural wall
        fixture(45, "ENEMY_ONLY_FENCE"),  # scenery railing
        fixture(597, "ENEMY_ONLY_FENCE"),  # type-1 fence
        fixture(691, "ENEMY_ONLY_FENCE"),  # fence named Bridge Blockade
        fixture(718, "ENEMY_ONLY_FENCE"),  # second fence visual
        fixture(951, "ENEMY_ONLY_FENCE"),  # type-0 fence
        fixture(1000, "NONE"),  # open chest is ordinary scenery
    ]
)
boundary_classification_fixtures = "".join(
    boundary_fixture(object_id, "ENEMY_ONLY_FENCE")
    for object_id in sorted(boundary_fence_ids)
) + boundary_fixture(0, "STRUCTURAL")

HARNESS = f"""
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.DoorDef;
import com.openrsc.server.model.CombatProjectileCollision;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class CombatProjectileCollisionHarness {{
    private static void require(boolean value, String message) {{
        if (!value) throw new AssertionError(message);
    }}

    public static void main(String[] args) {{
        TileValue uninitialized = new TileValue();
        require((uninitialized.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "uninitialized terrain must be combat-projectile void");

        TileValue tile = new TileValue();
        tile.initializeTerrainCollision();
        require(tile.getCombatProjectileCollisionMask() == 0,
            "initialized ordinary ground must start transparent");

        tile.overlay = 11;
        tile.setTerrainBlocked(true);
        tile.setTerrainOverlayProjectileBlocked(true);
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "lava movement collision must not become combat projectile cover");

        tile.overlay = 2;
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "water movement collision must not become combat projectile cover");

        tile.overlay = 1;
        tile.addBlockingScenery();
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "ordinary solid scenery must not become combat projectile cover");

        tile.addTerrainCollision(CollisionFlag.WALL_NORTH);
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.WALL_NORTH) == 0,
            "ordinary traversal collision leaked into semantic projectile cover");
        tile.addCombatProjectileCollision(CollisionFlag.WALL_NORTH);
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.WALL_NORTH) != 0,
            "semantic authored wall did not block combat projectiles");
        tile.removeCombatProjectileCollision(CollisionFlag.WALL_NORTH);
        tile.removeTerrainCollision(CollisionFlag.WALL_NORTH);

        tile.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        tile.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        tile.removeCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "overlapping hard-cover ownership was removed too early");
        tile.removeCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "removed hard cover remained in combat projectile collision");

        tile.addEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK_C);
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "enemy-only fence leaked into player-allied structural cover");
        require((tile.getEnemyProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "enemy-only fence did not enter hostile projectile cover");
        tile.removeEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK_C);
        require((tile.getEnemyProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "removed enemy-only fence remained in hostile projectile cover");

        tile.overlay = 10;
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "void overlay must block combat projectiles");

        TileValue copied = tile.copy();
        require(copied.equals(tile), "combat projectile ownership was not copied");
        copied.overlay = 1;
        require((tile.getCombatProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "mutating a copied tile changed the original void tile");
{classification_fixtures}
{boundary_classification_fixtures}
    }}
}}
"""

with tempfile.TemporaryDirectory(prefix="combat-projectile-collision-") as temp:
    harness = Path(temp) / "CombatProjectileCollisionHarness.java"
    harness.write_text(HARNESS, encoding="utf-8")
    subprocess.run(
        [
            "javac",
            "-d",
            temp,
            str(ENTITY_DEF),
            str(OBJECT_DEF),
            str(DOOR_DEF),
            str(CLASSIFIER),
            str(FLAGS),
            str(TILE),
            str(harness),
        ],
        check=True,
    )
    subprocess.run(
        ["java", "-cp", temp, "CombatProjectileCollisionHarness"], check=True
    )

require(CORE.exists(), "Missing server/core.jar; run ./scripts/build-server.sh first")
WORLD_STUB = """
package com.openrsc.server.model.world;

import com.openrsc.server.Server;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;

public final class World {
    private final TileValue[][] tiles = new TileValue[8][8];
    private final Server server = new Server();

    public World() {
        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[x].length; y++) {
                tiles[x][y] = initializedTile();
            }
        }
    }

    public TileValue getTile(int x, int y) {
        return tiles[x][y];
    }

    public TileValue getTile(WorldLocation location) {
        return getTile(
            location.getCoordinate().getX(),
            location.getCoordinate().getY());
    }

    public void setTile(int x, int y, TileValue tile) {
        tiles[x][y] = tile;
    }

    public RegionManager getRegionManager() {
        return null;
    }

    public Server getServer() {
        return server;
    }

    private static TileValue initializedTile() {
        TileValue tile = new TileValue();
        tile.initializeTerrainCollision();
        tile.overlay = 1;
        return tile;
    }
}
"""

SERVER_CONFIGURATION_STUB = """
package com.openrsc.server;

public final class ServerConfiguration {
    public boolean WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = false;
    public int NPC_BLOCKING = 0;
    public int PLAYER_BLOCKING = 0;
}
"""

SERVER_STUB = """
package com.openrsc.server;

public final class Server {
    private final ServerConfiguration configuration = new ServerConfiguration();

    public ServerConfiguration getConfig() {
        return configuration;
    }
}
"""

PATH_HARNESS = """
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class CombatProjectilePathHarness {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void requireSymmetric(World world, Point first, Point second,
            boolean expected, String message) {
        require(PathValidation.checkCombatProjectilePath(world, first, second)
                == expected, message + " (forward)");
        require(PathValidation.checkCombatProjectilePath(world, second, first)
                == expected, message + " (reverse)");
    }

    private static void requireEnemySymmetric(World world, Point first, Point second,
            boolean expected, String message) {
        require(PathValidation.checkEnemyCombatProjectilePath(world, first, second)
                == expected, message + " (forward)");
        require(PathValidation.checkEnemyCombatProjectilePath(world, second, first)
                == expected, message + " (reverse)");
    }

    public static void main(String[] args) {
        World world = new World();
        Point source = Point.location(1, 1);
        Point target = Point.location(4, 1);
        TileValue middle = world.getTile(2, 1);

        requireSymmetric(world, source, target, true,
            "clear combat projectile path was blocked");

        middle.overlay = 11;
        middle.setTerrainBlocked(true);
        middle.setTerrainOverlayProjectileBlocked(true);
        requireSymmetric(world, source, target, true,
            "lava blocked semantic combat projectile path");
        require(!PathValidation.checkPath(world, source, target, true),
            "legacy strict traversal fixture no longer demonstrates the lava safe spot");
        require(PathValidation.checkPath(world, source, target),
            "default player projectile path stopped honoring lava transparency");

        middle.overlay = 1;
        middle.addBlockingScenery();
        requireSymmetric(world, source, target, true,
            "ordinary solid scenery blocked semantic combat projectile path");

        middle.addEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK_C);
        requireSymmetric(world, source, target, true,
            "fence blocked a player-allied projectile");
        requireEnemySymmetric(world, source, target, false,
            "fence did not block an enemy projectile");
        middle.removeEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK_C);
        requireEnemySymmetric(world, source, target, true,
            "removed fence remained hostile projectile cover");

        // Structural hard cover represents walls and closed doors for both sides.
        middle.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        requireSymmetric(world, source, target, false,
            "wall/closed door did not block player-allied projectiles");
        requireEnemySymmetric(world, source, target, false,
            "wall/closed door did not block enemy projectiles");
        middle.removeCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        requireSymmetric(world, source, target, true,
            "open door continued blocking either combat direction");

        // Launch clear, close before delayed impact: the fresh impact check blocks.
        require(PathValidation.checkCombatProjectilePath(world, source, target),
            "clear launch fixture was blocked");
        middle.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require(!PathValidation.checkCombatProjectilePath(world, source, target),
            "door closed before impact did not block delivery");
        // Launch blocked, open before the next validation: current state is clear.
        middle.removeCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require(PathValidation.checkCombatProjectilePath(world, source, target),
            "door opened before validation remained blocked");

        middle.overlay = 2;
        requireSymmetric(world, source, target, true,
            "water blocked semantic combat projectile path");

        middle.overlay = 10;
        requireSymmetric(world, source, target, false,
            "void did not block combat projectile path");

        middle.overlay = 11;
        middle.addTerrainCollision(CollisionFlag.WALL_WEST);
        middle.addCombatProjectileCollision(CollisionFlag.WALL_WEST);
        requireSymmetric(world, source, target, false,
            "wall sharing transparent lava did not block combat projectile path");
        middle.removeCombatProjectileCollision(CollisionFlag.WALL_WEST);
        middle.removeTerrainCollision(CollisionFlag.WALL_WEST);
        requireSymmetric(world, source, target, true,
            "lava remained blocked after its wall owner was removed");

        World diagonalWorld = new World();
        Point diagonalSource = Point.location(1, 1);
        Point diagonalTarget = Point.location(3, 3);
        TileValue diagonalMiddle = diagonalWorld.getTile(2, 2);
        diagonalMiddle.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_A);
        requireSymmetric(diagonalWorld, diagonalSource, diagonalTarget, false,
            "diagonal barrier did not block both directions");
        diagonalMiddle.removeCombatProjectileCollision(CollisionFlag.FULL_BLOCK_A);
        requireSymmetric(diagonalWorld, diagonalSource, diagonalTarget, true,
            "removed diagonal barrier remained blocked");
        diagonalMiddle.addEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK_A);
        requireSymmetric(diagonalWorld, diagonalSource, diagonalTarget, true,
            "diagonal fence blocked player-allied projectiles");
        requireEnemySymmetric(diagonalWorld, diagonalSource, diagonalTarget, false,
            "diagonal fence did not block enemy projectiles");

        world.setTile(2, 1, new TileValue());
        requireSymmetric(world, source, target, false,
            "uninitialized terrain did not block combat projectile path");
    }
}
"""

with tempfile.TemporaryDirectory(prefix="combat-projectile-path-") as temp:
    temp_path = Path(temp)
    configuration_stub = temp_path / "com/openrsc/server/ServerConfiguration.java"
    configuration_stub.parent.mkdir(parents=True)
    configuration_stub.write_text(SERVER_CONFIGURATION_STUB, encoding="utf-8")
    server_stub = temp_path / "com/openrsc/server/Server.java"
    server_stub.write_text(SERVER_STUB, encoding="utf-8")
    stub = temp_path / "com/openrsc/server/model/world/World.java"
    stub.parent.mkdir(parents=True)
    stub.write_text(WORLD_STUB, encoding="utf-8")
    harness = temp_path / "CombatProjectilePathHarness.java"
    harness.write_text(PATH_HARNESS, encoding="utf-8")
    classpath = f"{CORE}:{LIB}"
    subprocess.run(
        [
            "javac",
            "-cp",
            classpath,
            "-d",
            temp,
            str(configuration_stub),
            str(server_stub),
            str(stub),
            str(FLAGS),
            str(TILE),
            str(PATH_VALIDATION),
            str(harness),
        ],
        check=True,
    )
    subprocess.run(
        [
            "java",
            "-cp",
            f"{temp}:{classpath}",
            "CombatProjectilePathHarness",
        ],
        check=True,
    )

path_source = PATH_VALIDATION.read_text(encoding="utf-8")
require(
    "public static boolean checkCombatProjectilePath" in path_source
    and "public static boolean checkEnemyCombatProjectilePath" in path_source
    and "public static boolean checkNpcDragonFireBreathPath" in path_source
    and "return checkEnemyCombatProjectilePath(world, src, dest);" in path_source
    and "t.getCombatProjectileCollisionMask()" in path_source
    and "t.getEnemyProjectileCollisionMask()" in path_source,
    "projectile path APIs must consume structural and enemy-fence masks",
)

world_source = WORLD.read_text(encoding="utf-8")
world_loader_source = WORLD_LOADER.read_text(encoding="utf-8")
require(
    "CombatProjectileCollision.sceneryCover" in world_source
    and "CombatProjectileCollision.boundaryCover" in world_source
    and "addCombatProjectileCollision" in world_source
    and "addEnemyProjectileFenceCollision" in world_source,
    "world object registration must own both reversible cover classes",
)
require(
    "applyCombatProjectileCollision(oldObject, false);" in world_source
    and "applyCombatProjectileCollision(newObject, true);" in world_source
    and "updateCombatProjectileBoundaryCollision(" in world_source,
    "boundary walls and closed doors must register and unregister hard cover",
)
require(
    "applyCombatProjectileBoundaryCollision(" in world_loader_source
    and "addEnemyProjectileFenceCollision" in world_loader_source
    and "addCombatProjectileCollision" in world_loader_source,
    "legacy authored terrain must derive semantic cover from boundary definitions",
)

for call_site in ENEMY_CALL_SITES:
    source = call_site.read_text(encoding="utf-8")
    require(
        "PathValidation.checkEnemyCombatProjectilePath(" in source,
        f"{call_site.name} bypasses enemy projectile collision",
    )

for call_site in NPC_DRAGON_BREATH_CALL_SITES:
    source = call_site.read_text(encoding="utf-8")
    require(
        "PathValidation.checkNpcDragonFireBreathPath(" in source,
        f"{call_site.name} bypasses shared hostile dragon-breath collision",
    )

elder_source = NPC_DRAGON_BREATH_CALL_SITES[-1].read_text(encoding="utf-8")
require(
    elder_source.count("if (!isValidProjectilePlayerTarget(dragon, player, AOE_RADIUS)")
    == 3,
    "Elder fireshot must validate at launch and impact, and burn at launch",
)
require(
    "PathValidation.checkNpcDragonFireBreathPath(" in elder_source,
    "Elder AOE target validation bypasses semantic combat collision",
)

for player_projectile in PLAYER_ALLIED_CALL_SITES:
    source = player_projectile.read_text(encoding="utf-8")
    require(
        "PathValidation.checkCombatProjectilePath(" in source,
        f"{player_projectile.name} bypasses shared combat projectile collision",
    )

impact_policy = IMPACT_POLICY.read_text(encoding="utf-8")
impact_validator = IMPACT_VALIDATOR.read_text(encoding="utf-8")
require(
    "GENERAL_PROJECTILE" not in impact_policy
    and "HOSTILE_PROJECTILE" not in impact_policy
    and impact_policy.count("Collision.PLAYER_ALLIED_PROJECTILE") == 5
    and impact_policy.count("Collision.ENEMY_PROJECTILE") == 1,
    "damaging projectile impact policies lost their explicit allegiance",
)
require(
    "case PLAYER_ALLIED_PROJECTILE:" in impact_validator
    and "case ENEMY_PROJECTILE:" in impact_validator
    and "PathValidation.checkCombatProjectilePath(" in impact_validator
    and "PathValidation.checkEnemyCombatProjectilePath(" in impact_validator
    and "PathValidation.checkPath(" not in impact_validator,
    "delayed projectile impact validation bypasses current hard-cover state",
)
for unaffected in (MELEE_EVENT, WALK_TO_MOB):
    require(
        "checkCombatProjectilePath(" not in unaffected.read_text(encoding="utf-8"),
        f"{unaffected.name} unexpectedly adopted combat projectile collision",
    )

print("PASS: asymmetric combat projectile cover and attack-path policy validated")
