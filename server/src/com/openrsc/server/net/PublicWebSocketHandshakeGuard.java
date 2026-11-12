package com.openrsc.server.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Direct public WSS only: bound handshake time, defer legacy session bytes until upgrade. */
public final class PublicWebSocketHandshakeGuard extends ChannelInboundHandlerAdapter {
    private ChannelHandlerContext context;
    private ScheduledFuture<?> timeout;
    private boolean complete;
    @Override public void handlerAdded(ChannelHandlerContext ctx) { context = ctx; }
    @Override public void channelActive(ChannelHandlerContext ctx) {
        timeout = ctx.executor().schedule(() -> ctx.close(), 10, TimeUnit.SECONDS);
        // RSCConnectionHandler registration/IP admission still happens normally.
        // Its session-ID timer must not run before TLS + WebSocket upgrade.
    }
    public void complete() {
        if (complete || !context.channel().isActive()) return;
        complete = true;
        if (timeout != null) timeout.cancel(false);
        context.fireChannelActive();
    }
    @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (timeout != null) timeout.cancel(false);
        super.channelInactive(ctx);
    }
}
