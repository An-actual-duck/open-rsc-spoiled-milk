package orsc;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.WholeRamBasis;

/** Private experiment. Preparing a mask never activates a region or grants scene authority. */
final class BrowserTerrainLookahead {
    private static final int MAX_MASKS = 6;
    private final Map<String, Mask> masks = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "browser-terrain-lookahead");
        thread.setDaemon(true);
        return thread;
    });
    private String center;
    private int stableFrames, generation;
    private boolean inFlight, finishedCenter, disabled;
    private int preparedCount, useCount;

    static boolean enabled() {
        return Boolean.getBoolean("spoiledmilk.browserTerrainLookahead")
            && Boolean.getBoolean("spoiledmilk.browserBenchmark")
            && !Boolean.getBoolean("spoiledmilk.browserPublicAlpha")
            && BrowserWholeOverworldRam.isEnabled();
    }

    synchronized void offer(BrowserWholeOverworldRam ram, int[] scope, WholeRamBasis basis,
                            int absoluteX, int absoluteZ) {
        if (disabled) return;
        if (scope[1] != 1 || scope[2] != 0 || scope[17] != 0) {
            clear();
            return;
        }
        int x = scope[5], z = scope[6];
        String current = key(x, z, basis);
        if (!current.equals(center)) {
            center = current;
            stableFrames = 0;
            finishedCenter = false;
        }
        if (stableFrames < 8) {
            stableFrames++;
            return;
        }
        if (inFlight || finishedCenter) return;

        int px = Math.floorMod(absoluteX - basis.offsetTilesX, 48);
        int pz = Math.floorMod(absoluteZ - basis.offsetTilesZ, 48);
        int[][] candidates = {{x - 1, z, px}, {x + 1, z, 47 - px},
                              {x, z - 1, pz}, {x, z + 1, 47 - pz}};
        Arrays.sort(candidates, (a, b) -> Integer.compare(a[2], b[2]));
        for (int[] next : candidates) {
            String target = key(next[0], next[1], basis);
            if (masks.containsKey(target) || !ram.hasLookaheadRecipe(next[0], next[1])) continue;
            int owner = generation;
            inFlight = true;
            worker.execute(() -> prepare(ram, basis, next[0], next[1], target, owner));
            return;
        }
        finishedCenter = true;
    }

    private void prepare(BrowserWholeOverworldRam ram, WholeRamBasis basis, int x, int z,
                         String target, int owner) {
        try {
            Renderer3DWorldChunkFrame world = ram.queryFrame(null, x, z, basis.offsetTilesX, basis.offsetTilesZ);
            TerrainDefinitionField field = new TerrainDefinitionField();
            Mask mask = field.prepareLookahead(world);
            synchronized (this) {
                if (owner == generation) {
                    if (masks.size() == MAX_MASKS) masks.remove(masks.keySet().iterator().next());
                    masks.put(target, mask);
                    if (mask != null)
                        System.out.println("TERRAIN_LOOKAHEAD prepared=" + ++preparedCount + " center=" + x + ":" + z);
                }
            }
        } catch (RuntimeException failure) {
            // A speculative job must never keep ordinary activation from building its mask.
            System.out.println("TERRAIN_LOOKAHEAD optional preparation stopped: " + failure.getClass().getSimpleName());
            synchronized (this) { if (owner == generation) disabled = true; }
        } finally {
            synchronized (this) { inFlight = false; }
        }
    }

    synchronized byte[] find(int plane, int level, int x, int z, int w, int h, int[] heights) {
        if (plane != 0 || level != 0) return null;
        for (Mask mask : masks.values()) {
            if (mask != null && mask.x == x && mask.z == z && mask.w == w && mask.h == h
                    && Arrays.equals(mask.heights, heights)) {
                System.out.println("TERRAIN_LOOKAHEAD used=" + ++useCount);
                return mask.pixels;
            }
        }
        return null;
    }

    synchronized void clear() {
        if (center != null) ++generation;
        center = null;
        stableFrames = 0;
        finishedCenter = false;
        masks.clear();
    }

    private static String key(int x, int z, WholeRamBasis basis) {
        return x + ":" + z + ":" + basis.offsetTilesX + ":" + basis.offsetTilesZ;
    }

    static final class Mask {
        final int x, z, w, h;
        final int[] heights;
        final byte[] pixels;

        Mask(int x, int z, int w, int h, int[] heights, byte[] pixels) {
            this.x = x;
            this.z = z;
            this.w = w;
            this.h = h;
            this.heights = heights;
            this.pixels = pixels;
        }
    }
}
