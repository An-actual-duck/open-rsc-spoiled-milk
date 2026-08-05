package com.openrsc.server.model.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Side-effect-free shared combat permission and participant validation. */
public final class CombatEligibility {
	private CombatEligibility() { }

	public static CombatEligibilityDecision evaluate(
			final CombatEligibilityRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request cannot be null");
		}
		final Mob source = request.getSource();
		final Mob target = request.getTarget();
		if (source == null) return denied(request, CombatEligibilityReason.SOURCE_MISSING);
		if (target == null) return denied(request, CombatEligibilityReason.TARGET_MISSING);
		if (source == target) return denied(request, CombatEligibilityReason.SELF_TARGET);
		if (source.getWorld() != target.getWorld()) {
			return denied(request, CombatEligibilityReason.DIFFERENT_WORLD);
		}

		if (request.getSourceSnapshot() != null
			&& !request.getSourceSnapshot().matches(source)) {
			return denied(request, CombatEligibilityReason.SOURCE_LIFECYCLE_CHANGED);
		}
		if (request.getTargetSnapshot() != null
			&& !request.getTargetSnapshot().matches(target)) {
			return denied(request, CombatEligibilityReason.TARGET_LIFECYCLE_CHANGED);
		}
		if (request.requiresCurrentParticipants()) {
			CombatEligibilityDecision liveness = validateLiveness(request, source, true);
			if (liveness != null) return liveness;
			liveness = validateLiveness(request, target, false);
			if (liveness != null) return liveness;
		}
		if (request.requiresRegistration()) {
			if (!isRegistered(source)) {
				return denied(request, CombatEligibilityReason.SOURCE_UNREGISTERED);
			}
			if (!isRegistered(target)) {
				return denied(request, CombatEligibilityReason.TARGET_UNREGISTERED);
			}
		}
		if (request.requiresSameSpatialDomain()
			&& !source.sharesSpatialDomain(target)) {
			return denied(request, CombatEligibilityReason.DIFFERENT_SPATIAL_DOMAIN);
		}
		if (request.enforcesSummonRules()) {
			if (Summoning.isSummon(target)) {
				return denied(request, CombatEligibilityReason.TARGET_IS_SUMMON);
			}
			if (Summoning.isSummon(source) && target.isPlayer()) {
				return denied(request, CombatEligibilityReason.SUMMON_CANNOT_ATTACK_PLAYER);
			}
		}
		if (request.enforcesPlayerAttackRules() && source.isPlayer()) {
			return validatePlayerRules(request, (Player) source, target);
		}
		return allowed(request);
	}

	private static CombatEligibilityDecision validateLiveness(
			final CombatEligibilityRequest request, final Mob participant,
			final boolean source) {
		if (participant.isNpc() && ((Npc) participant).isRespawning()) {
			return denied(request, source
				? CombatEligibilityReason.SOURCE_RESPAWNING
				: CombatEligibilityReason.TARGET_RESPAWNING);
		}
		if (participant.isRemoved()) {
			return denied(request, source ? CombatEligibilityReason.SOURCE_REMOVED
				: CombatEligibilityReason.TARGET_REMOVED);
		}
		if (participant.getSkills().getLevel(Skill.HITS.id()) <= 0
			|| participant.killed) {
			return denied(request, source ? CombatEligibilityReason.SOURCE_DEAD
				: CombatEligibilityReason.TARGET_DEAD);
		}
		if (participant.isPlayer() && !((Player) participant).loggedIn()) {
			return denied(request, source ? CombatEligibilityReason.SOURCE_LOGGED_OUT
				: CombatEligibilityReason.TARGET_LOGGED_OUT);
		}
		if (participant.isUnregistering()) {
			return denied(request, source
				? CombatEligibilityReason.SOURCE_UNREGISTERING
				: CombatEligibilityReason.TARGET_UNREGISTERING);
		}
		return null;
	}

	private static boolean isRegistered(final Mob participant) {
		return participant.isPlayer()
			? participant.getWorld().getPlayerByUUID(participant.getUUID()) == participant
			: participant.isNpc()
				&& participant.getWorld().getNpcByUUID(participant.getUUID()) == participant;
	}

	private static CombatEligibilityDecision validatePlayerRules(
			final CombatEligibilityRequest request, final Player source,
			final Mob target) {
		if (target.isNpc()) {
			final Npc npc = (Npc) target;
			return !request.allowsUnattackableTarget()
				&& !npc.getDef().isAttackable()
				? denied(request, CombatEligibilityReason.TARGET_NOT_ATTACKABLE)
				: allowed(request);
		}
		if (!target.isPlayer()) return allowed(request);
		final Player victim = (Player) target;
		if (!source.getConfig().WANT_PVP) {
			return denied(request, CombatEligibilityReason.PVP_DISABLED);
		}
		if (source.getParty() != null && victim.getParty() != null
			&& source.getParty() == victim.getParty()) {
			return denied(request, CombatEligibilityReason.PARTY_MEMBER);
		}
		if (source.inCombat() && source.getDuel().isDuelActive()
			&& victim.inCombat() && victim.getDuel().isDuelActive()
			&& victim.equals(source.getOpponent())) {
			return allowed(request);
		}
		if (request.getStyle() == CombatStyle.MELEE
			&& !victim.canBeReattacked()) {
			return denied(request, CombatEligibilityReason.TARGET_REATTACK_PROTECTED);
		}
		if (source.getConfig().USES_PK_MODE) {
			if (source.getPkMode() == 0 || victim.getPkMode() == 0) {
				return denied(request, CombatEligibilityReason.PK_MODE_DISABLED);
			}
			if (source.getLocation().isInLumbridgeStartingChunk()
				|| victim.getLocation().isInLumbridgeStartingChunk()) {
				return denied(request, CombatEligibilityReason.PK_LUMBRIDGE_RESTRICTED);
			}
			if (source.checkVisNpc(source, NpcId.BANKER.id(), 5) != null) {
				return denied(request, CombatEligibilityReason.PK_BANKER_RESTRICTED);
			}
			if (Math.abs(source.getCombatLevel() - victim.getCombatLevel()) > 5
				|| Math.abs(source.getSkills().getMaxStat(Skill.ATTACK.id())
					- victim.getSkills().getMaxStat(Skill.ATTACK.id())) > 10
				|| Math.abs(source.getSkills().getMaxStat(Skill.DEFENSE.id())
					- victim.getSkills().getMaxStat(Skill.DEFENSE.id())) > 10
				|| Math.abs(source.getSkills().getMaxStat(Skill.STRENGTH.id())
					- victim.getSkills().getMaxStat(Skill.STRENGTH.id())) > 10) {
				return denied(request, CombatEligibilityReason.PK_LEVEL_MISMATCH);
			}
		} else {
			final int sourceWilderness = source.getLocation().wildernessLevel();
			final int targetWilderness = victim.getLocation().wildernessLevel();
			if (sourceWilderness < 1) {
				return denied(request, CombatEligibilityReason.SOURCE_OUTSIDE_WILDERNESS);
			}
			if (targetWilderness < 1) {
				return denied(request, CombatEligibilityReason.TARGET_OUTSIDE_WILDERNESS);
			}
			final int difference = Math.abs(
				source.getCombatLevel() - victim.getCombatLevel());
			if (difference > sourceWilderness) {
				return denied(request,
					CombatEligibilityReason.SOURCE_WILDERNESS_LEVEL_MISMATCH,
					sourceWilderness);
			}
			if (difference > targetWilderness) {
				return denied(request,
					CombatEligibilityReason.TARGET_WILDERNESS_LEVEL_MISMATCH,
					targetWilderness);
			}
		}
		if (victim.isInvulnerableTo(source) || victim.isInvisibleTo(source)) {
			return denied(request, CombatEligibilityReason.TARGET_INVULNERABLE);
		}
		return allowed(request);
	}

	private static CombatEligibilityDecision allowed(
			final CombatEligibilityRequest request) {
		return new CombatEligibilityDecision(
			request, CombatEligibilityReason.ALLOWED, 0);
	}

	private static CombatEligibilityDecision denied(
			final CombatEligibilityRequest request,
			final CombatEligibilityReason reason) {
		return denied(request, reason, 0);
	}

	private static CombatEligibilityDecision denied(
			final CombatEligibilityRequest request,
			final CombatEligibilityReason reason, final int detailValue) {
		return new CombatEligibilityDecision(request, reason, detailValue);
	}
}
