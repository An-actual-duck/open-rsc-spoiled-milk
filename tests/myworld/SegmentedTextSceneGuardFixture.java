package orsc.graphics.two;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;

/** Frozen scoped Scene ownership case, including pending UI text before entry. */
public final class SegmentedTextSceneGuardFixture {
    static final class TextActor extends GraphicsController {
        TextActor(){super(64,64,1);}
        @Override public void drawEntity(int index,int x,int y,int width,int height,int movement,int skew,int pick){for(int n=0;n<9;n++)drawColoredString(1,10,"ABC",0,0x8152c6,0);}
    }
    public static void run()throws Exception{
        Method font=OverlayJournalRenderingIndependentFixture.class.getDeclaredMethod("fonts",boolean.class);font.setAccessible(true);font.invoke(null,true);
        TextActor g=new TextActor();g.setWorldOverlayCoverageEnabled(true);g.beginRenderer2DFrame();g.setRenderer2DPhase(Renderer2DFrame.Phase.UI_OVERLAY);g.beginTransparentWorldOverlay();g.drawBoxAlpha(0,0,64,64,0x283951,128);
        for(int n=0;n<9;n++)g.drawColoredString(1,10,"ABC",0,0x9657b3,0);
        g.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);
        Renderer3DFrame frame=SceneSpriteRasterPolicyFixture.frame();
        g.drawSceneEntity(5,10,10,16,20,0,0,0,0,7,frame,GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);
        WorldOverlayCoverage.Frame coverage=g.borrowWorldOverlayCoverage();
        if(g.getSceneSpriteRasterFailure().isEmpty()||coverage.valid)throw new AssertionError("Uncaptured Scene glyphs bypassed legacy ownership rejection");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");ByteBuffer bytes=ByteBuffer.allocate(g.pixelData.length*8);for(int c:g.pixelData)bytes.putInt(c);for(float t:coverage.transmittance)bytes.putInt(Float.floatToRawIntBits(t));hash.update(bytes.array());hash.update(g.getSceneSpriteRasterFailure().getBytes("UTF-8"));
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));System.out.println("SCENE-GUARD "+out);
    }
}
