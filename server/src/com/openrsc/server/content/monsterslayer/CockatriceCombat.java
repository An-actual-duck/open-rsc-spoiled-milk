package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** Melee Slayer encounter with independently expiring leg and arm locks. */
public final class CockatriceCombat {
	public static final int NPC_ID = 864;
	public static final int EYE_DROPS_ID = 3321;
	public static final int EYE_DROPS_TWO_ID = 3322;
	public static final int EYE_DROPS_ONE_ID = 3323;
	public static final long GLARE_MILLIS = 10_000L;
	public static final long PROTECTION_MILLIS = 600_000L;
	public static final String RELEASE_MESSAGE = "Your legs break free but you can't move your arms";
	private static final String GLARE = "slayer_stony_glare_until";
	private static final String DROPS = "slayer_eye_drops_until";
	private static final String LEGS = "slayer_stony_glare_legs";
	private CockatriceCombat() { }

	public static int doses(int id) {
		return id >= EYE_DROPS_ID && id <= EYE_DROPS_ONE_ID ? 3324 - id : 0;
	}
	public static boolean isCockatrice(Mob mob) {
		return mob != null && mob.isNpc() && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	private static long now(Player player) {
		return player.getWorld().getServer().getGameClock().currentTimeMillis();
	}
	private static long remaining(Player player, String key, long maximum) {
		if (!player.getCache().hasKey(key)) return 0;
		return Math.max(0, Math.min(maximum, player.getCache().getLong(key) - now(player)));
	}
	public static boolean protectedByEyeDrops(Player player) {
		return remaining(player, DROPS, PROTECTION_MILLIS) > 0;
	}
	public static boolean attacksBlocked(Mob mob) {
		return mob != null && mob.isPlayer() && mob.getConfig().WANT_MYWORLD
			&& remaining((Player) mob, GLARE, GLARE_MILLIS) > 0
			&& !protectedByEyeDrops((Player) mob);
	}
	public static boolean movementBlocked(Mob mob) {
		return attacksBlocked(mob) && mob.getWorld().getServer().getCurrentTick() < mob.getAttribute(LEGS, -1L);
	}
	/** Every admitted, unsuppressed melee swing glares, independently of damage. */
	public static void onMeleeSwing(Mob source, Mob target, boolean suppressed) {
		if (!isCockatrice(source) || suppressed || target == null || !target.isPlayer()
			|| source.killed || source.getSkills().getLevel(Skill.HITS.id()) <= 0) return;
		final Player player = (Player) target;
		if (player.killed || player.getSkills().getLevel(Skill.HITS.id()) <= 0 || protectedByEyeDrops(player)) return;
		player.getCache().store(GLARE, now(player) + GLARE_MILLIS);
		// Simultaneous glares refresh the arm lock but cannot lengthen the one-tick freeze.
		if (player.getAttribute(LEGS, -1L) < 0) {
			player.setAttribute(LEGS, player.getWorld().getServer().getCurrentTick() + 1);
			player.getWorld().getServer().getGameEventHandler().add(new GameTickEvent(
				player.getWorld(), player, 1, "Stony Glare leg release", DuplicationStrategy.ONE_PER_MOB) {
				@Override public void run() {
					// NPC events run before player events: do not release in the application tick.
					if (player.getWorld().getServer().getCurrentTick() < player.getAttribute(LEGS, -1L)) return;
					boolean wasFrozen = player.getAttribute(LEGS, -1L) >= 0;
					player.removeAttribute(LEGS);
					if (wasFrozen && !player.killed && player.getSkills().getLevel(Skill.HITS.id()) > 0
						&& attacksBlocked(player)) player.message(RELEASE_MESSAGE);
					stop();
				}
			});
		}
		player.message("The cockatrice's Stony Glare stiffens your limbs!");
		ActionSender.sendActivePotionEffects(player);
	}
	public static void applyEyeDrops(Player player) {
		player.getCache().store(DROPS, now(player) + PROTECTION_MILLIS);
		player.getCache().remove(GLARE);
		player.removeAttribute(LEGS);
		player.message("Eye Drops protect you from Stony Glare for 10 minutes.");
		ActionSender.sendActivePotionEffects(player);
	}
	public static void appendStatuses(Player player, List<ActiveStatusEntry> statuses) {
		long protection = remaining(player, DROPS, PROTECTION_MILLIS);
		long glare = remaining(player, GLARE, GLARE_MILLIS);
		if (protection > 0) statuses.add(ActiveStatusEntry.item("slayer:eye_drops", EYE_DROPS_ID,
			(int) ((protection + 999) / 1000)));
		else if (glare > 0) statuses.add(ActiveStatusEntry.slayer("slayer:stony_glare", 2, EYE_DROPS_ID,
			(int) ((glare + 999) / 1000)));
	}
}
