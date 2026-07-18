package com.openrsc.layeredmaps;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable world-space identity kept separate from geographic coordinates. */
public final class WorldSpaceId {
	private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

	public static final WorldSpaceId GLOBAL = new WorldSpaceId("global");

	private final String value;

	public WorldSpaceId(String value) {
		this.value = Objects.requireNonNull(value, "value");
		if (!VALID_ID.matcher(value).matches()) {
			throw new IllegalArgumentException(
				"World-space ID must match " + VALID_ID.pattern() + ": " + value);
		}
	}

	public String getValue() {
		return value;
	}

	@Override
	public boolean equals(Object other) {
		return this == other
			|| other instanceof WorldSpaceId && value.equals(((WorldSpaceId) other).value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value;
	}
}
