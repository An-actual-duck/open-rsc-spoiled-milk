package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only comparison of one packed visibility window and logical coverage. */
public final class LegacyPackedVisibilityCoverageComparison {
	private final WorldRegionWindow logicalWindow;
	private final int minPackedRegionX;
	private final int minPackedRegionY;
	private final int maxPackedRegionX;
	private final int maxPackedRegionY;
	private final long packedCellCount;
	private final int unsupportedPackedCellCount;
	private final List<WorldRegionKey> expectedLogicalKeys;
	private final List<WorldRegionKey> packedCoverageKeys;
	private final List<WorldRegionKey> missingLogicalKeys;
	private final List<WorldRegionKey> extraPackedCoverageKeys;

	private LegacyPackedVisibilityCoverageComparison(
		final WorldRegionWindow logicalWindow,
		final int minPackedRegionX,
		final int minPackedRegionY,
		final int maxPackedRegionX,
		final int maxPackedRegionY,
		final long packedCellCount,
		final int unsupportedPackedCellCount,
		final List<WorldRegionKey> expectedLogicalKeys,
		final List<WorldRegionKey> packedCoverageKeys,
		final List<WorldRegionKey> missingLogicalKeys,
		final List<WorldRegionKey> extraPackedCoverageKeys) {
		this.logicalWindow = logicalWindow;
		this.minPackedRegionX = minPackedRegionX;
		this.minPackedRegionY = minPackedRegionY;
		this.maxPackedRegionX = maxPackedRegionX;
		this.maxPackedRegionY = maxPackedRegionY;
		this.packedCellCount = packedCellCount;
		this.unsupportedPackedCellCount = unsupportedPackedCellCount;
		this.expectedLogicalKeys = Collections.unmodifiableList(expectedLogicalKeys);
		this.packedCoverageKeys = Collections.unmodifiableList(packedCoverageKeys);
		this.missingLogicalKeys = Collections.unmodifiableList(missingLogicalKeys);
		this.extraPackedCoverageKeys = Collections.unmodifiableList(extraPackedCoverageKeys);
	}

	public static LegacyPackedVisibilityCoverageComparison compare(
		final Point center,
		final int gridDistance,
		final int maximumPackedCells,
		final int maximumLogicalKeys) {
		Objects.requireNonNull(center, "center");
		if (gridDistance < 0) {
			throw new IllegalArgumentException("Grid distance must not be negative");
		}
		if (maximumPackedCells < 1 || maximumLogicalKeys < 1) {
			throw new IllegalArgumentException("Comparison allocation budgets must be positive");
		}

		WorldLocation location = LegacyPackedPointAdapter.fromLegacyPoint(center);
		int tileRadius = Math.multiplyExact(gridDistance, 8);
		WorldRegionWindow logicalWindow = WorldRegionWindow.around(location, tileRadius);
		List<WorldRegionKey> expected = new ArrayList<WorldRegionKey>(
			WorldRegionInterestDelta.materializeKeys(logicalWindow, maximumLogicalKeys));

		int minPackedX = Math.floorDiv(
			Math.subtractExact(center.getX(), tileRadius), WorldRegionKey.REGION_SIZE);
		int minPackedY = Math.floorDiv(
			Math.subtractExact(center.getY(), tileRadius), WorldRegionKey.REGION_SIZE);
		int maxPackedX = Math.floorDiv(
			Math.addExact(center.getX(), tileRadius), WorldRegionKey.REGION_SIZE);
		int maxPackedY = Math.floorDiv(
			Math.addExact(center.getY(), tileRadius), WorldRegionKey.REGION_SIZE);
		long packedWidth = (long) maxPackedX - minPackedX + 1L;
		long packedHeight = (long) maxPackedY - minPackedY + 1L;
		long packedCells = Math.multiplyExact(packedWidth, packedHeight);
		if (packedCells > maximumPackedCells) {
			throw new IllegalArgumentException(
				"Packed visibility window requires " + packedCells
					+ " cells, exceeding the caller budget of " + maximumPackedCells);
		}

		Set<WorldRegionKey> coverage = new LinkedHashSet<WorldRegionKey>();
		int unsupportedCells = 0;
		for (long packedX = minPackedX; packedX <= (long) maxPackedX; packedX++) {
			for (long packedY = minPackedY; packedY <= (long) maxPackedY; packedY++) {
				if (packedX < 0L || packedY < 0L) {
					unsupportedCells++;
					continue;
				}
				LegacyPackedRegionCoverage cell =
					LegacyPackedRegionCoverage.fromPackedRegionCoordinates(
						(int) packedX, (int) packedY);
				if (!cell.hasLegacyTiles()) {
					unsupportedCells++;
					continue;
				}
				for (WorldRegionKey key : cell.getCoveredKeys()) {
					if (!coverage.contains(key) && coverage.size() >= maximumLogicalKeys) {
						throw new IllegalArgumentException(
							"Packed coverage exceeds the caller key budget of "
								+ maximumLogicalKeys);
					}
					coverage.add(key);
				}
			}
		}

		List<WorldRegionKey> packedCoverage = new ArrayList<WorldRegionKey>(coverage);
		Set<WorldRegionKey> expectedSet = new LinkedHashSet<WorldRegionKey>(expected);
		List<WorldRegionKey> missing = new ArrayList<WorldRegionKey>();
		for (WorldRegionKey key : expected) {
			if (!coverage.contains(key)) {
				missing.add(key);
			}
		}
		List<WorldRegionKey> extra = new ArrayList<WorldRegionKey>();
		for (WorldRegionKey key : packedCoverage) {
			if (!expectedSet.contains(key)) {
				extra.add(key);
			}
		}

		return new LegacyPackedVisibilityCoverageComparison(
			logicalWindow,
			minPackedX,
			minPackedY,
			maxPackedX,
			maxPackedY,
			packedCells,
			unsupportedCells,
			expected,
			packedCoverage,
			missing,
			extra);
	}

	public WorldRegionWindow getLogicalWindow() {
		return logicalWindow;
	}

	public int getMinPackedRegionX() {
		return minPackedRegionX;
	}

	public int getMinPackedRegionY() {
		return minPackedRegionY;
	}

	public int getMaxPackedRegionX() {
		return maxPackedRegionX;
	}

	public int getMaxPackedRegionY() {
		return maxPackedRegionY;
	}

	public long getPackedCellCount() {
		return packedCellCount;
	}

	public int getUnsupportedPackedCellCount() {
		return unsupportedPackedCellCount;
	}

	public List<WorldRegionKey> getExpectedLogicalKeys() {
		return expectedLogicalKeys;
	}

	public List<WorldRegionKey> getPackedCoverageKeys() {
		return packedCoverageKeys;
	}

	public List<WorldRegionKey> getMissingLogicalKeys() {
		return missingLogicalKeys;
	}

	public List<WorldRegionKey> getExtraPackedCoverageKeys() {
		return extraPackedCoverageKeys;
	}

	public boolean isExactCoverage() {
		return missingLogicalKeys.isEmpty() && extraPackedCoverageKeys.isEmpty();
	}

	@Override
	public String toString() {
		return "LegacyPackedVisibilityCoverageComparison{packedCells=" + packedCellCount
			+ ", unsupportedPackedCells=" + unsupportedPackedCellCount
			+ ", expectedKeys=" + expectedLogicalKeys.size()
			+ ", packedCoverageKeys=" + packedCoverageKeys.size()
			+ ", missingKeys=" + missingLogicalKeys.size()
			+ ", extraKeys=" + extraPackedCoverageKeys.size() + "}";
	}
}
