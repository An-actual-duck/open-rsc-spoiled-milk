package orsc.graphics.two;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sprite;

import orsc.Config;
import orsc.mudclient;
import orsc.util.GenUtil;

public final class MudClientGraphics extends GraphicsController {
	private static final int ALTAR_GLYPH_ALPHA = 160;
	private static final int ALTAR_ORB_ALPHA = 192;
	public mudclient mudClientRef;

	public MudClientGraphics(int var1, int var2, int var3) {
		super(var1, var2, var3);
	}

	@Override
	public final void drawEntity(int index, int x, int y, int width, int height, int overlayMovement, int topPixelSkew) {
		try {
			if (index >= mudclient.spriteWorldGlyphBase && index < mudclient.spriteWorldGlyphBase + mudclient.ALTAR_VISUAL_COUNT) {
				Sprite glyph = this.mudClientRef.getWorldGlyphSprite(index - mudclient.spriteWorldGlyphBase);
				if (glyph != null) {
					super.drawSprite(glyph, x, y, width, height, 5924, ALTAR_GLYPH_ALPHA);
				}
			} else if (index >= mudclient.spriteWorldOrbBase && index < mudclient.spriteWorldOrbBase + mudclient.ALTAR_VISUAL_COUNT) {
				Sprite orb = this.mudClientRef.getWorldOrbSprite(index - mudclient.spriteWorldOrbBase);
				if (orb != null) {
					super.drawSprite(orb, x, y, width, height, 5924, ALTAR_ORB_ALPHA);
				}
			} else if (index >= mudclient.spriteCombatEffectBase
				&& index < mudclient.spriteCombatEffectBase + (mudclient.COMBAT_EFFECT_COUNT * mudclient.COMBAT_EFFECT_FRAME_SLOTS)) {
				Sprite effect = this.mudClientRef.getCombatEffectSpriteForSceneIndex(index);
				if (effect != null) {
					if (!this.mudClientRef.queueCombatEffectOverlay(index, x, y, width, height)) {
						super.drawSprite(effect, x, y, width, height, 5924, 224);
					}
				}
			} else if (index >= mudclient.spriteProjectileEffectBase
				&& index < mudclient.spriteProjectileEffectBase + (mudclient.CUSTOM_PROJECTILE_COUNT * mudclient.PROJECTILE_EFFECT_FRAME_SLOTS)) {
				Sprite projectile = this.mudClientRef.getProjectileEffectSpriteForSceneIndex(index);
				if (projectile != null) {
					if (!this.mudClientRef.queueProjectileEffectOverlay(index, x, y, width, height)) {
						super.drawSprite(projectile, x, y, width, height, 5924, 224);
					}
				}
			} else if (Config.S_WANT_BANK_NOTES && index == -1) {
				this.mudClientRef.drawItemAt(-1, x, y, width, height, topPixelSkew);
			}
			else if (index < 50000) {
				if (index < 40000) {
					if (index >= 20000) {
						this.mudClientRef.drawNPC(index - 20000, x, y, width, height, topPixelSkew, 105,
							overlayMovement);
					} else if (index < 5000) {
						Sprite projectile = spriteSelect(EntityHandler.projectiles.get(index-mudclient.spriteProjectile));
						super.drawSprite(projectile, x, y, width, height, 5924);
					} else {
						this.mudClientRef.drawPlayer(index - 5000, x, y, width, height, topPixelSkew, 20,
							overlayMovement);
					}
				} else {
					this.mudClientRef.drawItemAt(index - 40000, x, y, width, height, topPixelSkew);
				}
			} else {
				this.mudClientRef.drawTeleportBubble(index - 50000, x, y, width, height, topPixelSkew, 2);
			}
		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10, "ba.B(" + overlayMovement + ',' + index + ',' + height + ',' + x + ','
				+ y + ',' + width + ',' + 29 + ',' + topPixelSkew + ')');
		}
	}
}
