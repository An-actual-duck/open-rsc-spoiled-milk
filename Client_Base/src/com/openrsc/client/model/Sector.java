package com.openrsc.client.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Sector {
	private static final int PACKED_TILE_WORDS = 4;
	private static final Tile PACKED_TILE = new Tile();
	/**
	 * The width of a sector
	 */
	private static final short WIDTH = 48;

	/**
	 * The height of a sector
	 */
	private static final short HEIGHT = 48;

	/**
	 * An array containing all the tiles within this Sector
	 */
	private Tile[] tiles;
	private boolean loaded;
	private final int[] packedTiles;

	/**
	 * Creates a new Sector full of blank tiles
	 */
	public Sector() {
		this(false);
	}

	private Sector(boolean loaded) {
		this.loaded = loaded;
		packedTiles = null;
		tiles = new Tile[Sector.WIDTH * Sector.HEIGHT];
		for (int i = 0; i < tiles.length; i++) {
			tiles[i] = new Tile();
		}
	}

	private Sector(boolean loaded, int[] packedTiles) {
		this.loaded = loaded;
		this.packedTiles = packedTiles;
		tiles = new Tile[WIDTH * HEIGHT];
		Arrays.fill(tiles, PACKED_TILE);
	}

	/** Keep the checked gameplay worker tile plane in its primitive representation. */
	public static Sector fromSurfaceWords(boolean loaded, int[] words, int start) {
		int count = WIDTH * HEIGHT * PACKED_TILE_WORDS;
		if (start < 0 || start > words.length - count) throw new IllegalArgumentException("Sector word bounds");
		return new Sector(loaded, Arrays.copyOfRange(words, start, start + count));
	}

	public boolean hasPackedTiles() { return packedTiles != null; }

	/** Creates an empty page that is authoritative rather than archive-missing. */
	public static Sector blankLoaded() {
		return new Sector(true);
	}

	/**
	 * Create a new Sector from raw data packed into the given ByteBuffer
	 */
	public static Sector unpack(ByteBuffer in) throws IOException {
		int length = Sector.WIDTH * Sector.HEIGHT;
		if (in.remaining() < (10 * length)) {
			throw new IOException("Provided buffer too short");
		}
		Sector sector = new Sector(true);

		for (int i = 0; i < length; i++) {
			sector.setTile(i, Tile.unpack(in));
		}

		return sector;
	}

	public Sector copy() {
		if (packedTiles != null) {
			Sector sector = new Sector(loaded, packedTiles.clone());
			for (int i = 0; i < tiles.length; i++) {
				Tile tile = tiles[i];
				if (tile != PACKED_TILE) sector.tiles[i] = tile.copy();
			}
			return sector;
		}
		Sector sector = new Sector(loaded);
		for (int i = 0; i < tiles.length; i++) {
			sector.tiles[i] = tiles[i].copy();
		}
		return sector;
	}

	public boolean isLoaded() {
		return loaded;
	}

	/**
	 * Sets the the Tile at the given coords
	 */
	public void setTile(int x, int y, Tile t) {
		setTile(x * Sector.WIDTH + y, t);
	}

	/**
	 * Sets the Tile at the given index
	 */
	private void setTile(int i, Tile t) {
		tiles[i] = t;
	}

	/**
	 * Gets the Tile at the given coords
	 */
	public Tile getTile(int x, int y) {
		return getTile(x * Sector.WIDTH + y);
	}

	/**
	 * Gets the Tile at the given index
	 */
	public Tile getTile(int i) {
		Tile tile = tiles[i];
		return tile == PACKED_TILE ? materializeTile(i) : tile;
	}

	private synchronized Tile materializeTile(int i) {
		Tile tile = tiles[i];
		if (tile != PACKED_TILE) return tile;
		int at = i * PACKED_TILE_WORDS;
		int texture = packedTiles[at + 2];
		tile = new Tile();
		tile.groundElevation = packedTiles[at];
		tile.diagonalWalls = packedTiles[at + 1];
		tile.groundTexture = (byte)texture;
		tile.groundOverlay = (byte)(texture >>> 8);
		tile.roofTexture = (byte)(texture >>> 16);
		tile.horizontalWall = (byte)(texture >>> 24);
		tile.verticalWall = (byte)packedTiles[at + 3];
		return tiles[i] = tile;
	}

	public int groundElevation(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? packedTiles[index * PACKED_TILE_WORDS] : tile.groundElevation;
	}

	public int diagonalWalls(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? packedTiles[index * PACKED_TILE_WORDS + 1] : tile.diagonalWalls;
	}

	public byte groundTexture(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? (byte)packedTiles[index * PACKED_TILE_WORDS + 2] : tile.groundTexture;
	}

	public byte groundOverlay(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? (byte)(packedTiles[index * PACKED_TILE_WORDS + 2] >>> 8) : tile.groundOverlay;
	}

	public byte roofTexture(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? (byte)(packedTiles[index * PACKED_TILE_WORDS + 2] >>> 16) : tile.roofTexture;
	}

	public byte horizontalWall(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? (byte)(packedTiles[index * PACKED_TILE_WORDS + 2] >>> 24) : tile.horizontalWall;
	}

	public byte verticalWall(int x, int z) {
		int index = x * WIDTH + z;
		Tile tile = tiles[index];
		return tile == PACKED_TILE ? (byte)packedTiles[index * PACKED_TILE_WORDS + 3] : tile.verticalWall;
	}

	public boolean isEditorPaintedOverlay(int x, int z) {
		Tile tile = tiles[x * WIDTH + z];
		return tile != PACKED_TILE && tile.editorPaintedOverlay;
	}

	/**
	 * Writes the Sector raw data into a ByteBuffer
	 */
	public ByteBuffer pack() throws IOException {
		ByteBuffer out = ByteBuffer.allocate(10 * tiles.length);

		for (int i = 0; i < tiles.length; i++) {
			out.put(getTile(i).pack());
		}

		out.flip();
		return out;
	}
}
