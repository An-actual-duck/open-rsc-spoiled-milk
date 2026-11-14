package com.openrsc.client.entityhandling;

import com.openrsc.client.entityhandling.defs.PrayerDef;

import java.util.List;

/** Owns the three client prayer-book catalogs and active-book selection. */
final class PrayerBookDefinitions {
	private static final String SARADOMIN = "SARADOMIN";
	private static final String ZAMORAK = "ZAMORAK";
	private static final String GUTHIX = "GUTHIX";

	private final List<PrayerDef> prayers;
	private String activePrayerBook = SARADOMIN;

	PrayerBookDefinitions(List<PrayerDef> prayers) {
		this.prayers = prayers;
	}

	String activePrayerBook() {
		return activePrayerBook;
	}

	void reloadActiveBook() {
		setPrayerBook(activePrayerBook);
	}

	void setPrayerBook(String prayerBook) {
		if (prayerBook == null) {
			return;
		}
		String normalizedPrayerBook = prayerBook.toUpperCase();
		prayers.clear();
		if (normalizedPrayerBook.equals(ZAMORAK)) {
			activePrayerBook = ZAMORAK;
			loadZamorakPrayerDefinitions();
		} else if (normalizedPrayerBook.equals(GUTHIX)) {
			activePrayerBook = GUTHIX;
			loadGuthixPrayerDefinitions();
		} else {
			activePrayerBook = SARADOMIN;
			loadSaradominPrayerDefinitions();
		}
	}

	private void addPrayerDefinition(int pointCost, String name, String effectText) {
		prayers.add(new PrayerDef(0, pointCost, name,
			"Reserve " + pointCost + " prayer points. " + effectText));
	}

	private void addSpecialPrayerDefinition(int pointCost, String name, String effectText) {
		prayers.add(new PrayerDef(0, pointCost, name, effectText));
	}

	private void loadSaradominPrayerDefinitions() {
		addPrayerDefinition(3, "Weak Magic Power", "Magic damage +5%.");
		addPrayerDefinition(6, "Lesser Magic Power", "Magic damage +10%.");
		addPrayerDefinition(15, "Magic Power", "Magic damage +15%.");
		addPrayerDefinition(29, "Strong Magic Power", "Magic damage +20%.");
		addPrayerDefinition(49, "Greater Magic Power", "Magic damage +25%.");
		addPrayerDefinition(3, "Weak Melee Protection", "Melee damage taken -5%.");
		addPrayerDefinition(6, "Lesser Melee Protection", "Melee damage taken -10%.");
		addPrayerDefinition(15, "Melee Protection", "Melee damage taken -15%.");
		addPrayerDefinition(29, "Strong Melee Protection", "Melee damage taken -20%.");
		addPrayerDefinition(49, "Greater Melee Protection", "Melee damage taken -25%.");
		addPrayerDefinition(2, "Weak Enchanting Favor", "Enchanting XP +10%.");
		addPrayerDefinition(7, "Lesser Enchanting Favor", "Enchanting XP +15%.");
		addPrayerDefinition(22, "Enchanting Favor", "Enchanting XP +20%.");
		addPrayerDefinition(46, "Strong Enchanting Favor", "Enchanting XP +25%.");
		addPrayerDefinition(80, "Greater Enchanting Favor", "Enchanting XP +30%.");
		addSpecialPrayerDefinition(60, "Saving Grace", "Chance to lifesteal 100% of attack damage. Lower HP is more likely to trigger.");
		addSpecialPrayerDefinition(80, "Saradomin's Divine Bolts", "Requires your god crossbow. Free tier 6 bolts; matching cape: 60 points and tier 8 bolts. Overrides equipped ammunition.");
	}

	private void loadZamorakPrayerDefinitions() {
		addPrayerDefinition(3, "Weak Melee Power", "Melee damage +5%.");
		addPrayerDefinition(6, "Lesser Melee Power", "Melee damage +10%.");
		addPrayerDefinition(15, "Melee Power", "Melee damage +15%.");
		addPrayerDefinition(29, "Strong Melee Power", "Melee damage +20%.");
		addPrayerDefinition(49, "Greater Melee Power", "Melee damage +25%.");
		addPrayerDefinition(3, "Weak Ranged Protection", "Ranged damage taken -5%.");
		addPrayerDefinition(6, "Lesser Ranged Protection", "Ranged damage taken -10%.");
		addPrayerDefinition(15, "Ranged Protection", "Ranged damage taken -15%.");
		addPrayerDefinition(29, "Strong Ranged Protection", "Ranged damage taken -20%.");
		addPrayerDefinition(49, "Greater Ranged Protection", "Ranged damage taken -25%.");
		addPrayerDefinition(2, "Weak Smithing Favor", "Smithing XP +10%.");
		addPrayerDefinition(7, "Lesser Smithing Favor", "Smithing XP +15%.");
		addPrayerDefinition(22, "Smithing Favor", "Smithing XP +20%.");
		addPrayerDefinition(46, "Strong Smithing Favor", "Smithing XP +25%.");
		addPrayerDefinition(80, "Greater Smithing Favor", "Smithing XP +30%.");
		addSpecialPrayerDefinition(60, "Divine Retribution", "Chance to recoil double damage taken. Higher hits are more likely to trigger.");
		addSpecialPrayerDefinition(80, "Zamorak's Bolts of Wrath", "Requires your god crossbow. Free tier 6 bolts; matching cape: 60 points and tier 8 bolts. Overrides equipped ammunition.");
	}

	private void loadGuthixPrayerDefinitions() {
		addPrayerDefinition(3, "Weak Ranged Power", "Ranged damage +5%.");
		addPrayerDefinition(6, "Lesser Ranged Power", "Ranged damage +10%.");
		addPrayerDefinition(15, "Ranged Power", "Ranged damage +15%.");
		addPrayerDefinition(29, "Strong Ranged Power", "Ranged damage +20%.");
		addPrayerDefinition(49, "Greater Ranged Power", "Ranged damage +25%.");
		addPrayerDefinition(3, "Weak Magic Protection", "Magic damage taken -5%.");
		addPrayerDefinition(6, "Lesser Magic Protection", "Magic damage taken -10%.");
		addPrayerDefinition(15, "Magic Protection", "Magic damage taken -15%.");
		addPrayerDefinition(29, "Strong Magic Protection", "Magic damage taken -20%.");
		addPrayerDefinition(49, "Greater Magic Protection", "Magic damage taken -25%.");
		addPrayerDefinition(2, "Weak Crafting Favor", "Crafting XP +10%.");
		addPrayerDefinition(7, "Lesser Crafting Favor", "Crafting XP +15%.");
		addPrayerDefinition(22, "Crafting Favor", "Crafting XP +20%.");
		addPrayerDefinition(46, "Strong Crafting Favor", "Crafting XP +25%.");
		addPrayerDefinition(80, "Greater Crafting Favor", "Crafting XP +30%.");
		addSpecialPrayerDefinition(60, "Corrosive Aura", "Enemies that damage you receive 10-50 poison stacks. Lower HP applies more stacks.");
		addSpecialPrayerDefinition(80, "Guthix's Thorned Bolts", "Requires your god crossbow. Free tier 6 bolts; matching cape: 60 points and tier 8 bolts. Overrides equipped ammunition.");
	}
}
