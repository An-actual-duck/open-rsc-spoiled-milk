package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Explicit, immutable spatial-affinity declaration for a scheduled event.
 * Legacy events default to {@link Scope#UNSPECIFIED}; a null owner must never
 * be interpreted as proof that an event is non-spatial.
 */
public final class GameTickEventSpatialAffinity {
	private static final GameTickEventSpatialAffinity UNSPECIFIED =
		new GameTickEventSpatialAffinity(
			Scope.UNSPECIFIED, Collections.<Reference>emptyList());
	private static final GameTickEventSpatialAffinity NON_SPATIAL_GLOBAL =
		new GameTickEventSpatialAffinity(
			Scope.NON_SPATIAL_GLOBAL, Collections.<Reference>emptyList());

	private final Scope scope;
	private final List<Reference> references;

	private GameTickEventSpatialAffinity(
		final Scope scope,
		final List<Reference> references) {
		this.scope = Objects.requireNonNull(scope, "scope");
		Objects.requireNonNull(references, "references");
		List<Reference> copied = new ArrayList<Reference>(references.size());
		for (int index = 0; index < references.size(); index++) {
			copied.add(Objects.requireNonNull(
				references.get(index), "references[" + index + "]"));
		}
		if (scope == Scope.EXACT_SPATIAL && copied.isEmpty()) {
			throw new IllegalArgumentException(
				"Exact event affinity requires a spatial reference");
		}
		if (scope != Scope.EXACT_SPATIAL && !copied.isEmpty()) {
			throw new IllegalArgumentException(
				"Only exact event affinity may contain spatial references");
		}
		this.references = Collections.unmodifiableList(copied);
	}

	public static GameTickEventSpatialAffinity unspecified() {
		return UNSPECIFIED;
	}

	public static GameTickEventSpatialAffinity nonSpatialGlobal() {
		return NON_SPATIAL_GLOBAL;
	}

	public static GameTickEventSpatialAffinity exact(
		final List<Reference> references) {
		return new GameTickEventSpatialAffinity(Scope.EXACT_SPATIAL, references);
	}

	public static GameTickEventSpatialAffinity exactFixedLocation(
		final int x,
		final int y) {
		return exact(Collections.singletonList(
			Reference.of(Role.FIXED_EFFECT_LOCATION, x, y)));
	}

	public Scope getScope() { return scope; }
	public List<Reference> getReferences() { return references; }

	public enum Scope {
		UNSPECIFIED,
		EXACT_SPATIAL,
		NON_SPATIAL_GLOBAL
	}

	public enum Role {
		OWNER_CURRENT_POSITION,
		SUBJECT_CURRENT_POSITION,
		TARGET_CURRENT_POSITION,
		FIXED_EFFECT_LOCATION
	}

	public static final class Reference {
		private final Role role;
		private final int x;
		private final int y;

		private Reference(
			final Role role,
			final int x,
			final int y) {
			this.role = Objects.requireNonNull(role, "role");
			if (x < 0 || y < 0) {
				throw new IllegalArgumentException(
					"Event affinity coordinates must be non-negative");
			}
			this.x = x;
			this.y = y;
		}

		public static Reference of(
			final Role role,
			final int x,
			final int y) {
			return new Reference(role, x, y);
		}

		public Role getRole() { return role; }
		public int getX() { return x; }
		public int getY() { return y; }
	}
}
