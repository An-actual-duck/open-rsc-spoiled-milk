package orsc;
import java.util.*;import orsc.graphics.three.*;import orsc.graphics.two.MudClientGraphics;
/** CPU actual-production-matrix fixture; no GPU/performance acceptance. */
public final class BrowserFogDemandFixture {
 static int checks;static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Renderer3DWorldChunkFrame.ChunkMesh chunk(int x,int y,int z,int width,int plane,long signature){int[] c={x,y,z,x+width,y,z,x+width,y,z+width,x,y,z,x+width,y,z+width,x,y,z+width};return new Renderer3DWorldChunkFrame.ChunkMesh(plane,0,0,0,0,c,new float[6],new float[6],new int[6],new int[]{0,1,2,3,4,5},new int[]{-2,-2},new int[]{0x777777,0x777777},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN,Renderer3DModelKind.TERRAIN},null,null,2,0,0,false,0,signature);}
 static Renderer3DFrame frame(Renderer3DWorldChunkFrame w,int level,int yaw,int distance){System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");Scene s=new Scene(new MudClientGraphics(960,540,1),4,16,4);s.setMidpoints(270,true,960,480,270,9,540);s.fogLandscapeDistance=distance+40*128;s.fogSmoothingStartDistance=distance+28*128;s.setCamera(0,-160,0,112,yaw,0,distance);s.setRendererSignedWorldLevel(level);s.endScene(0);Renderer3DFrame f=s.getRenderer3DFrame();f.setWorldChunkFrame(w);return f;}
 static BrowserLightingData.ShadowPlan preview(BrowserLightingData d,Renderer3DFrame f){Renderer3DFrame p=RendererFrameLighting.bind(f);try{return d.previewShadow(f,4096);}finally{RendererFrameLighting.restore(p);}}
 static BrowserLightingData.Frame commit(BrowserLightingData d,Renderer3DFrame f,BrowserLightingData.ShadowPlan plan){Renderer3DFrame p=RendererFrameLighting.bind(f);try{return plan==null?d.prepare(f,4096):d.prepare(f,4096,plan);}finally{RendererFrameLighting.restore(p);}}
 static void bounds() {
  for(int y:new int[]{-5000,-123,9876}) {
   Renderer3DWorldChunkFrame.ChunkMesh a=chunk(12,y,81,256,0,1),b=a.rebasePresentation(-109,407);
   require(a.getMinVertexY()==y&&a.getMaxVertexY()==y,"Nonzero Y bounds not assigned");
   for(Renderer3DWorldChunkFrame.ChunkMesh m:new Renderer3DWorldChunkFrame.ChunkMesh[]{a,b})for(int n=0;n<m.getVertexCount();n++)require(m.getVertexCoord(n*3+1)>=m.getMinVertexY()&&m.getVertexCoord(n*3+1)<=m.getMaxVertexY(),"Rebase Y excludes actual vertex");
  }
  int[] positions={0,-3000,0,128,4567,0,0,122,128};
  Renderer3DWorldChunkFrame.ChunkMesh a=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,positions,new float[3],new float[3],new int[3],new int[]{0,1,2},new int[]{-2},new int[]{0x777777},new Renderer3DModelKind[]{Renderer3DModelKind.GAME_OBJECT},null,null,0,0,0,true,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,2);
  positions[1]=Integer.MIN_VALUE;require(a.getMinVertexY()==-3000&&a.getMaxVertexY()==4567,"Source vertices not immutable");
  Renderer3DWorldChunkFrame.ChunkMesh b=a.rebaseStaticObjectPresentation(7,8,1000,-2000);
  require(b.getMinVertexY()==-3000&&b.getMaxVertexY()==4567,"Static rebase changed Y extent");
  Renderer3DWorldChunkFrame.ChunkMesh empty=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,new int[0],new float[0],new float[0],new int[0],new int[0],new int[0],new int[0],new Renderer3DModelKind[0],null,null,0,0,0,false,0,3);
  require(!empty.hasVertexBounds()&&empty.getMinVertexY()==0&&empty.getMaxVertexY()==0,"Empty bounds changed");
 }
 public static void main(String[] args)throws Exception{
  bounds();
  RendererFogSettings.setMode(RendererFogSettings.Mode.ON);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererRemasterLightSettings.adjustAzimuth(0);RendererRemasterLightSettings.adjustElevation(0);
  List<Renderer3DWorldChunkFrame.ChunkMesh> chunks=new ArrayList<>();for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)chunks.add(chunk((x*48-24)*128,0,(z*48-24)*128,48*128,0,chunks.size()+1));Renderer3DWorldChunkFrame world=Renderer3DWorldChunkFrame.fromChunks(chunks);
  BrowserLightingData ordinary=new BrowserLightingData(),planned=new BrowserLightingData();require(BrowserFogDemand.POSITION_CONTRACT,"Shader allowlist mismatched production");
  for(int tick=0;tick<18;tick++){
   Renderer3DFrame f=frame(world,0,tick<9?128:640,1500);BrowserLightingData.ShadowPlan plan=preview(planned,f);BrowserFogDemand.Selection demand=BrowserFogDemand.select(f,plan);
   BrowserLightingData.Frame a=commit(ordinary,f,null),b=commit(planned,f,plan);require(Arrays.equals(a.values,b.values),"Preview/commit differs from actual preparation "+tick);require(a.changes.length==b.changes.length,"Preview staged masks");for(int i=0;i<a.changes.length;i++)require(Arrays.equals(a.changes[i].rgba,b.changes[i].rgba),"Mask bytes changed");
   System.out.println("ELIGIBILITY tick="+tick+" source="+demand.sourceChunks+" required="+demand.requiredChunks+" vertices="+demand.requiredVertices+" planes="+plan.demandMatrices.length);f.release();
  }
  Renderer3DFrame f=frame(world,0,128,1500);BrowserLightingData d=new BrowserLightingData();BrowserLightingData.ShadowPlan plan=preview(d,f);float[] expected=plan.values.clone();RendererShadowSettings.setQuality(RendererShadowSettings.Quality.OFF);RendererRemasterLightSettings.adjustAzimuth(100);BrowserLightingData.Frame captured=commit(d,f,plan);require(Arrays.equals(Arrays.copyOfRange(captured.values,32,80),Arrays.copyOfRange(expected,32,80)),"Live sun/quality escaped captured plan");require(captured.values[1]==expected[1]&&captured.values[3]==expected[3],"Captured quality changed");try{commit(d,f,plan);throw new AssertionError("Stale plan reused");}catch(IllegalStateException good){checks++;}f.release();
  System.out.println("PASS checks="+checks+" actual production constructors/projection preview; no GPU or speed claim");
 }
}
