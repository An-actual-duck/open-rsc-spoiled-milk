package orsc;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Strict protocol-v4 native terrain body decoder, isolated for wire tests. */
public final class NativeLayeredTerrainPacketDecoder {
	private static final int MAX_ID_BYTES = 128;
	private static final int MAX_VERSION_BYTES = 128;
	private static final int SHA256_BYTES = 64;

	private NativeLayeredTerrainPacketDecoder() {
	}

	public static NativeLayeredTerrainSnapshot decodeV4(
		byte[] payload, String worldSpace, int level) {
		if (payload == null) {
			throw new IllegalArgumentException(
				"Native terrain packet body is required");
		}
		try {
			ByteBuffer input = ByteBuffer.wrap(payload);
			String packageId = readString(input, MAX_ID_BYTES, "package ID");
			String packageVersion =
				readString(input, MAX_VERSION_BYTES, "package version");
			String manifestSha256 =
				readString(input, SHA256_BYTES, "manifest SHA-256");
			int chunkSize = unsignedByte(input);
			int currentChunkX = input.getInt();
			int currentChunkY = input.getInt();
			int chunkRadius = unsignedByte(input);
			int chunkCount = unsignedByte(input);
			int width = chunkRadius * 2 + 1;
			if (chunkRadius
					!= NativeLayeredTerrainSnapshot.STREAMING_CHUNK_RADIUS
				|| chunkCount != width * width) {
				throw new IllegalArgumentException(
					"Native terrain packet has an invalid readiness window");
			}

			NativeLayeredTerrainChunk[] chunks =
				new NativeLayeredTerrainChunk[chunkCount];
			for (int index = 0; index < chunkCount; index++) {
				int chunkX = input.getInt();
				int chunkY = input.getInt();
				int availability = unsignedByte(input);
				if (availability == 0) {
					chunks[index] = NativeLayeredTerrainChunk.voidChunk(
						chunkSize, chunkX, chunkY);
				} else if (availability == 1) {
					int sourceSectorX = input.getInt();
					int sourceSectorY = input.getInt();
					String sourceEncoding =
						readString(input, MAX_ID_BYTES, "source encoding");
					String sourcePayloadSha256 =
						readString(input, SHA256_BYTES, "source SHA-256");
					int tileByteCount = input.getShort() & 0xffff;
					int expectedTileBytes = Math.multiplyExact(
						Math.multiplyExact(chunkSize, chunkSize),
						NativeLayeredTerrainChunk.TILE_WIRE_BYTES);
					if (tileByteCount != expectedTileBytes
						|| input.remaining() < tileByteCount) {
						throw new IllegalArgumentException(
							"Native terrain chunk has an invalid wire length");
					}
					byte[] tileBytes = new byte[tileByteCount];
					input.get(tileBytes);
					chunks[index] = NativeLayeredTerrainChunk.available(
						chunkSize,
						chunkX,
						chunkY,
						sourceSectorX,
						sourceSectorY,
						sourceEncoding,
						sourcePayloadSha256,
						tileBytes);
				} else {
					throw new IllegalArgumentException(
						"Native terrain chunk availability must be zero or one");
				}
			}
			if (input.hasRemaining()) {
				throw new IllegalArgumentException(
					"Native terrain packet has trailing bytes");
			}
			return new NativeLayeredTerrainSnapshot(
				packageId,
				packageVersion,
				manifestSha256,
				chunkSize,
				worldSpace,
				level,
				currentChunkX,
				currentChunkY,
				chunkRadius,
				chunks);
		} catch (BufferUnderflowException failure) {
			throw new IllegalArgumentException(
				"Native terrain packet ended before its declared content",
				failure);
		}
	}

	private static int unsignedByte(ByteBuffer input) {
		return input.get() & 0xff;
	}

	private static String readString(
		ByteBuffer input, int maximumBytes, String label) {
		int start = input.position();
		int count = 0;
		while (input.hasRemaining()) {
			if (input.get() == 10) {
				if (count == 0 || count > maximumBytes) {
					throw new IllegalArgumentException(
						"Native terrain " + label + " length is invalid");
				}
				byte[] value = new byte[count];
				int end = input.position();
				input.position(start);
				input.get(value);
				input.get();
				if (input.position() != end) {
					throw new IllegalStateException(
						"Native terrain string cursor mismatch");
				}
				return new String(value, StandardCharsets.US_ASCII);
			}
			count++;
			if (count > maximumBytes) {
				throw new IllegalArgumentException(
					"Native terrain " + label + " is too long");
			}
		}
		throw new IllegalArgumentException(
			"Native terrain " + label + " is unterminated");
	}
}
