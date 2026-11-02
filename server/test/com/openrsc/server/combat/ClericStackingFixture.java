package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;

/** Real multi-Cleric packet casts; no network, accounts or owner map writes. */
public final class ClericStackingFixture {
  private static int checks;

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  private static void power(Player player, int level) {
    ClericBlessingsFixture.skill(player, Skill.PRAYER, level, 99);
  }

  private static final class Slots {
    final ClericEffectRegistry registry;
    final ClericEffectOriginValidator validator;

    Slots(ClericEffectRegistry registry, ClericEffectOriginValidator validator) {
      this.registry = registry;
      this.validator = validator;
    }

    ClericEffectEntry entry(ClericEffectFamily family) {
      return registry.get(family, validator).orElseThrow(() -> new AssertionError("Missing " + family));
    }
  }

  private static Slots slots(Mob actor) throws Exception {
    if (actor instanceof Player) {
      Player player = (Player) actor;
      return new Slots(ClericBlessingsFixture.registry(player), ClericEffectOrigins.validatorFor(player));
    }
    Object state = actor.getAttribute("cleric_hostile_state", null);
    check(state != null, "actual hostile state installed");
    Field registry = state.getClass().getDeclaredField("registry");
    Field validator = state.getClass().getDeclaredField("validator");
    registry.setAccessible(true);
    validator.setAccessible(true);
    return new Slots((ClericEffectRegistry) registry.get(state),
        (ClericEffectOriginValidator) validator.get(state));
  }

  private static void cast(Player caster, int code, boolean useful, Mob[] actors) throws Exception {
    for (Mob actor : actors) actor.resetAfterUpdate();
    ClericSpellDefinition spell = ClericSpellCatalog.fromCode(code);
    ClericBlessingsFixture.fund(caster, spell);
    ClericBlessingsFixture.packet(caster, code);
    for (ClericResourceCost cost : spell.getResourceCosts())
      check(ClericBlessingsFixture.count(caster, cost.getItemId())
          == 100 - (useful ? cost.getCount() : 0),
          "one useful-cast cost or no-op refund for " + spell.getDisplayName());
    for (Mob actor : actors)
      check(spell.getId().isBlessing() && useful || !actor.getUpdateFlags().hasCombatEffect(),
          "refused buffs, curses and uninvolved actors cannot emit animations");
  }

  private static void line(CurrentCombatHarness h, Player first, Player second,
      Mob recipient, int firstCode, ClericEffectFamily family, int expectedSlots, Mob[] actors)
      throws Exception {
    power(first, 160);
    power(second, 160);
    Map<ClericEffectFamily, ClericEffectEntry> otherGods =
        new EnumMap<ClericEffectFamily, ClericEffectEntry>(ClericEffectFamily.class);
    if (expectedSlots > 1) {
      Slots previous = slots(recipient);
      for (ClericEffectEntry e : previous.registry.snapshot(previous.validator))
        otherGods.put(e.getDefinition().getFamily(), e);
    }

    cast(first, firstCode, true, actors);
    Slots active = slots(recipient);
    check(active.entry(family).getDefinition().getFamilyPrecedence() == 1, "Tier1 applied");
    cast(second, firstCode + 1, true, actors);
    check(active.entry(family).getDefinition().getFamilyPrecedence() == 2,
        "another Cleric's Tier2 replaces Tier1");
    cast(first, firstCode + 2, true, actors);
    check(active.entry(family).getDefinition().getFamilyPrecedence() == 3,
        "Tier3 replaces Tier2");
    // Buff the first caster too, making later weaker casts ineffective for
    // every party recipient, not just the primary subject of this fixture.
    cast(second, firstCode + 2, true, actors);
    cast(first, firstCode + 2, true, actors);
    ClericEffectEntry strongest = active.entry(family);
    check(strongest.getDefinition().getRank() == 6, "highest power retained");
    h.clock().advanceMillis(1000);
    for (int lower = firstCode; lower <= firstCode + 1; lower++) {
      cast(second, lower, false, actors);
      check(active.entry(family) == strongest,
          "another Cleric's lower tier cannot replace, refresh or duplicate Tier3");
    }
    power(second, 40);
    cast(second, firstCode + 2, false, actors);
    check(active.entry(family) == strongest, "same-tier weaker power cannot replace or refresh");

    // Start this one god at lower potency; retain all other gods unchanged.
    if (recipient instanceof Player) {
      for (Player player : new Player[] {first, second, (Player) recipient}) {
        Slots own = slots(player);
        own.registry.remove(family, own.entry(family).getDefinition());
      }
    } else active.registry.remove(family, strongest.getDefinition());
    power(first, 40);
    cast(first, firstCode + 2, true, actors);
    check(active.entry(family).getDefinition().getRank() == 2, "Tier3 can begin at lower power");
    power(second, 160);
    cast(second, firstCode + 2, true, actors);
    ClericEffectEntry upgraded = active.entry(family);
    check(upgraded.getDefinition().getRank() == 6
        && upgraded.getOrigin().getCasterSession() == second.getTransientEffectSessionToken(),
        "another Cleric's stronger same-tier power replaces the complete effect");
    h.clock().advanceMillis(1000);
    power(first, 160);
    cast(first, firstCode + 2, true, actors);
    ClericEffectEntry refreshed = active.entry(family);
    check(refreshed.getExpiresAtNanos() > upgraded.getExpiresAtNanos()
        && refreshed.getOrigin().getCasterSession() == first.getTransientEffectSessionToken(),
        "equal strength from another Cleric refreshes one effect");
    check(active.registry.size(active.validator) == expectedSlots, "one slot per god across Clerics");
    for (Map.Entry<ClericEffectFamily, ClericEffectEntry> other : otherGods.entrySet())
      check(active.entry(other.getKey()) == other.getValue(), "other god's effect and expiry untouched");
  }

  public static void main(String[] args) throws Exception {
    try (CurrentCombatHarness h = new CurrentCombatHarness()) {
      h.server().getConfig().WANT_MYWORLD = true;
      h.server().getConfig().WANT_FATIGUE = false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      Player first = h.player("stackclerica", 450, 450);
      Player second = h.player("stackclericb", 450, 451);
      Player recipient = h.player("stackrecipient", 451, 450);
      for (Player p : new Player[] {first, second, recipient}) {
        p.setClientVersion(com.openrsc.server.net.rsc.ClericBlessingContract.CLIENT_VERSION);
        p.getClientLimitations().maxItemId = 65535;
        h.recordOutgoingPackets(p);
        ClericBlessingsFixture.skill(p, Skill.HITS, 100, 1000);
      }
      ClericBlessingsFixture.party(h, first, second, recipient);
      Npc enemy = h.npc(3, 451, 451);
      enemy.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
      Mob[] actors = {first, second, recipient, enemy};
      ClericEffectFamily[] blessings = {ClericEffectFamily.SARADOMIN_BLESSING,
          ClericEffectFamily.GUTHIX_BLESSING, ClericEffectFamily.ZAMORAK_BLESSING};
      ClericEffectFamily[] curses = {ClericEffectFamily.SARADOMIN_HOSTILE,
          ClericEffectFamily.GUTHIX_HOSTILE, ClericEffectFamily.ZAMORAK_HOSTILE};
      int[] curseCodes = {25, 22, 28};
      for (int god = 0; god < 3; god++)
        line(h, first, second, recipient, 12 + god * 3, blessings[god], god + 1, actors);
      for (int god = 0; god < 3; god++)
        line(h, first, second, enemy, curseCodes[god], curses[god], god + 1, actors);
      System.out.println("PASS " + checks
          + " real multi-Cleric god-slot/replacement/cost/cast-visual checks");
    }
  }
}
