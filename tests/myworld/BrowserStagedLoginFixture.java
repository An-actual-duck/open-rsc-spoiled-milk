package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.net.Socket;
import java.util.Arrays;
import orsc.buffers.RSBuffer_Bits;
import orsc.net.Network_Socket;

/** Exercises the real control codec/state; it cannot claim renderer readiness. */
public final class BrowserStagedLoginFixture {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    interface Body{void write(DataOutputStream out)throws Exception;}
    static byte[] control(int kind,Body body)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(254);out.writeInt(BrowserStagedLogin.MAGIC);out.writeByte(1);out.writeByte(kind);body.write(out);return bytes.toByteArray();
    }
    static RSBuffer_Bits buffer(byte[] bytes){RSBuffer_Bits b=new RSBuffer_Bits(8192);System.arraycopy(bytes,0,b.dataBuffer,0,bytes.length);b.packetEnd=1;return b;}
    static void text(DataOutputStream out,String value)throws Exception{out.writeBytes(value);out.writeByte(10);}
    static byte[] destination(byte[] token,byte[] digest,int level,int plane,String map)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.write(token);out.write(digest);out.writeInt(300000);
        for(int n:new int[]{104,671,level,233,461,plane,2304,1776,944})out.writeInt(n);
        text(out,"overworld");text(out,NativeLayeredTerrainSnapshot.PROJECTION_ID);text(out,map);return bytes.toByteArray();
    }
    static void set(Object value,String name,Object field)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);f.set(value,field);}
    static Object invoke(Method method,Object value,Object...args)throws Exception{
        try{return method.invoke(value,args);}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw(Exception)t;throw e;}
    }
    static void refused(Method method,Object value,byte[] bytes)throws Exception{
        try{invoke(method,value,buffer(bytes),bytes.length);throw new AssertionError("Invalid control accepted");}catch(IOException expected){checks++;}
    }
    static final class Backend implements BrowserStagedLogin.Backend {
        int finishes;boolean committed;
        public void begin(int owner){}public void prepareData(){throw new AssertionError("No preparation in codec fixture");}
        public boolean ready(int owner){return false;}public void finish(int owner,boolean value){finishes++;committed=value;}
    }
    static final class FakeSocket extends Socket {
        final InputStream input;final ByteArrayOutputStream output=new ByteArrayOutputStream();
        FakeSocket(byte[] response){input=new ByteArrayInputStream(response);}
        FakeSocket(){input=new InputStream(){public int available()throws IOException{throw new IOException("Game connection closed");}public int read(){return -1;}};}
        public InputStream getInputStream(){return input;}public OutputStream getOutputStream(){return output;}
        public synchronized void close(){}
    }
    static byte[] frame(byte[] body)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.writeShort(body.length+2);out.write(body);return b.toByteArray();}
    public static void main(String[] args)throws Exception{
        String map=WorldBuilderTerrainBootstrap.mapIdentity();
        byte[] token=new byte[16],digest=new byte[32];Arrays.fill(token,(byte)7);Arrays.fill(digest,(byte)9);
        for(int level:new int[]{-10,-1,0,1,10}){
            BrowserStagedLogin.Destination d=new BrowserStagedLogin.Destination(new DataInputStream(new ByteArrayInputStream(destination(token,digest,level,level==0?0:1,map))));
            check(d.level==level&&d.map.equals(map),"signed level and actual installed source retained");
            check(!d.toString().contains(map)&&!d.toString().contains("671"),"destination redacted");
        }
        for(byte[] invalid:new byte[][]{new byte[0],Arrays.copyOf(destination(token,digest,-1,1,map),70),destination(token,digest,0,4,map),destination(token,digest,0,0,new String(new char[64]).replace('\0','f'))}){
            try{new BrowserStagedLogin.Destination(new DataInputStream(new ByteArrayInputStream(invalid)));throw new AssertionError("Invalid destination accepted");}catch(IOException expected){checks++;}
        }
        Backend backend=new Backend();BrowserStagedLogin.install(backend);
        BrowserStagedLogin.Session session=new BrowserStagedLogin.Session(null,null);
        session.destination=new BrowserStagedLogin.Destination(new DataInputStream(new ByteArrayInputStream(destination(token,digest,-1,1,map))));
        set(session,"owner",1);set(session,"context",true);set(session,"contextSequence",17);
        Method message=BrowserStagedLogin.Session.class.getDeclaredMethod("message",RSBuffer_Bits.class,int.class);message.setAccessible(true);
        byte[] fence=control(5,o->{o.write(token);o.write(digest);o.writeInt(17);});
        check(((Integer)invoke(message,session,buffer(fence),fence.length))==-1&&session.hasFence(),"exact fence accepted");
        byte[] stale=token.clone();stale[0]++;
        refused(message,session,control(5,o->{o.write(stale);o.write(digest);o.writeInt(17);}));
        refused(message,session,control(5,o->{o.write(token);o.write(digest);o.writeInt(18);}));
        byte[] commit=control(2,o->{o.write(token);o.write(digest);o.writeByte(0x40);});
        refused(message,session,commit);set(session,"readySent",true);
        refused(message,session,control(2,o->{o.write(stale);o.write(digest);o.writeByte(0x40);}));
        refused(message,session,control(2,o->{o.write(token);o.write(digest);o.writeByte(3);}));
        refused(message,session,Arrays.copyOf(commit,commit.length+1));
        refused(message,session,control(4,o->{}));refused(message,session,control(3,o->o.writeByte(1)));
        check(((Integer)invoke(message,session,buffer(commit),commit.length))==0x40&&backend.finishes==1&&backend.committed&&!session.active(),"only owned prepared COMMIT finishes functional backend");
        RSBuffer_Bits capability=new RSBuffer_Bits(8);BrowserStagedLogin.capability(capability);
        check(capability.packetEnd==5&&capability.dataBuffer[0]==0x42&&capability.dataBuffer[4]==1,"five-byte opt-in trailer");
        for(boolean valid:new boolean[]{true,false}){
            byte[] reply=control(0,o->{});if(!valid)reply[1]=0;
            Network_Socket stream=new Network_Socket(new FakeSocket(frame(reply)),new PacketHandler(null));stream.m_d=123;
            try{check(BrowserStagedLogin.support(stream)==valid,"preauth support codec rejects unknown version/magic before credentials");check(stream.m_d==123,"poll limit restored");}finally{stream.close();}
        }
        check(!BrowserStagedLogin.enabled(),"native default never opts in");
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        mudclient client=(mudclient)((sun.misc.Unsafe)singleton.get(null)).allocateInstance(mudclient.class);
        BrowserStagedLogin.Session pending=new BrowserStagedLogin.Session(client,null);pending.destination=session.destination;
        set(client,"browserStagedLogin",pending);set(client,"currentViewMode",orsc.enumerations.GameMode.LOGIN);
        check(client.isBrowserStagedLoginPreparing()&&!client.isGameView()&&client.getLocalPlayer()==null,"pending state is neither GAME nor an entity");
        Method scene=BrowserStagedLogin.Session.class.getDeclaredMethod("scene",int.class,int.class);scene.setAccessible(true);
        for(int opcode:new int[]{193,25,67,143,154})try{invoke(scene,pending,opcode,1);throw new AssertionError("Unexpected pending scene accepted");}catch(IOException expected){checks++;}
        set(pending,"readySent",true);
        try{invoke(scene,pending,157,1);throw new AssertionError("Prepared scene mutated after READY");}catch(IOException expected){checks++;}
        final BrowserStagedLogin.Destination expectedDestination=session.destination;
        client.packetHandler=new PacketHandler(client){@Override boolean browserStagedContextMatches(BrowserStagedLogin.Destination d,int seq){return d==expectedDestination&&seq==17;}};
        set(client,"currentViewMode",orsc.enumerations.GameMode.GAME);set(client,"hasCompletedInitialRegionLoad",true);
        set(client,"browserPreparedLogin",expectedDestination);set(client,"browserPreparedLoginSequence",17);
        check(client.consumeBrowserPreparedWorldInfo(2304,1776,1,944),"exact commit worldInfo retains prebuilt scene");
        check(!client.consumeBrowserPreparedWorldInfo(2304,1776,1,944),"retention is consumed once; ordinary travel reloads");
        set(client,"browserPreparedLogin",expectedDestination);set(client,"browserPreparedLoginSequence",18);
        check(!client.consumeBrowserPreparedWorldInfo(2304,1776,1,944),"stale context cannot reuse prepared scene");
        set(client,"browserPreparedLogin",expectedDestination);set(client,"browserPreparedLoginSequence",17);
        check(!client.consumeBrowserPreparedWorldInfo(2304,1776,2,944),"changed plane cannot reuse prepared scene");
        for(boolean ready:new boolean[]{false,true}){
            Backend cleanup=new Backend();BrowserStagedLogin.install(cleanup);
            Network_Socket closed=new Network_Socket(new FakeSocket(),new PacketHandler(null));client.packetHandler=new PacketHandler(client);
            BrowserStagedLogin.Session disconnect=new BrowserStagedLogin.Session(client,closed);disconnect.destination=expectedDestination;set(disconnect,"owner",2);set(disconnect,"readySent",ready);
            long start=System.nanoTime();
            try{disconnect.run();throw new AssertionError("Closed pending socket accepted");}catch(IOException expected){checks++;}finally{closed.close();}
            check(System.nanoTime()-start<1000000000L&&cleanup.finishes==1&&!cleanup.committed,"pending disconnect promptly cancels before/after READY");
        }
        System.out.println("PASS "+checks+" real staged-login codec, signed/source binding, fence/commit ownership and legacy-off checks; CPU only");
    }
}
