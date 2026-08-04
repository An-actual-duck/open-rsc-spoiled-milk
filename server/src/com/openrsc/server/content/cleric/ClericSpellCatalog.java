package com.openrsc.server.content.cleric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative launch metadata shared by maintained-client presentation and
 * the bounded Cleric cast-request boundary. It remains independent of the
 * legacy Magic spell list and does not implement spell effects.
 */
public final class ClericSpellCatalog {
	public static final int SCHEMA_VERSION = 2;
	public static final int MAX_LAUNCH_HOLY_POWER = 64;

	private static final List<ClericSpellDefinition> DEFINITIONS;
	private static final Map<ClericSpellId, ClericSpellDefinition> BY_ID;
	private static final Map<Integer, ClericSpellDefinition> BY_CODE;
	private static final Map<String, ClericSpellDefinition> BY_KEY;

	static {
		List<ClericSpellDefinition> definitions = new ArrayList<ClericSpellDefinition>();
		definitions.add(definition(ClericSpellId.MEND, "Mend",
			"Heals nearby party members over three pulses.", ClericAlignment.SARADOMIN,
			1, 1, 2, 0, 12, 28));
		definitions.add(definition(ClericSpellId.UNIFY, "Unify",
			"Draws distant nearby party members closer.", ClericAlignment.NEUTRAL,
			3, 1, 4, 0));
		definitions.add(definition(ClericSpellId.FERVOR, "Fervor",
			"Improves nearby party members' accuracy.", ClericAlignment.ZAMORAK,
			5, 1, 2, 0, 12, 28, 44));
		definitions.add(definition(ClericSpellId.PURIFY, "Purify",
			"Reduces poison on nearby party members.", ClericAlignment.GUTHIX,
			8, 1, 2, 0, 12, 28, 44));
		definitions.add(definition(ClericSpellId.RESTORE, "Restore",
			"Restores reduced stats other than Hits.", ClericAlignment.GUTHIX,
			11, 1, 2, 0, 12, 28, 44));
		definitions.add(definition(ClericSpellId.WARD, "Ward",
			"Reduces several direct hits by 25 percent.", ClericAlignment.SARADOMIN,
			14, 1, 2, 0, 12, 24, 32));
		definitions.add(definition(ClericSpellId.GREATER_MEND, "Greater Mend",
			"Heals nearby party members with stronger pulses.", ClericAlignment.SARADOMIN,
			16, 2, 3, 0, 24, 44, 64));
		definitions.add(definition(ClericSpellId.ZEAL, "Zeal",
			"Improves nearby party members' direct damage.", ClericAlignment.ZAMORAK,
			19, 2, 3, 0, 24, 44, 64));
		definitions.add(definition(ClericSpellId.THORNS, "Thorns",
			"Reflects some direct damage taken by nearby allies.", ClericAlignment.GUTHIX,
			22, 2, 3, 0, 24, 44, 64));
		definitions.add(definition(ClericSpellId.AEGIS, "Aegis",
			"Reduces a few direct hits by 50 percent.", ClericAlignment.SARADOMIN,
			25, 2, 3, 0, 24, 44, 64));
		definitions.add(definition(ClericSpellId.RALLY, "Rally",
			"Grants injured nearby allies temporary lifesteal.", ClericAlignment.ZAMORAK,
			28, 2, 3, 0, 24, 44, 64));
		definitions.add(definition(ClericSpellId.RESPITE, "Respite",
			"Improves nearby party members' passive healing.", ClericAlignment.NEUTRAL,
			30, 2, 3, 0, 24, 44, 64));

		EnumMap<ClericSpellId, ClericSpellDefinition> ids =
			new EnumMap<ClericSpellId, ClericSpellDefinition>(ClericSpellId.class);
		Map<Integer, ClericSpellDefinition> codes = new HashMap<Integer, ClericSpellDefinition>();
		Map<String, ClericSpellDefinition> keys = new HashMap<String, ClericSpellDefinition>();
		for (ClericSpellDefinition definition : definitions) {
			putUnique(ids, definition.getId(), definition, "identity");
			putUnique(codes, definition.getStableCode(), definition, "code");
			putUnique(keys, definition.getStableKey(), definition, "key");
			validateDefinition(definition);
		}
		if (ids.size() != ClericSpellId.values().length) {
			throw new IllegalStateException("Every Cleric spell identity requires one definition");
		}
		for (int index = 0; index < definitions.size(); index++) {
			if (definitions.get(index).getStableCode() != index) {
				throw new IllegalStateException("Launch Cleric catalog must remain in stable-code order");
			}
		}
		DEFINITIONS = Collections.unmodifiableList(new ArrayList<ClericSpellDefinition>(definitions));
		BY_ID = Collections.unmodifiableMap(ids);
		BY_CODE = Collections.unmodifiableMap(codes);
		BY_KEY = Collections.unmodifiableMap(keys);
	}

	private ClericSpellCatalog() {
	}

	private static ClericSpellDefinition definition(ClericSpellId id, String displayName,
			String effectDescription,
			ClericAlignment alignment, int worshipLevel, int spellTier, int radius,
			int... holyPowerThresholds) {
		int spellbookIconItemId = ClericSigilItemId.get(
			ClericSigilMaterial.STONE, alignment, true).getItemId();
		return new ClericSpellDefinition(id, displayName, effectDescription, alignment,
			worshipLevel, spellTier,
			radius, false, ClericSigilCost.forLaunchTier(spellTier),
			new ClericSpellPresentation(spellbookIconItemId, statusIconAssetKey(id),
				ClericSpellPresentation.NONE, ClericSpellPresentation.NONE),
			holyPowerThresholds);
	}

	private static String statusIconAssetKey(ClericSpellId id) {
		// All twelve launch icons use the owner-approved Atelier Pixerelia set.
		return id.getKey().substring("cleric.".length()).replace('_', '-');
	}

	private static void validateDefinition(ClericSpellDefinition definition) {
		int expectedRadius = definition.getId() == ClericSpellId.UNIFY
			? 4 : definition.getSpellTier() + 1;
		if (definition.getRadius() != expectedRadius) {
			throw new IllegalStateException("Invalid Cleric radius for " + definition.getStableKey());
		}
		if (definition.affectsCaster()) {
			throw new IllegalStateException("Launch Cleric spells must exclude the caster");
		}
		int[] thresholds = definition.getHolyPowerThresholds();
		if (thresholds[thresholds.length - 1] > MAX_LAUNCH_HOLY_POWER) {
			throw new IllegalStateException("Cleric Holy Power threshold exceeds the launch staff scale");
		}
		ClericSigilCost expectedCost = ClericSigilCost.forLaunchTier(definition.getSpellTier());
		if (!expectedCost.equals(definition.getPrimarySigilCost())) {
			throw new IllegalStateException("Invalid Cleric sigil cost for " + definition.getStableKey());
		}
	}

	private static <K> void putUnique(Map<K, ClericSpellDefinition> values, K key,
			ClericSpellDefinition definition, String label) {
		if (values.put(key, definition) != null) {
			throw new IllegalStateException("Duplicate Cleric spell " + label + ": " + key);
		}
	}

	public static List<ClericSpellDefinition> getAll() {
		return DEFINITIONS;
	}

	public static ClericSpellDefinition get(ClericSpellId id) {
		ClericSpellDefinition definition = BY_ID.get(id);
		if (definition == null) {
			throw new IllegalArgumentException("Unknown Cleric spell identity: " + id);
		}
		return definition;
	}

	public static ClericSpellDefinition fromCode(int code) {
		ClericSpellDefinition definition = BY_CODE.get(code);
		if (definition == null) {
			throw new IllegalArgumentException("Unknown Cleric spell code: " + code);
		}
		return definition;
	}

	public static ClericSpellDefinition fromKey(String key) {
		ClericSpellDefinition definition = BY_KEY.get(key);
		if (definition == null) {
			throw new IllegalArgumentException("Unknown Cleric spell key: " + key);
		}
		return definition;
	}
}
