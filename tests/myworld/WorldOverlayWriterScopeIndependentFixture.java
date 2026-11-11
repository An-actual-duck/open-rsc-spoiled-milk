package orsc.graphics.two;

import java.util.Arrays;

/** Observable lifecycle/ACK obligations, independent of scope implementation fields. */
public final class WorldOverlayWriterScopeIndependentFixture {
    private static final int W=65,H=49;
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static final class Surface {
        final int[] pixels=new int[W*H];final WorldOverlayCoverage coverage=new WorldOverlayCoverage();
        Surface(){fresh();}
        void fresh(){coverage.beginFrame();check(coverage.begin(pixels,W,H),"fresh");}
        WorldOverlayCoverage.Frame frame(){return coverage.borrow(pixels,W,H);}
        WorldOverlayCoverage.Delta plan(){return frame().prepareDeltaTransport(false);}
    }
    private static void rejectedAck(WorldOverlayCoverage.Frame frame,WorldOverlayCoverage.Delta delta){
        try{frame.acknowledgeDeltaTransport(delta);throw new AssertionError("stale ACK accepted");}
        catch(IllegalStateException expected){checks++;}
    }
    private static void invalidTransport(Surface s){check(s.plan()==null,"invalidated scope must force full fallback");}
    private static void bits(WorldOverlayCoverage.Delta delta,boolean[] expected){
        check(delta!=null,"plan exists");int[] work=delta.work();check(work.length>=15,"tile metadata");
        int cols=(W+31)/32;
        for(int tile=0;tile<expected.length;tile++)check((((work[15+tile/16]>>>(tile%16))&1)!=0)==expected[tile],"bounded exact row footprint "+tile);
    }
    private static boolean[] expectedRows(int start,int width,int rows,int step){
        int cols=(W+31)/32;boolean[] expected=new boolean[cols*((H+15)/16)];
        for(int row=0;row<rows;row++)for(int i=start+row*step;i<start+row*step+width;i++)expected[(i/W/16)*cols+i%W/32]=true;
        return expected;
    }
    public static void main(String[] args){
        Surface s=new Surface();int start=15*W+31,width=3;
        float[] t=s.frame().transmittance;float[] before=t.clone();
        check(s.coverage.beginTransportWriter(s.pixels,start,width,3,2*W),"admit clipped interlace rows");
        for(int row=0;row<3;row++)s.coverage.transportWriterRow(s.pixels,start+row*2*W,width);
        s.coverage.endTransportWriter();
        check(Arrays.equals(before,t),"empty glyph transport rows must NEVER fill T");
        WorldOverlayCoverage.Frame frame=s.frame();WorldOverlayCoverage.Delta d=frame.prepareDeltaTransport(false);
        boolean[] footprint=expectedRows(start,width,3,2*W);bits(d,footprint);
        int[] counters=d.writerWork();check(counters[0]==1&&counters[1]==3&&counters[2]==0,"empty-row scope counters");
        counters[0]=99;check(d.writerWork()[0]==1,"counter snapshot caller mutation isolated");
        frame.acknowledgeDeltaTransport(d);s.fresh();d=s.plan();bits(d,footprint);s.frame().acknowledgeDeltaTransport(d);
        s.fresh();bits(s.plan(),new boolean[footprint.length]);

        // Literal T remains pointwise; the bounding row does not close holes.
        s=new Surface();t=s.frame().transmittance;
        check(s.coverage.beginTransportWriter(s.pixels,W+30,5,1,W),"admit sparse row");
        s.coverage.transportWriterRow(s.pixels,W+30,5);s.coverage.opaque(s.pixels,W+30);s.coverage.multiply(s.pixels,W+34,128);
        s.coverage.endTransportWriter();
        for(int i=0;i<t.length;i++)check(t[i]==(i==W+30?0f:i==W+34?0.5f:1f),"sparse T holes "+i);
        d=s.plan();check(d.writerWork()[2]==2,"actual two suppressed marks");bits(d,expectedRows(W+30,5,1,W));

        // Preparing before an entirely transparent glyph cannot remain ACK-able.
        s=new Surface();frame=s.frame();d=frame.prepareDeltaTransport(false);
        check(!s.coverage.beginTransportWriter(s.pixels,0,8,1,W),"pending plan denies even empty writer");
        s.coverage.endTransportWriter();rejectedAck(frame,d);invalidTransport(s);

        for(int event=0;event<11;event++){
            s=new Surface();frame=s.frame();
            check(s.coverage.beginTransportWriter(s.pixels,W+2,4,2,W),"lifecycle admit "+event);
            s.coverage.transportWriterRow(s.pixels,W+2,4);
            switch(event){
                case 0:check(!s.coverage.beginTransportWriter(s.pixels,W+2,4,1,W),"nested denied");break;
                case 1:s.coverage.transportWriterRow(s.pixels,W+3,4);break;
                case 2:s.coverage.transportWriterRow(s.pixels,2*W+2,3);break;
                case 3:s.coverage.opaque(s.pixels,W+20);break;
                case 4:s.coverage.opaque(new int[W*H],W+2);break;
                case 5:s.coverage.opaqueSpan(s.pixels,W+2,W+4);break;
                case 6:s.frame();break;
                case 7:frame.prepareDeltaTransport(false);break;
                case 8:s.coverage.invalidate("unknown raw writer");break;
                case 9:s.coverage.transportWriterRow(new int[W*H],2*W+2,4);break;
                default:s.coverage.multiply(s.pixels,W+2,257);break;
            }
            s.coverage.endTransportWriter();invalidTransport(s);
            s.fresh();s.coverage.opaque(s.pixels,48*W+64);bits(s.plan(),expectedRows(48*W+64,1,1,W));
        }
        // Reset and epoch changes must clear suppression, not leak a trusted row.
        for(int reset=0;reset<3;reset++){
            s=new Surface();check(s.coverage.beginTransportWriter(s.pixels,0,4,1,W),"reset admit");s.coverage.transportWriterRow(s.pixels,0,4);
            if(reset==0)s.coverage.reset(false);else if(reset==1)s.coverage.discard();else s.coverage.beginFrame();
            s.fresh();s.coverage.opaque(s.pixels,48*W+64);s.coverage.endTransportWriter();bits(s.plan(),expectedRows(48*W+64,1,1,W));
        }
        for(int[] shape:new int[][]{{-1,4,1,W},{W-2,4,1,W},{0,4,0,W},{0,4,2,4},{0,4,2,Integer.MAX_VALUE},{W*H-2,4,1,W}}){
            s=new Surface();check(!s.coverage.beginTransportWriter(s.pixels,shape[0],shape[1],shape[2],shape[3]),"unsupported geometry rejected");invalidTransport(s);
        }
        s=new Surface();check(!s.coverage.beginTransportWriter(new int[W*H],0,4,1,W),"foreign scope not admitted");
        bits(s.plan(),new boolean[((W+31)/32)*((H+15)/16)]);
        System.out.println("PASS independent writer scope holes/footprint/ACK/nesting/owner/reset/fallback: "+checks+" checks; CPU only");
    }
}
