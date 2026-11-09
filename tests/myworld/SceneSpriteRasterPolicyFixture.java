package orsc.graphics.two;

import com.openrsc.client.model.Sprite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import orsc.Config;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DSettings;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.two.GraphicsController.SceneSpriteRasterPolicy;

/** Real capture, scoped actor draw and production composite: no GPU mock ownership. */
public final class SceneSpriteRasterPolicyFixture {
    static final int W=64,H=64,BACKGROUND=0x010203,ORDER=7,SPRITE_ID=5;
    static int checks;
    static final Sprite SPRITE=new Sprite(new int[]{0,0xff000000,0x808080,0xff4444,0x998877,0xffffff,
        0x112233,0x334455,0x557799,0x123456,0,0xff7788,0xffffff,0xcccccc,0x8899aa,0},4,4);
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Object graphicsField(GraphicsController target,String name)throws Exception{Field f=GraphicsController.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Renderer3DFrame frame()throws Exception{
        Constructor<?> constructor=null;
        for(Constructor<?> c:Renderer3DFrame.class.getDeclaredConstructors())if(c.getParameterTypes().length==16)constructor=c;
        check(constructor!=null,"production frame constructor");constructor.setAccessible(true);
        Object[] args=new Object[16];Arrays.fill(args,0);args[3]=W;args[4]=H;
        args[15]=java.lang.reflect.Array.newInstance(Class.forName("orsc.graphics.three.Renderer3DTextureData"),0);
        Renderer3DFrame frame=(Renderer3DFrame)constructor.newInstance(args);
        Method anchor=null;for(Method m:Renderer3DFrame.class.getDeclaredMethods())if(m.getName().equals("addSpriteAnchor"))anchor=m;
        check(anchor!=null,"production anchor method");anchor.setAccessible(true);
        Object[] a={1,SPRITE_ID,0,ORDER,123,0,0,123,20,20,10,10,16,20,100,3,true};
        check((Integer)anchor.invoke(frame,a)==0,"owned anchor index");return frame;
    }
    static final class Actor extends GraphicsController {
        boolean mirror;int skew;int opacity=255;int dummy=1;boolean explode;boolean rawBox;boolean progressOverlay;
        Actor(){super(W,H,1);SPRITE.setSomething(4,4);}
        @Override public void drawEntity(int index,int x,int y,int width,int height,int movement,int topSkew,int pick){
            if(explode)throw new IllegalStateException("fixture scoped failure");
            if(rawBox){drawBox(10,10,3,3,0xabcdef);return;}
            withRenderer2DLegacySpriteId(SPRITE_ID,()->{
                drawSpriteClipping(SPRITE,x,y,width,height,0x44aa88,0xff7744,0x3355ff,mirror,skew,dummy,(opacity<<24)|0xeeeeee);
                drawSpriteClipping(SPRITE,x+2,y+2,width-3,height-3,0xaabbcc,0xffffff,0,mirror,skew,dummy,0xffffffff);
            });
            if(progressOverlay){Renderer2DFrame.Phase old=getRenderer2DPhase();setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);
                try{drawBox(35,35,3,3,0xabcdef);}finally{setRenderer2DPhase(old);}}
        }
    }
    static void start(Actor actor,Renderer2DFrame.Phase phase,boolean coverage){
        actor.beginRenderer2DFrame();actor.setRenderer2DPhase(phase);
        if(coverage){actor.setWorldOverlayCoverageEnabled(true);actor.beginTransparentWorldOverlay();}
        else{actor.beginOpaqueWorldOverlay();actor.setWorldOverlayCoverageEnabled(false);}
        Arrays.fill(actor.pixelData,BACKGROUND);
    }
    static void draw(Actor actor,Renderer3DFrame frame,SceneSpriteRasterPolicy policy){
        actor.drawSceneEntity(SPRITE_ID,10,10,16,20,0,actor.skew,0,0,ORDER,frame,policy);
    }
    static int painted(Actor actor){int count=0;for(int color:actor.pixelData)if(color!=BACKGROUND)count++;return count;}
    static String commandFingerprint(Renderer3DFrame frame)throws Exception{
        Renderer3DFrame.WorldSpriteSnapshot snapshot=frame.getWorldSpriteSnapshot(0);
        List<Renderer2DFrame.SpriteCommand> commands=new ArrayList<Renderer2DFrame.SpriteCommand>();
        StringBuilder metadata=new StringBuilder();
        for(int i=0;i<snapshot.getLayerCount();i++){
            Renderer2DFrame.SpriteCommand c=snapshot.getLayer(i);commands.add(c);
            metadata.append(c.getX()).append(',').append(c.getY()).append(',').append(c.getWidth()).append(',').append(c.getHeight())
                .append(',').append(c.getSourceStartX16()).append(',').append(c.getSourceStartY16()).append(',').append(c.getSourceScaleX16())
                .append(',').append(c.getSourceScaleY16()).append(',').append(c.getTopX16()).append(',').append(c.getBottomX16())
                .append(',').append(c.isMirrorX()).append(',').append(c.requiresOrderedReplay()).append(',').append(c.getTransform().hashCode()).append(';');
        }
        Class<?> builder=Class.forName("orsc.OpenGLSpriteTextureBuilder");
        Method composite=builder.getDeclaredMethod("buildCompositeCharacterSpriteTexture",List.class);composite.setAccessible(true);
        Object result=composite.invoke(null,commands);check(result!=null,"production composite exists");
        Object texture=field(result,"textureData");Method pixels=texture.getClass().getDeclaredMethod("pixels");pixels.setAccessible(true);
        java.nio.ByteBuffer buffer=(java.nio.ByteBuffer)pixels.invoke(texture);byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
        return metadata+":"+field(result,"x")+","+field(result,"y")+","+field(result,"width")+","+field(result,"height")+":"+Arrays.toString(bytes);
    }
    static void policyCases()throws Exception{
        for(boolean mirror:new boolean[]{false,true})for(int skew:new int[]{0,3,-3}){
            Actor legacy=new Actor();legacy.mirror=mirror;legacy.skew=skew;
            Renderer3DFrame reference=frame();start(legacy,Renderer2DFrame.Phase.SCENE,false);
            // Existing signature is deliberately DEFAULT, preserving the legacy ordered restriction.
            legacy.drawSceneEntity(SPRITE_ID,10,10,16,20,0,skew,0,0,ORDER,reference);
            check(reference.getWorldSpriteSnapshot(0).getLayerCount()==2,"default snapshot layers");
            check((painted(legacy)>0)==(!Renderer2DSettings.canReplayOpenGLWorldSprites()||mirror||skew!=0),"default raster policy unchanged");
            String fingerprint=commandFingerprint(reference);
            Actor gpu=new Actor();gpu.mirror=mirror;gpu.skew=skew;Renderer3DFrame owned=frame();
            Object anchor=owned.getWorldSpriteSnapshot(0).getAnchor();start(gpu,Renderer2DFrame.Phase.SCENE,true);
            draw(gpu,owned,SceneSpriteRasterPolicy.GPU_OWNED);
            check(painted(gpu)==0,"GPU actor must never paint CPU overlay: mirror="+mirror+" skew="+skew);
            check(gpu.getSceneSpriteRasterFailure().isEmpty()&&gpu.borrowWorldOverlayCoverage().valid,"valid owned layer frame");
            check(owned.getWorldSpriteSnapshot(0).getAnchor()==anchor,"depth anchor identity unchanged");
            String gpuFingerprint=commandFingerprint(owned);
            if(!fingerprint.equals(gpuFingerprint)){
                int at=0;while(at<Math.min(fingerprint.length(),gpuFingerprint.length())&&fingerprint.charAt(at)==gpuFingerprint.charAt(at))at++;
                throw new AssertionError("composite mismatch mirror="+mirror+" skew="+skew+" offset="+at+" expected="+
                    fingerprint.substring(Math.max(0,at-40),Math.min(fingerprint.length(),at+120))+" actual="+
                    gpuFingerprint.substring(Math.max(0,at-40),Math.min(gpuFingerprint.length(),at+120)));
            }
            check(true,"exact ordered source/bounds/RGBA unchanged");
            for(float t:gpu.borrowWorldOverlayCoverage().transmittance)check(t==1f,"GPU layers leave overlay T transparent");
            check(graphicsField(gpu,"sceneSpriteRasterPolicy")==SceneSpriteRasterPolicy.DEFAULT,"scope restores default policy");
            check(!(Boolean)graphicsField(gpu,"guardGpuSceneSoftwareWrites"),"scope restores inactive write guard");
            check(graphicsField(gpu,"renderer2DSceneFrame")==null&&(Integer)graphicsField(gpu,"renderer2DSceneSpriteAnchorIndex")==-1,"scope restores frame/anchor");
            Actor software=new Actor();software.mirror=mirror;software.skew=skew;Renderer3DFrame complete=frame();
            start(software,Renderer2DFrame.Phase.SCENE,false);draw(software,complete,SceneSpriteRasterPolicy.FORCE_SOFTWARE);
            check(painted(software)>0,"complete fallback paints every layer, including unmirrored");
            check(fingerprint.equals(commandFingerprint(complete)),"force-software capture retained");
            // Existing direct-ground comparator can coexist with subsequent owned legacy capture.
            Renderer3DFrame direct=frame();Object snapshot=direct.getWorldSpriteSnapshot(0);
            Method set=snapshot.getClass().getDeclaredMethod("setDirectGroundItemLayer",Renderer2DFrame.SpriteCommand.class);set.setAccessible(true);
            set.invoke(snapshot,reference.getWorldSpriteSnapshot(0).getLayer(0));
            start(gpu,Renderer2DFrame.Phase.SCENE,true);draw(gpu,direct,SceneSpriteRasterPolicy.GPU_OWNED);
            check(gpu.getSceneSpriteRasterFailure().isEmpty()&&direct.getWorldSpriteSnapshot(0).getLayerCount()==2,"direct predecessor does not reject owned capture");
            for(Renderer2DFrame.Phase phase:new Renderer2DFrame.Phase[]{Renderer2DFrame.Phase.UI_OVERLAY,Renderer2DFrame.Phase.WORLD_OVERLAY}){
                Actor ui=new Actor();ui.mirror=mirror;ui.skew=skew;start(ui,phase,false);draw(ui,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
                check(painted(ui)>0&&ui.getSceneSpriteRasterFailure().isEmpty(),"GPU Scene scope grants no UI/world-overlay replacement");
            }
        }
        Actor actor=new Actor();actor.mirror=true;actor.skew=3;
        actor.opacity=128;start(actor,Renderer2DFrame.Phase.SCENE,true);Renderer3DFrame translucent=frame();
        draw(actor,translucent,SceneSpriteRasterPolicy.GPU_OWNED);
        check(actor.getSceneSpriteRasterFailure().isEmpty()&&painted(actor)==0&&translucent.getWorldSpriteSnapshot(0).getLayer(0).getAlpha()==128,"explicit ownership captures partial alpha without native world-replay setting");
        actor.opacity=255;actor.progressOverlay=true;start(actor,Renderer2DFrame.Phase.SCENE,true);
        draw(actor,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
        check(actor.getSceneSpriteRasterFailure().isEmpty()&&painted(actor)==9&&actor.borrowWorldOverlayCoverage().transmittance[35+35*W]==0f,"scoped WORLD_OVERLAY progress/name remains software-owned");
        actor.progressOverlay=false;
        for(int[] position:new int[][]{{-500,-500},{500,500},{-500,10},{500,10},{10,-500},{10,500}}){
            start(actor,Renderer2DFrame.Phase.SCENE,true);Renderer3DFrame clipped=frame();
            actor.drawSceneEntity(SPRITE_ID,position[0],position[1],16,20,0,3,0,0,ORDER,clipped,SceneSpriteRasterPolicy.GPU_OWNED);
            check(actor.getSceneSpriteRasterFailure().isEmpty()&&actor.borrowWorldOverlayCoverage().valid&&painted(actor)==0,"fully clipped actor is a valid no-op");
            check(clipped.getWorldSpriteSnapshot(0).getLayerCount()==0,"no-op claims no captured layer");
        }
        start(actor,Renderer2DFrame.Phase.SCENE,true);actor.interlace=true;draw(actor,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
        check(!actor.getSceneSpriteRasterFailure().isEmpty()&&!actor.borrowWorldOverlayCoverage().valid&&painted(actor)==0,"capture failure rejects frame, never mixed CPU fallback");
        actor.interlace=false;start(actor,Renderer2DFrame.Phase.SCENE,false);
        actor.drawSceneEntity(SPRITE_ID,10,10,16,20,0,3,0,99,ORDER,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
        check(!actor.getSceneSpriteRasterFailure().isEmpty()&&painted(actor)==0,"capture accepted without owned anchor is not GPU ownership");
        actor.dummy=0;start(actor,Renderer2DFrame.Phase.SCENE,true);draw(actor,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
        check(!actor.getSceneSpriteRasterFailure().isEmpty()&&!actor.borrowWorldOverlayCoverage().valid&&painted(actor)==0,"unsupported legacy clipping sentinel fails closed");actor.dummy=1;
        actor.beginRenderer2DFrame();check(actor.getSceneSpriteRasterFailure().isEmpty(),"sticky failure resets next frame");
        actor.rawBox=true;start(actor,Renderer2DFrame.Phase.SCENE,true);draw(actor,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
        check(!actor.getSceneSpriteRasterFailure().isEmpty()&&!actor.borrowWorldOverlayCoverage().valid,"uncaptured software-only actor draw fails closed");
        actor.rawBox=false;actor.explode=true;start(actor,Renderer2DFrame.Phase.SCENE,false);
        boolean thrown=false;try{draw(actor,frame(),SceneSpriteRasterPolicy.GPU_OWNED);}catch(IllegalStateException expected){thrown=true;}
        check(thrown&&graphicsField(actor,"sceneSpriteRasterPolicy")==SceneSpriteRasterPolicy.DEFAULT,"exception restores policy");
        check(!(Boolean)graphicsField(actor,"guardGpuSceneSoftwareWrites"),"exception restores write guard");
        check(graphicsField(actor,"renderer2DSceneFrame")==null&&(Integer)graphicsField(actor,"renderer2DSceneSpriteDrawOrder")==-1,"exception restores owner/order");
    }
    public static void main(String[] args)throws Exception{
        Config.S_WANT_CUSTOM_SPRITES=true;
        if(args.length>0&&args[0].equals("capture-off")){
            Actor actor=new Actor();actor.mirror=true;actor.skew=3;start(actor,Renderer2DFrame.Phase.SCENE,false);
            draw(actor,frame(),SceneSpriteRasterPolicy.GPU_OWNED);
            check(!actor.getSceneSpriteRasterFailure().isEmpty()&&painted(actor)==0,"capture-disabled GPU owner explicitly fails even with T disabled");
        }else policyCases();
        System.out.println("PASS "+checks+" scoped Scene sprite raster checks; worldReplay="+Renderer2DSettings.canReplayOpenGLWorldSprites());
    }
}
