package com.openrsc.server.extensions;

/** A resource cleanup callback registered through the narrow extension context. */
public interface ExtensionCleanup {
	void close() throws Exception;
}
