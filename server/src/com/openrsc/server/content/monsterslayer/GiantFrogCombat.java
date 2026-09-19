package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** First Slayer encounter: explicit ranged combat and target-owned timed effects. */
public final class GiantFrogCombat {
	public static final int NPC_ID = 863;
	public static final int SOLVENT_ITEM_ID = 3318;
	public static final int SOLVENT_TWO_DOSE_ID = 3319;
	public static final int SOLVENT_ONE_DOSE_ID = 3320;
	public static int solventDoses(int itemId) {
		if (itemId == SOLVENT_ITEM_ID) return 3;
		if (itemId == SOLVENT_TWO_DOSE_ID) return 2;
		if (itemId == SOLVENT_ONE_DOSE_ID) return 1;
		return 0;
	}
	public static final long SPIT_MILLIS = 10_000L;
	public static final long SOLVENT_MILLIS = 600_000L;
	public static final int POISON_POWER = 10;
	private static final String SPIT = "slayer_frog_spit_until";
	private static final String SOLVENT = "slayer_frog_solvent_until";
	private GiantFrogCombat() { }

	public static boolean isFrog(Mob mob) {
		return mob != null && mob.isNpc() && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	private static long now(Player player) {
		return player.getWorld().getServer().getGameClock().currentTimeMillis();
	}
	private static long remaining(Player player, String key, long maximum) {
		if (!player.getCache().hasKey(key)) return 0;
		long expiry = player.getCache().getLong(key);
		long current = now(player);
		return expiry > current ? Math.min(maximum, expiry - current) : 0;
	}
	public static boolean protectedBySolvent(Player player) {
		return remaining(player, SOLVENT, SOLVENT_MILLIS) > 0;
	}
	public static boolean attacksBlocked(Mob mob) {
		return mob != null && mob.isPlayer() && mob.getConfig().WANT_MYWORLD
			&& remaining((Player) mob, SPIT, SPIT_MILLIS) > 0
			&& !protectedBySolvent((Player) mob);
	}
	/** Called only after an admitted projectile has dealt positive damage. */
	public static void onSpitImpact(Mob source, Player victim, int damageDealt) {
		if (!isFrog(source) || damageDealt <= 0 || victim.killed
			|| victim.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id()) <= 0) return;
		// Poison and the attack lock have separate protections.
		if (!victim.isAntidoteProtected()) {
			victim.applyPoison(POISON_POWER, POISON_POWER, source);
		}
		if (protectedBySolvent(victim)) return;
		victim.getCache().store(SPIT, now(victim) + SPIT_MILLIS);
		victim.message("Slimy Spit coats you. You cannot attack for 10 seconds!");
		ActionSender.sendActivePotionEffects(victim);
	}
	public static void applySolvent(Player player) {
		player.getCache().store(SOLVENT, now(player) + SOLVENT_MILLIS);
		player.getCache().remove(SPIT);
		player.message("Slime Solvent prevents Slimy Spit from stopping your attacks for 10 minutes.");
		ActionSender.sendActivePotionEffects(player);
	}
	public static void appendStatuses(Player player, List<ActiveStatusEntry> statuses) {
		long solvent = remaining(player, SOLVENT, SOLVENT_MILLIS);
		long spit = remaining(player, SPIT, SPIT_MILLIS);
		if (solvent > 0) statuses.add(ActiveStatusEntry.item("slayer:slime_solvent",
			SOLVENT_ITEM_ID, (int) ((solvent + 999) / 1000)));
		else if (spit > 0) statuses.add(ActiveStatusEntry.slayer("slayer:slimy_spit",
			1, SOLVENT_ITEM_ID, (int) ((spit + 999) / 1000)));
	}
}
