package orsc;

/** Opt-in bounded upload attribution. Copies are taken only by diagnostic fixtures. */
final class NativeGeometryDiagnostics {
    static final boolean ENABLED=Boolean.getBoolean("spoiledmilk.nativeGeometryDiagnostics");
    private static final int LIMIT=4096, WIDTH=11;
    private static final long[][] rows=ENABLED?new long[LIMIT][WIDTH]:null;
    private static long count;
    static long now() { return ENABLED ? System.nanoTime() : 0L; }
    static synchronized void record(long[] stages,long live,long scratch) {
        if(!ENABLED)return;
        long[] row=rows[(int)(count%LIMIT)];row[0]=System.nanoTime();
        System.arraycopy(stages,0,row,1,8);row[9]=live;row[10]=scratch;count++;
    }
    static synchronized long[][] snapshot() {
        int length=(int)Math.min(count,LIMIT);long[][] result=new long[length+1][];
        result[0]=new long[]{count,Math.max(0,count-LIMIT)};
        for(int i=0;i<length;i++)result[i+1]=rows[(int)((count-length+i)%LIMIT)].clone();
        return result;
    }
}
