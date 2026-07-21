package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, read-only contents and quiescence assessment for packed Region
 * retirement readiness or an explicitly non-ready diagnostic selection.
 *
 * <p>The assessment is intentionally ephemeral. Counts can change immediately
 * after capture, so even a lifecycle-ready result would be evidence rather than
 * a claim or commit token. The current manager reports no per-Region reload
 * capability, keeping all sources blocked regardless of apparent quiescence.
 * A diagnostic selection additionally lacks retirement-readiness evidence by
 * construction and cannot manufacture it from content counts.</p>
 */
public final class LayeredPackedRegionRetirementSafetyAssessment {
	private final long observedAtTick;
	private final long readinessObservedAtTick;
	private final long ownershipVersion;
	private final long residencyMirrorVersion;
	private final boolean retirementReadinessEvidence;
	private final List<SourceAssessment> sources;
	private final int contentQuiescentSourceCount;
	private final int lifecycleReadySourceCount;

	private LayeredPackedRegionRetirementSafetyAssessment(
		final long observedAtTick,
		final LayeredPackedRegionRetirementReadiness readiness,
		final List<SourceAssessment> sources) {
		this.observedAtTick = observedAtTick;
		this.readinessObservedAtTick = readiness.getObservedAtTick();
		this.ownershipVersion = readiness.getOwnershipVersion();
		this.residencyMirrorVersion = readiness.getResidencyMirrorVersion();
		this.retirementReadinessEvidence = true;
		this.sources = Collections.unmodifiableList(sources);
		int quiescent = 0;
		int lifecycleReady = 0;
		for (SourceAssessment source : sources) {
			quiescent += source.isContentQuiescent() ? 1 : 0;
			lifecycleReady += source.isLifecycleReady() ? 1 : 0;
		}
		this.contentQuiescentSourceCount = quiescent;
		this.lifecycleReadySourceCount = lifecycleReady;
	}

	private LayeredPackedRegionRetirementSafetyAssessment(
		final long observedAtTick,
		final List<SourceAssessment> sources) {
		this.observedAtTick = observedAtTick;
		this.readinessObservedAtTick = -1L;
		this.ownershipVersion = -1L;
		this.residencyMirrorVersion = -1L;
		this.retirementReadinessEvidence = false;
		this.sources = Collections.unmodifiableList(sources);
		int quiescent = 0;
		for (SourceAssessment source : sources) {
			quiescent += source.isContentQuiescent() ? 1 : 0;
			if (source.isLifecycleReady()) {
				throw new IllegalArgumentException(
					"Diagnostic selection cannot carry lifecycle readiness");
			}
		}
		this.contentQuiescentSourceCount = quiescent;
		this.lifecycleReadySourceCount = 0;
	}

	/** Combines one bounded readiness value with same-order source contents. */
	public static LayeredPackedRegionRetirementSafetyAssessment assess(
		final LayeredPackedRegionRetirementReadiness readiness,
		final List<PackedSourceContents> packedSourceContents,
		final long observedAtTick,
		final int maximumPackedSources) {
		LayeredPackedRegionRetirementReadiness checkedReadiness =
			Objects.requireNonNull(readiness, "readiness");
		if (packedSourceContents == null) {
			throw new NullPointerException("packedSourceContents");
		}
		if (observedAtTick < 0L
			|| checkedReadiness.getObservedAtTick() > observedAtTick
			|| maximumPackedSources < 0
			|| checkedReadiness.getSourceCount() > maximumPackedSources
			|| packedSourceContents.size()
				!= checkedReadiness.getSourceCount()) {
			throw new IllegalArgumentException(
				"Packed retirement contents differ from bounded readiness");
		}
		List<SourceAssessment> assessments =
			new ArrayList<SourceAssessment>(packedSourceContents.size());
		Set<Long> uniqueSources = new LinkedHashSet<Long>();
		for (int index = 0; index < packedSourceContents.size(); index++) {
			LayeredPackedRegionRetirementReadiness.SourceReadiness source =
				checkedReadiness.getSources().get(index);
			PackedSourceContents contents = Objects.requireNonNull(
				packedSourceContents.get(index),
				"packedSourceContents[" + index + "]");
			if (source.getPackedRegionX() != contents.getPackedRegionX()
				|| source.getPackedRegionY() != contents.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Packed retirement contents are not in readiness order");
			}
			long key = ((long) contents.getPackedRegionX() << 32)
				^ (contents.getPackedRegionY() & 0xFFFFFFFFL);
			if (!uniqueSources.add(Long.valueOf(key))) {
				throw new IllegalArgumentException(
					"Packed retirement contents contain a duplicate source");
			}
			assessments.add(SourceAssessment.from(source, contents));
		}
		return new LayeredPackedRegionRetirementSafetyAssessment(
			observedAtTick, checkedReadiness, assessments);
	}

	/**
	 * Captures contents for an exact diagnostic candidate selection without
	 * inventing logical retirement decisions or packed-source readiness.
	 */
	public static LayeredPackedRegionRetirementSafetyAssessment
		assessDiagnosticSelection(
			final List<PackedSourceContents> packedSourceContents,
			final long observedAtTick,
			final int maximumPackedSources) {
		if (packedSourceContents == null) {
			throw new NullPointerException("packedSourceContents");
		}
		if (observedAtTick < 0L || maximumPackedSources < 0
			|| packedSourceContents.size() > maximumPackedSources) {
			throw new IllegalArgumentException(
				"Diagnostic selection exceeds its bounded source budget");
		}
		List<SourceAssessment> assessments =
			new ArrayList<SourceAssessment>(packedSourceContents.size());
		Set<Long> uniqueSources = new LinkedHashSet<Long>();
		for (int index = 0; index < packedSourceContents.size(); index++) {
			PackedSourceContents contents = Objects.requireNonNull(
				packedSourceContents.get(index),
				"packedSourceContents[" + index + "]");
			long key = ((long) contents.getPackedRegionX() << 32)
				^ (contents.getPackedRegionY() & 0xFFFFFFFFL);
			if (!uniqueSources.add(Long.valueOf(key))) {
				throw new IllegalArgumentException(
					"Diagnostic selection contains a duplicate source");
			}
			assessments.add(SourceAssessment.fromDiagnosticSelection(contents));
		}
		return new LayeredPackedRegionRetirementSafetyAssessment(
			observedAtTick, assessments);
	}

	public long getObservedAtTick() {
		return observedAtTick;
	}

	public long getReadinessObservedAtTick() {
		return readinessObservedAtTick;
	}

	public long getOwnershipVersion() {
		return ownershipVersion;
	}

	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}

	public boolean hasRetirementReadinessEvidence() {
		return retirementReadinessEvidence;
	}

	public List<SourceAssessment> getSources() {
		return sources;
	}

	public int getSourceCount() {
		return sources.size();
	}

	public int getContentQuiescentSourceCount() {
		return contentQuiescentSourceCount;
	}

	public int getLifecycleReadySourceCount() {
		return lifecycleReadySourceCount;
	}

	public int getBlockedSourceCount() {
		return getSourceCount() - lifecycleReadySourceCount;
	}

	public enum Blocker {
		READINESS_NOT_READY,
		SOURCE_NOT_RESIDENT,
		TILE_STORAGE_UNAVAILABLE,
		PLAYERS_PRESENT,
		NPCS_PRESENT,
		OBJECTS_PRESENT,
		GROUND_ITEMS_PRESENT,
		RELOAD_PATH_UNAVAILABLE
	}

	/** Immutable counts captured for one packed source. */
	public static final class PackedSourceContents {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean resident;
		private final boolean tileStorageAvailable;
		private final boolean regionReloadSupported;
		private final int playerCount;
		private final int npcCount;
		private final int objectCount;
		private final int groundItemCount;

		private PackedSourceContents(
			final int packedRegionX,
			final int packedRegionY,
			final boolean resident,
			final boolean tileStorageAvailable,
			final boolean regionReloadSupported,
			final int playerCount,
			final int npcCount,
			final int objectCount,
			final int groundItemCount) {
			if (packedRegionX < 0 || packedRegionY < 0
				|| (tileStorageAvailable && !resident)
				|| (!resident && (playerCount > 0 || npcCount > 0
					|| objectCount > 0 || groundItemCount > 0))
				|| playerCount < 0 || npcCount < 0 || objectCount < 0
				|| groundItemCount < 0) {
				throw new IllegalArgumentException(
					"Invalid packed Region retirement contents");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.resident = resident;
			this.tileStorageAvailable = tileStorageAvailable;
			this.regionReloadSupported = regionReloadSupported;
			this.playerCount = playerCount;
			this.npcCount = npcCount;
			this.objectCount = objectCount;
			this.groundItemCount = groundItemCount;
		}

		public static PackedSourceContents of(
			final int packedRegionX,
			final int packedRegionY,
			final boolean resident,
			final boolean tileStorageAvailable,
			final boolean regionReloadSupported,
			final int playerCount,
			final int npcCount,
			final int objectCount,
			final int groundItemCount) {
			return new PackedSourceContents(
				packedRegionX, packedRegionY, resident, tileStorageAvailable,
				regionReloadSupported, playerCount, npcCount, objectCount,
				groundItemCount);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isResident() { return resident; }
		public boolean isTileStorageAvailable() { return tileStorageAvailable; }
		public boolean isRegionReloadSupported() { return regionReloadSupported; }
		public int getPlayerCount() { return playerCount; }
		public int getNpcCount() { return npcCount; }
		public int getObjectCount() { return objectCount; }
		public int getGroundItemCount() { return groundItemCount; }
	}

	/** One immutable assessment; never a lifecycle claim or commit token. */
	public static final class SourceAssessment {
		private final int packedRegionX;
		private final int packedRegionY;
		private final LayeredPackedRegionRetirementReadiness.SourceState
			readinessState;
		private final boolean resident;
		private final boolean tileStorageAvailable;
		private final boolean regionReloadSupported;
		private final int playerCount;
		private final int npcCount;
		private final int objectCount;
		private final int groundItemCount;
		private final boolean contentQuiescent;
		private final boolean lifecycleReady;
		private final List<Blocker> blockers;

		private SourceAssessment(
			final LayeredPackedRegionRetirementReadiness.SourceReadiness source,
			final PackedSourceContents contents,
			final boolean contentQuiescent,
			final boolean lifecycleReady,
			final List<Blocker> blockers) {
			this.packedRegionX = contents.getPackedRegionX();
			this.packedRegionY = contents.getPackedRegionY();
			this.readinessState = source.getSourceState();
			this.resident = contents.isResident();
			this.tileStorageAvailable = contents.isTileStorageAvailable();
			this.regionReloadSupported = contents.isRegionReloadSupported();
			this.playerCount = contents.getPlayerCount();
			this.npcCount = contents.getNpcCount();
			this.objectCount = contents.getObjectCount();
			this.groundItemCount = contents.getGroundItemCount();
			this.contentQuiescent = contentQuiescent;
			this.lifecycleReady = lifecycleReady;
			this.blockers = Collections.unmodifiableList(blockers);
		}

		private static SourceAssessment from(
			final LayeredPackedRegionRetirementReadiness.SourceReadiness source,
			final PackedSourceContents contents) {
			List<Blocker> blockers = new ArrayList<Blocker>();
			if (!source.isReady()) {
				blockers.add(Blocker.READINESS_NOT_READY);
			}
			if (!contents.isResident()) {
				blockers.add(Blocker.SOURCE_NOT_RESIDENT);
			}
			if (!contents.isTileStorageAvailable()) {
				blockers.add(Blocker.TILE_STORAGE_UNAVAILABLE);
			}
			if (contents.getPlayerCount() > 0) {
				blockers.add(Blocker.PLAYERS_PRESENT);
			}
			if (contents.getNpcCount() > 0) {
				blockers.add(Blocker.NPCS_PRESENT);
			}
			if (contents.getObjectCount() > 0) {
				blockers.add(Blocker.OBJECTS_PRESENT);
			}
			if (contents.getGroundItemCount() > 0) {
				blockers.add(Blocker.GROUND_ITEMS_PRESENT);
			}
			if (!contents.isRegionReloadSupported()) {
				blockers.add(Blocker.RELOAD_PATH_UNAVAILABLE);
			}
			boolean quiescent = contents.isResident()
				&& contents.isTileStorageAvailable()
				&& contents.getPlayerCount() == 0
				&& contents.getNpcCount() == 0
				&& contents.getObjectCount() == 0
				&& contents.getGroundItemCount() == 0;
			boolean ready = source.isReady() && quiescent
				&& contents.isRegionReloadSupported();
			if (ready != blockers.isEmpty()) {
				throw new IllegalStateException(
					"Packed Region blockers differ from lifecycle readiness");
			}
			return new SourceAssessment(
				source, contents, quiescent, ready, blockers);
		}

		private static SourceAssessment fromDiagnosticSelection(
			final PackedSourceContents contents) {
			List<Blocker> blockers = new ArrayList<Blocker>();
			blockers.add(Blocker.READINESS_NOT_READY);
			if (!contents.isResident()) {
				blockers.add(Blocker.SOURCE_NOT_RESIDENT);
			}
			if (!contents.isTileStorageAvailable()) {
				blockers.add(Blocker.TILE_STORAGE_UNAVAILABLE);
			}
			if (contents.getPlayerCount() > 0) {
				blockers.add(Blocker.PLAYERS_PRESENT);
			}
			if (contents.getNpcCount() > 0) {
				blockers.add(Blocker.NPCS_PRESENT);
			}
			if (contents.getObjectCount() > 0) {
				blockers.add(Blocker.OBJECTS_PRESENT);
			}
			if (contents.getGroundItemCount() > 0) {
				blockers.add(Blocker.GROUND_ITEMS_PRESENT);
			}
			if (!contents.isRegionReloadSupported()) {
				blockers.add(Blocker.RELOAD_PATH_UNAVAILABLE);
			}
			boolean quiescent = contents.isResident()
				&& contents.isTileStorageAvailable()
				&& contents.getPlayerCount() == 0
				&& contents.getNpcCount() == 0
				&& contents.getObjectCount() == 0
				&& contents.getGroundItemCount() == 0;
			return new SourceAssessment(contents, quiescent, blockers);
		}

		private SourceAssessment(
			final PackedSourceContents contents,
			final boolean contentQuiescent,
			final List<Blocker> blockers) {
			this.packedRegionX = contents.getPackedRegionX();
			this.packedRegionY = contents.getPackedRegionY();
			this.readinessState = LayeredPackedRegionRetirementReadiness
				.SourceState.DIAGNOSTIC_SELECTION_ONLY;
			this.resident = contents.isResident();
			this.tileStorageAvailable = contents.isTileStorageAvailable();
			this.regionReloadSupported = contents.isRegionReloadSupported();
			this.playerCount = contents.getPlayerCount();
			this.npcCount = contents.getNpcCount();
			this.objectCount = contents.getObjectCount();
			this.groundItemCount = contents.getGroundItemCount();
			this.contentQuiescent = contentQuiescent;
			this.lifecycleReady = false;
			this.blockers = Collections.unmodifiableList(blockers);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public LayeredPackedRegionRetirementReadiness.SourceState
			getReadinessState() { return readinessState; }
		public boolean isResident() { return resident; }
		public boolean isTileStorageAvailable() { return tileStorageAvailable; }
		public boolean isRegionReloadSupported() { return regionReloadSupported; }
		public int getPlayerCount() { return playerCount; }
		public int getNpcCount() { return npcCount; }
		public int getObjectCount() { return objectCount; }
		public int getGroundItemCount() { return groundItemCount; }
		public boolean isContentQuiescent() { return contentQuiescent; }
		public boolean isLifecycleReady() { return lifecycleReady; }
		public List<Blocker> getBlockers() { return blockers; }
	}
}
