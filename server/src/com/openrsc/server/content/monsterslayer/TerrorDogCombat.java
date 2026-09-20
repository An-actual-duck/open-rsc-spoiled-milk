package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.combat.CombatParticipantSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;
import java.util.function.IntConsumer;

/** One non-recursive additional normal bite per living neighboring Terror dog. */
public final class TerrorDogCombat {
	public static final int NPC_ID = 867;
	public static final int TREATS_ID = 3327;
	public static final long PROTECTION_MILLIS = 600_000L;
	public static final String USE_MESSAGE = "You scatter treats all around you.";
	public static final String EXAMINE = "Something for your dog to chew on for awhile besides you";
	private static final String PROTECTION = "slayer_dog_treats_until";
	private TerrorDogCombat() { }
	public static int uses(int id) { return id >= TREATS_ID && id <= TREATS_ID + 2 ? TREATS_ID + 3 - id : 0; }
	public static boolean isTerrorDog(Mob mob) {
		return mob instanceof Npc && mob.getConfig().WANT_MYWORLD && mob.getID() == NPC_ID;
	}
	private static long remaining(Player player) {
		if (!player.getCache().hasKey(PROTECTION)) return 0;
		return Math.max(0, Math.min(PROTECTION_MILLIS, player.getCache().getLong(PROTECTION)
			- player.getWorld().getServer().getGameClock().currentTimeMillis()));
	}
	public static boolean protectedByTreats(Player player) { return remaining(player) > 0; }
	public static void applyTreats(Player player) {
		player.getCache().store(PROTECTION, player.getWorld().getServer().getGameClock().currentTimeMillis() + PROTECTION_MILLIS);
		player.message(USE_MESSAGE);
		ActionSender.sendActivePotionEffects(player);
	}
	public static void appendStatuses(Player player, List<ActiveStatusEntry> statuses) {
		long left = remaining(player);
		if (left > 0) statuses.add(ActiveStatusEntry.item("slayer:dog_treats", TREATS_ID, (int) ((left + 999) / 1000)));
	}
	public static int nearbyDogs(Mob source) {
		if (!isTerrorDog(source)) return 0;
		int count = 0;
		for (Npc neighbor : source.getViewArea().getNpcsInView()) {
			if (neighbor != source && isTerrorDog(neighbor) && !neighbor.isRemoved()
				&& !neighbor.isRespawning() && neighbor.getLevel(Skill.HITS.id()) > 0
				&& source.sharesSpatialDomain(neighbor) && source.withinRange(neighbor, 2)) count++;
		}
		return count;
	}
	private static boolean eligible(Mob source, Mob target) {
		return isTerrorDog(source) && target instanceof Player && !source.isRemoved() && !target.isRemoved()
			&& source.getLevel(Skill.HITS.id()) > 0 && target.getLevel(Skill.HITS.id()) > 0
			&& !((Player) target).killed && ((Player) target).loggedIn()
			&& source.sharesSpatialDomain(target) && source.withinRange(target, 1)
			&& !protectedByTreats((Player) target);
	}
	/** Called only after a nonlethal, damaging primary hit; followups never call it. */
	public static void applyFrenzy(Mob source, Mob target, int dealt, boolean suppressed, IntConsumer bite) {
		if (dealt <= 0 || suppressed || !eligible(source, target)) return;
		int count = nearbyDogs(source);
		CombatParticipantSnapshot attacker = CombatParticipantSnapshot.capture(source);
		CombatParticipantSnapshot defender = CombatParticipantSnapshot.capture(target);
		for (int i = 0; i < count; i++) {
			if (!attacker.matches(source) || !defender.matches(target) || !eligible(source, target)) break;
			bite.accept(CombatFormula.doMeleeDamage(source, target));
		}
	}
}
