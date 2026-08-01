package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict reader for legacy five-file and layered package export manifests. */
final class WorldBuilderExportManifest {
	static final String LEGACY_TYPE = "world-builder-export";
	static final String LAYERED_TYPE = "world-builder-layered-export";
	private static final String LAYERED_BUNDLE_PREFIX =
		"authored/layered-world/package/";
	private static final Map<String, String> CANONICAL_FILES = canonicalFiles();

	final String manifestType;
	final String builderVersion, sourceCommit, layoutAdapter;
	final String sourceFingerprint, contentFingerprint;
	final String layeredSourceManifestSha256;
	final String layeredSourcePackageFingerprintSha256;
	final String layeredPackageManifestSha256;
	final String layeredPackageFingerprintSha256;
	final List<FileRecord> files;
	final int changedFileCount;
	final boolean terrainChanged, sceneryChanged, npcChanged;

	private WorldBuilderExportManifest(String manifestType, String builderVersion,
		String sourceCommit, String layoutAdapter, String sourceFingerprint,
		String contentFingerprint, String layeredSourceManifestSha256,
		String layeredSourcePackageFingerprintSha256,
		String layeredPackageManifestSha256,
		String layeredPackageFingerprintSha256, List<FileRecord> files,
		int changedFileCount, boolean terrainChanged, boolean sceneryChanged,
		boolean npcChanged) {
		this.manifestType = manifestType;
		this.builderVersion = builderVersion;
		this.sourceCommit = sourceCommit;
		this.layoutAdapter = layoutAdapter;
		this.sourceFingerprint = sourceFingerprint;
		this.contentFingerprint = contentFingerprint;
		this.layeredSourceManifestSha256 = layeredSourceManifestSha256;
		this.layeredSourcePackageFingerprintSha256 =
			layeredSourcePackageFingerprintSha256;
		this.layeredPackageManifestSha256 = layeredPackageManifestSha256;
		this.layeredPackageFingerprintSha256 = layeredPackageFingerprintSha256;
		this.files = java.util.Collections.unmodifiableList(
			new ArrayList<FileRecord>(files));
		this.changedFileCount = changedFileCount;
		this.terrainChanged = terrainChanged;
		this.sceneryChanged = sceneryChanged;
		this.npcChanged = npcChanged;
	}

	boolean isLayered() {
		return LAYERED_TYPE.equals(manifestType);
	}

	static WorldBuilderExportManifest read(Path path)
		throws IOException, WorldBuilderDiscoveryException {
		Map<String, Object> root = WorldBuilderJsonDocuments.readObject(path);
		if (integer(root, "schemaVersion") != 1) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest schema version is invalid.");
		}
		String type = string(root, "manifestType");
		if (LEGACY_TYPE.equals(type)) return readLegacy(root);
		if (LAYERED_TYPE.equals(type)) return readLayered(root);
		throw new WorldBuilderDiscoveryException("Export manifest identity is invalid.");
	}

	private static WorldBuilderExportManifest readLegacy(Map<String, Object> root)
		throws WorldBuilderDiscoveryException {
		exactKeys(root, "schemaVersion", "manifestType", "builderVersion",
			"sourceCommit", "layoutAdapter", "sourceFingerprintSha256",
			"contentFingerprintSha256", "files", "changeSummary");
		Provenance provenance = provenance(root);
		Object rawFiles = root.get("files");
		if (!(rawFiles instanceof List) || ((List<?>)rawFiles).size() != 5) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest must contain five files.");
		}
		List<FileRecord> files = new ArrayList<FileRecord>();
		Set<String> logicalNames = new HashSet<String>();
		Set<String> paths = new HashSet<String>();
		for (Object item : (List<?>)rawFiles) {
			FileRecord record = fileRecord(item);
			if (!record.bundlePath.equals(CANONICAL_FILES.get(record.logicalName))
				|| !logicalNames.add(record.logicalName)
				|| !paths.add(record.bundlePath)) {
				throw new WorldBuilderDiscoveryException(
					"Export file record is unsafe, noncanonical, or duplicated.");
			}
			files.add(record);
		}
		if (!logicalNames.equals(CANONICAL_FILES.keySet())) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest file inventory is incomplete.");
		}
		LegacyChanges changes = legacyChanges(root.get("changeSummary"), files);
		return new WorldBuilderExportManifest(LEGACY_TYPE, provenance.version,
			provenance.commit, provenance.layout, provenance.source,
			provenance.content, "", "", "", "", files, changes.changed,
			changes.terrain, changes.scenery, changes.npc);
	}

	private static WorldBuilderExportManifest readLayered(Map<String, Object> root)
		throws WorldBuilderDiscoveryException {
		exactKeys(root, "schemaVersion", "manifestType", "builderVersion",
			"sourceCommit", "layoutAdapter", "sourceFingerprintSha256",
			"contentFingerprintSha256", "layeredSourceManifestSha256",
			"layeredSourcePackageFingerprintSha256",
			"layeredPackageManifestSha256", "layeredPackageFingerprintSha256",
			"files", "changeSummary");
		Provenance provenance = provenance(root);
		if (!WorldBuilderLayeredPackage.ADAPTER_ID.equals(provenance.layout)) {
			throw new WorldBuilderDiscoveryException(
				"Layered export adapter identity is invalid.");
		}
		String sourceManifest = hash(root, "layeredSourceManifestSha256");
		String sourcePackage = hash(root, "layeredSourcePackageFingerprintSha256");
		String packageManifest = hash(root, "layeredPackageManifestSha256");
		String packageFingerprint = hash(root, "layeredPackageFingerprintSha256");
		Object rawFiles = root.get("files");
		if (!(rawFiles instanceof List) || ((List<?>)rawFiles).size() < 2
			|| ((List<?>)rawFiles).size() > 65_538) {
			throw new WorldBuilderDiscoveryException(
				"Layered export file inventory is outside 2..65538.");
		}
		List<FileRecord> files = new ArrayList<FileRecord>();
		Set<String> logicalNames = new HashSet<String>();
		Set<String> paths = new HashSet<String>();
		FileRecord packageManifestRecord = null;
		int actualChanged = 0;
		int actualAdded = 0;
		int actualReplaced = 0;
		for (Object item : (List<?>)rawFiles) {
			FileRecord record = fileRecord(item);
			String logical = normalizedRelative(record.logicalName);
			if (!logical.equals(record.logicalName)
				|| !record.bundlePath.equals(LAYERED_BUNDLE_PREFIX + logical)
				|| !logicalNames.add(logical) || !paths.add(record.bundlePath)) {
				throw new WorldBuilderDiscoveryException(
					"Layered export file record is unsafe, noncanonical, or duplicated.");
			}
			if (record.changed) {
				actualChanged++;
				if (record.sourcePresent) actualReplaced++; else actualAdded++;
			}
			if ("manifest.json".equals(logical)) packageManifestRecord = record;
			files.add(record);
		}
		if (packageManifestRecord == null
			|| !packageManifestRecord.sha256.equals(packageManifest)
			|| !packageManifestRecord.sourcePresent
			|| !packageManifestRecord.sourceSha256.equals(sourceManifest)
			|| !packageManifestRecord.changed) {
			throw new WorldBuilderDiscoveryException(
				"Layered export package manifest state is inconsistent.");
		}
		Map<String, Object> changes = object(root.get("changeSummary"));
		exactKeys(changes, "changedFileCount", "addedFileCount", "replacedFileCount");
		int changed = count(changes, "changedFileCount", files.size());
		int added = count(changes, "addedFileCount", files.size());
		int replaced = count(changes, "replacedFileCount", files.size());
		if (changed < 1 || changed != actualChanged || added != actualAdded
			|| replaced != actualReplaced || changed != added + replaced) {
			throw new WorldBuilderDiscoveryException(
				"Layered export change summary is inconsistent.");
		}
		return new WorldBuilderExportManifest(LAYERED_TYPE, provenance.version,
			provenance.commit, provenance.layout, provenance.source,
			provenance.content, sourceManifest, sourcePackage, packageManifest,
			packageFingerprint, files, changed, true, true, true);
	}

	private static Provenance provenance(Map<String, Object> root)
		throws WorldBuilderDiscoveryException {
		String version = string(root, "builderVersion");
		String commit = string(root, "sourceCommit");
		String layout = string(root, "layoutAdapter");
		if (version.isEmpty() || version.length() > 64
			|| !commit.matches("[0-9a-f]{40}") || layout.isEmpty()) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest provenance is invalid.");
		}
		return new Provenance(version, commit, layout,
			hash(root, "sourceFingerprintSha256"),
			hash(root, "contentFingerprintSha256"));
	}

	private static FileRecord fileRecord(Object item)
		throws WorldBuilderDiscoveryException {
		if (!(item instanceof Map)) {
			throw new WorldBuilderDiscoveryException("Export file record is invalid.");
		}
		@SuppressWarnings("unchecked") Map<String, Object> record =
			(Map<String, Object>)item;
		exactKeys(record, "logicalName", "bundlePath", "size", "sha256",
			"sourcePresent", "sourceSha256", "changed");
		String logical = string(record, "logicalName");
		String bundle = string(record, "bundlePath");
		String sha = hash(record, "sha256");
		long size = integer(record, "size");
		boolean sourcePresent = bool(record, "sourcePresent");
		boolean changed = bool(record, "changed");
		String sourceSha = string(record, "sourceSha256");
		if (sourcePresent ? !sourceSha.matches("[0-9a-f]{64}") : !sourceSha.isEmpty()) {
			throw new WorldBuilderDiscoveryException(
				"Export source-file state is invalid.");
		}
		Path relative = Paths.get(bundle).normalize();
		if (logical.isEmpty() || size < 0 || bundle.indexOf('\\') >= 0
			|| relative.isAbsolute() || relative.startsWith("..")) {
			throw new WorldBuilderDiscoveryException("Export file record is unsafe.");
		}
		if (sourcePresent && changed == sourceSha.equals(sha)) {
			throw new WorldBuilderDiscoveryException(
				"Export file change state is inconsistent.");
		}
		return new FileRecord(logical, bundle, size, sha, sourcePresent,
			sourceSha, changed);
	}

	private static LegacyChanges legacyChanges(Object value, List<FileRecord> files)
		throws WorldBuilderDiscoveryException {
		Map<String, Object> changes = object(value);
		exactKeys(changes, "changedFileCount", "terrainChanged",
			"sceneryChanged", "npcChanged");
		int changed = count(changes, "changedFileCount", 5);
		int actualChanged = 0;
		boolean terrain = false, scenery = false, npc = false;
		for (FileRecord file : files) {
			if (!file.changed) continue;
			actualChanged++;
			if ("terrain".equals(file.logicalName)) terrain = true;
			else if (file.logicalName.startsWith("scenery")) scenery = true;
			else npc = true;
		}
		boolean listedTerrain = bool(changes, "terrainChanged");
		boolean listedScenery = bool(changes, "sceneryChanged");
		boolean listedNpc = bool(changes, "npcChanged");
		if (changed != actualChanged || terrain != listedTerrain
			|| scenery != listedScenery || npc != listedNpc) {
			throw new WorldBuilderDiscoveryException(
				"Export change summary is inconsistent.");
		}
		return new LegacyChanges(changed, terrain, scenery, npc);
	}

	private static int count(Map<String, Object> object, String key, int maximum)
		throws WorldBuilderDiscoveryException {
		long value = integer(object, key);
		if (value < 0 || value > maximum) {
			throw new WorldBuilderDiscoveryException(
				"Export changed-file count is invalid.");
		}
		return (int)value;
	}

	private static String normalizedRelative(String value)
		throws WorldBuilderDiscoveryException {
		if (value == null || value.isEmpty() || value.indexOf('\\') >= 0) {
			throw new WorldBuilderDiscoveryException("Layered export path is invalid.");
		}
		Path relative = Paths.get(value).normalize();
		String normalized = relative.toString().replace('\\', '/');
		if (relative.isAbsolute() || relative.startsWith("..")
			|| !normalized.equals(value)) {
			throw new WorldBuilderDiscoveryException(
				"Layered export path is not normalized: " + value);
		}
		return normalized;
	}

	private static Map<String, Object> object(Object value)
		throws WorldBuilderDiscoveryException {
		if (!(value instanceof Map)) {
			throw new WorldBuilderDiscoveryException("Export change summary is invalid.");
		}
		@SuppressWarnings("unchecked") Map<String, Object> result =
			(Map<String, Object>)value;
		return result;
	}

	private static void exactKeys(Map<String, Object> object, String... keys)
		throws WorldBuilderDiscoveryException {
		Set<String> expected = new HashSet<String>(Arrays.asList(keys));
		if (object.size() != expected.size() || !object.keySet().equals(expected)) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest contains missing or unexpected fields.");
		}
	}

	private static String string(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof String)) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest field is not a string: " + key);
		}
		return (String)value;
	}

	private static String hash(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		String value = string(object, key);
		if (!value.matches("[0-9a-f]{64}")) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest hash is invalid: " + key);
		}
		return value;
	}

	private static long integer(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof Long)) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest field is not an integer: " + key);
		}
		return ((Long)value).longValue();
	}

	private static boolean bool(Map<String, Object> object, String key)
		throws WorldBuilderDiscoveryException {
		Object value = object.get(key);
		if (!(value instanceof Boolean)) {
			throw new WorldBuilderDiscoveryException(
				"Export manifest field is not boolean: " + key);
		}
		return ((Boolean)value).booleanValue();
	}

	private static Map<String, String> canonicalFiles() {
		Map<String, String> files = new LinkedHashMap<String, String>();
		files.put("terrain", "authored/Custom_Landscape.orsc");
		files.put("sceneryLocs", "authored/MyWorldSceneryLocs.json");
		files.put("sceneryRemovals", "authored/MyWorldSceneryRemovals.json");
		files.put("npcLocs", "authored/MyWorldNpcLocs.json");
		files.put("npcRemovals", "authored/MyWorldNpcRemovals.json");
		return java.util.Collections.unmodifiableMap(files);
	}

	static final class FileRecord {
		final String logicalName, bundlePath, sha256, sourceSha256;
		final long size;
		final boolean sourcePresent, changed;

		FileRecord(String logical, String bundle, long size, String sha,
			boolean sourcePresent, String sourceSha, boolean changed) {
			logicalName = logical;
			bundlePath = bundle;
			this.size = size;
			sha256 = sha;
			this.sourcePresent = sourcePresent;
			sourceSha256 = sourceSha;
			this.changed = changed;
		}
	}

	private static final class Provenance {
		final String version, commit, layout, source, content;

		Provenance(String version, String commit, String layout,
			String source, String content) {
			this.version = version;
			this.commit = commit;
			this.layout = layout;
			this.source = source;
			this.content = content;
		}
	}

	private static final class LegacyChanges {
		final int changed;
		final boolean terrain, scenery, npc;

		LegacyChanges(int changed, boolean terrain, boolean scenery, boolean npc) {
			this.changed = changed;
			this.terrain = terrain;
			this.scenery = scenery;
			this.npc = npc;
		}
	}
}
