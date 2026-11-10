package orsc.graphics.three;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import orsc.BrowserFrameSinkRegistry;
import orsc.BrowserTelemetryData;
import orsc.RenderTelemetry;
/** Real Scene path, camera/material mutation, actor anchors, picks, clipping and CPU pixels. */
public final class BrowserScenePreparationFixture {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void text(MessageDigest hash,String value)throws Exception{hash.update(value.getBytes("UTF-8"));hash.update((byte)0);}
 static long stat(String name,String member)throws Exception{
  java.lang.reflect.Field f=RenderTelemetry.class.getDeclaredField(name);f.setAccessible(true);Object stats=f.get(null);
  java.lang.reflect.Field value=stats.getClass().getDeclaredField(member);value.setAccessible(true);return value.getLong(stats);
 }
 public static void main(String[] args)throws Exception {
  boolean current=args[0].equals("current"),profiling=RenderTelemetry.isEnabled();
  for(String name:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster","browserEarlyFaceCull","browserOverlayCoverage"})System.setProperty("spoiledmilk."+name,"true");
  System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
  MessageDigest hash=MessageDigest.getInstance("SHA-256");
  for(int mode=0;mode<4;mode++){
   BrowserFrameSinkRegistry.install(null);
   if(mode<3)BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(mode==2));
   BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(true);
   for(int n=0;n<64;n++){
    BrowserWallRoofOwnershipFixture.Result value=view.draw(n%3==0?0:256,n%3==0?0:192,
     new int[]{20,100,600}[n%3],new int[]{640,768,900}[n%3],n%2*256,
     new int[]{-12345,0,Scene.TRANSPARENT}[n%3],mode==1||n%7==0);
    text(hash,value.picks);text(hash,value.anchors);text(hash,value.faces);text(hash,value.resident);
    ByteBuffer pixels=ByteBuffer.allocate(value.pixels.length*4);for(int p:value.pixels)pixels.putInt(p);hash.update(pixels.array());
   }
  }
  BrowserFrameSinkRegistry.install(null);
  if(current){
   String[] stages={"sceneLegacyDrawSampleStats","scenePolygonSortSampleStats","sceneSpriteCaptureSampleStats","sceneCoverageInitSampleStats","sceneSpriteCaptureSampleCountStats","sceneProjectedIterationSampleCountStats"};
   for(String stage:stages)check(stat(stage,"count")==(profiling?8:0),"exact 1/32 sampling or disabled no-op failed: "+stage);
   if(profiling){String[] names=BrowserTelemetryData.names().split("\\|");check(names.length<=32,"wire schema bound");double[] values=BrowserTelemetryData.snapshot();for(String stage:stages){int at=Arrays.asList(names).indexOf(stage);check(at>=0&&values[at*2]==8,"missing sampled export "+stage);}int actor=Arrays.asList(names).indexOf("sceneSpriteCaptureSampleCountStats");check(values[actor*2+1]>0,"fixture never captured actor anchors");check(stat("sceneLegacyDrawSampleStats","total")>=stat("scenePolygonSortSampleStats","total")+stat("sceneSpriteCaptureSampleStats","total"),"sampled intervals overlap or escape paired legacy total");}
   else check(RenderTelemetry.now()==0L,"production clocks not disabled");
  }
  StringBuilder result=new StringBuilder();for(byte b:hash.digest())result.append(String.format("%02x",b&255));
  System.out.println(result);
 }
}
