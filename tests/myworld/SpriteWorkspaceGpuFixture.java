package orsc;
import java.nio.*;
import java.lang.reflect.*;
import java.util.*;
import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
/** Own hidden GL context only; verifies actual synchronous upload and atlas retry. */
public final class SpriteWorkspaceGpuFixture {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ByteBuffer texture(LwjglBindings gl,int size)throws Exception {
        ByteBuffer data=ByteBuffer.allocateDirect(size*size*4);
        Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetTexImage",int.class,int.class,int.class,int.class,ByteBuffer.class).invoke(null,gl.GL_TEXTURE_2D,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,data);return data;
    }
    static CompositeWorldSpriteTexture composite(OpenGLPixelWorkspace scratch,int color,int size){
        return OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(Collections.singletonList(new Renderer2DFrame.SpriteCommand(new Sprite(new int[]{color},1,1),0,0,size,size,256)),scratch);
    }
    static void checkRegion(ByteBuffer pixels,int atlasSize,OpenGLTextureRegion region,int rgba){
        for(int y=region.getY()-1;y<=region.getY()+region.getHeight();y++)for(int x=region.getX()-1;x<=region.getX()+region.getWidth();x++)require(pixels.getInt((y*atlasSize+x)*4)==rgba,"Actual uploaded pixel/padding differs at "+x+","+y);
    }
    public static void main(String[] args)throws Exception {
        LwjglBindings gl=LwjglBindings.load();require(gl.glfwInit(),"GLFW init");long window=0;OpenGLDynamicTextureAtlas atlas=null;
        try{
            gl.glfwDefaultWindowHints();gl.glfwWindowHint(gl.GLFW_VISIBLE,gl.GLFW_FALSE);gl.glfwWindowHint(gl.GLFW_FOCUSED,gl.GLFW_FALSE);window=gl.glfwCreateWindow(64,64,"Hidden sprite upload workspace",0,0);require(window!=0,"Window");gl.glfwMakeContextCurrent(window);gl.createCapabilities();
            System.out.println("GL_RENDERER="+gl.glGetString(0x1F01));atlas=OpenGLDynamicTextureAtlas.create(gl,16,16);OpenGLPixelWorkspace scratch=new OpenGLPixelWorkspace();
            CompositeWorldSpriteTexture red=composite(scratch,0xd82132,9);OpenGLTextureRegion a=atlas.upload(red.textureData);require(a!=null,"Initial atlas upload");checkRegion(texture(gl,16),16,a,0xd82132ff);
            CompositeWorldSpriteTexture blue=composite(scratch,0x186ad4,9);require(atlas.upload(blue.textureData)==null,"Expected full atlas rejection");atlas.beginFrame();OpenGLTextureRegion b=atlas.upload(blue.textureData);require(b!=null,"Atlas-full retry lost borrowed bytes");checkRegion(texture(gl,16),16,b,0x186ad4ff);
            CompositeWorldSpriteTexture oversized=composite(scratch,0x123456,20);require(atlas.upload(oversized.textureData)==null,"Oversize atlas rejection");atlas.beginFrame();CompositeWorldSpriteTexture small=composite(scratch,0x95c123,2);OpenGLTextureRegion c=atlas.upload(small.textureData);require(c!=null,"Recovery after oversize");checkRegion(texture(gl,16),16,c,0x95c123ff);
            Field f=OpenGLDynamicTextureAtlas.class.getDeclaredField("uploadWorkspace");f.setAccessible(true);Object owned=f.get(atlas);atlas.close();atlas.close();Field bytes=owned.getClass().getDeclaredField("rgba");bytes.setAccessible(true);require(bytes.get(owned)==null,"Atlas close retained scratch");
            require(((Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null))==0,"GL error");System.out.println("PASS actual GL pixels/padding, synchronous scratch consumption, full-atlas retry, oversize rejection/recovery, idempotent release");
        }finally{if(atlas!=null)atlas.close();if(window!=0)gl.glfwDestroyWindow(window);gl.glfwTerminate();}
    }
}
