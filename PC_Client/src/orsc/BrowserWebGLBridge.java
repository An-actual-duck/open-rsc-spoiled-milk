package orsc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.*;

/** Experimental local WebGL2 backend. Native calls synchronously copy/upload borrowed primitive arrays. */
public final class BrowserWebGLBridge implements BrowserFrameSinkRegistry.Sink {
    private static final int MAX_CHUNKS=256, MAX_VERTICES=524288, MAX_PIXELS=2097152, UPLOAD_VERTICES_PER_FRAME=32768;
    private final Map<Long,CachedChunk> chunks=new LinkedHashMap<Long,CachedChunk>(32,.75f,true);
    private final Map<Integer,Long> textures=new HashMap<Integer,Long>();
    private final Map<Integer,int[]> textureMetadata=new HashMap<Integer,int[]>();
    private long materialRevision,packingSequence;
    private final OpenGLWorldChunkRenderer packing=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
    private final OpenGLPixelWorkspace spriteWorkspace=new OpenGLPixelWorkspace();
    private final BrowserWorldSpriteCache spriteCache=new BrowserWorldSpriteCache();
    private static final boolean SPRITE_CACHE="cached".equals(System.getProperty("spoiledmilk.browserSpriteCache","uncached"));
    private final BrowserLightingData lightingData=new BrowserLightingData();
    private final BrowserSkyData skyData=new BrowserSkyData();
    private int maxTextureSize;
    private static final long CHUNK_BUDGET=224L*1024*1024; // reserve32MiB for the bounded world texture catalog
    private static final boolean ACTIVE_ONLY="active".equals(System.getProperty("spoiledmilk.browserCachePolicy","lru"));
    private long residentChunkBytes;
    private int nextId=1;
    private boolean initialized;
    private static native int initialize(String vertex, String fragment);
    private static native void initializeLighting(String vertex,String fragment);
    private static native void uploadLightingMask(int unit,int width,int height,byte[] rgba,float[] bounds);
    private static native void lightingFrame(float[] values);
    private static native void initializeSky();
    private static native void uploadSkyGeometry(float[] geometry,int[] ranges,int paletteCount);
    private static native void skyFrame(float[] values,float[] palette);
    private static native void uploadChunk(int id,float[] vertices,int[] indices,int[] batches);
    private static native void uploadTexture(int id,int width,int height,int[] rgba,boolean transparency);
    private static native void replaceChunk(int id,float[] vertices,int[] indices,int[] batches);
    private static native void deleteTextures(int[] ids);
    private static native void present(int width,int height,boolean world,float[] matrices,float[] uniforms,int[] draws,int[] overlay);
    private static native void frameWork(float[] milliseconds,int[] pose);
    private static native void deleteChunks(int[] ids);
    private static native void beginWorldSprites();
    private static native void uploadWorldSprite(int width,int height,float[] vertices,byte[] rgba);
    public static void install() {
        if(Boolean.getBoolean("spoiledmilk.browserBenchmark")) {
            System.setProperty("spoiledmilk.remasterLightAzimuth","135");System.setProperty("spoiledmilk.remasterLightElevation","45");
            RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.HIGH);
        }
        BrowserFrameSinkRegistry.install(new BrowserWebGLBridge());
    }
    private static String shader(String name) throws Exception {
        Field f=OpenGLShaderProgram.class.getDeclaredField(name);f.setAccessible(true);return (String)f.get(null);
    }
    @Override public boolean needsCpuDepthFrame() {
        return !"gpu".equals(System.getProperty("spoiledmilk.browserDepthSource","cpu"));
    }
    @Override public void present(BufferedImage image,Renderer2DFrame commands,Renderer3DFrame frame) {
        long frameStart=System.nanoTime();
        int w=image.getWidth(),h=image.getHeight();
        if(w<1||h<1||(long)w*h>MAX_PIXELS)throw new IllegalStateException("Browser viewport exceeds bounded backend capacity");
        if(!initialized) {
            try {
                maxTextureSize=initialize(shader("RESIDENT_CHUNK_PARITY_VERTEX_SHADER"),shader("RESIDENT_CHUNK_PARITY_FRAGMENT_SHADER"));
                initializeLighting(BrowserLightingData.depthVertex(),BrowserLightingData.depthFragment());
                initializeSky();
            }
            catch(Exception e) { throw new IllegalStateException("Browser shader initialization failed",e); }
            initialized=true;
        }
        Renderer3DWorldChunkFrame world=frame==null?null:frame.getWorldChunkFrame();
        boolean hasWorld=world!=null&&world.getChunkCount()>0;
        if(!hasWorld) {
            exportLighting(null);
            exportSky(null);
            present(w,h,false,new float[48],new float[30],new int[0],borrowOverlay(image));
            return;
        }
        if(world.getChunkCount()>MAX_CHUNKS)throw new IllegalStateException("Browser scene exceeds chunk capacity");
        Renderer3DFrame previous=RendererFrameLighting.bind(frame);
        try {
            // Evict the old view before uploading a new immutable active set.
            // A count-only cache bound can exceed the byte budget long before 512 chunks.
            Set<Long> active=new HashSet<Long>();
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks())active.add(chunk.getStorageSignature());
            int[] deleted=new int[chunks.size()];int deletedCount=0;
            Iterator<Map.Entry<Long,CachedChunk>> iterator=chunks.entrySet().iterator();
            while(iterator.hasNext()){Map.Entry<Long,CachedChunk> entry=iterator.next();if(!active.contains(entry.getKey())){if(ACTIVE_ONLY){deleted[deletedCount++]=entry.getValue().id;residentChunkBytes-=entry.getValue().bytes;iterator.remove();}}}
            if(deletedCount>0)deleteChunks(Arrays.copyOf(deleted,deletedCount));
            for(Renderer3DTextureData texture:frame.getTextures()) {
                if(texture==null)continue;
                int id=texture.getTextureId();Long old=textures.get(id);
                if(!BrowserChunkVariant.sameTexture(textureMetadata.get(id),texture)){textureMetadata.put(id,BrowserChunkVariant.texture(texture));materialRevision++;}
                if(old!=null&&old.longValue()==texture.getSignature())continue;
                if(texture.getWidth()<1||texture.getHeight()<1||(long)texture.getWidth()*texture.getHeight()>262144||textures.size()>2048)
                    throw new IllegalStateException("Browser texture capacity exceeded");
                uploadTexture(id,texture.getWidth(),texture.getHeight(),texture.getRgbaPixels(),texture.hasTransparency());
                textures.put(id,texture.getSignature());
            }
            int[] removedTextures=new int[textureMetadata.size()];int removedCount=0;
            Iterator<Map.Entry<Integer,int[]>> materialIterator=textureMetadata.entrySet().iterator();
            while(materialIterator.hasNext()){int id=materialIterator.next().getKey();if(frame.getTexture(id)==null){removedTextures[removedCount++]=id;materialIterator.remove();textures.remove(id);materialRevision++;}}
            if(removedCount>0)deleteTextures(Arrays.copyOf(removedTextures,removedCount));
            long textureEnd=System.nanoTime();
            int[] packingSettings=packing.backendPackingSettings();
            int[] draws=new int[world.getChunkCount()*8];int count=0,uploaded=0;
            List<PendingRepack> pending=new ArrayList<PendingRepack>();
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
                CachedChunk cached=chunks.get(chunk.getStorageSignature());if(cached==null)continue;
                int[] materialState=cached.materials;
                if(cached.materialRevision!=materialRevision){materialState=BrowserChunkVariant.materials(frame,chunk);if(Arrays.equals(materialState,cached.materials))cached.materialRevision=materialRevision;}
                if(!Arrays.equals(cached.settings,packingSettings)||!Arrays.equals(cached.materials,materialState))pending.add(new PendingRepack(cached,chunk,materialState));
            }
            Collections.sort(pending,(a,b)->Long.compare(a.cached.packedAt,b.cached.packedAt));
            for(PendingRepack update:pending) {
                if(uploaded>0&&uploaded+update.chunk.getVertexCount()>UPLOAD_VERTICES_PER_FRAME)continue;
                BrowserChunkMaterials.Buffers buffers=BrowserChunkMaterials.pack(packing,frame,update.chunk);
                replaceChunk(update.cached.id,packing.copyBackendChunkVertices(update.chunk,frame),buffers.indices,buffers.batches);
                update.cached.x=update.chunk.getVertexOffsetX();update.cached.z=update.chunk.getVertexOffsetZ();update.cached.settings=packingSettings;update.cached.materials=update.materials;
                update.cached.materialRevision=materialRevision;update.cached.packedAt=++packingSequence;uploaded+=update.chunk.getVertexCount();
            }
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
                long signature=chunk.getStorageSignature();CachedChunk cached=chunks.get(signature);
                if(cached==null && (uploaded==0||uploaded+chunk.getVertexCount()<=UPLOAD_VERTICES_PER_FRAME)) {
                    if(chunk.getVertexCount()>MAX_VERTICES||chunk.getIndexCount()>MAX_VERTICES*3)throw new IllegalStateException("Browser chunk exceeds capacity");
                    long required=(long)chunk.getVertexCount()*28*4+(long)Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3)*3*4;
                    makeRoom(active,required);
                    cached=upload(frame,chunk,required,packingSettings);chunks.put(signature,cached);residentChunkBytes+=required;uploaded+=chunk.getVertexCount();
                }
                if(cached==null)continue;
                draws[count++]=cached.id;draws[count++]=chunk.getVertexOffsetX()-cached.x;draws[count++]=chunk.getVertexOffsetZ()-cached.z;
                draws[count++]=chunk.getLogicalWorldOffsetX();draws[count++]=chunk.getLogicalWorldOffsetZ();
                int visibility=0;for(Renderer3DModelKind kind:Renderer3DModelKind.values())if(frame.isWorldChunkModelKindVisible(kind,chunk.getPlane()))visibility|=1<<kind.ordinal();
                draws[count++]=visibility;draws[count++]=chunk.getPlane();draws[count++]=chunk.getChunkRole();
            }
            long geometryEnd=System.nanoTime();
            float[] view=OpenGLWorldChunkRenderer.backendWorldViewMatrix(frame);
            float[] clip=OpenGLWorldChunkRenderer.multiply(OpenGLWorldChunkRenderer.projectionMatrix(frame),view);
            float[] matrices=new float[48];columnMajor(clip,matrices,0);columnMajor(view,matrices,16);columnMajor(OpenGLFramePresenter.backendCameraToClipProjectionMatrix(frame),matrices,32);
            uploadSprites(frame);
            long spriteEnd=System.nanoTime();
            exportLighting(frame);
            long lightingEnd=System.nanoTime();
            exportSky(frame);
            long skyEnd=System.nanoTime();
            present(w,h,true,matrices,uniforms(frame),Arrays.copyOf(draws,count),borrowOverlay(image));
            long end=System.nanoTime();frameWork(new float[]{(end-frameStart)/1000000f,(textureEnd-frameStart)/1000000f,(geometryEnd-textureEnd)/1000000f,(spriteEnd-geometryEnd)/1000000f,(end-skyEnd)/1000000f,chunks.size(),residentChunkBytes,frame.getDepthFrame()==null?0:1,(lightingEnd-spriteEnd)/1000000f,(skyEnd-lightingEnd)/1000000f,SPRITE_CACHE?1:0,spriteCache.getHits(),spriteCache.getMisses(),spriteCache.getCacheBytes()},pose(frame));
        } finally { RendererFrameLighting.restore(previous); }
    }
    static int[] pose(Renderer3DFrame frame) {
        return new int[]{frame.getCameraOffsetX(),frame.getCameraOffsetY(),frame.getCameraOffsetZ(),frame.getCameraRotationX(),frame.getCameraRotationY(),frame.getCameraRotationZ(),frame.getCenterX(),frame.getCenterY(),frame.getViewportWidth(),frame.getViewportHeight(),frame.getPerspectiveShift(),frame.getFogStartDistance(),frame.getFogDistance(),frame.getSignedWorldLevel()};
    }
    private void exportSky(Renderer3DFrame frame) {
        BrowserSkyData.Frame sky=skyData.prepare(frame);
        if(sky.geometry!=null)uploadSkyGeometry(sky.geometry,sky.ranges,(int)sky.values[3]);
        skyFrame(sky.values,sky.palette);
    }
    private void exportLighting(Renderer3DFrame frame) {
        BrowserLightingData.Frame light=lightingData.prepare(frame,maxTextureSize);
        for(BrowserLightingData.Mask mask:light.changes)uploadLightingMask(mask.unit,mask.width,mask.height,mask.rgba,mask.bounds);
        lightingFrame(light.values);
    }
    private void uploadSprites(Renderer3DFrame frame) {
        beginWorldSprites();
        if(frame.getWorldSpriteSnapshotCount()>1024)throw new IllegalStateException("Browser world sprite count exceeds capacity");
        for(Renderer3DFrame.WorldSpriteSnapshot snapshot:frame.getWorldSpriteSnapshots()) {
            if(snapshot==null||snapshot.getAnchor()==null||snapshot.getLayerCount()==0)continue;
            BrowserWorldSpriteCache.Result cached=null;
            if(SPRITE_CACHE)try{cached=spriteCache.compose(snapshot,spriteWorkspace);}catch(IllegalArgumentException unsupported){/* Retain production builder for unsupported optional-cache layouts. */}
            CompositeWorldSpriteTexture composite;
            byte[] rgba;
            if(cached!=null){composite=new CompositeWorldSpriteTexture(cached.x,cached.y,cached.width,cached.height,null);rgba=cached.rgba;}
            else{composite=OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(snapshot,spriteWorkspace);if(composite==null)continue;ByteBuffer pixels=composite.textureData.pixels();rgba=new byte[pixels.remaining()];pixels.get(rgba);}
            if(composite.width<1||composite.height<1||(long)composite.width*composite.height>MAX_PIXELS)throw new IllegalStateException("Browser world sprite exceeds capacity");
            uploadWorldSprite(composite.width,composite.height,spriteQuad(frame,snapshot.getAnchor(),composite),rgba);
        }
    }
    static float[] spriteQuad(Renderer3DFrame frame,Renderer3DFrame.SpriteAnchor anchor,CompositeWorldSpriteTexture composite) {
        float scale=(float)(1<<Math.max(0,Math.min(24,frame.getPerspectiveShift())));
        float topDepth=OpenGLWorldSpriteRenderer.estimateEntitySpriteDepth(anchor,composite.y),bottomDepth=OpenGLWorldSpriteRenderer.estimateEntitySpriteDepth(anchor,composite.y+composite.height);
        float[] result=new float[20];int at=0;
        for(int n=0;n<4;n++) {
            float x=composite.x+(n==1||n==2?composite.width:0),y=composite.y+(n>=2?composite.height:0),z=n>=2?bottomDepth:topDepth;
            result[at++]=(x-frame.getCenterX())*z/scale;result[at++]=(y-frame.getCenterY())*z/scale;result[at++]=z;
            result[at++]=n==1||n==2?1:0;result[at++]=n>=2?1:0;
        }
        return result;
    }

    private void makeRoom(Set<Long> active,long required) {
        if(required>CHUNK_BUDGET)throw new IllegalStateException("Browser chunk exceeds resident byte budget");
        int[] deleted=new int[chunks.size()];int count=0;
        Iterator<Map.Entry<Long,CachedChunk>> iterator=chunks.entrySet().iterator();
        while((residentChunkBytes+required>CHUNK_BUDGET||chunks.size()>=MAX_CHUNKS*2)&&iterator.hasNext()) {
            Map.Entry<Long,CachedChunk> entry=iterator.next();if(active.contains(entry.getKey()))continue;
            deleted[count++]=entry.getValue().id;residentChunkBytes-=entry.getValue().bytes;iterator.remove();
        }
        if(count>0)deleteChunks(Arrays.copyOf(deleted,count));
        if(residentChunkBytes+required>CHUNK_BUDGET||chunks.size()>=MAX_CHUNKS*2)throw new IllegalStateException("Active browser scene exceeds resident byte budget");
    }
    private CachedChunk upload(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk,long bytes,int[] settings) {
        BrowserChunkMaterials.Buffers materials=BrowserChunkMaterials.pack(packing,frame,chunk);
        int id=nextId++;uploadChunk(id,packing.copyBackendChunkVertices(chunk,frame),materials.indices,materials.batches);
        return new CachedChunk(id,chunk.getVertexOffsetX(),chunk.getVertexOffsetZ(),bytes,settings,BrowserChunkVariant.materials(frame,chunk),materialRevision,++packingSequence);
    }
    static int[] borrowOverlay(BufferedImage image) {
        if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt)
            || !(image.getSampleModel() instanceof SinglePixelPackedSampleModel)
            || ((SinglePixelPackedSampleModel)image.getSampleModel()).getScanlineStride()!=image.getWidth()
            || image.getRaster().getDataBuffer().getOffset()!=0
            || image.getRaster().getSampleModelTranslateX()!=0 || image.getRaster().getSampleModelTranslateY()!=0) {
            throw new IllegalArgumentException("Browser presentation requires a packed direct RGB framebuffer");
        }
        int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        int count=Math.multiplyExact(image.getWidth(),image.getHeight());
        if(pixels.length<count)throw new IllegalArgumentException("Browser framebuffer is undersized");
        return pixels.length==count?pixels:Arrays.copyOf(pixels,count);
    }

    private static void columnMajor(float[] source,float[] target,int offset) {
        for(int col=0;col<4;col++)for(int row=0;row<4;row++)target[offset+col*4+row]=source[row*4+col];
    }
    private static float[] uniforms(Renderer3DFrame f) {
        RendererDayNightCycle.Presentation p=RendererDayNightCycle.currentPresentation();
        return new float[]{RendererDayNightCycle.currentBrightnessMultiplier()*RendererColorDiagnosticSettings.getDimnessMultiplier(),
            RendererColorDiagnosticSettings.getContrastMultiplier(),RendererColorDiagnosticSettings.getGammaValue(),RendererColorDiagnosticSettings.getSaturationMultiplier(),
            p.redMultiplier,p.greenMultiplier,p.blueMultiplier,p.toneBlend,
            RendererRemasterLightSettings.getLightDirectionX(),RendererRemasterLightSettings.getLightDirectionY(),RendererRemasterLightSettings.getLightDirectionZ(),RendererRemasterLightSettings.getAmbient(),RendererRemasterLightSettings.getIntensity(),
            f.getFogStartDistance(),f.getFogDistance(),p.fogRed,p.fogGreen,p.fogBlue,
            RendererReliefSettings.getTerrainStrength(),RendererReliefSettings.getObjectStrength(),RendererTerrainVariationSettings.getStrength(),RendererTerrainVariationSettings.getTolerance(),RendererTerrainVariationSettings.getTargetRed(),RendererTerrainVariationSettings.getTargetGreen(),RendererTerrainVariationSettings.getTargetBlue(),
            RendererTerrainDefinitionSettings.getMode().shaderId,RendererTerrainVariationSettings.isEnabled()?1:0,RendererLightingSettings.getMode()==RendererLightingSettings.Mode.DIRECTIONAL?1:0,RendererFogSettings.getMode()!=RendererFogSettings.Mode.OFF?1:0,f.getSignedWorldLevel()};
    }
    private static final class PendingRepack {
        final CachedChunk cached;final Renderer3DWorldChunkFrame.ChunkMesh chunk;final int[] materials;
        PendingRepack(CachedChunk cached,Renderer3DWorldChunkFrame.ChunkMesh chunk,int[] materials){this.cached=cached;this.chunk=chunk;this.materials=materials;}
    }
    private static final class CachedChunk {
        final int id;int x,z;final long bytes;int[] settings,materials;long materialRevision,packedAt;
        CachedChunk(int id,int x,int z,long bytes,int[] settings,int[] materials,long materialRevision,long packedAt){this.id=id;this.x=x;this.z=z;this.bytes=bytes;this.settings=settings;this.materials=materials;this.materialRevision=materialRevision;this.packedAt=packedAt;}
    }
}
