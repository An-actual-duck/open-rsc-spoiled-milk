package com.openrsc.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.nio.charset.StandardCharsets;

/** Regression coverage for custom-client framing before authentication. */
public final class RSCProtocolDecoderWireTest {
	public static void main(final String[] args) {
		assertCustomLogin(277);
		assertCustomLogin(278);
		assertCustomLogin(283);
		assertFragmentedLoginAtEveryBoundary();
		assertInitialConfigAndLegacyTrafficRemainDistinct();
		assertMalformedFramesFailClosed();
		System.out.println("PASS: undecided custom-client framing preserved");
	}

	private static void assertCustomLogin(final int declaredLength) {
		final ConnectionAttachment attachment = new ConnectionAttachment();
		final EmbeddedChannel channel = channel(attachment);
		channel.writeInbound(Unpooled.wrappedBuffer(loginFrame(declaredLength)));
		assertTrue(Short.valueOf((short)-1).equals(attachment.authenticClient.get()),
			"login length " + declaredLength + " did not select custom framing");
		assertPacket(channel, 0, declaredLength - 1,
			"login length " + declaredLength);
		assertTrue(channel.readInbound() == null,
			"login length " + declaredLength + " delivered twice");
		channel.finishAndReleaseAll();
	}

	private static void assertFragmentedLoginAtEveryBoundary() {
		final byte[] frame = loginFrame(278);
		for (int cut = 1; cut < frame.length; cut++) {
			final ConnectionAttachment attachment = new ConnectionAttachment();
			final EmbeddedChannel channel = channel(attachment);
			channel.writeInbound(Unpooled.wrappedBuffer(frame, 0, cut));
			assertTrue(channel.readInbound() == null,
				"fragment delivered before completion at byte " + cut);
			assertTrue(attachment.authenticClient.get() == null,
				"fragment selected a protocol before completion at byte " + cut);
			channel.writeInbound(Unpooled.wrappedBuffer(frame, cut, frame.length - cut));
			assertTrue(Short.valueOf((short)-1).equals(attachment.authenticClient.get()),
				"complete fragmented login did not select custom framing at byte " + cut);
			assertPacket(channel, 0, 277, "fragmented login at byte " + cut);
			assertTrue(channel.readInbound() == null,
				"fragmented login delivered twice at byte " + cut);
			channel.finishAndReleaseAll();
		}
	}

	private static void assertInitialConfigAndLegacyTrafficRemainDistinct() {
		final ConnectionAttachment custom = new ConnectionAttachment();
		final EmbeddedChannel customChannel = channel(custom);
		customChannel.writeInbound(Unpooled.wrappedBuffer(new byte[] {0, 1, 19}));
		assertTrue(Short.valueOf((short)-1).equals(custom.authenticClient.get()),
			"initial configuration request did not select custom framing");
		assertPacket(customChannel, 19, 0, "initial configuration request");
		customChannel.finishAndReleaseAll();

		final ConnectionAttachment legacy = new ConnectionAttachment();
		final EmbeddedChannel legacyChannel = channel(legacy);
		legacyChannel.writeInbound(Unpooled.wrappedBuffer(
			new byte[] {1, 19, 2, 1, 42}));
		assertPacket(legacyChannel, 19, 1, "legacy configuration request");
		assertPacket(legacyChannel, 42, 2, "legacy packet after configuration request");
		assertTrue(legacy.authenticClient.get() == null,
			"legacy traffic was classified as custom");
		legacyChannel.finishAndReleaseAll();

		final ConnectionAttachment collision = new ConnectionAttachment();
		final EmbeddedChannel collisionChannel = channel(collision);
		collisionChannel.writeInbound(Unpooled.wrappedBuffer(
			new byte[] {8, 22, 0, 7, 1, 2, 3, 4, 5}));
		assertPacket(collisionChannel, 0, 8, "legacy login-prefix collision");
		assertTrue(collision.authenticClient.get() == null,
			"legacy login-prefix collision was classified as custom");
		collisionChannel.finishAndReleaseAll();
	}

	private static void assertMalformedFramesFailClosed() {
		assertDecodeFailure(new byte[] {0, 0, 0}, false, "zero length");
		assertDecodeFailure(new byte[] {32, 1, 0}, false, "oversized login");

		final byte[] malformed = loginFrame(80);
		malformed[16] = 3;
		assertDecodeFailure(malformed, false, "unsupported encryption mode");

		final byte[] complete = loginFrame(278);
		final byte[] truncated = new byte[24];
		System.arraycopy(complete, 0, truncated, 0, truncated.length);
		assertDecodeFailure(truncated, true, "truncated login");
	}

	private static byte[] loginFrame(final int declaredLength) {
		assertTrue(declaredLength >= 27, "login fixture is too short");
		final byte[] frame = new byte[declaredLength + 2];
		frame[0] = (byte)(declaredLength >>> 8);
		frame[1] = (byte)declaredLength;
		frame[2] = 0;
		frame[3] = 0;
		frame[4] = 0;
		frame[5] = 0;
		frame[6] = 39;
		frame[7] = 68; // Custom client version 10052.

		int cursor = 8;
		final byte[] username = "Builder".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(username, 0, frame, cursor, username.length);
		cursor += username.length;
		frame[cursor++] = 10;
		frame[cursor++] = 1;
		frame[cursor++] = 0;
		frame[cursor++] = 1;
		frame[cursor++] = 42;
		frame[cursor++] = 0;
		frame[cursor++] = 1;
		frame[cursor++] = 43;
		cursor += 8;
		assertTrue(cursor <= frame.length, "login fixture payload exceeds frame");
		return frame;
	}

	private static EmbeddedChannel channel(final ConnectionAttachment attachment) {
		final EmbeddedChannel channel = new EmbeddedChannel(new RSCProtocolDecoder());
		channel.attr(RSCProtocolDecoder.attachment).set(attachment);
		return channel;
	}

	private static void assertPacket(final EmbeddedChannel channel, final int opcode,
		final int payloadLength, final String label) {
		final Packet packet = channel.readInbound();
		assertTrue(packet != null, label + " did not produce a packet");
		assertTrue(packet.getID() == opcode, label + " opcode mismatch");
		assertTrue(packet.getLength() == payloadLength, label + " length mismatch");
		packet.getBuffer().release();
	}

	private static void assertDecodeFailure(final byte[] frame, final boolean finish,
		final String label) {
		final ConnectionAttachment attachment = new ConnectionAttachment();
		final EmbeddedChannel channel = channel(attachment);
		boolean failed = false;
		try {
			channel.writeInbound(Unpooled.wrappedBuffer(frame));
			if (finish) {
				channel.finish();
			}
		} catch (RuntimeException expected) {
			failed = true;
		} finally {
			channel.close();
		}
		assertTrue(failed, label + " did not fail closed");
		assertTrue(channel.readInbound() == null, label + " delivered a packet");
		assertTrue(attachment.authenticClient.get() == null,
			label + " selected custom framing");
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
