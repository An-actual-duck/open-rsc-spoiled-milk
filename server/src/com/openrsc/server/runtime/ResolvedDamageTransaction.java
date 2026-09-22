package com.openrsc.server.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.HitSplat;

import java.util.Objects;

/**
 * Applies one already-resolved legacy damage request to the current Hits and
 * presentation fields.
 *
 * <p>The legacy boundary owns no formula, mitigation, contribution, XP or
 * death policy. Callers retain their existing effects and packet ordering.
 * New opt-in Slayer leather effects receive one explicit post-settlement
 * callback (not an observation callback). Otherwise callers retain
 * those responsibilities and their existing order around this transaction.
 * The request also selects whether settlement emits both the damage update and
 * hitsplat or only the damage update; the latter preserves sparse legacy
 * presentation such as Salarin's delayed strike.</p>
 */
public final class ResolvedDamageTransaction {
	public DamageResult apply(final DamageRequest request) {
		final DamageRequest checkedRequest = Objects.requireNonNull(
			request, "request");
		final Mob target = checkedRequest.getTarget();
		final int hitsBefore = target.getLevel(Skill.HITS.id());

		target.getSkills().subtractLevel(Skill.HITS.id(),
			checkedRequest.getResolvedDamage(), false,
			checkedRequest.shouldApplyGoblinTenacity());
		target.getUpdateFlags().setDamage(new Damage(
			target, checkedRequest.getResolvedDamage()));
		if (checkedRequest.getPresentation()
				== DamageRequest.Presentation.DAMAGE_AND_HITSPLAT) {
			target.getUpdateFlags().addHitSplat(new HitSplat(
				target, checkedRequest.getHitSplatType(),
				checkedRequest.getResolvedDamage()));
		}

		final DamageResult result = DamageResult.appliedCurrentPath(
			checkedRequest, hitsBefore, target.getLevel(Skill.HITS.id()));
		CombatDamageObservation.publish(result);
		// Explicit opt-in gameplay hook, separate from the non-authoritative observer.
		// Existing caller-local effects/credit/death order remain owned by their callers.
		com.openrsc.server.content.monsterslayer.SlayerLeatherEffects.afterDamage(result);
		return result;
	}
}
