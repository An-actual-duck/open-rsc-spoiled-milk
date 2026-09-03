package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Subspace;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.SpriteArchive.Workspace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Client-side verifier and path router for project-content-bundle-v1/v2/v3. */
public final class ProjectContentBundle {
	public static final String CAPABILITY_ID_V3 = "project-local-custom-content-v3";
	public static final String CAPABILITY_ID = "project-local-custom-content-v2";
	public static final String CAPABILITY_ID_V1 = "project-local-custom-content-v1";
	private static final String MANIFEST_TYPE = "world-builder-project-content-bundle";
	private static final String ZERO_HASH =
		"0000000000000000000000000000000000000000000000000000000000000000";
	private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
	private static final long MAX_FILE = 256L * 1024L * 1024L;
	private static final List<Spec> SPECS_V1 = specs(false);
	private static final List<Spec> SPECS_V2 = specs(true);
	private static final List<Spec> SPECS_V3 = specs(3);
	private static final ProjectContentBundle EMPTY =
		new ProjectContentBundle(null, null, Collections.<String, Path>emptyMap(),
			Collections.<Integer, ItemVisual>emptyMap(),
			Collections.<Integer, Sprite>emptyMap(),
			Collections.<Integer, ProjectNpcAnimationRegistry.EntryDef>emptyMap(),
			0, ZERO_HASH);

	private final Path root;
	private final JSONObject catalog;
	private final Map<String, Path> paths;
	private final Map<Integer, ItemVisual> itemVisuals;
	private final Map<Integer, Sprite> itemSprites;
	private final Map<Integer, ProjectNpcAnimationRegistry.EntryDef> npcAnimations;
	private final int schemaVersion;
	private final String itemVisualSha256;

	private ProjectContentBundle(Path root, JSONObject catalog,
		Map<String, Path> paths, Map<Integer, ItemVisual> itemVisuals,
		Map<Integer, Sprite> itemSprites,
		Map<Integer, ProjectNpcAnimationRegistry.EntryDef> npcAnimations,
		int schemaVersion,
		String itemVisualSha256) {
		this.root = root;
		this.catalog = catalog;
		this.paths = Collections.unmodifiableMap(
			new LinkedHashMap<String, Path>(paths));
		this.itemVisuals = Collections.unmodifiableMap(
			new LinkedHashMap<Integer, ItemVisual>(itemVisuals));
		this.itemSprites = Collections.unmodifiableMap(
			new LinkedHashMap<Integer, Sprite>(itemSprites));
		this.npcAnimations = Collections.unmodifiableMap(
			new LinkedHashMap<Integer, ProjectNpcAnimationRegistry.EntryDef>(npcAnimations));
		this.schemaVersion = schemaVersion;
		this.itemVisualSha256 = itemVisualSha256;
	}

	public static ProjectContentBundle empty() { return EMPTY; }

	public static ProjectContentBundle load(
		Path workspaceRoot, String requestedPath, String capability,
		String expectedBundle, String expectedDefinitions, String expectedAssets)
		throws IOException {
		return load(workspaceRoot, requestedPath, capability, expectedBundle,
			expectedDefinitions, expectedAssets,
			CAPABILITY_ID_V1.equals(trim(capability)) ? ZERO_HASH : "");
	}

	public static ProjectContentBundle load(
		Path workspaceRoot, String requestedPath, String capability,
		String expectedBundle, String expectedDefinitions, String expectedAssets,
		String expectedItemVisuals) throws IOException {
		String requested = trim(requestedPath);
		if (requested.isEmpty()) {
			if (!trim(capability).isEmpty() || !trim(expectedBundle).isEmpty()
				|| !trim(expectedDefinitions).isEmpty() || !trim(expectedAssets).isEmpty()
				|| !trim(expectedItemVisuals).isEmpty()) {
				throw new IOException("Content identities require a bundle path");
			}
			return EMPTY;
		}
		boolean v3 = CAPABILITY_ID_V3.equals(capability);
		boolean v2 = CAPABILITY_ID.equals(capability);
		boolean successor = v2 || v3;
		if ((!successor && !CAPABILITY_ID_V1.equals(capability))
			|| !SHA.matcher(expectedBundle).matches()
			|| !SHA.matcher(expectedDefinitions).matches()
			|| !SHA.matcher(expectedAssets).matches()
			|| !SHA.matcher(expectedItemVisuals).matches()
			|| (!successor && !ZERO_HASH.equals(expectedItemVisuals))) {
			throw new IOException("Project content launch identities are invalid");
		}
		Path expected = workspaceRoot.resolve("working/content-bundle")
			.toAbsolutePath().normalize();
		Path root = java.nio.file.Paths.get(requested);
		if (!root.isAbsolute()) root = workspaceRoot.resolve(root);
		root = root.toAbsolutePath().normalize();
		if (!root.equals(expected) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(root)) {
			throw new IOException("Project content must be working/content-bundle");
		}
		root = root.toRealPath();
		Path manifestPath = safeFile(root.resolve("manifest.json"), 16L * 1024L * 1024L);
		String document = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
		StrictJsonScanner.validate(document);
		JSONObject manifest = new JSONObject(document);
		requireKeys(manifest, successor ? set(
			"schemaVersion", "manifestType", "capabilityId", "sourceKind",
			"definitionCatalog", "itemVisuals", "familyBindings", "files",
			"definitionFingerprintSha256", "assetFingerprintSha256",
			"itemVisualFingerprintSha256", "bundleFingerprintSha256") : set(
			"schemaVersion", "manifestType", "capabilityId", "sourceKind",
			"definitionCatalog", "familyBindings", "files",
			"definitionFingerprintSha256", "assetFingerprintSha256",
			"bundleFingerprintSha256"));
		expectInt(manifest, "schemaVersion", v3 ? 3 : v2 ? 2 : 1);
		expect(manifest, "manifestType", MANIFEST_TYPE);
		expect(manifest, "capabilityId", capability);
		expect(manifest, "sourceKind", "target-adopted");
		JSONObject catalog = manifest.getJSONObject("definitionCatalog");
		List<Integer> groundItems = validateCatalog(catalog, successor);
		Map<Integer, ItemVisual> itemVisuals = successor
			? validateItemVisuals(manifest.getJSONArray("itemVisuals"), groundItems)
			: Collections.<Integer, ItemVisual>emptyMap();
		validateBindings(manifest.getJSONArray("familyBindings"));

		JSONArray rows = manifest.getJSONArray("files");
		List<Spec> specs = v3 ? SPECS_V3 : v2 ? SPECS_V2 : SPECS_V1;
		if (rows.length() != specs.size()) throw new IOException("Content inventory is incomplete");
		Map<String, Path> paths = new LinkedHashMap<String, Path>();
		List<JSONObject> records = new ArrayList<JSONObject>();
		long total = 0L;
		for (int index = 0; index < specs.size(); index++) {
			Spec spec = specs.get(index); JSONObject row = rows.getJSONObject(index);
			requireKeys(row, set("role", "bundleRelativePath", "runtimeRelativePath",
				"mediaType", "size", "sha256"));
			expect(row, "role", spec.role); expect(row, "runtimeRelativePath", spec.path);
			expect(row, "bundleRelativePath", "files/" + spec.path);
			expect(row, "mediaType", spec.media);
			long size = row.getLong("size"); String hash = row.getString("sha256");
			if (size < 1L || size > MAX_FILE || !SHA.matcher(hash).matches()) {
				throw new IOException("Content file metadata is outside its bound");
			}
			total += size; if (total > 1024L * 1024L * 1024L) throw new IOException("Content bundle is too large");
			Path path = safeFile(root.resolve("files").resolve(spec.path), MAX_FILE);
			if (Files.size(path) != size || !hash.equals(sha256(path))) {
				throw new IOException("Content file fingerprint mismatch: " + spec.role);
			}
			paths.put(spec.role, path); records.add(row);
		}
		Set<Path> actual = new HashSet<Path>();
		try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
			stream.forEach(path -> {
				if (Files.isSymbolicLink(path)) throw new UnsafeBundle("Content bundle contains a link");
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) actual.add(path.toAbsolutePath().normalize());
			});
		} catch (UnsafeBundle failure) { throw new IOException(failure.getMessage()); }
		Set<Path> expectedFiles = new HashSet<Path>(paths.values()); expectedFiles.add(manifestPath);
		if (!actual.equals(expectedFiles)) throw new IOException("Content bundle contains extra files");
		validateNpcRegistry(paths, catalog);
		if (successor) {
			validateItemVisualEvidence(paths.get("metadata.item-visuals"), itemVisuals);
			validateItemVisualArchives(paths, itemVisuals);
		}
		Map<Integer, Sprite> itemSprites = successor
			? decodeItemVisuals(paths, itemVisuals)
			: Collections.<Integer, Sprite>emptyMap();
		Map<Integer, ProjectNpcAnimationRegistry.EntryDef> npcAnimations = v3
			? ProjectNpcAnimationRegistry.load(paths.get("metadata.npc-animations"),
				paths.get("asset.sprite.custom"), paths.get("asset.sprite.authentic"))
			: Collections.<Integer, ProjectNpcAnimationRegistry.EntryDef>emptyMap();

		String calculatedDefinition = fingerprint("world-builder-project-content-definitions-v1\n",
			records, specs, true,
			catalog.getString("catalogSha256"));
		String calculatedAssets = fingerprint("world-builder-project-content-assets-v1\n",
			records, specs, false, "");
		String definition = manifest.getString("definitionFingerprintSha256");
		String assets = manifest.getString("assetFingerprintSha256");
		// Bundle v2/v3 producer identities are opaque values authenticated by the
		// self-fingerprinted manifest; v1 retains its legacy record derivation.
		if (!SHA.matcher(definition).matches() || !SHA.matcher(assets).matches()
			|| (!successor && (!definition.equals(calculatedDefinition)
				|| !assets.equals(calculatedAssets)))) {
			throw new IOException("Content domain fingerprint mismatch");
		}
		JSONObject zero = new JSONObject(manifest.toString());
		zero.put("bundleFingerprintSha256", ZERO_HASH);
		String itemVisualHash = successor
			? manifest.getString("itemVisualFingerprintSha256") : ZERO_HASH;
		if (successor) {
			String calculated = sha256((
				"world-builder-project-content-item-visuals-v1\n"
					+ canonical(manifest.getJSONArray("itemVisuals")))
				.getBytes(StandardCharsets.UTF_8));
			if (!calculated.equals(itemVisualHash)) {
				throw new IOException("Content item visual fingerprint mismatch");
			}
		}
		String bundle = sha256(("world-builder-project-content-bundle-v"
			+ (v3 ? "3" : v2 ? "2" : "1") + "\n"
			+ canonical(zero)).getBytes(StandardCharsets.UTF_8));
		if (!bundle.equals(manifest.getString("bundleFingerprintSha256"))
			|| !bundle.equals(expectedBundle) || !definition.equals(expectedDefinitions)
			|| !assets.equals(expectedAssets)
			|| !itemVisualHash.equals(expectedItemVisuals)) {
			throw new IOException("Content identity differs between client and server");
		}
		return new ProjectContentBundle(root, catalog, paths, itemVisuals, itemSprites,
			npcAnimations, v3 ? 3 : v2 ? 2 : 1, itemVisualHash);
	}

	public boolean isPresent() { return root != null; }
	public JSONObject catalog() { return catalog; }
	public int schemaVersion() { return schemaVersion; }
	public String itemVisualSha256() { return itemVisualSha256; }
	public ItemVisual itemVisual(int itemId) { return itemVisuals.get(Integer.valueOf(itemId)); }
	public Map<Integer, ItemVisual> itemVisuals() { return itemVisuals; }
	public Map<Integer, ProjectNpcAnimationRegistry.EntryDef> npcAnimations() { return npcAnimations; }
	public Sprite itemSprite(int itemId) { return itemSprites.get(Integer.valueOf(itemId)); }
	public boolean hasAuthenticItemVisuals() {
		for (ItemVisual visual : itemVisuals.values()) if (visual.authenticSpriteId != null) return true;
		return false;
	}
	public int maximumAuthenticSpriteId() {
		int result = -1;
		for (ItemVisual visual : itemVisuals.values()) if (visual.authenticSpriteId != null) result = Math.max(result, visual.authenticSpriteId.intValue());
		return result;
	}
	public Sprite authenticItemSprite(int authenticSpriteId) throws IOException {
		for (ItemVisual visual : itemVisuals.values()) {
			if (visual.authenticSpriteId != null
				&& visual.authenticSpriteId.intValue() == authenticSpriteId) {
				return itemSprite(visual.itemId);
			}
		}
		throw new IOException("Authentic project item sprite ID is not mapped");
	}
	public Path path(String role) {
		Path path = paths.get(role);
		if (path == null) throw new IllegalArgumentException("Unknown project content role: " + role);
		return path;
	}
	public Path assetForRuntimePath(String runtimePath) {
		if (!isPresent()) return null;
		for (Spec spec : schemaVersion == 3 ? SPECS_V3
			: schemaVersion == 2 ? SPECS_V2 : SPECS_V1)
			if (!spec.definition && !spec.metadata && spec.path.equals(runtimePath)) return path(spec.role);
		return null;
	}

	private static List<Integer> validateCatalog(JSONObject catalog, boolean v2) throws IOException {
		requireKeys(catalog, set("schemaVersion", "manifestType", "catalogId", "tiles",
				"boundaries", "scenery", "npcs", "groundItems", "catalogSha256"));
		expectInt(catalog, "schemaVersion", 1);
		expect(catalog, "manifestType", "world-builder-definition-catalog");
		List<Integer> groundItems = null;
		for (String family : Arrays.asList("tiles", "boundaries", "scenery", "npcs", "groundItems")) {
			JSONArray ids = catalog.getJSONArray(family); int previous = -1;
			List<Integer> familyIds = new ArrayList<Integer>();
			boolean byteFamily = family.equals("tiles") || family.equals("boundaries");
			int maximumCount = byteFamily ? 255 : 65536;
			int maximumId = byteFamily ? 254 : 65535;
			if (ids.length() < 1 || ids.length() > maximumCount) throw new IOException("Catalog family is outside its runtime bound");
			for (int index = 0; index < ids.length(); index++) {
				int id = ids.getInt(index);
				if (id < 0 || id > maximumId || id <= previous
					|| ((family.equals("tiles") || family.equals("boundaries")
						|| (!v2 && family.equals("scenery"))) && id != index)) {
					throw new IOException("Catalog IDs are not canonical");
				}
				previous = id;
				familyIds.add(Integer.valueOf(id));
			}
			if (family.equals("groundItems")) groundItems = familyIds;
		}
		JSONObject zero = new JSONObject(catalog.toString()); zero.put("catalogSha256", ZERO_HASH);
		if (!catalog.getString("catalogSha256").equals(
				sha256(canonical(zero).getBytes(StandardCharsets.UTF_8)))) {
			throw new IOException("Catalog self fingerprint mismatch");
		}
		return groundItems;
	}

	private static void validateNpcRegistry(
		Map<String,Path> paths, JSONObject catalog) throws IOException {
		int count = definitionArray(paths.get("definition.npc.base"), "NPC base").length()
			+ definitionArray(paths.get("definition.npc.custom"), "NPC custom").length();
		if (count < 1 || count > 65536) {
			throw new IOException("Project NPC sequential registry is outside 1..65536");
		}
		JSONArray ids = catalog.getJSONArray("npcs");
		for (int index = 0; index < ids.length(); index++) {
			int id = ids.getInt(index);
			if (id < 0 || id >= count) {
				throw new IOException("Project NPC catalog ID " + id
					+ " is not backed by the sequential definition registry");
			}
		}
		for (String role : Arrays.asList("definition.npc.patch", "definition.npc.world")) {
			JSONArray rows = definitionArray(paths.get(role), role);
			for (int index = 0; index < rows.length(); index++) {
				Object raw = rows.get(index);
				if (!(raw instanceof JSONObject)) {
					throw new IOException(role + " row is not an object");
				}
				int id = ((JSONObject)raw).getInt("id");
				if (id < 0 || id >= count) {
					throw new IOException(role + " references undefined NPC ID " + id);
				}
			}
		}
	}

	private static JSONArray definitionArray(Path path, String label) throws IOException {
		String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		JSONObject document;
		try {
			document = new JSONObject(text);
		} catch (RuntimeException malformed) {
			throw new IOException(label + " definition JSON is malformed", malformed);
		}
		String[] names = JSONObject.getNames(document);
		if (names == null || names.length != 1
			|| !(document.opt(names[0]) instanceof JSONArray)) {
			throw new IOException(label + " must contain exactly one definition array");
		}
		JSONArray rows = document.getJSONArray(names[0]);
		if (rows.length() > 65536) throw new IOException(label + " exceeds 65,536 rows");
		return rows;
	}

	private static Map<Integer, ItemVisual> validateItemVisuals(
		JSONArray rows, List<Integer> groundItems) throws IOException {
		Map<Integer, ItemVisual> result = new LinkedHashMap<Integer, ItemVisual>();
		int previous = -1;
		for (int index = 0; index < rows.length(); index++) {
			Object raw = rows.get(index);
			if (!(raw instanceof JSONObject)) throw new IOException("Item visual is not an object");
			JSONObject row = (JSONObject) raw;
			requireKeys(row, set("itemId", "authenticSpriteId", "customSpriteAssetRole",
				"customSpriteSubspace", "customSpriteEntry", "pictureMask", "blueMask"));
			int itemId = boundedInt(row, "itemId", 0, 65535);
			if (itemId <= previous || !groundItems.contains(Integer.valueOf(itemId))) {
				throw new IOException("Item visuals are not ascending target item IDs");
			}
			Integer authentic = nullableInt(row, "authenticSpriteId", 0, 65535);
			String role = nullableString(row, "customSpriteAssetRole");
			String subspace = nullableString(row, "customSpriteSubspace");
			String entry = nullableString(row, "customSpriteEntry");
			boolean named = role != null || subspace != null || entry != null;
			if ((authentic != null) == named || (named
				&& (role == null || subspace == null || entry == null))) {
				throw new IOException("Item visual must use exactly one complete mapping form");
			}
			if (named && !("asset.sprite.custom".equals(role)
				|| "asset.spritepack".equals(role))) {
				throw new IOException("Item visual asset role is unsupported");
			}
			if (named && (!safeArchiveName(subspace) || !safeArchiveName(entry))) {
				throw new IOException("Item visual archive name is unsafe");
			}
			ItemVisual visual = new ItemVisual(itemId, authentic, role, subspace,
				entry, exactInt(row, "pictureMask"), exactInt(row, "blueMask"));
			result.put(Integer.valueOf(itemId), visual);
			previous = itemId;
		}
		return result;
	}

	private static void validateBindings(JSONArray bindings) throws IOException {
		String expected = "[{\"assetRoles\":[\"asset.sprite.custom\"],\"definitionRoles\":[\"definition.tile\"],\"family\":\"floor\"},{\"assetRoles\":[\"asset.library\",\"asset.sprite.authentic\",\"asset.sprite.custom\",\"asset.spritepack\"],\"definitionRoles\":[\"definition.item.base\",\"definition.item.custom\",\"definition.item.patch\",\"definition.item.world\"],\"family\":\"ground-item\"},{\"assetRoles\":[\"asset.library\",\"asset.sprite.authentic\",\"asset.sprite.custom\",\"asset.spritepack\"],\"definitionRoles\":[\"definition.npc.base\",\"definition.npc.custom\",\"definition.npc.patch\",\"definition.npc.world\"],\"family\":\"npc\"},{\"assetRoles\":[\"asset.library\",\"asset.model\",\"asset.sprite.custom\"],\"definitionRoles\":[\"definition.scenery\"],\"family\":\"scenery\"},{\"assetRoles\":[\"asset.sprite.custom\"],\"definitionRoles\":[\"definition.boundary\"],\"family\":\"wall\"}]";
		if (!expected.equals(canonical(bindings))) throw new IOException("Family bindings differ from v1");
	}

	private static void validateItemVisualEvidence(Path path,
		Map<Integer, ItemVisual> visuals) throws IOException {
		String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		StrictJsonScanner.validate(text);
		JSONObject evidence = new JSONObject(text);
		requireKeys(evidence, set("schemaVersion", "manifestType", "itemVisuals"));
		expectInt(evidence, "schemaVersion", 1);
		expect(evidence, "manifestType", "world-builder-item-visual-evidence");
		JSONArray expected = new JSONArray();
		for (ItemVisual visual : visuals.values()) expected.put(visual.json());
		if (!canonical(expected).equals(canonical(evidence.getJSONArray("itemVisuals")))) {
			throw new IOException("Item visuals disagree with preserved static evidence");
		}
	}

	private static void validateItemVisualArchives(Map<String, Path> paths,
		Map<Integer, ItemVisual> visuals) throws IOException {
		Map<String, Set<String>> named = new LinkedHashMap<String, Set<String>>();
		for (String role : Arrays.asList("asset.sprite.custom", "asset.spritepack")) {
			named.put(role, decodeOsarEntries(paths.get(role)));
		}
		for (ItemVisual visual : visuals.values()) {
			if (visual.authenticSpriteId != null) {
				validateAuthenticSprite(paths.get("asset.sprite.authentic"),
					visual.authenticSpriteId.intValue());
			} else {
				String key = visual.customSpriteSubspace + "\0" + visual.customSpriteEntry;
				Set<String> entries = named.get(visual.customSpriteAssetRole);
				if (!entries.contains(key)) {
					throw new IOException("Item visual archive entry is missing: " + visual.itemId);
				}
			}
		}
	}

	private static Map<Integer, Sprite> decodeItemVisuals(Map<String, Path> paths,
		Map<Integer, ItemVisual> visuals) throws IOException {
		Map<String, Map<String, Sprite>> named = new LinkedHashMap<String, Map<String, Sprite>>();
		for (String role : Arrays.asList("asset.sprite.custom", "asset.spritepack")) {
			Workspace workspace = new Unpacker().unpackArchive(paths.get(role).toFile());
			if (workspace == null) throw new IOException("Unable to decode " + role);
			Map<String, Sprite> entries = new HashMap<String, Sprite>();
			for (Subspace subspace : workspace.getSubspaces()) {
				for (Entry entry : subspace.getEntryList()) {
					if (entry.getFrames().length < 1 || entry.getFrames()[0] == null) {
						throw new IOException("Decoded item sprite frame is absent");
					}
					entries.put(subspace.getName() + "\0" + entry.getID(),
						entry.getFrames()[0].getSprite());
				}
			}
			named.put(role, entries);
		}
		Map<Integer, Sprite> result = new LinkedHashMap<Integer, Sprite>();
		for (ItemVisual visual : visuals.values()) {
			Sprite sprite = visual.authenticSpriteId == null
				? named.get(visual.customSpriteAssetRole).get(
					visual.customSpriteSubspace + "\0" + visual.customSpriteEntry)
				: decodeAuthenticSprite(paths.get("asset.sprite.authentic"),
					visual.authenticSpriteId.intValue());
			if (sprite == null || sprite.getPixels() == null || sprite.getWidth() < 1
				|| sprite.getHeight() < 1 || sprite.getPixels().length < 1) {
				throw new IOException("Decoded item visual is empty: " + visual.itemId);
			}
			result.put(Integer.valueOf(visual.itemId), sprite);
		}
		return result;
	}

	private static Sprite decodeAuthenticSprite(Path archive, int spriteId)
		throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			ZipEntry entry = zip.getEntry("sprites/" + spriteId + ".dat");
			if (entry == null || entry.getSize() < 16L || entry.getSize() > MAX_FILE) {
				throw new IOException("Authentic project item sprite entry is missing or unsafe");
			}
			return decodeAuthenticPayload(readBounded(zip.getInputStream(entry), MAX_FILE));
		}
	}

	private static void validateAuthenticSprite(Path archive, int spriteId)
		throws IOException {
		String selected = "sprites/" + spriteId + ".dat";
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Set<String> names = new HashSet<String>(); ZipEntry target = null;
			java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement(); String name = entry.getName();
				if (entry.isDirectory() || !name.matches("sprites/[0-9]+\\.dat")
					|| !names.add(name)) {
					throw new IOException("Authentic sprite archive contains an unsafe entry");
				}
				if (selected.equals(name)) target = entry;
			}
			if (target == null || target.getSize() < 16L || target.getSize() > MAX_FILE) {
				throw new IOException("Authentic item sprite entry is missing or unsafe");
			}
			decodeAuthenticPayload(readBounded(zip.getInputStream(target), MAX_FILE));
		}
	}

	private static Sprite decodeAuthenticPayload(byte[] bytes) throws IOException {
		ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		try {
			int type = input.get() & 0xff;
			if (type != 0) throw new IOException("Authentic item sprite type is invalid");
			int frames = input.get() & 0xff;
			if (frames < 1) throw new IOException("Authentic item sprite is empty");
			int colours = (input.get() & 0xff) + 1;
			if (input.remaining() < colours * 3) throw new IOException("Authentic sprite palette is truncated");
			int[] palette = new int[colours];
			for (int colour = 0; colour < colours; colour++) {
				palette[colour] = (input.get() & 0xff) << 16
					| (input.get() & 0xff) << 8 | input.get() & 0xff;
			}
			Sprite first = null;
			for (int frame = 0; frame < frames; frame++) {
				int width = input.getShort() & 0xffff, height = input.getShort() & 0xffff;
				int shifted = input.get() & 0xff;
				int x = input.getShort(), y = input.getShort();
				int bw = input.getShort() & 0xffff, bh = input.getShort() & 0xffff;
				long count = (long) width * height;
				if (width < 1 || height < 1 || bw < 1 || bh < 1 || shifted > 1
					|| count > 16777216L || input.remaining() < count) {
					throw new IOException("Authentic sprite dimensions are unsafe");
				}
				int[] pixels = new int[(int) count];
				for (int pixel = 0; pixel < pixels.length; pixel++) {
					int index = input.get() & 0xff;
					if (index >= colours) throw new IOException("Authentic sprite palette index is invalid");
					pixels[pixel] = palette[index];
				}
				if (first == null) {
					first = new Sprite(pixels, width, height);
					first.setRequiresShift(shifted == 1);
					first.setShift(x, y); first.setSomething(bw, bh);
				}
			}
			if (input.hasRemaining()) throw new IOException("Authentic sprite has trailing content");
			return first;
		} catch (java.nio.BufferUnderflowException failure) {
			throw new IOException("Authentic sprite entry is truncated", failure);
		}
	}

	private static Set<String> decodeOsarEntries(Path archive) throws IOException {
		byte[] bytes;
		try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
			bytes = readBounded(input, MAX_FILE);
		}
		ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
		Set<String> result = new HashSet<String>(), subspaces = new HashSet<String>();
		try {
			int subspaceCount = input.get() & 0xff;
			if (subspaceCount < 1) throw new IOException("Sprite archive is empty");
			for (int s = 0; s < subspaceCount; s++) {
				String subspace = archiveString(input);
				if (!safeArchiveName(subspace) || !subspaces.add(subspace)) throw new IOException("Unsafe sprite subspace");
				int entryCount = input.getShort() & 0xffff;
				for (int e = 0; e < entryCount; e++) {
					String entry = archiveString(input);
					if (!safeArchiveName(entry) || !result.add(subspace + "\0" + entry)) throw new IOException("Unsafe sprite entry");
					int type = input.get() & 0xff; if (type > 4) throw new IOException("Invalid sprite type");
					if (type >= 1 && type <= 3) input.get();
					int frames = input.get() & 0xff; if (frames < 1) throw new IOException("Empty sprite entry");
					int colours = (input.get() & 0xff) + 1;
					if (input.remaining() < colours * 3) throw new IOException("Truncated sprite palette");
					input.position(input.position() + colours * 3);
					for (int f = 0; f < frames; f++) {
						int width = input.getShort() & 0xffff, height = input.getShort() & 0xffff;
						int shifted = input.get() & 0xff; input.getShort(); input.getShort();
						int bw = input.getShort() & 0xffff, bh = input.getShort() & 0xffff;
						long pixels = (long) width * height;
						if (width < 1 || height < 1 || shifted > 1
							|| pixels > 16777216L || input.remaining() < pixels) throw new IOException("Unsafe sprite frame");
						for (long p = 0; p < pixels; p++) if ((input.get() & 0xff) >= colours) throw new IOException("Invalid palette index");
					}
				}
			}
		} catch (java.nio.BufferUnderflowException failure) {
			throw new IOException("Sprite archive is truncated", failure);
		}
		if (input.hasRemaining()) throw new IOException("Sprite archive has trailing content");
		return result;
	}

	private static String archiveString(ByteBuffer input) throws IOException {
		StringBuilder result = new StringBuilder();
		while (input.hasRemaining()) {
			int value = input.get() & 0xff; if (value == 0) return result.toString();
			if (value < 0x20 || value > 0x7e || result.length() >= 128) throw new IOException("Unsafe archive name");
			result.append((char) value);
		}
		throw new IOException("Truncated archive name");
	}

	private static byte[] readBounded(InputStream input, long maximum) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; long total = 0;
		for (int read; (read = input.read(buffer)) >= 0;) {
			if (read == 0) continue; total += read;
			if (total > maximum) throw new IOException("Archive content exceeds its bound");
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static boolean safeArchiveName(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
	}

	private static String fingerprint(String domain, List<JSONObject> rows,
		List<Spec> specs, boolean definitions, String catalogHash) throws IOException {
		MessageDigest digest = digest(); digest.update(domain.getBytes(StandardCharsets.UTF_8));
		for (int index = 0; index < specs.size(); index++) {
			Spec spec = specs.get(index);
			if (spec.metadata || spec.definition != definitions) continue;
			JSONObject row = rows.get(index);
			digest.update((spec.role + "\0" + spec.path + "\0" + row.getLong("size")
				+ "\0" + row.getString("sha256") + "\n").getBytes(StandardCharsets.UTF_8));
		}
		if (!catalogHash.isEmpty()) digest.update(catalogHash.getBytes(StandardCharsets.US_ASCII));
		return hex(digest.digest());
	}
	private static Path safeFile(Path path, long maximum) throws IOException {
		Path result = path.toAbsolutePath().normalize();
		if (!Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(result)
			|| Files.size(result) < 1L || Files.size(result) > maximum) throw new IOException("Content file is missing or unsafe");
		try {
			Object links = Files.getAttribute(
				result, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
			if (links instanceof Number && ((Number) links).longValue() != 1L) {
				throw new IOException("Content file is hard linked");
			}
		} catch (UnsupportedOperationException unsupported) {
			result.toRealPath();
		}
		return result.toRealPath();
	}
	private static void requireKeys(JSONObject value, Set<String> keys) throws IOException {
		if (!value.keySet().equals(keys)) throw new IOException("Content JSON contains unknown or missing keys");
	}
	private static void expect(JSONObject value, String key, String expected) throws IOException {
		if (!(value.opt(key) instanceof String) || !expected.equals(value.optString(key))) throw new IOException("Content " + key + " differs from v1");
	}
	private static void expectInt(JSONObject value, String key, int expected) throws IOException {
		if (exactInt(value, key) != expected) throw new IOException("Content " + key + " is unsupported");
	}
	private static int exactInt(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof Number)) throw new IOException("Content " + key + " is not an integer");
		long result = ((Number) raw).longValue();
		if (raw instanceof Double || raw instanceof Float
			|| new BigDecimal(raw.toString()).compareTo(BigDecimal.valueOf(result)) != 0
			|| result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
			throw new IOException("Content " + key + " is not an exact signed integer");
		}
		return (int) result;
	}
	private static int boundedInt(JSONObject value, String key, int minimum,
		int maximum) throws IOException {
		int result = exactInt(value, key);
		if (result < minimum || result > maximum) throw new IOException("Content " + key + " is outside its bound");
		return result;
	}
	private static Integer nullableInt(JSONObject value, String key, int minimum,
		int maximum) throws IOException {
		Object raw = value.opt(key); if (raw == null || raw == JSONObject.NULL) return null;
		return Integer.valueOf(boundedInt(value, key, minimum, maximum));
	}
	private static String nullableString(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key); if (raw == null || raw == JSONObject.NULL) return null;
		if (!(raw instanceof String)) throw new IOException("Content " + key + " is not text or null");
		return (String) raw;
	}
	private static Set<String> set(String... values) { return new HashSet<String>(Arrays.asList(values)); }
	private static String trim(String value) { return value == null ? "" : value.trim(); }
	private static String canonical(Object value) throws IOException {
		if (value == null || value == JSONObject.NULL) return "null";
		if (value instanceof JSONObject) {
			JSONObject object = (JSONObject) value; List<String> keys = new ArrayList<String>(object.keySet()); Collections.sort(keys);
			StringBuilder out = new StringBuilder("{");
			for (int i = 0; i < keys.size(); i++) { if (i > 0) out.append(','); String key = keys.get(i); out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))); }
			return out.append('}').toString();
		}
		if (value instanceof JSONArray) {
			JSONArray array = (JSONArray) value; StringBuilder out = new StringBuilder("[");
			for (int i = 0; i < array.length(); i++) { if (i > 0) out.append(','); out.append(canonical(array.get(i))); }
			return out.append(']').toString();
		}
		if (value instanceof String) return JSONObject.quote((String) value);
		if (value instanceof Boolean || value instanceof Number) return value.toString();
		throw new IOException("Unsupported content JSON value");
	}
	private static String sha256(Path path) throws IOException {
		MessageDigest digest = digest(); byte[] buffer = new byte[8192];
		try (java.io.InputStream input = Files.newInputStream(path)) { for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read); }
		return hex(digest.digest());
	}
	private static String sha256(byte[] bytes) { return hex(digest().digest(bytes)); }
	private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
	private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(); for (byte value : bytes) out.append(String.format("%02x", value & 0xff)); return out.toString(); }

	private static List<Spec> specs(boolean v2) {
		return specs(v2 ? 2 : 1);
	}
	private static List<Spec> specs(int version) {
		List<Spec> values = Arrays.asList(
			new Spec("asset.sprite.authentic", "client/Cache/video/Authentic_Sprites.orsc", "application/vnd.openrsc.archive", false, false),
			new Spec("asset.sprite.custom", "client/Cache/video/Custom_Sprites.osar", "application/gzip", false, false),
			new Spec("asset.library", "client/Cache/video/library.orsc", "application/vnd.openrsc.archive", false, false),
			new Spec("asset.model", "client/Cache/video/models.orsc", "application/vnd.openrsc.archive", false, false),
			new Spec("asset.spritepack", "client/Cache/video/spritepacks/Menus.osar", "application/gzip", false, false),
			new Spec("definition.boundary", "server/conf/server/defs/DoorDef.xml", "application/xml", true, false),
			new Spec("definition.scenery", "server/conf/server/defs/GameObjectDef.xml", "application/xml", true, false),
			new Spec("definition.item.base", "server/conf/server/defs/ItemDefs.json", "application/json", true, false),
			new Spec("definition.item.custom", "server/conf/server/defs/ItemDefsCustom.json", "application/json", true, false),
			new Spec("definition.item.world", "server/conf/server/defs/ItemDefsMyWorld.json", "application/json", true, false),
			new Spec("definition.item.patch", "server/conf/server/defs/ItemDefsPatch18.json", "application/json", true, false),
			new Spec("definition.npc.base", "server/conf/server/defs/NpcDefs.json", "application/json", true, false),
			new Spec("definition.npc.custom", "server/conf/server/defs/NpcDefsCustom.json", "application/json", true, false),
			new Spec("definition.npc.world", "server/conf/server/defs/NpcDefsMyWorld.json", "application/json", true, false),
			new Spec("definition.npc.patch", "server/conf/server/defs/NpcDefsPatch18.json", "application/json", true, false),
			new Spec("definition.tile", "server/conf/server/defs/TileDef.xml", "application/xml", true, false));
		if (version >= 2) {
			values = new ArrayList<Spec>(values);
			values.add(new Spec("metadata.item-visuals", "server/conf/world-builder/item-visuals-v1.json", "application/json", false, true));
		}
		if (version >= 3) {
			values.add(new Spec("metadata.npc-animations",
				"server/conf/world-builder/npc-animations-v1.json",
				"application/json", true, true));
		}
		Collections.sort(values, new Comparator<Spec>() { public int compare(Spec a, Spec b) { return a.path.compareTo(b.path); } });
		return Collections.unmodifiableList(values);
	}
	private static final class Spec {
		final String role, path, media; final boolean definition, metadata;
		Spec(String role, String path, String media, boolean definition, boolean metadata) { this.role = role; this.path = path; this.media = media; this.definition = definition; this.metadata = metadata; }
	}
	public static final class ItemVisual {
		private final int itemId; private final Integer authenticSpriteId;
		private final String customSpriteAssetRole, customSpriteSubspace, customSpriteEntry;
		private final int pictureMask, blueMask;
		private ItemVisual(int itemId, Integer authenticSpriteId, String role,
			String subspace, String entry, int pictureMask, int blueMask) {
			this.itemId = itemId; this.authenticSpriteId = authenticSpriteId;
			this.customSpriteAssetRole = role; this.customSpriteSubspace = subspace;
			this.customSpriteEntry = entry; this.pictureMask = pictureMask; this.blueMask = blueMask;
		}
		public int itemId() { return itemId; }
		public Integer authenticSpriteId() { return authenticSpriteId; }
		public String customSpriteAssetRole() { return customSpriteAssetRole; }
		public String customSpriteSubspace() { return customSpriteSubspace; }
		public String customSpriteEntry() { return customSpriteEntry; }
		public String spriteLocation() { return authenticSpriteId == null ? customSpriteSubspace + ":" + customSpriteEntry : null; }
		public int pictureMask() { return pictureMask; }
		public int blueMask() { return blueMask; }
		private JSONObject json() {
			JSONObject value = new JSONObject(); value.put("itemId", itemId);
			value.put("authenticSpriteId", authenticSpriteId == null ? JSONObject.NULL : authenticSpriteId);
			value.put("customSpriteAssetRole", customSpriteAssetRole == null ? JSONObject.NULL : customSpriteAssetRole);
			value.put("customSpriteSubspace", customSpriteSubspace == null ? JSONObject.NULL : customSpriteSubspace);
			value.put("customSpriteEntry", customSpriteEntry == null ? JSONObject.NULL : customSpriteEntry);
			value.put("pictureMask", pictureMask); value.put("blueMask", blueMask); return value;
		}
	}
	private static final class UnsafeBundle extends RuntimeException { UnsafeBundle(String message) { super(message); } }

	static void validateStrictJson(String document) throws IOException {
		StrictJsonScanner.validate(document);
	}

	/** Minimal strict scanner used before org.json so duplicate keys fail closed. */
	private static final class StrictJsonScanner {
		private final String text; private int at;
		private StrictJsonScanner(String text) { this.text = text; }
		static void validate(String text) throws IOException { StrictJsonScanner s = new StrictJsonScanner(text); s.ws(); s.value(); s.ws(); if (s.at != text.length()) s.fail("trailing content"); }
		private void value() throws IOException { if (at >= text.length()) fail("missing value"); char c=text.charAt(at); if(c=='{')object(); else if(c=='[')array(); else if(c=='\"')string(); else if(c=='-'||Character.isDigit(c))number(); else if(text.startsWith("true",at))at+=4; else if(text.startsWith("false",at))at+=5; else if(text.startsWith("null",at))at+=4; else fail("invalid value"); }
		private void object() throws IOException { at++;ws();Set<String> keys=new HashSet<String>();if(take('}'))return;while(true){if(at>=text.length()||text.charAt(at)!='\"')fail("object key must be text");String key=string();if(!keys.add(key))fail("duplicate object key");ws();expect(':');ws();value();ws();if(take('}'))return;expect(',');ws();} }
		private void array() throws IOException { at++;ws();if(take(']'))return;while(true){value();ws();if(take(']'))return;expect(',');ws();} }
		private String string() throws IOException { StringBuilder out=new StringBuilder();expect('\"');while(at<text.length()){char c=text.charAt(at++);if(c=='\"')return out.toString();if(c<0x20)fail("control character");if(c!='\\'){out.append(c);continue;}if(at>=text.length())fail("truncated escape");char e=text.charAt(at++);if(e=='u'){if(at+4>text.length())fail("truncated unicode");try{out.append((char)Integer.parseInt(text.substring(at,at+4),16));}catch(NumberFormatException x){fail("invalid unicode");}at+=4;}else{int p="\"\\/bfnrt".indexOf(e);if(p<0)fail("invalid escape");out.append(p<3?e:"\b\f\n\r\t".charAt(p-3));}}fail("unterminated string");return ""; }
		private void number() throws IOException { int start=at;if(take('-')&&at>=text.length())fail("invalid number");if(take('0')){if(at<text.length()&&Character.isDigit(text.charAt(at)))fail("leading zero");}else{if(at>=text.length()||!Character.isDigit(text.charAt(at)))fail("invalid number");while(at<text.length()&&Character.isDigit(text.charAt(at)))at++;}if(at<text.length()&&(text.charAt(at)=='.'||text.charAt(at)=='e'||text.charAt(at)=='E'))fail("numbers must be integers");try{Long.parseLong(text.substring(start,at));}catch(NumberFormatException x){fail("integer outside bound");} }
		private void ws(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;} private boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;} private void expect(char c)throws IOException{if(!take(c))fail("expected "+c);} private void fail(String m)throws IOException{throw new IOException("Project content JSON "+m+" at byte "+at);}
	}
}
