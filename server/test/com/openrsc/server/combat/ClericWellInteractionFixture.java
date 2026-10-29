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
import java.lang.reflect.Field;
import java.util.*;

/** Real maintained World/Player registration, collision reach, packet location selection and Dip plugin. No game loop/listeners/accounts. */
public final class ClericWellInteractionFixture {
 static int checks;static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 static com.openrsc.server.model.world.region.TileValue tile(CurrentCombatHarness h,int x,int y){return h.world().getRegionManager().getMutableTile(WorldLocation.global(new WorldCoordinate(x,y,0)));}
 static GameObject place(CurrentCombatHarness h,int id,int direction){GameObject o=new GameObject(h.world(),Point.location(450,450),id,direction,0);h.world().registerGameObject(o);return o;}
 public static void main(String[] args)throws Exception{
  try(CurrentCombatHarness h=new CurrentCombatHarness()){
   h.server().getConfig().WANT_MYWORLD=true;
   for(int x=445;x<=455;x++)for(int y=445;y<=455;y++)tile(h,x,y).traversalMask=0;Player p=h.player("wellfixture",451,450);SigilWells plugin=new SigilWells();
   for(int id:new int[]{1354,1355})for(int direction=0;direction<8;direction++){
    GameObject o=place(h,id,direction);check(p.getViewArea().getGameObject(o.getLocation())==o,"actual registered view identity");check(o.getGameObjectDef().isInteractionOnly()&&o.getGameObjectDef().getType()==0,"paired server metadata");
    check(tile(h,450,450).traversalMask==0,"invisible spot itself adds no movement collision");tile(h,450,450).traversalMask=(byte)CollisionFlag.FULL_BLOCK; // blocked liquid
    for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}){
     p.teleport(450+delta[0],450+delta[1],false);p.resetAll();check(ClericWellInteraction.canInteract(p,o),"adjacent land approach at rotation"+direction+" delta"+Arrays.toString(delta));
    }
    p.teleport(451,450,false);tile(h,450,450).traversalMask=(byte)(CollisionFlag.FULL_BLOCK|CollisionFlag.WALL_WEST|CollisionFlag.WALL_EAST|CollisionFlag.WALL_NORTH|CollisionFlag.WALL_SOUTH);check(!ClericWellInteraction.canInteract(p,o),"solid wall cannot be reached through");
    tile(h,450,450).traversalMask=(byte)CollisionFlag.FULL_BLOCK;
    p.teleport(450,450,false);check(!ClericWellInteraction.canInteract(p,o),"standing in blocked liquid does not bypass shoreline admission");
    p.teleport(454,450,false);check(!ClericWellInteraction.canInteract(p,o),"range not bypassed");p.teleport(451,450,false);
    check(plugin.blockOpLoc(p,o,"dip sigils"),"production Dip command selected");check(!plugin.blockOpLoc(p,o,"examine"),"native examine left intact");
    int inventory=p.getCarriedItems().getInventory().size();double blessing=p.getSkills().getExperience(com.openrsc.server.constants.Skill.BLESSING.id());
    plugin.onOpLoc(p,o,"dip sigils");check(p.getCarriedItems().getInventory().size()==inventory&&p.getSkills().getExperience(com.openrsc.server.constants.Skill.BLESSING.id())==blessing,"authoring-stage guidance grants/consumes nothing");
    check(ClericWellInteraction.guidance(id).contains(id==1354?"Blight":"Grace"),"purpose-specific guidance");
    final GameObject captured=o;WalkToObjectAction pending=new WalkToObjectAction(p,o){public void executeInternal(){}};check(pending.shouldExecute(),"normal object walker permits adjacent liquid interaction");
    h.world().unregisterGameObject(o);GameObject replacement=place(h,id,direction);check(!ClericWellInteraction.canInteract(p,captured)&&ClericWellInteraction.canInteract(p,replacement),"stale captured object rejected after replacement");
    h.world().unregisterGameObject(replacement);tile(h,450,450).traversalMask=0;
   }
   GameObject o=place(h,1354,0);p.teleport(451,450,false);
   TargetObjectStruct packet=new TargetObjectStruct();packet.setOpcode(OpcodeIn.OBJECT_COMMAND);packet.coordObject=Point.location(450,450);new GameObjectAction().process(packet,p);check(p.getWalkToAction()!=null&&p.getWalkToAction().shouldExecute(),"actual game packet resolves well and establishes ordinary walking action");
   h.world().unregisterGameObject(o);check(!ClericWellInteraction.canInteract(p,o),"removed object rejected");
   GameObject otherLevel=new GameObject(h.world(),Point.location(450,1394),1354,0,0);otherLevel.setInitialLocation(Point.location(450,1394));check(!ClericWellInteraction.canInteract(p,otherLevel),"same XY different native level rejected before view admission");
   GameObject otherInstance=new GameObject(h.world(),Point.location(450,450),1354,0,0);otherInstance.setInitialWorldLocation(new WorldLocation(new WorldSpaceId("well-instance"),new WorldCoordinate(450,450,0)));check(!ClericWellInteraction.canInteract(p,otherInstance),"same coordinate in another world-space rejected");
   GameObject uninitialized=new GameObject(h.world(),Point.location(450,450),1354,0,0);check(!ClericWellInteraction.canInteract(p,uninitialized),"unavailable object scope fails closed");
   check(!ClericWellInteraction.canInteract(p,null)&&!ClericWellInteraction.canInteract(null,o),"null input rejected");
   System.out.println("PASS "+checks+" production well registry/land/liquid/wall/rotation/range/replacement/packet/plugin/no-charge checks");
  }
 }
}
