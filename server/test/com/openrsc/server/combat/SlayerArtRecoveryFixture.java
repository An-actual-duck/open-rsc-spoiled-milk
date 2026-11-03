package com.openrsc.server.combat;

import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.SlayerArtAppearances;
import com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef;
import com.openrsc.server.avatargenerator.HeldEquipmentAvatarAnimations;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Effective server assignments after all overrides; render-mode changes cannot alter gameplay. */
public final class SlayerArtRecoveryFixture {
 public static void main(String[] args) throws Exception {
  try(CurrentCombatHarness h=new CurrentCombatHarness()) {
   Object handler=h.server().getEntityHandler();
   Method apply=handler.getClass().getDeclaredMethod("applyRecoveredEquipmentAppearances");apply.setAccessible(true);
   Map<Integer,Map<String,Object>> policies=new HashMap<>();
   for(SlayerArtAppearances.Mapping m:SlayerArtAppearances.MAPPINGS) {
    ItemDefinition item=h.server().getEntityHandler().getItemDef(m.itemId);
    check(item.getAppearanceId()==m.appearanceId,"final custom appearance "+m.itemId);
    policies.put(m.itemId,fields(item));
   }
   for(boolean custom:new boolean[]{false,true}) {
    h.server().getConfig().WANT_CUSTOM_SPRITES=custom;apply.invoke(handler);
    for(SlayerArtAppearances.Mapping m:SlayerArtAppearances.MAPPINGS) {
     ItemDefinition item=h.server().getEntityHandler().getItemDef(m.itemId);
     check(item.getAppearanceId()==(custom?m.appearanceId:m.fallbackAppearance),"mode appearance "+m.itemId);
     check(policies.get(m.itemId).equals(fields(item)),"gameplay fields changed "+m.itemId);
    }
   }
   check(h.server().getEntityHandler().getItemDef(3351).getMagicOffense()==56,"current staff balance preserved");
   check(h.server().getEntityHandler().getItemDef(3352).getRangedOffense()==44,"current bow balance preserved");
   check(h.server().getEntityHandler().getItemDef(3350).getDefaultPrice()==600000,"approved whip value");
   java.util.List<AnimationDef> avatarBases=new ArrayList<>();
   for(int i=0;i<508;i++)avatarBases.add(new AnimationDef("legacy"+i,"equipment",0,0,true,false,i));
   for(String family:new String[]{"mediumhelm","hidegloves","hideboots","chainmaillegs","chainmail"})
    avatarBases.add(new AnimationDef(family,"equipment",0,0,true,false,42));
   for(SlayerArtAppearances.Mapping m:SlayerArtAppearances.MAPPINGS) {
    AnimationDef resolved=HeldEquipmentAvatarAnimations.resolve(avatarBases,m.appearanceId-1);
    if(m.family.equals("mediumhelm")||m.family.equals("hidegloves")||m.family.equals("hideboots")
       ||m.family.equals("chainmaillegs")||m.family.equals("chainmail"))
     check(resolved.getName().equals(m.family)&&resolved.getGrayMask()==m.mask&&resolved.getNumber()==42,"legacy avatar palette "+m.itemId);
    else check(resolved==avatarBases.get(m.fallbackAppearance-1),"legacy web-avatar PNG fallback "+m.itemId);
   }
   System.out.println("PASS: 37 effective final Slayer appearances, authentic fallbacks, unchanged gameplay metadata, current bow/staff balance and restored whip value");
  }
 }
 private static Map<String,Object> fields(ItemDefinition item)throws Exception {
  Map<String,Object> result=new TreeMap<>();
  for(Class<?> type=item.getClass();type!=Object.class;type=type.getSuperclass())for(Field f:type.getDeclaredFields()) {
   if(Modifier.isStatic(f.getModifiers())||f.getName().equals("appearanceID")||f.getName().equals("appearanceId"))continue;
   f.setAccessible(true);Object value=f.get(item);result.put(f.getName(),value instanceof Object[]?Arrays.deepToString((Object[])value):value);
  }
  return result;
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
