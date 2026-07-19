package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.LegacyLogicalRegionAssembly;
import com.openrsc.server.model.world.coordinate.LegacyPackedRegionPartition;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldTileBounds;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Detached, read-only logical tile copy assembled from current packed sources. */
public final class LayeredRegionTileSnapshot {
	private final WorldRegionKey logicalRegionKey;
	private final LegacyLogicalRegionAssembly assembly;
	private final TileValue[][] tiles;
	private final int sourceFragmentCount;
	private final int missingSourceRegionCount;
	private final int supportedTileCount;
	private final String fingerprint;

	private LayeredRegionTileSnapshot(
		final WorldRegionKey logicalRegionKey,
		final LegacyLogicalRegionAssembly assembly,
		final TileValue[][] tiles,
		final int sourceFragmentCount,
		final int missingSourceRegionCount,
		final int supportedTileCount,
		final String fingerprint) {
		this.logicalRegionKey = logicalRegionKey;
		this.assembly = assembly;
		this.tiles = tiles;
		this.sourceFragmentCount = sourceFragmentCount;
		this.missingSourceRegionCount = missingSourceRegionCount;
		this.supportedTileCount = supportedTileCount;
		this.fingerprint = fingerprint;
	}

	static LayeredRegionTileSnapshot capture(
		final WorldRegionKey logicalRegionKey,
		final PackedTileSource source) {
		Objects.requireNonNull(logicalRegionKey, "logicalRegionKey");
		Objects.requireNonNull(source, "source");
		LegacyLogicalRegionAssembly assembly =
			LegacyLogicalRegionAssembly.fromLogicalRegionKey(logicalRegionKey);
		TileValue[][] tiles = new TileValue[WorldRegionKey.REGION_SIZE]
			[WorldRegionKey.REGION_SIZE];
		int missingSources = 0;
		int copiedTiles = 0;
		WorldTileBounds target = assembly.getTargetBounds();
		for (LegacyLogicalRegionAssembly.SourceFragment sourceFragment
			: assembly.getSourceFragments()) {
			int packedRegionX = sourceFragment.getPackedRegionX();
			int packedRegionY = sourceFragment.getPackedRegionY();
			boolean sourcePresent = source.hasPackedRegion(
				packedRegionX, packedRegionY);
			if (!sourcePresent) {
				missingSources++;
			}
			LegacyPackedRegionPartition.Fragment fragment =
				sourceFragment.getFragment();
			WorldTileBounds logicalBounds = fragment.getLogicalBounds();
			int width = logicalBounds.getMaxX() - logicalBounds.getMinX() + 1;
			int height = logicalBounds.getMaxY() - logicalBounds.getMinY() + 1;
			for (int offsetX = 0; offsetX < width; offsetX++) {
				for (int offsetY = 0; offsetY < height; offsetY++) {
					int logicalLocalX = logicalBounds.getMinX()
						- target.getMinX() + offsetX;
					int logicalLocalY = logicalBounds.getMinY()
						- target.getMinY() + offsetY;
					if (tiles[logicalLocalX][logicalLocalY] != null) {
						throw new IllegalStateException(
							"Logical snapshot fragments overlap at local tile "
								+ logicalLocalX + ',' + logicalLocalY);
					}
					TileValue packedTile = sourcePresent
						? source.readPackedTile(
							packedRegionX,
							packedRegionY,
							fragment.getMinPackedLocalX() + offsetX,
							fragment.getMinPackedLocalY() + offsetY)
						: new TileValue();
					if (packedTile == null) {
						throw new IllegalStateException(
							"Present packed source returned a null TileValue");
					}
					tiles[logicalLocalX][logicalLocalY] = packedTile.copy();
					copiedTiles++;
				}
			}
		}
		if (copiedTiles != assembly.getAssembledTileCount()) {
			throw new IllegalStateException(
				"Logical snapshot tile count differs from its assembly");
		}
		String fingerprint = fingerprint(
			logicalRegionKey, tiles, assembly.getTargetTileCount(), copiedTiles,
			assembly.getSourceFragments().size(), missingSources);
		return new LayeredRegionTileSnapshot(
			logicalRegionKey,
			assembly,
			tiles,
			assembly.getSourceFragments().size(),
			missingSources,
			copiedTiles,
			fingerprint);
	}

	private static String fingerprint(
		final WorldRegionKey key,
		final TileValue[][] tiles,
		final long targetTileCount,
		final int supportedTileCount,
		final int sourceFragmentCount,
		final int missingSourceRegionCount) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(key.getWorldSpace().getValue().getBytes(StandardCharsets.UTF_8));
			updateInt(digest, key.getLevel());
			updateInt(digest, key.getRegionX());
			updateInt(digest, key.getRegionY());
			updateInt(digest, (int) targetTileCount);
			updateInt(digest, supportedTileCount);
			updateInt(digest, sourceFragmentCount);
			updateInt(digest, missingSourceRegionCount);
			for (int localX = 0; localX < WorldRegionKey.REGION_SIZE; localX++) {
				for (int localY = 0; localY < WorldRegionKey.REGION_SIZE; localY++) {
					TileValue tile = tiles[localX][localY];
					digest.update((byte) (tile == null ? 0 : 1));
					if (tile != null) {
						updateTile(digest, tile);
					}
				}
			}
			StringBuilder hex = new StringBuilder(64);
			for (byte value : digest.digest()) {
				hex.append(String.format("%02x", value & 0xFF));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static void updateTile(
		final MessageDigest digest,
		final TileValue tile) {
		updateInt(digest, tile.traversalMask);
		updateInt(digest, tile.diagWallVal);
		updateInt(digest, tile.horizontalWallVal);
		updateInt(digest, tile.overlay);
		updateInt(digest, tile.verticalWallVal);
		updateInt(digest, tile.elevation);
		digest.update((byte) (tile.projectileAllowed ? 1 : 0));
		digest.update((byte) (tile.originalProjectileAllowed ? 1 : 0));
		digest.update((byte) (tile.isTerrainBlocked() ? 1 : 0));
		updateInt(digest, tile.getBlockingSceneryCount());
		updateInt(digest, tile.getTerrainCollisionMask());
		for (int count : tile.getDynamicCollisionCounts()) {
			updateInt(digest, count);
		}
		digest.update((byte) (tile.isTerrainOverlayProjectileBlocked() ? 1 : 0));
		updateInt(digest, tile.getTerrainWallProjectileCount());
		updateInt(digest, tile.getDynamicProjectileCount());
	}

	private static void updateInt(final MessageDigest digest, final int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	public WorldRegionKey getLogicalRegionKey() {
		return logicalRegionKey;
	}

	public LegacyLogicalRegionAssembly getAssembly() {
		return assembly;
	}

	public int getSourceFragmentCount() {
		return sourceFragmentCount;
	}

	public int getMissingSourceRegionCount() {
		return missingSourceRegionCount;
	}

	public int getSupportedTileCount() {
		return supportedTileCount;
	}

	public int getTargetTileCount() {
		return WorldRegionKey.REGION_SIZE * WorldRegionKey.REGION_SIZE;
	}

	public boolean isComplete() {
		return supportedTileCount == getTargetTileCount();
	}

	public boolean isLegacySupported(final int logicalLocalX, final int logicalLocalY) {
		validateLocal(logicalLocalX, logicalLocalY);
		return tiles[logicalLocalX][logicalLocalY] != null;
	}

	/** Returns a detached copy, or null when this logical tile is unsupported. */
	public TileValue getTileValue(final int logicalLocalX, final int logicalLocalY) {
		validateLocal(logicalLocalX, logicalLocalY);
		TileValue tile = tiles[logicalLocalX][logicalLocalY];
		return tile == null ? null : tile.copy();
	}

	public String getFingerprint() {
		return fingerprint;
	}

	private static void validateLocal(final int logicalLocalX, final int logicalLocalY) {
		if (logicalLocalX < 0 || logicalLocalX >= WorldRegionKey.REGION_SIZE
			|| logicalLocalY < 0 || logicalLocalY >= WorldRegionKey.REGION_SIZE) {
			throw new IllegalArgumentException(
				"Logical snapshot local tile must be inside 0.."
					+ (WorldRegionKey.REGION_SIZE - 1));
		}
	}

	@Override
	public String toString() {
		return "LayeredRegionTileSnapshot{logicalRegionKey=" + logicalRegionKey
			+ ", sourceFragmentCount=" + sourceFragmentCount
			+ ", missingSourceRegionCount=" + missingSourceRegionCount
			+ ", supportedTileCount=" + supportedTileCount
			+ ", fingerprint='" + fingerprint + "'}";
	}

	interface PackedTileSource {
		boolean hasPackedRegion(int packedRegionX, int packedRegionY);

		TileValue readPackedTile(
			int packedRegionX,
			int packedRegionY,
			int packedLocalX,
			int packedLocalY);
	}
}
