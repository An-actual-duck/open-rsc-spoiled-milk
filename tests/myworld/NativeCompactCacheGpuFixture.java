package orsc;
import java.nio.*;import java.util.*;import java.lang.reflect.*;
import orsc.graphics.three.*;import orsc.graphics.two.MudClientGraphics;
/** Exercises production upload/cache/draw, mixed layout and fallback recovery on hardware. */
public final class NativeCompactCacheGpuFixture {
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Renderer3DWorldChunkFrame.ChunkMesh chunk(int origin,int mask,int texture,long signature){
        int[] xyz={-96,-96,0,96,-96,0,96,96,0,-96,-96,0,96,96,0,-96,96,0};
        return new Renderer3DWorldChunkFrame.ChunkMesh(0,origin,0,origin,0,xyz,new float[]{0,1,1,0,1,0},new float[]{0,0,1,0,1,1},new int[6],new int[]{0x112233,0x223344,0x334455,0x445566,0x556677,0x667788},new int[]{0,51,102,153,204,255},new int[]{0,1,2,3,4,5},new int[]{texture,texture},new int[]{0x337755,0x775533},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN,Renderer3DModelKind.TERRAIN},new Renderer3DMaterialFamily[]{Renderer3DMaterialFamily.values()[1],Renderer3DMaterialFamily.values()[2]},null,null,new int[]{1,mask},null,0,0,2,0,0,false,Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,signature);
    }
    public static void main(String[] args)throws Exception{
        LwjglBindings gl=LwjglBindings.load();require(gl.glfwInit(),"glfw");long window=0;OpenGLShaderProgram shader=null;OpenGLWorldTextureCache cache=null;
        try{
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(128,128,"Hidden native cache proof",0,0);require(window!=0,"window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();shader=OpenGLShaderProgram.createResidentChunkParity(gl);cache=new OpenGLWorldTextureCache(gl);
            System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");Scene scene=new Scene(new MudClientGraphics(128,128,2),4,16,4);scene.setMidpoints(64,true,128,64,64,7,128);scene.setFrustum(0,11,7,2);byte[] p=new byte[4096];Arrays.fill(p,(byte)1);for(int i=0;i<p.length;i+=2)p[i]=0;scene.loadTexture(0,new int[]{0xff00ff,0x337755},0,p);scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
            frame.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(chunk(0,2,0,1),chunk(1,256,0,2),chunk(2,2,-1,3))));
            int cases=0,mixed=0;
            for(boolean shaderOn:new boolean[]{true,false})for(boolean textured:new boolean[]{true,false})for(RendererLightingSettings.Mode light:RendererLightingSettings.Mode.values())for(RendererFogSettings.Mode fog:RendererFogSettings.Mode.values())for(RendererGeometrySettings.Mode geometry:RendererGeometrySettings.Mode.values()){
                RendererLightingSettings.setMode(light);RendererFogSettings.setMode(fog);RendererGeometrySettings.setMode(geometry);byte[] reference=null;
                for(boolean compact:new boolean[]{false,true}){
                    System.setProperty("spoiledmilk.nativeCompactGeometry",Boolean.toString(compact));OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(gl,cache,shaderOn?shader:null,true,true,false,false,shaderOn,true,false);
                    try{
                        OpenGLWorldChunkUploadStats first=renderer.upload(frame,frame.getWorldChunkFrame(),textured,false);require(first.uploadedChunks==3,"actual upload count");require(renderer.upload(frame,frame.getWorldChunkFrame(),textured,false).reusedChunks==3,"cache reuse");
                        Collection<WorldChunkBuffer> buffers=((Map<?,WorldChunkBuffer>)field(renderer,"residentChunks")).values();int packed=0;for(WorldChunkBuffer b:buffers)if(b.compact)packed++;
                        if(compact&&shaderOn&&textured&&light==RendererLightingSettings.Mode.DIRECTIONAL){require(packed==2,"mixed layout selection "+packed);mixed++;}else require(packed==0,"unsupported mode compacted");
                        gl.glViewport(0,0,128,128);gl.glClearColor(.11f,.13f,.17f,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT|gl.GL_DEPTH_BUFFER_BIT);renderer.drawDiagnostic(frame,true,textured);ByteBuffer read=ByteBuffer.allocateDirect(128*128*4);gl.glReadPixels(0,0,128,128,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,read);byte[] image=new byte[read.remaining()];read.get(image);if(!compact)reference=image;else require(Arrays.equals(reference,image),"cache/main pixels differ "+light+"/"+fog+"/"+geometry+"/"+textured+"/"+shaderOn);
                        // Same cache, actual mode change must replace all old buffers; restore afterward.
                        RendererLightingSettings.Mode other=light==RendererLightingSettings.Mode.DIRECTIONAL?RendererLightingSettings.Mode.values()[0]:RendererLightingSettings.Mode.DIRECTIONAL;
                        RendererLightingSettings.setMode(other);require(renderer.upload(frame,frame.getWorldChunkFrame(),textured,false).uploadedChunks==3,"lighting invalidation");RendererLightingSettings.setMode(light);require(renderer.upload(frame,frame.getWorldChunkFrame(),textured,false).uploadedChunks==3,"lighting return invalidation");
                        renderer.clearResidentWorldSession();require(((Map)field(renderer,"residentChunks")).isEmpty(),"session release");require(renderer.upload(frame,frame.getWorldChunkFrame(),textured,false).uploadedChunks==3,"session rebuild");
                    }finally{renderer.close();}
                }cases++;
            }
            require(mixed>0,"no compact coverage");frame.release();require((Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null)==0,"GL error");System.out.println("PASS production cache/upload/draw pixel parity cases="+cases+" mixed="+mixed+"; lighting/fog/geometry/shader/untextured/session transitions");
        }finally{if(cache!=null)cache.close();if(shader!=null)shader.close();if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
