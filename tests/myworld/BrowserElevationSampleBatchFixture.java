package orsc;

import java.lang.reflect.*;
import java.util.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.*;

/** The real World sampler remains the value oracle; no copied elevation formula. */
public final class BrowserElevationSampleBatchFixture {
 static int checks;
 static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Object get(Object o,String n)throws Exception{return BrowserResidentInputsFixture.get(o,n);}
 static void set(Object o,String n,Object v)throws Exception{BrowserResidentInputsFixture.set(o,n,v);}
 static Object call(Object o,String n,Class<?>[] t,Object...args)throws Exception{return BrowserResidentInputsFixture.call(o,n,t,args);}
 static int sample(mudclient c,int x,int z)throws Exception{return (Integer)call(c,"sampleResidentTerrainElevation",new Class<?>[]{int.class,int.class},x,z);}
 static void begin(Object batch)throws Exception{call(batch,"begin",new Class<?>[]{});}
 static NativeLayeredTerrainSnapshot snapshot(int plane,int height){
  String sha="0000000000000000000000000000000000000000000000000000000000000000";
  return new NativeLayeredTerrainSnapshot("fixture","1.0.0",sha,24,"main",plane,2,0,NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,sha,height,1,0,0,0,0,0);
 }
 static class BrokenDef extends GameObjectDef {
  BrokenDef(){super("Tree","fixture","Chop","Examine",1,1,1,0,"tree",0);modelID=0;}
  public int getWidth(){throw new IllegalStateException("fixture definition failure");}
 }
 @SuppressWarnings("unchecked") public static void main(String[]args)throws Exception{
  BrowserResidentInputsFixture.definitions();mudclient c=BrowserResidentInputsFixture.client();World w=(World)get(c,"world");
  BrowserFrameSinkRegistry.install(new BrowserFrameSinkRegistry.Sink(){public void present(java.awt.image.BufferedImage a,orsc.graphics.Renderer2DFrame b,Renderer3DFrame f){}public boolean needsCpuDepthFrame(){return false;}});
  // Shared wall endpoints and repeated presentation identities force real probe hits.
  List<SceneBaselineState.Record> walls=new ArrayList<SceneBaselineState.Record>();
  for(int n=0;n<100;n++)walls.add(new SceneBaselineState.Record(0,15+n%5,16,0,1));set(c,"staticPresentationWallRecords",walls);
  Renderer3DWorldChunkFrame base=BrowserResidentInputsFixture.base(0,0);
  BrowserResidentInputsFixture.compare(c,base,true);Object batch=get(c,"residentElevationSampleBatch");
  boolean selected=!"false".equalsIgnoreCase(System.getProperty("spoiledmilk.browserElevationSampleBatch","true"));
  check((batch!=null)==selected,"Explicit batch policy ignored");
  if(!selected){BrowserResidentInputsFixture.compare(c,base,true);BrowserFrameSinkRegistry.install(null);System.out.println("PASS disabled elevation batch preserves original probe/meshes");return;}
  check(!(Boolean)get(batch,"active"),"Batch leaked beyond descriptor probe");check((Integer)get(batch,"hits")>180,"Repeated actual wall endpoints did not reuse samples");
  int storage=0;for(String n:new String[]{"xs","zs","heights","epochs"})storage+=((int[])get(batch,n)).length*4;check(storage==131072,"Batch memory budget changed");
  Sector[] sectors=(Sector[])get(w,"sectors");Random random=new Random(891231);
  for(Sector sector:sectors)for(int x=0;x<48;x++)for(int z=0;z<48;z++)sector.getTile(x,z).groundElevation=random.nextInt(250);
  begin(batch);for(int n=0;n<6000;n++){int x=n%3==0?random.nextInt(20000)-5000:random.nextInt(),z=n%3==0?random.nextInt(20000)-5000:random.nextInt();int expected=w.getPresentationTerrainElevation(x,z);check(sample(c,x,z)==expected,"Fresh exact sampler differs");check(sample(c,x,z)==expected,"Repeated full-coordinate key differs");}
  check((Integer)get(batch,"entries")==4096,"Unique sample budget not enforced");
  // Deliberate initial-hash collision; full x/z equality must distinguish it.
  begin(batch);for(int[] p:new int[][]{{0,0},{1,0x9e3779b9},{Integer.MIN_VALUE,Integer.MAX_VALUE},{Integer.MAX_VALUE,Integer.MIN_VALUE}})for(int n=0;n<3;n++)check(sample(c,p[0],p[1])==w.getPresentationTerrainElevation(p[0],p[1]),"Collision/extreme source value differs");
  // Epoch wrap cannot resurrect an old height. Mutations happen between batches.
  set(batch,"active",false);int old=sample(c,1280,1280);for(Sector sector:sectors)for(int x=0;x<48;x++)for(int z=0;z<48;z++)sector.getTile(x,z).groundElevation=37;
  check(sample(c,1280,1280)==111&&old!=111,"Outside-scope samples retained stale heights");
  set(batch,"epoch",-1);begin(batch);check(sample(c,1280,1280)==111,"Epoch wrap reused old height");check((Integer)get(batch,"epoch")==1,"Epoch reset wrong");set(batch,"active",false);
  // Real signed-layer active and visual-halo paths, with loaded ACKed tiles
  // retaining priority over immutable snapshots; replace sources between batches.
  set(w,"nativeLayeredTerrainSnapshot",snapshot(-2,17));
  for(int phase=0;phase<3;phase++){
   begin(batch);for(int n=0;n<1000;n++){int x=128*random.nextInt(200)+random.nextInt(128),z=128*random.nextInt(180)+random.nextInt(128);int expected=w.getPresentationTerrainElevation(x,z);check(sample(c,x,z)==expected,"Native/halo interpolation differs");check(sample(c,x,z)==expected,"Repeated native/halo value differs");}
   check(sample(c,150*128+32,60*128+64)==(phase==0?51:phase==1?87:123),"Snapshot/halo publication not fresh between batches");
   check(sample(c,1280,1280)==111,"Loaded editor height lost priority");set(batch,"active",false);
   if(phase==0)set(w,"nativeLayeredTerrainSnapshot",snapshot(-2,29));
   if(phase==1)set(w,"symmetricNativeLayeredTerrainVisualSnapshot",snapshot(-2,41));
  }
  set(w,"nativeLayeredTerrainSnapshot",null);set(w,"symmetricNativeLayeredTerrainVisualSnapshot",null);
  BrowserResidentInputsFixture.compare(c,base,true);
  mudclient empty=BrowserResidentInputsFixture.client();empty.setGameObjectInstanceCount(0);empty.setWallObjectInstanceCount(0);BrowserResidentInputsFixture.compare(empty,base,true);check(get(empty,"residentElevationSampleBatch")==null,"Empty scenery allocated a memo");
  ArrayList<GameObjectDef> defs=(ArrayList<GameObjectDef>)BrowserResidentInputsFixture.field(EntityHandler.class,"objects").get(null);GameObjectDef original=defs.set(0,new BrokenDef());
  boolean threw=false;try{BrowserResidentInputsFixture.inputs(c,base,false);}catch(RuntimeException failure){threw=true;}finally{defs.set(0,original);}
  check(threw,"Exceptional definition did not exercise cleanup");check(!(Boolean)get(batch,"active"),"Exceptional probe retained active scope");BrowserResidentInputsFixture.compare(c,base,true);
  call(c,"clearResidentObjectChunkCache",new Class<?>[]{});check(get(c,"residentElevationSampleBatch")==null,"Area/relog retained memo storage");BrowserResidentInputsFixture.compare(c,base,true);
  BrowserFrameSinkRegistry.install(null);BrowserResidentInputsFixture.compare(c,base,true);check(get(c,"residentElevationSampleBatch")==null,"Native path allocated/retained browser memo");
  System.out.println("PASS "+checks+" actual elevation-value/collision/budget/epoch/frame/exception/native checks plus full input/mesh differential; no GPU/FPS claim");
 }
}
