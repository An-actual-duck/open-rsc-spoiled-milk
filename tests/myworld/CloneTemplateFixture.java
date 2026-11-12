package orsc;
import java.io.*;import java.util.*;import java.util.concurrent.*;import java.nio.file.*;import java.security.*;
import com.openrsc.client.entityhandling.defs.*;import java.lang.reflect.*;
import orsc.buffers.*;import orsc.graphics.three.*;import com.openrsc.client.entityhandling.*;
/** Real packet DELTA decode/reconcile, canonical factory, complete model graphs and aliases. */
public final class CloneTemplateFixture {
 static int checks,packets;static MessageDigest digest;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void hashModel(RSModel m)throws Exception{WholeRamData.Pool p=new WholeRamData.Pool();int d=RSModel.capturePreparedState(m,p);ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(d);p.write(out);digest.update(bytes.toByteArray());}
 static boolean active(){return BrowserPresentationCloneCache.isEnabled()&&Boolean.getBoolean("spoiledmilk.browserWholeRam")&&WholeOverworldZoneRegistry.isStaticSceneReuseEnabled();}
 static void delta(mudclient mc,int pass)throws Exception{
  RSBuffer_Bits b=mc.packetHandler.getPacketsIncoming();b.packetEnd=0;int count=24;
  for(int n=0;n<count;n++){int id=n%3,x=12+n%8*2,z=12+n/8*2,dir=(pass+n)%8;b.dataBuffer[b.packetEnd++]=(byte)(id>>8);b.dataBuffer[b.packetEnd++]=(byte)id;b.dataBuffer[b.packetEnd++]=(byte)x;b.dataBuffer[b.packetEnd++]=(byte)z;b.dataBuffer[b.packetEnd++]=(byte)dir;}
  int length=b.packetEnd;b.packetEnd=0;BaselineReuseFixture.call(mc.packetHandler,"showGameObjects",new Class<?>[]{int.class},length);check(b.packetEnd==length,"actual DELTA packet consumed");check(mc.getGameObjectInstanceCount()==count,"ordered DELTA output count");
  IdentityHashMap<RSModel,Boolean> seen=new IdentityHashMap<RSModel,Boolean>();for(int i=0;i<count;i++){RSModel m=mc.getGameObjectInstanceModel(i);check(seen.put(m,true)==null,"each placement owns mutable instance");check(m.key==i,"picking index retained");hashModel(m);}BaselineReuseFixture.snapshot(mc);hashModel(mc.getModelCacheItem(0));packets++;
 }
 public static void main(String[]args)throws Exception{
  digest=MessageDigest.getInstance("SHA-256");BaselineReuseFixture.digest=digest;Config.F_CACHE_DIR=Paths.get(args[0],"Client_Base/Cache").toString();BaselineReuseFixture.definitions();Renderer3DSettings.setBrowserPresentationAvailable(true);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);mudclient mc=BaselineReuseFixture.client();
  for(int n=0;n<8;n++)delta(mc,n);
  long[] work=mc.getBrowserCloneTemplateWork();check(!active()||work[3]>100,"actual warm DELTA owner has recipe hits");
  // Dirty source transform must commit exactly as literal clone, then rebuild recipe.
  mc.getModelCacheItem(0).addRotation(0,32,0);mc.getModelCacheItem(0).setTranslate(17,-9,31);delta(mc,8);
  ((int[])BaselineReuseFixture.get(mc.getModelCacheItem(0),"faceTextureFront"))[0]=-32123;delta(mc,9);
  EntityHandler.getObjectDef(1).width=2;delta(mc,10);
  // Caller mutation cannot touch the source snapshot, recipe metadata or other copies.
  RSModel one=mc.cloneBrowserPresentationGameObject(0,2),two=mc.cloneBrowserPresentationGameObject(0,2);check(one!=two,"recipe result identity");((int[])BaselineReuseFixture.get(one,"vertX"))[0]+=77;hashModel(two);hashModel(mc.cloneBrowserPresentationGameObject(0,2));
  // Exercise the real canonical presentation factory and original per-placement finishing.
  for(int n=0;n<4;n++){RSModel m=(RSModel)BaselineReuseFixture.call(mc,"createStaticPresentationGameObjectModel",new Class<?>[]{SceneBaselineState.Record.class,int.class,int.class},new SceneBaselineState.Record(0,35,36,n,0),n,19+n);hashModel(m);check(m.key==-1-n,"canonical pick key");}
  for(int n=0;n<3;n++)BaselineReuseFixture.baseline(mc,BaselineReuseFixture.records(n%2*10,30));
  // Unknown/animated/signed/private/scope/settings/patch branches use literal clone.
  for(int id:new int[]{74,0}){RSModel m=mc.cloneBrowserPresentationGameObject(id,2);hashModel(m);}
  for(int level:new int[]{-1,10,0}){BaselineReuseFixture.set(mc.getWorld(),"nativeLayeredTerrainSnapshot",BaselineReuseFixture.source(level,"global",'a'));hashModel(mc.cloneBrowserPresentationGameObject(0,2));}
  BaselineReuseFixture.set(mc.getWorld(),"nativeLayeredTerrainSnapshot",BaselineReuseFixture.source(0,"other",'a'));hashModel(mc.cloneBrowserPresentationGameObject(0,2));BaselineReuseFixture.set(mc.getWorld(),"nativeLayeredTerrainSnapshot",BaselineReuseFixture.source(0,"global",'b'));hashModel(mc.cloneBrowserPresentationGameObject(0,2));RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);hashModel(mc.cloneBrowserPresentationGameObject(0,2));RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
  Map patches=(Map)BaselineReuseFixture.get(mc.getWorld(),"worldEditorTerrainPatches");patches.put(1L,null);hashModel(mc.cloneBrowserPresentationGameObject(0,2));patches.clear();
  // Concurrent known-factory requests must own independent live arrays. Validate
  // equality before mutating each output, without requiring scheduler ordering.
  ExecutorService executor=Executors.newFixedThreadPool(4);List<Future<RSModel>> futures=new ArrayList<Future<RSModel>>();for(int n=0;n<80;n++)futures.add(executor.submit(()->mc.cloneBrowserPresentationGameObject(0,2)));Set<int[]> arrays=Collections.newSetFromMap(new IdentityHashMap<int[],Boolean>());for(Future<RSModel> f:futures){RSModel m=f.get();check(arrays.add((int[])BaselineReuseFixture.get(m,"vertX")),"concurrent copy arrays distinct");hashModel(m);}executor.shutdown();check(executor.awaitTermination(10,TimeUnit.SECONDS),"workers terminated");
  // Large recipes and full-cache admissions must preserve literal valid output.
  RSModel large=new RSModel(3,1025);int la=large.insertVertex(0,0,0),lb=large.insertVertex(128,0,0),lc=large.insertVertex(0,-128,0);for(int n=0;n<1025;n++)large.insertFace(3,new int[]{la,lb,lc},-12345,-12345,false);
  RSModel previous=mc.getModelCacheItem(0);BaselineReuseFixture.set(mc,"modelCache",new RSModel[]{large});long priorHits=mc.getBrowserCloneTemplateWork()[9];hashModel(mc.cloneBrowserPresentationGameObject(0,2));hashModel(mc.cloneBrowserPresentationGameObject(0,2));check(mc.getBrowserCloneTemplateWork()[9]==priorHits,"oversize recipe uses literal clone");BaselineReuseFixture.set(mc,"modelCache",new RSModel[]{previous});
  List<GameObjectDef> defs=(List<GameObjectDef>)BaselineReuseFixture.field(EntityHandler.class,"objects").get(null);while(defs.size()<300){int id=defs.size();GameObjectDef d=new GameObjectDef("Tree","fixture","Chop","Examine",1,1,1,0,"tree",id);d.modelID=0;defs.add(d);}for(int id=0;id<300;id++){if(id==74)continue;hashModel(mc.cloneBrowserPresentationGameObject(id,2));}check(mc.getBrowserCloneTemplateWork()[15]<=256,"template-count cap");
  WholeRamData.Pool tiny=new WholeRamData.Pool(1);boolean denied=false;try{tiny.intern(new int[32]);}catch(IllegalStateException expectedBudget){denied=true;}check(denied&&tiny.bytes()==0&&tiny.retainedBytes()==0&&tiny.size()==0,"pool refuses before primitive ownership allocation");
  work=mc.getBrowserCloneTemplateWork();check(work[13]+BrowserPresentationCloneCache.TRANSIENT_RESERVE<=BrowserPresentationCloneCache.MAX_BYTES,"retained budget");check(work[14]<=BrowserPresentationCloneCache.MAX_BYTES,"conservative peak budget");check(!active()||work[9]>20,"canonical owner hits observed");
  RSModel source=BaselineReuseFixture.model(),proto=source.clone();check(RSModel.browserCloneTemplate(source,proto,1)==null,"pre-copy tiny budget refusal");RSModel.BrowserCloneTemplate t=RSModel.browserCloneTemplate(source,proto,1<<20);check(t!=null&&t.matches(source),"standalone exact recipe");RSModel a=t.instantiate(),b=t.instantiate();check(BaselineReuseFixture.get(a,"vertX")!=BaselineReuseFixture.get(b,"vertX"),"owned instantiate arrays");hashModel(a);hashModel(b);mc.clearBrowserPresentationCloneCache();check(mc.getBrowserCloneTemplateWork()[13]==0,"explicit reset releases recipes");
  StringBuilder hex=new StringBuilder();for(byte v:digest.digest())hex.append(String.format("%02x",v&255));System.out.println("TRACE "+hex+" deltaPackets="+packets);System.out.println("PASS checks="+checks+" work="+Arrays.toString(work)+" full maintained JAR; CPU only, no GPU/FPS claim");
 }
}
