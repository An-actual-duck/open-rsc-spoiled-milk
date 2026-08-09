package com.openrsc.server.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable extension identity, ownership receipt, and dependency declaration. */
public final class ExtensionDescriptor {
	private final String id;
	private final SemanticVersion version;
	private final String owner;
	private final Set<String> dependencies;
	private final Map<String, ExtensionCapability> providedCapabilities;
	private final Map<String, ExtensionCapability> requiredCapabilities;
	private final ExtensionReloadPolicy reloadPolicy;

	public ExtensionDescriptor(final String id, final String owner,
			final Set<String> dependencies, final Set<String> capabilities) {
		this(id, "1.0.0", owner, dependencies, fromLegacyCapabilities(capabilities),
			Collections.<ExtensionCapability>emptySet(), ExtensionReloadPolicy.RESTART_REQUIRED);
	}

	public ExtensionDescriptor(final String id, final String version, final String owner,
			final Set<String> dependencies, final Set<ExtensionCapability> providedCapabilities,
			final Set<ExtensionCapability> requiredCapabilities,
			final ExtensionReloadPolicy reloadPolicy) {
		this.id = requireToken(id, "id");
		this.version = SemanticVersion.parse(version);
		this.owner = requireToken(owner, "owner");
		this.dependencies = immutableTokens(dependencies, "dependency");
		this.providedCapabilities = immutableCapabilities(providedCapabilities, "provided capability");
		this.requiredCapabilities = immutableCapabilities(requiredCapabilities, "required capability");
		if (reloadPolicy == null) throw new IllegalArgumentException("reload policy must not be null");
		this.reloadPolicy = reloadPolicy;
	}
	private static Set<ExtensionCapability> fromLegacyCapabilities(final Set<String> capabilities) {
		final Set<ExtensionCapability> result = new LinkedHashSet<ExtensionCapability>();
		if (capabilities != null) for (String capability : capabilities) {
			result.add(new ExtensionCapability(requireToken(capability, "capability"), "1.0.0"));
		}
		return result;
	}
	private static String requireToken(final String value, final String label) {
		if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
		return value.trim();
	}
	private static Set<String> immutableTokens(final Set<String> values, final String label) {
		final Set<String> result = new LinkedHashSet<String>();
		if (values != null) for (String value : values) result.add(requireToken(value, label));
		return Collections.unmodifiableSet(result);
	}
	private static Map<String, ExtensionCapability> immutableCapabilities(
			final Set<ExtensionCapability> capabilities, final String label) {
		final Map<String, ExtensionCapability> result = new LinkedHashMap<String, ExtensionCapability>();
		if (capabilities != null) for (ExtensionCapability capability : capabilities) {
			if (capability == null) throw new IllegalArgumentException(label + " must not be null");
			if (result.put(capability.getId(), capability) != null) {
				throw new IllegalArgumentException("duplicate " + label + ": " + capability.getId());
			}
		}
		return Collections.unmodifiableMap(result);
	}
	public String getId() { return id; } public String getOwner() { return owner; }
	public SemanticVersion getVersion() { return version; }
	public Set<String> getDependencies() { return dependencies; }
	/** Compatibility view of the provided capability identities. */
	public Set<String> getCapabilities() { return Collections.unmodifiableSet(providedCapabilities.keySet()); }
	public Map<String, ExtensionCapability> getProvidedCapabilities() { return providedCapabilities; }
	public Map<String, ExtensionCapability> getRequiredCapabilities() { return requiredCapabilities; }
	public ExtensionReloadPolicy getReloadPolicy() { return reloadPolicy; }
	public List<String> ownershipReceipt() {
		final List<String> receipt = new ArrayList<String>();
		receipt.add(id); receipt.add(version.toString()); receipt.add(owner);
		for (ExtensionCapability capability : providedCapabilities.values()) receipt.add(capability.toString());
		return Collections.unmodifiableList(receipt);
	}
}
