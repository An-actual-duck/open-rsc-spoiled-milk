package orsc.graphics.three;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import orsc.util.FastMath;

/** Exact browser-only stationary projection reuse. No source hashes/revision trust. */
final class BrowserProjectionReuse {
    static final int MAX_BYTES = 8 * 1024 * 1024, MAX_ENTRIES = 256;
    private static final int MIN_VERTICES = 16, MAX_VERTICES = 8192, ENTRY_OVERHEAD = 512;
    private final IdentityHashMap<RSModel, Entry> entries = new IdentityHashMap<>();
    private final int[] pose = new int[14], nextPose = new int[14];
    private boolean poseValid, active;
    private int stableFrames, frame, bytes, hits, misses, admissionRejects;
    private static final class Entry {
        final int[][] values;
        final int bytes, count;
        int seen;
        Entry(int n) { count = n; bytes = n * 8 * 4 + ENTRY_OVERHEAD; values = new int[8][n]; }
    }
    void begin(boolean enabled, int y, int vp, int x, int z, int ry, int rz, int rx, int near) {
        if (!enabled || ry < 0 || ry >= 1024 || rz < 0 || rz >= 1024 || rx < 0 || rx >= 1024
                || near <= 0 || FastMath.trigTable1024 == null || FastMath.trigTable1024.length < 2048) {
            clear(); return;
        }
        nextPose[0]=y; nextPose[1]=vp; nextPose[2]=x; nextPose[3]=z;
        nextPose[4]=ry; nextPose[5]=rz; nextPose[6]=rx; nextPose[7]=near;
        nextPose[8]=FastMath.trigTable1024[ry]; nextPose[9]=FastMath.trigTable1024[ry+1024];
        nextPose[10]=FastMath.trigTable1024[rz]; nextPose[11]=FastMath.trigTable1024[rz+1024];
        nextPose[12]=FastMath.trigTable1024[rx]; nextPose[13]=FastMath.trigTable1024[rx+1024];
        boolean same=poseValid;
        for (int n=0; same && n<pose.length; n++) same=pose[n]==nextPose[n];
        if (!same) { entries.clear(); bytes=0; stableFrames=1; System.arraycopy(nextPose,0,pose,0,pose.length); poseValid=true; }
        else if (stableFrames<2) stableFrames++;
        if (++frame<=0) { entries.clear(); bytes=0; frame=1; }
        active=stableFrames>=2;
    }
    boolean isActive() { return active; }
    boolean accepts(int y,int vp,int x,int z,int ry,int rz,int rx,int near) {
        return active && pose[0]==y && pose[1]==vp && pose[2]==x && pose[3]==z
            && pose[4]==ry && pose[5]==rz && pose[6]==rx && pose[7]==near
            && FastMath.trigTable1024!=null && FastMath.trigTable1024.length>=2048
            && pose[8]==FastMath.trigTable1024[ry] && pose[9]==FastMath.trigTable1024[ry+1024]
            && pose[10]==FastMath.trigTable1024[rz] && pose[11]==FastMath.trigTable1024[rz+1024]
            && pose[12]==FastMath.trigTable1024[rx] && pose[13]==FastMath.trigTable1024[rx+1024];
    }
    private boolean valid(int n,int[] x,int[] y,int[] z,int[] px,int[] py,int[] rx,int[] ry,int[] rz) {
        if (!active || n<MIN_VERTICES || n>MAX_VERTICES || x==null || y==null || z==null
                || px==null || py==null || rx==null || ry==null || rz==null
                || n>x.length || n>y.length || n>z.length || n>px.length || n>py.length
                || n>rx.length || n>ry.length || n>rz.length) return false;
        // Original interleaved writes can have special semantics for malformed
        // aliasing; retain its literal path rather than invent array-copy order.
        return px!=py && px!=rx && px!=ry && px!=rz && py!=rx && py!=ry && py!=rz
            && rx!=ry && rx!=rz && ry!=rz
            && x!=px && x!=py && x!=rx && x!=ry && x!=rz
            && y!=px && y!=py && y!=rx && y!=ry && y!=rz
            && z!=px && z!=py && z!=rx && z!=ry && z!=rz;
    }
    boolean restore(RSModel model,int n,int[] x,int[] y,int[] z,int[] px,int[] py,int[] rx,int[] ry,int[] rz) {
        if (!valid(n,x,y,z,px,py,rx,ry,rz)) return false;
        Entry e=entries.get(model);
        if (e==null || e.count!=n) { if(misses<Integer.MAX_VALUE)misses++; return false; }
        e.seen=frame;
        for (int i=0;i<n;i++) if(e.values[0][i]!=x[i] || e.values[1][i]!=y[i] || e.values[2][i]!=z[i]) { if(misses<Integer.MAX_VALUE)misses++; return false; }
        System.arraycopy(e.values[3],0,px,0,n); System.arraycopy(e.values[4],0,py,0,n);
        System.arraycopy(e.values[5],0,rx,0,n); System.arraycopy(e.values[6],0,ry,0,n); System.arraycopy(e.values[7],0,rz,0,n);
        if(hits<Integer.MAX_VALUE)hits++; return true;
    }
    void capture(RSModel model,int n,int[] x,int[] y,int[] z,int[] px,int[] py,int[] rx,int[] ry,int[] rz) {
        if (!valid(n,x,y,z,px,py,rx,ry,rz)) return;
        Entry e=entries.get(model);
        if(e!=null && e.count!=n) { entries.remove(model); bytes-=e.bytes; e=null; }
        if(e==null) {
            int cost=n*8*4+ENTRY_OVERHEAD;
            if(entries.size()>=MAX_ENTRIES || cost>MAX_BYTES-bytes) { if(admissionRejects<Integer.MAX_VALUE)admissionRejects++; return; }
            e=new Entry(n); entries.put(model,e); bytes+=e.bytes;
        }
        System.arraycopy(x,0,e.values[0],0,n); System.arraycopy(y,0,e.values[1],0,n); System.arraycopy(z,0,e.values[2],0,n);
        System.arraycopy(px,0,e.values[3],0,n); System.arraycopy(py,0,e.values[4],0,n);
        System.arraycopy(rx,0,e.values[5],0,n); System.arraycopy(ry,0,e.values[6],0,n); System.arraycopy(rz,0,e.values[7],0,n);
        e.seen=frame;
    }
    void end() {
        Iterator<Map.Entry<RSModel,Entry>> i=entries.entrySet().iterator();
        while(i.hasNext()) { Entry e=i.next().getValue(); if(e.seen!=frame) { bytes-=e.bytes; i.remove(); } }
    }
    void remove(RSModel model) { Entry e=entries.remove(model); if(e!=null)bytes-=e.bytes; }
    void clear() { entries.clear(); bytes=0; active=false; poseValid=false; stableFrames=0; }
    int[] stats() { return new int[]{hits,misses,entries.size(),bytes,admissionRejects}; }
}
