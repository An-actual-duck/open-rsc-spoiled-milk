package orsc.graphics.three;

import java.util.Arrays;
import java.util.IdentityHashMap;
import orsc.util.FastMath;

/** Private render-thread packet of borrowed array REFERENCES, never vertices.
 * Dirty/unsupported/interaction-only models are barriers handled by Scene.
 * No model metadata changes until a whole validated packet has completed. */
public final class BrowserBatchedModelKernel {
    static final int MAX_MODELS = 128;
    interface Backend {
        boolean transform(int size, int[][] rows, int[] counts, int[] camera, int[] trig, int[] trigSource);
    }
    private final Backend backend;
    private final int[][] rows = new int[MAX_MODELS * 8][];
    private final int[] counts = new int[MAX_MODELS], camera = new int[8], trig = new int[6];
    private final RSModel[] models = new RSModel[MAX_MODELS];
    private final boolean[] visible = new boolean[MAX_MODELS];
    private final IdentityHashMap<int[], Boolean> roles = new IdentityHashMap<int[], Boolean>(MAX_MODELS * 8);
    private int size, numericModels, pendingArrayRefs, pendingMapOperations;
    private long packetVertices;
    private int[] trigSource;
    private boolean coefficientsKnown;
    // Saturating diagnostics only, sampled once/second by private Bootstrap.
    private final int[] work = new int[14];

    BrowserBatchedModelKernel() {
        this(new Backend() {
            public boolean transform(int size, int[][] rows, int[] counts, int[] camera, int[] trig, int[] trigSource) {
                return transformPacket(size, rows, counts, camera, trig, trigSource);
            }
        });
    }
    BrowserBatchedModelKernel(Backend backend) {
        if (backend == null) throw new NullPointerException("backend");
        this.backend = backend;
    }
    private static native boolean transformPacket(int size, int[][] rows, int[] counts, int[] camera, int[] trig, int[] trigSource);

    private void add(int index, long amount) {
        work[index] = amount >= Integer.MAX_VALUE - work[index] ? Integer.MAX_VALUE : work[index] + (int)amount;
    }
    void begin(int yOffset, int shift, int xOffset, int zOffset, int rotY, int rotZ, int rotX, int near) {
        discard();
        camera[0] = xOffset; camera[1] = yOffset; camera[2] = zOffset; camera[3] = shift;
        camera[4] = near; camera[5] = rotZ; camera[6] = rotY; camera[7] = rotX;
        add(0, 1);
    }
    private boolean coefficients() {
        int[] table = FastMath.trigTable1024;
        if (coefficientsKnown) return table == trigSource;
        for (int n = 5; n < 8; n++) {
            int angle = camera[n];
            if (angle != 0 && (table == null || angle < 0 || angle > table.length - 1025)) return false;
        }
        for (int n = 0; n < 3; n++) {
            int angle = camera[5 + n];
            trig[n * 2] = angle == 0 ? 0 : table[angle];
            trig[n * 2 + 1] = angle == 0 ? 0 : table[angle + 1024];
        }
        trigSource = table; coefficientsKnown = true;
        return true;
    }

    /** Pure eligibility checks: no resetTransformCache, no m_dc write. */
    boolean enqueue(RSModel model) {
        if (model == null || !model.isBrowserBatchCacheClean()) {
            add(9, 1); return false;
        }
        if (size == MAX_MODELS) flush();
        boolean inFrustum = model.isBrowserBatchFrustumVisible();
        int count = inFrustum ? model.vertHead : 0;
        if (inFrustum && (count < 0 || camera[4] <= 0 || !coefficients())) return false;
        int offset = size * 8;
        if (count > 0) {
            model.borrowBrowserBatchArrays(rows, offset); pendingArrayRefs += 8;
            if (!validRow(rows, offset, count)) { Arrays.fill(rows, offset, offset + 8, null); return false; }
            // Coefficients are shared by this segment. A writer must not change
            // their source table or any earlier model's reader/writer storage.
            boolean conflict = false; pendingMapOperations += 8;
            for (int n = 0; n < 3; n++) conflict |= Boolean.TRUE.equals(roles.get(rows[offset + n]));
            for (int n = 3; n < 8; n++)
                conflict |= rows[offset + n] == trigSource || roles.containsKey(rows[offset + n]);
            if (conflict) {
                add(8, 1); Arrays.fill(rows, offset, offset + 8, null); return false;
            }
            for (int n = 0; n < 3; n++) roles.put(rows[offset + n], Boolean.FALSE);
            for (int n = 3; n < 8; n++) roles.put(rows[offset + n], Boolean.TRUE);
            pendingMapOperations += 8; numericModels++; packetVertices += count;
        }
        models[size] = model; visible[size] = inFrustum; counts[size] = count; size++;
        return true;
    }

    static boolean validRow(int[][] rows, int offset, int count) {
        for (int n = 0; n < 8; n++) if (rows[offset + n] == null || rows[offset + n].length < count) return false;
        for (int n = 3; n < 8; n++) for (int prior = 0; prior < n; prior++)
            if (rows[offset + n] == rows[offset + prior]) return false;
        return true;
    }

    void literalModel() { add(6, 1); }
    void flush() {
        if (size == 0) { add(12,pendingArrayRefs); add(13,pendingMapOperations); discard(); return; }
        try {
            add(12,pendingArrayRefs); add(13,pendingMapOperations);
            add(1, 1); add(10, size); work[11] = Math.max(work[11], size);
            boolean used = true;
            if (numericModels > 0) {
                add(2, 1);
                used = backend.transform(size, rows, counts, camera, trig, trigSource);
            }
            if (used) {
                for (int n = 0; n < size; n++) models[n].m_dc = visible[n];
                add(3, numericModels); add(4, packetVertices); add(5, size - numericModels);
            } else {
                // Native representation rejection promises ZERO writes. Replay
                // the packet literally, in order, including exception prefixes.
                add(7, 1);
                for (int n = 0; n < size; n++) {
                    literalModel();
                    models[n].rotate1024(camera[1], camera[3], camera[0], (byte)-122,
                        camera[2], camera[6], camera[5], camera[7], camera[4]);
                }
            }
        } finally { discard(); }
    }
    void discard() {
        Arrays.fill(rows, 0, size * 8, null); Arrays.fill(models, 0, size, null);
        Arrays.fill(counts, 0, size, 0); Arrays.fill(visible, 0, size, false);
        size = numericModels = pendingArrayRefs = pendingMapOperations = 0; packetVertices = 0; if (!roles.isEmpty()) roles.clear();
        coefficientsKnown = false; trigSource = null;
    }
    public int[] work() { return work.clone(); }
}
