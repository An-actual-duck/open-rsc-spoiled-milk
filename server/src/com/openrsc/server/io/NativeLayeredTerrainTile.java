package com.openrsc.server.io;

import java.util.Objects;

/** Immutable static terrain values decoded without registering a runtime tile. */
public final class NativeLayeredTerrainTile {
	private final int elevation;
	private final int texture;
	private final int overlay;
	private final int roof;
	private final int verticalWall;
	private final int horizontalWall;
	private final int diagonalWall;

	/** The one canonical empty-world material shared by load and authoring. */
	public static NativeLayeredTerrainTile worldBuilderVoid() {
		return new NativeLayeredTerrainTile(0, 1, 8, 0, 0, 0, 0);
	}

	public boolean isWorldBuilderVoid() {
		return elevation == 0 && texture == 1 && overlay == 8
			&& roof == 0 && verticalWall == 0 && horizontalWall == 0
			&& diagonalWall == 0;
	}

	public NativeLayeredTerrainTile(
		int elevation,
		int texture,
		int overlay,
		int roof,
		int verticalWall,
		int horizontalWall,
		int diagonalWall) {
		this.elevation = unsignedByte(elevation, "elevation");
		this.texture = unsignedByte(texture, "texture");
		this.overlay = unsignedByte(overlay, "overlay");
		this.roof = unsignedByte(roof, "roof");
		this.verticalWall = unsignedByte(verticalWall, "verticalWall");
		this.horizontalWall = unsignedByte(horizontalWall, "horizontalWall");
		this.diagonalWall = diagonalWall;
	}

	private static int unsignedByte(int value, String label) {
		if (value < 0 || value > 255) {
			throw new IllegalArgumentException(label + " must be an unsigned byte");
		}
		return value;
	}

	Tile copyToLegacyTile() {
		Tile tile = new Tile();
		tile.groundElevation = (byte) elevation;
		tile.groundTexture = (byte) texture;
		tile.groundOverlay = (byte) overlay;
		tile.roofTexture = (byte) roof;
		tile.verticalWall = (byte) verticalWall;
		tile.horizontalWall = (byte) horizontalWall;
		tile.diagonalWalls = diagonalWall;
		return tile;
	}

	public int getElevation() {
		return elevation;
	}

	public int getTexture() {
		return texture;
	}

	public int getOverlay() {
		return overlay;
	}

	public int getRoof() {
		return roof;
	}

	public int getVerticalWall() {
		return verticalWall;
	}

	public int getHorizontalWall() {
		return horizontalWall;
	}

	public int getDiagonalWall() {
		return diagonalWall;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NativeLayeredTerrainTile)) {
			return false;
		}
		NativeLayeredTerrainTile tile = (NativeLayeredTerrainTile) other;
		return elevation == tile.elevation
			&& texture == tile.texture
			&& overlay == tile.overlay
			&& roof == tile.roof
			&& verticalWall == tile.verticalWall
			&& horizontalWall == tile.horizontalWall
			&& diagonalWall == tile.diagonalWall;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			elevation,
			texture,
			overlay,
			roof,
			verticalWall,
			horizontalWall,
			diagonalWall);
	}
}
