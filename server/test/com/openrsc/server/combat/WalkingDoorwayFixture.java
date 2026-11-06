package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Path;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.plugins.Functions;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import java.util.ArrayList;
import java.util.List;

/** Production plugin threads and per-tick movement on a disposable signed-layered package. */
public final class WalkingDoorwayFixture {
    private static int checks;
    private static final List<String> failures=new ArrayList<String>();
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness();PvpSqliteLog logs=new PvpSqliteLog(h.server())) {
            h.world().getRegionManager().preflightNativeLayeredPlacements();
            h.world().getRegionManager().populateNativeLayeredPlacements();
            h.installPlugin(TalkNpcTrigger.class,Class.forName("com.openrsc.server.plugins.authentic.npcs.alkharid.BorderGuard").newInstance());
            Crossing crossing=new Crossing();h.installPlugin(OpLocTrigger.class,crossing);
            for(String plugin:new String[]{"custom.misc.RangersGuildDoor", "authentic.quests.members.legendsquest.obstacles.LegendsQuestGates", "authentic.quests.members.legendsquest.npcs.LegendsQuestGuildGuard"})
                h.installPlugin(OpLocTrigger.class,Class.forName("com.openrsc.server.plugins."+plugin).newInstance());
            for(boolean east:new boolean[]{false,true})for(int stage:new int[]{-1,3,0}) {
                String name="Al Kharid "+(east?"east":"west")+" stage "+stage;
                scenario(h,name,()->border(h,east,stage,2,20));
            }
            scenario(h,"Al Kharid declined toll",()->border(h,false,0,0,20));
            scenario(h,"Al Kharid insufficient coins",()->border(h,true,0,2,0));
            for(int level:new int[]{0,1,2,3,-3})for(int facing:new int[]{0,2,4,6}) {
                final int floor=level,dir=facing;
                scenario(h,"generic gate "+level+"/"+facing,()->gate(h,crossing,floor,dir));
            }
            for(String helper:new String[]{"door","tent","wall"})for(int facing:new int[]{0,1,2,3}) {
                final int dir=facing;
                scenario(h,helper+" "+facing,()->door(h,crossing,helper,dir));
            }
            for(boolean entering:new boolean[]{false,true})for(int lane:new int[]{495,496})
                scenario(h,"Rangers guild "+entering+"/"+lane,()->rangers(h,entering,lane,66));
            scenario(h,"Rangers requirement",()->rangers(h,true,495,65));
            for(boolean entering:new boolean[]{false,true})for(boolean outer:new boolean[]{false,true})
                scenario(h,"Legends guild "+outer+"/"+entering,()->legends(h,outer,entering,true));
            scenario(h,"Legends outer requirement",()->legends(h,true,true,false));
            scenario(h,"Legends inner requirement",()->legends(h,false,true,false));
            scenario(h,"successive crossings",()->chain(h,crossing));
            scenario(h,"external relocation interrupts crossing",()->interrupted(h,crossing));
            scenario(h,"real teleport control",()->teleportControl(h));
            scenario(h,"doorway destination guards",()->guardControls(h));
        }
        System.out.println("Walking doorway checks="+checks+" failures="+failures.size());
        if(!failures.isEmpty())throw new AssertionError(failures.toString());
    }
    private interface Case {void run()throws Exception;}
    private static void scenario(CurrentCombatHarness h,String name,Case action)throws Exception {
        try{action.run();System.out.println("PASS "+name);}
        catch(AssertionError failure){failures.add(name+": "+failure.getMessage());System.out.println("FAIL "+name+": "+failure.getMessage());}
        finally {
            List<Player> players=new ArrayList<Player>();for(Player p:h.world().getPlayers())players.add(p);for(Player p:players)h.logout(p);
            for(GameTickEvent event:new ArrayList<GameTickEvent>(h.server().getGameEventHandler().getEvents()))event.stop();
            h.server().getGameEventHandler().cleanupEvents();
        }
    }
    private static Player player(CurrentCombatHarness h,int x,int y,int level)throws Exception {
        Player p=h.player("passage test",x,y);
        p.teleportLayered(at(x,y,level),false);p.updateRegion();
        p.getClientLimitations().maxItemId=Integer.MAX_VALUE;h.recordOutgoingPackets(p);
        p.setTeleporting(false);p.setHasMoved(false);return p;
    }
    private static void border(CurrentCombatHarness h,boolean east,int stage,int choice,int coins)throws Exception {
        Player p=player(h,east?93:90,649,0);p.setQuestStage(Quests.PRINCE_ALI_RESCUE,stage);
        if(coins>0)check(p.getCarriedItems().getInventory().add(new Item(ItemId.COINS.id(),coins)),"coins prepared");
        Npc guard=null;for(Npc n:h.world().getNpcs())if(n.getID()==(east?161:162))guard=n;
        check(guard!=null,"authored guard registered");
        h.server().getPluginHandler().handlePlugin(TalkNpcTrigger.class,p,new Object[]{p,guard});
        boolean accepted=stage==-1||stage==3||choice==2&&coins>=10;
        WorldLocation expected=accepted?at(stage==0?(east?91:92):(east?90:93),649,0):p.getWorldLocation();
        trace(h,p,choice,expected);
        check(p.getCarriedItems().getInventory().countId(ItemId.COINS.id())==coins-(accepted&&stage==0?10:0),"toll charged exactly once only when due");
        GameObject gate=find(h,92,649,0);check(gate!=null&&gate.getID()==180,"toll gate ends closed");
    }
    private static void gate(CurrentCombatHarness h,Crossing crossing,int level,int direction)throws Exception {
        int x=12+(direction/2)*8,y=38;
        GameObject gate=find(h,x,y,level);check(gate!=null&&gate.getID()==180,"native closed gate present");
        Player p=player(h,x,y,level);
        Point destination=direction==0?Point.location(p.getX()-3,p.getY()):direction==2?Point.location(p.getX(),p.getY()+3):direction==4?Point.location(p.getX()+3,p.getY()):Point.location(p.getX(),p.getY()-3);
        WorldLocation target=h.world().getRegionManager().fromRuntimeCompatibilityPoint(destination,p.getWorldLocation(),false);
        crossing.helper="gate";crossing.destination=destination;
        h.server().getPluginHandler().handlePlugin(OpLocTrigger.class,p,new Object[]{p,gate,"open"});
        trace(h,p,-1,target);
        check(find(h,x,y,level).getID()==180,"gate closes after passage");
    }
    private static void door(CurrentCombatHarness h,Crossing crossing,String helper,int direction)throws Exception {
        int x=12+direction*8,y=42;
        GameObject door=h.world().getRegionManager().findInteractionBoundary(Point.location(x,y),player(h,x,y,0));
        check(door!=null,"native closed boundary door present");
        Player p=h.world().getPlayers().iterator().next();
        WorldLocation target=direction==0?at(x,y-1,0):direction==1?at(x-1,y,0):at(x+1,y,0);
        if(direction>=2){p.setLayeredLocation(at(x-1,y,0),false);target=at(x+1,y,0);}
        p.setTeleporting(false);p.setHasMoved(false);
        crossing.helper=helper;crossing.destination=null;
        h.server().getPluginHandler().handlePlugin(OpLocTrigger.class,p,new Object[]{p,door,"open"});
        trace(h,p,-1,target);
        check(h.world().getRegionManager().findInteractionBoundary(Point.location(x,y),p).getID()==2,"boundary restores closed frame");
    }
    private static void rangers(CurrentCombatHarness h,boolean entering,int x,int level)throws Exception {
        Player p=player(h,x,entering?462:464,0);p.getSkills().setLevel(Skill.RANGED.id(),level);
        GameObject door=find(h,495,463,0);
        check(door!=null&&door.getID()==64,"Rangers closed door present");
        h.server().getPluginHandler().handlePlugin(OpLocTrigger.class,p,new Object[]{p,door,"open"});
        trace(h,p,-1,level>=66?at(x,entering?464:462,0):p.getWorldLocation());
        check(find(h,495,463,0).getID()==64,"Rangers door restores closed frame");
    }
    private static void legends(CurrentCombatHarness h,boolean outer,boolean entering,boolean allowed)throws Exception {
        int y=outer?(entering?551:549):(entering?539:541);
        Player p=player(h,513,y,0);p.setQuestStage(Quests.LEGENDS_QUEST,allowed?(outer?1:11):0);
        GameObject object=outer?find(h,512,550,0):find(h,512,540,0);
        check(object!=null&&object.getID()==(outer?1079:1080),"Legends closed entrance present");
        h.server().getPluginHandler().handlePlugin(OpLocTrigger.class,p,new Object[]{p,object,"open"});
        trace(h,p,-1,allowed?at(513,outer?(entering?549:552):(entering?541:539),0):p.getWorldLocation());
        GameObject restored=outer?find(h,512,550,0):find(h,512,540,0);
        check(restored!=null&&restored.getID()==(outer?1079:1080),"Legends entrance restores closed frame");
    }
    private static void chain(CurrentCombatHarness h,Crossing crossing)throws Exception {
        Player p=player(h,12,38,0);crossing.helper="chain";
        h.server().getPluginHandler().handlePlugin(OpLocTrigger.class,p,new Object[]{p,find(h,12,38,0),"open"});
        trace(h,p,-1,at(15,38,0));
    }
    private static void interrupted(CurrentCombatHarness h,Crossing crossing)throws Exception {
        Player p=player(h,12,38,0);crossing.helper="gate";crossing.destination=Point.location(15,38);
        h.server().getPluginHandler().handlePlugin(OpLocTrigger.class,p,new Object[]{p,find(h,12,38,0),"open"});
        boolean moved=false;
        for(int tick=0;tick<10;tick++) {
            h.advanceOneCombatTick();if(p.getWorldLocation().equals(at(13,38,0))){moved=true;break;}
        }
        check(moved,"crossing began before interrupt");
        p.teleportLayered(at(18,40,0),false);
        trace(h,p,-1,at(18,40,0));
        check(find(h,12,38,0).getID()==180,"interrupted crossing closes gate");
    }
    private static void teleportControl(CurrentCombatHarness h)throws Exception {
        Player p=player(h,12,38,0);int before=h.countOutgoingPackets(p,OpcodeOut.SEND_WORLD_INFO);
        p.teleport(15,38,false);
        check(p.isTeleporting(),"real teleport retains teleport update");
        check(h.countOutgoingPackets(p,OpcodeOut.SEND_WORLD_INFO)>before,"real teleport retains world info packet");
    }
    private static void guardControls(CurrentCombatHarness h)throws Exception {
        Player p=player(h,12,38,0);
        for(Point target:new Point[]{Point.location(40,38),Point.location(12,38+944),Point.location(14,38)}) {
            boolean refused=false;
            try{Functions.walkThroughDoorway(p,target);}catch(IllegalArgumentException|IllegalStateException expected){refused=true;}
            check(refused,"unsafe or context-free crossing refused: "+target);
            check(p.getWorldLocation().equals(at(12,38,0)),"refused crossing leaves location untouched");
        }
    }
    private static void trace(CurrentCombatHarness h,Player p,int choice,WorldLocation expected)throws Exception {
        boolean teleported=false,skipped=false,wrongFacing=false;int moves=0;WorldLocation initial=p.getWorldLocation();
        int worldPackets=h.countOutgoingPackets(p,OpcodeOut.SEND_WORLD_INFO);
        for(int tick=0;tick<100;tick++) {
            if(p.getMenuHandler()!=null&&p.getOption()==-1&&choice>=0)p.getMenuHandler().handleReply(choice,p.getMenuHandler().getOption(choice));
            WorldLocation before=p.getWorldLocation();p.setTeleporting(false);p.setHasMoved(false);
            p.getWalkingQueue().processNextMovement();h.server().getGameUpdater().executeWalkToActions(p);h.advanceOneCombatTick();
            WorldLocation after=p.getWorldLocation();
            if(!before.equals(after)) {
                moves++;teleported|=p.isTeleporting();
                wrongFacing|=p.getSprite()!=Path.direction(after.getCoordinate().getX()-before.getCoordinate().getX(),after.getCoordinate().getY()-before.getCoordinate().getY());
                skipped|=!before.getWorldSpace().equals(after.getWorldSpace())||before.getCoordinate().getLevel()!=after.getCoordinate().getLevel()
                    ||Math.max(Math.abs(before.getCoordinate().getX()-after.getCoordinate().getX()),Math.abs(before.getCoordinate().getY()-after.getCoordinate().getY()))>1;
            }
            boolean pending=false;for(GameTickEvent e:h.server().getGameEventHandler().getPlayerEvents(p))if(e instanceof PluginTickEvent&&!e.shouldRemove())pending=true;
            if(tick>10&&!p.isBusy()&&!pending&&p.finishedPath())break;
        }
        check(p.getWorldLocation().equals(expected),"correct landing; got "+p.getWorldLocation()+" expected "+expected);
        check(!teleported,"passage uses walking updates, never teleport updates");
        check(!skipped,"every visible step is adjacent on the same signed level/world space");
        check(!wrongFacing,"walking sprite faces each step");
        check(h.countOutgoingPackets(p,OpcodeOut.SEND_WORLD_INFO)==worldPackets,"local passage sends no teleport world-info packet");
        check(initial.equals(expected)||moves>0,"crossing records movement");
        check(!p.isBusy(),"passage script releases player");
    }
    private static WorldLocation at(int x,int y,int level){return WorldLocation.global(new WorldCoordinate(x,y,level));}
    private static GameObject find(CurrentCombatHarness h,int x,int y,int level){return h.world().getRegionManager().findNativeLayeredScenery(at(x,y,level));}
    public static final class Crossing implements OpLocTrigger {
        String helper;Point destination;
        public boolean blockOpLoc(Player p,GameObject o,String command){return o.getX()<48&&o.getY()%944<48;}
        public void onOpLoc(Player p,GameObject o,String command) {
            if(helper.equals("chain")) {
                Functions.walkThroughDoorway(p,p.getX()+1,p.getY());
                Functions.walkThroughDoorway(p,p.getX()+2,p.getY());
            }
            else if(helper.equals("gate"))Functions.doGate(p,o,181,destination);
            else if(helper.equals("door"))Functions.doDoor(o,p);
            else if(helper.equals("tent"))Functions.doTentDoor(o,p);
            else Functions.doWallMovePlayer(o,p,11,3000,true);
        }
    }
}
