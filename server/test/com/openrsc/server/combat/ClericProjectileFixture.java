package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.util.*;

/**
 * Real bow, thrown and Magic packet producers; immutable launch rolls survive settlement-time
 * phase.
 */
public final class ClericProjectileFixture {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static ProjectileEvent launch(CurrentCombatHarness h, Player p, Npc npc, int style)
      throws Exception {
    SharedAttackFoundationFixture.advance(h, 12);
    h.random().scriptDoubles(0.0);
    if (style == 0) {
      RangeEvent event = new RangeEvent(h.world(), p, 0, npc);
      p.setRangeEvent(event);
      event.run();
    } else if (style == 1) {
      ThrowingEvent event = new ThrowingEvent(h.world(), p, 0, npc);
      p.setThrowingEvent(event);
      event.run();
    } else {
      for (Map.Entry<Integer, Integer> rune :
          h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE).getRunesRequired())
        p.getCarriedItems().getInventory().add(new Item(rune.getKey(), 100));
      SharedAttackFoundationFixture.magic(h, p, npc);
    }
    return SharedAttackFoundationFixture.lastLaunch(h, p);
  }

  static DamageResult settle(
      CurrentCombatHarness h, ProjectileEvent event, List<DamageResult> results) {
    int before = results.size();
    event.action();
    event.stop();
    h.server().getGameEventHandler().cleanupEvents();
    for (int i = before; i < results.size(); i++)
      if (results.get(i).getRequest().getSourceCategory() == DamageRequest.SourceCategory.ACTOR)
        return results.get(i);
    throw new AssertionError("real projectile did not settle");
  }

  public static void main(String[] args) throws Exception {
    List<DamageResult> results = new ArrayList<>();
    try (CurrentCombatHarness h = new CurrentCombatHarness(results::add)) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      int cosmic =
          com.openrsc.server.content.EnchantingItemEffects.getWoolRobeProduct(
              com.openrsc.server.content.EnchantingItemEffects.COSMIC_ALTAR,
              ItemId.WOOL_WIZARD_HAT.id(),
              10);
      Player c = h.player("projectilecleric", 450, 449);
      c.setClientVersion(10055);
      c.getClientLimitations().maxItemId = 65535;
      ClericBlessingsFixture.skill(c, Skill.HITS, 1000, 1000);
      ClericBlessingsFixture.skill(c, Skill.PRAYER, 160, 99);
      h.recordOutgoingPackets(c);
      for (int style = 0; style < 3; style++) {
        Player p = h.player("holylaunch" + style, 450, 450);
        p.setClientVersion(10055);
        p.getClientLimitations().maxItemId = 65535;
        h.recordOutgoingPackets(p);
        ClericBlessingsFixture.skill(p, Skill.HITS, 1000, 1000);
        ClericBlessingsFixture.skill(p, Skill.RANGED, 99, 99);
        ClericBlessingsFixture.skill(p, Skill.MAGIC, 99, 99);
        Npc npc = h.npc(41, 452, 450);
        SharedAttackFoundationFixture.healthy(npc);
        h.equip(p, cosmic, 1);
        if (style == 0) {
          h.equip(p, ItemId.LONGBOW.id(), 1);
          h.equip(p, ItemId.TIN_ARROWS.id(), 100);
        }
        if (style == 1) h.equip(p, ItemId.TIN_SHURIKEN.id(), 100);
        if (style == 2)
          for (Map.Entry<Integer, Integer> rune :
              h.server().getEntityHandler().getSpellDef(Spells.EARTH_STRIKE).getRunesRequired())
            p.getCarriedItems().getInventory().add(new Item(rune.getKey(), 100));
        ClericBlessingsFixture.party(h, c, p);
        DamageResult baseline = settle(h, launch(h, p, npc, style), results);
        check(
            baseline.getActualDamage() > 0 && baseline.isCritical(),
            "positive real critical baseline style" + style);
        ClericBlessingsFixture.cast(c, p, 14);
        DamageResult blessed = settle(h, launch(h, p, npc, style), results);
        check(
            blessed.getActualDamage()
                == baseline.getActualDamage() + baseline.getActualDamage() * 75 / 100,
            "actual Hallowed impact style" + style);
        check(blessed.isCritical(), "Hallowed retains explicit critical identity");
        ProjectileEvent inFlight = launch(h, p, npc, style);
        int recovery = AttackState.recoveryAttacks(p);
        Object owner = new Object();
        check(NonCorporealState.tryEnter(npc, owner, 100, 3, () -> true), "phase after launch");
        DamageResult phased = settle(h, inFlight, results);
        check(
            style == 2
                ? phased.getActualDamage() > 0
                : phased.isNonCorporealBlocked()
                    && phased.getActualDamage() == 0
                    && phased.getLegacyDamageDealt() == 0,
            "settlement-time phase preserves Magic exception style" + style);
        check(AttackState.recoveryAttacks(p) == recovery, "impact does not commit another attack");
        check(
            inFlight.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),
            "phase never mutates launch roll");
        NonCorporealState.clearOwned(npc, owner);
        DamageRequest secondary =
            DamageRequest.resolvedLegacy(
                    p, npc, DamageRequest.SourceCategory.OWNED_EFFECT, "secondary-magic-proof", 10)
                .style(CombatStyle.MAGIC)
                .build();
        check(
            h.server().getResolvedDamageTransaction().apply(secondary).getActualDamage() == 10,
            "secondary does not inherit Hallowed");
        p.setParty(null);
      }
      // A physically missed root is not a performed-contact Ogre hit, but independent elemental magic still runs.
      Player melee=h.player("phasecontact",470,450);Npc phased=h.npc(3,471,450);
      SharedAttackFoundationFixture.healthy(melee);SharedAttackFoundationFixture.healthy(phased);
      ClericBlessingsFixture.skill(melee,Skill.MELEE,99,99);
      for(int id=1880;id<=1884;id++)h.equip(melee,id,1);
      h.equip(melee,ItemId.FIRE_SWORD.id(),1);
      Object phaseOwner=new Object();NonCorporealState.tryEnter(phased,phaseOwner,100,20,()->true);
      int before=results.size();melee.startCombat(phased);
      h.random().scriptDoubles(0.0,0.0,0.0,0.0).scriptInts(1,1,1,1,1,1);
      melee.getPvmMeleeEvent().run();
      boolean blockedRoot=false,allowedMagic=false;
      for(int i=before;i<results.size();i++) {
        DamageResult result=results.get(i);
        if(result.getRequest().getSource()==melee&&result.getRequest().getTarget()==phased) {
          if(result.getRequest().getSourceCategory()==DamageRequest.SourceCategory.ACTOR)blockedRoot|=result.isNonCorporealBlocked();
          if(result.getRequest().getHarmNature()==CombatHarmNature.MAGICAL&&result.getActualDamage()>0)allowedMagic=true;
        }
      }
      check(blockedRoot&&allowedMagic,"actual physical root blocked while independent elemental Magic still damages");
      check(AttackState.pendingStaggers(phased)==0,"blocked physical root does not manufacture Ogre contact");
      NonCorporealState.clearOwned(phased,phaseOwner);melee.resetCombatEvent();
      Player cleaver=h.player("phasecleaver",480,450);Npc primary=h.npc(3,481,450),neighbor=h.npc(3,481,451);
      SharedAttackFoundationFixture.healthy(cleaver);SharedAttackFoundationFixture.healthy(primary);SharedAttackFoundationFixture.healthy(neighbor);
      ClericBlessingsFixture.skill(cleaver,Skill.MELEE,99,99);h.equip(cleaver,cosmic,1);h.equip(cleaver,ItemId.TIN_SCYTHE.id(),1);
      NonCorporealState.tryEnter(primary,phaseOwner,100,20,()->true);
      cleaver.startCombat(primary);h.random().scriptDoubles(0.0,0.0,0.0,0.0);cleaver.getPvmMeleeEvent().run();
      check(primary.getLevel(Skill.HITS.id())==1000&&neighbor.getLevel(Skill.HITS.id())<1000,"independent cleave still strikes tangible neighbor of phased primary");
      cleaver.resetCombatEvent();
      Npc poisoner=h.npc(3,493,491);Player poisoned=h.player("phasepoisoned",492,491);
      SharedAttackFoundationFixture.healthy(poisoner);SharedAttackFoundationFixture.healthy(poisoned);poisoned.setClientVersion(10055);h.recordOutgoingPackets(poisoned);
      NonCorporealState.tryEnter(poisoner,phaseOwner,100,3,()->true);poisoned.applyPoison(100,100,poisoner);
      ((com.openrsc.server.event.rsc.impl.PoisonEvent)poisoned.getAttribute("poisonEvent",null)).run();
      DamageResult poisonResult=results.get(results.size()-1);
      check(poisonResult.isNonCorporealBlocked()&&poisonResult.getRequest().getPeriodicProvenance()!=null,"blocked poison retains durable source/lifetime provenance");
      check(poisoned.getLevel(Skill.HITS.id())==1000&&poisoned.getCurrentPoisonPower()==97,"outgoing phased poison cleanses without damage");
      java.lang.reflect.Field outgoing=Player.class.getDeclaredField("outgoingPackets");outgoing.setAccessible(true);
      boolean factualMessage=false;
      for(Object value:(java.util.List<?>)outgoing.get(poisoned)) {
        com.openrsc.server.net.Packet message=(com.openrsc.server.net.Packet)value;
        byte[] bytes=new byte[message.getBuffer().readableBytes()];message.getBuffer().getBytes(message.getBuffer().readerIndex(),bytes);
        if(new String(bytes,"ISO-8859-1").contains("lose @gr2@0"))factualMessage=true;
      }
      check(factualMessage,"blocked poison chat reports actual zero health loss");
      System.out.println("PASS " + checks + " real Cleric projectile and phase assertions");
    }
  }
}
