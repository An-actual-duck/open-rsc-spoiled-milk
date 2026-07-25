package com.openrsc.server.model.world.coordinate;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable package/generation-qualified identity retained across one native
 * layered GameObject's runtime replacements and delayed restoration.
 */
public final class NativeLayeredGameObjectIdentity {
	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

	private final String packageId;
	private final long generation;
	private final String placementId;
	private final String kind;
	private final WorldLocation location;

	public NativeLayeredGameObjectIdentity(
		final String packageId,
		final long generation,
		final String placementId,
		final String kind,
		final WorldLocation location) {
		if (!ID.matcher(Objects.requireNonNull(
				packageId, "packageId")).matches()
			|| !ID.matcher(Objects.requireNonNull(
				placementId, "placementId")).matches()) {
			throw new IllegalArgumentException(
				"Native layered object identity contains an invalid ID");
		}
		if (generation <= 0L) {
			throw new IllegalArgumentException(
				"Native layered object generation must be positive");
		}
		if (!"scenery".equals(kind) && !"boundary".equals(kind)) {
			throw new IllegalArgumentException(
				"Native layered object kind must be scenery or boundary");
		}
		this.packageId = packageId;
		this.generation = generation;
		this.placementId = placementId;
		this.kind = kind;
		this.location = Objects.requireNonNull(location, "location");
	}

	public String getPackageId() {
		return packageId;
	}

	public long getGeneration() {
		return generation;
	}

	public String getPlacementId() {
		return placementId;
	}

	public String getKind() {
		return kind;
	}

	public WorldLocation getLocation() {
		return location;
	}

	@Override
	public boolean equals(final Object value) {
		if (this == value) {
			return true;
		}
		if (!(value instanceof NativeLayeredGameObjectIdentity)) {
			return false;
		}
		NativeLayeredGameObjectIdentity other =
			(NativeLayeredGameObjectIdentity) value;
		return generation == other.generation
			&& packageId.equals(other.packageId)
			&& placementId.equals(other.placementId)
			&& kind.equals(other.kind)
			&& location.equals(other.location);
	}

	@Override
	public int hashCode() {
		int result = packageId.hashCode();
		result = 31 * result + (int) (generation ^ (generation >>> 32));
		result = 31 * result + placementId.hashCode();
		result = 31 * result + kind.hashCode();
		result = 31 * result + location.hashCode();
		return result;
	}
}
