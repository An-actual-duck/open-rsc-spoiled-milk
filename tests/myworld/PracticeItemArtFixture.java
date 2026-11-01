package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.model.Sprite;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/** Load the game-owned definitions and actual packaged external icons, in both sprite modes. */
public final class PracticeItemArtFixture {
 public static void main(String[] args)throws Exception {
  Config.F_CACHE_DIR=args[0]+"/Client_Base/Cache";
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[2]);EntityHandler.load(true);
  ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[1]),PracticeItemArtFixture.class);
  for(int id:new int[]{3341,3410}) {
   ItemDef item=EntityHandler.getItemDef(id);
   String name=id==3341?"sticky-saliva-gland":"viscous-coating";
   if(!item.getSpriteLocation().equals("external-png:"+name+(id==3341?"@16x18":"")))throw new AssertionError("final selector "+id);
   Sprite sprite=loader.getExternalItemSprite(item);
   BufferedImage png=ImageIO.read(PracticeItemArtFixture.class.getResourceAsStream("/myworld-assets/sprites/items/inventory-ground/"+name+".png"));
   if(sprite==null||sprite.getWidth()!=48||sprite.getHeight()!=32||item.getPictureMask()!=0)throw new AssertionError("unmasked complete canvas "+id);
   java.util.Set<Integer> palette=new java.util.HashSet<Integer>();
   for(int y=0;y<32;y++)for(int x=0;x<48;x++)if((png.getRGB(x,y)>>>24)>0)palette.add(png.getRGB(x,y)&0xFFFFFF);
   int visible=0;
   for(int color:sprite.getPixels())if(color!=0){visible++;if(!palette.contains(color))throw new AssertionError("unexpected recoloring "+id);}
   if(visible<100)throw new AssertionError("icon absent or tiny "+id);
   if(id==3341)for(int y=0;y<18;y++)for(int x=0;x<16;x++) {
    int rgba=png.getRGB(x+15,y+11),expected=(rgba>>>24)==0?0:(rgba&0xFFFFFF);
    if(sprite.getPixels()[(y+7)*48+x+16]!=expected)throw new AssertionError("gland native pixels "+x+","+y);
   }
  }
  if(!EntityHandler.getItemDef(3342).getSpriteLocation().equals("items:116"))throw new AssertionError("eye changed");
  System.out.println("PASS packaged potion/gland selectors and native gland pixels/potion palette; eye unchanged; custom="+args[2]);
 }
}
