package com.openrsc.server.extensions;

import com.openrsc.server.Server;
import java.util.Objects;

/** Narrow runtime context for new extensions; direct foundation imports stay optional. */
public final class ExtensionContext {
	private final Server server;
	private final ExtensionOwnershipReceipt ownershipReceipt;
	public ExtensionContext(final Server server) {
		this(Objects.requireNonNull(server, "server"), null);
	}
	/** Headless registry fixture context; extensions requiring Server must reject it. */
	public static ExtensionContext forTesting() { return new ExtensionContext(); }
	private ExtensionContext() { this(null, null); }
	private ExtensionContext(final Server server, final ExtensionOwnershipReceipt ownershipReceipt) {
		this.server = server;
		this.ownershipReceipt = ownershipReceipt;
	}
	ExtensionContext forExtension(final ExtensionDescriptor descriptor) {
		return new ExtensionContext(server, new ExtensionOwnershipReceipt(descriptor.getId()));
	}
	/** Registers cleanup before acquiring further resources, so failed activation cannot leak it. */
	public void onDeactivate(final String resourceId, final ExtensionCleanup cleanup) {
		if (ownershipReceipt == null) throw new IllegalStateException("root context cannot own extension resources");
		ownershipReceipt.register(resourceId, cleanup);
	}
	ExtensionOwnershipReceipt ownershipReceipt() { return ownershipReceipt; }
	public Server getServer() {
		if (server == null) throw new IllegalStateException("headless extension context has no Server");
		return server;
	}
}
