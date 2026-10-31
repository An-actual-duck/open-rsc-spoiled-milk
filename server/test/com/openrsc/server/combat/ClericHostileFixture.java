package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.content.cleric.runtime.*;
import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator;
import com.openrsc.server.net.rsc.struct.outgoing.ClericSpellbookStruct;
import java.nio.file.*;

/** Native packet-to-NPC admission, weakness, phase and lifecycle fixtures. Offline only. */
public final class ClericHostileFixture {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static void cast(Player c, int code) throws Exception {
    ClericBlessingsFixture.fund(c, ClericSpellCatalog.fromCode(code));
    ClericBlessingsFixture.packet(c, code);
  }

  static DamageResult hit(
      Npc source,
      Npc target,
      int amount,
      CombatStyle style,
      String key,
      DamageRequest.SourceCategory category) {
    return target
        .getWorld()
        .getServer()
        .getResolvedDamageTransaction()
        .apply(
            DamageRequest.resolvedLegacy(source, target, category, key, amount)
                .style(style)
                .build());
  }

  public static void main(String[] args) throws Exception {
    try (CurrentCombatHarness h = new CurrentCombatHarness()) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      Player c = h.player("hostilecleric", 450, 450);
      c.setClientVersion(10055);
      c.getClientLimitations().maxItemId = 65535;
      h.recordOutgoingPackets(c);
      ClericBlessingsFixture.skill(c, Skill.HITS, 100, 100);
      ClericBlessingsFixture.skill(c, Skill.PRAYER, 160, 99);
      Npc target = h.npc(3, 451, 450), source = h.npc(3, 452, 450);
      target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      source.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      check(
          ClericSpellCatalog.getAll().size() == 17,
          "17 active spells and no Zam hostile activation");
      for (int tier = 1; tier <= 3; tier++)
        for (int power = 1; power <= tier * 2; power++) {
          ClericHostileMagnitude g =
              new ClericHostileMagnitude(ClericAlignment.GUTHIX, tier, power);
          ClericHostileMagnitude s =
              new ClericHostileMagnitude(ClericAlignment.SARADOMIN, tier, power);
          check(g.poisonWeakness() == 10 + 5 * (power - 1), "Guthix weakness curve");
          check(g.summonWeakness() == (tier >= 2 ? 10 + 5 * (power - 1) : 0), "summon inheritance");
          check(g.recoilWeakness() == (tier == 3 ? 10 + 5 * (power - 1) : 0), "recoil inheritance");
          check(s.magicDefenseReduction() == 10 + 3 * (power - 1), "Magic allergy curve");
          check(
              s.maximumMeleeReduction() == (tier >= 2 ? 5 + 2 * (power - 1) : 0), "atrophy curve");
          check(
              s.phaseChance() == (tier == 3 ? 5 + 2 * (power - 1) : 0)
                  && s.phaseTicks() == (power <= 3 ? 1 : power <= 5 ? 2 : 3),
              "phase chance and game ticks");
        }
      cast(c, 24);
      check(target.isChasing()||target.inCombat(),"successful hostile cast uses existing NPC retaliation");
      check(
          ClericHostileRuntime.effect(target, ClericEffectFamily.GUTHIX_HOSTILE).getPower() == 6,
          "real hostile packet applies without party or existing combat");
      for (ClericResourceCost cost : ClericSpellCatalog.fromCode(24).getResourceCosts())
        check(
            ClericBlessingsFixture.count(c, cost.getItemId()) == 100 - cost.getCount(),
            "one flat God plus Blight cost");
      check(
          hit(
                      source,
                      target,
                      10,
                      CombatStyle.MELEE,
                      "generic-poison",
                      DamageRequest.SourceCategory.DOT)
                  .getActualDamage()
              == 14,
          "poison damage ceil35percent");
      check(
          ClericHostileRuntime.poisonCleanse(target, 3) == 1,
          "positive cleanse reduced by rounded-up35percent");
      check(
          hit(
                      source,
                      target,
                      10,
                      CombatStyle.MELEE,
                      SecondaryEffectPolicy.PVM_MELEE_CLERIC_THORNS.getStableKey(),
                      DamageRequest.SourceCategory.OWNED_EFFECT)
                  .getActualDamage()
              == 13,
          "reflection weakness uses fractional carry");
      check(
          hit(
                      source,
                      target,
                      10,
                      CombatStyle.MELEE,
                      "plain-direct",
                      DamageRequest.SourceCategory.ACTOR)
                  .getActualDamage()
              == 10,
          "ordinary hit not a weakness family");
      cast(c, 22);
      check(
          ClericHostileRuntime.effect(target, ClericEffectFamily.GUTHIX_HOSTILE).getTier() == 3,
          "lower tier cannot downgrade");
      int magic = target.getMagicDefense();
      cast(c, 27);
      check(
          target.getMagicDefense() == Math.max(0, magic - (magic * 25 + 99) / 100),
          "actual Magic defense reduced");
      for(int maximum:new int[]{0,1,2,3,7,14})check(ClericHostileRuntime.reduceMaximumMelee(target,maximum)==(maximum==0?0:Math.max(1,maximum-(maximum*15+99)/100)),"atrophy low maxima remain positive");
      check(
          ClericDirectCombatRuntime.maximum(target, source, 100) == 85,
          "NPC max melee reduced15percent");
      check(
          ClericDirectCombatRuntime.maximum(target, source, 100, CombatStyle.MAGIC) == 100,
          "Magic max untouched");
      Object owner = new Object();
      check(NonCorporealState.tryEnter(target, owner, 100, 3, () -> true), "reusable phase entry");
      check(NonCorporealState.isActive(target), "phase active immediately");
      int before = target.getLevel(Skill.HITS.id());
      DamageResult blocked =
          hit(
              source,
              target,
              10,
              CombatStyle.RANGED,
              "plain-ranged",
              DamageRequest.SourceCategory.ACTOR);
      check(
          blocked.isNonCorporealBlocked()
              && blocked.isAttackSuppressed()
              && blocked.getActualDamage() == 0
              && blocked.getLegacyDamageDealt() == 0,
          "physical phase block distinct factual and legacyzero");
      check(target.getLevel(Skill.HITS.id()) == before, "physical phase block keepsHP");
      check(
          hit(
                      source,
                      target,
                      10,
                      CombatStyle.MAGIC,
                      "plain-magic",
                      DamageRequest.SourceCategory.ACTOR)
                  .getActualDamage()
              == 10,
          "magical combat passes");
      check(
          hit(
                  target,
                  source,
                  10,
                  CombatStyle.MELEE,
                  "outgoing-melee",
                  DamageRequest.SourceCategory.ACTOR)
              .isNonCorporealBlocked(),
          "outgoing physical blocked too");
      check(
          hit(
                      target,
                      source,
                      10,
                      CombatStyle.MAGIC,
                      "outgoing-magic",
                      DamageRequest.SourceCategory.ACTOR)
                  .getActualDamage()
              == 10,
          "outgoing Magic passes");
      check(
          hit(source, target, 10, null, "environment", DamageRequest.SourceCategory.ENVIRONMENT)
                  .getActualDamage()
              == 10,
          "genuine environment unaffected");
      check(
          hit(source, target, 10, null, "quest-script", DamageRequest.SourceCategory.SCRIPT)
                  .getActualDamage()
              == 10,
          "genuine quest script unaffected");
      check(
          hit(source, target, 10, null, "generic-poison", DamageRequest.SourceCategory.DOT)
              .isNonCorporealBlocked(),
          "ordinary poison blocked regardless application style");
      target.applyPoison(100, 100);
      int poison = target.getCurrentPoisonPower();
      before = target.getLevel(Skill.HITS.id());
      ((PoisonEvent) target.getAttribute("poisonEvent", null)).run();
      check(
          target.getCurrentPoisonPower() == poison - 1
              && target.getLevel(Skill.HITS.id()) == before,
          "poison pulse cleanses on schedule with no phased damage");
      target.curePoison();
      for (int i = 0; i < 3; i++) h.advanceOneCombatTick();
      check(!NonCorporealState.isActive(target), "phase expires in game ticks");
      check(
          !NonCorporealState.tryEnter(target, owner, 100, 1, () -> true),
          "expiry alone cannot rearm");
      AttackState.stagger(target, 1);
      check(
          AttackState.commitAttack(target).isSuppressed(),
          "corporeal staggered attempt remains forcedzero");
      check(
          !NonCorporealState.tryEnter(target, owner, 100, 1, () -> true),
          "no rephase during same-tick settlement");
      h.advanceOneCombatTick();
      check(
          NonCorporealState.tryEnter(target, owner, 100, 1, () -> true)
              || NonCorporealState.isActive(target),
          "actual tangible attempt rearms on later tick while Stagger owns own recovery");
      check(
          AttackState.recoveryAttacks(target) == 5,
          "phase rearm does not advance Stagger recovery");
      NonCorporealState.clearOwned(target, owner);
      check(!NonCorporealState.isActive(target), "owned phase clear");
      Object retainedState=target.getAttribute("cleric_hostile_state",null);
      c.setLoggedIn(false);
      check(
          ClericHostileRuntime.effect(target, ClericEffectFamily.GUTHIX_HOSTILE) == null,
          "caster logout clears hostile origin");
      h.advanceOneCombatTick();
      check(target.getAttribute("cleric_hostile_state",null)==null,"final invalid caster removes NPC hostile authority");
      java.lang.reflect.Field origins=retainedState.getClass().getDeclaredField("origins");origins.setAccessible(true);
      check(((java.util.Map<?,?>)origins.get(retainedState)).isEmpty(),"expired state retains no caster references");
      c.setLoggedIn(true);
      c.setClientVersion(10055);
      // A separate solo area makes all no-target outcomes observable at the real packet resource boundary.
      Player solo=h.player("admissioncleric",490,490);solo.setClientVersion(10055);solo.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(solo);
      ClericBlessingsFixture.skill(solo,Skill.HITS,100,100);ClericBlessingsFixture.skill(solo,Skill.PRAYER,160,99);
      Npc admission=h.npc(3,491,490);
      admission.teleport(com.openrsc.server.model.world.coordinate.WorldLocation.global(new com.openrsc.server.model.world.coordinate.WorldCoordinate(491,490,1)));
      cast(solo,22);
      check(ClericBlessingsFixture.count(solo,3427)==100,"other signed level is not charged or afflicted");
      admission.teleport(com.openrsc.server.model.world.coordinate.WorldLocation.global(new com.openrsc.server.model.world.coordinate.WorldCoordinate(491,490,0)));
      admission.setAttribute("myworld_summon_owner",123L);
      cast(solo,22);check(ClericBlessingsFixture.count(solo,3427)==100,"summon exclusion keeps resources");
      admission.removeAttribute("myworld_summon_owner");
      cast(solo,24);check(ClericBlessingsFixture.count(solo,3429)==98,"solo eligible NPC succeeds");
      cast(solo,22);check(ClericBlessingsFixture.count(solo,3427)==100,"all weaker no-op recipients do not spend");
      admission.teleport(500,500);
      Npc banker=h.npc(95,491,490);check(!banker.getDef().isAttackable(),"ordinary quest/service NPC fixture unattackable");
      cast(solo,22);check(ClericBlessingsFixture.count(solo,3427)==100&&!banker.isChasing()&&!banker.inCombat(),"unattackable service NPC excluded without retaliation");
      solo.setClientVersion(10054);admission.teleport(491,491);
      cast(solo,27);check(ClericBlessingsFixture.count(solo,3429)==100,"old schema3 client cannot activate hostile casts");
      ClericSpellbookStruct payload = new ClericSpellbookStruct();
      payload.setOpcode(OpcodeOut.SEND_CLERIC_SPELLBOOK);
      payload.schemaVersion = 4;
      payload.gameTickMilliseconds = h.server().getConfig().GAME_TICK;
      payload.definitions = ClericSpellCatalog.getAll().toArray(new ClericSpellDefinition[0]);
      Packet wire = new PayloadCustomGenerator().generate(payload, c);
      byte[] bytes = new byte[wire.getBuffer().readableBytes()];
      wire.getBuffer().getBytes(wire.getBuffer().readerIndex(), bytes);
      if (args.length > 0) Files.write(Paths.get(args[0]), bytes);
      System.out.println("PASS " + checks + " Cleric hostile and phase checks");
    }
  }
}
