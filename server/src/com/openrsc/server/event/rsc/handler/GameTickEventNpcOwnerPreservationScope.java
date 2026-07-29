package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements.SelectedSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thread-confined capability that exists only while one exact NPC-owner
 * preservation boundary remains held.
 *
 * <p>The scope carries detached source identities and counts, never event or
 * NPC references. It is invalidated before the enclosing gates release, so a
 * caller cannot retain it as a preservation fact, permit, lease, or commit
 * token for later work.</p>
 */
final class GameTickEventNpcOwnerPreservationScope {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final String schedulerInstanceIdentity;
	private final int requiredEventLinkCount;
	private final int requiredOwnerCount;
	private final List<PackedSource> selectedSources;
	private final Thread boundaryThread;
	private boolean active = true;

	private GameTickEventNpcOwnerPreservationScope(
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final int heldEventCount,
		final int heldOwnerCount,
		final boolean worldRegistrationBoundaryHeld,
		final boolean regionAbsenceQuiescenceHeld) {
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		if (!checked.isNpcRequirementSetComplete()
			|| heldEventCount != checked.getEventLinkCount()
			|| heldOwnerCount != checked.getUniqueNpcOwnerCount()
			|| !worldRegistrationBoundaryHeld
			|| !regionAbsenceQuiescenceHeld) {
			throw new IllegalArgumentException(
				"NPC owner preservation scope is incomplete");
		}
		this.generation = checked.getGeneration();
		this.requirementsObservedAtTick = checked.getEventObservedAtTick();
		this.schedulerInstanceIdentity =
			checked.getSchedulerInstanceIdentity();
		this.requiredEventLinkCount = checked.getEventLinkCount();
		this.requiredOwnerCount = checked.getUniqueNpcOwnerCount();
		List<PackedSource> sources =
			new ArrayList<PackedSource>(checked.getSelectedSources().size());
		for (SelectedSource source : checked.getSelectedSources()) {
			sources.add(new PackedSource(
				source.getPackedRegionX(), source.getPackedRegionY()));
		}
		this.selectedSources = Collections.unmodifiableList(sources);
		if (selectedSources.size() != checked.getSelectedSourceCount()) {
			throw new IllegalArgumentException(
				"NPC owner preservation source set is incomplete");
		}
		this.boundaryThread = Thread.currentThread();
	}

	static GameTickEventNpcOwnerPreservationScope open(
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final int heldEventCount,
		final int heldOwnerCount,
		final boolean worldRegistrationBoundaryHeld,
		final boolean regionAbsenceQuiescenceHeld) {
		return new GameTickEventNpcOwnerPreservationScope(
			requirements, heldEventCount, heldOwnerCount,
			worldRegistrationBoundaryHeld, regionAbsenceQuiescenceHeld);
	}

	long getGeneration() {
		requireActive();
		return generation;
	}

	long getRequirementsObservedAtTick() {
		requireActive();
		return requirementsObservedAtTick;
	}

	String getSchedulerInstanceIdentity() {
		requireActive();
		return schedulerInstanceIdentity;
	}

	int getRequiredEventLinkCount() {
		requireActive();
		return requiredEventLinkCount;
	}

	int getRequiredOwnerCount() {
		requireActive();
		return requiredOwnerCount;
	}

	List<PackedSource> getSelectedSources() {
		requireActive();
		return selectedSources;
	}

	boolean isCompleteBoundaryHeld() {
		requireActive();
		return true;
	}

	boolean isPointInTimeScope() {
		requireActive();
		return true;
	}

	boolean isRuntimeHandleRetained() {
		requireActive();
		return false;
	}

	boolean isReusablePermit() {
		requireActive();
		return false;
	}

	void invalidate() {
		requireBoundaryThread();
		if (!active) {
			throw new IllegalStateException(
				"NPC owner preservation scope is already inactive");
		}
		active = false;
	}

	private void requireActive() {
		requireBoundaryThread();
		if (!active) {
			throw new IllegalStateException(
				"NPC owner preservation scope is no longer active");
		}
	}

	private void requireBoundaryThread() {
		if (Thread.currentThread() != boundaryThread) {
			throw new IllegalStateException(
				"NPC owner preservation scope is thread-confined");
		}
	}

	/** Detached packed-source identity; never a Region handle. */
	static final class PackedSource {
		private final int packedRegionX;
		private final int packedRegionY;

		private PackedSource(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		int getPackedRegionX() {
			return packedRegionX;
		}

		int getPackedRegionY() {
			return packedRegionY;
		}
	}
}
