package com.openrsc.server.model.entity.npc;

/**
 * One view of an NPC's attack capabilities and a single cast's dependent payload.
 *
 * <p>Canonical MyWorld records supply capabilities and raw values. Their eligible
 * weighted attack selection and shared cadence belong to CanonicalNpcCombat;
 * enum names such as MELEE_MAGIC do not imply a primary style or modern weight.
 * Protected encounters and compatibility profiles retain their existing rules.</p>
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

	/** Legacy/special preference only; ordinary canonical dispatch uses CanonicalNpcCombat. */
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
	 * attack commitment; some profiles intentionally consume
	 * combat RNG to choose their element.
	 */
	public NpcMagicAttack selectMagicAttack() {
		return new NpcMagicAttack(npc, style, style.getMagicElement(npc));
	}
}
