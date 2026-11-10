package orsc;
import java.io.*;
import java.nio.file.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
/** CPU export of maintained native sky data, without any GL/native window. */
public final class BrowserOwnedSkyOracle {
 static void floats(Path p,float[] a)throws Exception{try(DataOutputStream o=new DataOutputStream(Files.newOutputStream(p))){for(float n:a)o.writeInt(Integer.reverseBytes(Float.floatToRawIntBits(n)));}}
 static void ints(Path p,int[] a)throws Exception{try(DataOutputStream o=new DataOutputStream(Files.newOutputStream(p))){for(int n:a)o.writeInt(Integer.reverseBytes(n));}}
 public static void main(String[] args)throws Exception{
  Path out=Paths.get(args[0]);Files.createDirectories(out);System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");OpenGLSkySettings.setMode(OpenGLSkySettings.Mode.WORLD_DOME);
  Scene scene=new Scene(new MudClientGraphics(192,128,1),4,16,4);scene.setMidpoints(96,true,192,64,96,9,128);scene.fogLandscapeDistance=7000;scene.setCamera(0,-4,0,768,0,0,600);scene.setRendererSignedWorldLevel(0);scene.endScene(-113);Renderer3DFrame frame=scene.getRenderer3DFrame();
  BrowserSkyData sky=new BrowserSkyData();RendererToneSettings.Mode[] modes={RendererToneSettings.Mode.DAY,RendererToneSettings.Mode.SUNRISE_AMBER,RendererToneSettings.Mode.COOL_NIGHT};
  for(int index=0;index<modes.length;index++){RendererToneSettings.setMode(modes[index]);BrowserSkyData.Frame data=sky.prepare(frame);if(!data.enabled)throw new AssertionError("Native sky export inactive");if(index==0){floats(out.resolve("geometry.bin"),data.geometry);ints(out.resolve("ranges.bin"),data.ranges);}floats(out.resolve("values"+index+".bin"),data.values);floats(out.resolve("palette"+index+".bin"),data.palette);}
  System.out.println("PASS CPU native sky exported: exact shared geometry and three current production palettes/matrices; no GPU execution");
 }
}
