package orsc.graphics.three;
import java.lang.reflect.*;import java.security.MessageDigest;import java.util.*;
import orsc.MiscFunctions;import orsc.BrowserFrameSinkRegistry;import orsc.util.FastMath;
/** Frozen model/Scene differential. Counts are work evidence, not FPS. */
public final class BrowserProjectionReuseFixture {
 static int checks;static MessageDigest hash;static void require(boolean x,String why){checks++;if(!x)throw new AssertionError(why);}
 static void emit(String s)throws Exception{hash.update(s.getBytes("UTF-8"));hash.update((byte)0);}
 static void snapshot(RSModel m)throws Exception{emit(Boolean.toString(m.m_dc));for(int[] a:new int[][]{m.vertexParam6,m.vertexParam2,m.vertXRot,m.vertYRot,m.vertZRot})emit(Arrays.toString(a));}
 static String cause(Runnable r){try{r.run();return"ok";}catch(Throwable e){return e.getClass().getName()+":"+e.getMessage();}}
 static void field(RSModel m,String name,Object value)throws Exception{Field f=RSModel.class.getDeclaredField(name);f.setAccessible(true);f.set(m,value);}
 static RSModel dense(int count){RSModel m=new RSModel(count,1);for(int n=0;n<count;n++)m.insertVertex((n%17-8)*37,(n%9-4)*13,(n/17-3)*43);m.insertFace(3,new int[]{0,1,2},-12345,-12345,false);return m;}
 static void frustum(){MiscFunctions.frustumMinX=MiscFunctions.frustumMinY=MiscFunctions.frustumNearZ=Integer.MAX_VALUE;MiscFunctions.frustumMaxX=MiscFunctions.frustumMaxY=MiscFunctions.frustumFarZ=Integer.MIN_VALUE;}
 static void direct(boolean enabled)throws Exception{
  BrowserProjectionReuse cache=new BrowserProjectionReuse();Random r=new Random(94112);int[] key={-300,9,100,-900,112,512,0,5};RSModel m=dense(127);
  int savedTrig=FastMath.trigTable1024[112];
  for(int f=0;f<1800;f++){
   if(f%31==0){key[0]=r.nextInt(400)-200;key[2]=r.nextInt(1200)-600;key[3]=r.nextInt(1200)-600;key[4]=r.nextInt(1024);key[5]=r.nextInt(1024);key[6]=r.nextInt(1024);key[7]=new int[]{1,5,32,512}[f%4];}
   frustum();cache.begin(enabled,key[0],key[1],key[2],key[3],key[4],key[5],key[6],key[7]);
   switch(f%43){
    case 1:m.setTranslate(f%17-8,f%13-6,f%23-11);break;
    case 2:m.vertX[5]^=271;break; // direct mutation, bypass transform revision
    case 3:Arrays.fill(m.vertexParam6,123456);Arrays.fill(m.vertZRot,-12345);break;
    case 4:m.vertexParam2=new int[m.vertexParam2.length];break;
    case 5:field(m,"vertXTransform",m.vertX.clone());break;
    case 6:FastMath.trigTable1024[key[4]]^=3;break; // mutable table within admitted frame
    case 7:MiscFunctions.frustumMinX=Integer.MIN_VALUE;break; // original offscreen outputs untouched
    default:break;
   }
   final RSModel model=m;final int[] p=key;String error=cause(()->{if(enabled)model.rotate1024WithReuse(cache,p[0],p[1],p[2],(byte)-122,p[3],p[4],p[5],p[6],p[7]);else model.rotate1024(p[0],p[1],p[2],(byte)-122,p[3],p[4],p[5],p[6],p[7]);});
   emit(error);snapshot(m);cache.end();require(cache.stats()[3]<=BrowserProjectionReuse.MAX_BYTES,"budget");
  }
  FastMath.trigTable1024[112]=savedTrig;
  // Literal malformed/aliased/extreme fallback and its partial output/error order.
  for(int test=0;test<15;test++){
   RSModel a=dense(64);frustum();for(int warm=0;warm<3;warm++){cache.begin(enabled,0,9,0,0,0,0,0,5);if(enabled)a.rotate1024WithReuse(cache,0,9,0,(byte)-122,0,0,0,0,5);else a.rotate1024(0,9,0,(byte)-122,0,0,0,0,5);cache.end();}
   if(test==0)a.vertexParam6=null;if(test==1)a.vertexParam2=new int[4];if(test==2)a.vertXRot=a.vertexParam6;if(test==3)field(a,"vertXTransform",a.vertXRot);if(test==4)a.vertHead=1000;if(test==5)a.vertHead=-1;if(test==6){a.vertX[0]=Integer.MAX_VALUE;a.vertZ[1]=Integer.MIN_VALUE;}
   int near=test==7?0:test==8?-1:5,ry=test==9?2048:0,vp=test==10?Integer.MAX_VALUE:9;byte sentinel=(byte)(test==11?0:-122);if(test==12)field(a,"m_Yb",2);if(test==13)a.vertZRot=new int[1];if(test==14)field(a,"vertYTransform",null);
   cache.begin(enabled,0,vp,0,0,ry,0,0,near);String error=cause(()->{if(enabled)a.rotate1024WithReuse(cache,0,vp,0,sentinel,0,ry,0,0,near);else a.rotate1024(0,vp,0,sentinel,0,ry,0,0,near);});emit(error);snapshot(a);cache.end();cache.clear();
  }
  System.out.println("DIRECT_STATS="+Arrays.toString(cache.stats()));
 }
 static void budget()throws Exception{
  BrowserProjectionReuse c=new BrowserProjectionReuse();frustum();RSModel[] models=new RSModel[300];for(int i=0;i<models.length;i++)models[i]=dense(4096);
  for(int f=0;f<5;f++){c.begin(true,0,9,0,-400,0,0,0,5);for(RSModel m:models)m.rotate1024WithReuse(c,0,9,0,(byte)-122,-400,0,0,0,5);c.end();require(c.stats()[3]<=BrowserProjectionReuse.MAX_BYTES,"byte cap");require(c.stats()[2]<=BrowserProjectionReuse.MAX_ENTRIES,"entry cap");}
  require(c.stats()[0]>0&&c.stats()[4]>0,"cache never reused/refused active admission");int before=c.stats()[0];c.begin(true,0,9,0,-400,0,0,0,5);models[0].rotate1024WithReuse(c,0,9,0,(byte)-122,-400,0,0,0,5);c.end();require(c.stats()[2]==1,"retired entries retained");require(c.stats()[0]>before,"hot first entry was thrashed");c.remove(models[0]);require(c.stats()[3]==0,"removal retained bytes");c.clear();
  System.out.println("BUDGET_STATS="+Arrays.toString(c.stats()));
 }
 static void scene(boolean enabled)throws Exception{
  System.setProperty("spoiledmilk.browserProjectionReuse",String.valueOf(enabled));
  for(String s:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster","browserOverlayCoverage","browserEarlyFaceCull"})System.setProperty("spoiledmilk."+s,"true");System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
  for(int mode=0;mode<4;mode++){
   BrowserFrameSinkRegistry.install(null);if(mode<3)BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(mode==2));BrowserWallRoofOwnershipFixture.View v=new BrowserWallRoofOwnershipFixture.View(mode!=1);RSModel d=dense(127);d.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);v.s.addModel(d);
   for(int f=0;f<240;f++){
    int phase=f/20;if(f%29==0)d.setTranslate(f%7*20,f%3,f%11*15);if(f%41==0)d.vertX[0]^=31;if(f%37==0)d.faceIndices[0]=new int[]{2,1,0};if(f%53==0){v.s.removeModel(d);v.s.addModel(d);}
    BrowserWallRoofOwnershipFixture.Result out=v.draw(f%3==0?0:f%3==1?256:511,f%3==0?0:f%3==1?192:383,new int[]{20,600,100}[phase%3],new int[]{640,768,900}[phase%3],phase%2*256,new int[]{-12345,0,Scene.TRANSPARENT}[phase%3],f%47==0);
    emit(out.picks);emit(out.anchors);emit(out.faces);emit(out.resident);emit(Arrays.toString(out.pixels));
   }
   v.s.removeModels(new RSModel[]{d},1);if(enabled){int[] stats=v.s.getBrowserProjectionReuseStats();require(stats[3]<=BrowserProjectionReuse.MAX_BYTES,"Scene budget");if(mode==0){require(stats[0]>0,"Scene never reused actual projections");require(stats[2]==0&&stats[3]==0,"Batch removal retained retired model");}else require(stats[2]==0&&stats[3]==0,"Native/CPU-depth retained cache");}
  }
  BrowserFrameSinkRegistry.install(null);
 }
 static void bench()throws Exception{
  for(boolean moving:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}){
   BrowserProjectionReuse cache=new BrowserProjectionReuse();RSModel[] models=new RSModel[64];for(int n=0;n<models.length;n++)models[n]=dense(127);frustum();long start=0;int checksum=0;
   for(int frame=0;frame<2400;frame++){if(frame==400)start=System.nanoTime();int x=moving?frame%119:0;if(enabled)cache.begin(true,-300,9,x,-900,112,512,0,5);BrowserProjectionReuse active=enabled&&cache.isActive()?cache:null;for(RSModel m:models){if(active!=null)m.rotate1024WithReuse(active,-300,9,x,(byte)-122,-900,112,512,0,5);else m.rotate1024(-300,9,x,(byte)-122,-900,112,512,0,5);}if(enabled)cache.end();checksum+=models[frame%64].vertexParam6[frame%127];}
   long elapsed=System.nanoTime()-start;System.out.println("BENCH "+moving+" "+enabled+" nsPerFrame="+(elapsed/2000.0)+" checksum="+checksum+" stats="+Arrays.toString(cache.stats()));
  }
 }
 public static void main(String[] args)throws Exception{hash=MessageDigest.getInstance("SHA-256");if(args[0].equals("default")){System.clearProperty("spoiledmilk.browserProjectionReuse");Scene s=new Scene(new orsc.graphics.two.MudClientGraphics(64,64,1),4,16,4);Field f=Scene.class.getDeclaredField("browserProjectionReuse");f.setAccessible(true);require(f.get(s)==null,"Default allocated projection cache");require(!s.isBrowserProjectionReuseEnabled(),"Default enabled cache");System.out.println("PASS defaultOFF/no cache allocation");return;}if(args[0].equals("bench")){bench();return;}boolean enabled=Boolean.parseBoolean(args[0]);if(args.length>1&&args[1].equals("budget")){budget();return;}direct(enabled);scene(enabled);StringBuilder s=new StringBuilder();for(byte b:hash.digest())s.append(String.format("%02x",b&255));System.out.println("DIGEST="+s);}
}
