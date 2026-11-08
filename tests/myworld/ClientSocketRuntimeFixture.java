package orsc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Actual public-Socket lifetime/failure contract plus Linux native-load probe. */
public final class ClientSocketRuntimeFixture {
    private static final SocketException IO_FAILURE = new SocketException("option sentinel");
    private static final SecurityException SECURITY_FAILURE = new SecurityException("permission sentinel");
    private static final List<ProbeSocket> sockets = new ArrayList<ProbeSocket>();
    private static int mode;
    private static int connects,binds,listens,accepts;
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static final class ProbeSocket extends SocketImpl {
        final int failureMode=mode;
        boolean created,closed;
        protected void create(boolean stream){created=true;}
        protected void connect(String host,int port)throws IOException{connects++;throw new IOException("Unexpected connect");}
        protected void connect(InetAddress host,int port)throws IOException{connects++;throw new IOException("Unexpected connect");}
        protected void connect(SocketAddress address,int timeout)throws IOException{connects++;throw new IOException("Unexpected connect");}
        protected void bind(InetAddress host,int port)throws IOException{binds++;throw new IOException("Unexpected bind");}
        protected void listen(int backlog)throws IOException{listens++;throw new IOException("Unexpected listen");}
        protected void accept(SocketImpl socket)throws IOException{accepts++;throw new IOException("Unexpected accept");}
        protected InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}
        protected OutputStream getOutputStream(){return new ByteArrayOutputStream();}
        protected int available(){return 0;}
        protected void close(){closed=true;}
        protected void sendUrgentData(int value){throw new AssertionError("Unexpected urgent data");}
        public Object getOption(int option){throw new AssertionError("Unexpected option read");}
        public void setOption(int option,Object value)throws SocketException{
            require(option==SocketOptions.TCP_NODELAY&&Boolean.TRUE.equals(value),"Unexpected socket preparation option");
            if(failureMode==1)throw IO_FAILURE;
            if(failureMode==2)throw SECURITY_FAILURE;
        }
    }
    private static void contract()throws Exception{
        Socket.setSocketImplFactory(()->{ProbeSocket socket=new ProbeSocket();sockets.add(socket);return socket;});
        ClientSocketRuntime.prepare();
        mode=1;try{ClientSocketRuntime.prepare();throw new AssertionError("Missing option IOException");}catch(IOException expected){require(expected==IO_FAILURE,"IOException identity changed");}
        mode=2;try{ClientSocketRuntime.prepare();throw new AssertionError("Missing option security failure");}catch(SecurityException expected){require(expected==SECURITY_FAILURE,"Security failure identity changed");}
        require(sockets.size()==3,"Unexpected socket ownership");
        for(ProbeSocket socket:sockets)require(socket.created&&socket.closed,"Socket was not released after preparation/failure");
        require(connects==0&&binds==0&&listens==0&&accepts==0,"Preparation contacted or bound a network endpoint");
        System.out.println("PASS public socket preparation creates no connection/binding and closes on success, IOException and security failure");
    }
    private static boolean extnetLoaded()throws IOException{
        try(Stream<String> lines=Files.lines(Paths.get("/proc/self/maps"))){return lines.anyMatch(s->s.contains("/libextnet.so"));}
    }
    private static long descriptors()throws IOException{
        try(Stream<java.nio.file.Path> files=Files.list(Paths.get("/proc/self/fd"))){return files.count();}
    }
    private static void real(boolean requireExtnet)throws Exception{
        if(!System.getProperty("os.name").equals("Linux")){System.out.println("SKIP Linux native-load observation on this OS");return;}
        boolean before=extnetLoaded();ClientSocketRuntime.prepare();boolean after=extnetLoaded();
        boolean graphicsLoaded;
        try(Stream<String> lines=Files.lines(Paths.get("/proc/self/maps"))){graphicsLoaded=lines.anyMatch(s->s.contains("/liblwjgl")||s.contains("/libglfw"));}
        require(!graphicsLoaded,"Socket preparation initialized native graphics");
        if(requireExtnet)require(after,"Public option call did not load the observed JDK21 libextnet library");
        long baseline=descriptors();for(int i=0;i<64;i++)ClientSocketRuntime.prepare();long remaining=descriptors();
        require(baseline==remaining,"Repeated preparation leaked file descriptors");
        System.out.println("{\"javaVersion\":\""+System.getProperty("java.version")+"\",\"libextnetBefore\":"+before+",\"libextnetAfter\":"+after+",\"nativeGraphicsLoaded\":"+graphicsLoaded+",\"fdBefore\":"+baseline+",\"fdAfter64Calls\":"+remaining+"}");
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("--real")){real(args.length>1&&args[1].equals("--require-extnet"));return;}
        contract();
    }
}
