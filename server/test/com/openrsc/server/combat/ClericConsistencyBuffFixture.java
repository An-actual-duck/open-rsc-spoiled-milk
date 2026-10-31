package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.content.cleric.runtime.*;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator;
import com.openrsc.server.net.rsc.struct.outgoing.ClericSpellbookStruct;
import java.lang.reflect.Method;
import java.nio.file.*;

/**
 * Offline native packet, loaded definition, damage-event and metadata checks for the buff
 * migration.
 */
public final class ClericConsistencyBuffFixture {
  private static int checks;

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    try (CurrentCombatHarness h = new CurrentCombatHarness()) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      Player caster = h.player("consistencycaster", 450, 450);
      Player ally = h.player("consistencyally", 451, 450);
      for (Player p : new Player[] {caster, ally}) {
        p.setClientVersion(10055);
        p.getClientLimitations().maxItemId = 65535;
        h.recordOutgoingPackets(p);
        ClericBlessingsFixture.skill(p, Skill.HITS, 100, 1000);
      }
      ClericBlessingsFixture.party(h, caster, ally);
      ClericBlessingsFixture.skill(caster, Skill.PRAYER, 160, 99);
      check(
          ClericSpellCatalog.fromCode(12).getDisplayName().equals("Saradomin's Light"),
          "Light name");
      String[] approvedFlavor = {
        "Your attacks carry a holy light that burns the restless dead.",
        "You feel your blood rush and your spirits lift",
        "Your wounds seal up as soon as they are opened",
        "Your blood feels thick in your veins like sap. Your ailments slow to a halt",
        "Your skin grows a thick layer of bark",
        "Sharp spikes protrude from you, piercing your foes.",
        "Your muscles bulge and your mind feels sharper",
        "Your enemies seem much smaller than before",
        "Your weapon will breathe new life into you should you feel weak"
      };
      java.lang.reflect.Field outgoing = Player.class.getDeclaredField("outgoingPackets");
      outgoing.setAccessible(true);
      for (int code = 12; code <= 20; code++) {
        check(
            ClericSpellCatalog.fromCode(code)
                .getRecipientFlavor()
                .equals(approvedFlavor[code - 12]),
            "exact approved receiver flavor metadata");
        java.util.List<Packet> messages = (java.util.List<Packet>) outgoing.get(ally);
        int before = messages.size();
        ClericBlessingsFixture.cast(caster, ally, code);
        boolean received = false;
        for (int index = before; index < messages.size(); index++) {
          Packet message = messages.get(index);
          byte[] bytes = new byte[message.getBuffer().readableBytes()];
          message.getBuffer().getBytes(message.getBuffer().readerIndex(), bytes);
          if (new String(bytes, "ISO-8859-1").contains(approvedFlavor[code - 12])) received = true;
        }
        check(received, "actual receiver message for blessing" + code);
      }
      for (int tier = 1; tier <= 3; tier++)
        for (int power = 1; power <= tier * 2; power++) {
          ClericBlessingMagnitude m =
              new ClericBlessingMagnitude(ClericAlignment.SARADOMIN, tier, power);
          check(m.undeadDamagePercent() == 25 + 10 * (power - 1), "Hallowed curve");
          check(
              m.regenerationPercent() == (tier >= 2 ? 200 + 50 * (power - 1) : 100),
              "regen starts tier2");
          check(m.healingPerPulse() == (tier == 3 ? power : 0), "HoT starts tier3");
        }
      Npc undead = h.npc(41, 452, 450),
          ordinary = h.npc(3, 452, 451),
          farmer = h.npc(319, 452, 452);
      check(UndeadClassification.legacy(319), "legacy Farmer identity preserved independently");
      check(!UndeadClassification.isUndead(farmer), "loaded explicit Farmer exclusion");
      check(
          UndeadClassification.isUndead(undead) && !UndeadClassification.isUndead(ordinary),
          "legacy undead and ordinary defaults");
      com.openrsc.server.external.NPCDef legacy = undead.getDef();
      Boolean original = legacy.undead;
      legacy.undead = Boolean.FALSE;
      check(
          !UndeadClassification.isUndead(undead),
          "explicit false overrides legacy constructor attribute");
      check(
          Boolean.FALSE.equals(new com.openrsc.server.external.NPCDef(legacy).undead),
          "definition copy retains explicit false");
      legacy.undead = original;
      check(
          new com.openrsc.server.external.NPCDef().undead == null,
          "missing tag is a nullable legacy default");
      check(
          Boolean.TRUE.equals(
              new com.openrsc.server.external.NPCDef.NPCDefinitionBuilder(3, "fixture")
                  .undead(Boolean.TRUE)
                  .build()
                  .undead),
          "builder retains explicit true");
      for (int id : new int[] {359, 498, 613, 614, 615, 865})
        check(UndeadClassification.isUndead(h.npc(id, 453, 450)), "authored extra undead " + id);
      check(
          !UndeadClassification.isUndead(h.npc(768, 453, 451)), "ambiguous Death Wing not guessed");
      ClericBlessingsFixture.cast(caster, ally, 12);
      int total = 0;
      for (int i = 0; i < 20; i++)
        total +=
            ClericDirectCombatRuntime.beforeDirectDamage(ally, undead, 1, CombatStyle.RANGED)
                .getDamage();
      check(total == 27, "P2 Hallowed fractional carry 20 damage plus7");
      check(
          ClericDirectCombatRuntime.beforeDirectDamage(ally, ordinary, 100, CombatStyle.MELEE)
                  .getDamage()
              == 100,
          "ordinary target unchanged");
      check(
          ClericDirectCombatRuntime.beforeDirectDamage(ally, undead, 0, CombatStyle.MAGIC)
                  .getDamage()
              == 0,
          "zero never gains damage");
      check(ClericTimedEffectRuntime.getRespiteSpeedBonus(ally) == 0, "Light no natural regen");
      ClericBlessingsFixture.cast(caster, ally, 13);
      check(ClericTimedEffectRuntime.getRespiteSpeedBonus(ally) > 0, "Aura grants regen");
      int hp = ally.getLevel(Skill.HITS.id());
      for (int i = 0; i < 10; i++) h.advanceOneCombatTick();
      check(ally.getLevel(Skill.HITS.id()) == hp, "Aura does not pulse heal");
      ClericBlessingsFixture.cast(caster, ally, 14);
      hp = ally.getLevel(Skill.HITS.id());
      for (int i = 0; i < 95; i++) h.advanceOneCombatTick();
      ClericTimedEffectRuntime.getRespiteSpeedBonus(ally);
      check(
          ally.getLevel(Skill.HITS.id()) == hp + 60,
          "Resplendence ten delayed pulses including endpoint");
      ClericBlessingsFixture.cast(caster, ally, 17);
      check(
          ClericTimedEffectRuntime.defense(ally, 100, CombatStyle.RANGED) == 115,
          "Barkskin ranged defense");
      check(
          ClericTimedEffectRuntime.defense(ally, 100, CombatStyle.MELEE) == 100,
          "Barkskin no melee defense");
      check(
          ClericTimedEffectRuntime.defense(ally, 100, CombatStyle.MAGIC) == 100,
          "Barkskin no magic defense");
      ClericBlessingsFixture.cast(caster, ally, 20);
      for (CombatStyle style :
          new CombatStyle[] {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC}) {
        check(
            ClericDirectCombatRuntime.maximum(ally, ordinary, 100, style)
                == (style == CombatStyle.MELEE ? 115 : 100),
            "Zam maximum style " + style);
        check(
            ClericDirectCombatRuntime.beforeDirectDamage(ally, ordinary, 100, style).getDamage()
                == (style == CombatStyle.MELEE ? 108 : 100),
            "Zam dealt style " + style);
      }
      // Real melee settlement must carry the same holy target classification and stacking order.
      ClericBlessingsFixture.fund(caster, ClericSpellCatalog.fromCode(14));
      ClericBlessingsFixture.packet(caster, 14);
      undead.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      Method inflict =
          com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent.class.getDeclaredMethod(
              "inflictDamage",
              com.openrsc.server.model.entity.Mob.class,
              com.openrsc.server.model.entity.Mob.class,
              int.class,
              boolean.class,
              boolean.class);
      inflict.setAccessible(true);
      Object event =
          new com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent(h.world(), ally, undead);
      inflict.invoke(event, ally, undead, 100, false, false);
      check(
          undead.getLevel(Skill.HITS.id()) == 811,
          "native melee 100 then Bloodrage8 then Hallowed75 yields189");
      inflict.invoke(event, ally, undead, 100, false, true);
      check(
          undead.getLevel(Skill.HITS.id()) == 711, "native offhand excludes both direct blessings");
      ClericSpellbookStruct payload = new ClericSpellbookStruct();
      payload.setOpcode(OpcodeOut.SEND_CLERIC_SPELLBOOK);
      payload.schemaVersion = ClericBlessingContract.SCHEMA_VERSION;
      payload.gameTickMilliseconds = h.server().getConfig().GAME_TICK;
      payload.definitions = ClericSpellCatalog.getAll().toArray(new ClericSpellDefinition[0]);
      Packet wire = new PayloadCustomGenerator().generate(payload, caster);
      byte[] bytes = new byte[wire.getBuffer().readableBytes()];
      wire.getBuffer().getBytes(wire.getBuffer().readerIndex(), bytes);
      check(bytes[0] == 4 && bytes[1] == 17, "actual schema4 native packet");
      if (args.length > 0) Files.write(Paths.get(args[0]), bytes);
      System.out.println("PASS " + checks + " Cleric consistency buff checks");
    }
  }
}
