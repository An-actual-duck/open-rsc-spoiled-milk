package orsc.graphics.two;

import java.lang.reflect.Method;
import orsc.graphics.Renderer2DFrame;

/** Actual GC publication/recovery route; no mudclient/GPU/pool ownership claim. */
public final class SegmentedTextPoisonIndependentFixture {
    private static int checks,calls;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static void queue(GraphicsController g){for(int n=0;n<9;n++)g.drawColoredString(1,10,"ABC",0,0x8152c6,0);check(g.hasPendingSegmentedText(),"actual GC queued TEXT");}
    private static void abort(GraphicsController g){try{g.flushPendingSegmentedText();throw new AssertionError("native fault did not abort");}catch(SegmentedTextRaster.FrameAbort expected){checks++;}}
    private static void blocked(GraphicsController g){try{WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();check(f!=null&&!f.valid,"poison cannot publish a valid or implicit opaque borrowed image");}catch(SegmentedTextRaster.FrameAbort expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Method font=OverlayJournalRenderingIndependentFixture.class.getDeclaredMethod("fonts",boolean.class);font.setAccessible(true);font.invoke(null,true);
        SegmentedTextRaster.install((h,x,p,f,c,t,g,s)->{calls++;c[0]=0x765432;t[0]=0f;return -1;});
        GraphicsController g=new GraphicsController(40,32,1);g.setWorldOverlayCoverageEnabled(true);g.beginRenderer2DFrame();g.setRenderer2DPhase(Renderer2DFrame.Phase.UI_OVERLAY);check(g.beginTransparentWorldOverlay(),"fresh GC owner");g.drawBoxAlpha(0,0,40,32,0x456789,128);
        queue(g);abort(g);check(g.isSegmentedTextPoisoned()&&!g.hasPendingSegmentedText()&&calls==1,"poison clears captured work");blocked(g);
        try{g.flushPendingSegmentedText();}catch(SegmentedTextRaster.FrameAbort expected){}check(calls==1,"poison does not retry callback");
        int width=g.width2;g.width2=0;try{check(!g.beginTransparentWorldOverlay(),"invalid fresh begin rejected");}finally{g.width2=width;}
        check(g.isSegmentedTextPoisoned(),"failed clear cannot reset poison");blocked(g);
        // This implementation permits opaque recovery ONLY by explicitly clearing
        // the entire actual C image; a metadata-only reset would fail these checks.
        g.beginOpaqueWorldOverlay();check(!g.isSegmentedTextPoisoned(),"complete opaque clear recovery");for(int c:g.pixelData)check(c==0,"opaque recovery clears partial native C");
        WorldOverlayCoverage.Frame opaque=g.borrowWorldOverlayCoverage();check(opaque.valid&&!opaque.transparent&&opaque.transmittance==null,"opaque image truly complete");
        g.beginRenderer2DFrame();check(g.beginTransparentWorldOverlay(),"fresh after opaque");queue(g);abort(g);blocked(g);check(calls==2,"second fault remains independent");
        g.beginRenderer2DFrame();check(!g.isSegmentedTextPoisoned(),"next frame actually clears poisoned base");WorldOverlayCoverage.Frame clear=g.borrowWorldOverlayCoverage();for(int c:g.pixelData)check(c==0,"fresh frame clear C");for(float t:clear.transmittance)check(t==1f,"fresh frame clear T");
        SegmentedTextRaster.install((h,x,p,f,c,t,gen,seq)->{calls++;return SegmentedTextPacketReference.raster(h,x,p,f,c,t,gen,seq);});
        check(g.beginTransparentWorldOverlay(),"successful actual fresh begin");queue(g);g.flushPendingSegmentedText();check(!g.isSegmentedTextPoisoned()&&g.borrowWorldOverlayCoverage().valid&&!g.hasPendingSegmentedText()&&calls==3,"later successful native frame recovers");
        SegmentedTextRaster.install(null);System.out.println("PASS actual-GC queued/poison/publication-block/failed-clear/complete-opaque-clear/fresh-recovery "+checks+" checks; injected JVM backend only");
    }
}
