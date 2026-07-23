#!/usr/bin/env python3
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ENTITY_DEF = ROOT / "server/src/com/openrsc/server/external/EntityDef.java"
OBJECT_DEF = ROOT / "server/src/com/openrsc/server/external/GameObjectDef.java"
CLASSIFIER = ROOT / "server/src/com/openrsc/server/model/HostileProjectileCollision.java"
TILE = ROOT / "server/src/com/openrsc/server/model/world/region/TileValue.java"
FLAGS = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
CORE = ROOT / "server/core.jar"
LIB = ROOT / "server/lib/*"

HOSTILE_CALL_SITES = (
    ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEventNpc.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/DragonFireBreath.java",
    ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java",
)
ELDER_SPECIALS = (
    ROOT
    / "server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java"
)
PLAYER_PROJECTILES = (
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java",
    ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/MagicCombatEvent.java",
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
        require(HostileProjectileCollision.blocksScenery(definition) == {str(expected).lower()},
            "object {object_id} ({field(definitions[object_id], 'name')}) classification changed");
        }}
"""


fence_ids = {
    object_id
    for object_id, definition in enumerate(definitions)
    if "fence" in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    )
    or "palisade" in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    )
}
require(
    fence_ids == {597, 691, 718, 951},
    f"Fence definition inventory changed; review classifier fixtures: {sorted(fence_ids)}",
)
boundary_fence_ids = {
    object_id
    for object_id, definition in enumerate(boundary_definitions)
    if "fence" in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    )
    or "palisade" in (
        f"{field(definition, 'name')} {field(definition, 'description')}".lower()
    )
}
require(
    boundary_fence_ids == {4, 101, 127, 199},
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

classification_fixtures = "".join(
    [
        fixture(0, False),  # solid pine tree
        fixture(179, False),  # solid pottery wheel
        fixture(366, False),  # wallclockface is not a structural wall
        fixture(57, True),  # closed gate
        fixture(58, False),  # open gate
        fixture(180, True),  # closed Lumbridge/Al Kharid gate
        fixture(393, True),  # structural wall
        fixture(597, True),  # type-1 fence
        fixture(691, True),  # fence named Bridge Blockade
        fixture(718, True),  # second fence visual
        fixture(951, True),  # type-0 fence
        fixture(1000, False),  # open chest is ordinary scenery
    ]
)

HARNESS = f"""
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.model.HostileProjectileCollision;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class HostileProjectileCollisionHarness {{
    private static void require(boolean value, String message) {{
        if (!value) throw new AssertionError(message);
    }}

    public static void main(String[] args) {{
        TileValue uninitialized = new TileValue();
        require((uninitialized.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "uninitialized terrain must be hostile-projectile void");

        TileValue tile = new TileValue();
        tile.initializeTerrainCollision();
        require(tile.getHostileProjectileCollisionMask() == 0,
            "initialized ordinary ground must start transparent");

        tile.overlay = 11;
        tile.setTerrainBlocked(true);
        tile.setTerrainOverlayProjectileBlocked(true);
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "lava movement collision must not become hostile projectile cover");

        tile.overlay = 2;
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "water movement collision must not become hostile projectile cover");

        tile.overlay = 1;
        tile.addBlockingScenery();
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "ordinary solid scenery must not become hostile projectile cover");

        tile.addTerrainCollision(CollisionFlag.WALL_NORTH);
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.WALL_NORTH) != 0,
            "authored terrain wall must block hostile projectiles");
        tile.removeTerrainCollision(CollisionFlag.WALL_NORTH);

        tile.addHostileProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        tile.addHostileProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        tile.removeHostileProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "overlapping hard-cover ownership was removed too early");
        tile.removeHostileProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) == 0,
            "removed hard cover remained in hostile projectile collision");

        tile.overlay = 10;
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "void overlay must block hostile projectiles");

        TileValue copied = tile.copy();
        require(copied.equals(tile), "hostile projectile ownership was not copied");
        copied.overlay = 1;
        require((tile.getHostileProjectileCollisionMask() & CollisionFlag.FULL_BLOCK_C) != 0,
            "mutating a copied tile changed the original void tile");
{classification_fixtures}
    }}
}}
"""

with tempfile.TemporaryDirectory(prefix="hostile-projectile-collision-") as temp:
    harness = Path(temp) / "HostileProjectileCollisionHarness.java"
    harness.write_text(HARNESS, encoding="utf-8")
    subprocess.run(
        [
            "javac",
            "-d",
            temp,
            str(ENTITY_DEF),
            str(OBJECT_DEF),
            str(CLASSIFIER),
            str(FLAGS),
            str(TILE),
            str(harness),
        ],
        check=True,
    )
    subprocess.run(
        ["java", "-cp", temp, "HostileProjectileCollisionHarness"], check=True
    )

require(CORE.exists(), "Missing server/core.jar; run ./scripts/build-server.sh first")
WORLD_STUB = """
package com.openrsc.server.model.world;

import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;

public final class World {
    private final TileValue[][] tiles = new TileValue[8][8];

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

    public void setTile(int x, int y, TileValue tile) {
        tiles[x][y] = tile;
    }

    public RegionManager getRegionManager() {
        return null;
    }

    private static TileValue initializedTile() {
        TileValue tile = new TileValue();
        tile.initializeTerrainCollision();
        tile.overlay = 1;
        return tile;
    }
}
"""

PATH_HARNESS = """
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class HostileProjectilePathHarness {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        World world = new World();
        Point source = Point.location(1, 1);
        Point target = Point.location(4, 1);
        TileValue middle = world.getTile(2, 1);

        require(PathValidation.checkHostileProjectilePath(world, source, target),
            "clear hostile projectile path was blocked");

        middle.overlay = 11;
        middle.setTerrainBlocked(true);
        middle.setTerrainOverlayProjectileBlocked(true);
        require(PathValidation.checkHostileProjectilePath(world, source, target),
            "lava blocked semantic hostile projectile path");
        require(!PathValidation.checkPath(world, source, target, true),
            "legacy strict traversal fixture no longer demonstrates the lava safe spot");
        require(PathValidation.checkPath(world, source, target),
            "default player projectile path stopped honoring lava transparency");

        middle.overlay = 1;
        middle.addBlockingScenery();
        require(PathValidation.checkHostileProjectilePath(world, source, target),
            "ordinary solid scenery blocked semantic hostile projectile path");

        middle.addHostileProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require(!PathValidation.checkHostileProjectilePath(world, source, target),
            "fence/full hard cover did not block hostile projectile path");
        middle.removeHostileProjectileCollision(CollisionFlag.FULL_BLOCK_C);
        require(PathValidation.checkHostileProjectilePath(world, source, target),
            "removed hard cover continued blocking hostile projectile path");

        middle.overlay = 10;
        require(!PathValidation.checkHostileProjectilePath(world, source, target),
            "void did not block hostile projectile path");

        middle.overlay = 11;
        middle.addTerrainCollision(CollisionFlag.WALL_WEST);
        require(!PathValidation.checkHostileProjectilePath(world, source, target),
            "wall sharing transparent lava did not block hostile projectile path");
        middle.removeTerrainCollision(CollisionFlag.WALL_WEST);
        require(PathValidation.checkHostileProjectilePath(world, source, target),
            "lava remained blocked after its wall owner was removed");

        world.setTile(2, 1, new TileValue());
        require(!PathValidation.checkHostileProjectilePath(world, source, target),
            "uninitialized terrain did not block hostile projectile path");
    }
}
"""

with tempfile.TemporaryDirectory(prefix="hostile-projectile-path-") as temp:
    temp_path = Path(temp)
    stub = temp_path / "com/openrsc/server/model/world/World.java"
    stub.parent.mkdir(parents=True)
    stub.write_text(WORLD_STUB, encoding="utf-8")
    harness = temp_path / "HostileProjectilePathHarness.java"
    harness.write_text(PATH_HARNESS, encoding="utf-8")
    classpath = f"{CORE}:{LIB}"
    subprocess.run(
        [
            "javac",
            "-cp",
            classpath,
            "-d",
            temp,
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
            "HostileProjectilePathHarness",
        ],
        check=True,
    )

path_source = PATH_VALIDATION.read_text(encoding="utf-8")
require(
    "public static boolean checkHostileProjectilePath" in path_source
    and "t.getHostileProjectileCollisionMask()" in path_source,
    "hostile projectile path API must consume the dedicated semantic collision mask",
)

world_source = WORLD.read_text(encoding="utf-8")
require(
    "HostileProjectileCollision.blocksScenery" in world_source
    and "addHostileProjectileCollision" in world_source
    and "removeHostileProjectileCollision" in world_source,
    "world object registration must own reversible hard-cover collision",
)
require(
    "updateHostileProjectileBoundaryCollision(x, y, dir, true)" in world_source
    and "updateHostileProjectileBoundaryCollision(x, y, dir, false)" in world_source,
    "boundary walls and closed doors must register and unregister hard cover",
)

for call_site in HOSTILE_CALL_SITES:
    source = call_site.read_text(encoding="utf-8")
    require(
        "PathValidation.checkHostileProjectilePath(" in source,
        f"{call_site.name} bypasses semantic hostile projectile collision",
    )

elder_source = ELDER_SPECIALS.read_text(encoding="utf-8")
require(
    elder_source.count("if (!isValidProjectilePlayerTarget(dragon, player, AOE_RADIUS))")
    == 2,
    "Elder fireshot and burn must each validate every AOE target at launch",
)
require(
    "PathValidation.checkHostileProjectilePath(" in elder_source,
    "Elder AOE target validation bypasses semantic hostile collision",
)
require(
    "if (!isValidPlayerTarget(dragon, player, AOE_RADIUS)" in elder_source,
    "Elder fireshot delivery should retain lifecycle checks without a second line-of-fire check",
)

for player_projectile in PLAYER_PROJECTILES:
    source = player_projectile.read_text(encoding="utf-8")
    require(
        "PathValidation.checkPath(" in source
        and "PathValidation.checkHostileProjectilePath(" not in source,
        f"{player_projectile.name} must retain existing player projectile behavior",
    )

print("PASS: hostile projectile hard-cover ownership and attack-path policy validated")
