package orsc;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** CPU boundary checks against actual production fields, projections and shader strings. */
public final class BrowserLightingFixture {
    static int checks;
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Renderer3DFrame frame(int level,Renderer3DWorldChunkFrame world) {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);
        scene.setCamera(0,-4,0,768,0,0,600);scene.setRendererSignedWorldLevel(level);scene.endScene(0);
        Renderer3DFrame frame=scene.getRenderer3DFrame();frame.setWorldChunkFrame(world);return frame;
    }
    static BrowserLightingData.Frame prepare(BrowserLightingData data,Renderer3DFrame frame,int maximum) {
        Renderer3DFrame previous=RendererFrameLighting.bind(frame);try{return data.prepare(frame,maximum);}finally{RendererFrameLighting.restore(previous);}
    }
    static byte[] bytes(ByteBuffer source){byte[] result=new byte[source.remaining()];source.get(result);return result;}
    static BrowserLightingData.Mask mask(BrowserLightingData.Frame frame,int unit){for(BrowserLightingData.Mask mask:frame.changes)if(mask.unit==unit)return mask;throw new AssertionError("Missing mask "+unit);}
    static Renderer3DWorldChunkFrame.ChunkMesh glowing(){
        return new Renderer3DWorldChunkFrame.ChunkMesh(0,10,9,0,0,new int[]{0,0,0,128,0,0,0,0,128},new float[3],new float[3],new int[3],null,null,new int[]{0,1,2},new int[]{-2},new int[]{10},new Renderer3DModelKind[]{Renderer3DModelKind.GAME_OBJECT},null,
            new Renderer3DWorldChunkFrame.GlowEmitter[]{new Renderer3DWorldChunkFrame.GlowEmitter(Renderer3DModelKind.GAME_OBJECT,64,-32,64,256,0x33ccff,255)},null,null,0,0,0,0,0,true,92);
    }
    public static void main(String[] args)throws Exception {
        RendererRemasterLightSettings.adjustAzimuth(0);RendererRemasterLightSettings.adjustElevation(0);
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
        RendererTerrainDefinitionSettings.setMode(RendererTerrainDefinitionSettings.Mode.RELIEF_AO);RendererToneSettings.setMode(RendererToneSettings.Mode.CYCLE);RendererDayNightCycle.syncServerTime(3600000,900000);
        Renderer3DWorldChunkFrame world=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(TerrainDefinitionFieldFixture.mesh(0,91),glowing(),GeometryShadowRoofMaskFixture.chunk(0,0,0,1,false,93)));
        Renderer3DFrame frame=frame(0,world);BrowserLightingData data=new BrowserLightingData();BrowserLightingData.Frame first=prepare(data,frame,4096);
        require(first.values.length==81&&first.values[0]==1&&first.values[1]==1,"schema/active flag");
        require(first.values[3]==2048&&first.values[4]==4096&&first.values[5]==1,"High quality resolution/filter policy");
        require(first.values[6]==1&&first.values[7]==0,"initial far history");
        TerrainDefinitionField actual=new TerrainDefinitionField();require(actual.update(frame),"actual field missing");
        BrowserLightingData.Mask terrain=mask(first,7);require(Arrays.equals(terrain.rgba,bytes(actual.pixels())),"terrain builder bytes differ");
        require(terrain.bounds[0]==actual.minX&&terrain.bounds[1]==actual.minZ&&terrain.bounds[2]==1f/actual.width&&terrain.bounds[3]==1f/actual.height,"terrain source-coordinate bounds differ");
        RemasterGlowMask glow=new RemasterGlowMaskBuilder().build(world).mask;BrowserLightingData.Mask light=mask(first,2);
        require(Arrays.equals(light.rgba,bytes(glow.pixels())),"glow builder bytes differ");require(light.bounds[0]==glow.minX&&light.bounds[2]==glow.invSpanX,"glow coordinate bounds differ");
        GeometryShadowRoofMask roof=new GeometryShadowRoofMask.Cache().get(world,frame.getActivePlane());BrowserLightingData.Mask roofExport=mask(first,5);
        require(Arrays.equals(roofExport.rgba,bytes(roof.pixels())),"declared roof coverage differs");
        int offX=GeometryShadowCoordinates.worldOffsetX(world),offZ=GeometryShadowCoordinates.worldOffsetZ(world);Renderer3DFrame previous=RendererFrameLighting.bind(frame);
        DirectionalShadowProjection expected;
        try{expected=new DirectionalShadowProjection((double)frame.getCameraOffsetX()+offX,frame.getCameraOffsetY(),(double)frame.getCameraOffsetZ()+offZ,RendererRemasterLightSettings.getLightDirectionX(),RendererRemasterLightSettings.getLightDirectionY(),RendererRemasterLightSettings.getLightDirectionZ(),32*128f,2048,null,Double.isFinite(frame.getShadowReferenceHeight())?frame.getShadowReferenceHeight():frame.getCameraOffsetY());}finally{RendererFrameLighting.restore(previous);}
        require(Arrays.equals(expected.matrix,Arrays.copyOfRange(first.values,32,48)),"High ground-aligned matrix changed");
        for(int age=1;age<150;age++){BrowserLightingData.Frame steady=prepare(data,frame,4096);require(steady.values[6]==0,"far rebuild before production age "+age);require(steady.changes.length==0,"steady mask uploaded again");}
        require(prepare(data,frame,4096).values[6]==1,"far age150 did not refresh");
        Renderer3DFrame underground=frame(-8,world);BrowserLightingData.Frame day=prepare(data,underground,4096);RendererDayNightCycle.syncServerTime(3600000,2700000);BrowserLightingData.Frame night=prepare(data,underground,4096);
        require(day.values[1]==0&&night.values[1]==0&&night.values[15]==-8,"negative level sunlight not suppressed");
        require(day.values[13]==1&&day.values[14]==1&&night.values[13]==1&&night.values[14]==1,"underground relief/glow lost");
        require(offX!=0||offZ!=0,"fixture must exercise nonzero absolute world basis");
        require(day.values[9]==offX&&day.values[10]==offZ&&night.values[9]==offX&&night.values[10]==offZ,"underground AO absolute world basis lost");
        require(prepare(data,frame,4096).values[6]==1,"surface return reused underground history");
        RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);BrowserLightingData.Frame low=prepare(data,frame,2048);
        require(low.values[3]==1024&&low.values[4]==0&&low.values[5]==0&&low.values[6]==0,"Low policy changed");
        RendererShadowSettings.setQuality(RendererShadowSettings.Quality.OFF);BrowserLightingData.Frame off=prepare(data,frame,4096);require(off.values[1]==0,"Off still shadows");
        require(off.values[9]==offX&&off.values[10]==offZ&&off.values[13]==1&&off.values[14]==1,"Off lost AO/glow absolute world basis");
        RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);data.clear();require(prepare(data,frame,2048).values[4]==2048,"production far size fallback changed");
        try{prepare(data,frame,1024);throw new AssertionError("unsupported High silently lowered");}catch(IllegalArgumentException expectedLimit){checks++;}
        Field v=OpenGLGeometryShadows.class.getDeclaredField("DEPTH_VERTEX"),f=OpenGLGeometryShadows.class.getDeclaredField("DEPTH_FRAGMENT");v.setAccessible(true);f.setAccessible(true);
        require(BrowserLightingData.depthVertex().equals(v.get(null))&&BrowserLightingData.depthFragment().equals(f.get(null)),"depth shaders forked from production");
        data.clear();require(prepare(data,frame,4096).changes.length==3,"session clear did not restore masks");
        System.out.println("PASS browser lighting "+checks+" CPU checks: production mask bytes/bounds, High/Low/Off, ground matrix, far age, negative/return and shared shaders");
    }
}
