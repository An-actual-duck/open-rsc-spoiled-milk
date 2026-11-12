package orsc;
import orsc.graphics.three.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
/** Same exact production JAR, retained versus literal controls; owned detached input. */
public final class StaticSceneReuseOracle {
 static int checks,frames;static int[] settings={0,0,1,0};
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void set(Object x,String n,Object v)throws Exception{WholeZoneDynamicFixture.set(x,n,v);}
 static BrowserWholeOverworldZone zone(JSONObject catalog,JSONObject source)throws Exception{
  BrowserWholeOverworldZone z=(BrowserWholeOverworldZone)WholeZoneDynamicFixture.allocate(BrowserWholeOverworldZone.class);
  Map<String,JSONObject> objects=new HashMap<>(),resources=new HashMap<>();Map<String,String> sources=new HashMap<>();
  for(Object o:catalog.getJSONArray("objectCells")){JSONObject c=(JSONObject)o;objects.put(WholeZoneDynamicFixture.id(c),c);}
  for(Object o:catalog.getJSONArray("chunks")){JSONObject c=(JSONObject)o;if(c.getString("sourceKind").equals("objects"))resources.put(WholeZoneDynamicFixture.id(c),c);}
  for(Object o:source.getJSONArray("terrainSectors")){JSONObject c=(JSONObject)o;if(c.getString("worldSpace").equals("global")&&c.getInt("level")>=0&&c.getInt("level")<=2)sources.put(c.getInt("level")+":"+c.getInt("sectorX")+":"+c.getInt("sectorY"),c.getString("sha256"));}
  set(z,"manifestHash",catalog.getString("manifestSha256"));set(z,"settings",settings);set(z,"materials",new int[0]);set(z,"objects",objects);set(z,"resources",resources);set(z,"sources",sources);set(z,"descriptors",new HashMap<>());set(z,"observed",new HashMap<>());set(z,"rows",new int[0]);set(z,"metadata",new int[18]);set(z,"expectedResources",catalog.getJSONArray("chunks").length());z.uploaded(catalog.getJSONArray("chunks").length());return z;
 }
 static mudclient client(World world,SceneBaselineState state)throws Exception{
  mudclient c=(mudclient)WholeZoneDynamicFixture.allocate(mudclient.class);set(c,"world",world);PacketHandler p=(PacketHandler)WholeZoneDynamicFixture.allocate(PacketHandler.class);set(p,"sceneBaselineState",state);c.packetHandler=p;return c;
 }
 static Method capture;static void capture(mudclient c,List<Object> rows)throws Exception{capture.invoke(c,rows);}
 static void baseline(SceneBaselineState s,int tick,int x,int y,int distance){s.recordPacket(1,tick,x,y,0,0,0,distance,0,0,0,0,0,0,0,Collections.<SceneBaselineState.Record>emptyList());}
 static NativeLayeredTerrainSnapshot recentered(JSONObject source,Path root,String manifest,int cx,int cy)throws Exception{
  Map<String,JSONObject> entries=new HashMap<>();for(Object o:source.getJSONArray("terrainSectors")){JSONObject r=(JSONObject)o;if(r.getString("worldSpace").equals("global")&&r.getInt("level")==0)entries.put(r.getInt("sectorX")+":"+r.getInt("sectorY"),r);}
  NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[9];int at=0;for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){int x=cx+dx,y=cy+dy;JSONObject r=entries.get(x+":"+y);chunks[at++]=r==null?NativeLayeredTerrainChunk.voidChunk(48,x,y):NativeLayeredTerrainChunk.available(48,x,y,x,y,r.getString("encoding"),r.getString("sha256"),Files.readAllBytes(root.resolve(r.getString("path"))));}
  return new NativeLayeredTerrainSnapshot(8,source.getString("packageId"),source.getString("packageVersion"),manifest,48,"global",0,cx,cy,1,chunks);
 }
 interface Action{void run()throws Exception;}
 static void refused(Action action,String label)throws Exception{try{action.run();throw new AssertionError(label+" not refused");}catch(IllegalStateException expected){checks++;}}
 static Renderer3DTextureData texture(int rgb)throws Exception{Method factory=WholeZoneDynamicFixture.method(Renderer3DTextureData.class,"fromLegacyResource",int.class,int.class,int[].class);int[] pixels=new int[4096];Arrays.fill(pixels,rgb);return(Renderer3DTextureData)factory.invoke(null,0,0,pixels);}
 static void scalarParity(Object a,Object b,String label)throws Exception{
  check(a!=null&&b!=null,label+" present");
  for(Method m:a.getClass().getMethods())if((m.getName().startsWith("get")||m.getName().startsWith("is"))&&m.getParameterTypes().length==0&&(m.getReturnType().isPrimitive()||m.getReturnType().isEnum()))check(Objects.equals(m.invoke(a),m.invoke(b)),label+" "+m.getName());
 }
 static void descriptorParity(Renderer3DWorldChunkFrame.ChunkMesh a,Renderer3DWorldChunkFrame.ChunkMesh b)throws Exception{
  check((a==null)==(b==null),"descriptor presence");if(a==null)return;scalarParity(a,b,"descriptor");
  for(int n=0;n<a.getShadowCasterCount();n++)scalarParity(a.getShadowCaster(n),b.getShadowCaster(n),"shadow");
  for(int n=0;n<a.getGlowEmitterCount();n++)scalarParity(a.getGlowEmitter(n),b.getGlowEmitter(n),"glow");
 }
 static void compare(BrowserWholeOverworldZone literal,BrowserWholeOverworldZone reused,mudclient c,Renderer3DFrame f,Renderer3DWorldChunkFrame input,JSONObject target)throws Exception{
  System.setProperty("spoiledmilk.browserStaticSceneReuse","false");literal.frame(c,f,settings);int[] rows=literal.objectRows().clone(),metadata=literal.metadata().clone();Renderer3DWorldChunkFrame filtered=literal.filter(input);Renderer3DWorldChunkFrame.ChunkMesh descriptor=literal.resolveStatic(0,target.getInt("cellX"),target.getInt("cellY"),WholeZoneDynamicFixture.key(target),c.getMidRegionBaseX(),c.getMidRegionBaseZ());
  System.setProperty("spoiledmilk.browserStaticSceneReuse","true");reused.frame(c,f,settings);
  check(Arrays.equals(rows,reused.objectRows()),"literal versus retained exact rows");check(Arrays.equals(metadata,reused.metadata()),"literal versus retained exact metadata");Renderer3DWorldChunkFrame other=reused.filter(input);check(filtered.getChunkCount()==other.getChunkCount(),"local render-plan count");for(int n=0;n<filtered.getChunkCount();n++)check(filtered.getChunks().get(n)==other.getChunks().get(n),"local render-plan ownership/order");
  descriptorParity(descriptor,reused.resolveStatic(0,target.getInt("cellX"),target.getInt("cellY"),WholeZoneDynamicFixture.key(target),c.getMidRegionBaseX(),c.getMidRegionBaseZ()));frames++;
 }
 public static void main(String[] args)throws Exception{
  Config.F_CACHE_DIR=args[2];JSONObject catalog=WholeZoneDynamicFixture.json(Paths.get(args[0])),source=WholeZoneDynamicFixture.json(Paths.get(args[1]).resolve("manifest.json"));Path root=Paths.get(args[1]);String manifest=catalog.getString("manifestSha256");
  BrowserWholeOverworldZone literal=zone(catalog,source),reused=zone(catalog,source);World world=new World(null,null);SceneBaselineState state=new SceneBaselineState();mudclient c=client(world,state);set(world,"nativeLayeredTerrainSnapshot",WholeZoneDynamicFixture.snapshot(source,root,manifest,false));
  capture=WholeZoneDynamicFixture.method(mudclient.class,"captureWholeOverworldObjectCells",List.class);
  JSONObject target=null;for(Object o:catalog.getJSONArray("objectCells")){JSONObject a=(JSONObject)o;if(a.getInt("plane")==0&&a.getInt("cellX")>=2&&a.getInt("cellX")<=9&&a.getInt("cellY")>=22&&a.getInt("cellY")<=31&&a.getInt("placementCount")>0){target=a;break;}}check(target!=null,"nonvacuous active authored cell");
  int x=target.getInt("cellX"),y=target.getInt("cellY");long key=WholeZoneDynamicFixture.key(target);Renderer3DWorldChunkFrame.ChunkMesh staticMesh=WholeZoneDynamicFixture.mesh(0,x,y,true),worldMesh=WholeZoneDynamicFixture.mesh(0,x,y,false);Renderer3DWorldChunkFrame input=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(worldMesh,staticMesh));Renderer3DFrame f=WholeZoneDynamicFixture.frame();
  List<Object> authored=Arrays.asList(WholeZoneDynamicFixture.input(staticMesh,x,y,1,key));capture(c,authored);baseline(state,1,x*24,y*24,0);
  for(int n=0;n<12;n++){capture(c,authored);compare(literal,reused,c,f,input,target);}
  check(reused.staticSceneDiagnostics()[3]>=11,"stationary frames reuse rows");check(reused.staticSceneDiagnostics()[2]<literal.staticSceneDiagnostics()[2],"counted row-build work reduced");
  // Complete door/tree state changes and restoration, then mixed animation in both orders.
  for(long mutation:new long[]{key^1,key^2,key,key^1,key}){capture(c,Arrays.asList(WholeZoneDynamicFixture.input(staticMesh,x,y,1,mutation)));compare(literal,reused,c,f,input,target);}
  for(boolean reverse:new boolean[]{false,true}){Object a=WholeZoneDynamicFixture.input(staticMesh,x,y,1,key),b=WholeZoneDynamicFixture.input(staticMesh,x,y,2,0);capture(c,reverse?Arrays.asList(b,a):Arrays.asList(a,b));compare(literal,reused,c,f,input,target);}
  capture(c,Collections.emptyList());compare(literal,reused,c,f,input,target);state.resetForScopeChange("other");compare(literal,reused,c,f,input,target);baseline(state,2,x*24,y*24,1);compare(literal,reused,c,f,input,target);baseline(state,3,0,0,1);compare(literal,reused,c,f,input,target);
  capture(c,authored);for(Renderer3DRoofVisibility roof:Renderer3DRoofVisibility.values())for(int plane:new int[]{0,1,2}){f.setRoofVisibility(roof,plane);Config.C_HIDE_ROOFS=plane==2;compare(literal,reused,c,f,input,target);}Config.C_HIDE_ROOFS=false;
  for(int base:new int[]{48,-48,0}){set(c,"midRegionBaseX",base);set(c,"midRegionBaseZ",base);capture(c,authored);compare(literal,reused,c,f,input,target);}
  for(int plane:new int[]{1,2,0}){c.setRequestedPlane(plane);compare(literal,reused,c,f,input,target);}
  for(int signed:new int[]{-1,-2,10,0}){set(world,"nativeLayeredTerrainSnapshot",WholeZoneDynamicFixture.snapshot(source,root,manifest,false,signed,signed==0?"other":"global"));Renderer3DFrame other=WholeZoneDynamicFixture.frame(signed,0);compare(literal,reused,c,other,input,target);check(reused.metadata()[1]==0,"safe signed/non-global fallback");other.release();}
  set(world,"nativeLayeredTerrainSnapshot",WholeZoneDynamicFixture.snapshot(source,root,manifest,false));compare(literal,reused,c,f,input,target);
  for(int[] center:new int[][]{{3,13},{2,14},{2,13}}){set(world,"nativeLayeredTerrainSnapshot",recentered(source,root,manifest,center[0],center[1]));compare(literal,reused,c,f,input,target);check(reused.metadata()[5]==center[0]&&reused.metadata()[6]==center[1],"actual source-center revision reflected");}
  // Equal immutable texture metadata can reuse; replacing that material must be revalidated.
  Renderer3DTextureData material=texture(0x123456);set(f,"textures",new Renderer3DTextureData[]{material});int[] expected=BrowserWholeOverworldZone.materialMetadata(f);set(literal,"materials",expected);set(reused,"materials",expected);compare(literal,reused,c,f,input,target);set(f,"textures",new Renderer3DTextureData[]{texture(0x123456)});compare(literal,reused,c,f,input,target);
  set(f,"textures",new Renderer3DTextureData[]{texture(0x654321)});for(final BrowserWholeOverworldZone z:new BrowserWholeOverworldZone[]{literal,reused})refused(()->z.frame(c,f,settings),"changed material");set(f,"textures",new Renderer3DTextureData[0]);set(literal,"materials",new int[0]);set(reused,"materials",new int[0]);
  for(final BrowserWholeOverworldZone z:new BrowserWholeOverworldZone[]{literal,reused})refused(()->z.frame(c,f,new int[]{1,0,1,0}),"changed packing options");
  // Equal revision counters from a replacement owner cannot validate the prior owner's rows.
  SceneBaselineState replacementState=new SceneBaselineState();mudclient replacement=client(world,replacementState);capture(replacement,Arrays.asList(WholeZoneDynamicFixture.input(staticMesh,x,y,1,key^7)));baseline(replacementState,1,0,0,0);set(c,"wholeOverworldObjectCellsRevision",1L);set(state,"wholeZoneAuthorityRevision",1L);capture(c,authored);set(c,"wholeOverworldObjectCellsRevision",1L);compare(literal,reused,c,f,input,target);compare(literal,reused,replacement,f,input,target);
  literal.invalidate();reused.invalidate();compare(literal,reused,replacement,f,input,target);check(reused.staticSceneDiagnostics()[3]>0,"reuse occurred nonvacuously");
  world.applyWorldEditorTerrainPatch(0,111,625,1,0,0,0,0,0,0,false);for(final BrowserWholeOverworldZone z:new BrowserWholeOverworldZone[]{literal,reused})refused(()->z.frame(replacement,f,settings),"world terrain revision cannot reuse unbaked products");
  System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("frames",frames).put("literalWork",new JSONArray(literal.staticSceneDiagnostics())).put("retainedWork",new JSONArray(reused.staticSceneDiagnostics())).put("scope","Actual same-JAR literal and retained row/metadata/filter/descriptor parity with detached owned complete index, real capture and baseline methods. No constructor admission, live packets, browser pixels, GPU or FPS proof.").toString());f.release();
 }
}
