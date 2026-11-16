package com.openrsc.server.model.entity.npc;

/** Immutable element selection plus the existing dependent NPC magic payload. */
public final class NpcMagicAttack {
	private final Npc npc;
	private final NpcAttackStyleProfile style;
	private final NpcMagicElement element;

	NpcMagicAttack(final Npc npc, final NpcAttackStyleProfile style,
			final NpcMagicElement element) {
		this.npc = npc;
		this.style = style;
		this.element = element;
	}

	public com.openrsc.server.model.entity.player.PrayerCatalog.GodLine getGodLine() {
        com.openrsc.server.content.npccombat.NpcCombatDefinition d = com.openrsc.server.content.npccombat.CanonicalNpcCombat.definition(npc);
        return d != null && d.genericDispatch ? d.magicGod : null;
    }

	public NpcMagicElement getElement() {
		return element;
	}

	public int getProjectileVisual() {
		return style.getMagicProjectileVisual(npc, element);
	}

	public int getImpactEffect() {
		return style.getMagicImpactEffect(npc, element);
	}

	public int getStartleProcChancePercent() {
		return style.getMagicStartleProcChancePercent(npc, element);
	}

	public int getAcidPoisonPower() {
		return style.getMagicAcidPoisonPower(npc, element);
	}

	public int getFireDefenseDebuffPercent() {
		return style.getMagicFireDefenseDebuffPercent(npc, element);
	}

	public int getSplinterProcChancePercent() {
		return style.getMagicSplinterProcChancePercent(npc, element);
	}
}
