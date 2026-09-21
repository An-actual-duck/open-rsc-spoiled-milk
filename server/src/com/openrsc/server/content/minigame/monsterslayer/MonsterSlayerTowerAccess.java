package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.player.Player;

/** Placement-independent tower entry policy. Door bindings are added after the map is authored. */
public enum MonsterSlayerTowerAccess {
	FLEDGLING(871, MonsterSlayerRank.FLEDGLING,
		"I can't let you in until you've joined the Monster Slayer's Guild, pal.",
		"Go on through, friend."),
	ADEPT(872, MonsterSlayerRank.INITIATE,
		"Slow down before you hurt yourself. Adept rank or higher past this point.",
		"All right, up you go. Watch your step."),
	VETERAN(873, MonsterSlayerRank.VETERAN,
		"I don't see a Veteran button. No button, no passage. Regulations.",
		"Your credentials are in order. Proceed."),
	ELITE(874, MonsterSlayerRank.ELITE,
		"Elite rank or higher. I'm tired of carrying people back down these stairs.",
		"Through you go. Coming back down is your responsibility."),
	CHAMPION(875, MonsterSlayerRank.CHAMPION,
		"Champions only. Everyone else tends to come back extra crispy.",
		"A Champion! Lovely. Try not to scorch the handrail."),
	HERO(876, MonsterSlayerRank.HERO,
		"Only Heroes beyond this door. Whatever you hear, don't answer it.",
		"You've earned your way in. I hope that's a good thing.");

	private final int npcId;
	private final MonsterSlayerRank requiredRank;
	private final String refusal, welcome;

	MonsterSlayerTowerAccess(int npcId, MonsterSlayerRank requiredRank, String refusal, String welcome) {
		this.npcId = npcId;
		this.requiredRank = requiredRank;
		this.refusal = refusal;
		this.welcome = welcome;
	}

	public int getNpcId() { return npcId; }
	public MonsterSlayerRank getRequiredRank() { return requiredRank; }
	public String getRefusal() { return refusal; }
	public String getWelcome() { return welcome; }
	public boolean allows(MonsterSlayerRank rank) { return rank != null && rank.isAtLeast(requiredRank); }
	public boolean allows(Player player) { return allows(readRank(player)); }

	public static MonsterSlayerTowerAccess forNpc(int npcId) {
		for (MonsterSlayerTowerAccess entry : values()) if (entry.npcId == npcId) return entry;
		return null;
	}

	/** Read only: never enroll, migrate/write, spend points or require a physical rank token. */
	public static MonsterSlayerRank readRank(Player player) {
		if (player == null || !player.getConfig().WANT_MYWORLD) return null;
		MonsterSlayerData data = player.getWorld().getMonsterSlayerData();
		if (data == null) return null;
		try {
			return MonsterSlayerState.read(player.getCache(), data).getRank();
		} catch (IllegalArgumentException invalidState) {
			return null; // Fail closed without misrepresenting a corrupt record as an unjoined player.
		}
	}
}
