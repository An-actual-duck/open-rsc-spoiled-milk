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


HARNESS = r"""
import com.openrsc.server.model.world.AuthoredLayeredGroundItemRegistry;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

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
