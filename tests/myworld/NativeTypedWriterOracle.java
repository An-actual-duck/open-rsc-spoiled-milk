package orsc;
import java.nio.*;
import java.lang.reflect.*;
import java.security.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Compiles unchanged against the frozen pre-change full JAR and candidate full JAR.
 * Hashes all 28 raw float fields from native and public backend writers. No copied
 * candidate formula is the oracle. Compact decode follows the frozen format. */
public final class NativeTypedWriterOracle {
    static Field field(Class<?> c,String n)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    static Method method(Class<?> c,String n,Class<?>... p)throws Exception {Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m;}
    static final int[] FLOATS={0,1,2,7,8,18,19,20};
    static final int[] BYTES={3,4,5,6,15,16,17,14,24,25,26,21,22,23,27,9,10,11,12};
    static final int[] OFFSETS={32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,48,49,50,51};
    static final Method PACK,ENSURE;
    static {try {PACK=method(OpenGLWorldChunkRenderer.class,"copyChunkVertices",Renderer3DWorldChunkFrame.ChunkMesh.class,Renderer3DFrame.class,int.class,boolean.class,List.class);ENSURE=method(OpenGLWorldChunkRenderer.class,"ensureUploadBuffers",int.class,int.class);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static Renderer3DWorldChunkFrame.ChunkMesh chunk(int seed,int triangles,boolean bad){
        Random r=new Random(seed);int count=triangles*3;int[] xyz=new int[count*3],lights=new int[count],colors=new int[count],strength=new int[count],indices=new int[count],texture=new int[triangles],fallback=new int[triangles],masks=new int[triangles];float[] u=new float[count],v=new float[count];Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];Renderer3DMaterialFamily[] families=new Renderer3DMaterialFamily[triangles];
        for(int i=0;i<count;i++){xyz[i*3]=i%3*128+(i/3)%64*256;xyz[i*3+1]=(i%11)*-32;xyz[i*3+2]=(i%3==2?128:0)+i/192*256;lights[i]=(seed+i)%400-64;colors[i]=r.nextInt();strength[i]=(seed+i)&255;indices[i]=i;u[i]=(i%5-1)*.5f;v[i]=(i%7-1)/3f;}
        for(int i=0;i<triangles;i++){texture[i]=new int[]{0,12345678,-1}[i%3];fallback[i]=r.nextInt()&0xffffff;kinds[i]=Renderer3DModelKind.values()[(i+seed)%Renderer3DModelKind.values().length];families[i]=Renderer3DMaterialFamily.values()[(i+seed)%Renderer3DMaterialFamily.values().length];masks[i]=(seed+i)&255;}
        if(bad){int n=new int[]{-1,256,Integer.MAX_VALUE,Integer.MIN_VALUE}[seed%4];if((seed&4)==0)masks[triangles-1]=n;else strength[count-1]=n;}
        return new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,xyz,u,v,lights,colors,strength,indices,texture,fallback,kinds,families,null,null,masks,null,0,0,triangles,0,0,false,Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,seed+1);
    }
    static float[] read(OpenGLWorldChunkRenderer renderer,int count)throws Exception {
        if(!field(OpenGLWorldChunkRenderer.class,"packedCompact").getBoolean(renderer)){FloatBuffer b=((FloatBuffer)field(OpenGLWorldChunkRenderer.class,"vertexUploadBuffer").get(renderer)).duplicate();float[] f=new float[b.remaining()];b.get(f);return f;}
        ByteBuffer b=((ByteBuffer)field(OpenGLWorldChunkRenderer.class,"compactUploadBuffer").get(renderer)).duplicate().order(ByteOrder.nativeOrder());float[] f=new float[count*28];
        for(int n=0;n<count;n++){int at=n*52;for(int i=0;i<FLOATS.length;i++)f[n*28+FLOATS[i]]=b.getFloat(at+i*4);for(int i=0;i<BYTES.length;i++){int c=BYTES[i],value=b.get(at+OFFSETS[i])&255;f[n*28+c]=(c==14||c==21||c==22||c==23)?value:value/255f;}f[n*28+13]=f[n*28+14];if(b.get(at+47)!=0)throw new AssertionError("padding");}return f;
    }
    static void hash(MessageDigest digest,float[] values){for(float f:values){int b=Float.floatToRawIntBits(f);digest.update((byte)(b>>>24));digest.update((byte)(b>>>16));digest.update((byte)(b>>>8));digest.update((byte)b);}}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    public static void main(String[] args)throws Exception {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);
        Scene scene=new Scene(new MudClientGraphics(64,64,2),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setFrustum(0,11,7,2);byte[] pixels=new byte[4096];Arrays.fill(pixels,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x337755},0,pixels);scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
        OpenGLWorldTextureCache cache=new OpenGLWorldTextureCache(null);((Map)field(OpenGLWorldTextureCache.class,"textureRegionsById").get(cache)).put(0,new OpenGLTextureRegion(9,100,200,64,64,.125f,.25f,.375f,.5f,false));Class<?> uc=Class.forName("sun.misc.Unsafe");Object unsafe=field(uc,"theUnsafe").get(null);OpenGLShaderProgram shader=(OpenGLShaderProgram)uc.getMethod("allocateInstance",Class.class).invoke(unsafe,OpenGLShaderProgram.class);
        boolean raw=Boolean.getBoolean("fixture.raw"),projected=Boolean.getBoolean("fixture.projected"),baked=Boolean.getBoolean("fixture.baked");
        OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,cache,shader,true,true,projected,false,true,!baked,raw);
        List casters=Collections.emptyList();
        if(projected){Class<?> caster=Class.forName("orsc.OpenGLWorldChunkRenderer$ShadowProofCaster");Constructor<?> ctor=caster.getDeclaredConstructor(long.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class);ctor.setAccessible(true);casters=Collections.singletonList(ctor.newInstance(1L,-256f,-256f,2048f,-256f,0f,256f,8192f,4096f,1f));}
        if(args.length>0 && args[0].equals("benchmark")){
            RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);Renderer3DWorldChunkFrame.ChunkMesh mesh=chunk(17,16384,false);int count=mesh.getVertexCount();ENSURE.invoke(renderer,count,mesh.getIndexCount());com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();long sink=0;
            for(int i=-24;i<32;i++){long allocated=bean.getThreadAllocatedBytes(id),start=System.nanoTime();PACK.invoke(renderer,mesh,frame,count,true,Collections.emptyList());long ns=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-allocated;sink+=field(OpenGLWorldChunkRenderer.class,"packedCompact").getBoolean(renderer)?1:0;if(i>=0)System.out.println("pack,"+i+","+count+","+ns+","+bytes);}
            System.out.println("# consumed="+sink+"; synthetic FACETED packing only, not game latency");
        }else{
            MessageDigest nativeDigest=MessageDigest.getInstance("SHA-256"),backendDigest=MessageDigest.getInstance("SHA-256");int packed=0,cases=0,shadowed=0;
            for(RendererLightingSettings.Mode light:RendererLightingSettings.Mode.values())for(RendererGeometrySettings.Mode geometry:RendererGeometrySettings.Mode.values())for(int seed=0;seed<256;seed++)for(boolean bad:new boolean[]{false,true}){
                RendererLightingSettings.setMode(light);RendererGeometrySettings.setMode(geometry);Renderer3DWorldChunkFrame.ChunkMesh mesh=chunk(seed,3,bad);int count=mesh.getVertexCount();ENSURE.invoke(renderer,count,mesh.getIndexCount());for(boolean atlas:new boolean[]{false,true}){PACK.invoke(renderer,mesh,frame,count,atlas,casters);float[] packedValues=read(renderer,count);if(projected)for(int n=0;n<count;n++)if(packedValues[n*28+13]!=packedValues[n*28+14])shadowed++;hash(nativeDigest,packedValues);if(field(OpenGLWorldChunkRenderer.class,"packedCompact").getBoolean(renderer))packed++;}hash(backendDigest,renderer.copyBackendChunkVertices(mesh,frame));cases++;
            }
            if(projected&&shadowed==0)throw new AssertionError("empty baked-shadow proof");
            System.out.println("native="+hex(nativeDigest.digest())+" backend="+hex(backendDigest.digest())+" cases="+cases+" compact="+packed+" shadowed="+shadowed);
        }
        frame.release();
    }
}
