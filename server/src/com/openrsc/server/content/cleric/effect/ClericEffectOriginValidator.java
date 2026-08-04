package com.openrsc.server.content.cleric.effect;

/** Fail-closed validation of current caster/session and party-membership state. */
public interface ClericEffectOriginValidator {
	boolean isCurrent(ClericEffectOrigin origin);
}
