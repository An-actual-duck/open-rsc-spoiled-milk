package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Checked logical-key coverage of one legacy packed 48-tile region cell. */
public final class LegacyPackedRegionCoverage {
	private final int packedRegionX;
	private final int packedRegionY;
	private final int nominalMinTileX;
	private final int nominalMinTileY;
	private final int nominalMaxTileX;
	private final int nominalMaxTileY;
	private final int legacyMinTileX;
	private final int legacyMinTileY;
	private final int legacyMaxTileX;
	private final int legacyMaxTileY;
	private final List<WorldRegionKey> coveredKeys;

	private LegacyPackedRegionCoverage(
		final int packedRegionX,
		final int packedRegionY,
		final int nominalMinTileX,
		final int nominalMinTileY,
		final int nominalMaxTileX,
		final int nominalMaxTileY,
		final int legacyMinTileX,
		final int legacyMinTileY,
		final int legacyMaxTileX,
		final int legacyMaxTileY,
		final List<WorldRegionKey> coveredKeys) {
		this.packedRegionX = packedRegionX;
		this.packedRegionY = packedRegionY;
		this.nominalMinTileX = nominalMinTileX;
		this.nominalMinTileY = nominalMinTileY;
		this.nominalMaxTileX = nominalMaxTileX;
		this.nominalMaxTileY = nominalMaxTileY;
		this.legacyMinTileX = legacyMinTileX;
		this.legacyMinTileY = legacyMinTileY;
		this.legacyMaxTileX = legacyMaxTileX;
		this.legacyMaxTileY = legacyMaxTileY;
		this.coveredKeys = Collections.unmodifiableList(coveredKeys);
	}

	public static LegacyPackedRegionCoverage fromPackedRegionCoordinates(
		final int packedRegionX,
		final int packedRegionY) {
		if (packedRegionX < 0 || packedRegionY < 0) {
			throw new IllegalArgumentException("Packed region coordinates must not be negative");
		}
		int nominalMinX = Math.multiplyExact(packedRegionX, WorldRegionKey.REGION_SIZE);
		int nominalMinY = Math.multiplyExact(packedRegionY, WorldRegionKey.REGION_SIZE);
		int nominalMaxX = Math.addExact(nominalMinX, WorldRegionKey.REGION_SIZE - 1);
		int nominalMaxY = Math.addExact(nominalMinY, WorldRegionKey.REGION_SIZE - 1);

		int legacyMinX = nominalMinX;
		int legacyMinY = nominalMinY;
		int legacyMaxX = Math.min(nominalMaxX, LegacyPackedPointAdapter.MAX_LEGACY_X);
		int legacyMaxY = Math.min(nominalMaxY, LegacyPackedPointAdapter.MAX_PACKED_Y);
		List<WorldRegionKey> keys = new ArrayList<WorldRegionKey>();
		if (legacyMinX <= legacyMaxX && legacyMinY <= legacyMaxY) {
			Set<WorldRegionKey> unique = new LinkedHashSet<WorldRegionKey>();
			for (int packedY = legacyMinY; packedY <= legacyMaxY; packedY++) {
				unique.add(WorldRegionKey.fromLegacyPoint(Point.location(legacyMinX, packedY)));
			}
			keys.addAll(unique);
		}

		return new LegacyPackedRegionCoverage(
			packedRegionX,
			packedRegionY,
			nominalMinX,
			nominalMinY,
			nominalMaxX,
			nominalMaxY,
			legacyMinX,
			legacyMinY,
			legacyMaxX,
			legacyMaxY,
			keys);
	}

	public int getPackedRegionX() {
		return packedRegionX;
	}

	public int getPackedRegionY() {
		return packedRegionY;
	}

	public int getNominalMinTileX() {
		return nominalMinTileX;
	}

	public int getNominalMinTileY() {
		return nominalMinTileY;
	}

	public int getNominalMaxTileX() {
		return nominalMaxTileX;
	}

	public int getNominalMaxTileY() {
		return nominalMaxTileY;
	}

	public boolean hasLegacyTiles() {
		return !coveredKeys.isEmpty();
	}

	public boolean isFullyInsideLegacyDomain() {
		return hasLegacyTiles()
			&& nominalMinTileX == legacyMinTileX
			&& nominalMinTileY == legacyMinTileY
			&& nominalMaxTileX == legacyMaxTileX
			&& nominalMaxTileY == legacyMaxTileY;
	}

	public long getLegacyTileCount() {
		if (!hasLegacyTiles()) {
			return 0L;
		}
		long width = (long) legacyMaxTileX - legacyMinTileX + 1L;
		long height = (long) legacyMaxTileY - legacyMinTileY + 1L;
		return Math.multiplyExact(width, height);
	}

	public List<WorldRegionKey> getCoveredKeys() {
		return coveredKeys;
	}

	public boolean contains(final WorldRegionKey key) {
		return coveredKeys.contains(Objects.requireNonNull(key, "key"));
	}

	public boolean spansLevels() {
		if (coveredKeys.size() < 2) {
			return false;
		}
		int firstLevel = coveredKeys.get(0).getLevel();
		for (int index = 1; index < coveredKeys.size(); index++) {
			if (coveredKeys.get(index).getLevel() != firstLevel) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "LegacyPackedRegionCoverage{packedRegionX=" + packedRegionX
			+ ", packedRegionY=" + packedRegionY + ", nominalTiles=("
			+ nominalMinTileX + ',' + nominalMinTileY + ".." + nominalMaxTileX
			+ ',' + nominalMaxTileY + "), legacyTileCount=" + getLegacyTileCount()
			+ ", coveredKeys=" + coveredKeys + "}";
	}
}
