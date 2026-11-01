package orsc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import orsc.buffers.RSBuffer_Bits;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DSettings;
import orsc.graphics.two.MudClientGraphics;
import orsc.net.Network_Socket;

/** Actual HUD draw and hit-test after empty/populated hit numbers. No sockets or login. */
public final class PracticeHudFixture {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current=type; current!=null; current=current.getSuperclass()) {
            try { Field f=current.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException e) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        field(object.getClass(),name).set(object,value);
    }
    private static void call(Object object, String name) throws Exception {
        Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);
    }
    public static void main(String[] args) throws Exception {
        Method fonts=FloatingHitSplatFixture.class.getDeclaredMethod("fonts",java.nio.file.Path.class);
        fonts.setAccessible(true);fonts.invoke(null,Paths.get(args[0]));
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)singleton.get(null);
        for (int width:new int[]{800,960}) for (boolean populated:new boolean[]{false,true}) {
            mudclient client=(mudclient)unsafe.allocateInstance(mudclient.class);
            MudClientGraphics surface=new MudClientGraphics(width,600,1);client.setSurface(surface);
            int[] hp=new int[4];hp[3]=99;set(client,"playerStatBase",hp);set(client,"playerStatCurrent",hp.clone());
            set(client,"showCoordinatesOverlay",true);
            set(client,"externalAssetLoader",new ClientExternalAssetLoader(Paths.get(args[0]),PracticeHudFixture.class));
            client.packetHandler=new PacketHandler(client);
            Network_Socket wire=(Network_Socket)unsafe.allocateInstance(Network_Socket.class);
            wire.bufferBits=new RSBuffer_Bits(8192);client.packetHandler.setClientStream(wire);
            FloatingHitSplatRenderer hits=new FloatingHitSplatRenderer();
            if (populated) {
                ORSCharacter actor=new ORSCharacter();actor.floatingHitSplats.offer(0,9,0);
                hits.collect(actor,width/2,180,0,surface);
            }
            for (Renderer2DFrame.Phase phase:new Renderer2DFrame.Phase[]{Renderer2DFrame.Phase.WORLD_OVERLAY,Renderer2DFrame.Phase.UI_OVERLAY,Renderer2DFrame.Phase.SCENE}) {
                surface.beginRenderer2DFrame();surface.setRenderer2DPhase(phase);hits.draw(surface);
                check(surface.getRenderer2DPhase()==phase,"hit numbers retain their caller's rendering phase");
            }
            surface.beginRenderer2DFrame();surface.setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);
            hits.draw(surface);call(client,"drawPlayerStatusHud");
            int hpX=width-199-104-8,buttonX=hpX-20;
            check(surface.pixelData[3*width+hpX]==0xFFFFFF,"health border is drawn inside viewport");
            check(surface.pixelData[6*width+hpX+4]!=0,"health fill is drawn");
            boolean coordinatePixels=false;
            for(int y=22;y<=33;y++)for(int x=hpX;x<hpX+104;x++)coordinatePixels|=surface.pixelData[y*width+x]!=0;
            check(coordinatePixels,"coordinates remain visible below health bar");
            Renderer2DFrame frame=surface.consumeRenderer2DFrame();
            if (Renderer2DSettings.isOpenGLSpriteCaptureEnabled()) {
                check(frame.getPrimitiveCommands().length>=10,"HUD boxes captured for OpenGL replay");
                for(Renderer2DFrame.PrimitiveCommand command:frame.getPrimitiveCommands())
                    check(command.getPhase()==Renderer2DFrame.Phase.WORLD_OVERLAY,"health/button primitives retain overlay ownership");
                check(frame.getTextCommands().length>=2,"health and coordinate text captured");
                for(Renderer2DFrame.TextCommand command:frame.getTextCommands())
                    check(command.getPhase()==Renderer2DFrame.Phase.WORLD_OVERLAY,"HUD text retains overlay ownership");
            }
            Config.C_AUTO_RETALIATE=false;
            set(client,"mouseX",buttonX+8);set(client,"mouseY",10);set(client,"mouseButtonClick",1);
            call(client,"drawPlayerStatusHud");
            check(Config.C_AUTO_RETALIATE&&field(client.getClass(),"mouseButtonClick").getInt(client)==0,"visible button toggles auto-retaliate and consumes click");
            check(wire.bufferBits.dataBuffer[2]==111&&wire.bufferBits.dataBuffer[3]==47&&wire.bufferBits.dataBuffer[4]==1,
                "toggle emits the actual server setting packet");
            set(client,"mouseX",buttonX-1);set(client,"mouseButtonClick",1);call(client,"drawPlayerStatusHud");
            check(Config.C_AUTO_RETALIATE&&field(client.getClass(),"mouseButtonClick").getInt(client)==1,"outside click leaves auto-retaliate unchanged");
        }
        System.out.println("PASS "+checks+" hit-number phase/HUD viewport/capture/input assertions");
    }
}
