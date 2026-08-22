package com.openrsc.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.Arrays;

/** Exact byte regressions across the non-ISAAC framing families. */
public final class RSCProtocolEncoderWireTest {
	public static void main(final String[] args) {
		assertTcp((short)-1, packet(42, 1, 2), 0, 5, 42, 1, 2);
		assertTcp((short)14, packet(42, 1, 2), 0, 3, 42, 1, 2);
		assertTcp((short)93, packet(42, 1, 2), 3, 2, 42, 1);
		assertRaw(9, 8, 7);
		assertWeb((short)-1, packet(42, 1, 2), 0, 5, 42, 1, 2);
		System.out.println("PASS: exact TCP/WebSocket packet bytes preserved");
	}

	private static Packet packet(final int opcode, final int... payload) {
		final ByteBuf bytes = Unpooled.buffer(payload.length);
		for (int value : payload) bytes.writeByte(value);
		return new Packet(opcode, bytes);
	}

	private static void assertTcp(final short client, final Packet packet,
		final int... expected) {
		final EmbeddedChannel channel = new EmbeddedChannel(new RSCProtocolEncoder());
		attachment(channel, client);
		if (!channel.writeOutbound(packet)) throw new AssertionError("no TCP output");
		final ByteBuf actual = channel.readOutbound();
		assertBytes(actual, expected);
		actual.release();
		channel.finishAndReleaseAll();
	}

	private static void assertRaw(final int... expected) {
		final EmbeddedChannel channel = new EmbeddedChannel(new RSCProtocolEncoder());
		attachment(channel, (short)-1);
		final ByteBuf raw = Unpooled.buffer(expected.length);
		for (int value : expected) raw.writeByte(value);
		if (!channel.writeOutbound(new Packet(-1, raw))) {
			throw new AssertionError("no raw output");
		}
		final ByteBuf actual = channel.readOutbound();
		assertBytes(actual, expected);
		actual.release();
		channel.finishAndReleaseAll();
	}

	private static void assertWeb(final short client, final Packet packet,
		final int... expected) {
		final EmbeddedChannel channel = new EmbeddedChannel(new RSCProtocolWebEncoder());
		attachment(channel, client);
		if (!channel.writeOutbound(packet)) throw new AssertionError("no WS output");
		final BinaryWebSocketFrame frame = channel.readOutbound();
		assertBytes(frame.content(), expected);
		frame.release();
		channel.finishAndReleaseAll();
	}

	private static void attachment(final EmbeddedChannel channel,
		final short client) {
		final ConnectionAttachment attachment = new ConnectionAttachment();
		attachment.authenticClient.set(client);
		channel.attr(RSCConnectionHandler.attachment).set(attachment);
	}

	private static void assertBytes(final ByteBuf actual, final int... expected) {
		final byte[] bytes = new byte[actual.readableBytes()];
		actual.getBytes(actual.readerIndex(), bytes);
		final byte[] expectedBytes = new byte[expected.length];
		for (int index = 0; index < expected.length; index++) {
			expectedBytes[index] = (byte)expected[index];
		}
		if (!Arrays.equals(bytes, expectedBytes)) {
			throw new AssertionError("wire mismatch expected="
				+ Arrays.toString(expectedBytes) + " actual=" + Arrays.toString(bytes));
		}
	}
}
