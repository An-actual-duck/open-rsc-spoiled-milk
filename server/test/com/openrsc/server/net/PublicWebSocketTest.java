package com.openrsc.server.net;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.nio.file.*;
import java.util.*;

/** Embedded production handlers + real in-memory TLS. No listening socket or database. */
public final class PublicWebSocketTest {
    static int checks;
    static final java.io.PrintStream OUT=System.out,ERR=System.err;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Observe extends ChannelInboundHandlerAdapter {
        int active,read;Object last;
        public void channelActive(ChannelHandlerContext ctx){active++;}
        public void channelRead(ChannelHandlerContext ctx,Object value){read++;last=value;}
    }
    static void pump(EmbeddedChannel a,EmbeddedChannel b){Object value;while((value=a.readOutbound())!=null)b.writeInbound(value);}
    static EmbeddedChannel[] tls(SslContext context,Observe target)throws Exception {
        EmbeddedChannel server=new EmbeddedChannel(context.newHandler(UnpooledByteBufAllocator.DEFAULT),new HttpRequestHandler("/"),target);
        EmbeddedChannel client=new EmbeddedChannel(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build().newHandler(UnpooledByteBufAllocator.DEFAULT));
        for(int i=0;i<100;i++){pump(client,server);pump(server,client);client.runPendingTasks();server.runPendingTasks();if(server.pipeline().get(SslHandler.class).handshakeFuture().isSuccess()&&client.pipeline().get(SslHandler.class).handshakeFuture().isSuccess())break;}
        check(server.pipeline().get(SslHandler.class).handshakeFuture().isSuccess(),"Real TLS handshake completed");return new EmbeddedChannel[]{server,client};
    }
    static FullHttpRequest request(){FullHttpRequest r=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/");r.headers().set(HttpHeaderNames.ORIGIN,PublicWebSocketPolicy.ORIGIN);r.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL,"binary");return r;}
    static void admission(SslContext context,boolean enabled)throws Exception {
        for(int kind=0;kind<9;kind++){
            Observe observe=new Observe();EmbeddedChannel[] pair=tls(context,observe);EmbeddedChannel server=pair[0];FullHttpRequest r=request();
            if(kind==1)r.headers().set(HttpHeaderNames.ORIGIN,"https://evil.example");
            if(kind==2)r.headers().remove(HttpHeaderNames.ORIGIN);
            if(kind==3)r.headers().add(HttpHeaderNames.ORIGIN,PublicWebSocketPolicy.ORIGIN);
            if(kind==4)r.setUri("/?forged=1");
            if(kind==5)r.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL,"binary, forged");
            if(kind==6)r.setMethod(HttpMethod.POST);
            if(kind==7)r.headers().set("X-Forwarded-For","127.0.0.1").set("Forwarded","for=127.0.0.1");
            if(kind==8)r.headers().set(HttpHeaderNames.ORIGIN,"null");
            Object peer=server.remoteAddress();
            server.pipeline().context(SslHandler.class).fireChannelRead(r);
            boolean pass=kind!=4&&(!enabled||kind==0||kind==7);
            check((observe.read==1)==pass,"Origin/path/binary/method admission case"+kind);
            check(server.remoteAddress().equals(peer),"Forwarded headers never rewrite socket peer");
            if(pass)((FullHttpRequest)observe.last).release();
            for(EmbeddedChannel channel:pair)channel.finishAndReleaseAll();
        }
        Observe observe=new Observe();EmbeddedChannel plain=new EmbeddedChannel(new HttpRequestHandler("/"),observe);plain.writeInbound(request());
        check((observe.read==0)==enabled,"Plaintext refused only for explicit public WSS");if(observe.last!=null)((FullHttpRequest)observe.last).release();plain.finishAndReleaseAll();
    }
    static void guard()throws Exception {
        Observe observe=new Observe();PublicWebSocketHandshakeGuard guard=new PublicWebSocketHandshakeGuard(20);
        EmbeddedChannel channel=new EmbeddedChannel(guard,observe);
        check(observe.active==0,"No legacy session bytes before upgrade");guard.complete();guard.complete();check(observe.active==1,"Upgrade forwards exactly one active event");
        Thread.sleep(30);channel.runScheduledPendingTasks();check(channel.isActive(),"Successful upgrade cancels handshake timeout");channel.finishAndReleaseAll();
        EmbeddedChannel slow=new EmbeddedChannel(new PublicWebSocketHandshakeGuard(1),new Observe());Thread.sleep(5);slow.runScheduledPendingTasks();check(!slow.isActive(),"Incomplete handshake deadline closes connection");slow.finishAndReleaseAll();
    }
    static void reload(Path root)throws Exception {
        Path cert=root.resolve("current-cert.pem"),key=root.resolve("current-key.pem");Files.copy(root.resolve("one-cert.pem"),cert);Files.copy(root.resolve("one-key.pem"),key);
        ReloadingPublicWebSocketTls source=new ReloadingPublicWebSocketTls(cert.toString(),key.toString());SslContext first=source.context();check(first==source.refresh(),"Unchanged certificate reuses lastgood context");
        Files.copy(root.resolve("two-key.pem"),key,StandardCopyOption.REPLACE_EXISTING);
        boolean refused=false;try{source.refresh();}catch(Exception expected){refused=true;}check(refused&&source.context()==first,"Mismatched renewal rejected and lastgood context retained");
        Files.copy(root.resolve("two-cert.pem"),cert,StandardCopyOption.REPLACE_EXISTING);SslContext second=source.refresh();check(second!=first,"Matching valid renewal atomically replaces context");
        Files.copy(root.resolve("wrong-cert.pem"),cert,StandardCopyOption.REPLACE_EXISTING);Files.copy(root.resolve("wrong-key.pem"),key,StandardCopyOption.REPLACE_EXISTING);
        refused=false;try{source.refresh();}catch(Exception expected){refused=true;}check(refused&&source.context()==second,"Wrong hostname rejected, lastgood retained");
        Files.write(cert,new byte[]{1,2,3});refused=false;try{source.refresh();}catch(Exception expected){refused=true;}check(refused&&source.context()==second,"Malformed renewal rejected, lastgood retained");
        Files.delete(key);check(source.context()==second,"Rate bounded lookup keeps lastgood without reread storm");
    }
    static void ipAdmission()throws Exception {
        Class<?> u=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field uf=u.getDeclaredField("theUnsafe");uf.setAccessible(true);
        com.openrsc.server.Server server=(com.openrsc.server.Server)u.getMethod("allocateInstance",Class.class).invoke(uf.get(null),com.openrsc.server.Server.class);
        System.setOut(OUT);System.setErr(ERR);
        com.openrsc.server.ServerConfiguration config=new com.openrsc.server.ServerConfiguration();config.MAX_CONNECTIONS_PER_IP=1;config.MAX_CONNECTIONS_PER_SECOND=100;
        java.lang.reflect.Field f=com.openrsc.server.Server.class.getDeclaredField("config");f.setAccessible(true);f.set(server,config);
        RSCPacketFilter filter=new RSCPacketFilter(server);EmbeddedChannel a=new EmbeddedChannel(),b=new EmbeddedChannel(),c=new EmbeddedChannel();
        check(filter.shouldAllowConnection(a,"198.51.100.1",false),"First actual-IP connection allowed");
        check(!filter.shouldAllowConnection(b,"198.51.100.1",false),"Same-IP limit still enforced");
        check(filter.shouldAllowConnection(c,"198.51.100.2",false),"Independent browser IP does not collapse into shared loopback bucket");
        filter.removeConnection("198.51.100.1",a);filter.removeConnection("198.51.100.1",b);filter.removeConnection("198.51.100.2",c);
        check(filter.getConnectionCount("198.51.100.1")==0&&filter.getConnectionCount("198.51.100.2")==0,"Real per-IP accounting removes independently");
        a.finishAndReleaseAll();b.finishAndReleaseAll();c.finishAndReleaseAll();
    }
    static void productionUpgrade(SslContext tls)throws Exception {
        Class<?> u=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field uf=u.getDeclaredField("theUnsafe");uf.setAccessible(true);
        com.openrsc.server.Server server=(com.openrsc.server.Server)u.getMethod("allocateInstance",Class.class).invoke(uf.get(null),com.openrsc.server.Server.class);
        System.setOut(OUT);System.setErr(ERR);
        java.lang.reflect.Field f=com.openrsc.server.Server.class.getDeclaredField("sslcontext");f.setAccessible(true);f.set(server,tls);
        Observe observe=new Observe();EmbeddedChannel backend=new EmbeddedChannel();
        backend.attr(RSCMultiPortDecoder.attachment).set(new ConnectionAttachment());
        backend.pipeline().addLast("decoder_ws",new RSCMultiPortDecoder(RSCMultiPortDecoder.DecoderMode.WS,server));
        PublicWebSocketHandshakeGuard guard=new PublicWebSocketHandshakeGuard();
        backend.pipeline().addLast("public-ws-handshake",guard);backend.pipeline().addLast(com.openrsc.server.Server.rscConnectionHandlerId,observe);
        // Added to an already-active embedded channel; exercise its initial event explicitly.
        backend.pipeline().fireChannelActive();check(observe.active==0,"Production WS stack defers legacy active event");
        EmbeddedChannel client=new EmbeddedChannel(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build().newHandler(UnpooledByteBufAllocator.DEFAULT),new HttpClientCodec(),new HttpObjectAggregator(65536));
        for(int i=0;i<100;i++){pump(client,backend);pump(backend,client);client.runPendingTasks();backend.runPendingTasks();if(client.pipeline().get(SslHandler.class).handshakeFuture().isSuccess())break;}
        FullHttpRequest request=request();request.headers().set(HttpHeaderNames.HOST,"play.rscspoiledmilk.com:43505").set(HttpHeaderNames.UPGRADE,"websocket").set(HttpHeaderNames.CONNECTION,"Upgrade").set(HttpHeaderNames.SEC_WEBSOCKET_VERSION,"13").set(HttpHeaderNames.SEC_WEBSOCKET_KEY,"dGhlIHNhbXBsZSBub25jZQ==");
        client.writeOutbound(request);
        for(int i=0;i<10;i++){pump(client,backend);pump(backend,client);backend.runPendingTasks();client.runPendingTasks();}
        FullHttpResponse response=client.readInbound();
        check(response!=null&&response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS),"Actual decoder/TLS/HTTP/WS pipeline upgrades to101");
        check("binary".equals(response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL)),"Actual handshake selects binary subprotocol");response.release();
        check(observe.active==1,"Only successful actual WS upgrade activates game protocol");
        check(backend.pipeline().get(PublicWebSocketHandshakeGuard.class)!=null&&backend.pipeline().get(HttpRequestHandler.class)==null,"Guard completion preserves game pipeline and removes only HTTP request handler");
        client.pipeline().remove(HttpClientCodec.class);client.pipeline().remove(HttpObjectAggregator.class);client.pipeline().addLast(new WebSocket13FrameEncoder(true));
        client.writeOutbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{0,1,19})));pump(client,backend);
        check(observe.last instanceof Packet&&((Packet)observe.last).getID()==19&&((Packet)observe.last).getLength()==0,"Actual masked binary WSS frame decodes original game configuration packet");
        ((Packet)observe.last).getBuffer().release();
        backend.finishAndReleaseAll();client.finishAndReleaseAll();
    }
    static void nativeParity() {
        Observe observe=new Observe();EmbeddedChannel channel=new EmbeddedChannel();channel.attr(RSCMultiPortDecoder.attachment).set(new ConnectionAttachment());
        channel.pipeline().addLast("decoder_tcp",new RSCMultiPortDecoder(RSCMultiPortDecoder.DecoderMode.TCP,null));channel.pipeline().addLast("handler",observe);channel.pipeline().fireChannelActive();
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{0,1,19}));
        check(observe.active==1&&observe.last instanceof Packet&&((Packet)observe.last).getID()==19&&((Packet)observe.last).getLength()==0,"Actual native decoder keeps immediate active event and byte-identical packet semantics");
        check(channel.pipeline().get(PublicWebSocketHandshakeGuard.class)==null&&channel.pipeline().get(HttpRequestHandler.class)==null&&!channel.attr(RSCMultiPortDecoder.attachment).get().isWebSocket.get(),"Native stack has no public WSS handler/policy substitution");
        ((Packet)observe.last).getBuffer().release();channel.finishAndReleaseAll();
    }
    public static void main(String[] args)throws Exception {
        boolean enabled=PublicWebSocketPolicy.enabled();Path root=Paths.get(args[0]);SslContext context=new ReloadingPublicWebSocketTls(root.resolve("one-cert.pem").toString(),root.resolve("one-key.pem").toString()).context();
        SslContext ec=new ReloadingPublicWebSocketTls(root.resolve("ec-cert.pem").toString(),root.resolve("ec-key.pem").toString()).context();
        EmbeddedChannel[] ecPair=tls(ec,new Observe());for(EmbeddedChannel channel:ecPair)channel.finishAndReleaseAll();
        admission(context,enabled);guard();reload(root);ipAdmission();productionUpgrade(context);nativeParity();System.out.println("PASS checks="+checks+" public="+enabled+" real-memory-TLS=true no-listeners=true");
    }
}
