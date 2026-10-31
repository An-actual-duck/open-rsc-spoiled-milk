package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.Slow;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.*;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import java.util.*;

/**
 * Native-world actual attack launch/strike/impact coverage, without network listeners or accounts.
 */
public final class SharedAttackFoundationFixture {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static void advance(CurrentCombatHarness h, int ticks) throws Exception {
    CurrentCombatHarness.invokePrivate(
        h.server(), "advanceTicks", new Class<?>[] {long.class}, (long) ticks);
    h.clock().advanceMillis((long) ticks * h.server().getConfig().GAME_TICK);
  }

  static void healthy(Mob mob) {
    mob.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
  }

  static List<ProjectileEvent> launches(CurrentCombatHarness h, Mob source) {
    List<ProjectileEvent> result = new ArrayList<>();
    for (GameTickEvent e : h.server().getGameEventHandler().getEvents())
      if (e instanceof ProjectileEvent
          && ((ProjectileEvent) e).getLaunchSnapshot().getSourceSnapshot().matches(source))
        result.add((ProjectileEvent) e);
    return result;
  }

  static ProjectileEvent lastLaunch(CurrentCombatHarness h, Mob source) {
    List<ProjectileEvent> all = launches(h, source);
    check(!all.isEmpty(), "actual producer enqueued projectile");
    return Collections.max(
        all, Comparator.comparingLong(e -> e.getLaunchSnapshot().getLaunchTick()));
  }

  static void magic(CurrentCombatHarness h, Player p, Npc n) throws Exception {
    p.setCastTimer(0);
    SpellStruct packet = new SpellStruct();
    packet.setOpcode(OpcodeIn.CAST_ON_NPC);
    packet.spell = Spells.EARTH_STRIKE;
    packet.targetIndex = n.getIndex();
    com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);
    new SpellHandler().process(packet, p);
    if (p.getWalkToAction() != null && p.getWalkToAction().shouldExecute()) {
      p.getWalkToAction().execute();
      p.setWalkToAction(null);
    }
  }

  public static void main(String[] args) throws Exception {
    List<DamageResult> results = new ArrayList<>();
    try (CurrentCombatHarness h = new CurrentCombatHarness(results::add)) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.server().getConfig().WANT_EQUIPMENT_TAB = true;
      h.server().getConfig().MEMBER_WORLD = true;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      h.openCombatProjectileRectangle(440, 460, 440, 455);
      Player p = h.player("shared melee", 440, 440);
      Npc n = h.npc(NpcId.GREATER_DEMON.id(), 441, 440);
      healthy(p);
      healthy(n);
      p.startCombat(n);
      PvmMeleeEvent melee = p.getPvmMeleeEvent();
      check(melee != null, "actual melee event owned");
      check(p.applyStagger(1), "shared player stagger admitted");
      p.applyStartleDebuff(n);
      int before = results.size();
      melee.run();
      check(results.size() > before, "stagger retains attack turn");
      DamageResult zero = results.get(results.size() - 1);
      check(zero.getActualDamage() == 0 && !zero.isCritical(), "forced zero is noncritical");
      check(
          AttackState.pendingStaggers(p) == 0 && AttackState.recoveryAttacks(p) == 5,
          "one loss then five recovery");
      for (int i = 0; i < 5; i++) {
        check(!p.applyStagger(1), "mixed sources cannot interrupt recovery " + i);
        advance(h, 4);
        melee.run();
        check(AttackState.recoveryAttacks(p) == 4 - i, "one actual melee recovery " + i);
      }
      check(p.applyStagger(2), "N attack API after exact boundary");
      for (int i = 0; i < 2; i++) {
        advance(h, 4);
        melee.run();
        check(AttackState.pendingStaggers(p) == 1 - i, "N loss preserved");
        check(!p.applyStagger(1), "cannot replace N losses");
      }
      p.advanceCombatLifecycle();
      check(
          AttackState.pendingStaggers(p) == 0 && AttackState.recoveryAttacks(p) == 0,
          "lifecycle clears recovery");

      Player archer = h.player("shared archer", 444, 440);
      Npc rangedTarget = h.npc(NpcId.GREATER_DEMON.id(), 446, 440);
      healthy(archer);
      healthy(rangedTarget);
      h.equip(archer, ItemId.LONGBOW.id(), 1);
      RangeEvent range = new RangeEvent(h.world(), archer, 0, rangedTarget);
      archer.setRangeEvent(range);
      check(archer.applyStagger(1), "bow stagger pending");
      AttackState.delay(archer, AttackCadenceState.Source.WHIP);
      range.run();
      check(
          AttackState.pendingStaggers(archer) == 1
              && AttackState.hasDelay(archer, AttackCadenceState.Source.WHIP)
              && AttackState.due(archer) == Long.MIN_VALUE,
          "ammo failure preserves pending delay and stagger");
      h.equip(archer, ItemId.TIN_ARROWS.id(), 100);
      range = new RangeEvent(h.world(), archer, 0, rangedTarget);
      archer.setRangeEvent(range);
      range.run();
      check(launches(h, archer).isEmpty(), "bow token delays before spending");
      check(
          archer.getCarriedItems().getEquipment().getAmmoItem().getAmount() == 100,
          "delay does not spend ammo");
      advance(h, 1);
      range.run();
      ProjectileEvent bow = lastLaunch(h, archer);
      check(
          bow.getLaunchSnapshot().getSpecification().getAttackCommitment().isSuppressed(),
          "real bow snapshots suppression");
      check(AttackState.recoveryAttacks(archer) == 5, "bow launch counts once");
      bow.action();
      check(AttackState.recoveryAttacks(archer) == 5, "bow impact never counts again");
      check(
          !bow.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),
          "stagger overrides critical identity");

      Player caster = h.player("shared magic", 450, 440);
      Npc magicTarget = h.npc(NpcId.GREATER_DEMON.id(), 452, 440);
      healthy(caster);
      healthy(magicTarget);
      caster.getSkills().setTemporaryLevelAndMaxStat(Skill.MAGIC.id(), 99, 99, false);
      caster.getClientLimitations().maxItemId = Integer.MAX_VALUE;
      caster.applyStagger(1);
      AttackState.delay(caster, AttackCadenceState.Source.STICKY);
      magic(h, caster, magicTarget);
      check(
          AttackState.pendingStaggers(caster) == 1 && AttackState.due(caster) == Long.MIN_VALUE,
          "rune failure preserves unlatched delay/stagger");
      for (Map.Entry<Integer, Integer> rune :
          h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE).getRunesRequired())
        caster.getCarriedItems().getInventory().add(new Item(rune.getKey(), 100));
      magic(h, caster, magicTarget);
      check(launches(h, caster).isEmpty(), "Magic extra delay admits only after rune check");
      advance(h, 1);
      magic(h, caster, magicTarget);
      ProjectileEvent spell = lastLaunch(h, caster);
      check(
          spell.getLaunchSnapshot().getSpecification().getAttackCommitment().isSuppressed(),
          "actual Magic packet snapshots suppression");
      check(AttackState.recoveryAttacks(caster) == 5, "Magic launch recovery exactly once");
      spell.action();
      check(AttackState.recoveryAttacks(caster) == 5, "Magic impact no recovery decrement");

      Player victim = h.player("shared victim", 450, 450);
      Npc npc = h.npc(NpcId.GREATER_DEMON.id(), 452, 450);
      healthy(victim);
      healthy(npc);
      java.lang.reflect.Field targetField = npc.getBehavior().getClass().getDeclaredField("target");
      targetField.setAccessible(true);
      targetField.set(npc.getBehavior(), victim);
      npc.applyStagger(1);
      advance(h, 10);
      CurrentCombatHarness.invokePrivate(
          npc.getBehavior(),
          "tryProjectileAttack",
          new Class<?>[] {long.class},
          h.clock().currentTimeMillis());
      ProjectileEvent npcSpell = lastLaunch(h, npc);
      check(
          npcSpell.getLaunchSnapshot().getSpecification().getAttackCommitment().isSuppressed(),
          "actual NPC Magic snapshots suppression");
      npcSpell.action();
      check(AttackState.recoveryAttacks(npc) == 5, "NPC impact does not advance recovery");
      npc.advanceCombatLifecycle();
      check(AttackState.recoveryAttacks(npc) == 0, "NPC new lifetime clears recovery");

      int cosmic =
          com.openrsc.server.content.EnchantingItemEffects.getWoolRobeProduct(
              com.openrsc.server.content.EnchantingItemEffects.COSMIC_ALTAR,
              ItemId.WOOL_WIZARD_HAT.id(),
              10);
      h.equip(caster, cosmic, 1);
      AttackState.clear(caster);
      advance(h, 10);
      h.random().scriptDoubles(0.0);
      magic(h, caster, magicTarget);
      ProjectileEvent criticalSpell = lastLaunch(h, caster);
      check(
          criticalSpell.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),
          "actual Magic launch true Cosmic identity");
      check(caster.applyStagger(1), "post-launch stagger is accepted for following action");
      before = results.size();
      criticalSpell.action();
      check(results.size() > before, "actual Magic impact settles");
      DamageResult criticalMagic = results.get(results.size() - 1);
      check(criticalMagic.isCritical(), "Magic critical receipt survives later stagger");
      check(
          AttackState.pendingStaggers(caster) == 1 && AttackState.recoveryAttacks(caster) == 0,
          "in-flight impact cannot consume new stagger");
      criticalSpell.action();
      check(
          AttackState.pendingStaggers(caster) == 1,
          "duplicate impact never commits another attack");

      AttackState.clear(archer);
      h.equip(archer, cosmic, 1);
      advance(h, 10);
      h.random().scriptDoubles(0.0);
      range = new RangeEvent(h.world(), archer, 0, rangedTarget);
      archer.setRangeEvent(range);
      range.run();
      ProjectileEvent criticalBow = lastLaunch(h, archer);
      check(
          criticalBow.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),
          "actual bow critical identity");
      before = results.size();
      criticalBow.action();
      check(
          results.size() > before && results.get(results.size() - 1).isCritical(),
          "bow critical reaches immutable DamageResult");

      Player hitter = h.player("shared critical", 440, 450);
      Npc defender = h.npc(NpcId.GREATER_DEMON.id(), 441, 450);
      healthy(hitter);
      healthy(defender);
      h.equip(hitter, cosmic, 1);
      hitter.startCombat(defender);
      h.random().scriptDoubles(0.0);
      hitter.getPvmMeleeEvent().run();
      check(
          results.get(results.size() - 1).isCritical(),
          "actual melee critical identity reaches DamageResult");
      check(hitter.applyStagger(1), "critical source can be staggered");
      advance(h, 4);
      h.random().reset(991);
      hitter.getPvmMeleeEvent().run();
      check(
          !results.get(results.size() - 1).isCritical()
              && results.get(results.size() - 1).getActualDamage() == 0,
          "forced melee zero never critical");
      check(
          !h.random().describeState().contains("double="),
          "suppressed melee never consumes Cosmic roll");
      check(!AttackRoll.normal(99).isCritical(), "normal maximum remains ordinary");
      h.random().scriptDoubles(0.0);
      check(
          CombatFormula.rollOffhandMeleeDamage(hitter, defender).isCritical(),
          "legacy Cosmic offhand eligibility retained explicitly");
      h.random().scriptDoubles(0.0);
      check(
          CombatFormula.calculateSecondaryMagicDamage(hitter, defender, 10) > 0,
          "legacy Cosmic secondary damage retained");

      for (int slow = 1; slow <= 2; slow++)
        for (int mask = 0; mask < 4; mask++) {
          AttackState.clear(victim);
          Slow.clear(victim);
          Slow.apply(victim, slow == 1 ? 12 : 22, slow == 1 ? Slow.CAP_ONE : Slow.CAP_TWO);
          int interval = Slow.delay(victim, 3);
          Slow.commit(victim, Slow.Cadence.MELEE, interval);
          if ((mask & 1) != 0) AttackState.delay(victim, AttackCadenceState.Source.WHIP);
          if ((mask & 2) != 0) AttackState.delay(victim, AttackCadenceState.Source.STICKY);
          check(!Slow.ready(victim, Slow.Cadence.MELEE), "Slow interval remains latched");
          advance(h, interval);
          int extras = Integer.bitCount(mask);
          boolean admitted = AttackState.ready(victim);
          check(admitted == (extras == 0), "extra delays follow base plus Slow");
          if (extras > 0) {
            long due = AttackState.due(victim);
            check(due == h.server().getCurrentTick() + extras, "sum exact additive extras");
            check(
                !AttackState.ready(victim) && AttackState.due(victim) == due,
                "poll does not duplicate extras");
            advance(h, extras);
            check(AttackState.ready(victim), "combined deadline exact");
          }
          AttackState.commitAttack(victim);
          check(
              !AttackState.hasDelay(victim, AttackCadenceState.Source.WHIP)
                  && !AttackState.hasDelay(victim, AttackCadenceState.Source.STICKY),
              "actual commit consumes both receipts");
        }
      System.out.println(
          "PASS " + checks + " shared Stagger/Crit/delay native event and real launch assertions");
    }
  }
}
