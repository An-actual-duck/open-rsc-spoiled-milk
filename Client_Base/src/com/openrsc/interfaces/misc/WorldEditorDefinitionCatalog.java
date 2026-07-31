package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.defs.NPCDef;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor-only semantic labels and search metadata for stable definition IDs.
 * Gameplay definitions remain authoritative; a missing or stale row falls back
 * to the runtime definition rather than changing game-facing behavior.
 */
public final class WorldEditorDefinitionCatalog {
	private static final String SCHEMA = "# world-editor-definition-catalog-v1";
	private static final String DEVELOPMENT_PATH =
		"dev/myworld/assets/ui/world-editor/definition-catalog-v1.tsv";
	private static final String RESOURCE_PATH =
		"myworld-assets/ui/world-editor/definition-catalog-v1.tsv";
	private static final String[] FLOOR_TEXTURE_LABELS = {
		"Base Floor Color",
		"Grey Road",
		"Water",
		"Wooden Floor",
		"Wooden Bridge",
		"Grey Stone Floor",
		"Red Carpet",
		"Swamp Water",
		"Invisible Void",
		"White Border",
		"Black Void",
		"Lava",
		"Wooden Bridge (alternate)",
		"Blue Carpet",
		"Pentagram",
		"Purple Carpet",
		"Dark Floor",
		"Light Stone Floor",
		"Dark Blue Floor",
		"Black Void (alternate)",
		"Invisible Agility Platform",
		"Invisible Agility Platform",
		"Dark Blue Floor (alternate)",
		"Sand Floor",
		"Mud Floor",
		"Shallow Water",
		"Invisible Path"
	};

	public static final class Entry {
		private final String kind;
		private final int id;
		private final String canonicalName;
		private final String displayName;
		private final String labelSource;
		private final String tags;
		private final String searchTerms;

		private Entry(String kind, int id, String canonicalName, String displayName,
			String labelSource, String tags, String searchTerms) {
			this.kind = kind;
			this.id = id;
			this.canonicalName = canonicalName;
			this.displayName = displayName;
			this.labelSource = labelSource;
			this.tags = tags;
			this.searchTerms = searchTerms;
		}

		public String kind() { return kind; }
		public int id() { return id; }
		public String canonicalName() { return canonicalName; }
		public String displayName() { return displayName; }
		public String labelSource() { return labelSource; }
		public String tags() { return tags; }
		public String searchTerms() { return searchTerms; }
	}

	private static final class LoadedCatalog {
		private final Map<Integer, Entry> scenery;
		private final Map<Integer, Entry> boundaries;
		private final List<Entry> sceneryEntries;
		private final List<Entry> boundaryEntries;

		private LoadedCatalog(Map<Integer, Entry> scenery, Map<Integer, Entry> boundaries) {
			this.scenery = Collections.unmodifiableMap(scenery);
			this.boundaries = Collections.unmodifiableMap(boundaries);
			this.sceneryEntries = Collections.unmodifiableList(new ArrayList<Entry>(scenery.values()));
			this.boundaryEntries = Collections.unmodifiableList(new ArrayList<Entry>(boundaries.values()));
		}
	}

	private static final class Holder {
		private static final LoadedCatalog INSTANCE = load();
	}

	/** Each runtime family is built only if its browser is opened. */
	private static final class NpcHolder {
		private static final List<Entry> NPCS = runtimeNpcs();
	}

	private static final class ItemHolder {
		private static final List<Entry> ITEMS = runtimeItems();
	}

	private WorldEditorDefinitionCatalog() {
	}

	public static String sceneryLabel(int id) {
		return sceneryLabel(id, runtimeSceneryName(id));
	}

	public static String sceneryLabel(int id, String canonicalName) {
		return label(Holder.INSTANCE.scenery, id, canonicalName, "Unknown scenery");
	}

	public static String sceneryReference(int id) {
		return sceneryLabel(id) + " [#" + id + "]";
	}

	public static String boundaryLabel(int id) {
		return boundaryLabel(id, runtimeBoundaryName(id));
	}

	public static String boundaryLabel(int id, String canonicalName) {
		return label(Holder.INSTANCE.boundaries, id, canonicalName, "Unknown boundary");
	}

	public static String boundaryReference(int id) {
		return boundaryLabel(id) + " [#" + id + "]";
	}

	/**
	 * Returns the author-facing appearance of the raw, one-based landscape
	 * overlay value. This presentation metadata does not replace TileDef or
	 * alter rendering, collision, archives, or protocol values.
	 */
	public static String floorTextureLabel(int overlay) {
		if (overlay == 250) {
			return "Bridge Transition";
		}
		if (overlay < 0 || overlay >= FLOOR_TEXTURE_LABELS.length) {
			return "Undefined Texture";
		}
		return FLOOR_TEXTURE_LABELS[overlay];
	}

	public static List<Entry> sceneryEntries() {
		return Holder.INSTANCE.sceneryEntries;
	}

	public static List<Entry> boundaryEntries() {
		return Holder.INSTANCE.boundaryEntries;
	}

	public static List<Entry> npcEntries() {
		return NpcHolder.NPCS;
	}

	public static List<Entry> itemEntries() {
		return ItemHolder.ITEMS;
	}

	private static List<Entry> runtimeNpcs() {
		List<Entry> entries = new ArrayList<Entry>();
		for (int id = 0; id < EntityHandler.npcCount(); id++) {
			NPCDef definition;
			try {
				definition = EntityHandler.getNpcDef(id);
			} catch (RuntimeException failure) {
				continue;
			}
			if (definition == null || definition.id != id) {
				continue;
			}
			String name = normalized(definition.getName());
			if (name.isEmpty()) {
				continue;
			}
			String tags = definition.isAttackable() ? "Attackable" : "Protected";
			String behavior = definition.isAttackable()
				? "combat attackable target"
				: "noncombat protected cannot attack";
			String search = normalized(name + " " + safe(definition.getDescription()) + " "
				+ safe(definition.getCommand1()) + " " + safe(definition.getCommand2()) + " "
				+ behavior + " npc character");
			entries.add(new Entry("npc", id, name, name, "runtime", tags, search));
		}
		return Collections.unmodifiableList(entries);
	}

	private static List<Entry> runtimeItems() {
		List<Entry> entries = new ArrayList<Entry>();
		for (int id = 0; id < EntityHandler.itemCount(); id++) {
			ItemDef definition;
			try {
				definition = EntityHandler.findItem(id, false);
			} catch (RuntimeException failure) {
				continue;
			}
			if (definition == null || definition.id != id || isPlaceholderItem(definition)) {
				continue;
			}
			String name = normalized(definition.getName());
			if (name.isEmpty()) {
				continue;
			}
			String tags = definition.isStackable() ? "Stackable" : "Single";
			String traits = definition.isStackable() ? "stackable stack" : "single individual";
			if (definition.isWieldable()) {
				traits += " wieldable wearable equipment";
			}
			if (definition.membersItem) {
				traits += " members";
			}
			if (definition.untradeable) {
				traits += " untradeable";
			}
			String search = normalized(name + " " + safe(definition.getDescription()) + " "
				+ commandTerms(definition.getCommand()) + " " + traits
				+ " item ground spawn respawn");
			entries.add(new Entry("item", id, name, name, "runtime", tags, search));
		}
		return Collections.unmodifiableList(entries);
	}

	private static boolean isPlaceholderItem(ItemDef definition) {
		return "Unobtanium".equals(definition.getName()) && definition.getSpriteID() == 70;
	}

	private static String commandTerms(String[] commands) {
		if (commands == null || commands.length == 0) {
			return "";
		}
		StringBuilder joined = new StringBuilder();
		for (String command : commands) {
			if (command == null || command.trim().isEmpty()) {
				continue;
			}
			if (joined.length() > 0) {
				joined.append(' ');
			}
			joined.append(command);
		}
		return joined.toString();
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static String label(Map<Integer, Entry> entries, int id, String canonicalName, String fallback) {
		String canonical = normalized(canonicalName);
		if (canonical.isEmpty()) {
			return fallback;
		}
		Entry entry = entries.get(id);
		if (entry == null || !entry.canonicalName.equals(canonical)) {
			return canonical;
		}
		return entry.displayName;
	}

	private static String runtimeSceneryName(int id) {
		try {
			if (id < 0 || id >= EntityHandler.objectCount()) {
				return "";
			}
			return EntityHandler.getObjectDef(id).getName();
		} catch (RuntimeException failure) {
			return "";
		}
	}

	private static String runtimeBoundaryName(int id) {
		try {
			if (id < 0 || id >= EntityHandler.doorCount()) {
				return "";
			}
			return EntityHandler.getDoorDef(id).getName();
		} catch (RuntimeException failure) {
			return "";
		}
	}

	private static LoadedCatalog load() {
		Map<Integer, Entry> scenery = new LinkedHashMap<Integer, Entry>();
		Map<Integer, Entry> boundaries = new LinkedHashMap<Integer, Entry>();
		try (InputStream input = open();
			 BufferedReader reader = input == null ? null : new BufferedReader(
				 new InputStreamReader(input, StandardCharsets.UTF_8))) {
			if (reader == null) {
				throw new IOException("catalog resource is missing");
			}
			String firstLine = reader.readLine();
			if (!SCHEMA.equals(firstLine)) {
				throw new IOException("unsupported catalog schema");
			}
			String line;
			int lineNumber = 1;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isEmpty() || line.charAt(0) == '#') {
					continue;
				}
				String[] raw = line.split("\\t", -1);
				if (raw.length != 7) {
					throw new IOException("expected 7 fields at line " + lineNumber);
				}
				String[] fields = new String[raw.length];
				for (int i = 0; i < raw.length; i++) {
					fields[i] = unescape(raw[i], lineNumber);
				}
				int id;
				try {
					id = Integer.parseInt(fields[1]);
				} catch (NumberFormatException failure) {
					throw new IOException("invalid ID at line " + lineNumber, failure);
				}
				Entry entry = new Entry(fields[0], id, normalized(fields[2]), normalized(fields[3]),
					fields[4], fields[5], fields[6]);
				Map<Integer, Entry> target;
				if ("scenery".equals(entry.kind)) {
					target = scenery;
				} else if ("boundary".equals(entry.kind)) {
					target = boundaries;
				} else {
					throw new IOException("unknown definition kind at line " + lineNumber);
				}
				if (entry.id < 0 || entry.canonicalName.isEmpty() || entry.displayName.isEmpty()
					|| target.put(entry.id, entry) != null) {
					throw new IOException("invalid or duplicate catalog row at line " + lineNumber);
				}
			}
			if (scenery.isEmpty() || boundaries.isEmpty()) {
				throw new IOException("catalog has no scenery or boundary rows");
			}
		} catch (IOException | RuntimeException failure) {
			System.out.println("[world-editor catalog] " + failure.getMessage()
				+ "; runtime definition names remain active");
			scenery.clear();
			boundaries.clear();
		}
		return new LoadedCatalog(scenery, boundaries);
	}

	private static InputStream open() throws IOException {
		Path userDirectory = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
		Path[] candidates = new Path[] {
			userDirectory.resolve(DEVELOPMENT_PATH).normalize(),
			userDirectory.resolve("..").resolve(DEVELOPMENT_PATH).normalize()
		};
		for (Path candidate : candidates) {
			File file = candidate.toFile();
			if (file.isFile()) {
				return Files.newInputStream(candidate);
			}
		}
		return WorldEditorDefinitionCatalog.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
	}

	private static String normalized(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}

	private static String unescape(String value, int lineNumber) throws IOException {
		StringBuilder result = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char current = value.charAt(i);
			if (current != '\\') {
				result.append(current);
				continue;
			}
			if (++i >= value.length()) {
				throw new IOException("trailing escape at line " + lineNumber);
			}
			char escaped = value.charAt(i);
			switch (escaped) {
				case '\\': result.append('\\'); break;
				case 't': result.append('\t'); break;
				case 'r': result.append('\r'); break;
				case 'n': result.append('\n'); break;
				default: throw new IOException("unknown escape at line " + lineNumber);
			}
		}
		return result.toString();
	}
}
