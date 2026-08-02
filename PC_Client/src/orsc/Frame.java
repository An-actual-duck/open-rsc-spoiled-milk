package orsc;

import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.nio.ByteBuffer;

/*
 * LEGACY BRIDGE: wraps the software framebuffer pixels that still feed parts
 * of the OpenGL presenter. Do not add world-renderer ownership here; move that
 * into renderer-v2 pass owners.
 */
final class Frame {
	final int sourceWidth;
	final int sourceHeight;
	final int targetWidth;
	final int targetHeight;
	final boolean linearFiltering;
	private final int byteCount;
	final Renderer2DFrame renderer2DFrame;
	final Renderer3DFrame renderer3DFrame;
	final String[] rendererDebugOverlayLines;
	long submittedNanos;
	private final FrameBufferPool frameBufferPool;
	private final FrameBuffer frameBuffer;
	private boolean released;

	private Frame(
		int sourceWidth,
		int sourceHeight,
		int targetWidth,
		int targetHeight,
		boolean linearFiltering,
		int byteCount,
		Renderer2DFrame renderer2DFrame,
		Renderer3DFrame renderer3DFrame,
		String[] rendererDebugOverlayLines,
		FrameBufferPool frameBufferPool,
		FrameBuffer frameBuffer) {
		this.sourceWidth = sourceWidth;
		this.sourceHeight = sourceHeight;
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;
		this.linearFiltering = linearFiltering;
		this.byteCount = byteCount;
		this.renderer2DFrame = renderer2DFrame;
		this.renderer3DFrame = renderer3DFrame;
		this.rendererDebugOverlayLines =
			rendererDebugOverlayLines == null ? null : rendererDebugOverlayLines.clone();
		this.frameBufferPool = frameBufferPool;
		this.frameBuffer = frameBuffer;
	}

	static Frame fromImage(
		BufferedImage image,
		float scalar,
		ScaledWindow.ScalingAlgorithm scalingAlgorithm,
		FrameBufferPool frameBufferPool,
		Renderer2DFrame renderer2DFrame,
		Renderer3DFrame renderer3DFrame,
		String[] rendererDebugOverlayLines) {
		int sourceWidth = image.getWidth();
		int sourceHeight = image.getHeight();
		int targetWidth = Math.max(1, Math.round(sourceWidth * scalar));
		int targetHeight = Math.max(1, Math.round(sourceHeight * scalar));
		boolean linearFiltering = false;
		int byteCount = sourceWidth * sourceHeight * 4;
		FrameBuffer frameBuffer = frameBufferPool.acquire(byteCount);

		try {
			frameBuffer.buffer.clear();
			frameBuffer.buffer.limit(byteCount);
			copyPixelsToRgba(image, sourceWidth, sourceHeight, frameBuffer.buffer);
			frameBuffer.buffer.flip();
			return new Frame(
				sourceWidth,
				sourceHeight,
				targetWidth,
				targetHeight,
				linearFiltering,
				byteCount,
				renderer2DFrame,
				renderer3DFrame,
				rendererDebugOverlayLines,
				frameBufferPool,
				frameBuffer);
		} catch (RuntimeException e) {
			frameBufferPool.release(frameBuffer);
			if (renderer3DFrame != null) {
				renderer3DFrame.release();
			}
			throw e;
		}
	}

	ByteBuffer pixels() {
		ByteBuffer pixels = frameBuffer.buffer.duplicate();
		pixels.position(0);
		pixels.limit(byteCount);
		return pixels;
	}

	void release() {
		if (released) {
			return;
		}
		released = true;
		if (renderer3DFrame != null) {
			renderer3DFrame.release();
		}
		frameBufferPool.release(frameBuffer);
	}

	private static void copyPixelsToRgba(BufferedImage image, int width, int height, ByteBuffer target) {
		if (copyPackedIntRgbToRgba(image, width, height, target)) {
			return;
		}

		int[] rowPixels = new int[width];
		for (int y = 0; y < height; y++) {
			image.getRGB(0, y, width, 1, rowPixels, 0, width);
			for (int x = 0; x < width; x++) {
				putRgbAsRgba(target, rowPixels[x]);
			}
		}
	}

	private static boolean copyPackedIntRgbToRgba(
		BufferedImage image,
		int width,
		int height,
		ByteBuffer target) {
		Raster raster = image.getRaster();
		DataBuffer dataBuffer = raster.getDataBuffer();
		SampleModel sampleModel = raster.getSampleModel();
		if (!(dataBuffer instanceof DataBufferInt)
			|| ((DataBufferInt) dataBuffer).getNumBanks() != 1
			|| !(sampleModel instanceof SinglePixelPackedSampleModel)) {
			return false;
		}

		SinglePixelPackedSampleModel packedSampleModel = (SinglePixelPackedSampleModel) sampleModel;
		if (!hasRgbMasks(packedSampleModel.getBitMasks())) {
			return false;
		}

		int stride = packedSampleModel.getScanlineStride();
		if (stride < width) {
			return false;
		}

		DataBufferInt intBuffer = (DataBufferInt) dataBuffer;
		int[] source = intBuffer.getData();
		int sourceOffset = intBuffer.getOffset()
			+ packedSampleModel.getOffset(
				raster.getMinX() - raster.getSampleModelTranslateX(),
				raster.getMinY() - raster.getSampleModelTranslateY());
		int lastSourceIndex = sourceOffset + (height - 1) * stride + width - 1;
		if (sourceOffset < 0 || lastSourceIndex >= source.length) {
			return false;
		}

		for (int y = 0; y < height; y++) {
			int rowOffset = sourceOffset + y * stride;
			for (int x = 0; x < width; x++) {
				putRgbAsRgba(target, source[rowOffset + x]);
			}
		}
		return true;
	}

	private static boolean hasRgbMasks(int[] masks) {
		return masks.length >= 3
			&& masks[0] == 0x00FF0000
			&& masks[1] == 0x0000FF00
			&& masks[2] == 0x000000FF;
	}

	private static void putRgbAsRgba(ByteBuffer target, int pixel) {
		target.putInt(((pixel & 0x00FFFFFF) << 8) | 0xFF);
	}
}
