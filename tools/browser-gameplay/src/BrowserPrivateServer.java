import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.DataConversions;
import org.json.JSONObject;
import java.nio.file.*;

/** Disposable local world; no owner account or hosted-server state is accessed. */
public final class BrowserPrivateServer {
    public static void main(String[] args) throws Exception {
        final Path serverRoot = Paths.get(".").toRealPath();
        final Path run = serverRoot.getParent();
        if (!Files.isRegularFile(run.resolve("stage.json")) || !Files.isRegularFile(serverRoot.resolve("BROWSER-PRIVATE.json"))) throw new IllegalStateException("Private staged world required");
        com.openrsc.server.config.ServerConfigurationLoadResult loaded = ServerConfigurationLoader.load("myworld.conf");
        com.openrsc.server.ServerConfiguration config = loaded.getLegacyConfiguration();
        if (!"127.0.0.1".equals(config.SERVER_BIND_ADDRESS) || loaded.getProcessNetwork().getServerPort() != 43629 || loaded.getProcessNetwork().getWebsocketPort() != 43529 || !config.WANT_FEATURE_WEBSOCKETS || config.DB_TYPE != DatabaseType.SQLITE || !"browser_spike_dev".equals(config.DB_NAME)) throw new IllegalStateException("Private bindings/database required");
        if (config.WANT_DISCORD_BOT || config.WANT_DISCORD_GENERAL_LOGGING || config.WANT_DISCORD_AUCTION_UPDATES || config.WANT_DISCORD_ITEM_SHOWCASE || config.WANT_DISCORD_MONITORING_UPDATES) throw new IllegalStateException("External services must be disabled");
        Files.write(run.resolve("server.pid"), java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes("UTF-8"));
        final Server server = Server.startServer("myworld.conf");
        if (!server.getDatabase().playerExists("browserdemo")) {
            int id = server.getDatabase().createPlayer("browserdemo", "", DataConversions.hashPassword("BrowserProof13", null), System.currentTimeMillis() / 1000, "127.0.0.1");
            if (id < 0) throw new IllegalStateException("Could not create disposable browser account");
        }
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(), null, 1, "Private browser proof status", DuplicationStrategy.ONE_PER_SERVER) {
            public void run() {
                try {
                    Player player = server.getWorld().getPlayer(DataConversions.usernameToHash("browserdemo"));
                    JSONObject state = new JSONObject().put("online", player != null).put("private", true);
                    if (player != null) state.put("x", player.getX()).put("y", player.getY()).put("level", player.getWorldLocation().getCoordinate().getLevel());
                    Path temp = run.resolve("status.next"); Files.write(temp, state.toString().getBytes("UTF-8"));
                    Files.move(temp, run.resolve("status.json"), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception error) { error.printStackTrace(); }
            }
        });
        boolean stopping = false;
        while (server.isRunning()) {
            if (!stopping && Files.exists(run.resolve("STOP"))) { stopping = true; Server.closeProcess(5, "Private browser test closing"); }
            server.checkShutdown(); Thread.sleep(500);
        }
        Files.deleteIfExists(run.resolve("server.pid"));
        System.exit(0);
    }
}
