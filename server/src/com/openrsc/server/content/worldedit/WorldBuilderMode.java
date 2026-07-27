package com.openrsc.server.content.worldedit;

import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.database.DatabaseType;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fail-closed guardrails for the opt-in, isolated World Builder runtime.
 */
public final class WorldBuilderMode {
	public static final String ACCOUNT_NAME = "Builder";
	public static final String DATABASE_NAME = "world_builder";

	private WorldBuilderMode() {
	}

	public static boolean isBuilderAccount(String username) {
		return username != null && ACCOUNT_NAME.equalsIgnoreCase(username.trim());
	}

	public static boolean isLoopbackAddress(String address) {
		if (address == null || address.trim().isEmpty()) {
			return false;
		}
		try {
			InetAddress[] resolved = InetAddress.getAllByName(address.trim());
			if (resolved.length == 0) {
				return false;
			}
			for (InetAddress candidate : resolved) {
				if (!candidate.isLoopbackAddress()) {
					return false;
				}
			}
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	public static void validate(ServerConfiguration config) {
		if (!config.WORLD_BUILDER_MODE) {
			return;
		}
		List<String> errors = validationErrors(
			config.SERVER_BIND_ADDRESS,
			config.DB_TYPE,
			config.DB_NAME,
			config.DB_TABLE_PREFIX,
			config.MAX_PLAYERS,
			config.WANT_PACKET_REGISTER,
			config.ALLOW_IN_GAME_WORLD_EDITOR,
			config.WANT_CUSTOM_LANDSCAPE,
			config.WANT_MYWORLD);
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException(
				"Unsafe World Builder configuration: " + String.join("; ", errors));
		}
		if (config.WORLD_BUILDER_LAYERED_REVIEW_MODE
			&& (!config.WANT_LAYERED_PLAYER_LOCATION_AUTHORITY
				|| !config.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY
				|| !config.WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY
				|| !config.WANT_LAYERED_NATIVE_TERRAIN_PACKAGE
				|| !"spoiled-milk-replacement".equals(
					config.LAYERED_NATIVE_WORLD_RUNTIME_PROFILE))) {
			throw new IllegalArgumentException(
				"Unsafe World Builder configuration: layered review requires "
					+ "the complete Spoiled Milk native package authority");
		}
	}

	public static List<String> validationErrors(
		String bindAddress,
		DatabaseType databaseType,
		String databaseName,
		String tablePrefix,
		int maxPlayers,
		boolean packetRegistration,
		boolean editorAllowed,
		boolean customLandscape,
		boolean myWorld) {
		List<String> errors = new ArrayList<String>();
		if (!isLoopbackAddress(bindAddress)) {
			errors.add("server_bind_address must resolve only to loopback addresses");
		}
		if (databaseType != DatabaseType.SQLITE) {
			errors.add("db_type must be sqlite");
		}
		if (!DATABASE_NAME.equals(databaseName)) {
			errors.add("db_name must be " + DATABASE_NAME);
		}
		if (tablePrefix != null && !tablePrefix.isEmpty()) {
			errors.add("db_table_prefix must be empty");
		}
		if (maxPlayers != 1) {
			errors.add("max_players must be 1");
		}
		if (packetRegistration) {
			errors.add("want_packet_register must be false");
		}
		if (!editorAllowed) {
			errors.add("allow_in_game_world_editor must be true");
		}
		if (!customLandscape) {
			errors.add("custom_landscape must be true");
		}
		if (!myWorld) {
			errors.add("want_myworld must be true");
		}
		return Collections.unmodifiableList(errors);
	}
}
