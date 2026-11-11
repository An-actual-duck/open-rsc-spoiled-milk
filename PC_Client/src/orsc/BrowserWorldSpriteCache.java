package orsc;

import com.openrsc.client.model.Sprite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;

/** Optional render-thread-only cache; not enabled by constructing a renderer.
 * Source arrays are mutable: legacy/escaped sources are hashed AND compared.
 * Explicit owned actor planes use their opaque edit token; metadata is always checked.
 * Callers must not mutate sprites during compose, or write the returned RGBA array.
 * No anchor/depth/frame state is retained. Caller scratch is borrowed only on misses.
 */
final class BrowserWorldSpriteCache {
	private static final boolean KEY_LIMBS = Boolean.parseBoolean(
		System.getProperty("spoiledmilk.browserSpriteKeyLimbs", "true"));
	static boolean isKeyLimbsEnabled() { return KEY_LIMBS; }
    private static final boolean OWNED_ACTORS = Boolean.getBoolean("spoiledmilk.browserOwnedActorPixels");
    static boolean isOwnedActorsRequested() { return OWNED_ACTORS; }
    private final Counters counters = new Counters();
    private static final class Counters {
        long trustedLayers, legacyLayers, escapedLayers, subclassLayers;
        long hashedPixels, comparedPixels, copiedPixels, avoidedHashPixels, avoidedComparePixels;
    }
    /** Bounded lifetime work counters; no pixel-level increments or exported arrays per frame. */
    double[] diagnostics() {
        return new double[] {counters.trustedLayers,counters.legacyLayers,counters.escapedLayers,
            counters.subclassLayers,counters.hashedPixels,counters.comparedPixels,counters.copiedPixels,
            counters.avoidedHashPixels,counters.avoidedComparePixels,hits,misses,cacheBytes,entries.size()};
    }
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
	private int nextImageId=1;
	private int epoch=1;

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
		Key probe = new Key(commands, minX, minY, counters);
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			if (probe.same(entry.key, counters)) {
				entries.remove(i);
				entries.add(entry);
				hits++;
				return new Result(minX, minY, (int) width, (int) height, entry.rgba, entry.id);
			}
		}
		misses++;
		CompositeWorldSpriteTexture composite =
			OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(commands, workspace);
		if (composite == null) return null;
		byte[] rgba = new byte[(int) (width * height * 4)];
		composite.textureData.pixels().get(rgba);
		int imageId=nextImageId();
		long cost = probe.retainedBytes + rgba.length + 256;
		if (cost <= byteBudget) {
			boolean evicted = false;
			while (!entries.isEmpty() && (entries.size() >= entryLimit || cacheBytes + cost > byteBudget)) {
				cacheBytes -= entries.remove(0).cost;
				evicted = true;
			}
			// Do not retain a previously large reference table after byte-budget eviction.
			if (evicted) entries.trimToSize();
			entries.add(new Entry(probe.ownedCopy(counters), rgba, cost, imageId));
			cacheBytes += cost;
		}
		return new Result(composite.x, composite.y, composite.width, composite.height, rgba, imageId);
	}

	/** Release retained cache data on context reset; statistics are lifetime totals. */
	void clear() { if(epoch==Integer.MAX_VALUE)throw new IllegalStateException("Sprite epoch exhausted"); entries.clear(); entries.trimToSize(); cacheBytes = 0; epoch++; }
	int getEpoch(){return epoch;}
	int nextImageId(){if(nextImageId==Integer.MAX_VALUE)throw new IllegalStateException("Sprite identity exhausted");return nextImageId++;}
	long getHits() { return hits; }
	long getMisses() { return misses; }
	long getCacheBytes() { return cacheBytes; }
	int getEntryCount() { return entries.size(); }

	static final class Result {
		final int x, y, width, height, id;
		/** Borrowed read-only, independent of scratch; caller must not modify. */
		final byte[] rgba;
		Result(int x, int y, int width, int height, byte[] rgba, int id) {
			this.x = x; this.y = y; this.width = width; this.height = height; this.rgba = rgba; this.id=id;
		}
	}

	private static final class Entry {
		final Key key;
		final byte[] rgba;
		final long cost;
		final int id;
		Entry(Key key, byte[] rgba, long cost, int id) { this.key = key; this.rgba = rgba; this.cost = cost; this.id=id; }
	}

	private static final class Key {
		final Layer[] layers;
		final int hash;
		final long retainedBytes;
		Key(List<Renderer2DFrame.SpriteCommand> commands, int x, int y, Counters counters) {
			layers = new Layer[commands.size()];
			int h = 1;
			long bytes = 128 + 8L * layers.length;
			for (int i = 0; i < layers.length; i++) {
				Renderer2DFrame.SpriteCommand c = commands.get(i);
				if (c != null) {
					layers[i] = new Layer(c, x, y, counters);
					bytes += 512 + (layers[i].ownedVersion == null ? 4L * layers[i].pixelCount : 0);
				}
				h = 31 * h + (layers[i] == null ? 0 : layers[i].hash);
			}
			hash = h;
			retainedBytes = bytes;
		}
		private Key(Key probe, Counters counters) {
			layers = new Layer[probe.layers.length];
			for (int i = 0; i < layers.length; i++) {
				if (probe.layers[i] != null) layers[i] = new Layer(probe.layers[i], counters);
			}
			hash = probe.hash; retainedBytes = probe.retainedBytes;
		}
		Key ownedCopy(Counters counters) { return new Key(this, counters); }
		boolean same(Key other, Counters counters) {
			if (hash != other.hash || layers.length != other.layers.length) return false;
			for (int i = 0; i < layers.length; i++) {
				if (layers[i] == null ? other.layers[i] != null
					: other.layers[i] == null || !layers[i].same(other.layers[i], counters)) return false;
			}
			return true;
		}
	}

	private static final class Layer {
		final long[] metadata;
		final int[] metadataLimbs;
		final RendererSpriteTransform transform; // final immutable value, no sprite/frame references
		final int[] pixels;
        final Object ownedVersion; // Opaque identity does not retain the source array.
		final int pixelCount;
		final int hash;
		Layer(Renderer2DFrame.SpriteCommand c, int x, int y, Counters counters) {
			Sprite sprite = c.getSprite();
            Sprite.OwnedPixels owned = OWNED_ACTORS ? sprite.getOwnedPixels() : null;
            ownedVersion = owned == null ? null : owned.version();
            if (owned != null) counters.trustedLayers++;
            else if (sprite.pixelOwnershipState() == 2) counters.escapedLayers++;
            else if (sprite.pixelOwnershipState() == 3) counters.subclassLayers++;
            else counters.legacyLayers++;
			if (KEY_LIMBS) {
				int width=sprite.getWidth(),height=sprite.getHeight();
				double count=(double)width*height;
				// Positive accepted integer products are <=2^21, hence exact.
				// Any inexact double product is many orders above this bound.
				check(width>0 && height>0 && count<=MAX_PIXELS,"Unsupported source dimensions");
				pixelCount=(int)count;
				pixels=owned == null ? sprite.getPixels() : null;
                check(owned == null ? pixels!=null && pixels.length>=pixelCount : owned.length()>=pixelCount,"Incomplete source pixels");
			} else {
				long count = (long) sprite.getWidth() * sprite.getHeight();
				check(sprite.getWidth() > 0 && sprite.getHeight() > 0 && count <= MAX_PIXELS,
					"Unsupported source dimensions");
				pixels = owned == null ? sprite.getPixels() : null;
                check(owned == null ? pixels != null && pixels.length >= count : owned.length() >= count, "Incomplete source pixels");
				pixelCount = (int) count;
			}
			if (KEY_LIMBS) {
				metadata = null;
				metadataLimbs = new int[] {0,0,0,0,c.getWidth(),c.getHeight(),
					c.getSourceX(),c.getSourceY(),c.getSourceWidth(),c.getSourceHeight(),
					c.getSourceStartX16(),c.getSourceStartY16(),c.getSourceScaleX16(),c.getSourceScaleY16(),
					c.getAlpha(),0,0,0,0,c.isMirrorX()?1:0,c.requiresOrderedReplay()?1:0,
					sprite.getWidth(),sprite.getHeight()};
				putDifference(metadataLimbs,0,c.getX(),c.getX()>>31,x,x>>31);
				putDifference(metadataLimbs,2,c.getY(),c.getY()>>31,y,y>>31);
				putDifference(metadataLimbs,15,c.getTopX16(),c.getTopX16()>>31,x<<16,x>>16);
				putDifference(metadataLimbs,17,c.getBottomX16(),c.getBottomX16()>>31,x<<16,x>>16);
			} else {
				metadataLimbs = null;
				metadata = new long[] {(long) c.getX() - x, (long) c.getY() - y, c.getWidth(), c.getHeight(),
				c.getSourceX(), c.getSourceY(), c.getSourceWidth(), c.getSourceHeight(),
				c.getSourceStartX16(), c.getSourceStartY16(), c.getSourceScaleX16(), c.getSourceScaleY16(),
				c.getAlpha(), (long) c.getTopX16() - ((long) x << 16),
				(long) c.getBottomX16() - ((long) x << 16), c.isMirrorX() ? 1 : 0,
				c.requiresOrderedReplay() ? 1 : 0, sprite.getWidth(), sprite.getHeight()};
			}
			transform = c.getTransform();
			int pixelHash = 1;
			if (ownedVersion == null) {
                for (int i = 0; i < pixelCount; i++) pixelHash = 31 * pixelHash + pixels[i];
                counters.hashedPixels += pixelCount;
            } else {
                pixelHash = System.identityHashCode(ownedVersion);
                counters.avoidedHashPixels += pixelCount;
            }
			hash = 31 * (31 * (KEY_LIMBS ? metadataHash(metadataLimbs) : Arrays.hashCode(metadata)) + transform.hashCode()) + pixelHash;
		}

		/** Exact signed-64 subtraction represented by two int limbs. The low int
		 * intentionally wraps mod2^32; unsigned borrow corrects the high limb.
		 * All caller high differences fit int (sign limbs or signed x>>16). */
		private static void putDifference(int[] out,int at,int aLow,int aHigh,int bLow,int bHigh) {
			out[at+1] = aLow-bLow;
			out[at] = aHigh-bHigh-((aLow^Integer.MIN_VALUE)<(bLow^Integer.MIN_VALUE)?1:0);
		}
		/** Identical to Arrays.hashCode(long[19]), including negative narrow ints. */
		private static int metadataHash(int[] m) {
			int h=31+(m[0]^m[1]);
			h=31*h+(m[2]^m[3]);
			for(int i=4;i<=14;i++)h=31*h+(m[i]^(m[i]>>31));
			h=31*h+(m[15]^m[16]);h=31*h+(m[17]^m[18]);
			for(int i=19;i<23;i++)h=31*h+(m[i]^(m[i]>>31));
			return h;
		}

		Layer(Layer probe, Counters counters) {
			metadata = probe.metadata == null ? null : probe.metadata.clone();
			metadataLimbs = probe.metadataLimbs == null ? null : probe.metadataLimbs.clone(); transform = probe.transform;
			pixelCount = probe.pixelCount; ownedVersion = probe.ownedVersion;
            pixels = ownedVersion == null ? Arrays.copyOf(probe.pixels, pixelCount) : null;
            if (ownedVersion == null) counters.copiedPixels += pixelCount;
            hash = probe.hash;
		}
		boolean same(Layer other, Counters counters) {
			if (hash != other.hash || pixelCount != other.pixelCount
				|| !(KEY_LIMBS ? Arrays.equals(metadataLimbs, other.metadataLimbs) : Arrays.equals(metadata, other.metadata)) || !transform.equals(other.transform)) return false;
			if (ownedVersion != null || other.ownedVersion != null) {
                if (ownedVersion == null || ownedVersion != other.ownedVersion) return false;
                counters.avoidedComparePixels += pixelCount;
                return true;
            }
            for (int i = 0; i < pixelCount; i++) if (pixels[i] != other.pixels[i]) {
                counters.comparedPixels += i + 1;
                return false;
            }
            counters.comparedPixels += pixelCount;
			return true;
		}
	}

	private static void check(boolean condition, String reason) {
		if (!condition) throw new IllegalArgumentException(reason);
	}
}
