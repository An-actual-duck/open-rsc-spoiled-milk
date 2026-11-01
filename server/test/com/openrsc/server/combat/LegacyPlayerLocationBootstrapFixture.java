package com.openrsc.server.combat;

import com.openrsc.server.Server;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.database.JDBCDatabaseConnection;
import com.openrsc.server.database.struct.PlayerCache;
import com.openrsc.server.database.struct.PlayerData;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.*;
import com.openrsc.server.service.PlayerService;
import com.openrsc.server.util.rsc.LoginResponse;
import java.security.Permission;
import java.sql.*;
import java.util.*;

/** Actual LoginRequest + complete PlayerService.loadPlayer over fresh SQLite profiles. */
public final class LegacyPlayerLocationBootstrapFixture {
    static int checks, nextId=500;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void sql(String statement)throws Exception{MarketAtomicityFixture.sql(statement);}
    static WorldLocation location(int level){return WorldLocation.global(new WorldCoordinate(109,677,level));}
    static final class Profiles extends MarketAtomicityFixture.LoginDatabase {
        final MySqlGameDatabase realCache;
        Profiles(Server server,MarketAtomicityFixture.Provider provider){super(server,provider);realCache=new MySqlGameDatabase(server){@Override public JDBCDatabaseConnection getConnection(){return provider;}};}
        @Override public PlayerData queryLoadPlayerData(Player p){
            PlayerData profile=super.queryLoadPlayerData(p);
            try(PreparedStatement s=provider.c.prepareStatement("SELECT x,y FROM qa_profile_locations WHERE id=?")){
                s.setInt(1,profile.playerId);try(ResultSet r=s.executeQuery()){check(r.next(),"synthetic stored location exists");profile.xLocation=r.getInt(1);profile.yLocation=r.getInt(2);return profile;}
            }catch(SQLException e){throw new RuntimeException(e);}
        }
        @Override public PlayerCache[] queryLoadPlayerCache(Player p){return realCache.queryLoadPlayerCache(p);}
    }
    static int profile(int x,int packedY,Map<String,Object> cache)throws Exception{
        int id=++nextId;String name="Location"+id;
        sql("INSERT INTO qa_players VALUES("+id+",'"+name+"')");
        sql("INSERT INTO qa_profile_locations VALUES("+id+","+x+","+packedY+")");
        try(Connection c=MarketAtomicityFixture.open();PreparedStatement s=c.prepareStatement("INSERT INTO qa_player_cache(playerID,`key`,type,value) VALUES(?,?,?,?)")){
            for(Map.Entry<String,Object> e:cache.entrySet()){
                s.setInt(1,id);s.setString(2,e.getKey());s.setInt(3,e.getValue() instanceof Integer?0:1);s.setString(4,e.getValue().toString());s.executeUpdate();
            }
        }
        return id;
    }
    static Player load(CurrentCombatHarness h,int id,boolean expectFailure)throws Exception{
        MarketAtomicityFixture.RecordingLogin request=new MarketAtomicityFixture.RecordingLogin(h,"Location"+id);
        request.process();h.advanceOneCombatTick();h.advanceOneCombatTick();
        if(expectFailure){check(request.response==LoginResponse.LOGINSERVER_OFFLINE&&request.completed==null,"malformed location denies full account load");return null;}
        check(request.completed!=null,"full account load completes");return request.completed;
    }
    static void release(CurrentCombatHarness h,Player player){h.server().getPacketFilter().removeLoggedInPlayer("127.0.0.1",player.getUsernameHash());h.logout(player);}
    static Map<String,Object> record(int level,boolean nativeProjection)throws Exception{
        Map<String,Object> cache=new HashMap<>();WorldLocation l=location(level);
        Point point=LayeredCompatibilityPointAdapter.toCompatibilityPoint(l,false,nativeProjection);
        LayeredPlayerLocationPersistence.write(cache,l,point,"fixture-authored",false,nativeProjection);return cache;
    }
    public static void main(String[] args)throws Exception{
        MarketAtomicityFixture.url=System.getenv("LOCATION_TEST_JDBC");MarketAtomicityFixture.user="";MarketAtomicityFixture.password="";MarketAtomicityFixture.mysql=false;
        if(MarketAtomicityFixture.url==null||!MarketAtomicityFixture.url.startsWith("jdbc:sqlite:"))throw new IllegalArgumentException("Fresh synthetic SQLite fixture required");
        System.setSecurityManager(new SecurityManager(){public void checkPermission(Permission p){}public void checkListen(int p){throw new AssertionError("No listeners");}public void checkConnect(String h,int p){if(p<0)throw new SecurityException("No DNS");throw new AssertionError("No network");}});
        MarketAtomicityFixture.schema();sql("CREATE TABLE qa_profile_locations(id INTEGER PRIMARY KEY,x INTEGER,y INTEGER)");sql("CREATE TABLE qa_player_cache(playerID INTEGER,`key` VARCHAR(32),type INTEGER,value VARCHAR(150))");
        try(CurrentCombatHarness h=new CurrentCombatHarness();MarketAtomicityFixture.Provider provider=new MarketAtomicityFixture.Provider()){
            h.server().getConfig().DB_TABLE_PREFIX="qa_";Profiles db=new Profiles(h.server(),provider);
            MarketAtomicityFixture.set(h.server(),Server.class,"database",db);MarketAtomicityFixture.set(h.server(),Server.class,"playerService",new PlayerService(h.world(),h.server().getConfig(),db));
            for(int level:new int[]{0,1,2,-1,-3})check(h.world().getRegionManager().hasNativeLayeredTerrain(location(level)),"synthetic native terrain exists at "+level);
            if(args.length>0&&args[0].equals("reproduce")){
                int id=profile(109,3509,Collections.emptyMap());Player p=load(h,id,false);
                check(p.getLayeredLocation().equals(location(0)),"pre-fix reproduction must land incorrectly on level0");
                System.out.println("REPRODUCED ordinary account load: stored (109,3509) without layer cache -> "+p.getLayeredLocation()+" runtime="+p.getLocation());release(h,p);
            }else{
                for(int level:new int[]{0,1,2,-1}){
                    int packed=677+LegacyPackedPointAdapter.legacyPlaneForLevel(level)*944;int id=profile(109,packed,Collections.emptyMap());Player p=load(h,id,false);
                    check(p.getLayeredLocation().equals(location(level)),"missing-cache packed level preserved: "+level);
                    check(p.getX()==109&&p.getY()==677,"native runtime projection remains unpacked");
                    check(p.getCache().getInt("layerloc_level")==level,"new cache records correct signed level");
                    check(p.getCache().getString("layerloc_origin").equals(LayeredPlayerLocationPersistence.LEGACY_BOOTSTRAP),"bootstrap origin retained");release(h,p);
                }
                for(int level:new int[]{1,2,-1}){
                    int packed=677+LegacyPackedPointAdapter.legacyPlaneForLevel(level)*944;int id=profile(109,packed,record(level,false));Player p=load(h,id,false);
                    check(p.getLayeredLocation().equals(location(level)),"complete classic location record remains authoritative: "+level);release(h,p);
                }
                int nativeId=profile(109,677,record(-3,true));Player deep=load(h,nativeId,false);
                check(deep.getLayeredLocation().equals(location(-3)),"complete deep native record retained");check(deep.getCache().getString("layerloc_origin").equals("fixture-authored"),"native origin retained");release(h,deep);
                Map<String,Object> rebase=record(1,false);int rebaseId=profile(109,2565,rebase);Player rebased=load(h,rebaseId,false);
                check(rebased.getLayeredLocation().equals(location(2)),"stale complete record rebases from original persisted band2");check(rebased.getCache().getString("layerloc_origin").equals(LayeredPlayerLocationPersistence.LEGACY_REBASE),"legacy rebase rule retained");release(h,rebased);
                Map<String,Object> partial=new HashMap<>();partial.put("layerloc_format",LayeredPlayerLocationPersistence.FORMAT);load(h,profile(109,3509,partial),true);
                Map<String,Object> corrupt=record(-3,true);corrupt.put("layerloc_format","invalid-format");load(h,profile(109,677,corrupt),true);
                Map<String,Object> divergent=record(-3,true);divergent.put("layerloc_packed_y",678);load(h,profile(109,677,divergent),true);
                // Different rows through the same service must not share a mutable receipt.
                Player first=load(h,profile(109,1621,Collections.emptyMap()),false);Player second=load(h,profile(109,3509,Collections.emptyMap()),false);
                check(first.getLayeredLocation().equals(location(1))&&second.getLayeredLocation().equals(location(-1)),"per-load receipt cannot bleed between accounts");release(h,first);release(h,second);
                System.out.println("PASS legacy player location bootstrap: "+checks+" assertions; full account loads, fresh SQLite/native fixture");
            }
        }
    }
}
