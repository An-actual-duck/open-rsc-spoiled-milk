package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable conservative packed-source reach for detached authored placements.
 *
 * <p>Every entry has the same source identity and order as an entry in
 * {@link LayeredPackedRegionAuthoredPlacementManifest}. Its bounded rectangle
 * covers the placement's authored anchor plus its object footprint, NPC roaming
 * bounds, or anchor-only item reach. The value describes dependency closure;
 * it does not retain runtime state or authorize loading, teardown, or replay.</p>
 */
public final class LayeredPackedRegionAuthoredPlacementDependencyInventory {
	public static final int MAXIMUM_PACKED_SOURCES =
		LayeredPackedRegionAuthoredPlacementManifest.MAXIMUM_PACKED_SOURCES;
	public static final int MAXIMUM_AUTHORED_PLACEMENTS =
		LayeredPackedRegionAuthoredPlacementManifest
			.MAXIMUM_AUTHORED_PLACEMENTS;

	private final long generation;
	private final List<PackedSourceDependencies> sources;
	private final int dependencyCount;
	private final int crossSourceDependencyCount;
	private final int affectedSourceReferenceCount;
	private final int maximumAffectedSourceCount;
	private final int objectFootprintDependencyCount;
	private final int npcRoamingDependencyCount;
	private final int anchorOnlyDependencyCount;
	private final int crossSourceObjectFootprintCount;
	private final int crossSourceNpcRoamingCount;
	private final int objectFootprintSourceReferenceCount;
	private final int npcRoamingSourceReferenceCount;
	private final int anchorOnlySourceReferenceCount;
	private final int maximumObjectFootprintSourceCount;
	private final int maximumNpcRoamingSourceCount;

	private LayeredPackedRegionAuthoredPlacementDependencyInventory(
		final long generation,
		final List<PackedSourceDependencies> sources) {
		this.generation = generation;
		this.sources = Collections.unmodifiableList(sources);
		int dependencies = 0;
		int crossSource = 0;
		int sourceReferences = 0;
		int maximumReferences = 0;
		int objectFootprints = 0;
		int npcRoaming = 0;
		int anchorOnly = 0;
		int crossSourceObjectFootprints = 0;
		int crossSourceNpcRoaming = 0;
		int objectFootprintReferences = 0;
		int npcRoamingReferences = 0;
		int anchorOnlyReferences = 0;
		int maximumObjectFootprintReferences = 0;
		int maximumNpcRoamingReferences = 0;
		for (PackedSourceDependencies source : sources) {
			dependencies = Math.addExact(
				dependencies, source.getDependencyCount());
			crossSource = Math.addExact(
				crossSource, source.getCrossSourceDependencyCount());
			sourceReferences = Math.addExact(
				sourceReferences, source.getAffectedSourceReferenceCount());
			maximumReferences = Math.max(
				maximumReferences, source.getMaximumAffectedSourceCount());
			for (PlacementDependency dependency : source.getDependencies()) {
				switch (dependency.getDependencyKind()) {
					case OBJECT_FOOTPRINT:
						objectFootprints = Math.incrementExact(objectFootprints);
						objectFootprintReferences = Math.addExact(
							objectFootprintReferences,
							dependency.getAffectedSourceCount());
						maximumObjectFootprintReferences = Math.max(
							maximumObjectFootprintReferences,
							dependency.getAffectedSourceCount());
						if (dependency.isCrossSource()) {
							crossSourceObjectFootprints = Math.incrementExact(
								crossSourceObjectFootprints);
						}
						break;
					case NPC_ROAMING:
						npcRoaming = Math.incrementExact(npcRoaming);
						npcRoamingReferences = Math.addExact(
							npcRoamingReferences,
							dependency.getAffectedSourceCount());
						maximumNpcRoamingReferences = Math.max(
							maximumNpcRoamingReferences,
							dependency.getAffectedSourceCount());
						if (dependency.isCrossSource()) {
							crossSourceNpcRoaming = Math.incrementExact(
								crossSourceNpcRoaming);
						}
						break;
					case ANCHOR_ONLY:
						anchorOnly = Math.incrementExact(anchorOnly);
						anchorOnlyReferences = Math.addExact(
							anchorOnlyReferences,
							dependency.getAffectedSourceCount());
						break;
					default:
						throw new IllegalArgumentException(
							"Unsupported dependency kind: "
								+ dependency.getDependencyKind());
				}
			}
		}
		this.dependencyCount = dependencies;
		this.crossSourceDependencyCount = crossSource;
		this.affectedSourceReferenceCount = sourceReferences;
		this.maximumAffectedSourceCount = maximumReferences;
		this.objectFootprintDependencyCount = objectFootprints;
		this.npcRoamingDependencyCount = npcRoaming;
		this.anchorOnlyDependencyCount = anchorOnly;
		this.crossSourceObjectFootprintCount =
			crossSourceObjectFootprints;
		this.crossSourceNpcRoamingCount = crossSourceNpcRoaming;
		this.objectFootprintSourceReferenceCount =
			objectFootprintReferences;
		this.npcRoamingSourceReferenceCount = npcRoamingReferences;
		this.anchorOnlySourceReferenceCount = anchorOnlyReferences;
		this.maximumObjectFootprintSourceCount =
			maximumObjectFootprintReferences;
		this.maximumNpcRoamingSourceCount = maximumNpcRoamingReferences;
	}

	public static
		LayeredPackedRegionAuthoredPlacementDependencyInventory empty() {
		return new LayeredPackedRegionAuthoredPlacementDependencyInventory(
			0L, Collections.<PackedSourceDependencies>emptyList());
	}

	public static Builder builder(final long generation) {
		return new Builder(generation);
	}

	public long getGeneration() { return generation; }
	public List<PackedSourceDependencies> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getDependencyCount() { return dependencyCount; }
	public int getCrossSourceDependencyCount() {
		return crossSourceDependencyCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public int getMaximumAffectedSourceCount() {
		return maximumAffectedSourceCount;
	}
	public int getObjectFootprintDependencyCount() {
		return objectFootprintDependencyCount;
	}
	public int getNpcRoamingDependencyCount() {
		return npcRoamingDependencyCount;
	}
	public int getAnchorOnlyDependencyCount() {
		return anchorOnlyDependencyCount;
	}
	public int getCrossSourceObjectFootprintCount() {
		return crossSourceObjectFootprintCount;
	}
	public int getCrossSourceNpcRoamingCount() {
		return crossSourceNpcRoamingCount;
	}
	public int getObjectFootprintSourceReferenceCount() {
		return objectFootprintSourceReferenceCount;
	}
	public int getNpcRoamingSourceReferenceCount() {
		return npcRoamingSourceReferenceCount;
	}
	public int getAnchorOnlySourceReferenceCount() {
		return anchorOnlySourceReferenceCount;
	}
	public int getMaximumObjectFootprintSourceCount() {
		return maximumObjectFootprintSourceCount;
	}
	public int getMaximumNpcRoamingSourceCount() {
		return maximumNpcRoamingSourceCount;
	}

	public PackedSourceDependencies findSource(
		final int packedRegionX,
		final int packedRegionY) {
		int low = 0;
		int high = sources.size() - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			PackedSourceDependencies source = sources.get(middle);
			int x = Integer.compare(
				source.getPackedRegionX(), packedRegionX);
			int comparison = x != 0 ? x : Integer.compare(
				source.getPackedRegionY(), packedRegionY);
			if (comparison < 0) {
				low = middle + 1;
			} else if (comparison > 0) {
				high = middle - 1;
			} else {
				return source;
			}
		}
		return null;
	}

	/** Proves source identity, order, and family match a completed manifest. */
	public boolean isAlignedWith(
		final LayeredPackedRegionAuthoredPlacementManifest manifest) {
		if (manifest == null
			|| generation != manifest.getGeneration()
			|| getSourceCount() != manifest.getSourceCount()
			|| dependencyCount != manifest.getPlacementCount()) {
			return false;
		}
		for (PackedSourceDependencies source : sources) {
			LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest
				definitions = manifest.findSource(
					source.getPackedRegionX(), source.getPackedRegionY());
			if (definitions == null
				|| definitions.getPlacementCount()
					!= source.getDependencyCount()) {
				return false;
			}
			for (int index = 0; index < source.getDependencyCount(); index++) {
				PlacementDependency dependency =
					source.getDependencies().get(index);
				LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
					placement = definitions.getPlacements().get(index);
				if (dependency.getSourceOrdinal()
						!= placement.getSourceOrdinal()
					|| dependency.getKind() != placement.getKind()) {
					return false;
				}
			}
		}
		return true;
	}

	public enum DependencyKind {
		OBJECT_FOOTPRINT,
		NPC_ROAMING,
		ANCHOR_ONLY
	}

	/** One immutable ordered dependency set for an authored packed source. */
	public static final class PackedSourceDependencies {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<PlacementDependency> dependencies;
		private final int crossSourceDependencyCount;
		private final int affectedSourceReferenceCount;
		private final int maximumAffectedSourceCount;

		private PackedSourceDependencies(final MutableSource source) {
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.dependencies = Collections.unmodifiableList(
				new ArrayList<PlacementDependency>(source.dependencies));
			this.crossSourceDependencyCount =
				source.crossSourceDependencyCount;
			this.affectedSourceReferenceCount =
				source.affectedSourceReferenceCount;
			this.maximumAffectedSourceCount =
				source.maximumAffectedSourceCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public List<PlacementDependency> getDependencies() {
			return dependencies;
		}
		public int getDependencyCount() { return dependencies.size(); }
		public int getCrossSourceDependencyCount() {
			return crossSourceDependencyCount;
		}
		public int getAffectedSourceReferenceCount() {
			return affectedSourceReferenceCount;
		}
		public int getMaximumAffectedSourceCount() {
			return maximumAffectedSourceCount;
		}

		public PlacementDependency findDependency(final int sourceOrdinal) {
			if (sourceOrdinal <= 0 || sourceOrdinal > dependencies.size()) {
				return null;
			}
			return dependencies.get(sourceOrdinal - 1);
		}
	}

	/** One placement's conservative tile and packed-source reach. */
	public static final class PlacementDependency {
		private final int sourceOrdinal;
		private final ConstructionKind kind;
		private final DependencyKind dependencyKind;
		private final int minimumPackedX;
		private final int maximumPackedX;
		private final int minimumPackedY;
		private final int maximumPackedY;
		private final int minimumPackedRegionX;
		private final int maximumPackedRegionX;
		private final int minimumPackedRegionY;
		private final int maximumPackedRegionY;
		private final int affectedSourceCount;
		private final boolean crossSource;

		private PlacementDependency(
			final int sourceOrdinal,
			final ConstructionKind kind,
			final DependencyKind dependencyKind,
			final int minimumPackedX,
			final int maximumPackedX,
			final int minimumPackedY,
			final int maximumPackedY,
			final int minimumPackedRegionX,
			final int maximumPackedRegionX,
			final int minimumPackedRegionY,
			final int maximumPackedRegionY,
			final int affectedSourceCount,
			final boolean crossSource) {
			this.sourceOrdinal = sourceOrdinal;
			this.kind = kind;
			this.dependencyKind = dependencyKind;
			this.minimumPackedX = minimumPackedX;
			this.maximumPackedX = maximumPackedX;
			this.minimumPackedY = minimumPackedY;
			this.maximumPackedY = maximumPackedY;
			this.minimumPackedRegionX = minimumPackedRegionX;
			this.maximumPackedRegionX = maximumPackedRegionX;
			this.minimumPackedRegionY = minimumPackedRegionY;
			this.maximumPackedRegionY = maximumPackedRegionY;
			this.affectedSourceCount = affectedSourceCount;
			this.crossSource = crossSource;
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public ConstructionKind getKind() { return kind; }
		public DependencyKind getDependencyKind() { return dependencyKind; }
		public int getMinimumPackedX() { return minimumPackedX; }
		public int getMaximumPackedX() { return maximumPackedX; }
		public int getMinimumPackedY() { return minimumPackedY; }
		public int getMaximumPackedY() { return maximumPackedY; }
		public int getMinimumPackedRegionX() {
			return minimumPackedRegionX;
		}
		public int getMaximumPackedRegionX() {
			return maximumPackedRegionX;
		}
		public int getMinimumPackedRegionY() {
			return minimumPackedRegionY;
		}
		public int getMaximumPackedRegionY() {
			return maximumPackedRegionY;
		}
		public int getAffectedSourceCount() { return affectedSourceCount; }
		public boolean isCrossSource() { return crossSource; }
	}

	/** Startup-only accumulator. A completed builder cannot be reused. */
	public static final class Builder {
		private final long generation;
		private final Map<Long, MutableSource> sources =
			new LinkedHashMap<Long, MutableSource>();
		private int dependencyCount;
		private boolean built;

		private Builder(final long generation) {
			if (generation <= 0L) {
				throw new IllegalArgumentException(
					"Dependency inventory generation must be positive");
			}
			this.generation = generation;
		}

		public Builder record(
			final ConstructionKind kind,
			final DependencyKind dependencyKind,
			final int sourcePackedRegionX,
			final int sourcePackedRegionY,
			final int minimumPackedX,
			final int maximumPackedX,
			final int minimumPackedY,
			final int maximumPackedY,
			final int minimumPackedRegionX,
			final int maximumPackedRegionX,
			final int minimumPackedRegionY,
			final int maximumPackedRegionY) {
			checkOpen();
			if (kind == null) {
				throw new NullPointerException("kind");
			}
			if (dependencyKind == null) {
				throw new NullPointerException("dependencyKind");
			}
			if (dependencyKind != dependencyKindFor(kind)) {
				throw new IllegalArgumentException(
					"Dependency kind does not match construction family");
			}
			if (sourcePackedRegionX < 0 || sourcePackedRegionY < 0
				|| minimumPackedX < 0 || minimumPackedY < 0
				|| minimumPackedRegionX < 0 || minimumPackedRegionY < 0
				|| maximumPackedX < minimumPackedX
				|| maximumPackedY < minimumPackedY
				|| maximumPackedRegionX < minimumPackedRegionX
				|| maximumPackedRegionY < minimumPackedRegionY
				|| sourcePackedRegionX < minimumPackedRegionX
				|| sourcePackedRegionX > maximumPackedRegionX
				|| sourcePackedRegionY < minimumPackedRegionY
				|| sourcePackedRegionY > maximumPackedRegionY) {
				throw new IllegalArgumentException(
					"Dependency bounds must be ordered and contain the source");
			}
			if (dependencyCount >= MAXIMUM_AUTHORED_PLACEMENTS) {
				throw new IllegalArgumentException(
					"Dependency inventory exceeds its placement budget");
			}
			long width = (long) maximumPackedRegionX
				- minimumPackedRegionX + 1L;
			long height = (long) maximumPackedRegionY
				- minimumPackedRegionY + 1L;
			long affected = Math.multiplyExact(width, height);
			if (affected > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(
					"Dependency source reach exceeds the integer budget");
			}
			long key = packedSourceKey(
				sourcePackedRegionX, sourcePackedRegionY);
			MutableSource source = sources.get(Long.valueOf(key));
			if (source == null) {
				if (sources.size() >= MAXIMUM_PACKED_SOURCES) {
					throw new IllegalArgumentException(
						"Dependency inventory exceeds its source budget");
				}
				source = new MutableSource(
					sourcePackedRegionX, sourcePackedRegionY);
				sources.put(Long.valueOf(key), source);
			}
			int sourceOrdinal = source.dependencies.size() + 1;
			boolean crossSource = minimumPackedRegionX
					!= sourcePackedRegionX
				|| maximumPackedRegionX != sourcePackedRegionX
				|| minimumPackedRegionY != sourcePackedRegionY
				|| maximumPackedRegionY != sourcePackedRegionY;
			PlacementDependency dependency = new PlacementDependency(
				sourceOrdinal, kind, dependencyKind,
				minimumPackedX, maximumPackedX,
				minimumPackedY, maximumPackedY,
				minimumPackedRegionX, maximumPackedRegionX,
				minimumPackedRegionY, maximumPackedRegionY,
				(int) affected, crossSource);
			source.dependencies.add(dependency);
			if (crossSource) {
				source.crossSourceDependencyCount = Math.incrementExact(
					source.crossSourceDependencyCount);
			}
			source.affectedSourceReferenceCount = Math.addExact(
				source.affectedSourceReferenceCount, (int) affected);
			source.maximumAffectedSourceCount = Math.max(
				source.maximumAffectedSourceCount, (int) affected);
			dependencyCount = Math.incrementExact(dependencyCount);
			return this;
		}

		public LayeredPackedRegionAuthoredPlacementDependencyInventory
			build() {
			checkOpen();
			built = true;
			List<MutableSource> ordered =
				new ArrayList<MutableSource>(sources.values());
			Collections.sort(ordered, new Comparator<MutableSource>() {
				@Override
				public int compare(
					final MutableSource left,
					final MutableSource right) {
					int x = Integer.compare(
						left.packedRegionX, right.packedRegionX);
					return x != 0 ? x : Integer.compare(
						left.packedRegionY, right.packedRegionY);
				}
			});
			List<PackedSourceDependencies> immutable =
				new ArrayList<PackedSourceDependencies>(ordered.size());
			for (MutableSource source : ordered) {
				immutable.add(new PackedSourceDependencies(source));
			}
			return new
				LayeredPackedRegionAuthoredPlacementDependencyInventory(
					generation, immutable);
		}

		private void checkOpen() {
			if (built) {
				throw new IllegalStateException(
					"Dependency inventory builder is already complete");
			}
		}
	}

	private static DependencyKind dependencyKindFor(
		final ConstructionKind kind) {
		switch (kind) {
			case SCENERY:
			case BOUNDARY:
			case HARVESTING_SCENERY:
				return DependencyKind.OBJECT_FOOTPRINT;
			case NPC_SPAWN:
				return DependencyKind.NPC_ROAMING;
			case GROUND_ITEM_SPAWN:
				return DependencyKind.ANCHOR_ONLY;
			default:
				throw new IllegalArgumentException(
					"Unsupported authored construction kind: " + kind);
		}
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<PlacementDependency> dependencies =
			new ArrayList<PlacementDependency>();
		private int crossSourceDependencyCount;
		private int affectedSourceReferenceCount;
		private int maximumAffectedSourceCount;

		private MutableSource(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}
	}
}
