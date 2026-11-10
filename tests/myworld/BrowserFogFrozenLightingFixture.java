package orsc;

import java.util.Arrays;
import orsc.graphics.three.*;

/** Independently compiled pre-task lighting source vs captured projection plan. */
public final class BrowserFogFrozenLightingFixture {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args) {
        RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
        RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);
        BrowserLightingData candidate=new BrowserLightingData();FrozenBrowserLightingData reference=new FrozenBrowserLightingData();
        Renderer3DWorldChunkFrame world=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(
            BrowserFogDemandFixture.chunk(-3000,-1700,-2400,2048,0,1),
            BrowserFogDemandFixture.chunk(8000,4321,9000,2048,0,2)));
        for(int tick=0;tick<190;tick++) {
            RendererShadowSettings.setQuality(tick<155?RendererShadowSettings.Quality.HIGH:tick<161?RendererShadowSettings.Quality.LOW:tick<165?RendererShadowSettings.Quality.OFF:RendererShadowSettings.Quality.HIGH);
            RendererRemasterLightSettings.adjustAzimuth(tick==153?1:0);
            Renderer3DFrame frame=BrowserFogDemandFixture.frame(world,tick>=180&&tick<185?-1:0,tick<152?128:640,tick==151?2012:1500);
            if(tick==170)frame.setRoofVisibility(Renderer3DRoofVisibility.VISIBLE,1);
            Renderer3DFrame previous=RendererFrameLighting.bind(frame);
            try {
                BrowserLightingData.ShadowPlan plan=candidate.previewShadow(frame,4096);
                FrozenBrowserLightingData.Frame expected=reference.prepare(frame,4096);
                BrowserLightingData.Frame actual=candidate.prepare(frame,4096,plan);
                check(Arrays.equals(expected.values,actual.values),"Frozen policy values differ at tick "+tick);
                check(expected.changes.length==actual.changes.length,"Frozen mask count differs at tick "+tick);
                for(int n=0;n<actual.changes.length;n++) {
                    check(expected.changes[n].unit==actual.changes[n].unit,"Frozen mask unit differs");
                    check(Arrays.equals(expected.changes[n].rgba,actual.changes[n].rgba),"Frozen mask bytes differ");
                }
            } finally {RendererFrameLighting.restore(previous);frame.release();}
        }
        System.out.println("PASS "+checks+" frozen pre-task lighting policy/mask byte checks across190 frames; no GPU or speed claim");
    }
}
