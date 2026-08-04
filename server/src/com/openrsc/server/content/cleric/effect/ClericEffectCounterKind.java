package com.openrsc.server.content.cleric.effect;

/** Explicit counter semantics; clients must never infer these from an icon. */
public enum ClericEffectCounterKind {
	NONE(0),
	CHARGES(1),
	PULSES(2);

	private final int code;

	ClericEffectCounterKind(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static ClericEffectCounterKind fromCode(int code) {
		for (ClericEffectCounterKind kind : values()) {
			if (kind.code == code) {
				return kind;
			}
		}
		throw new IllegalArgumentException("Unknown Cleric effect counter kind: " + code);
	}
}
