package orsc.graphics.two;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import orsc.graphics.Renderer2DFrame;

/** Same full literal RGB/T/error corpus against frozen and observed Java writers. */
public final class OverlayJournalRenderingIndependentFixture {
    private interface Draw {void run(GraphicsController g)throws Exception;}
    private static void fonts(boolean aa){
        int size=Fonts.inputFilterChars.length()*9;byte[] font=new byte[size+6];
        for(int i=0;i<size;i+=9){font[i]=(byte)(size>>14);font[i+1]=(byte)((size>>7)&127);font[i+2]=(byte)(size&127);font[i+3]=2;font[i+4]=3;font[i+6]=3;font[i+7]=3;}
        byte[] values={0,31,64,(byte)128,(byte)230,(byte)255};System.arraycopy(values,0,font,size,6);
        for(int i=0;i<Fonts.fontData.length;i++)Fonts.fontData[i]=font;
        for(int i=0;i<Fonts.fontAntiAliased.length;i++)Fonts.fontAntiAliased[i]=aa;
    }
    private static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format("%02x",b&255));return s.toString();}
    private static String exception(Throwable e){StringBuilder s=new StringBuilder();for(int i=0;e!=null&&i<8;i++,e=e.getCause())s.append(e.getClass().getName()).append(':').append(e.getMessage()).append('|');return s.toString();}
    private static void snapshot(MessageDigest hash,GraphicsController g)throws Exception{
        ByteBuffer bytes=ByteBuffer.allocate(g.pixelData.length*4);for(int c:g.pixelData)bytes.putInt(c);hash.update(bytes.array());
        WorldOverlayCoverage.Frame frame=g.borrowWorldOverlayCoverage();
        if(frame==null){hash.update((byte)99);return;}
        hash.update((byte)frame.mode);hash.update((byte)(frame.valid?1:0));
        if(frame.transmittance!=null){bytes=ByteBuffer.allocate(frame.transmittance.length*4);for(float t:frame.transmittance)bytes.putInt(Float.floatToRawIntBits(t));hash.update(bytes.array());}
    }
    public static void main(String[] args)throws Exception{
        List<Draw> draws=new ArrayList<Draw>();for(WorldOverlayCoverageFixture.Draw op:WorldOverlayCoverageFixture.operations())draws.add(g->op.run(g));
        for(boolean aa:new boolean[]{false,true}){
            draws.add(g->{fonts(aa);g.loggedIn=true;g.drawColoredString(-1,10,"A@red@Bc",0,0xabcdef,0);g.drawColoredStringCentered(10,"ABC",0xffffff,0,0,18);});
            draws.add(g->{fonts(aa);g.drawColoredString(2,10,"ABC",0,0x134687,0);Fonts.fontData[0][Fonts.fontData[0].length-1]=0;g.drawBoxAlpha(1,8,7,3,0x778899,128);g.drawColoredString(2,10,"ABC",0,0x5588ff,0);});
            draws.add(g->{fonts(aa);Fonts.fontData[0]=null;g.drawColoredString(2,10,"AB",0,1,0);g.setPixel(1,1,0x123456);});
            draws.add(g->{fonts(aa);Fonts.fontData[0]=new byte[9];g.drawColoredString(2,10,"AB",0,1,0);});
            draws.add(g->{fonts(aa);StringBuilder text=new StringBuilder();for(int i=0;i<4200;i++)text.append('A');g.drawColoredString(1,10,text.toString(),0,0x554433,0);});
            draws.add(g->{fonts(aa);for(int i=0;i<270;i++)g.drawColoredString(1,10,"A",0,0x554433,0);});
        }
        draws.add(g->{g.drawBox(1,1,5,3,0x112233);g.beginRenderer2DFrame();g.fade2black(0);g.drawBoxAlpha(2,3,4,3,0x223344,128);});
        draws.add(g->{g.drawBox(1,1,5,3,0x112233);g.beginOpaqueWorldOverlay();g.drawLineHoriz(1,2,8,0xffffff);});
        draws.add(g->{g.drawBox(1,1,5,3,0x112233);g.resize(17,19);g.beginRenderer2DFrame();g.beginTransparentWorldOverlay();g.drawBox(1,1,4,5,0xabcdef);});
        draws.add(g->{for(int i=0;i<4100;i++)g.drawBox(1,1,1,1,i);g.drawBox(8,1,1,1,0xff00ff);});
        draws.add(g->{g.invalidateWorldOverlayCoverage("external fixture raw writer");g.pixelData[0]=0x123456;});
        draws.add(g->{
            g.drawBox(1,1,3,3,0x223355);
            // Test-owned corruption simulates a partially failed journal buffer.
            // The actual GraphicsController catch/retirement path must still run
            // the original following writers, once, without altered exceptions.
            try{
                java.lang.reflect.Field field=GraphicsController.class.getDeclaredField("overlayJournal");field.setAccessible(true);Object observer=field.get(g);
                if(observer!=null){java.lang.reflect.Field ops=observer.getClass().getDeclaredField("ops");ops.setAccessible(true);if(ops.get(observer)!=null)ops.set(observer,null);}
            }catch(NoSuchFieldException baseline){}
            g.drawBoxAlpha(2,2,4,5,0x776655,128);fonts(false);g.drawColoredString(1,10,"ABC",0,0xffffff,0);
        });
        MessageDigest hash=MessageDigest.getInstance("SHA-256");int cases=0;
        for(boolean transparent:new boolean[]{false,true})for(boolean interlace:new boolean[]{false,true})for(boolean clip:new boolean[]{false,true})for(Draw draw:draws){
            GraphicsController g=new GraphicsController(40,32,1);g.setWorldOverlayCoverageEnabled(true);g.beginRenderer2DFrame();
            if(transparent)g.beginTransparentWorldOverlay();else g.beginOpaqueWorldOverlay();
            g.setRenderer2DPhase(Renderer2DFrame.Phase.UI_OVERLAY);g.interlace=interlace;if(clip)g.setClip(2,37,30,1);
            PrintStream oldOut=System.out,oldErr=System.err;ByteArrayOutputStream captured=new ByteArrayOutputStream();PrintStream silence=new PrintStream(captured);
            try{System.setOut(silence);System.setErr(silence);draw.run(g);}catch(Exception e){hash.update(exception(e).getBytes("UTF-8"));}
            finally{System.setOut(oldOut);System.setErr(oldErr);silence.close();}
            String logged=new String(captured.toByteArray(),"UTF-8");for(String line:logged.split("\\R"))if(line.startsWith("drawstring:")||line.startsWith("plotletter:"))hash.update(line.getBytes("UTF-8"));
            snapshot(hash,g);g.drawLineVert(35,2,0x735193,20);snapshot(hash,g);cases++;
        }
        System.out.println("SNAPSHOT "+hex(hash.digest()));System.err.println("PASS frozen journal writer C/raw-T and exception corpus cases="+cases+"; CPU only");
    }
}
