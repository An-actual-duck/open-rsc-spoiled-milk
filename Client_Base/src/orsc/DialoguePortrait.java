package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.GraphicsController;

/** Renders the existing side-facing head sprite and its appearance palette. */
final class DialoguePortrait {
	private static final int SIDE_FRAME = 6;

	private DialoguePortrait() {
	}

	static void draw(mudclient client, int animationId, int hair, int skin,
					 int x, int y, int size, boolean mirror, boolean speaking) {
		if (animationId < 0) return;
		AnimationDef animation = EntityHandler.getAnimationDef(animationId);
		if (animation == null || animation.getCharColour() != 1) return;
		Sprite sprite = client.spriteSelect(animation, SIDE_FRAME);
		if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0) return;

		// The frame is normally positioned inside a full character canvas. Fit its
		// visible bounds to the portrait without resizing or copying the asset.
		int longestSide = Math.max(sprite.getWidth(), sprite.getHeight());
		int width = sprite.getWidth() * size / longestSide;
		int height = sprite.getHeight() * size / longestSide;
		int canvasWidth = width;
		int canvasHeight = height;
		int shiftX = 0;
		int shiftY = 0;
		if (sprite.requiresShift()) {
			canvasWidth = sprite.getSomething1() * size / longestSide;
			canvasHeight = sprite.getSomething2() * size / longestSide;
			int left = mirror ? sprite.getSomething1() - sprite.getWidth() - sprite.getXShift() : sprite.getXShift();
			shiftX = left * size / longestSide;
			shiftY = sprite.getYShift() * size / longestSide;
		}

		GraphicsController surface = client.getSurface();
		surface.drawSpriteClipping(sprite, x + (size - width) / 2 - shiftX,
			y + (size - height) / 2 - shiftY, canvasWidth, canvasHeight, hair, skin, 0,
			mirror, 0, 1, speaking ? 0xFFFFFFFF : 0xFF858585);
	}
}
