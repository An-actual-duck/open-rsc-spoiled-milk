package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import java.io.File;
import java.nio.file.Paths;
import orsc.graphics.two.SpriteArchive.Frame;

public final class HeldShearsClientFixture {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true; EntityHandler.load(true);
  AnimationDef a=EntityHandler.getAnimationDef(1041);
  check("shears".equals(a.getName()) && !a.hasA() && !a.hasF(),"stable shears ID and no attack-frame lookup");
  check(EntityHandler.animationCount()==1150,"no shifted IDs");
  ClientExternalAssetLoader disk=new ClientExternalAssetLoader(Paths.get(args[0]),mudclient.class);
  ClientExternalAssetLoader embedded=new ClientExternalAssetLoader(Paths.get(args[1]),mudclient.class);
  for(int n=0;n<15;n++) {
   String relative="dev/myworld/assets/sprites/equipment/shears/numbered/"+String.format("%02d.png",n);
   Frame f=disk.loadExternalEquipmentFrame(new File(args[0],relative),0,0);
   Frame p=embedded.loadExternalEquipmentFrame(new File(args[1],relative),0,0);
   check(f!=null&&p!=null,"disk and packaged frame "+n);
   check(f.getWidth()==64&&f.getHeight()==102&&f.getOffsetX()==0&&f.getOffsetY()==0,"native registration "+n);
   check(java.util.Arrays.equals(f.getPixels(),p.getPixels()),"packaged decoding parity "+n);
   boolean visible=false;for(int pixel:f.getPixels())if(pixel!=0)visible=true;
   check(visible,"visible native frame "+n);
  }
  System.out.println("PASS: shears stable appearance, no attack art, 15 disk/package native frames");
 }
 private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
