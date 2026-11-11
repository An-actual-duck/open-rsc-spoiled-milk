package orsc.graphics.two;

import java.util.Arrays;

/** Core transaction/fault proof. Injected JVM backend is explicitly not JNI. */
public final class SegmentedTextCoreIndependentFixture {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Poison extends Error {Poison(String why){super(why);}}
    private static final class Surface implements SegmentedTextRaster.Owner {
        final int w,h;final int[] c;final WorldOverlayCoverage coverage=new WorldOverlayCoverage();int pixelsPerPlot=6;
        final SegmentedTextRaster queue=new SegmentedTextRaster(this);boolean poisoned;int poisonCalls,commits,replays;
        Surface(){this(16,12);}Surface(int w,int h){this.w=w;this.h=h;c=new int[w*h];fresh();}
        void fresh(){poisoned=false;coverage.beginFrame();check(coverage.begin(c,w,h),"fresh");}
        void prefill(){Arrays.fill(c,0x395781);for(int i=0;i<c.length;i++)coverage.multiply(c,i,128);}
        public boolean eligible(){return !poisoned;}
        public WorldOverlayCoverage.Frame frame(){return coverage.observeFrame();}
        public boolean current(WorldOverlayCoverage.Frame f){return !poisoned&&coverage.isCurrent(f,c,w,h);}
        public void committed(WorldOverlayCoverage.Frame f,boolean wrote){check(current(f),"commit current owner");commits++;}
        public void poison(String why){poisoned=true;poisonCalls++;coverage.invalidate(why);throw new Poison(why);}
        SegmentedTextRaster.Replay replay(){return (font,plots,l,t,r,b)->{
            replays++;check(l==0&&t==0&&r==w&&b==h,"saved clip");
            int[] header={1,w,h,1,plots.length/5,font.length,plots.length/5*pixelsPerPlot};
            SegmentedTextPacketReference.raster(header,new int[]{0,font.length,0,plots.length/5,l,t,r,b},plots,font,c,frame().transmittance,frame().generation,frame().frameSequence);
        };}
    }
    private static byte[] font(){byte[] f=new byte[15];f[2]=9;f[3]=3;f[4]=2;byte[] data={0,31,64,(byte)128,(byte)230,(byte)255};System.arraycopy(data,0,f,9,6);return f;}
    private static int[] plot(int n){return new int[]{1+n%3,1+n%2,0x8152c6+n,0,1};}
    private static boolean record(Surface s,byte[] f,int[] p){return s.queue.record(f,p,p.length/5,0,0,s.w,s.h,s.replay());}
    private static void expected(int[] c,float[] t,byte[] f,int count){for(int n=0;n<count;n++)SegmentedTextPacketReference.raster(new int[]{1,16,12,1,1,f.length,6},new int[]{0,f.length,0,1,0,0,16,12},plot(n),f,c,t,1,1);}
    private static void same(Surface s,int[] c,float[] t){check(Arrays.equals(s.c,c),"complete arbitrary prior C");float[] actual=s.frame().transmittance;for(int i=0;i<t.length;i++)check(Float.floatToRawIntBits(actual[i])==Float.floatToRawIntBits(t[i]),"complete raw T "+i);}
    public static void main(String[] args){
        final int[] nativeCalls={0};SegmentedTextRaster.install((h,x,p,f,c,t,g,s)->{nativeCalls[0]++;return SegmentedTextPacketReference.raster(h,x,p,f,c,t,g,s);});
        Surface s=new Surface();s.prefill();byte[] source=font(),captured=source.clone();int[] before=s.c.clone(),want=s.c.clone();float[] beforeT=s.frame().transmittance.clone(),wantT=beforeT.clone();
        for(int n=0;n<9;n++){int[] p=plot(n);check(record(s,source,p),"accepted queue");p[0]=999;}
        check(s.queue.pendingTexts()==9&&Arrays.equals(s.c,before)&&Arrays.equals(s.frame().transmittance,beforeT),"queued but no early writes");
        Arrays.fill(source,9,source.length,(byte)0);expected(want,wantT,captured,9);s.queue.flush();same(s,want,wantT);
        check(nativeCalls[0]==1&&s.commits==1&&s.replays==0&&s.queue.pendingTexts()==0,"one accepted native batch");
        s.queue.flush();check(nativeCalls[0]==1,"empty flush no retry");

        // A zero-write rejection may corrupt its packet copies, never fallback
        // snapshots. Saved font/scalars must replay once over existing C/T.
        SegmentedTextRaster.install((h,x,p,f,c,t,g,q)->{nativeCalls[0]++;Arrays.fill(f,(byte)0);Arrays.fill(p,0);return 0;});
        s=new Surface();s.prefill();source=font();want=s.c.clone();wantT=s.frame().transmittance.clone();expected(want,wantT,source,9);
        for(int n=0;n<9;n++)check(record(s,source,plot(n)),"rejected packet queue");Arrays.fill(source,(byte)0);int calls=nativeCalls[0];s.queue.flush();same(s,want,wantT);
        check(nativeCalls[0]==calls+1&&s.replays==9&&s.queue.pendingTexts()==0,"captured fallback once");s.queue.flush();check(s.replays==9,"fallback not retried");

        // Post-write failure cannot silently replay over partial native work.
        SegmentedTextRaster.install((h,x,p,f,c,t,g,q)->{nativeCalls[0]++;c[0]=0x765432;t[0]=0f;return -1;});
        s=new Surface();for(int n=0;n<9;n++)record(s,font(),plot(n));calls=nativeCalls[0];
        try{s.queue.flush();throw new AssertionError("missing poison");}catch(Poison expected){checks++;}
        check(s.poisoned&&s.poisonCalls==1&&s.replays==0&&s.queue.pendingTexts()==0,"partial-write poison clears queue without fallback");
        check(!s.frame().valid,"partial frame not publishable");s.queue.flush();check(nativeCalls[0]==calls+1&&s.poisonCalls==1,"fault not retried");
        s.fresh();check(s.frame().valid&&s.c[0]==0&&s.frame().transmittance[0]==1f,"fresh clear recovers exact base");
        SegmentedTextRaster.install((h,x,p,f,c,t,g,q)->SegmentedTextPacketReference.raster(h,x,p,f,c,t,g,q));
        source=font();want=s.c.clone();wantT=s.frame().transmittance.clone();expected(want,wantT,source,9);for(int n=0;n<9;n++)record(s,source,plot(n));s.queue.flush();same(s,want,wantT);check(!s.poisoned,"fresh accepted recovery");

        // Unsupported current input materializes earlier valid work, then lets
        // the original current writer preserve its own error/no-op behavior.
        s=new Surface();source=font();for(int n=0;n<9;n++)record(s,source,plot(n));check(!record(s,new byte[2],plot(0)),"malformed current source not queued");check(s.queue.pendingTexts()==0&&s.commits==1,"earlier batch materialized before literal current path");
        s=new Surface();for(int n=0;n<9;n++)record(s,font(),plot(n));s.coverage.beginFrame();
        try{s.queue.flush();throw new AssertionError("stale owner accepted");}catch(Poison expected){checks++;}check(s.queue.pendingTexts()==0&&s.poisonCalls==1,"lost epoch cannot write replacement surface");

        // If literal replay itself fails after a prefix, future flushes must not
        // replay that prefix again or permit publication of a partial frame.
        SegmentedTextRaster.install((h,x,p,f,c,t,g,q)->0);s=new Surface();final Surface failed=s;final int[] replayed={0};
        for(int n=0;n<9;n++)check(s.queue.record(font(),plot(n),1,0,0,s.w,s.h,(f,p,l,t,r,b)->{replayed[0]++;failed.c[0]++;throw new IllegalStateException("injected literal fallback failure");}),"fault replay queued");
        try{s.queue.flush();throw new AssertionError("fallback error swallowed");}catch(Poison expected){checks++;}
        check(s.poisoned&&s.queue.pendingTexts()==0&&replayed[0]==1,"failed literal prefix retired");s.queue.flush();check(replayed[0]==1,"failed literal prefix never retried");

        // Each queue cap materializes the old prefix before admitting more;
        // all-zero sources make accepted status1 truthful without another raster.
        final int[] max={0,0,0,0};SegmentedTextRaster.install((h,x,p,f,c,t,g,q)->{check(h[3]<=256&&h[4]<=4096&&h[5]<=8388608&&h[6]<=2097152,"every submitted cap bounded");max[0]=Math.max(max[0],h[3]);max[1]=Math.max(max[1],h[4]);max[2]=Math.max(max[2],h[5]);max[3]=Math.max(max[3],h[6]);return 1;});
        s=new Surface();byte[] zero=font();Arrays.fill(zero,9,zero.length,(byte)0);for(int n=0;n<257;n++)check(record(s,zero,plot(0)),"TEXT cap admits after flushing");check(s.queue.pendingTexts()==1&&max[0]==256,"TEXT cap old prefix materialized");s.queue.flush();
        s=new Surface();int[] many=new int[512*5];for(int i=0;i<512;i++)System.arraycopy(plot(0),0,many,i*5,5);for(int n=0;n<8;n++)check(record(s,zero,many),"glyph group");check(record(s,zero,plot(0))&&s.queue.pendingTexts()==1&&max[1]==4096,"glyph cap exact prefix materialized");s.queue.flush();
        s=new Surface();byte[] largeFont=new byte[1048576];System.arraycopy(zero,0,largeFont,0,zero.length);for(int n=0;n<9;n++)check(record(s,largeFont,plot(0)),"font cap group");check(s.queue.pendingTexts()==1&&max[2]==8388608,"font byte cap exact prefix materialized");s.queue.flush();
        check(!record(s,new byte[8388609],plot(0))&&s.queue.pendingTexts()==0,"single oversized font declines original path");
        s=new Surface(256,256);s.pixelsPerPlot=127*127;byte[] broad=new byte[9+127*127];broad[2]=9;broad[3]=127;broad[4]=127;for(int n=0;n<131;n++)check(record(s,broad,plot(0)),"work cap group");check(s.queue.pendingTexts()==1&&max[3]==130*127*127,"work cap bounded prefix materialized");s.queue.flush();
        SegmentedTextRaster.install(null);System.out.println("PASS independent segmented producer arbitrary C/raw-T, immutable capture, native rejection, literal-once and poison/fresh recovery: "+checks+" checks; JVM injection only");
    }
}
