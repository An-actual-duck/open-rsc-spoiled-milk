package com.openrsc.server.io;

import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.database.JDBCDatabaseConnection;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.custom.myworld.npcs.MonsterSlayerContacts;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.Permission;
import java.util.*;
import java.util.concurrent.ExecutorService;
import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.*;

/** Exact copied release map + loaded profiles + real contacts/services, without listeners or account data. */
public final class SlayerReleaseActivationTest {
    private static int checks;
    private static final String[] NEW_TASKS = {"falador.giant_frog", "port_sarim.cockatrice",
        "brimhaven.banshee", "brimhaven.naga", "champions.terror_dog", "champions.bloodveld",
        "champions.death_wings", "heroes.dark_beast", "legends.abyssal_demon"};

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Copied map and manifest hash required");
        final Path root = Paths.get("").toRealPath();
        check(Files.exists(root.resolve(".offline-slayer-release")), "disposable fixture marker required");
        check(!Files.exists(root.resolve("local.conf")), "no owner profile overrides");
        final Path map = Paths.get(args[0]).toRealPath();
        check(map.startsWith(root), "map must be a disposable copy inside fixture");
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkListen(int port) { throw new SecurityException("No Slayer fixture listeners"); }
            @Override public void checkConnect(String host, int port) { throw new SecurityException("No Slayer fixture network"); }
            @Override public void checkWrite(String file) {
                if (!Paths.get(file).toAbsolutePath().normalize().startsWith(root)) throw new SecurityException("Fixture write outside copy");
            }
            @Override public void checkDelete(String file) { checkWrite(file); }
        });
        for (String gate : new String[]{"PlayerLocationAuthority", "SpatialRuntimeAuthority", "ProtocolClientAuthority",
                "NativeTerrainPackage", "NativeTerrainResidency", "NativeTerrainReadiness", "NativeTerrainPrediction",
                "NativeTerrainSymmetricResidency", "NativeTerrainAtomicActivation"})
            System.setProperty("openrsc.layered" + gate, "true");
        System.setProperty("openrsc.layeredSyntheticDeepFixture", "false");
        System.setProperty("openrsc.layeredNativeTerrainPackagePath", map.toString());
        System.setProperty("openrsc.layeredNativeTerrainManifestSha256", args[1]);
        System.setProperty("openrsc.layeredNativeWorldRuntimeProfile", "native-replacement");
        check(ServerConfigurationLoader.load("myworld.conf").getLegacyConfiguration().WANT_SLAYER_TOWER_TASKS, "default production profile enabled");
        check(ServerConfigurationLoader.load("myworld-host.conf").getLegacyConfiguration().WANT_SLAYER_TOWER_TASKS, "hosted production profile enabled");
        check(!ServerConfigurationLoader.load("classic-fixture.conf").getLegacyConfiguration().WANT_MYWORLD
            && !ServerConfigurationLoader.load("classic-fixture.conf").getLegacyConfiguration().WANT_SLAYER_TOWER_TASKS,
            "classic fixture defaults to disabled without the optional setting");
        Server server = new Server("myworld.conf");
        try {
            field(server, Server.class, "database", new MySqlGameDatabase(server) {
                @Override public JDBCDatabaseConnection getConnection() { throw new AssertionError("Slayer fixture attempted JDBC"); }
            });
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
            server.getEntityHandler().load();
            World world = server.getWorld();
            world.getRegionManager().preflightNativeLayeredPlacements();
            world.getRegionManager().populateNativeLayeredPlacements();
            check(world.getRegionManager().getNativeLayeredWorldPackage().getManifestSha256().equals(args[1]), "exact selected manifest");
            MonsterSlayerData data = MonsterSlayerData.loadForWorld(world);
            check(data.getRosterVersion() == 5, "actual loaded configuration selects release v5");
            field(world, World.class, "monsterSlayerData", data);
            MonsterSlayerTaskService tasks = new MonsterSlayerTaskService(data);
            field(world, World.class, "monsterSlayerTaskService", tasks);
            Map<Integer,Integer> spawns = new HashMap<Integer,Integer>();
            int towerDeathWings = 0;
            for (Npc npc : world.getNpcs()) {
                spawns.put(npc.getID(), spawns.getOrDefault(npc.getID(), 0) + 1);
                if (npc.getID() == 768 && npc.getWorldLocation().getCoordinate().getLevel() == 3) towerDeathWings++;
            }
            for (int id = 863; id <= 870; id++) {
                check(server.getEntityHandler().getNpcDef(id).isAttackable() && spawns.getOrDefault(id, 0) > 0, "real attackable tower family spawned " + id);
            }
            check(spawns.getOrDefault(768, 0) == 14 && towerDeathWings == 6, "six tower and eight existing Death wings preserved");
            for (Contact contact : data.getContacts()) check(spawns.getOrDefault(contact.getNpcId(), 0) > 0, "real contact is placed " + contact.getKey());
            progression(server, data, tasks);
            disabledProfile(server);
            System.out.println("PASS Slayer release activation: " + checks + " assertions; exact manifest=" + args[1]);
            System.out.println("LIMITS: synthetic player caches and offline eligible-kill API, not live accounts, NPC combat, rendering or JDBC save/restart");
        } finally {
            for (GameTickEvent event : new ArrayList<GameTickEvent>(server.getGameEventHandler().getEvents())) event.stop();
            server.getGameEventHandler().cleanupEvents(); server.getWorld().getPlayers().clear(); server.getEntityHandler().unload();
            for (String name : new String[]{"sqlLoggingThreadPool", "sqlThreadPool", "onlineMonitorThreadPool"}) {
                Field f = Server.class.getDeclaredField(name); f.setAccessible(true);
                ((ExecutorService)f.get(server)).shutdownNow();
            }
            Server.serversList.remove(server.getName(), server);
        }
    }

    private static void progression(Server server, MonsterSlayerData data, MonsterSlayerTaskService tasks) throws Exception {
        Player player = player(server);
        CombatOdysseyMigration.LegacyData legacy = CombatOdysseyMigration.LegacyData.load(Paths.get("conf/server/defs/extras/CombatOdyssey.json"));
        MonsterSlayerState.initialize(player.getCache(), data, legacy);
        MonsterSlayerContactService contacts = new MonsterSlayerContactService(data, tasks);
        check(contacts.beginIntroduction(player).isAccepted() && contacts.completeIntroduction(player).isAccepted(), "normal enrollment state progression");
        MonsterSlayerContacts routes = new MonsterSlayerContacts(new MonsterSlayerContacts.DialogueRenderer() {
            public boolean render(Player p, Npc n, MonsterSlayerDialoguePlan.Step step) { return true; }
            public void npc(Player p, Npc n, String... text) { }
            public void player(Player p, Npc n, String text) { }
            public int choose(Player p, String... choices) { return 0; }
            public void pause() { }
        });
        Set<String> reached = new HashSet<String>();
        for (Contact contact : data.getContactsInChallengeOrder()) {
            Npc npc = null;
            for (Npc placed : server.getWorld().getNpcs()) if (placed.getID() == contact.getNpcId()) { npc = placed; break; }
            check(npc != null, "placed contact resolves");
            for (Task expected : contact.getMandatoryTasks()) {
                check(routes.blockOpNpc(player, npc, "Task"), "real Task opcode owner " + contact.getKey());
                routes.onOpNpc(player, npc, "Task");
                MonsterSlayerState.Snapshot state = MonsterSlayerState.read(player.getCache(), data);
                check(expected.getKey().equals(state.getActiveTaskKey()), "real contact assigned " + expected.getKey());
                reached.add(expected.getKey());
                int npcId = data.getFamily(expected.getFamilyKey()).getNpcIds().get(0);
                long balance = state.getBalances().get(contact.getChallenge()), completed = state.getTasksCompleted();
                check(!tasks.creditEligibleKill(player, 99999).isAccepted(), "unrelated NPC cannot count");
                for (int kill = 1; kill <= expected.getRequiredKills(); kill++) {
                    check(tasks.creditEligibleKill(player, npcId).isAccepted(), "eligible kill " + expected.getKey());
                    state = MonsterSlayerState.read(player.getCache(), data);
                    check(state.getBalances().get(contact.getChallenge()) == balance + (kill == expected.getRequiredKills() ? expected.getPointReward() : 0), "completion-only reward");
                    if (kill == 1) player = relog(server, player, data, legacy);
                }
                check(state.getTasksCompleted() == completed + 1 && !tasks.creditEligibleKill(player, npcId).isAccepted(), "exact-once completion");
            }
            check(MonsterSlayerState.read(player.getCache(), data).getRank() == contact.getAwardedRank(), "earned tier promotion");
            routes.onOpNpc(player, npc, "Task");
            check(MonsterSlayerState.read(player.getCache(), data).isPromotionAcknowledged(contact.getKey(), data), "real promotion acknowledgement");
            player = relog(server, player, data, legacy);
        }
        for (String key : NEW_TASKS) {
            check(reached.contains(key), "new mandatory task reached through actual contact " + key);
            final Contact owner = data.getContact(key.substring(0, key.indexOf('.')));
            final Task repeatable = data.getTask(key + ".repeatable");
            check(repeatable != null, "new repeatable exists " + key);
            MonsterSlayerContactService selected = new MonsterSlayerContactService(data, tasks, bound -> owner.getRepeatableTasks().indexOf(repeatable));
            check(selected.previewTask(player, owner.getKey()) == repeatable && selected.requestTask(player, owner.getKey()).isAccepted(), "normal repeatable pool selection " + key);
            int npcId = data.getFamily(repeatable.getFamilyKey()).getNpcIds().get(0);
            for (int kill = 0; kill < repeatable.getRequiredKills(); kill++) check(tasks.creditEligibleKill(player, npcId).isAccepted(), "repeatable kill credit");
            check(MonsterSlayerState.read(player.getCache(), data).getRank() == MonsterSlayerRank.LEGEND, "repeatables do not relock earned rank");
            player = relog(server, player, data, legacy);
        }
    }

    private static void disabledProfile(Server server) {
        server.getConfig().WANT_SLAYER_TOWER_TASKS = false;
        MonsterSlayerData disabled = MonsterSlayerData.loadForWorld(server.getWorld());
        check(disabled.getRosterVersion() == 3, "explicitly disabled profile loads supported historical current roster");
        for (String key : NEW_TASKS) check(disabled.getTask(key) == null, "disabled profile excludes " + key);
        server.getConfig().WANT_SLAYER_TOWER_TASKS = true;
    }

    private static Player player(Server server) {
        Player player = new Player(server.getWorld(), DataConversions.usernameToHash("slayerrelease"));
        player.setClientVersion(server.getConfig().CLIENT_VERSION);
        player.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));
        player.setQuestPoints(100); player.setQuestStage(Quests.HEROS_QUEST, -1); player.setQuestStage(Quests.LEGENDS_QUEST, -1);
        return player;
    }

    private static Player relog(Server server, Player before, MonsterSlayerData data, CombatOdysseyMigration.LegacyData legacy) {
        Player after = player(server);
        after.getCache().getCacheMap().putAll(before.getCache().getCacheMap());
        Map<String,Object> saved = new LinkedHashMap<String,Object>(after.getCache().getCacheMap());
        check(MonsterSlayerState.initialize(after.getCache(), data, legacy).getStatus() == MonsterSlayerState.LoadResult.Status.LOADED,
            "synthetic reconnect loads persisted state");
        check(saved.equals(after.getCache().getCacheMap()), "synthetic reconnect grants nothing");
        return after;
    }

    private static void field(Object object, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
