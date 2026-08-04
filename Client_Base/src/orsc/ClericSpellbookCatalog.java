package orsc;

import com.openrsc.client.entityhandling.defs.ClericSpellDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Session-local validated snapshot of the server's Cleric spell catalog. */
public final class ClericSpellbookCatalog {
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_DEFINITIONS = 32;

	private List<ClericSpellDef> definitions = Collections.emptyList();

	public synchronized void replace(int schemaVersion, List<ClericSpellDef> nextDefinitions) {
		if (schemaVersion != SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported Cleric spellbook schema: " + schemaVersion);
		}
		if (nextDefinitions == null || nextDefinitions.size() > MAX_DEFINITIONS) {
			throw new IllegalArgumentException("Invalid Cleric spellbook definition count");
		}
		ArrayList<ClericSpellDef> copy = new ArrayList<ClericSpellDef>(nextDefinitions.size());
		Set<String> keys = new HashSet<String>();
		for (int index = 0; index < nextDefinitions.size(); index++) {
			ClericSpellDef definition = nextDefinitions.get(index);
			if (definition == null || definition.getStableCode() != index
					|| !keys.add(definition.getStableKey())) {
				throw new IllegalArgumentException("Invalid Cleric spell identity at index " + index);
			}
			copy.add(definition);
		}
		definitions = Collections.unmodifiableList(copy);
	}

	public synchronized void clear() {
		definitions = Collections.emptyList();
	}

	public synchronized int size() {
		return definitions.size();
	}

	public synchronized ClericSpellDef get(int stableCode) {
		if (stableCode < 0 || stableCode >= definitions.size()) {
			return null;
		}
		return definitions.get(stableCode);
	}

	public synchronized List<ClericSpellDef> snapshot() {
		return definitions;
	}
}
