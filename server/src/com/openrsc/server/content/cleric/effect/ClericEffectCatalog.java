package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative typed rank data for the nine launch timed effects. */
public final class ClericEffectCatalog {
	private static final int[] TACTICAL_SECONDS = {30, 45, 60, 90};
	private static final int[] RESPITE_MINUTES = {5, 10, 15, 20};
	private static final Set<ClericSpellId> TIMED_SPELLS = Collections.unmodifiableSet(
		EnumSet.of(ClericSpellId.MEND, ClericSpellId.FERVOR, ClericSpellId.WARD,
			ClericSpellId.GREATER_MEND, ClericSpellId.ZEAL, ClericSpellId.THORNS,
			ClericSpellId.AEGIS, ClericSpellId.RALLY, ClericSpellId.RESPITE));

	private static final List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ALL;
	private static final Map<ClericSpellId,
		List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> BY_SPELL;

	static {
		EnumMap<ClericSpellId, List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>>
			bySpell = new EnumMap<ClericSpellId,
				List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>>(ClericSpellId.class);

		addHealing(bySpell, ClericSpellId.MEND, 1, new int[] {1, 2, 3});
		addAccuracy(bySpell);
		addProtection(bySpell, ClericSpellId.WARD, 1, 25, new int[] {2, 4, 6, 8});
		addHealing(bySpell, ClericSpellId.GREATER_MEND, 2, new int[] {2, 3, 4, 5});
		addDamage(bySpell);
		addReflection(bySpell);
		addProtection(bySpell, ClericSpellId.AEGIS, 2, 50, new int[] {1, 2, 3, 4});
		addLifesteal(bySpell);
		addRegeneration(bySpell);

		ArrayList<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> all =
			new ArrayList<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>();
		for (ClericSpellId spellId : ClericSpellId.values()) {
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ranks = bySpell.get(spellId);
			if (TIMED_SPELLS.contains(spellId)) {
				if (ranks == null) {
					throw new IllegalStateException("Missing timed Cleric effect ranks: " + spellId);
				}
				validateRanks(spellId, ranks);
				List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> immutableRanks =
					Collections.unmodifiableList(new ArrayList<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>(ranks));
				bySpell.put(spellId, immutableRanks);
				all.addAll(immutableRanks);
			} else if (ranks != null) {
				throw new IllegalStateException("Instant/movement Cleric spell gained timed state: " + spellId);
			}
		}
		ALL = Collections.unmodifiableList(all);
		BY_SPELL = Collections.unmodifiableMap(bySpell);
	}

	private ClericEffectCatalog() {
	}

	private static void addHealing(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell,
			ClericSpellId spellId, int precedence, int[] hitsPerPulse) {
		for (int index = 0; index < hitsPerPulse.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.HealingPulse>(
				spellId, ClericEffectFamily.HEALING_PULSES, index + 1, precedence,
				ClericEffectDuration.gameTicks(16), ClericEffectCounterKind.PULSES, 3,
				new ClericEffectMagnitudes.HealingPulse(hitsPerPulse[index], 8, 16)));
		}
	}

	private static void addAccuracy(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell) {
		int[] chances = {5, 10, 15, 20};
		for (int index = 0; index < chances.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.Accuracy>(
				ClericSpellId.FERVOR, ClericEffectFamily.ACCURACY, index + 1, 1,
				ClericEffectDuration.seconds(TACTICAL_SECONDS[index]),
				ClericEffectCounterKind.NONE, 0,
				new ClericEffectMagnitudes.Accuracy(chances[index], 1)));
		}
	}

	private static void addProtection(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell,
			ClericSpellId spellId, int precedence, int reductionPercent, int[] charges) {
		for (int index = 0; index < charges.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.Protection>(
				spellId, ClericEffectFamily.PROTECTION, index + 1, precedence,
				ClericEffectDuration.seconds(TACTICAL_SECONDS[index]),
				ClericEffectCounterKind.CHARGES, charges[index],
				new ClericEffectMagnitudes.Protection(reductionPercent)));
		}
	}

	private static void addDamage(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell) {
		int[] bonuses = {5, 8, 11, 15};
		for (int index = 0; index < bonuses.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.Damage>(
				ClericSpellId.ZEAL, ClericEffectFamily.DAMAGE, index + 1, 1,
				ClericEffectDuration.seconds(TACTICAL_SECONDS[index]),
				ClericEffectCounterKind.NONE, 0,
				new ClericEffectMagnitudes.Damage(bonuses[index])));
		}
	}

	private static void addReflection(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell) {
		int[] reflected = {5, 8, 11, 15};
		for (int index = 0; index < reflected.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.Reflection>(
				ClericSpellId.THORNS, ClericEffectFamily.REFLECTION, index + 1, 1,
				ClericEffectDuration.seconds(TACTICAL_SECONDS[index]),
				ClericEffectCounterKind.NONE, 0,
				new ClericEffectMagnitudes.Reflection(reflected[index])));
		}
	}

	private static void addLifesteal(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell) {
		int[] endingHits = {55, 60, 65, 70};
		for (int index = 0; index < endingHits.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.Lifesteal>(
				ClericSpellId.RALLY, ClericEffectFamily.LIFESTEAL, index + 1, 1,
				ClericEffectDuration.seconds(TACTICAL_SECONDS[index]),
				ClericEffectCounterKind.NONE, 0,
				new ClericEffectMagnitudes.Lifesteal(20, endingHits[index])));
		}
	}

	private static void addRegeneration(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell) {
		int[] speed = {10, 15, 20, 25};
		for (int index = 0; index < speed.length; index++) {
			add(bySpell, new ClericEffectRankDefinition<ClericEffectMagnitudes.Regeneration>(
				ClericSpellId.RESPITE, ClericEffectFamily.PASSIVE_REGENERATION, index + 1, 1,
				ClericEffectDuration.minutes(RESPITE_MINUTES[index]),
				ClericEffectCounterKind.NONE, 0,
				new ClericEffectMagnitudes.Regeneration(speed[index])));
		}
	}

	private static void add(Map<ClericSpellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>> bySpell,
			ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition) {
		List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ranks =
			bySpell.get(definition.getSpellId());
		if (ranks == null) {
			ranks = new ArrayList<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>();
			bySpell.put(definition.getSpellId(), ranks);
		}
		if (definition.getRank() != ranks.size() + 1) {
			throw new IllegalStateException("Non-contiguous Cleric effect rank: " + definition.getSpellId());
		}
		ranks.add(definition);
	}

	private static void validateRanks(ClericSpellId spellId,
			List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ranks) {
		ClericSpellDefinition spell = ClericSpellCatalog.get(spellId);
		if (ranks.size() != spell.getEffectRankCount()) {
			throw new IllegalStateException("Cleric effect rank-count drift: " + spellId);
		}
		for (ClericEffectRankDefinition<? extends ClericEffectMagnitude> rank : ranks) {
			if (rank.getFamilyPrecedence() > spell.getSpellTier()) {
				throw new IllegalStateException("Cleric family precedence exceeds spell tier: " + spellId);
			}
		}
	}

	public static boolean hasTimedEffect(ClericSpellId spellId) {
		return spellId != null && TIMED_SPELLS.contains(spellId);
	}

	public static List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> getAll() {
		return ALL;
	}

	public static List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> getRanks(
			ClericSpellId spellId) {
		if (spellId == null) {
			throw new IllegalArgumentException("Cleric spell identity is required");
		}
		List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ranks = BY_SPELL.get(spellId);
		return ranks == null
			? Collections.<ClericEffectRankDefinition<? extends ClericEffectMagnitude>>emptyList()
			: ranks;
	}

	public static ClericEffectRankDefinition<? extends ClericEffectMagnitude> get(
			ClericSpellId spellId, int rank) {
		List<ClericEffectRankDefinition<? extends ClericEffectMagnitude>> ranks = getRanks(spellId);
		if (rank <= 0 || rank > ranks.size()) {
			throw new IllegalArgumentException("Unknown Cleric effect rank: " + spellId + "/" + rank);
		}
		return ranks.get(rank - 1);
	}
}
