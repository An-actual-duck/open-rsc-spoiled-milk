package orsc;

import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import orsc.graphics.three.Renderer3DFrame;

/** Controlled hardware pixels; supplements real-game coverage, not a map/FPS test. */
public final class CharacterGroundShadowGpuFixture {
    static final int SIZE=512;
    static int checks;
    static void require(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static FloatBuffer floats(float...values){FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(values).flip();return b;}
    static Renderer3DFrame frame(int level)throws Exception {
        Constructor<?> selected=null;
        for(Constructor<?> c:Renderer3DFrame.class.getDeclaredConstructors()) {
            Class<?>[] types=c.getParameterTypes();
            if(types.length>2&&types[types.length-1]==int.class&&types[types.length-2]==double.class)selected=c;
        }
        require(selected!=null,"Explicit signed-level frame constructor missing");selected.setAccessible(true);
        Class<?>[] types=selected.getParameterTypes();Object[] args=new Object[types.length];
        for(int i=0;i<types.length;i++) {
            if(types[i]==int.class)args[i]=Integer.valueOf(1024);
            else if(types[i]==long.class)args[i]=Long.valueOf(1);
            else if(types[i]==double.class)args[i]=Double.valueOf(0);
            else if(types[i].isArray())args[i]=Array.newInstance(types[i].getComponentType(),0);
            else throw new AssertionError("Unexpected frame parameter");
        }
        args[args.length-1]=level;Renderer3DFrame frame=(Renderer3DFrame)selected.newInstance(args);
        frame.setWorldChunkFrame(GeometryShadowRoofMaskFixture.frame(0,false,1));
        Method add=Renderer3DFrame.class.getDeclaredMethod("addCharacterGroundShadow",int.class,int.class,int.class,int.class,float.class,float.class,float.class);
        add.setAccessible(true);add.invoke(frame,0,64,0,64,32f,0f,0f);return frame;
    }
    static ByteBuffer render(LwjglBindings gl,OpenGLCharacterGroundShadows pass,GeometryShadowRoofMask mask,int x,Path image)throws Exception {
        gl.glViewport(0,0,SIZE,SIZE);gl.glUseProgram(0);gl.glClearColor(1,1,1,1);gl.glDepthMask(true);
        gl.glClear(gl.GL_COLOR_BUFFER_BIT|gl.GL_DEPTH_BUFFER_BIT);
        gl.glMatrixMode(gl.GL_PROJECTION);gl.glLoadIdentity();gl.glOrtho(0,256,0,256,-1000,1000);
        gl.glMatrixMode(gl.GL_MODELVIEW);gl.glLoadMatrixf(floats(1,0,0,0, 0,0,-1,0, 0,1,0,0, 0,0,0,1));
        pass.mesh.clear();pass.mesh.append(x,0,64,32,0,0,.28f,mask,0,0);pass.drawMesh();
        ByteBuffer pixels=ByteBuffer.allocateDirect(SIZE*SIZE*4);gl.glReadPixels(0,0,SIZE,SIZE,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,pixels);
        BufferedImage out=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        for(int py=0;py<SIZE;py++)for(int px=0;px<SIZE;px++) {
            int at=(py*SIZE+px)*4;out.setRGB(px,SIZE-1-py,0xff000000|((pixels.get(at)&255)<<16)|((pixels.get(at+1)&255)<<8)|(pixels.get(at+2)&255));
        }
        ImageIO.write(out,"png",image.toFile());return pixels;
    }
    static int red(ByteBuffer pixels,int x,int z){return pixels.get(((z*SIZE/256)*SIZE+x*SIZE/256)*4)&255;}
    public static void main(String[]args)throws Exception {
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);
        RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
        require(OpenGLCharacterGroundShadows.enabledFor(frame(0)),"Ground surface rejected");
        require(OpenGLCharacterGroundShadows.enabledFor(frame(2)),"Outdoor upper level rejected");
        require(!OpenGLCharacterGroundShadows.enabledFor(frame(-1))&&!OpenGLCharacterGroundShadows.enabledFor(frame(-8)),"Underground sunlight circles enabled");
        RendererShadowSettings.setQuality(RendererShadowSettings.Quality.OFF);
        require(!OpenGLCharacterGroundShadows.enabledFor(frame(0)),"Off still enabled character shadows");
        RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);
        require(OpenGLCharacterGroundShadows.enabledFor(frame(0)),"Low lost character shadows");
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.CLASSIC);
        require(!OpenGLCharacterGroundShadows.enabledFor(frame(0)),"Classic lighting acquired sunlight circles");
        LwjglBindings gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init failed");long window=0;
        OpenGLCharacterGroundShadows pass=null;
        try {
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);
            window=gl.glfwCreateWindow(SIZE,SIZE,"Hidden character shadow pixels",0,0);require(window!=0,"Hidden context failed");
            gl.glfwMakeContextCurrent(window);gl.createCapabilities();
            System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));
            System.out.println("GL_VERSION="+gl.glGetString(0x1F02));
            pass=new OpenGLCharacterGroundShadows(gl);
            GeometryShadowRoofMask.Cache cache=new GeometryShadowRoofMask.Cache();
            GeometryShadowRoofMask hidden=cache.get(GeometryShadowRoofMaskFixture.frame(1L<<2,false,3),0);
            ByteBuffer plain=render(gl,pass,GeometryShadowRoofMask.EMPTY,120,output.resolve("outdoor.png"));
            require(red(plain,120,64)<220&&red(plain,136,64)<245,"Outdoor circle missing");
            ByteBuffer clipped=render(gl,pass,hidden,120,output.resolve("roof-edge.png"));
            require(red(clipped,120,64)<220,"Outdoor portion removed");
            for(int x=129;x<160;x++)for(int z=32;z<96;z++)require(red(clipped,x,z)>252,"Circle bled into roofed tile");
            GeometryShadowRoofMask shown=cache.get(GeometryShadowRoofMaskFixture.frame(1L<<2,true,4),0);
            ByteBuffer visible=render(gl,pass,shown,120,output.resolve("visible-roof.png"));
            require(visible.equals(clipped),"Hidden roof changed shadow pixels");
            ByteBuffer indoor=render(gl,pass,hidden,140,output.resolve("indoor.png"));
            require(red(indoor,140,64)>252&&pass.mesh.vertexCount()==0,"Indoor actor cast a circle");
            require(((Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null))==0,"GL error");
            pass.close();pass.close();pass=null;
            System.out.println("PASS "+checks+" native GPU roof-edge/indoor/hidden-roof pixels, signed-level/quality/lighting policy and idempotent resource close");
        } finally {if(pass!=null)pass.close();if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
