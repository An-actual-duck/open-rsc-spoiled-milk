package orsc.graphics.two;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/** Frozen literal writer oracle. The same source runs against baseline and candidate. */
public final class WorldOverlayWriterSpanIndependentFixture {
    private static final int W=97,H=65;
    private static int checks,cases;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private interface Draw { void run(GraphicsController g)throws Exception; }
    private static GraphicsController fresh(){
        GraphicsController g=new GraphicsController(W,H,1);g.setWorldOverlayCoverageEnabled(true);
        g.beginRenderer2DFrame();check(g.beginTransparentWorldOverlay(),"begin");return g;
    }
    private static void glyph(GraphicsController g,boolean aa,byte[] src,int width,int height,int head,int rowStride,int srcHead,int srcStride,boolean foreign)throws Exception{
        int[] dest=foreign?new int[W*H]:g.pixelData;
        Method method=GraphicsController.class.getDeclaredMethod(aa?"plotLetterAntialiased":"plotLetter",
            aa?new Class<?>[]{byte[].class,int.class,int.class,int.class,int.class,int.class,int.class,int[].class,int.class}
                :new Class<?>[]{int.class,int[].class,int.class,int.class,int.class,int.class,int.class,byte[].class,int.class});
        method.setAccessible(true);
        try{
            if(aa)method.invoke(g,src,0x85ca37,width,head,height,srcStride,rowStride,dest,srcHead);
            else method.invoke(g,0x85ca37,dest,head,rowStride,height,width,srcHead,src,srcStride);
        }catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    private static String exception(Throwable t){
        StringBuilder s=new StringBuilder();for(int n=0;t!=null&&n<8;n++,t=t.getCause())s.append(t.getClass().getName()).append(':').append(t.getMessage()).append('|');return s.toString();
    }
    private static void record(String name,Draw op)throws Exception{
        GraphicsController g=fresh();
        // Controlled fixture seeding (not a gameplay raw-writer admission): keep
        // the transport mask empty while supplying nontrivial prior C/T values.
        // Otherwise a full-background draw would make containment checks vacuous.
        Arrays.fill(g.pixelData,0x6c251f);
        Arrays.fill(g.borrowWorldOverlayCoverage().transmittance,0.75f);
        int[] before=g.pixelData.clone();float[] beforeT=g.borrowWorldOverlayCoverage().transmittance.clone();
        String failure="";PrintStream oldOut=System.out,oldErr=System.err;
        ByteArrayOutputStream captured=new ByteArrayOutputStream();PrintStream silence=new PrintStream(captured);
        try{System.setOut(silence);System.setErr(silence);op.run(g);}catch(Exception e){failure=exception(e);}
        finally{System.setOut(oldOut);System.setErr(oldErr);silence.close();}
        WorldOverlayCoverage.Frame frame=g.borrowWorldOverlayCoverage();
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        ByteBuffer words=ByteBuffer.allocate(W*H*8);
        for(int value:g.pixelData)words.putInt(value);
        for(float value:frame.transmittance)words.putInt(Float.floatToRawIntBits(value));
        hash.update(words.array());hash.update((byte)frame.mode);hash.update((byte)(frame.valid?1:0));
        hash.update(failure.getBytes("UTF-8"));
        // Mono's historical catch logs rather than throws. Compare its leading
        // exception line, never source-line-dependent stack trace text.
        String logged=new String(captured.toByteArray(),"UTF-8");
        String first=logged.isEmpty()?"":logged.split("\\R",2)[0];hash.update(first.getBytes("UTF-8"));
        if(frame.valid){
            WorldOverlayCoverage.Delta delta=frame.prepareDeltaTransport(false);
            if(delta!=null){int[] work=delta.work();int[] bands=delta.bands();
                int cols=(W+31)/32;
                for(int i=0;i<before.length;i++)if(before[i]!=g.pixelData[i]||Float.floatToRawIntBits(beforeT[i])!=Float.floatToRawIntBits(frame.transmittance[i])){
                    if(work.length>=15){int tile=(i/W/16)*cols+(i%W/32);check((work[15+tile/16]&(1<<(tile%16)))!=0,"changed pixel missing from transport "+name+" at "+i);}
                    else{boolean covered=false;for(int b=0;b<bands.length;b+=2)covered|=i/W>=bands[b]&&i/W<bands[b+1];check(covered,"changed pixel missing from row transport");}
                }
            }
        }
        System.out.println(name+" "+hex(hash.digest()));cases++;
    }
    private static byte[] pattern(int length,int mode){
        if(length<0)return null;byte[] data=new byte[length];Random r=new Random(0x34592+length+mode);
        int[] values={0,1,30,31,64,128,229,230,255};
        for(int i=0;i<length;i++)data[i]=(byte)(mode==0?0:mode==1?255:values[r.nextInt(values.length)]);return data;
    }
    public static void main(String[] args)throws Exception{
        int id=0;
        for(boolean aa:new boolean[]{false,true})for(int w:new int[]{1,3,4,5,31,33})for(int h:new int[]{1,3})for(int mode:new int[]{0,1,2}){
            final byte[] src=pattern(w*h,mode);final int width=w,height=h;
            record("glyph-"+(id++),g->glyph(g,aa,src,width,height,2*W+31,W-width,0,0,false));
        }
        // Boundaries and intentionally malformed arguments: never preempt the
        // literal writer's partial prefix, swallowed catch or wrapping behavior.
        final int[][] shapes={{8,3,W*2+93,W-8,0,0,24},{8,3,-1,W-8,0,0,24},
            {8,3,W*H-4,W-8,0,0,24},{8,3,2*W,W-8,0,0,9},{8,3,2*W,W-8,-1,0,24},
            {8,3,2*W,W-8,0,-1,24},{8,3,2*W,-9,0,0,24},{8,3,2*W,W-8,0,3,24},
            {0,3,2*W,W,0,0,0},{-1,3,2*W,W+1,0,0,0},{8,0,2*W,W-8,0,0,0},
            {8,-1,2*W,W-8,0,0,0},{8,3,2*W,Integer.MAX_VALUE,0,0,24},
            {8,3,2*W,W-8,0,Integer.MAX_VALUE,24},{8,3,2*W,W-8,0,0,-1}};
        for(boolean aa:new boolean[]{false,true})for(int[] shape:shapes){
            final int[] s=shape;record("malformed-"+(id++),g->glyph(g,aa,pattern(s[6],2),s[0],s[1],s[2],s[3],s[4],s[5],false));
        }
        for(boolean aa:new boolean[]{false,true})record("foreign-"+aa,g->glyph(g,aa,pattern(24,2),8,3,2*W,W-8,0,0,true));
        for(boolean interlace:new boolean[]{false,true})for(int alpha:new int[]{-1,0,1,64,128,255,256,257})
            for(int[] shape:new int[][]{{2,3,9,5},{-4,-3,20,10},{90,60,20,10},{3,3,0,4},{3,3,-2,4},{W,0,9,5}}){
                final int[] s=shape;record("box-"+(id++),g->{g.interlace=interlace;g.setClip(1,W-1,H-1,1);g.drawBoxAlpha(s[0],s[1],s[2],s[3],0xd18539,alpha);});
            }
        record("after-error",g->{try{glyph(g,true,pattern(5,2),8,3,W*2,W-8,0,0,false);}catch(Exception expected){}g.setPixel(80,50,0x3c9f01);});
        System.err.println("PASS frozen writer snapshots cases="+cases+" containmentChecks="+checks+"; CPU only");
    }
}
