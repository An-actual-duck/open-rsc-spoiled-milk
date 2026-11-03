package com.openrsc.server.avatargenerator;

import com.openrsc.server.external.HeldEquipmentFamilies;
import com.openrsc.server.avatargenerator.AvatarFormat.AnimationDef;
import java.util.List;

/** Sparse palette variants; does not expand or renumber the legacy avatar catalog. */
public final class HeldEquipmentAvatarAnimations {
 private HeldEquipmentAvatarAnimations() { }
 public static AnimationDef resolve(List<AnimationDef> animations, int animationIndex) {
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
