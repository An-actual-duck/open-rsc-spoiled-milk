package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dormant tile-mask comparison for an already expanded adjacent-step route.
 *
 * <p>This value does not choose, modify, or execute a route. It deliberately
 * excludes occupancy, NPC-specific scenery checks, projectile rules, and the
 * legacy Path zigzag policy. Existing movement and PathValidation remain
 * authoritative.</p>
 */
public final class LayeredTraversalCollisionComparison {
	public static final int MAXIMUM_STEP_COUNT = 50;

	private final List<LayeredAdjacentStepCollisionComparison> steps;
	private final int logicalDecisionAvailableCount;
	private final int packedDecisionAvailableCount;
	private final int comparableCount;
	private final int passabilityExactCount;
	private final int blockingReasonExactCount;
	private final int requiredStatesExactCount;
	private final Boolean logicalPassable;
	private final Boolean packedPassable;
	private final Integer firstLogicalBlockedStepIndex;
	private final Integer firstPackedBlockedStepIndex;
	private final Integer firstPassabilityMismatchStepIndex;
	private final Integer firstBlockingReasonMismatchStepIndex;

	private LayeredTraversalCollisionComparison(
		final List<LayeredAdjacentStepCollisionComparison> steps,
		final int logicalDecisionAvailableCount,
		final int packedDecisionAvailableCount,
		final int comparableCount,
		final int passabilityExactCount,
		final int blockingReasonExactCount,
		final int requiredStatesExactCount,
		final Boolean logicalPassable,
		final Boolean packedPassable,
		final Integer firstLogicalBlockedStepIndex,
		final Integer firstPackedBlockedStepIndex,
		final Integer firstPassabilityMismatchStepIndex,
		final Integer firstBlockingReasonMismatchStepIndex) {
		this.steps = Collections.unmodifiableList(
			new ArrayList<LayeredAdjacentStepCollisionComparison>(steps));
		this.logicalDecisionAvailableCount = logicalDecisionAvailableCount;
		this.packedDecisionAvailableCount = packedDecisionAvailableCount;
		this.comparableCount = comparableCount;
		this.passabilityExactCount = passabilityExactCount;
		this.blockingReasonExactCount = blockingReasonExactCount;
		this.requiredStatesExactCount = requiredStatesExactCount;
		this.logicalPassable = logicalPassable;
		this.packedPassable = packedPassable;
		this.firstLogicalBlockedStepIndex = firstLogicalBlockedStepIndex;
		this.firstPackedBlockedStepIndex = firstPackedBlockedStepIndex;
		this.firstPassabilityMismatchStepIndex = firstPassabilityMismatchStepIndex;
		this.firstBlockingReasonMismatchStepIndex = firstBlockingReasonMismatchStepIndex;
	}

	static LayeredTraversalCollisionComparison of(
		final List<LayeredAdjacentStepCollisionComparison> steps) {
		Objects.requireNonNull(steps, "steps");
		if (steps.isEmpty() || steps.size() > MAXIMUM_STEP_COUNT) {
			throw new IllegalArgumentException(
				"Layered traversal must contain 1-" + MAXIMUM_STEP_COUNT + " steps");
		}
		int logicalAvailable = 0;
		int packedAvailable = 0;
		int comparable = 0;
		int passabilityExact = 0;
		int reasonExact = 0;
		int statesExact = 0;
		boolean logicalAllPassable = true;
		boolean packedAllPassable = true;
		Integer firstLogicalBlocked = null;
		Integer firstPackedBlocked = null;
		Integer firstPassabilityMismatch = null;
		Integer firstReasonMismatch = null;
		WorldLocation expectedSource = null;
		for (int index = 0; index < steps.size(); index++) {
			LayeredAdjacentStepCollisionComparison step = Objects.requireNonNull(
				steps.get(index), "steps[" + index + "]");
			if (expectedSource != null && !expectedSource.equals(step.getSource())) {
				throw new IllegalArgumentException(
					"Layered traversal is discontinuous at step " + index);
			}
			expectedSource = step.getDestination();
			if (step.isLogicalDecisionAvailable()) {
				logicalAvailable++;
				if (!step.getLogicalPassable().booleanValue()) {
					logicalAllPassable = false;
					if (firstLogicalBlocked == null) {
						firstLogicalBlocked = Integer.valueOf(index);
					}
				}
			}
			if (step.isPackedDecisionAvailable()) {
				packedAvailable++;
				if (!step.getPackedPassable().booleanValue()) {
					packedAllPassable = false;
					if (firstPackedBlocked == null) {
						firstPackedBlocked = Integer.valueOf(index);
					}
				}
			}
			if (step.isComparable()) {
				comparable++;
			}
			if (step.isPassabilityExact()) {
				passabilityExact++;
			} else if (step.isComparable() && firstPassabilityMismatch == null) {
				firstPassabilityMismatch = Integer.valueOf(index);
			}
			if (step.isBlockingReasonExact()) {
				reasonExact++;
			} else if (step.isComparable() && firstReasonMismatch == null) {
				firstReasonMismatch = Integer.valueOf(index);
			}
			if (step.areRequiredStatesExact()) {
				statesExact++;
			}
		}
		int stepCount = steps.size();
		return new LayeredTraversalCollisionComparison(
			steps,
			logicalAvailable,
			packedAvailable,
			comparable,
			passabilityExact,
			reasonExact,
			statesExact,
			logicalAvailable == stepCount
				? Boolean.valueOf(logicalAllPassable) : null,
			packedAvailable == stepCount
				? Boolean.valueOf(packedAllPassable) : null,
			firstLogicalBlocked,
			firstPackedBlocked,
			firstPassabilityMismatch,
			firstReasonMismatch);
	}

	public List<LayeredAdjacentStepCollisionComparison> getSteps() {
		return steps;
	}

	public int getStepCount() { return steps.size(); }
	public WorldLocation getSource() { return steps.get(0).getSource(); }
	public WorldLocation getDestination() {
		return steps.get(steps.size() - 1).getDestination();
	}
	public int getLogicalDecisionAvailableCount() {
		return logicalDecisionAvailableCount;
	}
	public int getPackedDecisionAvailableCount() {
		return packedDecisionAvailableCount;
	}
	public int getComparableCount() { return comparableCount; }
	public int getPassabilityExactCount() { return passabilityExactCount; }
	public int getBlockingReasonExactCount() { return blockingReasonExactCount; }
	public int getRequiredStatesExactCount() { return requiredStatesExactCount; }
	public boolean isLogicalDecisionAvailable() { return logicalPassable != null; }
	public boolean isPackedDecisionAvailable() { return packedPassable != null; }
	public Boolean getLogicalPassable() { return logicalPassable; }
	public Boolean getPackedPassable() { return packedPassable; }
	public boolean isComparable() {
		return logicalPassable != null && packedPassable != null;
	}
	public boolean isPassabilityExact() {
		return isComparable() && logicalPassable.equals(packedPassable);
	}
	public boolean areAllStepsComparable() {
		return comparableCount == steps.size();
	}
	public boolean areAllStepPassabilitiesExact() {
		return passabilityExactCount == steps.size();
	}
	public boolean areAllStepBlockingReasonsExact() {
		return blockingReasonExactCount == steps.size();
	}
	public boolean areAllRequiredStatesExact() {
		return requiredStatesExactCount == steps.size();
	}
	public Integer getFirstLogicalBlockedStepIndex() {
		return firstLogicalBlockedStepIndex;
	}
	public Integer getFirstPackedBlockedStepIndex() {
		return firstPackedBlockedStepIndex;
	}
	public Integer getFirstPassabilityMismatchStepIndex() {
		return firstPassabilityMismatchStepIndex;
	}
	public Integer getFirstBlockingReasonMismatchStepIndex() {
		return firstBlockingReasonMismatchStepIndex;
	}

	@Override
	public String toString() {
		return "LayeredTraversalCollisionComparison{source=" + getSource()
			+ ", destination=" + getDestination() + ", stepCount="
			+ getStepCount() + ", logicalPassable=" + logicalPassable
			+ ", packedPassable=" + packedPassable + ", comparableCount="
			+ comparableCount + ", passabilityExactCount="
			+ passabilityExactCount + '}';
	}
}
