package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** Positional hybrid combat. Wail replaces a primary hit; it is never a second hit. */
public final class BansheeCombat {
	public static final int NPC_ID = 865;
	public static final int EARPLUGS_ID = 3324;
	public static final long PROTECTION_MILLIS = 600_000L;
	public static final String WAIL_MESSAGE = "The Banshee's wail pierces your eardrums and ripples through your body";
	private static final String PROTECTION = "slayer_wax_earplugs_until";
	private static final String NEXT_ATTACK = "slayer_banshee_next_attack_tick";
	private BansheeCombat() { }
	public static int uses(int id) { return id >= EARPLUGS_ID && id <= EARPLUGS_ID + 2 ? EARPLUGS_ID + 3 - id : 0; }
	public static boolean isBanshee(Mob mob) {
		return mob != null && mob.isNpc() && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	private static long remaining(Player player) {
		if (!player.getCache().hasKey(PROTECTION)) return 0;
		return Math.max(0, Math.min(PROTECTION_MILLIS, player.getCache().getLong(PROTECTION)
			- player.getWorld().getServer().getGameClock().currentTimeMillis()));
	}
	public static boolean protectedByEarplugs(Player player) { return remaining(player) > 0; }
	public static boolean usesWail(Mob source, Mob target) {
		return isBanshee(source) && target != null && target.isPlayer() && !protectedByEarplugs((Player) target);
	}
	public static int maxWailDamage(Player player) {
		return (int) (Math.max(0L, player.getHealingMaximumHits()) / 2L);
	}
	public static int rollWail(Mob source, Player target) {
		return source.getWorld().getServer().getCombatRandom().nextInt(maxWailDamage(target) + 1);
	}
	public static void onDamage(Mob target, int dealt, boolean wail) {
		if (wail && dealt > 0 && target.isPlayer()) ((Player) target).message(WAIL_MESSAGE);
	}
	public static boolean attackReady(Mob source) {
		return source.getWorld().getServer().getCurrentTick() >= source.getAttribute(NEXT_ATTACK, -1L);
	}
	public static void recordAttack(Mob source, int ticks) {
		source.setAttribute(NEXT_ATTACK, source.getWorld().getServer().getCurrentTick() + com.openrsc.server.content.Slow.delay(source, ticks));
	}
	public static void applyEarplugs(Player player) {
		player.getCache().store(PROTECTION, player.getWorld().getServer().getGameClock().currentTimeMillis() + PROTECTION_MILLIS);
		player.message("You insert the Wax earplugs. They protect you from the Banshee's wail for 10 minutes.");
		ActionSender.sendActivePotionEffects(player);
	}
	public static void appendStatuses(Player player, List<ActiveStatusEntry> statuses) {
		long left = remaining(player);
		if (left > 0) statuses.add(ActiveStatusEntry.item("slayer:wax_earplugs", EARPLUGS_ID, (int) ((left + 999) / 1000)));
	}
}
