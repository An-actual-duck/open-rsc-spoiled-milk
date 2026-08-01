package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict, atomic import/rollback receipt used as the recovery authority. */
final class WorldBuilderImportReceipt {
	private static final DateTimeFormatter ID_TIME = DateTimeFormatter
		.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);
	final String transactionId;
	final String status;
	final String transactionType;
	final String importMode;
	final String createdAtUtc;
	final String layoutAdapter;
	final String targetIdentitySha256;
	final String targetFingerprintBeforeSha256;
	final String exportManifestSha256;
	final String selectedConfig;
	final String revertsTransactionId;
	final String layeredPackageManifestSha256;
	final String layeredPackageFingerprintSha256;
	final List<FileRecord> files;

	private WorldBuilderImportReceipt(String transactionId, String status,
		String transactionType, String importMode, String createdAtUtc, String layoutAdapter,
		String targetIdentitySha256, String targetFingerprintBeforeSha256,
		String exportManifestSha256, String selectedConfig, String revertsTransactionId,
		String layeredPackageManifestSha256,
		String layeredPackageFingerprintSha256, List<FileRecord> files) {
		this.transactionId = transactionId;
		this.status = status;
		this.transactionType = transactionType;
		this.importMode = importMode;
		this.createdAtUtc = createdAtUtc;
		this.layoutAdapter = layoutAdapter;
		this.targetIdentitySha256 = targetIdentitySha256;
		this.targetFingerprintBeforeSha256 = targetFingerprintBeforeSha256;
		this.exportManifestSha256 = exportManifestSha256;
		this.selectedConfig = selectedConfig;
		this.revertsTransactionId = revertsTransactionId;
		this.layeredPackageManifestSha256 = layeredPackageManifestSha256;
		this.layeredPackageFingerprintSha256 = layeredPackageFingerprintSha256;
		this.files = java.util.Collections.unmodifiableList(
			new ArrayList<FileRecord>(files));
	}

	static WorldBuilderImportReceipt pendingImport(Path target,
		WorldBuilderImporter.ImportPlan plan) {
		String transactionId = ID_TIME.format(Instant.now()) + "-"
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		List<FileRecord> files = new ArrayList<FileRecord>();
		for (WorldBuilderImporter.Action action : plan.actions) {
			String backup = action.existedBefore
				? "backups/" + transactionId + "/before/" + action.relativePath : "";
			files.add(new FileRecord(action.relativePath, action.existedBefore,
				action.beforeSha256, true, action.installedSha256, backup));
		}
		return new WorldBuilderImportReceipt(transactionId, "pending", "import",
			plan.importMode,
			Instant.now().toString(), WorldBuilderDiscovery.LAYOUT_ADAPTER,
			targetIdentity(target), plan.sourceFingerprint, plan.exportManifestSha256,
			plan.selectedConfig, "", plan.layeredPackageManifestSha256,
			plan.layeredPackageFingerprintSha256, files);
	}

	static WorldBuilderImportReceipt pendingRollback(Path target,
		WorldBuilderImportReceipt imported) {
		String transactionId = ID_TIME.format(Instant.now()) + "-"
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		List<FileRecord> files = new ArrayList<FileRecord>();
		for (FileRecord importedFile : imported.files) {
			String backup = importedFile.installedPresent
				? "backups/" + transactionId + "/before/" + importedFile.relativePath : "";
			files.add(new FileRecord(importedFile.relativePath,
				importedFile.installedPresent, importedFile.installedSha256,
				importedFile.existedBefore, importedFile.beforeSha256, backup));
		}
		return new WorldBuilderImportReceipt(transactionId, "pending", "rollback",
			imported.importMode, Instant.now().toString(), imported.layoutAdapter,
			targetIdentity(target),
			stateFingerprint(imported.files, true), imported.exportManifestSha256,
			imported.selectedConfig, imported.transactionId,
			imported.layeredPackageManifestSha256,
			imported.layeredPackageFingerprintSha256, files);
	}

	WorldBuilderImportReceipt withStatus(String newStatus) {
		return new WorldBuilderImportReceipt(transactionId, newStatus, transactionType,
			importMode, createdAtUtc, layoutAdapter, targetIdentitySha256,
			targetFingerprintBeforeSha256,
			exportManifestSha256, selectedConfig, revertsTransactionId,
			layeredPackageManifestSha256, layeredPackageFingerprintSha256, files);
	}

	static String targetIdentity(Path target) {
		return WorldBuilderHashes.sha256(
			target.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String stateFingerprint(List<FileRecord> files, boolean installedState) {
		java.security.MessageDigest digest = WorldBuilderHashes.newDigest();
		for (FileRecord file : files) {
			WorldBuilderHashes.updateText(digest, file.relativePath);
			boolean present = installedState ? file.installedPresent : file.existedBefore;
			String hash = installedState ? file.installedSha256 : file.beforeSha256;
			WorldBuilderHashes.updateText(digest, Boolean.toString(present));
			WorldBuilderHashes.updateText(digest, hash);
		}
		return WorldBuilderHashes.hex(digest.digest());
	}

	Path receiptPath(Path workspace) {
		return workspace.resolve("receipts").resolve(transactionId + ".json");
	}

	void write(Path workspace) throws IOException, WorldBuilderDiscoveryException {
		Path receipts = workspace.resolve("receipts").normalize();
		if (!receipts.startsWith(workspace)) {
			throw new WorldBuilderDiscoveryException("Receipt directory escapes the workspace.");
		}
		Files.createDirectories(receipts);
		if (Files.isSymbolicLink(receipts)) {
			throw new WorldBuilderDiscoveryException("Receipt directory is unsafe.");
		}
		Path destination = receiptPath(workspace).normalize();
		Path temporary = receipts.resolve("." + transactionId + ".tmp-" + UUID.randomUUID());
		Files.write(temporary, toJson().getBytes(StandardCharsets.UTF_8));
		try {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		WorldBuilderImportReceipt written = read(destination);
		if (!transactionId.equals(written.transactionId) || !status.equals(written.status)) {
			throw new WorldBuilderDiscoveryException("Written transaction receipt did not verify.");
		}
	}

	static WorldBuilderImportReceipt read(Path path)
		throws IOException, WorldBuilderDiscoveryException {
		Map<String, Object> root = WorldBuilderJsonDocuments.readObject(path);
		long schema = integer(root, "schemaVersion");
		if (schema == 1) {
			exactKeys(root, "schemaVersion", "manifestType", "transactionId", "status",
				"transactionType", "createdAtUtc", "layoutAdapter", "targetIdentitySha256",
				"targetFingerprintBeforeSha256", "exportManifestSha256", "selectedConfig",
				"revertsTransactionId", "files");
		} else if (schema == 2) {
			exactKeys(root, "schemaVersion", "manifestType", "transactionId", "status",
				"transactionType", "importMode", "createdAtUtc", "layoutAdapter",
				"targetIdentitySha256", "targetFingerprintBeforeSha256",
				"exportManifestSha256", "selectedConfig", "revertsTransactionId",
				"layeredPackageManifestSha256",
				"layeredPackageFingerprintSha256", "files");
		} else {
			throw new WorldBuilderDiscoveryException("Import receipt identity is invalid.");
		}
		if (!"world-builder-import-receipt".equals(string(root, "manifestType"))) {
			throw new WorldBuilderDiscoveryException("Import receipt identity is invalid.");
		}
		String transactionId = string(root, "transactionId");
		String status = string(root, "status");
		String type = string(root, "transactionType");
		String importMode = schema == 1 ? "legacy-files-v1" : string(root, "importMode");
		String created = string(root, "createdAtUtc");
		String layout = string(root, "layoutAdapter");
		String targetIdentity = hash(root, "targetIdentitySha256");
		String targetBefore = hash(root, "targetFingerprintBeforeSha256");
		String exportManifest = hash(root, "exportManifestSha256");
		String selectedConfig = string(root, "selectedConfig");
		String reverts = string(root, "revertsTransactionId");
		String layeredManifest = schema == 1 ? ""
			: hash(root, "layeredPackageManifestSha256");
		String layeredFingerprint = schema == 1 ? ""
			: hash(root, "layeredPackageFingerprintSha256");
		if (!transactionId.matches("[A-Za-z0-9._-]+")
			|| !Arrays.asList("pending", "successful", "rolled-back", "reverted").contains(status)
			|| !Arrays.asList("import", "rollback").contains(type)
			|| !(schema == 1 ? "legacy-files-v1".equals(importMode)
				: WorldBuilderLayeredImportConfiguration.IMPORT_MODE.equals(importMode))
			|| !WorldBuilderDiscovery.LAYOUT_ADAPTER.equals(layout)
			|| !selectedConfig.matches("server/[A-Za-z0-9._/-]+\\.conf")
			|| selectedConfig.contains("..")
			|| !(reverts.isEmpty() || reverts.matches("[A-Za-z0-9._-]+"))) {
			throw new WorldBuilderDiscoveryException("Import receipt metadata is invalid.");
		}
		try {
			Instant.parse(created);
		} catch (RuntimeException invalidTime) {
			throw new WorldBuilderDiscoveryException("Import receipt timestamp is invalid.");
		}
		Object listed = root.get("files");
		int maximumFiles = schema == 1 ? 7 : 65_540;
		if (!(listed instanceof List) || ((List<?>)listed).isEmpty()
			|| ((List<?>)listed).size() > maximumFiles) {
			throw new WorldBuilderDiscoveryException("Import receipt file inventory is invalid.");
		}
		List<FileRecord> files = new ArrayList<FileRecord>();
		Set<String> paths = new HashSet<String>();
		for (Object item : (List<?>)listed) {
			if (!(item instanceof Map)) {
				throw new WorldBuilderDiscoveryException("Import receipt file record is invalid.");
			}
			@SuppressWarnings("unchecked") Map<String, Object> record = (Map<String, Object>)item;
			exactKeys(record, "relativePath", "existedBefore", "beforeSha256",
				"installedPresent", "installedSha256", "backupRelativePath");
			String relative = string(record, "relativePath");
			boolean existed = bool(record, "existedBefore");
			String before = optionalHash(record, "beforeSha256");
			boolean installed = bool(record, "installedPresent");
			String installedSha = optionalHash(record, "installedSha256");
			String backup = string(record, "backupRelativePath");
			if (!safeRelative(relative) || !paths.add(relative)
				|| existed != !before.isEmpty() || installed != !installedSha.isEmpty()
				|| existed != !backup.isEmpty()
				|| (!backup.isEmpty() && (!safeRelative(backup)
					|| !backup.startsWith("backups/" + transactionId + "/before/")))) {
				throw new WorldBuilderDiscoveryException("Import receipt file state is invalid.");
			}
			files.add(new FileRecord(relative, existed, before, installed, installedSha, backup));
		}
		return new WorldBuilderImportReceipt(transactionId, status, type, importMode,
			created, layout, targetIdentity, targetBefore, exportManifest,
			selectedConfig, reverts, layeredManifest, layeredFingerprint, files);
	}

	String toJson() {
		boolean layered = WorldBuilderLayeredImportConfiguration.IMPORT_MODE.equals(importMode);
		StringBuilder json = new StringBuilder(2048 + files.size() * 384);
		json.append("{\n  \"schemaVersion\": ").append(layered ? 2 : 1).append(",\n")
			.append("  \"manifestType\": \"world-builder-import-receipt\",\n")
			.append("  \"transactionId\": \"").append(transactionId).append("\",\n")
			.append("  \"status\": \"").append(status).append("\",\n")
			.append("  \"transactionType\": \"").append(transactionType).append("\",\n");
		if (layered) {
			json.append("  \"importMode\": \"").append(importMode).append("\",\n");
		}
		json.append("  \"createdAtUtc\": \"").append(createdAtUtc).append("\",\n")
			.append("  \"layoutAdapter\": \"").append(layoutAdapter).append("\",\n")
			.append("  \"targetIdentitySha256\": \"").append(targetIdentitySha256).append("\",\n")
			.append("  \"targetFingerprintBeforeSha256\": \"")
			.append(targetFingerprintBeforeSha256).append("\",\n")
			.append("  \"exportManifestSha256\": \"").append(exportManifestSha256).append("\",\n")
			.append("  \"selectedConfig\": \"").append(escape(selectedConfig)).append("\",\n")
			.append("  \"revertsTransactionId\": \"").append(revertsTransactionId).append("\",\n");
		if (layered) {
			json.append("  \"layeredPackageManifestSha256\": \"")
				.append(layeredPackageManifestSha256).append("\",\n")
				.append("  \"layeredPackageFingerprintSha256\": \"")
				.append(layeredPackageFingerprintSha256).append("\",\n");
		}
		json.append("  \"files\": [\n");
		for (int index = 0; index < files.size(); index++) {
			FileRecord file = files.get(index);
			json.append("    {\"relativePath\": \"").append(escape(file.relativePath))
				.append("\", \"existedBefore\": ").append(file.existedBefore)
				.append(", \"beforeSha256\": \"").append(file.beforeSha256)
				.append("\", \"installedPresent\": ").append(file.installedPresent)
				.append(", \"installedSha256\": \"").append(file.installedSha256)
				.append("\", \"backupRelativePath\": \"")
				.append(escape(file.backupRelativePath)).append("\"}")
				.append(index + 1 < files.size() ? "," : "").append('\n');
		}
		json.append("  ]\n}\n");
		return json.toString();
	}

	private static boolean safeRelative(String value) {
		if (value.isEmpty() || value.startsWith("/") || value.contains("\\")
			|| value.contains("..")) {
			return false;
		}
		Path path = java.nio.file.Paths.get(value).normalize();
		return !path.isAbsolute() && !path.startsWith("..");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
			.replace("\n", "\\n").replace("\r", "\\r");
	}

	private static void exactKeys(Map<String, Object> object, String... names)
		throws WorldBuilderDiscoveryException {
		Set<String> expected = new HashSet<String>(Arrays.asList(names));
		if (object.size() != expected.size() || !object.keySet().equals(expected)) {
			throw new WorldBuilderDiscoveryException(
				"Import receipt contains missing or unexpected fields.");
		}
	}

	private static String string(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof String)) {
			throw new WorldBuilderDiscoveryException("Import receipt field is invalid: " + key);
		}
		return (String)value;
	}

	private static long integer(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof Long)) {
			throw new WorldBuilderDiscoveryException("Import receipt integer is invalid: " + key);
		}
		return ((Long)value).longValue();
	}

	private static boolean bool(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof Boolean)) {
			throw new WorldBuilderDiscoveryException("Import receipt boolean is invalid: " + key);
		}
		return ((Boolean)value).booleanValue();
	}

	private static String hash(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		String value = string(object, key);
		if (!value.matches("[0-9a-f]{64}")) {
			throw new WorldBuilderDiscoveryException("Import receipt hash is invalid: " + key);
		}
		return value;
	}

	private static String optionalHash(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		String value = string(object, key);
		if (!(value.isEmpty() || value.matches("[0-9a-f]{64}"))) {
			throw new WorldBuilderDiscoveryException("Import receipt hash is invalid: " + key);
		}
		return value;
	}

	static final class FileRecord {
		final String relativePath;
		final boolean existedBefore;
		final String beforeSha256;
		final boolean installedPresent;
		final String installedSha256;
		final String backupRelativePath;

		FileRecord(String relativePath, boolean existedBefore, String beforeSha256,
			boolean installedPresent, String installedSha256, String backupRelativePath) {
			this.relativePath = relativePath;
			this.existedBefore = existedBefore;
			this.beforeSha256 = beforeSha256;
			this.installedPresent = installedPresent;
			this.installedSha256 = installedSha256;
			this.backupRelativePath = backupRelativePath;
		}
	}
}
