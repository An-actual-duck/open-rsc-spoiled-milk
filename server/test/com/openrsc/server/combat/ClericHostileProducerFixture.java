package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.*;
import java.util.*;

/** Actual summon launch, charged Frostbite and Cleric reflection with real hostile packet casts. */
public final class ClericHostileProducerFixture {
  static int checks;

  static void check(boolean v, String message) {
    checks++;
    if (!v) throw new AssertionError(message);
  }

  static int contribution(Npc npc, Player p, String method) throws Exception {
    Method get = Npc.class.getDeclaredMethod(method, java.util.UUID.class);
    get.setAccessible(true);
    return ((org.apache.commons.lang3.tuple.Pair<Integer, Long>) get.invoke(npc, p.getUUID()))
        .getLeft();
  }

  static void curse(Player c, Npc npc) throws Exception {
    ClericBlessingsFixture.fund(c, ClericSpellCatalog.fromCode(24));
    ClericBlessingsFixture.packet(c, 24);
    check(
        com.openrsc.server.content.cleric.runtime.ClericHostileRuntime.effect(
                npc, com.openrsc.server.content.cleric.effect.ClericEffectFamily.GUTHIX_HOSTILE)
            != null,
        "real Guthix hostile cast");
  }

  public static void main(String[] args) throws Exception {
    List<DamageResult> results = new ArrayList<>();
    try (CurrentCombatHarness h = new CurrentCombatHarness(results::add)) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      Player c = h.player("weaknessowner", 450, 450);
      c.setClientVersion(com.openrsc.server.net.rsc.ClericBlessingContract.CLIENT_VERSION);
      c.getClientLimitations().maxItemId = 65535;
      h.recordOutgoingPackets(c);
      SharedAttackFoundationFixture.healthy(c);
      ClericBlessingsFixture.skill(c, Skill.PRAYER, 160, 99);
      Npc target = h.npc(NpcId.GREATER_DEMON.id(), 451, 450);
      SharedAttackFoundationFixture.healthy(target);
      Npc summon = h.npc(3, 450, 451);
      SharedAttackFoundationFixture.healthy(summon);
      summon.relatedMob = c;
      summon.setAttribute("myworld_summon_owner", c.getUsernameHash());
      summon.setAttribute("myworld_summon_max_hit", 10);
      curse(c, target);
      Method summonAttack =
          Summoning.class.getDeclaredMethod(
              "trySummonProjectileAttack", Player.class, Npc.class, Mob.class);
      summonAttack.setAccessible(true);
      for (String kind : new String[] {"ghost", "otherworldly_being"}) {
        summon.setAttribute("myworld_summon_kind", kind);
        summon.setAttribute("myworld_summon_next_attack_tick", 0L);
        com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(42);
        int beforeCredit = contribution(target, c, "getSummonDamageInfoBy"),
            beforeXp = c.getSkills().getExperience(Skill.SUMMONING.id());
        check(
            (Boolean) summonAttack.invoke(null, c, summon, target),
            "actual summon producer admits");
        ProjectileEvent event = SharedAttackFoundationFixture.lastLaunch(h, summon);
        int raw = event.getLaunchSnapshot().getSpecification().getProposedDamage();
        check(raw > 0, "positive summon roll");
        DamageResult result = ClericProjectileFixture.settle(h, event, results);
        check(
            result.getActualDamage() >= raw
                && result.getActualDamage() <= raw + (raw * 35 + 99) / 100,
            "summon weakness one bounded carry multiplier");
        check(
            contribution(target, c, "getSummonDamageInfoBy") - beforeCredit
                == result.getLegacyDamageDealt(),
            "summon owner credited settled amplified amount exactlyonce");
        check(
            c.getSkills().getExperience(Skill.SUMMONING.id()) == beforeXp,
            "weakness does not invent on-hit summoning XP");
      }
      Player reflector = h.player("weakreflector", 450, 449);
      reflector.setClientVersion(com.openrsc.server.net.rsc.ClericBlessingContract.CLIENT_VERSION);
      reflector.getClientLimitations().maxItemId = 65535;
      h.recordOutgoingPackets(reflector);
      SharedAttackFoundationFixture.healthy(reflector);
      ClericBlessingsFixture.party(h, c, reflector);
      ClericBlessingsFixture.cast(c, reflector, 17);
      SharedAttackFoundationFixture.healthy(reflector);
      PvmMeleeEvent melee = new PvmMeleeEvent(h.world(), target, reflector);
      Method inflict =
          PvmMeleeEvent.class.getDeclaredMethod(
              "inflictDamage", Mob.class, Mob.class, int.class, boolean.class, boolean.class);
      inflict.setAccessible(true);
      int before = target.getLevel(Skill.HITS.id()),
          beforeCredit = contribution(target, reflector, "getCombatDamageInfoBy");
      inflict.invoke(melee, target, reflector, 100, false, false);
      check(
          before - target.getLevel(Skill.HITS.id()) == 13,
          "real Thorns producer amplifies10 to13 with carry");
      check(
          contribution(target, reflector, "getCombatDamageInfoBy") - beforeCredit == 13,
          "Thorns contribution uses boosted settlement");
      Method frost =
          PvmMeleeEvent.class.getDeclaredMethod(
              "applyFrostbiteReflection", Mob.class, Mob.class, int.class);
      frost.setAccessible(true);
      target.applyFrostbiteDebuff(reflector);
      before = target.getLevel(Skill.HITS.id());
      int beforeMagic = contribution(target, reflector, "getMageDamageInfoBy");
      int remaining = (Integer) frost.invoke(melee, target, reflector, 20);
      check(
          remaining == 10 && before - target.getLevel(Skill.HITS.id()) == 14,
          "charged Frostbite preserves root split and independent fractional weakness");
      check(
          contribution(target, reflector, "getMageDamageInfoBy") - beforeMagic == 14,
          "Frostbite retains Magic contribution attribution");
      Object owner = new Object();
      NonCorporealState.tryEnter(target, owner, 100, 3, () -> true);
      before = target.getLevel(Skill.HITS.id());
      target.applyFrostbiteDebuff(reflector);
      frost.invoke(melee, target, reflector, 20);
      check(
          before - target.getLevel(Skill.HITS.id()) == 13,
          "Frostbite magical harm passes through phase");
      Method thorns =
          PvmMeleeEvent.class.getDeclaredMethod(
              "inflictClericThornsDamage", Mob.class, Mob.class, int.class);
      thorns.setAccessible(true);
      before = target.getLevel(Skill.HITS.id());
      beforeCredit = contribution(target, reflector, "getCombatDamageInfoBy");
      thorns.invoke(melee, reflector, target, 10);
      check(
          target.getLevel(Skill.HITS.id()) == before
              && contribution(target, reflector, "getCombatDamageInfoBy") == beforeCredit,
          "nonmagical Thorns block has no contribution or recursive child");
      NonCorporealState.clearOwned(target, owner);
      before = target.getLevel(Skill.HITS.id());
      thorns.invoke(melee, reflector, target, 10);
      check(
          before - target.getLevel(Skill.HITS.id()) == 14,
          "blocked reflection did not consume fractional carry or bank damage");
      h.equip(reflector, 1696, 1);
      Method recoil =
          ProjectileEvent.class.getDeclaredMethod(
              "recoilDamage", Player.class, Mob.class, int.class);
      recoil.setAccessible(true);
      ProjectileEvent recoilEvent = new ProjectileEvent(h.world(), target, reflector, 40, 2, false);
      before = target.getLevel(Skill.HITS.id());
      beforeCredit = contribution(target, reflector, "getCombatDamageInfoBy");
      com.openrsc.server.util.rsc.DataConversions.getRandom().setSeed(0);
      recoil.invoke(recoilEvent, reflector, target, 40);
      check(
          before - target.getLevel(Skill.HITS.id()) == 13
              && contribution(target, reflector, "getCombatDamageInfoBy") == beforeCredit
              && results.get(results.size() - 1).getRequest().getSource() == reflector,
          "real recoil ring uses boosted settlement and retains existing attribution/contribution"
              + " boundary");
      AttackState.commitAttack(target);
      SharedAttackFoundationFixture.advance(h, 1);
      check(
          NonCorporealState.tryEnter(target, owner, 100, 20, () -> true),
          "later corporeal attempt rearms explicit armor phase fixture");
      before = target.getLevel(Skill.HITS.id());
      check(
          com.openrsc.server.content.ElderGreenDragonArmorEffect.inflictTrueDamage(c, target, 10)
              == 0,
          "reviewed Elder armor true harm is nonmagical");
      com.openrsc.server.content.ElderGreenDragonArmorEffect.applyBurn(c, target);
      com.openrsc.server.event.rsc.GameTickEvent burn = null;
      for (com.openrsc.server.event.rsc.GameTickEvent event :
          h.server().getGameEventHandler().getEvents())
        if (event.getOwner() == target
            && event.getClass().getName().contains("ElderArmorBurnEvent")) burn = event;
      check(burn != null, "real armor burn producer scheduled");
      burn.run();
      check(
          target.getLevel(Skill.HITS.id()) == before,
          "reviewed armor burn blocked while phase still spends its pulse");
      NonCorporealState.clearOwned(target, owner);
      for (int i = 0; i < 4; i++) burn.run();
      check(
          before - target.getLevel(Skill.HITS.id()) == 4,
          "no catch-up for phased armor burn pulse");
      System.out.println(
          "PASS " + checks + " real summon and reflection weakness producer assertions");
    }
  }
}
