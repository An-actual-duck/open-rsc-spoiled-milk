package com.openrsc.server.extensions;

/** Explicit result for an attempted runtime reload. */
public final class ExtensionReloadResult {
	public enum Status { RELOADED, RESTART_REQUIRED, FAILED, CLEANUP_FAILED }

	private final Status status;
	private final String detail;

	private ExtensionReloadResult(final Status status, final String detail) {
		this.status = status;
		this.detail = detail;
	}

	public static ExtensionReloadResult reloaded() { return new ExtensionReloadResult(Status.RELOADED, "reloaded"); }
	public static ExtensionReloadResult restartRequired(final String detail) {
		return new ExtensionReloadResult(Status.RESTART_REQUIRED, detail);
	}
	public static ExtensionReloadResult failed(final String detail) {
		return new ExtensionReloadResult(Status.FAILED, detail);
	}
	public static ExtensionReloadResult cleanupFailed(final String detail) {
		return new ExtensionReloadResult(Status.CLEANUP_FAILED, detail);
	}
	public boolean isReloaded() { return status == Status.RELOADED; }
	public boolean isFailed() { return status == Status.FAILED || status == Status.CLEANUP_FAILED; }
	public Status getStatus() { return status; }
	public String getDetail() { return detail; }
}
