package com.openrsc.server.io;

import org.json.JSONObject;
import com.openrsc.server.Server;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.login.LoginRequest;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Permission;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Offline production persistence rehearsal. Its input/output can contain private
 * account state: invoke only through the private-directory Python runner. This
 * deliberately never calls Server.init/start, World.load, or LoginRequest.process.
 */
public final class ReleaseUpgradeRehearsal {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void field(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static Channel channel() {
        return (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class},
            (proxy, method, args) -> {
                if (method.getName().equals("remoteAddress")) return new InetSocketAddress("127.0.0.1", 1);
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                if (method.getReturnType() == boolean.class) return true;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == long.class) return 0L;
                return null;
            });
    }
    private static final class Request extends LoginRequest {
        Request(Server s, String username) {
            super(s, channel(), username, "unused", false, s.getConfig().CLIENT_VERSION, false, null);
        }
        @Override public void loginValidated(int response) { }
        @Override public void loadingComplete(Player p) { }
    }
    private static List<Object> item(Item i, int slot) {
        if (i == null) return null;
        return Arrays.<Object>asList(slot, i.getItemId(), i.getCatalogId(), i.getAmount(),
            i.getNoted(), i.getItemStatus().isWielded(), i.getItemStatus().getDurability());
    }
    private static Map<String,Object> receipt(Player p, MonsterSlayerData data) {
        Map<String,Object> result = new TreeMap<>();
        List<Object> inv = new ArrayList<>(), bank = new ArrayList<>(), equip = new ArrayList<>();
        int slot = 0;
        for (Item i : p.getCarriedItems().getInventory().getItems()) inv.add(item(i, slot++));
        slot = 0;
        for (Item i : p.getBank().getItems()) bank.add(item(i, slot++));
        for (int n = 0; n < Equipment.SLOT_COUNT; n++) {
            Item i = p.getCarriedItems().getEquipment().get(n); if (i != null) equip.add(item(i,n));
        }
        result.put("inventory", inv); result.put("bank", bank); result.put("equipment", equip);
        List<Object> presets = new ArrayList<>();
        for (int n = 0; n < BankPreset.PRESET_COUNT; n++) {
            BankPreset preset = p.getBank().getBankPreset(n);
            List<Object> pi = new ArrayList<>(), pe = new ArrayList<>(); slot = 0;
            for (Item i : preset.getInventory()) { if (i != null) pi.add(item(i, slot)); slot++; }
            slot=0;
            for (Item i : preset.getEquipment()) { if (i != null) pe.add(item(i, slot)); slot++; }
            presets.add(Arrays.asList(pi,pe));
        }
        result.put("presets", presets);
        result.put("location", p.getLayeredLocation().toString());
        Map<String,Object> slayerCache=new TreeMap<>(), locationCache=new TreeMap<>();
        for (Map.Entry<String,Object> entry:p.getCache().getCacheMap().entrySet()) {
            if(entry.getKey().startsWith("monster_slayer_")) slayerCache.put(entry.getKey(),entry.getValue());
            if(entry.getKey().startsWith("layerloc_")) locationCache.put(entry.getKey(),entry.getValue());
        }
        result.put("slayerCache",slayerCache); result.put("locationCache",locationCache);
        MonsterSlayerState.Snapshot s = MonsterSlayerState.read(p.getCache(), data);
        Map<String,Object> slayer = new TreeMap<>();
        slayer.put("rank", s.getRank().name()); slayer.put("balances", s.getBalances().asMap());
        slayer.put("intro", s.getIntroStage()); slayer.put("task", s.getActiveTaskKey());
        slayer.put("kills", s.getActiveKills()); slayer.put("completed", s.getTasksCompleted());
        slayer.put("inventoryUpgrades", s.getInventoryUpgrades());
        slayer.put("migration", s.getMigrationVersion()); slayer.put("legacyStatus", s.getLegacyStatus().name());
        slayer.put("legacyPrestige", s.getLegacyPrestige());
        result.put("slayer", slayer);
        return result;
    }
    private static void installSlayer(World w) throws Exception {
        MonsterSlayerData data = MonsterSlayerData.loadForWorld(w);
        field(w, World.class, "monsterSlayerData", data);
        field(w, World.class, "monsterSlayerLegacyData", CombatOdysseyMigration.LegacyData.load(
            Paths.get(w.getServer().getConfig().CONFIG_DIR, "defs", "extras", "CombatOdyssey.json")));
        field(w, World.class, "monsterSlayerTaskService", new MonsterSlayerTaskService(data));
    }
    private static void progressProbe(Player p, MonsterSlayerData data) {
        MonsterSlayerTaskService tasks = new MonsterSlayerTaskService(data);
        MonsterSlayerContactService contacts = new MonsterSlayerContactService(data,tasks);
        require(contacts.beginIntroduction(p).isAccepted(), "probe introduction begins");
        require(contacts.completeIntroduction(p).isAccepted(), "probe introduction completes");
        MonsterSlayerDefinitions.Contact first = data.getContactsInChallengeOrder().get(0);
        require(tasks.assignMandatory(p,first.getKey()).isAccepted(), "probe real assignment");
        MonsterSlayerState.Snapshot s = MonsterSlayerState.read(p.getCache(),data);
        MonsterSlayerDefinitions.Task t = data.getTask(s.getActiveTaskKey());
        require(tasks.creditEligibleKill(p,data.getFamily(t.getFamilyKey()).getNpcIds().get(0)).isAccepted(),
            "probe real eligible kill progression");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("config, private receipt path, mode required");
        final Path root = Paths.get("").toRealPath();
        final Path report = Paths.get(args[1]).toAbsolutePath().normalize();
        require(Files.exists(root.resolve(".offline-release-rehearsal")), "disposable runtime marker required");
        require(report.startsWith(root), "receipt must stay in disposable runtime");
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission p) { }
            @Override public void checkListen(int port) { throw new SecurityException("Offline rehearsal: no listeners"); }
            @Override public void checkConnect(String host,int port) { throw new SecurityException("Offline rehearsal: no network"); }
            @Override public void checkWrite(String file) {
                if (!Paths.get(file).toAbsolutePath().normalize().startsWith(root))
                    throw new SecurityException("Offline rehearsal write outside disposable runtime");
            }
            @Override public void checkDelete(String file) { checkWrite(file); }
        });
        Server server = null;
        try {
            server = new Server(args[0]);
            require(server.getConfig().DB_TYPE.name().equals("SQLITE"), "private SQLite required");
            require(server.getConfig().DB_NAME.equals("rehearsal"), "fixed disposable DB required");
            require(server.getDiscordService() == null, "Discord disabled");
            server.getDatabase().open();
            server.getEntityHandler().load();
            field(server,Server.class,"maxItemId",server.getDatabase().getMaxItemID());
            World w = server.getWorld();
            w.getRegionManager().load();
            w.getRegionManager().preflightNativeLayeredPlacements();
            w.getWorldLoader().getWorldPopulator().populateWorld();
            w.getRegionManager().populateNativeLayeredPlacements();
            w.getNpcDrops().load(); installSlayer(w);
            MonsterSlayerData data = w.getMonsterSlayerData();
            if (args[2].equals("schema")) {
                Class<?> c = Class.forName("com.openrsc.server.content.market.MarketJdbcStore");
                Object store = c.getConstructor(Server.class).newInstance(server);
                Method ensure = c.getDeclaredMethod("ensureSchema"); ensure.setAccessible(true); ensure.invoke(store);
            }
            Map<String,Object> profiles = new TreeMap<>();
            int count=0, rejectedSlayer=0;
            Map<Integer,String> identities=new LinkedHashMap<>();
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:inc/sqlite/rehearsal.db");
                 Statement stmt = c.createStatement();
                 ResultSet rows = stmt.executeQuery("SELECT id,username FROM players ORDER BY id")) {
                while(rows.next()) identities.put(rows.getInt(1),rows.getString(2));
            } // Release SQLite's reader before the production writer commits.
                for (Map.Entry<Integer,String> identity : identities.entrySet()) {
                    Player p = server.getPlayerService().loadPlayer(new Request(server, identity.getValue()));
                    require(p != null, "production profile load failed at ordinal " + count);
                    int id=identity.getKey();
                    if (args[2].equals("downgrade")) {
                        try { MonsterSlayerState.read(p.getCache(), data); }
                        catch (IllegalArgumentException rejected) { rejectedSlayer++; }
                        count++; continue; // Never old-save a new-roster account.
                    }
                    if (args[2].equals("progress") && p.getUsername().equalsIgnoreCase("Releaseprobe"))
                        progressProbe(p,data);
                    Map<String,Object> before = receipt(p,data);
                    require(server.getPlayerService().savePlayer(p), "production profile save failed at ordinal " + count);
                    Map<String,Object> after = receipt(p,data);
                    require(before.equals(after), "save mutated in-memory profile at ordinal " + count);
                    profiles.put(Integer.toString(id), after); count++;
                }
            Map<String,Object> result = new TreeMap<>();
            result.put("profiles",profiles); result.put("count",count); result.put("checks",checks);
            List<String> graceNames=new ArrayList<>();
            for(int id:new int[]{3299,3300,3307,3308})
                graceNames.add(server.getEntityHandler().getItemDef(id).getName());
            result.put("legacyGraceDefinitionNames",graceNames);
            int rosterVersion=1;
            try { rosterVersion=((Number)data.getClass().getMethod("getRosterVersion").invoke(data)).intValue(); }
            catch (NoSuchMethodException oldRoster) { /* v0.2.89 predates the versioned roster API. */ }
            result.put("rosterVersion",rosterVersion); result.put("rejectedSlayer",rejectedSlayer);
            Files.write(report,new JSONObject(result).toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("PASS offline production persistence phase: "+count+" profiles; "+checks+" assertions");
        } finally {
            if (server != null) {
                server.getDatabase().close();
                for (String name : new String[]{"sqlLoggingThreadPool","sqlThreadPool","onlineMonitorThreadPool"}) {
                    Field f=Server.class.getDeclaredField(name); f.setAccessible(true);
                    ((ExecutorService)f.get(server)).shutdownNow();
                }
            }
        }
    }
}
