package orsc;
import java.nio.file.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import com.openrsc.client.model.Sprite;import orsc.graphics.two.*;
public class AnimationSizingAudit{public static void main(String[]a)throws Exception{
 Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);mudclient c=(mudclient)((sun.misc.Unsafe)u.get(null)).allocateInstance(mudclient.class);
 ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Files.createTempDirectory("sizing-packaged-"),mudclient.class);
 Method sizing=mudclient.class.getDeclaredMethod("getCombatEffectScreenSize",int.class,int.class);sizing.setAccessible(true);
 StringBuilder csv=new StringBuilder("id,key,loadedFrames,loadCap,peakVisibleWidth,peakVisibleHeight,overlayCanvas,visibleWidthAtOverlayScale,visibleHeightAtOverlayScale\n");
 for(Map.Entry<Integer,CombatEffectAnimationCatalog.Definition> e:CombatEffectAnimationCatalog.getDefinitions().entrySet()){
  int id=e.getKey(),loaded=0,cap=0;Sprite[] frames=new Sprite[64];
  for(CombatEffectAnimationCatalog.Definition d:CombatEffectAnimationCatalog.getSequence(id)){
   loaded=loader.appendExternalAnimationGridSheetFrames(new File("dev/myworld/assets/runtime/animations/"+d.getCategory()+"/"+d.getSheetPath()),frames,d.getMaxTargetSize(),d.getColumns(),d.getRows(),d.getFirstFrame(),d.getFrameCount(),loaded,d.getPlaybackFrameCount());cap=Math.max(cap,d.getMaxTargetSize());
  }
  int pw=0,ph=0;for(int f=0;f<loaded;f++){Sprite s=frames[f];int left=s.getWidth(),right=-1,top=s.getHeight(),bottom=-1;for(int y=0;y<s.getHeight();y++)for(int x=0;x<s.getWidth();x++)if(s.getPixels()[y*s.getWidth()+x]!=0){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}pw=Math.max(pw,right-left+1);ph=Math.max(ph,bottom-top+1);}
  int size=ClericBlessingContract.isRecipientOnlyBuffAnimation(id)||ClericBlessingContract.isNpcCurseAnimation(id)?64:(Integer)sizing.invoke(c,id,64);
  csv.append(id+","+e.getValue().getKey()+","+loaded+","+cap+","+pw+","+ph+","+size+","+String.format(java.util.Locale.ROOT,"%.1f,%.1f",pw*size/64.0,ph*size/64.0)+"\n");
 }
 Files.write(Paths.get(a[0]),csv.toString().getBytes("UTF-8"));System.out.println("Audited "+CombatEffectAnimationCatalog.getDefinitions().size()+" catalog effects through packaged loader");}}
