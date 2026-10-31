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
      Class<?> spellClass=Class.forName("com.openrsc.server.net.rsc.handlers.SpellClassification");java.lang.reflect.Method damaging=spellClass.getDeclaredMethod("isDamagingAttack",Spells.class);damaging.setAccessible(true);
      check(!(Boolean)damaging.invoke(null,Spells.FEAR)&&!(Boolean)damaging.invoke(null,Spells.CONFUSE_R),"retro hostile utility never latches combat-only extra delay");
      for(Spells spell:Spells.values()) {
        double damage=h.server().getConstants().getSpellDamages().getSpellDamage(spell,com.openrsc.server.model.entity.EntityType.NPC,com.openrsc.server.constants.SpellDamages.MagicType.MODERNMAGIC);
        if(damage>0)check((Boolean)damaging.invoke(null,spell),"all actual damaging spell producers classified "+spell);
      }
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
      check(
          zero.getActualDamage() == 0 && !zero.isCritical() && zero.isAttackSuppressed(),
          "forced zero is noncritical");
      check(
          AttackState.pendingStaggers(p) == 0 && AttackState.recoveryAttacks(p) == 5,
          "one loss then five recovery");
      for (int i = 0; i < 5; i++) {
        check(!p.applyStagger(1), "mixed sources cannot interrupt recovery " + i);
        advance(h, 4);
        if (i == 0) h.random().scriptInts(4, 4);
        melee.run();
        if (i == 0)
          check(
              results.get(results.size() - 1).getActualDamage() == 0,
              "natural zero is a performed recovery attack");
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
      bow.stop();
      h.server().getGameEventHandler().cleanupEvents();
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
      spell.stop();
      h.server().getGameEventHandler().cleanupEvents();
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
      npcSpell.stop();
      h.server().getGameEventHandler().cleanupEvents();
      check(AttackState.recoveryAttacks(npc) == 5, "NPC impact does not advance recovery");
      npc.advanceCombatLifecycle();
      check(AttackState.recoveryAttacks(npc) == 0, "NPC new lifetime clears recovery");

      Npc legacy = h.npc(NpcId.GREATER_DEMON.id(), 458, 445);
      Player legacyVictim = h.player("legacy ranged", 459, 445);
      healthy(legacy);
      healthy(legacyVictim);
      legacy.applyStagger(1);
      RangeEventNpc legacyEvent = new RangeEventNpc(h.world(), legacy, legacyVictim);
      legacy.setRangeEventNpc(legacyEvent);
      legacyEvent.run();
      ProjectileEvent legacyShot = lastLaunch(h, legacy);
      check(
          legacyShot.getLaunchSnapshot().getSpecification().getAttackCommitment().isSuppressed(),
          "legacy NPC ranged consumes at launch");
      legacyShot.action();
      legacyShot.stop();
      h.server().getGameEventHandler().cleanupEvents();
      check(AttackState.recoveryAttacks(legacy) == 5, "legacy NPC ranged impact never recounts");

      Player thrower = h.player("shared thrower", 454, 445);
      Npc throwTarget = h.npc(NpcId.GREATER_DEMON.id(), 455, 445);
      healthy(thrower);
      healthy(throwTarget);
      h.equip(thrower, ItemId.TIN_SHURIKEN.id(), 4);
      thrower.applyStagger(1);
      ThrowingEvent throwing = new ThrowingEvent(h.world(), thrower, 0, throwTarget);
      thrower.setThrowingEvent(throwing);
      throwing.run();
      List<ProjectileEvent> thrown = launches(h, thrower);
      check(!thrown.isEmpty(), "actual throwing producer launches");
      check(AttackState.recoveryAttacks(thrower) == 5, "throwing fanout commits only once");
      for (ProjectileEvent shot : thrown) {
        check(
            shot.getLaunchSnapshot().getSpecification().getAttackCommitment().isSuppressed(),
            "throwing sibling shares forcedzero receipt");
        shot.action();
        shot.stop();
      }
      h.server().getGameEventHandler().cleanupEvents();
      check(AttackState.recoveryAttacks(thrower) == 5, "throwing impacts never count recovery");
      check(
          thrower.getCarriedItems().getEquipment().get(4).getAmount() < 4,
          "throwing still spends ordinary ammunition");

      int cosmic =
          com.openrsc.server.content.EnchantingItemEffects.getWoolRobeProduct(
              com.openrsc.server.content.EnchantingItemEffects.COSMIC_ALTAR,
              ItemId.WOOL_WIZARD_HAT.id(),
              10);
      for (Map.Entry<Integer, Integer> rune :
          h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE).getRunesRequired()) {
        caster.getCarriedItems().getInventory().add(new Item(rune.getKey(), 100));
      }
      h.equip(caster, cosmic, 1);
      AttackState.clear(caster);
      advance(h, 10);
      h.random().scriptDoubles(0.0);
      check(caster.getCosmicRobeCritChance() > 0, "actual equipped Cosmic provider");
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

      // Actual after-damage Sticky and weapon Whip producers, then actual melee readiness.
      Player frog = h.player("delay frog", 458, 452), whip = h.player("delay whip", 456, 452);
      Npc delayed = h.npc(NpcId.GREATER_DEMON.id(), 457, 452);
      healthy(frog);
      healthy(whip);
      healthy(delayed);
      h.server().getConfig().WANT_CUSTOM_LEATHER = true;
      h.equip(whip, com.openrsc.server.constants.custom.MyWorldItemId.ABYSSAL_WHIP, 1);
      Slow.apply(delayed, 12, Slow.CAP_ONE);
      delayed.startPvmCounterCombat(frog);
      PvmMeleeEvent delayedEvent = delayed.getPvmMeleeEvent();
      delayedEvent.run();
      advance(h, 1);
      delayedEvent.run();
      long baselineDue = h.server().getCurrentTick() + 3;
      for (int id = com.openrsc.server.constants.custom.MyWorldItemId.GIANT_FROG_COIF;
          id < com.openrsc.server.constants.custom.MyWorldItemId.GIANT_FROG_COIF + 5;
          id++) h.equip(frog, id, 1);
      h.random().scriptInts(0);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipHit(whip, delayed, 1);
      check(
          AttackState.hasDelay(delayed, AttackCadenceState.Source.WHIP),
          "real Whip proc adds one token");
      h.random().scriptInts(0);
      h.server()
          .getResolvedDamageTransaction()
          .apply(
              DamageRequest.resolvedLegacy(
                      delayed,
                      frog,
                      DamageRequest.SourceCategory.ACTOR,
                      "sticky-fixture-primary",
                      1)
                  .build());
      check(
          AttackState.hasDelay(delayed, AttackCadenceState.Source.STICKY),
          "real settled primary triggers Sticky token");
      h.random().reset(112);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipHit(whip, delayed, 1);
      check(
          !h.random().describeState().contains("int("),
          "Whip immunity refuses another roll during pause plus three ticks");
      advance(h, 3);
      before = results.size();
      delayedEvent.run();
      check(
          results.size() == before && AttackState.due(delayed) == baselineDue + 2,
          "real attack waits base plus Slow plus both extras");
      delayedEvent.run();
      check(
          AttackState.due(delayed) == baselineDue + 2, "real repeated poll cannot duplicate delay");
      advance(h, 1);
      delayedEvent.run();
      check(results.size() == before, "combined second extra tick remains");
      advance(h, 1);
      h.random().scriptInts(99);
      delayedEvent.run();
      check(results.size() > before, "actual attack resolves exactly after plus three total ticks");
      check(
          !AttackState.hasDelay(delayed, AttackCadenceState.Source.WHIP),
          "Whip token consumed at real attack commit");

      Npc readyNow = h.npc(NpcId.GREATER_DEMON.id(), 456, 454);
      Player readyVictim = h.player("ready pause", 457, 454);
      healthy(readyNow);
      healthy(readyVictim);
      readyNow.startPvmCounterCombat(readyVictim);
      PvmMeleeEvent readyEvent = readyNow.getPvmMeleeEvent();
      h.random().scriptInts(0);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipHit(whip, readyNow, 1);
      check(
          com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipBlocked(readyNow),
          "Whip still pauses movement during its tick");
      readyNow.walkToEntity(456, 453);
      readyNow.getWalkingQueue().processNextMovement();
      check(
          readyNow.getX() == 456 && readyNow.getY() == 454,
          "real walking queue respects Whip movement pause");
      readyNow.resetPath();
      before = results.size();
      long readyTick = h.server().getCurrentTick();
      readyEvent.run();
      check(
          results.size() == before && AttackState.due(readyNow) == readyTick + 1,
          "ready-now Whip is exactly one tick not two");
      advance(h, 1);
      readyEvent.run();
      check(results.size() > before, "ready-now attack resolves after one Whip tick");
      Npc midWait = h.npc(NpcId.GREATER_DEMON.id(), 459, 450);
      Player midVictim = h.player("mid wait", 460, 450);
      healthy(midWait);
      healthy(midVictim);
      midWait.startPvmCounterCombat(midVictim);
      PvmMeleeEvent midEvent = midWait.getPvmMeleeEvent();
      AttackState.delay(midWait, AttackCadenceState.Source.STICKY);
      before = results.size();
      long midTick = h.server().getCurrentTick();
      midEvent.run();
      h.random().scriptInts(0);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipHit(whip, midWait, 1);
      check(
          AttackState.due(midWait) == midTick + 2,
          "Whip during Sticky wait extends same deadline once");
      advance(h, 1);
      midEvent.run();
      check(results.size() == before, "overlapping one-tick sources do not hide each other");
      advance(h, 1);
      midEvent.run();
      check(results.size() > before, "mid-wait additive deadline resolves exactly");

      Player ring = h.player("stagger ring", 450, 454);
      Npc ringTarget = h.npc(NpcId.GREATER_DEMON.id(), 451, 454);
      healthy(ring);
      healthy(ringTarget);
      h.equip(ring, 3090, 1);
      com.openrsc.server.content.EnchantingItemEffects.setDeathRingChargePoints(
          ring, ring.getCarriedItems().getEquipment().getEquippedRingItem(), 1000);
      check(
          com.openrsc.server.content.EnchantingItemEffects.getDeathRingChargeDamage(
                  ring, ring.getCarriedItems().getEquipment().getEquippedRingItem())
              > 0,
          "real charged Death Ring fixture");
      ring.startCombat(ringTarget);
      ring.applyStagger(1);
      int targetHP = ringTarget.getLevel(Skill.HITS.id());
      ring.getPvmMeleeEvent().run();
      check(
          ringTarget.getLevel(Skill.HITS.id()) == targetHP,
          "forced zero cannot bypass with charged Death Ring");

      Npc abyssal =
          h.npc(com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.NPC_ID, 444, 454);
      Player abyssalVictim = h.player("abyssal ready", 445, 454);
      healthy(abyssal);
      healthy(abyssalVictim);
      h.random().scriptInts(99);
      com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.beforeMelee(
          abyssal, abyssalVictim, false, (v, d) -> {});
      abyssal.applyStagger(1);
      abyssal.startPvmCounterCombat(abyssalVictim);
      abyssal.getPvmMeleeEvent().run();
      check(
          AttackState.pendingStaggers(abyssal) == 1 && AttackState.recoveryAttacks(abyssal) == 0,
          "Abyssal private cooldown poll never consumes stagger");
      advance(h, 1);
      abyssal.getPvmMeleeEvent().run();
      check(
          AttackState.pendingStaggers(abyssal) == 0 && AttackState.recoveryAttacks(abyssal) == 5,
          "Abyssal admitted turn consumes once");

      Npc beast = h.npc(com.openrsc.server.content.monsterslayer.DarkBeastCombat.NPC_ID, 454, 454);
      Player marked = h.player("charge target", 455, 454);
      healthy(beast);
      healthy(marked);
      beast.applyStagger(1);
      com.openrsc.server.content.monsterslayer.DarkBeastCombat.startCharge(beast);
      check(
          AttackState.pendingStaggers(beast) == 0 && AttackState.recoveryAttacks(beast) == 5,
          "Dark Beast commits at charge start once");
      GameTickEvent charge = h.findEvent("Dark beast lightning charge");
      int markedHP = marked.getLevel(Skill.HITS.id());
      advance(h, 10);
      charge.run();
      check(
          marked.getLevel(Skill.HITS.id()) == markedHP && AttackState.recoveryAttacks(beast) == 5,
          "suppressed charge retains telegraph then zero impact without recount");
      Npc fresh = h.npc(NpcId.GREATER_DEMON.id(), 442, 448);
      Player freshVictim = h.player("fresh coating", 443, 448);
      healthy(fresh);
      healthy(freshVictim);
      fresh.startPvmCounterCombat(freshVictim);
      PvmMeleeEvent freshEvent = fresh.getPvmMeleeEvent();
      freshEvent.run();
      long normalDue = h.server().getCurrentTick() + 2;
      Slow.apply(fresh, 12, Slow.CAP_ONE);
      AttackState.delay(fresh, AttackCadenceState.Source.WHIP);
      AttackState.delay(fresh, AttackCadenceState.Source.STICKY);
      advance(h, 2);
      before = results.size();
      freshEvent.run();
      check(
          results.size() == before && AttackState.due(fresh) == normalDue + 3,
          "fresh Slow1 plus Whip plus Sticky adds three after existing unslowed base");
      long freshDue = AttackState.due(fresh);
      check(
          !AttackState.ready(fresh, Slow.Cadence.RANGED) && AttackState.due(fresh) == freshDue,
          "style switch cannot duplicate or escape uncaptured Slow credit");
      advance(h, 3);
      freshEvent.run();
      check(results.size() > before, "fresh combination resolves at normal plus three");
      Npc topup = h.npc(NpcId.GREATER_DEMON.id(), 444, 448);
      Player topupVictim = h.player("slow topup", 445, 448);
      healthy(topup);
      healthy(topupVictim);
      Slow.apply(topup, 12, Slow.CAP_ONE);
      topup.startPvmCounterCombat(topupVictim);
      PvmMeleeEvent topupEvent = topup.getPvmMeleeEvent();
      topupEvent.run();
      advance(h, 1);
      topupEvent.run();
      long capturedDue = h.server().getCurrentTick() + 3;
      check(
          Slow.captured(topup, Slow.Cadence.MELEE) == 1, "actual interval captures one Slow tick");
      Slow.apply(topup, 10, Slow.CAP_TWO);
      advance(h, 3);
      before = results.size();
      topupEvent.run();
      check(
          results.size() == before && AttackState.due(topup) == capturedDue + 1,
          "captured Slow1 to Slow2 adds only one new Slow tick");
      long topupDue = AttackState.due(topup);
      Slow.clear(topup);
      check(
          !AttackState.ready(topup) && AttackState.due(topup) == topupDue,
          "decay or cleanse cannot shorten already latched Slow");
      advance(h, 1);
      topupEvent.run();
      check(results.size() > before, "topup resumes exactly after one delta");

      java.lang.reflect.Field sideEffects =
          h.server().getCombatScriptLoader().getClass().getDeclaredField("combatSideEffectScripts");
      sideEffects.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, com.openrsc.server.event.rsc.impl.combat.scripts.CombatSideEffectScript> scripts =
          (Map<String, com.openrsc.server.event.rsc.impl.combat.scripts.CombatSideEffectScript>)
              sideEffects.get(h.server().getCombatScriptLoader());
      scripts.put(
          "native-poison",
          new com.openrsc.server.event.rsc.impl.combat.scripts.all.NpcPoisonPlayerScript());
      Npc spider = h.npc(NpcId.POISON_SPIDER.id(), 449, 448);
      Player poisonTarget = h.player("forced poison", 450, 448);
      healthy(spider);
      healthy(poisonTarget);
      long poisonSeed = 0;
      while (new Random(poisonSeed).nextInt(100) < 90) poisonSeed++;
      spider.startPvmCounterCombat(poisonTarget);
      spider.applyStagger(1);
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(poisonSeed);
      spider.getPvmMeleeEvent().run();
      check(
          poisonTarget.getCurrentPoisonPower() == 0,
          "forced zero cannot invoke NPC offensive poison script");
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(poisonSeed);
      CurrentCombatHarness.invokePrivate(
          spider.getPvmMeleeEvent(),
          "inflictDamage",
          new Class<?>[] {Mob.class, Mob.class, int.class, boolean.class},
          spider,
          poisonTarget,
          0,
          false);
      check(
          poisonTarget.getCurrentPoisonPower() > 0,
          "natural zero retains existing NPC poison script eligibility");
      poisonTarget.curePoison();

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
