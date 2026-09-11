package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.death.DeathLifecycleState;
import com.openrsc.server.util.rsc.DataConversions;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.tuple.Pair;

/** Real equipped-set callbacks, settlement, attribution, death hooks and respawn. No server startup. */
public final class BalrogZeroHealthRegression {
    private static final Class<?>[] DAMAGE = {Mob.class, Mob.class, int.class};

    public static void main(String[] args) throws Exception {
        int scenario = 0;
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            for (int family : new int[] {2, 1, 5, 0, -1}) {
                for (boolean primaryLethal : new boolean[] {false, true}) {
                    for (boolean secondaryLethal : new boolean[] {true, false}) {
                        scenario(h, family, primaryLethal, secondaryLethal, scenario++);
                    }
                }
            }
            contributionAndOverkill(h);
            scheduledImpact(h);
        }
        System.out.println("PASS: 20 Balrog equipped-set scenarios plus contribution, overkill and scheduled-impact regressions");
    }

    private static void scenario(CurrentCombatHarness h, int family,
            boolean primaryLethal, boolean secondaryLethal, int index) throws Exception {
        for (GameTickEvent pending : h.server().getGameEventHandler().getEvents()) pending.stop();
        h.server().getGameEventHandler().cleanupEvents();
        h.random().reset(1L);
        int x = 100 + index * 20;
        // Keep these isolated fixtures away from native blocked terrain at x=440..467.
        if (x >= 440) x += 40;
        Player source = h.player("balrog " + index, x, 600);
        int[] set = {ItemId.BALROG_COIF.id(), ItemId.BALROG_GLOVES.id(),
            ItemId.BALROG_BOOTS.id(), ItemId.BALROG_CHAPS.id(), ItemId.BALROG_CUIRASS.id()};
        for (int id : set) h.equip(source, id, 1);
        check(source.getCarriedItems().getEquipment().hasFullBalrogSet(), "full equipped Balrog set");
        Npc primary = npc(h, x + 1, primaryLethal ? 6 : 20);
        Npc secondary = npc(h, x + 2, secondaryLethal ? 3 : 20);
        Npc distant = npc(h, x + 7, 20);
        primary.setShouldRespawn(false);
        distant.setShouldRespawn(false);
        AtomicInteger primaryHooks = hook(h, primary);
        AtomicInteger secondaryHooks = hook(h, secondary);
        int kills = source.getNpcKills();
        int xp = source.getSkills().getExperience(Skill.MAGIC.id());
        long generation = secondary.getCombatLifecycle();
        Object event = family > 0
            ? new ProjectileEvent(h.world(), source, primary, 1, family, false)
            : family == 0 ? new PvmMeleeEvent(h.world(), source, primary)
            : new CombatEvent(h.world(), source, primary);
        RangeEvent range = new RangeEvent(h.world(), source, 1, primary);
        if (family == 2 || family == 5) source.setRangeEvent(range);

        // Exercise the actual equipped on-hit entry, including InfernalFireProc and AOE selection.
        // Projectile and reciprocal melee still use the production Random adapter.
        long seed = procSeed(source.getInfernalFireProcChance(), source.getInfernalFireProcMaxHit());
        DataConversions.getRandom().setSeed(seed);
        h.random().scriptDoubles(0.0D).scriptInts(6);
        if (family > 0) {
            CurrentCombatHarness.invokePrivate(event, "applyLeatherSetOnHitEffects", new Class<?>[0]);
        } else {
            CurrentCombatHarness.invokePrivate(event, "applyLeatherSetOnHitEffects", DAMAGE, source, primary, 1);
        }
        String context = "family=" + family + " primaryLethal=" + primaryLethal
            + " secondaryLethal=" + secondaryLethal;
        check(secondary.getLevel(Skill.HITS.id()) == (secondaryLethal ? 0 : 17), context + " splash HP");
        check(secondary.killed == secondaryLethal, context + " zero-HP splash victim must enter death");
        check(primary.getLevel(Skill.HITS.id()) == (primaryLethal ? 0 : 14), context + " primary HP");
        check(primary.killed == primaryLethal, context + " no unearned primary death");
        if (family == 2 || family == 5) {
            check(primaryLethal ? source.getRangeEvent() == null : source.getRangeEvent() == range,
                context + " only primary death ends ranged engagement");
        }
        check(distant.getLevel(Skill.HITS.id()) == 20 && !distant.killed, "radius boundary");
        int deaths = (primaryLethal ? 1 : 0) + (secondaryLethal ? 1 : 0);
        check(source.getNpcKills() == kills + deaths, context + " exact kill rewards");
        check(primaryHooks.get() == (primaryLethal ? 1 : 0), "primary hook cardinality");
        check(secondaryHooks.get() == (secondaryLethal ? 1 : 0), "secondary hook cardinality");
        if (deaths > 0) check(source.getSkills().getExperience(Skill.MAGIC.id()) > xp, "magic contribution XP");

        if (!secondaryLethal) {
            check(magicDamage(secondary, source) == 3, "nonlethal splash records exact magic contribution");
            check(secondary.getCombatLifecycle() == generation, "nonlethal splash preserves lifetime");
            check(secondary.getDeathLifecycleSnapshot().getState() == DeathLifecycleState.ALIVE,
                "nonlethal splash stays alive");
            return;
        }
        check(secondary.getDeathLifecycleSnapshot().getState() == DeathLifecycleState.RESPAWNING,
            "lethal splash schedules normal respawn");
        check(secondary.getDeathLifecycleSnapshot().getContext().getKiller() == source,
            "splash death attributed to equipped player");
        check(secondary.getCombatLifecycle() > generation, "death invalidates combat generation");
        int rewardedXp = source.getSkills().getExperience(Skill.MAGIC.id());
        // A second callback in the same tick and a duplicate death cannot replay rewards or hooks.
        CurrentCombatHarness.invokePrivate(event, "inflictAuxiliaryMagicDamage", DAMAGE, source, secondary, 6);
        secondary.killedBy(source);
        check(source.getNpcKills() == kills + deaths, "same-tick duplicate kill rewards");
        check(source.getSkills().getExperience(Skill.MAGIC.id()) == rewardedXp, "duplicate XP");
        check(secondaryHooks.get() == 1, "duplicate loot/death hook");
        GameTickEvent respawn = h.findEvent("Respawn NPC");
        check(respawn != null, "production respawn event exists");
        long deathId = secondary.getDeathLifecycleSnapshot().getLifecycleId();
        respawn.run();
        check(!secondary.killed && secondary.getLevel(Skill.HITS.id()) > 0, "respawn restores health");
        check(secondary.getDeathLifecycleSnapshot().getState() == DeathLifecycleState.ALIVE,
            "respawn restores live state");
        check(secondary.getDeathLifecycleSnapshot().getLifecycleId() == deathId + 1, "new death lifetime");
        check(!respawn.isRunning(), "respawn event stops");
        check(!secondary.hasDamageFrom(source), "respawn clears old contributions");
        int freshHp = secondary.getLevel(Skill.HITS.id());
        CurrentCombatHarness.invokePrivate(event, "inflictAuxiliaryMagicDamage", DAMAGE, source, secondary, 1);
        check(secondary.getLevel(Skill.HITS.id()) == freshHp - 1, "respawn can take new damage");
        check(source.getNpcKills() == kills + deaths && secondaryHooks.get() == 1,
            "new nonlethal damage does not replay old rewards");
    }

    private static void contributionAndOverkill(CurrentCombatHarness h) throws Exception {
        Player source = h.player("balrog assist", 800, 600);
        Player contributor = h.player("prior damage", 800, 601);
        Npc primary = npc(h, 801, 20);
        Npc secondary = npc(h, 802, 20);
        primary.setShouldRespawn(false);
        secondary.setShouldRespawn(false);
        ProjectileEvent event = new ProjectileEvent(h.world(), source, primary, 1, 2, false);
        AtomicInteger hooks = hook(h, secondary);
        secondary.addMageDamage(contributor, 17);
        secondary.getSkills().setLevel(Skill.HITS.id(), 3);
        int sourceXp = source.getSkills().getExperience(Skill.MAGIC.id());
        int priorXp = contributor.getSkills().getExperience(Skill.MAGIC.id());
        CurrentCombatHarness.invokePrivate(event, "inflictAuxiliaryMagicDamage", DAMAGE, source, secondary, 0);
        check(!secondary.killed && secondary.getLevel(Skill.HITS.id()) == 3
            && !secondary.hasDamageFrom(source), "zero payload awards no damage or death");
        int applied = (Integer) CurrentCombatHarness.invokePrivate(event, "inflictAuxiliaryMagicDamage",
            DAMAGE, source, secondary, 18);
        check(applied == 3 && secondary.killed && !primary.killed, "overkill credits only remaining HP");
        check(contributor.getNpcKills() == 1 && source.getNpcKills() == 0,
            "largest contributor keeps kill reward instead of final splash owner");
        int sourceGain = source.getSkills().getExperience(Skill.MAGIC.id()) - sourceXp;
        int priorGain = contributor.getSkills().getExperience(Skill.MAGIC.id()) - priorXp;
        check(sourceGain > 0 && priorGain > sourceGain, "both contributors receive damage-share magic XP");
        int sourceDrops = ownedDropAmount(source);
        int priorDrops = ownedDropAmount(contributor);
        check(sourceDrops > 0 && priorDrops > 0, "both contributors retain personal drops");
        secondary.killedBy(source);
        CurrentCombatHarness.invokePrivate(event, "inflictAuxiliaryMagicDamage", DAMAGE, source, secondary, 18);
        check(hooks.get() == 1 && contributor.getNpcKills() == 1 && source.getNpcKills() == 0,
            "shared kill hook and rewards cannot repeat");
        check(source.getSkills().getExperience(Skill.MAGIC.id()) == sourceXp + sourceGain
            && contributor.getSkills().getExperience(Skill.MAGIC.id()) == priorXp + priorGain,
            "neither contributor receives duplicate XP");
        check(ownedDropAmount(source) == sourceDrops && ownedDropAmount(contributor) == priorDrops,
            "neither contributor receives duplicate personal drops");
    }

    private static int magicDamage(Npc target, Player source) throws Exception {
        Pair<?, ?> info = (Pair<?, ?>) CurrentCombatHarness.invokePrivate(target, "getMageDamageInfoBy",
            new Class<?>[] {UUID.class}, source.getUUID());
        return (Integer) info.getLeft();
    }

    private static void scheduledImpact(CurrentCombatHarness h) throws Exception {
        Player source = h.player("balrog impact", 700, 600);
        for (int id : new int[] {ItemId.BALROG_COIF.id(), ItemId.BALROG_GLOVES.id(),
                ItemId.BALROG_BOOTS.id(), ItemId.BALROG_CHAPS.id(), ItemId.BALROG_CUIRASS.id()}) {
            h.equip(source, id, 1);
        }
        Npc primary = npc(h, 701, 20);
        Npc first = npc(h, 702, 3);
        Npc second = npc(h, 703, 1);
        primary.setShouldRespawn(false);
        second.setShouldRespawn(false);
        AtomicInteger firstHooks = hook(h, first);
        AtomicInteger secondHooks = hook(h, second);
        ProjectileEvent canceled = new ProjectileEvent(h.world(), source, primary, 1, 2, false);
        canceled.setCanceled(true);
        canceled.action();
        check(primary.getLevel(Skill.HITS.id()) == 20 && first.getLevel(Skill.HITS.id()) == 3,
            "canceled impact cannot trigger equipped set damage");
        ProjectileEvent impact = new ProjectileEvent(h.world(), source, primary, 1, 2, false);
        DataConversions.getRandom().setSeed(procSeed(source.getInfernalFireProcChance(),
            source.getInfernalFireProcMaxHit()));
        impact.action();
        check(first.killed && second.killed && !primary.killed,
            "one scheduled impact finalizes multiple lethal splash victims");
        check(source.getNpcKills() == 2 && firstHooks.get() == 1 && secondHooks.get() == 1,
            "scheduled impact awards each victim exactly once");
        int hp = primary.getLevel(Skill.HITS.id());
        int xp = source.getSkills().getExperience(Skill.MAGIC.id());
        int drops = ownedDropAmount(source);
        check(drops > 0, "scheduled splash awards owned drops");
        impact.action();
        check(primary.getLevel(Skill.HITS.id()) == hp && source.getNpcKills() == 2,
            "duplicate scheduled impact cannot hit or award kills again");
        GameTickEvent respawn = h.findEvent("Respawn NPC");
        check(respawn != null, "scheduled splash starts respawn");
        respawn.run();
        int freshHp = first.getLevel(Skill.HITS.id());
        impact.action();
        check(first.getLevel(Skill.HITS.id()) == freshHp && !first.killed,
            "old projectile callback cannot damage respawned lifetime");
        check(source.getSkills().getExperience(Skill.MAGIC.id()) == xp
            && firstHooks.get() == 1 && secondHooks.get() == 1, "no repeated XP or loot hooks");
        check(ownedDropAmount(source) == drops, "no repeated ground-item rewards");
    }

    private static int ownedDropAmount(Player player) {
        int amount = 0;
        for (GroundItem item : player.getViewArea().getItemsInView()) {
            if (item.getOwnerUsernameHash() == player.getUsernameHash()) amount += item.getAmount();
        }
        return amount;
    }

    private static Npc npc(CurrentCombatHarness h, int x, int hp) {
        Npc npc = h.npc(NpcId.GREATER_DEMON.id(), x, 600);
        npc.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), hp, hp, false);
        return npc;
    }

    private static AtomicInteger hook(CurrentCombatHarness h, Npc target) {
        AtomicInteger calls = new AtomicInteger();
        target.addDeathListener(new NpcLootEvent(h.world(), target.getLocation(),
                target.getID(), 1, ItemId.COINS.id()) {
            @Override public void onLootNpcDeath(Player player, Npc npc) {
                calls.incrementAndGet();
                // Reentrant plugin/listener death must also be exactly once.
                npc.killedBy(player);
            }
        });
        return calls;
    }

    private static long procSeed(double chance, int maxHit) {
        for (long seed = 0; seed < 100000; seed++) {
            Random random = new Random(seed);
            if (random.nextDouble() < chance && random.nextInt(maxHit + 1) == 6) return seed;
        }
        throw new AssertionError("cannot find deterministic proc seed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
