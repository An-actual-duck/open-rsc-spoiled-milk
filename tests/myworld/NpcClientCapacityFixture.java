import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import java.lang.reflect.*;
import orsc.*;
import orsc.buffers.RSBuffer_Bits;

/** Calls maintained client decode/storage methods without constructing UI or starting a client. */
public final class NpcClientCapacityFixture {
    static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    static void set(Object target,String name,Object value)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    static Object get(Object target,String name)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    static void input(PacketHandler handler,Packet packet)throws Exception {
        RSBuffer_Bits buffer=(RSBuffer_Bits)get(handler,"packetsIncoming");
        buffer.dataBuffer=new byte[packet.getLength()+1];buffer.dataBuffer[0]=(byte)packet.getID();
        packet.getBuffer().getBytes(0,buffer.dataBuffer,1,packet.getLength());buffer.packetEnd=1;
    }
    static void show(PacketHandler handler,Packet packet)throws Exception {
        input(handler,packet);Method method=PacketHandler.class.getDeclaredMethod("showNPCs",int.class);method.setAccessible(true);method.invoke(handler,packet.getLength()+1);
    }
    static PacketBuilder add(PacketBuilder packet,int index) {
        return packet.writeBits(index,16).writeBits(0,8).writeBits(0,8).writeBits(0,4).writeBits(3,16);
    }
    public static void main(String[] args)throws Exception {
        com.openrsc.client.entityhandling.EntityHandler.load(true);
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        mudclient client=(mudclient)unsafe.allocateInstance(mudclient.class);
        set(client,"npcsServer",new ORSCharacter[5000]);set(client,"npcs",new ORSCharacter[500]);set(client,"npcsCache",new ORSCharacter[500]);
        set(client,"npcVisualOffsetX",new int[500]);set(client,"npcVisualOffsetZ",new int[500]);
        set(client,"tileSize",128);set(client,"playerLocalX",10);set(client,"playerLocalZ",10);
        for(String field:new String[]{"customNpcMovementTargetWorldX","customNpcMovementTargetWorldZ","customNpcMovementTargetDirection"})set(client,field,new int[5000]);
        set(client,"customNpcMovementTargetMillis",new long[5000]);set(client,"customNpcMovementTargetValid",new boolean[5000]);set(client,"customNpcMovementTargetResult",new String[5000]);
        PacketHandler handler=new PacketHandler(client);
        PacketBuilder spawn=new PacketBuilder(79).startBitAccess().writeBits(0,16);
        for(int index:new int[]{0,2047,4095,4096,8191,16383})add(spawn,index);
        show(handler,spawn.finishBitAccess().toPacket());
        check(client.getNpcCount()==6&&client.getNpcFromServer(0)!=client.getNpcFromServer(16383),"client global identity alias");
        int length=((ORSCharacter[])get(client,"npcsServer")).length;
        for(String name:new String[]{"customNpcMovementTargetWorldX","customNpcMovementTargetWorldZ","customNpcMovementTargetDirection","customNpcMovementTargetMillis","customNpcMovementTargetValid","customNpcMovementTargetResult"})check(Array.getLength(get(client,name))==length,"companion growth "+name);
        Packet staleChat=new PacketBuilder(104).writeShort(2).writeShort(16382).writeByte(1).writeShort(65535).writeString("stale")
            .writeShort(16383).writeByte(2).writeByte(7).writeByte(11).writeByte(20).toPacket();
        input(handler,staleChat);Method appearances=PacketHandler.class.getDeclaredMethod("updateNPCAppearances");appearances.setAccessible(true);appearances.invoke(handler);
        check(client.getNpcFromServer(16383).healthCurrent==11,"stale chat desynchronized following high-index hit");
        ORSCharacter player=new ORSCharacter();player.attackingNpcServerIndex=16383;player.projectileRange=40;
        set(client,"players",new ORSCharacter[]{player});set(client,"playerCount",1);
        client.getNpcFromServer(0).attackingNpcServerIndex=16383;client.getNpcFromServer(0).projectileRange=40;
        ((boolean[])get(client,"customNpcMovementTargetValid"))[16383]=true;
        PacketBuilder remove=new PacketBuilder(79).startBitAccess().writeBits(6,16);
        for(int i=0;i<5;i++)remove.writeBits(0,1);
        remove.writeBits(1,1).writeBits(1,1).writeBits(3,2);
        show(handler,remove.finishBitAccess().toPacket());
        check(client.getNpcCount()==5&&player.projectileRange==0&&client.getNpcFromServer(0).projectileRange==0,"removal retained cross-lifetime projectile references");
        check(!((boolean[])get(client,"customNpcMovementTargetValid"))[16383],"removal retained queued target");
        PacketBuilder readd=new PacketBuilder(79).startBitAccess().writeBits(5,16);
        for(int i=0;i<5;i++)readd.writeBits(0,1);
        add(readd,16383);show(handler,readd.finishBitAccess().toPacket());
        check(client.getNpcCount()==6&&client.getNpcFromServer(16383).projectileRange==0,"re-add retained previous visual lifetime");
        client.getNpcFromServer(16383).attackingNpcServerIndex=0;client.getNpcFromServer(16383).projectileRange=40;
        PacketBuilder removeFirst=new PacketBuilder(79).startBitAccess().writeBits(6,16).writeBits(1,1).writeBits(1,1).writeBits(3,2);
        for(int i=0;i<5;i++)removeFirst.writeBits(0,1);
        show(handler,removeFirst.finishBitAccess().toPacket());
        check(client.getNpcFromServer(16383).projectileRange==0,"old local cache retained NPC projectile origin");
        input(handler,new PacketBuilder(104).writeShort(1).writeShort(16383).writeByte(255).toPacket());
        try {appearances.invoke(handler);throw new AssertionError("unknown NPC update type accepted");}
        catch(InvocationTargetException expected) {check(expected.getCause() instanceof IllegalArgumentException,"malformed update exception");}
        try {
            show(handler,new PacketBuilder(79).startBitAccess().writeBits(0,16).writeBits(4096,16).finishBitAccess().toPacket());
            throw new AssertionError("truncated client addition accepted");
        } catch(InvocationTargetException expected) {check(expected.getCause() instanceof IllegalArgumentException,"truncated addition exception");}
        System.out.println("PASS maintained client decode: high indexes, all companion growth, stale chat/hit, removal/re-add, queued targets and projectile namespaces");
    }
}
