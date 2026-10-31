package com.openrsc.server.content.cleric.effect;

import java.util.List;

/** Typed, bounded component metadata for a single atomic Cleric god effect. */
public interface ClericCompositeMagnitude extends ClericEffectMagnitude {
  int getPower();
  int getTier();
  List<ClericBlessingComponent> components();
}
