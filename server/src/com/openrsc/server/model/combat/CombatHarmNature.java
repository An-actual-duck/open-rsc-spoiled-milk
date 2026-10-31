package com.openrsc.server.model.combat;

/**
 * Damage nature is independent of attribution and of the animation/style that initiated an effect.
 */
public enum CombatHarmNature {
  MAGICAL,
  NONMAGICAL,
  NON_COMBAT;

  public static CombatHarmNature classify(
      DamageRequest.SourceCategory category, CombatStyle style) {
    if (category == DamageRequest.SourceCategory.ENVIRONMENT
        || category == DamageRequest.SourceCategory.SCRIPT) return NON_COMBAT;
    return style == CombatStyle.MAGIC ? MAGICAL : NONMAGICAL;
  }
}
