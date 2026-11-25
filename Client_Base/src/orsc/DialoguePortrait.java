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
		PortraitLayer face = loadHead(client, layers[HEAD_LAYER] - 1, hair, skin, false);
		PortraitLayer hat = loadHead(client, layers[HEADWEAR_LAYER] - 1, hair, skin, false);
		draw(client.getSurface(), face, hat, x, y, size, speaking);
	}

	static void drawNpc(mudclient client, NPCDef npc, int x, int y, int size, boolean speaking) {
		int bodyId = profileAnimationId(npc);
		if (bodyId >= 0) {
			AnimationDef body = animation(bodyId);
			int colour = body.getCharColour();
			int skin = 0;
			if (colour == 1) {
				colour = npc.getHairColour();
				skin = npc.getSkinColour();
			} else if (bodyId >= 230 && Config.S_WANT_CUSTOM_SPRITES) {
				skin = npc.getSkinColour();
			} else if (colour == 2 || colour == 3) {
				colour = colour == 2 ? npc.getTopColour() : npc.getBottomColour();
				skin = npc.getSkinColour();
			}
			PortraitLayer profile = PortraitLayer.load(client, body, colour, skin, 0, true);
			draw(client.getSurface(), profile, null, x, y, size, speaking);
			return;
		}
		PortraitLayer face = loadHead(client, npc.getSprite(HEAD_LAYER), npc.getHairColour(), npc.getSkinColour(), true);
		PortraitLayer hat = loadHead(client, npc.getSprite(HEADWEAR_LAYER), npc.getHairColour(), npc.getSkinColour(), true);
		draw(client.getSurface(), face, hat, x, y, size, speaking);
	}

	private static int profileAnimationId(NPCDef npc) {
		for (int id : npc.sprites) {
			AnimationDef definition = animation(id);
			// Classic creature artwork can have a separate weapon layer with alternate facing frames.
			if (definition != null && "npc".equals(definition.category) && !definition.hasF()) return id;
		}
		return -1;
	}

	private static AnimationDef animation(int id) {
		// The registry's fallback is a human head. An absent layer contributes no art.
		return id >= 0 && id < EntityHandler.animationCount() ? EntityHandler.getAnimationDef(id) : null;
	}

	private static PortraitLayer loadHead(mudclient client, int id, int hair, int skin, boolean mirror) {
		AnimationDef definition = animation(id);
		if (definition == null || "npc".equals(definition.category)) return null;
		int colour = definition.getCharColour() == 1 ? hair : definition.getCharColour();
		return PortraitLayer.load(client, definition, colour, skin, definition.getBlueMask(), mirror);
	}

	private static void draw(GraphicsController surface, PortraitLayer face, PortraitLayer hat,
							int x, int y, int size, boolean speaking) {
		if (face == null && hat == null) return;

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
		PortraitLayer anchor = face == null ? hat : face;
		double aspect = anchor.frontWidth / (double) anchor.sprite.getSomething2();
		double height = size / Math.max((right - left) * aspect, bottom - top);
		double width = height * aspect;
		double originX = x + (size - (right - left) * width) / 2 - left * width;
		double originY = y + (size - (bottom - top) * height) / 2 - top * height;
		int tint = speaking ? 0xFFFFFFFF : 0xFF858585;
		if (face != null) face.draw(surface, originX, originY, width, height, tint);
		if (hat != null) hat.draw(surface, originX, originY, width, height, tint);
	}

	private static final class PortraitLayer {
		final Sprite sprite;
		final boolean mirror;
		final int frontWidth, colour, skin, blueMask;
		final double canvasWidth, origin, left, right, top, bottom;

		private PortraitLayer(Sprite sprite, int frontWidth, int colour, int skin, int blueMask, boolean mirror) {
			this.sprite = sprite;
			this.frontWidth = frontWidth;
			this.colour = colour;
			this.skin = skin;
			this.blueMask = blueMask;
			this.mirror = mirror;
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

		static PortraitLayer load(mudclient client, AnimationDef animation, int colour, int skin, int blueMask,
								  boolean mirror) {
			Sprite sprite = client.spriteSelect(animation, SIDE_FRAME + (mirror && animation.hasF() ? 15 : 0));
			int frontWidth = client.spriteSelect(animation, 0).getSomething1();
			if (sprite.getWidth() <= 0 || sprite.getHeight() <= 0 || frontWidth <= 0
				|| sprite.getSomething1() <= 0 || sprite.getSomething2() <= 0) return null;
			return new PortraitLayer(sprite, frontWidth, colour, skin, blueMask, mirror);
		}

		void draw(GraphicsController surface, double x, double y, double width, double height, int tint) {
			surface.drawSpriteClipping(sprite, (int) Math.round(x + origin * width), (int) Math.round(y),
				(int) Math.round(width * canvasWidth), (int) Math.round(height),
				colour, skin, blueMask, mirror, 0, 1, tint);
		}
	}
}
