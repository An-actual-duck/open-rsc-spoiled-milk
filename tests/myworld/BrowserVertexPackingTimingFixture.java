package orsc;
import java.lang.reflect.*;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
/** Bounded desktop-JVM packing throughput only, NOT game/GPU/browser FPS. */
public final class BrowserVertexPackingTimingFixture {
    static volatile float sink;
    public static void main(String[] args)throws Exception{
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);
        Scene scene=new Scene(new MudClientGraphics(32,32,2),4,16,4);scene.setMidpoints(16,true,32,16,16,9,32);scene.setCamera(0,-4,0,768,0,0,600);scene.setFrustum(0,11,7,2);byte[] pixels=new byte[4096];Arrays.fill(pixels,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x778899},0,pixels);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
        int vertices=6000,triangles=vertices/3;int[] xyz=new int[vertices*3],indices=new int[vertices],tex=new int[triangles],colors=new int[triangles];float[] u=new float[vertices],v=new float[vertices];Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];Arrays.fill(kinds,Renderer3DModelKind.GAME_OBJECT);Arrays.fill(colors,0x778899);
        for(int n=0;n<vertices;n++){indices[n]=n;xyz[n*3]=(n%3)*128;xyz[n*3+1]=-(n%7)*16;xyz[n*3+2]=(n%5)*64;u[n]=(n%3)*.5f;v[n]=(n%2);}
        Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,xyz,u,v,new int[vertices],indices,tex,colors,kinds,null,0,0,0,true,2,11);
        OpenGLWorldChunkRenderer browser=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false),nativeRenderer=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
        Method ensure=OpenGLWorldChunkRenderer.class.getDeclaredMethod("ensureUploadBuffers",int.class,int.class),copy=OpenGLWorldChunkRenderer.class.getDeclaredMethod("copyChunkVertices",Renderer3DWorldChunkFrame.ChunkMesh.class,Renderer3DFrame.class,int.class,boolean.class,List.class);ensure.setAccessible(true);copy.setAccessible(true);ensure.invoke(nativeRenderer,vertices,indices.length);
        for(int n=0;n<200;n++){sink=browser.copyBackendChunkVertices(chunk,frame)[0];copy.invoke(nativeRenderer,chunk,frame,vertices,false,Collections.emptyList());}
        long[] browserTimes=new long[5],nativeTimes=new long[5];int count=100;
        for(int round=0;round<5;round++){
            long start=System.nanoTime();for(int n=0;n<count;n++)copy.invoke(nativeRenderer,chunk,frame,vertices,false,Collections.emptyList());nativeTimes[round]=(System.nanoTime()-start)/count;
            start=System.nanoTime();for(int n=0;n<count;n++)sink=browser.copyBackendChunkVertices(chunk,frame)[0];browserTimes[round]=(System.nanoTime()-start)/count;
        }
        frame.release();System.out.println("{\"nativeNsPerChunk\":"+Arrays.toString(nativeTimes)+",\"browserNsPerChunk\":"+Arrays.toString(browserTimes)+",\"vertices\":"+vertices+",\"repetitionsPerRound\":"+count+",\"scope\":\"desktop JVM packing only, not FPS\"}");
    }
}
