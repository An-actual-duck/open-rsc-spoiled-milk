package com.openrsc.server.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslHandler;

/** Explicit direct-WSS deployment policy. Never interprets forwarding headers. */
public final class PublicWebSocketPolicy {
    public static final String ORIGIN = "https://play.rscspoiledmilk.com";
    private static final String configured = System.getProperty("spoiledmilk.publicWebSocketOrigin", "");
    static {
        if (!configured.isEmpty() && !ORIGIN.equals(configured))
            throw new IllegalArgumentException("Public WebSocket origin must be " + ORIGIN);
    }
    private PublicWebSocketPolicy() { }
    public static boolean enabled() { return !configured.isEmpty(); }

    static boolean accepts(ChannelHandlerContext ctx, FullHttpRequest request) {
        SslHandler tls = ctx.pipeline().get(SslHandler.class);
        return tls != null && tls.handshakeFuture().isSuccess()
            && HttpMethod.GET.equals(request.method()) && "/".equals(request.uri())
            && request.headers().getAll(HttpHeaderNames.ORIGIN).size() == 1
            && ORIGIN.equals(request.headers().get(HttpHeaderNames.ORIGIN))
            && request.headers().getAll(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL).size() == 1
            && "binary".equals(request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
    }
}
