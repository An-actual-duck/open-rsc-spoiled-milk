package orsc;

import com.openrsc.client.model.Tile;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Immutable packet-decoded terrain or explicit void for one presentation chunk. */
public final class NativeLayeredTerrainChunk {
	public static final int TILE_WIRE_BYTES = 10;
	public static final String UNIFORM_ENCODING = "uniform-layered-sector-v1";
	public static final String RLE_ENCODING = "rle-layered-sector-v1";
	public static final String RAW_ENCODING = "raw-layered-sector-v1";

	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private final int size;
	private final int chunkX;
	private final int chunkY;
	private final boolean available;
	private final int sourceSectorX;
	private final int sourceSectorY;
	private final String sourceEncoding;
	private final String sourcePayloadSha256;
	private final byte[] tileBytes;

	private NativeLayeredTerrainChunk(
		int size,
		int chunkX,
		int chunkY,
		boolean available,
		int sourceSectorX,
		int sourceSectorY,
		String sourceEncoding,
		String sourcePayloadSha256,
		byte[] tileBytes) {
		if (size <= 0 || size > NativeLayeredTerrainSnapshot.SECTOR_SIZE
			|| NativeLayeredTerrainSnapshot.SECTOR_SIZE % size != 0) {
			throw new IllegalArgumentException(
				"Presentation chunk size must be a positive divisor of 48");
		}
		this.size = size;
		requireSafeChunkCoordinate(chunkX, size, "chunk X");
		requireSafeChunkCoordinate(chunkY, size, "chunk Y");
		this.chunkX = chunkX;
		this.chunkY = chunkY;
		this.available = available;
		this.sourceSectorX = sourceSectorX;
		this.sourceSectorY = sourceSectorY;
		if (available) {
			if (!UNIFORM_ENCODING.equals(sourceEncoding)
				&& !RLE_ENCODING.equals(sourceEncoding)
				&& !RAW_ENCODING.equals(sourceEncoding)) {
				throw new IllegalArgumentException(
					"Unsupported terrain source encoding: " + sourceEncoding);
			}
			if (sourcePayloadSha256 == null
				|| !SHA256.matcher(sourcePayloadSha256).matches()) {
				throw new IllegalArgumentException(
					"Invalid terrain source SHA-256: " + sourcePayloadSha256);
			}
			if (tileBytes == null
				|| tileBytes.length != size * size * TILE_WIRE_BYTES) {
				throw new IllegalArgumentException(
					"Terrain chunk has an invalid tile byte count");
			}
			int expectedSectorX = Math.floorDiv(
				Math.multiplyExact(chunkX, size),
				NativeLayeredTerrainSnapshot.SECTOR_SIZE);
			int expectedSectorY = Math.floorDiv(
				Math.multiplyExact(chunkY, size),
				NativeLayeredTerrainSnapshot.SECTOR_SIZE);
			if (sourceSectorX != expectedSectorX
				|| sourceSectorY != expectedSectorY) {
				throw new IllegalArgumentException(
					"Terrain chunk source page does not cover the chunk");
			}
			this.sourceEncoding = sourceEncoding;
			this.sourcePayloadSha256 = sourcePayloadSha256;
			this.tileBytes = Arrays.copyOf(tileBytes, tileBytes.length);
		} else {
			if (sourceEncoding != null || sourcePayloadSha256 != null
				|| tileBytes != null) {
				throw new IllegalArgumentException(
					"Explicit void chunk cannot carry terrain source data");
			}
			this.sourceEncoding = null;
			this.sourcePayloadSha256 = null;
			this.tileBytes = null;
		}
	}

	public static NativeLayeredTerrainChunk available(
		int size,
		int chunkX,
		int chunkY,
		int sourceSectorX,
		int sourceSectorY,
		String sourceEncoding,
		String sourcePayloadSha256,
		byte[] tileBytes) {
		return new NativeLayeredTerrainChunk(
			size,
			chunkX,
			chunkY,
			true,
			sourceSectorX,
			sourceSectorY,
			sourceEncoding,
			sourcePayloadSha256,
			tileBytes);
	}

	public static NativeLayeredTerrainChunk voidChunk(
		int size, int chunkX, int chunkY) {
		return new NativeLayeredTerrainChunk(
			size, chunkX, chunkY, false, 0, 0, null, null, null);
	}

	public boolean covers(int worldX, int worldY) {
		long minimumX = (long) chunkX * size;
		long minimumY = (long) chunkY * size;
		return worldX >= minimumX && worldX < minimumX + size
			&& worldY >= minimumY && worldY < minimumY + size;
	}

	public Tile createTile(int worldX, int worldY) {
		if (!available || !covers(worldX, worldY)) {
			throw new IllegalArgumentException(
				"Terrain chunk cannot supply tile " + worldX + "," + worldY);
		}
		int localX = Math.floorMod(worldX, size);
		int localY = Math.floorMod(worldY, size);
		int offset = (localX * size + localY) * TILE_WIRE_BYTES;
		Tile tile = new Tile();
		tile.groundElevation = tileBytes[offset++];
		tile.groundTexture = tileBytes[offset++];
		tile.groundOverlay = tileBytes[offset++];
		tile.roofTexture = tileBytes[offset++];
		tile.verticalWall = tileBytes[offset++];
		tile.horizontalWall = tileBytes[offset++];
		tile.diagonalWalls =
			(tileBytes[offset++] & 0xff) << 24
				| (tileBytes[offset++] & 0xff) << 16
				| (tileBytes[offset++] & 0xff) << 8
				| tileBytes[offset] & 0xff;
		return tile;
	}

	public String identity() {
		return chunkX + "," + chunkY + ":"
			+ (available
				? sourceSectorX + "," + sourceSectorY + ":"
					+ sourceEncoding + ":" + sourcePayloadSha256
				: "void");
	}

	public boolean isAvailable() {
		return available;
	}

	public int getChunkX() {
		return chunkX;
	}

	public int getChunkY() {
		return chunkY;
	}

	private static void requireSafeChunkCoordinate(
		int value, int size, String label) {
		long minimum = (long) value * size;
		long maximum = minimum + size - 1L;
		if (minimum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
				label + " cannot be represented as signed tile coordinates");
		}
	}
}
