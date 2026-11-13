package orsc;

import java.nio.ByteBuffer;

/** Native-only lossless derivative. Atlas UVs and both color sources are retained.
 * Legacy light component 13 is unused by the admitted parity shader. */
final class NativeResidentGeometry {
    static final int STRIDE = 52;
    static final int[] FLOAT_SOURCE = {0,1,2,7,8,18,19,20};
    static final int[] BYTE_SOURCE = {3,4,5,6,15,16,17,14,24,25,26,21,22,23,27,9,10,11,12};
    static final int[] BYTE_TARGET = {32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,48,49,50,51};
    static boolean normalized(int source) { return source != 14 && source != 21 && source != 22 && source != 23; }
    static int exactByte(float value, boolean normalized) {
        if (!Float.isFinite(value)) return -1;
        int candidate = Math.round(normalized ? value * 255f : value);
        if (candidate < 0 || candidate > 255) return -1;
        float reconstructed = normalized ? candidate / 255f : (float)candidate;
        return Float.floatToRawIntBits(value) == Float.floatToRawIntBits(reconstructed) ? candidate : -1;
    }
    /** Failure leaves the destination position unchanged; caller falls back for the whole chunk. */
    static boolean put(ByteBuffer out, float[] source) {
        for (int c : FLOAT_SOURCE) if (!Float.isFinite(source[c])) return false;
        for (int c : BYTE_SOURCE) if (exactByte(source[c], normalized(c)) < 0) return false;
        int start = out.position();
        for (int c : FLOAT_SOURCE) out.putFloat(source[c]);
        for (int i=0;i<BYTE_SOURCE.length;i++) out.put(start+BYTE_TARGET[i], (byte)exactByte(source[BYTE_SOURCE[i]],normalized(BYTE_SOURCE[i])));
        out.put(start+47,(byte)0); out.position(start+STRIDE); return true;
    }
    static boolean enabled() {
        return Boolean.getBoolean("spoiledmilk.nativeCompactGeometry")
            && !Boolean.getBoolean("spoiledmilk.browserBenchmark") && !Boolean.getBoolean("spoiledmilk.browserPublicAlpha");
    }
}
