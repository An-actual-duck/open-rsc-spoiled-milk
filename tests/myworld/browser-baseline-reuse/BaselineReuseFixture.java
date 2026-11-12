package orsc;
import java.lang.reflect.*;
import java.util.*;
import java.security.*;
import java.nio.file.*;
import com.openrsc.client.entityhandling.*;
import com.openrsc.client.entityhandling.defs.*;
import com.openrsc.client.model.*;
import orsc.graphics.three.*;
import orsc.graphics.two.*;

/** Actual full-JAR PacketHandler baseline materialization; no socket/display/GL. */
public final class BaselineReuseFixture {
 static int checks,serial,frames;static MessageDigest digest;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Field field(Class<?> c,String n)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
 static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
 static Object call(Object o,String n,Class<?>[] t,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);try{return m.invoke(o,a);}catch(InvocationTargetException e){throw new RuntimeException(n,e.getCause());}}
 static void hash(Object v)throws Exception{
  if(v==null){digest.update((byte)0);return;}Class<?> c=v.getClass();
  if(c.isArray()){for(int i=0;i<Array.getLength(v);i++)hash(Array.get(v,i));digest.update((byte)127);return;}
  if(v instanceof Number||v instanceof Boolean||v instanceof String||c.isEnum()){digest.update(String.valueOf(v).getBytes("UTF-8"));digest.update((byte)0);return;}
  for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);hash(f.get(v));}
 }
 static RSModel model(){RSModel m=new RSModel(12,4);int a=m.insertVertex(-45,0,-45),b=m.insertVertex(45,0,-45),c=m.insertVertex(0,-180,0),d=m.insertVertex(45,0,45),e=m.insertVertex(-45,0,45);m.insertFace(3,new int[]{a,b,c},-12345,-12345,false);m.insertFace(3,new int[]{b,d,c},-22222,-22222,false);m.insertFace(3,new int[]{d,e,c},-12345,-12345,false);m.insertFace(3,new int[]{e,a,c},-22222,-22222,false);m.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);return m;}
 @SuppressWarnings("unchecked")static void definitions()throws Exception{
  List<GameObjectDef> objects=(List<GameObjectDef>)field(EntityHandler.class,"objects").get(null);objects.clear();
  for(int n=0;n<80;n++){GameObjectDef d=new GameObjectDef("Tree","fixture","Chop","Examine",1,1,1,0,n==74?"portal":"tree",n);d.modelID=0;objects.add(d);}
  List<DoorDef> doors=(List<DoorDef>)field(EntityHandler.class,"doors").get(null);doors.clear();doors.add(new DoorDef("Wall","fixture","WalkTo","Examine",1,0,128,-12345,-12345,0));
 }
 static NativeLayeredTerrainSnapshot source(int level,String scope,char sha){char[] chars=new char[64];Arrays.fill(chars,sha);String h=new String(chars);return new NativeLayeredTerrainSnapshot("fixture","1.0.0",h,24,scope,level,1,1,NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,h,0,0,0,0,0,0,0);}
 static mudclient client()throws Exception{
  Field u=field(sun.misc.Unsafe.class,"theUnsafe");mudclient mc=(mudclient)((sun.misc.Unsafe)u.get(null)).allocateInstance(mudclient.class);
  MudClientGraphics g=new MudClientGraphics(256,192,1);Scene scene=new Scene(g,32,1000,32);World w=new World(scene,g);
  Sector[] sectors=(Sector[])get(w,"sectors");for(int i=0;i<sectors.length;i++)sectors[i]=Sector.blankLoaded();
  set(w,"nativeLayeredTerrainSnapshot",source(0,"global",'a'));
  set(mc,"world",w);set(mc,"scene",scene);set(mc,"sceneInstanceStore",new ClientSceneInstanceStore());set(mc,"tileSize",128);set(mc,"modelCache",new RSModel[]{model()});mc.setSurface(g);
  RSModel foreign=model();foreign.key=9900;scene.addModel(foreign);
  PacketHandler packet=new PacketHandler(mc);mc.packetHandler=packet;scene.setMidpoints(96,true,256,128,96,9,256);scene.fogLandscapeDistance=20000;scene.fogEntityDistance=20000;scene.setCamera(3200,-200,3200,768,0,0,6000);
  return mc;
 }
 static SceneBaselineState.Record r(int id,int x,int z,int dir){return new SceneBaselineState.Record(id,x,z,dir,0);}
 static ArrayList<SceneBaselineState.Record> records(int start,int count){ArrayList<SceneBaselineState.Record> r=new ArrayList<>();for(int i=start;i<start+count;i++)r.add(r(i%3,10+(i%20)*2,10+(i/20)*2,i%8));return r;}
 static void baseline(mudclient mc,List<SceneBaselineState.Record> records)throws Exception{
  SceneBaselineState state=(SceneBaselineState)get(mc.packetHandler,"sceneBaselineState");int k=++serial;
  set(state,"staticSceneKey",k);set(state,"storedStaticSceneKey",k);set(state,"storedSceneryRecords",records);set(state,"scenery",records.size());set(state,"localX",40);set(state,"localY",40);
  List<SceneBaselineState.Record> walls=Arrays.asList(new SceneBaselineState.Record(0,25,25,k%4,1),new SceneBaselineState.Record(0,26,25,(k+1)%4,1));
  set(state,"storedWallRecords",walls);set(state,"walls",walls.size());
  call(mc.packetHandler,"applyCompleteSceneBaselineToLegacyLists",new Class<?>[]{boolean.class},true);
  check(mc.getGameObjectInstanceCount()==records.size(),"complete ordered count");
  for(int i=0;i<records.size();i++){SceneBaselineState.Record r=records.get(i);check(mc.getGameObjectInstanceID(i)==r.id&&mc.getGameObjectInstanceX(i)+mc.getMidRegionBaseX()==r.x&&mc.getGameObjectInstanceZ(i)+mc.getMidRegionBaseZ()==r.y&&mc.getGameObjectInstanceDir(i)==r.direction,"ordered record "+i);check(mc.getGameObjectInstanceModel(i).key==i,"picking index");check(mc.isGameObjectInstanceMaterialized(i),"terrain materialization");}
  snapshot(mc);
 }
 static void snapshot(mudclient mc)throws Exception{
  Scene s=mc.getScene();RSModel[] models=(RSModel[])get(s,"models");int count=(Integer)get(s,"modelCount");check(count==mc.getGameObjectInstanceCount()+mc.getWallObjectInstanceCount()+1,"no retired Scene references");
  RSModel[] active=Arrays.copyOf(models,count);check(active[0].key==9900,"foreign terrain/actor model order retained");hash(Arrays.copyOf((int[])get(s,"m_jb"),count));hash(count);for(RSModel m:active)hash(m.key);
  Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,active,count);hash(mesh);
  hash(get(mc.getWorld(),"collisionFlags"));hash(get(mc.getWorld(),"tileDirection"));
  s.setMouseLoc(0,128,96);Arrays.fill(mc.getSurface().pixelData,0x23262c);s.endScene(0);hash(mc.getSurface().pixelData);int picks=s.b(0);hash(picks);for(int i=0;i<picks;i++)hash(s.b((byte)100)[i].key);
  frames++;
 }
 static long reuse(mudclient mc){return mc.packetHandler.getBrowserBaselineReuseWork()[2];}
 static void proofMutations()throws Exception{
  RSModel s=model(),m=s.clone();m.addRotation(0,64,0);m.setTranslate(1,2,3);m.setDiffuseLightAndColor(-50,-10,-50,48,48,true,117);
  RSModel.BrowserBaselineProof p=RSModel.browserBaselineProof(s,m,1<<20);check(p!=null&&p.matchesSource(s)&&p.matchesModelGeometry(m)&&m.isBrowserBaselineRotation(2),"exact proof initial");
  check(RSModel.browserBaselineProof(s,m,1)==null,"budget refusal before copy");
  ((int[])get(m,"vertX"))[0]++;check(!p.matchesModelGeometry(m),"raw vertex mutation refused");((int[])get(m,"vertX"))[0]--;
  m.setInteractionOnly(true);check(!p.matchesModelGeometry(m),"interaction material mutation refused");
  ((int[])get(s,"vertXTransform"))[0]++;check(!p.matchesSource(s),"source transformed geometry mutation refused");
 }
 public static void main(String[] args)throws Exception{
  digest=MessageDigest.getInstance("SHA-256");Config.F_CACHE_DIR=Paths.get(args[0],"Client_Base/Cache").toString();definitions();Renderer3DSettings.setBrowserPresentationAvailable(true);
  RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
  mudclient mc=client();boolean enabled=Boolean.getBoolean("spoiledmilk.browserBaselineReuse")&&Boolean.getBoolean("spoiledmilk.browserBenchmark")&&Boolean.getBoolean("spoiledmilk.browserWholeRam")&&Boolean.getBoolean("spoiledmilk.browserStaticSceneReuse")&&Boolean.getBoolean("spoiledmilk.browserWholeZone");
  baseline(mc,records(0,100));long before=reuse(mc);baseline(mc,records(10,100));check(!enabled||reuse(mc)>before,"warm reuse occurs");baseline(mc,records(0,100));
  // Every reuse still samples new heights and repeats the original collision/normal path.
  for(Sector sector:(Sector[])get(mc.getWorld(),"sectors"))for(int x=0;x<48;x++)for(int z=0;z<48;z++)sector.getTile(x,z).groundElevation=12;
  baseline(mc,records(10,100));
  RSModel[] changed={mc.getGameObjectInstanceModel(0),mc.getGameObjectInstanceModel(1),mc.getGameObjectInstanceModel(2)};
  changed[0].addRotation(0,1,0);((int[])get(changed[1],"vertX"))[0]+=7;changed[2].setInteractionOnly(true);baseline(mc,records(0,100));
  for(int i=0;i<3;i++)check(mc.getGameObjectInstanceModel(10+i)!=changed[i],"mutated instance is cloned afresh");
  // Derived lighting is initialized again rather than retained as a cache proof.
  mc.getGameObjectInstanceModel(0).setDiffuseLightAndColor(50,30,10,20,30,false,117);baseline(mc,records(10,100));
  // Source template/definition/settings changes must never reuse previous geometry.
  before=reuse(mc);mc.getModelCacheItem(0).setTranslate(5,0,0);baseline(mc,records(10,100));check(reuse(mc)==before,"changed source transform refuses old template");
  EntityHandler.getObjectDef(0).width=2;baseline(mc,records(0,100));RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);baseline(mc,records(10,100));RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
  ArrayList<SceneBaselineState.Record> duplicate=records(0,90);duplicate.add(duplicate.get(0));duplicate.add(duplicate.get(0));duplicate.add(r(74,12,40,0));baseline(mc,duplicate);check(mc.getGameObjectInstanceModel(0)!=mc.getGameObjectInstanceModel(90)&&mc.getGameObjectInstanceModel(90)!=mc.getGameObjectInstanceModel(91),"duplicates own distinct models");baseline(mc,records(10,100));
  for(int level:new int[]{-1,10,0}){set(mc.getWorld(),"nativeLayeredTerrainSnapshot",source(level,"global",'a'));before=reuse(mc);baseline(mc,records(level==0?0:10,100));check(level==0||reuse(mc)==before,"signed level literal fallback");}
  set(mc.getWorld(),"nativeLayeredTerrainSnapshot",source(0,"other",'a'));before=reuse(mc);baseline(mc,records(10,100));check(reuse(mc)==before,"non-global fallback");set(mc.getWorld(),"nativeLayeredTerrainSnapshot",source(0,"global",'b'));baseline(mc,records(0,100));
  // Rebase/reversal uses the actual instance materialization operations, with a new loaded origin.
  for(int origin:new int[]{4,-4,0}) {
   int delta=origin-mc.getMidRegionBaseX();
   for(int i=0;i<mc.getGameObjectInstanceCount();i++)mc.dematerializeGameObjectInstance(i);
   for(int i=0;i<mc.getWallObjectInstanceCount();i++)mc.dematerializeWallObjectInstance(i);
   set(mc,"midRegionBaseX",origin);
   for(int i=0;i<mc.getGameObjectInstanceCount();i++){mc.setGameObjectInstanceX(i,mc.getGameObjectInstanceX(i)-delta);mc.materializeGameObjectInstance(i);}
   for(int i=0;i<mc.getWallObjectInstanceCount();i++){mc.setWallObjectInstanceX(i,mc.getWallObjectInstanceX(i)-delta);mc.materializeWallObjectInstance(i);}
   baseline(mc,records(origin==0?0:10,100));
  }
  Map patches=(Map)get(mc.getWorld(),"worldEditorTerrainPatches");patches.put(1L,null);before=reuse(mc);baseline(mc,records(10,100));check(reuse(mc)==before,"live terrain patch guard");patches.clear();
  BrowserBaselineObjectReuse work=(BrowserBaselineObjectReuse)get(mc.packetHandler,"browserBaselineReuse");work.clear();before=reuse(mc);baseline(mc,records(10,100));check(reuse(mc)==before,"reset drops all old proofs");check(work.peakBytes<=BrowserBaselineObjectReuse.MAX_BYTES,"workspace admission budget");
  mudclient over=client();over.setGameObjectInstanceCount(BrowserBaselineObjectReuse.MAX_OBJECTS+1);check(!new BrowserBaselineObjectReuse().begin(over,records(0,1)),"entry capacity refuses before model scan");
  mudclient duplicated=client();duplicated.setGameObjectInstanceCount(2);RSModel shared=model();duplicated.setGameObjectInstanceModel(0,shared);duplicated.setGameObjectInstanceModel(1,shared);check(!new BrowserBaselineObjectReuse().begin(duplicated,records(0,2)),"shared model references require literal path");
  proofMutations();StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));System.out.println("TRACE "+hex+" frames="+frames);System.out.println("PASS checks="+checks+" work="+Arrays.toString(mc.packetHandler.getBrowserBaselineReuseWork())+" CPU-only full maintained JAR; no browser/GPU/FPS claim");
 }
}
