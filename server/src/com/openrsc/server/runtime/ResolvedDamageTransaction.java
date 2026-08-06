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
 * <p>This A05.2-A05.4C boundary deliberately owns no formula, mitigation,
 * contribution, lifesteal, effect, packet, XP, or death policy. Callers retain
 * those responsibilities and their existing order around this transaction.</p>
 */
public final class ResolvedDamageTransaction {
	public DamageResult apply(final DamageRequest request) {
		final DamageRequest checkedRequest = Objects.requireNonNull(
			request, "request");
		final Mob target = checkedRequest.getTarget();
		final int hitsBefore = target.getLevel(Skill.HITS.id());

		target.getSkills().subtractLevel(Skill.HITS.id(),
			checkedRequest.getResolvedDamage(), false);
		target.getUpdateFlags().setDamage(new Damage(
			target, checkedRequest.getResolvedDamage()));
		target.getUpdateFlags().addHitSplat(new HitSplat(
			target, checkedRequest.getHitSplatType(),
			checkedRequest.getResolvedDamage()));

		final DamageResult result = DamageResult.appliedCurrentPath(
			checkedRequest, hitsBefore, target.getLevel(Skill.HITS.id()));
		CombatDamageObservation.publish(result);
		return result;
	}
}
