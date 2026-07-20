package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable detached record of authored object identities superseded by later
 * construction during one completed whole-world population pass.
 *
 * <p>The complete placement manifest remains the ordered replay history. This
 * value only records which earlier object identity no longer belongs to the
 * final live expectation because normal collision registration installed a
 * later authored identity in the same slot. It retains no entity, Region,
 * tile, event, registry, cache, or lifecycle handle.</p>
 */
public final class LayeredPackedRegionAuthoredPopulationOutcome {
	public static final int MAXIMUM_SUPERSESSIONS =
		LayeredPackedRegionAuthoredPlacementManifest
			.MAXIMUM_AUTHORED_PLACEMENTS;

	private final long generation;
	private final int manifestPlacementCount;
	private final List<Supersession> supersessions;
	private final Map<LayeredAuthoredPlacementIdentity, Supersession>
		supersessionByPredecessor;

	private LayeredPackedRegionAuthoredPopulationOutcome(
		final long generation,
		final int manifestPlacementCount,
		final List<Supersession> supersessions) {
		this.generation = generation;
		this.manifestPlacementCount = manifestPlacementCount;
		this.supersessions = Collections.unmodifiableList(
			new ArrayList<Supersession>(supersessions));
		Map<LayeredAuthoredPlacementIdentity, Supersession> byPredecessor =
			new LinkedHashMap<LayeredAuthoredPlacementIdentity, Supersession>();
		for (Supersession supersession : supersessions) {
			byPredecessor.put(
				supersession.getPredecessorIdentity(), supersession);
		}
		this.supersessionByPredecessor = Collections.unmodifiableMap(
			byPredecessor);
	}

	public static LayeredPackedRegionAuthoredPopulationOutcome empty() {
		return new LayeredPackedRegionAuthoredPopulationOutcome(
			0L, 0, Collections.<Supersession>emptyList());
	}

	public static Builder builder(final long generation) {
		return new Builder(generation);
	}

	public long getGeneration() { return generation; }
	public int getManifestPlacementCount() { return manifestPlacementCount; }
	public int getSupersessionCount() { return supersessions.size(); }
	public int getFinalExpectedPlacementCount() {
		return manifestPlacementCount - supersessions.size();
	}
	public List<Supersession> getSupersessions() { return supersessions; }
	public boolean isSuperseded(
		final LayeredAuthoredPlacementIdentity identity) {
		return identity != null
			&& supersessionByPredecessor.containsKey(identity);
	}

	public Supersession findSupersession(
		final LayeredAuthoredPlacementIdentity predecessorIdentity) {
		if (predecessorIdentity == null
			|| predecessorIdentity.getGeneration() != generation) {
			return null;
		}
		return supersessionByPredecessor.get(predecessorIdentity);
	}

	void validateAgainst(
		final LayeredPackedRegionAuthoredPlacementManifest manifest) {
		if (manifest == null) {
			throw new NullPointerException("manifest");
		}
		if (manifest.getGeneration() != generation
			|| manifest.getPlacementCount() != manifestPlacementCount) {
			throw new IllegalArgumentException(
				"Population outcome does not align with the manifest");
		}
		for (Supersession supersession : supersessions) {
			if (!supersession.getPredecessor().matches(
					findPlacement(manifest,
						supersession.getPredecessorIdentity()))
				|| !supersession.getSuccessor().matches(
					findPlacement(manifest,
						supersession.getSuccessorIdentity()))) {
				throw new IllegalArgumentException(
					"Population outcome placement metadata differs from the manifest");
			}
		}
	}

	public enum CollisionKind {
		SCENERY_ANCHOR,
		BOUNDARY_ANCHOR_DIRECTION
	}

	/** One predecessor/successor pair reduced to immutable manifest facts. */
	public static final class Supersession {
		private final PlacementMetadata predecessor;
		private final PlacementMetadata successor;
		private final CollisionKind collisionKind;

		private Supersession(
			final PlacementMetadata predecessor,
			final PlacementMetadata successor,
			final CollisionKind collisionKind) {
			this.predecessor = predecessor;
			this.successor = successor;
			this.collisionKind = collisionKind;
		}

		public PlacementMetadata getPredecessor() { return predecessor; }
		public PlacementMetadata getSuccessor() { return successor; }
		public LayeredAuthoredPlacementIdentity getPredecessorIdentity() {
			return predecessor.getIdentity();
		}
		public LayeredAuthoredPlacementIdentity getSuccessorIdentity() {
			return successor.getIdentity();
		}
		public CollisionKind getCollisionKind() { return collisionKind; }
	}

	/** Detached primitive placement fields needed to explain one outcome. */
	public static final class PlacementMetadata {
		private final LayeredAuthoredPlacementIdentity identity;
		private final int authoredDefinitionId;
		private final int constructedEntityId;
		private final int packedX;
		private final int packedY;
		private final int direction;
		private final int objectType;

		private PlacementMetadata(
			final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
				placement) {
			this.identity = placement.getIdentity();
			this.authoredDefinitionId = placement.getAuthoredDefinitionId();
			this.constructedEntityId = placement.getConstructedEntityId();
			this.packedX = placement.getPackedX();
			this.packedY = placement.getPackedY();
			this.direction = placement.getDirection();
			this.objectType = placement.getObjectType();
		}

		public LayeredAuthoredPlacementIdentity getIdentity() { return identity; }
		public long getGeneration() { return identity.getGeneration(); }
		public int getPackedRegionX() { return identity.getPackedRegionX(); }
		public int getPackedRegionY() { return identity.getPackedRegionY(); }
		public int getSourceOrdinal() { return identity.getSourceOrdinal(); }
		public ConstructionKind getConstructionKind() {
			return identity.getConstructionKind();
		}
		public int getAuthoredDefinitionId() { return authoredDefinitionId; }
		public int getConstructedEntityId() { return constructedEntityId; }
		public int getPackedX() { return packedX; }
		public int getPackedY() { return packedY; }
		public int getDirection() { return direction; }
		public int getObjectType() { return objectType; }

		private boolean matches(
			final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
				placement) {
			return placement != null
				&& identity.equals(placement.getIdentity())
				&& authoredDefinitionId == placement.getAuthoredDefinitionId()
				&& constructedEntityId == placement.getConstructedEntityId()
				&& packedX == placement.getPackedX()
				&& packedY == placement.getPackedY()
				&& direction == placement.getDirection()
				&& objectType == placement.getObjectType();
		}
	}

	/** Construction-time identity accumulator; no runtime entity is retained. */
	public static final class Builder {
		private final long generation;
		private final Map<LayeredAuthoredPlacementIdentity,
			LayeredAuthoredPlacementIdentity> successors =
				new LinkedHashMap<LayeredAuthoredPlacementIdentity,
					LayeredAuthoredPlacementIdentity>();
		private boolean built;

		private Builder(final long generation) {
			if (generation <= 0L) {
				throw new IllegalArgumentException(
					"Population outcome generation must be positive");
			}
			this.generation = generation;
		}

		public Builder recordSupersession(
			final LayeredAuthoredPlacementIdentity predecessor,
			final LayeredAuthoredPlacementIdentity successor) {
			checkOpen();
			if (predecessor == null) {
				throw new NullPointerException("predecessor");
			}
			if (successor == null) {
				throw new NullPointerException("successor");
			}
			if (predecessor.getGeneration() != generation
				|| successor.getGeneration() != generation) {
				throw new IllegalArgumentException(
					"Supersession identity belongs to another generation");
			}
			if (predecessor.equals(successor)) {
				throw new IllegalArgumentException(
					"An identity cannot supersede itself");
			}
			if (predecessor.getPackedRegionX()
					!= successor.getPackedRegionX()
				|| predecessor.getPackedRegionY()
					!= successor.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"A collision supersession must remain in one packed source");
			}
			if (successor.getSourceOrdinal() <= predecessor.getSourceOrdinal()) {
				throw new IllegalArgumentException(
					"A collision successor must follow its predecessor");
			}
			if (successors.size() >= MAXIMUM_SUPERSESSIONS) {
				throw new IllegalArgumentException(
					"Population supersession outcome exceeds its budget");
			}
			if (successors.containsKey(predecessor)) {
				throw new IllegalArgumentException(
					"An authored predecessor was superseded more than once");
			}
			LayeredAuthoredPlacementIdentity cursor = successor;
			while (cursor != null) {
				if (predecessor.equals(cursor)) {
					throw new IllegalArgumentException(
						"Population supersession chain must not cycle");
				}
				cursor = successors.get(cursor);
			}
			successors.put(predecessor, successor);
			return this;
		}

		public LayeredPackedRegionAuthoredPopulationOutcome build(
			final LayeredPackedRegionAuthoredPlacementManifest manifest) {
			checkOpen();
			built = true;
			if (manifest == null) {
				throw new NullPointerException("manifest");
			}
			if (manifest.getGeneration() != generation) {
				throw new IllegalArgumentException(
					"Population outcome and manifest generations differ");
			}
			List<Supersession> completed =
				new ArrayList<Supersession>(successors.size());
			for (Map.Entry<LayeredAuthoredPlacementIdentity,
				LayeredAuthoredPlacementIdentity> entry : successors.entrySet()) {
				LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
					predecessor = findPlacement(manifest, entry.getKey());
				LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
					successor = findPlacement(manifest, entry.getValue());
				validateCollision(predecessor, successor);
				completed.add(new Supersession(
					new PlacementMetadata(predecessor),
					new PlacementMetadata(successor),
					collisionKind(predecessor, successor)));
			}
			Collections.sort(completed, SUPERSESSION_ORDER);
			return new LayeredPackedRegionAuthoredPopulationOutcome(
				generation, manifest.getPlacementCount(), completed);
		}

		private void checkOpen() {
			if (built) {
				throw new IllegalStateException(
					"Population outcome builder is already complete");
			}
		}
	}

	private static LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
		findPlacement(
			final LayeredPackedRegionAuthoredPlacementManifest manifest,
			final LayeredAuthoredPlacementIdentity identity) {
		LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest source =
			manifest.findSource(
				identity.getPackedRegionX(), identity.getPackedRegionY());
		LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement placement =
			source == null ? null
				: source.findPlacement(identity.getSourceOrdinal());
		if (placement == null || !placement.getIdentity().equals(identity)) {
			throw new IllegalArgumentException(
				"Supersession identity is absent from the completed manifest");
		}
		return placement;
	}

	private static void validateCollision(
		final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
			predecessor,
		final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
			successor) {
		if (!isObjectFamily(predecessor.getKind())
			|| !isObjectFamily(successor.getKind())
			|| predecessor.getPackedX() != successor.getPackedX()
			|| predecessor.getPackedY() != successor.getPackedY()) {
			throw new IllegalArgumentException(
				"Supersession placements do not share one object collision anchor");
		}
		boolean predecessorBoundary =
			predecessor.getKind() == ConstructionKind.BOUNDARY;
		boolean successorBoundary =
			successor.getKind() == ConstructionKind.BOUNDARY;
		if (predecessorBoundary != successorBoundary
			|| (predecessorBoundary
				&& predecessor.getDirection() != successor.getDirection())) {
			throw new IllegalArgumentException(
				"Supersession placements do not share one collision family");
		}
	}

	private static CollisionKind collisionKind(
		final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
			predecessor,
		final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
			successor) {
		validateCollision(predecessor, successor);
		return predecessor.getKind() == ConstructionKind.BOUNDARY
			? CollisionKind.BOUNDARY_ANCHOR_DIRECTION
			: CollisionKind.SCENERY_ANCHOR;
	}

	private static boolean isObjectFamily(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	private static final Comparator<Supersession> SUPERSESSION_ORDER =
		new Comparator<Supersession>() {
			@Override
			public int compare(
				final Supersession left,
				final Supersession right) {
				LayeredAuthoredPlacementIdentity leftIdentity =
					left.getPredecessorIdentity();
				LayeredAuthoredPlacementIdentity rightIdentity =
					right.getPredecessorIdentity();
				int comparison = Integer.compare(
					leftIdentity.getPackedRegionX(),
					rightIdentity.getPackedRegionX());
				if (comparison != 0) { return comparison; }
				comparison = Integer.compare(
					leftIdentity.getPackedRegionY(),
					rightIdentity.getPackedRegionY());
				if (comparison != 0) { return comparison; }
				comparison = Integer.compare(
					leftIdentity.getSourceOrdinal(),
					rightIdentity.getSourceOrdinal());
				if (comparison != 0) { return comparison; }
				return Integer.compare(
					leftIdentity.getConstructionKind().ordinal(),
					rightIdentity.getConstructionKind().ordinal());
			}
		};
}
