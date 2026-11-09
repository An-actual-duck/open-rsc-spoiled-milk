package orsc;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
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
        require(BrowserFrameSinkRegistry.shouldPaintUnderlay(32,32)&&!BrowserFrameSinkRegistry.shouldPaintUnderlay(32,32)&&BrowserFrameSinkRegistry.shouldPaintUnderlay(64,32),"Browser underlay did not paint initial/resized surfaces only");
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
        Renderer3DFrame projected=frame();
        Constructor<?> constructor=null;for(Constructor<?> c:Renderer3DFrame.SpriteAnchor.class.getDeclaredConstructors())if(c.getParameterTypes().length==17)constructor=c;require(constructor!=null,"Production sprite anchor constructor missing");constructor.setAccessible(true);
        Renderer3DFrame.SpriteAnchor anchor=(Renderer3DFrame.SpriteAnchor)constructor.newInstance(1,5000,1,1,400,0,0,500,16,16,10,4,12,24,1,0,true);
        CompositeWorldSpriteTexture composite=new CompositeWorldSpriteTexture(8,2,16,28,null);float[] quad=BrowserWebGLBridge.spriteQuad(projected,anchor,composite);
        float scale=1<<projected.getPerspectiveShift();
        for(int n=0;n<4;n++) {
            int x=8+(n==1||n==2?16:0),y=2+(n>=2?28:0);float depth=OpenGLWorldSpriteRenderer.estimateEntitySpriteDepth(anchor,y);
            require(quad[n*5+2]==depth,"Browser sprite depth differs from production estimator");
            require(Math.abs(projected.getCenterX()+quad[n*5]*scale/depth-x)<.0001f&&Math.abs(projected.getCenterY()+quad[n*5+1]*scale/depth-y)<.0001f,"Sprite camera quad does not reproject to exact composite bounds");
        }
        projected.release();
        System.out.println("PASS optional backend ownership, desktop defaults, capability teardown, null image and exact exception/release contracts");
    }
    static boolean releasedUnchecked(Renderer3DFrame f){try{return released(f);}catch(Exception e){throw new RuntimeException(e);}}
}
