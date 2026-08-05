package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.defs.ClericEffectRankDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Formats the server-fed Cleric catalog for the detailed Worship guide. */
public final class ClericSpellGuideCatalog {
	public static final int MAX_LINE_CHARACTERS = 70;

	private ClericSpellGuideCatalog() {
	}

	public static List<Entry> build(List<ClericSpellDef> definitions) {
		if (definitions == null || definitions.isEmpty()) {
			return Collections.emptyList();
		}
		ArrayList<ClericSpellDef> ordered = new ArrayList<ClericSpellDef>(definitions);
		for (ClericSpellDef definition : ordered) {
			if (definition == null) {
				throw new IllegalArgumentException("Cleric guide definitions cannot contain null");
			}
		}
		Collections.sort(ordered, new Comparator<ClericSpellDef>() {
			@Override
			public int compare(ClericSpellDef left, ClericSpellDef right) {
				int levelOrder = Integer.compare(left.getWorshipLevel(), right.getWorshipLevel());
				return levelOrder != 0
					? levelOrder : Integer.compare(left.getStableCode(), right.getStableCode());
			}
		});
		ArrayList<Entry> entries = new ArrayList<Entry>(ordered.size());
		for (ClericSpellDef definition : ordered) {
			entries.add(format(definition));
		}
		return Collections.unmodifiableList(entries);
	}

	private static Entry format(ClericSpellDef definition) {
		String header = "Lvl " + definition.getWorshipLevel() + "  " + definition.getName()
			+ " - " + titleCase(definition.getAlignment()) + " sigil";
		String area = "Area: party allies within " + definition.getRadius()
			+ " tiles; caster excluded";
		return new Entry(definition.getStableCode(), header, area,
			formatMechanics(definition), formatHolyPower(definition));
	}

	private static String formatMechanics(ClericSpellDef definition) {
		String key = definition.getStableKey();
		if ("cleric.mend".equals(key) || "cleric.greater_mend".equals(key)) {
			return "Heals now, then about 5 and 10 seconds later; uses Hits cap";
		}
		if ("cleric.unify".equals(key)) {
			return "Clears walking; pulls allies up to 2 collision-safe steps";
		}
		if ("cleric.fervor".equals(key)) {
			return "Chance to raise a direct attack's offense roll by "
				+ firstSecondaryMagnitude(definition);
		}
		if ("cleric.purify".equals(key)) {
			return "Instantly lowers poison; cures it when power falls below 10";
		}
		if ("cleric.restore".equals(key)) {
			return "Restores all reduced stats except Hits; never creates a boost";
		}
		if ("cleric.ward".equals(key) || "cleric.aegis".equals(key)) {
			return "Reduces direct melee, ranged, and Magic hits by "
				+ firstPrimaryMagnitude(definition) + "%";
		}
		if ("cleric.zeal".equals(key)) {
			return "Raises direct damage after defense; excludes indirect damage";
		}
		if ("cleric.thorns".equals(key)) {
			return "Reflects direct damage; cannot trigger more recoil effects";
		}
		if ("cleric.rally".equals(key)) {
			return "Grants " + firstPrimaryMagnitude(definition)
				+ "% lifesteal while below its ending Hits threshold";
		}
		if ("cleric.respite".equals(key)) {
			return "Speeds natural passive healing, including during combat";
		}
		return definition.getDescription();
	}

	private static String formatHolyPower(ClericSpellDef definition) {
		List<ClericEffectRankDef> ranks = definition.getEffectRanks();
		if (ranks.isEmpty()) {
			if ("cleric.unify".equals(definition.getStableKey())) {
				return "Fixed effect: no Holy Power scaling, status, or charges";
			}
			if ("cleric.purify".equals(definition.getStableKey())) {
				return "Holy Power ranks: removes 10/20/30/40 poison power";
			}
			if ("cleric.restore".equals(definition.getStableKey())) {
				return "Holy Power ranks: restores 10/25/40/60% of each stat";
			}
			return "No timed Holy Power effect";
		}
		int kind = requireSharedPresentationKind(ranks);
		String durations = joinDurations(ranks);
		switch (kind) {
			case 1:
				return "Holy Power: " + joinPrimary(ranks) + " Hits per pulse; "
					+ ranks.get(0).getInitialCounter() + " pulses";
			case 2:
				return "Holy Power: " + joinPrimary(ranks) + "% chance; " + durations;
			case 3:
				return "Holy Power: " + joinCounters(ranks) + " charges; " + durations;
			case 4:
				return "Holy Power: +" + joinPrimary(ranks) + "% damage; " + durations;
			case 5:
				return "Holy Power: reflects " + joinPrimary(ranks) + "%; " + durations;
			case 6:
				return "Holy Power: ends at " + joinSecondary(ranks) + "% Hits; " + durations;
			case 7:
			default:
				return "Holy Power: +" + joinPrimary(ranks) + "% regen speed; " + durations;
		}
	}

	private static int requireSharedPresentationKind(List<ClericEffectRankDef> ranks) {
		int kind = ranks.get(0).getPresentationKind();
		for (ClericEffectRankDef rank : ranks) {
			if (rank.getPresentationKind() != kind) {
				throw new IllegalArgumentException("Cleric guide ranks must share one effect kind");
			}
		}
		return kind;
	}

	private static int firstPrimaryMagnitude(ClericSpellDef definition) {
		return requireRanks(definition).get(0).getPrimaryMagnitude();
	}

	private static int firstSecondaryMagnitude(ClericSpellDef definition) {
		return requireRanks(definition).get(0).getSecondaryMagnitude();
	}

	private static List<ClericEffectRankDef> requireRanks(ClericSpellDef definition) {
		List<ClericEffectRankDef> ranks = definition.getEffectRanks();
		if (ranks.isEmpty()) {
			throw new IllegalArgumentException("Missing Cleric guide effect ranks: "
				+ definition.getStableKey());
		}
		return ranks;
	}

	private static String joinPrimary(List<ClericEffectRankDef> ranks) {
		ArrayList<Integer> values = new ArrayList<Integer>(ranks.size());
		for (ClericEffectRankDef rank : ranks) {
			values.add(rank.getPrimaryMagnitude());
		}
		return join(values);
	}

	private static String joinSecondary(List<ClericEffectRankDef> ranks) {
		ArrayList<Integer> values = new ArrayList<Integer>(ranks.size());
		for (ClericEffectRankDef rank : ranks) {
			values.add(rank.getSecondaryMagnitude());
		}
		return join(values);
	}

	private static String joinCounters(List<ClericEffectRankDef> ranks) {
		ArrayList<Integer> values = new ArrayList<Integer>(ranks.size());
		for (ClericEffectRankDef rank : ranks) {
			values.add(rank.getInitialCounter());
		}
		return join(values);
	}

	private static String joinDurations(List<ClericEffectRankDef> ranks) {
		boolean wholeMinutes = true;
		for (ClericEffectRankDef rank : ranks) {
			wholeMinutes &= rank.getDurationMilliseconds() % 60_000 == 0;
		}
		int divisor = wholeMinutes ? 60_000 : 1_000;
		ArrayList<Integer> values = new ArrayList<Integer>(ranks.size());
		for (ClericEffectRankDef rank : ranks) {
			values.add(rank.getDurationMilliseconds() / divisor);
		}
		return join(values) + (wholeMinutes ? " min" : " sec");
	}

	private static String join(List<Integer> values) {
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < values.size(); index++) {
			if (index > 0) {
				result.append('/');
			}
			result.append(values.get(index));
		}
		return result.toString();
	}

	private static String titleCase(String value) {
		if (value == null || value.isEmpty()) {
			return "Unknown";
		}
		return Character.toUpperCase(value.charAt(0))
			+ value.substring(1).toLowerCase(Locale.ENGLISH);
	}

	public static final class Entry {
		private final int stableCode;
		private final String header;
		private final String area;
		private final String mechanics;
		private final String holyPower;

		private Entry(int stableCode, String header, String area,
				String mechanics, String holyPower) {
			if (stableCode < 0 || tooLong(header) || tooLong(area)
					|| tooLong(mechanics) || tooLong(holyPower)) {
				throw new IllegalArgumentException("Cleric guide entry exceeds its layout bounds");
			}
			this.stableCode = stableCode;
			this.header = header;
			this.area = area;
			this.mechanics = mechanics;
			this.holyPower = holyPower;
		}

		private static boolean tooLong(String line) {
			return line == null || line.isEmpty() || line.length() > MAX_LINE_CHARACTERS;
		}

		public int getStableCode() { return stableCode; }
		public String getHeader() { return header; }
		public String getArea() { return area; }
		public String getMechanics() { return mechanics; }
		public String getHolyPower() { return holyPower; }
	}
}
