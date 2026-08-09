package com.openrsc.server.extensions;

import java.util.Objects;

/** Immutable health snapshot; details must be bounded, non-secret operator text. */
public final class ExtensionHealthReceipt {
	private final String extensionId;
	private final ExtensionHealth health;
	private final String detail;

	public ExtensionHealthReceipt(final String extensionId, final ExtensionHealth health,
			final String detail) {
		this.extensionId = Objects.requireNonNull(extensionId, "extensionId");
		this.health = Objects.requireNonNull(health, "health");
		this.detail = detail == null ? "" : detail.substring(0, Math.min(256, detail.length()));
	}

	public String getExtensionId() { return extensionId; }
	public ExtensionHealth getHealth() { return health; }
	public String getDetail() { return detail; }
}
