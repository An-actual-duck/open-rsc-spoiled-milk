package orsc;

import com.openrsc.client.model.Sprite;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

/** Detached map art with small, independently compressed sectors. */
final class SurfaceMinimapAtlas {
	static final int SECTOR_SIZE = NativeLayeredTerrainSnapshot.SECTOR_SIZE;
	private static final int SECTOR_PIXELS = SECTOR_SIZE * 2;
	private static final String RESOURCE = "/myworld-assets/ui/minimap/surface.bin";

	private final Map<Long, byte[]> sectors = new HashMap<>();
	private final Map<Long, Sprite> visibleSectors = new LinkedHashMap<Long, Sprite>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Sprite> eldest) {
			return size() > 16;
		}
	};
	private final String manifestHash;
	private final int[] palette;
	final int bankX;
	final int bankY;

	static SurfaceMinimapAtlas open() throws IOException {
		try (InputStream resource = SurfaceMinimapAtlas.class.getResourceAsStream(RESOURCE)) {
			if (resource == null) throw new IOException("Surface minimap resource missing");
			return new SurfaceMinimapAtlas(new DataInputStream(resource));
		}
	}

	private SurfaceMinimapAtlas(DataInputStream input) throws IOException {
		if (input.readInt() != 0x524D4150 || input.readUnsignedByte() != 1) {
			throw new IOException("Unknown surface minimap format");
		}
		StringBuilder hash = new StringBuilder(64);
		for (int index = 0; index < 32; index++) {
			int value = input.readUnsignedByte();
			hash.append(Character.forDigit(value >>> 4, 16));
			hash.append(Character.forDigit(value & 15, 16));
		}
		manifestHash = hash.toString();
		bankX = input.readShort();
		bankY = input.readShort();
		palette = new int[input.readUnsignedByte()];
		int count = input.readUnsignedShort();
		if (palette.length == 0 || count > 2048) throw new IOException("Invalid surface minimap header");
		for (int index = 0; index < palette.length; index++) palette[index] = input.readInt();
		for (int index = 0; index < count; index++) {
			long key = key(input.readShort(), input.readShort());
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
		return snapshot != null && snapshot.getLevel() == 0 && "global".equals(snapshot.getWorldSpace())
			&& manifestHash.equals(snapshot.getManifestSha256());
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

	private static long key(int x, int y) {
		return (long) x << 32 | (y & 0xffffffffL);
	}
}
