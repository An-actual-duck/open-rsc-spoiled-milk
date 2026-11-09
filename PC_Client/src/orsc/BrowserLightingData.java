package orsc;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Bounded primitive export of the desktop lighting builders and projection policy.
 * The caller binds RendererFrameLighting and uploads/copies each returned array
 * synchronously before releasing its immutable frame. No browser/native API here. */
public final class BrowserLightingData {
    public static final int HEADER_FLOATS = 81;
    private final TerrainDefinitionField terrain = new TerrainDefinitionField();
    private final RemasterGlowMaskBuilder glow = new RemasterGlowMaskBuilder();
    private final GeometryShadowRoofMask.Cache roofs = new GeometryShadowRoofMask.Cache();
    private long terrainRevision = Long.MIN_VALUE, glowSignature = Long.MIN_VALUE, roofSignature = Long.MIN_VALUE;
    private DirectionalShadowProjection near, far, previousFar;
    private RendererShadowSettings.Quality quality;
    private long nearContext, farContext, farSignature, farRoofSignature;
    private int nearPlane, farPlane, farAge, blendAge;

    public static final class Mask {
        public final int unit, width, height;
        public final byte[] rgba;
        public final float[] bounds;
        Mask(int unit,int width,int height,ByteBuffer pixels,float[] bounds) {
            if(width<1||height<1||width>1024||height>1024||pixels.remaining()!=width*height*4)
                throw new IllegalArgumentException("Browser lighting mask dimensions exceed source bounds");
            this.unit=unit;this.width=width;this.height=height;this.bounds=bounds.clone();
            this.rgba=new byte[pixels.remaining()];pixels.get(this.rgba);
        }
    }
    public static final class Frame {
        public final float[] values;
        public final Mask[] changes;
        Frame(float[] values,List<Mask> changes){this.values=values;this.changes=changes.toArray(new Mask[changes.size()]);}
    }
    public static String depthVertex(){return depthSource("DEPTH_VERTEX");}
    public static String depthFragment(){return depthSource("DEPTH_FRAGMENT");}
    private static String depthSource(String name) {
        try {Field f=OpenGLGeometryShadows.class.getDeclaredField(name);f.setAccessible(true);return (String)f.get(null);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Production shadow source unavailable",error);}
    }
    public void clear() {
        terrain.clear();glow.clear();roofs.clear();terrainRevision=glowSignature=roofSignature=Long.MIN_VALUE;
        near=far=previousFar=null;quality=null;farAge=blendAge=0;
    }
    public Frame prepare(Renderer3DFrame frame,int maxTextureSize) {
        List<Mask> changes=new ArrayList<Mask>(3);
        Renderer3DWorldChunkFrame world=frame==null?null:frame.getWorldChunkFrame();
        boolean populated=world!=null&&world.getChunkCount()>0;
        ArrayList<Integer> excluded=new ArrayList<Integer>();
        if(populated)for(Renderer3DTextureData texture:frame.getTextures())
            if(texture!=null&&!RendererShadowCasterPolicy.isFarCaster(texture))excluded.add(texture.getTextureId());
        float[] values=new float[HEADER_FLOATS+excluded.size()];values[0]=1;values[15]=frame==null?0:frame.getSignedWorldLevel();
        values[80]=excluded.size();for(int n=0;n<excluded.size();n++)values[81+n]=excluded.get(n);
        if(!populated){near=far=previousFar=null;farAge=blendAge=0;return new Frame(values,changes);}
        if(world.getChunkCount()>256)throw new IllegalArgumentException("Browser lighting scene exceeds chunk bound");
        boolean directional=RendererLightingSettings.getMode()==RendererLightingSettings.Mode.DIRECTIONAL;
        if(directional&&RendererTerrainDefinitionSettings.getMode().shaderId>=2&&terrain.update(frame)) {
            values[13]=1;putBounds(values,24,terrain.minX,terrain.minZ,1f/terrain.width,1f/terrain.height);
            if(terrainRevision!=terrain.revision){changes.add(new Mask(7,terrain.width,terrain.height,terrain.pixels(),bounds(values,24)));terrainRevision=terrain.revision;}
        }
        RemasterGlowMaskBuild built=directional?glow.build(world):null;
        if(built!=null&&built.mask!=null) {
            RemasterGlowMask mask=built.mask;values[14]=1;putBounds(values,28,mask.minX,mask.minZ,mask.invSpanX,mask.invSpanZ);
            if(glowSignature!=mask.signature){changes.add(new Mask(2,mask.width,mask.height,mask.pixels(),bounds(values,28)));glowSignature=mask.signature;}
        }
        RendererShadowSettings.Quality next=RendererShadowSettings.getQuality();
        if(quality!=next){near=far=previousFar=null;farAge=blendAge=0;}quality=next;
        values[2]=next.ordinal();
        if(frame.isUnderground()||!directional||next==RendererShadowSettings.Quality.OFF) {
            near=far=previousFar=null;farAge=blendAge=0;return new Frame(values,changes);
        }
        if(maxTextureSize<next.nearSize)throw new IllegalArgumentException("Browser texture limit cannot preserve selected shadow quality");
        int farSize=OpenGLGeometryShadows.supportedFarSize(maxTextureSize);
        if(next==RendererShadowSettings.Quality.HIGH&&maxTextureSize<farSize)throw new IllegalArgumentException("Browser cannot allocate production far shadow resolution");
        int offX=GeometryShadowCoordinates.worldOffsetX(world),offZ=GeometryShadowCoordinates.worldOffsetZ(world);
        values[9]=offX;values[10]=offZ;
        double x=(double)frame.getCameraOffsetX()+offX,y=frame.getCameraOffsetY(),z=(double)frame.getCameraOffsetZ()+offZ;
        float lx=RendererRemasterLightSettings.getLightDirectionX(),ly=RendererRemasterLightSettings.getLightDirectionY(),lz=RendererRemasterLightSettings.getLightDirectionZ();
        long context=frame.getShadowContextEpoch();int plane=frame.getActivePlane();
        if(near!=null&&(nearContext!=context||nearPlane!=plane||Math.hypot(Math.hypot(x-near.centerX,z-near.centerZ),y-near.centerY)>next.nearRadiusTiles*128.0))near=null;
        double reference=frame.getShadowReferenceHeight();
        near=next==RendererShadowSettings.Quality.HIGH
            ?new DirectionalShadowProjection(x,y,z,lx,ly,lz,next.nearRadiusTiles*128f,next.nearSize,near,Double.isFinite(reference)?reference:y)
            :new DirectionalShadowProjection(x,y,z,lx,ly,lz,next.nearRadiusTiles*128f,next.nearSize,near);
        nearContext=context;nearPlane=plane;
        GeometryShadowRoofMask roof=roofs.get(world,plane);
        if(!roof.isEmpty()) {
            values[11]=1;values[12]=roof.plane;putBounds(values,20,roof.minX,roof.minZ,1f/(roof.width*128),1f/(roof.height*128));
            if(roofSignature!=roof.signature){changes.add(new Mask(5,roof.width,roof.height,roof.pixels(),bounds(values,20)));roofSignature=roof.signature;}
        }
        long signature=staticSignature(frame);
        if(previousFar!=null&&++blendAge>=OpenGLGeometryShadows.FAR_BLEND_FRAMES)previousFar=null;
        farAge++;
        boolean rebuild=next==RendererShadowSettings.Quality.HIGH&&(far==null||farSignature!=signature||farContext!=context||farPlane!=plane||farRoofSignature!=roof.signature||farAge>=150
            ||Math.hypot(x-far.centerX,z-far.centerZ)>=4f*128||lx*far.lightX-ly*far.lightY+lz*far.lightZ<Math.cos(Math.toRadians(.08)));
        if(rebuild) {
            boolean same=far!=null&&farSignature==signature&&farContext==context&&farPlane==plane&&farRoofSignature==roof.signature;
            boolean pose=same&&(x!=far.centerX||y!=far.centerY||z!=far.centerZ||Math.abs(lx-far.lightX)>.000001f||Math.abs(-ly-far.lightY)>.000001f||Math.abs(lz-far.lightZ)>.000001f);
            previousFar=pose?far:null;far=new DirectionalShadowProjection(x,y,z,lx,ly,lz,96f*128,farSize);
            farSignature=signature;farContext=context;farPlane=plane;farRoofSignature=roof.signature;farAge=blendAge=0;
        } else if(next!=RendererShadowSettings.Quality.HIGH){far=previousFar=null;blendAge=0;}
        values[1]=1;values[3]=next.nearSize;values[4]=far==null?0:farSize;values[5]=next.filterRadius;values[6]=rebuild?1:0;values[7]=previousFar==null?0:1;
        float t=Math.min(1f,(float)blendAge/OpenGLGeometryShadows.FAR_BLEND_FRAMES);values[8]=t*t*(3-2*t);
        values[16]=1f/next.nearSize;values[17]=far==null?0:1f/farSize;values[18]=near.worldTexel;values[19]=far==null?0:far.worldTexel;
        System.arraycopy(near.matrix,0,values,32,16);if(far!=null)System.arraycopy(far.matrix,0,values,48,16);if(previousFar!=null)System.arraycopy(previousFar.matrix,0,values,64,16);
        return new Frame(values,changes);
    }
    private static long staticSignature(Renderer3DFrame frame) {
        long hash=1469598103934665603L;
        for(Renderer3DWorldChunkFrame.ChunkMesh chunk:frame.getWorldChunkFrame().getChunks()) {
            if(chunk.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS)continue;
            hash=(hash^chunk.getStorageSignature())*1099511628211L;hash=(hash^chunk.getOriginWorldX())*1099511628211L;
            hash=(hash^chunk.getOriginWorldZ())*1099511628211L;hash=(hash^chunk.getPlane())*1099511628211L;
        }
        for(Renderer3DTextureData texture:frame.getTextures())if(texture!=null&&texture.hasTransparency()&&RendererShadowCasterPolicy.isFarCaster(texture))hash=(hash^texture.getSignature())*1099511628211L;
        return hash;
    }
    private static void putBounds(float[] target,int at,float x,float z,float ix,float iz){target[at]=x;target[at+1]=z;target[at+2]=ix;target[at+3]=iz;}
    private static float[] bounds(float[] values,int at){return new float[]{values[at],values[at+1],values[at+2],values[at+3]};}
}
