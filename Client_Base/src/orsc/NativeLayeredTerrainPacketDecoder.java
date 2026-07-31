package orsc;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Strict protocol-v4 native terrain body decoder, isolated for wire tests. */
public final class NativeLayeredTerrainPacketDecoder {
	private static final int MAX_ID_BYTES = 128;
	private static final int MAX_VERSION_BYTES = 128;
	private static final int SHA256_BYTES = 64;

	private NativeLayeredTerrainPacketDecoder() {
	}

	public static NativeLayeredTerrainSnapshot decodeV4(
		byte[] payload, String worldSpace, int level) {
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot.LEGACY_CHUNKED_PROTOCOL_VERSION,
			null,
			null);
	}

	public static NativeLayeredTerrainSnapshot decodeV5(
		byte[] payload, String worldSpace, int level) {
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot.PROTOCOL_VERSION,
			null,
			null);
	}

	public static NativeLayeredTerrainSnapshot decodeV6(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache) {
		if (residentCache == null) {
			throw new IllegalArgumentException(
				"Protocol-v6 native terrain requires a resident cache");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot.RESIDENT_PROTOCOL_VERSION,
			residentCache,
			null);
	}

	public static NativeLayeredTerrainSnapshot decodeV7(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache) {
		if (residentCache == null) {
			throw new IllegalArgumentException(
				"Protocol-v7 native terrain requires a resident cache");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION,
			residentCache,
			null);
	}

	public static NativeLayeredTerrainSnapshot decodeV8(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache) {
		if (residentCache == null) {
			throw new IllegalArgumentException(
				"Protocol-v8 native terrain requires a resident cache");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot
				.ATOMIC_ACTIVATION_PROTOCOL_VERSION,
			residentCache,
			null);
	}

	public static NativeLayeredTerrainSnapshot decodeV7Stage(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache,
		NativeLayeredTerrainSnapshot activeTerrain) {
		if (residentCache == null || activeTerrain == null) {
			throw new IllegalArgumentException(
				"Protocol-v7 terrain stage requires resident and active terrain");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION,
			residentCache,
			activeTerrain);
	}

	public static NativeLayeredTerrainSnapshot decodeV9Halo(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache,
		NativeLayeredTerrainSnapshot activeTerrain) {
		if (residentCache == null || activeTerrain == null) {
			throw new IllegalArgumentException(
				"Protocol-v9 terrain halo requires resident and active terrain");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot
				.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,
			residentCache,
			activeTerrain);
	}

	public static NativeLayeredTerrainSnapshot decodePredictedSymmetricHalo(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache,
		NativeLayeredTerrainSnapshot activeTerrain) {
		if (residentCache == null || activeTerrain == null) {
			throw new IllegalArgumentException(
				"Predicted symmetric terrain requires resident and active terrain");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot
				.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,
			residentCache,
			activeTerrain,
			true);
	}

	public static NativeLayeredTerrainSnapshot
		decodePredictedSymmetricStructure(
			byte[] payload,
			String worldSpace,
			int level,
			NativeLayeredTerrainResidentCache residentCache,
			NativeLayeredTerrainSnapshot activeTerrain) {
		if (residentCache == null || activeTerrain == null) {
			throw new IllegalArgumentException(
				"Predicted symmetric structure requires resident and active terrain");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot
				.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION,
			residentCache,
			activeTerrain,
			true);
	}

	public static NativeLayeredTerrainSnapshot decodeV10Structure(
		byte[] payload,
		String worldSpace,
		int level,
		NativeLayeredTerrainResidentCache residentCache,
		NativeLayeredTerrainSnapshot activeTerrain) {
		if (residentCache == null || activeTerrain == null) {
			throw new IllegalArgumentException(
				"Protocol-v10 terrain structure requires resident and active terrain");
		}
		return decodeChunked(
			payload,
			worldSpace,
			level,
			NativeLayeredTerrainSnapshot
				.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION,
			residentCache,
			activeTerrain);
	}

	private static NativeLayeredTerrainSnapshot decodeChunked(
		byte[] payload,
		String worldSpace,
		int level,
		int protocolVersion,
		NativeLayeredTerrainResidentCache residentCache,
		NativeLayeredTerrainSnapshot activeTerrain) {
		return decodeChunked(
			payload,
			worldSpace,
			level,
			protocolVersion,
			residentCache,
			activeTerrain,
			false);
	}

	private static NativeLayeredTerrainSnapshot decodeChunked(
		byte[] payload,
		String worldSpace,
		int level,
		int protocolVersion,
		NativeLayeredTerrainResidentCache residentCache,
		NativeLayeredTerrainSnapshot activeTerrain,
		boolean predictedSymmetric) {
		if (payload == null) {
			throw new IllegalArgumentException(
				"Native terrain packet body is required");
		}
		NativeLayeredTerrainResidentCache.Transaction residentTransaction =
			residentCache == null ? null : residentCache.begin();
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
			int expectedChunkSize =
				protocolVersion
						== NativeLayeredTerrainSnapshot
							.LEGACY_CHUNKED_PROTOCOL_VERSION
					? NativeLayeredTerrainSnapshot
						.LEGACY_STREAMING_CHUNK_SIZE
					: NativeLayeredTerrainSnapshot.STREAMING_CHUNK_SIZE;
			if (chunkSize != expectedChunkSize) {
				throw new IllegalArgumentException(
					"Native terrain packet has an invalid chunk size");
			}
			int width = chunkRadius * 2 + 1;
			int expectedChunkRadius =
				protocolVersion
						== NativeLayeredTerrainSnapshot
							.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION
					|| protocolVersion
						== NativeLayeredTerrainSnapshot
							.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION
					? NativeLayeredTerrainSnapshot
						.SYMMETRIC_RESIDENCY_CHUNK_RADIUS
					: NativeLayeredTerrainSnapshot
						.STREAMING_CHUNK_RADIUS;
			if (chunkRadius != expectedChunkRadius
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
					boolean payloadPresent = true;
					if (isResidentProtocol(protocolVersion)) {
						int payloadPresence = unsignedByte(input);
						if (payloadPresence > 1) {
							throw new IllegalArgumentException(
								"Native terrain payload presence must be zero or one");
						}
						payloadPresent = payloadPresence == 1;
					}
					int expandedTileBytes = Math.multiplyExact(
						Math.multiplyExact(chunkSize, chunkSize),
						NativeLayeredTerrainChunk.TILE_WIRE_BYTES);
					boolean visualPayload =
						NativeLayeredTerrainChunk.VISUAL_ENCODING.equals(
							sourceEncoding);
					boolean structuralPayload =
						NativeLayeredTerrainChunk.STRUCTURAL_ENCODING.equals(
							sourceEncoding);
					int expectedTileBytes = structuralPayload
						? Math.multiplyExact(
							Math.multiplyExact(chunkSize, chunkSize),
							7)
						: visualPayload
						? Math.multiplyExact(
							Math.multiplyExact(chunkSize, chunkSize),
							3)
						: expandedTileBytes;
					NativeLayeredTerrainChunk chunk;
					if (payloadPresent) {
						int wireByteCount = input.getShort() & 0xffff;
						if (wireByteCount <= 0
							|| input.remaining() < wireByteCount
							|| protocolVersion
									== NativeLayeredTerrainSnapshot
										.LEGACY_CHUNKED_PROTOCOL_VERSION
								&& wireByteCount != expectedTileBytes) {
							throw new IllegalArgumentException(
								"Native terrain chunk has an invalid wire length");
						}
						byte[] wireBytes = new byte[wireByteCount];
						input.get(wireBytes);
						byte[] tileBytes =
							protocolVersion
									== NativeLayeredTerrainSnapshot
										.LEGACY_CHUNKED_PROTOCOL_VERSION
								? wireBytes
								: inflateSector(wireBytes, expectedTileBytes);
						if (visualPayload) {
							if (protocolVersion
									!= NativeLayeredTerrainSnapshot
										.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION) {
								throw new IllegalArgumentException(
									"Visual terrain payload requires protocol v9");
							}
							tileBytes = expandVisualTerrain(
								tileBytes, expandedTileBytes);
						} else if (structuralPayload) {
							if (protocolVersion
									!= NativeLayeredTerrainSnapshot
										.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION) {
								throw new IllegalArgumentException(
									"Structural terrain payload requires protocol v10");
							}
							tileBytes = expandStructuralTerrain(
								tileBytes, expandedTileBytes);
						}
						chunk = NativeLayeredTerrainChunk.available(
							chunkSize,
							chunkX,
							chunkY,
							sourceSectorX,
							sourceSectorY,
							sourceEncoding,
							sourcePayloadSha256,
							tileBytes);
					} else {
						if (residentTransaction == null) {
							throw new IllegalArgumentException(
								"Native terrain reference requires protocol v6");
						}
						chunk = residentTransaction.resolveReference(
							residentContentIdentity(
								packageId,
								packageVersion,
								manifestSha256,
								worldSpace,
								level,
								chunkSize,
								chunkX,
								chunkY,
								sourceSectorX,
								sourceSectorY,
								sourceEncoding,
								sourcePayloadSha256));
					}
					if (residentTransaction != null && payloadPresent) {
						residentTransaction.acceptPayload(
							residentContentIdentity(
								packageId,
								packageVersion,
								manifestSha256,
								worldSpace,
								level,
								chunkSize,
								chunkX,
								chunkY,
								sourceSectorX,
								sourceSectorY,
								sourceEncoding,
								sourcePayloadSha256),
							chunk);
					}
					chunks[index] = chunk;
				} else {
					throw new IllegalArgumentException(
						"Native terrain chunk availability must be zero or one");
				}
			}
			if (input.hasRemaining()) {
				throw new IllegalArgumentException(
					"Native terrain packet has trailing bytes");
			}
			NativeLayeredTerrainSnapshot result =
				new NativeLayeredTerrainSnapshot(
				protocolVersion,
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
			if (activeTerrain != null) {
				if (protocolVersion
						== NativeLayeredTerrainSnapshot
							.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION
					|| protocolVersion
						== NativeLayeredTerrainSnapshot
							.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION) {
					if (predictedSymmetric) {
						requirePredictedSymmetricHalo(activeTerrain, result);
					} else {
						requireSymmetricHalo(activeTerrain, result);
					}
				} else {
					requireAdjacentStage(activeTerrain, result);
				}
			}
			if (residentTransaction != null) {
				residentTransaction.commit();
			}
			return result;
		} catch (BufferUnderflowException failure) {
			throw new IllegalArgumentException(
				"Native terrain packet ended before its declared content",
				failure);
		}
	}

	private static void requireAdjacentStage(
		NativeLayeredTerrainSnapshot active,
		NativeLayeredTerrainSnapshot staged) {
		if ((active.getProtocolVersion()
					!= NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION
				&& active.getProtocolVersion()
					!= NativeLayeredTerrainSnapshot
						.ATOMIC_ACTIVATION_PROTOCOL_VERSION)
			|| !active.packageIdentity().equals(staged.packageIdentity())
			|| !active.getWorldSpace().equals(staged.getWorldSpace())
			|| active.getLevel() != staged.getLevel()) {
			throw new IllegalArgumentException(
				"Native terrain stage does not match the active generation");
		}
		int deltaX = staged.getCurrentChunkX()
			- active.getCurrentChunkX();
		int deltaY = staged.getCurrentChunkY()
			- active.getCurrentChunkY();
		if ((deltaX == 0 && deltaY == 0)
			|| Math.abs(deltaX) > 1
			|| Math.abs(deltaY) > 1) {
			throw new IllegalArgumentException(
				"Native terrain stage must be one adjacent center");
		}
	}

	private static void requireSymmetricHalo(
		NativeLayeredTerrainSnapshot active,
		NativeLayeredTerrainSnapshot halo) {
		if ((active.getProtocolVersion()
					!= NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION
				&& active.getProtocolVersion()
					!= NativeLayeredTerrainSnapshot
						.ATOMIC_ACTIVATION_PROTOCOL_VERSION)
			|| !active.packageIdentity().equals(halo.packageIdentity())
			|| !active.getWorldSpace().equals(halo.getWorldSpace())
			|| active.getLevel() != halo.getLevel()
			|| active.getCurrentChunkX() != halo.getCurrentChunkX()
			|| active.getCurrentChunkY() != halo.getCurrentChunkY()
			|| halo.getChunkRadius()
				!= NativeLayeredTerrainSnapshot
					.SYMMETRIC_RESIDENCY_CHUNK_RADIUS) {
			throw new IllegalArgumentException(
				"Native terrain halo does not match the active center");
		}
	}

	private static void requirePredictedSymmetricHalo(
		NativeLayeredTerrainSnapshot active,
		NativeLayeredTerrainSnapshot halo) {
		if ((active.getProtocolVersion()
					!= NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION
				&& active.getProtocolVersion()
					!= NativeLayeredTerrainSnapshot
						.ATOMIC_ACTIVATION_PROTOCOL_VERSION)
			|| !active.packageIdentity().equals(halo.packageIdentity())
			|| !active.getWorldSpace().equals(halo.getWorldSpace())
			|| active.getLevel() != halo.getLevel()
			|| halo.getChunkRadius()
				!= NativeLayeredTerrainSnapshot
					.SYMMETRIC_RESIDENCY_CHUNK_RADIUS) {
			throw new IllegalArgumentException(
				"Predicted terrain halo does not match the active generation");
		}
		int deltaX =
			halo.getCurrentChunkX() - active.getCurrentChunkX();
		int deltaY =
			halo.getCurrentChunkY() - active.getCurrentChunkY();
		if ((deltaX == 0 && deltaY == 0)
			|| Math.abs(deltaX) > 1
			|| Math.abs(deltaY) > 1) {
			throw new IllegalArgumentException(
				"Predicted terrain halo must be one adjacent center");
		}
	}

	private static byte[] expandVisualTerrain(
		byte[] visualBytes, int expandedLength) {
		if (visualBytes == null
			|| visualBytes.length % 3 != 0
			|| expandedLength
				!= visualBytes.length / 3
					* NativeLayeredTerrainChunk.TILE_WIRE_BYTES) {
			throw new IllegalArgumentException(
				"Visual native terrain has an invalid tile image");
		}
		byte[] expanded = new byte[expandedLength];
		int source = 0;
		int target = 0;
		while (source < visualBytes.length) {
			expanded[target] = visualBytes[source++];
			expanded[target + 1] = visualBytes[source++];
			expanded[target + 2] = visualBytes[source++];
			target += NativeLayeredTerrainChunk.TILE_WIRE_BYTES;
		}
		return expanded;
	}

	private static byte[] expandStructuralTerrain(
		byte[] structuralBytes, int expandedLength) {
		if (structuralBytes == null
			|| structuralBytes.length % 7 != 0
			|| expandedLength
				!= structuralBytes.length / 7
					* NativeLayeredTerrainChunk.TILE_WIRE_BYTES) {
			throw new IllegalArgumentException(
				"Structural native terrain has an invalid tile image");
		}
		byte[] expanded = new byte[expandedLength];
		int source = 0;
		int target = 0;
		while (source < structuralBytes.length) {
			System.arraycopy(
				structuralBytes,
				source,
				expanded,
				target + 3,
				7);
			source += 7;
			target += NativeLayeredTerrainChunk.TILE_WIRE_BYTES;
		}
		return expanded;
	}

	private static boolean isResidentProtocol(final int protocolVersion) {
		return protocolVersion
				== NativeLayeredTerrainSnapshot.RESIDENT_PROTOCOL_VERSION
			|| protocolVersion
				== NativeLayeredTerrainSnapshot.READINESS_PROTOCOL_VERSION
			|| protocolVersion
				== NativeLayeredTerrainSnapshot
					.ATOMIC_ACTIVATION_PROTOCOL_VERSION
			|| protocolVersion
				== NativeLayeredTerrainSnapshot
					.SYMMETRIC_RESIDENCY_PROTOCOL_VERSION
			|| protocolVersion
				== NativeLayeredTerrainSnapshot
					.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION;
	}

	private static String residentContentIdentity(
		String packageId,
		String packageVersion,
		String manifestSha256,
		String worldSpace,
		int level,
		int chunkSize,
		int chunkX,
		int chunkY,
		int sourceSectorX,
		int sourceSectorY,
		String sourceEncoding,
		String sourcePayloadSha256) {
		return packageId
			+ "@" + packageVersion
			+ ":" + manifestSha256
			+ ":" + worldSpace
			+ ":" + level
			+ ":" + chunkSize
			+ ":" + chunkX + "," + chunkY
			+ ":" + sourceSectorX + "," + sourceSectorY
			+ ":" + sourceEncoding
			+ ":" + sourcePayloadSha256;
	}

	private static byte[] inflateSector(
		byte[] compressed, int expectedLength) {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(compressed);
			byte[] result = new byte[expectedLength];
			int length = inflater.inflate(result);
			if (length != expectedLength
				|| !inflater.finished()
				|| inflater.getRemaining() != 0) {
				throw new IllegalArgumentException(
					"Compressed native terrain sector has an invalid length");
			}
			return result;
		} catch (DataFormatException failure) {
			throw new IllegalArgumentException(
				"Compressed native terrain sector is malformed",
				failure);
		} finally {
			inflater.end();
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
