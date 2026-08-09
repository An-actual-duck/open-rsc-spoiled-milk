package com.openrsc.server.model.entity.npc;

/**
 * One resolved view of an NPC's existing attack-style authority.
 *
 * <p>The underlying selection remains {@link NpcAttackStyleProfile}; this
 * value does not introduce a second NPC table or change any profile rule. It
 * keeps a single cast's selected element and all of its dependent presentation
 * and proc values together, so callers cannot accidentally mix values from
 * separate selections.</p>
 */
public final class NpcCombatProfile {
	private final Npc npc;
	private final NpcAttackStyleProfile style;

	private NpcCombatProfile(final Npc npc,
			final NpcAttackStyleProfile style) {
		this.npc = npc;
		this.style = style;
	}

	public static NpcCombatProfile resolve(final Npc npc) {
		return new NpcCombatProfile(npc, NpcAttackStyleProfile.forNpc(npc));
	}

	public NpcAttackStyleProfile getStyle() {
		return style;
	}

	public boolean isMeleeOnly() {
		return style == NpcAttackStyleProfile.MELEE;
	}

	public boolean usesRangedProjectiles() {
		return style.usesRangedProjectiles();
	}

	public boolean usesMagicProjectiles() {
		return style.usesMagicProjectiles();
	}

	public int getProjectileRange() {
		return style.getProjectileRange(npc);
	}

	/** Delegates at the original decision point, preserving any RNG draw. */
	public boolean prefersProjectileAtDistance(final int distance) {
		return style.prefersProjectileAtDistance(npc, distance);
	}

	public int getRangedOffense() {
		return style.getRangedOffense(npc);
	}

	public int getMagicOffense() {
		return style.getMagicOffense(npc);
	}

	public double getMagicSpellPower() {
		return style.getMagicSpellPower(npc);
	}

	public int getRangedProjectileVisual() {
		return style.getRangedProjectileVisual(npc);
	}

	/**
	 * Selects the element exactly once for this cast. Call only after the
	 * existing projectile-preference gate; some profiles intentionally consume
	 * combat RNG to choose their element.
	 */
	public NpcMagicAttack selectMagicAttack() {
		return new NpcMagicAttack(npc, style, style.getMagicElement(npc));
	}
}
