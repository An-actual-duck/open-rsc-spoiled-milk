package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.GraphicsController;

/** Portraits drawn from the installed character animation frames. */
final class DialoguePortrait {
	private static final int HEAD_LAYER = 0;
	private static final int HEADWEAR_LAYER = 5;
	private static final int SIDE_FRAME = NpcDirectionalAnimationMapping.frameOffset(2, 0);

	private DialoguePortrait() {
	}

	static void drawPlayer(mudclient client, int[] layers, int hair, int skin,
						   int x, int y, int size, boolean speaking) {
		drawHead(client, EntityHandler.getAnimationDef(layers[HEAD_LAYER] - 1),
			EntityHandler.getAnimationDef(layers[HEADWEAR_LAYER] - 1), hair, skin, x, y, size, false, speaking);
	}

	static void drawNpc(mudclient client, NPCDef npc, int x, int y, int size, boolean speaking) {
		AnimationDef head = EntityHandler.getAnimationDef(npc.getSprite(HEAD_LAYER));
		if (isHead(head) && drawHead(client, head, EntityHandler.getAnimationDef(npc.getSprite(HEADWEAR_LAYER)),
			npc.getHairColour(), npc.getSkinColour(), x, y, size, true, speaking)) return;
		client.drawDialogueNpcAvatar(npc, x, y, size, tint(speaking));
	}

	private static boolean isHead(AnimationDef animation) {
		return animation != null && ("equipment".equals(animation.category)
			|| "player".equals(animation.category) && animation.getCharColour() == 1);
	}

	private static boolean drawHead(mudclient client, AnimationDef base, AnimationDef headwear, int hair, int skin,
									int x, int y, int size, boolean mirror, boolean speaking) {
		HeadLayer face = HeadLayer.load(client, base, mirror);
		HeadLayer hat = HeadLayer.load(client, headwear, mirror);
		if (face == null && hat == null) return false;

		// Head and hat frames use the same actor origin, even when their cropped
		// rectangles or full sprite widths differ. Fit the union around that origin.
		double left = face == null ? hat.left : face.left;
		double right = face == null ? hat.right : face.right;
		double top = face == null ? hat.top : face.top;
		double bottom = face == null ? hat.bottom : face.bottom;
		if (hat != null) {
			left = Math.min(left, hat.left);
			right = Math.max(right, hat.right);
			top = Math.min(top, hat.top);
			bottom = Math.max(bottom, hat.bottom);
		}
		HeadLayer anchor = face == null ? hat : face;
		double aspect = anchor.frontWidth / (double) anchor.sprite.getSomething2();
		double height = size / Math.max((right - left) * aspect, bottom - top);
		double width = height * aspect;
		double originX = x + (size - (right - left) * width) / 2 - left * width;
		double originY = y + (size - (bottom - top) * height) / 2 - top * height;
		GraphicsController surface = client.getSurface();
		int colourTransform = tint(speaking);
		if (face != null) face.draw(surface, originX, originY, width, height, hair, skin, mirror, colourTransform);
		if (hat != null) hat.draw(surface, originX, originY, width, height, hair, skin, mirror, colourTransform);
		return true;
	}

	private static int tint(boolean speaking) {
		return speaking ? 0xFFFFFFFF : 0xFF858585;
	}

	private static final class HeadLayer {
		final Sprite sprite;
		final AnimationDef animation;
		final int frontWidth;
		final double canvasWidth, origin, left, right, top, bottom;

		private HeadLayer(Sprite sprite, AnimationDef animation, int frontWidth, boolean mirror) {
			this.sprite = sprite;
			this.animation = animation;
			this.frontWidth = frontWidth;
			canvasWidth = sprite.getSomething1() / (double) frontWidth;
			origin = (1 - canvasWidth) / 2;
			int shiftX = sprite.requiresShift() ? sprite.getXShift() : 0;
			int shiftY = sprite.requiresShift() ? sprite.getYShift() : 0;
			if (mirror) shiftX = sprite.getSomething1() - shiftX - sprite.getWidth();
			left = origin + shiftX / (double) frontWidth;
			right = left + sprite.getWidth() / (double) frontWidth;
			top = shiftY / (double) sprite.getSomething2();
			bottom = top + sprite.getHeight() / (double) sprite.getSomething2();
		}

		static HeadLayer load(mudclient client, AnimationDef animation, boolean mirror) {
			if (animation == null) return null;
			Sprite sprite = client.spriteSelect(animation, SIDE_FRAME + (mirror && animation.hasF() ? 15 : 0));
			int frontWidth = client.spriteSelect(animation, 0).getSomething1();
			if (sprite.getWidth() <= 0 || sprite.getHeight() <= 0 || frontWidth <= 0
				|| sprite.getSomething1() <= 0 || sprite.getSomething2() <= 0) return null;
			return new HeadLayer(sprite, animation, frontWidth, mirror);
		}

		void draw(GraphicsController surface, double x, double y, double width, double height,
				  int hair, int skin, boolean mirror, int tint) {
			surface.drawSpriteClipping(sprite, (int) Math.round(x + origin * width), (int) Math.round(y),
				(int) Math.round(width * canvasWidth), (int) Math.round(height),
				animation.getCharColour() == 1 ? hair : animation.getCharColour(), skin, animation.getBlueMask(),
				mirror, 0, 1, tint);
		}
	}
}
