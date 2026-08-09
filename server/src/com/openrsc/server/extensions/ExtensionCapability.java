package com.openrsc.server.extensions;

import java.util.Objects;

/** A versioned capability provided by, or required from, an extension. */
public final class ExtensionCapability {
	private final String id;
	private final SemanticVersion version;

	public ExtensionCapability(final String id, final String version) {
		this(id, SemanticVersion.parse(version));
	}

	public ExtensionCapability(final String id, final SemanticVersion version) {
		if (id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("capability id must not be blank");
		}
		this.id = id.trim();
		this.version = Objects.requireNonNull(version, "version");
	}

	public String getId() {
		return id;
	}

	public SemanticVersion getVersion() {
		return version;
	}

	/** A provider is compatible only within the requested semantic major line. */
	public boolean satisfies(final ExtensionCapability requirement) {
		return id.equals(requirement.id) && version.isCompatibleWith(requirement.version);
	}

	@Override
	public boolean equals(final Object other) {
		if (!(other instanceof ExtensionCapability)) {
			return false;
		}
		ExtensionCapability capability = (ExtensionCapability) other;
		return id.equals(capability.id) && version.equals(capability.version);
	}

	@Override
	public int hashCode() {
		return 31 * id.hashCode() + version.hashCode();
	}

	@Override
	public String toString() {
		return id + "@" + version;
	}
}
