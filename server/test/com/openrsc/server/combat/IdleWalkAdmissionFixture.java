package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.ConnectionAttachment;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.RSCConnectionHandler;
import com.openrsc.server.plugins.Batch;
import com.openrsc.server.plugins.menu.Menu;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.DefaultAttributeMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real decoded packets -> network filter -> queue -> ordinary parser/processor. No real login. */
public final class IdleWalkAdmissionFixture {
    private static final long EPOCH = 1_700_000_000_000L;
    private static int checks, serial;
    private static boolean candidate;
    private static Object coordinator;
    private static Method poll;
    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
    private static void field(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static final class Peer {
        final Player player;
        final AtomicBoolean open = new AtomicBoolean(true);
        final AtomicBoolean active = new AtomicBoolean(true);
        final AtomicBoolean writable = new AtomicBoolean(true);
        final RSCConnectionHandler handler;
        final ChannelHandlerContext context;
        Peer(CurrentCombatHarness h) throws Exception {
            player = h.player("idle" + serial++, 450, 450);
            // Established idle state, >3s since login/exchange; not evidence of real authentication.
            field(player, "lastExchangeTime", Long.valueOf(0));
            player.setTeleporting(false);
            player.removeAttribute("native_terrain_self_appearance_pending");
            DefaultAttributeMap attributes = new DefaultAttributeMap();
            ConnectionAttachment attachment = new ConnectionAttachment();
            attachment.player.set(player); attachment.canSendSessionId.set(false);
            attributes.attr(RSCConnectionHandler.attachment).set(attachment);
            Channel channel = (Channel)Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isOpen": return open.get();
                    case "isActive": return active.get();
                    case "isWritable": return writable.get();
                    case "remoteAddress": return new InetSocketAddress("127.0.0.11", 40000 + serial);
                    case "attr": return attributes.attr((io.netty.util.AttributeKey)args[0]);
                    case "hasAttr": return attributes.hasAttr((io.netty.util.AttributeKey)args[0]);
                    case "close": open.set(false); return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "isolated-idle-walk-peer";
                    default: return null;
                }
            });
            field(player, "channel", channel);
            context = (ChannelHandlerContext)Proxy.newProxyInstance(ChannelHandlerContext.class.getClassLoader(), new Class<?>[]{ChannelHandlerContext.class},
                (proxy, method, args) -> method.getName().equals("channel") ? channel : null);
            handler = new RSCConnectionHandler(h.server());
        }
        void send(Packet packet) { handler.channelRead(context, packet); }
    }
    private static Packet walk(int opcode, int x, int y) {
        return new Packet(opcode, Unpooled.buffer(4,4).writeShort(x).writeShort(y));
    }
    @SuppressWarnings("unchecked")
    private static List<Packet> queue(Player player) throws Exception {
        return (List<Packet>)CurrentCombatHarness.readPrivateField(player, "incomingPackets");
    }
    private static int admit(CurrentCombatHarness h) throws Exception {
        return candidate ? ((Integer)poll.invoke(coordinator, h.world())).intValue() : 0;
    }
    private static void freshCoordinator() throws Exception {
        if (candidate) {
            Class<?> type = Class.forName("com.openrsc.server.net.rsc.IdleWalkCommandAdmission");
            java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(java.util.function.LongSupplier.class);constructor.setAccessible(true);
            coordinator=constructor.newInstance((java.util.function.LongSupplier)()->0L);poll=type.getMethod("poll",com.openrsc.server.model.world.World.class);
        }
    }
    private static void advanceTick(CurrentCombatHarness h) throws Exception {
        CurrentCombatHarness.invokePrivate(h.server(), "advanceTicks", new Class<?>[]{long.class}, Long.valueOf(1));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("baseline|candidate NEW_RESULT_JSON");
        candidate = args[0].equals("candidate");
        check(candidate || args[0].equals("baseline"), "explicit mode");
        Path output = Paths.get(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Do not overwrite evidence");
        JSONObject result = new JSONObject();
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            check(h.server().getConfig().configFile.equals("myworld.conf"), "isolated current config, no local override");
            check(h.server().getConfig().CLIENT_VERSION == 10059, "current protocol profile");
            check(h.server().getConfig().NPC_CAPACITY == 4000, "explicit compatible NPC capacity");
            h.server().getConfig().PLAYER_BLOCKING = 0;
            h.server().getConfig().MAX_WALKING_SPEED = 1;
            h.openCombatProjectileRectangle(445, 470, 445, 455);
            freshCoordinator();
            result.put("phaseSweep", phases(h));
            if (candidate) {
                barriers(h); approachAndAction(h); ownership(h); trapAndBlink(h); connectionAndProfiles(h); budgets(h); cadence(h);
                result.put("fair64", fairness(h, 64));
                result.put("fair256", fairness(h, 256));
                spamFairness(h); concurrentProducer(h); softBudget(h); flood(h);
            }
            result.put("clientVersion", h.server().getConfig().CLIENT_VERSION)
                .put("npcCapacity", h.server().getConfig().NPC_CAPACITY)
                .put("config", h.server().getConfig().configFile);
        }
        result.put("checks", checks).put("mode", args[0]).put("scope", "Decoded packet admission to authoritative first step; synthetic attached peers, not real login or native input");
        Files.write(output, (result.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS idle-walk admission: " + checks + " checks");
    }
    private static JSONObject phases(CurrentCombatHarness h) throws Exception {
        List<Long> delays = new ArrayList<>(); JSONArray samples = new JSONArray();
        for (int phase=0; phase<640; phase++) {
            h.clock().reset(EPOCH); freshCoordinator();
            Peer peer = new Peer(h);
            h.clock().advanceMillis(phase);
            Packet request = walk(187,451,450); peer.send(request);
            check(queue(peer.player).size()==1 && peer.player.getX()==450, "network only queues intent");
            long first = phase==0 ? 10 : ((phase+9)/10)*10;
            for (long t=first; t<=640; t+=10) {
                h.clock().advanceMillis(EPOCH+t-h.clock().currentTimeMillis());
                if (t==640) peer.player.processIncomingPackets(); else admit(h);
                peer.player.updatePosition();
                if (peer.player.getX()!=450) {
                    check(peer.player.getX()==451 && peer.player.getY()==450, "exact adjacent first destination");
                    check(candidate ? t-phase<=10 : t==640, "admission phase follows chosen policy");
                    delays.add(t-phase); samples.put(new JSONArray().put(phase).put(t-phase)); break;
                }
            }
            h.logout(peer.player);
        }
        check(delays.size()==640, "all 640 command phases produced actual movement"); Collections.sort(delays);
        return new JSONObject().put("count",delays.size()).put("p50Millis",delays.get(319))
            .put("p95Millis",delays.get(607)).put("maxMillis",delays.get(639)).put("samples",samples);
    }
    private static void barriers(CurrentCombatHarness h) throws Exception {
        int[] barriers={16,29,255};
        for (int opcode:barriers) {
            Peer p=new Peer(h); Packet first=opcode==16?walk(16,451,450):new Packet(opcode,Unpooled.buffer(0,0));
            Packet later=walk(187,452,450);p.send(first);p.send(later);
            int reader=first.getBuffer().readerIndex();check(admit(h)==0,"raw barrier stays tick-bound: "+opcode);
            check(queue(p.player).get(0)==first && queue(p.player).get(1)==later && first.getBuffer().readerIndex()==reader,"FIFO identities/reader positions unchanged");
            h.logout(p.player);
        }
        Peer p=new Peer(h); Packet a=walk(187,451,450),b=walk(187,450,451);p.send(a);p.send(b);
        check(admit(h)==2 && queue(p.player).isEmpty(),"two ordinary clicks consumed in order before movement");
        p.player.updatePosition();check(p.player.getX()==450 && p.player.getY()==451,"second click owns final route");h.logout(p.player);
        p=new Peer(h);p.send(new Packet(67,Unpooled.buffer(0,0)));p.send(walk(187,451,450));
        check(admit(h)==2 && queue(p.player).isEmpty(),"bounded ordinary heartbeat then walk");h.logout(p.player);
        for (Packet unusual:new Packet[]{walk(187,32000,450),new Packet(187,Unpooled.buffer(5,5).writeShort(451).writeShort(450).writeByte(0)),new Packet(187,Unpooled.buffer(56,56).writeZero(56))}) {
            p=new Peer(h);p.send(unusual);p.send(walk(187,451,450));check(admit(h)==0 && queue(p.player).get(0)==unusual,"oversized/odd/excess-work walk is a FIFO barrier");h.logout(p.player);
        }
        p=new Peer(h);Packet bounds=new Packet(187,Unpooled.buffer(8,8).writeShort(451).writeShort(450).writeByte(23).writeByte(0).writeByte(-23).writeByte(0));p.send(bounds);
        check(admit(h)==0 && bounds.getBuffer().readerIndex()==0,"aggregate waypoint work, not only first coordinate, is bounded");h.logout(p.player);
    }
    private static void approachAndAction(CurrentCombatHarness h) throws Exception {
        for(boolean actionFirst:new boolean[]{false,true}) {
            Peer p=new Peer(h);
            com.openrsc.server.model.entity.GroundItem drop=new com.openrsc.server.model.entity.GroundItem(
                h.world(),ItemId.COINS.id(),451,450,1,p.player);
            h.world().registerItem(drop);
            try {
                Packet action=new Packet(247,Unpooled.buffer(6,6).writeShort(451).writeShort(450).writeShort(ItemId.COINS.id()));
                Packet movement=walk(actionFirst?187:16,actionFirst?452:451,450);
                if(actionFirst){p.send(action);p.send(movement);}else{p.send(movement);p.send(action);}
                check(admit(h)==0 && queue(p.player).size()==2,"approach/action or earlier item packet is an intact FIFO barrier");
                check(p.player.getCarriedItems().getInventory().countId(ItemId.COINS.id())==0 && !drop.isRemoved(),"no resource action executes early");
                p.player.processIncomingPackets();
                check(queue(p.player).isEmpty(),"ordinary packet path still drains interaction pair");
                check(actionFirst ? p.player.getWalkToAction()==null : p.player.getWalkToAction()!=null,
                    "normal FIFO either cancels earlier pickup or retains approach action");
                check(!drop.isRemoved(),"packet admission alone never executes deferred pickup");
            } finally {h.world().unregisterItem(drop);h.logout(p.player);}
        }
        Peer p=new Peer(h);Packet first=walk(187,451,450),barrier=new Packet(29,Unpooled.buffer(0,0)),last=walk(187,450,451);
        p.send(first);p.send(barrier);p.send(last);
        check(admit(h)==1 && queue(p.player).get(0)==barrier && queue(p.player).get(1)==last,
            "eligible prefix stops exactly at barrier without coalescing past it");h.logout(p.player);
        p=new Peer(h);Packet full=new Packet(187,Unpooled.buffer(54,54).writeShort(451).writeShort(450).writeZero(50));p.send(full);
        check(admit(h)==1 && queue(p.player).isEmpty(),"shipped25-pair normal payload shape stays eligible when bounded");h.logout(p.player);
    }
    private static void ownership(CurrentCombatHarness h) throws Exception {
        List<Consumer<Player>> states=new ArrayList<>();
        states.add(p->p.setBusy(true));states.add(p->p.setMenu(new Menu()));states.add(p->p.setAccessingBank(true));
        states.add(p->p.getTrade().setTradeActive(true));states.add(p->p.getDuel().setDuelActive(true));
        states.add(p->p.setTeleporting(true));states.add(p->p.setAttribute("auctionhouse",true));
        states.add(p->p.setAttribute(com.openrsc.server.model.action.ConfiguredAgilityAction.PENDING_ATTRIBUTE,new Object()));
        states.add(p->p.setDropItemEvent(0,new Item(ItemId.COINS.id(),1)));
        states.add(p->p.setTalkToNpcEvent(p));
        for (Consumer<Player> state:states) {
            Peer p=new Peer(h);state.accept(p.player);Packet request=walk(187,451,450);p.send(request);
            check(admit(h)==0 && queue(p.player).get(0)==request && p.player.finishedPath(),"active owner is never reset between ticks");h.logout(p.player);
        }
        for(boolean incoming:new boolean[]{false,true}) {
            Peer p=new Peer(h);p.player.getCache().store("setting_auto_retaliate",false);
            com.openrsc.server.model.entity.npc.Npc enemy=h.npc(com.openrsc.server.constants.NpcId.MOSS_GIANT.id(),451,450);
            try {
                if(incoming)enemy.startCombat(p.player);else p.player.startCombat(enemy);
                Object attack=incoming?enemy.getPvmMeleeEvent():p.player.getPvmMeleeEvent();
                check(attack!=null,"real directional combat owner precondition");p.send(walk(187,450,451));
                check(admit(h)==0 && queue(p.player).size()==1,"incoming/outgoing combat retains normal-tick cancellation");
                check(attack==(incoming?enemy.getPvmMeleeEvent():p.player.getPvmMeleeEvent()),"admission inspection does not stop an attack owner");
            } finally {h.logout(p.player);h.world().unregisterNpc(enemy);}
        }
        {
            Peer p=new Peer(h);
            com.openrsc.server.event.rsc.PluginTask task=new com.openrsc.server.event.rsc.PluginTask(
                h.world(),p.player,"idle",new Object[0]) { public int action(){throw new AssertionError("must not execute");} };
            p.player.addOwnedPlugin(task);p.send(walk(187,451,450));
            check(admit(h)==0 && !Boolean.TRUE.equals(task.getScriptContext().getInterrupted()),"registered plugin owner is neither interrupted nor executed early");
            p.player.removeOwnedPlugin(task);h.logout(p.player);
        }
        {
            Peer p=new Peer(h);com.openrsc.server.model.entity.npc.Npc target=h.npc(com.openrsc.server.constants.NpcId.MOSS_GIANT.id(),451,450);
            com.openrsc.server.model.combat.AttackIntent pending=p.player.getAttackTransaction().issue(target,
                com.openrsc.server.model.combat.CombatStyle.MELEE,com.openrsc.server.model.combat.AttackIntent.Channel.MELEE,
                com.openrsc.server.model.combat.AttackIntent.Source.MANUAL,null);
            p.send(walk(187,450,451));check(admit(h)==0 && p.player.getAttackTransaction().getPending()==pending,
                "pending attack intent is preserved even before an approach/combat event exists");
            h.logout(p.player);h.world().unregisterNpc(target);
        }
        Peer p=new Peer(h);Batch batch=new Batch(p.player);batch.initialize(5);field(p.player,"batch",batch);p.send(walk(187,451,450));
        check(admit(h)==0 && !batch.isComplete(),"batch progress not interrupted early");h.logout(p.player);
        p=new Peer(h);p.player.getSkills().setLevel(Skill.HITS.id(),0,false);p.send(walk(187,451,450));check(admit(h)==0,"dead actor never admits walking");h.logout(p.player);
    }
    private static void trapAndBlink(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);Peer p=new Peer(h);
        com.openrsc.server.model.entity.npc.Npc demon=h.npc(com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.NPC_ID,449,450);
        try {
            com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.onDamage(demon,p.player,1);
            h.clock().advanceMillis(com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.TRAP_MILLIS-1);
            check(com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.actionsBlocked(p.player)&&p.player.canLogout(),"real trap independently blocks actions near its expiry");
            Packet intent=walk(187,451,450);p.send(intent);
            check(admit(h)==0&&queue(p.player).get(0)==intent,"early intake must defer, not silently consume, a near-expiry trapped walk");
            h.clock().advanceMillis(1);p.player.processIncomingPackets();p.player.updatePosition();
            check(p.player.getX()==451&&p.player.getY()==450,"original normal tick can admit the retained intent after trap expiry");
        }finally{h.logout(p.player);h.world().unregisterNpc(demon);}
        h.clock().reset(EPOCH);p=new Peer(h);p.player.setAttribute("blink",true);p.send(walk(187,453,450));
        check(admit(h)==0&&queue(p.player).size()==1,"blink teleports stay on normal command admission");
        p.player.processIncomingPackets();p.player.updatePosition();check(p.player.getX()==453,"normal blink still performs its existing teleport");h.logout(p.player);
    }
    private static void connectionAndProfiles(CurrentCombatHarness h) throws Exception {
        for(int state=0;state<3;state++) {
            Peer p=new Peer(h);p.send(walk(187,451,450));
            if(state==0)p.active.set(false);else if(state==1)p.writable.set(false);else p.open.set(false);
            check(admit(h)==0 && queue(p.player).size()==1,"inactive/unwritable/closed channel keeps queue untouched");h.logout(p.player);
        }
        Peer p=new Peer(h);p.player.setClientVersion(10052);p.send(walk(187,451,450));
        check(admit(h)==0,"older custom protocol stays on ordinary admission");h.logout(p.player);
        for(int state=0;state<2;state++) {
            p=new Peer(h);p.send(walk(187,451,450));
            if(state==0)h.server().getConfig().WANT_MYWORLD=false;else h.server().getConfig().WANT_CUSTOM_WALK_SPEED=false;
            check(admit(h)==0 && queue(p.player).size()==1,"legacy/noncustom walking stays world-tick bound");
            h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_CUSTOM_WALK_SPEED=true;h.logout(p.player);
        }
    }
    private static void budgets(CurrentCombatHarness h) throws Exception {
        Peer p=new Peer(h);for(int i=0;i<12;i++)p.send(walk(187,451,450));
        int direct=((Integer)Player.class.getMethod("processIdleWalkCommands",int.class).invoke(p.player,100)).intValue();
        check(direct==2 && queue(p.player).size()==10,"per-player poll bound2 even with oversized caller allowance");
        for(int i=0;i<3;i++)check(admit(h)==2,"remaining per-tick allowance");
        check(admit(h)==0 && queue(p.player).size()==4,"per-player world-tick bound8");
        advanceTick(h);check(admit(h)==2 && queue(p.player).size()==2,"new world tick renews allowance");
        p.player.processIncomingPackets();check(queue(p.player).isEmpty(),"ordinary world tick still drains budget remainder");h.logout(p.player);
    }
    private static void cadence(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);Peer p=new Peer(h);p.send(walk(187,451,450));admit(h);p.player.updatePosition();int x=451,steps=1;
        for(int t=10;t<=1720;t+=10) {
            h.clock().advanceMillis(10);if(t%20==0)p.send(walk(187,x+1,450));
            if(t%640==0){advanceTick(h);p.player.processIncomingPackets();}else admit(h);
            p.player.updatePosition();
            if(p.player.getX()!=x){check(t%430==0,"packet reclick cannot bypass430ms walking cadence");check(p.player.getX()==x+1,"one tile per step");x=p.player.getX();steps++;}
        }
        check(steps==5&&x==455,"real packet intake keeps exact elapsed movement distance");h.logout(p.player);
    }
    private static JSONObject fairness(CurrentCombatHarness h,int n) throws Exception {
        freshCoordinator();List<Peer> peers=new ArrayList<>();for(int i=0;i<n;i++){Peer p=new Peer(h);p.send(walk(187,451,450));peers.add(p);}
        int total=0,polls=0;while(total<n&&polls<100){int got=admit(h);check(got<=8,"global poll bound8");total+=got;polls++;}
        check(total==n&&polls==(n+7)/8,"rotating fairness serves every queued peer within budget bound");
        for(Peer p:peers){check(queue(p.player).isEmpty(),"every client served");h.logout(p.player);}
        return new JSONObject().put("clients",n).put("polls",polls).put("maxSyntheticPollWaitMillis",polls*10);
    }
    private static void spamFairness(CurrentCombatHarness h) throws Exception {
        freshCoordinator();Peer noisy=new Peer(h);for(int i=0;i<40;i++)noisy.send(walk(187,451,450));
        Peer barrier=new Peer(h);Packet unknown=new Packet(255,Unpooled.buffer(0,0));barrier.send(unknown);
        List<Peer> ordinary=new ArrayList<>();for(int i=0;i<64;i++){Peer p=new Peer(h);p.send(walk(187,451,450));ordinary.add(p);}
        for(int i=0;i<12;i++)check(admit(h)<=8,"spam still respects global allowance");
        for(Peer p:ordinary){check(queue(p.player).isEmpty(),"noisy/barrier peers cannot starve ordinary clients");h.logout(p.player);}
        check(queue(noisy.player).size()==32,"noisy peer cannot exceed eight early packets per world tick");
        check(queue(barrier.player).size()==1&&queue(barrier.player).get(0)==unknown,"fair rotation never consumes a raw barrier");
        noisy.player.processIncomingPackets();check(queue(noisy.player).isEmpty(),"ordinary world tick drains noisy remainder without new restrictions");
        h.logout(noisy.player);h.logout(barrier.player);
    }
    private static void concurrentProducer(CurrentCombatHarness h) throws Exception {
        Peer p=new Peer(h);List<Packet> sent=new ArrayList<>();
        Thread producer=new Thread(()->{for(int i=0;i<50;i++){Packet packet=walk(187,451,450);sent.add(packet);p.send(packet);Thread.yield();}},"isolated-packet-producer");
        producer.start();int consumed=0;while(producer.isAlive()){consumed+=admit(h);Thread.yield();}producer.join();consumed+=admit(h);
        check(consumed<=8&&consumed+queue(p.player).size()==50,"concurrent enqueue conserves all packets within tick budget");
        for(int i=0;i<queue(p.player).size();i++)check(queue(p.player).get(i)==sent.get(consumed+i),"concurrent producer retains exact FIFO suffix identities");
        p.player.processIncomingPackets();check(queue(p.player).isEmpty(),"normal drain finishes concurrent suffix");
        check(((List<?>)CurrentCombatHarness.readPrivateField(p.player,"activePackets")).isEmpty(),"shared processing retains active-opcode bookkeeping");h.logout(p.player);
    }
    private static void softBudget(CurrentCombatHarness h) throws Exception {
        Class<?> type=Class.forName("com.openrsc.server.net.rsc.IdleWalkCommandAdmission");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(java.util.function.LongSupplier.class);constructor.setAccessible(true);
        for(long origin:new long[]{0L,-1_000_000_000L,Long.MAX_VALUE-1_000_000L}) {
            java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong(origin);
            coordinator=constructor.newInstance((java.util.function.LongSupplier)()->clock.getAndAdd(3_000_000L));
            Peer a=new Peer(h),b=new Peer(h);Packet first=walk(187,451,450),remainder=walk(187,450,451);
            a.send(first);a.send(remainder);b.send(walk(187,451,450));
            check(admit(h)==1 && queue(a.player).size()==1 && queue(a.player).get(0)==remainder,
                "soft budget stops between actual packets with FIFO suffix retained across arbitrary clock origin");
            check(admit(h)==1 && queue(b.player).isEmpty(),"soft budget rotates to another peer before returning to noisy head");
            a.player.processIncomingPackets();a.player.updatePosition();
            check(a.player.getX()==450&&a.player.getY()==451,"ordinary tick drains budget remainder using normal final-intent order");
            h.logout(a.player);h.logout(b.player);
        }
        for(int n:new int[]{64,256}) {
            java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();
            coordinator=constructor.newInstance((java.util.function.LongSupplier)()->clock.getAndAdd(3_000_000L));
            List<Peer> peers=new ArrayList<>();for(int i=0;i<n;i++){Peer p=new Peer(h);p.send(walk(187,451,450));peers.add(p);}
            for(int i=0;i<n;i++)check(admit(h)==1,"exhausted soft budget guarantees exactly one eligible command's progress");
            for(Peer p:peers){check(queue(p.player).isEmpty(),"one-command budget retains rotating fairness for every client");h.logout(p.player);}
        }
        java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        coordinator=constructor.newInstance((java.util.function.LongSupplier)()->{reads.incrementAndGet();return 0L;});
        check(admit(h)==0&&reads.get()==0,"empty registry does not read budget clock");
        freshCoordinator();
    }
    private static void flood(CurrentCombatHarness h) throws Exception {
        Peer p=new Peer(h);int prior=h.server().getConfig().MAX_PACKETS_PER_SECOND;
        h.server().getConfig().MAX_PACKETS_PER_SECOND=3;
        for(int i=0;i<4;i++)p.send(walk(187,451,450));
        check(!p.open.get()&&queue(p.player).size()==3,"unchanged upstream filter rejects fourth packet and closes channel");
        check(admit(h)==0,"closed filtered channel never drains early");h.server().getConfig().MAX_PACKETS_PER_SECOND=prior;h.logout(p.player);
    }
}
