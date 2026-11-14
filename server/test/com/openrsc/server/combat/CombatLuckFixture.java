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
      for (int base :
          new int[] {
            ItemId.WOOL_WIZARD_HAT.id(),
            ItemId.WOOL_ROBE_TOP.id(),
            ItemId.WOOL_ROBE_SKIRT.id(),
            ItemId.WOOL_GLOVES.id(),
            ItemId.WOOL_BOOTS.id()
          })
        h.equip(
            source,
            EnchantingItemEffects.getWoolRobeProduct(EnchantingItemEffects.COSMIC_ALTAR, base, 10),
            1);
      check(
          source.getCombatLuckChance() == .5, "all five actual tier10 pieces aggregate to50% cap");
      h.random().reset(3);
      h.random().scriptInts(0, 9, 0, 9).scriptDoubles(0.0, 0.0);
      check(
          CombatFormula.calculateSecondaryMagicDamage(source, npc, 10) > 0,
          "player-owned secondary Magic uses its own damage candidates");
      Player poisoned = h.player("poisoned", 444, 440);
      for (ItemId item :
          new ItemId[] {
            ItemId.GOBLIN_HIDE_COIF,
            ItemId.GOBLIN_HIDE_GLOVES,
            ItemId.GOBLIN_HIDE_BOOTS,
            ItemId.GOBLIN_HIDE_CHAPS,
            ItemId.GOBLIN_HIDE_CUIRASS
          }) h.equip(poisoned, item.id(), 1);
      poisoned.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 100, 100, false);
      com.openrsc.server.model.combat.dot.PeriodicEffectProvenance[] origins = {
        null,
        com.openrsc.server.model.combat.dot.PeriodicEffectProvenance.environment("trap"),
        com.openrsc.server.model.combat.dot.PeriodicEffectProvenance.script("chest"),
        com.openrsc.server.model.combat.dot.PeriodicEffectProvenance.player(
            java.util.UUID.randomUUID()),
        com.openrsc.server.model.combat.dot.PeriodicEffectProvenance.npc(
            java.util.UUID.randomUUID(), 1)
      };
      Method settlePoison =
          com.openrsc.server.event.rsc.impl.PoisonEvent.class.getDeclaredMethod(
              "settleTypedPoisonDamage", int.class);
      settlePoison.setAccessible(true);
      Method combatOrigin =
          com.openrsc.server.event.rsc.impl.PoisonEvent.class.getDeclaredMethod(
              "hasCombatProvenance");
      combatOrigin.setAccessible(true);
      long safeSeed = 0;
      while (new java.util.Random(safeSeed).nextDouble() >= .05) safeSeed++;
      for (int i = 0; i < origins.length; i++) {
        com.openrsc.server.event.rsc.impl.PoisonEvent event =
            new com.openrsc.server.event.rsc.impl.PoisonEvent(
                h.world(), poisoned, 68, origins[i], true);
        check(
            (Boolean) combatOrigin.invoke(event) == (i >= 3),
            "poison survival luck admission uses durable actor provenance, not a vanished live"
                + " source "
                + i);
        poisoned.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 100, 100, false);
        com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(safeSeed);
        settlePoison.invoke(event, 100);
        java.util.Random expected = new java.util.Random(safeSeed);
        // Existing PvP safety rejects orphan player-owned poison outside the
        // Wilderness before survival effects; it must consume no survival RNG.
        boolean safePlayerPoison = origins[i] != null && origins[i].isPlayer()
            && !poisoned.getLocation().inWilderness();
        if (!safePlayerPoison) expected.nextDouble();
        check(
            com.openrsc.server.util.rsc.DataConversions.getRandom().nextDouble()
                == expected.nextDouble(),
            "zero-luck poison survival RNG respects PvP safety " + i);
        check(poisoned.getLevel(Skill.HITS.id()) == (safePlayerPoison ? 100 : 1),
            "blocked player poison cannot damage or invoke Tenacity " + i);
      }
      h.equip(source, com.openrsc.server.constants.custom.MyWorldItemId.SULLEN_PENDANT, 1);
      npc.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      h.random().reset(4);
      h.random().scriptInts(99, 0, 0, 9).scriptDoubles(0.0, 0.0);
      com.openrsc.server.content.monsterslayer.SlayerRewardCombat.pendantHit(source, npc, 1);
      check(
          npc.getLevel(Skill.HITS.id()) == 900,
          "actual defensive pendant gets wearer chance plus damage luck and settles exactly"
              + " one100HP effect");
      h.server().getConfig().WANT_CUSTOM_SPRITES = true;
      source.setClientVersion(10055);
      source.getClientLimitations().maxItemId = 65535;
      h.recordOutgoingPackets(source);
      h.equip(source, ItemId.MAGIC_CAPE.id(), 1);
      long capeSeed = 0;
      for (; ; capeSeed++) {
        java.util.Random r = new java.util.Random(capeSeed);
        if ((int) (r.nextDouble() * 99) + 1 > 10
            && r.nextDouble() < .5
            && (int) (r.nextDouble() * 99) + 1 <= 10) break;
      }
      for (Spells spell : new Spells[] {Spells.EARTH_STRIKE, Spells.ALCHEMY})
        for (java.util.Map.Entry<Integer, Integer> rune :
            h.server().getEntityHandler().getSpellDef(spell).getRunesRequired())
          source
              .getCarriedItems()
              .getInventory()
              .add(new com.openrsc.server.model.container.Item(rune.getKey(), 100));
      source.setLocation(com.openrsc.server.model.Point.location(441, 440));
      source.startCombat(npc);
      source.faceCombat(npc);
      check(source.inCombat(), "utility exclusion tested while actually in combat");
      com.openrsc.server.external.SpellDef utility =
          h.server().getEntityHandler().getSpellDef(Spells.ALCHEMY);
      java.util.Map<Integer, Integer> utilityBefore = new java.util.HashMap<>();
      for (java.util.Map.Entry<Integer, Integer> rune : utility.getRunesRequired())
        utilityBefore.put(
            rune.getKey(), source.getCarriedItems().getInventory().countId(rune.getKey()));
      for (java.util.Map.Entry<Integer, Integer> rune : utility.getRunesRequired())
        check(
            source.getCarriedItems().getInventory().countId(rune.getKey()) >= rune.getValue(),
            "utility input present "
                + rune
                + " count="
                + source.getCarriedItems().getInventory().countId(rune.getKey()));
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(capeSeed);
      int utilityUpdates =
          h.countOutgoingPackets(
              source, com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_INVENTORY_UPDATEITEM);
      check(
          com.openrsc.server.net.rsc.handlers.SpellHandler.checkAndRemoveRunes(
              source, utility, null),
          "actual utility rune-removal succeeds during combat");
      for (java.util.Map.Entry<Integer, Integer> rune : utility.getRunesRequired())
        check(
            source.getCarriedItems().getInventory().countId(rune.getKey())
                == utilityBefore.get(rune.getKey()) - rune.getValue(),
            "utility preserves original failed cape check and removes exact runes once");
      check(
          h.countOutgoingPackets(
                      source, com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_INVENTORY_UPDATEITEM)
                  - utilityUpdates
              == utility.getRunesRequired().size(),
          "one inventory stack update per actual utility rune removal");
      java.util.Map<Integer, Integer> combatBefore = new java.util.HashMap<>();
      for (java.util.Map.Entry<Integer, Integer> rune :
          h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE).getRunesRequired())
        combatBefore.put(
            rune.getKey(), source.getCarriedItems().getInventory().countId(rune.getKey()));
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      h.openCombatProjectileRectangle(440, 445, 440, 443);
      source.setCastTimer(0);
      com.openrsc.server.net.rsc.struct.incoming.SpellStruct packet =
          new com.openrsc.server.net.rsc.struct.incoming.SpellStruct();
      packet.setOpcode(com.openrsc.server.net.rsc.enums.OpcodeIn.CAST_ON_NPC);
      packet.spell = Spells.EARTH_STRIKE;
      packet.targetIndex = npc.getIndex();
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(capeSeed);
      int combatUpdates =
          h.countOutgoingPackets(
              source, com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_INVENTORY_UPDATEITEM);
      new com.openrsc.server.net.rsc.handlers.SpellHandler().process(packet, source);
      if (source.getWalkToAction() != null && source.getWalkToAction().shouldExecute()) {
        source.getWalkToAction().execute();
        source.setWalkToAction(null);
      }
      check(
          !SharedAttackFoundationFixture.launches(h, source).isEmpty(),
          "actual damaging packet launches with favorable combat cape result");
      check(
          h.countOutgoingPackets(
                  source, com.openrsc.server.net.rsc.enums.OpcodeOut.SEND_INVENTORY_UPDATEITEM)
              == combatUpdates,
          "lucky rune-free launch never sends duplicate rune-removal updates");
      for (java.util.Map.Entry<Integer, Integer> rune : combatBefore.entrySet())
        check(
            source.getCarriedItems().getInventory().countId(rune.getKey()) == rune.getValue(),
            "lucky combat cape saves actual launch resources without duplicate removal");
      System.out.println(
          "PASS " + checks + " native Combat Luck ownership/formula/state assertions");
    }
  }
}
