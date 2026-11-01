package com.openrsc.server.combat;

import com.openrsc.server.GameStateUpdater;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import java.lang.reflect.*;
import java.util.*;
import orsc.*;
import orsc.buffers.RSBuffer_Bits;

/** Offline real server encoders and real client handlers; no UI, sockets or database. */
public final class LayeredActorSceneFixture {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    static void set(Object target, String name, Object value) throws Exception { field(target.getClass(), name).set(target, value); }
    static Object construct(String name) throws Exception {
        Constructor<?> c = Class.forName(name).getDeclaredConstructor(); c.setAccessible(true); return c.newInstance();
    }
    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(target, args); }
        catch (InvocationTargetException e) { throw (Exception)e.getCause(); }
    }
    static sun.misc.Unsafe unsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return (sun.misc.Unsafe)f.get(null);
    }
    static mudclient client() throws Exception {
        mudclient c = (mudclient)unsafe().allocateInstance(mudclient.class);
        for (String name : new String[]{"npcsServer", "npcs", "npcsCache", "players", "knownPlayers", "playerServer"})
            set(c, name, new ORSCharacter[16384]);
        for (String name : new String[]{"npcVisualOffsetX", "npcVisualOffsetZ", "customNpcMovementTargetWorldX", "customNpcMovementTargetWorldZ", "customNpcMovementTargetDirection"})
            set(c, name, new int[16384]);
        set(c, "customNpcMovementTargetMillis", new long[16384]);
        set(c, "customNpcMovementTargetValid", new boolean[16384]);
        set(c, "customNpcMovementTargetResult", new String[16384]);
        set(c, "groundItemAreaEpoch", construct("orsc.GroundItemAreaEpoch"));
        set(c, "sceneInstanceStore", construct("orsc.ClientSceneInstanceStore"));
        set(c, "predictiveTerrainPreloader", construct("orsc.PredictiveTerrainPreloader"));
        set(c, "layeredScenePresentationLatch", construct("orsc.LayeredScenePresentationLatch"));
        set(c, "cachedResidentObjectChunks", new HashMap<>());
        set(c, "cachedResidentObjectChunkCurrentViewCells", new HashSet<>());
        set(c, "cachedResidentObjectChunkPreviousViewCells", new HashSet<>());
        set(c, "staticPresentationSceneryRecords", Collections.emptyList());
        set(c, "staticPresentationWallRecords", Collections.emptyList());
        set(c, "world", new orsc.graphics.three.World(null,null));
        set(c, "deathScreenTimeout", 1); // Renderer boundary: do not build/draw a region in a wire test.
        set(c, "tileSize", 128);
        set(c, "localPlayer", new ORSCharacter());
        return c;
    }
    @SuppressWarnings("unchecked") static List<Packet> packets(Player p) throws Exception { return (List<Packet>)get(p,"outgoingPackets"); }
    static void deliver(PacketHandler h, Packet p, String method) throws Exception {
        RSBuffer_Bits b = h.getPacketsIncoming();
        b.dataBuffer = new byte[p.getLength()+1]; b.dataBuffer[0] = (byte)p.getID();
        p.getBuffer().getBytes(0,b.dataBuffer,1,p.getLength()); b.packetEnd = 1;
        call(h, method, new Class<?>[]{int.class}, p.getLength()+1);
    }
    static Packet take(Player p, int id) throws Exception {
        for (Packet packet : packets(p)) if (packet.getID() == id) return packet;
        throw new AssertionError("missing packet " + id);
    }
    static void context(GameStateUpdater u, Player p, PacketHandler h) throws Exception {
        packets(p).clear();
        check((Boolean)call(u,"ensureLayeredSceneContext",new Class<?>[]{Player.class},p),"context not ready");
        deliver(h,take(p,157),"updateLayeredSceneContext");
    }
    static void npcs(GameStateUpdater u, Player p, PacketHandler h, Npc... visible) throws Exception {
        packets(p).clear();
        call(u,"updateNpcs",new Class<?>[]{Player.class,Collection.class},p,Arrays.asList(visible));
        deliver(h,take(p,79),"showNPCs");
    }
    public static void main(String[] args) throws Exception {
        com.openrsc.client.entityhandling.EntityHandler.load(true);
        try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
            harness.server().getConfig().WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY = true;
            Player p = harness.player("sceneviewer",450,600);
            harness.recordOutgoingPackets(p);
            GameStateUpdater u = harness.server().getGameUpdater();
            mudclient c = client(); PacketHandler h = new PacketHandler(c);
            orsc.net.Network_Socket transport = (orsc.net.Network_Socket)unsafe().allocateInstance(orsc.net.Network_Socket.class);
            transport.bufferBits = new RSBuffer_Bits(65536); h.setClientStream(transport);
            set(c,"packetHandler",h); set(c,"localPlayerServerIndex",p.getIndex());
            context(u,p,h);
            Npc old = harness.npc(3,451,600);
            npcs(u,p,h,old);
            check(c.getNpcCount()==1,"surface NPC baseline missing");
            ORSCharacter previous = c.getNpc(0);
            previous.projectileRange=40;
            p.setLayeredLocation(com.openrsc.server.model.world.coordinate.WorldLocation.global(
                new com.openrsc.server.model.world.coordinate.WorldCoordinate(450,600,-1)),true);
            context(u,p,h);
            check(c.getNpcCount()==0&&c.getNpcFromServer(old.getIndex())==null,"real scope reset retained NPC");
            npcs(u,p,h);
            check(c.getNpcCount()==0,"empty signed-level scene not empty");
            p.setLayeredLocation(com.openrsc.server.model.world.coordinate.WorldLocation.global(
                new com.openrsc.server.model.world.coordinate.WorldCoordinate(450,600,0)),true);
            context(u,p,h); npcs(u,p,h,old);
            check(c.getNpcCount()==1&&c.getNpc(0)!=previous&&c.getNpc(0).projectileRange==0,"return reused old NPC lifetime");
            System.out.println("PASS real context/NPC encoder-handler signed-level populated/empty/return replay");
        }
    }
}
