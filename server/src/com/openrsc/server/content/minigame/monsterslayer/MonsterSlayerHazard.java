package com.openrsc.server.content.minigame.monsterslayer;

/** Explicit preparation hazards attached to a Slayer task definition. */
public enum MonsterSlayerHazard {
	DESERT_HEAT,
	WILDERNESS,
	PRAYER_DRAIN,
	POISON,
	DRAGON_FIRE,
	FROG_SPIT,
	STONY_GLARE,
	BANSHEE_WAIL,
	FEEDING_FRENZY,
	MARKED_LIGHTNING,
	STICKY_FLESH,
	// Strategy-only entries: these do not require a gimmick consumable.
	NAGA_STRATEGY,
	BLOODVELD_STRATEGY,
	BALROG,
	ELDER_DRAGON;

	/** Short, mechanically specific preparation lines, also used on active-task reminders. */
	public String[] getPreparationLines() {
		switch (this) {
		case BALROG: return new String[] {
			"Use the Dwarven Mine ladder after rescuing the dwarven youth.",
			"Bring strong magic defense and plenty of food. Its magic splashes nearby players."
		};
		case ELDER_DRAGON: return new String[] {
			"The elite Mining Guild area requires level 80 Mining.",
			"Bring dragonfire protection, food and poison treatment. Watch for its area attacks."
		};
		case FROG_SPIT: return new String[] {
			"Bring Slime Solvent so its sticky spit cannot stop you attacking.",
			"Bring an antidote for its poison as well."
		};
		case STONY_GLARE: return new String[] {
			"Use Eye Drops before fighting; its glare can freeze you and stop your attacks."
		};
		case BANSHEE_WAIL: return new String[] {
			"Insert Wax earplugs before fighting. They dissolve after ten minutes.",
			"Her wail is deadly without them, whether she attacks up close or with magic."
		};
		case FEEDING_FRENZY: return new String[] {
			"Scatter Dog Treats before fighting to prevent Feeding Frenzy for ten minutes.",
			"Nearby terror dogs add extra bites, so packs are dangerous without treats."
		};
		case MARKED_LIGHTNING: return new String[] {
			"Bring Static discharge wipes. Use one when you feel the air turn tingly.",
			"Running away will not remove the mark.",
			"One wipe clears all your current marks, but a later charge needs another wipe."
		};
		case STICKY_FLESH: return new String[] {
			"Use Slime Solvent before fighting and bring spare doses.",
			"Each damaging hit wears five seconds off its protection.",
			"Without it, sticky flesh stops you moving, fighting or using items.",
			"Bring a melee weapon; its magic and ranged defenses are very high."
		};
		case NAGA_STRATEGY: return new String[] {
			"Bring food for its paired sword strikes. Melee gets through its defenses best.",
			"Fighting at a distance avoids the paired strikes while it throws swords and closes in."
		};
		case BLOODVELD_STRATEGY: return new String[] {
			"Bring food; it heals itself by feeding on the damage it deals.",
			"Its tongue can pull you into melee, so do not rely on keeping your distance."
		};
		default: return new String[0];
		}
	}

	public static MonsterSlayerHazard fromKey(String key) {
		try {
			return valueOf(key);
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException("Unknown Monster Slayer hazard: " + key, failure);
		}
	}
}
