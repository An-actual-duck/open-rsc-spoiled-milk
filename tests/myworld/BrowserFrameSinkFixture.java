package orsc;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Field;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Exercises the optional presenter seam with real pooled frames and no native/browser runtime. */
public final class BrowserFrameSinkFixture {
    static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
    static Renderer3DFrame frame(){Scene s=new Scene(new MudClientGraphics(32,32,1),4,16,4);s.setMidpoints(16,true,32,16,16,9,32);s.setCamera(0,-4,0,768,0,0,600);s.endScene(0);return s.getRenderer3DFrame();}
    static boolean released(Renderer3DFrame f)throws Exception{Field field=Renderer3DFrame.class.getDeclaredField("worldFaceStorageReleased");field.setAccessible(true);return field.getBoolean(f);}
    public static void main(String[] args)throws Exception{
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        System.setProperty("spoiledmilk.openglWorldMesh","true");System.setProperty("spoiledmilk.openglWorldMeshTexturedVisible","true");System.setProperty("spoiledmilk.openglWorldMeshTexturedStaticVisible","true");System.setProperty("spoiledmilk.skipLegacyWorldRaster","true");
        BufferedImage overlay=new BufferedImage(3,1,BufferedImage.TYPE_INT_RGB);
        int[] raw=((DataBufferInt)overlay.getRaster().getDataBuffer()).getData();raw[0]=0x01000000;raw[1]=0;raw[2]=0x112233;
        require((overlay.getRGB(0,0)&0xffffff)==0,"Background marker altered RGB");
        require(BrowserWebGLBridge.borrowOverlay(overlay)==raw&&raw[0]!=raw[1]&&raw[1]==0,"Direct overlay lost marked background or legitimate black");
        BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);Renderer3DFrame initial=frame();
        require(!Renderer3DSettings.canSkipLegacyWorldRaster(),"Desktop default falsely reports browser capability");
        require(!BrowserFrameSinkRegistry.present(image,null,initial)&&!released(initial),"Uninstalled seam consumed desktop frame");initial.release();
        final int[] calls={0};BrowserFrameSinkRegistry.install((i,c,f)->{calls[0]++;require(!releasedUnchecked(f),"Sink received released frame");});
        require(Renderer3DSettings.canSkipLegacyWorldRaster(),"Explicit browser sink did not activate capture/skip capability");
        Renderer3DFrame good=frame();require(BrowserFrameSinkRegistry.present(image,null,good)&&released(good)&&calls[0]==1,"Normal sink release mismatch");
        Renderer3DFrame empty=frame();require(BrowserFrameSinkRegistry.present(null,null,empty)&&released(empty)&&calls[0]==1,"Null image release mismatch");
        boolean refused=false;try{BrowserFrameSinkRegistry.install((i,c,f)->{});}catch(IllegalStateException e){refused=true;}require(refused,"Second sink stole ownership");
        BrowserFrameSinkRegistry.install(null);RuntimeException failure=new IllegalArgumentException("controlled draw failure");BrowserFrameSinkRegistry.install((i,c,f)->{throw failure;});
        Renderer3DFrame failed=frame();try{BrowserFrameSinkRegistry.present(image,null,failed);throw new AssertionError("Failure swallowed");}catch(RuntimeException e){require(e==failure,"Failure identity changed");}require(released(failed),"Failure leaked pooled frame");
        BrowserFrameSinkRegistry.install(null);require(!Renderer3DSettings.canSkipLegacyWorldRaster(),"Teardown retained browser capability");
        System.out.println("PASS optional backend ownership, desktop defaults, capability teardown, null image and exact exception/release contracts");
    }
    static boolean releasedUnchecked(Renderer3DFrame f){try{return released(f);}catch(Exception e){throw new RuntimeException(e);}}
}
