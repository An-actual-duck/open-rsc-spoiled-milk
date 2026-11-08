package orsc;
import java.nio.file.*;
import java.util.Properties;
/** Brief scoped native window test: no gameplay, login, input callback or grab. */
public final class RendererWindowLifecycleGpuFixture {
    static int releases,saves;static Path receipt;
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void settle(OpenGLWindowController w)throws Exception{
        for(int n=0;n<40;n++){w.pollEvents();w.prepareFrame(960,540);Thread.sleep(20);}
    }
    public static void main(String[] args)throws Exception{
        receipt=Paths.get(args[0]);Files.createDirectories(receipt);
        LwjglBindings gl=LwjglBindings.load();OpenGLWindowController window=new OpenGLWindowController(gl,"Spoiled Milk scoped window lifecycle check",true,new OpenGLWindowController.Delegate(){
            public void contextCreated(){}public void releaseInputState(){releases++;}public void suppressKeysUntilRelease(){}
            public void saveWindowSettings(){try{Properties p=new Properties();OpenGLWindowSettings.saveToClientSettings(p);RendererShadowSettings.saveToClientSettings(p);try(java.io.OutputStream out=Files.newOutputStream(receipt.resolve("settings.properties"))){p.store(out,"Disposable fixture preferences");}saves++;}catch(Exception e){throw new RuntimeException(e);}}
            public void closeClient(){throw new AssertionError("Fixture requested game close");}public void log(String s){System.out.println(s);}
        });
        try{
            require(window.initializeGlfw(),"GLFW init");OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);OpenGLWindowSettings.setWindowedBounds(80,80,960,540);window.createWindow(960,540,false);settle(window);
            Class<?> glfw=Class.forName("org.lwjgl.glfw.GLFW");long handle=window.window();
            gl.glfwSetWindowSize(handle,1100,700);settle(window);int[] resized=window.diagnosticSurfaceSize();require(resized[2]==1100&&resized[3]==700,"Real window resize not reflected");
            for(OpenGLWindowSettings.Mode mode:new OpenGLWindowSettings.Mode[]{OpenGLWindowSettings.Mode.FULLSCREEN,OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN,OpenGLWindowSettings.Mode.WINDOWED}){
                OpenGLWindowSettings.setMode(mode);settle(window);require(OpenGLWindowSettings.getMode()==mode,"Window mode lost");require(window.diagnosticSurfaceSize()[0]>0,"Invalid framebuffer after mode change");System.out.println("mode="+mode+" surface="+java.util.Arrays.toString(window.diagnosticSurfaceSize()));
            }
            glfw.getMethod("glfwIconifyWindow",long.class).invoke(null,handle);settle(window);require((window.diagnosticWindowFlags()&2)!=0,"Window manager did not iconify scoped window");
            System.out.println("iconified cached="+window.diagnosticWindowFlags()+" direct="+gl.glfwGetWindowAttrib(handle,gl.GLFW_ICONIFIED));
            glfw.getMethod("glfwRestoreWindow",long.class).invoke(null,handle);gl.glfwShowWindow(handle);glfw.getMethod("glfwFocusWindow",long.class).invoke(null,handle);settle(window);
            System.out.println("restored cached="+window.diagnosticWindowFlags()+" direct="+gl.glfwGetWindowAttrib(handle,gl.GLFW_ICONIFIED)+" mode="+OpenGLWindowSettings.getMode());
            require((window.diagnosticWindowFlags()&2)==0,"Window did not restore");
            RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);window.prepareForShutdown();
            // Quality/mode UI commits save independently of window-bounds dirtiness.
            Properties selected=new Properties();OpenGLWindowSettings.saveToClientSettings(selected);RendererShadowSettings.saveToClientSettings(selected);
            try(java.io.OutputStream out=Files.newOutputStream(receipt.resolve("settings.properties"))){selected.store(out,"Disposable final settings serializer roundtrip");}
            Properties persisted=new Properties();try(java.io.InputStream in=Files.newInputStream(receipt.resolve("settings.properties"))){persisted.load(in);}require("windowed".equals(persisted.getProperty("opengl_window_mode")),"Window mode not persisted");
            RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);RendererShadowSettings.loadFromClientSettings(persisted);require(RendererShadowSettings.getQuality()==RendererShadowSettings.Quality.LOW,"Shadow preference did not roundtrip");
            require(saves>0,"Settings never saved");require(releases>0,"Transitions did not release input state");
            System.out.println("PASS native resize, fullscreen/borderless/windowed, iconify/restore, settings persistence, scoped cleanup; no input hooks installed");
        }finally{window.shutdown(true);require(!window.hasWindow(),"Window not destroyed");window.shutdown(true);}
    }
}
