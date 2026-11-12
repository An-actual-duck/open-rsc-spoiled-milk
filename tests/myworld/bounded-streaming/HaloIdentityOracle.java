package orsc;
import orsc.graphics.three.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import com.openrsc.client.model.Sector;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
/** Actual cached versus original canonical identities across halo/live ownership changes. */
public final class HaloIdentityOracle {
 static int checks,cases;static Method retained,literal;static String[] fields={"kind","worldTileX","worldTileZ","objectId","direction","elevation1","elevation2"};
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static void set(Object x,String n,Object v)throws Exception{WholeZoneDynamicFixture.set(x,n,v);}
 static Object get(Object x,String n)throws Exception{return WholeZoneDynamicFixture.get(x,n);}
 static Object compare(mudclient client,Renderer3DModelKind kind,int x,int y,int id,int dir,boolean reset)throws Exception{
  if(reset)set(client,"residentCanonicalMemoCursor",0);Object original=literal.invoke(client,kind,x,y,id,dir),cached=retained.invoke(client,kind,x,y,id,dir);
  for(String f:fields)check(Objects.equals(get(original,f),get(cached,f)),"identity field "+f);cases++;return cached;
 }
 static void fill(Sector sector,int elevation){for(int x=0;x<48;x++)for(int z=0;z<48;z++)sector.getTile(x,z).groundElevation=elevation;}
 @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception{
  Config.F_CACHE_DIR=args[2];Path packageRoot=Paths.get(args[1]);JSONObject source=WholeZoneDynamicFixture.json(packageRoot.resolve("manifest.json")),catalog=WholeZoneDynamicFixture.json(Paths.get(args[0]));String hash=catalog.getString("manifestSha256");
  System.setProperty("spoiledmilk.browserStaticSceneReuse","true");World world=new World(null,null);NativeLayeredTerrainSnapshot initial=StaticSceneReuseOracle.recentered(source,packageRoot,hash,2,13);set(world,"nativeLayeredTerrainSnapshot",initial);set(world,"nativeLayeredTerrainAppliedSectionX",2);set(world,"nativeLayeredTerrainAppliedSectionY",13);
  Sector[] sectors=(Sector[])get(world,"sectors");for(int i=0;i<sectors.length;i++)sectors[i]=new Sector();
  mudclient client=StaticSceneReuseOracle.client(world,new SceneBaselineState());set(client,"midRegionBaseX",48);set(client,"midRegionBaseZ",576);set(client,"tileSize",128);
  ArrayList<GameObjectDef> defs=(ArrayList<GameObjectDef>)WholeZoneDynamicFixture.field(EntityHandler.class,"objects").get(null);defs.clear();GameObjectDef definition=new GameObjectDef("Tree","owned oracle","Chop","Examine",1,1,1,0,"tree",0);definition.modelID=0;defs.add(definition);
  Class<?>[] types={Renderer3DModelKind.class,int.class,int.class,int.class,int.class};retained=WholeZoneDynamicFixture.method(mudclient.class,"createResidentObjectCanonicalIdentity",types);literal=WholeZoneDynamicFixture.method(mudclient.class,"createResidentObjectCanonicalIdentityUncached",types);
  check(world.getWholeZoneImmutableElevationContext()!=null,"private global halo context exists");check(world.getPresentationTerrainElevation(85*128,84*128)!=World.PRESENTATION_ELEVATION_UNAVAILABLE,"actual source height nonvacuous");
  Object first=compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true),second=compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);check(first==second,"owned halo identity retained");
  for(Renderer3DModelKind kind:new Renderer3DModelKind[]{Renderer3DModelKind.GAME_OBJECT,Renderer3DModelKind.WALL_OBJECT})for(int direction=0;direction<8;direction++)for(int n=0;n<3;n++)compare(client,kind,133,660,0,direction,true);
  // The native snapshot is deliberately unchanged while an actual loaded sector takes ownership.
  Object context=world.getWholeZoneImmutableElevationContext();sectors[4]=Sector.blankLoaded();fill(sectors[4],12);Object loaded=compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);check(world.getWholeZoneImmutableElevationContext()==context,"live sector transition did not replace source token");check((Integer)get(loaded,"elevation1")==36,"loaded sector owns actual height");fill(sectors[4],31);loaded=compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);check((Integer)get(loaded,"elevation1")==93,"live mutation cannot reuse prior halo height");
  // The fourth interpolation vertex can be the only vertex in a loaded sector.
  for(int i=0;i<sectors.length;i++)sectors[i]=new Sector();sectors[4]=Sector.blankLoaded();check(!world.isWholeZoneImmutableElevationSample(47*128,47*128),"fourth vertex rejects halo ownership");compare(client,Renderer3DModelKind.WALL_OBJECT,95,623,0,0,true);
  sectors[4]=new Sector();compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);
  for(int width:new int[]{2,3,1}){definition.width=width;definition.height=4;compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,2,true);}definition.height=1;definition.modelID=-1;compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);definition.modelID=0;compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);
  // Every input to World presentation coordinates must invalidate or miss exactly.
  for(String name:new String[]{"nativeLayeredTerrainAppliedSectionX","nativeLayeredTerrainAppliedSectionY","syntheticDeepFixtureOffsetX","syntheticDeepFixtureOffsetZ"}){int prior=(Integer)get(world,name);set(world,name,prior+48);compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);set(world,name,prior);compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);}
  for(String name:new String[]{"midRegionBaseX","midRegionBaseZ","tileSize"}){int prior=(Integer)get(client,name);set(client,name,prior+1);compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);set(client,name,prior);compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);}
  set(world,"nativeLayeredTerrainSnapshot",StaticSceneReuseOracle.recentered(source,packageRoot,hash,3,13));compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);set(world,"nativeLayeredTerrainSnapshot",initial);set(world,"symmetricNativeLayeredTerrainVisualSnapshot",StaticSceneReuseOracle.recentered(source,packageRoot,hash,2,13));compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);
  for(int level:new int[]{-1,10}){set(world,"nativeLayeredTerrainSnapshot",WholeZoneDynamicFixture.snapshot(source,packageRoot,hash,false,level,"global"));check(world.getWholeZoneImmutableElevationContext()==null,"signed level refuses immutable-global proof");compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);}set(world,"nativeLayeredTerrainSnapshot",initial);
  client.setRequestedPlane(1);int hits=client.getBrowserStaticIdentityWork()[2];compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);check(client.getBrowserStaticIdentityWork()[2]==hits,"nonzero requested plane remains literal");client.setRequestedPlane(0);
  // Reordering and oversize input use the real original identity as an oracle.
  set(client,"residentCanonicalMemoCursor",0);for(int n=0;n<8200;n++)compare(client,Renderer3DModelKind.WALL_OBJECT,133,660,0,n%4,false);int[] work=client.getBrowserStaticIdentityWork();check(work[6]<=8192&&work[7]>0,"retention capacity enforced nonvacuously");
  set(client,"residentCanonicalMemoHits",Integer.MAX_VALUE);compare(client,Renderer3DModelKind.WALL_OBJECT,133,660,0,0,true);check(client.getBrowserStaticIdentityWork()[2]==Integer.MAX_VALUE,"long-session hit diagnostic saturates");
  set(client,"residentCanonicalMemoLiveRefusals",Integer.MAX_VALUE);sectors[4]=Sector.blankLoaded();compare(client,Renderer3DModelKind.WALL_OBJECT,133,660,0,0,true);check(client.getBrowserStaticIdentityWork()[4]==Integer.MAX_VALUE,"long-session mutable refusal diagnostic saturates");sectors[4]=new Sector();
  set(client,"residentCanonicalMemoMisses",Integer.MAX_VALUE);compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,3,true);check(client.getBrowserStaticIdentityWork()[3]==Integer.MAX_VALUE,"long-session miss diagnostic saturates");
  set(client,"residentCanonicalMemoCapacityRefusals",Integer.MAX_VALUE);set(client,"residentCanonicalMemoCursor",8192);compare(client,Renderer3DModelKind.WALL_OBJECT,133,660,0,0,false);check(client.getBrowserStaticIdentityWork()[7]==Integer.MAX_VALUE,"long-session capacity diagnostic saturates");
  set(client,"residentCanonicalMemoResets",Integer.MAX_VALUE);set(world,"nativeLayeredTerrainSnapshot",StaticSceneReuseOracle.recentered(source,packageRoot,hash,2,13));compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);check(client.getBrowserStaticIdentityWork()[5]==Integer.MAX_VALUE,"long-session context reset diagnostic saturates");
  world.applyWorldEditorTerrainPatch(0,111,625,1,0,0,0,0,0,0,false);check(world.getWholeZoneImmutableElevationContext()==null,"acknowledged terrain patch refuses immutable proof");compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);
  System.setProperty("spoiledmilk.browserStaticSceneReuse","false");check(world.getWholeZoneImmutableElevationContext()==null,"same-artifact disabled control remains literal");compare(client,Renderer3DModelKind.GAME_OBJECT,133,660,0,0,true);
  System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("frames",cases).put("work",new JSONArray(work)).put("finalWork",new JSONArray(client.getBrowserStaticIdentityWork())).put("scope","Actual original versus cached canonical identity fields and real native/loaded-sector elevation APIs. Detached owners and definition; no gameplay packets, browser/GPU, pixels or FPS proof.").toString());
 }
}
