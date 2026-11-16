package orsc;
import com.openrsc.client.entityhandling.*;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.CombatEffectAnimationCatalog;
import java.nio.file.Paths;
import java.io.File;
public final class PoisonVisualClientFixture {
 public static void main(String[]args)throws Exception{
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);EntityHandler.load(true);
  CombatEffectAnimationCatalog.Definition effect=CombatEffectAnimationCatalog.getDefinition(119),borrowed=CombatEffectAnimationCatalog.getDefinition(105);
  if(effect==null||!effect.getSheetPath().equals(borrowed.getSheetPath())||effect.getFrameCount()!=28||effect.getPlaybackFrameCount()!=12)throw new AssertionError("borrow existing green poison-snake");
  ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[0]),PoisonVisualClientFixture.class);
  Sprite[] frames=new Sprite[64];
  int loaded=loader.appendExternalAnimationGridSheetFrames(new File(args[0]+"/dev/myworld/assets/runtime/animations/on-entity/"+effect.getSheetPath()),frames,effect.getMaxTargetSize(),28,1,0,28,0,12);
  if(loaded!=12)throw new AssertionError("animation grid playback count");
  for(int i=0;i<loaded;i++)if(frames[i]==null||frames[i].getWidth()>64||frames[i].getHeight()>64)throw new AssertionError("compact single frame, not whole sheet");
  if(PoisonVisualClientFixture.class.getResource("/myworld-assets/animations/on-entity/"+effect.getSheetPath())==null)throw new AssertionError("packaged sheet");
  if(mudclient.COMBAT_EFFECT_COUNT<119||Config.CLIENT_VERSION<10062)throw new AssertionError("client capacity/compatibility");
  System.out.println("PASS poison animation grid, compact playback and packaged sheet; custom="+Config.S_WANT_CUSTOM_SPRITES);
 }
}
