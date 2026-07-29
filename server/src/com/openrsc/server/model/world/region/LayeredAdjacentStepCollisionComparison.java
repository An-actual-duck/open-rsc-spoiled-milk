package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.Objects;

/**
 * Dormant comparison of one adjacent tile-mask collision decision.
 *
 * <p>This mirrors the tile-mask portion of legacy adjacent walking, including
 * its diagonal pass-through behavior. Player/NPC occupancy and NPC-specific
 * scenery checks are deliberately outside this value.</p>
 */
public final class LayeredAdjacentStepCollisionComparison {
	private final WorldLocation source;
	private final WorldLocation destination;
	private final int offsetX;
	private final int offsetY;
	private final int requiredCellCount;
	private final int exactRequiredStateCount;
	private final Decision logicalDecision;
	private final Decision packedDecision;

	private LayeredAdjacentStepCollisionComparison(
		final WorldLocation source,
		final WorldLocation destination,
		final int offsetX,
		final int offsetY,
		final int requiredCellCount,
		final int exactRequiredStateCount,
		final Decision logicalDecision,
		final Decision packedDecision) {
		this.source = source;
		this.destination = destination;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.requiredCellCount = requiredCellCount;
		this.exactRequiredStateCount = exactRequiredStateCount;
		this.logicalDecision = logicalDecision;
		this.packedDecision = packedDecision;
	}

	static LayeredAdjacentStepCollisionComparison of(
		final LayeredTileNeighborhoodParityComparison neighborhood,
		final int offsetX,
		final int offsetY) {
		Objects.requireNonNull(neighborhood, "neighborhood");
		validateStep(offsetX, offsetY);
		boolean[][] required = requiredCells(offsetX, offsetY);
		int requiredCount = 0;
		int exactCount = 0;
		boolean logicalAvailable = true;
		boolean packedAvailable = true;
		for (int y = -1; y <= 1; y++) {
			for (int x = -1; x <= 1; x++) {
				if (!required[y + 1][x + 1]) {
					continue;
				}
				requiredCount++;
				LayeredTileStateParityComparison cell = neighborhood.getCell(x, y);
				logicalAvailable &= cell.getLogicalSnapshotState() != null;
				packedAvailable &= cell.getDirectPackedState() != null;
				if (cell.isExact()) {
					exactCount++;
				}
			}
		}
		Decision logical = logicalAvailable
			? evaluate(neighborhood, offsetX, offsetY, true) : null;
		Decision packed = packedAvailable
			? evaluate(neighborhood, offsetX, offsetY, false) : null;
		return new LayeredAdjacentStepCollisionComparison(
			neighborhood.getCenter(),
			LayeredTileNeighborhoodParityComparison.offset(
				neighborhood.getCenter(), offsetX, offsetY),
			offsetX,
			offsetY,
			requiredCount,
			exactCount,
			logical,
			packed);
	}

	private static void validateStep(final int offsetX, final int offsetY) {
		if (offsetX < -1 || offsetX > 1 || offsetY < -1 || offsetY > 1
			|| (offsetX == 0 && offsetY == 0)) {
			throw new IllegalArgumentException(
				"Adjacent step offsets must be in -1..1 and not both zero");
		}
	}

	private static boolean[][] requiredCells(
		final int offsetX,
		final int offsetY) {
		boolean[][] required = new boolean[3][3];
		require(required, 0, 0);
		require(required, offsetX, offsetY);
		if (offsetX != 0 && offsetY != 0) {
			require(required, offsetX, 0);
			require(required, 0, offsetY);
			// Preserve the legacy northwest pass-through lookup at (+1,+1).
			if (offsetX == 1 && offsetY == -1) {
				require(required, 1, 1);
			}
		}
		return required;
	}

	private static void require(
		final boolean[][] required,
		final int offsetX,
		final int offsetY) {
		required[offsetY + 1][offsetX + 1] = true;
	}

	private static Decision evaluate(
		final LayeredTileNeighborhoodParityComparison neighborhood,
		final int offsetX,
		final int offsetY,
		final boolean logical) {
		boolean currentXBlocked = false;
		boolean currentYBlocked = false;
		boolean adjacentXBlocked = false;
		boolean adjacentYBlocked = false;
		int center = mask(neighborhood, 0, 0, logical);
		if (offsetX < 0) {
			currentXBlocked = isBlocking(
				center, CollisionFlag.WALL_EAST, true);
			adjacentXBlocked = isBlocking(
				mask(neighborhood, -1, 0, logical),
				CollisionFlag.WALL_WEST, false);
		} else if (offsetX > 0) {
			currentXBlocked = isBlocking(
				center, CollisionFlag.WALL_WEST, true);
			adjacentXBlocked = isBlocking(
				mask(neighborhood, 1, 0, logical),
				CollisionFlag.WALL_EAST, false);
		}
		if (offsetY < 0) {
			currentYBlocked = isBlocking(
				center, CollisionFlag.WALL_NORTH, true);
			adjacentYBlocked = isBlocking(
				mask(neighborhood, 0, -1, logical),
				CollisionFlag.WALL_SOUTH, false);
		} else if (offsetY > 0) {
			currentYBlocked = isBlocking(
				center, CollisionFlag.WALL_SOUTH, true);
			adjacentYBlocked = isBlocking(
				mask(neighborhood, 0, 1, logical),
				CollisionFlag.WALL_NORTH, false);
		}

		if (currentXBlocked && currentYBlocked) {
			return Decision.blocked(BlockingReason.CURRENT_AXES);
		}
		if (currentXBlocked && offsetY == 0) {
			return Decision.blocked(BlockingReason.CURRENT_X);
		}
		if (currentYBlocked && offsetX == 0) {
			return Decision.blocked(BlockingReason.CURRENT_Y);
		}
		if (adjacentXBlocked && adjacentYBlocked) {
			return Decision.blocked(BlockingReason.ADJACENT_AXES);
		}
		if (adjacentXBlocked && offsetY == 0) {
			return Decision.blocked(BlockingReason.ADJACENT_X);
		}
		if (adjacentYBlocked && offsetX == 0) {
			return Decision.blocked(BlockingReason.ADJACENT_Y);
		}
		if (currentXBlocked && adjacentXBlocked) {
			return Decision.blocked(BlockingReason.ADJACENT_X_CORRIDOR);
		}
		if (currentYBlocked && adjacentYBlocked) {
			return Decision.blocked(BlockingReason.ADJACENT_Y_CORRIDOR);
		}

		int destination = mask(neighborhood, offsetX, offsetY, logical);
		boolean destinationXBlocked = offsetX == 0 ? false : isBlocking(
			destination,
			offsetX > 0 ? CollisionFlag.WALL_EAST : CollisionFlag.WALL_WEST,
			false);
		boolean destinationYBlocked = offsetY == 0 ? false : isBlocking(
			destination,
			offsetY > 0 ? CollisionFlag.WALL_NORTH : CollisionFlag.WALL_SOUTH,
			false);
		if (destinationXBlocked && destinationYBlocked) {
			return Decision.blocked(BlockingReason.DESTINATION_AXES);
		}
		if (destinationXBlocked && offsetY == 0) {
			return Decision.blocked(BlockingReason.DESTINATION_X);
		}
		if (destinationYBlocked && offsetX == 0) {
			return Decision.blocked(BlockingReason.DESTINATION_Y);
		}
		if (currentXBlocked && destinationXBlocked) {
			return Decision.blocked(BlockingReason.DESTINATION_X_CORRIDOR);
		}
		if (currentYBlocked && destinationYBlocked) {
			return Decision.blocked(BlockingReason.DESTINATION_Y_CORRIDOR);
		}

		if (offsetX != 0 && offsetY != 0) {
			int diagonalBit = (offsetX > 0
				? CollisionFlag.WALL_EAST : CollisionFlag.WALL_WEST)
				| (offsetY > 0
					? CollisionFlag.WALL_NORTH : CollisionFlag.WALL_SOUTH);
			if (isBlocking(destination, diagonalBit, false)) {
				return Decision.blocked(BlockingReason.DESTINATION_DIAGONAL);
			}
			if (isBlocking(mask(neighborhood, offsetX, 0, logical), -2, false)
				|| isBlocking(mask(neighborhood, 0, offsetY, logical), -2, false)) {
				return Decision.blocked(BlockingReason.SIDE_DIAGONAL);
			}
			if (blocksDiagonalPassThrough(
				neighborhood, offsetX, offsetY, logical)) {
				return Decision.blocked(BlockingReason.DIAGONAL_PASS_THROUGH);
			}
		}
		return Decision.passable();
	}

	private static boolean blocksDiagonalPassThrough(
		final LayeredTileNeighborhoodParityComparison neighborhood,
		final int offsetX,
		final int offsetY,
		final boolean logical) {
		int xSide = mask(neighborhood, offsetX, 0, logical);
		int ySide = mask(neighborhood, 0, offsetY, logical);
		int destination = mask(neighborhood, offsetX, offsetY, logical);
		if (offsetX == -1 && offsetY == -1) {
			return (hasAny(xSide, CollisionFlag.FULL_BLOCK_A | CollisionFlag.FULL_BLOCK_C)
				&& (hasAny(ySide, CollisionFlag.WALL_EAST)
					|| hasAny(destination, CollisionFlag.WALL_WEST)))
				|| (hasAny(ySide, CollisionFlag.FULL_BLOCK_A | CollisionFlag.FULL_BLOCK_C)
					&& (hasAny(xSide, CollisionFlag.WALL_NORTH)
						|| hasAny(destination, CollisionFlag.WALL_SOUTH)));
		}
		if (offsetX == 1 && offsetY == -1) {
			int legacyAuxiliary = mask(neighborhood, 1, 1, logical);
			return (hasAny(xSide, CollisionFlag.FULL_BLOCK_B | CollisionFlag.FULL_BLOCK_C)
				&& (hasAny(ySide, CollisionFlag.WALL_WEST)
					|| hasAny(legacyAuxiliary, CollisionFlag.WALL_EAST)))
				|| (hasAny(ySide, CollisionFlag.FULL_BLOCK_B | CollisionFlag.FULL_BLOCK_C)
					&& (hasAny(xSide, CollisionFlag.WALL_NORTH)
						|| hasAny(destination, CollisionFlag.WALL_SOUTH)));
		}
		if (offsetX == -1 && offsetY == 1) {
			return (hasAny(xSide, CollisionFlag.FULL_BLOCK_B | CollisionFlag.FULL_BLOCK_C)
				&& (hasAny(ySide, CollisionFlag.WALL_EAST)
					|| hasAny(destination, CollisionFlag.WALL_WEST)))
				|| (hasAny(ySide, CollisionFlag.FULL_BLOCK_B | CollisionFlag.FULL_BLOCK_C)
					&& (hasAny(xSide, CollisionFlag.WALL_SOUTH)
						|| hasAny(destination, CollisionFlag.WALL_NORTH)));
		}
		return (hasAny(xSide, CollisionFlag.FULL_BLOCK_A | CollisionFlag.FULL_BLOCK_C)
			&& (hasAny(ySide, CollisionFlag.WALL_WEST)
				|| hasAny(destination, CollisionFlag.WALL_EAST)))
			|| (hasAny(ySide, CollisionFlag.FULL_BLOCK_A | CollisionFlag.FULL_BLOCK_C)
				&& (hasAny(xSide, CollisionFlag.WALL_SOUTH)
					|| hasAny(destination, CollisionFlag.WALL_NORTH)));
	}

	private static int mask(
		final LayeredTileNeighborhoodParityComparison neighborhood,
		final int offsetX,
		final int offsetY,
		final boolean logical) {
		LayeredTileStateParityComparison cell = neighborhood.getCell(offsetX, offsetY);
		LayeredTileState state = logical
			? cell.getLogicalSnapshotState() : cell.getDirectPackedState();
		if (state == null) {
			throw new IllegalStateException(
				"Collision decision requested from an unavailable tile state");
		}
		return state.getTraversalMask() & 0xFF;
	}

	private static boolean isBlocking(
		final int traversalMask,
		final int bit,
		final boolean currentTile) {
		if (bit > -1 && (traversalMask & bit) != 0) {
			return true;
		}
		if (!currentTile
			&& (traversalMask & CollisionFlag.FULL_BLOCK_A) != 0) {
			return true;
		}
		if (!currentTile
			&& (traversalMask & CollisionFlag.FULL_BLOCK_B) != 0) {
			return true;
		}
		return bit > -2 && !currentTile
			&& (traversalMask & CollisionFlag.FULL_BLOCK_C) != 0;
	}

	private static boolean hasAny(final int traversalMask, final int bits) {
		return (traversalMask & bits) != 0;
	}

	public WorldLocation getSource() {
		return source;
	}

	public WorldLocation getDestination() {
		return destination;
	}

	public int getOffsetX() {
		return offsetX;
	}

	public int getOffsetY() {
		return offsetY;
	}

	public int getRequiredCellCount() {
		return requiredCellCount;
	}

	public int getExactRequiredStateCount() {
		return exactRequiredStateCount;
	}

	public boolean areRequiredStatesExact() {
		return exactRequiredStateCount == requiredCellCount;
	}

	public boolean isLogicalDecisionAvailable() {
		return logicalDecision != null;
	}

	public boolean isPackedDecisionAvailable() {
		return packedDecision != null;
	}

	/** Returns null when one required logical tile is unsupported. */
	public Boolean getLogicalPassable() {
		return logicalDecision == null ? null : logicalDecision.passable;
	}

	/** Returns null when one required direct packed source is unavailable. */
	public Boolean getPackedPassable() {
		return packedDecision == null ? null : packedDecision.passable;
	}

	public BlockingReason getLogicalBlockingReason() {
		return logicalDecision == null ? null : logicalDecision.reason;
	}

	public BlockingReason getPackedBlockingReason() {
		return packedDecision == null ? null : packedDecision.reason;
	}

	public boolean isComparable() {
		return logicalDecision != null && packedDecision != null;
	}

	public boolean isPassabilityExact() {
		return isComparable()
			&& logicalDecision.passable == packedDecision.passable;
	}

	public boolean isBlockingReasonExact() {
		return isComparable()
			&& logicalDecision.reason == packedDecision.reason;
	}

	@Override
	public String toString() {
		return "LayeredAdjacentStepCollisionComparison{source=" + source
			+ ", destination=" + destination + ", offsetX=" + offsetX
			+ ", offsetY=" + offsetY + ", requiredCellCount="
			+ requiredCellCount + ", exactRequiredStateCount="
			+ exactRequiredStateCount + ", logicalDecision=" + logicalDecision
			+ ", packedDecision=" + packedDecision + '}';
	}

	/** Stable tile-mask reason; occupancy is outside this projection. */
	public enum BlockingReason {
		NONE,
		CURRENT_AXES,
		CURRENT_X,
		CURRENT_Y,
		ADJACENT_AXES,
		ADJACENT_X,
		ADJACENT_Y,
		ADJACENT_X_CORRIDOR,
		ADJACENT_Y_CORRIDOR,
		DESTINATION_AXES,
		DESTINATION_X,
		DESTINATION_Y,
		DESTINATION_X_CORRIDOR,
		DESTINATION_Y_CORRIDOR,
		DESTINATION_DIAGONAL,
		SIDE_DIAGONAL,
		DIAGONAL_PASS_THROUGH
	}

	private static final class Decision {
		private final boolean passable;
		private final BlockingReason reason;

		private Decision(final boolean passable, final BlockingReason reason) {
			this.passable = passable;
			this.reason = reason;
		}

		static Decision passable() {
			return new Decision(true, BlockingReason.NONE);
		}

		static Decision blocked(final BlockingReason reason) {
			return new Decision(false, reason);
		}

		@Override
		public String toString() {
			return passable ? "PASS" : "BLOCKED:" + reason;
		}
	}
}
