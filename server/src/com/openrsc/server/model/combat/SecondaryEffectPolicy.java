package com.openrsc.server.model.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable identities for the current secondary-damage policy boundaries.
 *
 * <p>This catalog is deliberately descriptive. It does not select targets,
 * execute procs, apply damage, order child effects, or own death settlement.
 * Those responsibilities remain with the existing callers until each effect
 * family has executable ordering and eligibility coverage.</p>
 *
 * <p>The catalog has no imported fixed registration cap. Its current size is
 * derived from the policies declared here, so adding a reviewed current or
 * planned effect cannot silently exceed an unrelated historical limit.</p>
 */
public enum SecondaryEffectPolicy {
	RECIPROCAL_MELEE_AUXILIARY_MAGIC(
		"reciprocal-melee-auxiliary-magic", Family.AUXILIARY),
	RECIPROCAL_MELEE_AUXILIARY_TRUE(
		"reciprocal-melee-auxiliary-true", Family.AUXILIARY),
	PVM_MELEE_AUXILIARY_MAGIC(
		"pvm-melee-auxiliary-magic", Family.AUXILIARY),
	PVM_MELEE_AUXILIARY_TRUE(
		"pvm-melee-auxiliary-true", Family.AUXILIARY),
	PROJECTILE_AUXILIARY_MAGIC(
		"projectile-auxiliary-magic", Family.AUXILIARY),
	PROJECTILE_AUXILIARY_TRUE(
		"projectile-auxiliary-true", Family.AUXILIARY),

	RECIPROCAL_MELEE_FROSTBITE_REFLECTION(
		"reciprocal-melee-frostbite-reflection", Family.REFLECTION),
	PVM_MELEE_FROSTBITE_REFLECTION(
		"pvm-melee-frostbite-reflection", Family.REFLECTION),
	PROJECTILE_FROSTBITE_REFLECTION(
		"projectile-frostbite-reflection", Family.REFLECTION),
	RECIPROCAL_MELEE_CLERIC_THORNS(
		"reciprocal-melee-cleric-thorns", Family.REFLECTION),
	PVM_MELEE_CLERIC_THORNS(
		"pvm-melee-cleric-thorns", Family.REFLECTION),
	PROJECTILE_CLERIC_THORNS(
		"projectile-cleric-thorns", Family.REFLECTION),
	RECIPROCAL_MELEE_JEWELRY_RECOIL(
		"reciprocal-melee-jewelry-recoil", Family.REFLECTION),
	PVM_MELEE_JEWELRY_RECOIL(
		"pvm-melee-jewelry-recoil", Family.REFLECTION),
	PROJECTILE_JEWELRY_RECOIL(
		"projectile-jewelry-recoil", Family.REFLECTION),
	DIVINE_RETRIBUTION("divine-retribution", Family.REFLECTION),

	RECIPROCAL_MELEE_CHAIN_LIGHTNING(
		"reciprocal-melee-chain-lightning", Family.PLAYER_CHILD),
	PVM_MELEE_CHAIN_LIGHTNING(
		"pvm-melee-chain-lightning", Family.PLAYER_CHILD),
	PROJECTILE_CHAIN_LIGHTNING(
		"projectile-chain-lightning", Family.PLAYER_CHILD),
	PROJECTILE_SPLINTER("projectile-splinter", Family.PLAYER_CHILD),
	PROJECTILE_BLOOD_ROBE_SPLASH(
		"projectile-blood-robe-splash", Family.PLAYER_CHILD),
	RECIPROCAL_MELEE_DEATH_ROBE_OVERKILL(
		"reciprocal-melee-death-robe-overkill", Family.PLAYER_CHILD),
	PVM_MELEE_DEATH_ROBE_OVERKILL(
		"pvm-melee-death-robe-overkill", Family.PLAYER_CHILD),
	PROJECTILE_DEATH_ROBE_OVERKILL(
		"projectile-death-robe-overkill", Family.PLAYER_CHILD),
	PVM_MELEE_SCYTHE_CLEAVE(
		"pvm-melee-scythe-cleave", Family.PLAYER_CHILD),
	PLAYER_DEATH_AMULET_BURST(
		"player-death-amulet-burst", Family.PLAYER_CHILD),
	PLAYER_DEATH_RING_CHARGE_HIT(
		"player-death-ring-charge-hit", Family.PLAYER_CHILD),

	PROJECTILE_BALROG_MAGIC_SPLASH(
		"projectile-balrog-magic-splash", Family.OWNED_CONTENT),
	ELDER_GREEN_DRAGON_MELEE_SWEEP(
		"elder-green-dragon-melee-sweep", Family.OWNED_CONTENT),
	ELDER_GREEN_DRAGON_RANGED_FIRESHOT(
		"elder-green-dragon-ranged-fireshot", Family.OWNED_CONTENT),
	ELDER_GREEN_DRAGON_MAGIC_SECONDARY(
		"elder-green-dragon-magic-secondary", Family.OWNED_CONTENT),
	ELDER_GREEN_DRAGON_BURN_PULSE(
		"elder-green-dragon-burn-pulse", Family.OWNED_CONTENT),
	ELDER_GREEN_DRAGON_ARMOR_TRUE_DAMAGE(
		"elder-green-dragon-armor-true-damage", Family.OWNED_CONTENT),
	ELDER_GREEN_DRAGON_ARMOR_BURN(
		"elder-green-dragon-armor-burn", Family.OWNED_CONTENT),
	SUMMON_BONUS_MAGIC("summon-bonus-magic", Family.OWNED_CONTENT),
	SUMMON_BONUS_MELEE("summon-bonus-melee", Family.OWNED_CONTENT),

	DELAYED_GOD_SPELL_SECONDARY(
		"delayed-god-spell-secondary", Family.DELAYED_SPELL),
	DELAYED_IBAN_BLAST_SECONDARY(
		"delayed-iban-blast-secondary", Family.DELAYED_SPELL),
	DELAYED_SALARIN_STRIKE_SECONDARY(
		"delayed-salarin-strike-secondary", Family.DELAYED_SPELL);

	public enum Family {
		AUXILIARY,
		REFLECTION,
		PLAYER_CHILD,
		OWNED_CONTENT,
		DELAYED_SPELL
	}

	private static final Map<String, SecondaryEffectPolicy> BY_STABLE_KEY;

	static {
		final Map<String, SecondaryEffectPolicy> policies =
			new LinkedHashMap<String, SecondaryEffectPolicy>();
		for (final SecondaryEffectPolicy policy : values()) {
			if (policy.stableKey.isEmpty()) {
				throw new IllegalStateException(
					"Secondary effect policy requires a stable key");
			}
			final SecondaryEffectPolicy previous =
				policies.put(policy.stableKey, policy);
			if (previous != null) {
				throw new IllegalStateException(
					"Duplicate secondary effect policy key " + policy.stableKey);
			}
		}
		BY_STABLE_KEY = Collections.unmodifiableMap(policies);
	}

	private final String stableKey;
	private final Family family;

	SecondaryEffectPolicy(final String stableKey, final Family family) {
		this.stableKey = stableKey == null ? "" : stableKey.trim();
		if (family == null) {
			throw new IllegalArgumentException(
				"Secondary effect policy requires a family");
		}
		this.family = family;
	}

	public String getStableKey() {
		return stableKey;
	}

	public Family getFamily() {
		return family;
	}

	public static SecondaryEffectPolicy forStableKey(final String stableKey) {
		if (stableKey == null) {
			return null;
		}
		return BY_STABLE_KEY.get(stableKey);
	}

	public static Map<String, SecondaryEffectPolicy> byStableKey() {
		return BY_STABLE_KEY;
	}

	public static int currentPolicyCount() {
		return BY_STABLE_KEY.size();
	}
}
