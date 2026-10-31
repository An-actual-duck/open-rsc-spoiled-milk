package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.content.cleric.runtime.*;
import com.openrsc.server.content.party.*;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator;
import com.openrsc.server.net.rsc.handlers.*;
import com.openrsc.server.net.rsc.struct.incoming.OptionsStruct;
import com.openrsc.server.net.rsc.struct.outgoing.ClericSpellbookStruct;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * Real packet casts, native map/party/inventory/effects/combat and serialized metadata. No
 * network/account DB.
 */
public final class ClericBlessingsFixture {
  static int checks;

  static void check(boolean b, String s) {
    checks++;
    if (!b) throw new AssertionError(s);
  }

  static Inventory inv(Player p) {
    return p.getCarriedItems().getInventory();
  }

  static int count(Player p, int id) {
    return inv(p).countId(id, Optional.of(false));
  }

  static void skill(Player p, Skill skill, int current, int normal) {
    p.getSkills().setTemporaryLevelAndMaxStat(skill.id(), current, normal, false);
  }

  static void clear(Player p) {
    p.getTransientEffectState().clearAll();
    p.resetAll();
    inv(p).getItems().clear();
    skill(p, Skill.HITS, 100, 1000);
  }

  static void fund(Player p, ClericSpellDefinition d) {
    inv(p).getItems().clear();
    for (ClericResourceCost c : d.getResourceCosts()) inv(p).add(new Item(c.getItemId(), 100));
  }

  static void packet(Player p, int code) throws Exception {
    OptionsStruct s = new OptionsStruct();
    s.index = 26;
    s.id = code;
    new InterfaceOptionHandler().process(s, p);
  }

  static Party party(CurrentCombatHarness h, Player... ps) throws Exception {
    Party party = new Party(h.world());
    Constructor<PartyPlayer> c = PartyPlayer.class.getDeclaredConstructor(String.class);
    c.setAccessible(true);
    Method set = PartyPlayer.class.getDeclaredMethod("setPlayerReference", Player.class);
    set.setAccessible(true);
    for (Player p : ps) {
      p.setParty(party);
      PartyPlayer pp = c.newInstance(p.getUsername());
      set.invoke(pp, p);
      pp.setRank(PartyRank.NORMAL);
      party.getPlayers().add(pp);
      if (party.getLeader() == null) party.setLeader(pp);
    }
    return party;
  }

  static ClericEffectRegistry registry(Player p) {
    return (ClericEffectRegistry) p.getTransientEffectState();
  }

  static void cast(Player caster, Player recipient, int code) throws Exception {
    clear(recipient);
    fund(caster, ClericSpellCatalog.fromCode(code));
    packet(caster, code);
    check(
        recipient.getTransientEffectState() instanceof ClericEffectRegistry,
        "packet applies blessing " + code);
  }

  public static void main(String[] args) throws Exception {
    try (CurrentCombatHarness h = new CurrentCombatHarness()) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      Player c = h.player("clericcast", 450, 450), r = h.player("clerically", 451, 450);
      for (Player p : new Player[] {c, r}) {
        p.setClientVersion(10054);
        p.getClientLimitations().maxItemId = 65535;
        h.recordOutgoingPackets(p);
        skill(p, Skill.HITS, 100, 1000);
      }
      party(h, c, r);
      skill(c, Skill.PRAYER, 160, 99);
      int[] animationIds = {67, 72, 75, 69, 70, 74, 68, 73, 76};
      for (int i = 0; i < animationIds.length; i++)
        check(
            ClericSpellCatalog.fromCode(12 + i).getPresentation().getOnEntityAnimationId()
                == animationIds[i],
            "active blessing maps approved existing animation");
      check(
          !ClericSpellCatalog.fromCode(1).getPresentation().hasOnEntityAnimation()
              && !ClericSpellCatalog.fromCode(21).getPresentation().hasOnEntityAnimation(),
          "both Unify forms stay animationless");
      check(ClericSpellCatalog.getAll().size() == 11, "active11 book");
      int[] thresholds = {0, 20, 45, 75, 115, 160};
      for (ClericSpellDefinition d : ClericSpellCatalog.getAll()) {
        check(d.getId().isActive(), "no retired metadata");
        if (!d.getId().isBlessing()) continue;
        check(d.getRadius() == d.getSpellTier() + 1, "tier radius");
        for (int threshold : thresholds)
          for (int hp : new int[] {Math.max(0, threshold - 1), threshold, threshold + 1}) {
            int rank = 1;
            for (int i = 1; i < 6; i++) if (hp >= thresholds[i]) rank = i + 1;
            check(
                d.resolveEffectRank(hp) == Math.min(rank, d.getSpellTier() * 2),
                "all threshold boundaries " + d.getId() + "/" + hp);
          }
        cast(c, r, d.getStableCode());
        for (ClericResourceCost cost : d.getResourceCosts())
          check(
              count(c, cost.getItemId()) == 100 - cost.getCount(),
              "flat exact mixed cost " + d.getId());
        check(c.getTransientEffectState() == TransientEffectState.empty(), "caster excluded");
        ClericBlessingMagnitude m =
            (ClericBlessingMagnitude)
                registry(r)
                    .snapshot(ClericEffectOrigins.validatorFor(r))
                    .get(0)
                    .getDefinition()
                    .getMagnitude();
        check(m.getPower() == d.getSpellTier() * 2, "tier power cap at high HolyPower");
      }
      for (int old : new int[] {0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}) {
        clear(r);
        packet(c, old);
        check(
            (r.getTransientEffectState() == TransientEffectState.empty()
                    ? 0
                    : registry(r).size(ClericEffectOrigins.validatorFor(r)))
                == 0,
            "retired packet refused " + old);
      }
      clear(r);
      fund(c, ClericSpellCatalog.fromCode(12));
      inv(c).getItems().removeIf(i -> i.getCatalogId() == 3300);
      int god = count(c, 3294);
      packet(c, 12);
      check(
          count(c, 3294) == god
              && (r.getTransientEffectState() == TransientEffectState.empty()
                      ? 0
                      : registry(r).size(ClericEffectOrigins.validatorFor(r)))
                  == 0,
          "missing Grace cannot partly consume god cost");
      fund(c, ClericSpellCatalog.fromCode(12));
      c.setClientVersion(10053);
      packet(c, 12);
      check(count(c, 3294) == 100, "old maintained client failclosed");
      c.setClientVersion(10054);
      clear(r);
      fund(c, ClericSpellCatalog.fromCode(12));
      inv(c).getItems().removeIf(item -> item.getCatalogId() == 3300);
      inv(c).getItems().add(new Item(3300, 100, true));
      packet(c, 12);
      check(
          count(c, 3294) == 100 && count(c, 3300) == 0,
          "noted Grace cannot satisfy or partially consume mixed cost");
      clear(r);
      skill(r, Skill.HITS, 1000, 1000);
      fund(c, ClericSpellCatalog.fromCode(12));
      packet(c, 12);
      check(
          count(c, 3294) == 99
              && ClericTimedEffectRuntime.blessing(r, ClericEffectFamily.SARADOMIN_BLESSING)
                  != null,
          "fullHP recipient still eligible for whole blessing");

      cast(c, r, 14);
      double rate = ClericTimedEffectRuntime.getRespiteSpeedBonus(r);
      check(rate == 3.5, "P6 total regen4.5x, additional3.5");
      int start = r.getSkills().getLevel(Skill.HITS.id());
      for (int pulse = 1; pulse <= 10; pulse++) {
        h.clock().advanceMillis(6000);
        ClericTimedEffectRuntime.getRespiteSpeedBonus(r);
        check(
            r.getSkills().getLevel(Skill.HITS.id()) == start + 6 * pulse,
            "ten delayed Sara pulses inclusive endpoint " + pulse);
      }
      check(
          ClericTimedEffectRuntime.getRespiteSpeedBonus(r) == 0,
          "all composite components expire at60s");
      cast(c, r, 13);
      start = r.getSkills().getLevel(Skill.HITS.id());
      h.clock().advanceMillis(1000);
      fund(c, ClericSpellCatalog.fromCode(13));
      packet(c, 13);
      check(r.getSkills().getLevel(Skill.HITS.id()) == start, "equal refresh no immediate healing");
      h.clock().advanceMillis(5000);
      ClericTimedEffectRuntime.getRespiteSpeedBonus(r);
      check(
          r.getSkills().getLevel(Skill.HITS.id()) == start + 4,
          "refresh preserves original6s cadence");
      fund(c, ClericSpellCatalog.fromCode(12));
      packet(c, 12);
      check(
          ClericTimedEffectRuntime.blessing(r, ClericEffectFamily.SARADOMIN_BLESSING).getTier()
              == 2,
          "lower tier cannot downgrade");
      fund(c, ClericSpellCatalog.fromCode(17));
      packet(c, 17);
      fund(c, ClericSpellCatalog.fromCode(20));
      packet(c, 20);
      check(registry(r).size(ClericEffectOrigins.validatorFor(r)) == 3, "three gods coexist");
      cast(c, r, 17);
      skill(r, Skill.MELEE, 60, 80);
      skill(r, Skill.MAGIC, 90, 80);
      int hits = r.getSkills().getLevel(Skill.HITS.id());
      h.clock().advanceMillis(4999);
      check(
          ClericTimedEffectRuntime.poisonCleanse(r) == 7
              && r.getSkills().getLevel(Skill.MELEE.id()) == 60,
          "no instant cleanse/statrestore");
      h.clock().advanceMillis(1);
      ClericTimedEffectRuntime.poisonCleanse(r);
      check(
          r.getSkills().getLevel(Skill.MELEE.id()) == 61
              && r.getSkills().getLevel(Skill.MAGIC.id()) == 90
              && r.getSkills().getLevel(Skill.HITS.id()) == hits,
          "restore one each drained nonHP, preserve boost andHP");
      check(ClericTimedEffectRuntime.defense(r, 100) == 115, "P6 Barkskin15 percent");
      Npc n = h.npc(3, 452, 450);
      cast(c, r, 20);
      check(
          ClericDirectCombatRuntime.maximum(r, n, 100) == 115, "maximum roll boost before defense");
      check(
          ClericDirectCombatRuntime.beforeDirectDamage(r, n, 100).getDamage() == 108,
          "dealt bonus after defense");
      check(
          ClericDirectCombatRuntime.maximum(r, c, 100) == 100
              && ClericDirectCombatRuntime.beforeDirectDamage(r, c, 100).getDamage() == 100,
          "PvP never boosted");
      skill(r, Skill.HITS, 490, 1000);
      int healed = ClericDirectCombatRuntime.applyDeferredRally(r, n, 100);
      check(
          healed == 10 && r.getSkills().getLevel(Skill.HITS.id()) == 500,
          "lifesteal while below half");
      check(
          ClericDirectCombatRuntime.applyDeferredRally(r, n, 100) == 0
              && registry(r).size(ClericEffectOrigins.validatorFor(r)) == 1,
          "at half only lifesteal gates, blessing remains");
      check(
          ClericDirectCombatRuntime.combineFervorRollChance(r, 0.2) == 0.2, "old accuracy retired");
      cast(c, r, 17);
      check(
          ClericDirectCombatRuntime.afterExistingLifesteal(n, r, 100).getThornsDamage() == 10,
          "Guthix reflection terminal damage");
      check(
          ClericDirectCombatRuntime.afterExistingLifesteal(n, r, 0).getThornsDamage() == 0,
          "no reflection on zero hit");
      r.setParty(null);
      check(
          registry(r).size(ClericEffectOrigins.validatorFor(r)) == 0,
          "party break clears composite");
      party(h, c, r);
      cast(c, r, 14);
      skill(r, Skill.HITS, 0, 1000);
      h.clock().advanceMillis(6000);
      check(
          ClericTimedEffectRuntime.getRespiteSpeedBonus(r) == 0
              && r.getSkills().getLevel(Skill.HITS.id()) == 0,
          "dead target never heals");
      clear(r);
      skill(r, Skill.HITS, 100, 1000);
      c.teleport(450, 450, false);
      r.teleport(453, 450, false);
      fund(c, ClericSpellCatalog.fromCode(1));
      packet(c, 1);
      check(r.getX() == 452 && count(c, 3300) == 98, "Unify radius3 actualone step cost2");
      r.teleport(455, 450, false);
      fund(c, ClericSpellCatalog.fromCode(21));
      packet(c, 21);
      check(r.getX() == 452 && count(c, 3300) == 94, "Greater radius5 three steps cost6");
      r.teleport(456, 450, false);
      fund(c, ClericSpellCatalog.fromCode(21));
      packet(c, 21);
      check(count(c, 3300) == 100, "outside range no useful target no cost");
      c.teleport(450, 450, false);
      r.teleport(451, 450, false);
      party(h, c, r);
      cast(c, r, 20);
      int totalBonus = 0;
      for (int i = 0; i < 13; i++)
        totalBonus += ClericDirectCombatRuntime.beforeDirectDamage(r, n, 1).getDamage() - 1;
      check(totalBonus == 1, "fractional dealt bonus carries small hits conservatively");
      skill(r, Skill.HITS, 100, 1000);
      int life = 0;
      for (int i = 0; i < 10; i++) life += ClericDirectCombatRuntime.applyDeferredRally(r, n, 1);
      check(life == 1, "fractional lifesteal independent of dealt carry");
      fund(c, ClericSpellCatalog.fromCode(20));
      packet(c, 20);
      check(
          ClericDirectCombatRuntime.beforeDirectDamage(r, n, 1).getDamage() == 1,
          "equal refresh cannot mint fractional points");
      cast(c, r, 14);
      r.teleportLayered(
          com.openrsc.server.model.world.coordinate.WorldLocation.global(
              new com.openrsc.server.model.world.coordinate.WorldCoordinate(451, 450, 2)),
          false);
      check(
          ClericTimedEffectRuntime.getRespiteSpeedBonus(r) == 0,
          "different signed level invalidates blessing");
      r.teleportLayered(
          com.openrsc.server.model.world.coordinate.WorldLocation.global(
              new com.openrsc.server.model.world.coordinate.WorldCoordinate(451, 450, 0)),
          false);
      cast(c, r, 14);
      h.server().getConfig().USES_PK_MODE = true;
      check(
          ClericTimedEffectRuntime.getRespiteSpeedBonus(r) == 0,
          "PvP transition disables existing blessing");
      h.server().getConfig().USES_PK_MODE = false;
      cast(c, r, 14);
      c.setLoggedIn(false);
      check(
          ClericTimedEffectRuntime.getRespiteSpeedBonus(r) == 0,
          "caster logout invalidates origin");
      c.setLoggedIn(true);
      clear(r);
      r.teleport(451, 450, false);
      fund(c, ClericSpellCatalog.fromCode(12));
      r.setParty(null);
      packet(c, 12);
      check(count(c, 3294) == 100, "no party target no resource cost");
      party(h, c, r);
      // Quantized native maximums must remain functional at low offense.
      for (int power = 1; power <= 2; power++)
        for (int maximum : new int[] {0, 1, 3, 7, 14, Integer.MAX_VALUE}) {
          int percent = power == 1 ? 5 : 7;
          int expected =
              maximum == 0
                  ? 0
                  : (int)
                      Math.min(
                          Integer.MAX_VALUE,
                          (long) maximum + ((long) maximum * percent + 99) / 100);
          check(
              ClericDirectCombatRuntime.addMaximumPercent(maximum, percent) == expected,
              "positive low-cap maximum rounds up, zero/overflow safe");
        }
      c.teleport(450, 450, false);
      r.teleport(451, 450, false);
      party(h, c, r);
      cast(c, r, 14);
      fund(c, ClericSpellCatalog.fromCode(17));
      packet(c, 17);
      fund(c, ClericSpellCatalog.fromCode(20));
      packet(c, 20);
      skill(r, Skill.PRAYER, 50, 99);
      int initialHits = r.getSkills().getLevel(Skill.HITS.id());
      for (int ticks = 0; ticks < 95; ticks++) h.advanceOneCombatTick();
      ClericTimedEffectRuntime.getRespiteSpeedBonus(r);
      check(
          r.getSkills().getLevel(Skill.HITS.id()) == initialHits + 60,
          "actual640ms scheduler settles exactly ten delayed pulses including60s endpoint");
      check(
          registry(r).size(ClericEffectOrigins.validatorFor(r)) == 0
              && r.getSkills().getLevel(Skill.PRAYER.id()) == 62,
          "all gods coexpire with Worship restoration and derived-stat reentry");
      clear(r);
      skill(r, Skill.PRAYER, 20, 99);
      int statsPackets = h.countOutgoingPackets(r, OpcodeOut.SEND_EQUIPMENT_STATS);
      r.getSkills().setLevel(Skill.PRAYER.id(), 45, true, true);
      check(
          h.countOutgoingPackets(r, OpcodeOut.SEND_EQUIPMENT_STATS) == statsPackets + 1
              && r.getCarriedItems().getEquipment().getHolyPower() == 45,
          "Worship change sends bounded derived HolyPower stats");
      Field packetsField = Player.class.getDeclaredField("outgoingPackets");
      packetsField.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<Packet> outgoing = (List<Packet>) packetsField.get(r);
      int statsOpcode =
          com.openrsc.server.net.rsc.ActionSender.getGenerator(r)
              .fromOpcodeEnum(OpcodeOut.SEND_EQUIPMENT_STATS, r)
              .getOpcode();
      Packet lastStats = null;
      for (Packet packet : outgoing) if (packet.getID() == statsOpcode) lastStats = packet;
      check(
          lastStats != null
              && lastStats.getBuffer().getInt(lastStats.getBuffer().writerIndex() - 4) == 45,
          "actual equipment packet contains combined HolyPower45");
      fund(r, ClericSpellCatalog.fromCode(14));
      packet(r, 14);
      check(
          ClericTimedEffectRuntime.blessing(c, ClericEffectFamily.SARADOMIN_BLESSING).getPower()
              == 3,
          "next cast resolves fresh Worship-derived rank");
      c.getTransientEffectState().clearAll();
      int[] beforeDefense = {r.getMeleeDefense(), r.getRangedDefense(), r.getMagicDefense()};
      cast(c, r, 17);
      int[] afterDefense = {r.getMeleeDefense(), r.getRangedDefense(), r.getMagicDefense()};
      for (int i = 0; i < 3; i++)
        check(
            afterDefense[i] == beforeDefense[i] + beforeDefense[i] * 15 / 100,
            "actual Barkskin all three defense values");
      com.openrsc.server.content.AntidoteCleansing.activate(r, 3405);
      r.applyPoison(100, 100);
      int poisonBefore = r.getCurrentPoisonPower();
      check(poisonBefore == 100, "applying Guthix never instantly removes poison");
      ((com.openrsc.server.event.rsc.impl.PoisonEvent) r.getAttribute("poisonEvent", null)).run();
      check(
          r.getCurrentPoisonPower() == 70, "actual poison pulse adds baseline3,antidote20,Guthix7");
      r.curePoison();
      Player crit = h.player("clericcrit", 451, 450);
      crit.setClientVersion(10054);
      crit.getClientLimitations().maxItemId = 65535;
      h.recordOutgoingPackets(crit);
      skill(crit, Skill.HITS, 100, 1000);
      party(h, c, crit);
      int cosmic =
          com.openrsc.server.content.EnchantingItemEffects.getWoolRobeProduct(
              com.openrsc.server.content.EnchantingItemEffects.COSMIC_ALTAR,
              com.openrsc.server.constants.ItemId.WOOL_WIZARD_HAT.id(),
              10);
      crit.getCarriedItems().getEquipment().getList()[0] = new Item(cosmic);
      check(crit.getCosmicRobeCritChance() > 0, "real cosmic crit source");
      h.random().scriptDoubles(0.0);
      int melee = com.openrsc.server.event.rsc.impl.combat.CombatFormula.doMeleeDamage(crit, n);
      h.random().scriptDoubles(0.0);
      int ranged =
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.doRangedDamage(
              crit, 11, 10, n, false);
      h.random().scriptDoubles(0.0);
      int magic =
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.calculateMagicDamage(crit, n, 10);
      h.random().scriptDoubles(0.0);
      int offhand =
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.doOffhandMeleeDamage(crit, n);
      h.random().scriptDoubles(0.0);
      int secondary =
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.calculateSecondaryMagicDamage(
              crit, n, 10);
      cast(c, crit, 20);
      h.random().scriptDoubles(0.0);
      check(
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.doMeleeDamage(crit, n)
              == ClericDirectCombatRuntime.addMaximumPercent(melee, 15),
          "actual melee crit uses boosted maximum");
      h.random().scriptDoubles(0.0);
      check(
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.doRangedDamage(
                  crit, 11, 10, n, false)
              == ClericDirectCombatRuntime.addMaximumPercent(ranged, 15),
          "actual ranged crit uses boosted maximum");
      int boostedMagic = ClericDirectCombatRuntime.addMaximumPercent(magic, 15);
      h.random().scriptDoubles(0.0);
      check(
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.calculateMagicDamage(crit, n, 10)
              == boostedMagic,
          "actual Magic crit uses boosted maximum");
      h.random().scriptDoubles(0.0);
      check(
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.calculateMagicDamage(
                  crit, n, 10, 0.5)
              == (int) Math.ceil(boostedMagic * 0.5),
          "capped Magic applies percent cap after boosted maximum");
      h.random().scriptDoubles(0.0);
      check(
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.doOffhandMeleeDamage(crit, n)
              == offhand,
          "offhand maximum excluded");
      h.random().scriptDoubles(0.0);
      check(
          com.openrsc.server.event.rsc.impl.combat.CombatFormula.calculateSecondaryMagicDamage(
                  crit, n, 10)
              == secondary,
          "secondary Magic maximum excluded");
      check(ClericDirectCombatRuntime.maximum(crit, n, 0) == 0, "zero maximum stayszero");
      crit.setParty(null);
      party(h, c, r);
      c.teleport(450, 450, false);
      r.teleport(451, 450, false);
      cast(c, r, 17);
      skill(r, Skill.HITS, 500, 1000);
      n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent event =
          new com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent(h.world(), n, r);
      Method inflict =
          com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent.class.getDeclaredMethod(
              "inflictDamage",
              com.openrsc.server.model.entity.Mob.class,
              com.openrsc.server.model.entity.Mob.class,
              int.class,
              boolean.class,
              boolean.class);
      inflict.setAccessible(true);
      inflict.invoke(event, n, r, 100, false, true);
      check(
          n.getLevel(Skill.HITS.id()) == 1000 && r.getLevel(Skill.HITS.id()) == 400,
          "actual offhand event deals damage without Thorns proc");
      skill(r, Skill.HITS, 500, 1000);
      inflict.invoke(event, n, r, 100, false, false);
      check(
          n.getLevel(Skill.HITS.id()) == 990 && r.getLevel(Skill.HITS.id()) == 400,
          "actual primary event applies one terminal reflection");
      cast(c, r, 17);
      for (ItemId item :
          new ItemId[] {
            ItemId.GOBLIN_HIDE_COIF,
            ItemId.GOBLIN_HIDE_GLOVES,
            ItemId.GOBLIN_HIDE_BOOTS,
            ItemId.GOBLIN_HIDE_CHAPS,
            ItemId.GOBLIN_HIDE_CUIRASS
          }) h.equip(r, item.id(), 1);
      n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      for (int hit = 0; hit < 10; hit++) {
        skill(r, Skill.HITS, 5, 1000);
        for (long seed = 0; seed < 100000; seed++)
          if (new java.util.Random(seed).nextDouble() < 0.05) {
            com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(seed);
            break;
          }
        inflict.invoke(event, n, r, 100, false, false);
        check(r.getLevel(Skill.HITS.id()) == 1, "actual survival event leaves oneHP");
      }
      check(
          n.getLevel(Skill.HITS.id()) == 996,
          "Thorns accumulates factual4HP damage per survival hit, not legacy5");
      java.util.Arrays.fill(r.getCarriedItems().getEquipment().getList(), null);
      cast(c, r, 17);
      skill(r, Skill.HITS, 5, 1000);
      r.setGroupID(com.openrsc.server.model.entity.player.Group.ADMIN);
      n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      inflict.invoke(event, n, r, 100, false, false);
      check(
          n.getLevel(Skill.HITS.id()) == 1000
              && registry(r).size(ClericEffectOrigins.validatorFor(r)) == 0,
          "actual lethal primary hit creates no new dead-recipient reflection");
      skill(r, Skill.HITS, 0, 1000);
      check(
          ClericDirectCombatRuntime.applyDeferredRally(r, n, 100) == 0
              && r.getLevel(Skill.HITS.id()) == 0,
          "dead player cannot heal through blessing lifesteal");
      ClericSpellbookStruct payload = new ClericSpellbookStruct();
      payload.setOpcode(OpcodeOut.SEND_CLERIC_SPELLBOOK);
      payload.schemaVersion = 3;
      payload.gameTickMilliseconds = h.server().getConfig().GAME_TICK;
      payload.definitions = ClericSpellCatalog.getAll().toArray(new ClericSpellDefinition[0]);
      Packet wire = new PayloadCustomGenerator().generate(payload, c);
      byte[] bytes = new byte[wire.getBuffer().readableBytes()];
      wire.getBuffer().getBytes(wire.getBuffer().readerIndex(), bytes);
      if (args.length > 0) Files.write(Paths.get(args[0]), bytes);
      check(bytes[0] == 3 && bytes[1] == 11, "actual generated schema3 eleven entries");
      System.out.println(
          "PASS "
              + checks
              + " active Cleric"
              + " catalog/packet/mixedcost/potency/composite/cadence/lifecycle/combat/Unify/wire"
              + " checks");
    }
  }
}
