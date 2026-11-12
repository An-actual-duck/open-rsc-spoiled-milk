package orsc;

/** Private browser trace over existing boundary hooks, without JMX or file I/O.
 * Timers overlap. An absent start is -1, never an inferred chronological span. */
public final class BrowserRegionHandoffTrace {
    public static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.browserBenchmark")
        && Boolean.getBoolean("spoiledmilk.browserLoadProfile");
    static final int CAPACITY = 512, STRIDE = 12, HEADER = 8;
    private static final long ORIGIN = ENABLED ? System.nanoTime() : 0L;
    private static final double[] ROWS = ENABLED ? new double[CAPACITY * STRIDE] : null;
    private static final String[] PHASES = {
        "unmapped", "context.header-decode", "context.terrain-decode", "context.state-accept",
        "context.scope-apply", "context.prediction-and-activation", "context.ready-receipt",
        "terrain-stage.packet-and-queue", "client-region.total", "client-region.dematerialize",
        "client-region.world-load", "client-region.static-rebase", "client-region.static-materialize",
        "client-region.ground-items", "client-region.actor-rebase", "client-region.finalize",
        "client-region.baseline", "world-section.total", "world-section.reset",
        "world-section.active-plane", "world-section.upper-planes", "world-section.bridge",
        "world-section.active-chunk-build", "world-section.predictive-clear",
        "world-section.symmetric-compose", "world-section.preload", "scenery.input", "scenery.mesh"
    };
    private static long ordinal;
    private static int count, next, context, packetBudget, frameBudget;

    private BrowserRegionHandoffTrace() { }

    public static String phaseNames() { return String.join("|", PHASES); }
    private static double millis(long nanos) { return Math.max(0L, nanos - ORIGIN) / 1_000_000d; }

    // Row: ordinal, context, kind, phase, observedMs, startMs/-1, durationMs, a..e.
    private static void row(int kind, int phase, long start, long duration,
                            double a, double b, double c, double d, double e) {
        if (context == 0 || ordinal == 9_007_199_254_740_991L) return;
        int at = next * STRIDE;
        ROWS[at++] = ++ordinal; ROWS[at++] = context; ROWS[at++] = kind; ROWS[at++] = phase;
        ROWS[at++] = millis(System.nanoTime()); ROWS[at++] = start > 0L ? millis(start) : -1d;
        ROWS[at++] = Math.max(0L, duration) / 1_000_000d;
        ROWS[at++] = a; ROWS[at++] = b; ROWS[at++] = c; ROWS[at++] = d; ROWS[at] = e;
        next = (next + 1) % CAPACITY; count = Math.min(CAPACITY, count + 1);
    }

    static synchronized void begin(int sequence, int protocol, int tick,
                                   int x, int y, int level, long start) {
        if (!ENABLED) return;
        context = sequence; packetBudget = 128; frameBudget = 32;
        row(1, 0, start, 0L, protocol, tick, x, y, level);
    }

    static synchronized void destination(int level, int x, int y, boolean scope, boolean predicted) {
        if (ENABLED) row(2, 0, 0L, 0L, level, x, y, scope ? 1 : 0, predicted ? 1 : 0);
    }

    static synchronized void phase(String owner, String name, long start, long duration) {
        if (!ENABLED) return;
        int code = 0;
        for (int i = 1; i < PHASES.length; i++) {
            String key = PHASES[i]; int dot = key.indexOf('.');
            if (key.regionMatches(0, owner, 0, dot) && owner.length() == dot
                && key.regionMatches(dot + 1, name, 0, name.length())
                && key.length() == dot + 1 + name.length()) { code = i; break; }
        }
        row(3, code, start, duration, 0, 0, 0, 0, 0);
    }

    static synchronized void packet(int opcode, int bytes, long start, long duration) {
        if (ENABLED && packetBudget > 0) {
            packetBudget--; row(4, 0, start, duration, opcode, bytes, 0, 0, 0);
        }
    }

    static synchronized void baseline(int sequence, int category, int index, int total, int records) {
        if (ENABLED) row(5, 0, 0L, 0L, sequence, category, index, total, records);
    }

    static synchronized void region(boolean hard, boolean changed, int dx, int dy, int scenery, int walls) {
        if (ENABLED) row(6, 0, 0L, 0L, (hard ? 1 : 0) | (changed ? 2 : 0), dx, dy, scenery, walls);
    }

    static synchronized void atomic(boolean complete, boolean player, boolean baseline, long elapsed) {
        if (ENABLED) row(7, 0, 0L, 0L, complete ? 1 : 0, player ? 1 : 0, baseline ? 1 : 0, elapsed, 0);
    }

    static synchronized void release(int samples, boolean stable, int chunks, int triangles) {
        if (ENABLED) row(8, 0, 0L, 0L, samples, stable ? 1 : 0, chunks, triangles, 0);
    }

    static synchronized void bridge(long attempt, int path, long epoch, long start, long duration) {
        if (ENABLED && frameBudget > 0) {
            frameBudget--; row(9, 0, start, duration, attempt, path, epoch, 0, 0);
        }
    }

    /** Owned chronological rows. Snapshot clock and browser receipt clock are separate. */
    public static synchronized double[] snapshot() {
        double[] result = new double[HEADER + count * STRIDE];
        result[0] = 1; result[1] = ENABLED ? 1 : 0; result[2] = ordinal;
        result[3] = Math.max(0L, ordinal - count); result[4] = count; result[5] = STRIDE;
        result[6] = ENABLED ? millis(System.nanoTime()) : 0; result[7] = context;
        for (int i = 0; i < count; i++) {
            int index = (next - count + i + CAPACITY) % CAPACITY;
            System.arraycopy(ROWS, index * STRIDE, result, HEADER + i * STRIDE, STRIDE);
        }
        return result;
    }
}
