package com.openrsc.server.extensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic package resolver with transactional activation and ownership
 * cleanup. Discovery and validation are side-effect free; every activated
 * extension receives a receipt that releases resources in reverse order.
 */
public final class ExtensionRegistry {
	private final Map<String, ServerExtension> discovered = new LinkedHashMap<String, ServerExtension>();
	private final List<ActiveExtension> active = new ArrayList<ActiveExtension>();

	public synchronized void discover(final Collection<? extends ServerExtension> extensions) {
		if (!active.isEmpty()) throw new IllegalStateException("cannot discover while active");
		if (extensions == null) throw new IllegalArgumentException("extensions must not be null");
		for (ServerExtension extension : extensions) {
			if (extension == null) throw new IllegalArgumentException("extension must not be null");
			final ExtensionDescriptor descriptor = extension.descriptor();
			if (descriptor == null) throw new IllegalArgumentException("extension descriptor must not be null");
			final String id = descriptor.getId();
			if (discovered.containsKey(id)) throw new IllegalArgumentException("duplicate extension id: " + id);
			discovered.put(id, extension);
		}
	}

	public synchronized List<ServerExtension> resolve() {
		final Map<String, String> capabilityProviders = validateCapabilities();
		final List<ServerExtension> ordered = new ArrayList<ServerExtension>();
		final Set<String> visiting = new LinkedHashSet<String>();
		final Set<String> visited = new LinkedHashSet<String>();
		final List<String> ids = new ArrayList<String>(discovered.keySet());
		Collections.sort(ids);
		for (String id : ids) visit(id, visiting, visited, ordered, capabilityProviders);
		return Collections.unmodifiableList(ordered);
	}

	private Map<String, String> validateCapabilities() {
		final Map<String, ExtensionCapability> providers = new LinkedHashMap<String, ExtensionCapability>();
		final Map<String, String> providerOwners = new LinkedHashMap<String, String>();
		for (ServerExtension extension : discovered.values()) {
			for (ExtensionCapability capability : extension.descriptor().getProvidedCapabilities().values()) {
				String owner = providerOwners.put(capability.getId(), extension.descriptor().getId());
				if (owner != null) throw new IllegalStateException("duplicate capability provider: "
					+ capability.getId() + " from " + owner + " and " + extension.descriptor().getId());
				providers.put(capability.getId(), capability);
			}
		}
		for (ServerExtension extension : discovered.values()) {
			for (ExtensionCapability requirement : extension.descriptor().getRequiredCapabilities().values()) {
				ExtensionCapability provider = providers.get(requirement.getId());
				if (provider == null) throw new IllegalStateException("missing extension capability: "
					+ requirement + " required by " + extension.descriptor().getId());
				if (!provider.satisfies(requirement)) throw new IllegalStateException("incompatible extension capability: "
					+ provider + " does not satisfy " + requirement + " required by " + extension.descriptor().getId());
			}
		}
		return providerOwners;
	}

	private void visit(final String id, final Set<String> visiting, final Set<String> visited,
			final List<ServerExtension> ordered, final Map<String, String> capabilityProviders) {
		if (visited.contains(id)) return;
		if (!visiting.add(id)) throw new IllegalStateException("extension dependency cycle: " + visiting);
		final ServerExtension extension = discovered.get(id);
		if (extension == null) throw new IllegalStateException("missing extension dependency: " + id);
		final List<String> dependencies = new ArrayList<String>(extension.descriptor().getDependencies());
		Collections.sort(dependencies);
		for (String dependency : dependencies) visit(dependency, visiting, visited, ordered, capabilityProviders);
		final List<String> capabilityIds = new ArrayList<String>(extension.descriptor()
			.getRequiredCapabilities().keySet());
		Collections.sort(capabilityIds);
		for (String capabilityId : capabilityIds) {
			String providerId = capabilityProviders.get(capabilityId);
			if (!id.equals(providerId)) visit(providerId, visiting, visited, ordered, capabilityProviders);
		}
		visiting.remove(id);
		visited.add(id);
		ordered.add(extension);
	}

	/** Activates all resolved packages or releases every resource acquired by the failed attempt. */
	public synchronized void activate(final ExtensionContext rootContext) throws Exception {
		if (!active.isEmpty()) throw new IllegalStateException("extensions already active");
		final List<ServerExtension> ordered = resolve();
		for (ServerExtension extension : ordered) {
			final ExtensionContext context = rootContext.forExtension(extension.descriptor());
			try {
				extension.activate(context);
				active.add(new ActiveExtension(extension, context.ownershipReceipt()));
			} catch (Throwable failure) {
				cleanupFailedActivation(extension, context.ownershipReceipt(), failure);
				deactivateInternal(failure);
				rethrow(failure);
			}
		}
	}

	private static void rethrow(final Throwable failure) throws Exception {
		if (failure instanceof Exception) throw (Exception) failure;
		if (failure instanceof Error) throw (Error) failure;
		throw new RuntimeException(failure);
	}

	private void cleanupFailedActivation(final ServerExtension extension,
			final ExtensionOwnershipReceipt receipt, final Throwable failure) {
		try {
			extension.deactivate();
		} catch (Throwable cleanupFailure) {
			failure.addSuppressed(cleanupFailure);
		}
		ExtensionCleanupReport report = new ExtensionCleanupReport();
		release(extension.descriptor().getId(), receipt, report);
		for (ExtensionCleanupFailure cleanupFailure : report.getFailures()) {
			failure.addSuppressed(new IllegalStateException(cleanupFailure.getExtensionId()
				+ " cleanup failed during " + cleanupFailure.getPhase() + ": "
				+ cleanupFailure.getExceptionType()));
		}
	}

	/** Deactivation continues through all packages, then reports the first cleanup failure. */
	public synchronized ExtensionCleanupReport deactivate() {
		return deactivateInternal(null);
	}

	private ExtensionCleanupReport deactivateInternal(final Throwable activationFailure) {
		ExtensionCleanupReport report = new ExtensionCleanupReport();
		for (int index = active.size() - 1; index >= 0; index--) {
			ActiveExtension extension = active.get(index);
			try {
				extension.extension.deactivate();
			} catch (Throwable failure) {
				report.record(extension.extension.descriptor().getId(), "deactivate", failure);
			}
			release(extension.extension.descriptor().getId(), extension.ownershipReceipt, report);
		}
		active.clear();
		if (activationFailure != null) {
			for (ExtensionCleanupFailure failure : report.getFailures()) {
				activationFailure.addSuppressed(new IllegalStateException(failure.getExtensionId()
					+ " cleanup failed during " + failure.getPhase() + ": " + failure.getExceptionType()));
			}
		}
		return report;
	}

	private static void release(final String extensionId, final ExtensionOwnershipReceipt receipt,
			final ExtensionCleanupReport report) {
		try {
			receipt.release();
		} catch (Throwable failure) {
			report.record(extensionId, "owned-resource", failure);
		}
	}

	/** Reloads only packages that declared a truthful hot-reload lifecycle. */
	public synchronized ExtensionReloadResult reload(final ExtensionContext context) throws Exception {
		for (ActiveExtension extension : active) {
			if (extension.extension.descriptor().getReloadPolicy() == ExtensionReloadPolicy.RESTART_REQUIRED) {
				return ExtensionReloadResult.restartRequired("restart required by "
					+ extension.extension.descriptor().getId());
			}
		}
		ExtensionCleanupReport cleanup = deactivate();
		if (!cleanup.isSuccessful()) {
			return ExtensionReloadResult.cleanupFailed("reload stopped after "
				+ cleanup.getFailures().size() + " cleanup failure(s)");
		}
		try {
			activate(context);
			return ExtensionReloadResult.reloaded();
		} catch (Throwable failure) {
			return ExtensionReloadResult.failed("reload activation failed: "
				+ failure.getClass().getSimpleName());
		}
	}

	/** Clears discovery only after all active extensions have been deactivated. */
	public synchronized void reset() {
		if (!active.isEmpty()) throw new IllegalStateException("cannot reset active extensions");
		discovered.clear();
	}

	public synchronized List<String> activeIds() {
		final List<String> ids = new ArrayList<String>();
		for (ActiveExtension extension : active) ids.add(extension.extension.descriptor().getId());
		return Collections.unmodifiableList(ids);
	}

	public synchronized List<ExtensionOwnershipReceipt> ownershipReceipts() {
		final List<ExtensionOwnershipReceipt> receipts = new ArrayList<ExtensionOwnershipReceipt>();
		for (ActiveExtension extension : active) receipts.add(extension.ownershipReceipt);
		return Collections.unmodifiableList(receipts);
	}

	public synchronized List<ExtensionHealthReceipt> healthReceipts() {
		final List<ExtensionHealthReceipt> receipts = new ArrayList<ExtensionHealthReceipt>();
		for (ActiveExtension extension : active) {
			try {
				receipts.add(new ExtensionHealthReceipt(extension.extension.descriptor().getId(),
					extension.extension.health(), ""));
			} catch (Throwable failure) {
				receipts.add(new ExtensionHealthReceipt(extension.extension.descriptor().getId(),
					ExtensionHealth.FAILED, failure.getClass().getSimpleName()));
			}
		}
		return Collections.unmodifiableList(receipts);
	}

	private static final class ActiveExtension {
		private final ServerExtension extension;
		private final ExtensionOwnershipReceipt ownershipReceipt;
		private ActiveExtension(final ServerExtension extension, final ExtensionOwnershipReceipt ownershipReceipt) {
			this.extension = extension;
			this.ownershipReceipt = ownershipReceipt;
		}
	}
}
