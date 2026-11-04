package com.openrsc.server.model.combat;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.combat.dot.PeriodicEffectProvenance;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Final MyWorld boundary for player damage, including owned and delayed effects. */
public final class PlayerCombatSafety {
    private PlayerCombatSafety() { }

    public static boolean blocks(DamageRequest request) {
        return blocks(request.getSource(), request.getTarget(), request.getPeriodicProvenance());
    }

    public static boolean blocks(Mob source, Mob target) {
        return blocks(source, target, null);
    }

    public static boolean blocks(Mob source, Mob target, PeriodicEffectProvenance provenance) {
        if (!(target instanceof Player) || !target.getConfig().WANT_MYWORLD
                || source == target) return false;
        // Poison keeps its PvE lifetime after the owner logs out, but never damages in safety.
        if (source == null && provenance != null && provenance.isPlayer())
            return !target.getConfig().WANT_PVP || !target.getLocation().inWilderness()
                || ((Player) target).getDuel().isDuelActive();
        boolean summon = Summoning.isSummon(source);
        Player owner = source instanceof Player ? (Player) source
            : summon ? Summoning.getSummonOwner((Npc) source) : null;
        if (owner == null && !summon) return false; // Ordinary PvE/environmental damage.
        if (owner == null || !owner.getConfig().WANT_PVP
                || owner.getDuel().isDuelActive() || ((Player) target).getDuel().isDuelActive()
                || !owner.getLocation().inWilderness() || !target.getLocation().inWilderness()
                || !owner.sharesSpatialDomain(target)) return true;
        return !source.getLocation().inWilderness()
            || !source.sharesSpatialDomain(target);
    }
}
