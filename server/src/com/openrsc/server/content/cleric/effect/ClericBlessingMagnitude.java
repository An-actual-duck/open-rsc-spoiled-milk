package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.content.cleric.ClericAlignment;

/** One immutable, cast-time god blessing snapshot. Components share its rank and lifetime. */
public final class ClericBlessingMagnitude implements ClericEffectMagnitude {
    private final ClericAlignment god;
    private final int tier, power;
    public ClericBlessingMagnitude(ClericAlignment god, int tier, int power) {
        if (god == null || god == ClericAlignment.NEUTRAL || tier < 1 || tier > 3
                || power < 1 || power > tier * 2) throw new IllegalArgumentException("Invalid blessing snapshot");
        this.god = god; this.tier = tier; this.power = power;
    }
    public ClericAlignment getGod() { return god; }
    public int getTier() { return tier; }
    public int getPower() { return power; }
}
