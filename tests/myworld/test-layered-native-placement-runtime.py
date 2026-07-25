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
                0, 0, tableObject, table)
                == tableObject,
            "register layered scenery");
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

        WorldLocation fenceLocation = new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(448, 604, -2));
        GameTickEventRestorationCollisionFootprintPlanner.Result fence =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(4, 448, 604, 0, 1),
                Definition.boundary(1, "Fence", new String[0]),
                false,
                WorldBounds.of(1000, 1000));
        Object fenceObject = new Object();
        objects.register(
            objectGeneration, "deep-fence", fenceLocation,
            1, 0, fenceObject, fence);
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
        check(objects.size() == 2 && objects.countType(0) == 1
                && objects.countType(1) == 1,
            "typed package object counts");
        check(objects.getCollisionTileCount() == 3,
            "combined collision tile count");

        GameTickEventRestorationCollisionFootprintPlanner.Result doorframe =
            GameTickEventRestorationCollisionFootprintPlanner.plan(
                Operation.REGISTER,
                ConstructorState.of(1, 448, 604, 0, 1),
                Definition.boundary(0, "Doorframe", new String[0]),
                false,
                WorldBounds.of(1000, 1000));
        Object openDoorframe = new Object();
        check(objects.replace(
                objectGeneration, "deep-fence", fenceObject,
                fenceLocation, 1, 0, openDoorframe, doorframe)
                == openDoorframe,
            "replace package boundary");
        check(objects.find("deep-fence") == openDoorframe
                && objects.size() == 2,
            "replacement retains placement identity");
        TileValue openedNorth = emptyTile();
        objects.applyCollision(fenceLocation, openedNorth);
        check((openedNorth.traversalMask & CollisionFlag.WALL_NORTH) == 0,
            "replacement removes closed boundary collision");
        TileValue openedSouth = emptyTile();
        objects.applyCollision(
            new WorldLocation(
                WorldSpaceId.GLOBAL, new WorldCoordinate(448, 603, -2)),
            openedSouth);
        check((openedSouth.traversalMask & CollisionFlag.WALL_SOUTH) == 0,
            "replacement removes reciprocal boundary collision");
        check(objects.getCollisionTileCount() == 1,
            "replacement commits exact collision delta");

        check(objects.unregister(
                objectGeneration, "deep-fence", openDoorframe)
                == openDoorframe,
            "remove package boundary");
        check(objects.find("deep-fence") == null && objects.size() == 1,
            "removal releases placement identity");
        check(objects.register(
                objectGeneration, "deep-fence", fenceLocation,
                1, 0, fenceObject, fence) == fenceObject,
            "same-generation delayed restoration");
        check(objects.getCollisionTileCount() == 3,
            "restoration reinstates exact collision");

        expectIllegal(() -> objects.register(
            objectGeneration, "deep-table", tableLocation,
            0, 0, new Object(), table));
        expectIllegal(() -> objects.register(
            objectGeneration, "other-table", tableLocation,
            0, 4, new Object(), table));

        NativeLayeredGameObjectIdentity identity =
            new NativeLayeredGameObjectIdentity(
                "test.package", objectGeneration, "deep-fence",
                "boundary", fenceLocation);
        NativeLayeredGameObjectIdentitySlot identitySlot =
            new NativeLayeredGameObjectIdentitySlot();
        identitySlot.assign(identity);
        identitySlot.assign(identity);
        check(identitySlot.get() == identity,
            "native placement identity assignment is idempotent");
        expectIllegalState(() -> identitySlot.assign(
            new NativeLayeredGameObjectIdentity(
                "test.package", objectGeneration, "other-fence",
                "boundary", fenceLocation)));

        objects.reset();
        check(objects.size() == 0 && objects.getCollisionTileCount() == 0,
            "object registry reset");
        check(objects.register(
                objectGeneration, "stale-table", tableLocation,
                0, 0, new Object(), table) == null,
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
