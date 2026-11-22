package orsc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Detached browser raster jobs keyed by the actual glow inputs and target origin. */
final class BrowserGlowPreparation {
    private static final int MAX_PLANS = 6;
    private final List<Plan> plans = new ArrayList<>(MAX_PLANS);
    private final int[] work = new int[10];
    private int nextSectionX, nextSectionZ;
    private long nextOffer;

    private static native int prepareGlow(int[] words);
    private static native byte[] takeGlow(int id);

    static final class Source {
        final Renderer3DWorldChunkFrame.ChunkMesh chunk;
        final int offsetX, offsetZ;

        Source(Renderer3DWorldChunkFrame.ChunkMesh chunk, int offsetX, int offsetZ) {
            this.chunk = chunk;
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }
    }

    private static final class Plan {
        final int id;
        final int[] words;

        Plan(int id, int[] words) {
            this.id = id;
            this.words = words;
        }
    }

    void offer(mudclient client) {
        long now = System.currentTimeMillis();
        if (now < nextOffer || RendererLightingSettings.getMode() != RendererLightingSettings.Mode.DIRECTIONAL) return;
        nextOffer = now + 333L;
        List<Renderer3DWorldChunkFrame.ChunkMesh> terrain = client.getPreparedTerrainMeshes(true);
        if (terrain.isEmpty()) return;
        Renderer3DWorldChunkFrame.ChunkMesh anchor = terrain.get(0);
        if (anchor.getPlane() != 0) return;
        int x = anchor.getCenterSectionX(), z = anchor.getCenterSectionY();
        if (plans.isEmpty() || x != nextSectionX || z != nextSectionZ) {
            plans.clear();
            nextSectionX = x;
            nextSectionZ = z;
            work[0]++;
        }
        List<Source> full = new ArrayList<>();
        List<Source> retained = new ArrayList<>();
        for (Renderer3DWorldChunkFrame.ChunkMesh chunk : terrain) {
            Source src = new Source(chunk, 0, 0);
            full.add(src);
            retained.add(src);
        }
        client.appendPredictedGlowScenery(x, z, full, retained);
        offer(RemasterGlowMaskBuilder.prepareRecipe(full));
        offer(RemasterGlowMaskBuilder.prepareRecipe(retained));
    }

    private void offer(int[] recipe) {
        if (recipe == null) return;
        for (Plan plan : plans) if (Arrays.equals(recipe, plan.words)) return;
        int id = prepareGlow(recipe);
        if (id <= 0) return;
        if (plans.size() == MAX_PLANS) plans.remove(0);
        plans.add(new Plan(id, recipe));
        work[1]++;
    }

    RemasterGlowMask take(Renderer3DWorldChunkFrame frame, int[] recipe, long revision,
            float minX, float minZ, float invSpanX, float invSpanZ) {
        if (plans.isEmpty()) return null;
        Renderer3DWorldChunkFrame.ChunkMesh anchor = frame.getChunks().get(0);
        if (anchor.getCenterSectionX() != nextSectionX || anchor.getCenterSectionY() != nextSectionZ) return null;
        work[2]++;
        boolean size = false, bounds = false, emitters = false;
        for (Plan plan : plans) {
            size |= plan.words.length == recipe.length;
            bounds |= sameWords(plan.words, recipe, 0, 5);
            emitters |= plan.words.length == recipe.length && sameWords(plan.words, recipe, 5, recipe.length);
            if (!Arrays.equals(plan.words, recipe)) continue;
            work[3]++;
            byte[] bytes = takeGlow(plan.id);
            int axis = recipe[0];
            if (bytes == null || bytes.length != 4 + axis * axis * 4) return null;
            ByteBuffer payload = ByteBuffer.wrap(bytes);
            int visible = payload.getInt();
            if (visible <= 0 || visible > axis * axis) return null;
            work[4]++;
            work[5] += bytes.length - 4;
            return new RemasterGlowMask(revision, axis, axis, visible, minX, minZ, invSpanX, invSpanZ, payload.slice());
        }
        if (size) work[6]++;
        if (bounds) work[7]++;
        if (emitters) work[8]++;
        work[9] = (recipe.length - 5) / 5;
        return null;
    }

    private static boolean sameWords(int[] a, int[] b, int from, int end) {
        for (int i = from; i < end; i++) if (a[i] != b[i]) return false;
        return true;
    }

    int[] work() { return work.clone(); }
}
