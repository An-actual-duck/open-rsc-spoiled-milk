package orsc;

import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Subspace;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.SpriteArchive.Workspace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Strict client consumer for the project-local NPC animation registry v1. */
public final class ProjectNpcAnimationRegistry {
	private static final String TYPE = "world-builder-npc-animation-registry";
	private static final long MAX_BYTES = 16L * 1024L * 1024L;
	private static final Pattern NAME =
		Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
	private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
	private static final Set<String> ROOT_KEYS = set(
		"schemaVersion", "manifestType", "animations");
	private static final Set<String> RECORD_KEYS = set(
		"animationId", "name", "category", "charColour", "blueMask",
		"genderModel", "hasCombatFrames", "hasSpecialCombatFrames",
		"requiredFrameCount", "customSpriteSubspace", "customSpriteEntry",
		"customEntrySha256", "authenticBaseSpriteId",
		"authenticFrameSha256s");

	private ProjectNpcAnimationRegistry() {
	}

	static Map<Integer, EntryDef> load(Path path, Path customArchive,
		Path authenticArchive) throws IOException {
		if (path == null || Files.size(path) < 1L || Files.size(path) > MAX_BYTES) {
			throw new IOException("NPC animation registry is missing or outside its bound");
		}
		String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		ProjectContentBundle.validateStrictJson(text);
		JSONObject document;
		try {
			document = new JSONObject(text);
		} catch (RuntimeException malformed) {
			throw new IOException("NPC animation registry is malformed", malformed);
		}
		requireKeys(document, ROOT_KEYS, "NPC animation registry");
		exactInt(document, "schemaVersion", 1);
		if (!TYPE.equals(document.opt("manifestType"))) {
			throw new IOException("NPC animation registry type is unsupported");
		}

		Map<String,Integer> customFrames = customFrames(customArchive);
		JSONArray rows = document.optJSONArray("animations");
		if (rows == null || rows.length() < 1 || rows.length() > 65536) {
			throw new IOException("NPC animation registry is empty or too large");
		}
		Map<Integer,EntryDef> result = new LinkedHashMap<Integer,EntryDef>();
		int previous = -1;
		for (int index = 0; index < rows.length(); index++) {
			Object raw = rows.opt(index);
			if (!(raw instanceof JSONObject)) {
				throw new IOException("NPC animation registry row is not an object");
			}
			JSONObject row = (JSONObject)raw;
			requireKeys(row, RECORD_KEYS, "NPC animation registry row");
			int id = boundedInt(row, "animationId", 0, 65535);
			if (id <= previous) {
				throw new IOException("NPC animation IDs are not sorted and unique");
			}
			previous = id;
			String name = name(row, "name"), category = name(row, "category");
			String subspace = name(row, "customSpriteSubspace");
			String entry = name(row, "customSpriteEntry");
			if (!category.equals(subspace) || !name.equals(entry)) {
				throw new IOException("NPC animation custom lookup differs from category/name");
			}
			String entryHash = string(row, "customEntrySha256");
			if (!SHA.matcher(entryHash).matches()) {
				throw new IOException("NPC animation custom entry hash is invalid");
			}
			boolean combat = bool(row, "hasCombatFrames");
			boolean special = bool(row, "hasSpecialCombatFrames");
			if (special && !combat) {
				throw new IOException("NPC animation special frames require combat frames");
			}
			int required = boundedInt(row, "requiredFrameCount", 1, 27);
			int expected = 15 + (combat ? 3 : 0) + (special ? 9 : 0);
			if (required != expected
				|| !Integer.valueOf(required).equals(customFrames.get(category + "\0" + name))) {
				throw new IOException("NPC animation custom frames disagree with renderer semantics");
			}
			int authenticBase = boundedInt(row, "authenticBaseSpriteId", 0, 65535);
			JSONArray hashes = row.optJSONArray("authenticFrameSha256s");
			if (hashes == null || hashes.length() != required) {
				throw new IOException("NPC animation authentic frame inventory is incomplete");
			}
			validateAuthenticFrames(authenticArchive, authenticBase, hashes);
			EntryDef value = new EntryDef(id, name, category,
				exactInt(row, "charColour"), exactInt(row, "blueMask"),
				exactInt(row, "genderModel"), combat, special, authenticBase);
			result.put(Integer.valueOf(id), value);
		}
		return Collections.unmodifiableMap(result);
	}

	private static Map<String,Integer> customFrames(Path archive) throws IOException {
		Workspace workspace = new Unpacker().unpackArchive(archive.toFile());
		if (workspace == null) throw new IOException("NPC custom sprite archive is unreadable");
		Map<String,Integer> result = new HashMap<String,Integer>();
		for (Subspace subspace : workspace.getSubspaces()) {
			if (!NAME.matcher(subspace.getName()).matches()) {
				throw new IOException("NPC custom sprite subspace is unsafe");
			}
			for (Entry entry : subspace.getEntryList()) {
				if (!NAME.matcher(entry.getID()).matches()
					|| result.put(subspace.getName() + "\0" + entry.getID(),
						Integer.valueOf(entry.getFrames().length)) != null) {
					throw new IOException("NPC custom sprite entry is unsafe or duplicated");
				}
			}
		}
		return result;
	}

	private static void validateAuthenticFrames(Path archive, int base,
		JSONArray hashes) throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			for (int index = 0; index < hashes.length(); index++) {
				String expected = hashes.optString(index, "");
				if (!SHA.matcher(expected).matches()) {
					throw new IOException("NPC authentic frame hash is invalid");
				}
				int id = base + index;
				if (id > 65535) throw new IOException("NPC authentic frame range overflows");
				ZipEntry entry = zip.getEntry(String.valueOf(id));
				if (entry == null) entry = zip.getEntry("sprites/" + id + ".dat");
				if (entry == null || entry.isDirectory() || entry.getSize() < 1L
					|| entry.getSize() > MAX_BYTES) {
					throw new IOException("NPC authentic frame is missing or unsafe: " + id);
				}
				try (InputStream input = zip.getInputStream(entry)) {
					if (!expected.equals(sha256(input))) {
						throw new IOException("NPC authentic frame hash mismatch: " + id);
					}
				}
			}
		}
	}

	private static String sha256(InputStream input) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192]; long total = 0L;
			for (int read; (read = input.read(buffer)) >= 0;) {
				if (read == 0) continue;
				total += read;
				if (total > MAX_BYTES) throw new IOException("NPC authentic frame is too large");
				digest.update(buffer, 0, read);
			}
			StringBuilder out = new StringBuilder();
			for (byte value : digest.digest()) out.append(String.format("%02x", value & 0xff));
			return out.toString();
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private static void requireKeys(JSONObject value, Set<String> expected,
		String label) throws IOException {
		if (!value.keySet().equals(expected)) {
			throw new IOException(label + " contains unknown or missing keys");
		}
	}
	private static String name(JSONObject value, String key) throws IOException {
		String result = string(value, key);
		if (!NAME.matcher(result).matches()) throw new IOException("NPC animation " + key + " is unsafe");
		return result;
	}
	private static String string(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof String)) throw new IOException("NPC animation " + key + " is not text");
		return (String)raw;
	}
	private static boolean bool(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof Boolean)) throw new IOException("NPC animation " + key + " is not boolean");
		return ((Boolean)raw).booleanValue();
	}
	private static int boundedInt(JSONObject value, String key, int minimum,
		int maximum) throws IOException {
		int result = exactInt(value, key);
		if (result < minimum || result > maximum) throw new IOException("NPC animation " + key + " is outside its bound");
		return result;
	}
	private static int exactInt(JSONObject value, String key) throws IOException {
		Object raw = value.opt(key);
		if (!(raw instanceof Number)) throw new IOException("NPC animation " + key + " is not an integer");
		long result = ((Number)raw).longValue();
		if (raw instanceof Double || raw instanceof Float
			|| new BigDecimal(raw.toString()).compareTo(BigDecimal.valueOf(result)) != 0
			|| result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
			throw new IOException("NPC animation " + key + " is not an exact signed integer");
		}
		return (int)result;
	}
	private static void exactInt(JSONObject value, String key, int expected)
		throws IOException {
		if (exactInt(value, key) != expected) throw new IOException("NPC animation " + key + " is unsupported");
	}
	private static Set<String> set(String... values) {
		return new HashSet<String>(Arrays.asList(values));
	}

	public static final class EntryDef {
		final int id;
		final String name;
		final String category;
		final int charColour;
		final int blueMask;
		final int genderModel;
		final boolean combat;
		final boolean special;
		final int authenticBase;
		EntryDef(int id, String name, String category, int charColour, int blueMask,
			int genderModel, boolean combat, boolean special, int authenticBase) {
			this.id = id; this.name = name; this.category = category;
			this.charColour = charColour; this.blueMask = blueMask;
			this.genderModel = genderModel; this.combat = combat;
			this.special = special; this.authenticBase = authenticBase;
		}
		public int id() { return id; }
		public AnimationDef animationDef() {
			return new AnimationDef(name, category, charColour, blueMask,
				genderModel, combat, special, authenticBase);
		}
	}
}
