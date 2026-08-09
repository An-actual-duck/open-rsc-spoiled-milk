package com.openrsc.server.extensions;

/** Bounded diagnostic for cleanup that ran but did not complete successfully. */
public final class ExtensionCleanupFailure {
	private final String extensionId;
	private final String phase;
	private final String exceptionType;

	ExtensionCleanupFailure(final String extensionId, final String phase, final Throwable failure) {
		this.extensionId = extensionId;
		this.phase = phase;
		this.exceptionType = failure.getClass().getSimpleName();
	}

	public String getExtensionId() { return extensionId; }
	public String getPhase() { return phase; }
	public String getExceptionType() { return exceptionType; }
}
