import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.container.Bank;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.util.rsc.DataConversions;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;

/** Disposable loopback world. Never use an owner database or a public endpoint. */
public final class RendererBenchmarkServer {
    private static final List<Player> actors = new ArrayList<Player>();
    private static final List<Npc> npcs = new ArrayList<Npc>();
    private static final List<Point> positions = new ArrayList<Point>();
    private static Player viewer;
    private static Server worldServer;
    private static boolean busy;
    private static boolean configured;
    private static int tick;
    private static int removedMapNpcs;
    private static int samePositionTeleports;
    private static long serial;
    private static String phase = "dense-idle";
    private static int viewerX = 502, viewerY = 453;

    private static boolean clear(int x, int y) {
        com.openrsc.server.model.world.region.TileValue tile = worldServer.getWorld().getTile(
            com.openrsc.server.model.world.coordinate.WorldLocation.global(
                new com.openrsc.server.model.world.coordinate.WorldCoordinate(x, y, 0)));
        return (tile.traversalMask & 255) == 0 && (tile.overlay & 255) != 10;
    }

    private static void removeActors() {
        for (Player actor : actors) {
            Summoning.dismissAll(actor); actor.setLoggedIn(false); actor.remove();
            worldServer.getWorld().getPlayers().remove(actor); worldServer.getWorld().removePlayer(actor.getUsernameHash());
        }
        for (Npc npc : npcs) worldServer.getWorld().unregisterNpc(npc);
        actors.clear(); npcs.clear(); positions.clear();
    }

    private static void equip(Player player, int id) throws Exception {
        java.lang.reflect.Field slots = Equipment.class.getDeclaredField("list"); slots.setAccessible(true);
        com.openrsc.server.external.ItemDefinition definition = worldServer.getEntityHandler().getItemDef(id);
        Item item = new Item(id, 1, false, 90_000_000L + ++serial); item.setWielded(true);
        ((Item[]) slots.get(player.getCarriedItems().getEquipment()))[definition.getWieldPosition()] = item;
        player.updateWornItems(definition.getWieldPosition(), definition.getAppearanceId(), definition.getWearableId(), true);
    }

    private static void createActors() throws Exception {
        List<Point> tiles = new ArrayList<Point>();
        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) == radius && clear(502 + dx, 453 + dy)) tiles.add(Point.location(502 + dx, 453 + dy));
            }
        }
        if (tiles.size() < 48) throw new IllegalStateException("Insufficient authored walkable crowd tiles");
        for (int index = 0; index < 16; index++) {
            Point point = tiles.get(index); Player actor = new Player(worldServer.getWorld(), DataConversions.usernameToHash("Render " + index));
            actor.setAttribute("dummyplayer", true); actor.setAttribute("renderer_benchmark_actor", true); actor.setBank(new Bank(actor));
            actor.setClientVersion(worldServer.getConfig().CLIENT_VERSION); actor.setClientLimitations(ClientLimitations.forVersion(worldServer.getConfig().CLIENT_VERSION));
            actor.getClientLimitations().maxItemId = 65535;
            actor.getSettings().setAppearance(new PlayerAppearance(index % 8, (index * 3) % 8, 0, 0, 1, 2));
            actor.setInitialLocation(point); actor.setWornItems(actor.getSettings().getAppearance().getSprites());
            worldServer.getWorld().getPlayers().add(actor); actor.updateRegion(); actor.setLoggedIn(true); actor.setBusy(false);
            for (int skill = 0; skill < worldServer.getConstants().getSkills().getSkillsCount(); skill++) actor.getSkills().setTemporaryLevelAndMaxStat(skill, 99, 99, false);
            actor.getCache().store("setting_auto_retaliate", false);
            for (int id : new int[]{ItemId.RUNE_PLATE_MAIL_BODY.id(), ItemId.RUNE_PLATE_MAIL_LEGS.id(), ItemId.LARGE_RUNE_HELMET.id(), ItemId.RUNE_LONG_SWORD.id(), ItemId.RUNE_SQUARE_SHIELD.id()}) equip(actor, id);
            actors.add(actor); positions.add(point);
        }
        for (int index = 0; index < 32; index++) {
            Point point = tiles.get(16 + index); Npc npc = new Npc(worldServer.getWorld(), 3, point.getX(), point.getY(), 0);
            npc.setAttribute("renderer_benchmark_actor", true); worldServer.getWorld().registerNpc(npc); npcs.add(npc);
        }
        for (int index = 0; index < 8; index++) Summoning.summonTestBear(actors.get(index));
    }

    private static void driveViewer() {
        if (viewer == null || !phase.equals("busy-walk") || tick % 16 != 0) return;
        // A short authored, obstruction-free corridor, traversed through normal queues.
        int[][] axes = {{1,0},{0,1},{-1,0},{0,-1}};
        for (int[] axis : axes) {
            boolean corridor = true;
            for (int step=0;step<=4;step++) corridor &= clear(502+axis[0]*step,453+axis[1]*step);
            if (corridor) {
                int step = (tick / 16) % 2 == 0 ? 0 : 4;
                viewer.walk(502+axis[0]*step,453+axis[1]*step); return;
            }
        }
        throw new IllegalStateException("No authored4-tile viewer walk corridor");
    }
    private static void driveActors() {
        if (!busy || actors.isEmpty()) return;
        for (int index = 0; index < actors.size(); index++) {
            Player actor = actors.get(index); Point origin = positions.get(index);
            if (tick % 4 == 0) {
                int x = origin.getX() + (tick % 8 == 0 ? 1 : 0);
                if (clear(x, origin.getY())) actor.walk(x, origin.getY());
            }
            if (index < 8 && tick % 2 == 0) {
                int[] types = {Projectile.THUNDER_BALL, Projectile.ICICLE_SHOT, Projectile.WOOD_LEAD_2, Projectile.ELECTRIC_DISC};
                Npc target = npcs.get(index * 3);
                target.getUpdateFlags().setProjectile(new Projectile(actor, target, types[index % types.length]));
            }
        }
    }
    public static void main(String[] args) throws Exception {
        final Path root = Paths.get(".").toRealPath();
        final Path run = root.getParent();
        if (!Files.isRegularFile(run.resolve("RENDERER-BENCHMARK.json"))) {
            throw new IllegalStateException("Marked renderer fixture required");
        }
        com.openrsc.server.config.ServerConfigurationLoadResult loaded = ServerConfigurationLoader.load("myworld.conf");
        com.openrsc.server.ServerConfiguration config = loaded.getLegacyConfiguration();
        if (!"127.0.0.1".equals(config.SERVER_BIND_ADDRESS)
                || loaded.getProcessNetwork().getServerPort() != 43639
                || loaded.getProcessNetwork().getWebsocketPort() != 43539
                || config.DB_TYPE != DatabaseType.SQLITE || !"renderer_benchmark_dev".equals(config.DB_NAME)) {
            throw new IllegalStateException("Renderer-only loopback bindings/database required");
        }
        if (config.WANT_DISCORD_BOT || config.WANT_DISCORD_GENERAL_LOGGING || config.WANT_DISCORD_AUCTION_UPDATES
                || config.WANT_DISCORD_ITEM_SHOWCASE || config.WANT_DISCORD_MONITORING_UPDATES) {
            throw new IllegalStateException("External services must be disabled");
        }
        Files.write(run.resolve("server.pid"), java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes("UTF-8"));
        final Server server = Server.startServer("myworld.conf");
        worldServer = server;
        if (!server.getDatabase().playerExists("renderdemo")) {
            int id = server.getDatabase().createPlayer("renderdemo", "", DataConversions.hashPassword("RendererProof13", null), System.currentTimeMillis() / 1000, "127.0.0.1");
            if (id < 0) throw new IllegalStateException("Disposable account creation failed");
        }
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("inc/sqlite/renderer_benchmark_dev.db"));
             java.sql.PreparedStatement st = c.prepareStatement("UPDATE players SET login_date=1,x=502,y=453 WHERE username=?")) {
            st.setString(1, "renderdemo"); st.executeUpdate();
        }
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(), null, 1, "Renderer benchmark status", DuplicationStrategy.ONE_PER_SERVER) {
            public void run() {
                try {
                    Player player = server.getWorld().getPlayer(DataConversions.usernameToHash("renderdemo"));
                    if (!configured) {
                        List<Npc> ambient = new ArrayList<Npc>(server.getWorld().getNpcs());
                        removedMapNpcs = ambient.size();
                        for (Npc npc : ambient) server.getWorld().unregisterNpc(npc);
                        configured = true;
                    }
                    Path request = run.resolve("request.json");
                    if (Files.isRegularFile(request)) {
                        JSONObject command = new JSONObject(new String(Files.readAllBytes(request), "UTF-8"));
                        Files.delete(request); removeActors();
                        busy = command.optBoolean("busy", false); phase = command.optString("phase", "dense-idle");
                        viewerX = command.optInt("x", 502); viewerY = command.optInt("y", 453);
                        viewer = null; tick = 0;
                        server.getWorldDayNightClock().setCurrentCycleMillis(900000);
                        if (player != null) ActionSender.sendWorldTime(player);
                    }
                    if (player != viewer) {
                        removeActors(); viewer = player; tick = 0;
                        if (viewer != null) {
                            if (viewerX != 502 || viewerY != 453) {
                                if (!clear(viewerX, viewerY)) throw new IllegalStateException("Requested visual camera tile is not authored walkable ground");
                                viewer.teleport(viewerX, viewerY, false);
                            }
                            if (busy) createActors();
                        }
                    }
                    driveActors(); driveViewer(); tick++;
                    if (viewer != null && phase.equals("same-position") && tick == 50) {
                        viewer.teleport(viewer.getX(), viewer.getY(), false); samePositionTeleports++;
                        System.out.println("BENCHMARK same-position teleport x="+viewer.getX()+" y="+viewer.getY()+" tick="+tick);
                    }
                    if (viewer != null && phase.equals("transition") && (tick == 50 || tick == 100)) {
                        int targetX = tick == 50 ? 478 : 502;
                        boolean moved = false;
                        for (int radius = 0; radius <= 5 && !moved; radius++) {
                            for (int dx = -radius; dx <= radius && !moved; dx++) for (int dy = -radius; dy <= radius && !moved; dy++) {
                                if (clear(targetX + dx, 453 + dy)) { viewer.teleport(targetX + dx, 453 + dy, false); moved = true; }
                            }
                        }
                        if (!moved) throw new IllegalStateException("No valid authored transition landing");
                    }
                    JSONObject state = new JSONObject().put("online", player != null).put("private", true);
                    if (player != null) state.put("x", player.getX()).put("y", player.getY()).put("level", player.getWorldLocation().getCoordinate().getLevel());
                    state.put("phase", phase).put("phaseTick", tick).put("samePositionTeleports", samePositionTeleports).put("controlledPlayers", actors.size()).put("controlledNpcs", npcs.size())
                        .put("summonsRequested", actors.isEmpty() ? 0 : 8).put("visualProjectileEmitters", actors.isEmpty() ? 0 : 8)
                        .put("visualProjectilesDamage", false).put("removedMapNpcs", removedMapNpcs)
                        .put("cycleMillis", server.getWorldDayNightClock().getCurrentCycleMillis())
                        .put("cycleLengthMillis", server.getWorldDayNightClock().getCycleMillis()).put("cycleRate", server.getWorldDayNightClock().getRateMultiplier());
                    Path next = run.resolve("status.next"); Files.write(next, state.toString().getBytes("UTF-8"));
                    Files.move(next, run.resolve("status.json"), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception error) {
                    System.err.println("RENDERER_FIXTURE_SETUP_FAILURE " + error);
                    error.printStackTrace();
                    try { Files.write(run.resolve("FIXTURE-FAILED"), error.toString().getBytes("UTF-8")); }
                    catch (Exception writeFailure) { writeFailure.printStackTrace(); }
                }
            }
        });
        boolean stopping = false;
        while (server.isRunning()) {
            if (!stopping && Files.exists(run.resolve("STOP"))) {
                stopping = true; Server.closeProcess(5, "Private renderer benchmark closing");
            }
            server.checkShutdown(); Thread.sleep(500);
        }
        Files.deleteIfExists(run.resolve("server.pid"));
        System.exit(0);
    }
}
