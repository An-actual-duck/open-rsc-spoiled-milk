package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileLaunchSnapshot;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;

public abstract class CustomProjectileEvent extends ProjectileEvent {

	protected CustomProjectileEvent(World world, Mob caster, Mob opponent, int type) {
		this(world, caster, opponent, type, true);
	}

	protected CustomProjectileEvent(World world, Mob caster, Mob opponent, int type, boolean setChasing) {
		this(world, caster, opponent, ProjectileLaunchSpecification.builder(
			ProjectileLaunchSpecification.Producer.MAGIC_SCRIPTED_EFFECT,
			0, type)
			.chase(setChasing)
			.build());
	}

	protected CustomProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification) {
		super(world, caster, opponent,
			requireScriptedSpecification(launchSpecification));
	}

	private static ProjectileLaunchSpecification requireScriptedSpecification(
			final ProjectileLaunchSpecification launchSpecification) {
		if (launchSpecification == null
				|| launchSpecification.getKind()
					!= ProjectileLaunchSnapshot.Kind.SCRIPTED_EFFECT) {
			throw new IllegalArgumentException(
				"CustomProjectileEvent requires a scripted-effect specification");
		}
		return launchSpecification;
	}

	@Override
	public void action() {
		final ProjectileImpactDecision impact = beginProjectileImpact();
		if (!impact.isAuthorized()) {
			return;
		}
		try {
			doSpell();
			if (opponent.isNpc() && caster.isPlayer()) {
				Npc npc = (Npc) opponent;
				Player player = (Player) caster;
				if (!npc.isChasing() && !npc.inCombat() && npc.getCombatState() != CombatState.RUNNING && this.shouldChase) {
					npc.setChasing(player);
				}
			}
			completeProjectileImpact(impact);
		} catch (final RuntimeException failure) {
			failProjectileImpact();
			throw failure;
		} catch (final Error failure) {
			failProjectileImpact();
			throw failure;
		}
	}

	public abstract void doSpell();
}
