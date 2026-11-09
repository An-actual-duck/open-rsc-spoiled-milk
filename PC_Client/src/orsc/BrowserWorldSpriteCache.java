package orsc;

import com.openrsc.client.model.Sprite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;

/** Optional render-thread-only cache; not enabled by constructing a renderer.
 * Source arrays are mutable: every lookup hashes AND compares their active contents.
 * Callers must not mutate sprites during compose, or write the returned RGBA array.
 * No anchor/depth/frame state is retained. Caller scratch is borrowed only on misses.
 */
final class BrowserWorldSpriteCache {
	static final long MAX_CACHE_BYTES = 16L * 1024 * 1024;
	static final int MAX_ENTRIES = 256;
	static final int MAX_PIXELS = 2 * 1024 * 1024;
	static final int MAX_LAYERS = 256;
	private final long byteBudget;
	private final int entryLimit;
	private final ArrayList<Entry> entries = new ArrayList<Entry>();
	private long cacheBytes;
	private long hits;
	private long misses;

	BrowserWorldSpriteCache() { this(MAX_CACHE_BYTES, 128); }

	BrowserWorldSpriteCache(long byteBudget, int entryLimit) {
		if (byteBudget < 0 || byteBudget > MAX_CACHE_BYTES || entryLimit < 1 || entryLimit > MAX_ENTRIES) {
			throw new IllegalArgumentException("Invalid sprite cache budget");
		}
		this.byteBudget = byteBudget;
		this.entryLimit = entryLimit;
	}

	Result compose(Renderer3DFrame.WorldSpriteSnapshot snapshot, OpenGLPixelWorkspace workspace) {
		if (snapshot == null) return null;
		int count = snapshot.getLayerCount();
		check(count <= MAX_LAYERS, "Too many sprite layers");
		List<Renderer2DFrame.SpriteCommand> commands = new ArrayList<Renderer2DFrame.SpriteCommand>(count);
		for (int i = 0; i < count; i++) commands.add(snapshot.getLayer(i));
		return compose(commands, workspace);
	}

	Result compose(List<Renderer2DFrame.SpriteCommand> commands, OpenGLPixelWorkspace workspace) {
		if (commands == null || commands.isEmpty()) return null;
		check(commands.size() <= MAX_LAYERS, "Too many sprite layers");
		// Match the production builder's row interpolation, including negative fractions.
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE;
		for (Renderer2DFrame.SpriteCommand c : commands) {
			if (c == null) continue;
			check(c.getWidth() > 0 && c.getWidth() <= MAX_PIXELS
				&& c.getHeight() > 0 && c.getHeight() <= 8192, "Unsupported sprite dimensions");
			long bottom = (long) c.getY() + c.getHeight();
			check(bottom <= Integer.MAX_VALUE, "Sprite Y overflow");
			minY = Math.min(minY, c.getY());
			maxY = Math.max(maxY, bottom);
			long delta = (long) c.getBottomX16() - c.getTopX16();
			// Interpolated row starts are monotone, so only the exact endpoints are needed.
			int first = c.getTopX16() >> 16;
			int last = (int) ((c.getTopX16() + delta * (c.getHeight() - 1) / c.getHeight()) >> 16);
			minX = Math.min(minX, Math.min(first, last));
			maxX = Math.max(maxX, (long) Math.max(first, last) + c.getWidth());
		}
		if (minX == Integer.MAX_VALUE) return null;
		long width = maxX - minX, height = maxY - minY;
		check(maxX <= Integer.MAX_VALUE && width > 0 && height > 0
			&& width <= MAX_PIXELS && height <= MAX_PIXELS && width * height <= MAX_PIXELS,
			"Unsupported composite bounds");
		Key probe = new Key(commands, minX, minY);
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			if (probe.same(entry.key)) {
				entries.remove(i);
				entries.add(entry);
				hits++;
				return new Result(minX, minY, (int) width, (int) height, entry.rgba);
			}
		}
		misses++;
		CompositeWorldSpriteTexture composite =
			OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(commands, workspace);
		if (composite == null) return null;
		byte[] rgba = new byte[(int) (width * height * 4)];
		composite.textureData.pixels().get(rgba);
		long cost = probe.retainedBytes + rgba.length + 256;
		if (cost <= byteBudget) {
			boolean evicted = false;
			while (!entries.isEmpty() && (entries.size() >= entryLimit || cacheBytes + cost > byteBudget)) {
				cacheBytes -= entries.remove(0).cost;
				evicted = true;
			}
			// Do not retain a previously large reference table after byte-budget eviction.
			if (evicted) entries.trimToSize();
			entries.add(new Entry(probe.ownedCopy(), rgba, cost));
			cacheBytes += cost;
		}
		return new Result(composite.x, composite.y, composite.width, composite.height, rgba);
	}

	/** Release retained cache data on context reset; statistics are lifetime totals. */
	void clear() { entries.clear(); entries.trimToSize(); cacheBytes = 0; }
	long getHits() { return hits; }
	long getMisses() { return misses; }
	long getCacheBytes() { return cacheBytes; }
	int getEntryCount() { return entries.size(); }

	static final class Result {
		final int x, y, width, height;
		/** Borrowed read-only, independent of scratch; caller must not modify. */
		final byte[] rgba;
		Result(int x, int y, int width, int height, byte[] rgba) {
			this.x = x; this.y = y; this.width = width; this.height = height; this.rgba = rgba;
		}
	}

	private static final class Entry {
		final Key key;
		final byte[] rgba;
		final long cost;
		Entry(Key key, byte[] rgba, long cost) { this.key = key; this.rgba = rgba; this.cost = cost; }
	}

	private static final class Key {
		final Layer[] layers;
		final int hash;
		final long retainedBytes;
		Key(List<Renderer2DFrame.SpriteCommand> commands, int x, int y) {
			layers = new Layer[commands.size()];
			int h = 1;
			long bytes = 128 + 8L * layers.length;
			for (int i = 0; i < layers.length; i++) {
				Renderer2DFrame.SpriteCommand c = commands.get(i);
				if (c != null) {
					layers[i] = new Layer(c, x, y);
					bytes += 512 + 4L * layers[i].pixelCount;
				}
				h = 31 * h + (layers[i] == null ? 0 : layers[i].hash);
			}
			hash = h;
			retainedBytes = bytes;
		}
		private Key(Key probe) {
			layers = new Layer[probe.layers.length];
			for (int i = 0; i < layers.length; i++) {
				if (probe.layers[i] != null) layers[i] = new Layer(probe.layers[i]);
			}
			hash = probe.hash; retainedBytes = probe.retainedBytes;
		}
		Key ownedCopy() { return new Key(this); }
		boolean same(Key other) {
			if (hash != other.hash || layers.length != other.layers.length) return false;
			for (int i = 0; i < layers.length; i++) {
				if (layers[i] == null ? other.layers[i] != null
					: other.layers[i] == null || !layers[i].same(other.layers[i])) return false;
			}
			return true;
		}
	}

	private static final class Layer {
		final long[] metadata;
		final RendererSpriteTransform transform; // final immutable value, no sprite/frame references
		final int[] pixels;
		final int pixelCount;
		final int hash;
		Layer(Renderer2DFrame.SpriteCommand c, int x, int y) {
			Sprite sprite = c.getSprite();
			long count = (long) sprite.getWidth() * sprite.getHeight();
			check(sprite.getWidth() > 0 && sprite.getHeight() > 0 && count <= MAX_PIXELS,
				"Unsupported source dimensions");
			pixels = sprite.getPixels();
			check(pixels != null && pixels.length >= count, "Incomplete source pixels");
			pixelCount = (int) count;
			metadata = new long[] {(long) c.getX() - x, (long) c.getY() - y, c.getWidth(), c.getHeight(),
				c.getSourceX(), c.getSourceY(), c.getSourceWidth(), c.getSourceHeight(),
				c.getSourceStartX16(), c.getSourceStartY16(), c.getSourceScaleX16(), c.getSourceScaleY16(),
				c.getAlpha(), (long) c.getTopX16() - ((long) x << 16),
				(long) c.getBottomX16() - ((long) x << 16), c.isMirrorX() ? 1 : 0,
				c.requiresOrderedReplay() ? 1 : 0, sprite.getWidth(), sprite.getHeight()};
			transform = c.getTransform();
			int pixelHash = 1;
			for (int i = 0; i < pixelCount; i++) pixelHash = 31 * pixelHash + pixels[i];
			hash = 31 * (31 * Arrays.hashCode(metadata) + transform.hashCode()) + pixelHash;
		}
		Layer(Layer probe) {
			metadata = probe.metadata.clone(); transform = probe.transform;
			pixelCount = probe.pixelCount; pixels = Arrays.copyOf(probe.pixels, pixelCount); hash = probe.hash;
		}
		boolean same(Layer other) {
			if (hash != other.hash || pixelCount != other.pixelCount
				|| !Arrays.equals(metadata, other.metadata) || !transform.equals(other.transform)) return false;
			for (int i = 0; i < pixelCount; i++) if (pixels[i] != other.pixels[i]) return false;
			return true;
		}
	}

	private static void check(boolean condition, String reason) {
		if (!condition) throw new IllegalArgumentException(reason);
	}
}
