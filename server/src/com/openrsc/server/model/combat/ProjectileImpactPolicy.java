package com.openrsc.server.model.combat;

/**
 * Immutable impact-eligibility policy selected by a stable projectile producer.
 *
 * <p>The policy describes validation only. It owns no launch checks, resource
 * costs, experience, damage, effects, packets, or death settlement.</p>
 */
public enum ProjectileImpactPolicy {
	PLAYER_DAMAGE(
		SourceLifetime.ALLOW_LAUNCHED_DAMAGE_AFTER_DEATH,
		true, true, 15, Collision.COMBAT_PROJECTILE, true, false),
	NPC_DAMAGE(
		SourceLifetime.ALLOW_LAUNCHED_DAMAGE_AFTER_DEATH,
		true, true, 15, Collision.COMBAT_PROJECTILE, true, false),
	SUMMON_DAMAGE(
		SourceLifetime.REQUIRE_EXACT_LIVE,
		true, true, 15, Collision.COMBAT_PROJECTILE, true, true),
	ADMIN_DAMAGE(
		SourceLifetime.REQUIRE_EXACT_LIVE,
		true, true, 15, Collision.COMBAT_PROJECTILE, true, false),
	POSITIONAL_COMPATIBILITY_DAMAGE(
		SourceLifetime.ALLOW_LAUNCHED_DAMAGE_AFTER_DEATH,
		true, true, 15, Collision.NONE, true, false),
	SCRIPTED_MAGIC(
		SourceLifetime.REQUIRE_EXACT_LIVE,
		true, true, 15, Collision.COMBAT_PROJECTILE, true, false),
	LEGENDS_HOLY_WATER(
		SourceLifetime.REQUIRE_EXACT_LIVE,
		true, true, 4, Collision.COMBAT_PROJECTILE, true, false),
	GNOME_BALL(
		SourceLifetime.REQUIRE_EXACT_LIVE,
		true, true, -1, Collision.NONE, true, false),
	BENIGN_COMPATIBILITY_CLEANUP(
		SourceLifetime.TERMINAL_CLEANUP,
		false, false, -1, Collision.NONE, false, false);

	public enum SourceLifetime {
		ALLOW_LAUNCHED_DAMAGE_AFTER_DEATH,
		REQUIRE_EXACT_LIVE,
		TERMINAL_CLEANUP
	}

	public enum Collision {
		COMBAT_PROJECTILE,
		NONE
	}

	private final SourceLifetime sourceLifetime;
	private final boolean requireExactLiveTarget;
	private final boolean requireLaunchDomain;
	private final int maximumLaunchOriginRange;
	private final Collision collision;
	private final boolean honorCancellation;
	private final boolean requireExactLiveSourceOwner;

	ProjectileImpactPolicy(final SourceLifetime sourceLifetime,
			final boolean requireExactLiveTarget,
			final boolean requireLaunchDomain,
			final int maximumLaunchOriginRange,
			final Collision collision,
			final boolean honorCancellation,
			final boolean requireExactLiveSourceOwner) {
		this.sourceLifetime = sourceLifetime;
		this.requireExactLiveTarget = requireExactLiveTarget;
		this.requireLaunchDomain = requireLaunchDomain;
		this.maximumLaunchOriginRange = maximumLaunchOriginRange;
		this.collision = collision;
		this.honorCancellation = honorCancellation;
		this.requireExactLiveSourceOwner = requireExactLiveSourceOwner;
	}

	public SourceLifetime getSourceLifetime() {
		return sourceLifetime;
	}

	public boolean requiresExactLiveTarget() {
		return requireExactLiveTarget;
	}

	public boolean requiresLaunchDomain() {
		return requireLaunchDomain;
	}

	/** Returns the inclusive tile ceiling, or {@code -1} for no ceiling. */
	public int getMaximumLaunchOriginRange() {
		return maximumLaunchOriginRange;
	}

	public Collision getCollision() {
		return collision;
	}

	public boolean honorsCancellation() {
		return honorCancellation;
	}

	public boolean requiresExactLiveSourceOwner() {
		return requireExactLiveSourceOwner;
	}
}
