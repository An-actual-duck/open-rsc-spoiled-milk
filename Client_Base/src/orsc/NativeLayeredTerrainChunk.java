package orsc;

import com.openrsc.client.model.Tile;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Immutable packet-decoded terrain or explicit void for one presentation chunk. */
public final class NativeLayeredTerrainChunk {
	public static final int LEGACY_TILE_WIRE_BYTES = 10;
	public static final int WIDE_TILE_WIRE_BYTES = 11;
	public static final int TILE_WIRE_BYTES = WIDE_TILE_WIRE_BYTES;
	public static final String UNIFORM_ENCODING = "uniform-layered-sector-v1";
	public static final String RLE_ENCODING = "rle-layered-sector-v1";
	public static final String RAW_ENCODING = "raw-layered-sector-v1";
	public static final String UNIFORM_ENCODING_V2 = "uniform-layered-sector-v2-u16";
	public static final String RLE_ENCODING_V2 = "rle-layered-sector-v2-u16";
	public static final String RAW_ENCODING_V2 = "raw-layered-sector-v2-u16";
	public static final String VISUAL_ENCODING =
		"visual-layered-sector-v1";
	public static final String STRUCTURAL_ENCODING =
		"structural-layered-sector-v1";
	public static final String PRESENTATION_ENCODING =
		"presentation-layered-sector-v1";
	public static final String VISUAL_ENCODING_V2 = "visual-layered-sector-v2-u16";
	public static final String STRUCTURAL_ENCODING_V2 = "structural-layered-sector-v2-u16";
	public static final String PRESENTATION_ENCODING_V2 = "presentation-layered-sector-v2-u16";

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
	private final int tileWireBytes;

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
				&& !RAW_ENCODING.equals(sourceEncoding)
				&& !VISUAL_ENCODING.equals(sourceEncoding)
				&& !STRUCTURAL_ENCODING.equals(sourceEncoding)
				&& !PRESENTATION_ENCODING.equals(sourceEncoding)
				&& !UNIFORM_ENCODING_V2.equals(sourceEncoding)
				&& !RLE_ENCODING_V2.equals(sourceEncoding)
				&& !RAW_ENCODING_V2.equals(sourceEncoding)
				&& !VISUAL_ENCODING_V2.equals(sourceEncoding)
				&& !STRUCTURAL_ENCODING_V2.equals(sourceEncoding)
				&& !PRESENTATION_ENCODING_V2.equals(sourceEncoding)) {
				throw new IllegalArgumentException(
					"Unsupported terrain source encoding: " + sourceEncoding);
			}
			if (sourcePayloadSha256 == null
				|| !SHA256.matcher(sourcePayloadSha256).matches()) {
				throw new IllegalArgumentException(
					"Invalid terrain source SHA-256: " + sourcePayloadSha256);
			}
			this.tileWireBytes = isWideEncoding(sourceEncoding)
				? WIDE_TILE_WIRE_BYTES : LEGACY_TILE_WIRE_BYTES;
			if (tileBytes == null
				|| tileBytes.length != size * size * tileWireBytes) {
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
			this.tileWireBytes = 0;
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

	public static NativeLayeredTerrainChunk mergePresentation(
		NativeLayeredTerrainChunk visual,
		NativeLayeredTerrainChunk structural) {
		if (visual == null || structural == null
			|| !visual.available || !structural.available
			|| visual.size != structural.size
			|| visual.chunkX != structural.chunkX
			|| visual.chunkY != structural.chunkY
			|| visual.sourceSectorX != structural.sourceSectorX
			|| visual.sourceSectorY != structural.sourceSectorY
			|| !(VISUAL_ENCODING.equals(visual.sourceEncoding)
				&& STRUCTURAL_ENCODING.equals(structural.sourceEncoding)
				|| VISUAL_ENCODING_V2.equals(visual.sourceEncoding)
				&& STRUCTURAL_ENCODING_V2.equals(structural.sourceEncoding))
			|| !visual.sourcePayloadSha256.equals(
				structural.sourcePayloadSha256)) {
			throw new IllegalArgumentException(
				"Visual and structural terrain chunks do not match");
		}
		boolean wide = VISUAL_ENCODING_V2.equals(visual.sourceEncoding);
		int visualBytes = wide ? 4 : 3;
		int fullBytes = wide ? WIDE_TILE_WIRE_BYTES : LEGACY_TILE_WIRE_BYTES;
		byte[] merged = new byte[visual.size * visual.size * fullBytes];
		for (int offset = 0; offset < merged.length; offset += fullBytes) {
			System.arraycopy(visual.tileBytes, offset, merged, offset, visualBytes);
			System.arraycopy(
				structural.tileBytes,
				offset + visualBytes,
				merged,
				offset + visualBytes,
				7);
		}
		return available(
			visual.size,
			visual.chunkX,
			visual.chunkY,
			visual.sourceSectorX,
			visual.sourceSectorY,
			wide ? PRESENTATION_ENCODING_V2 : PRESENTATION_ENCODING,
			visual.sourcePayloadSha256,
			merged);
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
		int offset = tileOffset(worldX, worldY);
		Tile tile = new Tile();
		tile.groundElevation = tileWireBytes == WIDE_TILE_WIRE_BYTES
			? (tileBytes[offset++] & 0xff) << 8 | tileBytes[offset++] & 0xff
			: tileBytes[offset++] & 0xff;
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

	int groundElevation(int worldX, int worldY) {
		int offset = tileOffset(worldX, worldY);
		return tileWireBytes == WIDE_TILE_WIRE_BYTES
			? (tileBytes[offset] & 0xff) << 8 | tileBytes[offset + 1] & 0xff
			: tileBytes[offset] & 0xff;
	}

	int groundOverlay(int worldX, int worldY) {
		return tileBytes[tileOffset(worldX, worldY)
			+ (tileWireBytes == WIDE_TILE_WIRE_BYTES ? 3 : 2)] & 0xff;
	}

	private int tileOffset(int worldX, int worldY) {
		if (!available || !covers(worldX, worldY)) {
			throw new IllegalArgumentException(
				"Terrain chunk cannot supply tile " + worldX + "," + worldY);
		}
		int localX = Math.floorMod(worldX, size);
		int localY = Math.floorMod(worldY, size);
		return (localX * size + localY) * tileWireBytes;
	}

	public static boolean isWideEncoding(String encoding) {
		return UNIFORM_ENCODING_V2.equals(encoding)
			|| RLE_ENCODING_V2.equals(encoding) || RAW_ENCODING_V2.equals(encoding)
			|| VISUAL_ENCODING_V2.equals(encoding)
			|| STRUCTURAL_ENCODING_V2.equals(encoding)
			|| PRESENTATION_ENCODING_V2.equals(encoding);
	}

	public static int wireBytesForEncoding(String encoding) {
		return isWideEncoding(encoding) ? WIDE_TILE_WIRE_BYTES : LEGACY_TILE_WIRE_BYTES;
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
