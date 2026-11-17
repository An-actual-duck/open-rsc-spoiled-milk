package com.openrsc.server.content.npccombat;

import com.openrsc.server.model.entity.player.Player;

/** Defense-bypassing breath; the encounter still owns its shield arithmetic and roll.
 * These adapters deliberately retain each legacy settlement path, including combat luck.
 * True damage describes bypassing ordinary style defense, not immunity to equipment rules.
 */
public final class NpcTrueDamage {
    private NpcTrueDamage() {}
    public enum Kind { TRUE_DAMAGE }
    public static final Kind DRAGON_BREATH = Kind.TRUE_DAMAGE;
    public static void dragonBreath(Player player, int damage, boolean combatLuck) {
        if (combatLuck) player.damageFromCombat(damage);
        else player.damage(damage);
    }
}
