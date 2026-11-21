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

    public static byte[] buildScenery(byte[] source) throws IOException {
        return SurfaceSceneryMeshPreparation.buildPacket(source);
    }

    public static byte[] build(String cacheDir, byte[] source, int offsetX, int offsetZ,
                               int dx, int dz, boolean withRoofs, boolean customSprites, boolean gameplay) throws IOException {
        if (world == null) {
            Config.F_CACHE_DIR = cacheDir;
            Config.S_WANT_CUSTOM_SPRITES = customSprites;
            if (!WorldBuilderTerrainBootstrap.isNativeOnly()) throw new IllegalStateException("Missing signed map profile");
            EntityHandler.load(true);
            world = new World(null, null);
            custom = customSprites;
        }
        if (custom != customSprites) throw new IllegalStateException("Surface worker definitions changed");
        if (gameplay) return world.buildSurfaceGameplayPacket(source, offsetX, offsetZ, withRoofs);
        return world.buildSurfaceMeshPacket(source, offsetX, offsetZ, dx, dz, withRoofs, BrowserWebGLBridge::prepareWorkerSurfaceLighting);
    }
}
