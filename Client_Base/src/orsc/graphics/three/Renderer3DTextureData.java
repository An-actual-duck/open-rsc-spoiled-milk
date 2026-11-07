package orsc.graphics.three;

import orsc.graphics.RendererTransparency;

import java.nio.ByteBuffer;

public final class Renderer3DTextureData {
	private final int textureId;
	private final int width;
	private final int height;
	private final int textureType;
	private final int[] paletteRgb;
	private final byte[] indices;
	private final int[] rgbaPixels;
	private final boolean hasTransparency;
	private final int opaquePixelCount;
	private final int transparentPixelCount;
	private final int averageOpaqueRgb;
	private final long signature;
	private final boolean animationIntent;

	private Renderer3DTextureData(
		int textureId,
		int width,
		int height,
		int textureType,
		int[] paletteRgb,
		byte[] indices,
		int[] rgbaPixels,
		boolean hasTransparency,
		int opaquePixelCount,
		int transparentPixelCount,
		int averageOpaqueRgb,
		long signature,
		boolean animationIntent) {
		this.textureId = textureId;
		this.width = width;
		this.height = height;
		this.textureType = textureType;
		this.paletteRgb = paletteRgb;
		this.indices = indices;
		this.rgbaPixels = rgbaPixels;
		this.hasTransparency = hasTransparency;
		this.opaquePixelCount = opaquePixelCount;
		this.transparentPixelCount = transparentPixelCount;
		this.averageOpaqueRgb = averageOpaqueRgb;
		this.signature = signature;
		this.animationIntent = animationIntent;
	}

	static Renderer3DTextureData fromLegacyPalette(int textureId, int[] palette, int textureType, byte[] sourceIndices) {
        return fromLegacyPalette(textureId, palette, textureType, sourceIndices, false);
    }
    static Renderer3DTextureData fromLegacyPalette(int textureId, int[] palette, int textureType, byte[] sourceIndices, boolean animationIntent) {
		int width = textureType != 0 ? 128 : 64;
		int height = width;
		int pixelCount = width * height;
		int[] paletteRgb = copyNormalizedPalette(palette);
		byte[] indices = new byte[pixelCount];
		if (sourceIndices != null) {
			System.arraycopy(sourceIndices, 0, indices, 0, Math.min(sourceIndices.length, indices.length));
		}

		return fromRgbaPixels(textureId, width, height, textureType, paletteRgb, indices, buildRgbaPixels(paletteRgb, indices), animationIntent);
	}

	static Renderer3DTextureData fromLegacyResource(int textureId, int textureType, int[] resourcePixels) {
        return fromLegacyResource(textureId, textureType, resourcePixels, false);
    }
    static Renderer3DTextureData fromLegacyResource(int textureId, int textureType, int[] resourcePixels, boolean animationIntent) {
		int width = textureType != 0 ? 128 : 64;
		int height = width;
		int pixelCount = width * height;
		int[] rgbaPixels = new int[pixelCount];
		if (resourcePixels != null) {
			for (int i = 0; i < pixelCount && i < resourcePixels.length; i++) {
				int rgb = RendererTransparency.normalizeLegacyTextureResourceColor(resourcePixels[i]);
				rgbaPixels[i] = rgb == RendererTransparency.TRANSPARENT_SAMPLE
					? 0
					: (rgb & RendererTransparency.RGB_MASK) << 8 | 0xFF;
			}
		}
		return fromRgbaPixels(textureId, width, height, textureType, new int[0], new byte[pixelCount], rgbaPixels, animationIntent);
	}

	private static int[] buildRgbaPixels(int[] paletteRgb, byte[] indices) {
		int[] rgbaPixels = new int[indices.length];
		for (int i = 0; i < indices.length; i++) {
			int paletteIndex = indices[i] & 255;
			int rgb = paletteIndex < paletteRgb.length ? paletteRgb[paletteIndex] : RendererTransparency.TRANSPARENT_SAMPLE;
			rgbaPixels[i] = rgb == RendererTransparency.TRANSPARENT_SAMPLE
				? 0
				: (rgb & RendererTransparency.RGB_MASK) << 8 | 0xFF;
		}
		return rgbaPixels;
	}

	private static Renderer3DTextureData fromRgbaPixels(
		int textureId,
		int width,
		int height,
		int textureType,
		int[] paletteRgb,
		byte[] indices,
		int[] rgbaPixels,
        boolean animationIntent) {
		int opaquePixels = 0;
		int transparentPixels = 0;
		long redTotal = 0L;
		long greenTotal = 0L;
		long blueTotal = 0L;
		for (int rgba : rgbaPixels) {
			if (rgba == 0) {
				transparentPixels++;
			} else {
				opaquePixels++;
				int rgb = (rgba >>> 8) & RendererTransparency.RGB_MASK;
				redTotal += (rgb >> 16) & 0xFF;
				greenTotal += (rgb >> 8) & 0xFF;
				blueTotal += rgb & 0xFF;
			}
		}
		int averageOpaqueRgb = 0;
		if (opaquePixels > 0) {
			int red = (int) (redTotal / opaquePixels);
			int green = (int) (greenTotal / opaquePixels);
			int blue = (int) (blueTotal / opaquePixels);
			averageOpaqueRgb = red << 16 | green << 8 | blue;
		}

		return new Renderer3DTextureData(
			textureId,
			width,
			height,
			textureType,
			paletteRgb,
			indices,
			rgbaPixels,
			transparentPixels > 0,
			opaquePixels,
			transparentPixels,
			averageOpaqueRgb,
			computeSignature(textureId, width, height, textureType, rgbaPixels), animationIntent);
	}

	private static long computeSignature(int textureId, int width, int height, int textureType, int[] rgbaPixels) {
		long signature = 1469598103934665603L;
		signature = mixSignature(signature, textureId);
		signature = mixSignature(signature, width);
		signature = mixSignature(signature, height);
		signature = mixSignature(signature, textureType);
		for (int rgba : rgbaPixels) {
			signature = mixSignature(signature, rgba);
		}
		return signature;
	}

	private static long mixSignature(long signature, long value) {
		signature ^= value;
		return signature * 1099511628211L;
	}

	private static int[] copyNormalizedPalette(int[] palette) {
		if (palette == null) {
			return new int[0];
		}
		int[] copy = new int[palette.length];
		for (int i = 0; i < palette.length; i++) {
			copy[i] = RendererTransparency.normalizeLegacyTexturePaletteColor(palette[i]);
		}
		return copy;
	}

    /** Intent is immutable and independent of RGB/alpha bytes and asset identity. */
    Renderer3DTextureData withAnimationIntent() {
        return animationIntent ? this : new Renderer3DTextureData(textureId, width, height, textureType,
            paletteRgb, indices, rgbaPixels, hasTransparency, opaquePixelCount, transparentPixelCount,
            averageOpaqueRgb, signature, true);
    }
    public boolean hasAnimatedAlpha() { return animationIntent && hasTransparency; }

	public int getTextureId() {
		return textureId;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getTextureType() {
		return textureType;
	}

	public int[] getPaletteRgb() {
		return paletteRgb;
	}

	public byte[] getIndices() {
		return indices;
	}

	public int[] getRgbaPixels() {
		return rgbaPixels;
	}

	public boolean hasTransparency() {
		return hasTransparency;
	}

	public int getOpaquePixelCount() {
		return opaquePixelCount;
	}

	public int getTransparentPixelCount() {
		return transparentPixelCount;
	}

	public int getAverageOpaqueRgb() {
		return averageOpaqueRgb;
	}

	public long getSignature() {
		return signature;
	}

	public ByteBuffer copyToDirectRgbaBuffer() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(rgbaPixels.length * 4);
		for (int rgba : rgbaPixels) {
			buffer.putInt(rgba);
		}
		buffer.flip();
		return buffer;
	}
}
