package orsc;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
/** Compile against retained10056 count77 or count100 JARs, never the new115 client. */
public final class ClericBuffOldClientFixture {
 public static void main(String[]args)throws Exception {
  int retainedCount=args.length>1?Integer.parseInt(args[1]):77;int firstUnknown=retainedCount==77?86:101;
  if(Config.CLIENT_VERSION!=10056||mudclient.COMBAT_EFFECT_COUNT!=retainedCount)throw new AssertionError("not retained retained10056 client");
  java.util.List<com.openrsc.client.entityhandling.defs.ClericSpellDef> defs=ClericSpellbookPacketDecoder.decode(Files.readAllBytes(Paths.get(args[0])));
  if(defs.size()!=20)throw new AssertionError("old schema5 decode");
  Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);mudclient mc=(mudclient)((sun.misc.Unsafe)u.get(null)).allocateInstance(mudclient.class);
  Field names=mudclient.class.getDeclaredField("combatEffectNames");names.setAccessible(true);String[] legacy=new String[retainedCount+1];Arrays.fill(legacy,"legacy");names.set(mc,legacy);
  for(int id=firstUnknown;id<=115;id++){
   if(mc.getCombatEffectDuration(id)!=0)throw new AssertionError("unknown cosmetic must have zero duration");
   ORSCharacter p=new ORSCharacter();mc.applyCombatEffectUpdate(p,id);if(p.combatEffectTime!=0)throw new AssertionError("old client renders unknown animation");
  }
  Field counts=mudclient.class.getDeclaredField("combatEffectFrameCounts");counts.setAccessible(true);int[] frameCounts=new int[retainedCount+1];frameCounts[34]=13;counts.set(mc,frameCounts);
  for(String name:new String[]{"detachedCombatEffectType","detachedCombatEffectTime","detachedCombatEffectX","detachedCombatEffectZ","detachedCombatEffectHeightOffset"}){Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);f.set(mc,new int[32]);}
  ORSCharacter actor=new ORSCharacter();actor.serverIndex=5;ORSCharacter[] actors=new ORSCharacter[6];actors[5]=actor;
  Field players=mudclient.class.getDeclaredField("playerServer");players.setAccessible(true);players.set(mc,actors);
  PacketHandler handler=new PacketHandler(mc);Method appearance=PacketHandler.class.getDeclaredMethod("drawNearbyPlayers");appearance.setAccessible(true);
  Field detached=mudclient.class.getDeclaredField("detachedCombatEffectCount");detached.setAccessible(true);
  for(int id=firstUnknown;id<=115;id++){
   actor.combatEffectType=34;actor.combatEffectTime=39;actor.pendingCombatEffectType=30;
   int before=detached.getInt(mc);
   handler.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,5,10,(byte)id};handler.getPacketsIncoming().packetEnd=0;appearance.invoke(handler);
   if(actor.combatEffectTime!=0||detached.getInt(mc)!=before+1||actor.pendingCombatEffectType!=0)throw new AssertionError("old actual packet fallback/detached overlay behavior changed");
  }
  Field npcs=mudclient.class.getDeclaredField("npcsServer");npcs.setAccessible(true);npcs.set(mc,actors);
  Method npcAppearance=PacketHandler.class.getDeclaredMethod("updateNPCAppearances");npcAppearance.setAccessible(true);
  for(int id=101;id<=115;id++){
   actor.combatEffectType=34;actor.combatEffectTime=39;actor.pendingCombatEffectType=30;
   int before=detached.getInt(mc);
   handler.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,5,10,(byte)id};handler.getPacketsIncoming().packetEnd=0;npcAppearance.invoke(handler);
   if(actor.combatEffectTime!=0||detached.getInt(mc)!=Math.min(32,before+1)||actor.pendingCombatEffectType!=0)throw new AssertionError("old NPC packet unknown-curse fallback");
  }
  int assigned=0;for(com.openrsc.client.entityhandling.defs.ClericSpellDef d:defs)if(d.hasOnEntityAnimation())assigned++;
  if(assigned!=18)throw new AssertionError("old client accepts all eighteen optional cosmetics without schema bump");
  System.out.println("PASS retained10056 client: real schema5 metadata20, optional animations18, unknown-ID duration/update+actual player+NPC packet fallback vectors (legacy detachment and pending-impact clearing documented)");
 }
}
