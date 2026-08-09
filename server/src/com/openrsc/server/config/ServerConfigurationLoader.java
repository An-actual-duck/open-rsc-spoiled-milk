package com.openrsc.server.config;

import com.openrsc.server.ServerConfiguration;
import java.io.IOException;

/**
 * Side-effect-free configuration phase of Server R2 bootstrap.
 *
 * <p>It intentionally creates no Server, database, scheduler, plugin, service
 * thread, socket, or listener. Validation happens here so a later composition
 * failure cannot be mistaken for a partially running server.</p>
 */
public final class ServerConfigurationLoader {
	private ServerConfigurationLoader() { }

	public static ServerConfigurationLoadResult load(final String configFile)
			throws IOException {
		final ServerConfiguration configuration = new ServerConfiguration();
		try {
			configuration.initConfig(configFile);
			final ProcessNetworkConfiguration process =
				configuration.processNetworkConfiguration();
			final DiagnosticsConfiguration diagnostics =
				configuration.diagnosticsConfiguration();
			process.requireValid();
			diagnostics.requireValid();
			return new ServerConfigurationLoadResult(configuration, process, diagnostics);
		} catch (ConfigurationValidationException exception) {
			throw new ConfigurationLoadException("configuration validation failed", exception);
		} catch (IllegalArgumentException exception) {
			throw new ConfigurationLoadException("configuration parsing failed", exception);
		}
	}
}
