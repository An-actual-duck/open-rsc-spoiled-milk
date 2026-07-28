#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
REGISTRY = (
    SERVER
    / "src/com/openrsc/server/model/world/AuthoredLayeredGroundItemRegistry.java"
)
OBJECT_REGISTRY = (
    SERVER
    / "src/com/openrsc/server/model/world/NativeLayeredGameObjectRegistry.java"
)
GAME_OBJECT_LOC = SERVER / "src/com/openrsc/server/external/GameObjectLoc.java"
WORLD = SERVER / "src/com/openrsc/server/model/world/World.java"
GROUND_ITEM = SERVER / "src/com/openrsc/server/model/entity/GroundItem.java"
REGION_MANAGER = (
    SERVER
    / "src/com/openrsc/server/model/world/region/RegionManager.java"
)
DEVELOPMENT = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
FUNCTIONS = SERVER / "src/com/openrsc/server/plugins/Functions.java"


HARNESS = r"""
import com.openrsc.server.model.world.AuthoredLayeredGroundItemRegistry;
import com.openrsc.server.model.world.NativeLayeredGameObjectRegistry;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.model.world.coordinate.NativeLayeredGameObjectIdentity;
import com.openrsc.server.model.world.coordinate.NativeLayeredGameObjectIdentitySlot;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class NativeLayeredPlacementRegistryFixture {
    public static void main(String[] args) {
        AuthoredLayeredGroundItemRegistry<Object> registry =
            new AuthoredLayeredGroundItemRegistry<Object>();
        WorldLocation deep = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(448, 600, -2));
        WorldLocation surface = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(448, 600, 0));
        Object deepItem = new Object();
        Object surfaceItem = new Object();

        check(registry.register(deep, () -> deepItem) == deepItem,
            "register deep");
        check(registry.register(deep, Object::new) == deepItem,
            "deduplicate exact layered spawn");
        check(registry.register(surface, () -> surfaceItem) == surfaceItem,
            "same XY remains distinct across levels");
        check(registry.size() == 2, "two layered identities");
        check(registry.remove(deep, new Object())
                == AuthoredLayeredGroundItemRegistry.NO_GENERATION,
            "foreign instance cannot release spawn");
        long generation = registry.remove(deep, deepItem);
        check(generation >= 0 && registry.size() == 1, "release exact spawn");
        check(registry.registerForGeneration(deep, generation, () -> deepItem)
                == deepItem,
            "same-generation respawn");
        registry.reset();
        check(registry.size() == 0, "reset");
        check(registry.registerForGeneration(deep, generation, Object::new)
                == null,
            "stale timer refused");

        NativeLayeredGameObjectRegistry<Object> objects =
            new NativeLayeredGameObjectRegistry<Object>();
        long objectGeneration = objects.getGeneration();
        WorldLocation tableLocation = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(446, 604, -2));
        GameTickEventRestorationCollisionFootprintPlanner.Result table =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(3, 446, 604, 0, 0),
                Definition.scenery(1, 1, 1, "Table", new String[0]),
                false,
                WorldBounds.of(1000, 1000));
        Object tableObject = new Object();
        check(objects.register(
                objectGeneration, "deep-table", tableLocation,
                0, 0, tableObject, table,
                java.util.Collections.singleton(tableLocation))
                == tableObject,
            "register layered scenery");
        check(objects.hasNpcBlockingSceneryAt(tableLocation),
            "exact NPC-blocking scenery lookup");
        TileValue deepTableTile = emptyTile();
        objects.applyCollision(tableLocation, deepTableTile);
        check((deepTableTile.traversalMask & CollisionFlag.FULL_BLOCK_C) != 0,
            "deep scenery collision composed");
        TileValue surfaceTableTile = emptyTile();
        objects.applyCollision(
            new WorldLocation(
                WorldSpaceId.GLOBAL, new WorldCoordinate(446, 604, 0)),
            surfaceTableTile);
        check((surfaceTableTile.traversalMask & CollisionFlag.FULL_BLOCK) == 0,
            "same XY surface collision remains isolated");
        check(!objects.hasNpcBlockingSceneryAt(
                new WorldLocation(
                    WorldSpaceId.GLOBAL, new WorldCoordinate(446, 604, 0))),
            "NPC-blocking scenery lookup remains level isolated");

        NativeLayeredGameObjectRegistry<Object> directionalObjects =
            new NativeLayeredGameObjectRegistry<Object>();
        long directionalGeneration = directionalObjects.getGeneration();
        WorldLocation directionalOrigin = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(470, 604, -2));
        WorldLocation directionalSecond = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(471, 604, -2));
        GameTickEventRestorationCollisionFootprintPlanner.Result directional =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(5, 470, 604, 0, 0),
                Definition.scenery(
                    2, 2, 1, "Directional scenery", new String[0]),
                false,
                WorldBounds.of(1000, 1000));
        Object directionalObject = new Object();
        check(directionalObjects.register(
                directionalGeneration, "directional", directionalOrigin,
                0, 0, directionalObject, directional,
                java.util.Arrays.asList(
                    directionalOrigin, directionalSecond))
                == directionalObject,
            "register directional scenery occupancy");
        check(directionalObjects.hasNpcBlockingSceneryAt(directionalOrigin)
                && directionalObjects.hasNpcBlockingSceneryAt(
                    directionalSecond),
            "directional scenery exact footprint blocks NPCs");
        check(!directionalObjects.hasNpcBlockingSceneryAt(
                new WorldLocation(
                    WorldSpaceId.GLOBAL,
                    new WorldCoordinate(469, 604, -2))),
            "directional collision neighbor is not scenery occupancy");
        directionalObjects.unregister(
            directionalGeneration, "directional", directionalObject);
        check(!directionalObjects.hasNpcBlockingSceneryAt(directionalOrigin),
            "directional scenery removal clears exact occupancy");

        WorldLocation cartLocation = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(465, 663, 0));
        GameTickEventRestorationCollisionFootprintPlanner.Result cart =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(769, 465, 663, 8, 0),
                Definition.scenery(0, 1, 1, "Travel cart", new String[0]),
                false,
                WorldBounds.of(1000, 1000));
        Object cartObject = new Object();
        check(objects.register(
                objectGeneration, "spoiled-milk-cart", cartLocation,
                0, 8, cartObject, cart,
                java.util.Collections.<WorldLocation>emptyList())
                == cartObject,
            "register authored direction-eight scenery");
        check(!objects.hasNpcBlockingSceneryAt(cartLocation),
            "collisionless scenery does not block NPCs");
        check(objects.unregister(
                objectGeneration, "spoiled-milk-cart", cartObject)
                == cartObject,
            "remove authored direction-eight scenery");

        WorldLocation fenceLocation = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(448, 604, -2));
        GameTickEventRestorationCollisionFootprintPlanner.Result fence =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(4, 448, 604, 0, 1),
                Definition.boundary(
                    1, "Fence", new String[] {"fence"}),
                false,
                WorldBounds.of(1000, 1000));
        Object fenceObject = new Object();
        objects.register(
            objectGeneration, "deep-fence", fenceLocation,
            1, 0, fenceObject, fence,
            java.util.Collections.<WorldLocation>emptyList());
        TileValue fenceNorth = emptyTile();
        objects.applyCollision(fenceLocation, fenceNorth);
        check((fenceNorth.traversalMask & CollisionFlag.WALL_NORTH) != 0,
            "boundary north collision composed");
        TileValue fenceSouth = emptyTile();
        objects.applyCollision(
            new WorldLocation(
                WorldSpaceId.GLOBAL, new WorldCoordinate(448, 603, -2)),
            fenceSouth);
        check((fenceSouth.traversalMask & CollisionFlag.WALL_SOUTH) != 0,
            "boundary reciprocal collision composed");
        TileValue fenceProjectile = emptyTile();
        objects.applyCollision(
            new WorldLocation(
                WorldSpaceId.GLOBAL, new WorldCoordinate(447, 604, -2)),
            fenceProjectile);
        check(fenceProjectile.getDynamicProjectileCount() == 1,
            "allowlisted boundary projectile footprint composed");
        check(objects.size() == 2 && objects.countType(0) == 1
                && objects.countType(1) == 1,
            "typed package object counts");
        check(objects.getCollisionTileCount() == 4,
            "combined collision tile count");

        WorldLocation doorLocation = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(452, 604, -2));
        GameTickEventRestorationCollisionFootprintPlanner.Result door =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(2, 452, 604, 0, 1),
                Definition.boundary(
                    1, "Door", new String[] {"fence"}),
                false,
                WorldBounds.of(1000, 1000));
        Object closedDoor = new Object();
        check(objects.register(
                objectGeneration, "deep-door", doorLocation,
                1, 0, closedDoor, door,
                java.util.Collections.<WorldLocation>emptyList())
                == closedDoor,
            "register package door");
        check(objects.size() == 3 && objects.countType(0) == 1
                && objects.countType(1) == 2,
            "fixture object counts");
        check(objects.getCollisionTileCount() == 6,
            "closed fixture collision tile count");

        GameTickEventRestorationCollisionFootprintPlanner.Result doorframe =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(1, 452, 604, 0, 1),
                Definition.boundary(0, "Doorframe", new String[0]),
                false,
                WorldBounds.of(1000, 1000));
        Object openDoorframe = new Object();
        check(objects.replace(
                objectGeneration, "deep-door", closedDoor,
                doorLocation, 1, 0, openDoorframe, doorframe,
                java.util.Collections.<WorldLocation>emptyList())
                == openDoorframe,
            "replace package boundary");
        check(objects.find("deep-door") == openDoorframe
                && objects.size() == 3,
            "replacement retains placement identity");
        TileValue openedNorth = emptyTile();
        objects.applyCollision(doorLocation, openedNorth);
        check((openedNorth.traversalMask & CollisionFlag.WALL_NORTH) == 0,
            "replacement removes closed boundary collision");
        TileValue openedSouth = emptyTile();
        objects.applyCollision(
            new WorldLocation(
                WorldSpaceId.GLOBAL, new WorldCoordinate(452, 603, -2)),
            openedSouth);
        check((openedSouth.traversalMask & CollisionFlag.WALL_SOUTH) == 0,
            "replacement removes reciprocal boundary collision");
        check(objects.getCollisionTileCount() == 4,
            "replacement commits exact collision delta");

        check(objects.unregister(
                objectGeneration, "deep-door", openDoorframe)
                == openDoorframe,
            "remove package boundary");
        check(objects.find("deep-door") == null && objects.size() == 2,
            "removal releases placement identity");
        check(objects.register(
                objectGeneration, "deep-door", doorLocation,
                1, 0, closedDoor, door,
                java.util.Collections.<WorldLocation>emptyList())
                == closedDoor,
            "same-generation delayed restoration");
        check(objects.getCollisionTileCount() == 6,
            "restoration reinstates exact collision");

        WorldLocation treeLocation = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(456, 604, -2));
        GameTickEventRestorationCollisionFootprintPlanner.Result tree =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(1, 456, 604, 0, 0),
                Definition.scenery(
                    1, 1, 1, "Tree",
                    new String[] {"tree", "treestump"}),
                false,
                WorldBounds.of(1000, 1000));
        GameTickEventRestorationCollisionFootprintPlanner.Result stump =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(4, 456, 604, 0, 0),
                Definition.scenery(
                    1, 1, 1, "Treestump",
                    new String[] {"tree", "treestump"}),
                false,
                WorldBounds.of(1000, 1000));
        Object liveTree = new Object();
        check(objects.register(
                objectGeneration, "deep-tree", treeLocation,
                0, 0, liveTree, tree,
                java.util.Collections.singleton(treeLocation))
                == liveTree,
            "register package tree");
        check(objects.hasNpcBlockingSceneryAt(treeLocation),
            "tree occupies exact NPC-blocking tile");
        check(objects.size() == 4 && objects.countType(0) == 2
                && objects.countType(1) == 2
                && objects.getCollisionTileCount() == 7,
            "harvesting fixture counts");
        Object liveStump = new Object();
        check(objects.replace(
                objectGeneration, "deep-tree", liveTree,
                treeLocation, 0, 0, liveStump, stump,
                java.util.Collections.singleton(treeLocation))
                == liveStump,
            "tree becomes stump");
        check(objects.find("deep-tree") == liveStump
                && objects.getCollisionTileCount() == 7
                && objects.hasNpcBlockingSceneryAt(treeLocation),
            "stump retains placement and collision identity");
        Object restoredTree = new Object();
        check(objects.replace(
                objectGeneration, "deep-tree", liveStump,
                treeLocation, 0, 0, restoredTree, tree,
                java.util.Collections.singleton(treeLocation))
                == restoredTree,
            "delayed callback restores tree");
        check(objects.find("deep-tree") == restoredTree
                && objects.size() == 4
                && objects.getCollisionTileCount() == 7,
            "delayed restoration remains duplicate-free");

        expectIllegal(() -> objects.register(
            objectGeneration, "deep-table", tableLocation,
            0, 0, new Object(), table,
            java.util.Collections.singleton(tableLocation)));
        expectIllegal(() -> objects.register(
            objectGeneration, "other-table", tableLocation,
            0, 4, new Object(), table,
            java.util.Collections.singleton(tableLocation)));

        NativeLayeredGameObjectIdentity identity =
            new NativeLayeredGameObjectIdentity(
                "test.package", objectGeneration, "deep-door",
                "boundary", doorLocation);
        NativeLayeredGameObjectIdentitySlot identitySlot =
            new NativeLayeredGameObjectIdentitySlot();
        identitySlot.assign(identity);
        identitySlot.assign(identity);
        check(identitySlot.get() == identity,
            "native placement identity assignment is idempotent");
        expectIllegalState(() -> identitySlot.assign(
            new NativeLayeredGameObjectIdentity(
                "test.package", objectGeneration, "other-door",
                "boundary", doorLocation)));

        objects.reset();
        check(objects.size() == 0 && objects.getCollisionTileCount() == 0,
            "object registry reset");
        check(!objects.hasNpcBlockingSceneryAt(tableLocation),
            "object registry reset clears NPC scenery occupancy");
        check(objects.register(
                objectGeneration, "stale-table", tableLocation,
                0, 0, new Object(), table,
                java.util.Collections.singleton(tableLocation)) == null,
            "stale delayed object callback refused");
    }

    private static TileValue emptyTile() {
        TileValue tile = new TileValue();
        tile.initializeTerrainCollision();
        return tile;
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected registration refusal");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected identity refusal");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredNativePlacementRuntimeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run(
            ["./scripts/build-server.sh"],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
        )
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="native-layered-placement-runtime-"
        )
        cls.classes = Path(cls.compile_temp.name)
        source = cls.classes / "NativeLayeredPlacementRegistryFixture.java"
        source.write_text(HARNESS, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                str(CORE_JAR),
                "-d",
                str(cls.classes),
                str(source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_layer_qualified_spawn_registry_is_generation_safe(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredPlacementRegistryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_world_load_owns_population_and_layered_item_respawn(self):
        world = WORLD.read_text(encoding="utf-8")
        item = GROUND_ITEM.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        loc = GAME_OBJECT_LOC.read_text(encoding="utf-8")
        functions = FUNCTIONS.read_text(encoding="utf-8")
        self.assertIn(
            "getRegionManager().populateNativeLayeredPlacements()", world
        )
        self.assertIn(
            '"Native layered world load failed closed"', world
        )
        self.assertIn("registerNativeLayeredGroundItem", world)
        self.assertIn("removeNativeLayeredGroundItem", world)
        self.assertIn("findNativeLayeredGameObject(o)", world)
        self.assertIn(
            "registerGameObject(new GameObject(getWorld(), loc)",
            world,
        )
        self.assertIn(
            '"Respawn Native Layered Ground Item"', item
        )
        self.assertIn(
            "AuthoredLayeredGroundItemRegistry.NO_GENERATION", item
        )
        self.assertIn("populateNativeLayeredPlacements()", manager)
        self.assertIn("new Npc(", manager)
        self.assertIn("placement.getStart()", manager)
        self.assertIn("NativeLayeredSceneryPlacement", manager)
        self.assertIn("NativeLayeredBoundaryPlacement", manager)
        self.assertIn("populateNativeLayeredGameObject(", manager)
        self.assertIn(
            "nativeLayeredGameObjects.applyCollision(location, tile)",
            manager,
        )
        self.assertIn(
            "nativeLayeredGameObjects.hasNpcBlockingSceneryAt(",
            manager,
        )
        self.assertIn(
            "if (usesNativeLayeredRegionlessMembership(location))",
            manager,
        )
        self.assertIn("applyNativeLayeredGameObjectTransaction(", manager)
        self.assertIn("nativeLayeredGameObjects.replace(", manager)
        self.assertIn("nativeLayeredGameObjects.unregister(", manager)
        self.assertIn("NativeLayeredGameObjectIdentity", loc)
        self.assertIn(
            "o.getWorld().replaceGameObject(o, newObject);", functions
        )
        self.assertIn(
            "obj.getWorld().replaceGameObject(obj, replaceObj);", functions
        )
        self.assertNotIn(
            "applyObjectMembershipAndCollisionTransaction(\n"
            "\t\t\tnull",
            manager,
        )

    def test_native_command_requires_world_population_instead_of_spawning(self):
        development = DEVELOPMENT.read_text(encoding="utf-8")
        native_gate = development.index(
            "WANT_LAYERED_NATIVE_TERRAIN_PACKAGE",
            development.index("ensureSyntheticDeepFixtureEntities"),
        )
        native_return = development.index("return;", native_gate)
        legacy_spawn = development.index("new Npc(", native_return)
        self.assertLess(native_gate, native_return)
        self.assertLess(native_return, legacy_spawn)
        self.assertIn(
            "areNativeLayeredPlacementsPopulated()", development[
                native_gate:legacy_spawn
            ]
        )
        self.assertNotIn(
            "new GroundItem(", development[native_gate:native_return]
        )


if __name__ == "__main__":
    unittest.main()
