package orsc;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import orsc.graphics.three.*;
/** Independent adversarial derived-cache mutations through actual PacketHandler replay.
 * Uses the candidate's maintained setup/snapshot helper; no production class overlay. */
public final class DerivedReplayReview {
 static int transitions;
 static void check(boolean value,String why){BaselineReuseFixture.check(value,why);}
 static int retainedIndex(mudclient c){for(int i=0;i<c.getGameObjectInstanceCount();i++)if(c.getGameObjectInstanceX(i)==30&&c.getGameObjectInstanceZ(i)==10&&c.getGameObjectInstanceID(i)==1)return i;throw new AssertionError("missing overlap object");}
 static void replay(mudclient c,int start)throws Exception{BaselineReuseFixture.baseline(c,BaselineReuseFixture.records(start,30));transitions++;}
 public static void main(String[]args)throws Exception{
  BaselineReuseFixture.digest=MessageDigest.getInstance("SHA-256");Config.F_CACHE_DIR=Paths.get(args[0],"Client_Base/Cache").toString();BaselineReuseFixture.definitions();Renderer3DSettings.setBrowserPresentationAvailable(true);
  RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
  mudclient mc=BaselineReuseFixture.client();boolean enabled=Boolean.getBoolean("spoiledmilk.browserBaselineReuse")&&Boolean.getBoolean("spoiledmilk.browserBenchmark");replay(mc,0);replay(mc,10);int start=10;
  String[] arrays={"faceDiffuseLight","scenePolyNormalShift","scenePolyNormalMagnitude","vertDiffuseLight","vertexParam2","vertexParam6","vertXRot","vertYRot","vertZRot","faceMaxX","faceMaxY","faceMaxZ","faceMinX","faceMinY","faceMinZ","faceNormX","faceNormY","faceNormZ","vertXTransform","vertYTransform","vertZTransform"};
  for(String name:arrays){RSModel old=mc.getGameObjectInstanceModel(retainedIndex(mc));Object v=BaselineReuseFixture.get(old,name);check(v instanceof int[],"actual derived int array "+name);Arrays.fill((int[])v,37);long before=BaselineReuseFixture.reuse(mc);start=10-start;replay(mc,start);RSModel now=mc.getGameObjectInstanceModel(retainedIndex(mc));check(!enabled||now==old,"derived cache alone remains eligible "+name);check(!enabled||BaselineReuseFixture.reuse(mc)>before,"actual replay reused "+name);}
  for(String name:new String[]{"maxFaceDimension","maxX","maxY","maxZ","minX","minY","minZ","m_Yb","appliedTransform","translateX","translateY","translateZ"}){RSModel old=mc.getGameObjectInstanceModel(retainedIndex(mc));BaselineReuseFixture.set(old,name,37);start=10-start;replay(mc,start);check(!enabled||mc.getGameObjectInstanceModel(retainedIndex(mc))==old,"derived scalar rebuilt "+name);}
  // Raw/material state must force a fresh clone instead of retaining changed art.
  for(String name:new String[]{"renderer3DGlowColor","renderer3DGlowRadius","renderer3DGlowIntensity","pickBoundsScale"}){RSModel old=mc.getGameObjectInstanceModel(retainedIndex(mc));BaselineReuseFixture.set(old,name,37);start=10-start;replay(mc,start);check(mc.getGameObjectInstanceModel(retainedIndex(mc))!=old,"changed material/pick state refused "+name);}
  BrowserBaselineObjectReuse work=(BrowserBaselineObjectReuse)BaselineReuseFixture.get(mc.packetHandler,"browserBaselineReuse");check(work.peakBytes<=BrowserBaselineObjectReuse.MAX_BYTES,"proof workspace within declared conservative allowance");
  StringBuilder hex=new StringBuilder();for(byte b:BaselineReuseFixture.digest.digest())hex.append(String.format("%02x",b&255));System.out.println("TRACE "+hex+" transitions="+transitions);System.out.println("PASS checks="+BaselineReuseFixture.checks+" exact production PacketHandler/RSModel; derived-cache adversarial review, CPU only");
 }
}
