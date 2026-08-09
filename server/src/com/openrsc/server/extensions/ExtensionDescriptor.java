package com.openrsc.server.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable extension identity, ownership receipt, and dependency declaration. */
public final class ExtensionDescriptor {
	private final String id;
	private final String owner;
	private final Set<String> dependencies;
	private final Set<String> capabilities;

	public ExtensionDescriptor(final String id, final String owner,
			final Set<String> dependencies, final Set<String> capabilities) {
		this.id = requireToken(id, "id"); this.owner = requireToken(owner, "owner");
		this.dependencies = immutableTokens(dependencies, "dependency");
		this.capabilities = immutableTokens(capabilities, "capability");
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
	public String getId() { return id; } public String getOwner() { return owner; }
	public Set<String> getDependencies() { return dependencies; }
	public Set<String> getCapabilities() { return capabilities; }
	public List<String> ownershipReceipt() {
		final List<String> receipt = new ArrayList<String>();
		receipt.add(id); receipt.add(owner); receipt.addAll(capabilities); return Collections.unmodifiableList(receipt);
	}
}
