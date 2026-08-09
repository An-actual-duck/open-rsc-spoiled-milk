package com.openrsc.server.extensions;

/** Explicit result for an attempted runtime reload. */
public final class ExtensionReloadResult {
	private final boolean reloaded;
	private final String detail;

	private ExtensionReloadResult(final boolean reloaded, final String detail) {
		this.reloaded = reloaded;
		this.detail = detail;
	}

	public static ExtensionReloadResult reloaded() { return new ExtensionReloadResult(true, "reloaded"); }
	public static ExtensionReloadResult restartRequired(final String detail) {
		return new ExtensionReloadResult(false, detail);
	}
	public boolean isReloaded() { return reloaded; }
	public String getDetail() { return detail; }
}
