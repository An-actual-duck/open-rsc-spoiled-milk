package orsc.graphics.three;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
/** CPU-only construction kernels; not a game FPS or transition benchmark. */
public final class NativeConstructionBenchmark {
    static volatile long sink;
    static final Constructor<RSModel> MERGE;
    static final Constructor<?> WORLD;
    static final Method ADD, BUILD;
    static {
        try {
            MERGE=RSModel.class.getDeclaredConstructor(RSModel[].class,int.class);MERGE.setAccessible(true);
            Class<?> c=Class.forName("orsc.graphics.three.World$WorldGpuChunkMeshBuilder");
            WORLD=c.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,boolean.class);WORLD.setAccessible(true);
            ADD=c.getDeclaredMethod("addFace",Renderer3DModelKind.class,int.class,int.class,int[].class,int[].class);ADD.setAccessible(true);
            BUILD=c.getDeclaredMethod("build");BUILD.setAccessible(true);
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    static RSModel[] inputs() {
        RSModel[] a=new RSModel[4];
        for(int j=0;j<a.length;j++) {
            RSModel m=a[j]=new RSModel(4000,3998);
            for(int i=0;i<4000;i++)m.insertVertex2(false,i*7,(i%997)*31,-(i%13)*17);
            for(int i=2;i<4000;i++)m.insertFace(3,new int[]{i-2,i-1,i},-1,0x334455,false);
        }
        return a;
    }
    static void merge(RSModel[] a)throws Exception {RSModel m=MERGE.newInstance(a,a.length);sink+=m.vertHead+m.faceHead;}
    static void world()throws Exception {
        Object b=WORLD.newInstance(0,10,9,0,0,false);
        int[] light={1,2,3};
        for(int i=0;i<6000;i++)ADD.invoke(b,Renderer3DModelKind.TERRAIN,-1,0x334455,new int[]{i,0,0,i+1,0,0,i,0,1},light);
        sink+=BUILD.invoke(b).hashCode();
    }
    public static void main(String[] args)throws Exception {
        RSModel[] a=inputs();for(int i=0;i<8;i++){merge(a);world();}
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();
        for(String kernel:new String[]{"merge","world"})for(int trial=0;trial<12;trial++) {
            long allocated=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
            if(kernel.equals("merge"))merge(a);else world();
            long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-allocated;
            System.out.println(kernel+","+trial+","+elapsed+","+bytes);
        }
    }
}
