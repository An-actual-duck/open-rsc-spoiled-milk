package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.ConfiguredAgilityAction;
import com.openrsc.server.model.action.ConfiguredAgilityShortcutHandler;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.GameObjectAction;
import com.openrsc.server.net.rsc.struct.incoming.TargetObjectStruct;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

/** Real maintained World/Player, stable placement registry, packet action and tick scheduler. No sockets/login/database reads. */
public final class ConfiguredAgilityRuntimeIntegrationTest {
	private static int checks;
	private static void check(boolean condition,String label) {checks++;if(!condition)throw new AssertionError(label);}
	public static void main(String[] arguments) throws Exception {
		Server server=new Server("myworld.conf");
		try {
			server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY=false;
			server.getEntityHandler().load();
			server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
			server.getWorld().getRegionManager().populateNativeLayeredPlacements();
			GameObject success=server.getWorld().getRegionManager().findNativeLayeredScenery(at(20,20,0));
			GameObject failure=server.getWorld().getRegionManager().findNativeLayeredScenery(at(24,20,0));
			check(success!=null && failure!=null,"v5genericplacements registered by authored identity");
			Player player=new Player(server.getWorld(),DataConversions.usernameToHash("agilityfixture"));
			player.setClientVersion(server.getConfig().CLIENT_VERSION);
			player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
			player.setInitialLayeredLocation(at(21,20,0));
			server.getWorld().getPlayers().add(player);player.updateRegion();player.setBusy(false);player.setLoggedIn(true);
			player.getSkills().setTemporaryLevelAndMaxStat(Skill.AGILITY.id(),45,45,false);
			player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),40,40,false);
			int index=0;
			for(AgilityShortcut.Template template:AgilityShortcut.Template.values()) {
				for(int outcome=0;outcome<2;outcome++) {
					int x=outcome==0?20:24, y=20+index*4;
					GameObject obstacle=server.getWorld().getRegionManager().findNativeLayeredScenery(at(x,y,0));
					check(obstacle!=null,"discovered placement "+template.id);
					com.openrsc.server.external.GameObjectDef definition=server.getEntityHandler().getGameObjectDef(obstacle.getID());
					check(definition.getCommand1().equals(template.action)&&definition.getType()==0&&definition.getWidth()==(template==AgilityShortcut.Template.CLIMB?2:1)&&definition.getHeight()==1,"paired native nonblocking footprint "+template.id);
					player.teleportLayered(at(x+1,y,0),false);
					check(!ConfiguredAgilityShortcutHandler.handle(player,obstacle,"jump to"),"authentic action not intercepted "+template.id);
					String wrong=template==AgilityShortcut.Template.JUMP?"agility-climb":"agility-jump";
					check(ConfiguredAgilityShortcutHandler.handle(player,obstacle,wrong)&&!player.isBusy(),"reserved mismatched action cannot activate "+template.id);
					click(player,obstacle);
					check(player.isBusy(),"packet starts "+template.id);
					player.resetAll();
					check(!player.isBusy()&&player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE)==null,"packet transaction reset "+template.id);
					click(player,obstacle);
					Object retry=player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE);
					click(player,obstacle);
					check(retry!=null&&retry==player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE),"busy repeated packet retains one retry token "+template.id);
					for(int tick=1;tick<template.ticks;tick++) {
						drain(server,player);check(player.isBusy()&&player.getWorldLocation().equals(at(x+1,y,0)),"waits full template delay "+template.id);
					}
					drain(server,player);
					check(!player.isBusy()&&player.getWorldLocation().equals(outcome==0?at(30,30,2):at(32,30,-3)),"signed success/failure "+template.id);
				}
				index++;
			}
			player.teleportLayered(at(21,20,0),false);
			check(!ConfiguredAgilityShortcutHandler.handle(player,success,"examine"),"othercommands stay with ordinaryplugins");
			click(player,success);
			check(player.isBusy() && player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE)!=null,"packetwalktohandler starts transaction");
			drain(server,player);check(player.getWorldLocation().equals(at(30,30,2))&&!player.isBusy(),"scheduler success preserves explicitdestination level");
			player.teleportLayered(at(25,20,0),false);click(player,failure);drain(server,player);
			check(player.getWorldLocation().equals(at(32,30,-3))&&!player.isBusy(),"scheduler failure uses explicitnegativelevel");
			player.teleportLayered(at(21,20,0),false);click(player,success);
			player.resetAll();check(!player.isBusy() && player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE)==null,"realPlayer reset cancels/release ownbusy");
			click(player,success);check(player.isBusy(),"retry before cancelledscheduler drains");
			drain(server,player);check(player.getWorldLocation().equals(at(30,30,2))&&!player.isBusy(),"oldcallback cannot cancelnewtoken or lockbusy");
			player.teleportLayered(at(21,20,0),false);click(player,success);player.setLoggedIn(false);drain(server,player);
			check(player.getWorldLocation().equals(at(21,20,0))&&!player.isBusy(),"logoutcancels realcallback");player.setLoggedIn(true);
			click(player,success);player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),0,40,false);drain(server,player);
			check(player.getWorldLocation().equals(at(21,20,0))&&!player.isBusy(),"deathcancels realcallback");player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),40,40,false);
			player.getSkills().setTemporaryLevelAndMaxStat(Skill.AGILITY.id(),29,45,false);click(player,success);
			check(!player.isBusy()&&player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE)==null,"real skills requirement prevents rollandmovement");
			player.getSkills().setTemporaryLevelAndMaxStat(Skill.AGILITY.id(),45,45,false);
			GameObject marker=server.getWorld().getRegionManager().findNativeLayeredScenery(at(30,30,2));
			click(player,success);
			GameObject blocker=new GameObject(server.getWorld(),Point.location(30,30),2,0,0);
			server.getWorld().replaceGameObject(marker,blocker);
			drain(server,player);
			check(player.getWorldLocation().equals(at(21,20,0))&&!player.isBusy(),"dynamicblocking landing after start cancels withoutteleport");
			GameObject restored=new GameObject(server.getWorld(),Point.location(30,30),marker.getID(),0,0);
			server.getWorld().replaceGameObject(blocker,restored);
			click(player,success);
			GameObject replacement=new GameObject(server.getWorld(),Point.location(20,20),success.getID(),0,0);
			server.getWorld().replaceGameObject(success,replacement);
			drain(server,player);
			check(player.getWorldLocation().equals(at(21,20,0))&&!player.isBusy(),"samecoordinate replacement invalidates oldobject callback");
			player.teleportLayered(at(21,20,2),false);
			ConfiguredAgilityShortcutHandler.handle(player,replacement,"agility-jump");
			check(!player.isBusy()&&player.getAttribute(ConfiguredAgilityAction.PENDING_ATTRIBUTE)==null,"cannot use shortcut through sameXYotherlevel");
			System.out.println("PASS real configurable agility integration: "+checks+" assertions; packet→walkto→Player→scheduler→nativeleveltransition; no server started");
		} finally {
			for(GameTickEvent event:new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents()))event.stop();
			server.getGameEventHandler().cleanupEvents();server.getWorld().getPlayers().clear();
			server.getEntityHandler().unload();
			for(String name:new String[]{"sqlLoggingThreadPool","sqlThreadPool","onlineMonitorThreadPool"}) {Field field=Server.class.getDeclaredField(name);field.setAccessible(true);((ExecutorService)field.get(server)).shutdownNow();}
			Server.serversList.remove(server.getName(),server);
		}
	}
	private static WorldLocation at(int x,int y,int level){return WorldLocation.global(new WorldCoordinate(x,y,level));}
	private static void click(Player player,GameObject object)throws Exception {
		TargetObjectStruct payload=new TargetObjectStruct();payload.setOpcode(OpcodeIn.OBJECT_COMMAND);payload.coordObject=Point.location(object.getX(),object.getY());
		new GameObjectAction().process(payload,player);
		if(player.getWalkToAction()!=null&&player.getWalkToAction().shouldExecute())player.getWalkToAction().execute();
	}
	private static void drain(Server server,Player player) {
		server.getGameEventHandler().processEvents(player);server.getGameEventHandler().cleanupEvents();
	}
}
