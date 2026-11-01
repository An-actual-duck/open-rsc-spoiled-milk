package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.PoisonProcChance;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Actual game-owned robe aggregation, formula ownership and one-shot effect state. */
public final class CombatLuckFixture {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static int formula(CurrentCombatHarness h, Mob source, Mob target, int offense, int defense)
      throws Exception {
    Method m =
        CombatFormula.class.getDeclaredMethod(
            "applyMitigationRoll", Mob.class, Mob.class, int.class, int.class, boolean.class);
    m.setAccessible(true);
    return (Integer) m.invoke(null, source, target, offense, defense, true);
  }

  public static void main(String[] args) throws Exception {
    try (CurrentCombatHarness h = new CurrentCombatHarness()) {
      h.server().getConfig().WANT_MYWORLD = true;
      Player source = h.player("lucky", 440, 440), victim = h.player("defense", 441, 440);
      Npc npc = h.npc(NpcId.GREATER_DEMON.id(), 442, 440);
      int robe =
          EnchantingItemEffects.getWoolRobeProduct(
              EnchantingItemEffects.COSMIC_ALTAR, ItemId.WOOL_WIZARD_HAT.id(), 10);
      h.equip(source, robe, 1);
      check(source.getCombatLuckChance() == .1, "one tier10 piece is10% combat luck");
      check(
          source.getCosmicRobeCritChance() == 0 && !source.rollCosmicRobeCrit(),
          "retired Cosmic critical accessors cannot grant criticals");
      h.random().reset(1);
      h.random().scriptInts(0, 8).scriptDoubles(0.0);
      check(
          formula(h, source, npc, 10, 0) == 9,
          "actual offense selects favorable complete candidate");
      h.random().reset(1);
      h.random().scriptInts(0, 8).scriptDoubles(.99);
      check(formula(h, source, npc, 10, 0) == 1, "failed luck keeps original offense");
      Arrays.fill(source.getCarriedItems().getEquipment().getList(), null);
      h.equip(victim, robe, 1);
      h.random().reset(1);
      h.random().scriptInts(9, 0, 8).scriptDoubles(0.0);
      check(
          formula(h, source, victim, 10, 10) == 1,
          "defender owns its favorable mitigation candidate");
      h.random().reset(1);
      h.random().scriptInts(9, 0).scriptDoubles(.99);
      check(
          formula(h, source, victim, 10, 10) == 9,
          "defender failed luck leaves original mitigation");
      h.random().reset(1);
      h.random().scriptInts(0).scriptDoubles(0.0);
      check(
          formula(h, npc, npc, 10, 0) == 0 && !h.random().describeState().contains("int("),
          "NPC damage keeps its original distribution and no owner luck");
      h.equip(source, robe, 1);
      h.random().reset(1);
      h.random().scriptDoubles(.9, 0.0, .1);
      AttackRoll authored =
          CriticalHitResolver.resolve(
              source, 10, .15, AttackRoll.Critical.AUTHORED, h.random(), () -> 1);
      check(
          authored.isCritical() && authored.getDamage() == 10,
          "authored generic critical check benefits from luck");
      h.random().reset(1);
      h.random().scriptInts(9, 9).scriptDoubles(0.0);
      AttackRoll normal = CombatFormula.rollMagicDamage(source, npc, 10);
      check(!normal.isCritical(), "lucky ordinary maximum does not become critical");
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(1);
      check(
          PoisonProcChance.rollWeapon(source, npc, 1), "guaranteed adaptive first poison succeeds");
      check(
          source.getAttribute("poison_proc_weapon_chance", 0) == 50,
          "adaptive guaranteed success mutates chance once");
      long favorableSeed = -1;
      for (long seed = 0; seed < 100000; seed++) {
        java.util.Random random = new java.util.Random(seed);
        if (random.nextInt(100) >= 50 && random.nextDouble() < .1 && random.nextInt(100) < 50) {
          favorableSeed = seed;
          break;
        }
      }
      check(favorableSeed >= 0, "deterministic integer poison luck boundary exists");
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(favorableSeed);
      check(
          PoisonProcChance.rollWeapon(source, npc, 1),
          "adaptive integer proc failure salvaged by favorable extra candidate");
      check(
          source.getAttribute("poison_proc_weapon_chance", 0) == 40,
          "lucky selected success updates adaptive state exactly once");
      h.equip(source, com.openrsc.server.constants.custom.MyWorldItemId.ABYSSAL_WHIP, 1);
      h.random().reset(1);
      h.random().scriptInts(99, 0).scriptDoubles(0.0);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipHit(source, npc, 1);
      check(
          com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipBlocked(npc),
          "actual integer Whip proc gains favorable check");
      h.random().reset(2);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.whipHit(source, npc, 1);
      check(
          !h.random().describeState().contains("int("),
          "Whip immunity is checked once before any luck candidates");
      for (int i = 0; i < 50; i++) {
        PoisonProcChance.rollWeapon(source, npc, 1);
        int chance = source.getAttribute("poison_proc_weapon_chance", 0);
        check(
            chance >= 20 && chance <= 100,
            "adaptive luck preserves chance bounds and single state authority");
      }
      System.out.println(
          "PASS " + checks + " native Combat Luck ownership/formula/state assertions");
    }
  }
}
