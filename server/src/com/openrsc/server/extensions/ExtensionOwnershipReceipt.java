package com.openrsc.server.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owned registration resources, released in reverse registration order. */
public final class ExtensionOwnershipReceipt {
	private final String extensionId;
	private final Map<String, ExtensionCleanup> resources = new LinkedHashMap<String, ExtensionCleanup>();

	ExtensionOwnershipReceipt(final String extensionId) {
		this.extensionId = extensionId;
	}

	public synchronized void register(final String resourceId, final ExtensionCleanup cleanup) {
		if (resourceId == null || resourceId.trim().isEmpty()) {
			throw new IllegalArgumentException("resource id must not be blank");
		}
		if (cleanup == null) {
			throw new IllegalArgumentException("resource cleanup must not be null");
		}
		if (resources.put(resourceId.trim(), cleanup) != null) {
			throw new IllegalArgumentException("duplicate extension resource: " + resourceId);
		}
	}

	public synchronized List<String> getResourceIds() {
		return Collections.unmodifiableList(new ArrayList<String>(resources.keySet()));
	}

	public String getExtensionId() {
		return extensionId;
	}

	void release() throws Exception {
		final List<ExtensionCleanup> cleanups;
		synchronized (this) {
			cleanups = new ArrayList<ExtensionCleanup>(resources.values());
			resources.clear();
		}
		Exception failure = null;
		for (int index = cleanups.size() - 1; index >= 0; index--) {
			try {
				cleanups.get(index).close();
			} catch (Exception exception) {
				if (failure == null) failure = exception;
				else failure.addSuppressed(exception);
			}
		}
		if (failure != null) throw failure;
	}
}
