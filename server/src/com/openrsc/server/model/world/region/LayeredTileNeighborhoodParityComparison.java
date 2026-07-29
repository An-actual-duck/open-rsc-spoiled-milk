package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Read-only parity result for the 3x3 tile neighborhood around one location. */
public final class LayeredTileNeighborhoodParityComparison {
	public static final int RADIUS = 1;
	public static final int CELL_COUNT = 9;

	private final WorldLocation center;
	private final List<LayeredTileStateParityComparison> cells;
	private final int legacyRepresentableCount;
	private final int packedSourcePresentCount;
	private final int missingPackedSourceCount;
	private final int comparableCount;
	private final int exactCount;

	private LayeredTileNeighborhoodParityComparison(
		final WorldLocation center,
		final List<LayeredTileStateParityComparison> cells,
		final int legacyRepresentableCount,
		final int packedSourcePresentCount,
		final int missingPackedSourceCount,
		final int comparableCount,
		final int exactCount) {
		this.center = center;
		this.cells = Collections.unmodifiableList(
			new ArrayList<LayeredTileStateParityComparison>(cells));
		this.legacyRepresentableCount = legacyRepresentableCount;
		this.packedSourcePresentCount = packedSourcePresentCount;
		this.missingPackedSourceCount = missingPackedSourceCount;
		this.comparableCount = comparableCount;
		this.exactCount = exactCount;
	}

	static LayeredTileNeighborhoodParityComparison of(
		final WorldLocation center,
		final List<LayeredTileStateParityComparison> cells) {
		Objects.requireNonNull(center, "center");
		Objects.requireNonNull(cells, "cells");
		if (cells.size() != CELL_COUNT) {
			throw new IllegalArgumentException(
				"Layered tile neighborhood must contain exactly " + CELL_COUNT + " cells");
		}
		int representable = 0;
		int sourcePresent = 0;
		int missingSource = 0;
		int comparable = 0;
		int exact = 0;
		int index = 0;
		for (int offsetY = -RADIUS; offsetY <= RADIUS; offsetY++) {
			for (int offsetX = -RADIUS; offsetX <= RADIUS; offsetX++) {
				LayeredTileStateParityComparison cell = Objects.requireNonNull(
					cells.get(index), "cells[" + index + "]");
				WorldLocation expected = offset(center, offsetX, offsetY);
				if (!expected.equals(cell.getLogicalLocation())) {
					throw new IllegalArgumentException(
						"Layered tile neighborhood cell order/location mismatch at index "
							+ index);
				}
				if (cell.isLegacyRepresentable()) {
					representable++;
				}
				if (cell.isPackedSourcePresent()) {
					sourcePresent++;
				}
				if (cell.isMissingPackedSource()) {
					missingSource++;
				}
				if (cell.isComparable()) {
					comparable++;
				}
				if (cell.isExact()) {
					exact++;
				}
				index++;
			}
		}
		return new LayeredTileNeighborhoodParityComparison(
			center,
			cells,
			representable,
			sourcePresent,
			missingSource,
			comparable,
			exact);
	}

	static WorldLocation offset(
		final WorldLocation center,
		final int offsetX,
		final int offsetY) {
		Objects.requireNonNull(center, "center");
		if (offsetX < -RADIUS || offsetX > RADIUS
			|| offsetY < -RADIUS || offsetY > RADIUS) {
			throw new IllegalArgumentException(
				"Neighborhood offsets must be in -" + RADIUS + ".." + RADIUS);
		}
		WorldCoordinate coordinate = center.getCoordinate();
		return new WorldLocation(
			center.getWorldSpace(),
			new WorldCoordinate(
				Math.addExact(coordinate.getX(), offsetX),
				Math.addExact(coordinate.getY(), offsetY),
				coordinate.getLevel()));
	}

	public WorldLocation getCenter() {
		return center;
	}

	/** Row-major order from northwest offset (-1,-1) to southeast (+1,+1). */
	public List<LayeredTileStateParityComparison> getCells() {
		return cells;
	}

	public LayeredTileStateParityComparison getCell(
		final int offsetX,
		final int offsetY) {
		if (offsetX < -RADIUS || offsetX > RADIUS
			|| offsetY < -RADIUS || offsetY > RADIUS) {
			throw new IllegalArgumentException(
				"Neighborhood offsets must be in -" + RADIUS + ".." + RADIUS);
		}
		return cells.get((offsetY + RADIUS) * (RADIUS * 2 + 1)
			+ offsetX + RADIUS);
	}

	public LayeredTileStateParityComparison getCenterCell() {
		return getCell(0, 0);
	}

	public int getLegacyRepresentableCount() {
		return legacyRepresentableCount;
	}

	public int getUnsupportedCount() {
		return CELL_COUNT - legacyRepresentableCount;
	}

	public int getPackedSourcePresentCount() {
		return packedSourcePresentCount;
	}

	public int getMissingPackedSourceCount() {
		return missingPackedSourceCount;
	}

	public int getComparableCount() {
		return comparableCount;
	}

	public int getExactCount() {
		return exactCount;
	}

	public boolean isComplete() {
		return legacyRepresentableCount == CELL_COUNT
			&& packedSourcePresentCount == CELL_COUNT;
	}

	public boolean isExact() {
		return comparableCount == CELL_COUNT && exactCount == CELL_COUNT;
	}

	@Override
	public String toString() {
		return "LayeredTileNeighborhoodParityComparison{center=" + center
			+ ", legacyRepresentableCount=" + legacyRepresentableCount
			+ ", packedSourcePresentCount=" + packedSourcePresentCount
			+ ", missingPackedSourceCount=" + missingPackedSourceCount
			+ ", comparableCount=" + comparableCount + ", exactCount="
			+ exactCount + '}';
	}
}
