package orsc;

import java.nio.ByteBuffer;

final class OpenGLTexturePixels {
	private OpenGLTexturePixels() {
	}

	static ByteBuffer buildPaddedRgbaPixels(
		ByteBuffer sourcePixels,
		int contentWidth,
		int contentHeight,
		int padding) {
		return buildPaddedRgbaPixels(sourcePixels, contentWidth, contentHeight, padding, null);
	}

	/** Borrowed result is consumed by glTexSubImage2D before this owner acquires again. */
	static ByteBuffer buildPaddedRgbaPixels(
		ByteBuffer sourcePixels,
		int contentWidth,
		int contentHeight,
		int padding,
		OpenGLPixelWorkspace workspace) {
		if (padding <= 0) {
			ByteBuffer duplicate = sourcePixels.duplicate();
			duplicate.position(0);
			duplicate.limit(contentWidth * contentHeight * 4);
			return duplicate;
		}

		int paddedWidth = contentWidth + padding * 2;
		int paddedHeight = contentHeight + padding * 2;
		ByteBuffer source = sourcePixels.duplicate();
		source.position(0);
		source.limit(contentWidth * contentHeight * 4);
		int bytes = paddedWidth * paddedHeight * 4;
		ByteBuffer padded = workspace == null
			? ByteBuffer.allocateDirect(bytes) : workspace.rgba(bytes);
		for (int y = -padding; y < contentHeight + padding; y++) {
			int sourceY = clamp(y, 0, contentHeight - 1);
			for (int x = -padding; x < contentWidth + padding; x++) {
				int sourceX = clamp(x, 0, contentWidth - 1);
				padded.putInt(source.getInt((sourceY * contentWidth + sourceX) * 4));
			}
		}
		padded.flip();
		return padded;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
