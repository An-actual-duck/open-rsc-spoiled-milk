import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.DataConversions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Separate disposable profiling World. Never used by the normal/private-owner runner. */
public final class BrowserRepeatableServer {
    private static final String ACCOUNT="browserdemo",DATABASE="browser_repeatable_dev";
    /** Normal immutable NPC visuals; remove only autonomous movement/AI in this fixture. */
    private static final class FixedNpc extends Npc {
        FixedNpc(World world,int id,int x,int y,int direction){super(world,id,x,y,0);setSprite(direction);}
        @Override public void updateBehavior(boolean players){ }
        @Override public void updateMovementOnly(){ }
    }
    private static void write(Path path,JSONObject value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".next");Files.write(temp,value.toString().getBytes("UTF-8"));
        Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
    }
    private static void guardDatabase(Path file)throws Exception {
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+file);Statement sql=connection.createStatement();ResultSet names=sql.executeQuery("SELECT username FROM players")) {
            int count=0;while(names.next()){if(!ACCOUNT.equals(names.getString(1))||++count>1)throw new IllegalStateException("Repeatable fixture only allows its synthetic account");}
        }
    }
    private static void resetAccount(Path file)throws Exception {
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+file)) {
            connection.setAutoCommit(false);
            try(PreparedStatement sql=connection.prepareStatement("UPDATE players SET x=133,y=660,group_id=1,login_date=1,cameraauto=0,online=0 WHERE username=?")) {
                sql.setString(1,ACCOUNT);if(sql.executeUpdate()!=1)throw new IllegalStateException("Synthetic account unavailable");
            }
            // This dedicated account is deliberately reset on fixture restart.
            // Its native location cache must not disagree with legacy x/y.
            try(Statement sql=connection.createStatement()){sql.executeUpdate("DELETE FROM player_cache WHERE playerID IN (SELECT id FROM players WHERE username='browserdemo')");}
            connection.commit();
        }
    }
    public static void main(String[] args)throws Exception {
        final Path root=Paths.get(".").toRealPath(),run=root.getParent();
        JSONObject marker=new JSONObject(new String(Files.readAllBytes(root.resolve("BROWSER-PRIVATE.json")),"UTF-8"));
        final JSONObject fixture=new JSONObject(new String(Files.readAllBytes(run.resolve("repeatable-fixture.json")),"UTF-8"));
        if(!Files.isRegularFile(run.resolve("stage.json"))||marker.optInt("repeatableFrameWork",0)!=1||fixture.getInt("schema")!=1)throw new IllegalStateException("Explicit repeatable fixture marker required");
        com.openrsc.server.config.ServerConfigurationLoadResult loaded=ServerConfigurationLoader.load("myworld.conf");
        com.openrsc.server.ServerConfiguration config=loaded.getLegacyConfiguration();
        if(!"127.0.0.1".equals(config.SERVER_BIND_ADDRESS)||loaded.getProcessNetwork().getServerPort()!=43649||loaded.getProcessNetwork().getWebsocketPort()!=43549||!config.WANT_FEATURE_WEBSOCKETS||config.DB_TYPE!=DatabaseType.SQLITE||!DATABASE.equals(config.DB_NAME))throw new IllegalStateException("Dedicated repeatable loopback ports/database required");
        if(config.WANT_DISCORD_BOT||config.WANT_DISCORD_GENERAL_LOGGING||config.WANT_DISCORD_AUCTION_UPDATES||config.WANT_DISCORD_ITEM_SHOWCASE||config.WANT_DISCORD_MONITORING_UPDATES)throw new IllegalStateException("External services must be disabled");
        Path database=root.resolve("inc/sqlite/"+DATABASE+".db");guardDatabase(database);
        Files.write(run.resolve("server.pid"),java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes("UTF-8"));
        final Server server=Server.startServer("myworld.conf");
        if(!server.getDatabase().playerExists(ACCOUNT)&&server.getDatabase().createPlayer(ACCOUNT,"",DataConversions.hashPassword("BrowserProof13",null),System.currentTimeMillis()/1000,"127.0.0.1")<0)throw new IllegalStateException("Synthetic account creation failed");
        resetAccount(database);
        final JSONArray roster=fixture.getJSONArray("actors");
        if(roster.length()!=32)throw new IllegalArgumentException("Expected exactly32 maintained mixed NPCs");
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(),null,1,"Repeatable private frame fixture",DuplicationStrategy.ONE_PER_SERVER) {
            private final List<FixedNpc> actors=new ArrayList<FixedNpc>();
            private int stableTicks;
            public void run() {
                try {
                    // A separate fixture World may lazily activate authored NPCs.
                    // Suppress only those actors here; terrain/scenery/game logic,
                    // production networking and client animation stay unchanged.
                    List<Npc> removed=new ArrayList<Npc>();
                    for(Npc npc:server.getWorld().getNpcs())if(!(npc instanceof FixedNpc))removed.add(npc);
                    for(Npc npc:removed)server.getWorld().unregisterNpc(npc);
                    if(actors.isEmpty())for(int n=0;n<roster.length();n++) {
                        JSONObject entry=roster.getJSONObject(n);
                        FixedNpc actor=new FixedNpc(server.getWorld(),entry.getInt("id"),entry.getInt("x"),entry.getInt("y"),entry.getInt("direction"));
                        server.getWorld().registerNpc(actor);actors.add(actor);
                    }
                    boolean valid=server.getWorld().getNpcs().size()==32;JSONArray actual=new JSONArray();
                    for(int n=0;n<actors.size();n++) {
                        FixedNpc actor=actors.get(n);JSONObject expected=roster.getJSONObject(n);
                        valid&=actor.getX()==expected.getInt("x")&&actor.getY()==expected.getInt("y")&&actor.getSprite()==expected.getInt("direction");
                        actual.put(new JSONObject().put("id",actor.getID()).put("name",actor.getDef().getName()).put("index",actor.getIndex()).put("x",actor.getX()).put("y",actor.getY()).put("direction",actor.getSprite()));
                    }
                    stableTicks=valid?stableTicks+1:0;
                    Player player=server.getWorld().getPlayer(DataConversions.usernameToHash(ACCOUNT));
                    JSONObject state=new JSONObject().put("schema",1).put("private",true).put("fixture","repeatable-v1").put("account",ACCOUNT).put("online",player!=null).put("valid",valid).put("stableTicks",stableTicks).put("npcCount",server.getWorld().getNpcs().size()).put("actors",actual);
                    if(player!=null)state.put("x",player.getX()).put("y",player.getY()).put("level",player.getWorldLocation().getCoordinate().getLevel()).put("groupId",player.getGroupID());
                    write(run.resolve("status.json"),state);
                    if(valid&&stableTicks==1)write(run.resolve("REPEATABLE-FIXTURE-READY.json"),state);
                } catch(Exception error){error.printStackTrace();}
            }
        });
        boolean stopping=false;
        while(server.isRunning()) {
            if(!stopping&&Files.exists(run.resolve("STOP"))){stopping=true;Server.closeProcess(5,"Repeatable private benchmark closing");}
            server.checkShutdown();Thread.sleep(500);
        }
        Files.deleteIfExists(run.resolve("server.pid"));System.exit(0);
    }
}
