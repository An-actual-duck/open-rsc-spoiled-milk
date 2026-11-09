package orsc;

import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
import java.util.Arrays;

/** Actual immutable Scene publication and render-thread presentation contracts. */
public final class UndergroundLightingFixture {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Scene scene(){
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Scene s=new Scene(new MudClientGraphics(64,64,1),4,16,4);
        s.setMidpoints(32,true,64,32,32,9,64);s.setCamera(0,-4,0,768,0,0,600);return s;
    }
    static Renderer3DFrame frame(int level){Scene s=scene();s.setRendererSignedWorldLevel(level);s.endScene(0);return s.getRenderer3DFrame();}
    static float[] presentation(){RendererDayNightCycle.Presentation p=RendererDayNightCycle.currentPresentation();return new float[]{p.redMultiplier,p.greenMultiplier,p.blueMultiplier,p.toneBlend,p.brightnessMultiplier,p.fogRed,p.fogGreen,p.fogBlue,RendererRemasterLightSettings.getLightDirectionX(),RendererRemasterLightSettings.getLightDirectionY(),RendererRemasterLightSettings.getLightDirectionZ()};}
    static void time(boolean night){RendererDayNightCycle.syncServerTime(3600000,night?2700000:900000);}
    public static void main(String[] args)throws Exception{
        RendererToneSettings.setMode(RendererToneSettings.Mode.CYCLE);
        PacketHandler packet=new PacketHandler(null);
        require(packet.getLogicalWorldLevel(2832)==-1,"legacy dungeon sign");
        require(packet.getLogicalWorldLevel(944)==1&&packet.getLogicalWorldLevel(1888)==2,"positive floor sign");
        Scene s=scene();s.setRendererSignedWorldLevel(-5);s.endScene(0);Renderer3DFrame deep=s.getRenderer3DFrame();
        deep.setRoofVisibility(Renderer3DRoofVisibility.VISIBLE,-5);
        require(deep.getSignedWorldLevel()==-5&&deep.isUnderground()&&deep.getActivePlane()==0,"signed scope confused with clamped plane");
        for(int level:new int[]{-1,0,1,10,0,-8}){s.setRendererSignedWorldLevel(level);s.endScene(0);require(s.getRenderer3DFrame().getSignedWorldLevel()==level,"scope transition lost");require(deep.getSignedWorldLevel()==-5,"queued old frame mutated");}
        s.setRendererSignedWorldLevel(0);s.endScene(0);require(!s.getRenderer3DFrame().isUnderground(),"login producer reset leaked deep scope");
        LayeredScenePresentationLatch latch=new LayeredScenePresentationLatch();
        for(int[] transition:new int[][]{{-1,0},{0,-2},{-2,10},{10,-1},{-1,-1}}){
            Renderer3DFrame oldFrame=frame(transition[0]);
            boolean sameScope=transition[0]==transition[1];latch.begin(sameScope);
            require(latch.shouldRetainLastPresentedFrame()==sameScope,"different-level geometry retained during activation");
            // New context may be accepted while this immutable old frame is queued.
            Renderer3DFrame next=frame(transition[1]);
            Renderer3DFrame previous=RendererFrameLighting.bind(oldFrame);
            try{require(RendererFrameLighting.isUnderground()==(transition[0]<0),"pending context retagged queued geometry");}finally{RendererFrameLighting.restore(previous);}
            latch.updatePending(false);
            if(sameScope){require(latch.shouldRetainLastPresentedFrame(),"same-scope old frame lost prematurely");latch.completeFreshFrame(7,2,true);latch.completeFreshFrame(7,2,true);}
            require(!latch.shouldRetainLastPresentedFrame(),"fresh matching scene failed to release");
            previous=RendererFrameLighting.bind(next);try{require(RendererFrameLighting.isUnderground()==(transition[1]<0),"fresh scene has old lighting");}finally{RendererFrameLighting.restore(previous);}
        }
        for(RendererBrightnessSettings.Mode brightness:RendererBrightnessSettings.Mode.values()) {
            RendererBrightnessSettings.setMode(brightness);
            for(int level:new int[]{-1,-2,-20,Integer.MIN_VALUE}) {
                Renderer3DFrame previous=RendererFrameLighting.bind(frame(level));
                try {
                    time(false);float[] day=presentation();time(true);float[] night=presentation();
                    require(Arrays.equals(day,night),"underground changes with clock at "+level);
                    require(day[0]==1&&day[1]==1&&day[2]==1&&day[4]==brightness.multiplier,"neutral/saved brightness lost");
                    final boolean[] leak={true};Thread other=new Thread(()->leak[0]=RendererFrameLighting.isUnderground());other.start();other.join();require(!leak[0],"render thread scope leaked to producer");
                    Renderer3DFrame outer=RendererFrameLighting.bind(frame(10));
                    try{require(!RendererFrameLighting.isUnderground(),"upper frame inherited prior context");throw new IllegalStateException("test draw failure");}
                    catch(IllegalStateException expected){}finally{RendererFrameLighting.restore(outer);}
                    require(RendererFrameLighting.isUnderground(),"exception restore lost enclosing scope");
                } finally {RendererFrameLighting.restore(previous);}
                require(!RendererFrameLighting.isUnderground(),"finally leaked to UI/login frame");
            }
        }
        for(int level:new int[]{0,1,2,10}){
            Renderer3DFrame previous=RendererFrameLighting.bind(frame(level));
            try{time(false);float[] day=presentation();time(true);float[] night=presentation();require(!Arrays.equals(day,night),"outside day/night policy changed "+level);}
            finally{RendererFrameLighting.restore(previous);}
        }
        require(RendererToneSettings.getMode()==RendererToneSettings.Mode.CYCLE,"saved tone setting mutated");
        System.out.println("PASS signed immutable frames, deep/legacy/upper scope transitions, day/night neutral brightness/fog/light, thread isolation and exception restoration");
    }
}
