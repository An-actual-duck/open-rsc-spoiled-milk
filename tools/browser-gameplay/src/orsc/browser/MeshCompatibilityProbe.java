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
    private static int directChecks() throws Exception {
        for (boolean provideKinds : new boolean[]{false, true}) {
            for (boolean provideFamilies : new boolean[]{false, true}) {
                try {
                    Renderer3DWorldChunkFrame.ChunkMesh direct = new Renderer3DWorldChunkFrame.ChunkMesh(
                        0, 0, 0, 0, 0, new int[]{0,0,0,128,0,0,0,0,128}, new float[3], new float[3], new int[3], new int[3], new int[3],
                        new int[]{0,1,2}, new int[]{1}, new int[]{1}, provideKinds ? new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN} : null,
                        provideFamilies ? new Renderer3DMaterialFamily[]{Renderer3DMaterialFamily.WATER} : null,
                        null, null, new int[1], new long[0], 0, 0, 1, 0, 0, false, 0, 0L);
                    Renderer3DMaterialFamily expected = provideFamilies ? Renderer3DMaterialFamily.WATER : provideKinds ? Renderer3DMaterialFamily.TERRAIN : Renderer3DMaterialFamily.UNCLASSIFIED;
                    if (direct.getTriangleMaterialFamily(0) != expected) throw new AssertionError("Direct material mismatch");
                } catch (Throwable error) {
                    throw new IllegalStateException("Direct constructor failed: kinds=" + provideKinds + " families=" + provideFamilies, error);
                }
            }
        }
        int checks = 4;
        for (int count : new int[]{16, 4608}) {
            Renderer3DModelKind[] kinds = new Renderer3DModelKind[count];
            for (int i = 0; i < count; i++) kinds[i] = Renderer3DModelKind.TERRAIN;
            // Mirrors real halo cropping: full normalized kinds and either
            // absent families or supplied per-triangle material overrides.
            for (boolean supplyFamilies : new boolean[]{false, true}) {
                Renderer3DMaterialFamily[] families = supplyFamilies
                    ? new Renderer3DMaterialFamily[]{Renderer3DMaterialFamily.WATER, null} : null;
                Renderer3DWorldChunkFrame.ChunkMesh mesh = new Renderer3DWorldChunkFrame.ChunkMesh(
                    0, 0, 0, 0, 0, new int[0], new float[0], new float[0], new int[0], new int[0], new int[0],
                    new int[0], new int[count], new int[count], kinds, families,
                    null, null, new int[count], new long[0], 0, 0, count, 0, 0, false, 0, 0L);
                for (int i = 0; i < count; i++) {
                    Renderer3DMaterialFamily expected = supplyFamilies && i == 0
                        ? Renderer3DMaterialFamily.WATER : Renderer3DMaterialFamily.TERRAIN;
                    if (mesh.getTriangleMaterialFamily(i) != expected) throw new AssertionError("Halo-size material mismatch at " + i);
                }
                kinds[0] = Renderer3DModelKind.WALL;
                if (families != null) families[0] = Renderer3DMaterialFamily.SCENERY;
                Renderer3DMaterialFamily first = supplyFamilies ? Renderer3DMaterialFamily.WATER : Renderer3DMaterialFamily.TERRAIN;
                if (mesh.getTriangleMaterialFamily(0) != first) throw new AssertionError("Mesh leaked source family array");
                kinds[0] = Renderer3DModelKind.TERRAIN;
                checks++;
            }
        }
        return checks;
    }
    private static int primitiveChecks() throws Exception {
        Method ints=Renderer3DWorldChunkFrame.ChunkMesh.class.getDeclaredMethod("normalizeIntArray",int[].class,int.class,int.class);
        Method floats=Renderer3DWorldChunkFrame.ChunkMesh.class.getDeclaredMethod("normalizeFloatArray",float[].class,int.class,float.class);
        ints.setAccessible(true);floats.setAccessible(true);int checks=0;
        for(int repeat=0;repeat<100;repeat++)for(int count:new int[]{0,1,16,1024,4608})for(int length:new int[]{-1,0,1,16,4608}) {
            int[] iv=length<0?null:new int[length];float[] fv=length<0?null:new float[length];
            if(iv!=null)for(int n=0;n<length;n++){iv[n]=n*3-7;fv[n]=n*.25f-7;}
            int[] ia=(int[])ints.invoke(null,new Object[]{iv,count,91});float[] fa=(float[])floats.invoke(null,new Object[]{fv,count,-2.5f});
            if(ia.length!=count||fa.length!=count)throw new AssertionError("Primitive length mismatch");
            for(int n=0;n<count;n++) {int wantI=length<0||n>=length?91:n*3-7;float wantF=length<0||n>=length?-2.5f:n*.25f-7;
                if(ia[n]!=wantI||Float.floatToIntBits(fa[n])!=Float.floatToIntBits(wantF))throw new AssertionError("Primitive copy/fill mismatch");}
            if(iv!=null&&length>0&&count>0){int old=ia[0];float previous=fa[0];iv[0]=42;fv[0]=42;if(ia[0]!=old||fa[0]!=previous)throw new AssertionError("Primitive array aliases source");}
            checks+=2;
        }
        return checks;
    }
    public static String run() throws Exception {
        final int primitiveMain=primitiveChecks();
        ExecutorService primitiveWorker=Executors.newSingleThreadExecutor();
        try { System.out.println("mesh-probe:primitives:PASS main="+primitiveMain+" worker="+primitiveWorker.submit(new Callable<Integer>(){public Integer call()throws Exception{return primitiveChecks();}}).get()); }
        finally { primitiveWorker.shutdownNow(); }
        // Both direct controls finish before any reflective invocation.
        int directMain = directChecks();
        ExecutorService directWorker = Executors.newSingleThreadExecutor();
        try {
            int directBackground = directWorker.submit(new Callable<Integer>() {
                public Integer call() throws Exception { return directChecks(); }
            }).get();
            System.out.println("mesh-probe:direct-constructor:PASS main=" + directMain + " worker=" + directBackground + " (before reflection)");
        } finally { directWorker.shutdownNow(); }
        final Method method = Renderer3DWorldChunkFrame.ChunkMesh.class.getDeclaredMethod("normalizeFamilies", Renderer3DMaterialFamily[].class, Renderer3DModelKind[].class, int.class);
        method.setAccessible(true);
        Callable<Integer> checks = new Callable<Integer>() {
            public Integer call() throws Exception {
                int assertions = 0;
                for (int repeat = 0; repeat < 200; repeat++) {
                    for (int count : new int[]{0, 1, 16, 1024, 4608}) {
                        for (boolean provideKinds : new boolean[]{false, true}) {
                            Renderer3DModelKind[] kinds = provideKinds ? new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN, null} : null;
                            for (boolean provideFamilies : new boolean[]{false, true}) {
                                Renderer3DMaterialFamily[] families = provideFamilies ? new Renderer3DMaterialFamily[]{Renderer3DMaterialFamily.WATER, null} : null;
                                Renderer3DMaterialFamily[] result;
                                try { result = (Renderer3DMaterialFamily[]) method.invoke(null, new Object[]{families, kinds, count}); }
                                catch (Throwable error) { throw new IllegalStateException("Normalizer failed: repeat=" + repeat + " count=" + count + " kinds=" + provideKinds + " families=" + provideFamilies, error); }
                                if (result.length != count) throw new AssertionError("Wrong normalizer length");
                                for (int i = 0; i < result.length; i++) {
                                    Renderer3DMaterialFamily expected = provideFamilies && i == 0
                                        ? Renderer3DMaterialFamily.WATER
                                        : provideKinds && i == 0 ? Renderer3DMaterialFamily.TERRAIN
                                        : Renderer3DMaterialFamily.UNCLASSIFIED;
                                    if (result[i] != expected) throw new AssertionError("Material mismatch at " + i);
                                }
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
