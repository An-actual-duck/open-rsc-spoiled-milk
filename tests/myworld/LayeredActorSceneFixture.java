package com.openrsc.server.combat;

import com.openrsc.server.GameStateUpdater;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.struct.incoming.LayeredTerrainReadyStruct;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
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
        set(c, "playerSkinColors", new int[64]);
        return c;
    }
    @SuppressWarnings("unchecked") static List<Packet> packets(Player p) throws Exception { return (List<Packet>)get(p,"outgoingPackets"); }
    static void input(PacketHandler h, Packet p) throws Exception {
        RSBuffer_Bits b = h.getPacketsIncoming();
        b.dataBuffer = new byte[p.getLength()+1]; b.dataBuffer[0] = (byte)p.getID();
        p.getBuffer().getBytes(0,b.dataBuffer,1,p.getLength()); b.packetEnd = 1;
    }
    static void deliver(PacketHandler h, Packet p, String method) throws Exception {
        input(h,p); h.handlePacket(p.getID(),p.getLength()+1);
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
    static void players(GameStateUpdater u, Player p, PacketHandler h, Player... visible) throws Exception {
        packets(p).clear();
        call(u,"updatePlayers",new Class<?>[]{Player.class,Collection.class},p,Arrays.asList(visible));
        deliver(h,take(p,191),"showOtherPlayers");
        u.updatePlayerAppearances(p);
        for (Packet packet : packets(p)) if (packet.getID()==234) {
            input(h,packet); h.handlePacket(packet.getID(),packet.getLength()+1);
        }
    }
    static WorldLocation location(int x, int level) { return WorldLocation.global(new WorldCoordinate(x,600,level)); }
    static void move(Player p, int x, int level) {
        p.setLayeredLocation(location(x,level),true); p.setTeleporting(false); p.setHasMoved(false);
    }
    static void move(Npc n, int x, int level) {
        n.teleport(location(x,level)); n.setTeleporting(false); n.setHasMoved(false);
    }
    static void ready(GameStateUpdater u, Player p, PacketHandler h) throws Exception {
        Object state=get(h,"layeredSceneContextState");
        NativeLayeredTerrainSnapshot terrain=(NativeLayeredTerrainSnapshot)call(state,"getNativeTerrainSnapshot",new Class<?>[0]);
        LayeredTerrainReadyStruct receipt=new LayeredTerrainReadyStruct();
        receipt.protocolVersion=1;
        receipt.contextSequence=(Integer)call(state,"getSequence",new Class<?>[0]);
        receipt.worldSpace="global"; receipt.logicalLevel=terrain.getLevel();
        receipt.centerSectorX=terrain.getCurrentChunkX(); receipt.centerSectorY=terrain.getCurrentChunkY();
        receipt.manifestSha256=terrain.getManifestSha256(); u.acceptLayeredTerrainReady(p,receipt);
    }
    public static void main(String[] args) throws Exception {
        com.openrsc.client.entityhandling.EntityHandler.load(true);
        try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
            harness.server().getConfig().WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY = true;
            Player p = harness.player("sceneviewer",450,600);
            p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0,0,0,0,1,2));
            harness.recordOutgoingPackets(p);
            GameStateUpdater u = harness.server().getGameUpdater();
            mudclient c = client(); PacketHandler h = new PacketHandler(c);
            orsc.net.Network_Socket transport = (orsc.net.Network_Socket)unsafe().allocateInstance(orsc.net.Network_Socket.class);
            transport.bufferBits = new RSBuffer_Bits(65536); h.setClientStream(transport);
            set(c,"packetHandler",h); set(c,"localPlayerServerIndex",p.getIndex());
            context(u,p,h);
            ready(u,p,h);
            Npc old = harness.npc(3,451,600);
            npcs(u,p,h,old);
            check(c.getNpcCount()==1,"surface NPC baseline missing");
            ORSCharacter previous = c.getNpc(0);
            previous.projectileRange=40;
            Player peer=harness.player("scenepeer",451,600); peer.setTeleporting(false);
            peer.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0,0,0,0,1,2));
            players(u,p,h,peer);
            check("Scenepeer".equalsIgnoreCase(c.getPlayerFromServer(peer.getIndex()).accountName),"initial peer appearance absent");
            check(!p.requiresAppearanceUpdateForPeek(peer),"peer appearance was not cached");
            move(p,450,-1);
            context(u,p,h);
            check(c.getNpcCount()==0&&c.getNpcFromServer(old.getIndex())==null,"real scope reset retained NPC");
            npcs(u,p,h);
            check(c.getNpcCount()==0,"empty signed-level scene not empty");
            players(u,p,h);
            check(c.getPlayerCount()==1,"empty scene retained old peer");
            move(p,450,0);
            context(u,p,h); npcs(u,p,h,old);
            check(c.getNpcCount()==1&&c.getNpc(0)!=previous&&c.getNpc(0).projectileRange==0,"return reused old NPC lifetime");
            players(u,p,h,peer); ready(u,p,h);
            ORSCharacter oldPeer=c.getPlayerFromServer(peer.getIndex());
            peer.getUpdateFlags().setAppearanceChanged(false);
            move(p,450,-37); move(peer,451,-37); move(old,451,-37);
            context(u,p,h);
            check(p.requiresAppearanceUpdateForPeek(peer),"scope change did not invalidate known peer appearance");
            npcs(u,p,h,old); players(u,p,h,peer); ready(u,p,h);
            check(c.getNpcCount()==1&&c.getPlayerCount()==2,"populated signed level lost actors");
            check(c.getPlayerFromServer(peer.getIndex())!=oldPeer
                && "Scenepeer".equalsIgnoreCase(c.getPlayerFromServer(peer.getIndex()).accountName),"new lifetime peer appearance not resent");
            ORSCharacter retainedNpc=c.getNpc(0), retainedPeer=c.getPlayerFromServer(peer.getIndex());
            // Force a terrain-only context replacement while actor identity remains current.
            p.removeAttribute("layered_scene_context_scope");
            context(u,p,h); npcs(u,p,h,old); players(u,p,h,peer); ready(u,p,h);
            check(c.getNpc(0)==retainedNpc&&c.getPlayerFromServer(peer.getIndex())==retainedPeer
                &&!p.requiresAppearanceUpdateForPeek(peer),"same-scope refresh discarded actor baseline");
            // An adjacent native center is a terrain generation change, not an actor lifetime.
            move(p,480,-37); move(peer,481,-37); move(old,481,-37);
            context(u,p,h); npcs(u,p,h,old); players(u,p,h,peer); ready(u,p,h);
            check(c.getNpc(0)==retainedNpc&&c.getPlayerFromServer(peer.getIndex())==retainedPeer,"chunk shift discarded actor identity");
            // Lose client residency, then receive a real reference-only context.
            // The server's next actor deltas are emitted but quarantined by the real handler.
            call(get(h,"nativeLayeredTerrainResidentCache"),"clear",new Class<?>[0]);
            p.removeAttribute("layered_scene_context_scope");
            context(u,p,h);
            check((Boolean)call(get(h,"nativeTerrainRecovery"),"hasContextGap",new Class<?>[0]),"missing reference did not open context gap");
            npcs(u,p,h,old); players(u,p,h,peer);
            check((Integer)get(h,"quarantinedSceneDeltaCount")>=2,"real actor deltas were not quarantined");
            LayeredTerrainReadyStruct resync=new LayeredTerrainReadyStruct(); resync.protocolVersion=2;
            u.acceptLayeredTerrainReady(p,resync);
            context(u,p,h);
            check(p.getLocalNpcs().isEmpty()&&p.getLocalPlayers().isEmpty()
                &&p.requiresAppearanceUpdateForPeek(peer),"recovery retained server actor baseline/appearance IDs");
            npcs(u,p,h,old); players(u,p,h,peer); ready(u,p,h);
            check(c.getNpcCount()==1&&c.getPlayerCount()==2
                &&"Scenepeer".equalsIgnoreCase(c.getPlayerFromServer(peer.getIndex()).accountName),"recovery baseline did not restore actors/appearance");
            System.out.println("PASS real context/NPC/player/appearance signed-level empty/populated/return, scope refresh and chunk shift replay");
            System.out.println("PASS real missing-reference context gap, quarantined actor deltas and complete recovery baseline");
        }
    }
}
