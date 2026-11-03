package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.combat.AttackIntent;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;

/** Real incoming projectile settlement and selected Iban autocast ownership. */
final class CurrentProjectileRetaliationCharacterization {
    private CurrentProjectileRetaliationCharacterization() { }

    static void incomingStyles(final CurrentCombatHarness h) throws Exception {
        int variant = 0;
        for (ProjectileLaunchSpecification.Producer producer : new ProjectileLaunchSpecification.Producer[] {
                ProjectileLaunchSpecification.Producer.NPC_MAGIC,
                ProjectileLaunchSpecification.Producer.NPC_RANGED,
                ProjectileLaunchSpecification.Producer.LEGACY_NPC_RANGED}) {
            Player p = mage(h, "retal " + variant, 600);
            Npc n = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
            ProjectileEvent hit = impact(h, n, p, producer, 0);
            hit.action();
            require(p.getMagicCombatEvent() != null && p.getMagicCombatEvent().getTarget() == n,
                    producer + " zero-damage attack starts selected autocast; impact=" + hit.getInitialProjectileImpactDecision().getReason()
                            + " current=" + p.getOutgoingCombatTarget() + " spell=" + p.getAutoCastSpell()
                            + " sourceHP=" + n.getLevel(Skill.HITS.id()));
            int runes = p.getCarriedItems().getInventory().countId(ItemId.DEATH_RUNE.id());
            p.getMagicCombatEvent().run();
            WalkToAction cast = p.getWalkToAction();
            require(cast != null, producer + " retaliation queues a real cast");
            cast.execute();
            require(p.getCarriedItems().getInventory().countId(ItemId.DEATH_RUNE.id()) < runes,
                    producer + " retaliation launches Iban Blast and consumes its rune");
            finish(h, p, n);
            variant++;
        }
    }

    static void targetDeath(final CurrentCombatHarness h) throws Exception {
        Player p = mage(h, "retal death", 600);
        Npc first = h.npc(NpcId.MOSS_GIANT.id(), 602, 650);
        Npc survivor = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
        require(MagicCombatEvent.start(p, first), "initial Iban target starts");
        MagicCombatEvent current = p.getMagicCombatEvent();
        impact(h, survivor, p, ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1).action();
        require(p.getMagicCombatEvent() == current && current.getTarget() == first,
                "another attacker cannot steal a living autocast target");

        first.getSkills().setLevel(Skill.HITS.id(), 0, false);
        impact(h, survivor, p, ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1).action();
        require(p.getMagicCombatEvent() != null && p.getMagicCombatEvent().getTarget() == survivor,
                "next incoming magic attack replaces dead target even before old event cleanup");

        Npc next = h.npc(NpcId.MOSS_GIANT.id(), 604, 650);
        h.world().unregisterNpc(survivor);
        impact(h, next, p, ProjectileLaunchSpecification.Producer.NPC_RANGED, 1).action();
        require(p.getMagicCombatEvent() != null && p.getMagicCombatEvent().getTarget() == next,
                "incoming ranged attack replaces removed target after event cleanup");
        finish(h, p, first, survivor, next);
    }

    static void playerChoices(final CurrentCombatHarness h) throws Exception {
        Player off = mage(h, "retal off", 600);
        Npc attacker = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
        off.getCache().store("setting_auto_retaliate", false);
        impact(h, attacker, off, ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1).action();
        require(!off.hasOutgoingAttack() && off.getMagicCombatEvent() == null,
                "disabled auto-retaliate stays idle under projectile attack");
        finish(h, off, attacker);

        Player manual = mage(h, "retal manual", 600);
        Npc selected = h.npc(NpcId.MOSS_GIANT.id(), 602, 650);
        Npc other = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
        AttackIntent pending = manual.getAttackTransaction().issue(selected, CombatStyle.MAGIC,
                AttackIntent.Channel.AUTOCAST, AttackIntent.Source.MANUAL, Spells.IBAN_BLAST);
        impact(h, other, manual, ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1).action();
        require(manual.getAttackTransaction().getPending() == pending
                        && manual.getMagicCombatEvent() == null && manual.getPvmMeleeEvent() == null,
                "incoming attack preserves pending manual command without melee fallback");
        finish(h, manual, selected, other);
    }

    static void rejectedAndTerminal(final CurrentCombatHarness h) throws Exception {
        Player p = mage(h, "retal cancel", 600);
        Npc n = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
        ProjectileEvent cancelled = impact(h, n, p, ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1);
        cancelled.setCanceled(true);
        cancelled.action();
        require(!p.hasOutgoingAttack(), "cancelled projectile cannot trigger retaliation");
        finish(h, p, n);

        Player doomed = mage(h, "retal lethal", 600);
        Npc killer = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
        doomed.getSkills().setLevel(Skill.HITS.id(), 1, false);
        impact(h, killer, doomed, ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1).action();
        require(doomed.getMagicCombatEvent() == null,
                "lethal incoming hit cannot start a posthumous cast");
        finish(h, doomed, killer);

        Player safe = mage(h, "retal source", 600);
        Npc dead = h.npc(NpcId.MOSS_GIANT.id(), 603, 650);
        ProjectileEvent inFlight = impact(h, dead, safe, ProjectileLaunchSpecification.Producer.NPC_RANGED, 1);
        dead.getSkills().setLevel(Skill.HITS.id(), 0, false);
        inFlight.action();
        require(!safe.hasOutgoingAttack(), "dead projectile source is never selected");
        finish(h, safe, dead);
    }

    private static Player mage(CurrentCombatHarness h, String name, int x) throws Exception {
        h.openCombatProjectileRectangle(x, x + 5, 650, 650);
        Player p = h.player(name, x, 650);
        p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 99, 99, false);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.MAGIC.id(), 50, 50, false);
        p.getCache().store("setting_auto_retaliate", true);
        p.setQuestStage(Quests.UNDERGROUND_PASS, -1);
        h.equip(p, ItemId.STAFF_OF_IBAN.id(), 1);
        require(p.getCarriedItems().getInventory().add(new Item(ItemId.FIRE_RUNE.id(), 2500))
                        && p.getCarriedItems().getInventory().add(new Item(ItemId.DEATH_RUNE.id(), 500)),
                "Iban reagents installed");
        p.setAutoCastSpell(Spells.IBAN_BLAST);
        return p;
    }

    private static ProjectileEvent impact(CurrentCombatHarness h, Npc n, Player p,
            ProjectileLaunchSpecification.Producer producer, int damage) {
        boolean magic = producer == ProjectileLaunchSpecification.Producer.NPC_MAGIC;
        return new ProjectileEvent(h.world(), n, p,
                ProjectileLaunchSpecification.builder(producer, damage, magic ? 1 : 2)
                        .magicElement(magic ? NpcMagicElement.EARTH : NpcMagicElement.NONE).build());
    }

    private static void finish(CurrentCombatHarness h, Player p, Npc... npcs) {
        h.logout(p);
        for (Npc n : npcs) if (!n.isRemoved()) h.world().unregisterNpc(n);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
