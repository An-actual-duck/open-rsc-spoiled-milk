package com.openrsc.server.config;

/**
 * Configuration parsing/loading failure before any server runtime resource has
 * been constructed. The command-line launcher remains responsible for mapping
 * this boundary to its existing non-zero process exit.
 */
public final class ConfigurationLoadException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public ConfigurationLoadException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
