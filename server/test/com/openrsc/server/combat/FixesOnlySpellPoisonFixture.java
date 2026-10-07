package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SpellDamages;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.content.PoisonPower;
import com.openrsc.server.model.entity.EntityType;

/** Verifies the standalone fixes without any new Slayer or antidote content. */
public final class FixesOnlySpellPoisonFixture {
 public static void main(String[] args) {
  SpellDamages table = new SpellDamages();
  int compared = 0;
  for (Spells spell : Spells.values()) {
   for (EntityType target : new EntityType[]{EntityType.NPC, EntityType.PLAYER}) {
    double lower = table.getSpellDamage(spell, target, SpellDamages.MagicType.F2PONLYMAGIC);
    if (lower >= 0) {
     check(lower == table.getSpellDamage(spell, target, SpellDamages.MagicType.MODERNMAGIC),
       "missing/changed modern lower-tier spell " + spell);
     compared++;
    }
   }
  }
  check(compared > 0, "lower book tested");
  for (EntityType target : new EntityType[]{EntityType.NPC, EntityType.PLAYER}) {
   check(table.getSpellDamage(Spells.THUNDER_BALL, target, SpellDamages.MagicType.MODERNMAGIC) == 4.8, "thunder ball power");
   check(table.getSpellDamage(Spells.THUNDER_SPLASH, target, SpellDamages.MagicType.MODERNMAGIC) == 7.2, "thunder splash power");
   check(table.getSpellDamage(Spells.THUNDER_STRIKE, target, SpellDamages.MagicType.MODERNMAGIC) == 9.6, "thunder strike power");
  }
  int titan = 0;
  for (ItemId item : ItemId.values()) {
   if (item.name().startsWith("POISON") && item.name().contains("TITAN_STEEL")) {
    check(PoisonPower.getWeaponMaxPoisonPower(item.id()) == 70, "Titan Steel poison cap " + item);
    check(PoisonPower.getWeaponAppliedPoisonPower(item.id()) == 28, "Titan Steel poison application " + item);
    titan++;
   }
  }
  check(titan > 0, "Titan Steel weapons tested");
  System.out.println("PASS: modern spell coverage, unchanged thunder powers and all " + titan + " Titan Steel poison variants");
 }
 private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
