package orsc;
import orsc.graphics.three.*;
import org.json.*;
/** Run in fresh JVMs: private startup gates are immutable static policy. */
public final class WholeRamDefaultsFixture {
 public static void main(String[] args)throws Exception{
  boolean expected=Boolean.getBoolean("spoiledmilk.browserBenchmark")&&Boolean.getBoolean("spoiledmilk.browserWholeZone")&&Boolean.getBoolean("spoiledmilk.browserWholeRam");
  if(WholeOverworldRamRegistry.isEnabled()!=expected)throw new AssertionError("All three explicit gates required");
  WholeOverworldRamRegistry.Provider marker=new WholeOverworldRamRegistry.Provider(){public boolean accepts(World w,NativeLayeredTerrainSnapshot s){throw new AssertionError("No active World in gate test");}public boolean acceptsCaptured(NativeLayeredTerrainSnapshot s,int offsetX,int offsetZ,boolean roofs){throw new AssertionError();}public boolean activate(World w,int p,int x,int y){throw new AssertionError();}public Renderer3DWorldChunkFrame queryFrame(World w,int x,int y){throw new AssertionError();}public Renderer3DWorldChunkFrame queryFrame(World w,int x,int y,int offsetX,int offsetZ){throw new AssertionError();}public void avoidedBuild(){throw new AssertionError();}};
  boolean installed=false;try{WholeOverworldRamRegistry.install(marker);installed=true;}catch(IllegalStateException refusal){if(expected)throw refusal;}
  if(installed!=expected)throw new AssertionError("OFF cannot install provider");WholeOverworldRamRegistry.clear();
  if(!expected){Config.F_CACHE_DIR="/path/that/does/not/exist/independent-no-RAM-files";try{new BrowserWholeOverworldRam("invalid",new int[4]);throw new AssertionError("OFF reader admitted");}catch(IllegalStateException refusal){if(!refusal.getMessage().contains("requires private native-only"))throw new AssertionError("OFF must refuse before metadata/file allocation",refusal);}}
  System.out.println(new JSONObject().put("passed",true).put("explicitPrivatePolicy",expected).put("checks",expected?2:3).put("scope","Fresh-JVM immutable registry gates; missing flag refuses reader before file decoding. Not prepared readiness or browser allocation proof."));
 }
}
