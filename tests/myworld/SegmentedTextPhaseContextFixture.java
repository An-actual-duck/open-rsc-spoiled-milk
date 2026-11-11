package orsc.graphics.two;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;

/** Actual scoped legal WORLD names, plus illegal guarded SCENE prefix case. */
public final class SegmentedTextPhaseContextFixture {
    private static final class Names extends GraphicsController {
        int name;boolean illegal;
        Names(){super(64,64,1);loggedIn=true;}
        @Override public void drawEntity(int index,int x,int y,int width,int height,int movement,int skew,int pick){
            if(illegal){drawColoredString(3,12,"ABC",0,0x8ba352,0);return;}
            setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);
            for(int n=0;n<3;n++)drawColoredString(1+name%4,10+name%3,"ABC",0,0x845193+name+n,0);
            byte[] f=Fonts.fontData[0];f[f.length-1]=(byte)(name%2==0?0:255);name++;
            setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);
        }
    }
    private static int[] work(GraphicsController g)throws Exception{try{return (int[])GraphicsController.class.getMethod("getSegmentedTextWork").invoke(g);}catch(NoSuchMethodException baseline){return null;}}
    public static void run(boolean expectDeferred)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(boolean aa:new boolean[]{false,true})for(boolean illegal:new boolean[]{false,true}){
            Method font=OverlayJournalRenderingIndependentFixture.class.getDeclaredMethod("fonts",boolean.class);font.setAccessible(true);font.invoke(null,aa);
            Names g=new Names();g.setWorldOverlayCoverageEnabled(true);g.beginRenderer2DFrame();g.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);g.beginTransparentWorldOverlay();g.drawBoxAlpha(0,0,64,64,0x284871,128);
            Renderer3DFrame frame=SceneSpriteRasterPolicyFixture.frame();for(int i=0;i<17;i++)g.drawSceneEntity(5,10,10,16,20,0,0,0,0,7,frame,GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);
            if(expectDeferred){boolean pending=((Boolean)GraphicsController.class.getMethod("hasPendingSegmentedText").invoke(g)).booleanValue();int[] before=work(g);if(!pending||before[10]!=51||before[2]!=0)throw new AssertionError("51 legal WORLD names fragmented before a real consumer");}
            if(illegal){g.illegal=true;g.drawSceneEntity(5,10,10,16,20,0,0,0,0,7,frame,GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);}
            WorldOverlayCoverage.Frame result=g.borrowWorldOverlayCoverage();
            if(illegal&&(result.valid||g.getSceneSpriteRasterFailure().isEmpty()))throw new AssertionError("genuine SCENE guard bypassed");
            if(!illegal&&(!result.valid||!g.getSceneSpriteRasterFailure().isEmpty()))throw new AssertionError("legal WORLD replay invented Scene ownership failure");
            if(expectDeferred){int[] after=work(g);if(after[2]!=1||((Boolean)GraphicsController.class.getMethod("hasPendingSegmentedText").invoke(g)).booleanValue())throw new AssertionError("51 WORLD names did not materialize exactly one native segment");}
            ByteBuffer bytes=ByteBuffer.allocate(g.pixelData.length*8);for(int c:g.pixelData)bytes.putInt(c);for(float t:result.transmittance)bytes.putInt(Float.floatToRawIntBits(t));hash.update(bytes.array());hash.update(g.getSceneSpriteRasterFailure().getBytes("UTF-8"));
        }
        StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format("%02x",b&255));System.out.println("PHASE-CONTEXT "+text);
    }
}
