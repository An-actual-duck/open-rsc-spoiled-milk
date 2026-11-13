package orsc;

import java.lang.reflect.*;
import java.util.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.*;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.*;

/** Differential checks against the unchanged production input builder, not a duplicate algorithm. */
public final class BrowserResidentInputsFixture {
 static int checks;
 static boolean nativeBackend;
 static void backend(boolean available) {
  Renderer3DSettings.setOpenGLPresentationAvailable(nativeBackend && available);
  BrowserFrameSinkRegistry.install(!nativeBackend && available ? new BrowserFrameSinkRegistry.Sink(){public void present(java.awt.image.BufferedImage a,orsc.graphics.Renderer2DFrame v,Renderer3DFrame f){}public boolean needsCpuDepthFrame(){return false;}} : null);
 }
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Field field(Class<?> c,String n)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
 static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
 static Object call(Object o,String n,Class<?>[] t,Object... v)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);try{return m.invoke(o,v);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}}
 static sun.misc.Unsafe unsafe()throws Exception{Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return (sun.misc.Unsafe)f.get(null);}
 static RSModel model(){RSModel m=new RSModel(4,1);int a=m.insertVertex(0,0,0),b=m.insertVertex(128,0,0),c=m.insertVertex(128,-64,128),d=m.insertVertex(0,-64,128);m.insertFace(4,new int[]{a,b,c,d},-12345,-12345,false);m.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);return m;}
 static Renderer3DWorldChunkFrame base(int plane,int origin){int[] xyz={0,0,0,128,0,0,0,0,128};return Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(new Renderer3DWorldChunkFrame.ChunkMesh(plane,7,9,origin,origin,xyz,new float[3],new float[3],new int[3],new int[]{0,1,2},new int[]{-1},new int[]{0x222222},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},null,1,0,0,false,0,700)));}
 @SuppressWarnings("unchecked") static void definitions()throws Exception{
  ArrayList<GameObjectDef> defs=(ArrayList<GameObjectDef>)field(EntityHandler.class,"objects").get(null);defs.clear();
  for(int n=0;n<80;n++){GameObjectDef d=new GameObjectDef("Tree","fixture","Chop","Examine",1,1,1,0,"tree",n);d.modelID=0;defs.add(d);}
  ArrayList<DoorDef> walls=(ArrayList<DoorDef>)field(EntityHandler.class,"doors").get(null);walls.clear();walls.add(new DoorDef("Wall","fixture","WalkTo","Examine",1,0,128,-12345,-12345,0));
 }
 static mudclient client()throws Exception{
  mudclient c=(mudclient)unsafe().allocateInstance(mudclient.class);World w=(World)unsafe().allocateInstance(World.class);
  int count=field(World.class,"ACTIVE_SECTION_COUNT").getInt(null);Sector[] sectors=new Sector[count];for(int n=0;n<count;n++)sectors[n]=Sector.blankLoaded();set(w,"sectors",sectors);
  set(c,"world",w);set(c,"sceneInstanceStore",new ClientSceneInstanceStore());set(c,"tileSize",128);set(c,"modelCache",new RSModel[]{model()});
  set(c,"staticPresentationSceneryRecords",new ArrayList<SceneBaselineState.Record>());set(c,"staticPresentationWallRecords",new ArrayList<SceneBaselineState.Record>());
  set(c,"cachedResidentObjectChunks",new HashMap<Long,Object>());set(c,"cachedResidentObjectChunkCurrentViewCells",new HashSet<Long>());set(c,"cachedResidentObjectChunkPreviousViewCells",new HashSet<Long>());
  c.setGameObjectInstanceCount(3);
  for(int n=0;n<3;n++){c.setGameObjectInstanceID(n,n==2?74:n);c.setGameObjectInstanceX(n,8+n*5);c.setGameObjectInstanceZ(n,11+n);c.setGameObjectInstanceDir(n,0);c.setGameObjectInstanceModel(n,model());c.setGameObjectInstanceMaterialized(n,true);}
  c.setWallObjectInstanceCount(1);c.setWallObjectInstanceID(0,0);c.setWallObjectInstanceX(0,15);c.setWallObjectInstanceZ(0,16);c.setWallObjectInstanceDir(0,0);c.setWallObjectInstanceModel(0,model());c.setWallObjectInstanceMaterialized(0,true);
  return c;
 }
 @SuppressWarnings("unchecked") static List<Object> inputs(mudclient c,Renderer3DWorldChunkFrame b,boolean old)throws Exception{return (List<Object>)call(c,old?"buildResidentObjectChunkInputsUncached":"buildResidentObjectChunkInputs",new Class<?>[]{Renderer3DWorldChunkFrame.class},b);}
 static String inputSignature(List<Object> all)throws Exception{
  StringBuilder s=new StringBuilder();for(Object i:all){for(String f:new String[]{"cellKey","localOriginTileX","localOriginTileZ","chunkRole","modelCount","cacheKey","canonicalContentKey","liveModelCount","presentationModelCount","debugMatchedModelCount","debugFirstModelSummary"})s.append(get(i,f)).append(',');
   Object a=get(i,"anchor");for(String f:new String[]{"plane","centerSectionX","centerSectionY","originWorldX","originWorldZ"})s.append(get(a,f)).append(',');
   for(Object d:(Object[])get(i,"canonicalIdentities"))for(String f:new String[]{"kind","worldTileX","worldTileZ","objectId","direction","elevation1","elevation2"})s.append(get(d,f)).append(',');
   for(RSModel m:(RSModel[])get(i,"models"))s.append(System.identityHashCode(m)).append('/').append(m.getRenderer3DTransformVersion()).append(';');s.append('|');
  }return s.toString();
 }
 static String meshSignature(mudclient c,List<Object> all)throws Exception{
  StringBuilder s=new StringBuilder();for(Object input:all){Renderer3DWorldChunkFrame.ChunkMesh m=(Renderer3DWorldChunkFrame.ChunkMesh)call(c,"buildResidentObjectChunkMesh",new Class<?>[]{input.getClass()},input);if(m==null){s.append("null");continue;}
   s.append(m.getPlane()).append('/').append(m.getChunkRole()).append('/').append(m.getOriginWorldX()).append('/').append(m.getOriginWorldZ());
   for(int n=0;n<m.getVertexCount()*3;n++)s.append(':').append(m.getVertexCoord(n));
   for(int n=0;n<m.getVertexCount();n++)s.append(':').append(Float.floatToRawIntBits(m.getVertexTextureU(n))).append(':').append(Float.floatToRawIntBits(m.getVertexTextureV(n))).append(':').append(m.getVertexLight(n)).append(':').append(m.getVertexNormalX(n)).append(':').append(m.getVertexNormalY(n)).append(':').append(m.getVertexNormalZ(n)).append(':').append(m.getVertexTerrainBlendColor(n)).append(':').append(m.getVertexTerrainBlendStrength(n));
   for(int n=0;n<m.getIndexCount();n++)s.append(':').append(m.getIndex(n));for(int n=0;n<m.getTriangleCount();n++)s.append(':').append(m.getTriangleTexture(n)).append(':').append(m.getTriangleFallbackColor(n)).append(':').append(m.getTriangleModelKind(n)).append(':').append(m.getTriangleMaterialFamily(n)).append(':').append(m.getTriangleTerrainVariationMask(n));s.append('|');
  }return s.toString();
 }
 static List<Object> compare(mudclient c,Renderer3DWorldChunkFrame b,boolean meshes)throws Exception{
  List<Object> a=inputs(c,b,false),r=inputs(c,b,true);check(inputSignature(a).equals(inputSignature(r)),"Cached inputs differ from production builder");if(meshes)check(meshSignature(c,a).equals(meshSignature(c,r)),"Canonical/animated mesh bytes differ");return a;
 }
 static Object firstStatic(List<Object> a)throws Exception{for(Object i:a)if((Integer)get(i,"chunkRole")==Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS)return i;return null;}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  nativeBackend=args.length>0 && args[0].equals("native");
  definitions();mudclient c=client();Renderer3DWorldChunkFrame b=base(0,0);backend(true);
  boolean enabled=nativeBackend ? !"false".equalsIgnoreCase(System.getProperty("spoiledmilk.nativeResidentInputCache","true")) : !"false".equalsIgnoreCase(System.getProperty("spoiledmilk.browserResidentInputCache","true"));
  List<Object> first=compare(c,b,true),second=compare(c,b,true);check((firstStatic(first)==firstStatic(second))==enabled,"Static reuse does not follow backend policy");
  Object fixed=firstStatic(second);for(int n=0;n<30;n++){c.getGameObjectInstanceModel(2).addRotation(0,5,0);List<Object> next=compare(c,b,true);if(enabled)check(firstStatic(next)==fixed,"Animation invalidated stable static groups");}
  c.setGameObjectInstanceDir(0,2);compare(c,b,true);c.setWallObjectInstanceDir(0,1);compare(c,b,true);
  c.setGameObjectInstanceMaterialized(0,false);compare(c,b,true);c.setGameObjectInstanceMaterialized(0,true);compare(c,b,true);
  c.setGameObjectInstanceModel(1,null);compare(c,b,true);c.setGameObjectInstanceModel(1,model());compare(c,b,true);
  Sector[] sectors=(Sector[])get(get(c,"world"),"sectors");for(Sector sector:sectors)for(int x=0;x<48;x++)for(int z=0;z<48;z++)sector.getTile(x,z).groundElevation=12;compare(c,b,true);
  List<SceneBaselineState.Record> scenery=(List<SceneBaselineState.Record>)get(c,"staticPresentationSceneryRecords");scenery.add(new SceneBaselineState.Record(0,44,41,4,0));compare(c,b,true);scenery.set(0,new SceneBaselineState.Record(1,45,42,0,0));compare(c,b,true);
  List<SceneBaselineState.Record> walls=(List<SceneBaselineState.Record>)get(c,"staticPresentationWallRecords");walls.add(new SceneBaselineState.Record(0,32,34,2,1));compare(c,b,true);
  Collections.reverse(scenery);set(c,"staticPresentationRevision",5L);compare(c,b,true);
  b=base(-3,0);compare(c,b,true);set(c,"midRegionBaseX",48);set(c,"midRegionBaseZ",-48);compare(c,b,true);b=base(-3,6144);compare(c,b,true);
  field(GameObjectDef.class,"objectModel").set(EntityHandler.getObjectDef(0),"portal");compare(c,b,true);field(GameObjectDef.class,"objectModel").set(EntityHandler.getObjectDef(0),"tree");compare(c,b,true);
  EntityHandler.getObjectDef(1).modelID=-1;compare(c,b,true);EntityHandler.getObjectDef(1).modelID=0;EntityHandler.getObjectDef(1).width=3;compare(c,b,true);
  Random random=new Random(531122);for(int n=0;n<200;n++){int i=random.nextInt(2);c.setGameObjectInstanceX(i,random.nextInt(80));c.setGameObjectInstanceZ(i,random.nextInt(80));c.setGameObjectInstanceDir(i,random.nextInt(8));c.setGameObjectInstanceID(i,random.nextInt(4));c.setWallObjectInstanceDir(0,random.nextInt(4));compare(c,b,n%10==0);}
  call(c,"clearResidentObjectChunkCache",new Class<?>[]{});check(get(c,"residentStaticInputCache")==null&&get(c,"residentStaticInputProbe")==null,"Area/relog clear retained input cache");compare(c,b,true);
  backend(false);List<Object> nativeA=compare(c,b,true),nativeB=compare(c,b,true);check(firstStatic(nativeA)!=firstStatic(nativeB),"Unavailable backend retained input cache");
  check(get(c,"residentStaticInputCache")==null && get(c,"residentStaticInputProbe")==null,"Unavailable backend retained cache storage");
  backend(true);
  set(c,"staticPresentationSceneryRecords",Collections.nCopies(131073,new SceneBaselineState.Record(0,2,2,0,0)));inputs(c,b,false);check(get(c,"residentStaticInputCache")==null&&get(c,"residentStaticInputProbe")==null,"Oversized input set bypassed descriptor bound");
  backend(false);System.out.println("PASS "+checks+" actual static-input reuse/animation/canonical mesh/elevation/definition/door/instance/plane/rebase/reset/default/budget checks; selected="+enabled+"; native="+nativeBackend+"; no GPU/FPS claim");
 }
}
