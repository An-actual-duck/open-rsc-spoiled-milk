package orsc.graphics.three;

import java.lang.reflect.*;
import java.util.Arrays;
import orsc.graphics.RendererTransparency;
import orsc.graphics.two.MudClientGraphics;

/** Actual Scene texture animation intent, immutable snapshots, and cached-caster policy. */
public final class AnimatedTextureShadowFixture {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Renderer3DTextureData texture(Scene scene,int id){scene.endScene(0);return scene.getRenderer3DFrame().getTexture(id);}
    static boolean far(Renderer3DTextureData texture)throws Exception{
        Method policy=Class.forName("orsc.RendererShadowCasterPolicy").getDeclaredMethod("isFarCaster",Renderer3DTextureData.class);policy.setAccessible(true);return (Boolean)policy.invoke(null,texture);
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        MudClientGraphics graphics=new MudClientGraphics(64,64,1);Scene scene=new Scene(graphics,4,16,4);
        scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.setFrustum(0,4,4,24);
        int[] palette={0x204060,RendererTransparency.LEGACY_TEXTURE_TRANSPARENT_KEY};byte[] indices=new byte[4096];
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)indices[y*64+x]=(byte)((y%7==0||x%11==0)?1:0);
        // Animation intent before resource/data load must survive loadTexture.
        scene.d(25013,3);scene.loadTexture(3,palette,0,indices);
        Renderer3DTextureData before=texture(scene,3);require(before.hasAnimatedAlpha(),"preload animation intent lost");
        int[] oldPixels=before.getRgbaPixels().clone();long oldSignature=before.getSignature();
        scene.d(25013,3);Renderer3DTextureData after=texture(scene,3);
        require(after.hasAnimatedAlpha(),"loaded animation intent lost");
        require(after.getSignature()!=oldSignature && !Arrays.equals(oldPixels,after.getRgbaPixels()),"actual Scene.d did not animate alpha/RGB");
        require(Arrays.equals(oldPixels,before.getRgbaPixels()) && before.getSignature()==oldSignature,"published texture snapshot mutated");
        require(before.getOpaquePixelCount()==after.getOpaquePixelCount(),"scroll changed coverage count");
        require(!far(after),"animated cutout entered cached far map");
        // Static same-ID textures are not special-cased by renderer ID.
        scene.loadTexture(12,palette,0,indices);scene.loadTexture(17,palette,0,indices);
        require(far(texture(scene,12)) && far(texture(scene,17)),"static cutout incorrectly excluded by ID");
        // An evicted resource still gets immutable intent without pixel changes.
        scene.loadTexture(4,palette,0,indices);Renderer3DTextureData unloaded=texture(scene,4);
        Field resources=Scene.class.getDeclaredField("resourceDatabase");resources.setAccessible(true);((int[][])resources.get(scene))[4]=null;
        scene.d(25013,4);Renderer3DTextureData marked=texture(scene,4);
        require(marked.hasAnimatedAlpha() && !unloaded.hasAnimatedAlpha(),"evicted texture intent is not immutable");
        require(marked.getSignature()==unloaded.getSignature() && Arrays.equals(marked.getRgbaPixels(),unloaded.getRgbaPixels()),"intent changed visual bytes/signature");
        Renderer3DTextureData opaque=Renderer3DTextureData.fromLegacyPalette(9,new int[]{0x204060},0,new byte[4096],true);
        require(!opaque.hasAnimatedAlpha() && far(opaque),"opaque RGB-only animation lost stable far geometry");
        require(far(null),"untextured opaque caster excluded");
        // Repeated scrolling cannot perturb the stable fence's far signature.
        long fenceSignature=texture(scene,12).getSignature();
        for(int i=0;i<80;i++){scene.d(25013,3);require(texture(scene,12).getSignature()==fenceSignature && far(texture(scene,12)) && !far(texture(scene,3)),"animated alpha invalidated static fence policy");}
        System.out.println("PASS actual Scene.d animation intent before load/eviction, immutable bytes, static cutout far policy, moving alpha near-only, opaque animation unchanged.");
    }
}
