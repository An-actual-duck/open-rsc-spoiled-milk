package com.openrsc.server.avatargenerator;

import com.openrsc.server.external.HeldEquipmentFamilies;
import com.openrsc.server.external.SlayerArtAppearances;
import com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef;
import java.util.List;

/** Sparse palette variants; does not expand or renumber the legacy avatar catalog. */
public final class HeldEquipmentAvatarAnimations {
 private HeldEquipmentAvatarAnimations() { }
 public static AnimationDef resolve(List<AnimationDef> animations, int animationIndex) {
  for (SlayerArtAppearances.Mapping mapping : SlayerArtAppearances.MAPPINGS) {
   if (mapping.appearanceId - 1 != animationIndex) continue;
   for (AnimationDef base : animations) {
    if (mapping.family.equals(base.getName()))
     return new AnimationDef(mapping.family, "equipment", mapping.mask, 0, mapping.hasCombat, false, base.getNumber());
   }
   // The legacy web-avatar exporter does not load the client's external PNG families.
   // Keep its established silhouette rather than indexing beyond its smaller catalog.
   return animations.get(mapping.fallbackAppearance - 1);
  }
  for (HeldEquipmentFamilies.Definition definition : HeldEquipmentFamilies.DEFINITIONS) {
   if (definition.appearanceId - 1 != animationIndex) continue;
   for (AnimationDef base : animations) {
    if (definition.family.equals(base.getName()))
     return new AnimationDef(definition.family, "equipment", definition.mask, 0, true, false, base.getNumber());
   }
   throw new IllegalStateException("Missing avatar weapon family: " + definition.family);
  }
  if(animationIndex>=1175&&animationIndex<1189) {
   int[] masks={12044761,13134379,16737817,15654365,15658734,3158064,15196886,10397087,10072780,9348795,11717785,5914493,65535,14221311};
   // The legacy account-avatar exporter has no appended animation catalog.
   // Resolve its existing scimitar pose safely; the game uses coated frames.
   for(AnimationDef base:animations)if("scimitar".equals(base.getName()))
    return new AnimationDef("scimitar","equipment",masks[animationIndex-1175],0,true,false,base.getNumber());
  }
  return animations.get(animationIndex);
 }
}
