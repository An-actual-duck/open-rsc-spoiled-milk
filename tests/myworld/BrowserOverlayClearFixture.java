package orsc.graphics.two;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import orsc.BrowserFrameSinkRegistry;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;

/** Frozen/current coverage and live borrowed-view byte oracle. No copied raster math. */
public final class BrowserOverlayClearFixture {
 static final int W=32,H=24;static int checks,fastProofs;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static WorldOverlayCoverage coverage(GraphicsController g)throws Exception{return (WorldOverlayCoverage)field(g,"worldOverlayCoverage");}
 static void snapshot(DataOutputStream out,GraphicsController g)throws Exception{
  WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();out.writeBoolean(f!=null);if(f==null)return;
  out.writeInt(f.width);out.writeInt(f.height);out.writeBoolean(f.valid);out.writeBoolean(f.transparent);out.writeUTF(f.reason);out.writeLong(f.generation);out.writeLong(f.frameSequence);out.writeInt(f.mode);out.writeInt(f.retainedFadeCount);out.writeBoolean(g.isWorldOverlayCoverageCurrent(f));
  for(int pixel:g.pixelData)out.writeInt(pixel);out.writeBoolean(f.transmittance!=null);if(f.transmittance!=null)for(float t:f.transmittance)out.writeInt(Float.floatToRawIntBits(t));
 }
 static void proof(GraphicsController g,String n,boolean expected)throws Exception{
  if(!Boolean.getBoolean("test.currentClear"))return;
  check(((Boolean)field(coverage(g),n))==expected,"Fast clear proof: "+n);fastProofs++;
 }
 static void start(GraphicsController g){g.beginRenderer2DFrame();check(g.beginTransparentWorldOverlay(),"Begin world failed");}
 interface Draw{void run(GraphicsController g);}
 public static void main(String[]args)throws Exception{
  BrowserFrameSinkRegistry.install(new BrowserFrameSinkRegistry.Sink(){public void present(java.awt.image.BufferedImage b,Renderer2DFrame c,Renderer3DFrame f){}public boolean needsCpuDepthFrame(){return false;}});
  boolean active=GraphicsController.isBrowserFramePreparationEnabled();
  GraphicsController g=new GraphicsController(W,H,1);g.setWorldOverlayCoverageEnabled(true);
  try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){
   start(g);g.drawBoxAlpha(1,1,6,7,0xabcdef,128);g.blackScreen(true);proof(g,"pendingOpaque",active);check(g.beginTransparentWorldOverlay(),"Second begin");proof(g,"lastBeginReusedRgbClear",active);snapshot(out,g);
   // Borrowed same-frame T must update immediately without another borrow.
   start(g);WorldOverlayCoverage.Frame held=g.borrowWorldOverlayCoverage();g.blackScreen(true);proof(g,"pendingOpaque",false);for(float t:held.transmittance)check(t==0f,"Held T did not update immediately");check(g.isWorldOverlayCoverageCurrent(held),"Same-frame clear changed view coherence");for(float t:held.transmittance)out.writeInt(Float.floatToRawIntBits(t));snapshot(out,g);
   // Unborrowed full clear materializes exactly at borrow, preserving mode/tokens.
   start(g);g.blackScreen(true);proof(g,"pendingOpaque",active);snapshot(out,g);proof(g,"pendingOpaque",false);
   Draw[] draws={x->x.drawBox(2,2,4,5,0x123456),x->x.drawBoxAlpha(2,2,4,5,0xabcdef,128),x->x.setPixel(3,3,0),x->x.drawLineHoriz(2,2,5,0xffaacc),x->x.drawVerticalGradient(2,2,5,6,0x123456,0xabcdef),x->x.drawCircle(7,7,3,0x123456,128,0)};
   for(Draw draw:draws){start(g);g.blackScreen(true);draw.run(g);snapshot(out,g);check(g.beginTransparentWorldOverlay(),"Writer begin");proof(g,"lastBeginReusedRgbClear",false);snapshot(out,g);}
   // Direct coverage producers and malformed bounds retain existing exceptions.
   for(int mode=0;mode<5;mode++){
    start(g);g.blackScreen(true);WorldOverlayCoverage c=coverage(g);String error="";
    try{if(mode==0)c.opaque(g.pixelData,-1);if(mode==1)c.opaqueSpan(g.pixelData,-1,3);if(mode==2)c.opaqueSpan(g.pixelData,0,W*H+1);if(mode==3)c.multiply(g.pixelData,-1,128);if(mode==4)c.multiply(g.pixelData,-1,300);}catch(RuntimeException e){error=e.getClass().getName()+":"+e.getMessage();}
    out.writeUTF(error);snapshot(out,g);
   }
   // Retained fades after a complete submitted frame, including repeated fades.
   start(g);g.drawBoxAlpha(1,1,8,9,0x123456,128);snapshot(out,g);g.beginRenderer2DFrame();g.fade2black(0);snapshot(out,g);g.fade2black(0);snapshot(out,g);
   start(g);g.blackScreen(true);g.fade2black(0);snapshot(out,g); // Same-frame clear is still a write, so retained reuse is rejected.
   // Interlace half clear, changed surface, disable/reenable, external invalidation.
   g.interlace=true;start(g);g.drawBox(0,0,W,H,0x667788);g.blackScreen(true);snapshot(out,g);g.blackScreen(false);snapshot(out,g);g.interlace=false;
   start(g);g.blackScreen(true);g.invalidateWorldOverlayCoverage("external writer");Arrays.fill(g.pixelData,0x123456);snapshot(out,g);check(g.beginTransparentWorldOverlay(),"Invalidation reset");proof(g,"lastBeginReusedRgbClear",false);snapshot(out,g);
   start(g);g.blackScreen(true);g.pixelData=new int[W*H];Arrays.fill(g.pixelData,0xabcdef);check(g.beginTransparentWorldOverlay(),"Surface reset");proof(g,"lastBeginReusedRgbClear",false);snapshot(out,g);
   start(g);g.blackScreen(true);g.setWorldOverlayCoverageEnabled(false);snapshot(out,g);g.setWorldOverlayCoverageEnabled(true);check(g.beginTransparentWorldOverlay(),"Enable reset");snapshot(out,g);
   // All maintained raster writer families, interleaved with full clears,
   // held/borrowed views, fresh worlds and invalidation/fade paths.
   java.util.List<WorldOverlayCoverageFixture.Draw> raster=WorldOverlayCoverageFixture.operations();Random random=new Random(41021);
   for(int n=0;n<1500;n++){
    if(n%10==0)start(g);if(n%10==1)g.blackScreen(true);
    String error="";try{raster.get(random.nextInt(raster.size())).run(g);}catch(RuntimeException failure){error=failure.getClass().getName();}out.writeUTF(error);
    if(n%4==0)snapshot(out,g);if(n%33==0){g.fade2black(0);snapshot(out,g);}
   }
   // Switching out of browser ownership leaves a materialized ordinary surface.
   start(g);g.blackScreen(true);BrowserFrameSinkRegistry.install(null);g.beginRenderer2DFrame();g.drawBoxAlpha(1,1,5,6,0x123456,128);snapshot(out,g);
  }finally{BrowserFrameSinkRegistry.install(null);}
  System.out.println("PASS "+checks+" borrowed-view/producer/epoch/fade/exception/surface checks; fast proofs="+fastProofs+"; no GPU/FPS claim");
 }
}
