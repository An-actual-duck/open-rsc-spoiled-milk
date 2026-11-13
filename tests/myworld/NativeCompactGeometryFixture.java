package orsc;
import java.nio.*;
import java.lang.reflect.*;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Full-JAR CPU proof, including the actual native atlas writer, not browser packing. */
public final class NativeCompactGeometryFixture {
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    static Method method(Class<?> c,String n,Class<?>... p)throws Exception{Method m=c.getDeclaredMethod(n,p);m.setAccessible(true);return m;}
    static long checks;
    static void equal(float a,float b,String m){require(Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b),m+" "+a+" != "+b);checks++;}
    static void verify(float[] source,ByteBuffer b){
        for(int i=0;i<NativeResidentGeometry.FLOAT_SOURCE.length;i++)equal(source[NativeResidentGeometry.FLOAT_SOURCE[i]],b.getFloat(i*4),"float");
        for(int i=0;i<NativeResidentGeometry.BYTE_SOURCE.length;i++){
            int c=NativeResidentGeometry.BYTE_SOURCE[i],v=b.get(NativeResidentGeometry.BYTE_TARGET[i])&255;
            equal(source[c],NativeResidentGeometry.normalized(c)?v/255f:v,"byte component"+c);
        }
        require(b.get(47)==0,"padding");
    }
    public static void main(String[] args)throws Exception {
        Random r=new Random(7721);ByteBuffer b=ByteBuffer.allocateDirect(52).order(ByteOrder.nativeOrder());
        for(int trial=0;trial<4096;trial++){
            float[] v=new float[28];for(int c:NativeResidentGeometry.FLOAT_SOURCE)v[c]=Float.intBitsToFloat(r.nextInt()&0xfeffffff);
            for(int c:NativeResidentGeometry.BYTE_SOURCE){int n=r.nextInt(256);v[c]=NativeResidentGeometry.normalized(c)?n/255f:n;}
            b.clear();require(NativeResidentGeometry.put(b,v),"representable");verify(v,b);
            for(int c:NativeResidentGeometry.BYTE_SOURCE){float old=v[c];v[c]=Math.nextUp(old);b.clear();require(!NativeResidentGeometry.put(b,v)&&b.position()==0,"lossy admission "+c);v[c]=old;}
        }
        float[] v=new float[28];for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-0f,-1f,256f,.1234f}){
            v[14]=bad;b.clear();require(!NativeResidentGeometry.put(b,v)&&b.position()==0,"unsupported byte");}v[14]=0;
        for(int c:NativeResidentGeometry.FLOAT_SOURCE){v[c]=Float.NaN;b.clear();require(!NativeResidentGeometry.put(b,v),"nonfinite");v[c]=-0f;b.clear();require(NativeResidentGeometry.put(b,v),"signed zero float");verify(v,b);v[c]=0;}
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);
        Scene scene=new Scene(new MudClientGraphics(64,64,2),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setFrustum(0,11,7,2);
        byte[] pixels=new byte[4096];Arrays.fill(pixels,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x337755},0,pixels);
        scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
        OpenGLWorldTextureCache cache=new OpenGLWorldTextureCache(null);
        ((Map)field(OpenGLWorldTextureCache.class,"textureRegionsById").get(cache)).put(0,new OpenGLTextureRegion(9,100,200,64,64,.125f,.25f,.375f,.5f,false));
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        OpenGLShaderProgram shader=(OpenGLShaderProgram)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,OpenGLShaderProgram.class);
        OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,cache,shader,true,true,false,false,true,true,false);
        int[] xyz={0,0,0,128,0,0,0,0,128};
        Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,xyz,new float[]{0,1,.5f},new float[]{1,0,.5f},new int[3],new int[]{0,1,2},new int[]{0},new int[]{0x337755},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},null,null,1,0,0,true,1,1);
        method(OpenGLWorldChunkRenderer.class,"ensureUploadBuffers",int.class,int.class).invoke(renderer,3,3);
        Method pack=method(OpenGLWorldChunkRenderer.class,"copyChunkVertices",Renderer3DWorldChunkFrame.ChunkMesh.class,Renderer3DFrame.class,int.class,boolean.class,List.class);
        System.setProperty("spoiledmilk.nativeCompactGeometry","false");pack.invoke(renderer,chunk,frame,3,true,Collections.emptyList());
        FloatBuffer legacy=((FloatBuffer)field(OpenGLWorldChunkRenderer.class,"vertexUploadBuffer").get(renderer)).duplicate();float[] literal=new float[84];legacy.get(literal);
        equal(literal[7],.125f,"atlas U");equal(literal[8],.5f,"atlas V");
        System.setProperty("spoiledmilk.nativeCompactGeometry","true");pack.invoke(renderer,chunk,frame,3,true,Collections.emptyList());
        require(field(OpenGLWorldChunkRenderer.class,"packedCompact").getBoolean(renderer),"actual native compact selection");
        ByteBuffer compact=((ByteBuffer)field(OpenGLWorldChunkRenderer.class,"compactUploadBuffer").get(renderer)).duplicate().order(ByteOrder.nativeOrder());
        for(int n=0;n<3;n++){compact.position(n*52);verify(Arrays.copyOfRange(literal,n*28,(n+1)*28),compact.slice().order(ByteOrder.nativeOrder()));}
        for(String flag:new String[]{"spoiledmilk.browserBenchmark","spoiledmilk.browserPublicAlpha"}){System.setProperty(flag,"true");pack.invoke(renderer,chunk,frame,3,true,Collections.emptyList());require(!field(OpenGLWorldChunkRenderer.class,"packedCompact").getBoolean(renderer),"browser isolation");System.clearProperty(flag);}
        pack.invoke(renderer,chunk,frame,3,false,Collections.emptyList());require(!field(OpenGLWorldChunkRenderer.class,"packedCompact").getBoolean(renderer),"unsupported atlas mode");
        frame.release();System.out.println("PASS native compact exact attributes/atlas writer/fallback/isolation checks="+checks);
    }
}
