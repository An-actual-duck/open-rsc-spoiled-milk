package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.misc.MagicGuildPortals;
import com.openrsc.server.plugins.authentic.quests.free.DragonSlayer;
import com.openrsc.server.plugins.authentic.quests.members.*;
import com.openrsc.server.plugins.authentic.quests.members.grandtree.GnomeGlider;
import com.openrsc.server.plugins.authentic.quests.members.watchtower.WatchTowerObstacles;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline real callbacks, original native placements and actual Player transitions. */
public final class LayerTransitionAuditIntegrationTest {
    private interface Action { void run() throws Exception; }
    private static Server server;
    private static Player player;
    private static int checks, pauses;
    private static final JSONArray results = new JSONArray();
    private static final List<String> failures = new ArrayList<String>();
    private static final Deque<Integer> menu = new ArrayDeque<Integer>();
    private static final List<WorldLocation> visits = new ArrayList<WorldLocation>();
    private static boolean baseline;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static WorldLocation at(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static JSONArray triple(WorldLocation location) {
        WorldCoordinate c = location.getCoordinate();
        return new JSONArray(Arrays.asList(c.getX(), c.getY(), c.getLevel()));
    }

    private static GameObject scenery(int x, int y, int level, int id) {
        GameObject object = server.getWorld().getRegionManager().findNativeLayeredScenery(at(x, y, level));
        check(object != null && object.getID() == id, "installed scenery " + id + " at " + at(x,y,level));
        return object;
    }

    private static GameObject boundary(int x, int y, int level, int id) {
        GameObject object = server.getWorld().getRegionManager().findInteractionBoundary(Point.location(x,y), player);
        check(object != null && object.getID() == id && object.getWorldLocation().equals(at(x,y,level)),
            "installed signed boundary " + id);
        return object;
    }

    private static void invoke(OpLocTrigger plugin, GameObject object, String command) {
        check(plugin.blockOpLoc(player, object, command), "specific quest/misc callback owns installed scenery");
        plugin.onOpLoc(player, object, command);
    }

    private static void equip(ItemId id) throws Exception {
        Item item = new Item(id.id()); item.setWielded(true);
        Field field = Equipment.class.getDeclaredField("list"); field.setAccessible(true);
        ((Item[]) field.get(player.getCarriedItems().getEquipment()))[
            item.getDef(server.getWorld()).getWieldPosition()] = item;
    }

    private static void observe() {
        WorldLocation now = player.getWorldLocation();
        if (visits.isEmpty() || !visits.get(visits.size()-1).equals(now)) visits.add(now);
    }

    private static void whileNpcBusy(int id, Action action) throws Exception {
        List<Npc> occupied = new ArrayList<Npc>();
        for (Npc npc:server.getWorld().getNpcs())
            if(npc.getID()==id && !npc.isBusy()){occupied.add(npc);npc.setBusy(true);}
        try { action.run(); }
        finally { for(Npc npc:occupied)npc.setBusy(false); }
    }

    private static void route(String label, WorldLocation start, WorldLocation expected,
                              Action setup, Action action) throws Exception {
        JSONObject result = new JSONObject().put("label", label).put("start", triple(start))
            .put("expected", triple(expected));
        menu.clear(); visits.clear(); pauses=0;
        player.getCache().getCacheMap().clear(); player.getQuestStages().clear();
        player.getCarriedItems().getInventory().getItems().clear();
        Field equipment = Equipment.class.getDeclaredField("list"); equipment.setAccessible(true);
        Arrays.fill((Item[]) equipment.get(player.getCarriedItems().getEquipment()), null);
        player.teleportLayered(start, false); player.setBusy(false); player.resetMenuHandler(); player.setFatigue(0);
        player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),99,99,false);
        setup.run();
        try {
            action.run(); observe();
            check(menu.isEmpty(), "all scripted dialogue choices consumed");
            result.put("actual", triple(player.getWorldLocation()));
            JSONArray trace = new JSONArray(); for (WorldLocation visit:visits) trace.put(triple(visit));
            result.put("trace",trace).put("damage",99-player.getSkills().getLevel(Skill.HITS.id()));
            check(player.getWorldLocation().equals(expected), "wrong signed landing: expected " + expected + ", actual " + player.getWorldLocation());
            // Denial/inspection controls prove no transition, not a new landing.
            if(expected.equals(start)){result.put("unchangedScopeControl",true).put("passed",true);return;}
            check(server.getWorld().getRegionManager().hasNativeLayeredTerrain(expected), "native terrain at expected destination");
            int overlay = server.getWorld().getRegionManager().findNativeLayeredWorldPackage(expected).get()
                .findTile(expected).get().getOverlay();
            check(overlay!=8 && overlay!=250 && overlay!=255, "destination is not authored void");
            if (expected.getCoordinate().getLevel()==1 || expected.getCoordinate().getLevel()==2)
                check(overlay!=0, "upper-floor destination has a floor");
            int exits=0,x=player.getX(),y=player.getY();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                if(PathValidation.checkAdjacent(player,x,y,x+d[0],y+d[1])) exits++;
            result.put("walkingExits",exits).put("overlay",overlay);
            check(exits>0, "destination has an outward walking step");
            result.put("passed",true);
        } catch (AssertionError | RuntimeException failure) {
            result.put("passed",false).put("actual",triple(player.getWorldLocation())).put("failure",failure.toString());
            failures.add(label+": "+failure); System.out.println("FAIL "+failures.get(failures.size()-1));
        } finally {
            for(GameTickEvent event:new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents()))event.stop();
            server.getGameEventHandler().cleanupEvents(); results.put(result);
        }
    }

    private static final Action NONE = () -> { };

    private static void routes() throws Exception {
        final DwarfCannon cannon = new DwarfCannon();
        route("Dwarf Cannon tower down",at(616,491,1),at(616,493,0),NONE,
            ()->invoke(cannon,scenery(616,492,1,985),"climb-down"));
        route("Dwarf Cannon mudpile exit",at(578,523,-1),at(578,521,0),NONE,
            ()->invoke(cannon,scenery(577,523,-1,983),"climb"));
        route("Dwarf Cannon tower entry denied before quest",at(616,493,0),at(616,493,0),NONE,
            ()->invoke(cannon,scenery(616,492,0,981),"climb-up"));
        route("Dwarf Cannon tower entry control",at(616,493,0),at(616,491,1),
            ()->player.setQuestStage(Quests.DWARF_CANNON,1),
            ()->invoke(cannon,scenery(616,492,0,981),"climb-up"));

        final DragonSlayer dragon = new DragonSlayer();
        for(final int[] source:new int[][]{{259,641},{259,662},{281,641},{281,662}})
            for(final int port:new int[]{DragonSlayer.PORT_SARIM,DragonSlayer.CRANDOR})
                route("Dragon Slayer ship exit "+Arrays.toString(source)+" port="+port,
                    at(source[0],source[1]-1,-1),port==DragonSlayer.CRANDOR?at(409,638,0):at(259,641,0),
                    ()->player.getCache().set("lumb_lady",port),
                    ()->invoke(dragon,scenery(source[0],source[1],-1,227),"climb-up"));

        final PlagueCity plague = new PlagueCity();
        route("Plague City mudpile exit",at(621,582,-1),at(620,578,0),NONE,
            ()->invoke(plague,scenery(620,582,-1,448),"climb"));
        route("Plague City sewer exit completed quests",at(635,590,-1),at(632,589,0),
            ()->{player.setQuestStage(Quests.PLAGUE_CITY,-1);player.setQuestStage(Quests.BIOHAZARD,-1);},
            ()->invoke(plague,scenery(636,590,-1,449),"climb-through"));
        route("Plague City sewer exit wearing gasmask",at(635,590,-1),at(632,589,0),
            ()->{player.setQuestStage(Quests.PLAGUE_CITY,5);equip(ItemId.GASMASK);},
            ()->invoke(plague,scenery(636,590,-1,449),"climb-through"));
        route("Plague City sewer exit denied without mask",at(635,590,-1),at(635,590,-1),
            ()->player.setQuestStage(Quests.PLAGUE_CITY,5),
            ()->invoke(plague,scenery(636,590,-1,449),"climb-through"));

        final MagicGuildPortals magic = new MagicGuildPortals();
        for(final int[] portal:new int[][]{{147,602,754,212,695,0},{147,603,754,212,695,0},
                {148,599,757,511,508,1},{148,599,758,511,508,1},
                {149,602,762,362,571,1},{149,603,762,362,571,1}})
            route("Magic guild portal "+Arrays.toString(portal),at(portal[1]+1,portal[2]-1,2),
                at(portal[3],portal[4],portal[5]),NONE,()->{
                    GameObject o=boundary(portal[1],portal[2],2,portal[0]);
                    check(magic.blockOpBound(player,o,0),"portal owns boundary");magic.onOpBound(player,o,0);
                });
        route("Magic guild portal inspect does not travel",at(603,753,2),at(603,753,2),NONE,
            ()->magic.onOpBound(player,boundary(602,754,2,147),1));

        final Waterfall_Quest waterfall = new Waterfall_Quest();
        route("Waterfall cave ledge exit",at(658,472,-1),at(654,485,0),NONE,
            ()->invoke(waterfall,scenery(659,472,-1,469),"jump-off"));
        route("Waterfall flood exit without amulet",at(658,471,-1),at(654,485,0),NONE,
            ()->invoke(waterfall,scenery(659,471,-1,471),"open"));
        route("Waterfall surface jump control",at(661,463,0),at(654,485,0),NONE,
            ()->invoke(waterfall,scenery(662,463,0,462),"jump off"));

        final WatchTowerObstacles watch = new WatchTowerObstacles();
        for(final int[] cave:new int[][]{{1024,648,772,662,788},{1008,654,748,638,776},{969,651,760,609,742}})
            route("Watchtower cave exit "+cave[0],at(cave[1]-1,cave[2],-1),at(cave[3],cave[4],0),NONE,
                ()->invoke(watch,scenery(cave[1],cave[2],-1,cave[0]),"search"));
        for(final int[] web:new int[][]{{188,649,722,648,769},{189,638,731,638,777},
                {190,627,731,629,777},{191,629,741,631,789},{187,626,759,624,807},{192,646,764,645,812}})
            route("Watchtower web exit "+web[0],at(web[1]-1,web[2],-1),at(web[3],web[4],0),NONE,()->{
                GameObject o=boundary(web[1],web[2],-1,web[0]);check(watch.blockOpBound(player,o,0),"web owns boundary");watch.onOpBound(player,o,0);
            });

        final TheHolyGrail grail = new TheHolyGrail();
        for(final int level:new int[]{0,1,2})
            route("Holy Grail whistle exit floor "+level,at(516,35,level),at(490,651,0),NONE,()->{
                Item whistle=new Item(ItemId.MAGIC_WHISTLE.id());
                check(grail.blockOpInv(player,0,whistle,"blow"),"whistle owns item");grail.onOpInv(player,0,whistle,"blow");
            });

        final MerlinsCrystal merlin = new MerlinsCrystal();
        route("Merlin ship cutscene returns to surface",at(435,509,0),at(456,520,0),
            ()->player.setQuestStage(Quests.MERLINS_CRYSTAL,2),
            ()->whileNpcBusy(NpcId.ARHEIN.id(),()->invoke(merlin,scenery(436,509,0,292),"hide")));
        route("Merlin ship entry denied before quest",at(435,509,0),at(435,509,0),NONE,
            ()->invoke(merlin,scenery(436,509,0,292),"hide"));

        final GnomeGlider glider = new GnomeGlider();
        Npc pilot=null;
        for(Npc n:server.getWorld().getNpcs())if(n.getID()==NpcId.GNOME_PILOT_GRANDTREE.id()){pilot=n;break;}
        check(pilot!=null,"installed Grand Tree pilot");final Npc grandPilot=pilot;
        final WorldCoordinate pc=pilot.getWorldLocation().getCoordinate();
        final WorldLocation dock=at(pc.getX()+1,pc.getY(),pc.getLevel());
        for(final int[] destination:new int[][]{{0,389,753},{1,58,504},{2,88,664},{3,400,461}})
            route("Gnome glider destination "+destination[0],dock,at(destination[1],destination[2],0),
                ()->{player.setQuestStage(Quests.GRAND_TREE,-1);menu.add(destination[0]);},
                ()->{check(glider.blockTalkNpc(player,grandPilot),"pilot owns dialogue");glider.onTalkNpc(player,grandPilot);});
        route("Gnome glider story crash",dock,at(425,764,0),
            ()->{player.setQuestStage(Quests.GRAND_TREE,8);menu.add(0);},()->glider.onTalkNpc(player,grandPilot));
        route("Gnome glider declined control",dock,dock,
            ()->{player.setQuestStage(Quests.GRAND_TREE,-1);menu.add(4);},()->glider.onTalkNpc(player,grandPilot));
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        baseline=args.length>0 && args[0].equals("baseline");
        server=new Server("myworld.conf");
        Field field=PluginTask.class.getDeclaredField("tasksMap");field.setAccessible(true);
        Map<String,PluginTask> contexts=(Map<String,PluginTask>)field.get(null);
        try {
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY=false;
            server.getEntityHandler().load();server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
            server.getWorld().getRegionManager().populateNativeLayeredPlacements();
            player=new Player(server.getWorld(),DataConversions.usernameToHash("layeraudit"));
            player.setClientVersion(server.getConfig().CLIENT_VERSION);
            player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
            player.getClientLimitations().maxSceneryId=Integer.MAX_VALUE;player.getClientLimitations().maxItemId=Integer.MAX_VALUE;
            player.setInitialLayeredLocation(at(223,554,0));server.getWorld().getPlayers().add(player);player.updateRegion();player.setLoggedIn(true);
            contexts.put(Thread.currentThread().getName(),new PluginTask(server.getWorld(),player,"OpLoc",new Object[0]) {
                public int action(){return 0;}
                public synchronized void pause(int ticks){
                    if(++pauses>2000)throw new AssertionError("unbounded dialogue wait");observe();
                    if(player.getMenuHandler()!=null && player.getOption()==-1){
                        check(!menu.isEmpty(),"unexpected dialogue menu");player.setOption(menu.remove());
                    }
                }
            });
            routes();
            Files.write(Paths.get(baseline?"baseline-results.json":"candidate-results.json"),
                (results.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Layer transition audit cases="+results.length()+" checks="+checks+" failures="+failures.size()+" baseline="+baseline);
            if(!baseline && !failures.isEmpty())throw new AssertionError(failures.toString());
            if(baseline)check(!failures.isEmpty(),"baseline must reproduce actual defects");
        } finally {
            contexts.remove(Thread.currentThread().getName());
            for(GameTickEvent e:new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents()))e.stop();
            server.getGameEventHandler().cleanupEvents();server.getWorld().getPlayers().clear();server.getEntityHandler().unload();
            for(String n:new String[]{"sqlLoggingThreadPool","sqlThreadPool","onlineMonitorThreadPool"}){
                Field f=Server.class.getDeclaredField(n);f.setAccessible(true);((ExecutorService)f.get(server)).shutdownNow();
            }
            Server.serversList.remove(server.getName(),server);
        }
    }
}
