package orsc;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Run with the completed-mask lighting overlay ahead of the release client on the classpath. */
public final class CompletedTerrainMaskFixture {
    private static Renderer3DFrame frame(int level, long signature, int peak) {
        Renderer3DWorldChunkFrame.ChunkMesh sample = TerrainDefinitionFieldFixture.mesh(0, signature);
        int vertices = sample.getVertexCount();
        int triangles = sample.getTriangleCount();
        int[] coordinates = new int[vertices * 3];
        int[] indices = new int[sample.getIndexCount()];
        int[] textures = new int[triangles];
        int[] colors = new int[triangles];
        Renderer3DModelKind[] kinds = new Renderer3DModelKind[triangles];
        for (int i = 0; i < vertices; i++) {
            int at = i * 3;
            coordinates[at] = sample.getVertexCoord(at);
            coordinates[at + 1] = sample.getVertexCoord(at + 1);
            coordinates[at + 2] = sample.getVertexCoord(at + 2);
            if (coordinates[at] == 4 * 128 && coordinates[at + 2] == 4 * 128) {
                coordinates[at + 1] += peak;
            }
        }
        for (int i = 0; i < indices.length; i++) indices[i] = sample.getIndex(i);
        for (int i = 0; i < triangles; i++) {
            kinds[i] = sample.getTriangleModelKind(i);
            textures[i] = sample.getTriangleTexture(i);
            colors[i] = sample.getTriangleFallbackColor(i);
        }
        Renderer3DWorldChunkFrame.ChunkMesh mesh = new Renderer3DWorldChunkFrame.ChunkMesh(
            0, 10, 9, 432 * 128, 384 * 128, coordinates, new float[vertices], new float[vertices],
            new int[vertices], indices, textures, colors, kinds, triangles, 0, 0, signature);
        return BrowserLightingFixture.frame(level,
            Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(mesh)));
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static int count(TerrainDefinitionField field, String name) throws Exception {
        Field member = TerrainDefinitionField.class.getDeclaredField(name);
        member.setAccessible(true);
        return member.getInt(field);
    }

    private static byte[] render(TerrainDefinitionField field, Renderer3DFrame frame) {
        require(field.update(frame), "Mask unavailable");
        ByteBuffer pixels = field.pixels();
        byte[] output = new byte[pixels.remaining()];
        pixels.get(output);
        return output;
    }

    public static void main(String[] args) throws Exception {
        TerrainDefinitionField field = new TerrainDefinitionField();
        Renderer3DFrame a = frame(0, 1, 0);
        Renderer3DFrame b = frame(0, 2, -96);
        byte[] original = render(field, a);
        require(count(field, "completedMisses") == 1, "Cold mask did not build");
        long revision = field.revision;
        require(Arrays.equals(original, render(field, a)), "Unchanged frame changed its mask");
        require(field.revision == revision && count(field, "completedHits") == 0, "Steady fast path was bypassed");
        require(!Arrays.equals(original, render(field, b)), "Elevation change did not change the mask");
        require(count(field, "completedMisses") == 2, "Changed height reused a stale mask");
        revision = field.revision;
        require(Arrays.equals(original, render(field, a)), "Return mask pixels changed");
        require(field.revision != revision && count(field, "completedHits") == 1,
            "Return did not restore and publish the cached mask");
        render(field, b);
        require(Arrays.equals(original, render(field, frame(-3, 3, 0))), "Underground mask changed");
        require(count(field, "completedMisses") == 3, "Cross-level reuse escaped the cache scope");
        for (int n = 4; n < 9; n++) render(field, frame(0, n, n * 30));
        require(count(field, "completedBytes") == 4 * 9 * 9 * 8, "Retention did not obey the 4-entry limit");
        int misses = count(field, "completedMisses");
        require(Arrays.equals(original, render(field, a)), "Evicted mask did not rebuild exactly");
        require(count(field, "completedMisses") == misses + 1, "Evicted region reported a cache hit");
        field.clear();
        require(count(field, "completedBytes") == 0, "Session clear kept cached planes");
        require(Arrays.equals(original, render(field, a)), "Mask failed after clearing the session");
        require(count(field, "completedMisses") == misses + 2, "Session clear reused a cached mask");
        System.out.println("PASS exact height-match mask reuse, elevation and signed-level isolation, revisions, eviction and clear");
    }
}
