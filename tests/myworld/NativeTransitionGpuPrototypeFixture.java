package orsc;
import java.nio.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;
/** Real GL pending/live laboratory; synthetic bytes, NOT a populated-game scheduler. */
public final class NativeTransitionGpuPrototypeFixture {
    static LwjglBindings gl;
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static final class Gpu implements NativeTransitionUploadPrototype.Backend {
        final Method allocate,write,read;final Map<Integer,Integer> sizes=new HashMap<Integer,Integer>();
        final ByteBuffer scratch=ByteBuffer.allocateDirect(65536);
        Gpu()throws Exception{Class<?> c=Class.forName("org.lwjgl.opengl.GL15");allocate=c.getMethod("glBufferData",int.class,long.class,int.class);write=c.getMethod("glBufferSubData",int.class,long.class,ByteBuffer.class);read=c.getMethod("glGetBufferSubData",int.class,long.class,ByteBuffer.class);}
        public int allocate(int n)throws Exception{int id=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,id);allocate.invoke(null,gl.GL_ARRAY_BUFFER,(long)n,gl.GL_STATIC_DRAW);sizes.put(id,n);return id;}
        public void upload(int id,int offset,ByteBuffer b)throws Exception{scratch.clear();scratch.put(b).flip();gl.glBindBuffer(gl.GL_ARRAY_BUFFER,id);write.invoke(null,gl.GL_ARRAY_BUFFER,(long)offset,scratch);}
        public void delete(int id)throws Exception{require(sizes.remove(id)!=null,"double-delete");gl.glDeleteBuffers(id);}
        void verify(int id,byte[] expected)throws Exception{gl.glBindBuffer(gl.GL_ARRAY_BUFFER,id);ByteBuffer b=ByteBuffer.allocateDirect(expected.length);read.invoke(null,gl.GL_ARRAY_BUFFER,0L,b);for(int i=0;i<expected.length;i++)require(b.get(i)==expected[i],"pending upload mismatch");}
    }
    static NativeTransitionUploadPrototype.FrameToken frame(NativeTransitionUploadPrototype.Identity id,long n){return new NativeTransitionUploadPrototype.FrameToken(id,n,n,n,new byte[]{(byte)n});}
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[0]);Files.createDirectories(out);gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW");long window=0;
        try{
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(64,64,"Hidden sliced upload laboratory",0,0);require(window!=0,"window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();System.out.println("GL_RENDERER="+gl.glGetString(0x1f01));
            // Match the observed total bytes and67 chunks, not the game's actual contents/distribution.
            byte[][] bytes=new byte[201][];int left=141441120;
            for(int i=0;i<bytes.length;i++){int n=i==bytes.length-1?left:(i%3==0?1964460:70152);n=Math.min(n,left);bytes[i]=new byte[n];for(int j=0;j<n;j++)bytes[i][j]=(byte)(i+j);left-=n;}require(left==0,"total");
            for(int cap:new int[]{4*1024*1024,16*1024*1024}){
                Gpu backend=new Gpu();NativeTransitionUploadPrototype s=new NativeTransitionUploadPrototype(backend,512L*1024*1024,cap,8*1024*1024);
                NativeTransitionUploadPrototype.Identity a=new NativeTransitionUploadPrototype.Identity(1,0,1,1,1),b=new NativeTransitionUploadPrototype.Identity(2,0,1,1,1);
                NativeTransitionUploadPrototype.FrameToken initial=frame(a,1);s.request(new NativeTransitionUploadPrototype.Snapshot(a,new byte[][]{new byte[1024]}),initial,0);while(!s.ready())s.tick(s.generation(),0);require(s.publish(s.generation(),initial,true,0),"initial");NativeTransitionUploadPrototype.Published live=s.live();
                long copyStart=System.nanoTime();NativeTransitionUploadPrototype.Snapshot snapshot=new NativeTransitionUploadPrototype.Snapshot(b,bytes);long copyNanos=System.nanoTime()-copyStart;
                NativeTransitionUploadPrototype.FrameToken latest=frame(b,1);require(s.request(snapshot,latest,0),"admission");long generation=s.generation();int ticks=0;long max=0,total=0;
                List<String> csv=new ArrayList<String>();csv.add("tick,submittedBytes,cpuNanos,modeledOldImageAgeNanos");
                while(!s.ready()){
                    latest=frame(b,++ticks);s.updateFrame(latest);long start=System.nanoTime();int submitted=s.tick(generation,ticks*16666667L);long elapsed=System.nanoTime()-start;total+=elapsed;max=Math.max(max,elapsed);
                    require(submitted<=cap&&s.live()==live&&!s.allowsInput(initial),"partial scene or input publication");csv.add(ticks+","+submitted+","+elapsed+","+(ticks*16666667L));
                }
                // Synchronous verification is deliberately AFTER all timed submission slices.
                backend.verify(live.handles[0],new byte[1024]);require(s.publish(generation,latest,true,ticks*16666667L),"complete publication");
                for(int i=0;i<bytes.length;i++)backend.verify(s.live().handles[i],bytes[i]);
                Files.write(out.resolve("slices-"+cap+".csv"),csv);System.out.println("PASS capBytes="+cap+" ticks="+ticks+" submittedBytes=141441120 cpuSubmissionMs="+total/1e6+" maxTickMs="+max/1e6+" upfrontImmutableCopyMs="+copyNanos/1e6+" modeled60HzReadinessMs="+ticks*1000.0/60+" (not display or game latency)");s.close();require(backend.sizes.isEmpty(),"leak");
            }
        }finally{if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
