package com.openrsc.server.content;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.DataConversions;

public final class PoisonProcChance {

	private static final int FAILURE_RECHARGE_ATTEMPTS = 5;
	private static final int PROC_STEP_PERCENT = 10;

	private static final int WEAPON_START_PERCENT = 100;
	private static final int WEAPON_FIRST_SUCCESS_PERCENT = 50;
	private static final int WEAPON_FLOOR_PERCENT = 20;

	private static final int ARMOR_START_PERCENT = 50;
	private static final int ARMOR_FLOOR_PERCENT = 10;

	private PoisonProcChance() {
	}

	public static boolean rollWeapon(final Player player, final Mob target, final int weaponId) {
		return roll(player, target, "weapon", weaponId, WEAPON_START_PERCENT, WEAPON_FLOOR_PERCENT, WEAPON_FIRST_SUCCESS_PERCENT);
	}

	public static boolean rollArmor(final Player player, final Mob target, final String armorSource) {
		return roll(player, target, "armor_" + armorSource, armorSource.hashCode(), ARMOR_START_PERCENT, ARMOR_FLOOR_PERCENT, 0);
	}

	private static boolean roll(final Player player, final Mob target, final String sourceName, final int sourceId,
								final int startChance, final int floorChance, final int firstSuccessChance) {
		final String attributePrefix = "poison_proc_" + sourceName;
		final String sourceState = sourceId + "@" + describeTarget(target);
		final String previousSourceState = player.getAttribute(attributePrefix + "_source", "");
		int currentChance = player.getAttribute(attributePrefix + "_chance", startChance);
		int failedAttempts = player.getAttribute(attributePrefix + "_failures", 0);
		if (!sourceState.equals(previousSourceState)) {
			currentChance = startChance;
			failedAttempts = 0;
		}

		final boolean proc = DataConversions.getRandom().nextInt(100) < currentChance;
		int nextChance = currentChance;
		int nextFailures = failedAttempts;
		if (proc) {
			nextChance = getChanceAfterSuccess(currentChance, startChance, floorChance, firstSuccessChance);
			nextFailures = 0;
		} else {
			nextFailures++;
			if (nextFailures >= FAILURE_RECHARGE_ATTEMPTS) {
				nextChance = getChanceAfterRecharge(currentChance, startChance, firstSuccessChance);
				nextFailures = 0;
			}
		}

		player.setAttribute(attributePrefix + "_source", sourceState);
		player.setAttribute(attributePrefix + "_chance", nextChance);
		player.setAttribute(attributePrefix + "_failures", nextFailures);
		return proc;
	}

	private static int getChanceAfterSuccess(final int currentChance, final int startChance, final int floorChance, final int firstSuccessChance) {
		if (firstSuccessChance > 0 && currentChance >= startChance) {
			return firstSuccessChance;
		}
		return Math.max(floorChance, currentChance - PROC_STEP_PERCENT);
	}

	private static int getChanceAfterRecharge(final int currentChance, final int startChance, final int firstSuccessChance) {
		if (firstSuccessChance > 0) {
			if (currentChance >= firstSuccessChance) {
				return startChance;
			}
			return Math.min(firstSuccessChance, currentChance + PROC_STEP_PERCENT);
		}
		return Math.min(startChance, currentChance + PROC_STEP_PERCENT);
	}

	private static String describeTarget(final Mob target) {
		if (target.isNpc()) {
			final Npc npc = (Npc) target;
			return "npc:" + npc.getID() + ":" + target.getIndex();
		}
		if (target.isPlayer()) {
			return "player:" + ((Player) target).getUsername();
		}
		return "mob:" + target.getIndex();
	}
}
