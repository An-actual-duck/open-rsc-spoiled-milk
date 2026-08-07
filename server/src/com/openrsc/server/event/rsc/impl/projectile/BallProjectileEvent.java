package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.world.World;

public abstract class BallProjectileEvent extends BenignProjectileEvent {

	protected BallProjectileEvent(World world, Mob caster, Mob opponent, int type) {
		super(world, caster, opponent, 0, type);
	}

	@Override
	public void action() {
		final ProjectileImpactDecision impact = beginBenignImpact(true);
		if (!impact.isAuthorized()) {
			return;
		}
		try {
			doSpell();
			completeBenignImpact(impact);
		} catch (final RuntimeException failure) {
			failBenignImpact();
			throw failure;
		} catch (final Error failure) {
			failBenignImpact();
			throw failure;
		}
	}

	public abstract void doSpell();
}
