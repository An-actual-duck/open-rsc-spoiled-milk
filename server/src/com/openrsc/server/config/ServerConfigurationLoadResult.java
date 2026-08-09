package com.openrsc.server.config;

import com.openrsc.server.ServerConfiguration;
import java.util.Objects;

/** Immutable successful configuration-load boundary for bootstrap consumers. */
public final class ServerConfigurationLoadResult {
	private final ServerConfiguration legacyConfiguration;
	private final ProcessNetworkConfiguration processNetwork;
	private final DiagnosticsConfiguration diagnostics;

	public ServerConfigurationLoadResult(final ServerConfiguration legacyConfiguration,
			final ProcessNetworkConfiguration processNetwork,
			final DiagnosticsConfiguration diagnostics) {
		this.legacyConfiguration = Objects.requireNonNull(legacyConfiguration, "legacyConfiguration");
		this.processNetwork = Objects.requireNonNull(processNetwork, "processNetwork");
		this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
	}

	/** Compatibility facade retained for existing mutable runtime-admin paths. */
	public ServerConfiguration getLegacyConfiguration() { return legacyConfiguration; }
	public ProcessNetworkConfiguration getProcessNetwork() { return processNetwork; }
	public DiagnosticsConfiguration getDiagnostics() { return diagnostics; }
}
