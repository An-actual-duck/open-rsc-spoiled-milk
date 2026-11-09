package orsc.net;

import java.io.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
import orsc.buffers.RSBuffer_Bits;

/** Exercises the packaged production frame reader with deterministic byte delivery. */
public final class NetworkDisabledDiagnosticsFixture {
    static int checks;
    static final boolean ENABLED=Boolean.getBoolean("spoiledmilk.boundaryDiagnostics");
    static final ByteArrayOutputStream logs=new ByteArrayOutputStream(), decoded=new ByteArrayOutputStream();
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static String takeLogs()throws Exception{String text=logs.toString("UTF-8");logs.reset();return text;}
    static RSBuffer_Bits buffer(){return new RSBuffer_Bits(2);}
    static int packet(Scripted n,RSBuffer_Bits b){int size=n.readIncomingPacket(b);require(b.packetEnd==0,"public read resets packet cursor");if(size>0)decoded.write(b.dataBuffer,0,size);return size;}
    static final class Scripted extends Network_Base {
        final byte[] wire;int position,delivered,availableCalls,scalarReads,bulkReads;boolean failBulk,failAvailableAfterDrain;
        Scripted(byte...wire){this.wire=wire;}
        void deliver(int end){delivered=Math.max(delivered,Math.min(end,wire.length));}
        @Override int available()throws IOException{availableCalls++;if(failAvailableAfterDrain&&position==wire.length)throw new IOException("diagnostic-available-fault");return delivered-position;}
        @Override int read()throws IOException{scalarReads++;if(position>=delivered)throw new IOException("header-eof");return wire[position++]&255;}
        @Override void read(byte[] dest,int offset,int count)throws IOException{bulkReads++;if(failBulk)throw new IOException("payload-read-fault");if(count>delivered-position)throw new IOException("read-exceeds-delivery");System.arraycopy(wire,position,dest,offset,count);position+=count;}
    }
    static int diagnosticCount(Scripted n)throws Exception{Field f=Network_Base.class.getDeclaredField("diagnosticFrameCount");f.setAccessible(true);return f.getInt(n);}
    public static void main(String[] args)throws Exception{
        PrintStream original=System.out;int fullCalls,capCalls;
        try{
            System.setOut(new PrintStream(logs,true,"UTF-8"));
            require(Network_Base.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("/Open_RSC_Client.jar"),"reader comes from actual packaged client");
            Scripted full=new Scripted((byte)0,(byte)5,(byte)143,(byte)7,(byte)9);full.deliver(5);RSBuffer_Bits b=buffer();
            require(packet(full,b)==3,"full payload decoded");require(Arrays.equals(Arrays.copyOf(b.dataBuffer,3),new byte[]{(byte)143,7,9}),"full decoded bytes");
            fullCalls=full.availableCalls;require(fullCalls==(ENABLED?5:2),"full packet only protocol queries when disabled");require(full.scalarReads==2&&full.bulkReads==1,"full reads unchanged");
            String expected="NETWORK_FRAME_HEADER declaredBytes=5 pendingBytes=3 available=3\nNETWORK_FRAME_PROGRESS read=3 received=3/3 available=0\nNETWORK_FRAME_COMPLETE opcode=143 bytes=3 available=0\n";
            require(takeLogs().equals(ENABLED?expected:""),"enabled full diagnostic text / disabled silence");

            Scripted partial=new Scripted((byte)0,(byte)9,(byte)143,(byte)1,(byte)2,(byte)3,(byte)4,(byte)5,(byte)6,(byte)0,(byte)4,(byte)51,(byte)99);partial.m_d=2;
            partial.deliver(5);require(packet(partial,b)==0&&partial.position==5,"partial bytes drained immediately");
            require(packet(partial,b)==0&&!partial.errorHappened,"empty partial poll");
            partial.deliver(7);require(packet(partial,b)==0&&partial.position==7,"second partial consumed");
            require(packet(partial,b)==0&&!partial.errorHappened,"progress resets timeout attempts");
            partial.deliver(9);require(packet(partial,b)==7,"first fragmented packet complete");for(int i=1;i<7;i++)require(b.dataBuffer[i]==i,"fragment byte "+i);
            partial.deliver(13);require(packet(partial,b)==2&&b.dataBuffer[0]==51&&b.dataBuffer[1]==99,"following packet alignment");
            require(!partial.errorHappened,"fragmented sequence has no error");if(!ENABLED)require(takeLogs().isEmpty(),"disabled partial diagnostics absent");else require(takeLogs().contains("NETWORK_FRAME_PROGRESS read=2 received=5/7 available=0"),"enabled partial text retained");

            byte[] largeWire=new byte[4099];largeWire[0]=16;largeWire[1]=3;
            for(int i=2;i<largeWire.length;i++)largeWire[i]=(byte)(i*37);
            Scripted large=new Scripted(largeWire);large.deliver(1026);require(packet(large,b)==0&&large.position==1026,"large frame drains first offered chunk");
            large.deliver(2050);require(packet(large,b)==0&&large.position==2050,"large frame drains second offered chunk");
            large.deliver(4099);require(packet(large,b)==4097&&Arrays.equals(Arrays.copyOf(b.dataBuffer,4097),Arrays.copyOfRange(largeWire,2,4099)),"large packet capacity/gathered bytes preserved");takeLogs();

            Scripted pending=new Scripted((byte)0,(byte)9,(byte)143,(byte)1,(byte)2,(byte)3,(byte)4,(byte)5,(byte)6);pending.deliver(2);
            require(packet(pending,b)==0,"pending header");for(int i=0;i<120;i++)require(packet(pending,b)==0&&!pending.errorHappened,"pending poll "+i);
            require(pending.availableCalls==(ENABLED?124:122),"pending diagnostic does not add disabled availability query");
            String pendingText=takeLogs();require(pendingText.equals(ENABLED?"NETWORK_FRAME_HEADER declaredBytes=9 pendingBytes=7 available=0\nNETWORK_FRAME_PENDING attempts=120 received=0/7 available=0\n":""),"pending cadence/text unchanged");
            pending.deliver(9);require(packet(pending,b)==7,"pending frame resumes normally");takeLogs();

            Scripted timeout=new Scripted();timeout.m_d=1;require(packet(timeout,b)==0,"first idle timeout poll");require(packet(timeout,b)==0&&timeout.errorHappened&&timeout.errorCode.equals("time-out")&&timeout.m_d==2,"timeout state preserved");
            require(timeout.availableCalls==(ENABLED?2:1),"timeout avoids disabled diagnostic query");
            require(takeLogs().equals(ENABLED?"NETWORK_FRAME_TIMEOUT attempts=2 received=0/0 available=0\n":""),"timeout diagnostic text unchanged");
            try{timeout.flush(0,true);throw new AssertionError("timeout flush accepted");}catch(IOException e){require(e.getMessage().equals("time-out"),"timeout exception preserved");}
            for(int declared=0;declared<3;declared++){Scripted bad=new Scripted((byte)0,(byte)declared);bad.deliver(2);require(packet(bad,b)==0&&bad.errorHappened&&bad.errorCode.equals("invalid-frame-length"),"malformed length "+declared);require(bad.availableCalls==1,"malformed has only required availability");}
            require(takeLogs().isEmpty(),"malformed lengths do not create new diagnostics");
            Scripted fault=new Scripted((byte)0,(byte)4,(byte)11,(byte)22);fault.deliver(4);fault.failBulk=true;require(packet(fault,b)==0&&fault.errorHappened&&fault.errorCode.equals("payload-read-fault"),"actual I/O failure handling unchanged");takeLogs();

            byte[] wire=new byte[300];for(int i=0;i<100;i++){wire[i*3]=0;wire[i*3+1]=3;wire[i*3+2]=(byte)i;}
            Scripted capped=new Scripted(wire);capped.deliver(wire.length);for(int i=0;i<100;i++)require(packet(capped,b)==1&&(b.dataBuffer[0]&255)==i,"capped frame "+i);
            capCalls=capped.availableCalls;require(capCalls==(ENABLED?456:200),"after log cap only protocol availability remains");
            String capLog=takeLogs();require((capLog.isEmpty()?0:capLog.split("\n").length)==(ENABLED?256:0),"exact diagnostic line cap");require(diagnosticCount(capped)==(ENABLED?256:0),"diagnostic cap state");
            capped.m_d=1;require(packet(capped,b)==0&&packet(capped,b)==0&&capped.errorCode.equals("time-out"),"timeout after cap");require(capped.availableCalls==capCalls+1,"capped timeout adds no diagnostic availability query");require(takeLogs().isEmpty(),"no diagnostic after cap");

            // A diagnostic-only post-read available() failure stays observable only
            // when diagnostics are enabled; normal decode must not ask it when off.
            Scripted diagFault=new Scripted((byte)0,(byte)3,(byte)19);diagFault.deliver(3);diagFault.failAvailableAfterDrain=true;
            int count=diagFault.readIncomingPacket(buffer());require(ENABLED?(count==0&&diagFault.errorHappened&&diagFault.errorCode.equals("diagnostic-available-fault")):(count==1&&!diagFault.errorHappened),"disabled diagnostic cannot introduce an I/O fault");takeLogs();
        }finally{System.setOut(original);}
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(decoded.toByteArray());StringBuilder sha=new StringBuilder();for(byte x:hash)sha.append(String.format("%02x",x&255));
        System.out.println("RESULT {\"enabled\":"+ENABLED+",\"checks\":"+checks+",\"decodedSha256\":\""+sha+"\",\"fullAvailableCalls\":"+fullCalls+",\"cappedAvailableCalls\":"+capCalls+"}");
        System.out.println("PASS production Network_Base diagnostics gate: framed packets, partial drains, pending, timeout, malformed lengths, I/O failures and enabled log cap");
    }
}
