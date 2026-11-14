package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.SlayerArtAppearances;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import orsc.graphics.two.MudClientGraphics;

/** Actual packaged-only equipment loading and the effective client appearance catalog. */
public final class SlayerArtRecoveryClientFixture {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);
  Config.F_CACHE_DIR=args[0]+"/Client_Base/Cache";
  EntityHandler.load(true);
  if(!Config.S_WANT_CUSTOM_SPRITES) {
   check(EntityHandler.animationCount()==1113,"fresh authentic-mode published catalog");
   for(SlayerArtAppearances.Mapping m:SlayerArtAppearances.MAPPINGS)
    check(EntityHandler.getAnimationDef(m.fallbackAppearance-1)!=null,"authentic fallback "+m.itemId);
   System.out.println("PASS: fresh authentic-mode catalog and all 37 fallback references");
   return;
  }
  check(EntityHandler.animationCount()==1174,"append after every published appearance");
  for(int id=1170;id<=1172;id++)check("crossbow".equals(EntityHandler.getAnimationDef(id).getName()),"god crossbow ID retained");
  check("slayer-preview-stirge".equals(EntityHandler.getAnimationDef(1173).getName()),"Stirge appended after published equipment");
  for(int i=0;i<15;i++)check("viscousbattleaxe".equals(EntityHandler.getAnimationDef(1097+i).getName()),"coating ID retained");
  for(int tier=0;tier<5;tier++)check("gemdragon".equals(EntityHandler.getAnimationDef(1092+tier).getName()),"gem dragon ID retained");
  Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
  mudclient client=(mudclient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(mudclient.class);
  MudClientGraphics graphics=new MudClientGraphics(240,180,5000);client.setSurface(graphics);
  check(graphics.fillSpriteTree(),"native base archive");
  Field loader=mudclient.class.getDeclaredField("externalAssetLoader");loader.setAccessible(true);
  loader.set(client,new ClientExternalAssetLoader(Paths.get("/nonexistent/recovery-packaged-only"),mudclient.class));
  Method load=mudclient.class.getDeclaredMethod("loadExternalEquipmentSprites");load.setAccessible(true);load.invoke(client);
  for(SlayerArtAppearances.Mapping m:SlayerArtAppearances.MAPPINGS) {
   AnimationDef a=EntityHandler.getAnimationDef(m.appearanceId-1);
   check(m.family.equals(a.getName())&&a.getCharColour()==m.mask&&a.hasA()==m.hasCombat,"definition "+m.itemId);
   check(graphics.spriteTree.get("equipment").containsKey(m.family),"actual equipment family "+m.itemId);
   check(client.spriteSelect(a,6)!=null,"side frame "+m.itemId);
   if(m.hasCombat)check(client.spriteSelect(a,17)!=null,"attack frame "+m.itemId);
   check(EntityHandler.getItemDef(m.itemId).getSpriteLocation().startsWith("external-png:"),"approved inventory icon "+m.itemId);
  }
  check("external-png:sticky-saliva-gland@16x18".equals(EntityHandler.getItemDef(3341).getSpriteLocation()),"later gland revision retained");
  System.out.println("PASS: 37 actual packaged equipment families, inventory mappings, preserved Gem Dragon/coating IDs and authentic catalog fallbacks");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
