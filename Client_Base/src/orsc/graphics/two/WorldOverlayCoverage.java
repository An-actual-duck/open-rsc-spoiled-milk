package orsc.graphics.two;

import java.util.Arrays;

/** Optional, render-thread-owned transmittance for one software RGB surface.
 * The ordinary RGB writers remain authoritative and unchanged. For a GPU world
 * base, RGB contains the legacy result over black and T is the remaining world
 * coefficient: output = RGB + T * world. Legacy integer rounding is approximate
 * under this affine reconstruction; it is not a bit-exact general compositor.
 */
public final class WorldOverlayCoverage {
	public static final int MAX_PIXELS = 2 * 1024 * 1024;
	public static final int MODE_OPAQUE = 0;
	public static final int MODE_WORLD = 1;
	public static final int MODE_RETAINED_BASE = 2;
	public static final int MODE_RETAINED_FADE = 3;
	public static final int MODE_INVALID = 4;
	public static final int MAX_RETAINED_FADES = 16;
	private float[] transmittance;
	private int[] ownerPixels;
	private int width;
	private int height;
	private boolean transparent;
	private boolean valid = true;
	private String reason = "";
	private long generation;
	private long frameSequence;
	private boolean written;
	private boolean freshWorld;
	private boolean retained;
	private int retainedFadeCount;

	void beginFrame() {
		frameSequence++;
		written = freshWorld = false;
		retainedFadeCount = 0;
	}

	boolean begin(int[] pixels, int width, int height) {
		reset(false);
		long count = (long) width * height;
		if (pixels == null || width <= 0 || height <= 0 || count > MAX_PIXELS || pixels.length < count) {
			valid = false;
			reason = "Unsupported world overlay surface";
			return false;
		}
		this.ownerPixels = pixels;
		this.width = width;
		this.height = height;
		if (transmittance == null || transmittance.length != count) transmittance = new float[(int) count];
		Arrays.fill(transmittance, 1f);
		Arrays.fill(pixels, 0, (int) count, 0);
		transparent = true;
		freshWorld = true;
		return true;
	}

	void reset(boolean release) {
		ownerPixels = null;
		width = height = 0;
		transparent = false;
		written = freshWorld = retained = false;
		retainedFadeCount = 0;
		valid = true;
		reason = "";
		generation++;
		if (release) transmittance = null;
	}

	void discard() {
		boolean partialImage = transparent || !valid;
		reset(true);
		if (partialImage) {
			valid = false;
			reason = "Discarded overlay requires a new complete image/world base";
		}
	}

	boolean isValid() { return valid; }

	void opaque(int[] destination, int index) {
		if (transparent && valid && destination == ownerPixels) { written = true; transmittance[index] = 0f; }
	}

	void multiply(int[] destination, int index, int numerator) {
		if (transparent && valid && destination == ownerPixels) {
			written = true;
			if (numerator < 0 || numerator > 256) {
				invalidate("Out-of-range overlay blend coefficient");
				return;
			}
			transmittance[index] *= numerator * (1f / 256f);
		}
	}

	/** Request exact fade of the GPU's preceding FULL final image, then start a UI delta.
	 * This is only valid before new drawing in a retained frame. Legacy RGB remains
	 * untouched when coverage is opaque/disabled; callers execute the old fade then.
	 */
	boolean retainFade(int[] pixels, int width, int height) {
		if (!transparent || !valid) return false;
		if (pixels != ownerPixels || width != this.width || height != this.height) {
			invalidate("Retained fade surface identity/dimensions changed");
			return false;
		}
		if (written || freshWorld || retainedFadeCount >= MAX_RETAINED_FADES) {
			invalidate("Retained fade after new drawing/fresh world or beyond fade limit");
			return false;
		}
		if (retainedFadeCount == 0) {
			Arrays.fill(pixels, 0, width * height, 0);
			Arrays.fill(transmittance, 1f);
		}
		retained = true;
		retainedFadeCount++;
		return true;
	}

	void invalidate(String reason) {
		if (transparent && valid) { valid = false; this.reason = reason; }
	}

	Frame borrow(int[] pixels, int width, int height) {
		if (transparent && (pixels != ownerPixels || width != this.width || height != this.height)) {
			invalidate("World overlay surface identity/dimensions changed");
		}
		return new Frame(this, pixels, width, height, transparent ? transmittance : null,
			transparent, valid, reason, generation, frameSequence, mode(), retainedFadeCount);
	}

	private int mode() {
		return !valid ? MODE_INVALID : !transparent ? MODE_OPAQUE : !retained ? MODE_WORLD
			: retainedFadeCount > 0 ? MODE_RETAINED_FADE : MODE_RETAINED_BASE;
	}

	boolean isCurrent(Frame frame, int[] pixels, int width, int height) {
		return frame != null && frame.owner == this && frame.ownerPixels == pixels && frame.width == width && frame.height == height
			&& frame.generation == generation && frame.frameSequence == frameSequence && frame.valid && valid
			&& frame.mode == mode() && frame.retainedFadeCount == retainedFadeCount
			&& frame.transparent == transparent
			&& (!transparent || (pixels == ownerPixels && width == this.width && height == this.height
				&& frame.transmittance == transmittance));
	}

	/** Synchronous borrowed view; RGB and T expire at the next surface mutation.
	 * Invalid views MUST NOT be presented: request a complete software-owned frame.
	 * A valid opaque view has null T and the RGB image must be displayed opaquely.
	 */
	public static final class Frame {
		private final WorldOverlayCoverage owner;
		public final int[] ownerPixels;
		public final int width;
		public final int height;
		public final float[] transmittance;
		public final boolean transparent;
		public final boolean valid;
		public final String reason;
		public final long generation;
		public final long frameSequence;
		public final int mode;
		public final int retainedFadeCount;
		private Frame(WorldOverlayCoverage owner, int[] pixels, int width, int height, float[] transmittance,
			boolean transparent, boolean valid, String reason, long generation, long frameSequence,
			int mode, int retainedFadeCount) {
			this.owner = owner; ownerPixels = pixels; this.width = width; this.height = height;
			this.transmittance = transmittance; this.transparent = transparent;
			this.valid = valid; this.reason = reason; this.generation = generation;
			this.frameSequence = frameSequence; this.mode = mode; this.retainedFadeCount = retainedFadeCount;
		}
	}
}
