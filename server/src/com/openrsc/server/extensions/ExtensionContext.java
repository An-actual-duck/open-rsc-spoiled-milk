package com.openrsc.server.extensions;

import com.openrsc.server.Server;
import java.util.Objects;

/** Narrow runtime context for new extensions; direct foundation imports stay optional. */
public final class ExtensionContext {
	private final Server server;
	public ExtensionContext(final Server server) { this.server = Objects.requireNonNull(server, "server"); }
	/** Headless registry fixture context; extensions requiring Server must reject it. */
	public static ExtensionContext forTesting() { return new ExtensionContext(); }
	private ExtensionContext() { this.server = null; }
	public Server getServer() {
		if (server == null) throw new IllegalStateException("headless extension context has no Server");
		return server;
	}
}
