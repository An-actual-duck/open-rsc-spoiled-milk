package orsc.browser;

import com.openrsc.client.entityhandling.EntityHandler;
import orsc.BrowserWebGLBridge;
import orsc.Config;
import orsc.WorldBuilderTerrainBootstrap;
import orsc.graphics.three.World;
import orsc.graphics.three.SurfaceSceneryMeshPreparation;

import java.io.IOException;

/** A browser worker loads only the read-only map definitions and meshing code. */
public final class BrowserSurfaceMeshWorker {
    private static World world;
    private static boolean custom;
    private static final SurfaceSceneryMeshPreparation.PlacementMesher scenery = new SurfaceSceneryMeshPreparation.PlacementMesher();

    public static byte[] buildScenery(String cacheDir, byte[] source) throws IOException {
        return scenery.build(source, cacheDir + "/video/models.orsc");
    }

    public static int[] buildSceneryWords(String cacheDir, byte[] source) {
        return scenery.buildWords(source, cacheDir + "/video/models.orsc");
    }

    public static byte[] build(String cacheDir, byte[] source, int offsetX, int offsetZ,
                               int dx, int dz, boolean withRoofs, boolean customSprites, boolean gameplay) throws IOException {
        World terrain = terrain(cacheDir, customSprites);
        if (gameplay) return terrain.buildSurfaceGameplayPacket(source, offsetX, offsetZ, withRoofs);
        return terrain.buildSurfaceMeshPacket(source, offsetX, offsetZ, dx, dz, withRoofs, BrowserWebGLBridge::prepareWorkerSurfaceLighting);
    }

    public static int[] buildDrawWords(String cacheDir, byte[] source, int offsetX, int offsetZ,
                                       int dx, int dz, boolean withRoofs, boolean customSprites) throws IOException {
        return terrain(cacheDir, customSprites).buildSurfaceDrawWordPacket(source, offsetX, offsetZ, dx, dz, withRoofs,
            BrowserWebGLBridge::prepareWorkerSurfaceLighting);
    }

    public static int[] buildCompatibilityWords(String cacheDir, byte[] source, int offsetX, int offsetZ,
                                                int dx, int dz, boolean withRoofs, boolean customSprites) throws IOException {
        return terrain(cacheDir, customSprites).buildSurfaceDrawWordPacket(source, offsetX, offsetZ, dx, dz, withRoofs,
            false, null);
    }

    public static int[] buildGameplayWords(String cacheDir, byte[] source, int offsetX, int offsetZ,
                                           boolean withRoofs, boolean customSprites) throws IOException {
        return terrain(cacheDir, customSprites).buildSurfaceGameplayWordPacket(source, offsetX, offsetZ, withRoofs);
    }

    private static World terrain(String cacheDir, boolean customSprites) {
        if (world == null) {
            Config.F_CACHE_DIR = cacheDir;
            Config.S_WANT_CUSTOM_SPRITES = customSprites;
            if (!WorldBuilderTerrainBootstrap.isNativeOnly()) throw new IllegalStateException("Missing signed map profile");
            EntityHandler.load(true);
            world = new World(null, null);
            custom = customSprites;
        }
        if (custom != customSprites) throw new IllegalStateException("Surface worker definitions changed");
        return world;
    }
}
