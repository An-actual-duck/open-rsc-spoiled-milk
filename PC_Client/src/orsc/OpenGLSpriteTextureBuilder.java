package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;

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
		if (layerCommands == null || layerCommands.isEmpty()) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (Renderer2DFrame.SpriteCommand command : layerCommands) {
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
		int[] compositePixels = new int[width * height];
		int visiblePixelCount = 0;
		for (Renderer2DFrame.SpriteCommand command : layerCommands) {
			if (command == null) {
				continue;
			}
			visiblePixelCount += compositeSpriteCommandInto(command, minX, minY, width, compositePixels);
		}
		if (visiblePixelCount <= 0) {
			return null;
		}
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		int finalVisiblePixelCount = 0;
		for (int i = 0; i < compositePixels.length; i++) {
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

	private static int compositeSpriteCommandInto(
		Renderer2DFrame.SpriteCommand command,
		int originX,
		int originY,
		int compositeWidth,
		int[] compositePixels) {
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
			return 0;
		}
		long xDelta16 = (long) command.getBottomX16() - command.getTopX16();
		int visiblePixelCount = 0;
		for (int row = 0; row < height; row++) {
			int targetY = command.getY() + row - originY;
			int screenX0 = (int) ((command.getTopX16() + xDelta16 * row / height) >> 16);
			int sourceY = (int) ((command.getSourceStartY16() + (long) row * command.getSourceScaleY16()) >> 16);
			if (targetY < 0 || sourceY < 0 || sourceY >= spriteHeight) {
				continue;
			}
			for (int column = 0; column < width; column++) {
				int targetX = screenX0 + column - originX;
				int sourceX =
					(int) ((command.getSourceStartX16() + (long) column * command.getSourceScaleX16()) >> 16);
				if (targetX < 0
					|| targetX >= compositeWidth
					|| sourceX < 0
					|| sourceX >= spriteWidth) {
					continue;
				}
				int sourcePixel = sourcePixels[sourceY * spriteWidth + sourceX];
				if (!orsc.graphics.RendererTransparency.isVisibleSpritePixel(sourcePixel)) {
					continue;
				}
				int rgb = command.getTransform().apply(sourcePixel) & orsc.graphics.RendererTransparency.RGB_MASK;
				int targetIndex = targetY * compositeWidth + targetX;
				if (alpha >= Renderer2DFrame.SpriteCommand.FULL_ALPHA) {
					compositePixels[targetIndex] = (rgb << 8) | 0xFF;
				} else {
					compositePixels[targetIndex] = OpenGLSpriteAlpha.blendStraightRgbaOver(
						compositePixels[targetIndex],
						rgb,
						alpha);
				}
				visiblePixelCount++;
			}
		}
		return visiblePixelCount;
	}

}
