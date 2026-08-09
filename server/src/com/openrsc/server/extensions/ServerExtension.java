package com.openrsc.server.extensions;

/** A declared, reversible server extension. */
public interface ServerExtension {
	ExtensionDescriptor descriptor();
	void activate(ExtensionContext context) throws Exception;
	void deactivate() throws Exception;
}
