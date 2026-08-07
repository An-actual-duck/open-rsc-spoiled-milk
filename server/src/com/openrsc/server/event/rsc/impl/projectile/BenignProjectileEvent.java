package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileLaunchSnapshot;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.World;

public class BenignProjectileEvent extends SingleTickEvent {

	private final Mob caster;
	private final Mob opponent;
	protected int damage;
	protected int type;
	boolean canceled;
	private final ProjectileLaunchSnapshot launchSnapshot;
	private final ProjectileImpactLedger impactLedger;

	BenignProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type) {
		super(world, caster, 1, "Benign Projectile Event");
		this.caster = caster;
		this.opponent = opponent;
		this.damage = damage;
		this.type = type;
		final long launchTick = world.getServer().getCurrentTick();
		this.launchSnapshot = ProjectileLaunchSnapshot.capture(
			getUUID(), launchTick, launchTick + getDelayTicks(), caster,
			opponent, ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT,
			this instanceof BallProjectileEvent
				? "ball-projectile" : "benign-projectile",
			-1, type, 0, 0, true);
		this.impactLedger = new ProjectileImpactLedger(launchSnapshot);
		if (caster.isPlayer() && opponent.isPlayer()) {
			caster.setAttribute("benignprojectile", this);
			opponent.setAttribute("benignprojectile", this);
		}
		sendProjectile(caster, opponent);
	}

	private void sendProjectile(Mob caster, Mob opponent) {
		Projectile projectile = new Projectile(caster, opponent, type);
		caster.getUpdateFlags().setProjectile(projectile);
	}

	@Override
	public void action() {
		final ProjectileImpactDecision impact = beginBenignImpact(false);
		if (!impact.isAuthorized()) {
			return;
		}
		try {
			if (caster.isPlayer() && opponent.isPlayer()) {
				caster.removeAttribute("benignprojectile");
				opponent.removeAttribute("benignprojectile");
			}
			completeBenignImpact(impact);
		} catch (final RuntimeException failure) {
			failBenignImpact();
			throw failure;
		} catch (final Error failure) {
			failBenignImpact();
			throw failure;
		}
	}

	protected final ProjectileImpactDecision beginBenignImpact(
			final boolean honorCancellation) {
		if (!impactLedger.claimImpact()) {
			return impactLedger.duplicate();
		}
		try {
			if (honorCancellation && canceled) {
				return impactLedger.invalidate(
					ProjectileImpactDecision.Reason.EXPLICIT_CANCELLATION,
					null, null);
			}
			return impactLedger.authorize(
				caster.getWorldLocation(), opponent.getWorldLocation());
		} catch (final RuntimeException failure) {
			impactLedger.fail();
			throw failure;
		} catch (final Error failure) {
			impactLedger.fail();
			throw failure;
		}
	}

	protected final void completeBenignImpact(
			final ProjectileImpactDecision impact) {
		impactLedger.complete(impact);
	}

	protected final void failBenignImpact() {
		impactLedger.fail();
	}

	public final ProjectileLaunchSnapshot getLaunchSnapshot() {
		return launchSnapshot;
	}

	public final ProjectileImpactLedger.State getProjectileImpactState() {
		return impactLedger.getState();
	}

	public final ProjectileImpactDecision getInitialProjectileImpactDecision() {
		return impactLedger.getInitialDecision();
	}

	public final int getProjectileImpactCallbackCount() {
		return impactLedger.getCallbackCount();
	}

	public void setCanceled(boolean b) {
		canceled = b;
	}

}
