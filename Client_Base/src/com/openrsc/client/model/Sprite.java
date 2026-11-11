package com.openrsc.client.model;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Sprite {

	private int[] pixels;

	private OwnedPixels ownedPixels;

	private int[] transparentPixels;

	private byte[] unknownByteArray;

	private int width;
	private int height;

	/**
	 * WARNING: packageName, id, xShift, yShift, something1, something2 are lost
	 * when loading from img.
	 **/

	private String packageName = "unknown";
	private int id = -1;

	private boolean requiresShift;
	private int xShift = 0;
	private int yShift = 0;

	private int something1 = 0;
	private int something2 = 0;


	public Sprite() {
		pixels = new int[0];
		width = 0;
		height = 0;
	}

	public Sprite(int[] pixels, int width, int height) {
		this.pixels = pixels;
		this.width = width;
		this.height = height;
	}

	/**
	 * Create a new sprite from raw data packed into the given ByteBuffer
	 */
	public static Sprite unpack(ByteBuffer in) throws IOException {
		if (in.remaining() < 25) {
			throw new IOException("Provided buffer too short - Headers missing");
		}
		int width = in.getInt();
		int height = in.getInt();

		boolean requiresShift = in.get() == 1;
		int xShift = in.getInt();
		int yShift = in.getInt();

		int something1 = in.getInt();
		int something2 = in.getInt();

		int[] pixels = new int[width * height];
		if (in.remaining() < (pixels.length * 4)) {
			throw new IOException("Provided buffer too short - Pixels missing");
		}
		for (int c = 0; c < pixels.length; c++) {
			pixels[c] = in.getInt();
		}

		Sprite sprite = new Sprite(pixels, width, height);
		sprite.setRequiresShift(requiresShift);
		sprite.setShift(xShift, yShift);
		sprite.setSomething(something1, something2);

		return sprite;
	}

	public void setSomething(int something1, int something2) {
		this.something1 = something1;
		this.something2 = something2;
	}

	public int getSomething1() {
		return something1;
	}

	public int getSomething2() {
		return something2;
	}

	public void setName(int id, String packageName) {
		this.id = id;
		this.packageName = packageName;
	}

	public int getID() {
		return id;
	}
	public void setID(int id) { this.id = id; }

	public String getPackageName() {
		return packageName;
	}
	public void setPackageName(String name) { this.packageName = name; }

	public void setShift(int xShift, int yShift) {
		this.xShift = xShift;
		this.yShift = yShift;
	}

	public void setRequiresShift(boolean requiresShift) {
		this.requiresShift = requiresShift;
	}

	public boolean requiresShift() {
		return requiresShift;
	}

	public int getXShift() {
		return xShift;
	}
	public void setXShift(int value) { this.xShift = value; }

	public int getYShift() { return yShift;	}
	public void setYShift(int value) { this.yShift = value; }

	public int[] getPixels() {
		if (ownedPixels != null) ownedPixels.escape();
		return pixels;
	}

	public void setPixels(int[] pixels) {
		this.ownedPixels = null; // Caller retains the array: legacy alias semantics.
		this.pixels = pixels;
	}

	public int getPixel(int i) {
		return pixels[i];
	}

	public void setPixel(int i, int val) {
		pixels[i] = val;
		if (ownedPixels != null) ownedPixels.changed();
	}

	/** A nonescaping read guard. Subclasses retain their virtual getPixels contract. */
	public final boolean hasPixelData() {
		return getClass() == Sprite.class ? pixels != null : getPixels() != null;
	}

	/** Null after ANY raw array escape; no caller can re-certify an escaped buffer. */
	public final OwnedPixels getOwnedPixels() {
		return getClass() == Sprite.class && ownedPixels != null && !ownedPixels.escaped
			? ownedPixels : null;
	}

	/** Diagnostic only: 0 legacy, 1 owned, 2 escaped, 3 subclass. */
	public final int pixelOwnershipState() {
		return getClass() != Sprite.class ? 3 : ownedPixels == null ? 0 : ownedPixels.escaped ? 2 : 1;
	}

	/**
	 * Explicit actor publication boundary, NOT an in-place seal. Copies caller data
	 * once, after decoding/recolouring. The old Sprite and all its aliases retain
	 * their old behaviour. Generic constructors/setPixels remain untrusted.
	 */
    /** Accurate payload copied by a NEW publication (array headers excluded). */
    public static final class ActorCopyCost {
        public final long primaryBytes, auxiliaryBytes;
        private ActorCopyCost(long primaryBytes, long auxiliaryBytes) {
            this.primaryBytes = primaryBytes; this.auxiliaryBytes = auxiliaryBytes;
        }
        public long totalBytes() { return primaryBytes + auxiliaryBytes; }
    }

    public final ActorCopyCost ownedActorCopyCost() {
        if (getClass() != Sprite.class || ownedPixels != null || pixels == null || width <= 0 || height <= 0
            || (long) width * height > 2097152 || pixels.length > 2097152
            || pixels.length < (long) width * height
            || (transparentPixels != null && transparentPixels.length > 2097152)
            || (unknownByteArray != null && unknownByteArray.length > 2097152)) return null;
        long auxiliaryBytes = transparentPixels == null || transparentPixels == pixels
            ? 0 : 4L * transparentPixels.length;
        if (unknownByteArray != null) auxiliaryBytes += unknownByteArray.length;
        return new ActorCopyCost(4L * pixels.length, auxiliaryBytes);
    }

    public final Sprite copyForOwnedActor() { return copyForOwnedActor(Long.MAX_VALUE); }

    public final Sprite copyForOwnedActor(long payloadBudget) {
        if (getClass() != Sprite.class) return null;
        if (ownedPixels != null) return getOwnedPixels() == null ? null : this;
        ActorCopyCost cost = ownedActorCopyCost();
        if (cost == null || cost.totalBytes() > payloadBudget) return null;
        Sprite copy = new Sprite(pixels.clone(), width, height);
        copy.ownedPixels = new OwnedPixels(copy.pixels);
        copy.requiresShift = requiresShift;
        copy.xShift = xShift; copy.yShift = yShift;
        copy.something1 = something1; copy.something2 = something2;
        copy.id = id; copy.packageName = packageName;
        // Preserve an auxiliary alias, but its raw getter must revoke the same owner.
        copy.transparentPixels = transparentPixels == pixels ? copy.pixels
            : transparentPixels == null ? null : transparentPixels.clone();
        copy.unknownByteArray = unknownByteArray == null ? null : unknownByteArray.clone();
        return copy;
    }

	/**
	 * Exclusive storage with a nonescaping reader and an opaque version identity.
	 * A tracked edit changes identity; raw exposure permanently revokes trust.
	 * Render-thread only, matching Sprite/cache's existing synchronous contract.
	 */
	public static final class OwnedPixels {
		private final int[] values;
		private Object version = new Object();
		private boolean escaped;
		private OwnedPixels(int[] values) { this.values = values; }
		public int length() { return values.length; }
		public int get(int index) { return values[index]; }
		public Object version() { return escaped ? null : version; }
		public int[] expose() { escape(); return values; }
		private void escape() { escaped = true; }
		private void changed() { if (!escaped) version = new Object(); }
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String toString() {
		return "id = " + id + "; package = " + packageName;
	}

	/**
	 * Writes the sprites raw data into a ByteBuffer
	 */
	public ByteBuffer pack() throws IOException {
		ByteBuffer out = ByteBuffer.allocate(25 + (pixels.length * 4));

		out.putInt(width);
		out.putInt(height);

		out.put((byte) (requiresShift ? 1 : 0));
		out.putInt(xShift);
		out.putInt(yShift);

		out.putInt(something1);
		out.putInt(something2);

		for (int pixel : pixels) {
			out.putInt(pixel);
		}

		out.flip();
		return out;
	}

	public int[] getTransparentPixels() {
        if (ownedPixels != null && transparentPixels == pixels) ownedPixels.escape();
		return transparentPixels;
	}

	public void setTransparentPixels(int[] transparentPixels) {
		this.transparentPixels = transparentPixels;
	}

	public byte[] getUnknownByteArray() {
		return unknownByteArray;
	}

	public void setUnknownByteArray(byte[] unknownByteArray) {
		this.unknownByteArray = unknownByteArray;
	}

	// returns pink and black checkerboard pattern found in source games
	public static Sprite getUnknownSprite(int width, int height) {
		int[] pixelArray = new int[width * height];
		int squareSize = width / 6;
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if ((y / squareSize) % 2 == 0) {
					pixelArray[i++] = (x / squareSize) % 2 == 0 ? 1 : 0xFF00FF;
				} else {
					pixelArray[i++] = (x / squareSize) % 2 == 0 ? 0xFF00FF : 1;
				}
			}
		}
		return new Sprite(pixelArray, width, height);
	}
}
