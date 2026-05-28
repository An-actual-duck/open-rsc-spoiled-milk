package com.openrsc.server.model.entity.player;

import com.openrsc.server.constants.Skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PrayerCatalog {
	public static final int PRAYERS_PER_BOOK = 15;
	public static final int COMBAT_EFFECT_CAP_PERCENT = 60;
	public static final int[] COMBAT_TIER_POINT_COSTS = {3, 6, 10, 15, 21};
	public static final int[] COMBAT_TIER_EFFECT_PERCENTS = {5, 10, 15, 20, 25};
	public static final int[] SKILLING_TIER_POINT_COSTS = {2, 8, 20, 35, 55};
	public static final int[] SKILLING_TIER_EFFECT_PERCENTS = {10, 15, 20, 25, 30};

	public enum GodLine {
		ZAMORAK,
		SARADOMIN,
		GUTHIX
	}

	public enum PrayerKind {
		OFFENSE,
		DEFENSE,
		SKILLING
	}

	public enum CombatStyle {
		MELEE,
		RANGED,
		MAGIC
	}

	public static final class PrayerDefinition {
		private final GodLine godLine;
		private final PrayerKind kind;
		private final int tier;
		private final String name;
		private final int pointCost;
		private final int effectPercent;
		private final CombatStyle combatStyle;
		private final String skillName;

		private PrayerDefinition(
			final GodLine godLine,
			final PrayerKind kind,
			final int tier,
			final String name,
			final int pointCost,
			final int effectPercent,
			final CombatStyle combatStyle,
			final String skillName
		) {
			this.godLine = godLine;
			this.kind = kind;
			this.tier = tier;
			this.name = name;
			this.pointCost = pointCost;
			this.effectPercent = effectPercent;
			this.combatStyle = combatStyle;
			this.skillName = skillName;
		}

		public GodLine getGodLine() {
			return godLine;
		}

		public PrayerKind getKind() {
			return kind;
		}

		public int getTier() {
			return tier;
		}

		public String getName() {
			return name;
		}

		public int getPointCost() {
			return pointCost;
		}

		public int getEffectPercent() {
			return effectPercent;
		}

		public CombatStyle getCombatStyle() {
			return combatStyle;
		}

		public String getSkillName() {
			return skillName;
		}
	}

	private static final String[] TIER_PREFIXES = {"Weak", "Lesser", "", "Strong", "Greater"};
	private static final List<PrayerDefinition> DEFINITIONS = buildDefinitions();

	private PrayerCatalog() {
	}

	public static List<PrayerDefinition> getDefinitions() {
		return DEFINITIONS;
	}

	public static PrayerDefinition getDefinition(final GodLine godLine, final int slot) {
		if (godLine == null || slot < 0 || slot >= PRAYERS_PER_BOOK) {
			return null;
		}
		int godSlot = 0;
		for (PrayerDefinition definition : DEFINITIONS) {
			if (definition.getGodLine() != godLine) {
				continue;
			}
			if (godSlot == slot) {
				return definition;
			}
			godSlot++;
		}
		return null;
	}

	public static GodLine parseGodLine(final String name) {
		if (name == null) {
			return getDefaultGodLine();
		}
		try {
			return GodLine.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException e) {
			return getDefaultGodLine();
		}
	}

	public static int getBookId(final GodLine godLine) {
		if (godLine == GodLine.ZAMORAK) {
			return 1;
		}
		if (godLine == GodLine.GUTHIX) {
			return 2;
		}
		return 0;
	}

	public static GodLine getDefaultGodLine() {
		return GodLine.SARADOMIN;
	}

	public static GodLine getGodLineForAltar(final int objectId, final int x, final int y) {
		switch (objectId) {
			case 200:
				return GodLine.SARADOMIN;
			case 235:
				return GodLine.GUTHIX;
			case 144:
			case 296:
			case 625:
			case 939:
				return GodLine.ZAMORAK;
			case 19:
				return GodLine.SARADOMIN;
			default:
				return null;
		}
	}

	private static List<PrayerDefinition> buildDefinitions() {
		final List<PrayerDefinition> definitions = new ArrayList<PrayerDefinition>();
		addGodLine(definitions, GodLine.ZAMORAK, CombatStyle.MELEE, CombatStyle.RANGED, Skills.SMITHING);
		addGodLine(definitions, GodLine.SARADOMIN, CombatStyle.MAGIC, CombatStyle.MELEE, "ENCHANTING");
		addGodLine(definitions, GodLine.GUTHIX, CombatStyle.RANGED, CombatStyle.MAGIC, Skills.CRAFTING);
		return Collections.unmodifiableList(definitions);
	}

	private static void addGodLine(
		final List<PrayerDefinition> definitions,
		final GodLine godLine,
		final CombatStyle offenseStyle,
		final CombatStyle defenseStyle,
		final String skillingSkill
	) {
		for (int i = 0; i < COMBAT_TIER_POINT_COSTS.length; i++) {
			definitions.add(new PrayerDefinition(
				godLine,
				PrayerKind.OFFENSE,
				i + 1,
				buildPrayerName(TIER_PREFIXES[i], toTitleCase(offenseStyle.name()), "Power"),
				COMBAT_TIER_POINT_COSTS[i],
				COMBAT_TIER_EFFECT_PERCENTS[i],
				offenseStyle,
				null
			));
		}
		for (int i = 0; i < COMBAT_TIER_POINT_COSTS.length; i++) {
			definitions.add(new PrayerDefinition(
				godLine,
				PrayerKind.DEFENSE,
				i + 1,
				buildPrayerName(TIER_PREFIXES[i], toTitleCase(defenseStyle.name()), "Protection"),
				COMBAT_TIER_POINT_COSTS[i],
				COMBAT_TIER_EFFECT_PERCENTS[i],
				defenseStyle,
				null
			));
		}
		for (int i = 0; i < SKILLING_TIER_POINT_COSTS.length; i++) {
			definitions.add(new PrayerDefinition(
				godLine,
				PrayerKind.SKILLING,
				i + 1,
				buildPrayerName(TIER_PREFIXES[i], toTitleCase(skillingSkill), "Favor"),
				SKILLING_TIER_POINT_COSTS[i],
				SKILLING_TIER_EFFECT_PERCENTS[i],
				null,
				skillingSkill
			));
		}
	}

	private static String buildPrayerName(final String prefix, final String subject, final String suffix) {
		if (prefix.length() == 0) {
			return subject + " " + suffix;
		}
		return prefix + " " + subject + " " + suffix;
	}

	private static String toTitleCase(final String name) {
		final String lower = name.toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
