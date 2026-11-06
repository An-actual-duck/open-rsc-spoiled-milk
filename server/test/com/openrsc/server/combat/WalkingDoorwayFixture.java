package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
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
    }
    private static void trace(CurrentCombatHarness h,Player p,int choice,WorldLocation expected)throws Exception {
        boolean teleported=false,skipped=false;int moves=0;WorldLocation initial=p.getWorldLocation();
        for(int tick=0;tick<100;tick++) {
            if(p.getMenuHandler()!=null&&p.getOption()==-1&&choice>=0)p.getMenuHandler().handleReply(choice,p.getMenuHandler().getOption(choice));
            WorldLocation before=p.getWorldLocation();p.setTeleporting(false);p.setHasMoved(false);
            p.getWalkingQueue().processNextMovement();h.server().getGameUpdater().executeWalkToActions(p);h.advanceOneCombatTick();
            WorldLocation after=p.getWorldLocation();
            if(!before.equals(after)) {
                moves++;teleported|=p.isTeleporting();
                skipped|=!before.getWorldSpace().equals(after.getWorldSpace())||before.getCoordinate().getLevel()!=after.getCoordinate().getLevel()
                    ||Math.max(Math.abs(before.getCoordinate().getX()-after.getCoordinate().getX()),Math.abs(before.getCoordinate().getY()-after.getCoordinate().getY()))>1;
            }
            boolean pending=false;for(GameTickEvent e:h.server().getGameEventHandler().getPlayerEvents(p))if(e instanceof PluginTickEvent&&!e.shouldRemove())pending=true;
            if(tick>10&&!p.isBusy()&&!pending&&p.finishedPath())break;
        }
        check(p.getWorldLocation().equals(expected),"correct landing; got "+p.getWorldLocation()+" expected "+expected);
        check(!teleported,"passage uses walking updates, never teleport updates");
        check(!skipped,"every visible step is adjacent on the same signed level/world space");
        check(initial.equals(expected)||moves>0,"crossing records movement");
        check(!p.isBusy(),"passage script releases player");
    }
    private static WorldLocation at(int x,int y,int level){return WorldLocation.global(new WorldCoordinate(x,y,level));}
    private static GameObject find(CurrentCombatHarness h,int x,int y,int level){return h.world().getRegionManager().findNativeLayeredScenery(at(x,y,level));}
    public static final class Crossing implements OpLocTrigger {
        String helper;Point destination;
        public boolean blockOpLoc(Player p,GameObject o,String command){return true;}
        public void onOpLoc(Player p,GameObject o,String command) {
            if(helper.equals("gate"))Functions.doGate(p,o,181,destination);
            else if(helper.equals("door"))Functions.doDoor(o,p);
            else if(helper.equals("tent"))Functions.doTentDoor(o,p);
            else Functions.doWallMovePlayer(o,p,11,3000,true);
        }
    }
}
