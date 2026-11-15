package com.openrsc.client.entityhandling;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import java.util.List;
/** Append-only aliases: published NPC and equipment appearances keep their IDs. */
public final class ScimitarCoatingArtDefinitions {
 private ScimitarCoatingArtDefinitions() {}
 public static final int APPEARANCE_START=1176, CLASSIC_START=4300;
 public static final String FAMILY="anticoagulantscimitar";
 public static final int[] MASKS={12044761,13134379,16737817,15654365,15658734,3158064,15196886,10397087,10072780,9348795,11717785,5914493,65535,14221311};
 public static void animations(List<AnimationDef> animations) {
  while(animations.size()<APPEARANCE_START-1)
   animations.add(new AnimationDef("sword","equipment",0xffffff,0,true,false,0));
  if(animations.size()!=APPEARANCE_START-1)throw new IllegalStateException("Scimitar coating appearance tail moved");
  for(int mask:MASKS)animations.add(new AnimationDef(FAMILY,"equipment",mask,0,true,false,0));
 }
}
