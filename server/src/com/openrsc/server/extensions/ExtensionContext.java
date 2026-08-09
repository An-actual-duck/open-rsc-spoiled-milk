package com.openrsc.server.extensions;

/**
 * Narrow runtime context for new extensions. It intentionally exposes no
 * Server, World, executor, persistence, or transport object; new packages can
 * only record owned cleanup until subsequent R2 service contracts are added.
 */
public final class ExtensionContext {
	private final ExtensionOwnershipReceipt ownershipReceipt;
	private ExtensionContext() { this(null); }
	private ExtensionContext(final ExtensionOwnershipReceipt ownershipReceipt) { this.ownershipReceipt = ownershipReceipt; }
	/** Creates a composition-root context without leaking foundation authority. */
	public static ExtensionContext create() { return new ExtensionContext(); }
	/** Compatibility alias for compiled fixtures. */
	public static ExtensionContext forTesting() { return create(); }
	ExtensionContext forExtension(final ExtensionDescriptor descriptor) {
		return new ExtensionContext(new ExtensionOwnershipReceipt(descriptor.getId()));
	}
	/** Registers cleanup before acquiring further resources, so failed activation cannot leak it. */
	public void onDeactivate(final String resourceId, final ExtensionCleanup cleanup) {
		if (ownershipReceipt == null) throw new IllegalStateException("root context cannot own extension resources");
		ownershipReceipt.register(resourceId, cleanup);
	}
	ExtensionOwnershipReceipt ownershipReceipt() { return ownershipReceipt; }
}
