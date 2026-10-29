package com.openrsc.server.combat;

import com.openrsc.server.content.cleric.ClericWellInteraction;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.WalkToObjectAction;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.*;
import com.openrsc.server.net.rsc.handlers.GameObjectAction;
import com.openrsc.server.net.rsc.struct.incoming.TargetObjectStruct;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.plugins.custom.myworld.skills.blessing.SigilWells;
import com.openrsc.server.util.rsc.CollisionFlag;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.lang.reflect.Field;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

/** Real native authored package/World/Player, collision reach, packet selection and plugin. No game loop/listeners/accounts. */
public final class ClericWellInteractionFixture {
 static int checks;static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 static WorldLocation at(int x,int y,int level){return WorldLocation.global(new WorldCoordinate(x,y,level));}
 public static void main(String[] args)throws Exception{
  try(CurrentCombatHarness h=new CurrentCombatHarness()){
   h.server().getConfig().WANT_MYWORLD=true;
   h.world().getRegionManager().preflightNativeLayeredPlacements();h.world().getRegionManager().populateNativeLayeredPlacements();
   Player p=h.player("wellfixture",441,440);SigilWells plugin=new SigilWells();h.recordOutgoingPackets(p);h.installPlugin(OpLocTrigger.class,plugin);p.getCarriedItems().getInventory().add(new Item(3299,3));
   for(int id:new int[]{1354,1355})for(int direction=0;direction<8;direction++){
    int x=440+4*direction,y=id==1354?440:448;
    GameObject o=h.world().getRegionManager().findNativeLayeredScenery(at(x,y,0));
    check(o!=null&&o.getID()==id&&o.getDirection()==direction,"authored identity/definition/rotation materialized");
    p.teleport(x+1,y,false);check(p.getViewArea().getGameObject(o.getLocation())==o,"actual current registered view identity");
    check(o.getGameObjectDef().isInteractionOnly()&&o.getGameObjectDef().getType()==0,"paired server metadata");
    check((p.getTileAtCurrentLevel(x,y).traversalMask&CollisionFlag.FULL_BLOCK)!=0,"authored liquid really blocks walking");
    check((p.getTileAtCurrentLevel(x+1,y).traversalMask&(CollisionFlag.FULL_BLOCK|CollisionFlag.OBJECT))==0,"nonblocking spot adds no collision to adjacent land");
    for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}){
     p.teleport(x+delta[0],y+delta[1],false);p.resetAll();check(ClericWellInteraction.canInteract(p,o),"adjacent land approach rotation"+direction+" delta"+Arrays.toString(delta));
    }
    p.teleport(x,y,false);check(!ClericWellInteraction.canInteract(p,o),"standing in blocked liquid cannot bypass land admission");
    p.teleport(x+3,y,false);check(!ClericWellInteraction.canInteract(p,o),"range not bypassed");p.teleport(x+1,y,false);
    check(plugin.blockOpLoc(p,o,"dip sigils"),"production Dip command selected");check(!plugin.blockOpLoc(p,o,"examine"),"native examine left intact");
    int inventory=p.getCarriedItems().getInventory().size();int raw=p.getCarriedItems().getInventory().countId(3299,java.util.Optional.of(false));double blessing=p.getSkills().getExperience(com.openrsc.server.constants.Skill.BLESSING.id());
    int messages=p.getOutgoingPacketQueueSize();plugin.onOpLoc(p,o,"dip sigils");check(p.getOutgoingPacketQueueSize()==messages+1,"production Dip sends explicit player guidance");check(p.getCarriedItems().getInventory().size()==inventory&&p.getCarriedItems().getInventory().countId(3299,java.util.Optional.of(false))==raw&&p.getSkills().getExperience(com.openrsc.server.constants.Skill.BLESSING.id())==blessing,"authoring guidance grants/consumes nothing");
    check(ClericWellInteraction.guidance(id).contains(id==1354?"Blight":"Grace"),"purpose-specific guidance");
    WalkToObjectAction pending=new WalkToObjectAction(p,o){public void executeInternal(){}};check(pending.shouldExecute(),"ordinary object walker admits adjacent land");
    GameObject replacement=new GameObject(h.world(),Point.location(o.getX(),o.getY()),id,direction,0);h.world().replaceGameObject(o,replacement);
    check(!ClericWellInteraction.canInteract(p,o)&&ClericWellInteraction.canInteract(p,replacement),"native replacement rejects stale captured identity");
   }
   GameObject blocked=h.world().getRegionManager().findNativeLayeredScenery(at(440,456,0));
   check((h.world().getTile(at(440,456,0)).traversalMask&15)==15,"fixture's real native raw wall edges surround liquid");
   for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){p.teleport(440+d[0],456+d[1],false);check(!ClericWellInteraction.canInteract(p,blocked),"solid wall cannot be reached through");}
   GameObject o=h.world().getRegionManager().findNativeLayeredScenery(at(440,440,0));p.teleport(441,440,false);
   TargetObjectStruct packet=new TargetObjectStruct();packet.setOpcode(OpcodeIn.OBJECT_COMMAND);packet.coordObject=Point.location(440,440);new GameObjectAction().process(packet,p);check(p.getWalkToAction()!=null&&p.getWalkToAction().shouldExecute(),"real game packet resolves well and creates normal walking action");
   int packetMessages=p.getOutgoingPacketQueueSize();p.getWalkToAction().execute();h.advanceOneCombatTick();Field executor=CurrentCombatHarness.class.getDeclaredField("pluginExecutor");executor.setAccessible(true);((ExecutorService)executor.get(h)).submit(()->{}).get();check(p.getOutgoingPacketQueueSize()==packetMessages+1,"real packet action dispatches registered Dip plugin and guidance");
   p.teleportLayered(at(441,440,2),false);check(!ClericWellInteraction.canInteract(p,o),"same XY different native level rejected");
   p.teleportLayered(at(441,440,-3),false);check(!ClericWellInteraction.canInteract(p,o),"negative signed level cannot reach surface well");
   p.teleportLayered(at(441,440,0),false);h.world().unregisterGameObject(o);check(!ClericWellInteraction.canInteract(p,o),"removed object rejected");
   GameObject uninitialized=new GameObject(h.world(),Point.location(440,440),1354,0,0);check(!ClericWellInteraction.canInteract(p,uninitialized),"unavailable object scope fails closed");
   check(!ClericWellInteraction.canInteract(p,null)&&!ClericWellInteraction.canInteract(null,o),"null input rejected");
   System.out.println("PASS "+checks+" production native well/land/liquid/wall/rotation/range/replacement/level/packet/plugin/no-charge checks");
  }
 }
}
