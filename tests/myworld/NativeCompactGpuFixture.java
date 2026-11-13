package orsc;
import java.nio.*;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Actual production resident GLSL and native bindings, same GPU readback OFF/ON. */
public final class NativeCompactGpuFixture {
    static LwjglBindings gl;
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static FloatBuffer floats(float[] f){FloatBuffer b=ByteBuffer.allocateDirect(f.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(f).flip();return b;}
    public static void main(String[] args)throws Exception {
        gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW");long window=0;OpenGLShaderProgram shader=null;int vbo=0,ebo=0,texture=0;
        try {
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);
            window=gl.glfwCreateWindow(128,128,"Hidden compact native proof",0,0);require(window!=0,"window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();
            System.out.println("GL_RENDERER="+gl.glGetString(0x1f01));shader=OpenGLShaderProgram.createResidentChunkParity(gl);
            System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);
            Scene scene=new Scene(new MudClientGraphics(64,64,2),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setFrustum(0,11,7,2);scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
            vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);IntBuffer index=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer();for(int n=0;n<6;n++)index.put(n);index.flip();gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,index,gl.GL_STATIC_DRAW);
            texture=gl.glGenTextures();gl.glActiveTexture(gl.GL_TEXTURE0);gl.glBindTexture(gl.GL_TEXTURE_2D,texture);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);
            ByteBuffer pixels=ByteBuffer.allocateDirect(16);pixels.put(new byte[]{(byte)200,40,80,(byte)255,30,(byte)200,60,0,70,80,(byte)220,(byte)255,(byte)240,(byte)180,90,(byte)255}).flip();gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,2,2,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
            float[] positions={-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0};float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            gl.glViewport(0,0,128,128);gl.glDisable(gl.GL_DEPTH_TEST);gl.glDisable(gl.GL_CULL_FACE);gl.glDisable(gl.GL_BLEND);
            int proofs=0;Set<Integer> hashes=new HashSet<Integer>();
            for(boolean textured:new boolean[]{false,true})for(int kind:new int[]{1,2,3,4,5})for(int blend:new int[]{0,127,255})for(float slope:new float[]{-.5f,0,.5f}){
                float[] legacy=new float[6*28];ByteBuffer compact=ByteBuffer.allocateDirect(6*52).order(ByteOrder.nativeOrder());
                for(int n=0;n<6;n++){
                    float[] v=new float[28];System.arraycopy(positions,n*3,v,0,3);v[3]=120/255f;v[4]=180/255f;v[5]=60/255f;v[6]=1;
                    v[7]=(v[0]+1)*.5f;v[8]=(v[1]+1)*.5f;v[9]=v[10]=v[11]=v[12]=1;v[14]=130;v[15]=v[3];v[16]=v[4];v[17]=v[5];v[18]=slope;v[19]=1;v[21]=kind;v[22]=0;v[23]=0;v[24]=90/255f;v[25]=30/255f;v[26]=200/255f;v[27]=blend/255f;
                    System.arraycopy(v,0,legacy,n*28,28);require(NativeResidentGeometry.put(compact,v),"exact pack");
                }compact.flip();byte[] reference=null;
                for(boolean packed:new boolean[]{false,true}){
                    gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);if(packed)gl.glBufferData(gl.GL_ARRAY_BUFFER,compact,gl.GL_STATIC_DRAW);else gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(legacy),gl.GL_STATIC_DRAW);
                    shader.useResidentChunk(floats(identity),floats(identity),textured,false,true,frame,null,null);shader.setTextureEnabled(textured);
                    if(packed)shader.bindNativeCompactAttributes(textured);else shader.bindWorldParityAttributes(3,2,4,3,1,3,1,1,1,3,1,112,0,28,textured?36:12,60,56,72,84,88,92,96,108);
                    gl.glClearColor(.11f,.13f,.17f,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glDrawElements(gl.GL_TRIANGLES,6,gl.GL_UNSIGNED_INT,0L);
                    ByteBuffer read=ByteBuffer.allocateDirect(128*128*4);gl.glReadPixels(0,0,128,128,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,read);byte[] actual=new byte[read.remaining()];read.get(actual);
                    if(!packed)reference=actual;else require(Arrays.equals(reference,actual),"main pixel mismatch textured="+textured+" kind="+kind+" blend="+blend+" slope="+slope);
                    hashes.add(Arrays.hashCode(actual));
                }proofs++;
            }
            require(hashes.size()>3,"vacuous output");require((Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null)==0,"GL error");frame.release();
            System.out.println("PASS exact production main pixels: "+proofs+" variants, distinct outputs="+hashes.size());
        } finally {if(shader!=null)shader.close();if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(texture!=0)gl.glDeleteTextures(texture);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
