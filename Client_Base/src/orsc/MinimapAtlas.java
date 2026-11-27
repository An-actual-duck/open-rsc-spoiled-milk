package orsc;

import com.openrsc.client.model.Sprite;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/** Detached map art with small, independently compressed sectors. */
final class MinimapAtlas {
	static final int SECTOR_SIZE = NativeLayeredTerrainSnapshot.SECTOR_SIZE;
	private static final int SECTOR_PIXELS = SECTOR_SIZE * 2;
	private static final String RESOURCE_DIRECTORY = "/myworld-assets/ui/minimap/";

	private final Map<Long, byte[]> sectors = new HashMap<>();
	// Keep a full viewport at Compact UI size cached while panning.
	private final Map<Long, Sprite> visibleSectors = new LinkedHashMap<Long, Sprite>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Sprite> eldest) {
			return size() > 64;
		}
	};
	private final String manifestHash;
	private final int level;
	private final int[] palette;
	final Landmark[] landmarks;
	private int minX = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int minY = Integer.MAX_VALUE;
	private int maxY = Integer.MIN_VALUE;

	static MinimapAtlas open(int level) throws IOException {
		String name = level == 0 ? "surface.bin" : "level-" + level + ".bin";
		try (InputStream resource = MinimapAtlas.class.getResourceAsStream(RESOURCE_DIRECTORY + name)) {
			return resource == null ? null : new MinimapAtlas(new DataInputStream(resource), level);
		}
	}

	private MinimapAtlas(DataInputStream input, int expectedLevel) throws IOException {
		if (input.readInt() != 0x524D4150 || input.readUnsignedByte() != 3) {
			throw new IOException("Unknown minimap format");
		}
		StringBuilder hash = new StringBuilder(64);
		for (int index = 0; index < 32; index++) {
			int value = input.readUnsignedByte();
			hash.append(Character.forDigit(value >>> 4, 16));
			hash.append(Character.forDigit(value & 15, 16));
		}
		manifestHash = hash.toString();
		level = input.readShort();
		palette = new int[input.readUnsignedByte()];
		int count = input.readUnsignedShort();
		int landmarkCount = input.readUnsignedShort();
		if (level != expectedLevel || palette.length == 0 || count > 2048 || landmarkCount > 4096) {
			throw new IOException("Invalid minimap header");
		}
		for (int index = 0; index < palette.length; index++) palette[index] = input.readInt();
		landmarks = new Landmark[landmarkCount];
		for (int index = 0; index < landmarkCount; index++) landmarks[index] = new Landmark(input);
		for (int index = 0; index < count; index++) {
			int x = input.readShort();
			int y = input.readShort();
			long key = key(x, y);
			minX = Math.min(minX, x * SECTOR_SIZE);
			maxX = Math.max(maxX, (x + 1) * SECTOR_SIZE - 1);
			minY = Math.min(minY, y * SECTOR_SIZE);
			maxY = Math.max(maxY, (y + 1) * SECTOR_SIZE - 1);
			int length = input.readUnsignedShort();
			if (length == 0 || length > SECTOR_PIXELS * SECTOR_PIXELS + 64) {
				throw new IOException("Invalid minimap sector length");
			}
			byte[] bytes = new byte[length];
			input.readFully(bytes);
			if (sectors.put(key, bytes) != null) throw new IOException("Duplicate minimap sector");
		}
	}

	boolean matches(NativeLayeredTerrainSnapshot snapshot) {
		return snapshot != null && snapshot.getLevel() == level && "global".equals(snapshot.getWorldSpace())
			&& manifestHash.equals(snapshot.getManifestSha256());
	}

	double clampX(double x) {
		return sectors.isEmpty() ? x : Math.max(minX, Math.min(maxX, x));
	}

	double clampY(double y) {
		return sectors.isEmpty() ? y : Math.max(minY, Math.min(maxY, y));
	}

	Sprite sector(int x, int y) {
		long key = key(x, y);
		Sprite decoded = visibleSectors.get(key);
		if (decoded != null) return decoded;
		byte[] compressed = sectors.get(key);
		if (compressed == null) return null;
		try (DataInputStream input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
			byte[] indices = new byte[SECTOR_PIXELS * SECTOR_PIXELS];
			input.readFully(indices);
			if (input.read() != -1) throw new IOException("Minimap sector has excess pixels");
			int[] pixels = new int[indices.length];
			for (int index = 0; index < indices.length; index++) {
				int color = indices[index] & 0xff;
				if (color >= palette.length) throw new IOException("Unknown minimap color");
				pixels[index] = palette[color];
			}
			decoded = new Sprite(pixels, SECTOR_PIXELS, SECTOR_PIXELS);
			visibleSectors.put(key, decoded);
			return decoded;
		} catch (IOException corruptSector) {
			sectors.remove(key);
			return null;
		}
	}

	static final class Landmark {
		final int x;
		final int y;
		final int offsetX;
		final int offsetY;
		final MinimapMarkerIcon icon;
		final String label;

		private Landmark(DataInputStream input) throws IOException {
			x = input.readShort();
			y = input.readShort();
			offsetX = input.readByte();
			offsetY = input.readByte();
			byte[] iconName = new byte[input.readUnsignedByte()];
			byte[] labelBytes = new byte[input.readUnsignedByte()];
			input.readFully(iconName);
			input.readFully(labelBytes);
			try {
				icon = MinimapMarkerIcon.valueOf(new String(iconName, StandardCharsets.US_ASCII));
			} catch (IllegalArgumentException invalid) {
				throw new IOException("Unknown minimap icon", invalid);
			}
			label = new String(labelBytes, StandardCharsets.UTF_8);
		}
	}

	private static long key(int x, int y) {
		return (long) x << 32 | (y & 0xffffffffL);
	}
}
