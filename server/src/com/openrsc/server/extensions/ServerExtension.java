package com.openrsc.server.extensions;

/** A declared, reversible server extension. */
public interface ServerExtension {
	ExtensionDescriptor descriptor();
	void activate(ExtensionContext context) throws Exception;
	void deactivate() throws Exception;
	/** Extensions without explicit diagnostics are healthy while their lifecycle is active. */
	default ExtensionHealth health() { return ExtensionHealth.HEALTHY; }
}
