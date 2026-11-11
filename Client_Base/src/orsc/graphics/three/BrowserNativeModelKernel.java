package orsc.graphics.three;

/** Private browser experiment. The Java loop remains the default and fallback. */
public final class BrowserNativeModelKernel {
    static boolean enabled;
    private static long calls, vertices, fallbacks;

    private BrowserNativeModelKernel() {}

    /** Called only by the explicitly opted-in private WebGL bootstrap. */
    public static void installForPrivateBrowser() {
        if (!Boolean.getBoolean("spoiledmilk.browserBenchmark")
                || !Boolean.getBoolean("spoiledmilk.browserNativeModelKernel"))
            throw new IllegalStateException("Native model kernel requires explicit private browser opt-in");
        enabled = true;
    }

    public static boolean isBrowserNativeModelKernelEnabled() { return enabled; }

    public static long[] getWork() { return new long[]{calls, vertices, fallbacks}; }

    static boolean supported(int count, int[] sx, int[] sy, int[] sz,
            int[] px, int[] py, int[] rx, int[] ry, int[] rz, int near) {
        if (count <= 0 || near <= 0 || sx == null || sy == null || sz == null
                || px == null || py == null || rx == null || ry == null || rz == null
                || sx.length < count || sy.length < count || sz.length < count
                || px.length < count || py.length < count || rx.length < count
                || ry.length < count || rz.length < count) return false;
        // Aliased source arrays are harmless. Every output must have exclusive
        // storage: otherwise Java's ordered writes can affect later reads.
        return px != py && px != rx && px != ry && px != rz
            && py != rx && py != ry && py != rz && rx != ry && rx != rz && ry != rz
            && px != sx && px != sy && px != sz && py != sx && py != sy && py != sz
            && rx != sx && rx != sy && rx != sz && ry != sx && ry != sy && ry != sz
            && rz != sx && rz != sy && rz != sz;
    }

    static boolean tryTransform(int count, int[] sx, int[] sy, int[] sz,
            int[] px, int[] py, int[] rx, int[] ry, int[] rz,
            int ox, int oy, int oz, int shift, int near,
            int rotZ, int rotY, int rotX, int sinZ, int cosZ,
            int sinY, int cosY, int sinX, int cosX) {
        if (!supported(count, sx, sy, sz, px, py, rx, ry, rz, near)) {
            fallbacks++;
            return false;
        }
        // Both ends validate before writes. A representation unsupported by the
        // browser returns false, retaining the literal Java implementation.
        boolean used = transform(count, sx, sy, sz, px, py, rx, ry, rz,
            ox, oy, oz, shift, near, rotZ, rotY, rotX,
            sinZ, cosZ, sinY, cosY, sinX, cosX);
        if (used) { calls++; vertices += count; } else fallbacks++;
        return used;
    }

    private static native boolean transform(int count, int[] sx, int[] sy, int[] sz,
        int[] px, int[] py, int[] rx, int[] ry, int[] rz,
        int ox, int oy, int oz, int shift, int near,
        int rotZ, int rotY, int rotX, int sinZ, int cosZ,
        int sinY, int cosY, int sinX, int cosX);
}
