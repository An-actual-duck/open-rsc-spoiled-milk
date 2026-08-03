package com.openrsc.server.content.worldedit;

import com.openrsc.server.io.AdaptiveWorldBuilderPackageGuard;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog;
import com.openrsc.server.io.NativeLayeredWorldRuntimeProfile;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Verified copy-on-write publication for one isolated adaptive project. */
public final class AdaptiveWorldBuilderPackagePublisher {
	private static final String TRANSACTION_SCHEMA =
		"adaptive-world-builder-save-transaction-v1";
	private static final Logger LOGGER = LogManager.getLogger(
		AdaptiveWorldBuilderPackagePublisher.class);

	private AdaptiveWorldBuilderPackagePublisher() {
	}

	public enum Stage {
		PACKAGE_WRITTEN,
		PACKAGE_VALIDATED,
		BEFORE_PUBLICATION,
		PREVIOUS_MOVED,
		PACKAGE_PUBLISHED
	}

	public interface Observer {
		void at(Stage stage, Path stagedPackage) throws IOException;
	}

	public interface PackageVerifier {
		void verify(NativeLayeredWorldPackage worldPackage) throws IOException;
	}

	public static final Observer NO_OBSERVER = new Observer() {
		@Override
		public void at(Stage stage, Path stagedPackage) {
		}
	};

	/**
	 * Publishes only after exact staged bytes, generic profile semantics and the
	 * caller's active definition contract have all been revalidated.
	 */
	public static SaveResult publish(
		Path requestedWorkingPackage,
		Path immutableBaseline,
		String expectedWorkingInventorySha256,
		String expectedBaselineInventorySha256,
		Draft draft,
		PackageVerifier verifier,
		Observer requestedObserver) throws IOException {
		if (draft == null || verifier == null) {
			throw new IOException("Adaptive save model and verifier are required");
		}
		Observer observer = requestedObserver == null
			? NO_OBSERVER : requestedObserver;
		Path working = safeExistingDirectory(
			requestedWorkingPackage, "adaptive working package");
		Path immutable = safeExistingDirectory(
			immutableBaseline, "immutable source baseline");
		if (Files.isSameFile(working, immutable)
			|| working.startsWith(immutable) || immutable.startsWith(working)) {
			throw new IOException(
				"Adaptive working package and immutable baseline must be disjoint");
		}
		Path parent = safeExistingDirectory(
			working.getParent(), "adaptive working package parent");
		Path stage = parent.resolve(working.getFileName() + ".save-stage");
		Path previous = parent.resolve(working.getFileName() + ".save-previous");
		Path transaction = parent.resolve(
			working.getFileName() + ".save-transaction");
		requireAbsent(stage, "adaptive save stage");
		requireAbsent(previous, "adaptive previous package");
		requireAbsent(transaction, "adaptive save transaction");

		AdaptiveWorldBuilderPackageGuard.Inventory current =
			AdaptiveWorldBuilderPackageGuard.requireClosedPackage(working);
		requireFingerprint(
			"working package", expectedWorkingInventorySha256,
			current.getFingerprint());
		AdaptiveWorldBuilderPackageGuard.Inventory baseline =
			AdaptiveWorldBuilderPackageGuard.requireClosedPackage(immutable);
		requireFingerprint(
			"immutable source baseline", expectedBaselineInventorySha256,
			baseline.getFingerprint());

		Map<String, byte[]> expectedFiles = draft.files();
		String newInventorySha256 = null;
		String newManifestSha256 = null;
		boolean previousMoved = false;
		try {
			writeStage(stage, expectedFiles);
			observer.at(Stage.PACKAGE_WRITTEN, stage);
			Validated staged = validateExact(stage, expectedFiles, verifier);
			newInventorySha256 = staged.inventory.getFingerprint();
			newManifestSha256 = staged.worldPackage.getManifestSha256();
			observer.at(Stage.PACKAGE_VALIDATED, stage);
			staged = validateExact(stage, expectedFiles, verifier);
			if (!newInventorySha256.equals(staged.inventory.getFingerprint())
				|| !newManifestSha256.equals(
					staged.worldPackage.getManifestSha256())) {
				throw new IOException(
					"Adaptive staged package changed across validation");
			}
			requireFingerprint(
				"immutable source baseline", expectedBaselineInventorySha256,
				AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
					immutable).getFingerprint());
			observer.at(Stage.BEFORE_PUBLICATION, stage);
			staged = validateExact(stage, expectedFiles, verifier);
			if (!newInventorySha256.equals(staged.inventory.getFingerprint())
				|| !newManifestSha256.equals(
					staged.worldPackage.getManifestSha256())) {
				throw new IOException(
					"Adaptive staged package changed before publication");
			}
			writeTransaction(
				transaction, current.getFingerprint(), newInventorySha256);
			moveAtomic(working, previous);
			previousMoved = true;
			observer.at(Stage.PREVIOUS_MOVED, stage);
			moveAtomic(stage, working);
			observer.at(Stage.PACKAGE_PUBLISHED, working);
			Validated published = validateExact(working, expectedFiles, verifier);
			if (!newInventorySha256.equals(published.inventory.getFingerprint())
				|| !newManifestSha256.equals(
					published.worldPackage.getManifestSha256())) {
				throw new IOException(
					"Published adaptive package differs from the verified stage");
			}
			requireFingerprint(
				"immutable source baseline", expectedBaselineInventorySha256,
				AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
					immutable).getFingerprint());
			try {
				deleteTree(previous);
				Files.delete(transaction);
			} catch (IOException cleanupFailure) {
				LOGGER.warn(
					"Committed adaptive save left recovery metadata for next startup",
					cleanupFailure);
			}
			return new SaveResult(
				newManifestSha256, newInventorySha256,
				draft.sectors.size(), draft.levels.size(),
				draft.boundaries.size(), draft.scenery.size(),
				draft.npcs.size(), draft.groundItems.size());
		} catch (IOException failure) {
			rollback(
				working, stage, previous, transaction, previousMoved,
				expectedWorkingInventorySha256, failure);
			requireFingerprint(
				"immutable source baseline", expectedBaselineInventorySha256,
				AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
					immutable).getFingerprint());
			throw failure;
		} catch (RuntimeException failure) {
			IOException wrapped = new IOException(
				"Adaptive package publication failed", failure);
			rollback(
				working, stage, previous, transaction, previousMoved,
				expectedWorkingInventorySha256, wrapped);
			requireFingerprint(
				"immutable source baseline", expectedBaselineInventorySha256,
				AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
					immutable).getFingerprint());
			throw wrapped;
		}
	}

	/** Restores the last complete package after a process interruption. */
	public static void recover(Path requestedWorkingPackage) throws IOException {
		Path working = requestedWorkingPackage.toAbsolutePath().normalize();
		Path parent = safeExistingDirectory(
			working.getParent(), "adaptive working package parent");
		Path stage = parent.resolve(working.getFileName() + ".save-stage");
		Path previous = parent.resolve(working.getFileName() + ".save-previous");
		Path transaction = parent.resolve(
			working.getFileName() + ".save-transaction");
		if (!Files.exists(transaction, LinkOption.NOFOLLOW_LINKS)) {
			if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)
				|| Files.exists(previous, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException(
					"Unowned adaptive save staging state requires manual review");
			}
			return;
		}
		Transaction state = readTransaction(transaction);
		String current = packageFingerprint(working);
		if (state.newInventory.equals(current)) {
			deleteTreeIfPresent(stage);
			deleteTreeIfPresent(previous);
			Files.delete(transaction);
			return;
		}
		if (state.oldInventory.equals(current)) {
			deleteTreeIfPresent(stage);
			deleteTreeIfPresent(previous);
			Files.delete(transaction);
			return;
		}
		String backup = packageFingerprint(previous);
		if (!state.oldInventory.equals(backup)) {
			throw new IOException(
				"Interrupted adaptive save has no verified previous package");
		}
		deleteTreeIfPresent(working);
		moveAtomic(previous, working);
		deleteTreeIfPresent(stage);
		Files.delete(transaction);
		requireFingerprint(
			"recovered working package", state.oldInventory,
			AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
				working).getFingerprint());
	}

	private static Validated validateExact(
		Path packageRoot, Map<String, byte[]> expectedFiles,
		PackageVerifier verifier) throws IOException {
		AdaptiveWorldBuilderPackageGuard.Inventory inventory =
			AdaptiveWorldBuilderPackageGuard.requireClosedPackage(packageRoot);
		if (!expectedFiles.keySet().equals(inventory.getEntries().keySet())) {
			throw new IOException(
				"Adaptive staged package inventory differs from its model");
		}
		for (Map.Entry<String, byte[]> entry : expectedFiles.entrySet()) {
			byte[] actual = Files.readAllBytes(packageRoot.resolve(entry.getKey()));
			if (!Arrays.equals(entry.getValue(), actual)) {
				throw new IOException(
					"Adaptive staged package bytes differ from its model: "
						+ entry.getKey());
			}
		}
		NativeLayeredWorldPackage loaded =
			NativeLayeredWorldPackage.load(packageRoot);
		NativeLayeredWorldRuntimeProfile.ADAPTIVE_WORLD_BUILDER.validate(
			NativeLayeredWorldPackageCatalog.of(
				Collections.singletonList(loaded)));
		verifier.verify(loaded);
		return new Validated(inventory, loaded);
	}

	private static void rollback(
		Path working, Path stage, Path previous, Path transaction,
		boolean previousMoved, String oldFingerprint, IOException failure) {
		try {
			if (previousMoved && Files.exists(previous, LinkOption.NOFOLLOW_LINKS)) {
				deleteTreeIfPresent(working);
				moveAtomic(previous, working);
			}
			deleteTreeIfPresent(stage);
			deleteTreeIfPresent(previous);
			Files.deleteIfExists(transaction);
			if (Files.exists(working, LinkOption.NOFOLLOW_LINKS)) {
				requireFingerprint(
					"rolled-back working package", oldFingerprint,
					AdaptiveWorldBuilderPackageGuard.requireClosedPackage(
						working).getFingerprint());
			}
		} catch (Exception rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
		}
	}

	private static void writeStage(
		Path stage, Map<String, byte[]> files) throws IOException {
		Files.createDirectory(stage);
		for (Map.Entry<String, byte[]> entry : files.entrySet()) {
			Path destination = stage.resolve(entry.getKey()).normalize();
			if (!destination.startsWith(stage)) {
				throw new IOException("Adaptive output path escapes its stage");
			}
			Files.createDirectories(destination.getParent());
			Files.write(
				destination, entry.getValue(), StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE);
			force(destination);
		}
	}

	private static void writeTransaction(
		Path destination, String oldInventory, String newInventory)
		throws IOException {
		String value = TRANSACTION_SCHEMA + "\nold=" + oldInventory
			+ "\nnew=" + newInventory + "\n";
		Files.write(
			destination, value.getBytes(StandardCharsets.US_ASCII),
			StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		force(destination);
	}

	private static Transaction readTransaction(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(path) || Files.size(path) > 256L) {
			throw new IOException("Adaptive save transaction evidence is unsafe");
		}
		requireSingleLink(path, "adaptive save transaction evidence");
		String value = new String(
			Files.readAllBytes(path), StandardCharsets.US_ASCII);
		String[] lines = value.split("\n", -1);
		if (lines.length != 4 || !TRANSACTION_SCHEMA.equals(lines[0])
			|| !lines[1].startsWith("old=") || !lines[2].startsWith("new=")
			|| !lines[3].isEmpty()) {
			throw new IOException("Adaptive save transaction evidence is invalid");
		}
		String oldValue = lines[1].substring(4);
		String newValue = lines[2].substring(4);
		if (!oldValue.matches("[0-9a-f]{64}")
			|| !newValue.matches("[0-9a-f]{64}")) {
			throw new IOException("Adaptive save transaction hashes are invalid");
		}
		return new Transaction(oldValue, newValue);
	}

	private static String packageFingerprint(Path path) {
		try {
			return AdaptiveWorldBuilderPackageGuard.requireClosedPackage(path)
				.getFingerprint();
		} catch (Exception failure) {
			return "";
		}
	}

	private static void requireFingerprint(
		String label, String expected, String actual) throws IOException {
		if (expected == null || !expected.matches("[0-9a-f]{64}")
			|| !expected.equals(actual)) {
			throw new IOException(label + " inventory changed");
		}
	}

	private static Path safeExistingDirectory(Path requested, String label)
		throws IOException {
		if (requested == null) throw new IOException(label + " is required");
		Path normalized = requested.toAbsolutePath().normalize();
		Path current = normalized.getRoot();
		if (current == null) throw new IOException(label + " has no root");
		for (Path part : normalized) {
			current = current.resolve(part);
			if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
				&& Files.isSymbolicLink(current)) {
				throw new IOException(label + " contains a symbolic link");
			}
		}
		if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException(label + " is missing or unsafe");
		}
		return normalized.toRealPath();
	}

	private static void requireAbsent(Path path, String label) throws IOException {
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException(label + " already exists");
		}
	}

	private static void moveAtomic(Path source, Path destination)
		throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException failure) {
			throw new IOException(
				"Adaptive package save requires atomic moves on the project filesystem",
				failure);
		}
	}

	private static void force(Path path) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
	}

	private static void requireSingleLink(Path path, String label)
		throws IOException {
		try {
			Object links = Files.getAttribute(
				path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (links instanceof Number && ((Number) links).longValue() != 1L) {
				throw new IOException(label + " is hard linked");
			}
		} catch (UnsupportedOperationException unsupported) {
			path.toRealPath();
		} catch (IllegalArgumentException unsupported) {
			path.toRealPath();
		}
	}

	private static void deleteTreeIfPresent(Path path) throws IOException {
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) deleteTree(path);
	}

	private static void deleteTree(Path root) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(
				Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(
				Path directory, IOException failure) throws IOException {
				if (failure != null) throw failure;
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static String sha256(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] value = digest.digest(bytes);
			StringBuilder result = new StringBuilder(64);
			for (byte part : value) {
				result.append(String.format("%02x", part & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static String signed(int value) {
		return value < 0 ? "m" + Long.toString(-(long)value)
			: "p" + Integer.toString(value);
	}

	private static void json(StringBuilder value, String text) {
		value.append('"');
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			switch (character) {
				case '"': value.append("\\\""); break;
				case '\\': value.append("\\\\"); break;
				case '\b': value.append("\\b"); break;
				case '\f': value.append("\\f"); break;
				case '\n': value.append("\\n"); break;
				case '\r': value.append("\\r"); break;
				case '\t': value.append("\\t"); break;
				default:
					if (character < 32) {
						value.append(String.format("\\u%04x", (int)character));
					} else {
						value.append(character);
					}
			}
		}
		value.append('"');
	}

	private static void location(StringBuilder value, WorldLocation location) {
		value.append("{\"x\":")
			.append(location.getCoordinate().getX())
			.append(",\"y\":")
			.append(location.getCoordinate().getY()).append('}');
	}

	public static final class Draft {
		private final String packageId;
		private final String packageVersion;
		private final int presentationChunkSize;
		private final Map<String, String> worldSpaces;
		private final List<Level> levels;
		private final List<Sector> sectors;
		private final List<Boundary> boundaries;
		private final List<Scenery> scenery;
		private final List<Npc> npcs;
		private final List<GroundItem> groundItems;

		public Draft(
			String packageId, String packageVersion, int presentationChunkSize,
			Map<String, String> worldSpaces, List<Level> levels,
			List<Sector> sectors, List<Boundary> boundaries,
			List<Scenery> scenery, List<Npc> npcs,
			List<GroundItem> groundItems) {
			this.packageId = packageId;
			this.packageVersion = packageVersion;
			this.presentationChunkSize = presentationChunkSize;
			this.worldSpaces = Collections.unmodifiableMap(
				new TreeMap<String, String>(worldSpaces));
			this.levels = sorted(levels, LEVEL_ORDER);
			this.sectors = sorted(sectors, SECTOR_ORDER);
			this.boundaries = sorted(boundaries, BOUNDARY_ORDER);
			this.scenery = sorted(scenery, SCENERY_ORDER);
			this.npcs = sorted(npcs, NPC_ORDER);
			this.groundItems = sorted(groundItems, GROUND_ITEM_ORDER);
		}

		private Map<String, byte[]> files() throws IOException {
			Map<String, byte[]> result = new TreeMap<String, byte[]>();
			List<FileRecord> terrainFiles = new ArrayList<FileRecord>();
			for (Sector sector : sectors) {
				String path = "terrain/" + sector.identity.getWorldSpace().getValue()
					+ "/l" + signed(sector.identity.getLevel())
					+ "/x" + signed(sector.identity.getSectorX())
					+ "-y" + signed(sector.identity.getSectorY()) + ".raw";
				putUnique(result, path, sector.bytes);
				terrainFiles.add(new FileRecord(path, sha256(sector.bytes)));
			}
			Map<LevelKey, PlacementGroup> groups = groups();
			List<FileRecord> placementFiles = new ArrayList<FileRecord>();
			for (Map.Entry<LevelKey, PlacementGroup> entry : groups.entrySet()) {
				LevelKey key = entry.getKey();
				String path = "placements/" + key.worldSpace + "/l"
					+ signed(key.level) + ".json";
				byte[] bytes = placement(entry.getValue());
				putUnique(result, path, bytes);
				placementFiles.add(new FileRecord(path, sha256(bytes)));
			}
			byte[] manifest = manifest(terrainFiles, placementFiles);
			putUnique(result, "manifest.json", manifest);
			return Collections.unmodifiableMap(
				new LinkedHashMap<String, byte[]>(result));
		}

		private Map<LevelKey, PlacementGroup> groups() throws IOException {
			Map<LevelKey, PlacementGroup> result =
				new TreeMap<LevelKey, PlacementGroup>(LEVEL_KEY_ORDER);
			for (Level level : levels) {
				LevelKey key = new LevelKey(level.worldSpace, level.level);
				if (result.put(key, new PlacementGroup(key)) != null) {
					throw new IOException("Duplicate adaptive level declaration");
				}
			}
			for (Boundary value : boundaries) group(result, value.location).boundaries.add(value);
			for (Scenery value : scenery) group(result, value.location).scenery.add(value);
			for (Npc value : npcs) group(result, value.start).npcs.add(value);
			for (GroundItem value : groundItems) group(result, value.location).groundItems.add(value);
			return result;
		}

		private static PlacementGroup group(
			Map<LevelKey, PlacementGroup> groups, WorldLocation location)
			throws IOException {
			LevelKey key = new LevelKey(
				location.getWorldSpace().getValue(),
				location.getCoordinate().getLevel());
			PlacementGroup result = groups.get(key);
			if (result == null) {
				throw new IOException("Placement references an undeclared adaptive level");
			}
			return result;
		}

		private byte[] placement(PlacementGroup group) {
			StringBuilder value = new StringBuilder();
			value.append("{\"schemaVersion\":3,\"encoding\":");
			json(value, NativeLayeredWorldPackage.WORLD_PLACEMENT_ENCODING_V3);
			value.append(",\"worldSpace\":"); json(value, group.key.worldSpace);
			value.append(",\"level\":").append(group.key.level);
			value.append(",\"npcs\":[");
			for (int index = 0; index < group.npcs.size(); index++) {
				if (index > 0) value.append(',');
				Npc item = group.npcs.get(index);
				value.append("{\"placementId\":"); json(value, item.placementId);
				value.append(",\"npcId\":").append(item.npcId)
					.append(",\"start\":"); location(value, item.start);
				value.append(",\"roamBounds\":{\"minimum\":{\"x\":")
					.append(item.minX).append(",\"y\":").append(item.minY)
					.append("},\"maximum\":{\"x\":").append(item.maxX)
					.append(",\"y\":").append(item.maxY).append("}}}");
			}
			value.append("],\"groundItems\":[");
			for (int index = 0; index < group.groundItems.size(); index++) {
				if (index > 0) value.append(',');
				GroundItem item = group.groundItems.get(index);
				value.append("{\"placementId\":"); json(value, item.placementId);
				value.append(",\"itemId\":").append(item.itemId)
					.append(",\"position\":"); location(value, item.location);
				value.append(",\"amount\":").append(item.amount)
					.append(",\"respawnSeconds\":")
					.append(item.respawnSeconds).append('}');
			}
			value.append("],\"scenery\":[");
			for (int index = 0; index < group.scenery.size(); index++) {
				if (index > 0) value.append(',');
				Scenery item = group.scenery.get(index);
				value.append("{\"placementId\":"); json(value, item.placementId);
				value.append(",\"sceneryId\":").append(item.sceneryId)
					.append(",\"position\":"); location(value, item.location);
				value.append(",\"direction\":").append(item.direction).append('}');
			}
			value.append("],\"boundaries\":[");
			for (int index = 0; index < group.boundaries.size(); index++) {
				if (index > 0) value.append(',');
				Boundary item = group.boundaries.get(index);
				value.append("{\"placementId\":"); json(value, item.placementId);
				value.append(",\"boundaryId\":").append(item.boundaryId)
					.append(",\"position\":"); location(value, item.location);
				value.append(",\"direction\":").append(item.direction).append('}');
			}
			value.append("]}\n");
			return value.toString().getBytes(StandardCharsets.UTF_8);
		}

		private byte[] manifest(
			List<FileRecord> terrainFiles, List<FileRecord> placementFiles) {
			StringBuilder value = new StringBuilder();
			value.append("{\"schemaVersion\":1,\"packageType\":\"layered-world\",\"packageId\":");
			json(value, packageId);
			value.append(",\"packageVersion\":"); json(value, packageVersion);
			value.append(",\"coordinateModel\":\"signed-layered-v1\",\"storage\":{\"sectorSize\":48,\"presentationChunkSize\":")
				.append(presentationChunkSize).append("},\"worldSpaces\":[");
			int worldIndex = 0;
			for (Map.Entry<String, String> world : worldSpaces.entrySet()) {
				if (worldIndex++ > 0) value.append(',');
				value.append("{\"id\":"); json(value, world.getKey());
				value.append(",\"kind\":"); json(value, world.getValue());
				value.append('}');
			}
			value.append("],\"levels\":[");
			for (int index = 0; index < levels.size(); index++) {
				if (index > 0) value.append(',');
				Level level = levels.get(index);
				value.append("{\"worldSpace\":"); json(value, level.worldSpace);
				value.append(",\"level\":").append(level.level)
					.append(",\"name\":"); json(value, level.name);
				value.append(",\"role\":"); json(value, level.role);
				value.append('}');
			}
			value.append("],\"terrainSectors\":[");
			for (int index = 0; index < sectors.size(); index++) {
				if (index > 0) value.append(',');
				Sector sector = sectors.get(index);
				FileRecord file = terrainFiles.get(index);
				value.append("{\"worldSpace\":");
				json(value, sector.identity.getWorldSpace().getValue());
				value.append(",\"level\":").append(sector.identity.getLevel())
					.append(",\"sectorX\":").append(sector.identity.getSectorX())
					.append(",\"sectorY\":").append(sector.identity.getSectorY())
					.append(",\"encoding\":\"raw-layered-sector-v1\",\"path\":");
				json(value, file.path); value.append(",\"sha256\":");
				json(value, file.sha256); value.append('}');
			}
			value.append("],\"placementSets\":[");
			for (int index = 0; index < levels.size(); index++) {
				if (index > 0) value.append(',');
				Level level = levels.get(index);
				FileRecord file = placementFiles.get(index);
				String setId = "world-builder." + level.worldSpace + ".l"
					+ signed(level.level);
				value.append("{\"id\":"); json(value, setId);
				value.append(",\"worldSpace\":"); json(value, level.worldSpace);
				value.append(",\"level\":").append(level.level)
					.append(",\"encoding\":\"layered-world-placements-v3\",\"path\":");
				json(value, file.path); value.append(",\"sha256\":");
				json(value, file.sha256); value.append('}');
			}
			value.append("]}\n");
			return value.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	private static void putUnique(
		Map<String, byte[]> values, String path, byte[] bytes) throws IOException {
		if (values.put(path, bytes.clone()) != null) {
			throw new IOException("Duplicate adaptive output path: " + path);
		}
	}

	private static <T> List<T> sorted(List<T> values, Comparator<T> order) {
		List<T> result = new ArrayList<T>(values);
		Collections.sort(result, order);
		return Collections.unmodifiableList(result);
	}

	public static final class Level {
		public final String worldSpace;
		public final int level;
		public final String name;
		public final String role;

		public Level(String worldSpace, int level, String name, String role) {
			this.worldSpace = worldSpace;
			this.level = level;
			this.name = name;
			this.role = role;
		}
	}

	public static final class Sector {
		public final WorldMapSectorId identity;
		public final byte[] bytes;

		public Sector(WorldMapSectorId identity, byte[] bytes) {
			this.identity = identity;
			this.bytes = bytes.clone();
		}
	}

	public static final class Boundary {
		public final String placementId;
		public final int boundaryId;
		public final WorldLocation location;
		public final int direction;

		public Boundary(
			String placementId, int boundaryId, WorldLocation location,
			int direction) {
			this.placementId = placementId;
			this.boundaryId = boundaryId;
			this.location = location;
			this.direction = direction;
		}
	}

	public static final class Scenery {
		public final String placementId;
		public final int sceneryId;
		public final WorldLocation location;
		public final int direction;

		public Scenery(
			String placementId, int sceneryId, WorldLocation location,
			int direction) {
			this.placementId = placementId;
			this.sceneryId = sceneryId;
			this.location = location;
			this.direction = direction;
		}
	}

	public static final class Npc {
		public final String placementId;
		public final int npcId;
		public final WorldLocation start;
		public final int minX;
		public final int minY;
		public final int maxX;
		public final int maxY;

		public Npc(
			String placementId, int npcId, WorldLocation start,
			int minX, int minY, int maxX, int maxY) {
			this.placementId = placementId;
			this.npcId = npcId;
			this.start = start;
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}
	}

	public static final class GroundItem {
		public final String placementId;
		public final int itemId;
		public final WorldLocation location;
		public final int amount;
		public final int respawnSeconds;

		public GroundItem(
			String placementId, int itemId, WorldLocation location,
			int amount, int respawnSeconds) {
			this.placementId = placementId;
			this.itemId = itemId;
			this.location = location;
			this.amount = amount;
			this.respawnSeconds = respawnSeconds;
		}
	}

	public static final class SaveResult {
		public final String manifestSha256;
		public final String inventorySha256;
		public final int sectorCount;
		public final int levelCount;
		public final int boundaryCount;
		public final int sceneryCount;
		public final int npcCount;
		public final int groundItemCount;

		private SaveResult(
			String manifestSha256, String inventorySha256,
			int sectorCount, int levelCount, int boundaryCount,
			int sceneryCount, int npcCount, int groundItemCount) {
			this.manifestSha256 = manifestSha256;
			this.inventorySha256 = inventorySha256;
			this.sectorCount = sectorCount;
			this.levelCount = levelCount;
			this.boundaryCount = boundaryCount;
			this.sceneryCount = sceneryCount;
			this.npcCount = npcCount;
			this.groundItemCount = groundItemCount;
		}
	}

	private static final Comparator<Level> LEVEL_ORDER = new Comparator<Level>() {
		@Override public int compare(Level left, Level right) {
			int value = left.worldSpace.compareTo(right.worldSpace);
			return value == 0 ? Integer.compare(left.level, right.level) : value;
		}
	};
	private static final Comparator<Sector> SECTOR_ORDER = new Comparator<Sector>() {
		@Override public int compare(Sector left, Sector right) {
			return compareSector(left.identity, right.identity);
		}
	};
	private static final Comparator<Boundary> BOUNDARY_ORDER =
		new Comparator<Boundary>() {
			@Override public int compare(Boundary left, Boundary right) {
				return left.placementId.compareTo(right.placementId);
			}
		};
	private static final Comparator<Scenery> SCENERY_ORDER =
		new Comparator<Scenery>() {
			@Override public int compare(Scenery left, Scenery right) {
				return left.placementId.compareTo(right.placementId);
			}
		};
	private static final Comparator<Npc> NPC_ORDER = new Comparator<Npc>() {
		@Override public int compare(Npc left, Npc right) {
			return left.placementId.compareTo(right.placementId);
		}
	};
	private static final Comparator<GroundItem> GROUND_ITEM_ORDER =
		new Comparator<GroundItem>() {
			@Override public int compare(GroundItem left, GroundItem right) {
				return left.placementId.compareTo(right.placementId);
			}
		};
	private static final Comparator<LevelKey> LEVEL_KEY_ORDER =
		new Comparator<LevelKey>() {
			@Override public int compare(LevelKey left, LevelKey right) {
				int value = left.worldSpace.compareTo(right.worldSpace);
				return value == 0 ? Integer.compare(left.level, right.level) : value;
			}
		};

	private static int compareSector(
		WorldMapSectorId left, WorldMapSectorId right) {
		int value = left.getWorldSpace().getValue().compareTo(
			right.getWorldSpace().getValue());
		if (value == 0) value = Integer.compare(left.getLevel(), right.getLevel());
		if (value == 0) value = Integer.compare(left.getSectorX(), right.getSectorX());
		if (value == 0) value = Integer.compare(left.getSectorY(), right.getSectorY());
		return value;
	}

	private static final class LevelKey {
		final String worldSpace;
		final int level;
		LevelKey(String worldSpace, int level) {
			this.worldSpace = worldSpace;
			this.level = level;
		}
	}

	private static final class PlacementGroup {
		final LevelKey key;
		final List<Boundary> boundaries = new ArrayList<Boundary>();
		final List<Scenery> scenery = new ArrayList<Scenery>();
		final List<Npc> npcs = new ArrayList<Npc>();
		final List<GroundItem> groundItems = new ArrayList<GroundItem>();
		PlacementGroup(LevelKey key) { this.key = key; }
	}

	private static final class FileRecord {
		final String path;
		final String sha256;
		FileRecord(String path, String sha256) {
			this.path = path;
			this.sha256 = sha256;
		}
	}

	private static final class Validated {
		final AdaptiveWorldBuilderPackageGuard.Inventory inventory;
		final NativeLayeredWorldPackage worldPackage;
		Validated(
			AdaptiveWorldBuilderPackageGuard.Inventory inventory,
			NativeLayeredWorldPackage worldPackage) {
			this.inventory = inventory;
			this.worldPackage = worldPackage;
		}
	}

	private static final class Transaction {
		final String oldInventory;
		final String newInventory;
		Transaction(String oldInventory, String newInventory) {
			this.oldInventory = oldInventory;
			this.newInventory = newInventory;
		}
	}
}
