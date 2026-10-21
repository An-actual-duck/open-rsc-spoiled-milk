package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.DoubleDoorPassage;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.defaults.DoorAction;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

/** Maintained World/Player/door plugin/helper/scheduler; no listeners, accounts or database reads. */
public final class DoubleDoorPassageIntegrationTest {
	private static int checks;
	private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
	public static void main(String[] args) throws Exception {
		Server server = new Server("myworld.conf");
		try {
			server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
			server.getEntityHandler().load();
			server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
			server.getWorld().getRegionManager().populateNativeLayeredPlacements();
			Player player = new Player(server.getWorld(), DataConversions.usernameToHash("doorfixture"));
			player.setClientVersion(server.getConfig().CLIENT_VERSION);
			player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
			player.setInitialLayeredLocation(at(12, 12, 0));
			server.getWorld().getPlayers().add(player); player.updateRegion(); player.setBusy(false); player.setLoggedIn(true);
			player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 40, 40, false);
			DoorAction plugin = new DoorAction();
			for (int level : new int[]{0, 1, 2, 3, -3}) {
				for (int index = 0; index < 4; index++) {
					int direction = index * 2, x = 12 + index * 8, y = 12;
					for (int lane = 0; lane < 2; lane++) for (int side = 0; side < 2; side++) {
						GameObject closed = find(server, x, y, level);
						WorldLocation source = side(closed.getWorldLocation(), direction, lane, side);
						WorldLocation target = side(closed.getWorldLocation(), direction, lane, 1 - side);
						player.teleportLayered(source, false);
						int sourceMask = mask(server, source), targetMask = mask(server, target);
						check(sourceMask != 0 && targetMask != 0, "both halves block before passage");
						check(plugin.blockObjectAction(closed, "open", player), "normal door plugin owns generic action");
						plugin.onObjectAction(closed, "open", player);
						GameObject open = find(server, x, y, level);
						check(player.getWorldLocation().equals(target), "lane/side/cardinal/signed passage " + direction + "/" + lane + "/" + side + "/" + level);
						check(open.getID() == SceneryId.DOORS_GENERIC_PASS_OPEN.id() && open != closed, "existing open model is live");
						check(mask(server, source) == sourceMask && mask(server, target) == targetMask, "followers cannot cross during open visual");
						check(open.getLoc().getNativeLayeredGameObjectIdentity().equals(closed.getLoc().getNativeLayeredGameObjectIdentity()), "native placement identity retained");
						int events = closeEvents(server);
						plugin.onObjectAction(closed, "open", player);
						check(events == closeEvents(server) && player.getWorldLocation().equals(target), "duplicate stale click has no additional passage or timer");
						drain(server);
						check(find(server,x,y,level) == open, "open visual lasts first tick");
						drain(server);
						GameObject restored = find(server,x,y,level);
						check(restored.getID() == closed.getID() && restored.getLoc().getNativeLayeredGameObjectIdentity().equals(closed.getLoc().getNativeLayeredGameObjectIdentity()), "closed authored identity restored");
						check(mask(server,source) == sourceMask && mask(server,target) == targetMask && !player.isBusy(), "collision and player action availability restored");
					}
				}
			}
			GameObject first = find(server,12,22,0);
			WorldLocation source=at(12,22,0), target=at(11,22,0);
			player.teleportLayered(source,false);
			// A side wall alongside a legitimate hallway must not prevent crossing its doorway.
			GameObject sideMarker=server.getWorld().getRegionManager().findInteractionBoundary(Point.location(11,22),player);
			GameObject sideWall=new GameObject(server.getWorld(),sideMarker.getLocation(),0,0,1);
			server.getWorld().replaceGameObject(sideMarker,sideWall);
			plugin.onObjectAction(first,"open",player);
			check(player.getWorldLocation().equals(target),"continuing room wall beside landing is allowed");
			drain(server);drain(server);
			server.getWorld().replaceGameObject(sideWall,new GameObject(server.getWorld(),sideMarker.getLoc()));
			for(int scenario=0;scenario<2;scenario++) {
				first=find(server,12,22,0);player.teleportLayered(source,false);
				GameObject marker=scenario==0?find(server,11,22,0):server.getWorld().getRegionManager().findInteractionBoundary(Point.location(12,22),player);
				GameObject blocker=new GameObject(server.getWorld(),marker.getLocation(),2,marker.getDirection(),marker.getType());
				server.getWorld().replaceGameObject(marker,blocker);
				plugin.onObjectAction(first,"open",player);
				check(player.getWorldLocation().equals(source)&&find(server,12,22,0)==first&&closeEvents(server)==0,"unrelated obstacle/overlapping passage wall refuses before mutation");
				server.getWorld().replaceGameObject(blocker,new GameObject(server.getWorld(),marker.getLoc()));
			}
			for(int level:new int[]{1,2,3,-3}) {
				GameObject hall=find(server,12,28,level);player.teleportLayered(at(12,28,level),false);
				plugin.onObjectAction(hall,"open",player);
				check(player.getWorldLocation().equals(at(11,28,level)),"two tile upper hallway with void outside walls remains supported "+level);drain(server);drain(server);
			}
			for(int level:new int[]{1,2}) {
				GameObject partial=find(server,22,28,level);player.teleportLayered(at(22,28,level),false);
				plugin.onObjectAction(partial,"open",player);
				check(find(server,22,28,level)==partial&&player.getWorldLocation().equals(at(22,28,level)),"rendered half-empty upper floor refuses "+level);
			}
			for(int level:new int[]{1,2,3,-3}) {
				GameObject voidDoor=find(server,60,30,level);player.teleportLayered(at(60,30,level),false);
				plugin.onObjectAction(voidDoor,"open",player);
				check(find(server,60,30,level)==voidDoor&&player.getWorldLocation().equals(at(60,30,level))&&closeEvents(server)==0,"walkable upper void and raw255 refused "+level);
			}
			GameObject edge=find(server,30,46,0);player.teleportLayered(at(30,46,0),false);plugin.onObjectAction(edge,"open",player);
			check(find(server,30,46,0)==edge&&player.getWorldLocation().equals(at(30,46,0)),"missing surrounding authored terrain refuses safely");
			source=at(12,12,0);target=at(11,12,0);
			first=find(server,12,12,0);player.teleportLayered(at(12,12,1),false);plugin.onObjectAction(first,"open",player);
			check(find(server,12,12,0)==first,"sameXY other level cannot activate");
			player.teleportLayered(source,false);plugin.onObjectAction(first,"open",player);player.resetAll();player.setLoggedIn(false);drain(server);drain(server);
			check(find(server,12,12,0).getID()==SceneryId.DOORS_GENERIC_PASS_CLOSED.id()&&!player.isBusy(),"reset/logout do not strand open visual or busy state");player.setLoggedIn(true);
			// Two ownerless close events must coexist; stale event cannot overwrite an intervening edit.
			first=find(server,12,12,0);player.teleportLayered(source,false);plugin.onObjectAction(first,"open",player);
			GameObject oldOpen=find(server,12,12,0);
			GameObject replacement=new GameObject(server.getWorld(),oldOpen.getLocation(),SceneryId.DOORS_GENERIC_PASS_CLOSED.id(),0,0);
			server.getWorld().replaceGameObject(oldOpen,replacement);
			GameObject second=find(server,20,12,0);player.teleportLayered(at(20,12,0),false);plugin.onObjectAction(second,"open",player);
			check(closeEvents(server)==2,"simultaneous independent doors retain both closure callbacks");drain(server);drain(server);
			check(find(server,12,12,0)==replacement&&find(server,20,12,0).getID()==SceneryId.DOORS_GENERIC_PASS_CLOSED.id(),"stale callback preserves replacement while second door closes");
			for(int direction:new int[]{1,3,5,7}) check(DoubleDoorPassage.destination(source,direction,source)==null,"diagonal facing refuses");
			check(DoubleDoorPassage.destination(source,0,at(12,14,0))==null,"end-on click cannot snap into passage");
			System.out.println("PASS generic double doors: "+checks+" assertions; real plugin/helper, both sides/halves, all cardinals, signed levels, floor/collision/stale/logout guards; no server started");
		} finally {
			for(GameTickEvent event:new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents()))event.stop();
			server.getGameEventHandler().cleanupEvents();server.getWorld().getPlayers().clear();server.getEntityHandler().unload();
			for(String name:new String[]{"sqlLoggingThreadPool","sqlThreadPool","onlineMonitorThreadPool"}){Field field=Server.class.getDeclaredField(name);field.setAccessible(true);((ExecutorService)field.get(server)).shutdownNow();}
			Server.serversList.remove(server.getName(),server);
		}
	}
	private static WorldLocation at(int x,int y,int level){return WorldLocation.global(new WorldCoordinate(x,y,level));}
	private static GameObject find(Server server,int x,int y,int level){return server.getWorld().getRegionManager().findNativeLayeredScenery(at(x,y,level));}
	private static int mask(Server server,WorldLocation at){return server.getWorld().getRegionManager().getTile(at).traversalMask&255;}
	private static WorldLocation side(WorldLocation door,int direction,int lane,int side){int x=door.getCoordinate().getX(),y=door.getCoordinate().getY(),level=door.getCoordinate().getLevel();if(direction==0)return at(x-side,y+lane,level);if(direction==4)return at(x+side,y+lane,level);if(direction==2)return at(x+lane,y+side,level);return at(x+lane,y-side,level);}
	private static void drain(Server server){server.getGameEventHandler().processNonPlayerEvents();server.getGameEventHandler().cleanupEvents();}
	private static int closeEvents(Server server){int count=0;for(GameTickEvent event:server.getGameEventHandler().getEvents())if(event.getDescriptor().equals("Generic double door close")&&!event.shouldRemove())count++;return count;}
}
