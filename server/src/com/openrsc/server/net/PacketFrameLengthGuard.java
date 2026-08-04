package com.openrsc.server.net;

/**
 * Bounds the legacy packet-length fields before Java narrows them to bytes or
 * shorts. The maintained custom client uses an unsigned two-byte total-frame
 * length, while authentic clients use the older 160-based extended length.
 */
public final class PacketFrameLengthGuard {
	public static final int MAX_UNSIGNED_SHORT = 0xFFFF;
	public static final int SIMPLIFIED_FRAME_OVERHEAD_BYTES = 3;
	public static final int MAX_SIMPLIFIED_PAYLOAD_BYTES =
		MAX_UNSIGNED_SHORT - SIMPLIFIED_FRAME_OVERHEAD_BYTES;
	public static final int MAX_AUTHENTIC_PACKET_LENGTH =
		(0xFF - 160) * 256 + 0xFF;
	public static final int MAX_LEGACY_PAYLOAD_BYTES =
		MAX_UNSIGNED_SHORT - 1;

	private PacketFrameLengthGuard() {
	}

	public static void requireSimplifiedPayloadLength(
			final int payloadBytes) {
		requireRange(
			payloadBytes,
			MAX_SIMPLIFIED_PAYLOAD_BYTES,
			"Simplified packet payload");
	}

	/** Packet length includes the opcode for the 160-based framing form. */
	public static void requireAuthenticPacketLength(
			final int packetLength) {
		requireRange(
			packetLength,
			MAX_AUTHENTIC_PACKET_LENGTH,
			"Authentic packet");
	}

	public static void requireLegacyPayloadLength(final int payloadBytes) {
		requireRange(
			payloadBytes,
			MAX_LEGACY_PAYLOAD_BYTES,
			"Legacy packet payload");
	}

	private static void requireRange(
			final int length,
			final int maximum,
			final String label) {
		if (length < 0 || length > maximum) {
			throw new IllegalArgumentException(
				label + " exceeds its wire-length field: "
					+ length + " > " + maximum);
		}
	}
}
