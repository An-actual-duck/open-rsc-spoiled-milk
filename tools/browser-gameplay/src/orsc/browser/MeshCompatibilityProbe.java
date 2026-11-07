package orsc.browser;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import orsc.graphics.three.Renderer3DMaterialFamily;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Calls the actual maintained mesh normalizer, including on a worker thread. */
public final class MeshCompatibilityProbe {
    private MeshCompatibilityProbe() { }
    public static void main(String[] args) throws Exception { System.out.println(run()); }
    public static String run() throws Exception {
        final Method method = Renderer3DWorldChunkFrame.ChunkMesh.class.getDeclaredMethod("normalizeFamilies", Renderer3DMaterialFamily[].class, Renderer3DModelKind[].class, int.class);
        method.setAccessible(true);
        Callable<Integer> checks = new Callable<Integer>() {
            public Integer call() throws Exception {
                int assertions = 0;
                for (int repeat = 0; repeat < 200; repeat++) {
                    for (int count : new int[]{0, 1, 16, 1024}) {
                        for (boolean provideKinds : new boolean[]{false, true}) {
                            Renderer3DModelKind[] kinds = provideKinds ? new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN, null} : null;
                            for (boolean provideFamilies : new boolean[]{false, true}) {
                                Renderer3DMaterialFamily[] families = provideFamilies ? new Renderer3DMaterialFamily[]{Renderer3DMaterialFamily.WATER, null} : null;
                                Renderer3DMaterialFamily[] result = (Renderer3DMaterialFamily[]) method.invoke(null, new Object[]{families, kinds, count});
                                if (result.length != count) throw new AssertionError("Wrong normalizer length");
                                for (Renderer3DMaterialFamily family : result) if (family == null) throw new AssertionError("Null normalized family");
                                assertions++;
                            }
                        }
                    }
                    Renderer3DWorldChunkFrame.ChunkMesh chunk = new Renderer3DWorldChunkFrame.ChunkMesh(
                        0, 0, 0, 0, 0, new int[]{0,0,0,128,0,0,0,0,128}, new float[3], new float[3], new int[3], new int[3], new int[3],
                        new int[]{0,1,2}, new int[]{1}, new int[]{1}, new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},
                        new Renderer3DMaterialFamily[]{Renderer3DMaterialFamily.TERRAIN}, null, null, new int[1], new long[0], 0, 0, 1, 0, 0, false, 0, 0L);
                    if (chunk.getTriangleMaterialFamily(0) != Renderer3DMaterialFamily.TERRAIN) throw new AssertionError("Constructor material mismatch");
                    assertions++;
                }
                return assertions;
            }
        };
        int main = checks.call();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try { return "mesh-probe:PASS main=" + main + " worker=" + worker.submit(checks).get(); }
        finally { worker.shutdownNow(); }
    }
}
