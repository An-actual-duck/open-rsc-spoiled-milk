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
  return animations.get(animationIndex);
 }
}
