package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements.SelectedSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Thread-confined, handle-free proof that one exact packed-source set was
 * resident while the RegionManager lifecycle boundary was held.
 *
 * <p>The boundary contains detached coordinates only. It is invalidated before
 * the RegionManager lifecycle monitor is released and cannot authorize source
 * absence, reconstruction, visibility, or any later operation.</p>
 */
public final class LayeredPackedRegionSourceLifecycleBoundary {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long residencyMirrorVersion;
	private final List<PackedSource> selectedSources;
	private final Thread boundaryThread;
	private boolean active = true;

	private LayeredPackedRegionSourceLifecycleBoundary(
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final long residencyMirrorVersion,
		final boolean regionLifecycleBoundaryHeld) {
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		if (!regionLifecycleBoundaryHeld || residencyMirrorVersion < 0L
			|| checked.getSelectedSourceCount() <= 0
			|| checked.getSelectedSources().size()
				!= checked.getSelectedSourceCount()) {
			throw new IllegalArgumentException(
				"Packed-source lifecycle boundary is incomplete");
		}
		this.generation = checked.getGeneration();
		this.requirementsObservedAtTick =
			checked.getEventObservedAtTick();
		this.residencyMirrorVersion = residencyMirrorVersion;
		List<PackedSource> sources =
			new ArrayList<PackedSource>(checked.getSelectedSourceCount());
		Set<Long> unique = new HashSet<Long>();
		for (SelectedSource source : checked.getSelectedSources()) {
			SelectedSource selected =
				Objects.requireNonNull(source, "selectedSource");
			long key = pack(
				selected.getPackedRegionX(), selected.getPackedRegionY());
			if (!unique.add(Long.valueOf(key))) {
				throw new IllegalArgumentException(
					"Packed-source lifecycle boundary contains a duplicate");
			}
			sources.add(new PackedSource(
				selected.getPackedRegionX(), selected.getPackedRegionY()));
		}
		this.selectedSources = Collections.unmodifiableList(sources);
		this.boundaryThread = Thread.currentThread();
	}

	static LayeredPackedRegionSourceLifecycleBoundary open(
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final long residencyMirrorVersion,
		final boolean regionLifecycleBoundaryHeld) {
		return new LayeredPackedRegionSourceLifecycleBoundary(
			requirements, residencyMirrorVersion,
			regionLifecycleBoundaryHeld);
	}

	public long getGeneration() {
		requireActive();
		return generation;
	}

	public long getRequirementsObservedAtTick() {
		requireActive();
		return requirementsObservedAtTick;
	}

	public long getResidencyMirrorVersion() {
		requireActive();
		return residencyMirrorVersion;
	}

	public int getSelectedSourceCount() {
		requireActive();
		return selectedSources.size();
	}

	public List<PackedSource> getSelectedSources() {
		requireActive();
		return selectedSources;
	}

	public boolean matchesRequirements(
		final LayeredPackedRegionNpcOwnerPreservationRequirements
			requirements) {
		requireActive();
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		if (generation != checked.getGeneration()
			|| requirementsObservedAtTick
				!= checked.getEventObservedAtTick()
			|| selectedSources.size() != checked.getSelectedSourceCount()
			|| selectedSources.size()
				!= checked.getSelectedSources().size()) {
			return false;
		}
		for (int index = 0; index < selectedSources.size(); index++) {
			PackedSource left = selectedSources.get(index);
			SelectedSource right = checked.getSelectedSources().get(index);
			if (right == null
				|| left.getPackedRegionX()
					!= right.getPackedRegionX()
				|| left.getPackedRegionY()
					!= right.getPackedRegionY()) {
				return false;
			}
		}
		return true;
	}

	public boolean isRegionLifecycleBoundaryHeld() {
		requireActive();
		return true;
	}

	public boolean isAllSourcesResidentAtEntry() {
		requireActive();
		return true;
	}

	public boolean isSourceAbsencePerformed() {
		requireActive();
		return false;
	}

	public boolean isSourceReconstructionPerformed() {
		requireActive();
		return false;
	}

	public boolean isRuntimeHandleRetained() {
		requireActive();
		return false;
	}

	public boolean isLifecycleAuthority() {
		requireActive();
		return false;
	}

	void invalidate() {
		requireBoundaryThread();
		if (!active) {
			throw new IllegalStateException(
				"Packed-source lifecycle boundary is already inactive");
		}
		active = false;
	}

	private void requireActive() {
		requireBoundaryThread();
		if (!active) {
			throw new IllegalStateException(
				"Packed-source lifecycle boundary is no longer active");
		}
	}

	private void requireBoundaryThread() {
		if (Thread.currentThread() != boundaryThread) {
			throw new IllegalStateException(
				"Packed-source lifecycle boundary is thread-confined");
		}
	}

	private static long pack(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	@FunctionalInterface
	public interface Operation {
		void execute(LayeredPackedRegionSourceLifecycleBoundary boundary);
	}

	/** Detached packed-source coordinate; never a Region or registry handle. */
	public static final class PackedSource {
		private final int packedRegionX;
		private final int packedRegionY;

		private PackedSource(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		public int getPackedRegionX() {
			return packedRegionX;
		}

		public int getPackedRegionY() {
			return packedRegionY;
		}
	}
}
