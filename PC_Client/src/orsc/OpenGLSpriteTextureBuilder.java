package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.RendererTransparency;
import orsc.graphics.three.Renderer3DFrame;

import java.nio.ByteBuffer;
import java.util.List;

/*
 * RENDERER-V2 OWNER: builds command-sized dynamic sprite textures for OpenGL
 * replay paths. These are CPU-built bridge textures today; future sprite
 * batching should either reuse this boundary or replace it with persistent
 * sprite/material submissions.
 */
final class OpenGLSpriteTextureBuilder {
	private OpenGLSpriteTextureBuilder() {
	}

	static DynamicTextureData buildDirectSpriteTexture(Renderer2DFrame.SpriteCommand command) {
		Sprite sprite = command.getSprite();
		int[] sourcePixels = sprite.getPixels();
		int spriteWidth = sprite.getWidth();
		int spriteHeight = sprite.getHeight();
		int width = command.getWidth();
		int height = command.getHeight();
		int alpha = command.getAlpha();
		if (sourcePixels == null
			|| sourcePixels.length < spriteWidth * spriteHeight
			|| width <= 0
			|| height <= 0
			|| alpha <= 0) {
			return null;
		}

		ByteBuffer overlayPixels = ByteBuffer.allocateDirect(width * height * 4);
		int visiblePixelCount = 0;

		for (int row = 0; row < height; row++) {
			int sourceY = (int) ((command.getSourceStartY16() + (long) row * command.getSourceScaleY16()) >> 16);
			for (int column = 0; column < width; column++) {
				int rgba = 0;
				int sourceX = (int) ((command.getSourceStartX16() + (long) column * command.getSourceScaleX16()) >> 16);
				if (sourceX >= 0
					&& sourceX < spriteWidth
					&& sourceY >= 0
					&& sourceY < spriteHeight) {
					int sourcePixel = sourcePixels[sourceY * spriteWidth + sourceX];
					if (orsc.graphics.RendererTransparency.isVisibleSpritePixel(sourcePixel)) {
						int replayRgb = command.getTransform().apply(sourcePixel)
							& orsc.graphics.RendererTransparency.RGB_MASK;
						// SpriteCommand opacity is applied when the direct texture is drawn.
						// Keeping bridge texture pixels opaque prevents partial opacity from
						// being multiplied once here and again by OpenGL's draw colour.
						rgba = (replayRgb << 8) | Renderer2DFrame.SpriteCommand.FULL_ALPHA;
						visiblePixelCount++;
					}
				}
				overlayPixels.putInt(rgba);
			}
		}

		if (visiblePixelCount == 0) {
			return null;
		}
		overlayPixels.flip();
		return new DynamicTextureData(width, height, overlayPixels, visiblePixelCount);
	}

	static CompositeWorldSpriteTexture buildCompositeCharacterSpriteTexture(
		List<Renderer2DFrame.SpriteCommand> layerCommands) {
		return buildCompositeCharacterSpriteTexture(null, layerCommands, null);
	}

	static CompositeWorldSpriteTexture buildCompositeCharacterSpriteTexture(
		Renderer3DFrame.WorldSpriteSnapshot snapshot) {
		return buildCompositeCharacterSpriteTexture(snapshot, null, null);
	}

	/** Borrowed storage; only the synchronous world-sprite upload owner uses this. */
	static CompositeWorldSpriteTexture buildCompositeCharacterSpriteTexture(
		Renderer3DFrame.WorldSpriteSnapshot snapshot, OpenGLPixelWorkspace workspace) {
		return buildCompositeCharacterSpriteTexture(snapshot, null, workspace);
	}

	static CompositeWorldSpriteTexture buildCompositeCharacterSpriteTexture(
		List<Renderer2DFrame.SpriteCommand> layers, OpenGLPixelWorkspace workspace) {
		return buildCompositeCharacterSpriteTexture(null, layers, workspace);
	}

	/** Produces owned WebGL upload bytes for the bounds verified by the sprite cache. */
	static byte[] buildCompositeSpriteRgba(List<Renderer2DFrame.SpriteCommand> layers,
		int x, int y, int width, int height, OpenGLPixelWorkspace workspace) {
		int pixelCount = width * height;
		int[] raster = rasterizeComposite(null, layers, layers.size(), x, y, width, pixelCount, workspace);
		if (raster == null) return null;
		byte[] rgba = new byte[pixelCount * 4];
		for (int i = 0, offset = 0; i < pixelCount; i++, offset += 4) {
			int pixel = raster[i];
			rgba[offset] = (byte) (pixel >>> 24);
			rgba[offset + 1] = (byte) (pixel >>> 16);
			rgba[offset + 2] = (byte) (pixel >>> 8);
			rgba[offset + 3] = (byte) pixel;
		}
		return rgba;
	}

	private static CompositeWorldSpriteTexture buildCompositeCharacterSpriteTexture(
		Renderer3DFrame.WorldSpriteSnapshot snapshot,
		List<Renderer2DFrame.SpriteCommand> layerCommands,
		OpenGLPixelWorkspace workspace) {
		int layerCount = snapshot == null
			? (layerCommands == null ? 0 : layerCommands.size())
			: snapshot.getLayerCount();
		if (layerCount == 0) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
			Renderer2DFrame.SpriteCommand command = snapshot == null
				? layerCommands.get(layerIndex)
				: snapshot.getLayer(layerIndex);
			if (command == null) {
				continue;
			}
			minY = Math.min(minY, command.getY());
			maxY = Math.max(maxY, command.getY() + command.getHeight());
			long xDelta16 = (long) command.getBottomX16() - command.getTopX16();
			for (int row = 0; row < command.getHeight(); row++) {
				int rowLeft = (int) ((command.getTopX16() + xDelta16 * row / command.getHeight()) >> 16);
				minX = Math.min(minX, rowLeft);
				maxX = Math.max(maxX, rowLeft + command.getWidth());
			}
		}
		if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE || maxX <= minX || maxY <= minY) {
			return null;
		}
		int width = maxX - minX;
		int height = maxY - minY;
		int pixelCount = width * height;
		int[] compositePixels = rasterizeComposite(snapshot, layerCommands, layerCount, minX, minY, width,
			pixelCount, workspace);
		if (compositePixels == null) return null;
		ByteBuffer pixels = workspace == null
			? ByteBuffer.allocateDirect(pixelCount * 4) : workspace.rgba(pixelCount * 4);
		int finalVisiblePixelCount = 0;
		for (int i = 0; i < pixelCount; i++) {
			int rgba = compositePixels[i];
			if ((rgba & 0xFF) != 0) {
				finalVisiblePixelCount++;
			}
			pixels.putInt(rgba);
		}
		pixels.flip();
		return new CompositeWorldSpriteTexture(
			minX,
			minY,
			width,
			height,
			new DynamicTextureData(width, height, pixels, finalVisiblePixelCount));
	}

	private static int[] rasterizeComposite(Renderer3DFrame.WorldSpriteSnapshot snapshot,
		List<Renderer2DFrame.SpriteCommand> layers, int layerCount, int x, int y, int width,
		int pixelCount, OpenGLPixelWorkspace workspace) {
		int[] pixels = workspace == null ? new int[pixelCount] : workspace.clearedComposite(pixelCount);
		boolean visible = false;
		for (int i = 0; i < layerCount; i++) {
			Renderer2DFrame.SpriteCommand layer = snapshot == null ? layers.get(i) : snapshot.getLayer(i);
			if (layer != null) visible |= compositeSpriteCommandInto(layer, x, y, width, pixels, workspace);
		}
		return visible ? pixels : null;
	}

	private static boolean compositeSpriteCommandInto(
		Renderer2DFrame.SpriteCommand command,
		int originX,
		int originY,
		int compositeWidth,
		int[] compositePixels,
		OpenGLPixelWorkspace workspace) {
		Sprite sprite = command.getSprite();
		int[] sourcePixels = sprite.getPixels();
		int spriteWidth = sprite.getWidth();
		int spriteHeight = sprite.getHeight();
		int width = command.getWidth();
		int height = command.getHeight();
		int alpha = command.getAlpha();
		if (sourcePixels == null
			|| sourcePixels.length < spriteWidth * spriteHeight
			|| width <= 0
			|| height <= 0
			|| alpha <= 0) {
			return false;
		}
		// A layer's source columns are independent of both the row and destination skew.
		int[] sourceColumns = workspace == null ? new int[width] : workspace.spriteColumns(width);
		long sourceX16 = command.getSourceStartX16();
		int sourceStepX16 = command.getSourceScaleX16();
		for (int column = 0; column < width; column++, sourceX16 += sourceStepX16) {
			sourceColumns[column] = (int) (sourceX16 >> 16);
		}
		int topX16 = command.getTopX16();
		long xDelta16 = (long) command.getBottomX16() - topX16;
		int y = command.getY();
		int sourceStartY16 = command.getSourceStartY16();
		int sourceStepY16 = command.getSourceScaleY16();
		RendererSpriteTransform transform = command.getTransform();
		boolean opaque = alpha >= Renderer2DFrame.SpriteCommand.FULL_ALPHA;
		boolean visible = false;
		for (int row = 0; row < height; row++) {
			int targetY = y + row - originY;
			int sourceY = (int) ((sourceStartY16 + (long) row * sourceStepY16) >> 16);
			if (targetY < 0 || sourceY < 0 || sourceY >= spriteHeight) {
				continue;
			}
			int screenX0 = (int) ((topX16 + xDelta16 * row / height) >> 16);
			int targetRow = targetY * compositeWidth;
			int sourceRow = sourceY * spriteWidth;
			for (int column = 0; column < width; column++) {
				int targetX = screenX0 + column - originX;
				int sourceX = sourceColumns[column];
				if (targetX < 0
					|| targetX >= compositeWidth
					|| sourceX < 0
					|| sourceX >= spriteWidth) {
					continue;
				}
				int sourcePixel = sourcePixels[sourceRow + sourceX];
				if (!RendererTransparency.isVisibleSpritePixel(sourcePixel)) {
					continue;
				}
				int rgb = transform.apply(sourcePixel) & RendererTransparency.RGB_MASK;
				int targetIndex = targetRow + targetX;
				if (opaque) {
					compositePixels[targetIndex] = (rgb << 8) | 0xFF;
				} else {
					compositePixels[targetIndex] = OpenGLSpriteAlpha.blendStraightRgbaOver(
						compositePixels[targetIndex],
						rgb,
						alpha);
				}
				visible = true;
			}
		}
		return visible;
	}

}
