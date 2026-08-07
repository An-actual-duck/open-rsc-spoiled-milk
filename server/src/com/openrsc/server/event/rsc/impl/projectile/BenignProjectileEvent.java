package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileImpactValidator;
import com.openrsc.server.model.combat.ProjectileLaunchSnapshot;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.entity.Mob;
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
	private final ProjectileResourceLedger resourceLedger;

	BenignProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type) {
		this(world, caster, opponent, ProjectileLaunchSpecification.builder(
			ProjectileLaunchSpecification.Producer.BENIGN_COMPATIBILITY,
			damage, -1)
			.presentation(type, 0, true)
			.build());
	}

	BenignProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification) {
		this(world, caster, opponent, launchSpecification,
			ProjectileResourceLedger.defaultFor(
				requireBenignSpecification(launchSpecification).getProducer()));
	}

	BenignProjectileEvent(final World world, final Mob caster,
			final Mob opponent,
			final ProjectileLaunchSpecification launchSpecification,
			final ProjectileResourceLedger resourceLedger) {
		super(world, caster, 1, "Benign Projectile Event",
			requireBenignSpecification(launchSpecification)
				.getDuplicationStrategy());
		if (resourceLedger == null) {
			throw new IllegalArgumentException("resourceLedger cannot be null");
		}
		this.caster = caster;
		this.opponent = opponent;
		this.damage = launchSpecification.getProposedDamage();
		this.type = launchSpecification.getProjectileType();
		final long launchTick = world.getServer().getCurrentTick();
		this.launchSnapshot = ProjectileLaunchSnapshot.capture(
			getUUID(), launchTick, launchTick + getDelayTicks(), caster,
			opponent, launchSpecification);
		this.impactLedger = new ProjectileImpactLedger(launchSnapshot);
		this.resourceLedger = resourceLedger;
		this.resourceLedger.bindEvent(getUUID(),
			launchSpecification.getProducer());
		if (caster.isPlayer() && opponent.isPlayer()) {
			caster.setAttribute("benignprojectile", this);
			opponent.setAttribute("benignprojectile", this);
		}
		sendProjectile(caster, opponent);
	}

	private static ProjectileLaunchSpecification requireBenignSpecification(
			final ProjectileLaunchSpecification launchSpecification) {
		if (launchSpecification == null
				|| launchSpecification.getKind()
					!= ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT) {
			throw new IllegalArgumentException(
				"BenignProjectileEvent requires a benign-effect specification");
		}
		return launchSpecification;
	}

	private void sendProjectile(Mob caster, Mob opponent) {
		Projectile projectile = new Projectile(caster, opponent, type);
		caster.getUpdateFlags().setProjectile(projectile);
	}

	@Override
	public void action() {
		final ProjectileImpactDecision impact = beginBenignImpact();
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

	protected final ProjectileImpactDecision beginBenignImpact() {
		if (!impactLedger.claimImpact()) {
			return impactLedger.duplicate();
		}
		try {
			if (canceled && launchSnapshot.getSpecification()
					.getImpactPolicy().honorsCancellation()) {
				return impactLedger.invalidate(
					ProjectileImpactDecision.Reason.EXPLICIT_CANCELLATION,
					null, null);
			}
			final ProjectileImpactDecision.Reason validation =
				ProjectileImpactValidator.validate(
					launchSnapshot, caster, opponent);
			if (validation
					!= ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED) {
				return impactLedger.invalidate(
					validation, caster.getWorldLocation(),
					opponent.getWorldLocation());
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

	public final ProjectileResourceLedger getProjectileResourceLedger() {
		return resourceLedger;
	}

	public void setCanceled(boolean b) {
		canceled = b;
	}

}
