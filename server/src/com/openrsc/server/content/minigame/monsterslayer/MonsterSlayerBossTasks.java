package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

/** Independent, persistent preferences; access never implies consent to an assignment. */
public final class MonsterSlayerBossTasks {
	private MonsterSlayerBossTasks() { }
	public enum Boss {
		BALROG("balrog", "Balrog", 809, "slayer_opt_balrog"),
		ELDER_DRAGON("elder_green_dragon", "Elder Green Dragon", 844, "slayer_opt_elder_dragon");
		public final String familyKey, displayName;
		public final int npcId;
		private final String cacheKey;
		Boss(String familyKey, String displayName, int npcId, String cacheKey) {
			this.familyKey = familyKey; this.displayName = displayName; this.npcId = npcId; this.cacheKey = cacheKey;
		}
		public String taskKey() { return "legends." + familyKey + ".repeatable"; }
		public boolean optedIn(Player player) { return player.getCache().hasKey(cacheKey) && player.getCache().getBoolean(cacheKey); }
		public boolean accessible(Player player) {
			// Same predicates as the Dwarven Mine forge ladder and elite Mining Guild door.
			return this == BALROG ? player.getConfig().WANT_CUSTOM_QUESTS
				&& player.getCache().hasKey("miniquest_dwarf_youth_rescue")
				: player.getSkills().getLevel(Skill.MINING.id()) >= 80;
		}
		public String accessMessage() { return this == BALROG
			? "Rescue the dwarven youth to gain access to the Balrog's cavern first."
			: "You need level 80 Mining to enter the Elder Green Dragon's area."; }
	}
	public static boolean eligible(Player player, String taskKey) {
		for (Boss boss : Boss.values()) if (boss.taskKey().equals(taskKey)) return boss.optedIn(player) && boss.accessible(player);
		return true;
	}
	/** Returns a player-facing result; opting out never awards points or rerolls a task. */
	public static String choose(Player player, MonsterSlayerData data, Boss boss, boolean enable) {
		synchronized (player) {
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			if (enable) {
				if (!boss.accessible(player)) return boss.accessMessage();
				player.getCache().store(boss.cacheKey, true);
				return boss.displayName + " tasks are now enabled for your eligible Legends' Guild assignments.";
			}
			MonsterSlayerState.write(player.getCache(), data, MonsterSlayerState.cancelTask(current, data, boss.taskKey()));
			player.getCache().remove(boss.cacheKey);
			return boss.displayName + " tasks are disabled. Any current task for that boss was cancelled without rewards.";
		}
	}
}
