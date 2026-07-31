package com.openrsc.server.model.world.coordinate;

import java.util.Objects;
import java.util.function.Function;

/**
 * Narrow fail-safe for an unusable persisted Player login destination.
 *
 * <p>Only absent terrain and the authored void overlay are rejected. Collision,
 * walls, roofs, and surrounding accessibility are deliberately outside this
 * policy so a valid enclosed or blocked gameplay tile is not mistaken for a
 * broken login location.</p>
 */
public final class LayeredPlayerLoginRecovery {
	public static final int EXPLICIT_VOID_OVERLAY = 8;
	public static final String RECOVERY_ORIGIN =
		"unusable-terrain-respawn-v1";

	private static final String RETAINED = "retained";
	private static final String MISSING_LOCATION = "missing-location";
	private static final String MISSING_TERRAIN = "missing-terrain";
	private static final String EXPLICIT_VOID = "explicit-void-overlay";

	private LayeredPlayerLoginRecovery() {
	}

	/**
	 * Retains a usable restored location or replaces it with the validated,
	 * explicitly layered configured respawn.
	 */
	public static Decision resolve(
		final WorldLocation restoredLocation,
		final String restoredOrigin,
		final WorldLocation configuredRespawn,
		final Function<WorldLocation, Integer> terrainOverlayLookup) {
		Objects.requireNonNull(restoredOrigin, "restoredOrigin");
		WorldLocation checkedRespawn = Objects.requireNonNull(
			configuredRespawn, "configuredRespawn");
		Function<WorldLocation, Integer> checkedLookup =
			Objects.requireNonNull(
				terrainOverlayLookup, "terrainOverlayLookup");

		String respawnProblem = unusableReason(
			checkedRespawn, checkedLookup);
		if (respawnProblem != null) {
			throw new IllegalStateException(
				"Configured Player respawn terrain is unusable: "
					+ respawnProblem);
		}

		String restoredProblem = unusableReason(
			restoredLocation, checkedLookup);
		if (restoredProblem == null) {
			return new Decision(
				restoredLocation, restoredOrigin, false, RETAINED);
		}
		return new Decision(
			checkedRespawn,
			RECOVERY_ORIGIN,
			true,
			restoredProblem);
	}

	private static String unusableReason(
		final WorldLocation location,
		final Function<WorldLocation, Integer> terrainOverlayLookup) {
		if (location == null) {
			return MISSING_LOCATION;
		}
		Integer overlay = terrainOverlayLookup.apply(location);
		if (overlay == null) {
			return MISSING_TERRAIN;
		}
		return overlay.intValue() == EXPLICIT_VOID_OVERLAY
			? EXPLICIT_VOID
			: null;
	}

	public static final class Decision {
		private final WorldLocation location;
		private final String origin;
		private final boolean recovered;
		private final String reason;

		private Decision(
			final WorldLocation location,
			final String origin,
			final boolean recovered,
			final String reason) {
			this.location = Objects.requireNonNull(location, "location");
			this.origin = Objects.requireNonNull(origin, "origin");
			this.recovered = recovered;
			this.reason = Objects.requireNonNull(reason, "reason");
		}

		public WorldLocation getLocation() {
			return location;
		}

		public String getOrigin() {
			return origin;
		}

		public boolean isRecovered() {
			return recovered;
		}

		public String getReason() {
			return reason;
		}
	}
}
