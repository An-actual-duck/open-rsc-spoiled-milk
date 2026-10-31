package com.openrsc.server.content.cleric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Explicit Cleric identities. Protocol/storage code must use code or key, never ordinal. */
public enum ClericSpellId {
	MEND(0, "cleric.mend"),
	UNIFY(1, "cleric.unify"),
	FERVOR(2, "cleric.fervor"),
	PURIFY(3, "cleric.purify"),
	RESTORE(4, "cleric.restore"),
	WARD(5, "cleric.ward"),
	GREATER_MEND(6, "cleric.greater_mend"),
	ZEAL(7, "cleric.zeal"),
	THORNS(8, "cleric.thorns"),
	AEGIS(9, "cleric.aegis"),
	RALLY(10, "cleric.rally"),
	RESPITE(11, "cleric.respite"),
    SARADOMIN_WARMTH(12, "cleric.saradomin_warmth"),
    SARADOMIN_AURA(13, "cleric.saradomin_aura"),
    SARADOMIN_RESPLENDENCE(14, "cleric.saradomin_resplendence"),
    GUTHIX_RESPITE(15, "cleric.guthix_respite"),
    GUTHIX_PROTECTION(16, "cleric.guthix_protection"),
    GUTHIX_VENGEANCE(17, "cleric.guthix_vengeance"),
    ZAMORAK_FERVOR(18, "cleric.zamorak_fervor"),
    ZAMORAK_FURY(19, "cleric.zamorak_fury"),
    ZAMORAK_WRATH(20, "cleric.zamorak_wrath");

	private static final Map<Integer, ClericSpellId> BY_CODE;
	private static final Map<String, ClericSpellId> BY_KEY;

	static {
		Map<Integer, ClericSpellId> codes = new HashMap<Integer, ClericSpellId>();
		Map<String, ClericSpellId> keys = new HashMap<String, ClericSpellId>();
		for (ClericSpellId spell : ClericSpellId.values()) {
			if (codes.put(spell.code, spell) != null) {
				throw new IllegalStateException("Duplicate Cleric spell code: " + spell.code);
			}
			if (keys.put(spell.key, spell) != null) {
				throw new IllegalStateException("Duplicate Cleric spell key: " + spell.key);
			}
		}
		BY_CODE = Collections.unmodifiableMap(codes);
		BY_KEY = Collections.unmodifiableMap(keys);
	}

	private final int code;
	private final String key;

	ClericSpellId(int code, String key) {
		this.code = code;
		this.key = key;
	}

	public int getCode() {
		return code;
	}

	public String getKey() {
		return key;
	}

    /** Legacy identities retain their codes but cannot be cast from the active book. */
    public boolean isBlessing() { return code >= 12 && code <= 20; }
    public boolean isActive() { return this == UNIFY || isBlessing(); }

	public static ClericSpellId fromCode(int code) {
		ClericSpellId spell = BY_CODE.get(code);
		if (spell == null) {
			throw new IllegalArgumentException("Unknown Cleric spell code: " + code);
		}
		return spell;
	}

	public static ClericSpellId fromKey(String key) {
		ClericSpellId spell = BY_KEY.get(key);
		if (spell == null) {
			throw new IllegalArgumentException("Unknown Cleric spell key: " + key);
		}
		return spell;
	}
}
