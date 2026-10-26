package com.openrsc.server.combat;

import com.openrsc.server.GameStateUpdater;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import com.openrsc.server.net.rsc.NpcCapacityContract;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.parsers.impl.PayloadCustomParser;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.util.EntityList;
import orsc.NpcSpawnRecord;
import orsc.buffers.RSBuffer_Bits;
import java.lang.reflect.*;
import java.util.*;

/** Offline production World/registry/packet fixture: never starts Server or a listener. */
public final class NpcCapacityProductionFixture {
    static void check(boolean ok,String message) {if(!ok) throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    static List<Packet> packets(Player p) throws Exception {
        Field f=Player.class.getDeclaredField("outgoingPackets");f.setAccessible(true);return (List<Packet>)f.get(p);
    }
    static RSBuffer_Bits buffer(Packet packet) {
        RSBuffer_Bits buffer=new RSBuffer_Bits(packet.getLength());
        packet.getBuffer().getBytes(0,buffer.dataBuffer);buffer.startBitAccess();return buffer;
    }
    static void profileMatrix() {
        for(int version:new int[]{38,39,40,69,115,140,177,204,235,10052,10053}) {
            int capacity=NpcCapacityContract.supportedCapacity(version);
            check(NpcCapacityContract.supports(version,capacity),"supported profile");
            check(!NpcCapacityContract.supports(version,capacity+1),"overbudget profile");
        }
        check(!NpcCapacityContract.supports(10052,16384),"old custom accepted expanded world");
        check(!NpcCapacityContract.supports(235,16384),"classic accepted expanded world");
        check(!NpcCapacityContract.supports(10053,0)&&!NpcCapacityContract.supports(10053,65536),"invalid budget");
    }
    public static void main(String[] args) throws Exception {
        profileMatrix();
        try(CurrentCombatHarness harness=new CurrentCombatHarness()) {
            EntityList<Npc> registry=new EntityList<>(NpcCapacityContract.TARGET_CAPACITY);
            Field worldNpcs=harness.world().getClass().getDeclaredField("npcs");worldNpcs.setAccessible(true);worldNpcs.set(harness.world(),registry);
            harness.server().getConfig().NPC_CAPACITY=NpcCapacityContract.TARGET_CAPACITY;
            harness.openTile(450,450);
            List<Npc> boundary=new ArrayList<>();
            Set<Integer> wanted=new HashSet<>(Arrays.asList(0,2047,4095,4096,8191,16383));
            for(int i=0;i<NpcCapacityContract.TARGET_CAPACITY;i++) {
                Npc npc=new Npc(harness.world(),3,450,450,0);
                harness.world().registerNpc(npc);
                if(wanted.contains(i)) boundary.add(npc);
                check(npc.getIndex()==i,"registration index at "+i);
            }
            check(registry.size()==16384 && registry.getAvailableCapacity()==0,"full registry");
            Player player=harness.player("capacity",451,450);
            player.setClientVersion(10053); player.setClientLimitations(ClientLimitations.forVersion(10053));
            harness.recordOutgoingPackets(player);
            Method setID=Entity.class.getDeclaredMethod("setID",int.class);setID.setAccessible(true);
            Method update=GameStateUpdater.class.getDeclaredMethod("updateNpcs",Player.class,Collection.class);update.setAccessible(true);
            int[] definitions={1023,1024,2047,4096,32768,65535};
            for(int i=0;i<boundary.size();i++) setID.invoke(boundary.get(i),definitions[i]);
            update.invoke(harness.server().getGameUpdater(),player,boundary);
            Packet spawn=packets(player).stream().filter(p->p.getID()==79).findFirst().orElseThrow(()->new AssertionError("missing spawn packet"));
            RSBuffer_Bits input=buffer(spawn);check(input.getBitMask(16)==0,"local header");
            Set<Integer> seen=new HashSet<>();
            for(int i=0;i<boundary.size();i++) {
                NpcSpawnRecord record=NpcSpawnRecord.read(input,spawn.getLength());
                check(record.index==boundary.get(i).getIndex(),"global index round trip");
                check(record.definition==definitions[i],"definition round trip");
                check(record.offsetX==-1 && record.offsetZ==0,"signed offset round trip");
                seen.add(record.index);
            }
            NpcSpawnRecord.requirePadding(input,spawn.getLength());check(seen.size()==6,"index alias");
            // Actual custom interaction parser, retaining independent NPC/player namespaces.
            PayloadCustomParser parser=new PayloadCustomParser();
            int attackOpcode=-1;
            for(int op=0;op<256;op++) if(parser.toOpcodeEnum(new PacketBuilder(op).writeShort(16383).toPacket(),player)==OpcodeIn.NPC_ATTACK) attackOpcode=op;
            check(attackOpcode>=0,"attack opcode");
            TargetMobStruct target=(TargetMobStruct)parser.parse(new PacketBuilder(attackOpcode).writeShort(16383).toPacket(),player);
            check(target.serverIndex==16383 && harness.world().getNpc(target.serverIndex)==boundary.get(5),"high target lookup");
            check(harness.world().getNpc(65535)==null && harness.world().getNpc(-1)==null,"invalid lookup");
            Npc old=boundary.get(5); registry.remove(old);
            Npc replacement=new Npc(harness.world(),3,450,450,0);harness.world().registerNpc(replacement);
            old.setIndex(16383);registry.remove(old);
            check(registry.get(16383)==replacement,"stale removal after high-index reuse");
            // Reader rejects unsupported indexes, byte truncation and nonzero padding.
            for(int bad:new int[]{16384,32767,32768,65535}) {
                Packet p=new PacketBuilder().startBitAccess().writeBits(bad,16).writeBits(0,8).writeBits(0,8).writeBits(0,4).writeBits(3,16).finishBitAccess().toPacket();
                try {NpcSpawnRecord.read(buffer(p),p.getLength());throw new AssertionError("unsupported index accepted");}catch(IllegalArgumentException expected){}
            }
            Packet truncated=new PacketBuilder().writeShort(4096).toPacket();
            try {NpcSpawnRecord.read(buffer(truncated),2);throw new AssertionError("truncation accepted");}catch(IllegalArgumentException expected){}
            System.out.println("PASS production 16384 registrations; boundary spawn/definition encoder-decoder; high attack target; invalid carriers; index reuse; profile matrix");
        }
    }
}
