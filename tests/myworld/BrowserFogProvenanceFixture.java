package orsc;

import java.lang.reflect.Method;
import java.util.Arrays;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.Scene;
import orsc.graphics.two.MudClientGraphics;

/** Actual immutable texture factories and native static-signature dependency oracle. */
public final class BrowserFogProvenanceFixture {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Renderer3DTextureData texture(int id,int color,boolean cutout,boolean animated,int type)throws Exception {
        Method factory=Renderer3DTextureData.class.getDeclaredMethod("fromLegacyPalette",int.class,int[].class,int.class,byte[].class,boolean.class);
        factory.setAccessible(true);
        byte[] indices=new byte[type==0?4096:16384];Arrays.fill(indices,(byte)1);if(cutout)indices[0]=0;
        return (Renderer3DTextureData)factory.invoke(null,id,new int[]{0xff00ff,color},type,indices,animated);
    }
    private static long nativeSignature(Renderer3DFrame frame)throws Exception {
        Method method=BrowserLightingData.class.getDeclaredMethod("staticSignature",Renderer3DFrame.class);method.setAccessible(true);
        return (Long)method.invoke(null,frame);
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setFrustum(0,11,7,1);
        scene.loadTexture(0,new int[]{0xff00ff,0x112233},0,new byte[4096]);scene.endScene(0);
        Renderer3DFrame frame=scene.getRenderer3DFrame();
        Renderer3DWorldChunkFrame.ChunkMesh source=BrowserFogDemandFixture.chunk(0,-300,0,128,0,9007199254740993L);
        frame.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(source)));
        BrowserCasterProvenance provenance=new BrowserCasterProvenance();Renderer3DTextureData[] textures=frame.getTextures();
        check(textures.length>0,"No actual texture catalog");textures[0]=null;
        int first=provenance.update(frame);long signature=nativeSignature(frame);
        for(Renderer3DTextureData opaque:new Renderer3DTextureData[]{texture(0,0x112233,false,false,0),texture(0,0xaabbcc,false,false,1),null}) {
            textures[0]=opaque;check(nativeSignature(frame)==signature,"Native opaque catalog unexpectedly invalidated");
            check(provenance.update(frame)==first,"Harmless opaque catalog change dropped blend provenance");
        }
        textures[0]=texture(0,0x112233,true,false,0);int cutout=provenance.update(frame);
        check(cutout!=first&&nativeSignature(frame)!=signature,"Cutout presence did not invalidate");
        check(provenance.update(frame)==cutout,"Identical immutable cutout changed provenance");
        textures[0]=texture(0,0x334455,true,false,0);check(provenance.update(frame)!=cutout,"Cutout contents were stale");
        int before=provenance.update(frame);textures[0]=texture(0,0x334455,true,true,0);
        check(provenance.update(frame)!=before,"Animated-alpha far exclusion not reflected");
        int excluded=provenance.update(frame);textures[0]=texture(0,0x8899aa,true,true,0);
        check(provenance.update(frame)==excluded,"Far-excluded alpha animation invalidated immutable far source");
        textures[0]=null;check(provenance.update(frame)==excluded,"Far-excluded catalog removal changed native provenance");
        frame.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(BrowserFogDemandFixture.chunk(0,-300,0,128,0,9007199254740992L))));
        check(provenance.update(frame)!=excluded,"64bit source signature lost low limb beyond JS safe-integer range");
        int beforeShape=provenance.update(frame);long beforeShapeSignature=nativeSignature(frame);
        int[] positions=new int[source.getVertexCount()*3];for(int n=0;n<positions.length;n++)positions[n]=source.getVertexCoord(n);
        Renderer3DWorldChunkFrame.ChunkMesh shape=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,positions,new float[6],new float[6],new int[6],new int[]{0,1,2,3,4,5},new int[]{-2},new int[]{0x777777},new orsc.graphics.three.Renderer3DModelKind[]{orsc.graphics.three.Renderer3DModelKind.TERRAIN},null,null,1,0,0,false,0,9007199254740992L);
        frame.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(shape)));
        check(nativeSignature(frame)==beforeShapeSignature,"Shape defense case unexpectedly changed native signature");
        check(provenance.update(frame)!=beforeShape,"Packed index shape changed under unchanged raw index count/signature");
        frame.release();System.out.println("PASS "+checks+" real source/texture provenance checks including unrelated opaque catalog, animated alpha and >2^53 signatures; no GPU claim");
    }
}
