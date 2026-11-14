package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import orsc.graphics.two.MudClientGraphics;

/** Invoke the actual Death wing installer in custom and authentic graphics modes. */
public final class BatNpcWingColorFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);Config.F_CACHE_DIR=args[2]+"/Client_Base/Cache";EntityHandler.load(true);
  int[] bat=EntityHandler.getNpcDef(43).sprites.clone();
  int[] death=EntityHandler.getNpcDef(768).sprites.clone();
  check(bat[0]==138&&death[0]==138,"shared legacy family before external activation");
  int deathId=EntityHandler.getDeathWingAnimationId();
  check(deathId==(Config.S_WANT_CUSTOM_SPRITES?1174:1113),"Death wing appended after existing animations");
  if(Config.S_WANT_CUSTOM_SPRITES) {
   check("slayer-preview-stirge".equals(EntityHandler.getAnimationDef(1173).getName()),"Stirge ID retained");
   for(int id=1170;id<=1172;id++)check("crossbow".equals(EntityHandler.getAnimationDef(id).getName()),"God crossbow ID retained");
  }
  Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
  mudclient client=(mudclient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(mudclient.class);
  MudClientGraphics graphics=new MudClientGraphics(240,180,5000);client.setSurface(graphics);
  check(graphics.fillSpriteTree(),"base native archive loads");
  Field loader=mudclient.class.getDeclaredField("externalAssetLoader");loader.setAccessible(true);
  loader.set(client,new ClientExternalAssetLoader(Paths.get(args[0]),mudclient.class));
  AnimationDef animation=EntityHandler.getAnimationDef(deathId);
  if(!Config.S_WANT_CUSTOM_SPRITES)animation.number=4500;
  Method load=mudclient.class.getDeclaredMethod("loadExternalDeathWingNpcSprite");load.setAccessible(true);load.invoke(client);
  check(EntityHandler.getNpcDef(768).sprites[0]==deathId,"Death wing uses separate family");
  check(Arrays.equals(bat,EntityHandler.getNpcDef(43).sprites),"Giant bat appearance unchanged");
  check(EntityHandler.getNpcDef(768).camera1==225&&EntityHandler.getNpcDef(768).camera2==195,"legacy world size retained");
  for(int f=0;f<18;f++) {
   if(Config.S_WANT_CUSTOM_SPRITES)check(client.spriteSelect(animation,f)!=null,"all custom flight/attack frames loaded");
   else check(graphics.sprites[4500+f]!=null,"all authentic flight/attack frames loaded");
  }
  System.out.println("PASS actual Death wing graphics installer: eighteen frames, separate family, Giant bat preserved, published IDs retained; custom="+Config.S_WANT_CUSTOM_SPRITES);
 }
}
