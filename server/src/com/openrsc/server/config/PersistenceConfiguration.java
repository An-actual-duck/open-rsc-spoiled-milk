package com.openrsc.server.config;

import com.openrsc.server.database.DatabaseType;
import java.util.Objects;

/** Immutable persistence selection snapshot; credentials remain redacted by design. */
public final class PersistenceConfiguration {
	private final DatabaseType databaseType;
	private final String databaseName;
	private final String databaseHost;
	private final String tablePrefix;
	public PersistenceConfiguration(final DatabaseType databaseType, final String databaseName,
			final String databaseHost, final String tablePrefix) {
		this.databaseType = Objects.requireNonNull(databaseType, "databaseType");
		this.databaseName = value(databaseName); this.databaseHost = value(databaseHost);
		this.tablePrefix = value(tablePrefix);
	}
	private static String value(final String value) { return value == null ? "" : value; }
	public DatabaseType getDatabaseType() { return databaseType; }
	public String getDatabaseName() { return databaseName; }
	public String getDatabaseHost() { return databaseHost; }
	public String getTablePrefix() { return tablePrefix; }
}
