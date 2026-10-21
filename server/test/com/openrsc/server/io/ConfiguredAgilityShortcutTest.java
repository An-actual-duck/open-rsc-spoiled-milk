package com.openrsc.server.io;

import com.openrsc.server.external.TileDef;
import com.openrsc.server.model.action.ConfiguredAgilityAction;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

/** Deterministic offline tests of the actual maintained action, parser and floor proof. No server/accounts. */
public final class ConfiguredAgilityShortcutTest {
	private static int checks;
	private static WorldLocation at(int x, int y, int level) { return WorldLocation.global(new WorldCoordinate(x,y,level)); }
	private static final WorldLocation START = at(50,50,0), SUCCESS = at(60,60,2), FAILURE = at(55,55,-3);
	private static AgilityShortcut shortcut(int start, int max) { return new AgilityShortcut(30,start,max,65,SUCCESS,FAILURE); }
	private static void check(boolean condition, String description) { checks++; if (!condition) throw new AssertionError(description); }
	private static void refuses(Runnable action, String description) { try { action.run(); throw new AssertionError(description); } catch (IllegalArgumentException|IllegalStateException expected) { checks++; } }
	public static void main(String[] args) throws Exception {
		AgilityShortcut normal = shortcut(60,95);
		check(normal.chanceBasisPoints(29)==-1,"below requirement");
		check(normal.chanceBasisPoints(30)==6000,"start chance");
		check(normal.chanceBasisPoints(45)==7500,"linear example");
		check(normal.chanceBasisPoints(65)==9500 && normal.chanceBasisPoints(Integer.MAX_VALUE)==9500,"capped chance");
		check(new AgilityShortcut(1,42,42,1,SUCCESS,FAILURE).chanceBasisPoints(99)==4200,"flat equal-level chance");
		refuses(() -> new AgilityShortcut(5,20,30,5,SUCCESS,FAILURE),"rising zero span");
		refuses(() -> new AgilityShortcut(5,90,20,20,SUCCESS,FAILURE),"decreasing chance");
		refuses(() -> new AgilityShortcut(0,20,90,20,SUCCESS,FAILURE),"zero required level");
		refuses(() -> new AgilityShortcut(1,20,101,20,SUCCESS,FAILURE),"over100 chance");
		refuses(() -> new AgilityShortcut(1,20,90,100,SUCCESS,FAILURE),"unsupported skill level");
		refuses(() -> new AgilityShortcut(1,20,90,30,at(0,4,0),FAILURE),"edge destination");
		JSONObject valid = json();
		check(AgilityShortcut.read(valid).chanceBasisPoints(45)==7500,"strict JSON parser");
		for(AgilityShortcut.Template template:AgilityShortcut.Template.values()) {
			JSONObject configured=new JSONObject(valid.toString()).put("template",template.id).put("action",template.action);
			check(AgilityShortcut.read(configured).getTemplate()==template,"known exact pair "+template.id);
			for(AgilityShortcut.Template other:AgilityShortcut.Template.values()) if(other!=template)
				rejectJson(new JSONObject(configured.toString()).put("action",other.action));
			for(int chance:new int[]{0,100}) {
				Actor selected=new Actor(); AgilityShortcut behavior=new AgilityShortcut(template,1,chance,chance,1,SUCCESS,FAILURE);
				start(selected,behavior,0);selected.complete();
				check(selected.where.equals(chance==0?FAILURE:SUCCESS),"template outcome "+template.id);
				check(selected.messages.get(0).equals(template.preparation)&&selected.messages.get(1).equals(chance==0?template.failure:template.success),"template messages "+template.id);
			}
			AgilityShortcut behavior=new AgilityShortcut(template,1,100,100,1,SUCCESS,FAILURE);
			Actor cancelled=new Actor();start(cancelled,behavior,0);
			cancelled.token=null;cancelled.busy=false;start(cancelled,behavior,0);
			ConfiguredAgilityAction.begin(cancelled,behavior,()->{throw new AssertionError("duplicate template roll");});
			check(cancelled.events.size()==2,"cancel retry with no duplicate callback "+template.id);
			cancelled.events.get(0).run();check(cancelled.busy&&cancelled.lands==0&&cancelled.rolls==0,"old callback does not own retry "+template.id);
			cancelled.events.get(1).run();cancelled.events.get(1).run();
			check(!cancelled.busy&&cancelled.lands==1&&cancelled.rolls==1,"retry rolls/lands exactly once "+template.id);
			for(String reason:new String[]{"unavailable","moved","replacement","unsafe"}) {
				Actor interrupted=new Actor();start(interrupted,behavior,0);
				if(reason.equals("unavailable"))interrupted.available=false;
				if(reason.equals("moved"))interrupted.where=FAILURE;
				if(reason.equals("replacement"))interrupted.current=false;
				if(reason.equals("unsafe"))interrupted.safe=false;
				interrupted.complete();
				check(!interrupted.busy&&interrupted.lands==0&&interrupted.rolls==0,"template guarded completion "+template.id+" "+reason);
			}
		}
		for (String field : new String[]{"schemaVersion","template","action","requiredLevel","successDestination"}) {
			JSONObject bad = new JSONObject(valid.toString()); bad.remove(field); rejectJson(bad);
		}
		for (JSONObject bad : new JSONObject[]{new JSONObject(valid.toString()).put("extra",1),
			new JSONObject(valid.toString()).put("schemaVersion",2),new JSONObject(valid.toString()).put("template","swing-v1"),
			new JSONObject(valid.toString()).put("action","jump to"),new JSONObject(valid.toString()).put("requiredLevel",30.5),
			new JSONObject(valid.toString()).put("startChancePercent","60")}) rejectJson(bad);
		parserVersions(valid);
		landingTests();
		Actor actor = new Actor(); start(actor,normal,7499); check(actor.busy && actor.events.size()==1,"scheduled/busy");
		ConfiguredAgilityAction.begin(actor,normal,()->{throw new AssertionError("duplicate roll");});
		check(actor.events.size()==1,"repeat click cannot add event"); actor.complete();
		check(actor.lands==1 && actor.where.equals(SUCCESS) && !actor.busy && actor.token==null,"success commits once/releases");
		actor.complete(); check(actor.lands==1,"callback replay no double teleport");
		actor = new Actor(); start(actor,normal,7500); actor.complete(); check(actor.where.equals(FAILURE),"exact threshold failure");
		actor = new Actor(); start(actor,shortcut(0,0),0); actor.complete(); check(actor.where.equals(FAILURE),"0percent always fails");
		actor = new Actor(); start(actor,shortcut(100,100),9999); actor.complete(); check(actor.where.equals(SUCCESS),"100percent succeeds at last roll");
		actor = new Actor(); actor.level=29; start(actor,normal,0); check(actor.events.isEmpty()&&!actor.busy,"requirement blocks before roll");
		for (String cancellation : new String[]{"death","logout","combat","moved","level","placement","unsafe","cancel","otherToken"}) {
			actor=new Actor(); start(actor,normal,0);
			if (cancellation.equals("death")||cancellation.equals("logout")||cancellation.equals("combat")) actor.available=false;
			if (cancellation.equals("moved")) actor.where=at(50,51,0);
			if (cancellation.equals("level")) actor.level=29;
			if (cancellation.equals("placement")) actor.current=false;
			if (cancellation.equals("unsafe")) actor.safe=false;
			if (cancellation.equals("cancel")) {actor.token=null;actor.busy=false;}
			Object replacement=null;
			if (cancellation.equals("otherToken")) {replacement=new Object();actor.token=replacement;}
			actor.complete(); check(actor.lands==0 && actor.rolls==0,"cancel before roll: "+cancellation);
			if (replacement!=null) check(actor.token==replacement && actor.busy,"do not release another action busy");
			else check(!actor.busy && actor.token==null,"own busy release: "+cancellation);
		}
		actor=new Actor(); actor.safe=false; start(actor,normal,0); check(actor.events.isEmpty(),"unsafe startup no event");
		actor=new Actor(); actor.throwSchedule=true; Actor broken=actor;
		refuses(()->start(broken,normal,0),"scheduler throws"); check(!actor.busy && actor.token==null,"scheduler failure releases busy");
		JSONObject resource = new JSONObject(new String(Files.readAllBytes(java.nio.file.Paths.get("../runtime-contract/agility-shortcuts-v1.json")),StandardCharsets.UTF_8));
		check(resource.getString("placementEncoding").equals(NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V5),"capability encoding");
		check(resource.getJSONArray("templates").length()==AgilityShortcut.Template.values().length,"declaration template count");
		for(int i=0;i<resource.getJSONArray("templates").length();i++) {
			JSONObject declared=resource.getJSONArray("templates").getJSONObject(i);
			AgilityShortcut.Template selected=AgilityShortcut.Template.forAction(declared.getJSONArray("actions").getString(0));
			check(selected!=null&&selected.id.equals(declared.getString("id")),"declaration exact known pair");
		}
		check(ConfiguredAgilityShortcutTest.class.getResourceAsStream("/map-runtime/agility-shortcuts-v1.json")!=null,"capability embedded in built jar");
		System.out.println("PASS configurable agility shortcut: "+checks+" assertions (parser, chance, landing, scheduled action lifecycle)");
	}
	private static void rejectJson(JSONObject value) throws Exception { try {AgilityShortcut.read(value);throw new AssertionError("invalid JSON accepted");}catch(IOException expected){checks++;} }
	private static JSONObject json() {return new JSONObject().put("schemaVersion",1).put("template","jump-v1").put("action","agility-jump")
		.put("requiredLevel",30).put("startChancePercent",60).put("maxChancePercent",95).put("maxChanceLevel",65)
		.put("successDestination",destination(SUCCESS)).put("failureDestination",destination(FAILURE));}
	private static JSONObject destination(WorldLocation at) {return new JSONObject().put("worldSpace","global").put("x",at.getCoordinate().getX())
		.put("y",at.getCoordinate().getY()).put("level",at.getCoordinate().getLevel());}
	private static void parserVersions(JSONObject value) throws Exception {
		Method reader = NativeLayeredWorldPackage.class.getDeclaredMethod("readPlacementSet",Path.class,String.class,WorldSpaceId.class,int.class,String.class,String.class,String.class,java.util.Set.class);
		reader.setAccessible(true); Path path=Files.createTempFile("agility-placement-", ".json");
		try {
			for(int version=1;version<=5;version++) {
				String encoding=version==1?"layered-entity-placements-v1":"layered-world-placements-v"+version;
				JSONObject data=new JSONObject().put("schemaVersion",version).put("encoding",encoding).put("worldSpace","global").put("level",0)
					.put("npcs",new JSONArray()).put("groundItems",new JSONArray().put(new JSONObject().put("placementId","item").put("itemId",1)
						.put("position",new JSONObject().put("x",5).put("y",5)).put("amount",1).put("respawnSeconds",30)));
				if(version>1)data.put("scenery",new JSONArray().put(new JSONObject().put("placementId","jump").put("sceneryId",1).put("position",new JSONObject().put("x",3).put("y",3)).put("direction",0))).put("boundaries",new JSONArray());
				if(version==5)data.getJSONArray("scenery").getJSONObject(0).put("agilityShortcut",value);
				Files.write(path,data.toString().getBytes(StandardCharsets.UTF_8));
				NativeLayeredPlacementSet set=(NativeLayeredPlacementSet)reader.invoke(null,path,"set",WorldSpaceId.GLOBAL,0,encoding,"test","0",new HashSet<String>());
				check(version==1|| (set.getScenery().get(0).getAgilityShortcut()!=null)==(version==5),"old/new placement version "+version);
				if(version==4) {
					data.getJSONArray("scenery").getJSONObject(0).put("agilityShortcut",value);Files.write(path,data.toString().getBytes(StandardCharsets.UTF_8));
					try{reader.invoke(null,path,"set",WorldSpaceId.GLOBAL,0,encoding,"test","0",new HashSet<String>());throw new AssertionError("v4 accepted critical v5 field");}
					catch(InvocationTargetException expected){check(expected.getCause() instanceof IOException,"old parser refuses new metadata");}
				}
			}
		} finally {Files.deleteIfExists(path);}
	}
	private static void landingTests() {
		Terrain terrain=new Terrain(); AgilityShortcutLanding.requireSafe(START,terrain); checks++;
		terrain.overlay=0; AgilityShortcutLanding.requireSafe(at(50,50,-5),terrain);checks++;
		refuses(()->AgilityShortcutLanding.requireSafe(at(50,50,2),terrain),"upper void is walkable but unsupported");
		terrain.overlay=255; refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"raw255void");
		terrain.overlay=250; refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"raw250seam");
		terrain.overlay=1;
		for(int visual:new int[]{3,4,5}) {terrain.definition.unknown=visual;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"water/bridge/hidden floor");}
		terrain.definition.unknown=1; terrain.definition.colour=12345678;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"invisible material");
		terrain.definition.colour=-10;terrain.definition.objectType=1;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"blocked material");
		terrain.definition.objectType=0;
		terrain.missing=true;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"missing neighbor terrain");terrain.missing=false;
		terrain.wall=true;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"wall in safety footprint");terrain.wall=false;
		terrain.farWall=true;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"far owning wall anchor");terrain.farWall=false;
		terrain.clear=false;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"dynamic obstruction");terrain.clear=true;
		terrain.different=true;refuses(()->AgilityShortcutLanding.requireSafe(START,terrain),"ambiguous half floor seam");
	}
	private static void start(Actor actor,AgilityShortcut shortcut,int roll) {ConfiguredAgilityAction.begin(actor,shortcut,()->{actor.rolls++;return roll;});}
	private static class Actor implements ConfiguredAgilityAction.Actor {
		boolean available=true,busy,current=true,safe=true,throwSchedule;int level=45,lands,rolls;Object token; WorldLocation where=START;
		List<Runnable> events=new ArrayList<Runnable>();
		List<String> messages=new ArrayList<String>();
		void complete(){events.get(0).run();} public boolean available(){return available;}public boolean busy(){return busy;}public void busy(boolean v){busy=v;}
		public int effectiveAgility(){return level;}public WorldLocation location(){return where;}public Object pending(){return token;}public void pending(Object t){token=t;}
		public boolean placementCurrent(){return current;} public void requireSafe(WorldLocation at){if(!safe)throw new IllegalStateException("unsafe");}
		public void schedule(Runnable callback){if(throwSchedule)throw new IllegalStateException("scheduler failure");events.add(callback);}
		public void message(String text){messages.add(text);} public void land(WorldLocation at){lands++;where=at;}
	}
	private static class Terrain implements AgilityShortcutLanding.Terrain {
		int overlay=1; boolean clear=true,missing,wall,farWall,different; TileDef definition=new TileDef();
		Terrain(){definition.colour=-10;definition.objectType=0;definition.unknown=1;}
		public NativeLayeredTerrainTile tile(WorldLocation at){if(missing&&at.getCoordinate().getX()==49)return null;
			return new NativeLayeredTerrainTile(0,different&&at.getCoordinate().getX()==49?1:0,overlay,0,wall?1:0,farWall&&at.getCoordinate().getX()==52?1:0,0);}
		public TileDef definition(int id){return definition;}public boolean collisionClear(WorldLocation at){return clear;}
	}
}
