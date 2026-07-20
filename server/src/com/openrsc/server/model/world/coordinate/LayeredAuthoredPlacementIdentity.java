package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

/**
 * Immutable generation-fenced identity for one authored placement definition.
 *
 * <p>The stable address within one configured population generation is
 * {@code (packedRegionX, packedRegionY, sourceOrdinal, constructionKind)}.
 * Generation prevents an identity retained by an old callback or runtime
 * instance from being mistaken for a placement in a later population pass.</p>
 *
 * <p>This value has no entity, Region, event, registry, cache, lease, permit,
 * or lifecycle authority.</p>
 */
public final class LayeredAuthoredPlacementIdentity {
	private final long generation;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int sourceOrdinal;
	private final ConstructionKind constructionKind;

	public LayeredAuthoredPlacementIdentity(
		final long generation,
		final int packedRegionX,
		final int packedRegionY,
		final int sourceOrdinal,
		final ConstructionKind constructionKind) {
		if (generation <= 0L) {
			throw new IllegalArgumentException(
				"Authored placement generation must be positive");
		}
		if (packedRegionX < 0 || packedRegionY < 0) {
			throw new IllegalArgumentException(
				"Authored placement source coordinates must not be negative");
		}
		if (sourceOrdinal <= 0
			|| sourceOrdinal
				> LayeredPackedRegionAuthoredPlacementManifest
					.MAXIMUM_AUTHORED_PLACEMENTS) {
			throw new IllegalArgumentException(
				"Authored placement source ordinal is outside its budget");
		}
		if (constructionKind == null) {
			throw new NullPointerException("constructionKind");
		}
		this.generation = generation;
		this.packedRegionX = packedRegionX;
		this.packedRegionY = packedRegionY;
		this.sourceOrdinal = sourceOrdinal;
		this.constructionKind = constructionKind;
	}

	public long getGeneration() { return generation; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public int getSourceOrdinal() { return sourceOrdinal; }
	public ConstructionKind getConstructionKind() {
		return constructionKind;
	}

	@Override
	public boolean equals(final Object value) {
		if (this == value) {
			return true;
		}
		if (!(value instanceof LayeredAuthoredPlacementIdentity)) {
			return false;
		}
		LayeredAuthoredPlacementIdentity other =
			(LayeredAuthoredPlacementIdentity) value;
		return generation == other.generation
			&& packedRegionX == other.packedRegionX
			&& packedRegionY == other.packedRegionY
			&& sourceOrdinal == other.sourceOrdinal
			&& constructionKind == other.constructionKind;
	}

	@Override
	public int hashCode() {
		int result = (int) (generation ^ (generation >>> 32));
		result = 31 * result + packedRegionX;
		result = 31 * result + packedRegionY;
		result = 31 * result + sourceOrdinal;
		result = 31 * result + constructionKind.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "authored-placement:g" + generation
			+ ":r" + packedRegionX + ',' + packedRegionY
			+ ":o" + sourceOrdinal
			+ ':' + constructionKind.name();
	}
}
