package com.openrsc.server;

import com.openrsc.server.config.ServerConfigurationLoadResult;
import java.util.Objects;

/**
 * Pre-resource Server R2 composition boundary.
 *
 * <p>This deliberately owns no runtime resource. It permits compiled fixtures
 * to verify successful configuration composition and idempotent rollback
 * without opening a database or listener. Server remains the compatibility
 * runtime owner until later R2 stages extract individual resource stages.</p>
 */
public final class ServerBootstrapComposition implements AutoCloseable {
	private final ServerConfigurationLoadResult configuration;
	private boolean closed;

	private ServerBootstrapComposition(final ServerConfigurationLoadResult configuration) {
		this.configuration = Objects.requireNonNull(configuration, "configuration");
	}

	public static ServerBootstrapComposition prepare(
			final ServerConfigurationLoadResult configuration) {
		return new ServerBootstrapComposition(configuration);
	}

	public ServerConfigurationLoadResult getConfiguration() { return configuration; }
	public boolean isClosed() { return closed; }
	public boolean ownsRuntimeResources() { return false; }

	/** Idempotent no-resource rollback/shutdown boundary for pre-start failure. */
	@Override
	public void close() { closed = true; }
}
