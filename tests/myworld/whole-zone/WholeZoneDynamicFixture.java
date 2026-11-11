package orsc;
import orsc.graphics.three.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
/** Detached reader/capture semantic proof. Catalog constructor admission is not bypassed in gameplay. */
public final class WholeZoneDynamicFixture {
 static int checks;
 static Field field(Class<?> c,String name)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
 static Object get(Object x,String n)throws Exception{return field(x.getClass(),n).get(x);}
 static void set(Object x,String n,Object v)throws Exception{field(x.getClass(),n).set(x,v);}
 static Method method(Class<?> c,String n,Class<?>...p)throws Exception{Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m;}
 static Object allocate(Class<?> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");return u.getMethod("allocateInstance",Class.class).invoke(field(u,"theUnsafe").get(null),c);}
 static JSONObject json(Path p)throws Exception{return new JSONObject(new String(Files.readAllBytes(p),"UTF-8"));}
 static void check(boolean b,String n){checks++;if(!b)throw new AssertionError(n);}
 interface Action{void run()throws Exception;}
 static void refused(Action a,String label)throws Exception{try{a.run();throw new AssertionError("Not refused: "+label);}catch(IllegalStateException expected){checks++;}}
 static Renderer3DFrame frame()throws Exception{return frame(0,0);}
 static Renderer3DFrame frame(int signed,int compatibility)throws Exception{
  Class<?>[] types=new Class<?>[19];Arrays.fill(types,int.class);types[15]=Renderer3DTextureData[].class;types[16]=long.class;types[17]=double.class;Constructor<?> c=Renderer3DFrame.class.getDeclaredConstructor(types);c.setAccessible(true);Object[] args=new Object[19];Arrays.fill(args,Integer.valueOf(0));args[3]=64;args[4]=64;args[13]=9;args[14]=5;args[15]=new Renderer3DTextureData[0];args[16]=0L;args[17]=Double.NaN;args[18]=signed;Renderer3DFrame f=(Renderer3DFrame)c.newInstance(args);f.setRoofVisibility(Renderer3DRoofVisibility.VISIBLE,compatibility);return f;
 }
 static Renderer3DWorldChunkFrame.ChunkMesh mesh(int p,int x,int y,boolean object){return new Renderer3DWorldChunkFrame.ChunkMesh(p,2,13,x*24*128,y*24*128,new int[0],new float[0],new float[0],new int[0],new int[0],new int[0],new int[0],new Renderer3DModelKind[0],0,0,0,object,0L);}
 static Object input(Renderer3DWorldChunkFrame.ChunkMesh anchor,int x,int y,int role,long key)throws Exception{
  Class<?> c=Class.forName("orsc.mudclient$ResidentObjectChunkInput"),id=Class.forName("orsc.mudclient$ResidentObjectCanonicalIdentity");Constructor<?> ctor=null;for(Constructor<?> v:c.getDeclaredConstructors())if(v.getParameterTypes().length==14)ctor=v;if(ctor==null)throw new IllegalStateException("Input constructor changed");ctor.setAccessible(true);return ctor.newInstance(anchor,0L,x*24,y*24,role,new RSModel[0],Array.newInstance(id,0),0,0L,key,0,0,0,"");
 }
 static long key(JSONObject c){return Long.parseUnsignedLong(c.getString("canonicalContentKey"),16);}
 static String id(JSONObject c){return c.getInt("plane")+":"+c.getInt("cellX")+":"+c.getInt("cellY");}
 static NativeLayeredTerrainSnapshot snapshot(JSONObject source,Path root,String manifest,boolean wrong)throws Exception{return snapshot(source,root,manifest,wrong,0,"global");}
 static NativeLayeredTerrainSnapshot snapshot(JSONObject source,Path root,String manifest,boolean wrong,int signed,String space)throws Exception{
  Map<String,JSONObject> entries=new HashMap<String,JSONObject>();for(Object item:source.getJSONArray("terrainSectors")){JSONObject r=(JSONObject)item;if(r.getString("worldSpace").equals("global")&&r.getInt("level")==0)entries.put(r.getInt("sectorX")+":"+r.getInt("sectorY"),r);}
  NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[9];int at=0;
  for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){int x=2+dx,y=13+dy;JSONObject r=entries.get(x+":"+y);chunks[at++]=r==null?NativeLayeredTerrainChunk.voidChunk(48,x,y):NativeLayeredTerrainChunk.available(48,x,y,x,y,r.getString("encoding"),wrong&&dx==0&&dy==0?String.join("",Collections.nCopies(64,"0")):r.getString("sha256"),Files.readAllBytes(root.resolve(r.getString("path"))));}
  return new NativeLayeredTerrainSnapshot(8,source.getString("packageId"),source.getString("packageVersion"),manifest,48,space,signed,2,13,1,chunks);
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Config.F_CACHE_DIR=Paths.get(args[2]).toString();Path catalogPath=Paths.get(args[0]),packageRoot=Paths.get(args[1]);JSONObject catalog=json(catalogPath),source=json(packageRoot.resolve("manifest.json"));if(catalog.getJSONArray("objectCells").length()>64)throw new IllegalArgumentException("Explicit bounded pilot index required");
  // Intentionally inject only owned index metadata to isolate runtime semantics. Full admission is tested independently.
  BrowserWholeOverworldZone zone=(BrowserWholeOverworldZone)allocate(BrowserWholeOverworldZone.class);
  Map<String,JSONObject> objects=new HashMap<String,JSONObject>(),resources=new HashMap<String,JSONObject>();Map<String,String> sources=new HashMap<String,String>();
  for(Object item:catalog.getJSONArray("objectCells")){JSONObject c=(JSONObject)item;objects.put(id(c),c);}
  for(Object item:catalog.getJSONArray("chunks")){JSONObject c=(JSONObject)item;if(c.getString("sourceKind").equals("objects"))resources.put(id(c),c);}
  for(Object item:source.getJSONArray("terrainSectors")){JSONObject r=(JSONObject)item;if(r.getString("worldSpace").equals("global")&&r.getInt("level")>=0&&r.getInt("level")<=2)sources.put(r.getInt("level")+":"+r.getInt("sectorX")+":"+r.getInt("sectorY"),r.getString("sha256"));}
  int[] settings={0,0,1,0};set(zone,"manifestHash",catalog.getString("manifestSha256"));set(zone,"settings",settings);set(zone,"materials",new int[0]);set(zone,"objects",objects);set(zone,"resources",resources);set(zone,"sources",sources);set(zone,"descriptors",new HashMap<>());set(zone,"observed",new HashMap<>());set(zone,"expectedResources",catalog.getJSONArray("chunks").length());set(zone,"metadata",new int[18]);set(zone,"rows",new int[0]);
  mudclient client=(mudclient)allocate(mudclient.class);World owner=new World(null,null);set(client,"world",owner);PacketHandler packets=(PacketHandler)allocate(PacketHandler.class);SceneBaselineState state=new SceneBaselineState();set(packets,"sceneBaselineState",state);client.packetHandler=packets;set(owner,"nativeLayeredTerrainSnapshot",snapshot(source,packageRoot,catalog.getString("manifestSha256"),false));Renderer3DFrame frame=frame();
  JSONObject target=null;for(JSONObject c:objects.values())if(c.getInt("plane")==0&&c.getInt("placementCount")>0){target=c;break;}final JSONObject cell=target;check(cell!=null,"nonvacuous authored cell");int x=cell.getInt("cellX"),y=cell.getInt("cellY");long baked=key(cell);Renderer3DWorldChunkFrame.ChunkMesh staticMesh=mesh(0,x,y,true),worldMesh=mesh(0,x,y,false);Method capture=method(mudclient.class,"captureWholeOverworldObjectCells",List.class);
  for(boolean reverse:new boolean[]{false,true}){List<Object> list=new ArrayList<>();Object s=input(staticMesh,x,y,1,baked),a=input(staticMesh,x,y,2,0L);list.add(reverse?a:s);list.add(reverse?s:a);capture.invoke(client,list);int[] r=client.getWholeOverworldObjectCells();check(r.length==6&&r[5]==2,"mixed reason sticky both orders");check((((long)r[4]<<32)|(r[3]&0xffffffffL))==baked,"mixed static key preserved");}
  int[] localRows=new int[]{0,x,y,(int)baked,(int)(baked>>>32),0};set(client,"wholeOverworldObjectCells",localRows);zone.uploaded(catalog.getJSONArray("chunks").length());zone.frame(client,frame,settings);
  check(zone.ready(),"actual ready gate");Renderer3DWorldChunkFrame input=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(worldMesh,staticMesh));check(zone.filter(input).getChunkCount()==0,"covered world+static removed from local upload list");
  Renderer3DWorldChunkFrame.ChunkMesh descriptor=zone.resolveStatic(0,x,y,baked,0,0);check(descriptor!=null&&descriptor.getVertexCount()==0,"equal key skips static geometry via descriptor");JSONObject resource=resources.get(id(cell));check(descriptor.getShadowCasterCount()==resource.getJSONArray("shadowCasters").length(),"shadow descriptors preserved");check(descriptor.getGlowEmitterCount()==resource.getJSONArray("glowEmitters").length(),"glow descriptors preserved");check(zone.resolveStatic(0,x,y,baked,0,0)==descriptor,"bounded descriptor reused");
  for(int reason:new int[]{1,2}){localRows[5]=reason;if(reason==1)localRows[3]^=1;zone.frame(client,frame,settings);check(zone.filter(input).getChunkCount()==1,"mutated/mixed static stays live");check(zone.resolveStatic(0,x,y,reason==1?baked^1:baked,0,0)==null,"mutated/mixed resolver refuses stale mesh");if(reason==1)localRows[3]^=1;}
  set(client,"wholeOverworldObjectCells",new int[0]);set(state,"completedBaselines",1);set(state,"objectViewDistance",0);zone.frame(client,frame,settings);int[] out=zone.objectRows();boolean found=false;for(int at=0;at<out.length;at+=6)if(out[at+1]==x&&out[at+2]==y&&out[at]==0){found=true;check(out[at+5]==1,"destroyed last authoritative object masks baseline");long e=Long.parseUnsignedLong(cell.getString("emptyCanonicalContentKey"),16);check((((long)out[at+4]<<32)|(out[at+3]&0xffffffffL))==e,"explicit empty production key");}check(found,"empty cell authority exercised");
  JSONObject partialCell=new JSONObject(cell.toString());JSONArray ids=cell.getJSONArray("identities"),one=new JSONArray();for(int n=0;n<7;n++)one.put(ids.getInt(n));partialCell.put("identities",one);objects.put(id(cell),partialCell);set(state,"localX",one.getInt(1));set(state,"localY",one.getInt(2));set(state,"objectViewDistance",1);
  boolean partial=!client.isWholeZoneObjectCellAuthoritative(0,x,y);if(partial){zone.frame(client,frame,settings);out=zone.objectRows();for(int at=0;at<out.length;at+=6)if(out[at]==0&&out[at+1]==x&&out[at+2]==y)check(out[at+5]==1,"known authored positions establish empty partial-cell authority");}check(partial,"partial-cell known-position authority nonvacuous");objects.put(id(cell),cell);
  set(state,"completedBaselines",0);zone.frame(client,frame,settings);out=zone.objectRows();for(int at=0;at<out.length;at+=6)if(out[at]==0&&out[at+1]==x&&out[at+2]==y)check(out[at+5]==3,"unknown halo preserves authored baseline rather than guesses empty");
  for(int[] scope:new int[][]{{-1,3},{-2,2},{10,0},{0,0}}){String space=scope[0]==0?"other":"global";Renderer3DFrame other=frame(scope[0],scope[1]);client.setRequestedPlane(scope[1]);set(owner,"nativeLayeredTerrainSnapshot",snapshot(source,packageRoot,catalog.getString("manifestSha256"),false,scope[0],space));zone.frame(client,other,settings);check(zone.metadata()[1]==0&&zone.metadata()[2]==scope[0],"signed/non-global scope metadata");check(zone.metadata()[17]==(space.equals("global")?0:1),"explicit non-global scope bit");check(zone.filter(input)==input,"inactive scope preserves exact local draw object");check(zone.resolveStatic(0,x,y,baked,0,0)==null,"inactive scope cannot suppress local static");other.release();}
  client.setRequestedPlane(0);set(owner,"nativeLayeredTerrainSnapshot",snapshot(source,packageRoot,catalog.getString("manifestSha256"),false));zone.frame(client,frame,settings);
  refused(()->zone.frame(client,frame,new int[]{1,0,1,0}),"packing settings");
  set(owner,"nativeLayeredTerrainSnapshot",snapshot(source,packageRoot,catalog.getString("manifestSha256"),true));refused(()->zone.frame(client,frame,settings),"same-package payload mutation");
  set(owner,"nativeLayeredTerrainSnapshot",snapshot(source,packageRoot,String.join("",Collections.nCopies(64,"0")),false));refused(()->zone.frame(client,frame,settings),"manifest revision");
  set(owner,"nativeLayeredTerrainSnapshot",snapshot(source,packageRoot,catalog.getString("manifestSha256"),false));owner.applyWorldEditorTerrainPatch(0,111,625,1,0,0,0,0,0,0,false);refused(()->zone.frame(client,frame,settings),"unsupported terrain patch");
  zone.invalidate();check(!zone.ready(),"Java invalidation clears confirmation");check(WholeOverworldZoneRegistry.resolveStatic(0,x,y,baked,0,0)==null,"registry invalidation prevents reuse");
  frame.release();System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("scope","Actual Java capture/reader/filter/resolver semantics with detached partial owned index and actual SceneBaselineState authority with detached owned state. Constructor/full admission, gameplay packets, native upload and GPU remain separate.").toString());
 }
}
