package orsc.graphics.two;

import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Literal software writers and transport state oracle, without JNI/GPU. */
public final class WorldOverlayDeltaTransportFixture {
    static final int W=96,H=128;
    static int checks,partialFrames,fullFrames,serial;
    static long uploadedRows,equivalentRows;
    static int[] gpuRgb;static float[] gpuT;static int gw,gh;
    static List<String> exported=new ArrayList<String>();static Path export;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static GraphicsController graphics(int w,int h){GraphicsController g=new GraphicsController(w,h,1);g.setWorldOverlayCoverageEnabled(true);return g;}
    static void fresh(GraphicsController g){g.beginRenderer2DFrame();check(g.beginTransparentWorldOverlay(),"fresh coverage");}
    static void fail(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalStateException expected){checks++;}}
    static void binary(String name,int[] data)throws Exception{ByteBuffer b=ByteBuffer.allocate(data.length*4).order(ByteOrder.LITTLE_ENDIAN);for(int value:data)b.putInt(value);Files.write(export.resolve(name),b.array());}
    static void binary(String name,float[] data)throws Exception{ByteBuffer b=ByteBuffer.allocate(data.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float value:data)b.putFloat(value);Files.write(export.resolve(name),b.array());}
    static void submit(GraphicsController g,String name,boolean reset)throws Exception{
        WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();check(f.valid,"invalid raster passed to transport "+name);
        WorldOverlayCoverage.Delta d=f.prepareDeltaTransport(reset);
        int[] bands=d==null||!d.partial?new int[]{0,f.height}:d.bands();
        if(gpuRgb==null||gw!=f.width||gh!=f.height){gpuRgb=new int[f.width*f.height];gpuT=new float[gpuRgb.length];gw=f.width;gh=f.height;}
        for(int n=0;n<bands.length;n+=2){int from=bands[n]*gw,to=bands[n+1]*gw;System.arraycopy(f.ownerPixels,from,gpuRgb,from,to-from);if(f.transmittance!=null)System.arraycopy(f.transmittance,from,gpuT,from,to-from);}
        check(Arrays.equals(gpuRgb,f.ownerPixels),"RGB stale outside submitted bands "+name);
        if(f.transmittance!=null)check(Arrays.equals(gpuT,f.transmittance),"T stale outside submitted bands "+name);
        if(d!=null){int[] work=d.work();check(work.length==6&&work[2]<=f.height,"bounded work shape");int rows=0;for(int n=0;n<bands.length;n+=2)rows+=bands[n+1]-bands[n];check(work[3]==rows,"upload rows receipt");uploadedRows+=rows;equivalentRows+=f.height;if(d.partial)partialFrames++;else fullFrames++;}
        if(export!=null){String id=String.format("%03d",serial++);binary(id+"-rgb.bin",f.ownerPixels);if(f.transmittance!=null)binary(id+"-t.bin",f.transmittance);
            exported.add("{\"name\":\""+name+"\",\"id\":\""+id+"\",\"width\":"+f.width+",\"height\":"+f.height+",\"mode\":"+f.mode+",\"generation\":"+f.generation+",\"frameSequence\":"+f.frameSequence+",\"fades\":"+f.retainedFadeCount+",\"reset\":"+reset+",\"delta\":"+(d!=null)+",\"partial\":"+(d!=null&&d.partial)+",\"baseGeneration\":"+(d==null?-1:d.baseGeneration)+",\"baseFrameSequence\":"+(d==null?-1:d.baseFrameSequence)+",\"bands\":"+Arrays.toString(d==null?new int[0]:d.bands())+",\"work\":"+Arrays.toString(d==null?new int[0]:d.work())+"}");
        }
        if(d!=null)f.acknowledgeDeltaTransport(d);
    }
    static void sequence()throws Exception{
        System.setProperty("spoiledmilk.browserOverlayDeltaTransport","true");GraphicsController g=graphics(W,H);
        fresh(g);g.drawBox(2,2,10,4,0x123456);WorldOverlayCoverage.Frame first=g.borrowWorldOverlayCoverage();WorldOverlayCoverage.Delta fd=first.prepareDeltaTransport(true);
        check(fd!=null&&!fd.partial&&fd.work()[2]==16,"First full lost current touched rows");submit(g,"first-world",true);
        fresh(g);WorldOverlayCoverage.Delta clear=g.borrowWorldOverlayCoverage().prepareDeltaTransport(false);check(clear.partial&&Arrays.equals(clear.bands(),new int[]{0,16}),"Disappearing UI did not clear previous submitted touch");submit(g,"disappearing-ui",false);
        fresh(g);WorldOverlayCoverage.Delta empty=g.borrowWorldOverlayCoverage().prepareDeltaTransport(false);check(empty.partial&&empty.bands().length==0,"Empty stable overlay not zero transport");submit(g,"zero-touch",false);
        for(int n=0;n<20;n++){fresh(g);int y=(n%4)*24;g.drawBoxAlpha(1,y,20,9,0xffee11,128);g.drawLineVert(25,y,0x123456,12);g.setPixel(50,120-n%16,0x000000);submit(g,"moving-vfx-"+n,false);}
        // Skipped source frame, including a borrowed but never submitted plan,
        // must not advance the GPU history used by the following clear.
        fresh(g);g.drawBox(0,96,20,10,0xffffff);WorldOverlayCoverage.Frame skipped=g.borrowWorldOverlayCoverage();skipped.prepareDeltaTransport(false);
        fresh(g);g.drawBox(0,32,20,4,0x444444);submit(g,"after-held-source",false);
        fresh(g);g.drawBox(0,0,W,H,0x111111);WorldOverlayCoverage.Delta dense=g.borrowWorldOverlayCoverage().prepareDeltaTransport(false);check(!dense.partial,"Broad dirty region did not choose full fallback");submit(g,"broad-full",false);
        fresh(g);submit(g,"clear-broad-full",false);fresh(g);submit(g,"after-broad-cleared",false);
        // Every existing valid pointwise writer participates in the same hooks.
        int op=0;for(WorldOverlayCoverageFixture.Draw draw:WorldOverlayCoverageFixture.operations()){
            fresh(g);draw.run(g);WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();
            if(f.valid)submit(g,"writer-"+(op++),false);else check(f.prepareDeltaTransport(false)==null,"Invalid writer retained delta history");
        }
        fresh(g);submit(g,"before-retained",false);g.beginRenderer2DFrame();g.fade2black(0);g.drawBoxAlpha(0,80,20,10,0x33aa66,128);submit(g,"retained-fade-full",false);
        g.beginRenderer2DFrame();submit(g,"retained-base-full",false);fresh(g);g.drawBox(0,24,20,10,0x775533);check(!g.borrowWorldOverlayCoverage().prepareDeltaTransport(false).partial,"Retained mode did not invalidate history");submit(g,"fresh-after-retained",false);
        g.beginOpaqueWorldOverlay();Arrays.fill(g.pixelData,0x333333);submit(g,"opaque-full",false);fresh(g);check(!g.borrowWorldOverlayCoverage().prepareDeltaTransport(false).partial,"Opaque did not invalidate history");submit(g,"fresh-after-opaque",false);
        // Same dimensions with a new pixel owner and a new coverage generation.
        g=graphics(W,H);fresh(g);g.setPixel(1,1,0);submit(g,"new-owner-same-size",true);
        g=graphics(64,96);fresh(g);g.drawBoxAlpha(0,80,20,10,0x334477,64);submit(g,"resized-owner",true);
        fresh(g);submit(g,"resized-clear",false);
        g=graphics(64,256);fresh(g);submit(g,"fragment-owner",true);
        fresh(g);for(int y=0;y<160;y+=32)g.setPixel(1,y,0x997755);
        check(!g.borrowWorldOverlayCoverage().prepareDeltaTransport(false).partial,"More than four bands did not choose full fallback");submit(g,"fragment-full",false);
        fresh(g);submit(g,"fragment-clear-full",false);fresh(g);submit(g,"fragment-cleared-empty",false);
        check(partialFrames>10&&fullFrames>3&&uploadedRows<equivalentRows,"Vacuous transport sequence");
        if(export!=null)Files.write(export.resolve("manifest.json"),("{\"schema\":1,\"scope\":\"Literal software RGB/T planes and owned transport metadata; GPU comparison pending\",\"frames\":["+String.join(",",exported)+"]}\n").getBytes("UTF-8"));
    }
    static void acknowledgements()throws Exception{
        System.setProperty("spoiledmilk.browserOverlayDeltaTransport","true");GraphicsController g=graphics(W,H);fresh(g);g.setPixel(1,1,0xffffff);
        WorldOverlayCoverage.Frame a=g.borrowWorldOverlayCoverage();WorldOverlayCoverage.Delta da=a.prepareDeltaTransport(false);a.acknowledgeDeltaTransport(da);fail(()->a.acknowledgeDeltaTransport(da),"Duplicate ACK accepted");
        fresh(g);WorldOverlayCoverage.Frame b=g.borrowWorldOverlayCoverage();WorldOverlayCoverage.Delta db=b.prepareDeltaTransport(false);fresh(g);fail(()->b.acknowledgeDeltaTransport(db),"Out-of-order ACK accepted");
        fresh(g);WorldOverlayCoverage.Frame c=g.borrowWorldOverlayCoverage();WorldOverlayCoverage.Delta dc=c.prepareDeltaTransport(false);g.setPixel(2,2,0x123456);fail(()->c.acknowledgeDeltaTransport(dc),"Post-borrow mutation ACK accepted");
        fresh(g);g.setPixel(1,1,0xffffff);WorldOverlayCoverage.Frame d=g.borrowWorldOverlayCoverage();WorldOverlayCoverage.Delta old=d.prepareDeltaTransport(false);WorldOverlayCoverage.Delta latest=d.prepareDeltaTransport(false);check(old!=latest,"Plan ownership is not distinct");fail(()->d.acknowledgeDeltaTransport(old),"Superseded plan ACK accepted");
        fresh(g);WorldOverlayCoverage.Frame e=g.borrowWorldOverlayCoverage();WorldOverlayCoverage.Delta de=e.prepareDeltaTransport(false);g.clearWorldOverlayCoverage();fail(()->e.acknowledgeDeltaTransport(de),"Discarded owner ACK accepted");
        fresh(g);g.invalidateWorldOverlayCoverage("unknown raw writer");check(!g.borrowWorldOverlayCoverage().valid&&g.borrowWorldOverlayCoverage().prepareDeltaTransport(false)==null,"Unknown writer stayed eligible");
        fresh(g);check(!g.borrowWorldOverlayCoverage().prepareDeltaTransport(false).partial,"Invalidation did not force full");
    }
    static void literal(String policy)throws Exception{
        System.setProperty("spoiledmilk.browserOverlayDeltaTransport",policy);MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(WorldOverlayCoverageFixture.Draw op:WorldOverlayCoverageFixture.operations())for(boolean interlace:new boolean[]{false,true}){
            GraphicsController g=graphics(W,H);g.interlace=interlace;fresh(g);op.run(g);WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();
            ByteBuffer b=ByteBuffer.allocate(g.pixelData.length*8);for(int x:g.pixelData)b.putInt(x);if(f.transmittance!=null)for(float t:f.transmittance)b.putFloat(t);hash.update(b.array());hash.update((byte)f.mode);hash.update((byte)(f.valid?1:0));
        }
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));System.out.println(out);
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("literal")){literal(args[1]);return;}
        if(args.length>1){export=Paths.get(args[1]);Files.createDirectories(export);}
        sequence();acknowledgements();System.out.println("PASS "+checks+" overlay history/planes/ACK checks; partialFrames="+partialFrames+" fullFrames="+fullFrames+" uploadedRows="+uploadedRows+" equivalentRows="+equivalentRows);
    }
}
