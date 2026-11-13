package orsc;

import java.nio.ByteBuffer;

/** Native-only lossless derivative. Atlas UVs and both color sources are retained.
 * Legacy light component 13 is unused by the admitted parity shader. */
final class NativeResidentGeometry {
    static final int STRIDE = 52;
    private static final int[] UNORM_BITS=new int[256];
    static { for(int i=0;i<256;i++)UNORM_BITS[i]=Float.floatToRawIntBits(i/255f); }
    static final int[] FLOAT_SOURCE = {0,1,2,7,8,18,19,20};
    static final int[] BYTE_SOURCE = {3,4,5,6,15,16,17,14,24,25,26,21,22,23,27,9,10,11,12};
    static final int[] BYTE_TARGET = {32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,48,49,50,51};
    static boolean normalized(int source) { return source != 14 && source != 21 && source != 22 && source != 23; }
    static int exactByte(float value, boolean normalized) {
        if (!Float.isFinite(value)) return -1;
        int candidate = (int)((normalized ? value * 255f : value) + .5f);
        if (candidate < 0 || candidate > 255) return -1;
        int reconstructed = normalized ? UNORM_BITS[candidate] : Float.floatToRawIntBits((float)candidate);
        return Float.floatToRawIntBits(value) == reconstructed ? candidate : -1;
    }
    /** Failure leaves the destination position unchanged; caller falls back for the whole chunk. */
    static boolean put(ByteBuffer out, float[] source) {
        for (int c : FLOAT_SOURCE) if (!Float.isFinite(source[c])) return false;
        int start = out.position();
        for (int i=0;i<BYTE_SOURCE.length;i++) {
            int c=BYTE_SOURCE[i], value=exactByte(source[c],normalized(c));
            if(value<0)return false;
            out.put(start+BYTE_TARGET[i],(byte)value);
        }
        for (int c : FLOAT_SOURCE) out.putFloat(source[c]);
        out.put(start+47,(byte)0); out.position(start+STRIDE); return true;
    }
    // These encode already-derived integer shader fields, never arbitrary floats.
    // No clamping/truncation is allowed here: future IDs/masks/strengths must fall
    // back for the entire chunk. RGB extraction is the existing 24-bit contract.
    static boolean putTypedShaderInputs(ByteBuffer out, int light, int rawColor,
        int kind, int family, int mask, int terrainColor, int strength) {
        if (((light | kind | family | mask | strength) & ~255) != 0) return false;
        int start = out.position();
        putRgb(out, start + 36, rawColor);
        out.put(start + 39, (byte)light);
        putRgb(out, start + 40, terrainColor);
        out.put(start + 43, (byte)kind);
        out.put(start + 44, (byte)family);
        out.put(start + 45, (byte)mask);
        out.put(start + 46, (byte)strength);
        return true;
    }
    private static void putRgb(ByteBuffer out, int at, int color) {
        out.put(at, (byte)((color >> 16) & 255));
        out.put(at + 1, (byte)((color >> 8) & 255));
        out.put(at + 2, (byte)(color & 255));
    }
    static void putTypedMaterialColor(ByteBuffer out, int color) {
        int start = out.position();
        putRgb(out, start + 32, color);
        out.put(start + 35, (byte)255);
    }
    static void putUnbakedTextureLight(ByteBuffer out) {
        int start = out.position();
        for (int i = 48; i < 52; i++) out.put(start + i, (byte)255);
    }
    /** Commit a vertex only after admission. Negative zero is preserved in floats;
     * integers cannot express it. Arbitrary float byte fields still use exactByte. */
    static boolean putTypedFloats(ByteBuffer out, float[] source) {
        for (int c : FLOAT_SOURCE) if (!Float.isFinite(source[c])) return false;
        int start = out.position();
        for (int c : FLOAT_SOURCE) out.putFloat(source[c]);
        out.put(start + 47, (byte)0);
        out.position(start + STRIDE);
        return true;
    }
    static boolean typedWriterEnabled() {
        return Boolean.getBoolean("spoiledmilk.nativeCompactTypedWriter") && enabled();
    }
    static boolean enabled() {
        return Boolean.getBoolean("spoiledmilk.nativeCompactGeometry")
            && !Boolean.getBoolean("spoiledmilk.browserBenchmark") && !Boolean.getBoolean("spoiledmilk.browserPublicAlpha");
    }
}
