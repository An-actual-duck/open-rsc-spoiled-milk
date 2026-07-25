package orsc;

import com.openrsc.client.model.Tile;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, packet-decoded terrain page for one signed layered scene scope.
 *
 * <p>The first native format is deliberately small: one validated uniform
 * 48x48 storage page. The presentation chunk size remains independent and is
 * carried so later streaming can refresh smaller scopes without changing the
 * archive page contract.</p>
 */
public final class NativeLayeredTerrainSnapshot {
	public static final int PROTOCOL_VERSION = 3;
	public static final int SECTOR_SIZE = 48;
	public static final String PROJECTION_ID = "native-layered-package-v1";
	public static final String UNIFORM_ENCODING = "uniform-layered-sector-v1";

	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
	private static final Pattern VERSION =
		Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

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
	}

	public boolean covers(
		String expectedWorldSpace,
		int expectedLevel,
		int worldX,
		int worldY) {
		if (!worldSpace.equals(expectedWorldSpace) || level != expectedLevel) {
			return false;
		}
		long minX = (long) sectorX * SECTOR_SIZE;
		long minY = (long) sectorY * SECTOR_SIZE;
		return worldX >= minX && worldX < minX + SECTOR_SIZE
			&& worldY >= minY && worldY < minY + SECTOR_SIZE;
	}

	public Tile createUniformTile() {
		Tile tile = new Tile();
		tile.groundElevation = (byte) elevation;
		tile.groundTexture = (byte) texture;
		tile.groundOverlay = (byte) overlay;
		tile.roofTexture = (byte) roof;
		tile.verticalWall = (byte) verticalWall;
		tile.horizontalWall = (byte) horizontalWall;
		tile.diagonalWalls = diagonalWall;
		tile.editorPaintedOverlay = true;
		return tile;
	}

	public String scopeIdentity() {
		return packageId + "@" + packageVersion
			+ ":" + manifestSha256
			+ ":" + worldSpace + ":" + level
			+ ":" + sectorX + "," + sectorY
			+ ":" + payloadSha256
			+ ":chunk-" + presentationChunkSize;
	}

	public String summary() {
		return "native terrain " + packageId + "@" + packageVersion
			+ " " + worldSpace + " L" + level
			+ " page " + sectorX + "," + sectorY
			+ " chunk " + presentationChunkSize
			+ " manifest " + manifestSha256.substring(0, 12);
	}

	public int getLevel() {
		return level;
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
