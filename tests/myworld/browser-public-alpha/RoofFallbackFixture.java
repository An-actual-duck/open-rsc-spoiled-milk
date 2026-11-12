package orsc;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import orsc.graphics.three.*;
/** Fresh in-memory one-centre recipe and real source/UI reloads; not a full cooked delivery. */
public final class RoofFallbackFixture {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Field f(Class<?> c,String n)throws Exception{Field x=c.getDeclaredField(n);x.setAccessible(true);return x;}
 static Object get(Object o,String n)throws Exception{return f(o.getClass(),n).get(o);}
 static void set(Object o,String n,Object v)throws Exception{f(o.getClass(),n).set(o,v);}
 static Method method(Class<?> c,String n,Class<?>...p)throws Exception{Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m;}
 static BrowserWholeOverworldRam owner(WholeRamPrepared data,NativeLayeredTerrainSnapshot s,boolean hidden,Map<String,JSONObject> pages)throws Exception{
  Field field=f(sun.misc.Unsafe.class,"theUnsafe");BrowserWholeOverworldRam r=(BrowserWholeOverworldRam)((sun.misc.Unsafe)field.get(null)).allocateInstance(BrowserWholeOverworldRam.class);
  set(r,"source",new byte[0]);set(r,"prepared",data);set(r,"ready",true);set(r,"hiddenRoofs",hidden);set(r,"manifestSha",s.getManifestSha256());set(r,"settings",new int[]{0,0,1,0});
  Map<String,String> sectors=new HashMap<String,String>();for(Map.Entry<String,JSONObject> entry:pages.entrySet())if(entry.getKey().startsWith("0:"))sectors.put(entry.getKey(),entry.getValue().getString("sha256"));set(r,"sectors",sectors);
  f(BrowserWholeOverworldRam.class,"roofSelection").set(null,new java.lang.ref.WeakReference<BrowserWholeOverworldRam>(r));return r;
 }
 static void equal(JSONObject a,JSONObject b,String label){for(String key:a.keySet())check(a.get(key).toString().equals(b.get(key).toString()),label+" "+key);}
 static void client(mudclient client,WholeRamActivationFixture.Active a,int sx,int sy)throws Exception{
  set(client,"world",a.world);set(client,"scene",a.scene);set(client,"surface",a.surface);set(client,"sceneInstanceStore",new ClientSceneInstanceStore());
  set(client,"cachedResidentObjectChunks",new HashMap<Long,Object>());set(client,"cachedResidentObjectChunkCurrentViewCells",new HashSet<Long>());set(client,"cachedResidentObjectChunkPreviousViewCells",new HashSet<Long>());
  set(client,"hasCompletedInitialRegionLoad",true);set(client,"localPlayer",new ORSCharacter());set(client,"midRegionBaseX",(sx-1)*48);set(client,"midRegionBaseZ",(sy-1)*48);set(client,"worldOffsetX",0);set(client,"worldOffsetZ",0);set(client,"requestedPlane",0);
 }
 public static void main(String[] args)throws Exception{
  boolean publicMode=Boolean.getBoolean("spoiledmilk.browserPublicAlpha");Path root=Paths.get(args[0]),source=Paths.get(args[1]),mini=Paths.get(args[2]),out=Paths.get(args[3]);Files.createDirectories(out);
  for(String flag:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldReplacementComposite","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster"})System.setProperty("spoiledmilk."+flag,"true");
  Class<?> c=Class.forName("orsc.BrowserWholeZoneBaker$Context");Constructor<?> ctor=c.getDeclaredConstructor(Path.class,Path.class,Path.class,Path.class);ctor.setAccessible(true);Object context=ctor.newInstance(root,source,mini,out.resolve("source-context"));
  Renderer3DSettings.setBrowserPresentationAvailable(true);Scene assets=((mudclient)get(context,"client")).getScene();mudclient ui=(mudclient)get(context,"client");Method snapshot=method(c,"snapshot",int.class,int.class,int.class),reload=method(mudclient.class,"reloadCurrentRegionForRoofVisibility");
  int sx=2,sy=12;NativeLayeredTerrainSnapshot s=(NativeLayeredTerrainSnapshot)snapshot.invoke(context,0,sx,sy);
  Map<Integer,NativeLayeredTerrainSnapshot> layerSnapshots=new HashMap<Integer,NativeLayeredTerrainSnapshot>();for(int level:new int[]{1,-1,10})layerSnapshots.put(level,(NativeLayeredTerrainSnapshot)snapshot.invoke(context,level,sx,sy));
  Map<String,JSONObject> pages=(Map<String,JSONObject>)get(context,"pages");NativeLayeredTerrainChunk[] haloChunks=new NativeLayeredTerrainChunk[25];int h=0;
  for(int x=sx-2;x<=sx+2;x++)for(int y=sy-2;y<=sy+2;y++){JSONObject page=pages.get("0:"+x+":"+y);haloChunks[h++]=page==null?NativeLayeredTerrainChunk.voidChunk(48,x,y):NativeLayeredTerrainChunk.available(48,x,y,x,y,page.getString("encoding"),page.getString("sha256"),Files.readAllBytes(source.resolve(page.getString("path"))));}
  NativeLayeredTerrainSnapshot halo=new NativeLayeredTerrainSnapshot(9,(String)get(s,"packageId"),(String)get(s,"packageVersion"),s.getManifestSha256(),48,"global",0,sx,sy,2,haloChunks);
  final String denied=source.toRealPath().toString();final int[] blockedReads={0};
  System.setSecurityManager(new SecurityManager(){public void checkPermission(java.security.Permission p){}public void checkRead(String path){if(Paths.get(path).toAbsolutePath().normalize().toString().startsWith(denied+java.io.File.separator)||Paths.get(path).toAbsolutePath().normalize().toString().equals(denied)){blockedReads[0]++;throw new SecurityException("Fixture forbids raw source file reads after authoritative snapshots: "+path);}}});
  try{Files.readAllBytes(source.resolve("manifest.json"));throw new AssertionError("raw-source guard inactive");}catch(SecurityException expected){checks++;}
  for(boolean cooked:new boolean[]{false,true}){
   Config.C_HIDE_ROOFS=cooked;WholeOverworldRamRegistry.clear();WholeRamActivationFixture.Active seed=new WholeRamActivationFixture.Active(assets);seed.scope(s);seed.world.loadSections(sx*48,sy*48,0);
   WholeRamData.Pool pool=new WholeRamData.Pool();WholeRamPrepared prepared=new WholeRamPrepared(pool);prepared.addRecipe(seed.world.prepareWholeRamActivation(s,pool));
   Renderer3DWorldChunkFrame.ChunkMesh mesh=seed.world.getRenderer3DWorldChunkFrame().getChunks().get(0);prepared.addQueryCell(0,sx,sy,mesh.captureWholeRamCell(pool));
   WholeRamActivationFixture.Active active=new WholeRamActivationFixture.Active(assets);active.scope(s);client(ui,active,sx,sy);BrowserWholeOverworldRam reader=owner(prepared,s,cooked,pages);
   int hits=0;for(boolean preference:new boolean[]{cooked,!cooked,cooked,!cooked,cooked}){
    // Actual account-setting setter after reader creation, followed by the exact
    // UI reload method used by the Graphics roof toggle (no networking needed).
    World.NativeLayeredTerrainHaloPrebuildResult stale=null;
    if(publicMode&&preference!=cooked)stale=active.world.preloadNativeLayeredTerrainHalo(halo,0,0).get();
    ui.setOptionHideRoofs(preference);
    if(!publicMode&&preference!=cooked){
      try{BrowserWholeOverworldRam.validateInitialRoofOption(cooked);throw new AssertionError("private constructor roof guard lost");}catch(IllegalStateException expected){checks++;}
      try{reader.validateOptions(new int[]{0,0,1,0});throw new AssertionError("private option roof guard lost");}catch(IllegalStateException expected){checks++;}
      continue;
    }
    BrowserWholeOverworldRam.validateInitialRoofOption(cooked);
    reader.validateOptions(new int[]{0,0,1,0});WholeOverworldRamRegistry.clear();WholeRamActivationFixture.Active literal=new WholeRamActivationFixture.Active(assets);literal.scope(s);literal.world.loadSections(sx*48,sy*48,0);JSONObject expected=literal.state();
    WholeOverworldRamRegistry.install(reader);int before=(Integer)get(reader,"activations");
    if(!publicMode&&preference!=cooked)continue;
    reload.invoke(ui);if(stale!=null){Object beforeFrame=active.world.getRenderer3DWorldChunkFrame();check(!active.world.publishNativeLayeredTerrainHalo(stale,halo,0,0),"old actual background roof result rejected after toggle");check(active.world.getRenderer3DWorldChunkFrame()==beforeFrame,"stale roof result did not replace current query");}
    equal(expected,active.state(),"real UI reload parity cooked="+cooked+" pref="+preference);
    check((Integer)get(reader,"activations")==before+(preference==cooked?1:0),"actual recipe used only by compatible roof");
    int[] selection=BrowserWholeOverworldRam.getRoofSelectionWork();check(selection[5]==(preference==cooked?1:0),"honest separate roof eligibility receipt");
    if(!preference){int nonnull=0;for(RSModel[] row:(RSModel[][])get(active.world,"modelRoofGrid"))for(RSModel model:row)if(model!=null)nonnull++;check(nonnull>0,"show-roof comparison contains real picking models");}
    // A stale captured opposite-roof result remains ineligible across the toggle.
    check(reader.acceptsCaptured(s,0,0,!preference)==(preference==cooked),"captured roof decision exact");
    if(preference==cooked)hits++;
   }
   check((Integer)get(reader,"activations")==hits,"final matching return resumes same retained recipes");
   Config.C_HIDE_ROOFS=!cooked;
   for(int[] bad:new int[][]{{0,0,1,1},{1,0,1,0}}){try{reader.validateOptions(bad);throw new AssertionError("packing mismatch accepted");}catch(IllegalStateException expected){checks++;}}
   Map patches=(Map)get(active.world,"worldEditorTerrainPatches");patches.put("fixture",null);try{reader.accepts(active.world,s);throw new AssertionError("fallback bypassed patches");}catch(IllegalStateException expected){check(expected.getMessage().contains("patch"),"original patch guard");}finally{patches.clear();}
   NativeLayeredTerrainSnapshot bad=new NativeLayeredTerrainSnapshot(8,"wrong","1.0.0",String.join("",Collections.nCopies(64,"b")),48,"global",0,sx,sy,1,new NativeLayeredTerrainChunk[]{NativeLayeredTerrainChunk.voidChunk(48,sx-1,sy-1),NativeLayeredTerrainChunk.voidChunk(48,sx-1,sy),NativeLayeredTerrainChunk.voidChunk(48,sx-1,sy+1),NativeLayeredTerrainChunk.voidChunk(48,sx,sy-1),NativeLayeredTerrainChunk.voidChunk(48,sx,sy),NativeLayeredTerrainChunk.voidChunk(48,sx,sy+1),NativeLayeredTerrainChunk.voidChunk(48,sx+1,sy-1),NativeLayeredTerrainChunk.voidChunk(48,sx+1,sy),NativeLayeredTerrainChunk.voidChunk(48,sx+1,sy+1)});
   try{reader.accepts(active.world,bad);throw new AssertionError("fallback bypassed source");}catch(IllegalStateException expected){check(expected.getMessage().contains("manifest"),"original manifest guard");}
   // Same manifest but missing an authored page must still fail. Private mode
   // keeps its old earlier roof exception, so exercise its matching path here.
   if(!publicMode)Config.C_HIDE_ROOFS=cooked;
   NativeLayeredTerrainSnapshot missing=new NativeLayeredTerrainSnapshot(8,(String)get(s,"packageId"),(String)get(s,"packageVersion"),s.getManifestSha256(),48,"global",0,sx,sy,1,new NativeLayeredTerrainChunk[]{NativeLayeredTerrainChunk.voidChunk(48,sx-1,sy-1),NativeLayeredTerrainChunk.voidChunk(48,sx-1,sy),NativeLayeredTerrainChunk.voidChunk(48,sx-1,sy+1),NativeLayeredTerrainChunk.voidChunk(48,sx,sy-1),NativeLayeredTerrainChunk.voidChunk(48,sx,sy),NativeLayeredTerrainChunk.voidChunk(48,sx,sy+1),NativeLayeredTerrainChunk.voidChunk(48,sx+1,sy-1),NativeLayeredTerrainChunk.voidChunk(48,sx+1,sy),NativeLayeredTerrainChunk.voidChunk(48,sx+1,sy+1)});
   try{reader.accepts(active.world,missing);throw new AssertionError("fallback bypassed owned source pages");}catch(IllegalStateException expected){check(expected.getMessage().contains("authored source"),"owned page proof precedes fallback");}
   // Original public signed-level setter then UI reload; return to L0 must not
   // retain the previous layer's query or incompatible roof models.
   for(int level:new int[]{1,-1,10}){
    Config.C_HIDE_ROOFS=publicMode?!cooked:cooked;
    int beforeLayer=(Integer)get(reader,"activations");WholeOverworldRamRegistry.clear();NativeLayeredTerrainSnapshot other=layerSnapshots.get(level);active.world.setLayeredTerrainScope(true,other,0,0);if(level==1)active.world.loadSections(sx*48,sy*48,level);
    WholeOverworldRamRegistry.install(reader);check(!reader.accepts(active.world,other),"non-ground level uses literal parser");active.world.setLayeredTerrainScope(true,s,0,0);set(ui,"requestedPlane",0);reload.invoke(ui);
    WholeOverworldRamRegistry.clear();WholeRamActivationFixture.Active literal=new WholeRamActivationFixture.Active(assets);literal.scope(s);literal.world.loadSections(sx*48,sy*48,0);equal(literal.state(),active.state(),"mismatched-roof layer return "+level);
    WholeOverworldRamRegistry.install(reader);ui.setOptionHideRoofs(cooked);reload.invoke(ui);hits+=publicMode?1:2;check((Integer)get(reader,"activations")==beforeLayer+(publicMode?1:2),"matching return resumes recipe after layer");ui.setOptionHideRoofs(!cooked);
   }
   WholeOverworldRamRegistry.clear();
  }
  check(blockedReads[0]==1,"literal reloads never attempted raw source filesystem reads");
  System.out.println("PASS "+checks+" fresh one-centre prepared/UI toggle/source/packing/picking/collision parity checks public="+publicMode+"; not full cooked delivery or browser proof");
 }
}
