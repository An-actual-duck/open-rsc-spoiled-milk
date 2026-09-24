package com.openrsc.server.combat;

import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.HeldEquipmentFamilies;
import java.lang.reflect.*;
import java.util.*;

public final class HeldEquipmentAppearanceFixture {
 public static void main(String[] args) throws Exception {
  try (CurrentCombatHarness h = new CurrentCombatHarness()) {
   Object handler = h.server().getEntityHandler();
   Method apply = handler.getClass().getDeclaredMethod("applyHeldEquipmentAppearanceOverrides"); apply.setAccessible(true);
   Map<Integer, Map<String,Object>> original = new HashMap<>();
   for (HeldEquipmentFamilies.Mapping m : HeldEquipmentFamilies.MAPPINGS) {
    ItemDefinition item = h.server().getEntityHandler().getItemDef(m.itemId);
    check(item.getAppearanceId() == m.appearanceId, "effective enabled appearance " + m.itemId);
    original.put(m.itemId, fields(item));
   }
   int[] preserved = {1996,2007,2018,2029,2200,2201,2202,2203,2001,2012,2023,2034,
    3349,3350,3351,3352,3353,3354,3355,1987,2047,2048,2049,3268};
   Map<Integer,Integer> before = new HashMap<>();
   for (int id : preserved) before.put(id,h.server().getEntityHandler().getItemDef(id).getAppearanceId());
   for (boolean custom : new boolean[]{false,true}) {
    h.server().getConfig().WANT_CUSTOM_SPRITES = custom; apply.invoke(handler);
    for (HeldEquipmentFamilies.Mapping m : HeldEquipmentFamilies.MAPPINGS) {
     ItemDefinition item = h.server().getEntityHandler().getItemDef(m.itemId);
     check(item.getAppearanceId() == (custom ? m.appearanceId : m.fallbackAppearance), "mode appearance " + m.itemId);
     check(original.get(m.itemId).equals(fields(item)), "gameplay metadata changed " + m.itemId);
    }
    for (int id : preserved) check(before.get(id) == h.server().getEntityHandler().getItemDef(id).getAppearanceId(), "unrelated item changed " + id);
   }
   h.server().getConfig().WANT_MYWORLD = false;
   h.server().getConfig().WANT_CUSTOM_SPRITES = false;
   apply.invoke(handler);
   for (HeldEquipmentFamilies.Mapping m : HeldEquipmentFamilies.MAPPINGS)
    check(h.server().getEntityHandler().getItemDef(m.itemId).getAppearanceId() == (m.itemId>=1900 ? m.appearanceId : m.fallbackAppearance), "profile isolation " + m.itemId);
   h.server().getConfig().WANT_MYWORLD = true;
   h.server().getConfig().WANT_CUSTOM_SPRITES = true;
   apply.invoke(handler);
   java.util.List<com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef> avatarBases = new ArrayList<>();
   for (HeldEquipmentFamilies.Definition d : HeldEquipmentFamilies.DEFINITIONS)
    avatarBases.add(new com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef(d.family,"equipment",0,0,true,false,42));
   for (HeldEquipmentFamilies.Definition d : HeldEquipmentFamilies.DEFINITIONS) {
    com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef animation =
     com.openrsc.server.avatargenerator.HeldEquipmentAvatarAnimations.resolve(avatarBases,d.appearanceId-1);
    check(d.family.equals(animation.getName()), "avatar family");
    check(animation.getGrayMask()==d.mask && animation.getNumber()==42, "avatar palette/base frame binding");
   }
   check(com.openrsc.server.avatargenerator.HeldEquipmentAvatarAnimations.resolve(avatarBases,0)==avatarBases.get(0), "legacy avatar definition retained");
   System.out.println("PASS held appearances: 36 effective mappings, authentic fallbacks, MyWorld isolation, gameplay invariance, preserved Slayer/knife/pickaxe/hatchet mappings, 21 avatar variants");
  }
 }
 private static Map<String,Object> fields(ItemDefinition item) throws Exception {
  Map<String,Object> result=new TreeMap<>();
  for(Class<?> type=item.getClass();type!=Object.class;type=type.getSuperclass()) for(Field field:type.getDeclaredFields()) {
   if(Modifier.isStatic(field.getModifiers())||field.getName().equals("appearanceID")||field.getName().equals("appearanceId"))continue;
   field.setAccessible(true);Object value=field.get(item);
   result.put(field.getName(), value instanceof Object[]?Arrays.deepToString((Object[])value):value);
  }
  return result;
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
