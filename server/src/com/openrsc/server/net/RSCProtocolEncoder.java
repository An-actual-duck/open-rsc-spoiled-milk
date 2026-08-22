package com.openrsc.server.net;

import com.openrsc.server.diagnostics.AuthenticatedNetworkBenchmarkProbe;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;

public final class RSCProtocolEncoder extends MessageToByteEncoder<Packet> implements AttributeMap {
	private final RSCProtocolEncoderMain encoder = new RSCProtocolEncoderMain();

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet message, ByteBuf outBuffer) throws Exception {
		final long start = AuthenticatedNetworkBenchmarkProbe.isEnabled()
			? System.nanoTime() : 0L;
		encoder.encode(ctx, message, outBuffer);
		if (AuthenticatedNetworkBenchmarkProbe.isEnabled()) {
			AuthenticatedNetworkBenchmarkProbe.recordSerialization(
				System.nanoTime() - start, outBuffer.readableBytes());
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
