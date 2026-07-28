package orsc;

import com.openrsc.client.model.Tile;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, packet-decoded terrain readiness window for one signed layered
 * scene scope. Protocol v3's uniform page and protocol v4's 24-tile window
 * remain readable. Protocol v5 owns a radius-one set of complete compressed
 * 48-tile storage sectors aligned with the client's active section grid.
 */
public final class NativeLayeredTerrainSnapshot {
	public static final int UNIFORM_PAGE_PROTOCOL_VERSION = 3;
	public static final int LEGACY_CHUNKED_PROTOCOL_VERSION = 4;
	public static final int PROTOCOL_VERSION = 5;
	public static final int RESIDENT_PROTOCOL_VERSION = 6;
	public static final int READINESS_PROTOCOL_VERSION = 7;
	public static final int SECTOR_SIZE = 48;
	public static final String PROJECTION_ID = "native-layered-package-v1";
	public static final String UNIFORM_ENCODING = "uniform-layered-sector-v1";
	public static final int LEGACY_STREAMING_CHUNK_SIZE = 24;
	public static final int STREAMING_CHUNK_SIZE = SECTOR_SIZE;
	public static final int STREAMING_CHUNK_RADIUS = 1;

	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Pattern VERSION =
		Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private final int protocolVersion;
	private final String packageId;
	private final String packageVersion;
	private final String manifestSha256;
	private final int presentationChunkSize;
	private final String worldSpace;
	private final int level;
	private final int sectorX;
	private final int sectorY;
	private final String encoding;
	private final String payloadSha256;
	private final int elevation;
	private final int texture;
	private final int overlay;
	private final int roof;
	private final int verticalWall;
	private final int horizontalWall;
	private final int diagonalWall;
	private final int currentChunkX;
	private final int currentChunkY;
	private final int chunkRadius;
	private final NativeLayeredTerrainChunk[] chunks;

	public NativeLayeredTerrainSnapshot(
		String packageId,
		String packageVersion,
		String manifestSha256,
		int presentationChunkSize,
		String worldSpace,
		int level,
		int sectorX,
		int sectorY,
		String encoding,
		String payloadSha256,
		int elevation,
		int texture,
		int overlay,
		int roof,
		int verticalWall,
		int horizontalWall,
		int diagonalWall) {
		this.protocolVersion = UNIFORM_PAGE_PROTOCOL_VERSION;
		this.packageId = matched(packageId, ID, "package ID");
		this.packageVersion = matched(packageVersion, VERSION, "package version");
		this.manifestSha256 = matched(
			manifestSha256, SHA256, "manifest SHA-256");
		if (presentationChunkSize <= 0
			|| presentationChunkSize > SECTOR_SIZE
			|| SECTOR_SIZE % presentationChunkSize != 0) {
			throw new IllegalArgumentException(
				"Presentation chunk size must be a positive divisor of 48");
		}
		this.presentationChunkSize = presentationChunkSize;
		this.worldSpace = matched(worldSpace, ID, "world space");
		this.level = level;
		requireSafeSectorCoordinate(sectorX, "sector X");
		requireSafeSectorCoordinate(sectorY, "sector Y");
		this.sectorX = sectorX;
		this.sectorY = sectorY;
		if (!UNIFORM_ENCODING.equals(encoding)) {
			throw new IllegalArgumentException(
				"Unsupported native terrain encoding: " + encoding);
		}
		this.encoding = encoding;
		this.payloadSha256 = matched(
			payloadSha256, SHA256, "payload SHA-256");
		this.elevation = unsignedByte(elevation, "elevation");
		this.texture = unsignedByte(texture, "texture");
		this.overlay = unsignedByte(overlay, "overlay");
		this.roof = unsignedByte(roof, "roof");
		this.verticalWall = unsignedByte(verticalWall, "vertical wall");
		this.horizontalWall = unsignedByte(horizontalWall, "horizontal wall");
		// The wire uses all 32 raw bits, matching the legacy Tile field.
		this.diagonalWall = diagonalWall;
		this.currentChunkX = Math.floorDiv(
			Math.multiplyExact(sectorX, SECTOR_SIZE),
			presentationChunkSize);
		this.currentChunkY = Math.floorDiv(
			Math.multiplyExact(sectorY, SECTOR_SIZE),
			presentationChunkSize);
		this.chunkRadius = 0;
		this.chunks = new NativeLayeredTerrainChunk[0];
	}

	public NativeLayeredTerrainSnapshot(
		String packageId,
		String packageVersion,
		String manifestSha256,
		int presentationChunkSize,
		String worldSpace,
		int level,
		int currentChunkX,
		int currentChunkY,
		int chunkRadius,
		NativeLayeredTerrainChunk[] chunks) {
		this(
			presentationChunkSize == LEGACY_STREAMING_CHUNK_SIZE
				? LEGACY_CHUNKED_PROTOCOL_VERSION
				: PROTOCOL_VERSION,
			packageId,
			packageVersion,
			manifestSha256,
			presentationChunkSize,
			worldSpace,
			level,
			currentChunkX,
			currentChunkY,
			chunkRadius,
			chunks);
	}

	public NativeLayeredTerrainSnapshot(
		int protocolVersion,
		String packageId,
		String packageVersion,
		String manifestSha256,
		int presentationChunkSize,
		String worldSpace,
		int level,
		int currentChunkX,
		int currentChunkY,
		int chunkRadius,
		NativeLayeredTerrainChunk[] chunks) {
		if (protocolVersion != LEGACY_CHUNKED_PROTOCOL_VERSION
			&& protocolVersion != PROTOCOL_VERSION
			&& protocolVersion != RESIDENT_PROTOCOL_VERSION
			&& protocolVersion != READINESS_PROTOCOL_VERSION) {
			throw new IllegalArgumentException(
				"Unsupported chunked native terrain protocol: "
					+ protocolVersion);
		}
		this.protocolVersion = protocolVersion;
		this.packageId = matched(packageId, ID, "package ID");
		this.packageVersion = matched(packageVersion, VERSION, "package version");
		this.manifestSha256 = matched(
			manifestSha256, SHA256, "manifest SHA-256");
		int expectedChunkSize =
			protocolVersion == LEGACY_CHUNKED_PROTOCOL_VERSION
				? LEGACY_STREAMING_CHUNK_SIZE : STREAMING_CHUNK_SIZE;
		if (presentationChunkSize != expectedChunkSize) {
			throw new IllegalArgumentException(
				"Chunked native terrain protocol/chunk-size mismatch");
		}
		this.presentationChunkSize = presentationChunkSize;
		this.worldSpace = matched(worldSpace, ID, "world space");
		this.level = level;
		requireSafeChunkCoordinate(
			currentChunkX, presentationChunkSize, "current chunk X");
		requireSafeChunkCoordinate(
			currentChunkY, presentationChunkSize, "current chunk Y");
		this.currentChunkX = currentChunkX;
		this.currentChunkY = currentChunkY;
		if (chunkRadius != STREAMING_CHUNK_RADIUS) {
			throw new IllegalArgumentException(
				"Chunked native terrain requires radius-one readiness");
		}
		this.chunkRadius = chunkRadius;
		int width = chunkRadius * 2 + 1;
		int expectedCount = width * width;
		if (chunks == null || chunks.length != expectedCount) {
			throw new IllegalArgumentException(
				"Native terrain readiness window must contain "
					+ expectedCount + " explicit chunk slots");
		}
		this.chunks = new NativeLayeredTerrainChunk[chunks.length];
		int index = 0;
		for (int deltaX = -chunkRadius; deltaX <= chunkRadius; deltaX++) {
			for (int deltaY = -chunkRadius; deltaY <= chunkRadius; deltaY++) {
				NativeLayeredTerrainChunk chunk =
					Objects.requireNonNull(chunks[index], "chunk");
				int expectedX = Math.addExact(currentChunkX, deltaX);
				int expectedY = Math.addExact(currentChunkY, deltaY);
				if (chunk.getChunkX() != expectedX
					|| chunk.getChunkY() != expectedY) {
					throw new IllegalArgumentException(
						"Native terrain chunks are not in x-major/y-minor "
							+ "radius order at index " + index);
				}
				this.chunks[index++] = chunk;
			}
		}
		this.sectorX = 0;
		this.sectorY = 0;
		this.encoding = "";
		this.payloadSha256 = "";
		this.elevation = 0;
		this.texture = 0;
		this.overlay = 0;
		this.roof = 0;
		this.verticalWall = 0;
		this.horizontalWall = 0;
		this.diagonalWall = 0;
	}

	public boolean covers(
		String expectedWorldSpace,
		int expectedLevel,
		int worldX,
		int worldY) {
		if (!worldSpace.equals(expectedWorldSpace) || level != expectedLevel) {
			return false;
		}
		if (isChunkedProtocol()) {
			return findAvailableChunk(worldX, worldY) != null;
		}
		long minX = (long) sectorX * SECTOR_SIZE;
		long minY = (long) sectorY * SECTOR_SIZE;
		return worldX >= minX && worldX < minX + SECTOR_SIZE
			&& worldY >= minY && worldY < minY + SECTOR_SIZE;
	}

	public Tile createUniformTile() {
		if (protocolVersion != UNIFORM_PAGE_PROTOCOL_VERSION) {
			throw new IllegalStateException(
				"Chunked native terrain has no uniform tile");
		}
		Tile tile = new Tile();
		tile.groundElevation = (byte) elevation;
		tile.groundTexture = (byte) texture;
		tile.groundOverlay = (byte) overlay;
		tile.roofTexture = (byte) roof;
		tile.verticalWall = (byte) verticalWall;
		tile.horizontalWall = (byte) horizontalWall;
		tile.diagonalWalls = diagonalWall;
		return tile;
	}

	public Tile createTile(int worldX, int worldY) {
		if (protocolVersion == UNIFORM_PAGE_PROTOCOL_VERSION) {
			if (!covers(worldSpace, level, worldX, worldY)) {
				throw new IllegalArgumentException(
					"Uniform native page does not cover the requested tile");
			}
			return createUniformTile();
		}
		NativeLayeredTerrainChunk chunk =
			findAvailableChunk(worldX, worldY);
		if (chunk == null) {
			throw new IllegalArgumentException(
				"Native readiness window has no terrain for "
					+ worldX + "," + worldY);
		}
		return chunk.createTile(worldX, worldY);
	}

	public String scopeIdentity() {
		String identity = packageIdentity()
			+ ":" + worldSpace + ":" + level;
		if (isChunkedProtocol()) {
			StringBuilder result = new StringBuilder(identity)
				.append(":center-")
				.append(currentChunkX)
				.append(',')
				.append(currentChunkY)
				.append(":radius-")
				.append(chunkRadius);
			for (NativeLayeredTerrainChunk chunk : chunks) {
				result.append(':').append(chunk.identity());
			}
			return result.toString();
		}
		return identity
			+ ":" + sectorX + "," + sectorY
			+ ":" + payloadSha256
			+ ":chunk-" + presentationChunkSize;
	}

	public String packageIdentity() {
		return packageId + "@" + packageVersion + ":" + manifestSha256;
	}

	public String summary() {
		String start = "native terrain " + packageId + "@" + packageVersion
			+ " " + worldSpace + " L" + level
			+ " chunk " + presentationChunkSize;
		if (isChunkedProtocol()) {
			int available = 0;
			for (NativeLayeredTerrainChunk chunk : chunks) {
				if (chunk.isAvailable()) {
					available++;
				}
			}
			return start
				+ " center " + currentChunkX + "," + currentChunkY
				+ " ready " + available + "/" + chunks.length
				+ " manifest " + manifestSha256.substring(0, 12);
		}
		return start
			+ " page " + sectorX + "," + sectorY
			+ " manifest " + manifestSha256.substring(0, 12);
	}

	public int getLevel() {
		return level;
	}

	public String getWorldSpace() {
		return worldSpace;
	}

	public String getManifestSha256() {
		return manifestSha256;
	}

	public int getSectorX() {
		return sectorX;
	}

	public int getSectorY() {
		return sectorY;
	}

	public int getPresentationChunkSize() {
		return presentationChunkSize;
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	public int getCurrentChunkX() {
		return currentChunkX;
	}

	public int getCurrentChunkY() {
		return currentChunkY;
	}

	public int getAvailableChunkCount() {
		int result = 0;
		for (NativeLayeredTerrainChunk chunk : chunks) {
			if (chunk.isAvailable()) {
				result++;
			}
		}
		return result;
	}

	public int getChunkSlotCount() {
		return isChunkedProtocol() ? chunks.length : 1;
	}

	private NativeLayeredTerrainChunk findAvailableChunk(
		int worldX, int worldY) {
		for (NativeLayeredTerrainChunk chunk : chunks) {
			if (chunk.isAvailable() && chunk.covers(worldX, worldY)) {
				return chunk;
			}
		}
		return null;
	}

	private static String matched(
		String value, Pattern pattern, String label) {
		if (value == null || !pattern.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid " + label + ": " + value);
		}
		return value;
	}

	private static int unsignedByte(int value, String label) {
		if (value < 0 || value > 255) {
			throw new IllegalArgumentException(
				label + " must be an unsigned byte");
		}
		return value;
	}

	private static void requireSafeSectorCoordinate(int value, String label) {
		long minimum = (long) value * SECTOR_SIZE;
		long maximum = minimum + SECTOR_SIZE - 1L;
		if (minimum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
				label + " cannot be represented as signed tile coordinates");
		}
	}

	private boolean isChunkedProtocol() {
		return protocolVersion == LEGACY_CHUNKED_PROTOCOL_VERSION
			|| protocolVersion == PROTOCOL_VERSION
			|| protocolVersion == RESIDENT_PROTOCOL_VERSION
			|| protocolVersion == READINESS_PROTOCOL_VERSION;
	}

	private static void requireSafeChunkCoordinate(
		int value, int chunkSize, String label) {
		long minimum = (long) value * chunkSize;
		long maximum = minimum + chunkSize - 1L;
		if (minimum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
				label + " cannot be represented as signed tile coordinates");
		}
	}

	@Override
	public boolean equals(Object other) {
		return this == other
			|| other instanceof NativeLayeredTerrainSnapshot
				&& scopeIdentity().equals(
					((NativeLayeredTerrainSnapshot) other).scopeIdentity());
	}

	@Override
	public int hashCode() {
		return Objects.hash(scopeIdentity());
	}
}
