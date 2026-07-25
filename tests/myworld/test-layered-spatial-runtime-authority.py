#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
REGION_MANAGER = REGION / "RegionManager.java"
VISIBILITY = REGION / "VisibilitySnapshot.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
CONFIGURATION = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
DEVELOPMENT = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
FUNCTIONS = ROOT / "server/src/com/openrsc/server/plugins/Functions.java"
PLAN = (
    ROOT
    / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"
)


ENTITY_STUB = r"""
package com.openrsc.server.model.entity;

public class Entity {
}
"""

GAME_OBJECT_STUB = r"""
package com.openrsc.server.model.entity;

public final class GameObject extends Entity {
}
"""

POINT_STUB = r"""
package com.openrsc.server.model;

public final class Point {
    private final int x;
    private final int y;

    private Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        return new Point(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
"""


REGION_MANAGER_STUB = r"""
package com.openrsc.server.model.world.region;

public final class RegionManager {
    public static final int MAX_LAYERED_REGIONS_PER_INTEREST_OWNER = 4096;
}
"""


REGION_STUB = r"""
package com.openrsc.server.model.world.region;

public final class Region {
}
"""


FIXTURE = r"""
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.world.coordinate.LayeredSpatialWindowKey;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

public final class LayeredSpatialRuntimeAuthorityFixture {
    public static void main(String[] args) {
        LayeredSpatialEntityIndex index = new LayeredSpatialEntityIndex();
        Entity surface = new Entity();
        Entity upper = new Entity();
        Entity underground = new Entity();
        WorldLocation surfaceLocation = location(100, 400, 0);
        WorldLocation upperLocation = location(100, 400, 1);
        WorldLocation undergroundLocation = location(100, 400, -1);

        index.synchronize(surface, null, surfaceLocation);
        long objectVersionBefore = snapshot(
            index, surfaceLocation).getObjectVersion();
        index.synchronize(upper, null, upperLocation);
        index.synchronize(underground, null, undergroundLocation);
        check(snapshot(index, surfaceLocation).getObjectVersion()
            == objectVersionBefore, "mob movement leaves object version stable");
        check(index.getMembershipCount() == 3, "membership count");
        check(snapshot(index, surfaceLocation).getEntities().size() == 1,
            "surface isolation");
        check(snapshot(index, upperLocation).getEntities().size() == 1,
            "upper isolation");
        check(snapshot(index, undergroundLocation).getEntities().size() == 1,
            "underground isolation");
        check(snapshot(index, surfaceLocation).getEntities().get(0) == surface,
            "surface identity");

        WorldLocation boundaryOrigin = location(47, 943, 0);
        WorldLocation boundaryTarget = location(48, 942, 0);
        index.synchronize(surface, surfaceLocation, boundaryOrigin);
        index.synchronize(surface, boundaryOrigin, boundaryTarget);
        index.requireMembership(surface, boundaryTarget);
        expectState(() -> index.requireMembership(surface, boundaryOrigin));
        expectState(() -> index.synchronize(
            surface, boundaryOrigin, location(49, 942, 0)));

        LayeredSpatialWindowKey surfaceKey =
            LayeredSpatialWindowKey.around(location(100, 400, 0), 32);
        LayeredSpatialWindowKey upperKey =
            LayeredSpatialWindowKey.around(location(100, 400, 1), 32);
        check(!surfaceKey.equals(upperKey), "level-qualified cache identity");
        check(surfaceKey.getRegionWindow().getLevel() == 0,
            "surface cache level");
        check(upperKey.getRegionWindow().getLevel() == 1,
            "upper cache level");

        GameObject object = new GameObject();
        index.synchronize(object, null, boundaryTarget);
        long objectVersionAfter = snapshot(
            index, boundaryTarget).getObjectVersion();
        check(objectVersionAfter > objectVersionBefore,
            "object registration advances object version");
        index.remove(object, boundaryTarget);

        index.remove(surface, boundaryTarget);
        check(index.getMembershipCount() == 2, "removal count");
        expectState(() -> index.remove(surface, boundaryTarget));
        index.clear();
        check(index.getMembershipCount() == 0, "clear");
    }

    private static LayeredSpatialEntityIndex.Snapshot snapshot(
        LayeredSpatialEntityIndex index,
        WorldLocation center) {
        return index.snapshot(WorldRegionWindow.around(center, 15));
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
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


class LayeredSpatialRuntimeAuthorityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-spatial-runtime-authority-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        sources = {
            "src/com/openrsc/server/model/Point.java": POINT_STUB,
            "src/com/openrsc/server/model/entity/Entity.java": ENTITY_STUB,
            (
                "src/com/openrsc/server/model/entity/GameObject.java"
            ): GAME_OBJECT_STUB,
            (
                "src/com/openrsc/server/model/world/region/RegionManager.java"
            ): REGION_MANAGER_STUB,
            "src/com/openrsc/server/model/world/region/Region.java": REGION_STUB,
            (
                "src/com/openrsc/server/model/world/region/"
                "LayeredSpatialRuntimeAuthorityFixture.java"
            ): FIXTURE,
        }
        paths = []
        for relative, contents in sources.items():
            path = cls.temp / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(contents, encoding="utf-8")
            paths.append(path)

        compile_sources = [
            cls.temp / "src/com/openrsc/server/model/Point.java",
            cls.temp / "src/com/openrsc/server/model/entity/Entity.java",
            cls.temp / "src/com/openrsc/server/model/entity/GameObject.java",
            cls.temp
            / "src/com/openrsc/server/model/world/region/RegionManager.java",
            cls.temp / "src/com/openrsc/server/model/world/region/Region.java",
            COORDINATES / "WorldCoordinate.java",
            COORDINATES / "WorldSpaceId.java",
            COORDINATES / "WorldLocation.java",
            COORDINATES / "LegacyPackedPointAdapter.java",
            COORDINATES / "WorldRegionKey.java",
            COORDINATES / "WorldRegionWindow.java",
            COORDINATES / "LayeredSpatialWindowKey.java",
            REGION / "LayeredSpatialEntityIndex.java",
            paths[-1],
        ]
        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                *(str(path) for path in compile_sources),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_logical_membership_level_isolation_and_exact_keys(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                (
                    "com.openrsc.server.model.world.region."
                    "LayeredSpatialRuntimeAuthorityFixture"
                ),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_gate_and_authority_consumers_are_explicit(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        entity = ENTITY.read_text(encoding="utf-8")
        mob = MOB.read_text(encoding="utf-8")
        npc = NPC.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        visibility = VISIBILITY.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        world = WORLD.read_text(encoding="utf-8")
        functions = FUNCTIONS.read_text(encoding="utf-8")

        self.assertIn("WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY", configuration)
        self.assertIn(
            "OPENRSC_LAYERED_SPATIAL_RUNTIME_AUTHORITY", configuration
        )
        self.assertIn('"want_layered_spatial_runtime_authority"', configuration)
        self.assertIn(
            "private final AtomicReference<WorldLocation> worldLocation",
            entity,
        )
        self.assertIn("sharesSpatialDomain", entity)
        self.assertIn("sharesSpatialDomain(e)", mob)
        self.assertIn("n.updateRegion();", npc)
        self.assertNotIn("n.getRegion().addEntity(n);", npc)
        self.assertIn(
            "private final LayeredSpatialEntityIndex layeredSpatialEntityIndex",
            region_manager,
        )
        self.assertIn("buildLayeredVisibilitySnapshot", region_manager)
        self.assertIn("requireLegacyTerrainProjection", region_manager)
        self.assertIn("LayeredSpatialWindowKey", visibility)
        self.assertIn("WorldLocation src", path_validation)
        self.assertIn("sameSpatialDomain(owner, destination)", path_validation)
        self.assertIn("spatialAuthority=", development)
        self.assertIn(
            "if (!i.isRemoved()) {\n\t\t\t\t\t\t\tunregisterItem(i);",
            world,
        )
        self.assertIn(
            "if (!i.isRemoved()) {\n"
            "\t\t\t\t\t\ti.getWorld().unregisterItem(i);",
            functions,
        )

    def test_plan_records_boundary_and_refusal_rules(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Phase 5 Authority Milestone B", plan)
        self.assertIn("944-tile legacy level stride is not divisible by 48", plan)
        self.assertIn("LayeredSpatialEntityIndex", (
            REGION / "LayeredSpatialEntityIndex.java"
        ).read_text(encoding="utf-8"))
        self.assertIn("level `-2` remains gated", plan)


if __name__ == "__main__":
    unittest.main()
