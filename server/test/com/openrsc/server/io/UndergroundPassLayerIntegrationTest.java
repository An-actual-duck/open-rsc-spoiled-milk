package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.mechanism.UndergroundPassMechanismMap1;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap1;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.obstacles.UndergroundPassPuzzle;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.obstacles.UndergroundPassOrbs;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.obstacles.UndergroundPassDungeonFloor;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.CollisionFlag;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Real rope/puzzle callbacks and signed-layer scenery over a disposable map. */
public final class UndergroundPassLayerIntegrationTest {
    private static int checks;
    private static Runnable pauseCheck;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static WorldLocation at(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static GameObject find(Server server, int x, int y, int level) {
        return server.getWorld().getRegionManager().findNativeLayeredScenery(at(x, y, level));
    }

    private static void stand(Player player, int x, int y) {
        player.teleport(x, y, -1, false);
        player.setBusy(false);
    }

    private static List<GameTickEvent> restorations(Server server) {
        List<GameTickEvent> result = new ArrayList<GameTickEvent>();
        for (GameTickEvent event : server.getGameEventHandler().getEvents()) {
            if (event.getDescriptor().equals("Delayed Spawn Object") && !event.shouldRemove()) result.add(event);
        }
        return result;
    }

    private static void restore(Server server, int millis) {
        List<GameTickEvent> pending = restorations(server);
        check(pending.size() == 1, "exactly one scenery restoration");
        long ticks = pending.get(0).getDelayTicks();
        check(ticks * server.getConfig().GAME_TICK >= millis
            && ticks * server.getConfig().GAME_TICK < millis + server.getConfig().GAME_TICK,
            "authentic restoration delay rounded to the next game tick");
        for (long i = 0; i < ticks; i++) {
            server.getGameEventHandler().processNonPlayerEvents();
            server.getGameEventHandler().cleanupEvents();
        }
        check(restorations(server).isEmpty(), "restoration event completes");
    }

    private static void ropes(Server server, Player player) {
        UndergroundPassMechanismMap1 ropes = new UndergroundPassMechanismMap1();
        UndergroundPassObstaclesMap1 ordinary = new UndergroundPassObstaclesMap1();
        Item rope = new Item(ItemId.ROPE.id());
        player.getCarriedItems().getInventory().add(rope);
        int[][] routes = {{681,603,677,603}, {679,603,682,604}, {697,603,695,603}};
        for (int[] route : routes) {
            stand(player, route[0], route[1] + 1);
            GameObject stalactite = find(server, route[0], route[1], -1);
            check(!ordinary.blockOpLoc(player, stalactite, "WalkTo"), "stalactite is not a normal climb-rock action");
            check(ropes.blockUseLoc(player, stalactite, rope), "rope owns the stalactite crossing");
            ropes.onUseLoc(player, stalactite, rope);
            check(player.getWorldLocation().equals(at(route[2], route[3], -1)),
                "rope crosses at native signed level for stalactite " + stalactite.getID());
        }
        check(player.getCarriedItems().hasCatalogID(ItemId.ROPE.id()), "rope remains reusable as before");
        System.out.println("PASS Underground Pass rope crossings, including reported stalactite (681,603,-1)");
    }

    private static void puzzle(Server server, Player player) {
        UndergroundPassPuzzle plugin = new UndergroundPassPuzzle();
        GameObject surface = find(server, 690, 617, 0);
        GameObject north = find(server, 689, 612, -1);
        GameObject south = find(server, 689, 621, -1);
        stand(player, 689, 613);
        check(plugin.blockOpLoc(player, north, "pull"), "north cage lever owns action");
        check(plugin.blockOpLoc(player, south, "pull"), "south cage lever owns action");
        plugin.onOpLoc(player, north, "pull");
        GameObject raised = find(server, 690, 617, -1);
        check(raised.getID() == 803, "native underground cage raises");
        check(raised.getWorldLocation().equals(at(690, 617, -1)), "raised cage keeps signed level");
        check(raised.getLoc().getNativeLayeredGameObjectIdentity() != null, "cage keeps authored native ownership");
        check(player.getWorldLocation().equals(at(690, 619, -1)), "lever crossing preserves original destination");
        check(player.getViewArea().getGameObjectsInView().contains(raised), "raised cage is visible underground");
        check(!player.getViewArea().getGameObjectsInView().contains(surface), "surface cage is excluded from view");
        check(find(server, 690, 617, 0) == surface, "same-XY surface cage stays untouched");
        stand(player, 689, 620);
        plugin.onOpLoc(player, south, "pull");
        check(find(server, 690, 617, -1) == raised, "second lever reuses raised cage");
        check(restorations(server).size() == 1, "repeated pull does not duplicate reset timers");
        restore(server, 5000);
        check(find(server, 690, 617, -1).getID() == 802, "five-second reset lowers native cage");
        check(find(server, 690, 617, 0) == surface, "reset cannot affect surface cage");
        stand(player, 689, 620);
        plugin.onOpLoc(player, south, "pull");
        check(find(server, 690, 617, -1).getID() == 803, "south lever raises a closed cage too");
        check(player.getWorldLocation().equals(at(690, 619, -1)), "south lever crossing remains underground");
        restore(server, 5000);

        GameObject closed = find(server, 690, 617, -1);
        server.getWorld().replaceGameObject(closed,
            new GameObject(server.getWorld(), closed.getLocation(), 97, closed.getDirection(), closed.getType()));
        stand(player, 689, 613);
        plugin.onOpLoc(player, north, "pull");
        check(find(server, 690, 617, -1).getID() == 97, "unrelated scenery is not overwritten");
        check(player.getWorldLocation().equals(at(689, 613, -1)), "unrelated scenery refuses crossing");
        check(restorations(server).isEmpty(), "unrelated scenery schedules no reset");

        server.getWorld().unregisterGameObject(find(server, 690, 617, -1));
        stand(player, 689, 613);
        plugin.onOpLoc(player, north, "pull");
        check(player.getWorldLocation().equals(at(689, 613, -1)), "missing cage refuses crossing");
        check(find(server, 690, 617, -1) == null, "missing cage is not manufactured");
        check(restorations(server).isEmpty(), "missing cage schedules no reset");
        check(find(server, 690, 617, 0) == surface, "missing underground cage cannot select surface cage");
    }

    private static void planks(Server server, Player player) {
        UndergroundPassOrbs plugin = new UndergroundPassOrbs();
        Item plank = new Item(ItemId.PLANK.id());
        player.getCarriedItems().getInventory().add(new Item(ItemId.PLANK.id(), 2));
        stand(player, 728, 609);
        GameObject passage = find(server, 728, 608, -1);
        check(plugin.blockUseLoc(player, passage, plank), "plank owns north-passage pressure triggers");
        plugin.onUseLoc(player, passage, plank);
        check(player.getWorldLocation().equals(at(728, 603, -1)), "plank crosses north at signed level -1");
        GameObject visual = find(server, 728, 603, -1);
        check(visual != null && visual.getID() == 827, "temporary planks are native underground scenery");
        check(player.getViewArea().getGameObjectsInView().contains(visual), "temporary planks are visible underground");
        check(find(server, 728, 603, 0) == null, "planks do not appear on the surface");
        stand(player, 728, 603);
        plugin.onUseLoc(player, find(server, 728, 604, -1), plank);
        check(player.getWorldLocation().equals(at(728, 609, -1)), "plank crosses south at signed level -1");
        check(find(server, 728, 603, -1) == visual, "second crossing reuses active temporary planks");
        List<GameTickEvent> removals = new ArrayList<GameTickEvent>();
        for (GameTickEvent event : server.getGameEventHandler().getEvents()) {
            if (event.getDescriptor().equals("Delayed Remove Object") && !event.shouldRemove()) removals.add(event);
        }
        check(removals.size() == 1, "temporary planks schedule one removal");
        for (long tick = 0; tick < removals.get(0).getDelayTicks(); tick++) {
            server.getGameEventHandler().processNonPlayerEvents();
            server.getGameEventHandler().cleanupEvents();
        }
        check(find(server, 728, 603, -1) == null, "temporary planks disappear on schedule");
    }

    private static void tomb(final Server server, final Player player) {
        player.teleport(724, 653, 0, false);
        player.setBusy(false);
        player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 99, 99, false);
        UndergroundPassDungeonFloor plugin = new UndergroundPassDungeonFloor();
        GameObject tomb = find(server, 724, 654, 0);
        check(plugin.blockOpLoc(player, tomb, "open"), "actual surface Iban tomb owns action");
        pauseCheck = new Runnable() {
            public void run() {
                GameObject claws = player.getViewArea().getGameObject(879, 724, 653);
                check(claws != null && claws.getWorldLocation().equals(at(724, 653, 0)),
                    "tomb claws stay on the actual surface floor");
                check(player.getViewArea().getGameObjectsInView().contains(claws), "tomb claws are visible during attack");
                check(find(server, 724, 653, -1) == null, "tomb claws cannot leak underground");
            }
        };
        try {
            plugin.onOpLoc(player, tomb, "open");
        } finally {
            pauseCheck = null;
        }
        check(player.getSkills().getLevel(Skill.HITS.id()) == 75, "tomb keeps its original damage");
        check(player.getViewArea().getGameObject(879, 724, 653) == null, "tomb claws are removed after attack");
        check(find(server, 724, 654, 0) == tomb, "tomb itself is unchanged");
    }

    private static void exit(Server server, Player player) {
        UndergroundPassObstaclesMap1 plugin = new UndergroundPassObstaclesMap1();
        GameObject entrance = find(server, 715, 580, 0);
        GameObject rock = find(server, 672, 588, -1);
        player.teleport(713, 581, 0, false);
        player.setQuestStage(Quests.UNDERGROUND_PASS, 0);
        player.setQuestStage(Quests.BIOHAZARD, 0);
        plugin.onOpLoc(player, entrance, "enter");
        check(player.getWorldLocation().equals(at(713, 581, 0)), "entrance refuses unfinished Biohazard");
        player.setQuestStage(Quests.BIOHAZARD, -1);
        plugin.onOpLoc(player, entrance, "enter");
        check(player.getWorldLocation().equals(at(713, 581, 0)), "entrance still requires starting Underground Pass");
        for (int stage : new int[]{2, -1}) {
            player.setQuestStage(Quests.UNDERGROUND_PASS, stage);
            player.setBusy(false);
            plugin.onOpLoc(player, entrance, "enter");
            check(player.getWorldLocation().equals(at(673, 588, -1)), "allowed entry reaches underground at quest stage " + stage);
            player.setBusy(false);
            plugin.onOpLoc(player, rock, "climb");
            check(player.getWorldLocation().equals(at(713, 581, 0)), "crumbled rock exits to the intended surface landing");
        }
        check(server.getWorld().getRegionManager().hasNativeLayeredTerrain(at(713, 581, 0)), "surface landing has authored terrain");
        int mask = server.getWorld().getTile(at(713, 581, 0)).traversalMask;
        check((mask & (CollisionFlag.FULL_BLOCK | CollisionFlag.OBJECT)) == 0, "surface landing is walkable after scenery collision");
        int exits = 0;
        for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            if (PathValidation.checkAdjacent(player, 713, 581, 713 + offset[0], 581 + offset[1])) exits++;
        }
        check(exits > 0, "surface landing has a legal adjacent walking exit");
        System.out.println("PASS crumbled-rock exit and quest entry; landing (713,581,0), traversal mask " + mask + ", walking exits " + exits);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Server server = new Server("myworld.conf");
        Field contextField = PluginTask.class.getDeclaredField("tasksMap");
        contextField.setAccessible(true);
        Map<String, PluginTask> contexts = (Map<String, PluginTask>) contextField.get(null);
        try {
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
            server.getEntityHandler().load();
            server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
            server.getWorld().getRegionManager().populateNativeLayeredPlacements();
            final Player player = new Player(server.getWorld(), DataConversions.usernameToHash("layercheck"));
            player.setClientVersion(server.getConfig().CLIENT_VERSION);
            player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
            player.getClientLimitations().maxSceneryId = Integer.MAX_VALUE;
            player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
            player.setInitialLayeredLocation(at(681, 604, -1));
            server.getWorld().getPlayers().add(player);
            player.updateRegion();
            player.setLoggedIn(true);
            player.setBusy(false);
            player.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 99, 99, false);
            contexts.put(Thread.currentThread().getName(), new PluginTask(server.getWorld(), player, "OpLoc", new Object[0]) {
                public int action() { return 0; }
                public synchronized void pause(int ticks) {
                    if (ticks == 2 && pauseCheck != null) pauseCheck.run();
                }
            });
            String scenario = args.length == 0 ? "all" : args[0];
            if ("planks".equals(scenario)) {
                planks(server, player);
            } else if ("tomb".equals(scenario)) {
                tomb(server, player);
            } else if ("exit".equals(scenario)) {
                exit(server, player);
            } else {
                ropes(server, player);
                puzzle(server, player);
                planks(server, player);
                tomb(server, player);
                exit(server, player);
            }
            System.out.println("PASS Underground Pass layer audit: " + checks
                + " assertions; actual rope and both cage-lever plugins, native replacement, crossing, timed reset and floor isolation; no listeners or player database");
        } finally {
            contexts.remove(Thread.currentThread().getName());
            for (GameTickEvent event : new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents())) event.stop();
            server.getGameEventHandler().cleanupEvents();
            server.getWorld().getPlayers().clear();
            server.getEntityHandler().unload();
            for (String name : new String[]{"sqlLoggingThreadPool", "sqlThreadPool", "onlineMonitorThreadPool"}) {
                Field field = Server.class.getDeclaredField(name); field.setAccessible(true);
                ((ExecutorService) field.get(server)).shutdownNow();
            }
            Server.serversList.remove(server.getName(), server);
        }
    }
}
