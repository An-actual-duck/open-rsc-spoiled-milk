package com.openrsc.server.net;

import com.openrsc.server.diagnostics.AuthenticatedNetworkBenchmarkProbe;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;

import java.util.List;

public final class RSCProtocolWebEncoder extends MessageToMessageEncoder<Packet> implements AttributeMap {
	private final RSCProtocolEncoderMain encoder = new RSCProtocolEncoderMain();

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet message, List<Object> out) throws Exception {
		final long start = AuthenticatedNetworkBenchmarkProbe.isEnabled()
			? System.nanoTime() : 0L;
		final ByteBuf encoded = ctx.alloc().buffer();
		try {
			encoder.encode(ctx, message, encoded);
			if (AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
				AuthenticatedNetworkBenchmarkProbe.recordSerialization(
					System.nanoTime() - start, encoded.readableBytes());
			}
			out.add(new BinaryWebSocketFrame(encoded));
		} catch (Exception failure) {
			encoded.release();
			throw failure;
		}
	}

	@Override
	public <T> Attribute<T> attr(AttributeKey<T> attributeKey) {
		return null;
	}

	@Override
	public <T> boolean hasAttr(AttributeKey<T> attributeKey) {
		return false;
	}
}
