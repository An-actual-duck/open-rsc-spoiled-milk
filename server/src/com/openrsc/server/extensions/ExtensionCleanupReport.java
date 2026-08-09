package com.openrsc.server.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accumulated best-effort cleanup outcome; callers must not silently discard it. */
public final class ExtensionCleanupReport {
	private final List<ExtensionCleanupFailure> failures = new ArrayList<ExtensionCleanupFailure>();

	void record(final String extensionId, final String phase, final Throwable failure) {
		failures.add(new ExtensionCleanupFailure(extensionId, phase, failure));
	}

	public boolean isSuccessful() { return failures.isEmpty(); }
	public List<ExtensionCleanupFailure> getFailures() {
		return Collections.unmodifiableList(new ArrayList<ExtensionCleanupFailure>(failures));
	}
}
