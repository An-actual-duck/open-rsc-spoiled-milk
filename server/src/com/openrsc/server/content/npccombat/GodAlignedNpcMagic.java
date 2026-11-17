package com.openrsc.server.content.npccombat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.entity.player.PrayerCatalog;

/** The only active resistance implied by the authored thematic tags. */
public final class GodAlignedNpcMagic {
    private GodAlignedNpcMagic() {}
    public static int reduce(Mob source, Mob target, ProjectileLaunchSpecification launch, int damage) {
        if (damage <= 0 || !(source instanceof Npc) || !(target instanceof Player)
            || launch.getProducer() != ProjectileLaunchSpecification.Producer.NPC_MAGIC
            || launch.getAttackType() != 1) return damage;
        NpcCombatDefinition d = CanonicalNpcCombat.definition((Npc) source);
        PrayerCatalog.GodLine god = launch.getNpcMagicGodLine();
        if (d == null || !d.genericDispatch || god == null || ((Player) target).getPrayerBook() != god) return damage;
        return (int) ((long) damage * 80 / 100);
    }
}
