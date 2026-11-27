package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.defs.ClericComponentDef;
import com.openrsc.client.entityhandling.defs.ClericEffectRankDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orsc.ClericBlessingContract;

/** A plain-language summary of the strongest rank available to each spell. */
final class ClericGuideDescriptions {
	private ClericGuideDescriptions() {
	}

	static List<String> describe(ClericSpellDef spell) {
		List<ClericEffectRankDef> ranks = spell.getEffectRanks();
		if (ranks.isEmpty()) return Collections.singletonList(spell.getDescription());
		List<ClericComponentDef> components = ranks.get(ranks.size() - 1).getComponents();
		if (components.isEmpty()) return Collections.singletonList(spell.getDescription());

		Map<String, String> effects = new LinkedHashMap<>();
		for (ClericComponentDef component : components) {
			effects.merge(component.getName(), effect(component), (first, next) -> first + " " + next);
		}
		List<String> paragraphs = new ArrayList<>();
		paragraphs.add(spell.isHostile() ? "Debuffs" : "Buffs");
		effects.forEach((name, effect) -> paragraphs.add(name + "  -  " + effect));
		return paragraphs;
	}

	private static String effect(ClericComponentDef component) {
		int amount = component.getMagnitude();
		String percent = amount + "%";
		switch (component.getKind()) {
			case ClericBlessingContract.UNDEAD_DAMAGE:
				return "Direct melee, magic, and ranged attacks deal up to " + percent + " additional damage against undead.";
			case ClericBlessingContract.REGENERATION:
				return "Natural Hits regeneration reaches up to " + percent + " of its usual rate.";
			case ClericBlessingContract.HEALING_PULSE:
				return "Restores up to " + amount + " Hits per healing pulse.";
			case ClericBlessingContract.POISON_CLEANSING:
				return "Clears up to " + amount + " extra poison strength per pulse.";
			case ClericBlessingContract.STAT_RESTORATION:
				return "Gradually restores drained stats.";
			case ClericBlessingContract.DEFENSE:
				return "Raises ranged defense by up to " + percent + ".";
			case ClericBlessingContract.REFLECTION:
				return "Reflects up to " + percent + " of direct damage received.";
			case ClericBlessingContract.MAX_DAMAGE:
				return "Raises maximum direct melee damage by up to " + percent + ".";
			case ClericBlessingContract.DEALT_DAMAGE:
				return "Increases direct melee damage dealt by up to " + percent + ".";
			case ClericBlessingContract.LIFESTEAL:
				return "At low Hits, heals you for up to " + percent + " of direct damage dealt.";
			case ClericBlessingContract.POISON_WEAKNESS:
				return "Enemies take up to " + percent + " more poison damage and clear poison more slowly.";
			case ClericBlessingContract.SUMMON_WEAKNESS:
				return "Enemies take up to " + percent + " more damage from summons.";
			case ClericBlessingContract.RECOIL_WEAKNESS:
				return "Enemies take up to " + percent + " more recoil and reflected damage.";
			case ClericBlessingContract.MAGIC_DEFENSE_REDUCTION:
				return "Reduces enemy magic defense by up to " + percent + ".";
			case ClericBlessingContract.MAX_MELEE_REDUCTION:
				return "Reduces the enemy's maximum melee damage by up to " + percent + ".";
			case ClericBlessingContract.NON_CORPOREAL:
				return "Enemies have up to a " + percent + " chance to briefly phase out of physical combat. They can still deal and take magic damage.";
			case ClericBlessingContract.CRITICAL_VULNERABILITY:
				return "Main attacks against the enemy have up to a " + percent + " chance to land a critical hit for maximum damage.";
			case ClericBlessingContract.CRITICAL_STAGGER:
				return "Critical hits have up to a " + percent + " chance to stagger the enemy and cancel its next attack.";
			case ClericBlessingContract.CRITICAL_EXECUTION:
				return "Critical hits have up to a " + percent + " chance to finish a weakened ordinary enemy.";
			default:
				throw new IllegalArgumentException("Unknown Cleric guide effect " + component.getKind());
		}
	}
}
