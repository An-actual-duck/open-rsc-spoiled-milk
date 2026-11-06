package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericWellInteraction;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.database.JDBCDatabaseConnection;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.model.action.DoubleDoorPassage;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.action.WalkToObjectAction;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.handlers.GameObjectAction;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.TargetObjectStruct;
import com.openrsc.server.plugins.handler.PluginHandler;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.google.common.collect.Multimap;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.defaults.Ladders;
import com.openrsc.server.runtime.DeterministicGameRandom;
import com.openrsc.server.runtime.GameClock;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Release-specific acceptance over an explicitly selected external package.
 * Uses maintained World registration, PathValidation and gameplay helpers.
 * No synthetic terrain, owner data in Git, Server.init(), listeners or accounts.
 */
public final class NextUpdateMapAcceptanceTest {
    private final Server server;
    private final Player player;
    private final NativeLayeredWorldPackage map;
    private final Clock clock;
    private int checks;
    private final List<String> failures = new ArrayList<String>();

    private NextUpdateMapAcceptanceTest(Server server, Clock clock) {
        this.server = server;
        this.clock = clock;
        map = server.getWorld().getRegionManager().getNativeLayeredWorldPackage();
        player = new Player(server.getWorld(), DataConversions.usernameToHash("mapaccept"));
        player.setClientVersion(server.getConfig().CLIENT_VERSION);
        player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
        player.setInitialLayeredLocation(at(22, 475, 0));
        server.getWorld().getPlayers().add(player);
        player.updateRegion(); player.setBusy(false); player.setLoggedIn(true);
        player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 99, 99, false);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) { failures.add(message); System.out.println("FAIL " + message); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Explicit package and manifest SHA256 required");
        String[] gates = {"PlayerLocationAuthority", "SpatialRuntimeAuthority", "ProtocolClientAuthority",
            "NativeTerrainPackage", "NativeTerrainResidency", "NativeTerrainReadiness", "NativeTerrainPrediction",
            "NativeTerrainSymmetricResidency", "NativeTerrainAtomicActivation"};
        for (String gate : gates) System.setProperty("openrsc.layered" + gate, "true");
        System.setProperty("openrsc.layeredSyntheticDeepFixture", "false");
        System.setProperty("openrsc.layeredNativeTerrainPackagePath", Paths.get(args[0]).toRealPath().toString());
        System.setProperty("openrsc.layeredNativeTerrainManifestSha256", args[1]);
        System.setProperty("openrsc.layeredNativeWorldRuntimeProfile", "native-replacement");
        Clock clock = new Clock();
        Server server = new Server("myworld.conf", clock, new DeterministicGameRandom(4748));
        try {
            // Any accidental JDBC operation fails, even against the copied sample config.
            Field database = Server.class.getDeclaredField("database"); database.setAccessible(true);
            database.set(server, new MySqlGameDatabase(server) {
                @Override public JDBCDatabaseConnection getConnection() {
                    throw new AssertionError("Offline map acceptance attempted JDBC access");
                }
            });
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
            server.getEntityHandler().load();
            server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
            System.out.println("PASS maintained material/placement/collision-footprint/NPC-capacity preflight");
            server.getWorld().getRegionManager().populateNativeLayeredPlacements();
            NextUpdateMapAcceptanceTest test = new NextUpdateMapAcceptanceTest(server, clock);
            test.check(test.map.getManifestSha256().equals(args[1]), "runtime selected exact manifest");
            test.check(server.getWorld().getRegionManager().getNativeLayeredWorldPackageCount() == 1
                && server.getWorld().getRegionManager().replacesLegacyBasePopulation(), "one replacement package");
            System.out.println("PACKAGE manifest=" + test.map.getManifestSha256() + " sectors=" + test.map.getTerrainSectorCount()
                + " npc=" + test.map.getNpcPlacementCount() + " items=" + test.map.getGroundItemPlacementCount()
                + " scenery=" + test.map.getSceneryPlacementCount() + " boundaries=" + test.map.getBoundaryPlacementCount());
            test.tower(); test.dragons(); test.eggs(); test.wells();
            System.out.println("BASELINE_RESULT checks=" + test.checks + " failures=" + test.failures.size());
            test.ichorLadder();
            System.out.println("RESULT checks=" + test.checks + " failures=" + test.failures.size());
            System.out.println("LIMITS: offline in-memory runtime; real door packet/plugin/rank path covered offline; no client rendering, live-player acceptance, combat, saving or database persistence proof");
            if (!test.failures.isEmpty()) throw new AssertionError("Candidate map acceptance failed: " + test.failures.size());
        } finally {
            for (GameTickEvent event : new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents())) event.stop();
            Field executor = PluginHandler.class.getDeclaredField("executor"); executor.setAccessible(true);
            ExecutorService plugins = (ExecutorService) executor.get(server.getPluginHandler());
            if (plugins != null) plugins.shutdownNow();
            server.getGameEventHandler().cleanupEvents(); server.getWorld().getPlayers().clear(); server.getEntityHandler().unload();
            for (String name : new String[]{"sqlLoggingThreadPool", "sqlThreadPool", "onlineMonitorThreadPool"}) {
                Field field = Server.class.getDeclaredField(name); field.setAccessible(true);
                ((ExecutorService) field.get(server)).shutdownNow();
            }
            Server.serversList.remove(server.getName(), server);
        }
    }

    private void tower() throws Exception {
        installDoorPlugins();
        for (int lane = 0; lane < 2; lane++) {
            setTowerRank(MonsterSlayerRank.HERO);
            WorldLocation outside = at(22 + lane, 474, 0), inside = at(22 + lane, 473, 0);
            GameObject object = scenery(at(22, 474, 0));
            check(object != null && object.getID() == 1337 && object.getDirection() == 6, "tower entrance binding " + object);
            if (object == null) continue;
            move(at(22 + lane, 475, 0));
            check(MonsterSlayerTowerPassages.passage(player, object, "open") != null, "tower entrance rank binding lane " + lane);
            check(PathValidation.checkAdjacent(player, 22 + lane, 475, 22 + lane, 474), "tower exterior approach lane " + lane);
            approachDoor(object, at(22 + lane, 475, 0), outside);
            check(player.getWorldLocation().equals(inside), "actual entrance crossing lane " + lane + " got " + player.getWorldLocation());
            drain(); drain();
            object = scenery(at(22, 474, 0));
            check(object.getID() == 1337, "tower entrance auto-close lane " + lane);
            check(MonsterSlayerTowerPassages.passage(player, object, "open") == null, "tower free exit rank binding lane " + lane);
            setTowerRank(MonsterSlayerRank.UNSTAMPED); // Leaving remains free, even without membership.
            approachDoor(object, at(22 + lane, 472, 0), inside);
            check(player.getWorldLocation().equals(outside), "actual entrance exit lane " + lane);
            drain(); drain();
        }
        rejectedDoorActions();
        Ladders ladders = new Ladders();
        for (int level = 0; level < 5; level++) {
            int x = level % 2 == 0 ? 6 : 37;
            GameObject up = scenery(at(x, 472, level));
            check(up != null && up.getID() == 43 && up.getDirection() == 0, "tower upward binding " + at(x, 472, level));
            if (up == null) continue;
            move(at(x, 471, level));
            check(player.atObject(up), "tower up stairs approachable " + up.getWorldLocation());
            check(MonsterSlayerTowerPassages.passage(player, up, "climb-up") != null, "tower actual rank binding " + up.getWorldLocation());
            ladders.onObjectAction(up, "climb-up", player);
            WorldLocation landing = at(x, 475, level + 1);
            check(player.getWorldLocation().equals(landing), "actual tower upward transport " + up.getWorldLocation() + " got " + player.getWorldLocation());
            check(standing(landing) && exits(landing) > 0, "tower upward landing has runtime footing and exit " + landing + " " + tile(landing));
            GameObject down = scenery(at(x, 472, level + 1));
            check(down != null && down.getID() == 44 && down.getDirection() == 0, "tower downward counterpart " + at(x, 472, level + 1));
            if (down != null) {
                check(player.atObject(down), "tower down stairs approachable from upward landing " + landing);
                check(MonsterSlayerTowerPassages.passage(player, down, "climb-down") == null, "tower free downward binding " + down.getWorldLocation());
                ladders.onObjectAction(down, "climb-down", player);
                landing = at(x, 471, level);
                check(player.getWorldLocation().equals(landing), "actual tower downward transport " + down.getWorldLocation());
                check(standing(landing) && exits(landing) > 0, "tower downward landing has runtime footing and exit " + landing + " " + tile(landing));
            }
        }
        int[][] spots = {{23,475,0}, {8,471,0}, {39,471,1}, {8,471,2}, {39,471,3}, {8,471,4}};
        for (int index = 0; index < spots.length; index++) {
            int id = 871 + index, count = 0;
            WorldLocation expected = at(spots[index][0], spots[index][1], spots[index][2]);
            for (NativeLayeredPlacementSet set : map.getPlacementSets().values()) for (NativeLayeredNpcPlacement placement : set.getNpcs()) {
                if (placement.getNpcId() != id) continue;
                count++;
                check(placement.getStart().equals(expected) && placement.getMinX() == spots[index][0] && placement.getMaxX() == spots[index][0]
                    && placement.getMinY() == spots[index][1] && placement.getMaxY() == spots[index][1], "stationary associate " + id + " " + placement.getStart());
            }
            check(count == 1, "associate placement count id=" + id + " count=" + count);
            int actual = 0;
            for (Npc npc : server.getWorld().getNpcs()) if (npc.getID() == id && npc.getWorldLocation().equals(expected)) {
                actual++; check(!npc.getDef().isAttackable() && !npc.getDef().isAggressive(), "runtime friendly associate " + id);
            }
            check(actual == 1 && standing(expected), "runtime associate registration/footing id=" + id + " " + expected);
            WorldLocation routeStart=index==0?at(22,475,0):at(spots[index][0]-2,471,spots[index][2]);
            check(flood(routeStart,2,43,450,478).contains(key(expected.getCoordinate().getX(),expected.getCoordinate().getY())),
                "associate reachable from passage approach id="+id+" at="+expected);
            System.out.println("ASSOCIATE id=" + id + " count=" + count + " at=" + expected + " exits=" + exits(expected));
        }
    }

    /** Exercise the real packet, walk-to action, rank dispatch, plugin thread and default handler. */
    private void approachDoor(final GameObject object, WorldLocation approach, WorldLocation edge) throws Exception {
        move(approach);
        player.setTeleporting(false);
        player.setHasMoved(false);
        clickDoor(object);
        WalkToAction action = player.getWalkToAction();
        check(action != null, "real door packet schedules an approach");
        if (action == null) return;
        server.getGameUpdater().executeWalkToActions(player);
        check(!action.isExecuted(), "tower door does not activate before the approach finishes from " + approach);
        Path path = new Path(player, Path.PathType.WALK_TO_POINT);
        path.addStep(edge.getCoordinate().getX(), edge.getCoordinate().getY());
        player.getWalkingQueue().setPath(path);
        for (int step = 0; step < 3 && !action.isExecuted(); step++) {
            player.getWalkingQueue().processNextMovement();
            server.getGameUpdater().executeWalkToActions(player);
        }
        check(action.isExecuted(), "tower door activates after walking to its edge");
        check(server.getGameEventHandler().getEvents().stream().anyMatch(event ->
            event.getDescriptor().equals("Default.onOpLoc") && !event.shouldRemove()),
            "eligible rank reaches real default plugin");
        server.getGameEventHandler().runPlayerEvents(player);
        server.getGameEventHandler().cleanupEvents();
        check(player.hasMoved() && !player.isTeleporting(), "tower door crosses with a walking update");
        check(scenery(object.getWorldLocation()).getID() == 1338, "packet/plugin path opens the door");
        check(!player.isBusy() && player.getOwnedPlugins().isEmpty(), "door script releases its busy ownership");
    }

    private void clickDoor(GameObject object) throws Exception {
        TargetObjectStruct click = new TargetObjectStruct();
        click.setOpcode(OpcodeIn.OBJECT_COMMAND);
        click.coordObject = object.getLocation();
        new GameObjectAction().process(click, player);
    }

    private void rejectedDoorActions() throws Exception {
        final GameObject object = scenery(at(22, 474, 0));
        WorldLocation edge = object.getWorldLocation();
        move(edge);
        setTowerRank(MonsterSlayerRank.HERO);
        player.setBusy(true);
        clickDoor(object);
        check(player.getWalkToAction() == null && player.isBusy(), "busy player packet remains blocked");
        DoubleDoorPassage.begin(player, object, 1338);
        check(scenery(edge) == object && player.getWorldLocation().equals(edge) && player.isBusy(),
            "helper cannot bypass unrelated busy state or clear it");
        player.setBusy(false);
        PluginTask unrelated = new PluginTask(server.getWorld(), player, "useinv", new Object[0]) {
            public int action() {
                check(player.isBusy(), "real inventory script owns busy state");
                DoubleDoorPassage.begin(player, object, 1338);
                return 0;
            }
        };
        server.getGameEventHandler().add(new PluginTickEvent(server.getWorld(), player,
            "Unrelated inventory action", null, unrelated));
        server.getGameEventHandler().runPlayerEvents(player);
        server.getGameEventHandler().cleanupEvents();
        check(scenery(edge) == object && player.getWorldLocation().equals(edge) && !player.isBusy(),
            "unrelated executing script cannot use the door busy exception");

        setTowerRank(MonsterSlayerRank.UNSTAMPED);
        Npc guard = null;
        for (Npc candidate : player.getViewArea().getNpcsInView()) if (candidate.getID() == 871) guard = candidate;
        check(guard != null, "entrance guard available for real denied click");
        if (guard != null) guard.getUpdateFlags().setChatMessage(null);
        clickDoor(object);
        server.getGameUpdater().executeWalkToActions(player);
        check(server.getGameEventHandler().getEvents().stream().anyMatch(event ->
            event.getDescriptor().equals("MonsterSlayerTowerAssociates.onOpLoc") && !event.shouldRemove()),
            "ineligible real door click reaches rank refusal instead of default");
        for (int tick = 0; tick < 10; tick++) {
            server.getGameEventHandler().runPlayerEvents(player);
            server.getGameEventHandler().cleanupEvents();
        }
        check(scenery(edge) == object && player.getWorldLocation().equals(edge), "rank refusal leaves entrance closed");
        check(guard != null && guard.getUpdateFlags().getChatMessage() != null
            && guard.getUpdateFlags().getChatMessage().getMessageString().equals(MonsterSlayerTowerAccess.FLEDGLING.getRefusal()),
            "denied door click produces the actual nearby NPC refusal");
        check(!player.isBusy() && player.getOwnedPlugins().isEmpty() && guard != null && !guard.isBusy(),
            "denied dialogue releases player and NPC busy ownership");
    }

    @SuppressWarnings("unchecked")
    private void installDoorPlugins() throws Exception {
        // Same real default and rank plugins as production; no Server.init/listeners or shop registrars.
        PluginHandler handler = server.getPluginHandler();
        Field reloading = PluginHandler.class.getDeclaredField("reloading"); reloading.setAccessible(true);
        reloading.setBoolean(handler, false);
        Field defaults = PluginHandler.class.getDeclaredField("defaultHandler"); defaults.setAccessible(true);
        defaults.set(handler, Class.forName("com.openrsc.server.plugins.authentic.defaults.Default").newInstance());
        Field instances = PluginHandler.class.getDeclaredField("triggerTypeToInstance"); instances.setAccessible(true);
        ((Multimap<Class<?>, Object>) instances.get(handler)).put(OpLocTrigger.class,
            Class.forName("com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerTowerAssociates").newInstance());
        ExecutorService pluginExecutor = Executors.newFixedThreadPool(1);
        Field executor = PluginHandler.class.getDeclaredField("executor"); executor.setAccessible(true);
        executor.set(handler, pluginExecutor);
        MonsterSlayerData data = MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"),
            new MonsterSlayerData.ReferenceCatalog() {
                public boolean npcExists(int id) { return server.getEntityHandler().getNpcDef(id) != null; }
                public boolean npcAttackable(int id) { return server.getEntityHandler().getNpcDef(id).isAttackable(); }
                public boolean npcSpawned(int id) { for (Npc npc : server.getWorld().getNpcs()) if (npc.getID() == id) return true; return false; }
                public boolean itemExists(int id) { return server.getEntityHandler().getItemDef(id) != null; }
            });
        Field slayer = World.class.getDeclaredField("monsterSlayerData"); slayer.setAccessible(true);
        slayer.set(server.getWorld(), data);
    }

    private void setTowerRank(MonsterSlayerRank rank) {
        MonsterSlayerData data = server.getWorld().getMonsterSlayerData();
        Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
        for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder())
            cursors.put(contact.getKey(), rank.isAtLeast(contact.getAwardedRank()) ? contact.getMandatoryTasks().size() : 0);
        MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.create(rank == MonsterSlayerRank.UNSTAMPED ? 0 : 2,
            rank, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0, 1, MonsterSlayerState.LegacyStatus.NONE, 0, data));
    }

    private void dragons() {
        List<NativeLayeredNpcPlacement> dragons = new ArrayList<NativeLayeredNpcPlacement>();
        for (int id = 877; id <= 881; id++) {
            int count = 0;
            for (NativeLayeredPlacementSet set : map.getPlacementSets().values()) for (NativeLayeredNpcPlacement placement : set.getNpcs()) {
                if (placement.getNpcId() != id) continue;
                count++; dragons.add(placement);
                check(placement.getStart().getCoordinate().getLevel() == -2 && standing(placement.getStart()) && exits(placement.getStart()) > 0,
                    "gem dragon runtime spawn footing/access id=" + id + " " + placement.getStart() + " " + tile(placement.getStart()));
                System.out.println("DRAGON id=" + id + " at=" + placement.getStart() + " roam=" + placement.getMinX() + "," + placement.getMinY() + ".." + placement.getMaxX() + "," + placement.getMaxY());
            }
            check(count > 0, "gem dragon placements id=" + id); System.out.println("DRAGON_COUNT id=" + id + " count=" + count);
        }
        // Discover the actual cave's authored upward interaction from effective definitions.
        int entrances = 0;
        Set<Long> caveReachable = new HashSet<Long>();
        for (NativeLayeredPlacementSet set : map.getPlacementSets().values()) for (NativeLayeredSceneryPlacement placement : set.getScenery()) {
            WorldCoordinate c = placement.getLocation().getCoordinate();
            if (c.getLevel() != -2 || c.getX() < 320 || c.getX() > 410 || c.getY() < 570 || c.getY() > 650) continue;
            GameObject object = scenery(placement.getLocation());
            String command = upCommand(object);
            if (command == null) continue;
            WorldLocation approach = approach(object);
            if (approach == null) continue;
            Set<Long> reachable = flood(approach, 320, 410, 570, 650);
            boolean belongs = false;
            for (NativeLayeredNpcPlacement dragon : dragons) if (reachable.contains(key(dragon.getStart().getCoordinate().getX(), dragon.getStart().getCoordinate().getY()))) belongs = true;
            if (!belongs) continue; // A nearby classic dungeon is a separate connected component.
            entrances++; caveReachable.addAll(reachable);
            int safe = 0;
            for (WorldLocation from : approaches(object)) {
                move(from);
                WalkToObjectAction action=new WalkToObjectAction(player,object){public void executeInternal(){}};
                check(action.shouldExecute(),"gem cave real object walker admits approach "+from);
                new Ladders().onObjectAction(object, command, player);
                WorldLocation destination = player.getWorldLocation();
                boolean clear = destination.getCoordinate().getLevel() == -1 && standing(destination) && exits(destination) > 0;
                check(clear,"gem cave admitted upward approach lands blocked from="+from+" destination="+destination+" "+tile(destination));
                if (clear) {safe++; caveSurfaceRoute(object,destination);}
                System.out.println("CAVE_TRAVEL id=" + object.getID() + " from=" + object.getWorldLocation() + " approach=" + from + " destination=" + destination + " safe=" + clear + " " + tile(destination));
            }
            check(safe > 0, "gem cave actual upward travel has no safe destination from " + object.getWorldLocation());
            System.out.println("CAVE_EXIT id=" + object.getID() + " from=" + object.getWorldLocation() + " safeApproaches=" + safe + " connectedTiles=" + reachable.size());
        }
        check(entrances > 0, "gem cave has an authored upward exit in cave bounds");
        for (NativeLayeredNpcPlacement dragon : dragons) check(caveReachable.contains(key(dragon.getStart().getCoordinate().getX(), dragon.getStart().getCoordinate().getY())),
            "gem cave spawn connected to an exit id=" + dragon.getNpcId() + " spawn=" + dragon.getStart());
        for (NativeLayeredPlacementSet set : map.getPlacementSets().values()) for (NativeLayeredSceneryPlacement placement : set.getScenery()) {
            WorldCoordinate c=placement.getLocation().getCoordinate();
            if(c.getLevel() < -2 || c.getLevel() > 0 || c.getX()<320 || c.getX()>410 || c.getY()<570 || c.getY()>650)continue;
            GameObject object=scenery(placement.getLocation());
            String commands=object.getGameObjectDef().getCommand1()+"/"+object.getGameObjectDef().getCommand2();
            if(commands.toLowerCase(java.util.Locale.ROOT).contains("climb")||commands.toLowerCase(java.util.Locale.ROOT).contains("go down")||commands.toLowerCase(java.util.Locale.ROOT).contains("go up"))
                System.out.println("NEAR_VERTICAL id="+object.getID()+" at="+object.getWorldLocation()+" commands="+commands+" "+tile(object.getWorldLocation()));
        }
    }

    private void caveSurfaceRoute(GameObject caveExit,WorldLocation corridor) {
        Set<Long> corridorTiles=flood(corridor,320,410,570,650);
        GameObject reciprocal=scenery(at(caveExit.getWorldLocation().getCoordinate().getX(),caveExit.getWorldLocation().getCoordinate().getY(),-1));
        check(reciprocal!=null&&downCommand(reciprocal)!=null,"gem cave reciprocal downward ladder");
        if(reciprocal!=null&&downCommand(reciprocal)!=null){
            move(corridor);check(player.atObject(reciprocal),"gem cave down ladder accessible from clear return landing");
            new Ladders().onObjectAction(reciprocal,downCommand(reciprocal),player);
            check(player.getWorldLocation().equals(at(corridor.getCoordinate().getX(),corridor.getCoordinate().getY(),-2)),"gem cave actual downward return");
        }
        int surfaceRoutes=0;
        for(NativeLayeredPlacementSet set:map.getPlacementSets().values())for(NativeLayeredSceneryPlacement placement:set.getScenery()){
            WorldCoordinate c=placement.getLocation().getCoordinate();
            if(c.getLevel()!=-1||c.getX()<320||c.getX()>410||c.getY()<570||c.getY()>650)continue;
            GameObject up=scenery(placement.getLocation());String command=upCommand(up);if(command==null)continue;
            for(WorldLocation from:approaches(up)){
                if(!corridorTiles.contains(key(from.getCoordinate().getX(),from.getCoordinate().getY())))continue;
                move(from);new Ladders().onObjectAction(up,command,player);WorldLocation surface=player.getWorldLocation();
                if(surface.getCoordinate().getLevel()!=0||!standing(surface)||exits(surface)==0)continue;
                GameObject down=scenery(at(c.getX(),c.getY(),0));
                if(down==null||downCommand(down)==null)continue;
                move(surface);if(!player.atObject(down))continue;
                new Ladders().onObjectAction(down,downCommand(down),player);WorldLocation returned=player.getWorldLocation();
                if(returned.getCoordinate().getLevel()!=-1||!standing(returned)||!corridorTiles.contains(key(returned.getCoordinate().getX(),returned.getCoordinate().getY())))continue;
                surfaceRoutes++;
                System.out.println("CAVE_SURFACE_ROUTE up="+up.getWorldLocation()+" from="+from+" surface="+surface+" returned="+returned+" corridorTiles="+corridorTiles.size());
            }
        }
        check(surfaceRoutes>0,"gem cave clear landing connects to actual round-trip surface stairs from="+corridor);
    }

    private void eggs() {
        for (int id : new int[]{3400, 3401}) {
            int count = 0;
            for (NativeLayeredPlacementSet set : map.getPlacementSets().values()) for (NativeLayeredGroundItemPlacement placement : set.getGroundItems()) {
                if (placement.getItemId() != id) continue;
                count++; WorldLocation location = placement.getLocation(); move(location);
                GroundItem item = server.getWorld().getRegionManager().findInteractionGroundItem(id, player.getLocation(), player);
                check(item != null && item.getAmount() == placement.getAmount(), "egg actual registered spawn id=" + id + " " + location);
                check(standing(location) && exits(location) > 0, "egg runtime pickup footing/access id=" + id + " " + location + " " + tile(location));
                check(placement.getRespawnSeconds() > 0, "egg positive respawn id=" + id + " " + location);
                if (item == null) continue;
                item.remove();
                check(server.getWorld().getRegionManager().findInteractionGroundItem(id, player.getLocation(), player) == null,
                    "egg removed before respawn id=" + id + " " + location);
                int ticks=(int)Math.ceil(placement.getRespawnSeconds()*1000D/server.getConfig().GAME_TICK);
                for(int tick=1;tick<ticks;tick++)drain();
                check(server.getWorld().getRegionManager().findInteractionGroundItem(id, player.getLocation(), player) == null,
                    "egg stays absent until authored respawn tick id=" + id + " " + location);
                drain();
                GroundItem respawned = server.getWorld().getRegionManager().findInteractionGroundItem(id, player.getLocation(), player);
                check(respawned != null && respawned != item && respawned.getAmount() == placement.getAmount(),
                    "egg actual delayed runtime respawn id=" + id + " " + location);
                System.out.println("EGG id=" + id + " at=" + location + " amount=" + placement.getAmount() + " respawnSeconds=" + placement.getRespawnSeconds());
            }
            check(count > 0, "egg authored placement count id=" + id); System.out.println("EGG_COUNT id=" + id + " count=" + count);
        }
    }

    private void wells() {
        for (int id : new int[]{1354, 1355}) {
            int count = 0, reachable = 0;
            for (NativeLayeredPlacementSet set : map.getPlacementSets().values()) for (NativeLayeredSceneryPlacement placement : set.getScenery()) {
                if (placement.getSceneryId() != id) continue;
                count++; GameObject object = scenery(placement.getLocation()); int approaches = 0;
                check(object != null && object.getGameObjectDef().isInteractionOnly(), "well runtime interaction marker id=" + id + " " + placement.getLocation());
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    WorldLocation land = offset(placement.getLocation(), dx, dy);
                    if (!standing(land)) continue;
                    move(land);
                    if (ClericWellInteraction.canInteract(player, object) && exits(land) > 0) approaches++;
                }
                if (approaches > 0) reachable++;
                check(approaches > 0, "Dip sigils blocked from every adjacent land tile id=" + id + " " + placement.getLocation() + " " + tile(placement.getLocation()));
                if(approaches==0)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                    WorldLocation sample=offset(placement.getLocation(),dx,dy);
                    System.out.println("WELL_NEIGHBOR well="+placement.getLocation()+" sample="+sample+" "+tile(sample));
                }
                System.out.println("WELL id=" + id + " at=" + placement.getLocation() + " landApproaches=" + approaches);
            }
            check(count > 0, "well authored placement count id=" + id);
            System.out.println("WELL_COUNT id=" + id + " total=" + count + " reachable=" + reachable);
        }
        shoreDiagnostics();
    }

    private void shoreDiagnostics(){
        for(int y=662;y<=684;y++){
            StringBuilder masks=new StringBuilder(),overlays=new StringBuilder();
            for(int x=94;x<=114;x++){
                WorldLocation location=at(x,y,-1);TileValue actual=server.getWorld().getTile(location);NativeLayeredTerrainTile raw=map.findTile(location).orElse(null);
                if(x>94){masks.append(',');overlays.append(',');}masks.append(actual.traversalMask&255);overlays.append(raw==null?-1:raw.getOverlay());
            }
            System.out.println("ICHOR_SHORE_ROW x=94..114 y="+y+" masks="+masks+" overlays="+overlays);
        }
        for(NativeLayeredPlacementSet set:map.getPlacementSets().values())for(NativeLayeredSceneryPlacement placement:set.getScenery()){
            WorldCoordinate c=placement.getLocation().getCoordinate();
            if(c.getLevel()!=-1||c.getX()<85||c.getX()>120||c.getY()<645||c.getY()>690)continue;
            GameObject object=scenery(placement.getLocation());String command=upCommand(object);
            if(command!=null){
                WorldLocation from=approach(object);Set<Long> reachable=from==null?new HashSet<Long>():flood(from,85,120,645,690);
                System.out.println("ICHOR_ROOM_EXIT id="+object.getID()+" at="+object.getWorldLocation()+" approach="+from+" connectedTiles="+reachable.size());
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)System.out.println("ICHOR_EXIT_NEIGHBOR "+offset(object.getWorldLocation(),dx,dy)+" "+tile(offset(object.getWorldLocation(),dx,dy)));
                for(int y=665;y<=676;y++)for(int x=98;x<=107;x++)if(reachable.contains(key(x,y)))System.out.println("ICHOR_REACHABLE_LAND "+at(x,y,-1)+" "+tile(at(x,y,-1)));
            }
        }
        Set<Long> corridor=flood(at(109,674,-1),85,120,645,690);
        System.out.println("ICHOR_EXISTING_CORRIDOR origin="+at(109,674,-1)+" connectedTiles="+corridor.size());
        for(int y=665;y<=686;y++)for(int x=98;x<=114;x++)if(corridor.contains(key(x,y)))System.out.println("ICHOR_CORRIDOR_LAND "+at(x,y,-1)+" "+tile(at(x,y,-1)));
    }

    /** A shoreline repair must also leave every ordinary ladder contact safe. */
    private void ichorLadder(){
        GameObject up=scenery(at(108,681,-1)),down=scenery(at(108,681,0));
        check(up!=null&&upCommand(up)!=null,"Ichor authored upward ladder at108,681,L-1");
        check(down!=null&&downCommand(down)!=null,"Ichor reciprocal downward ladder at108,681,L0");
        System.out.println("ICHOR_LADDER_PAIR up="+(up==null?"missing":up.getID()+"@"+up.getWorldLocation())
            +" down="+(down==null?"missing":down.getID()+"@"+down.getWorldLocation()));
        if(up==null||down==null||upCommand(up)==null||downCommand(down)==null)return;
        Set<Long> reachableFromLadder=new HashSet<Long>();
        for(GameObject source:new GameObject[]{up,down}){
            boolean upward=source==up;GameObject inverse=upward?down:up;
            String command=upward?upCommand(source):downCommand(source);
            String inverseCommand=upward?downCommand(inverse):upCommand(inverse);
            List<WorldLocation> contacts=new ArrayList<WorldLocation>();
            WorldCoordinate origin=source.getWorldLocation().getCoordinate();
            for(int dx=-4;dx<=4;dx++)for(int dy=-4;dy<=4;dy++){
                WorldLocation candidate=at(origin.getX()+dx,origin.getY()+dy,origin.getLevel());
                if(!standing(candidate))continue;
                move(candidate);
                WalkToObjectAction action=new WalkToObjectAction(player,source){public void executeInternal(){}};
                if(action.shouldExecute())contacts.add(candidate);
            }
            check(!contacts.isEmpty(),"Ichor ladder has an admissible land contact "+source.getWorldLocation());
            int safe=0;
            for(WorldLocation from:contacts){
                check(exits(from)>0,"Ichor ladder source footing has a movement exit "+from);
                move(from);new Ladders().onObjectAction(source,command,player);
                WorldLocation destination=player.getWorldLocation();
                WorldCoordinate c=from.getCoordinate();WorldLocation expected=at(c.getX(),c.getY(),c.getLevel()+(upward?1:-1));
                check(destination.equals(expected),"Ichor actual ladder destination from="+from+" got="+destination+" expected="+expected);
                boolean clear=standing(destination)&&exits(destination)>0;
                check(clear,"Ichor admitted ladder contact lands blocked from="+from+" destination="+destination+" "+tile(destination));
                System.out.println("ICHOR_LADDER_TRAVEL id="+source.getID()+" from="+from+" destination="+destination+" safe="+clear+" "+tile(destination));
                if(!clear)continue;
                move(destination);
                WalkToObjectAction reverse=new WalkToObjectAction(player,inverse){public void executeInternal(){}};
                boolean returnAdmitted=reverse.shouldExecute();
                check(returnAdmitted,"Ichor destination admits reciprocal ladder from="+destination+" inverse="+inverse.getWorldLocation());
                if(!returnAdmitted)continue;
                new Ladders().onObjectAction(inverse,inverseCommand,player);
                boolean returned=player.getWorldLocation().equals(from)&&standing(player.getWorldLocation());
                check(returned,"Ichor exact ladder round trip from="+from+" returned="+player.getWorldLocation());
                if(!returned)continue;
                safe++;
                WorldLocation below=upward?from:destination;
                Set<Long> component=flood(below,85,120,645,690);
                reachableFromLadder.addAll(component);
                int totalWells=0,connectedWells=0;
                for(NativeLayeredPlacementSet set:map.getPlacementSets().values())for(NativeLayeredSceneryPlacement placement:set.getScenery()){
                    if(placement.getSceneryId()!=1354)continue;totalWells++;
                    if(ichorShoreReachable(placement,component))connectedWells++;
                }
                check(totalWells>0&&connectedWells==totalWells,"Ichor each ladder contact reaches every shore from="+from+" connected="+connectedWells+"/"+totalWells);
                System.out.println("ICHOR_LADDER_CONTACT_COMPONENT from="+from+" below="+below+" tiles="+component.size()+" connectedWells="+connectedWells+"/"+totalWells);
            }
            System.out.println("ICHOR_LADDER_CONTACTS at="+source.getWorldLocation()+" admitted="+contacts.size()+" safeRoundTrips="+safe);
        }
        for(NativeLayeredPlacementSet set:map.getPlacementSets().values())for(NativeLayeredSceneryPlacement placement:set.getScenery()){
            if(placement.getSceneryId()!=1354)continue;
            boolean reachable=ichorShoreReachable(placement,reachableFromLadder);
            check(reachable,"Ichor Dip shore connected to a safe ladder round trip "+placement.getLocation());
        }
        System.out.println("ICHOR_LADDER_CONNECTED_TILES count="+reachableFromLadder.size());
    }

    private boolean ichorShoreReachable(NativeLayeredSceneryPlacement placement,Set<Long> component){
        GameObject well=scenery(placement.getLocation());
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
            if(dx==0&&dy==0)continue;WorldLocation land=offset(placement.getLocation(),dx,dy);WorldCoordinate c=land.getCoordinate();
            if(!component.contains(key(c.getX(),c.getY())))continue;
            move(land);if(ClericWellInteraction.canInteract(player,well))return true;
        }
        return false;
    }

    private GameObject scenery(WorldLocation location) { return server.getWorld().getRegionManager().findNativeLayeredScenery(location); }
    private void move(WorldLocation location) { player.resetAll(); player.teleportLayered(location, false); player.setBusy(false); }
    private boolean standing(WorldLocation location) {
        try {
            TileValue tile = server.getWorld().getTile(location);
            return map.findTile(location).isPresent() && tile != null && (tile.traversalMask & (CollisionFlag.FULL_BLOCK | CollisionFlag.OBJECT)) == 0;
        } catch (IllegalArgumentException | IllegalStateException unavailable) { return false; }
    }
    private int exits(WorldLocation location) {
        if (!standing(location)) return 0;
        move(location); int count = 0;
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) if (standing(offset(location,d[0],d[1]))
            && PathValidation.checkAdjacent(player, player.getX(), player.getY(), player.getX()+d[0], player.getY()+d[1])) count++;
        return count;
    }
    private WorldLocation approach(GameObject object) {
        List<WorldLocation> options=approaches(object);return options.isEmpty()?null:options.get(0);
    }
    private List<WorldLocation> approaches(GameObject object) {
        List<WorldLocation> options=new ArrayList<WorldLocation>();
        WorldCoordinate c = object.getWorldLocation().getCoordinate();
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) {
            WorldLocation land = at(c.getX()+dx,c.getY()+dy,c.getLevel());
            if (!standing(land) || exits(land) == 0) continue;
            move(land); if (player.atObject(object)) options.add(land);
        }
        return options;
    }
    private Set<Long> flood(WorldLocation origin, int minX, int maxX, int minY, int maxY) {
        move(origin); Set<Long> seen = new HashSet<Long>(); ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        int level = origin.getCoordinate().getLevel(); int x = origin.getCoordinate().getX(), y = origin.getCoordinate().getY();
        seen.add(key(x,y)); queue.add(new int[]{x,y});
        while (!queue.isEmpty()) {
            int[] here = queue.remove();
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) {
                int nx=here[0]+d[0],ny=here[1]+d[1]; long key=key(nx,ny);
                if(nx<minX||nx>maxX||ny<minY||ny>maxY||seen.contains(key)||!standing(at(nx,ny,level)))continue;
                if(PathValidation.checkAdjacent(player,here[0],here[1],nx,ny)){seen.add(key);queue.add(new int[]{nx,ny});}
            }
        }
        return seen;
    }
    private String tile(WorldLocation location) {
        try { TileValue tile = server.getWorld().getTile(location); NativeLayeredTerrainTile raw=map.findTile(location).orElse(null);
            return "mask="+(tile.traversalMask&255)+" terrainBlocked="+tile.isTerrainBlocked()+" sceneryBlockers="+tile.getBlockingSceneryCount()+(raw==null?" no authored terrain":" overlay="+raw.getOverlay()+" walls="+raw.getVerticalWall()+","+raw.getHorizontalWall()+","+raw.getDiagonalWall()); }
        catch (RuntimeException missing) { return "runtime tile unavailable"; }
    }
    private static String upCommand(GameObject object) {
        if (object == null) return null;
        for (String command : new String[]{object.getGameObjectDef().getCommand1(),object.getGameObjectDef().getCommand2()})
            if (command.equalsIgnoreCase("climb-up")||command.equalsIgnoreCase("climb up")||command.equalsIgnoreCase("go up"))return command.toLowerCase(java.util.Locale.ROOT);
        return null;
    }
    private static String downCommand(GameObject object) {
        if(object==null)return null;
        for(String command:new String[]{object.getGameObjectDef().getCommand1(),object.getGameObjectDef().getCommand2()})
            if(command.equalsIgnoreCase("climb-down")||command.equalsIgnoreCase("climb down")||command.equalsIgnoreCase("go down"))return command.toLowerCase(java.util.Locale.ROOT);
        return null;
    }
    private void drain() {
        clock.millis += server.getConfig().GAME_TICK;
        server.getGameEventHandler().processNonPlayerEvents();
        // A real tick also retires completed player plugin events. Omitting this
        // can leave a prior Default.onOpLoc queued and reject the next test click.
        server.getGameEventHandler().runPlayerEvents(player);
        server.getGameEventHandler().cleanupEvents();
    }
    private static long key(int x,int y) { return ((long)x<<32)|(y&0xffffffffL); }
    private static WorldLocation offset(WorldLocation location,int dx,int dy) {
        WorldCoordinate c=location.getCoordinate(); return new WorldLocation(location.getWorldSpace(),new WorldCoordinate(c.getX()+dx,c.getY()+dy,c.getLevel()));
    }
    private static WorldLocation at(int x,int y,int level) { return WorldLocation.global(new WorldCoordinate(x,y,level)); }
    private static final class Clock implements GameClock {
        private long millis=1700000000000L;
        public long currentTimeMillis(){return millis;}
        public long nanoTime(){return millis*1000000L;}
    }
}
