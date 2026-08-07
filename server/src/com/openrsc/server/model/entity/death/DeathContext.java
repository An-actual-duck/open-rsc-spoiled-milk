package com.openrsc.server.model.entity.death;

import com.openrsc.server.model.entity.EntityType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.util.Objects;
import java.util.UUID;

/** Immutable identity captured when one mob acquires death ownership. */
public final class DeathContext {
	private final long lifecycleId;
	private final UUID targetId;
	private final EntityType targetType;
	private final long targetCombatLifecycle;
	private final Mob killer;
	private final UUID killerId;
	private final WorldLocation location;
	private final long startTick;

	DeathContext(final long lifecycleId, final Mob target,
			final Mob killer) {
		if (lifecycleId <= 0L) {
			throw new IllegalArgumentException(
				"death lifecycle ID must be positive");
		}
		final Mob checkedTarget = Objects.requireNonNull(target, "target");
		this.lifecycleId = lifecycleId;
		targetId = checkedTarget.getUUID();
		targetType = checkedTarget.getEntityType();
		targetCombatLifecycle = checkedTarget.getCombatLifecycle();
		this.killer = killer;
		killerId = killer == null ? null : killer.getUUID();
		location = checkedTarget.getWorldLocation();
		startTick = checkedTarget.getWorld().getServer().getCurrentTick();
	}

	public long getLifecycleId() { return lifecycleId; }
	public UUID getTargetId() { return targetId; }
	public EntityType getTargetType() { return targetType; }
	public long getTargetCombatLifecycle() { return targetCombatLifecycle; }
	public Mob getKiller() { return killer; }
	public UUID getKillerId() { return killerId; }
	public WorldLocation getLocation() { return location; }
	public long getStartTick() { return startTick; }

	public boolean matchesTarget(final Mob target) {
		return target != null && targetId.equals(target.getUUID())
			&& targetType == target.getEntityType();
	}
}
