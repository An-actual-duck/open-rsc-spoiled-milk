package orsc;

import com.openrsc.client.model.Sprite;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.*;

/** Actual JAR loader, PacketHandler, software draw and native WORLD_OVERLAY capture. */
public final class ClericBuffAnimationClientFixture {
  static int checks;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static void set(Object o,String n,Object v)throws Exception{Field f=mudclient.class.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
  static Object call(Object o,String n,Class<?>[] t,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}
  static mudclient client(Path root)throws Exception{
    Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
    mudclient mc=(mudclient)((sun.misc.Unsafe)f.get(null)).allocateInstance(mudclient.class);
    set(mc,"combatEffectSprites",new Sprite[mudclient.COMBAT_EFFECT_COUNT+1][64]);
    set(mc,"combatEffectFrameCounts",new int[mudclient.COMBAT_EFFECT_COUNT+1]);
    String[] names=new String[78];Arrays.fill(names,"legacy");set(mc,"combatEffectNames",names);
    set(mc,"externalAssetLoader",new ClientExternalAssetLoader(root,mudclient.class));
    set(mc,"clericSpellbook",new ClericSpellbookCatalog());set(mc,"clericIconSprites",new HashMap<Integer,Sprite>());
    mc.localPlayer=new ORSCharacter();mc.localPlayer.serverIndex=5;
    ORSCharacter[] actors=new ORSCharacter[10];actors[5]=mc.localPlayer;actors[6]=new ORSCharacter();actors[6].serverIndex=6;
    set(mc,"playerServer",actors);set(mc,"npcsServer",new ORSCharacter[10]);
    call(mc,"loadExternalCombatEffectSprites",new Class<?>[0]);
    return mc;
  }
  static void packet(mudclient mc,PacketHandler h,int index,int id)throws Exception{
    h.getPacketsIncoming().dataBuffer=new byte[]{0,1,0,(byte)index,10,(byte)id};h.getPacketsIncoming().packetEnd=0;
    call(h,"drawNearbyPlayers",new Class<?>[0]);
  }
  static void png(MudClientGraphics s,Path out)throws Exception{
    BufferedImage image=new BufferedImage(s.width2,s.height2,BufferedImage.TYPE_INT_RGB);
    image.setRGB(0,0,s.width2,s.height2,s.pixelData,0,s.width2);ImageIO.write(image,"png",out.toFile());
  }
  static int[] bounds(int[] pixels,int width,int background){
    int minX=width,minY=width,maxX=-1,maxY=-1;
    for(int i=0;i<pixels.length;i++)if(pixels[i]!=background){int x=i%width,y=i/width;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
    return new int[]{maxX<minX?0:maxX-minX+1,maxY<minY?0:maxY-minY+1};
  }
  public static void main(String[]args)throws Exception{
    Path root=Paths.get(args[0]),empty=Paths.get(args[1]),out=Paths.get(args[2]);Files.createDirectories(out);
    Config.S_WANT_CUSTOM_SPRITES=true;
    mudclient mc=client(root),packed=client(empty);
    int[] expectedCounts={25,13,17,17,21,11,14,11,17,38,55,38,49,25,42};
    check(mudclient.COMBAT_EFFECT_COUNT==ClericBlessingContract.LAST_CURSE_ANIMATION,"authoritative range bound");
    check(mudclient.spriteProjectileEffectBase==51200+115*64,"projectiles follow expanded combat sprite slots");
    check(mudclient.spriteProjectileStaticMirrorBase+256<65536,"all ranges fit 16-bit scene sprite namespace");
    check(mc.getCombatEffectFrameCount(78)==0&&mc.getCombatEffectDuration(85)==0,"NPC pose reservations remain outside sprite animations");
    for(int id=86;id<=100;id++){
      int count=expectedCounts[id-86];check(mc.getCombatEffectFrameCount(id)==count,"native frame count "+id);
      check(mc.getCombatEffectDuration(id)==count*3,"three ticks per frame "+id);
      int offset=0;
      for(int part:ClericBlessingContract.buffAnimationSequence(id)){
        for(int f=0;f<mc.getCombatEffectFrameCount(part);f++){
          Sprite a=mc.getCombatEffectSprite(id,offset),b=packed.getCombatEffectSprite(id,offset);
          check(a!=null&&b!=null&&Arrays.equals(a.getPixels(),b.getPixels()),"source/embedded native sprite parity "+id+":"+offset);
          check(Arrays.equals(a.getPixels(),mc.getCombatEffectSprite(part,f).getPixels()),"concatenated component frame order");
          int limit=CombatEffectAnimationCatalog.getDefinition(part).getMaxTargetSize();int[] box=bounds(a.getPixels(),64,0);
          check(box[0]<=limit&&box[1]<=limit,"approved visible 48px/Bark32 footprint retained");
          check((Integer)call(mc,"getCombatEffectCurrentFrame",new Class<?>[]{int.class,int.class},id,count*3-offset*3)==offset,"frame start boundary");
          check((Integer)call(mc,"getCombatEffectCurrentFrame",new Class<?>[]{int.class,int.class},id,count*3-offset*3-2)==offset,"frame end boundary");offset++;
        }
      }
      check(offset==count,"complete sequence");
    }
    PacketHandler h=new PacketHandler(mc);ORSCharacter p=mc.localPlayer;
    p.combatEffectType=34;p.combatEffectTime=39;p.pendingCombatEffectType=30;p.hasCombatEffectScreenAnchor=true;p.combatEffectScreenX=123;
    packet(mc,h,5,86);check(p.clericBuffPlayback!=null&&p.clericBuffPlayback.getEffectType()==86,"real packet starts local playback");
    check(p.combatEffectType==34&&p.combatEffectTime==39&&p.pendingCombatEffectType==30&&p.hasCombatEffectScreenAnchor&&p.combatEffectScreenX==123,"buff packet leaves all ordinary combat overlay fields/anchors intact");
    packet(mc,h,6,96);check(mc.getPlayerFromServer(6).clericBuffPlayback==null,"client rejects remote recipient cosmetic");
    ClericBuffPlayback q=p.clericBuffPlayback;packet(mc,h,5,89);packet(mc,h,5,92);packet(mc,h,5,95);packet(mc,h,5,96);packet(mc,h,5,95);
    for(int i=0;i<100;i++){packet(mc,h,5,89);packet(mc,h,5,92);}
    check(q.getPendingCount()==3,"one pending per god under refresh flood");
    for(int i=0;i<75;i++)q.tick();check(q.getEffectType()==89&&q.getRemainingTicks()==51,"different god FIFO");
    for(int i=0;i<51;i++)q.tick();check(q.getEffectType()==92,"third god survives");
    for(int i=0;i<42;i++)q.tick();check(q.getEffectType()==96&&q.getRemainingTicks()==165,"pending upgrade survives refresh/lower sequence");
    q.clear();q.enqueue(86,0);q.enqueue(85,50);check(q.getEffectType()==0&&q.getPendingCount()==0,"missing asset/unknown ID safely omitted");
    q.enqueue(96,165);p.clearTransientVisualState();check(q.getEffectType()==0,"actor replacement transient reset");
    set(mc,"players",new ORSCharacter[10]);set(mc,"knownPlayers",new ORSCharacter[10]);
    mc.applyCombatEffectUpdate(p,96);check(mc.createPlayer(64,5,64,0,orsc.enumerations.ORSCharacterDirection.NORTH)==p&&q.getEffectType()==0,"actual player slot reuse clears cosmetic queue");
    mc.applyCombatEffectUpdate(p,96);mc.clearClericSpellbook();check(q.getEffectType()==0,"logout/reconnect/book reset");
    // Draw every frame through the same software renderer and native replay capture route.
    MudClientGraphics surface=new MudClientGraphics(96,96,1);mc.setSurface(surface);
    for(int id:new int[]{96,98,100}){
      q.clear();mc.applyCombatEffectUpdate(p,id);int frames=mc.getCombatEffectFrameCount(id);
      for(int frame=0;frame<frames;frame++){
        Arrays.fill(surface.pixelData,0x252930);surface.beginRenderer2DFrame();
        call(mc,"drawClericBuffOverlay",new Class<?>[]{ORSCharacter.class,int.class,int.class,int.class,int.class},p,16,16,64,64);
        Renderer2DFrame capture=surface.consumeRenderer2DFrame();
        // Blank source frames may generate no command; visible ones must retain the 64px canvas.
        Sprite source=mc.getCombatEffectSprite(id,frame);int visible=0;for(int pixel:source.getPixels())if(pixel!=0)visible++;
        if(visible>0){
          check(capture.getSpriteCommands().length==1,"one native buff overlay command");
          Renderer2DFrame.SpriteCommand command=capture.getSpriteCommands()[0];
          check(command.getPhase()==Renderer2DFrame.Phase.WORLD_OVERLAY,"native world UI ownership");
          check(command.getWidth()==64&&command.getHeight()==64,"64px native canvas avoids double shrinking 48/32 artwork");
          check(command.getSprite()==source,"native command uses exact software sprite");
          int changed=0;for(int pixel:surface.pixelData)if(pixel!=0x252930)changed++;
          check(changed>0,"visible software pixels");
          int[] srcBox=bounds(source.getPixels(),64,0),drawBox=bounds(surface.pixelData,96,0x252930);
          check(Arrays.equals(srcBox,drawBox),"software visible footprint equals native sprite: no double scale");
        }
        png(surface,out.resolve(id+"-"+String.format("%02d",frame)+".png"));
        for(int t=0;t<3;t++)q.tick();
      }
      check(q.getEffectType()==0,"sequence finishes exactly after last frame");
    }
    check(p.combatEffectType==0&&p.combatEffectTime==0,"cosmetics never install ordinary overlay");
    p.combatEffectType=34;p.combatEffectTime=21;p.pendingCombatEffectType=30;
    mc.applyCombatEffectUpdate(p,96);for(int t=0;t<36;t++)q.tick();
    surface.beginRenderer2DFrame();call(mc,"drawCombatEffectOverlay",new Class<?>[]{ORSCharacter.class,int.class,int.class,int.class,int.class},p,16,16,64,64);
    int anchorX=p.combatEffectScreenX,anchorY=p.combatEffectScreenY;
    call(mc,"drawClericBuffOverlay",new Class<?>[]{ORSCharacter.class,int.class,int.class,int.class,int.class},p,16,16,64,64);
    Renderer2DFrame both=surface.consumeRenderer2DFrame();check(both.getSpriteCommands().length==2,"ordinary and buff both render in same native replay frame");
    check(p.combatEffectType==34&&p.combatEffectTime==21&&p.pendingCombatEffectType==30&&p.combatEffectScreenX==anchorX&&p.combatEffectScreenY==anchorY,"separate overlay draw preserves ordinary fields and anchors");
    System.out.println("PASS "+checks+" native Cleric sequence/packet/queue/source+embedded/software+OpenGL-capture checks");
  }
}
