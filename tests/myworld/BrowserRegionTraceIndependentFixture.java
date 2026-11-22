package orsc;
import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Records production output; only a separately compiled frozen baseline is expected. */
public final class BrowserRegionTraceIndependentFixture {
 static final MessageDigest digest;static final DataOutputStream out;static int passes,uploads,replaces,deletes,profileChecks;static long submittedVertices,submittedBytes;
 static {try{digest=MessageDigest.getInstance("SHA-256");out=new DataOutputStream(new DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[]b,int o,int n){}},digest));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static void ints(int[]a)throws IOException{out.writeInt(a.length);for(int x:a)out.writeInt(x);}
 static void floats(float[]a)throws IOException{out.writeInt(a.length);for(float x:a)out.writeInt(Float.floatToRawIntBits(x));}
 static void text(String s)throws IOException{out.writeUTF(s);}
 static final class Backend implements BrowserWebGLBridge.ChunkBackend {
  final Set<Integer> live=new HashSet<Integer>();boolean failUpload,failReplace;
  public void upload(int id,float[]v,int[]i,int[]b){try{text("U");out.writeInt(id);floats(v);ints(i);ints(b);if(failUpload){failUpload=false;throw new IllegalStateException("injected upload");}if(!live.add(id))throw new AssertionError("duplicate ID");uploads++;submittedVertices+=v.length/28;submittedBytes+=(long)v.length*4+(long)i.length*4+(long)b.length*4;}catch(IOException e){throw new RuntimeException(e);}}
  public void replace(int id,float[]v,int[]i,int[]b){try{text("R");out.writeInt(id);floats(v);ints(i);ints(b);if(failReplace){failReplace=false;throw new IllegalStateException("injected replace");}if(!live.contains(id))throw new AssertionError("absent replace");replaces++;submittedVertices+=v.length/28;submittedBytes+=(long)v.length*4+(long)i.length*4+(long)b.length*4;}catch(IOException e){throw new RuntimeException(e);}}
  public void delete(int[] ids){try{text("D");ints(ids);for(int id:ids)if(!live.remove(id))throw new AssertionError("absent delete");deletes+=ids.length;}catch(IOException e){throw new RuntimeException(e);}}
 }
 static Renderer3DWorldChunkFrame.ChunkMesh mesh(int id,int plane,int role,int triangles,int pose,long signature){
  int vertices=triangles*3;int[]xyz=new int[vertices*3],ix=new int[vertices],light=new int[vertices],texture=new int[triangles],color=new int[triangles];float[]u=new float[vertices],v=new float[vertices];Renderer3DModelKind[]kind=new Renderer3DModelKind[triangles];
  for(int t=0;t<triangles;t++){int p=t*9;xyz[p]=t*2+pose;xyz[p+1]=-pose;xyz[p+3]=t*2+128;xyz[p+4]=-32;xyz[p+6]=t*2;xyz[p+8]=128;ix[t*3]=t*3;ix[t*3+1]=t*3+1;ix[t*3+2]=t*3+2;texture[t]=-1;color[t]=0x336699+id;kind[t]=Renderer3DModelKind.GAME_OBJECT;u[t*3+1]=1;v[t*3+2]=1;}
  return new Renderer3DWorldChunkFrame.ChunkMesh(plane,id,id,0,0,xyz,u,v,light,ix,texture,color,kind,null,0,0,0,true,role,signature);
 }
 static void accounting(BrowserWebGLBridge bridge)throws Exception{Field f=BrowserWebGLBridge.class.getDeclaredField("residentChunkBytes");f.setAccessible(true);out.writeLong(f.getLong(bridge));f=BrowserWebGLBridge.class.getDeclaredField("chunks");f.setAccessible(true);out.writeInt(((Map<?,?>)f.get(bridge)).size());}
 static Object beginProfile(BrowserWebGLBridge bridge)throws Exception{
  if(!Boolean.getBoolean("fixture.enableProfile"))return null;Class<?>type=Class.forName("orsc.BrowserLoadProfile");Constructor<?>ctor=type.getDeclaredConstructor(long.class,long.class);ctor.setAccessible(true);Object sample=ctor.newInstance(1L,(long)passes+1);Field f=BrowserWebGLBridge.class.getDeclaredField("loadProfile");f.setAccessible(true);f.set(bridge,sample);Method enter=type.getDeclaredMethod("enter",int.class);enter.setAccessible(true);enter.invoke(sample,13);return sample;
 }
 static void endProfile(BrowserWebGLBridge bridge,Object sample,int u,int r,long v,long b)throws Exception{
  if(sample==null)return;Field f=BrowserWebGLBridge.class.getDeclaredField("loadProfile");f.setAccessible(true);f.set(bridge,null);Method finish=sample.getClass().getDeclaredMethod("finish",int.class,long.class);finish.setAccessible(true);double[]values=(double[])finish.invoke(sample,0,0L);if(values.length!=36||values[0]!=1||values[23]!=uploads-u||values[24]!=replaces-r||values[25]!=submittedVertices-v||values[26]!=submittedBytes-b)throw new AssertionError("profile success counts/bytes mismatch");for(double x:values)if(!Double.isFinite(x)||x<0)throw new AssertionError("invalid profile metric");Field data=sample.getClass().getDeclaredField("values");data.setAccessible(true);values[0]=99;if(((double[])data.get(sample))[0]!=1)throw new AssertionError("profile snapshot aliases source");profileChecks++;
 }
 static void prepare(String label,BrowserWebGLBridge bridge,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh[]meshes)throws Exception{
  text(label);Renderer3DWorldChunkFrame world=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(meshes));frame.setWorldChunkFrame(world);
  for(int n=0;n<=meshes.length+1;n++){
   Set<WorldChunkBufferKey>active=BrowserWebGLBridge.activeKeys(world);bridge.evictInactive(active);Renderer3DFrame old=RendererFrameLighting.bind(frame);BrowserWebGLBridge.ChunkDraws d;
   Object sample=beginProfile(bridge);int beforeU=uploads,beforeR=replaces;long beforeV=submittedVertices,beforeB=submittedBytes;try{d=bridge.prepareChunks(frame,world,active);}finally{RendererFrameLighting.restore(old);endProfile(bridge,sample,beforeU,beforeR,beforeV,beforeB);}passes++;text("draw");ints(d.draws);out.writeInt(d.remainingRepacks);accounting(bridge);
   if(d.draws.length==meshes.length*8&&d.remainingRepacks==0)return;
  }
  throw new AssertionError("no progress");
 }
 static void attempt(String label,BrowserWebGLBridge bridge,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh[]meshes)throws Exception{try{prepare(label,bridge,frame,meshes);text("success");}catch(RuntimeException e){text(e.getClass().getName()+":"+e.getMessage());accounting(bridge);}}
 public static void main(String[]args)throws Exception{
  System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
  Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();Backend backend=new Backend();BrowserWebGLBridge bridge=new BrowserWebGLBridge(backend);int fixed=Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,animated=Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS;
  try{
   Renderer3DWorldChunkFrame.ChunkMesh[]all=new Renderer3DWorldChunkFrame.ChunkMesh[10];for(int n=0;n<10;n++)all[n]=mesh(n,0,fixed,4000,0,1000+n);Renderer3DWorldChunkFrame.ChunkMesh[]first=Arrays.copyOfRange(all,0,7),next=Arrays.copyOfRange(all,3,10);
   prepare("first",bridge,frame,first);prepare("forward",bridge,frame,next);prepare("return",bridge,frame,first);prepare("forward-again",bridge,frame,next);
   Renderer3DWorldChunkFrame.ChunkMesh[]rebased=new Renderer3DWorldChunkFrame.ChunkMesh[first.length];for(int n=0;n<first.length;n++)rebased[n]=first[n].rebaseStaticObjectPresentation(8,8,128,-128);prepare("rebase",bridge,frame,rebased);
   RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);prepare("settings",bridge,frame,rebased);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);prepare("settings-return",bridge,frame,first);
   prepare("negative",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{mesh(0,-1,fixed,1000,0,1000)});prepare("upper",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{mesh(0,2,fixed,1000,0,1000)});
   Renderer3DWorldChunkFrame.ChunkMesh a=mesh(17,-1,animated,2,1,9000),b=mesh(17,-1,animated,2,2,9001),c=mesh(17,-1,animated,3,3,9002);prepare("pose-a",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{a});backend.failReplace=true;attempt("pose-b-fail",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{b});prepare("pose-b-retry",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{b});prepare("shape",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{c});
   backend.failUpload=true;Renderer3DWorldChunkFrame.ChunkMesh fresh=mesh(30,0,fixed,1,0,12345);attempt("upload-fail",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{fresh});prepare("upload-retry",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[]{fresh});prepare("empty",bridge,frame,new Renderer3DWorldChunkFrame.ChunkMesh[0]);accounting(bridge);out.flush();
   StringBuilder hex=new StringBuilder();for(byte x:digest.digest())hex.append(String.format("%02x",x&255));System.out.println("TRACE "+hex+" passes="+passes+" uploads="+uploads+" replaces="+replaces+" deletes="+deletes+"; raw production bytes/cache/order, no GPU/FPS");
  System.out.println("PROFILE checks="+profileChecks+"; CPU-only injected per-attempt sample, no native loadingWork");
  }finally{frame.release();}
 }
}
