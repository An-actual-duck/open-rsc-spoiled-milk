package com.openrsc.server.combat;

import com.openrsc.server.Server;
import com.openrsc.server.diagnostics.ActiveCombatBenchmark;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.runtime.DeterministicGameRandom;
import com.openrsc.server.util.EntityList;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Listener-free offline workload. Calls production phases, never Server.start(). */
public final class NpcCapacityRuntimeBenchmark {
    static final com.sun.management.ThreadMXBean memory=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static long allocated() {return memory.getThreadAllocatedBytes(Thread.currentThread().getId());}
    static void open(World world,int x,int y) {
        world.getTile(x,y).traversalMask=0;world.getTile(x,y).projectileAllowed=true;world.getTile(x,y).originalProjectileAllowed=true;
    }
    static void report(String name,long[] values,int n,String mode,long bytes) {
        Arrays.sort(values);
        System.out.printf(Locale.ROOT,"RUNTIME population=%d workload=%s phase=%s median_ns=%d p95_ns=%d p99_ns=%d workload_allocated_bytes_per_tick=%d%n",n,mode,name,values[values.length/2],values[(values.length*95)/100],values[(values.length*99)/100],bytes/values.length);
    }
    public static void main(String[] args) throws Exception {
        int n=Integer.parseInt(args[0]);String mode=args[1];
        MutableGameClock clock=new MutableGameClock(1700000000000L);
        Server server=new Server("myworld.conf",clock,new DeterministicGameRandom(1511506148L));
        try {
            server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY=false;
            server.getConfig().NPC_CAPACITY=16384;
            server.getConfig().WANT_DISCORD_GENERAL_LOGGING=false;
            server.getEntityHandler().load();
            World world=server.getWorld();world.getNpcDrops().load();
            Field registry=World.class.getDeclaredField("npcs");registry.setAccessible(true);registry.set(world,new EntityList<Npc>(16384));
            ActiveCombatBenchmark combat=null;
            if(mode.equals("active")) {
                combat=new ActiveCombatBenchmark(server,32,10053);combat.initialize();
                Field channelField=Player.class.getDeclaredField("channel");channelField.setAccessible(true);
                for(Player player:world.getPlayers()) {
                    io.netty.channel.Channel channel=(io.netty.channel.Channel)Proxy.newProxyInstance(
                        io.netty.channel.Channel.class.getClassLoader(),new Class<?>[]{io.netty.channel.Channel.class},
                        (proxy,method,arguments)-> {
                            if(method.getName().equals("isOpen")||method.getName().equals("isActive")||method.getName().equals("isWritable"))return true;
                            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                            if(method.getName().equals("equals"))return proxy==arguments[0];
                            return null;
                        });
                    channelField.set(player,channel);
                }
            }
            int idleX=100,idleY=100;open(world,idleX,idleY);
            while(world.getNpcs().size()<n) world.registerNpc(new Npc(world,3,idleX,idleY,0));
            Method processEvents=server.getGameEventHandler().getClass().getDeclaredMethod("processEvents");processEvents.setAccessible(true);
            Method tick=Server.class.getDeclaredMethod("advanceTicks",long.class);tick.setAccessible(true);
            Field outgoing=Player.class.getDeclaredField("outgoingPackets");outgoing.setAccessible(true);
            String[] names={"events","npc-behavior","movement","visibility-packets","cleanup","total"};
            long[][] times=new long[names.length][80]; long bytes=0;
            for(int sample=-20;sample<80;sample++) {
                long start=System.nanoTime(),allocation=allocated(),last=start;
                processEvents.invoke(server.getGameEventHandler());server.getGameEventHandler().cleanupEvents();
                long now=System.nanoTime();if(sample>=0)times[0][sample]=now-last;last=now;
                server.getGameUpdater().processNpcs();
                now=System.nanoTime();if(sample>=0)times[1][sample]=now-last;last=now;
                world.getNpcs().forEachLive(Npc::updateMovementOnly);
                now=System.nanoTime();if(sample>=0)times[2][sample]=now-last;last=now;
                server.getGameUpdater().updateWorld();
                for(Player player:world.getPlayers())server.getGameUpdater().updateClient(player);
                now=System.nanoTime();if(sample>=0)times[3][sample]=now-last;last=now;
                server.getGameUpdater().doCleanup();
                for(Player player:world.getPlayers()) ((List<?>)outgoing.get(player)).clear();
                now=System.nanoTime();if(sample>=0) {times[4][sample]=now-last;times[5][sample]=now-start;bytes+=allocated()-allocation;}
                clock.advanceMillis(server.getConfig().GAME_TICK);tick.invoke(server,1L);
            }
            for(int i=0;i<names.length;i++)report(names[i],times[i],n,mode,bytes);
            long localNpcs=0;for(Player player:world.getPlayers())localNpcs+=player.getLocalNpcs().size();
            System.out.println("VISIBILITY local_npc_references="+localNpcs);
            if(combat!=null&&localNpcs==0)throw new AssertionError("active workload produced no NPC visibility");
            System.out.println("HEAP snapshot_used_bytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
            if(combat!=null) {
                String summary=combat.buildSummary();System.out.println(summary);
                if(!summary.contains("activeCombatInvariant=pass"))throw new AssertionError(summary);
            }
            if(world.getNpcs().size()!=n)throw new AssertionError("population changed");
            System.out.println("PASS listener-free runtime workload");
        } finally {
            for(GameTickEvent event:server.getGameEventHandler().getEvents())event.stop();
            server.getGameEventHandler().cleanupEvents();
            for(String name:new String[]{"sqlLoggingThreadPool","sqlThreadPool","onlineMonitorThreadPool"}) {
                Field field=Server.class.getDeclaredField(name);field.setAccessible(true);Object executor=field.get(server);
                if(executor instanceof ExecutorService)((ExecutorService)executor).shutdownNow();
            }
            server.getWorld().getNpcs().clear();server.getWorld().getPlayers().clear();
            Server.serversList.remove(server.getName(),server);
        }
    }
}
