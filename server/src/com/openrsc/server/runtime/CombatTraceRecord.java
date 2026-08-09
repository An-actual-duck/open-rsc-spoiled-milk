package com.openrsc.server.runtime;

import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;

/**
 * A redacted, factual record from a completed damage transaction.
 *
 * <p>It intentionally contains no player name, account identity, network
 * information, location, participant snapshot, or raw entity reference. A
 * terminal record means the immediate Hits mutation reached zero; it does not
 * claim that a caller-owned death adapter or respawn sequence has completed.</p>
 */
public final class CombatTraceRecord {
	public enum Reason {
		DAMAGE_SETTLED,
		TARGET_HITS_DEPLETED
	}

	private final Reason reason;
	private final long tick;
	private final DamageRequest.SourceCategory sourceCategory;
	private final CombatStyle style;
	private final String effectKey;
	private final int resolvedDamage;
	private final int actualDamage;
	private final int hitsBefore;
	private final int hitsAfter;
	private final int overkillDamage;

	private CombatTraceRecord(final Reason reason, final DamageResult result) {
		final DamageRequest request = result.getRequest();
		this.reason = reason;
		tick = request.getTick();
		sourceCategory = request.getSourceCategory();
		style = request.getStyle();
		effectKey = CombatTraceRedaction.effectKey(request.getEffectKey());
		resolvedDamage = request.getResolvedDamage();
		actualDamage = result.getActualDamage();
		hitsBefore = result.getHitsBefore();
		hitsAfter = result.getHitsAfter();
		overkillDamage = result.getOverkillDamage();
	}

	static CombatTraceRecord settled(final DamageResult result) {
		return new CombatTraceRecord(Reason.DAMAGE_SETTLED, result);
	}

	static CombatTraceRecord targetHitsDepleted(final DamageResult result) {
		return new CombatTraceRecord(Reason.TARGET_HITS_DEPLETED, result);
	}

	public Reason getReason() { return reason; }
	public long getTick() { return tick; }
	public DamageRequest.SourceCategory getSourceCategory() { return sourceCategory; }
	public CombatStyle getStyle() { return style; }
	public String getEffectKey() { return effectKey; }
	public int getResolvedDamage() { return resolvedDamage; }
	public int getActualDamage() { return actualDamage; }
	public int getHitsBefore() { return hitsBefore; }
	public int getHitsAfter() { return hitsAfter; }
	public int getOverkillDamage() { return overkillDamage; }
}
