package orsc.graphics.three;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.util.*;
import orsc.MiscFunctions;
import orsc.BrowserFrameSinkRegistry;
import orsc.util.FastMath;

/** Frozen real Scene/RSModel comparison with an injected literal-Java backend.
 * This tests Java segmentation/ordering, not browser JNI or performance. */
public final class BrowserBatchedModelKernelFixture {
    static int checks;
    static void check(boolean ok,String why) { checks++; if(!ok)throw new AssertionError(why); }
    static Field field(Class<?> owner,String name)throws Exception {Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
    static final String[] ARRAYS={"vertXTransform","vertYTransform","vertZTransform","vertexParam6","vertexParam2","vertXRot","vertYRot","vertZRot"};
    static int[][] arrays(RSModel m)throws Exception {int[][] a=new int[8][];for(int n=0;n<8;n++)a[n]=(int[])field(RSModel.class,ARRAYS[n]).get(m);return a;}
    static void text(MessageDigest d,String s)throws Exception {d.update(s.getBytes("UTF-8"));d.update((byte)0);}
    static String hex(MessageDigest d){StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02x",b&255));return s.toString();}
    static void modelDigest(MessageDigest d,RSModel m)throws Exception {
        if(m==null){text(d,"null");return;}text(d,"visible="+m.m_dc+" cache="+m.m_Yb);
        for(int[] a:arrays(m)){if(a==null){text(d,"null-array");continue;}text(d,"length="+a.length);for(int v:a){d.update((byte)(v>>>24));d.update((byte)(v>>>16));d.update((byte)(v>>>8));d.update((byte)v);}}
    }
    static final class Oracle implements BrowserBatchedModelKernel.Backend {
        final boolean reject,fail;int calls,models,vertices;
        Oracle(boolean reject,boolean fail){this.reject=reject;this.fail=fail;}
        public boolean transform(int size,int[][] rows,int[] counts,int[] camera,int[] trig,int[] trigSource){
            calls++;if(fail)throw new IllegalStateException("Injected backend failure");if(reject)return false;
            int[] liveTable=FastMath.trigTable1024;
            int[] packetTable=liveTable==null?null:liveTable.clone();
            for(int axis=0;axis<3;axis++)if(camera[5+axis]!=0){packetTable[camera[5+axis]]=trig[axis*2];packetTable[camera[5+axis]+1024]=trig[axis*2+1];}
            FastMath.trigTable1024=packetTable;
            try {
                for(int n=0;n<size;n++)if(counts[n]>0){
                    int[][] a=new int[8][];System.arraycopy(rows,n*8,a,0,8);
                    int[] f={MiscFunctions.frustumMinX,MiscFunctions.frustumMaxX,MiscFunctions.frustumMinY,MiscFunctions.frustumMaxY,MiscFunctions.frustumNearZ,MiscFunctions.frustumFarZ};
                    RSModel m=NativeModelKernelReferenceFixture.model(counts[n],a);
                    MiscFunctions.frustumMinX=f[0];MiscFunctions.frustumMaxX=f[1];MiscFunctions.frustumMinY=f[2];MiscFunctions.frustumMaxY=f[3];MiscFunctions.frustumNearZ=f[4];MiscFunctions.frustumFarZ=f[5];
                    NativeModelKernelReferenceFixture.rotate(m,camera);models++;vertices+=counts[n];
                }
                return true;
            }catch(RuntimeException e){throw e;}catch(Exception e){throw new AssertionError(e);}
            finally {FastMath.trigTable1024=liveTable;}
        }
    }
    static RSModel model(int seed)throws Exception {
        int[][] a=new int[8][7];for(int n=0;n<8;n++)for(int v=0;v<a[n].length;v++)a[n][v]=n<3?(seed*31+v*17+n*13)-2000:-777;
        RSModel m=NativeModelKernelReferenceFixture.model(4,a);m.m_dc=false;return m;
    }
    static void culled(RSModel m)throws Exception {field(RSModel.class,"minX").setInt(m,1);field(RSModel.class,"maxX").setInt(m,2);}
    static void noReferences(BrowserBatchedModelKernel batch)throws Exception {
        for(Object x:(Object[])field(BrowserBatchedModelKernel.class,"models").get(batch))check(x==null,"Retained model");
        for(Object x:(Object[])field(BrowserBatchedModelKernel.class,"rows").get(batch))check(x==null,"Retained primitive array");
        check(((Map<?,?>)field(BrowserBatchedModelKernel.class,"roles").get(batch)).isEmpty(),"Retained role map");
        check(field(BrowserBatchedModelKernel.class,"trigSource").get(batch)==null,"Retained trig table");
    }
    static String rotateSequence(List<RSModel> models,int[] camera,BrowserBatchedModelKernel batch)throws Exception {
        if(batch!=null)batch.begin(camera[1],camera[3],camera[0],camera[2],camera[6],camera[5],camera[7],camera[4]);
        try {
            for(RSModel m:models){
                if(batch!=null&&batch.enqueue(m))continue;
                if(batch!=null){batch.flush();batch.literalModel();}
                NativeModelKernelReferenceFixture.rotate(m,camera);
            }
            if(batch!=null)batch.flush();return "ok";
        }catch(Throwable e){return e.getClass().getName()+":"+e.getMessage();}
        finally {if(batch!=null){batch.discard();noReferences(batch);}}
    }
    static void edges(boolean current,boolean reject)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long packets=0,numeric=0,metadata=0,aliases=0,literal=0;
        for(int scenario=0;scenario<13;scenario++){
            int[] old=FastMath.trigTable1024;FastMath.trigTable1024=old.clone();
            try {
                List<RSModel> ms=new ArrayList<RSModel>();int count=scenario<2?300:5;
                for(int n=0;n<count;n++)ms.add(model(n+1));
                int[] camera={17,-33,91,9,8,1,1,1};
                if(scenario==1)for(int n=0;n<count;n++){if(n%7==0)culled(ms.get(n));else if(n%11==0){ms.get(n).vertHead=0;ms.get(n).vertexParam6=null;}}
                if(scenario==2){ms.get(1).m_Yb=1;field(RSModel.class,"vertXTransform").set(ms.get(1),ms.get(0).vertXRot);}
                if(scenario==3)field(RSModel.class,"vertXTransform").set(ms.get(2),new int[2]);
                if(scenario==4)ms.set(2,null);
                if(scenario==5)field(RSModel.class,"vertYTransform").set(ms.get(1),ms.get(0).vertXRot);
                if(scenario==6)ms.get(1).vertXRot=arrays(ms.get(0))[1];
                if(scenario==7)ms.get(1).vertYRot=ms.get(0).vertXRot;
                if(scenario==8)ms.get(0).vertexParam6=FastMath.trigTable1024;
                if(scenario==9)ms.get(1).vertexParam6=FastMath.trigTable1024;
                if(scenario==10){culled(ms.get(0));camera[4]=0;arrays(ms.get(1))[2][0]=camera[2];}
                if(scenario==11){culled(ms.get(0));camera[5]=-1;}
                if(scenario==12)ms.get(1).setInteractionOnly(true);
                Oracle oracle=new Oracle(reject,false);BrowserBatchedModelKernel batch=current?new BrowserBatchedModelKernel(oracle):null;
                String outcome=rotateSequence(ms,camera,batch);text(digest,scenario+":"+outcome);
                for(RSModel m:ms)modelDigest(digest,m);
                if(batch!=null){int[] w=batch.work();packets+=w[2];numeric+=w[3];metadata+=w[5];literal+=w[6];aliases+=w[8];if(scenario==0&&!reject){check(w[2]==3&&w[3]==300&&w[11]==128,"Large clean group was not batched128/128/44");}}
            }finally{FastMath.trigTable1024=old;}
        }
        System.out.println("EDGE_DIGEST="+hex(digest));
        System.out.println("EDGE_WORK packets="+packets+" numeric="+numeric+" metadata="+metadata+" aliases="+aliases+" literal="+literal);
    }
    static void configure(){
        for(String f:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster","browserEarlyFaceCull"})System.setProperty("spoiledmilk."+f,"true");
        System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
    }
    static void scene(boolean current,boolean requested,boolean reject)throws Exception {
        configure();System.setProperty("spoiledmilk.browserBatchedModelKernel",String.valueOf(requested));MessageDigest hash=MessageDigest.getInstance("SHA-256");
        int nativeCalls=0,numeric=0,frames=0;
        for(int mode=0;mode<3;mode++){
            BrowserFrameSinkRegistry.install(null);if(mode!=0)BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(mode==2));
            BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(true);
            Oracle oracle=new Oracle(reject,false);BrowserBatchedModelKernel batch=new BrowserBatchedModelKernel(oracle);
            if(current&&requested)field(Scene.class,"browserBatchedModelKernel").set(view.s,batch);
            for(int frame=0;frame<96;frame++){
                if(frame%9==0)view.front.addRotation(0,1,0);
                if(frame%17==0)view.s.forceLegacyWorldRasterOnce();
                view.s.setRendererSignedWorldLevel(new int[]{-2,-1,0,1,10}[frame%5]);
                BrowserWallRoofOwnershipFixture.Result r=view.draw(new int[]{256,210,0,511}[frame%4],new int[]{192,160,0,383}[frame%4],new int[]{20,100,600}[frame%3],new int[]{640,768,900}[frame%3],frame%2*256,-12345,false);
                text(hash,r.picks);text(hash,r.anchors);text(hash,r.faces);text(hash,r.resident);ByteBuffer pixels=ByteBuffer.allocate(r.pixels.length*4);for(int p:r.pixels)pixels.putInt(p);hash.update(pixels.array());
                if(current&&requested)noReferences(batch);frames++;
            }
            if(current&&requested&&mode==0)check(oracle.calls==0,"Batch escaped browser capability");
            if(current&&requested&&mode>0)check(oracle.calls>0,"Actual Scene never used batching");
            nativeCalls+=oracle.calls;numeric+=oracle.models;
        }
        BrowserFrameSinkRegistry.install(null);
        System.out.println("SCENE_DIGEST="+hex(hash));System.out.println("SCENE_WORK frames="+frames+" packets="+nativeCalls+" numeric="+numeric);
    }
    static void failureCleanup()throws Exception {
        BrowserBatchedModelKernel batch=new BrowserBatchedModelKernel(new Oracle(false,true));List<RSModel> ms=Arrays.asList(model(1),model(2));
        String result=rotateSequence(ms,new int[]{0,0,0,9,8,1,1,1},batch);check(result.contains("Injected backend failure"),"Injected failure not propagated");noReferences(batch);
        System.out.println("FAILURE_CLEANUP_PASS checks="+checks);
    }
    public static void main(String[] args)throws Exception {
        if(args[0].equals("edges"))edges(args[1].equals("current"),args.length>2&&args[2].equals("reject"));
        else if(args[0].equals("scene"))scene(args[1].equals("current"),args[2].equals("on"),args.length>3&&args[3].equals("reject"));
        else failureCleanup();
    }
}
