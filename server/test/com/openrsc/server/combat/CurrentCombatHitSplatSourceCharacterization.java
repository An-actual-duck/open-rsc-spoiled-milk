package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.ElderGreenDragonArmorEffect;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Exercises real event-owned callbacks so shared auxiliary helpers cannot erase source colors. */
final class CurrentCombatHitSplatSourceCharacterization {
    static void sourceColors(CurrentCombatHarness h) throws Exception {
        int position = 500;
        for (boolean pvm : new boolean[] {false, true}) {
            for (ItemId weapon : new ItemId[] {ItemId.FIRE_SWORD, ItemId.ICE_SWORD, ItemId.EARTH_SWORD,
                    ItemId.DRAGON_SWORD, ItemId.DEMON_PITCHFORK}) {
                Player source = h.player("color w " + position, position, 900);
                Npc target = target(h, position + 1);
                h.equip(source, weapon.id(), 1);
                Object event = pvm ? new PvmMeleeEvent(h.world(), source, target) : new CombatEvent(h.world(), source, target);
                h.random().reset(0xC010L + position);
                h.random().scriptDoubles(0.0D).scriptInts(1, 1);
                if (weapon == ItemId.DEMON_PITCHFORK) {
                    invoke(event, "applyDemonPitchforkHellBlazeProc", new Class<?>[] {Mob.class, Mob.class, int.class}, source, target, 5);
                } else if (weapon == ItemId.DRAGON_SWORD) {
                    invoke(event, "applyDragonWeaponBreathDamage", new Class<?>[] {Mob.class, Mob.class}, source, target);
                } else {
                    invoke(event, "applyElementalSwordProc", new Class<?>[] {Mob.class, Mob.class}, source, target);
                }
                assertOneHit(target, HitSplat.TYPE_STANDARD, "weapon callback " + weapon + "/pvm=" + pvm);
                position += 6;
            }
            Player armor = h.player("color bear " + position, position, 900);
            for (ItemId piece : new ItemId[] {ItemId.BEAR_HIDE_COIF, ItemId.BEAR_HIDE_GLOVES,
                    ItemId.BEAR_HIDE_BOOTS, ItemId.BEAR_HIDE_CHAPS, ItemId.BEAR_HIDE_CUIRASS}) h.equip(armor, piece.id(), 1);
            Npc target = target(h, position + 1);
            Object event = pvm ? new PvmMeleeEvent(h.world(), armor, target) : new CombatEvent(h.world(), armor, target);
            invoke(event, "applyBearMaulSecondHit", new Class<?>[] {Mob.class, Mob.class, int.class}, armor, target, 4);
            assertOneHit(target, HitSplat.TYPE_ARMOR_PROC, "armor-gated Maul remains armor-colored");
            position += 6;
        }
        Player archer = h.player("color bow", position, 900);
        Npc bowTarget = target(h, position + 1);
        h.equip(archer, ItemId.DRAGON_LONGBOW.id(), 1);
        ProjectileEvent projectile = new ProjectileEvent(h.world(), archer, bowTarget, 0, 2, false);
        Field breath = ProjectileEvent.class.getDeclaredField("dragonBreathDamage"); breath.setAccessible(true); breath.setInt(projectile, 4);
        invoke(projectile, "applyDragonWeaponBreathDamage", new Class<?>[0]);
        assertOneHit(bowTarget, HitSplat.TYPE_STANDARD, "ranged Dragon weapon offshoot red");

        Player elderArmor = h.player("color elder", 760, 900);
        Npc burned = target(h, 761);
        ElderGreenDragonArmorEffect.applyProc(elderArmor, burned, 4);
        assertOneHit(burned, HitSplat.TYPE_ARMOR_PROC, "initial Elder armor hit purple");
        Object burn = burned.getAttribute("elder_green_dragon_armor_burn_event", null);
        if (burn == null) throw new AssertionError("Elder armor burn event missing");
        invoke(burn, "run", new Class<?>[0]);
        List<HitSplat> values = burned.getUpdateFlags().getHitSplats();
        if (values.size() != 2 || values.get(1).getType() != HitSplat.TYPE_BURN
                || values.get(1).getAmount() != ElderGreenDragonArmorEffect.BURN_DAMAGE) {
            throw new AssertionError("Independent Elder armor burn pulse must be orange with unchanged damage");
        }
    }

    private static Npc target(CurrentCombatHarness h, int x) {
        Npc npc = h.npc(NpcId.GREATER_DEMON.id(), x, 900);
        npc.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 50, 50, false);
        return npc;
    }
    private static void assertOneHit(Npc target, int expected, String label) {
        List<HitSplat> hits = target.getUpdateFlags().getHitSplats();
        if (hits.size() != 1 || hits.get(0).getType() != expected || hits.get(0).getAmount() <= 0
                || target.getLevel(Skill.HITS.id()) != 50 - hits.get(0).getAmount()) {
            throw new AssertionError(label + ": expected one source-colored positive hit and unchanged HP settlement");
        }
    }
    private static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(owner, args);
    }
}
