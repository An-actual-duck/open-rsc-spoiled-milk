import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.entityhandling.HeldEquipmentFamilies;
import orsc.graphics.two.SpriteArchive.*;
import orsc.Config;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public final class HeldEquipmentClientAudit {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES = true;
  EntityHandler.load(true);
  check(EntityHandler.animationCount()==1150,"append-only catalog size");
  check("abyssalwhip".equals(EntityHandler.getAnimationDef(1128).getName()),"published whip ID");
  Map<String,Entry> archive=new HashMap<>();
  Workspace workspace=new Unpacker().unpackArchive(new File("Client_Base/Cache/video/Custom_Sprites.osar"));
  check(workspace!=null,"client custom archive loads");
  for(Entry e:workspace.getSubspaceByName("equipment").getEntryList())archive.put(e.getID(),e);
  Set<String> families=new HashSet<>();
  for(HeldEquipmentFamilies.Definition d:HeldEquipmentFamilies.DEFINITIONS){
   AnimationDef a=EntityHandler.getAnimationDef(d.appearanceId-1);
   check(d.family.equals(a.getName())&&a.getCharColour()==d.mask&&a.getBlueMask()==0&&a.hasA(),"family/palette/attack " + d.appearanceId);
   families.add(d.family);
  }
  for(String family:families){
   Entry e=archive.get(family);check(e!=null,"archive family "+family);
   check(e.getType()==Entry.TYPE.PLAYER_EQUIPPABLE_HASCOMBAT && e.getLayer()==Frame.LAYER.MAIN_HAND,"equipment layer/type "+family);
   check(e.getFrames().length>=18,"all direction/walk/attack frames "+family);
   int visible=0;
   for(int frame=0;frame<18;frame++){
    Frame f=e.getFrames()[frame];check(f!=null&&f.getPixels().length==f.getWidth()*f.getHeight(),"frame geometry");
    // Zero-pixel directional occlusion frames are intentional and retained.
    for(int pixel:f.getPixels())if(pixel!=0)visible++;
    if(args.length>0){
     BufferedImage ref=ImageIO.read(new File(args[0]+"/"+family+"/frame-"+String.format("%02d",frame)+".png"));
     check(ref.getWidth()==f.getWidth()&&ref.getHeight()==f.getHeight(),"reference dimensions "+family);
     for(int y=0;y<f.getHeight();y++)for(int x=0;x<f.getWidth();x++){
      int argb=ref.getRGB(x,y),expected=(argb>>>24)==0?0:argb&0xffffff;
      check(expected==f.getPixels()[y*f.getWidth()+x],"exact existing reference pixels "+family+" "+frame);
     }
    }
   }
   check(visible>0,"visible family artwork");
  }
  for(String spec:Arrays.asList("272:dagger:3158064","265:shortsword:3158064","286:2hander:3158064","480:scimitar:3158064","267:dagger:15654365","1036:firesword:0","1037:icesword:0")){
   String[] f=spec.split(":");AnimationDef a=EntityHandler.getAnimationDef(Integer.parseInt(f[0])-1);
   check(f[1].equals(a.getName())&&a.getCharColour()==Integer.parseInt(f[2]),"reused family mapping "+spec);
  }
  Config.S_WANT_CUSTOM_SPRITES=false;EntityHandler.load(true);
  for(HeldEquipmentFamilies.Mapping m:HeldEquipmentFamilies.MAPPINGS){
   AnimationDef a=EntityHandler.getAnimationDef(m.fallbackAppearance-1);
   check(a!=null&&("sword".equals(a.getName())||"battleaxe".equals(a.getName())),"valid authentic fallback "+m.itemId);
  }
  System.out.println("PASS client held families: append-only IDs, masks, all18 archive frames, reference pixels, layer/type, authentic fallback catalog");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
