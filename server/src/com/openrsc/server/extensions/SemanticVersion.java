package com.openrsc.server.extensions;

/** Minimal immutable semantic version used for extension compatibility gates. */
public final class SemanticVersion implements Comparable<SemanticVersion> {
	private final int major;
	private final int minor;
	private final int patch;

	private SemanticVersion(final int major, final int minor, final int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	public static SemanticVersion parse(final String value) {
		if (value == null || !value.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
			throw new IllegalArgumentException("version must use major.minor.patch: " + value);
		}
		String[] parts = value.split("\\.");
		try {
			return new SemanticVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2]));
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("invalid version: " + value, exception);
		}
	}

	public boolean isCompatibleWith(final SemanticVersion requirement) {
		return major == requirement.major && compareTo(requirement) >= 0;
	}

	@Override
	public int compareTo(final SemanticVersion other) {
		if (major != other.major) return major < other.major ? -1 : 1;
		if (minor != other.minor) return minor < other.minor ? -1 : 1;
		if (patch != other.patch) return patch < other.patch ? -1 : 1;
		return 0;
	}

	@Override
	public boolean equals(final Object other) {
		if (!(other instanceof SemanticVersion)) return false;
		SemanticVersion version = (SemanticVersion) other;
		return major == version.major && minor == version.minor && patch == version.patch;
	}

	@Override
	public int hashCode() {
		return ((major * 31) + minor) * 31 + patch;
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + patch;
	}
}
