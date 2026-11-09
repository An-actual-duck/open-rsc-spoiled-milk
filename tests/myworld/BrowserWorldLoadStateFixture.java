package orsc;
import java.util.Arrays;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
public final class BrowserWorldLoadStateFixture {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Renderer3DFrame frame(int section,int level,long content){
  Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,section,9,0,0,new int[]{0,0,0,128,0,0,0,0,128},new float[3],new float[3],new int[3],new int[]{0,1,2},new int[]{-2},new int[]{1},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},1,0,0,content);
  Scene scene=new Scene(new MudClientGraphics(32,32,1),4,16,4);scene.setMidpoints(16,true,32,16,16,9,32);scene.setCamera(0,-4,0,768,0,0,600);scene.setRendererSignedWorldLevel(level);scene.endScene(0);Renderer3DFrame f=scene.getRenderer3DFrame();f.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(chunk)));return f;
 }
 public static void main(String[] args){Config.S_WANT_CUSTOM_SPRITES=true;System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");BrowserWorldLoadState state=new BrowserWorldLoadState();Renderer3DFrame a=frame(10,0,1),anim=frame(10,0,2),area=frame(11,0,3),below=frame(11,-1,3);
  int[] first=state.frame(true,false,true,a,4,2,1,0,0,32,32,3);check(first.length==12&&first[1]==2&&first[3]==4&&first[4]==2&&first[5]==1,"actual pending counts");int epoch=first[2];
  check(state.frame(true,false,true,anim,4,4,0,0,0,32,32,3)[2]==epoch,"content/animation change started area epoch");check(state.frame(true,false,false,anim,4,4,0,0,0,32,32,3)[1]==1,"core authority not required");
  check(state.frame(true,true,false,null,0,0,0,0,0,32,32,0)[1]==3,"legitimate creation/death/sleep UI blocked");check(state.frame(false,false,false,null,0,0,0,0,0,32,32,0)[1]==0,"login classification");check(state.frame(true,false,true,a,4,4,0,0,0,32,32,3)[2]>epoch,"relogin did not renew epoch");epoch=state.frame(true,false,true,a,4,4,0,0,0,32,32,3)[2];
  check(state.frame(true,false,true,area,4,4,0,0,0,32,32,3)[2]>epoch,"area center unchanged");epoch=state.frame(true,false,true,area,4,4,0,0,0,32,32,3)[2];check(state.frame(true,false,true,below,4,4,0,0,0,32,32,3)[2]>epoch,"signed layer not included");for(Renderer3DFrame f:new Renderer3DFrame[]{a,anim,area,below})f.release();System.out.println("PASS "+checks+" real frame region/layer/content/readiness/UI classification checks");
 }
}
