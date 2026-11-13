package orsc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import orsc.buffers.RSBuffer_Bits;
import orsc.graphics.three.Renderer3DSettings;
import orsc.net.Network_Socket;
import orsc.util.GenUtil;

/** Negotiated browser-only authenticated preparation; never a player/game state. */
public final class BrowserStagedLogin {
    public static final int MAGIC=0x42535031, VERSION=1, OPCODE=254, PENDING_RESPONSE=26;
    private static final AtomicInteger OWNERS=new AtomicInteger();
    public interface Backend {
        void begin(int owner);
        void prepareData();
        boolean ready(int owner);
        void finish(int owner,boolean committed);
    }
    private static volatile Backend backend;
    public static void install(Backend value){backend=value;}
    static boolean enabled(){return Boolean.getBoolean("spoiledmilk.browserStagedLogin")
        &&Renderer3DSettings.isBrowserPresentationAvailable()&&backend!=null;}
    static void capability(RSBuffer_Bits out){out.putInt(MAGIC);out.putByte(VERSION);}
    static void header(RSBuffer_Bits out,int action){capability(out);out.putByte(action);}
    private static DataInputStream control(RSBuffer_Bits in,int length)throws IOException {
        if(length<7||length>8192)throw new IOException("Invalid staged login control length");
        DataInputStream data=new DataInputStream(new ByteArrayInputStream(in.dataBuffer,1,length-1));
        if(data.readInt()!=MAGIC||data.readUnsignedByte()!=VERSION)throw new IOException("Unsupported staged login control");
        return data;
    }
    private static String string(DataInputStream in)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int c;
        while((c=in.read())!=-1&&c!=10){if(c<32||c>126||bytes.size()>=128)throw new IOException("Invalid staged login text");bytes.write(c);}
        if(c!=10)throw new IOException("Truncated staged login text");return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
    }
    private static void end(DataInputStream in)throws IOException{if(in.available()!=0)throw new IOException("Trailing staged login control");}
    static boolean support(Network_Socket stream)throws IOException {
        int readLimit=stream.m_d;stream.m_d=0;
        try{
            stream.newPacket(OPCODE);header(stream.bufferBits,0);stream.finishPacketAndFlush();
            RSBuffer_Bits input=new RSBuffer_Bits(8192);long deadline=System.nanoTime()+10000000000L;
            while(System.nanoTime()<deadline){
                int n=stream.readIncomingPacket(input);
                if(n>0){if(input.getUnsignedByte()!=OPCODE)return false;
                    DataInputStream data=control(input,n);boolean ok=data.readUnsignedByte()==0;end(data);return ok;}
                GenUtil.sleepShadow(20L);
            }
            return false;
        }catch(IOException unsupported){return false;}
        finally{stream.m_d=readLimit;}
    }
    static final class Destination {
        final byte[] token=new byte[16],digest=new byte[32];
        final int remainingMillis,x,y,level,legacyX,legacyY,plane,offsetX,offsetY,levelStride;
        final String worldSpace,projection,map;
        Destination(DataInputStream in)throws IOException {
            in.readFully(token);in.readFully(digest);remainingMillis=in.readInt();x=in.readInt();y=in.readInt();level=in.readInt();legacyX=in.readInt();legacyY=in.readInt();plane=in.readInt();offsetX=in.readInt();offsetY=in.readInt();levelStride=in.readInt();
            worldSpace=string(in);projection=string(in);map=string(in);end(in);
            if(remainingMillis<1||remainingMillis>300000||plane<0||plane>3||legacyX<0||legacyX>32767||legacyY<0||legacyY>8191
                ||offsetX!=2304||offsetY!=1776||levelStride!=944||!worldSpace.matches("[a-z0-9][a-z0-9._-]{0,127}")
                ||!projection.equals(NativeLayeredTerrainSnapshot.PROJECTION_ID)||!map.matches("[0-9a-f]{64}")
                ||!WorldBuilderTerrainBootstrap.isNativeOnly()||!map.equals(WorldBuilderTerrainBootstrap.mapIdentity()))
                throw new IOException("Staged login destination is incompatible with this client");
        }
        @Override public String toString(){return "Browser preparation destination [private]";}
    }
    static final class Session {
        private final mudclient client;
        private final Network_Socket stream;
        private final Backend presentation;
        private final long hardDeadline=System.nanoTime()+300000000000L;
        Destination destination;
        private long deadline=hardDeadline,lastHeartbeat;
        private int owner,contextSequence,fencedSequence;
        private boolean settings,context,prepared,readySent,committed;
        Session(mudclient client,Network_Socket stream){this.client=client;this.stream=stream;this.presentation=backend;}
        boolean active(){return !committed&&destination!=null;}
        int owner(){return owner;}
        boolean hasContext(){return context;}
        boolean hasFence(){return context&&fencedSequence>0&&fencedSequence==contextSequence;}
        int contextSequence(){return contextSequence;}
        private void envelope(int action)throws IOException {
            if(destination==null)throw new IOException("No pending browser preparation");
            stream.newPacket(OPCODE);header(stream.bufferBits,action);stream.bufferBits.writeBytes(destination.token,16);stream.bufferBits.writeBytes(destination.digest,32);
        }
        void receipt(int opcode){
            if(opcode!=154&&opcode!=155||!active())throw new IllegalStateException("Unowned preparation receipt");
            try{envelope(4);stream.bufferBits.putByte(opcode);}catch(IOException e){throw new IllegalStateException("Cannot write preparation receipt",e);}
        }
        private boolean owned(DataInputStream in)throws IOException {
            byte[] token=new byte[16],digest=new byte[32];in.readFully(token);in.readFully(digest);
            return destination!=null&&Arrays.equals(token,destination.token)&&Arrays.equals(digest,destination.digest);
        }
        private int message(RSBuffer_Bits input,int length)throws IOException {
            DataInputStream data=control(input,length);int kind=data.readUnsignedByte();
            if(kind==1){
                Destination next=new Destination(data);
                if(destination!=null&&Arrays.equals(next.token,destination.token))throw new IOException("Repeated preparation owner");
                if(destination!=null)presentation.finish(owner,false);
                destination=next;owner=OWNERS.incrementAndGet();if(owner<1)throw new IOException("Preparation owner exhausted");
                deadline=Math.min(hardDeadline,System.nanoTime()+next.remainingMillis*1000000L);settings=context=prepared=readySent=false;contextSequence=fencedSequence=0;
                client.beginBrowserStagedWorld(next);presentation.begin(owner);return -1;
            }
            if(kind==3){int reason=data.readUnsignedByte();end(data);throw new IOException("Browser preparation was declined (code "+reason+")");}
            if(kind==5){if(!owned(data))throw new IOException("Stale preparation fence");int sequence=data.readInt();end(data);if(!context||sequence!=contextSequence||sequence<1)throw new IOException("Preparation fence differs from scene context");fencedSequence=sequence;return -1;}
            if(kind==2){if(!owned(data))throw new IOException("Stale preparation commit");int code=data.readUnsignedByte();end(data);if(!readySent||(code&0x40)==0)throw new IOException("Unexpected preparation commit");committed=true;presentation.finish(owner,true);return code;}
            throw new IOException("Unexpected staged login message");
        }
        private void scene(int opcode,int length)throws IOException {
            if(destination==null||readySent||opcode!=157&&opcode!=143&&opcode!=154&&opcode!=240)throw new IOException("Unexpected packet before world entry");
            if(opcode==240){if(context||prepared||length<39||length>43)throw new IOException("Invalid pending account settings");client.packetHandler.handleBrowserStagedScenePacket(opcode,length);settings=true;return;}
            if(opcode!=157&&!context)throw new IOException("Static preparation precedes authenticated context");
            if(opcode==157){
                if(!settings||context)throw new IOException("Pending account settings/context order is invalid");
                DataInputStream b=new DataInputStream(new ByteArrayInputStream(client.packetHandler.getPacketsIncoming().dataBuffer,1,length-1));
                int protocol=b.readUnsignedByte(),sequence=b.readInt();b.readInt();String space=string(b),projection=string(b);int x=b.readInt(),y=b.readInt(),level=b.readInt(),legacyX=b.readUnsignedShort(),legacyY=b.readUnsignedShort();
                if(protocol!=8||sequence<1||!space.equals(destination.worldSpace)||!projection.equals(destination.projection)||x!=destination.x||y!=destination.y||level!=destination.level||legacyX!=destination.legacyX||legacyY!=destination.legacyY)throw new IOException("Preparation scene differs from authenticated destination");
                contextSequence=sequence;context=true;
            }
            client.packetHandler.handleBrowserStagedScenePacket(opcode,length);
            if(context&&!prepared){
                if(!client.packetHandler.browserStagedContextMatches(destination,contextSequence))throw new IOException("Preparation source differs from authenticated destination");
                presentation.prepareData();client.loadBrowserStagedWorld(destination);prepared=true;
            }
        }
        int run()throws IOException {
            int readLimit=stream.m_d;stream.m_d=0;
            try{
                while(System.nanoTime()<deadline){
                    int n=stream.readIncomingPacket(client.packetHandler.getPacketsIncoming());
                    if(n>0){int opcode=client.packetHandler.getPacketsIncoming().getUnsignedByte();if(opcode==OPCODE){int code=message(client.packetHandler.getPacketsIncoming(),n);if(code>=0)return code;}else scene(opcode,n);}
                    if(prepared){client.packetHandler.pollLayeredTerrainStageReady();
                        if(hasFence()&&client.isBrowserStagedSceneReady()){
                            client.drawBrowserStagedWorld();
                            if(!readySent&&presentation.ready(owner)){envelope(1);stream.finishPacketAndFlush();readySent=true;}
                        }}
                    long now=System.nanoTime();if(destination!=null&&now-lastHeartbeat>=5000000000L){envelope(3);stream.finishPacketAndFlush();lastHeartbeat=now;}
                    stream.flush(0,true);GenUtil.sleepShadow(20L);
                }
                throw new IOException("Browser preparation timed out; please retry");
            }finally{
                stream.m_d=readLimit;
                if(!committed){try{if(destination!=null){envelope(2);stream.finishPacketAndFlush();}}catch(Exception ignored){}try{if(owner>0)presentation.finish(owner,false);}catch(Throwable ignored){}}
            }
        }
    }
}
