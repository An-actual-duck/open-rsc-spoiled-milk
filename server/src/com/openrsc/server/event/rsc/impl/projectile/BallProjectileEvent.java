package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.world.World;

public abstract class BallProjectileEvent extends BenignProjectileEvent {

	protected BallProjectileEvent(World world, Mob caster, Mob opponent, int type) {
		this(world, caster, opponent, ProjectileLaunchSpecification.builder(
			ProjectileLaunchSpecification.Producer.GNOME_BALL, 0, -1)
			.presentation(type, 0, true)
			.build());
	}

	protected BallProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification) {
		super(world, caster, opponent, launchSpecification);
	}

	protected BallProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification,
			final ProjectileResourceLedger resourceLedger) {
		super(world, caster, opponent, launchSpecification, resourceLedger);
	}

	@Override
	public void action() {
		final ProjectileImpactDecision impact = beginBenignImpact();
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
