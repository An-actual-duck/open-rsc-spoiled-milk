package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.MudClientGraphics;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** North-up map view using the same coordinates for art, markers and picking. */
final class MinimapView {
	private static final int PLAYER = 0xF5FAFF;
	private static final int NPC = 0xFFD768;
	private static final int ENEMY = 0xF16A61;
	private static final int MARKER_BACKGROUND = 0xFFFBEF;
	private static final int OUTLINE = 0x16262D;
	private static final Sprite MARKER_DISC = makeMarkerDisc();

	private final Map<Integer, MinimapAtlas> atlases = new LinkedHashMap<Integer, MinimapAtlas>(4, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, MinimapAtlas> eldest) {
			return size() > 3;
		}
	};
	private MinimapAtlas atlas;
	private double worldX;
	private double worldY;
	private int centerX;
	private int centerY;
	private int pixelsPerTile;
	private boolean terrainReady;
	private MinimapAtlas.Landmark hoveredLandmark;
	private int hoveredX;
	private int hoveredY;

	void draw(MudClientGraphics surface, MinimapFrameLayout frame, NativeLayeredTerrainSnapshot terrain,
			  double worldX, double worldY, ORSCharacter ownPlayer, ORSCharacter[] players, int playerCount,
			  ORSCharacter[] npcs, int npcCount, int tileSize, int mouseX, int mouseY) {
		this.worldX = worldX;
		this.worldY = worldY;
		centerX = (frame.mapLeft() + frame.mapRight()) / 2;
		centerY = (frame.mapTop() + frame.mapBottom()) / 2;
		pixelsPerTile = frame.pixelsPerTile;
		atlas = atlasFor(terrain);
		surface.drawBox(frame.mapLeft(), frame.mapTop(), frame.mapRight() - frame.mapLeft(),
			frame.mapBottom() - frame.mapTop(), OUTLINE);
		terrainReady = atlas != null && atlas.matches(terrain);
		hoveredLandmark = null;
		surface.setClip(frame.mapLeft(), frame.mapRight(), frame.mapBottom(), frame.mapTop());
		try {
			if (terrainReady) {
				drawTerrain(surface, frame);
				drawLandmarkConnectors(surface, frame);
			} else {
				surface.drawColoredStringCentered(centerX, "Map unavailable here", PLAYER, 0,
					frame.labelFont, frame.mapTop() + frame.labelLine);
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
			if (terrainReady) drawLandmarks(surface, frame, mouseX, mouseY);
			drawDot(surface, centerX, centerY, frame.dotRadius + 1, 0xA9EAF7);
			drawNorth(surface, frame);
			if (hoveredLandmark != null) drawLandmarkTooltip(surface, frame);
		} finally {
			surface.clearClip();
		}
	}

	private MinimapAtlas atlasFor(NativeLayeredTerrainSnapshot terrain) {
		if (terrain == null || !"global".equals(terrain.getWorldSpace())) return null;
		int level = terrain.getLevel();
		if (atlases.containsKey(level)) return atlases.get(level);
		MinimapAtlas loaded = null;
		try {
			loaded = MinimapAtlas.open(level);
		} catch (IOException failure) {
			System.out.println("Minimap unavailable: " + failure.getMessage());
		}
		atlases.put(level, loaded);
		return loaded;
	}

	private void drawTerrain(MudClientGraphics surface, MinimapFrameLayout frame) {
		int sector = MinimapAtlas.SECTOR_SIZE;
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

	private void drawLandmarkConnectors(MudClientGraphics surface, MinimapFrameLayout frame) {
		for (MinimapAtlas.Landmark point : atlas.landmarks) {
			if (point.offsetX == 0 && point.offsetY == 0) continue;
			int x = pixelX(point.x + point.offsetX + 0.5);
			int y = pixelY(point.y + point.offsetY + 0.5);
			if (!frame.containsMap(x, y)) continue;
			int anchorX = pixelX(point.x + 0.5);
			int anchorY = pixelY(point.y + 0.5);
			if (x != anchorX) {
				surface.drawLineHoriz(Math.min(anchorX, x), anchorY, Math.abs(anchorX - x) + 1, OUTLINE);
			}
			if (y != anchorY) {
				surface.drawLineVert(x, Math.min(anchorY, y), OUTLINE, Math.abs(anchorY - y) + 1);
			}
		}
	}

	private void drawLandmarks(MudClientGraphics surface, MinimapFrameLayout frame, int mouseX, int mouseY) {
		int size = frame.markerSize;
		int radius = size / 2;
		boolean pointerOnMap = frame.containsMap(mouseX, mouseY);
		for (MinimapAtlas.Landmark point : atlas.landmarks) {
			int x = pixelX(point.x + point.offsetX + 0.5);
			int y = pixelY(point.y + point.offsetY + 0.5);
			if (!frame.containsMap(x, y)) continue;
			int left = x - radius;
			int top = y - radius;
			drawLocationMarker(surface, left, top, size, point.icon.symbol);
			int dx = mouseX - x;
			int dy = mouseY - y;
			if (pointerOnMap && dx * dx + dy * dy <= radius * radius) {
				hoveredLandmark = point;
				hoveredX = x;
				hoveredY = y;
			}
		}
	}

	private void drawLandmarkTooltip(MudClientGraphics surface, MinimapFrameLayout frame) {
		int radius = frame.markerSize / 2;
		String label = hoveredLandmark.label;
		int font = frame.labelFont;
		int width = surface.stringWidth(font, label) + 10;
		while (font > 0 && width > frame.mapRight() - frame.mapLeft()) {
			width = surface.stringWidth(--font, label) + 10;
		}
		int height = surface.fontHeight(font) + 4;
		int left = Math.max(frame.mapLeft(), Math.min(hoveredX - width / 2, frame.mapRight() - width));
		int top = hoveredY - radius - height - 2;
		if (top < frame.mapTop()) top = hoveredY + radius + 3;
		surface.drawBoxAlpha(left, top, width, height, OUTLINE, 245);
		surface.drawString(label, left + 5, top + height - 5, MARKER_BACKGROUND, font);
	}

	private static void drawNorth(MudClientGraphics surface, MinimapFrameLayout frame) {
		int width = frame.labelLine + 5;
		int x = frame.mapLeft() + 3;
		int y = frame.mapTop() + 3;
		surface.drawBoxAlpha(x, y, width, width, OUTLINE, 225);
		surface.drawColoredStringCentered(x + width / 2, "N", PLAYER, 0, frame.labelFont, y + frame.labelLine - 1);
	}

	private static void drawDot(MudClientGraphics surface, int x, int y, int radius, int color) {
		surface.drawCircle(x, y, radius + 1, OUTLINE, 255, 0);
		surface.drawCircle(x, y, radius, color, 255, 0);
	}

	private static void drawLocationMarker(MudClientGraphics surface, int left, int top, int size, Sprite symbol) {
		surface.drawSprite(MARKER_DISC, left, top, size, size, 5924);
		int available = size * 7 / 10;
		int dimension = Math.max(symbol.getWidth(), symbol.getHeight());
		int width = (available * symbol.getWidth() + dimension / 2) / dimension;
		int height = (available * symbol.getHeight() + dimension / 2) / dimension;
		surface.drawSprite(symbol, left + (size - width) / 2, top + (size - height) / 2, width, height, 5924);
	}

	private static Sprite makeMarkerDisc() {
		int size = 64;
		int fill = size - 6;
		int[] pixels = new int[size * size];
		for (int y = 0; y < size; y++) {
			int dy = 2 * y + 1 - size;
			for (int x = 0; x < size; x++) {
				int dx = 2 * x + 1 - size;
				int distanceSquared = dx * dx + dy * dy;
				if (distanceSquared <= size * size) {
					pixels[y * size + x] = distanceSquared <= fill * fill ? MARKER_BACKGROUND : OUTLINE;
				}
			}
		}
		return new Sprite(pixels, size, size);
	}

	boolean hasTerrain() { return terrainReady; }
	int targetTileX(int pixel) { return hoveredLandmark == null ? tileX(pixel) : hoveredLandmark.x; }
	int targetTileY(int pixel) { return hoveredLandmark == null ? tileY(pixel) : hoveredLandmark.y; }

	int tileX(int pixel) { return (int) Math.floor(worldX - (pixel - centerX) / (double) pixelsPerTile); }
	int tileY(int pixel) { return (int) Math.floor(worldY + (pixel - centerY) / (double) pixelsPerTile); }
	private int pixelX(double x) { return centerX + (int) Math.round((worldX - x) * pixelsPerTile); }
	private int pixelY(double y) { return centerY + (int) Math.round((y - worldY) * pixelsPerTile); }
}
