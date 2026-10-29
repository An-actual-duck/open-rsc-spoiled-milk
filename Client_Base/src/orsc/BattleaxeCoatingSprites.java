// Generated native geometry; edit coating manifest/source art.
package orsc;
import orsc.graphics.two.SpriteArchive.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
public final class BattleaxeCoatingSprites {
 private BattleaxeCoatingSprites() {}
 private static final int[][] GEOMETRY={
  {6,26,1,15,32,64,102},
  {6,22,1,14,36,64,102},
  {6,26,1,14,40,64,102},
  {20,20,1,22,49,64,102},
  {20,18,1,25,44,64,102},
  {20,20,1,26,39,64,102},
  {38,20,1,24,48,64,102},
  {35,25,1,29,40,64,102},
  {31,31,1,33,33,64,102},
  {25,20,1,39,43,64,102},
  {21,25,1,43,38,64,102},
  {18,28,1,46,33,64,102},
  {6,26,1,43,44,64,102},
  {6,27,1,44,36,64,102},
  {6,34,1,45,29,64,102},
  {25,34,1,1,8,84,102},
  {25,37,1,31,9,84,102},
  {34,25,1,39,24,84,102},
 };
 public static Entry load() {
  Entry entry=new Entry("viscousbattleaxe",Entry.TYPE.PLAYER_EQUIPPABLE_HASCOMBAT,Frame.LAYER.MAIN_HAND,18);
  try { for(int n=0;n<18;n++) {
   String relative="sprites/equipment/viscousbattleaxe/held-"+String.format(java.util.Locale.ROOT,"%02d",n)+".png";
   BufferedImage image;
   try(InputStream in=BattleaxeCoatingSprites.class.getResourceAsStream("/myworld-assets/"+relative)) {
    if(in!=null) image=ImageIO.read(in); else image=ImageIO.read(new java.io.File("../dev/myworld/assets/"+relative));
   }
   int[] g=GEOMETRY[n]; Frame frame=new Frame(g[0],g[1],g[2]!=0,g[3],g[4],g[5],g[6]);
   for(int y=0;y<g[1];y++) for(int x=0;x<g[0];x++) { int pixel=image.getRGB(x+(g[2]!=0?g[3]:0),y+(g[2]!=0?g[4]:0)); frame.getPixels()[y*g[0]+x]=(pixel>>>24)==0?0:pixel&0xFFFFFF; }
   entry.getFrames()[n]=frame;
  } return entry; } catch(java.io.IOException e) { throw new IllegalStateException("Coated battleaxe artwork is missing",e); }
 }
}
