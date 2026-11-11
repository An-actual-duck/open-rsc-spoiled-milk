package com.openrsc.server.combat;

import com.openrsc.server.Server;
import com.openrsc.server.database.JDBCDatabaseConnection;
import com.openrsc.server.database.impl.mysql.MySqlGameDatabase;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Listener-free test of the source renderer harness over real production queues. */
public final class RendererCombatWorkloadFixture {
    private static final String[] FAMILIES={"ice","thunder","wood","ranged","throwing","melee"};
    private static Class<?> fixture;
    private static int checks;

    /** Empty seed inventory is the only database fact needed for world population. */
    private static final class NoJdbcDatabase extends MySqlGameDatabase {
        NoJdbcDatabase(Server server){super(server);}
        @Override public Long[] queryInUseItemIds(){return new Long[0];}
        @Override public JDBCDatabaseConnection getConnection(){throw new AssertionError("Offline workload must not open JDBC");}
    }

    private static void check(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    private static Object get(String name)throws Exception {
        Field field=fixture.getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }
    private static void set(String name,Object value)throws Exception {
        Field field=fixture.getDeclaredField(name);field.setAccessible(true);field.set(null,value);
    }
    private static Object call(String name)throws Exception {
        Method method=fixture.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(null);
    }
    private static JSONObject state()throws Exception {return (JSONObject)call("combatState");}
    private static void step(CurrentCombatHarness h,int count)throws Exception {
        for(int tick=0;tick<count;tick++) {
            h.server().getGameEventHandler().processNonPlayerEvents();
            h.server().getGameUpdater().updateWorld();
            h.server().getGameUpdater().processNpcs();
            List<Player> players=new ArrayList<Player>(h.world().getPlayers());
            for(Player player:players){player.updatePosition();player.processTick();}
            for(Player player:players){player.processLogout();player.sendUpdates();}
            h.server().getGameUpdater().doCleanup();
            h.server().getGameEventHandler().cleanupEvents();
            CurrentCombatHarness.invokePrivate(h.server(),"advanceTicks",new Class<?>[]{long.class},1L);
            h.clock().advanceMillis(h.server().getConfig().GAME_TICK);
        }
    }
    private static void healthy(JSONObject state) {
        check(state.getBoolean("workloadComplete"),"all real families must be complete: "+state);
        check(state.getInt("activeChannels")==6,"exactly six expected registered channels");
        for(String family:FAMILIES) {
            JSONObject channel=state.getJSONObject("channels").getJSONObject(family);
            check(channel.getBoolean("running")&&channel.getBoolean("registered")
                &&channel.getBoolean("playerRegistered"),"live exact registered channel "+family);
            check(channel.getInt("eventTicks")>0,"production scheduler executed "+family);
            check(channel.getString("error").isEmpty(),"healthy channel error "+family);
            check(state.getJSONObject("creditedDamageByFamily").getInt(family)>0,"real ledger contribution "+family);
            if(!family.equals("melee"))check(state.getJSONObject("resourceUnitsSpent").getInt(family)>0,"real resource consumption "+family);
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            Field database=Server.class.getDeclaredField("database");database.setAccessible(true);
            database.set(h.server(),new NoJdbcDatabase(h.server()));
            h.world().getRegionManager().load();
            h.world().getRegionManager().preflightNativeLayeredPlacements();
            h.world().getWorldLoader().getWorldPopulator().populateWorld();
            h.world().getRegionManager().populateNativeLayeredPlacements();
            for(Npc npc:new ArrayList<Npc>(h.world().getNpcs()))h.world().unregisterNpc(npc);
            Player viewer=h.player("renderdemo",502,453);
            viewer.setAttribute("dummyplayer",true);
            viewer.getSettings().setAppearance(new PlayerAppearance(0,0,0,0,1,2));
            viewer.setWornItems(viewer.getSettings().getAppearance().getSprites());
            h.server().getPluginHandler().load();
            try {
                fixture=Class.forName("RendererBenchmarkServer");
                set("worldServer",h.server());set("phase","combat-real");set("sustainSummonsForBenchmark",true);
                call("createActors");call("beginRealCombat");
                List<Player> actors=(List<Player>)get("actors");
                check(actors.size()==16,"source bootstrap crowd");
                step(h,16);JSONObject before=state();healthy(before);
                // Utility castTimer retains wall time in production. Cross it once;
                // do not change timers, spell costs, cadence, or damage algorithms.
                Thread.sleep(h.server().getConfig().MILLISECONDS_BETWEEN_CASTS+100L);
                step(h,16);JSONObject after=state();healthy(after);
                for(String family:FAMILIES) {
                    check(after.getJSONObject("creditedDamageByFamily").getInt(family)
                        >before.getJSONObject("creditedDamageByFamily").getInt(family),"advancing contribution "+family);
                    check(after.getJSONObject("channels").getJSONObject(family).getInt("eventTicks")
                        >before.getJSONObject("channels").getJSONObject(family).getInt("eventTicks"),"advancing scheduled event "+family);
                    if(!family.equals("melee"))check(after.getJSONObject("resourceUnitsSpent").getInt(family)
                        >before.getJSONObject("resourceUnitsSpent").getInt(family),"advancing real consumption "+family);
                }
                Player caster=actors.get(0);
                MagicCombatEvent original=caster.getMagicCombatEvent();
                MagicCombatEvent orphan=new MagicCombatEvent(h.world(),caster,0,original.getTarget(),caster.getAutoCastSpell());
                caster.setMagicCombatEvent(orphan);
                check(h.server().getGameEventHandler().has(orphan),"same scheduler key exists even when new handle is orphaned");
                JSONObject orphaned=state();JSONObject channel=orphaned.getJSONObject("channels").getJSONObject("ice");
                check(channel.getBoolean("schedulerKeyPresent")&&!channel.getBoolean("registered")
                    &&!channel.getBoolean("running")&&!orphaned.getBoolean("workloadComplete"),"exact-identity guard rejects coalesced orphan");
                original.restart();caster.setMagicCombatEvent(original);
                healthy(state());original.stop();
                JSONObject stopped=state();
                check(!stopped.getJSONObject("channels").getJSONObject("ice").getBoolean("running")
                    &&!stopped.getBoolean("workloadComplete"),"stopped registered handle is not coverage");
                System.out.println("PASS renderer combat workload "+checks+" checks; actual queues, resources, contributions, orphan/stopped guards");
                System.out.println("WORKLOAD_BEFORE "+before);
                System.out.println("WORKLOAD_AFTER "+after);
            } finally {h.server().getPluginHandler().unload();}
        }
    }
}
