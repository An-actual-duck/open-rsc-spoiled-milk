package orsc;

import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Hidden real production shader/uniform proof; no server, input or displayed window. */
public final class UndergroundLightingGpuFixture {
    static LwjglBindings gl;static OpenGLShaderProgram shader;static int program,vbo,ebo,glowTexture;
    static Method bind,restore;
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static FloatBuffer floats(float[] x){FloatBuffer b=ByteBuffer.allocateDirect(x.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(x).flip();return b;}
    static FloatBuffer identity(){return floats(new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1});}
    static Renderer3DFrame frame(int level)throws Exception{
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");Scene s=new Scene(new MudClientGraphics(64,64,1),4,16,4);s.setMidpoints(32,true,64,32,32,9,64);s.setCamera(0,-4,0,768,0,0,600);
        try{Scene.class.getMethod("setRendererSignedWorldLevel",int.class).invoke(s,level);}catch(NoSuchMethodException old){require(level>=0,"baseline cannot model signed negative scope");}
        s.endScene(0);return s.getRenderer3DFrame();
    }
    static Object scope(Renderer3DFrame f)throws Exception{return bind==null?null:bind.invoke(null,f);}
    static void endScope(Object old)throws Exception{if(restore!=null)restore.invoke(null,old);}
    static void mesh(float nx,float legacy){
        float[] pos={-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0};float[] data=new float[6*23];
        for(int i=0;i<6;i++){int o=i*23;System.arraycopy(pos,i*3,data,o,3);data[o+3]=data[o+4]=.5f;data[o+5]=.25f;data[o+6]=.3f;data[o+7]=.35f;data[o+8]=1;data[o+9]=.25f;data[o+10]=.3f;data[o+11]=.35f;data[o+12]=legacy;data[o+13]=nx;data[o+14]=.8f;data[o+16]=1;}
        try{gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,floats(data),gl.GL_STATIC_DRAW);shader.bindWorldParityAttributes(3,2,4,3,1,3,1,1,1,3,1,92,0,12,20,36,48,52,64,68,72,76,88);}catch(Exception e){throw new RuntimeException(e);}
    }
    static byte[] draw(Renderer3DFrame f,float normal,float legacy,boolean glow)throws Exception{
        shader.useResidentChunk(identity(),identity(),false,false,true,f,null,glow?new RemasterGlowMask(1,1,1,1,-1,-1,.5f,.5f,ByteBuffer.allocateDirect(4)):null);
        mesh(normal,legacy);gl.glActiveTexture(gl.GL_TEXTURE0+2);gl.glBindTexture(gl.GL_TEXTURE_2D,glowTexture);gl.glActiveTexture(gl.GL_TEXTURE0);
        gl.glClearColor(1,0,1,1);gl.glClear(gl.GL_COLOR_BUFFER_BIT);gl.glDrawElements(gl.GL_TRIANGLES,6,gl.GL_UNSIGNED_INT,0L);
        ByteBuffer p=ByteBuffer.allocateDirect(64*64*4);gl.glReadPixels(0,0,64,64,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,p);byte[] bytes=new byte[p.capacity()];p.get(bytes);return bytes;
    }
    static String hash(byte[] a)throws Exception{byte[] d=java.security.MessageDigest.getInstance("SHA-256").digest(a);StringBuilder b=new StringBuilder();for(byte v:d)b.append(String.format("%02x",v&255));return b.toString();}
    static float[] tintAndFog()throws Exception{
        OpenGLWorldSpriteRenderer sprites=new OpenGLWorldSpriteRenderer(gl);
        Method tint=OpenGLWorldSpriteRenderer.class.getDeclaredMethod("useWorldToneColor",float.class);tint.setAccessible(true);tint.invoke(sprites,1.0f);
        FloatBuffer color=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asFloatBuffer();
        Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetFloatv",int.class,FloatBuffer.class).invoke(null,0x0B00,color);
        Method uniform=Class.forName("org.lwjgl.opengl.GL20").getMethod("glGetUniformf",int.class,int.class);
        float[] result=new float[6];for(int i=0;i<3;i++)result[i]=color.get(i);
        String[] names={"uFogRed","uFogGreen","uFogBlue"};for(int i=0;i<3;i++)result[i+3]=(Float)uniform.invoke(null,program,gl.glGetUniformLocation(program,names[i]));
        return result;
    }
    static int red(byte[] a){return a[(32*64+32)*4]&255;}
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[0]);Files.createDirectories(out);boolean baseline=args.length>1;
        try{Class<?> c=Class.forName("orsc.RendererFrameLighting");bind=c.getDeclaredMethod("bind",Renderer3DFrame.class);restore=c.getDeclaredMethod("restore",Renderer3DFrame.class);bind.setAccessible(true);restore.setAccessible(true);}catch(ClassNotFoundException expected){require(baseline,"missing frame scope");}
        gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;
        try{
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(64,64,"Hidden underground light contracts",0,0);require(window!=0,"hidden window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));
            shader=OpenGLShaderProgram.createResidentChunkParity(gl);Field id=OpenGLShaderProgram.class.getDeclaredField("programId");id.setAccessible(true);program=id.getInt(shader);
            vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();IntBuffer idx=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer();idx.put(new int[]{0,1,2,3,4,5}).flip();gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,idx,gl.GL_STATIC_DRAW);
            glowTexture=gl.glGenTextures();gl.glActiveTexture(gl.GL_TEXTURE0+2);gl.glBindTexture(gl.GL_TEXTURE_2D,glowTexture);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_NEAREST);gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_NEAREST);ByteBuffer emission=ByteBuffer.allocateDirect(4);emission.put(new byte[]{32,16,8,(byte)255}).flip();gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,1,1,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,emission);gl.glActiveTexture(gl.GL_TEXTURE0);
            gl.glViewport(0,0,64,64);gl.glDisable(gl.GL_DEPTH_TEST);gl.glDisable(gl.GL_CULL_FACE);RendererToneSettings.setMode(RendererToneSettings.Mode.CYCLE);RendererReliefSettings.setTerrainLevel(10);
            StringBuilder csv=new StringBuilder("level,brightness,night,pixelSha256,red,tintAndFog\n");
            for(int level:baseline?new int[]{0,1,10}:new int[]{0,1,10,-1,-8})for(RendererBrightnessSettings.Mode brightness:RendererBrightnessSettings.Mode.values()){
                RendererBrightnessSettings.setMode(brightness);Renderer3DFrame f=frame(level);Object previous=scope(f);byte[] day=null;float[] dayTint=null;
                try{for(boolean night:new boolean[]{false,true}){
                    RendererDayNightCycle.syncServerTime(3600000,night?2700000:900000);RendererRemasterLightSettings.adjustAzimuth(135-RendererRemasterLightSettings.getAzimuthDegrees());RendererRemasterLightSettings.adjustElevation(45-RendererRemasterLightSettings.getElevationDegrees());
                    byte[] p=draw(f,.6f,100,false);float[] tint=tintAndFog();csv.append(level).append(',').append(brightness).append(',').append(night).append(',').append(hash(p)).append(',').append(red(p)).append(',').append(Arrays.toString(tint).replace(',', ';')).append('\n');
                    if(!night){day=p;dayTint=tint;}else {require(Arrays.equals(day,p)==(level<0),"day/night environment mismatch "+level);require(Arrays.equals(dayTint,tint)==(level<0),"world-sprite/fog time mismatch "+level);}
                }
                if(level<0){byte[] toward=draw(f,-.6f,100,false),away=draw(f,.6f,100,false);System.out.println("DETAIL "+brightness+" "+level+" toward="+red(toward)+" away="+red(away));require(Math.abs(red(toward)-red(away))>=2,"underground terrain definition flattened");byte[] glow=draw(f,.6f,100,true);require(red(glow)>red(away)+12,"emission lost");}
                }finally{endScope(previous);}
            }
            Files.write(out.resolve("pixels.csv"),csv.toString().getBytes("UTF-8"));
            if(!baseline){
                OpenGLWorldChunkRenderer chunks=new OpenGLWorldChunkRenderer(gl,null,shader,true,true,false,true,true,true,false);
                try{for(int level:new int[]{0,-1,-8,0,10}){Renderer3DFrame f=frame(level);f.setWorldChunkFrame(GeometryShadowRoofMaskFixture.frame(0,true,1));Object old=scope(f);try{boolean shadows=chunks.prepareGeometryShadows(f);require(shadows==(level>=0),"sunlight shadow lifecycle level"+level);if(level<0)require(!chunks.prepareRemasterTerrainShadowMask(f),"legacy sunlight fallback underground");}finally{endScope(old);}}}finally{chunks.close();}
            }
            require(((Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null)).intValue()==0,"GL error");
            System.out.println("PASS full production shader: signed day/night equality, outside distinction, brightness, slope detail, glows and sunlight lifecycle");
        }finally{if(shader!=null)shader.close();if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);if(glowTexture!=0)gl.glDeleteTextures(glowTexture);if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
