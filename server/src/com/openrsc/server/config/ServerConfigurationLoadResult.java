package com.openrsc.server.config;

import com.openrsc.server.ServerConfiguration;
import java.util.Objects;

/** Immutable successful configuration-load boundary for bootstrap consumers. */
public final class ServerConfigurationLoadResult {
	private final ServerConfiguration legacyConfiguration;
	private final ProcessNetworkConfiguration processNetwork;
	private final DiagnosticsConfiguration diagnostics;
	private final PersistenceConfiguration persistence;
	private final WorldRuntimeConfiguration worldRuntime;
	private final CompatibilityConfiguration compatibility;
	private final ToolsConfiguration tools;
	private final ContentConfiguration content;

	public ServerConfigurationLoadResult(final ServerConfiguration legacyConfiguration,
			final ProcessNetworkConfiguration processNetwork,
			final DiagnosticsConfiguration diagnostics) {
		this.legacyConfiguration = Objects.requireNonNull(legacyConfiguration, "legacyConfiguration");
		this.processNetwork = Objects.requireNonNull(processNetwork, "processNetwork");
		this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
		this.persistence = legacyConfiguration.persistenceConfiguration();
		this.worldRuntime = legacyConfiguration.worldRuntimeConfiguration();
		this.compatibility = legacyConfiguration.compatibilityConfiguration();
		this.tools = legacyConfiguration.toolsConfiguration();
		this.content = legacyConfiguration.contentConfiguration();
	}

	/** Compatibility facade retained for existing mutable runtime-admin paths. */
	public ServerConfiguration getLegacyConfiguration() { return legacyConfiguration; }
	public ProcessNetworkConfiguration getProcessNetwork() { return processNetwork; }
	public DiagnosticsConfiguration getDiagnostics() { return diagnostics; }
	public PersistenceConfiguration getPersistence() { return persistence; }
	public WorldRuntimeConfiguration getWorldRuntime() { return worldRuntime; }
	public CompatibilityConfiguration getCompatibility() { return compatibility; }
	public ToolsConfiguration getTools() { return tools; }
	public ContentConfiguration getContent() { return content; }
}
