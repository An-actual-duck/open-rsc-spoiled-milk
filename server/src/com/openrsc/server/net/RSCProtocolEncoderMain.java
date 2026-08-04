package com.openrsc.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RSCProtocolEncoderMain {
	public static final AttributeKey<ConnectionAttachment> attachment = AttributeKey.valueOf("conn-attachment");
	private static final Logger LOGGER = LogManager.getLogger();
	private static final boolean LAYERED_TERRAIN_PROTOCOL_DIAGNOSTICS =
		Boolean.parseBoolean(System.getenv(
			"SPOILED_MILK_BOUNDARY_DIAGNOSTICS"));

	private boolean isInauthenticPacket(int opcode) {
		switch (opcode) {
			case 19: // server configs for inauthentic client
				return true;
			default:
				return false;
		}
	}
	public ByteBuf encode(ChannelHandlerContext ctx, Packet message) throws Exception {
		final Channel channel = ctx.channel();
		ConnectionAttachment att = channel.attr(attachment).get();
		ByteBuf outBuffer = null;
		logLayeredPacketEncoding(channel, message);

		if (att.player != null && att.player.get() != null) {
			if (att.player.get().getWorld().getServer().getConfig().WANT_PCAP_LOGGING) {
				att.pcapLogger.get().addPacket(message, true); // incoming from perspective of client
			}
		}

		if (!message.isRaw()) {
			Short authenticClient = null;
			if (att.authenticClient.get() != null) {
				authenticClient = att.authenticClient.get();
			}

			if (authenticClient == null || isInauthenticPacket(message.getID()) || authenticClient == -1) {
				// This is code only to support RSCL based clients which simplified the network protocol
				int packetLength = message.getBuffer().readableBytes();
				PacketFrameLengthGuard.requireSimplifiedPayloadLength(
					packetLength);
				int frameLength = Math.addExact(packetLength, 3);
				ByteBuf buffer = Unpooled.buffer(frameLength);

				buffer.writeShort(frameLength);
				buffer.writeByte(message.getID());

				buffer.writeBytes(message.getBuffer());
				outBuffer = buffer;
			} else if (authenticClient >= 183) {
				// Modern Authentic Packet Handling (With ISAAC)
				// Don't know exactly when ISAAC started getting used, but mudclient 183 from 2004-02-04 uses opcode shuffling
				int packetLength = Math.addExact(
					message.getBuffer().readableBytes(), 1); // + 1 for opcode
				PacketFrameLengthGuard.requireAuthenticPacketLength(
					packetLength);

				/* debug info
				if (message.getID() != 191 && message.getID() != 79 && message.getID() != 48) {
					System.out.println(String.format("starting to handle opcode %d", message.getID()));
				}
				*/

				ByteBuf buffer;
				int encodedOpcode;
				if (packetLength >= 160) {
					buffer = Unpooled.buffer(packetLength + 2); // + 2 to hold length
					buffer.writeByte((byte) (packetLength / 256 + 160));
					buffer.writeByte((byte) (packetLength & 0xFF));

					encodedOpcode = att.ISAAC.get().encodeOpcode(message.getID());
					buffer.writeByte(encodedOpcode);

					buffer.writeBytes(message.getBuffer());

				} else {
					buffer = Unpooled.buffer(packetLength + 1); // + 1 to hold length
					buffer.writeByte((byte) packetLength);
					int bufferLen = message.getBuffer().readableBytes();

					if (packetLength != 1) {
						// Strangely, the last byte of the Payload goes between length and encoded opcode
						try {
							buffer.writeByte(message.getBuffer().slice(bufferLen - 1, 1).readByte());
						} catch (IndexOutOfBoundsException e) {
							// This should probably never happen, but "Just In Case" it is good to handle it b/c otherwise it fails silently
							System.out.println(String.format("Warning: index out of bounds on sending last byte of opcode %d", message.getID()));
							System.out.println(e.toString());
							if (message.getBuffer().hasArray()) {
								byte[] bArr = message.getBuffer().array();
								buffer.writeByte(bArr[bArr.length - 1]);
							}
						}

						encodedOpcode = att.ISAAC.get().encodeOpcode(message.getID());
						buffer.writeByte(encodedOpcode);

						buffer.writeBytes(message.getBuffer().slice(0, bufferLen - 1));
					} else {
						// single opcode payload
						encodedOpcode = att.ISAAC.get().encodeOpcode(message.getID());
						buffer.writeByte(encodedOpcode);
					}
				}

				outBuffer = buffer;
			} else if (authenticClient >= 93) {
				int packetLength = Math.addExact(
					message.getBuffer().readableBytes(), 1); // + 1 for opcode
				PacketFrameLengthGuard.requireAuthenticPacketLength(
					packetLength);

				ByteBuf buffer;
				if (packetLength >= 160) {
					buffer = Unpooled.buffer(packetLength + 2); // + 2 to hold length
					buffer.writeByte((byte) (packetLength / 256 + 160));
					buffer.writeByte((byte) (packetLength & 0xFF));

					buffer.writeByte(message.getID());
					buffer.writeBytes(message.getBuffer());

				} else {
					buffer = Unpooled.buffer(packetLength + 1); // + 1 to hold length
					buffer.writeByte((byte) packetLength);
					int bufferLen = message.getBuffer().readableBytes();

					if (packetLength != 1) {
						// Strangely, the last byte of the Payload goes between length and encoded opcode
						try {
							buffer.writeByte(message.getBuffer().slice(bufferLen - 1, 1).readByte());
						} catch (IndexOutOfBoundsException e) {
							// This should probably never happen, but "Just In Case" it is good to handle it b/c otherwise it fails silently
							System.out.println(String.format("Warning: index out of bounds on sending last byte of opcode %d", message.getID()));
							System.out.println(e.toString());
							if (message.getBuffer().hasArray()) {
								byte[] bArr = message.getBuffer().array();
								buffer.writeByte(bArr[bArr.length - 1]);
							}
						}

						buffer.writeByte(message.getID());
						buffer.writeBytes(message.getBuffer().slice(0, bufferLen - 1));
					} else {
						// single opcode payload
						buffer.writeByte(message.getID());
					}
				}

				outBuffer = buffer;
			} else if (authenticClient >= 14) {
				//TODO: verify if always holds like this
				int packetLength = message.getBuffer().readableBytes();
				PacketFrameLengthGuard.requireLegacyPayloadLength(packetLength);
				ByteBuf buffer = Unpooled.buffer(
					Math.addExact(packetLength, 3));

				buffer.writeShort(packetLength + 1);
				buffer.writeByte(message.getID());

				buffer.writeBytes(message.getBuffer());
				outBuffer = buffer;
			}
		} else {
			outBuffer = message.getBuffer();
		}

		return outBuffer;
	}

	/**
	 * Records only packet identity, framing size, and channel pressure for the
	 * two packets involved in atomic terrain activation. No account, address,
	 * or packet contents are retained. The encoder boundary distinguishes a
	 * packet that was merely queued by the game thread from one that actually
	 * reached Netty framing.
	 */
	private static void logLayeredPacketEncoding(
			final Channel channel,
			final Packet message) {
		if (!LAYERED_TERRAIN_PROTOCOL_DIAGNOSTICS
			|| message == null
			|| (message.getID() != 143 && message.getID() != 154)) {
			return;
		}
		LOGGER.info(
			"LAYERED_PACKET_ENCODED packet={} opcode={} bytes={} identity={} "
				+ "writable={} bytesBeforeUnwritable={}",
			message.getPacketNumber(),
			message.getID(),
			message.getBuffer().readableBytes(),
			layeredPacketIdentity(message),
			channel.isWritable(),
			channel.bytesBeforeUnwritable());
	}

	private static String layeredPacketIdentity(final Packet message) {
		final ByteBuf payload = message.getBuffer().duplicate();
		final int start = payload.readerIndex();
		final int readable = payload.readableBytes();
		if (readable < 1) {
			return "empty";
		}
		final int protocol = payload.getUnsignedByte(start);
		if (message.getID() == 154) {
			if (protocol == 6 && readable >= 23) {
				return "terrain-page protocol=6,sequence="
					+ payload.getInt(start + 1)
					+ ",context=" + payload.getInt(start + 5)
					+ ",page=" + payload.getUnsignedShort(start + 17)
					+ "/" + payload.getUnsignedShort(start + 19);
			}
			return "terrain-stage protocol=" + protocol;
		}

		int pageOffset = start + 1 + 4 + 2 + 2;
		String contextIdentity = "";
		if (protocol >= 6) {
			if (pageOffset + 4 > start + readable) {
				return "scene-baseline protocol=" + protocol
					+ ",truncated-context";
			}
			contextIdentity = ",context=" + payload.getInt(pageOffset);
			pageOffset += 4;
		}
		pageOffset += 6 * 2 + 5 * 4;
		if (protocol >= 8) {
			pageOffset += 4 + 4 + 1 + 1 + 2 + 2 + 4 + 4;
		}
		if (pageOffset + 5 > start + readable) {
			return "scene-baseline protocol=" + protocol
				+ ",truncated-header";
		}
		return "scene-baseline protocol=" + protocol
			+ contextIdentity
			+ ",category=" + payload.getUnsignedByte(pageOffset)
			+ ",page=" + payload.getUnsignedShort(pageOffset + 1)
			+ "/" + payload.getUnsignedShort(pageOffset + 3);
	}
}
