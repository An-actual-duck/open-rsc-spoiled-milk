package orsc;
import com.openrsc.client.entityhandling.*;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.RendererSpriteTransform;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.Paths;
import orsc.multiclient.ClientPort;
public final class ScimitarCoatingArtFixture {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);Config.F_CACHE_DIR=args[0]+"/Client_Base/Cache";EntityHandler.load(true);
  check(EntityHandler.animationCount()==1192,"append-only alias tail");
  Entry entry=null,base=null;
  for(Entry e:new Unpacker().unpackArchive(new java.io.File(args[0]+"/Client_Base/Cache/video/Custom_Sprites.osar")).getSubspaceByName("equipment").getEntryList()) {
   if(e.getID().equals("anticoagulantscimitar"))entry=e;
   if(e.getID().equals("scimitar"))base=e;
  }
  check(entry!=null&&base!=null&&entry.getFrames().length==18,"complete native held family");
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
  mudclient client=(mudclient)((sun.misc.Unsafe)uf.get(null)).allocateInstance(mudclient.class);
  MudClientGraphics surface=new MudClientGraphics(240,180,5000);client.setSurface(surface);check(surface.fillSpriteTree(),"game archive loaded");
  ClientExternalAssetLoader icons=new ClientExternalAssetLoader(Paths.get("/nonexistent/packaged-only"),ScimitarCoatingArtFixture.class);
  int[] bases={1999,2010,82,83,84,427,2155,3117,85,2021,86,2032,398,3265};
  ArrayList<AnimationDef> aliases=new ArrayList<>();
  for(int i=0;i<14;i++) {
   AnimationDef alias=EntityHandler.getAnimationDef(1175+i);aliases.add(alias);
   check(alias.getName().equals("anticoagulantscimitar")&&alias.getCharColour()==ScimitarCoatingArtDefinitions.MASKS[i],"distinct material alias");
   Sprite icon=icons.getExternalItemSprite(EntityHandler.getItemDef(3448+i));check(icon!=null,"packaged inventory icon");
  }
  if(!Config.S_WANT_CUSTOM_SPRITES) {
   Field archive=orsc.graphics.two.GraphicsController.class.getDeclaredField("spriteArchive");archive.setAccessible(true);archive.set(surface,new java.util.zip.ZipFile(args[0]+"/Client_Base/Cache/video/Authentic_Sprites.orsc"));
   Field af=EntityHandler.class.getDeclaredField("animations");af.setAccessible(true);
   @SuppressWarnings("unchecked") ArrayList<AnimationDef> all=(ArrayList<AnimationDef>)af.get(null);ArrayList<AnimationDef> saved=new ArrayList<>(all);ClientPort old=mudclient.clientPort;
   try {
    mudclient.clientPort=(ClientPort)Proxy.newProxyInstance(ClientPort.class.getClassLoader(),new Class<?>[]{ClientPort.class},(p,m,a)->null);
    Method allocate=mudclient.class.getDeclaredMethod("loadEntitiesAuthentic");allocate.setAccessible(true);allocate.invoke(client);
    for(AnimationDef alias:aliases)check(alias.getNumber()==4300,"actual classic allocator reserves independent range");
   }finally{all.clear();all.addAll(saved);mudclient.clientPort=old;}
  }
  for(int f=0;f<18;f++) {
   Sprite raw=base.getFrames()[f].getSprite(),coated=entry.getFrames()[f].getSprite();int changed=0;
   check(raw.getWidth()==coated.getWidth()&&raw.getHeight()==coated.getHeight()&&raw.getXShift()==coated.getXShift()&&raw.getYShift()==coated.getYShift()&&raw.getSomething1()==coated.getSomething1()&&raw.getSomething2()==coated.getSomething2(),"exact crop/offset/canvas retained");
   for(int pixel=0;pixel<raw.getPixels().length;pixel++) {
    int a=raw.getPixels()[pixel],b=coated.getPixels()[pixel];
    check((a==0)==(b==0),"transparency preserved");
    if(a!=b){changed++;int r=(a>>16)&255;check(r==((a>>8)&255)&&r==(a&255)&&r>50,"only blade metal painted");}
   }
   check(changed>0,"visible coating in every movement and attack frame");
   for(AnimationDef alias:aliases) {
    Sprite selected=client.spriteSelect(alias,f);check(selected!=null&&Arrays.equals(selected.getPixels(),coated.getPixels()),"actual named/classic frame selection");
    RendererSpriteTransform tint=RendererSpriteTransform.legacyMasks(alias.getCharColour(),0,0,0xffffffff);
    for(int color:new int[]{0x581828,0x8b2438,0xa7394d})check(tint.apply(color)==RendererSpriteTransform.legacyMasks(0xffffff,0,0,0xffffffff).apply(color),"red coating immune to metal tint");
   }
  }
  System.out.println("PASS coated scimitar art: "+checks+" checks, fourteen icons/materials, eighteen native poses and actual classic/custom loading; custom="+Config.S_WANT_CUSTOM_SPRITES);
 }
}
