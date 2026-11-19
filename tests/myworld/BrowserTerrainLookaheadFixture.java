package orsc;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

public final class BrowserTerrainLookaheadFixture {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        BrowserTerrainLookahead lookahead = new BrowserTerrainLookahead();
        Renderer3DWorldChunkFrame world = Renderer3DWorldChunkFrame.fromChunks(
            Collections.singletonList(TerrainDefinitionFieldFixture.mesh(0, 1)));
        Renderer3DFrame live = BrowserLightingFixture.frame(0, world);
        TerrainDefinitionField literal = new TerrainDefinitionField();
        require(literal.update(live), "fixture terrain");
        BrowserTerrainLookahead.Mask ready = new TerrainDefinitionField().prepareLookahead(world);
        byte[] actual = new byte[literal.pixels().remaining()];
        literal.pixels().get(actual);
        require(Arrays.equals(ready.pixels, actual), "preparing ahead changed terrain shading");
        Field cache = BrowserTerrainLookahead.class.getDeclaredField("masks");
        cache.setAccessible(true);
        ((Map<String, BrowserTerrainLookahead.Mask>) cache.get(lookahead)).put("fixture", ready);
        require(lookahead.find(0, 0, ready.x, ready.z, ready.w, ready.h, ready.heights) == ready.pixels,
            "the exact live sample did not reuse the prepared pixels");
        int[] changed = ready.heights.clone();
        changed[0]++;
        require(lookahead.find(0, 0, ready.x, ready.z, ready.w, ready.h, changed) == null,
            "stale sample reused after terrain height changed");
        require(lookahead.find(0, -1, ready.x, ready.z, ready.w, ready.h, ready.heights) == null,
            "a signed-level change reused a surface mask");
        require(lookahead.find(0, 0, ready.x + 1, ready.z, ready.w, ready.h, ready.heights) == null,
            "a different absolute region reused a mask");
        lookahead.clear();
        require(lookahead.find(0, 0, ready.x, ready.z, ready.w, ready.h, ready.heights) == null,
            "session clear retained an old mask");
        System.out.println("PASS prepared terrain pixels, exact height and bounds, signed levels, and clear");
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
