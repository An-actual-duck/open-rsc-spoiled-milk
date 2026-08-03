package com.openrsc.server.content.worldedit;

import com.openrsc.server.Server;
import com.openrsc.server.database.JDBCDatabase;
import com.openrsc.server.database.struct.PlayerLoginData;
import com.openrsc.server.model.entity.player.Group;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.regex.Pattern;

/** Creates and repairs the single local Builder identity in its isolated database. */
public final class WorldBuilderAccountProvisioner {
	public static final String CREDENTIAL_FILE_PROPERTY = "openrsc.worldBuilderCredentialFile";
	public static final String DEFAULT_CREDENTIAL_FILE = "inc/sqlite/world-builder.credential";
	private static final Pattern CREDENTIAL_PATTERN = Pattern.compile("[A-Za-z0-9]{20}");
	private static final char[] CREDENTIAL_ALPHABET =
		"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Logger LOGGER = LogManager.getLogger(WorldBuilderAccountProvisioner.class);

	private WorldBuilderAccountProvisioner() {
	}

	public static void provision(Server server) throws Exception {
		if (!server.getConfig().WORLD_BUILDER_MODE) {
			return;
		}
		Path credentialPath = resolveCredentialPath();
		if (AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(server.getConfig())) {
			credentialPath = server.getWorldEditStorage().validateGeneratedPath(
				credentialPath, "adaptive World Builder credential file");
			if (Files.exists(credentialPath, LinkOption.NOFOLLOW_LINKS)) {
				requireSingleLink(credentialPath);
			}
		}
		String credential = readOrCreateCredential(credentialPath);
		PlayerLoginData playerData = server.getDatabase().getPlayerLoginData(WorldBuilderMode.ACCOUNT_NAME);
		if (playerData == null) {
			int id = server.getDatabase().createPlayer(
				WorldBuilderMode.ACCOUNT_NAME,
				"builder@localhost.invalid",
				DataConversions.hashPassword(credential, null),
				System.currentTimeMillis() / 1000L,
				"127.0.0.1");
			if (id < 0) {
				throw new IllegalStateException("Unable to create isolated Builder account");
			}
			playerData = server.getDatabase().getPlayerLoginData(WorldBuilderMode.ACCOUNT_NAME);
		}

		boolean passwordMatches = playerData != null
			&& DataConversions.checkPassword(credential, playerData.salt, playerData.password);
		boolean adminMatches = playerData != null && playerData.groupId == Group.ADMIN;
		if (!passwordMatches || !adminMatches) {
			String passwordHash = passwordMatches
				? playerData.password
				: DataConversions.hashPassword(credential, null);
			String prefix = server.getConfig().DB_TABLE_PREFIX;
			String query = "UPDATE `" + prefix + "players` "
				+ "SET `group_id` = ?, `pass` = ?, `salt` = '' "
				+ "WHERE LOWER(`username`) = LOWER(?)";
			try (PreparedStatement statement = ((JDBCDatabase)server.getDatabase())
				.getConnection().prepareStatement(query)) {
				statement.setInt(1, Group.ADMIN);
				statement.setString(2, passwordHash);
				statement.setString(3, WorldBuilderMode.ACCOUNT_NAME);
				if (statement.executeUpdate() != 1) {
					throw new IllegalStateException("Unable to authorize isolated Builder account");
				}
			}
		}
		LOGGER.info("Isolated Builder account is ready; credential file: {}", credentialPath);
	}

	static Path resolveCredentialPath() throws IOException {
		Path runtimeRoot = Paths.get("").toAbsolutePath().normalize();
		String configured = System.getProperty(CREDENTIAL_FILE_PROPERTY, DEFAULT_CREDENTIAL_FILE);
		Path path = Paths.get(configured);
		if (!path.isAbsolute()) {
			path = runtimeRoot.resolve(path);
		}
		path = path.toAbsolutePath().normalize();
		if (!path.startsWith(runtimeRoot)) {
			throw new IOException("World Builder credential file must remain inside the isolated runtime");
		}
		return path;
	}

	static String readOrCreateCredential(Path path) throws IOException {
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("World Builder credential path is not a regular file");
			}
			return readCredential(path);
		}
		Files.createDirectories(path.getParent());
		try {
			Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
			Files.createFile(path, PosixFilePermissions.asFileAttribute(permissions));
		} catch (UnsupportedOperationException ignored) {
			Files.createFile(path);
		}
		String credential = newCredential();
		Files.write(path, credential.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.TRUNCATE_EXISTING);
		return credential;
	}

	static String readCredential(Path path) throws IOException {
		long size = Files.size(path);
		if (size < 1 || size > 64) {
			throw new IOException("World Builder credential file has an invalid size");
		}
		String credential = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII).trim();
		if (!isValidCredential(credential)) {
			throw new IOException("World Builder credential file is invalid");
		}
		return credential;
	}

	public static boolean isValidCredential(String credential) {
		return credential != null && CREDENTIAL_PATTERN.matcher(credential).matches();
	}

	private static void requireSingleLink(Path path) throws IOException {
		try {
			Object links = Files.getAttribute(
				path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (links instanceof Number && ((Number) links).longValue() != 1L) {
				throw new IOException(
					"Adaptive World Builder credential file is hard linked");
			}
		} catch (UnsupportedOperationException unsupported) {
			path.toRealPath();
		} catch (IllegalArgumentException unsupported) {
			path.toRealPath();
		}
	}

	private static String newCredential() {
		StringBuilder builder = new StringBuilder(20);
		for (int i = 0; i < 20; i++) {
			builder.append(CREDENTIAL_ALPHABET[SECURE_RANDOM.nextInt(CREDENTIAL_ALPHABET.length)]);
		}
		return builder.toString();
	}
}
