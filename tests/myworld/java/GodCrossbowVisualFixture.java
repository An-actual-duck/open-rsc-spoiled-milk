package orsc;
import com.openrsc.client.entityhandling.*;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.two.ProjectileAnimationCatalog;
import orsc.multiclient.ClientPort;
import java.lang.reflect.*;
import java.util.*;
/** Real custom/classic allocator and sprite selection, without a window or network. */
public final class GodCrossbowVisualFixture {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);Config.F_CACHE_DIR=args[0]+"/Client_Base/Cache";EntityHandler.load(true);
  check(EntityHandler.animationCount()==1192,"no appearance IDs shifted");
  Map<String,Entry> entries=new HashMap<>();
  for(Entry e:new Unpacker().unpackArchive(new java.io.File(args[0]+"/Client_Base/Cache/video/Custom_Sprites.osar")).getSubspaceByName("equipment").getEntryList())entries.put(e.getID(),e);
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
  mudclient client=(mudclient)((sun.misc.Unsafe)uf.get(null)).allocateInstance(mudclient.class);
  MudClientGraphics surface=new MudClientGraphics(240,180,5000);client.setSurface(surface);check(surface.fillSpriteTree(),"archive loaded");
  if(!Config.S_WANT_CUSTOM_SPRITES){
   Field archive=orsc.graphics.two.GraphicsController.class.getDeclaredField("spriteArchive");archive.setAccessible(true);archive.set(surface,new java.util.zip.ZipFile(args[0]+"/Client_Base/Cache/video/Authentic_Sprites.orsc"));
   ClientPort old=mudclient.clientPort;
   try{mudclient.clientPort=(ClientPort)Proxy.newProxyInstance(ClientPort.class.getClassLoader(),new Class<?>[]{ClientPort.class},(p,m,a)->null);Method allocate=mudclient.class.getDeclaredMethod("loadEntitiesAuthentic");allocate.setAccessible(true);allocate.invoke(client);}
   finally{mudclient.clientPort=old;}
  }
  String[] gods={"saradomin","zamorak","guthix"};
  for(int i=0;i<3;i++){
   AnimationDef alias=EntityHandler.getAnimationDef(1170+i);Entry entry=entries.get(gods[i]+"crossbow"),base=entries.get("crossbow");
   check(alias.getName().equals(gods[i]+"crossbow")&&alias.getCharColour()==0,"fixed two-material palette binding");
   check(entry!=null&&entry.getFrames().length==15,"all directions and movements exist");
   if(!Config.S_WANT_CUSTOM_SPRITES)check(alias.getNumber()==4200+i*27,"reserved classic allocation");
   for(int f=0;f<15;f++){
    Sprite raw=base.getFrames()[f].getSprite(),art=entry.getFrames()[f].getSprite(),selected=client.spriteSelect(alias,f);
    check(raw.getWidth()==art.getWidth()&&raw.getHeight()==art.getHeight()&&raw.getXShift()==art.getXShift()&&raw.getYShift()==art.getYShift()&&raw.getSomething1()==art.getSomething1()&&raw.getSomething2()==art.getSomething2(),"same native framing");
    check(selected!=null&&Arrays.equals(selected.getPixels(),art.getPixels()),"actual custom/classic frame selection");
   }
   ProjectileAnimationCatalog.Definition effect=ProjectileAnimationCatalog.getProjectileFallback(43+i);
   check(effect!=null&&effect.getKey().equals(gods[i]+"-bolt")&&effect.getFrameCount()==4,"bolt animation binding");
   check(ProjectileAnimationCatalog.getImpactSegments(effect.getKey())[0].getFrameCount()==6&&ProjectileAnimationCatalog.getImpactScreenSize(effect.getKey())==32,"compact impact binding");
   for(String name:new String[]{"bolt","impact"})check(GodCrossbowVisualFixture.class.getResource("/myworld-assets/animations/projectile-moving/god-bolts/"+gods[i]+"/"+name+".png")!=null,"packaged effect sheet");
  }
  check(EntityHandler.getAnimationDef(106).getName().equals("crossbow"),"ordinary family intact");
  System.out.println("PASS "+checks+" actual held selection and packaged effect checks; custom="+Config.S_WANT_CUSTOM_SPRITES);
 }
}
