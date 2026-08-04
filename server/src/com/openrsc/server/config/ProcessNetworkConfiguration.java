package com.openrsc.server.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable startup/network view projected from the mutable compatibility
 * configuration. Runtime-administered rate-limit consumers intentionally keep
 * reading {@code ServerConfiguration} directly during the R2-1 transition.
 */
public final class ProcessNetworkConfiguration {

	private static final List<String> DEPRECATED_KEYS = Collections.emptyList();
	private static final Map<String, String> OWNERSHIP_NOTES = ownershipNotes();

	private final String serverBindAddress;
	private final int serverPort;
	private final int websocketPort;
	private final boolean websocketsEnabled;
	private final String sslServerCertPath;
	private final String sslServerKeyPath;
	private final int maxConnectionsPerIp;
	private final int maxConnectionsPerSecond;
	private final int maxPacketsPerSecond;
	private final int maxLoginsPerSecond;
	private final int maxLoginsPerServerPerTick;
	private final int maxPasswordGuessesPerFiveMinutes;
	private final int networkFloodIpBanMinutes;
	private final int maxPlayers;
	private final int maxPlayersPerIp;
	private final int sessionIdSenderTimer;
	private final List<String> ignoredNetworkExceptions;
	private final List<String> connectionResetExceptions;
	private final List<String> validationErrors;

	public ProcessNetworkConfiguration(
		final String serverBindAddress,
		final int serverPort,
		final int websocketPort,
		final boolean websocketsEnabled,
		final String sslServerCertPath,
		final String sslServerKeyPath,
		final int maxConnectionsPerIp,
		final int maxConnectionsPerSecond,
		final int maxPacketsPerSecond,
		final int maxLoginsPerSecond,
		final int maxLoginsPerServerPerTick,
		final int maxPasswordGuessesPerFiveMinutes,
		final int networkFloodIpBanMinutes,
		final int maxPlayers,
		final int maxPlayersPerIp,
		final int sessionIdSenderTimer,
		final List<String> ignoredNetworkExceptions,
		final List<String> connectionResetExceptions) {
		this.serverBindAddress = serverBindAddress;
		this.serverPort = serverPort;
		this.websocketPort = websocketPort;
		this.websocketsEnabled = websocketsEnabled;
		this.sslServerCertPath = valueOrEmpty(sslServerCertPath);
		this.sslServerKeyPath = valueOrEmpty(sslServerKeyPath);
		this.maxConnectionsPerIp = maxConnectionsPerIp;
		this.maxConnectionsPerSecond = maxConnectionsPerSecond;
		this.maxPacketsPerSecond = maxPacketsPerSecond;
		this.maxLoginsPerSecond = maxLoginsPerSecond;
		this.maxLoginsPerServerPerTick = maxLoginsPerServerPerTick;
		this.maxPasswordGuessesPerFiveMinutes = maxPasswordGuessesPerFiveMinutes;
		this.networkFloodIpBanMinutes = networkFloodIpBanMinutes;
		this.maxPlayers = maxPlayers;
		this.maxPlayersPerIp = maxPlayersPerIp;
		this.sessionIdSenderTimer = sessionIdSenderTimer;
		this.ignoredNetworkExceptions = immutableCopy(ignoredNetworkExceptions);
		this.connectionResetExceptions = immutableCopy(connectionResetExceptions);
		this.validationErrors = Collections.unmodifiableList(validate());
	}

	private static String valueOrEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static List<String> immutableCopy(final List<String> values) {
		return Collections.unmodifiableList(new ArrayList<String>(values));
	}

	private List<String> validate() {
		final List<String> errors = new ArrayList<String>();
		if (serverBindAddress == null || serverBindAddress.trim().isEmpty()) {
			errors.add("server_bind_address must not be blank");
		}
		validatePort(errors, "server_port", serverPort);
		validatePort(errors, "ws_server_port", websocketPort);
		if (websocketsEnabled && serverPort == websocketPort) {
			errors.add("want_feature_websockets cannot use the same TCP and WebSocket port");
		}
		validateNonNegative(errors, "max_connections_per_ip", maxConnectionsPerIp);
		validateNonNegative(errors, "max_connections_per_second", maxConnectionsPerSecond);
		validateNonNegative(errors, "max_packets_per_second", maxPacketsPerSecond);
		validateNonNegative(errors, "max_logins_per_second", maxLoginsPerSecond);
		validateNonNegative(errors, "max_logins_per_server_per_tick", maxLoginsPerServerPerTick);
		validateNonNegative(errors, "max_password_guesses_per_five_minutes", maxPasswordGuessesPerFiveMinutes);
		validateNonNegative(errors, "network_flood_ip_ban_minutes", networkFloodIpBanMinutes);
		validateNonNegative(errors, "max_players", maxPlayers);
		validateNonNegative(errors, "max_players_per_ip", maxPlayersPerIp);
		validateNonNegative(errors, "session_id_sender_timer", sessionIdSenderTimer);
		return errors;
	}

	private static void validatePort(final List<String> errors, final String key, final int port) {
		if (port < 1 || port > 65535) {
			errors.add(key + " must be between 1 and 65535");
		}
	}

	private static void validateNonNegative(final List<String> errors, final String key, final int value) {
		if (value < 0) {
			errors.add(key + " must not be negative");
		}
	}

	private static Map<String, String> ownershipNotes() {
		final Map<String, String> notes = new LinkedHashMap<String, String>();
		notes.put("max_players", "world capacity consumed at network admission; final process/world owner remains open");
		notes.put("session_id_sender_timer", "legacy name describes a sender but the value is a connection timeout in milliseconds");
		notes.put("ssl_server_cert_path", "loaded from connections.conf before the selected world profile; precedence is preserved");
		notes.put("ssl_server_key_path", "loaded from connections.conf before the selected world profile; precedence is preserved");
		return Collections.unmodifiableMap(notes);
	}

	public void requireValid() {
		if (!validationErrors.isEmpty()) {
			throw new ConfigurationValidationException("process/network", validationErrors);
		}
	}

	public String getServerBindAddress() { return serverBindAddress; }
	public int getServerPort() { return serverPort; }
	public int getWebsocketPort() { return websocketPort; }
	public boolean isWebsocketsEnabled() { return websocketsEnabled; }
	public String getSslServerCertPath() { return sslServerCertPath; }
	public String getSslServerKeyPath() { return sslServerKeyPath; }
	public int getMaxConnectionsPerIp() { return maxConnectionsPerIp; }
	public int getMaxConnectionsPerSecond() { return maxConnectionsPerSecond; }
	public int getMaxPacketsPerSecond() { return maxPacketsPerSecond; }
	public int getMaxLoginsPerSecond() { return maxLoginsPerSecond; }
	public int getMaxLoginsPerServerPerTick() { return maxLoginsPerServerPerTick; }
	public int getMaxPasswordGuessesPerFiveMinutes() { return maxPasswordGuessesPerFiveMinutes; }
	public int getNetworkFloodIpBanMinutes() { return networkFloodIpBanMinutes; }
	public int getMaxPlayers() { return maxPlayers; }
	public int getMaxPlayersPerIp() { return maxPlayersPerIp; }
	public int getSessionIdSenderTimer() { return sessionIdSenderTimer; }
	public List<String> getIgnoredNetworkExceptions() { return ignoredNetworkExceptions; }
	public List<String> getConnectionResetExceptions() { return connectionResetExceptions; }
	public List<String> getValidationErrors() { return validationErrors; }
	public List<String> getDeprecatedKeys() { return DEPRECATED_KEYS; }
	public Map<String, String> getOwnershipNotes() { return OWNERSHIP_NOTES; }
}
