package orsc;

import com.openrsc.client.entityhandling.defs.ClericSpellDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Session-local validated snapshot of the server's Cleric spell catalog. */
public final class ClericSpellbookCatalog {
	// Activate only with the matching checked packet decoder.
	public static final int SCHEMA_VERSION = ClericBlessingContract.SCHEMA_VERSION;
	public static final int MAX_DEFINITIONS = ClericBlessingContract.MAX_DEFINITIONS;

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
        Set<Integer> codes = new HashSet<Integer>();
		for (int index = 0; index < nextDefinitions.size(); index++) {
			ClericSpellDef definition = nextDefinitions.get(index);
			if (definition == null || definition.getStableCode() < 0 || definition.getStableCode() > 65535
                    || definition.getStableCode()==0 || (definition.getStableCode()>=2 && definition.getStableCode()<=11)
                    || !codes.add(definition.getStableCode())
					|| !keys.add(definition.getStableKey())) {
				throw new IllegalArgumentException("Invalid Cleric spell identity at index " + index);
			}
			copy.add(definition);
		}
        Collections.sort(copy,new java.util.Comparator<ClericSpellDef>(){public int compare(ClericSpellDef a,ClericSpellDef b){int level=Integer.compare(a.getWorshipLevel(),b.getWorshipLevel());return level!=0?level:Integer.compare(a.getStableCode(),b.getStableCode());}});
		definitions = Collections.unmodifiableList(copy);
	}

	public synchronized void clear() {
		definitions = Collections.emptyList();
	}

	public synchronized int size() {
		return definitions.size();
	}

    public synchronized ClericSpellDef get(int stableCode) {
        for (ClericSpellDef definition : definitions) if (definition.getStableCode() == stableCode) return definition;
        return null;
    }
    public synchronized int codeAtIndex(int index){ClericSpellDef d=atIndex(index);return d==null?-1:d.getStableCode();}
    /** UI position is separate from protocol identity; retired codes leave deliberate gaps. */
    public synchronized ClericSpellDef atIndex(int index) {
        return index < 0 || index >= definitions.size() ? null : definitions.get(index);
    }

	public synchronized List<ClericSpellDef> snapshot() {
		return definitions;
	}
}
