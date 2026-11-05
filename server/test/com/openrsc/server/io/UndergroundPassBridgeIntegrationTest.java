package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTask;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.mechanism.UndergroundPassMechanismMap1;
import com.openrsc.server.plugins.authentic.quests.members.undergroundpass.obstacles.UndergroundPassObstaclesMap1;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Actual plugins, native object ownership, player travel and scheduled restoration. */
public final class UndergroundPassBridgeIntegrationTest {

    private static int checks;

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

    private static List<GameTickEvent> restorations(Server server) {
        List<GameTickEvent> result = new ArrayList<GameTickEvent>();
        for (GameTickEvent event : server.getGameEventHandler().getEvents()) {
            if (event.getDescriptor().equals("Delayed Spawn Object") && !event.shouldRemove()) result.add(event);
        }
        return result;
    }

    private static void restore(Server server) {
        List<GameTickEvent> pending = restorations(server);
        check(pending.size() == 1, "exactly one bridge restoration");
        GameTickEvent event = pending.get(0);
        long ticks = event.getDelayTicks();
        long millis = ticks * server.getConfig().GAME_TICK;
        check(millis >= 10000 && millis < 10000 + server.getConfig().GAME_TICK,
            "ten-second restoration rounded to the next game tick");
        for (long i = 0; i < ticks; i++) event.doRun();
        server.getGameEventHandler().cleanupEvents();
        check(find(server, 704, 585, -1).getID() == 726, "bridge closes on the underground floor");
        check(restorations(server).isEmpty(), "restoration event completes");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PluginTask> contexts() throws Exception {
        Field field = PluginTask.class.getDeclaredField("tasksMap");
        field.setAccessible(true);
        return (Map<String, PluginTask>) field.get(null);
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server("myworld.conf");
        Map<String, PluginTask> contexts = contexts();
        try {
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
            server.getEntityHandler().load();
            server.getWorld().getRegionManager().preflightNativeLayeredPlacements();
            server.getWorld().getRegionManager().populateNativeLayeredPlacements();
            final Player player = new Player(server.getWorld(), DataConversions.usernameToHash("bridgecheck"));
            player.setClientVersion(server.getConfig().CLIENT_VERSION);
            player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
            player.getClientLimitations().maxSceneryId = Integer.MAX_VALUE;
            player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
            player.setInitialLayeredLocation(at(711, 587, -1));
            server.getWorld().getPlayers().add(player);
            player.updateRegion();
            player.setLoggedIn(true);
            player.setBusy(false);
            contexts.put(Thread.currentThread().getName(), new PluginTask(server.getWorld(), player, "OpLoc", new Object[0]) {
                public int action() { return 0; }
                public synchronized void pause(int ticks) { }
            });
            GameObject surface = find(server, 704, 585, 0);
            GameObject lever = find(server, 711, 586, -1);
            UndergroundPassObstaclesMap1 plugin = new UndergroundPassObstaclesMap1();
            check(plugin.blockOpLoc(player, lever, "pull"), "real lever trigger owns action");
            plugin.onOpLoc(player, lever, "pull");
            GameObject lowered = find(server, 704, 585, -1);
            check(lowered.getID() == 727, "visible native bridge becomes lowered");
            check(lowered.getWorldLocation().equals(at(704, 585, -1)), "replacement preserves signed level");
            check(lowered.getLoc().getNativeLayeredGameObjectIdentity() != null, "replacement keeps native ownership");
            check(player.getWorldLocation().equals(at(703, 588, -1)), "lever crosses west without packed Y");
            check(find(server, 704, 585, 0) == surface, "same-XY surface bridge stays untouched");
            player.teleport(711, 587, -1, false);
            player.setBusy(false);
            plugin.onOpLoc(player, lever, "pull");
            check(find(server, 704, 585, -1) == lowered, "already lowered bridge is reused");
            check(restorations(server).size() == 1, "repeated pull does not duplicate close timers");
            restore(server);
            check(find(server, 704, 585, 0) == surface, "restoration cannot affect surface bridge");

            // The lit-arrow path shares this bridge and must use the same native crossing row.
            player.teleport(700, 585, -1, false);
            player.setBusy(false);
            player.getSkills().setTemporaryLevelAndMaxStat(Skill.RANGED.id(), 99, 99, false);
            player.getCarriedItems().getInventory().add(new Item(ItemId.SHORTBOW.id()));
            player.getCarriedItems().getInventory().add(new Item(ItemId.LIT_ARROW.id()));
            DataConversions.getRandom().setSeed(0); // Isolated process: nextInt(5) is 0, a successful shot.
            UndergroundPassMechanismMap1 arrows = new UndergroundPassMechanismMap1();
            GameObject closed = find(server, 704, 585, -1);
            Item arrow = new Item(ItemId.LIT_ARROW.id());
            check(arrows.blockUseLoc(player, closed, arrow), "lit-arrow action owns the bridge");
            arrows.onUseLoc(player, closed, arrow);
            check(find(server, 704, 585, -1).getID() == 727, "correct native firing row lowers bridge");
            check(player.getWorldLocation().equals(at(709, 588, -1)), "arrow crosses east without packed Y");
            restore(server);
            check(find(server, 704, 585, 0) == surface, "arrow restoration is level-isolated");

            server.getWorld().unregisterGameObject(find(server, 704, 585, -1));
            player.teleport(711, 587, -1, false);
            player.setBusy(false);
            plugin.onOpLoc(player, lever, "pull");
            check(player.getWorldLocation().equals(at(711, 587, -1)), "missing bridge fails without moving player");
            check(restorations(server).isEmpty(), "missing bridge does not invent a replacement");
            check(find(server, 704, 585, 0) == surface, "missing underground bridge cannot select surface bridge");
            System.out.println("PASS Underground Pass bridge: " + checks + " assertions; actual lever/arrow plugins, native replacement, crossing, timed restoration, repeated pull and floor isolation; no listeners or player database");
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
