package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.MudClientGraphics;

import java.io.IOException;

/** North-up map view using the same coordinates for art, markers and picking. */
final class MinimapView {
	private static final int PLAYER = 0xF5FAFF;
	private static final int NPC = 0xFFD768;
	private static final int ENEMY = 0xF16A61;
	private static final int BANK = 0xF4CB72;
	private static final int OUTLINE = 0x16262D;

	private SurfaceMinimapAtlas atlas;
	private boolean triedToOpen;
	private double worldX;
	private double worldY;
	private int centerX;
	private int centerY;
	private int pixelsPerTile;
	private boolean terrainReady;

	void draw(MudClientGraphics surface, MinimapFrameLayout frame, NativeLayeredTerrainSnapshot terrain,
			  double worldX, double worldY, ORSCharacter ownPlayer, ORSCharacter[] players, int playerCount,
			  ORSCharacter[] npcs, int npcCount, int tileSize, int mouseX, int mouseY) {
		this.worldX = worldX;
		this.worldY = worldY;
		centerX = (frame.mapLeft() + frame.mapRight()) / 2;
		centerY = (frame.mapTop() + frame.mapBottom()) / 2;
		pixelsPerTile = frame.pixelsPerTile;
		if (!triedToOpen) {
			triedToOpen = true;
			try {
				atlas = SurfaceMinimapAtlas.open();
			} catch (IOException failure) {
				System.out.println("Minimap unavailable: " + failure.getMessage());
			}
		}
		surface.drawBox(frame.mapLeft(), frame.mapTop(), frame.mapRight() - frame.mapLeft(),
			frame.mapBottom() - frame.mapTop(), OUTLINE);
		terrainReady = atlas != null && atlas.matches(terrain);
		surface.setClip(frame.mapLeft(), frame.mapRight(), frame.mapBottom(), frame.mapTop());
		try {
			if (terrainReady) {
				drawTerrain(surface, frame);
			} else {
				surface.drawColoredStringCentered(centerX, "Map unavailable here", PLAYER, 0,
					frame.legendFont, frame.mapTop() + frame.labelLine);
			}
			for (int i = 0; i < npcCount; i++) {
				ORSCharacter actor = npcs[i];
				if (actor == null) continue;
				NPCDef definition = EntityHandler.getNpcDef(actor.npcId);
				boolean enemy = definition != null && definition.isAttackable() && !actor.suppressAttackOption;
				drawActor(surface, frame, ownPlayer, actor, tileSize, enemy ? ENEMY : NPC);
			}
			for (int i = 0; i < playerCount; i++) {
				ORSCharacter actor = players[i];
				if (actor != null && actor != ownPlayer) drawActor(surface, frame, ownPlayer, actor, tileSize, PLAYER);
			}
			if (terrainReady) drawBankMarker(surface, frame, mouseX, mouseY);
			drawDot(surface, centerX, centerY, frame.dotRadius + 1, 0xA9EAF7);
			drawNorth(surface, frame);
		} finally {
			surface.clearClip();
		}
		drawLegend(surface, frame);
	}

	private void drawTerrain(MudClientGraphics surface, MinimapFrameLayout frame) {
		int sector = SurfaceMinimapAtlas.SECTOR_SIZE;
		int xMin = Math.floorDiv(tileX(frame.mapRight()), sector);
		int xMax = Math.floorDiv(tileX(frame.mapLeft()), sector);
		int yMin = Math.floorDiv(tileY(frame.mapTop()), sector);
		int yMax = Math.floorDiv(tileY(frame.mapBottom()), sector);
		int span = sector * pixelsPerTile;
		for (int y = yMin; y <= yMax; y++) {
			for (int x = xMin; x <= xMax; x++) {
				Sprite sprite = atlas.sector(x, y);
				if (sprite != null) {
					surface.drawSprite(sprite, pixelX((x + 1) * sector), pixelY(y * sector), span, span, 5924);
				}
			}
		}
	}

	private void drawActor(MudClientGraphics surface, MinimapFrameLayout frame, ORSCharacter ownPlayer,
						   ORSCharacter actor, int tileSize, int color) {
		int x = pixelX(worldX + (actor.currentX - ownPlayer.currentX) / (double) tileSize);
		int y = pixelY(worldY + (actor.currentZ - ownPlayer.currentZ) / (double) tileSize);
		if (frame.containsMap(x, y)) drawDot(surface, x, y, frame.dotRadius, color);
	}

	private void drawBankMarker(MudClientGraphics surface, MinimapFrameLayout frame, int mouseX, int mouseY) {
		int x = pixelX(atlas.bankX + 0.5);
		int y = pixelY(atlas.bankY + 0.5);
		if (!frame.containsMap(x, y)) return;
		drawBank(surface, x, y, frame.bankSize);
		int radius = frame.bankSize / 2;
		if (Math.abs(x - mouseX) <= radius && Math.abs(y - mouseY) <= radius) {
			String label = "Lumbridge Bank";
			int font = frame.labelFont;
			int width = surface.stringWidth(font, label) + 10;
			int height = frame.labelLine + 4;
			int left = Math.max(frame.mapLeft(), Math.min(x - width / 2, frame.mapRight() - width));
			int top = y - radius - height - 2;
			if (top < frame.mapTop()) top = y + radius + 3;
			surface.drawBoxAlpha(left, top, width, height, OUTLINE, 245);
			surface.drawString(label, left + 5, top + frame.labelLine - 1, BANK, font);
		}
	}

	private static void drawNorth(MudClientGraphics surface, MinimapFrameLayout frame) {
		int width = frame.labelLine + 5;
		int x = frame.mapLeft() + 3;
		int y = frame.mapTop() + 3;
		surface.drawBoxAlpha(x, y, width, width, OUTLINE, 225);
		surface.drawColoredStringCentered(x + width / 2, "N", PLAYER, 0, frame.labelFont, y + frame.labelLine - 1);
	}

	private static void drawLegend(MudClientGraphics surface, MinimapFrameLayout frame) {
		int font = frame.legendFont;
		int line = surface.fontHeight(font);
		int y = frame.mapBottom() + (frame.bottom - frame.mapBottom()) / 2;
		int x = frame.mapLeft();
		x = legendItem(surface, x, y, "Player", font, line, frame.dotRadius, PLAYER);
		x = legendItem(surface, x, y, "NPC", font, line, frame.dotRadius, NPC);
		x = legendItem(surface, x, y, "Enemy", font, line, frame.dotRadius, ENEMY);
		surface.drawString("Bank", x + frame.bankSize + 4, y + line / 2 - 2, BANK, font);
		drawBank(surface, x + frame.bankSize / 2, y, frame.bankSize);
	}

	private static int legendItem(MudClientGraphics surface, int x, int y, String text, int font, int line, int radius, int color) {
		drawDot(surface, x + radius + 1, y, radius, color);
		int textX = x + radius * 2 + 6;
		surface.drawString(text, textX, y + line / 2 - 2, color, font);
		return textX + surface.stringWidth(font, text) + 8;
	}

	private static void drawDot(MudClientGraphics surface, int x, int y, int radius, int color) {
		surface.drawCircle(x, y, radius + 1, OUTLINE, 255, 0);
		surface.drawCircle(x, y, radius, color, 255, 0);
	}

	private static void drawBank(MudClientGraphics surface, int x, int y, int size) {
		int left = x - size / 2;
		int top = y - size / 2;
		surface.drawBox(left, top, size, size, OUTLINE);
		surface.drawBoxBorder(left, size, top, size, BANK);
		int inset = Math.max(2, size / 5);
		int interior = size - 2 * inset;
		surface.drawLineHoriz(left + inset, top + inset, interior, BANK);
		surface.drawLineHoriz(left + inset, top + size - inset - 1, interior, BANK);
		for (int column = 0; column < 3; column++) {
			int columnX = left + inset + column * (interior - 1) / 2;
			surface.drawLineVert(columnX, top + inset + 1, BANK, interior - 2);
		}
	}

	boolean hasTerrain() { return terrainReady; }

	int tileX(int pixel) { return (int) Math.floor(worldX - (pixel - centerX) / (double) pixelsPerTile); }
	int tileY(int pixel) { return (int) Math.floor(worldY + (pixel - centerY) / (double) pixelsPerTile); }
	private int pixelX(double x) { return centerX + (int) Math.round((worldX - x) * pixelsPerTile); }
	private int pixelY(double y) { return centerY + (int) Math.round((y - worldY) * pixelsPerTile); }
}
