package orsc;
import java.lang.reflect.Field;
import java.util.Arrays;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.GraphicsController;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.two.WorldOverlayCoverage;
/** Production ordinary teleport effect must remain an overlay, not an actor capture failure. */
public final class BrowserTeleportOverlayFixture {
    static int checks;
    static void require(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    static void set(Object owner,String name,Object value)throws Exception{Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    public static void main(String[] ignored)throws Exception {
        Config.S_WANT_CUSTOM_SPRITES=true;
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        mudclient client=(mudclient)((sun.misc.Unsafe)f.get(null)).allocateInstance(mudclient.class);
        MudClientGraphics graphics=new MudClientGraphics(48,48,1);graphics.mudClientRef=client;client.setSurface(graphics);
        int[] types=new int[50],times=new int[50];set(client,"teleportBubbleType",types);set(client,"teleportBubbleTime",times);
        for(int type=0;type<2;type++)for(int time:new int[]{0,10,25,49}) {
            types[0]=type;times[0]=time;graphics.beginRenderer2DFrame();graphics.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);
            Arrays.fill(graphics.pixelData,0);client.drawTeleportBubble(0,4,4,40,40,0,2);int[] baseline=graphics.pixelData.clone();
            require(graphics.getRenderer2DPhase()==Renderer2DFrame.Phase.SCENE,"Native phase changed");
            BrowserFrameSinkRegistry.install((image,commands,frame)->{});graphics.beginRenderer2DFrame();graphics.setWorldOverlayCoverageEnabled(true);graphics.beginTransparentWorldOverlay();graphics.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);
            graphics.drawSceneEntity(50000,4,4,40,40,0,0,0,-1,-1,null,GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);
            require(graphics.getSceneSpriteRasterFailure().isEmpty(),"Ordinary teleport incorrectly rejected as an actor layer");
            require(graphics.getRenderer2DPhase()==Renderer2DFrame.Phase.SCENE,"Browser Scene phase leaked");
            require(Arrays.equals(baseline,graphics.pixelData),"Browser overlay changed ordinary teleport RGB");
            WorldOverlayCoverage.Frame coverage=graphics.borrowWorldOverlayCoverage();require(coverage.valid&&coverage.transparent,"Teleport invalidated coverage");boolean affected=false;for(float t:coverage.transmittance)affected|=t<1;require(affected,"Teleport did not cover the GPU world");
            graphics.setRenderer2DPhase(Renderer2DFrame.Phase.UI_OVERLAY);boolean refused=false;try{client.drawTeleportBubble(99,0,0,1,1,0,2);}catch(RuntimeException expected){refused=true;}
            require(refused&&graphics.getRenderer2DPhase()==Renderer2DFrame.Phase.UI_OVERLAY,"Exceptional teleport did not restore previous phase");
            graphics.beginOpaqueWorldOverlay();graphics.setWorldOverlayCoverageEnabled(false);BrowserFrameSinkRegistry.install(null);
        }
        System.out.println("PASS "+checks+" production teleport overlay native RGB/phase, scoped GPU actor guard, real coverage and exceptional restoration checks");
    }
}
