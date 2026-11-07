package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.ConfiguredAgilityShortcutHandler;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.triggers.OpBoundTrigger;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual obstacle callbacks and Player transitions; no listeners, account load or database setup. */
public final class NonzeroAgilityLayerIntegrationTest {
    private static int checks;

    private static final class EdgeRandom extends Random {
        boolean high;
        @Override public int nextInt(int bound) { return high ? bound - 1 : 0; }
    }

    private static final List<WorldLocation> visits = new ArrayList<WorldLocation>();
    private static JSONArray poses;
    private static WorldLocation lastObserved;
    private static void observe(Player player) {
        WorldLocation location = player.getWorldLocation();
        if (!location.equals(lastObserved)) visits.add(location);
        lastObserved = location;
        if (poses != null) poses.put(new JSONObject().put("location", triple(location)).put("sprite", player.getSprite()));
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static WorldLocation at(JSONArray triple) {
        return WorldLocation.global(new WorldCoordinate(triple.getInt(0), triple.getInt(1), triple.getInt(2)));
    }

    private static JSONArray triple(WorldLocation location) {
        WorldCoordinate c = location.getCoordinate();
        return new JSONArray(Arrays.asList(c.getX(), c.getY(), c.getLevel()));
    }

    private static void setup(Player player, JSONObject scenario) throws Exception {
        player.getCache().getCacheMap().clear();
        player.setQuestStage(Quests.SHILO_VILLAGE, 0);
        player.setQuestStage(Quests.LEGENDS_QUEST, 0);
        player.setQuestStage(Quests.DIGSITE, 0);
        player.setQuestStage(Quests.UNDERGROUND_PASS, 0);
        for (Object value : scenario.getJSONArray("setup")) {
            String flag = (String) value;
            if (flag.equals("beads")) {
                Field field = Equipment.class.getDeclaredField("list"); field.setAccessible(true);
                Item beads = new Item(ItemId.BEADS_OF_THE_DEAD.id(), 1, false, 9000000L);
                beads.setWielded(true);
                ((Item[]) field.get(player.getCarriedItems().getEquipment()))[
                    beads.getDef(player.getWorld()).getWieldPosition()] = beads;
            } else if (flag.equals("shilo-fissure")) {
                player.setQuestStage(Quests.SHILO_VILLAGE, 3);
                player.getCache().store("SV_DIG_BUMP", true);
            } else if (flag.equals("rope")) {
                player.getCache().store("SV_DIG_ROPE", true);
            } else if (flag.equals("shilo-tomb")) {
                player.setQuestStage(Quests.SHILO_VILLAGE, 6);
            } else if (flag.equals("legends")) {
                player.setQuestStage(Quests.LEGENDS_QUEST, 2);
            } else if (flag.startsWith("digsite")) {
                player.setQuestStage(Quests.DIGSITE, flag.equals("digsite-late") ? 6 : 3);
                player.getCache().store("digsite_winshaft", true);
                player.getCache().store("winch_rope_1", true);
                player.getCache().store("winch_rope_2", true);
            } else if (flag.equals("underground-pass")) {
                player.setQuestStage(Quests.UNDERGROUND_PASS, 5);
            } else if (flag.equals("up-old-stage")) {
                player.setQuestStage(Quests.UNDERGROUND_PASS, 3);
            } else throw new AssertionError("Unknown fixture setup: " + flag);
        }
    }

    private static void scenario(Server server, Player player, EdgeRandom random,
                                 JSONObject scenario, JSONObject result) throws Exception {
        setup(player, scenario);
        player.teleportLayered(at(scenario.getJSONArray("start")), false);
        player.updateRegion(); player.setBusy(false); player.setFatigue(0); player.resetMenuHandler();
        player.getSkills().setTemporaryLevelAndMaxStat(Skill.AGILITY.id(), scenario.getInt("agility"), 99, false);
        player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 99, 99, false);
        random.high = scenario.getString("edge").equals("high");
        WorldLocation objectLocation = at(scenario.getJSONArray("objectLocation"));
        GameObject object = scenario.getString("kind").equals("boundaries")
            ? server.getWorld().getRegionManager().findInteractionBoundary(
                Point.location(objectLocation.getCoordinate().getX(), objectLocation.getCoordinate().getY()), player)
            : server.getWorld().getRegionManager().findNativeLayeredScenery(objectLocation);
        check(object != null && object.getID() == scenario.getInt("objectId"), "actual installed obstacle identity");
        check(object.getWorldLocation().equals(objectLocation), "obstacle belongs to the expected signed floor");
        visits.clear(); poses = new JSONArray(); lastObserved = player.getWorldLocation();
        String command = scenario.isNull("command") ? (object.getType() == 1 ? "jump" : object.getGameObjectDef().getCommand1())
            : scenario.getString("command");
        if (scenario.getString("plugin").equals("configured")) {
            NativeLayeredSceneryPlacement placement = null;
            for (NativeLayeredWorldPackage owner : Arrays.asList(
                    server.getWorld().getRegionManager().findNativeLayeredWorldPackage(objectLocation).get())) {
                for (NativeLayeredPlacementSet set : owner.getPlacementSets().values()) {
                    for (NativeLayeredSceneryPlacement entry : set.getScenery()) {
                        if (entry.getPlacementId().equals(scenario.getString("placementId"))) placement = entry;
                    }
                }
            }
            check(placement != null && placement.getAgilityShortcut() != null, "configured placement retains metadata");
            // Runtime checks both endpoints before activation; 100% authored chance makes failure unreachable.
            check(placement.getAgilityShortcut().chanceBasisPoints(99) == 10000, "installed shortcut chance is 100 percent");
            check(ConfiguredAgilityShortcutHandler.handle(player, object, command), "configured action owns the click");
            check(player.isBusy(), "configured action passes actual endpoint safety checks");
            for (int tick = 0; tick < placement.getAgilityShortcut().getTemplate().ticks; tick++) {
                server.getGameEventHandler().processEvents(player);
                server.getGameEventHandler().cleanupEvents();
            }
            check(!player.isBusy(), "configured action finishes");
        } else {
            Object plugin = Class.forName("com.openrsc.server.plugins." + scenario.getString("plugin")).newInstance();
            if (object.getType() == 1) {
                check(((OpBoundTrigger) plugin).blockOpBound(player, object, 1), "boundary plugin owns the obstacle");
                ((OpBoundTrigger) plugin).onOpBound(player, object, 1);
            } else {
                check(((OpLocTrigger) plugin).blockOpLoc(player, object, command), "scenery plugin owns the obstacle");
                ((OpLocTrigger) plugin).onOpLoc(player, object, command);
            }
        }
        observe(player);
        WorldLocation destination = at(scenario.getJSONArray("destination"));
        result.put("actualDestination", triple(player.getWorldLocation()));
        JSONArray trace = new JSONArray(); for (WorldLocation visit : visits) trace.put(triple(visit));
        result.put("actualTrace", trace).put("poses", poses);
        result.put("damage", 99 - player.getSkills().getLevel(Skill.HITS.id()));
        check(player.getWorldLocation().equals(destination), "landing expected " + destination + ", actual " + player.getWorldLocation());
        if (!scenario.isNull("trace")) {
            JSONArray expected = new JSONArray();
            WorldLocation previous = at(scenario.getJSONArray("start"));
            for (Object value : scenario.getJSONArray("trace")) {
                WorldLocation next = at((JSONArray) value);
                if (!next.equals(previous)) expected.put(triple(next));
                previous = next;
            }
            check(trace.toString().equals(expected.toString()),
                "observed intermediate landings differ: expected " + expected + ", actual " + trace);
        }
        if (scenario.has("facings")) {
            for (Object value : scenario.getJSONArray("facings")) {
                JSONObject expected = (JSONObject) value; boolean found = false;
                for (Object pose : poses) if (((JSONObject) pose).toString().equals(expected.toString())) found = true;
                check(found, "expected facing pose " + expected + " absent from " + poses);
            }
        }
        if (!scenario.isNull("damage")) check(result.getInt("damage") == scenario.getInt("damage"),
            "authentic damage changed: expected " + scenario.getInt("damage") + ", actual " + result.getInt("damage"));
        check(server.getWorld().getRegionManager().hasNativeLayeredTerrain(destination), "authored terrain at final landing");
        int mask = server.getWorld().getRegionManager().getTile(destination).traversalMask;
        result.put("collisionMask", mask);
        NativeLayeredTerrainTile floor = server.getWorld().getRegionManager()
            .findNativeLayeredWorldPackage(destination).get().findTile(destination).get();
        result.put("overlay", floor.getOverlay());
        check(floor.getOverlay() != 250 && floor.getOverlay() != 255, "final landing is not authored void");
        if (destination.getCoordinate().getLevel() == 1 || destination.getCoordinate().getLevel() == 2)
            check(floor.getOverlay() != 0, "upper-floor landing has actual floor");
        int x = player.getX(), y = player.getY(), exits = 0;
        for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
            if (PathValidation.checkAdjacent(player, x, y, x + offset[0], y + offset[1])) exits++;
        result.put("walkingExits", exits);
        check(exits > 0, "final landing has no legal outward walking step; mask=" + mask);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Server server = new Server("myworld.conf");
        Field contextField = PluginTask.class.getDeclaredField("tasksMap"); contextField.setAccessible(true);
        Map<String, PluginTask> contexts = (Map<String, PluginTask>) contextField.get(null);
        Field randomField = DataConversions.class.getDeclaredField("rand"); randomField.setAccessible(true);
        Object originalRandom = randomField.get(null); EdgeRandom random = new EdgeRandom();
        JSONArray results = new JSONArray(); List<String> failures = new ArrayList<String>();
        try {
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
            server.getEntityHandler().load();
            server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
            server.getWorld().getRegionManager().populateNativeLayeredPlacements();
            final Player player = new Player(server.getWorld(), DataConversions.usernameToHash("agilityaudit"));
            player.setClientVersion(server.getConfig().CLIENT_VERSION);
            player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
            player.getClientLimitations().maxSceneryId = Integer.MAX_VALUE;
            player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
            player.setInitialLayeredLocation(WorldLocation.global(new WorldCoordinate(692, 494, 0)));
            server.getWorld().getPlayers().add(player); player.updateRegion(); player.setLoggedIn(true);
            contexts.put(Thread.currentThread().getName(), new PluginTask(server.getWorld(), player, "OpLoc", new Object[0]) {
                public int action() { return 0; }
                public synchronized void pause(int ticks) { observe(player); if (player.getMenuHandler() != null) player.setOption(0); }
            });
            randomField.set(null, random);
            JSONArray scenarios = new JSONArray(new String(Files.readAllBytes(Paths.get("scenarios.json")), StandardCharsets.UTF_8));
            for (Object value : scenarios) {
                JSONObject scenario = (JSONObject) value;
                if (args.length > 0 && !scenario.getString("key").contains(args[0])) continue;
                JSONObject result = new JSONObject().put("scenario", scenario);
                try { scenario(server, player, random, scenario, result); result.put("passed", true); }
                catch (AssertionError | RuntimeException failure) {
                    result.put("passed", false).put("failure", failure.toString());
                    failures.add(scenario.getString("key") + ": " + failure.toString());
                    System.out.println("FAIL " + failures.get(failures.size() - 1));
                } finally {
                    for (GameTickEvent event : new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents())) event.stop();
                    server.getGameEventHandler().cleanupEvents();
                }
                results.put(result);
            }
            Files.write(Paths.get("results.json"), (results.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Nonzero agility scenarios=" + results.length() + " checks=" + checks + " failures=" + failures.size());
            if (!failures.isEmpty()) throw new AssertionError(failures.toString());
        } finally {
            randomField.set(null, originalRandom);
            contexts.remove(Thread.currentThread().getName());
            for (GameTickEvent event : new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents())) event.stop();
            server.getGameEventHandler().cleanupEvents(); server.getWorld().getPlayers().clear(); server.getEntityHandler().unload();
            for (String name : new String[]{"sqlLoggingThreadPool", "sqlThreadPool", "onlineMonitorThreadPool"}) {
                Field field = Server.class.getDeclaredField(name); field.setAccessible(true);
                ((ExecutorService) field.get(server)).shutdownNow();
            }
            Server.serversList.remove(server.getName(), server);
        }
    }
}
