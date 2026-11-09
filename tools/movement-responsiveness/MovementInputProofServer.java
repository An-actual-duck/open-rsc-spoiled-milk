import com.openrsc.server.Server;
import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.DataConversions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.json.JSONObject;

/** Disposable loopback server. Ordinary account, login/plugins/network and Server.run. */
public final class MovementInputProofServer {
    public static void main(String[] args) {
        try { run(); } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void run() throws Exception {
        Path root=Paths.get(".").toRealPath(), run=root.getParent();
        if(!Files.isRegularFile(run.resolve("MOVEMENT-INPUT-PROOF.json"))) throw new IllegalStateException("Marked private fixture required");
        com.openrsc.server.config.ServerConfigurationLoadResult loaded=ServerConfigurationLoader.load("myworld.conf");
        ServerConfiguration c=loaded.getLegacyConfiguration();
        if(!c.configFile.equals("myworld.conf") || !"127.0.0.1".equals(c.SERVER_BIND_ADDRESS)
            || loaded.getProcessNetwork().getServerPort()!=43649 || loaded.getProcessNetwork().getWebsocketPort()!=43549
            || c.DB_TYPE!=DatabaseType.SQLITE || !"movement_walk_dev".equals(c.DB_NAME)
            || c.CLIENT_VERSION!=10059 || c.NPC_CAPACITY!=16384 || c.WANT_DISCORD_BOT || c.WANT_DISCORD_GENERAL_LOGGING
            || c.WANT_DISCORD_AUCTION_UPDATES || c.WANT_DISCORD_ITEM_SHOWCASE || c.WANT_DISCORD_MONITORING_UPDATES)
            throw new IllegalStateException("Exact isolated current-protocol endpoint/database/config required");
        Server server=Server.startServer("myworld.conf");
        if(!server.getDatabase().playerExists("walkproof")) {
            int id=server.getDatabase().createPlayer("walkproof","",DataConversions.hashPassword("WalkProof13",null),System.currentTimeMillis()/1000,"127.0.0.1");
            if(id<0)throw new IllegalStateException("Disposable account creation failed");
        }
        try(java.sql.Connection connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+root.resolve("inc/sqlite/movement_walk_dev.db"));
            java.sql.PreparedStatement statement=connection.prepareStatement("UPDATE players SET login_date=1,x=502,y=453 WHERE username=?")) {
            statement.setString(1,"walkproof");statement.executeUpdate();
        }
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(),null,1,"Private movement proof status",DuplicationStrategy.ONE_PER_SERVER) {
            boolean cleared;
            public void run() {
                try {
                    if(!cleared){for(Npc npc:new ArrayList<Npc>(server.getWorld().getNpcs()))server.getWorld().unregisterNpc(npc);cleared=true;}
                    Player p=server.getWorld().getPlayer(DataConversions.usernameToHash("walkproof"));
                    JSONObject status=new JSONObject().put("tick",server.getCurrentTick()).put("wallMillis",System.currentTimeMillis()).put("monoNanos",System.nanoTime())
                        .put("actualServerRun",true).put("clientVersion",c.CLIENT_VERSION).put("npcCapacity",c.NPC_CAPACITY).put("loggedIn",p!=null&&p.isLoggedIn());
                    if(p!=null){
                        status.put("x",p.getX()).put("y",p.getY()).put("level",p.getWorldLocation().getCoordinate().getLevel())
                            .put("busy",p.isBusy()).put("teleporting",p.isTeleporting()).put("ownedPlugins",p.getOwnedPlugins().size()).put("canLogout",p.canLogout());
                        try {java.lang.reflect.Method eligible=Player.class.getDeclaredMethod("eligibleIdleWalkOwner");eligible.setAccessible(true);status.put("idleLaneOwnerEligible",eligible.invoke(p));}
                        catch(NoSuchMethodException baseline){status.put("idleLaneOwnerEligible","baseline-no-lane");}
                    }
                    Path next=run.resolve("status.next");Files.write(next,status.toString().getBytes(StandardCharsets.UTF_8));
                    Files.move(next,run.resolve("status.json"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    Files.write(run.resolve("server-ticks.jsonl"),(status.toString()+"\n").getBytes(StandardCharsets.UTF_8),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
                }catch(Exception failure){try{Files.write(run.resolve("FIXTURE-FAILED"),failure.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}failure.printStackTrace();}
            }
        });
        boolean stopping=false;
        while(server.isRunning()){
            if(!stopping&&Files.exists(run.resolve("STOP"))){stopping=true;Server.closeProcess(3,"Private movement proof finished");}
            server.checkShutdown();Thread.sleep(100);
        }
        System.exit(0);
    }
}
