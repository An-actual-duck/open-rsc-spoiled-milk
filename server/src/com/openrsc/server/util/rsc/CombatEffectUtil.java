package com.openrsc.server.util.rsc;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.combat.CombatEligibility;
import com.openrsc.server.model.combat.CombatEligibilityPhase;
import com.openrsc.server.model.combat.CombatEligibilityRequest;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.PlayerOwnedNpcRadiusSelection;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CombatEffectUtil {
	public static final int HELLS_INFERNO_MAX_HIT = 18;
	public static final int HELLS_INFERNO_SPLASH_RADIUS = 2;

	private CombatEffectUtil() {
	}

	public static int remapLegacyPlayerMeleeStat(Mob mob, int skillId) {
		if (mob != null && mob.isPlayer() && isLegacyMeleeStat(skillId)) {
			return Skill.MELEE.id();
		}
		return skillId;
	}

	public static int[] remapLegacyPlayerMeleeStats(Mob mob, int... skillIds) {
		return Arrays.stream(skillIds)
			.map(skillId -> remapLegacyPlayerMeleeStat(mob, skillId))
			.distinct()
			.toArray();
	}

	public static void sendInfernalProcDebug(Player player, String source, Mob target, int damage, int pieces,
											 int maxHit, double roll, double chance, boolean procced,
											 int procDamageRoll, int procDamageDealt) {
	}

	public static int hellsInfernoSplashDamage(final int primaryDamageDealt) {
		return primaryDamageDealt <= 0 ? 0 : (primaryDamageDealt / 2) + (primaryDamageDealt % 2);
	}

	public static List<Npc> findPlayerOwnedNpcSplashTargets(final Player player,
			final Npc primaryTarget, final int radius) {
		if (player == null || primaryTarget == null || radius < 0
				|| Summoning.isPlayerAreaEffectSuppressed(player)) {
			return Collections.emptyList();
		}
		return PlayerOwnedNpcRadiusSelection
			.aroundPrimary(player, primaryTarget, radius)
			.snapshotViewOrder();
	}

	public static List<Player> findPlayerOwnedPvpSplashTargets(final Player player,
			final Player primaryTarget, final int radius) {
		if (player == null || primaryTarget == null || radius < 0
			|| player.getDuel().isDuelActive()
			|| Summoning.isPlayerAreaEffectSuppressed(player)) {
			return Collections.emptyList();
		}
		final List<Player> targets = new ArrayList<>();
		for (Player candidate : player.getViewArea().getPlayersInView()) {
			if (candidate == null || candidate == player || candidate == primaryTarget
				|| !candidate.withinRange(primaryTarget.getLocation(), radius)) {
				continue;
			}
			if (CombatEligibility.evaluate(CombatEligibilityRequest.builder(
				player, candidate, CombatEligibilityPhase.COMPATIBILITY, CombatStyle.MAGIC)
				.currentParticipants(true)
				.registration(true)
				.sameSpatialDomain(true)
				.summonRules(true)
				.playerAttackRules(true)
				.build()).isAllowed()) {
				targets.add(candidate);
			}
		}
		return targets;
	}

	private static boolean isLegacyMeleeStat(int skillId) {
		return skillId == Skill.ATTACK.id()
			|| skillId == Skill.DEFENSE.id()
			|| skillId == Skill.STRENGTH.id();
	}
}
