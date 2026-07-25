package com.openrsc.server.model.action;

import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldLocation;

public abstract class WalkToMobAction extends WalkToAction {

	protected final Mob mob;
	private final int radius;
	private final boolean ignoreProjectileAllowed;
	private final ActionType actionType;

	public WalkToMobAction(final Player owner, final Mob mob, final int radius) {
		this(owner, mob, radius, true, ActionType.OTHER);
	}

	public WalkToMobAction(final Player owner, final Mob mob, final int radius, final boolean ignoreProjectileAllowed, final ActionType actionType) {
		super(owner, mob.getLocation());
		this.mob = mob;
		this.radius = radius;
		this.ignoreProjectileAllowed = ignoreProjectileAllowed;
		this.actionType = actionType;
	}

	public Mob getMob() {
		return mob;
	}

	public ActionType getActionType() {
		return actionType;
	}

	@Override
	public boolean shouldExecuteInternal() {
		boolean myworldCombatAttack = actionType == ActionType.ATTACK && getPlayer().getConfig().WANT_MYWORLD;
		boolean myworldMeleeAttack = myworldCombatAttack && ignoreProjectileAllowed;
		boolean projectilePathAttack = actionType == ActionType.ATTACKMAGIC;
		Point checkedPoint = ((ignoreProjectileAllowed || projectilePathAttack) && !myworldCombatAttack)
			? getPlayer().getWalkingQueue().getNextMovement()
			: getPlayer().getLocation();
		boolean sameTileCombatAttack = myworldCombatAttack
			&& checkedPoint.getX() == mob.getX()
			&& checkedPoint.getY() == mob.getY();
		boolean pathingCheckPassed = !sameTileCombatAttack
			&& checkAdjacentDistance(checkedPoint);
		boolean actionExecutedThisTick = checkedPoint.withinRange(mob.getLocation(), radius) && pathingCheckPassed;
		if (actionType == ActionType.ATTACKMAGIC
			&& !getPlayer().getConfig().WANT_MYWORLD
			&& getPlayer().inCombat()
			&& !actionExecutedThisTick) {
			getPlayer().setWalkToAction(null);
		}
		if (myworldMeleeAttack && !actionExecutedThisTick) {
			repathMyWorldMeleeAttackIfNeeded();
		}
		return actionExecutedThisTick;
	}

	private boolean checkAdjacentDistance(final Point checkedPoint) {
		if (!getPlayer().getConfig()
				.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			return PathValidation.checkAdjacentDistance(
				getPlayer().getWorld(),
				checkedPoint.getX(),
				checkedPoint.getY(),
				mob.getX(),
				mob.getY(),
				ignoreProjectileAllowed,
				!ignoreProjectileAllowed);
		}
		try {
			WorldLocation checkedLocation =
				LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
					checkedPoint,
					getPlayer().getWorldLocation(),
					getPlayer().getConfig()
						.WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE,
					false);
			return PathValidation.checkAdjacentDistance(
				getPlayer().getWorld(),
				checkedLocation,
				mob.getWorldLocation(),
				ignoreProjectileAllowed,
				!ignoreProjectileAllowed);
		} catch (IllegalArgumentException outsideScope) {
			return false;
		}
	}

	private void repathMyWorldMeleeAttackIfNeeded() {
		if (mob.isRemoved()) {
			return;
		}
		final Path path = getPlayer().getWalkingQueue().path;
		final Point pathEnd = path == null || path.isEmpty() ? null : path.getWaypoints().peekLast();
		if (pathEnd != null
			&& pathEnd.withinRange(mob.getLocation(), radius)
			&& PathValidation.checkAdjacentDistance(getPlayer().getWorld(),
			pathEnd.getX(), pathEnd.getY(), mob.getX(), mob.getY(),
			ignoreProjectileAllowed, !ignoreProjectileAllowed)) {
			return;
		}
		getPlayer().walkAdjacentToEntity(mob);
	}

	@Override
	public boolean isPvPAttack() {
		return mob.isPlayer() && (actionType == ActionType.ATTACK || actionType == ActionType.ATTACKMAGIC);
	}
}
