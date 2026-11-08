package orsc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Render-thread scratch only: borrowed pixels expire at the next acquire.
 * Callers must finish synchronous texture upload before acquiring again.
 * Oversized inputs use independent storage and never enlarge retained buffers.
 */
final class OpenGLPixelWorkspace {
	static final int MAX_RETAINED_PIXELS = 1 << 20;
	private static final int[] EMPTY = new int[0];
	private int[] composite = EMPTY;
	private ByteBuffer rgba;

	int[] clearedComposite(int pixels) {
		if (pixels > MAX_RETAINED_PIXELS) return new int[pixels];
		if (composite.length < pixels) {
			composite = new int[capacity(pixels)];
		} else {
			Arrays.fill(composite, 0, pixels, 0);
		}
		return composite;
	}

	ByteBuffer rgba(int bytes) {
		if (bytes > MAX_RETAINED_PIXELS * 4) return ByteBuffer.allocateDirect(bytes);
		if (rgba == null || rgba.capacity() < bytes) {
			rgba = ByteBuffer.allocateDirect(capacity(bytes));
		}
		rgba.clear();
		rgba.limit(bytes);
		rgba.order(ByteOrder.BIG_ENDIAN);
		return rgba;
	}

	void clear() {
		composite = EMPTY;
		rgba = null;
	}

	private static int capacity(int required) {
		return required <= 1 ? 1 : Integer.highestOneBit(required - 1) << 1;
	}
}
