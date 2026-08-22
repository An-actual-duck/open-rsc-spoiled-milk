#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
REGION_MANAGER = REGION / "RegionManager.java"
VISIBILITY = REGION / "VisibilitySnapshot.java"
GAME_OBJECT_DEFINITIONS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
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
    public boolean isInvisibleTo(Entity observer) {
        return false;
    }
}
"""

GAME_OBJECT_STUB = r"""
package com.openrsc.server.model.entity;

public final class GameObject extends Entity {
}
"""

NPC_STUB = r"""
package com.openrsc.server.model.entity.npc;

import com.openrsc.server.model.entity.Entity;

public final class Npc extends Entity {
}
"""

GROUND_ITEM_STUB = r"""
package com.openrsc.server.model.entity;

public final class GroundItem extends Entity {
}
"""

PLAYER_STUB = r"""
package com.openrsc.server.model.entity.player;

import com.openrsc.server.model.entity.Entity;

public final class Player extends Entity {
    private final boolean inRange;

    public Player(boolean inRange) {
        this.inRange = inRange;
    }

    public boolean withinRange(Entity observer) {
        return inRange;
    }
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
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredSpatialWindowKey;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

import java.util.HashSet;
import java.util.Set;

public final class LayeredSpatialRuntimeAuthorityFixture {
    public static void main(String[] args) {
		assertLocalRegionHashesAreDistributed();
        LayeredSpatialEntityIndex index = new LayeredSpatialEntityIndex();
        Entity surface = new Entity();
        Entity upper = new Entity();
        Entity underground = new Entity();
        Entity deep = new Entity();
        WorldLocation surfaceLocation = location(100, 400, 0);
        WorldLocation upperLocation = location(100, 400, 1);
        WorldLocation undergroundLocation = location(100, 400, -1);
        WorldLocation deepLocation = location(100, 400, -2);

        index.synchronize(surface, null, surfaceLocation);
        long objectVersionBefore = snapshot(
            index, surfaceLocation).getObjectVersion();
        index.synchronize(upper, null, upperLocation);
        index.synchronize(underground, null, undergroundLocation);
        index.synchronize(deep, null, deepLocation);
        check(snapshot(index, surfaceLocation).getObjectVersion()
            == objectVersionBefore, "mob movement leaves object version stable");
        check(index.getMembershipCount() == 4, "membership count");
        check(snapshot(index, surfaceLocation).getEntities().size() == 1,
            "surface isolation");
        check(snapshot(index, upperLocation).getEntities().size() == 1,
            "upper isolation");
        check(snapshot(index, undergroundLocation).getEntities().size() == 1,
            "underground isolation");
        check(snapshot(index, deepLocation).getEntities().size() == 1,
            "deep isolation at identical x/y");
        check(snapshot(index, deepLocation).getEntities().get(0) == deep,
            "deep identity at identical x/y");
        check(snapshot(index, surfaceLocation).getEntities().get(0) == surface,
            "surface identity");

        Player nearbyPlayer = new Player(true);
        Player outOfRangePlayer = new Player(false);
        index.synchronize(nearbyPlayer, null, surfaceLocation);
        index.synchronize(outOfRangePlayer, null, upperLocation);
        check(index.hasPlayerWithinRange(
                WorldRegionWindow.around(surfaceLocation, 15), surface),
            "player-only surface range hit");
        check(index.snapshotPlayersWithinRange(
				WorldRegionWindow.around(surfaceLocation, 15), surface)
				.size() == 1,
			"player-only snapshot filters by range and level");
		check(index.snapshotPlayersWithinRange(
				WorldRegionWindow.around(surfaceLocation, 15), surface)
				.get(0) == nearbyPlayer,
			"player-only snapshot preserves player identity");
        check(!index.hasPlayerWithinRange(
                WorldRegionWindow.around(upperLocation, 15), upper),
            "player-only range predicate");
		check(index.snapshotPlayersWithinRange(
				WorldRegionWindow.around(upperLocation, 15), upper)
				.isEmpty(),
			"player-only snapshot applies range predicate");
        check(!index.hasPlayerWithinRange(
                WorldRegionWindow.around(deepLocation, 15), deep),
            "player-only level isolation");
		check(index.snapshotPlayersWithinRange(
				WorldRegionWindow.around(deepLocation, 15), deep)
				.isEmpty(),
			"player-only snapshot preserves level isolation");

        Npc surfaceNpc = new Npc();
        Npc deepNpc = new Npc();
        GroundItem surfaceItem = new GroundItem();
        GroundItem deepItem = new GroundItem();
        index.synchronize(surfaceNpc, null, surfaceLocation);
        index.synchronize(deepNpc, null, deepLocation);
        index.synchronize(surfaceItem, null, surfaceLocation);
        index.synchronize(deepItem, null, deepLocation);
        check(index.snapshotNpcs(
                WorldRegionWindow.around(surfaceLocation, 15)).size() == 1
                && index.snapshotNpcs(
                    WorldRegionWindow.around(surfaceLocation, 15)).get(0)
                    == surfaceNpc,
            "NPC-only snapshot preserves identity and level");
        check(index.snapshotGroundItems(
                WorldRegionWindow.around(deepLocation, 15)).size() == 1
                && index.snapshotGroundItems(
                    WorldRegionWindow.around(deepLocation, 15)).get(0)
                    == deepItem,
            "ground-item-only snapshot preserves identity and level");

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
        LayeredSpatialWindowKey exactKey =
            LayeredSpatialWindowKey.exact(
                location(100, 400, -2), 48, 576, 192, 720);
        check(exactKey.hasExactTileBounds(), "exact key marker");
        check(exactKey.getMinTileX() == 48
                && exactKey.getMinTileY() == 576
                && exactKey.getMaxTileXExclusive() == 192
                && exactKey.getMaxTileYExclusive() == 720,
            "exact half-open tile bounds");
        check(exactKey.getRegionWindow().getMinRegionX() == 1
                && exactKey.getRegionWindow().getMaxRegionX() == 3
                && exactKey.getRegionWindow().getMinRegionY() == 12
                && exactKey.getRegionWindow().getMaxRegionY() == 14,
            "exact three-by-three region window");
        check(!exactKey.equals(
                LayeredSpatialWindowKey.exact(
                    location(100, 400, -2), 49, 576, 193, 720)),
            "exact tile bounds qualify cache identity");
        check(!surfaceKey.hasExactTileBounds(),
            "radius key retains legacy identity mode");
        check(surfaceKey.getMinTileX() == 68
                && surfaceKey.getMaxTileXExclusive() == 133,
            "radius key retains inclusive radius semantics");

        GameObject object = new GameObject();
        index.synchronize(object, null, boundaryTarget);
        check(index.snapshotGameObjects(
                WorldRegionWindow.around(boundaryTarget, 15))
                .getGameObjects().size() == 1,
            "game-object-only projection includes object");
        check(index.snapshotGameObjects(
                WorldRegionWindow.around(boundaryTarget, 15))
                .getGameObjects().get(0) == object,
            "game-object-only projection preserves identity");
        check(index.snapshotGameObjects(
                WorldRegionWindow.around(deepLocation, 15))
                .getGameObjects().isEmpty(),
            "game-object-only projection preserves level isolation");
        check(index.hasGameObjectAt(
                WorldRegionWindow.around(boundaryTarget, 15),
                48,
                942,
                (candidate, tileX, tileY) ->
                    candidate == object && tileX == 48 && tileY == 942),
            "allocation-free game-object query finds matching object");
        check(!index.hasGameObjectAt(
                WorldRegionWindow.around(deepLocation, 15),
                48,
                942,
                (candidate, tileX, tileY) -> true),
            "allocation-free game-object query preserves level isolation");
        check(index.hasGameObjectAt(
                WorldRegionWindow.around(boundaryTarget, 128),
                48,
                942,
                48,
                48,
                (candidate, tileX, tileY) -> candidate == object),
            "footprint-bounded query retains target-region object");
        check(!index.hasGameObjectAt(
                WorldRegionWindow.around(boundaryTarget, 128),
                144,
                942,
                48,
                48,
                (candidate, tileX, tileY) -> candidate == object),
            "footprint-bounded query excludes impossible origin region");
        long objectVersionAfter = snapshot(
            index, boundaryTarget).getObjectVersion();
        check(objectVersionAfter > objectVersionBefore,
            "object registration advances object version");
        index.remove(object, boundaryTarget);
        check(index.snapshotGameObjects(
                WorldRegionWindow.around(boundaryTarget, 15))
                .getGameObjects().isEmpty(),
            "game-object-only projection removes object");

        index.remove(nearbyPlayer, surfaceLocation);
        index.remove(outOfRangePlayer, upperLocation);
        index.remove(surface, boundaryTarget);
        check(index.getMembershipCount() == 7, "removal count");
        expectState(() -> index.remove(surface, boundaryTarget));
        index.clear();
        check(index.getMembershipCount() == 0, "clear");
        check(!index.hasPlayerWithinRange(
                WorldRegionWindow.around(surfaceLocation, 15), surface),
            "player-only clear");
    }

    private static LayeredSpatialEntityIndex.Snapshot snapshot(
        LayeredSpatialEntityIndex index,
        WorldLocation center) {
        return index.snapshot(WorldRegionWindow.around(center, 15));
    }

	private static void assertLocalRegionHashesAreDistributed() {
		Set<Integer> hashes = new HashSet<Integer>();
		for (int regionX = -32; regionX <= 32; regionX++) {
			for (int regionY = -32; regionY <= 32; regionY++) {
				hashes.add(new WorldRegionKey(
					com.openrsc.server.model.world.coordinate.WorldSpaceId.GLOBAL,
					-2, regionX, regionY).hashCode());
			}
		}
		check(hashes.size() >= 4200,
			"local region hashes avoid systematic grid collisions");
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
                "src/com/openrsc/server/model/entity/GroundItem.java"
            ): GROUND_ITEM_STUB,
            (
                "src/com/openrsc/server/model/entity/npc/Npc.java"
            ): NPC_STUB,
            (
                "src/com/openrsc/server/model/entity/player/Player.java"
            ): PLAYER_STUB,
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
            cls.temp / "src/com/openrsc/server/model/entity/GroundItem.java",
            cls.temp / "src/com/openrsc/server/model/entity/npc/Npc.java",
            cls.temp
            / "src/com/openrsc/server/model/entity/player/Player.java",
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
        self.assertIn("hasPlayerWithinRange", region_manager)
        self.assertIn("snapshotPlayersWithinRange", region_manager)
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

    def test_shipped_scenery_footprints_fit_bounded_collision_query(self):
        definitions = ET.parse(GAME_OBJECT_DEFINITIONS).getroot()
        footprints = [
            (int(definition.findtext("width")), int(definition.findtext("height")))
            for definition in definitions.findall("GameObjectDef")
        ]
        self.assertTrue(footprints)
        self.assertLessEqual(max(width for width, _ in footprints), 48)
        self.assertLessEqual(max(height for _, height in footprints), 48)

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
