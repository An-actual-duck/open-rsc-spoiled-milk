package com.openrsc.server.combat;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.IdleWalkCommandAdmission;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** CPU cost of the additional intake ONLY; not a complete Server.run/tick benchmark. */
public final class IdleWalkAdmissionCostFixture {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path output=Paths.get(args[0]);if(Files.exists(output))throw new IllegalArgumentException("Fresh output required");
        JSONObject result=new JSONObject().put("scope","Additional intake only; excludes enqueue/network, ordinary packet drain, movement, world tick and rendering")
            .put("timer","System.nanoTime").put("warmupPolls",256).put("measuredPolls",1024);
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            if(h.server().getConfig().CLIENT_VERSION!=10059||h.server().getConfig().NPC_CAPACITY!=4000)throw new IllegalStateException("Current isolated synthetic fixture required");
            h.server().getConfig().PLAYER_BLOCKING=0;
            for(int n:new int[]{64,256}) {
                List<Player> players=new ArrayList<>();List<List<Packet>> queues=new ArrayList<>();
                for(int i=0;i<n;i++) {
                    Player p=h.player("cost"+i,450,450);p.setTeleporting(false);p.removeAttribute("native_terrain_self_appearance_pending");
                    Field age=Player.class.getDeclaredField("lastExchangeTime");age.setAccessible(true);age.setLong(p,0);
                    h.recordOutgoingPackets(p);players.add(p);queues.add((List<Packet>)CurrentCombatHarness.readPrivateField(p,"incomingPackets"));
                }
                for(boolean saturated:new boolean[]{false,true}) {
                    IdleWalkCommandAdmission lane=new IdleWalkCommandAdmission();long[] timings=new long[1024];int processed=0;
                    for(int poll=0;poll<1280;poll++) {
                        if(poll%64==0)CurrentCombatHarness.invokePrivate(h.server(),"advanceTicks",new Class<?>[]{long.class},Long.valueOf(1));
                        if(saturated)for(int i=0;i<n;i++)if(queues.get(i).isEmpty())for(int packet=0;packet<2;packet++)
                            players.get(i).addToPacketQueue(new Packet(187,Unpooled.buffer(4,4).writeShort(474).writeShort(450)));
                        // Enqueue/setup is outside this timed admission-only region.
                        long start=System.nanoTime();int count=lane.poll(h.world());long nanos=System.nanoTime()-start;
                        if(count>8 || (saturated&&count!=8))throw new AssertionError("Bounded saturated intake must process exactly8");
                        if(saturated&&poll==255)for(Player p:players)
                            if(p.getWalkingQueue().path==null||p.getWalkingQueue().path.size()!=24
                                ||!com.openrsc.server.model.PathValidation.checkExistingPath(p,p.getWalkingQueue().path))
                                throw new AssertionError("Saturated control must retain24 genuinely traversable steps");
                        if(poll>=256){timings[poll-256]=nanos;processed+=count;}
                        h.clock().advanceMillis(10);
                    }
                    long[] sorted=timings.clone();Arrays.sort(sorted);JSONArray raw=new JSONArray();for(long t:timings)raw.put(t);
                    result.put((saturated?"saturated":"idle")+n,new JSONObject().put("clients",n).put("processedPackets",processed)
                        .put("precheckDistanceBoundTiles",saturated?24:0).put("verifiedTraversableQueuedSteps",saturated?24:0).put("p50Nanos",sorted[511]).put("p95Nanos",sorted[972]).put("p99Nanos",sorted[1013])
                        .put("maxNanos",sorted[1023]).put("samplesNanos",raw));
                    for(Player p:players)p.processIncomingPackets();
                }
                for(Player p:players)h.logout(p);
            }
        }
        Files.write(output,(result.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS bounded additional-intake CPU receipt");
    }
}
