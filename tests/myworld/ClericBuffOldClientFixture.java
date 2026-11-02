package orsc;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
/** Compile and execute only against the retained pre-animation 10056 client JAR. */
public final class ClericBuffOldClientFixture {
 public static void main(String[]args)throws Exception {
  if(Config.CLIENT_VERSION!=10056||mudclient.COMBAT_EFFECT_COUNT!=77)throw new AssertionError("not retained pre-animation10056 client");
  java.util.List<com.openrsc.client.entityhandling.defs.ClericSpellDef> defs=ClericSpellbookPacketDecoder.decode(Files.readAllBytes(Paths.get(args[0])));
  if(defs.size()!=20)throw new AssertionError("old schema5 decode");
  Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);mudclient mc=(mudclient)((sun.misc.Unsafe)u.get(null)).allocateInstance(mudclient.class);
  Field names=mudclient.class.getDeclaredField("combatEffectNames");names.setAccessible(true);String[] legacy=new String[78];Arrays.fill(legacy,"legacy");names.set(mc,legacy);
  for(int id=86;id<=100;id++){
   if(mc.getCombatEffectDuration(id)!=0)throw new AssertionError("unknown cosmetic must have zero duration");
   ORSCharacter p=new ORSCharacter();mc.applyCombatEffectUpdate(p,id);if(p.combatEffectTime!=0)throw new AssertionError("old client renders unknown animation");
  }
  Field counts=mudclient.class.getDeclaredField("combatEffectFrameCounts");counts.setAccessible(true);int[] frameCounts=new int[78];frameCounts[34]=13;counts.set(mc,frameCounts);
  for(String name:new String[]{"detachedCombatEffectType","detachedCombatEffectTime","detachedCombatEffectX","detachedCombatEffectZ","detachedCombatEffectHeightOffset"}){Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);f.set(mc,new int[32]);}
  ORSCharacter actor=new ORSCharacter();actor.serverIndex=5;ORSCharacter[] actors=new ORSCharacter[6];actors[5]=actor;
  Field players=mudclient.class.getDeclaredField("playerServer");players.setAccessible(true);players.set(mc,actors);
  PacketHandler handler=new PacketHandler(mc);Method appearance=PacketHandler.class.getDeclaredMethod("drawNearbyPlayers");appearance.setAccessible(true);
  Field detached=mudclient.class.getDeclaredField("detachedCombatEffectCount");detached.setAccessible(true);
  for(int id=86;id<=100;id++){
   actor.combatEffectType=34;actor.combatEffectTime=39;actor.pendingCombatEffectType=30;
   int before=detached.getInt(mc);
   handler.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,5,10,(byte)id};handler.getPacketsIncoming().packetEnd=0;appearance.invoke(handler);
   if(actor.combatEffectTime!=0||detached.getInt(mc)!=before+1||actor.pendingCombatEffectType!=0)throw new AssertionError("old actual packet fallback/detached overlay behavior changed");
  }
  int assigned=0;for(com.openrsc.client.entityhandling.defs.ClericSpellDef d:defs)if(d.hasOnEntityAnimation())assigned++;
  if(assigned!=9)throw new AssertionError("old client accepts all nine optional cosmetics without schema bump");
  System.out.println("PASS retained10056 client: real schema5 metadata20, optional buff animations9, unknown-ID duration/update+actual packet fallback15 (legacy detachment and pending-impact clearing documented)");
 }
}
