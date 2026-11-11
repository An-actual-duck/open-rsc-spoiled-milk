package orsc.graphics.three;

/** Private exact construction experiment. No retained model/coordinate cache. */
public final class BrowserRegionBuild {
    private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.browserBenchmark")
        && Boolean.getBoolean("spoiledmilk.browserRegionBuild");
    private BrowserRegionBuild() {}
    public static boolean isEnabled() { return ENABLED; }

    /** FNV-1a modulo 2^64, represented by two primitive signed int limbs. */
    public static final class Fnv64 {
        private int low, high;
        public Fnv64(long seed) { low=(int)seed; high=(int)(seed >>> 32); }
        public void add(int value) {
            int x=low ^ value;
            int p0=(x & 65535)*435;
            int p1=(x >>> 16)*435+(p0 >>> 16);
            low=(p0 & 65535)|(p1 << 16);
            high=high*435+(p1 >>> 16)+(x << 8);
        }
        public long value() { return ((long)high << 32)|(low & 0xffffffffL); }
    }

    /** Scratch arrays capped at one million elements each; oversized inputs use legacy lists. */
    static final class IntValues {
        private int[] values=new int[16];
        private int size;
        private java.util.ArrayList<Integer> overflow;
        int size() { return size; }
        void add(int value) {
            if (overflow!=null) { overflow.add(Integer.valueOf(value)); size++; return; }
            if (size==values.length) {
                if (size==1048576) {
                    overflow=new java.util.ArrayList<Integer>(size+1);
                    for (int i=0;i<size;i++) overflow.add(Integer.valueOf(values[i]));
                    values=null; overflow.add(Integer.valueOf(value)); size++; return;
                }
                values=java.util.Arrays.copyOf(values,Math.min(1048576,size+(size>>1)+1));
            }
            values[size++]=value;
        }
        int[] array() {
            if (overflow==null) return java.util.Arrays.copyOf(values,size);
            int[] result=new int[size];
            for(int i=0;i<size;i++) result[i]=overflow.get(i).intValue();
            return result;
        }
    }
    static final class FloatValues {
        private float[] values=new float[16];
        private int size;
        private java.util.ArrayList<Float> overflow;
        void add(float value) {
            if (overflow!=null) { overflow.add(Float.valueOf(value)); size++; return; }
            if (size==values.length) {
                if (size==1048576) {
                    overflow=new java.util.ArrayList<Float>(size+1);
                    for (int i=0;i<size;i++) overflow.add(Float.valueOf(values[i]));
                    values=null; overflow.add(Float.valueOf(value)); size++; return;
                }
                values=java.util.Arrays.copyOf(values,Math.min(1048576,size+(size>>1)+1));
            }
            values[size++]=value;
        }
        float[] array() {
            if (overflow==null) return java.util.Arrays.copyOf(values,size);
            float[] result=new float[size];
            for(int i=0;i<size;i++) result[i]=overflow.get(i).floatValue();
            return result;
        }
    }
}
