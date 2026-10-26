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
        check(!NpcCapacityContract.supportsWorld(10052,10053,4000),"old custom admitted to expanded wire profile");
        check(!NpcCapacityContract.supportsWorld(235,10053,4000),"classic admitted to expanded wire profile");
        check(NpcCapacityContract.supportsWorld(235,235,4000),"classic compatible profile refused");
        NpcCapacityContract.requireStartupHeadroom(10053,512);
        NpcCapacityContract.requireStartupHeadroom(235,0);
        try {NpcCapacityContract.requireStartupHeadroom(10053,511);throw new AssertionError("insufficient dynamic headroom accepted");}
        catch(IllegalStateException expected){}
        check(!NpcCapacityContract.supports(10053,0)&&!NpcCapacityContract.supports(10053,65536),"invalid budget");
    }
    public static void main(String[] args) throws Exception {
        profileMatrix();
        com.openrsc.client.entityhandling.EntityHandler.load(true);
        com.openrsc.client.entityhandling.defs.NPCDef lowDefinition=com.openrsc.client.entityhandling.EntityHandler.getNpcDef(3);
        for(int id:new int[]{1024,32768,65535}) {
            com.openrsc.client.entityhandling.defs.NPCDef definition=new com.openrsc.client.entityhandling.defs.NPCDef(
                "synthetic", "wire fixture", "",1,1,3,1,false,new int[12],0,0,0,0,145,220,6,6,5,id);
            com.openrsc.client.entityhandling.EntityHandler.setRuntimeNpcDefinition(id,definition);
            check(com.openrsc.client.entityhandling.EntityHandler.getNpcDef(id)==definition,"high client definition identity");
        }
        check(com.openrsc.client.entityhandling.EntityHandler.getNpcDef(3)==lowDefinition,"high definition replaced low identity");
        orsc.ORSCharacter npcOrigin=new orsc.ORSCharacter(),playerOrigin=new orsc.ORSCharacter();
        npcOrigin.attackingNpcServerIndex=16383;npcOrigin.projectileRange=40;
        playerOrigin.attackingNpcServerIndex=-1;playerOrigin.attackingPlayerServerIndex=16383;playerOrigin.projectileRange=40;
        orsc.NpcProjectileReferences.clear(new orsc.ORSCharacter[]{npcOrigin,playerOrigin,null},3,16383);
        check(npcOrigin.projectileRange==0&&npcOrigin.attackingNpcServerIndex==-1,"projectile retained removed NPC lifetime");
        check(playerOrigin.projectileRange==40,"NPC invalidation aliased player namespace");
        try(CurrentCombatHarness harness=new CurrentCombatHarness()) {
            boolean enforceBefore=harness.server().getConfig().ENFORCE_CUSTOM_CLIENT_VERSION;
            for(boolean enforce:new boolean[]{false,true}) {
                harness.server().getConfig().ENFORCE_CUSTOM_CLIENT_VERSION=enforce;
                for(int version:new int[]{235,10052}) {
                    com.openrsc.server.login.LoginRequest request=new com.openrsc.server.login.LoginRequest(
                        harness.server(),"wirefixture","127.0.0.1",version) {
                        public void loginValidated(int response) {}
                        public void loadingComplete(Player loadedPlayer) {}
                    };
                    check(request.validateLogin().responseCode==com.openrsc.server.util.rsc.LoginResponse.CLIENT_UPDATED,"real login gate admitted incompatible profile");
                }
            }
            harness.server().getConfig().ENFORCE_CUSTOM_CLIENT_VERSION=enforceBefore;
            EntityList<Npc> registry=harness.world().getNpcs();
            check(harness.server().getConfig().NPC_CAPACITY==NpcCapacityContract.TARGET_CAPACITY
                && registry.getAvailableCapacity()==NpcCapacityContract.TARGET_CAPACITY,"production startup registry budget");
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
            Npc failed=new Npc(harness.world(),3,450,450,0);
            try {harness.world().registerNpc(failed);throw new AssertionError("overfull spawn accepted");}
            catch(IllegalStateException expected){}
            check(failed.isRemoved()&&failed.getIndex()==-1&&!failed.getStatRestorationEvent().isRunning(),"failed spawn left membership/event");
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
            for(OpcodeIn opcode:new OpcodeIn[]{OpcodeIn.NPC_COMMAND,OpcodeIn.NPC_COMMAND2,OpcodeIn.NPC_TALK_TO,OpcodeIn.NPC_USE_ITEM,OpcodeIn.CAST_ON_NPC}) {
                int numeric=-1;int size=opcode==OpcodeIn.NPC_USE_ITEM||opcode==OpcodeIn.CAST_ON_NPC?4:2;
                for(int op=0;op<256;op++)if(parser.toOpcodeEnum(new PacketBuilder(op).write(new byte[size]).toPacket(),player)==opcode)numeric=op;
                check(numeric>=0,"missing interaction opcode "+opcode);
                PacketBuilder request=new PacketBuilder(numeric);
                if(opcode==OpcodeIn.CAST_ON_NPC)request.writeShort(0).writeShort(16383);
                else {request.writeShort(16383);if(size==4)request.writeShort(0);}
                Object parsed=parser.parse(request.toPacket(),player);
                String field=opcode==OpcodeIn.CAST_ON_NPC?"targetIndex":"serverIndex";
                check(parsed!=null&&parsed.getClass().getField(field).getInt(parsed)==16383,"high interaction carrier "+opcode);
            }
            check(harness.world().getNpc(65535)==null && harness.world().getNpc(-1)==null,"invalid lookup");
            for(Npc npc:boundary)npc.resetAfterUpdate();
            Npc high=boundary.get(5);
            high.getUpdateFlags().setDamage(new com.openrsc.server.model.entity.update.Damage(high,3));
            high.getUpdateFlags().setProjectile(new com.openrsc.server.model.entity.update.Projectile(boundary.get(0),high,1));
            Method appearances=GameStateUpdater.class.getDeclaredMethod("updateNpcAppearances",Player.class);appearances.setAccessible(true);
            packets(player).clear();appearances.invoke(harness.server().getGameUpdater(),player);
            Packet appearance=packets(player).stream().filter(p->p.getID()==104).findFirst().orElseThrow(()->new AssertionError("missing NPC appearance"));
            orsc.buffers.RSBuffer appearanceInput=new orsc.buffers.RSBuffer(appearance.getLength());
            appearance.getBuffer().getBytes(0,appearanceInput.dataBuffer);
            int records=appearanceInput.getShort();boolean hit=false,projectile=false;
            for(int record=0;record<records;record++) {
                check(appearanceInput.getShort()==16383,"high appearance sender aliased");
                int type=appearanceInput.getUnsignedByte();
                if(type==2) {check(appearanceInput.getUnsignedByte()==3,"hit damage");appearanceInput.getUnsignedByte();appearanceInput.getUnsignedByte();hit=true;}
                else if(type==3) {check(appearanceInput.getShort()==1&&appearanceInput.getShort()==0,"projectile caster namespace");projectile=true;}
                else throw new AssertionError("unexpected fixture appearance type="+type);
            }
            check(hit&&projectile,"missing hit/projectile record");
            Npc old=boundary.get(5);
            long oldLifetime=old.getCombatLifecycle();
            com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent delayed=new com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent(harness.world(),player,old,3,1);
            harness.world().unregisterNpc(old);
            check(old.getCombatLifecycle()>oldLifetime,"despawn did not advance lifetime");
            Npc replacement=new Npc(harness.world(),3,450,450,0);harness.world().registerNpc(replacement);
            old.setIndex(16383);registry.remove(old);
            check(registry.get(16383)==replacement,"stale removal after high-index reuse");
            int replacementHits=replacement.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id());
            delayed.action();
            check(replacement.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id())==replacementHits,"old projectile hit replacement");
            // Golden legacy spawn bodies: original count/index/offset/sprite/definition widths.
            setID.invoke(boundary.get(0),3);
            for(int version:new int[]{38,39,40,69,115,140,177,235,10052}) {
                player.setClientVersion(version);player.setClientLimitations(ClientLimitations.forVersion(version));
                player.getLocalNpcs().clear();packets(player).clear();
                update.invoke(harness.server().getGameUpdater(),player,Collections.singletonList(boundary.get(0)));
                Packet actual=packets(player).get(packets(player).size()-1);
                boolean custom=version>=10000;
                Packet expected=version>=38&&version<=40
                    ? new PacketBuilder().writeShort(62).writeByte(boundary.get(0).getSprite()).writeByte(3).toPacket()
                    : new PacketBuilder().startBitAccess().writeBits(0,custom?16:8)
                    .writeBits(0,version==115||version==140?11:12)
                    .writeBits(custom?255:31,custom?8:5).writeBits(0,custom?8:5)
                    .writeBits(boundary.get(0).getSprite(),4).writeBits(3,version==115?8:version==140?9:10).finishBitAccess().toPacket();
                byte[] before=new byte[expected.getLength()],after=new byte[actual.getLength()];
                expected.getBuffer().getBytes(0,before);actual.getBuffer().getBytes(0,after);
                check(Arrays.equals(before,after),"legacy spawn bytes changed for "+version);
            }
            player.setClientVersion(10053);player.setClientLimitations(ClientLimitations.forVersion(10053));
            player.getLocalNpcs().clear();packets(player).clear();
            player.setOpponent(replacement);
            Method prioritize=GameStateUpdater.class.getDeclaredMethod("prioritizeVisibleNpcs",Player.class,Collection.class);prioritize.setAccessible(true);
            java.util.List<?> prioritized=(java.util.List<?>)prioritize.invoke(null,player,registry);
            check(prioritized.size()==NpcCapacityContract.VISIBLE_NPC_LIMIT&&prioritized.get(0)==replacement,"bounded view lost direct combat priority");
            boolean customMovementBefore=harness.server().getConfig().WANT_CUSTOM_WALK_SPEED;
            for(boolean customMovement:new boolean[]{true,false}) {
                harness.server().getConfig().WANT_CUSTOM_WALK_SPEED=customMovement;
                player.getLocalNpcs().clear();packets(player).clear();
                update.invoke(harness.server().getGameUpdater(),player,registry);
                check(player.getLocalNpcs().size()==NpcCapacityContract.VISIBLE_NPC_LIMIT,"view budget not enforced");
                check(player.getLocalNpcs().contains(replacement),"crowded view dropped combat target with movement mode="+customMovement);
            }
            harness.server().getConfig().WANT_CUSTOM_WALK_SPEED=customMovementBefore;
            Packet denseSpawn=packets(player).stream().filter(p->p.getID()==79).findFirst().orElseThrow(()->new AssertionError("missing bounded dense spawn"));
            com.openrsc.server.net.PacketFrameLengthGuard.requireSimplifiedPayloadLength(denseSpawn.getLength());
            check(denseSpawn.getLength()<7000,"dense spawn payload unexpectedly large");
            player.setOpponent(null);
            // Reader rejects unsupported indexes, byte truncation and nonzero padding.
            for(int bad:new int[]{16384,32767,32768,65535}) {
                Packet p=new PacketBuilder().startBitAccess().writeBits(bad,16).writeBits(0,8).writeBits(0,8).writeBits(0,4).writeBits(3,16).finishBitAccess().toPacket();
                try {NpcSpawnRecord.read(buffer(p),p.getLength());throw new AssertionError("unsupported index accepted");}catch(IllegalArgumentException expected){}
            }
            Packet truncated=new PacketBuilder().writeShort(4096).toPacket();
            try {NpcSpawnRecord.read(buffer(truncated),2);throw new AssertionError("truncation accepted");}catch(IllegalArgumentException expected){}
            System.out.println("PASS production 16384 registrations; boundary spawn/definition encoder-decoder; high attack target; invalid carriers; index reuse/lifetimes; high client definition storage; classic/retro golden bytes; 1024 view priority and frame budget; profile matrix");
        }
    }
}
