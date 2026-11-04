package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.model.Sprite;
import java.nio.file.*;
import java.util.*;
import orsc.enumerations.ORSCharacterDirection;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.*;

/** Native source/JAR frames, real NPC packets/draw path, queue and actor lifecycles. */
public final class ClericDebuffAnimationClientFixture {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void set(Object o,String n,Object v)throws Exception{ClericBuffAnimationClientFixture.set(o,n,v);}
 static Object call(Object o,String n,Class<?>[]t,Object...a)throws Exception{return ClericBuffAnimationClientFixture.call(o,n,t,a);}
 static void packet(PacketHandler h,int...bytes)throws Exception{byte[] b=new byte[bytes.length];for(int i=0;i<b.length;i++)b[i]=(byte)bytes[i];h.getPacketsIncoming().dataBuffer=b;h.getPacketsIncoming().packetEnd=0;call(h,"updateNPCAppearances",new Class<?>[0]);}
 static void effect(PacketHandler h,int id)throws Exception{packet(h,0,1,0,5,10,id);}
 static void initActors(mudclient mc,ORSCharacter npc)throws Exception{
  ORSCharacter[] registry=new ORSCharacter[10];registry[5]=npc;set(mc,"npcsServer",registry);ORSCharacter[] visible=new ORSCharacter[10];visible[0]=npc;
  set(mc,"npcs",visible);set(mc,"npcsCache",new ORSCharacter[10]);set(mc,"npcCount",1);set(mc,"players",new ORSCharacter[10]);
  set(mc,"customNpcMovementTargetValid",new boolean[10]);set(mc,"customNpcMovementTargetResult",new String[10]);
  for(String n:new String[]{"detachedCombatEffectType","detachedCombatEffectTime","detachedCombatEffectX","detachedCombatEffectZ","detachedCombatEffectHeightOffset","detachedScreenCombatEffectType","detachedScreenCombatEffectTime","detachedScreenCombatEffectX","detachedScreenCombatEffectY","detachedScreenCombatEffectWidth","detachedScreenCombatEffectHeight"})set(mc,n,new int[32]);
  set(mc,"floatingHitSplatRenderer",new FloatingHitSplatRenderer());set(mc,"animFrameToSprite_Walk",new int[]{0,1,2,1});set(mc,"animDirLayer_To_CharLayer",new int[8][12]);
 }
 public static void main(String[]args)throws Exception{
  Path root=Paths.get(args[0]),empty=Paths.get(args[1]),out=Paths.get(args[2]);Files.createDirectories(out);
  Config.S_WANT_CUSTOM_SPRITES=true;Config.F_CACHE_DIR=root.resolve("Client_Base/Cache").toString();EntityHandler.load(true);
  mudclient mc=ClericBuffAnimationClientFixture.client(root),packed=ClericBuffAnimationClientFixture.client(empty);
  int[] counts={11,10,12,9,12,12,10,12,13,21,33,21,33,22,35};
  ClientExternalAssetLoader loader=new ClientExternalAssetLoader(root,mudclient.class);
  check(mudclient.COMBAT_EFFECT_COUNT==ClericBlessingContract.LAST_CURSE_ANIMATION,"authoritative115 range");
  check(mudclient.spriteProjectileEffectBase==51200+115*64&&mudclient.spriteProjectileStaticMirrorBase+256<65536,"expanded ranges retain nonoverlap and16bit bounds");
  for(int id=101;id<=115;id++){
   int count=counts[id-101];check(mc.getCombatEffectFrameCount(id)==count&&mc.getCombatEffectDuration(id)==count*2,"approved two-tick native duration "+id);
   int offset=0;
   for(int part:ClericBlessingContract.curseAnimationSequence(id)){
    CombatEffectAnimationCatalog.Definition d=CombatEffectAnimationCatalog.getDefinition(part);Sprite[] full=new Sprite[64];
    int authored=loader.appendExternalAnimationGridSheetFrames(loader.findFirstFile(new String[]{"dev/myworld/assets/runtime/animations/on-entity"},d.getSheetPath()),full,24,d.getColumns(),1,0,d.getFrameCount(),0);
    check(authored==d.getFrameCount(),"native full source preserved "+part);
    for(int frame=0;frame<d.getPlaybackFrameCount();frame++,offset++){
     int selected=(frame*(authored-1)+(d.getPlaybackFrameCount()-1)/2)/(d.getPlaybackFrameCount()-1);
     Sprite a=mc.getCombatEffectSprite(id,offset),b=packed.getCombatEffectSprite(id,offset);
     check(a!=null&&b!=null&&Arrays.equals(a.getPixels(),b.getPixels()),"source/embedded parity");
     check(Arrays.equals(a.getPixels(),full[selected].getPixels()),"full start/end resampling and sequential component order");
     int[] bounds=ClericBuffAnimationClientFixture.bounds(a.getPixels(),64,0);check(bounds[0]<=24&&bounds[1]<=24,"fixed24px source-cell scale");
     check((Integer)call(mc,"getCombatEffectCurrentFrame",new Class<?>[]{int.class,int.class},id,count*2-offset*2)==offset,"frame start boundary");
     check((Integer)call(mc,"getCombatEffectCurrentFrame",new Class<?>[]{int.class,int.class},id,count*2-offset*2-1)==offset,"frame end boundary");
    }
   }
   check(offset==count,"entire cumulative sequence played");
  }
  check(mc.getCombatEffectDuration(105)==24&&mc.getCombatEffectDuration(106)==24,"both Guthix sources normalized to480ms");
  check(mc.getCombatEffectDuration(96)==165&&mc.getCombatEffectDuration(98)==147&&mc.getCombatEffectDuration(100)==126,"all approved buff sequence timings unchanged");
  check(mc.getCombatEffectDuration(78)==0&&mc.getCombatEffectDuration(85)==0,"reserved NPC poses remain outside sprite table");
  ORSCharacter npc=new ORSCharacter();npc.npcId=3;npc.serverIndex=5;npc.direction=ORSCharacterDirection.NORTH;initActors(mc,npc);PacketHandler h=new PacketHandler(mc);
  npc.combatEffectType=34;npc.combatEffectTime=21;npc.pendingCombatEffectType=30;npc.hasCombatEffectScreenAnchor=true;npc.combatEffectScreenX=123;npc.nonCorporeal=true;npc.darkBeastPose=2;npc.darkBeastPoseExpiresMillis=Long.MAX_VALUE;
  effect(h,101);ClericCursePlayback q=npc.clericCursePlayback;check(q!=null&&q.getEffectType()==101,"real NPC packet enqueues one curse");
  check(npc.combatEffectType==34&&npc.combatEffectTime==21&&npc.pendingCombatEffectType==30&&npc.hasCombatEffectScreenAnchor&&npc.combatEffectScreenX==123,"ordinary/pending/anchor independence");
  check(npc.nonCorporeal&&npc.darkBeastPose==2&&npc.darkBeastPoseExpiresMillis==Long.MAX_VALUE,"phase and pose independence");
  // Existing byte HP cannot distinguish death from a living256HP actor. Keep VFX until authoritative removal.
  packet(h,0,1,0,5,2,1,0,232);check(q.getEffectType()==101,"living256HP aliases wire0: no false death suppression");
  packet(h,0,1,0,5,11,0,1,0,232);check(q.getEffectType()==101,"typed high-HP zero alias also preserves curse");npc.floatingHitSplats.clear();npc.combatTimeout=0;
  effect(h,104);effect(h,107);effect(h,110);effect(h,111);effect(h,110);for(int i=0;i<100;i++){effect(h,104);effect(h,107);}
  check(q.getPendingCount()==3,"three god pending bound");for(int i=0;i<22;i++)q.tick();check(q.getEffectType()==104,"Guthix FIFO");for(int i=0;i<18;i++)q.tick();check(q.getEffectType()==107,"Zamorak FIFO");for(int i=0;i<20;i++)q.tick();check(q.getEffectType()==111,"upgrade retained through lower refresh");
  int saved=npc.pendingCombatEffectType;mc.applyCombatEffectUpdate(mc.localPlayer,111);check(mc.localPlayer.clericCursePlayback!=null&&mc.localPlayer.clericCursePlayback.getEffectType()==111,"generic player receiver accepts hostile Cleric cosmetics");
  h.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,5,10,111};h.getPacketsIncoming().packetEnd=0;mc.localPlayer.combatEffectType=34;mc.localPlayer.combatEffectTime=21;mc.localPlayer.pendingCombatEffectType=30;call(h,"drawNearbyPlayers",new Class<?>[0]);
  check(mc.localPlayer.clericCursePlayback.getEffectType()==111&&mc.localPlayer.pendingCombatEffectType==30&&mc.localPlayer.combatEffectTime==21,"actual player curse packet preserves independent ordinary VFX");
  ClericBuffAnimationClientFixture.packet(mc,h,6,113);check(mc.getPlayerFromServer(6).clericCursePlayback.getEffectType()==113,"remote player curse is observer-visible");
  h.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,5,12,1};h.getPacketsIncoming().packetEnd=0;call(h,"drawNearbyPlayers",new Class<?>[0]);check(mc.localPlayer.nonCorporeal,"actual phase wire makes a player non-corporeal");
  h.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,5,12,0};h.getPacketsIncoming().packetEnd=0;call(h,"drawNearbyPlayers",new Class<?>[0]);check(!mc.localPlayer.nonCorporeal,"actual phase wire restores solid rendering state");
  effect(h,96);check(npc.clericBuffPlayback==null&&npc.pendingCombatEffectType==saved,"NPC receiver refuses recipient-only buff");
  // All frames use actual drawNPC; body layers are suppressed in this fixture only to isolate the VFX pixels.
  NPCDef def=EntityHandler.getNpcDef(3);int[] original=def.sprites;def.sprites=new int[12];Arrays.fill(def.sprites,-1);MudClientGraphics surface=new MudClientGraphics(96,96,1);mc.setSurface(surface);
  try{
   ORSCharacter local=mc.localPlayer;local.direction=ORSCharacterDirection.NORTH;local.combatEffectTime=0;local.pendingCombatEffectType=0;local.combatTimeout=0;
   ORSCharacter[] playerView=new ORSCharacter[10];playerView[0]=local;playerView[1]=mc.getPlayerFromServer(6);set(mc,"players",playerView);set(mc,"playerCount",2);
   surface.beginRenderer2DFrame();((FloatingHitSplatRenderer)get(mc,"floatingHitSplatRenderer")).beginFrame();mc.drawPlayer(0,16,16,64,64,0,20,100);
   Renderer2DFrame playerCapture=surface.consumeRenderer2DFrame();check(playerCapture.getSpriteCommands().length==1,"actual player draw includes its hostile Cleric overlay");
   check(playerCapture.getSpriteCommands()[0].getPhase()==Renderer2DFrame.Phase.WORLD_OVERLAY,"player curse uses the same native overlay path as NPC curses");
   ClericBuffAnimationClientFixture.png(surface,out.resolve("player-curse.png"));
   npc.combatEffectTime=0;npc.hasCombatEffectScreenAnchor=false;
   for(int id:new int[]{111,113,115}){
    q.clear();effect(h,id);int count=mc.getCombatEffectFrameCount(id);
    for(int frame=0;frame<count;frame++){
     Arrays.fill(surface.pixelData,0x252930);surface.beginRenderer2DFrame();((FloatingHitSplatRenderer)get(mc,"floatingHitSplatRenderer")).beginFrame();mc.drawNPC(0,16,16,64,64,0,0,100);
     Renderer2DFrame capture=surface.consumeRenderer2DFrame();Sprite source=mc.getCombatEffectSprite(id,frame);int[] box=ClericBuffAnimationClientFixture.bounds(source.getPixels(),64,0);
     if(box[0]>0){check(capture.getSpriteCommands().length==1,"actual NPC draw captures one VFX command");Renderer2DFrame.SpriteCommand command=capture.getSpriteCommands()[0];check(command.getPhase()==Renderer2DFrame.Phase.WORLD_OVERLAY&&command.getWidth()==64&&command.getHeight()==64&&command.getSprite()==source,"native64canvas24px VFX replay ownership");check(Arrays.equals(box,ClericBuffAnimationClientFixture.bounds(surface.pixelData,96,0x252930)),"software visible bbox equals native source");}
     ClericBuffAnimationClientFixture.png(surface,out.resolve(id+"-"+String.format("%02d",frame)+".png"));q.tick();q.tick();
    }
    check(q.getEffectType()==0,"composite ends after its last source frame");
   }
   effect(h,111);for(int i=0;i<10;i++)q.tick();npc.combatEffectType=34;npc.combatEffectTime=21;npc.combatTimeout=0;surface.beginRenderer2DFrame();mc.drawNPC(0,16,16,64,64,0,0,100);check(surface.consumeRenderer2DFrame().getSpriteCommands().length==2,"normal and curse overlay coexist in actual NPC draw");check(npc.pendingCombatEffectType==saved&&npc.nonCorporeal,"drawing leaves projectile and phase state intact");
  }finally{def.sprites=original;}
  // Actual removal wire is the authoritative death/despawn boundary.
  h.getPacketsIncoming().dataBuffer=new byte[]{0,1,(byte)0xf0};h.getPacketsIncoming().packetEnd=0;call(h,"showNPCs",new Class<?>[]{int.class},3);check(q.getEffectType()==0&&q.getPendingCount()==0,"actual NPC removal clears death/despawn playback");
  set(mc,"npcCacheCount",0);check(mc.createNpc(0,3,64,64,5)==npc&&q.getEffectType()==0,"actual slot reuse remains clear");effect(h,111);mc.clearClericSpellbook();check(q.getEffectType()==0,"logout/reconnect clears NPC playback");check(mc.localPlayer.clericCursePlayback.getEffectType()==0&&mc.getPlayerFromServer(6).clericCursePlayback.getEffectType()==0,"logout/reconnect also clears both player curse queues");
  effect(h,111);mc.setNpcFromCache(0,npc);set(mc,"npcCacheCount",1);set(mc,"npcCount",0);check(mc.createNpc(0,4,64,64,5)==npc&&q.getEffectType()==0,"continued-cache definition replacement clears old curse");
  effect(h,111);set(mc,"groundItemAreaEpoch",new GroundItemAreaEpoch());set(mc,"sceneInstanceStore",new ClientSceneInstanceStore());mc.beginAreaLoad();check(q.getEffectType()==0,"actual scene/area reset clears NPC playback");
  ClericCursePlayback missing=new ClericCursePlayback();missing.enqueue(111,0);missing.enqueue(96,165);check(missing.getEffectType()==0,"missing duration and role mismatch safely ignored");
  System.out.println("PASS "+checks+" native curse packet/drawNPC/source+embedded/frame/resampling/queue/lifecycle/coexistence assertions");
 }
 static Object get(Object o,String n)throws Exception{java.lang.reflect.Field f=mudclient.class.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
}
