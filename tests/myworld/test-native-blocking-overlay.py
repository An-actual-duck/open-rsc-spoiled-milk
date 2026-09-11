#!/usr/bin/env python3
"""Isolated source-compiled regression for reserved native overlay 255."""

import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
REGION = SERVER / "model/world/region"
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


HARNESS = r"""
import com.openrsc.server.external.TileDef;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.model.world.region.NativeLayeredTerrainCollisionPlan;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;
import orsc.WorldBuilderTerrainOverlay;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class NativeBlockingOverlayHarness {
    private TileDef[] tiles;
    private final List<Integer> lookups = new ArrayList<>();
    // Keep the production callback and EntityHandler bounds behavior verbatim;
    // only the server/world wiring is replaced, without booting a server.
    private NativeBlockingOverlayHarness getWorld() { return this; }
    private NativeBlockingOverlayHarness getServer() { return this; }
    private NativeBlockingOverlayHarness getEntityHandler() { return this; }
    @CALLBACK@
    @LOOKUP@

    private NativeLayeredTerrainCollisionPlan.Result derive(int overlay) {
        lookups.clear();
        return NativeLayeredTerrainCollisionPlan.derive(
            new NativeLayeredTerrainTile(37, 93, overlay, 0, 0, 0, 0),
            null, null, id -> {
                lookups.add(id - 1);
                return nativeTerrainOverlayBlocks(id);
            }, id -> false, id -> false, id -> false, id -> false);
    }

    private void verify(int overlay, boolean blocked, int lookup) {
        NativeLayeredTerrainCollisionPlan.Result result = derive(overlay);
        check(result.isTerrainBlocked() == blocked, "movement overlay " + overlay);
        check(lookup < 0 ? lookups.isEmpty()
            : lookups.size() == 1 && lookups.get(0) == lookup,
            "definition lookup overlay " + overlay + ": " + lookups);
        boolean projectile = overlay == 2 || overlay == 11;
        check(result.isOverlayProjectileBlocked() == projectile,
            "raw overlay projectile semantics " + overlay);
        TileValue tile = new TileValue();
        tile.overlay = (byte) overlay;
        tile.initializeTerrainCollision();
        result.applyTo(tile);
        check(tile.traversalMask == (blocked ? CollisionFlag.FULL_BLOCK_C : 0),
            "runtime movement mask overlay " + overlay);
        check(tile.isTerrainBlocked() == blocked, "terrain ownership " + overlay);
        check(tile.projectileAllowed == projectile
            && tile.originalProjectileAllowed == projectile,
            "legacy projectile flags " + overlay);
        check(tile.getTerrainWallProjectileCount() == 0,
            "overlay must not add projectile walls");
        int combat = overlay == 10 ? CollisionFlag.FULL_BLOCK_C : 0;
        check(tile.getCombatProjectileCollisionMask() == combat
            && tile.getEnemyProjectileCollisionMask() == combat,
            "combat cover remains separate from movement " + overlay);
        tile.addBlockingScenery();
        tile.removeBlockingScenery();
        check(tile.traversalMask == (blocked ? CollisionFlag.FULL_BLOCK_C : 0),
            "removing scenery must not make blocking terrain walkable");
        check(tile.copy().equals(tile), "collision ownership survives copy");
    }

    public static void main(String[] args) throws Exception {
        NativeBlockingOverlayHarness harness = new NativeBlockingOverlayHarness();
        NodeList definitions = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(new File(args[0])).getElementsByTagName("TileDef");
        harness.tiles = new TileDef[definitions.getLength()];
        for (int i = 0; i < harness.tiles.length; i++) {
            TileDef definition = new TileDef();
            definition.objectType = Integer.parseInt(((Element) definitions.item(i))
                .getElementsByTagName("objectType").item(0).getTextContent());
            harness.tiles[i] = definition;
        }
        check(harness.getTileDef(254) == null, "255 is outside checked-in inventory");
        harness.verify(255, true, -1);
        // A permissive predicate cannot accidentally turn this sentinel walkable.
        check(NativeLayeredTerrainCollisionPlan.derive(
            new NativeLayeredTerrainTile(0, 0, 255, 0, 0, 0, 0), null, null,
            id -> false, id -> false, id -> false, id -> false, id -> false)
            .isTerrainBlocked(), "255 must block independently of definitions");
        harness.verify(0, false, -1);
        for (int overlay = 1; overlay <= harness.tiles.length; overlay++) {
            harness.verify(overlay, harness.tiles[overlay - 1].getObjectType() != 0,
                overlay - 1);
        }
        harness.verify(250, harness.tiles[1].getObjectType() != 0, 1);
        try {
            harness.derive(254);
            throw new AssertionError("unsupported ordinary overlay silently accepted");
        } catch (NullPointerException expected) {
            check(harness.lookups.size() == 1 && harness.lookups.get(0) == 253,
                "unsupported ordinary overlay still reaches definition validation");
        }
        check(WorldBuilderTerrainOverlay.BLOCKING_BASE_COLOR == 255,
            "client/server reserved value parity");
        check(WorldBuilderTerrainOverlay.usesBaseColor(255)
            && WorldBuilderTerrainOverlay.usesBaseColor(0)
            && WorldBuilderTerrainOverlay.isBlockingBaseColor(255)
            && !WorldBuilderTerrainOverlay.isBlockingBaseColor(0),
            "client base color and movement semantics");
        for (int overlay = 1; overlay <= 254; overlay++) {
            check(!WorldBuilderTerrainOverlay.usesBaseColor(overlay)
                && !WorldBuilderTerrainOverlay.isBlockingBaseColor(overlay),
                "ordinary overlay must retain its rendering semantics");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
"""


class NativeBlockingOverlayTest(unittest.TestCase):
    def test_native_collision_with_production_definition_callback(self):
        callback = method((REGION / "RegionManager.java").read_text(),
                          "private boolean nativeTerrainOverlayBlocks(")
        lookup = method((SERVER / "external/EntityHandler.java").read_text(),
                        "public TileDef getTileDef(")
        harness = HARNESS.replace("@CALLBACK@", callback).replace("@LOOKUP@", lookup)
        with tempfile.TemporaryDirectory(prefix="native-blocking-overlay-") as temp:
            fixture = Path(temp) / "NativeBlockingOverlayHarness.java"
            fixture.write_text(harness)
            sources = [
                SERVER / "external/TileDef.java",
                SERVER / "io/NativeLayeredTerrainTile.java",
                SERVER / "io/Tile.java",
                SERVER / "util/rsc/CollisionFlag.java",
                REGION / "NativeLayeredTerrainCollisionPlan.java",
                REGION / "TileValue.java",
                ROOT / "Client_Base/src/orsc/WorldBuilderTerrainOverlay.java",
                fixture,
            ]
            subprocess.run(["javac", "-source", "8", "-target", "8", "-d", temp,
                            *map(str, sources)], check=True, cwd=ROOT)
            subprocess.run(["java", "-cp", temp, "NativeBlockingOverlayHarness",
                            str(ROOT / "server/conf/server/defs/TileDef.xml")],
                           check=True, cwd=ROOT)

    def test_client_rendering_and_collision_wiring(self):
        source = WORLD.read_text()
        # Both the staged terrain mesh and legacy scene retain the base resource
        # in the sentinel branch, before ordinary TileDef color lookup.
        self.assertIn("if (WorldBuilderTerrainOverlay.isBlockingBaseColor(decorID)) {\n"
                      "\t\t\t\t\tcollisionFullBlock = true;\n"
                      "\t\t\t\t} else if (decorID > 0)", source)
        self.assertIn("if (WorldBuilderTerrainOverlay.isBlockingBaseColor(decorID)) {\n"
                      "\t\t\t\t\tthis.collisionFlags[x][z] = FastMath.bitwiseOr(\n"
                      "\t\t\t\t\t\tthis.collisionFlags[x][z], CollisionFlag.FULL_BLOCK_C);\n"
                      "\t\t\t\t} else if (decorID > 0)", source)
        self.assertIn("return this.colorToResource[this.getTerrainColour(xTile, zTile)];",
                      method(source, "private int getTileDecorationCacheVal("))
        self.assertIn("return colorToResource[terrainColour(tileX, tileZ)];",
                      method(source, "private int tileDecorationCacheVal("))
        region = (REGION / "RegionManager.java").read_text()
        self.assertIn("this::nativeTerrainOverlayBlocks,", region)
        self.assertIn(".applyTo(tile);", region)


if __name__ == "__main__":
    unittest.main()
