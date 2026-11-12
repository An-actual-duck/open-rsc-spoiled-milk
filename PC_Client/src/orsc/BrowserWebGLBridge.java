package orsc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.*;
import orsc.graphics.two.GraphicsController;
import orsc.graphics.two.WorldOverlayCoverage;
import orsc.graphics.two.OverlayJournalObserver;

/** Experimental local WebGL2 backend. Native calls synchronously copy/upload borrowed primitive arrays. */
public final class BrowserWebGLBridge implements BrowserFrameSinkRegistry.Sink {
    /** One-time private startup receipt; never probes per-frame cache contents. */
    public static boolean isSpriteKeyLimbsEnabled(){return BrowserWorldSpriteCache.isKeyLimbsEnabled();}
    public static String getOverlayRectMode(){return isOverlayDeltaTransportEnabled()
        ? System.getProperty("spoiledmilk.browserOverlayRectMode","off") : "off";}
    private static final boolean WHOLE_ZONE=WholeOverworldZoneRegistry.isEnabled();
    private BrowserWholeOverworldZone wholeZone;
    private BrowserWholeOverworldRam wholeRam;
    private static final int[] WHOLE_RAM_FAILED_WORK=new int[]{1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private static volatile int[] wholeRamWork=new int[0];
    static void clearWholeRamReady(){wholeRamWork=WHOLE_RAM_FAILED_WORK;WholeOverworldRamRegistry.clear();}
    public static int[] getWholeRamWork(){return wholeRamWork.clone();}
    static void publishWholeRamWork(int[] work){if(work.length!=18||work[0]!=1)throw new IllegalStateException("Whole-RAM work schema");wholeRamWork=work.clone();}
    public static boolean isWholeRamEnabled(){return BrowserWholeOverworldRam.isEnabled();}
    private static volatile int[] wholeZoneWork=new int[0];
    public static int[] getWholeZoneWork(){return wholeZoneWork.clone();}
    public static boolean isStaticSceneReuseEnabled(){return BrowserWholeOverworldZone.isStaticSceneReuseEnabled();}
    private static volatile int[] staticSceneReuseWork=new int[0];
    public static int[] getStaticSceneReuseWork(){return staticSceneReuseWork.clone();}
    private void invalidateWholeZone(){if(wholeRam==null&&BrowserWholeOverworldRam.isEnabled())clearWholeRamReady();if(wholeRam!=null){wholeRam.invalidate();wholeRamWork=wholeRam.diagnostics();}WholeOverworldRamRegistry.clear();if(wholeZone!=null){wholeZone.invalidate();wholeZoneWork=wholeZone.diagnostics();staticSceneReuseWork=wholeZone.staticSceneDiagnostics();}WholeOverworldZoneRegistry.clear();}
    public static boolean isWholeZoneEnabled(){return WHOLE_ZONE;}
    private static native int initializeWholeZone(String manifestIdentity,int[] settings,int[] materials);
    private static native void wholeZoneFrame(int[] metadata,int[] objectCells);
    private static final int MAX_CHUNKS=256, MAX_VERTICES=524288, MAX_PIXELS=2097152, UPLOAD_VERTICES_PER_FRAME=32768;
    private final Map<WorldChunkBufferKey,CachedChunk> chunks=new LinkedHashMap<WorldChunkBufferKey,CachedChunk>(32,.75f,true);
    private final Map<Integer,Long> textures=new HashMap<Integer,Long>();
    private final Map<Integer,int[]> textureMetadata=new HashMap<Integer,int[]>();
    private long materialRevision,packingSequence;
    private static final boolean LOAD_PROFILE=Boolean.getBoolean("spoiledmilk.browserBenchmark")&&Boolean.getBoolean("spoiledmilk.browserLoadProfile");
    private static final java.util.concurrent.atomic.AtomicLong LOAD_OWNERS=new java.util.concurrent.atomic.AtomicLong();
    private final long loadOwner=LOAD_PROFILE?LOAD_OWNERS.incrementAndGet():0;
    private long loadAttempt,lastLoadEpoch=-1;private int lastLoadPath=-1;
    private BrowserLoadProfile loadProfile;
    private static native void loadingWork(double[] values);
    public static boolean isLoadProfileEnabled(){return LOAD_PROFILE;}

    private static final boolean OVERLAY_COVERAGE=Boolean.getBoolean("spoiledmilk.browserOverlayCoverage");
    private static final boolean OVERLAY_DELTA=Boolean.getBoolean("spoiledmilk.browserOverlayDeltaTransport");
    public static boolean isOverlayDeltaTransportEnabled(){return OVERLAY_DELTA&&OVERLAY_COVERAGE;}
    private boolean overlayDeltaReset=true;
    private Object overlayDeltaOwner;

    private Field mainClientField;
    private int[] overlayOwner;private int overlayWidth,overlayHeight;
    private long opaqueSequence;
    private final OpenGLWorldChunkRenderer packing=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
    private final OpenGLPixelWorkspace spriteWorkspace=new OpenGLPixelWorkspace();
    private final BrowserWorldSpriteCache spriteCache=new BrowserWorldSpriteCache();
    private static final boolean SPRITE_CACHE="cached".equals(System.getProperty("spoiledmilk.browserSpriteCache","uncached"));
    private final BrowserLightingData lightingData=new BrowserLightingData();
    private final BrowserSkyData skyData=new BrowserSkyData();
    private final BrowserWorldLoadState loadState=new BrowserWorldLoadState();
    private int maxTextureSize;
    private static final long CHUNK_BUDGET=224L*1024*1024; // reserve32MiB for the bounded world texture catalog
    private static final boolean ACTIVE_ONLY="active".equals(System.getProperty("spoiledmilk.browserCachePolicy","lru"));
    private static final boolean ANIMATED_BUFFER_REUSE=Boolean.parseBoolean(System.getProperty("spoiledmilk.browserAnimatedBufferReuse","true"));
    /** Actual Java-selected private control; never changes desktop renderer policy. */
    public static boolean isAnimatedBufferReuseEnabled(){return ANIMATED_BUFFER_REUSE;}
    static WorldChunkBufferKey chunkKey(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        if(!ANIMATED_BUFFER_REUSE&&chunk.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS) {
            // Native from(chunk,false) still gives animated positional identity.
            // The private control explicitly restores immutable per-pose storage.
            return new WorldChunkBufferKey(chunk.getPlane(),0,0,0,0,chunk.isObjectChunk(),chunk.getChunkRole(),true,chunk.getStorageSignature(),0L);
        }
        return WorldChunkBufferKey.from(chunk,true);
    }
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
    interface ChunkBackend {
        void upload(int id,float[] vertices,int[] indices,int[] batches);
        void replace(int id,float[] vertices,int[] indices,int[] batches);
        void delete(int[] ids);
    }
    private final ChunkBackend chunkBackend;
    public BrowserWebGLBridge() { this(new ChunkBackend() {
        public void upload(int id,float[] vertices,int[] indices,int[] batches){uploadChunk(id,vertices,indices,batches);}
        public void replace(int id,float[] vertices,int[] indices,int[] batches){replaceChunk(id,vertices,indices,batches);}
        public void delete(int[] ids){deleteChunks(ids);}
    }); }
    BrowserWebGLBridge(ChunkBackend backend) { if(backend==null)throw new NullPointerException("chunk backend");chunkBackend=backend; }
    private static native void uploadChunk(int id,float[] vertices,int[] indices,int[] batches);
    private static native void uploadTexture(int id,int width,int height,int[] rgba,boolean transparency);
    private static native void replaceChunk(int id,float[] vertices,int[] indices,int[] batches);
    private static native void deleteTextures(int[] ids);
    private static native void present(int width,int height,boolean world,float[] matrices,float[] uniforms,int[] draws,int[] overlay);
    private static native void frameWork(float[] milliseconds,int[] pose);
    private static native void worldProgress(int[] metadata);
    private static native void regionTransition(boolean ordinaryWalking);
    private static native void holdWorld();
    @Override public void worldTransition(boolean ordinaryWalking) {
        overlayDeltaReset=true;
        loadState.beginTransition();
        if(initialized)regionTransition(ordinaryWalking);
    }
    private static native void overlayFrame(int width,int height,int mode,double generation,double frameSequence,int fades,boolean reset,float[] transmittance);
    private static native void overlayDeltaFrame(int width,int height,double generation,double frameSequence,boolean reset,
        boolean partial,double baseGeneration,double baseFrameSequence,int[] bands,int[] work,int[] rgb,float[] transmittance);
    private static native void overlayDeltaFrameObserved(int width,int height,double generation,double frameSequence,boolean reset,
        boolean partial,double baseGeneration,double baseFrameSequence,int[] bands,int[] work,int[] rgb,float[] transmittance,
        int[] journalHeader,int[] journalOps,int[] journalGlyphs,byte[] journalFonts,int[] journalRuns,int[] journalStats);

    private static final class PreparedOverlay {
        final WorldOverlayCoverage.Frame frame;final WorldOverlayCoverage.Delta delta;
        PreparedOverlay(WorldOverlayCoverage.Frame frame,WorldOverlayCoverage.Delta delta){this.frame=frame;this.delta=delta;}
    }
    private void acknowledgeOverlay(PreparedOverlay prepared){
        if(prepared!=null){prepared.frame.acknowledgeDeltaTransport(prepared.delta);overlayDeltaReset=false;}
    }

    private static native void deleteChunks(int[] ids);
    private static native int[] planWorldSprites(int epoch,double sequence,int[] images);
    private static native void commitWorldSprites(int epoch,double sequence,byte[] rgba,float[] quads);
    private long spriteSequence;
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
        if(!LOAD_PROFILE){if(!WHOLE_ZONE){presentFrame(image,commands,frame);return;}try{presentFrame(image,commands,frame);}catch(RuntimeException|Error failure){invalidateWholeZone();throw failure;}return;}
        BrowserLoadProfile sample=new BrowserLoadProfile(loadOwner,++loadAttempt);loadProfile=sample;
        Throwable failure=null;
        try {presentFrame(image,commands,frame);}
        catch(RuntimeException|Error error){failure=error;if(WHOLE_ZONE)invalidateWholeZone();throw error;}
        finally {loadProfile=null;double[] values=sample.finish(chunks.size(),residentChunkBytes);if(sample.report(lastLoadEpoch,lastLoadPath)){lastLoadEpoch=(long)values[6];lastLoadPath=(int)values[3];try{loadingWork(values);}catch(RuntimeException|Error diagnosticError){if(failure==null)throw diagnosticError;failure.addSuppressed(diagnosticError);}}}
    }
    private void presentFrame(BufferedImage image,Renderer2DFrame commands,Renderer3DFrame frame) {
        long frameStart=System.nanoTime();
        mudclient client=currentClient();
        GraphicsController currentSurface=client==null?null:client.getSurface();
        if(currentSurface!=null&&!currentSurface.getSceneSpriteRasterFailure().isEmpty())throw new IllegalStateException("Browser actor capture rejected: "+currentSurface.getSceneSpriteRasterFailure());
        int w=image.getWidth(),h=image.getHeight();
        if(loadProfile!=null){loadProfile.values[4]=w;loadProfile.values[5]=h;}
        if(w<1||h<1||(long)w*h>MAX_PIXELS)throw new IllegalStateException("Browser viewport exceeds bounded backend capacity");
        if(!initialized) {
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.INITIALIZE);
            try {
                maxTextureSize=initialize(shader("RESIDENT_CHUNK_PARITY_VERTEX_SHADER"),shader("RESIDENT_CHUNK_PARITY_FRAGMENT_SHADER"));
                initializeLighting(BrowserLightingData.depthVertex(),BrowserLightingData.depthFragment());
                initializeSky();
            }
            catch(Exception e) { throw new IllegalStateException("Browser shader initialization failed",e); }
            initialized=true;
        }
        if(loadProfile!=null)loadProfile.enter(-1);
        Renderer3DWorldChunkFrame world=frame==null?null:frame.getWorldChunkFrame();
        if(loadProfile!=null)loadProfile.values[33]=world==null?0:world.getChunkCount();
        boolean hasWorld=world!=null&&world.getChunkCount()>0;
        if(!hasWorld) {
            boolean game=client!=null&&client.isGameView(),modal=client!=null&&client.isFullScreenModalUiActive();
            int[] progress=loadState.frame(game,modal,false,frame,0,0,0,0,0,w,h,textures.size());
            if(game&&!modal&&loadState.shouldHold(false,0,0,0)) {
                overlayDeltaReset=true;if(loadProfile!=null){loadProfile.progress(progress);loadProfile.enter(BrowserLoadProfile.TERMINAL);}worldProgress(progress);holdWorld();if(loadProfile!=null)loadProfile.values[BrowserLoadProfile.PATH]=1;return;
            }
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.LIGHTING);
            exportLighting(null);
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.SKY);
            exportSky(null);
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.OVERLAY);
            PreparedOverlay preparedOverlay=prepareOverlay(image,currentSurface);
            if(loadProfile!=null){loadProfile.progress(progress);loadProfile.enter(BrowserLoadProfile.TERMINAL);}
            worldProgress(progress);
            present(w,h,false,new float[48],new float[30],new int[0],borrowOverlay(image));
            acknowledgeOverlay(preparedOverlay);
            if(loadProfile!=null)loadProfile.values[BrowserLoadProfile.PATH]=4;
            return;
        }
        if(WHOLE_ZONE&&client!=null&&client.isGameView()&&!client.isFullScreenModalUiActive()&&!client.isRenderer3DWorldReady()){
            int[] progress=loadState.frame(client.isGameView(),client.isFullScreenModalUiActive(),false,frame,world.getChunkCount(),0,0,0,0,w,h,textures.size());
            overlayDeltaReset=true;worldProgress(progress);holdWorld();if(loadProfile!=null){loadProfile.progress(progress);loadProfile.values[BrowserLoadProfile.PATH]=2;}return;
        }
        if(world.getChunkCount()>MAX_CHUNKS)throw new IllegalStateException("Browser scene exceeds chunk capacity");
        Renderer3DWorldChunkFrame presentationWorld=world;
        if(WHOLE_ZONE){
            if(client==null||client.getWorld()==null)throw new IllegalStateException("Whole-zone requires actual client source authority");
            if(wholeZone==null)wholeZone=new BrowserWholeOverworldZone();
            wholeZone.frame(client,frame,packing.backendPackingSettings());
            if(!wholeZone.ready())wholeZone.uploaded(initializeWholeZone(wholeZone.manifestHash(),wholeZone.settings(),wholeZone.materials()));
            if(BrowserWholeOverworldRam.isEnabled()){if(wholeRam==null)wholeRam=new BrowserWholeOverworldRam(wholeZone.manifestHash(),packing.backendPackingSettings());wholeRam.validateOptions(packing.backendPackingSettings());wholeRamWork=wholeRam.diagnostics();}
            wholeZoneFrame(wholeZone.metadata(),wholeZone.objectRows());
            presentationWorld=wholeZone.filter(world);wholeZoneWork=wholeZone.diagnostics();staticSceneReuseWork=wholeZone.staticSceneDiagnostics();
        }
        final int catalogCoveredSources=world.getChunkCount()-presentationWorld.getChunkCount();
        Renderer3DFrame previous=RendererFrameLighting.bind(frame);
        try {
            // Evict the old view before uploading the current logical/immutable active set.
            // A count-only cache bound can exceed the byte budget long before 512 chunks.
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.ACTIVE);
            Set<WorldChunkBufferKey> active=activeKeys(presentationWorld);
            evictInactive(active);
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.TEXTURES);
            for(Renderer3DTextureData texture:frame.getTextures()) {
                if(texture==null)continue;
                int id=texture.getTextureId();Long old=textures.get(id);
                if(!BrowserChunkVariant.sameTexture(textureMetadata.get(id),texture)){textureMetadata.put(id,BrowserChunkVariant.texture(texture));materialRevision++;}
                if(old!=null&&old.longValue()==texture.getSignature())continue;
                if(texture.getWidth()<1||texture.getHeight()<1||(long)texture.getWidth()*texture.getHeight()>262144||textures.size()>2048)
                    throw new IllegalStateException("Browser texture capacity exceeded");
                uploadTexture(id,texture.getWidth(),texture.getHeight(),texture.getRgbaPixels(),texture.hasTransparency());
                if(loadProfile!=null){loadProfile.values[27]++;loadProfile.values[28]+=(long)texture.getWidth()*texture.getHeight()*4;}
                textures.put(id,texture.getSignature());
            }
            int[] removedTextures=new int[textureMetadata.size()];int removedCount=0;
            Iterator<Map.Entry<Integer,int[]>> materialIterator=textureMetadata.entrySet().iterator();
            while(materialIterator.hasNext()){int id=materialIterator.next().getKey();if(frame.getTexture(id)==null){removedTextures[removedCount++]=id;materialIterator.remove();textures.remove(id);materialRevision++;}}
            if(removedCount>0)deleteTextures(Arrays.copyOf(removedTextures,removedCount));
            long textureEnd=System.nanoTime();
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.GEOMETRY);
            ChunkDraws geometry=prepareChunks(frame,presentationWorld,active);
            int[] draws=geometry.draws;int count=draws.length,remainingRepacks=geometry.remainingRepacks;
            long geometryEnd=System.nanoTime();
            boolean authority=client!=null&&client.isRenderer3DWorldReady();
            int[] progress=loadState.frame(client!=null&&client.isGameView(),client!=null&&client.isFullScreenModalUiActive(),authority,frame,world.getChunkCount(),count/8+catalogCoveredSources,remainingRepacks,0,0,w,h,textures.size());
            // Core already retained its last coherent CPU frame during area activation.
            // Preserve that same final GPU image until EVERY new source chunk is ready.
            // Uploads are synchronous, so no borrowed frame storage escapes this call.
            if(progress[1]!=BrowserWorldLoadState.UI&&loadState.shouldHold(authority,world.getChunkCount(),count/8+catalogCoveredSources,remainingRepacks)) {
                overlayDeltaReset=true;if(loadProfile!=null){loadProfile.progress(progress);loadProfile.enter(BrowserLoadProfile.TERMINAL);}worldProgress(progress);holdWorld();if(loadProfile!=null)loadProfile.values[BrowserLoadProfile.PATH]=2;return;
            }
            float[] view=OpenGLWorldChunkRenderer.backendWorldViewMatrix(frame);
            float[] clip=OpenGLWorldChunkRenderer.multiply(OpenGLWorldChunkRenderer.projectionMatrix(frame),view);
            float[] matrices=new float[48];columnMajor(clip,matrices,0);columnMajor(view,matrices,16);columnMajor(OpenGLFramePresenter.backendCameraToClipProjectionMatrix(frame),matrices,32);
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.SPRITES);
            int spriteGroups=uploadSprites(frame);
            long spriteEnd=System.nanoTime();
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.LIGHTING);
            exportLighting(frame);
            long lightingEnd=System.nanoTime();
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.SKY);
            exportSky(frame);
            long skyEnd=System.nanoTime();
            if(loadProfile!=null)loadProfile.enter(BrowserLoadProfile.OVERLAY);
            PreparedOverlay preparedOverlay=prepareOverlay(image,currentSurface);
            if(loadProfile!=null){loadProfile.progress(progress);loadProfile.enter(BrowserLoadProfile.TERMINAL);}
            progress[6]=progress[7]=spriteGroups;worldProgress(progress);
            present(w,h,true,matrices,uniforms(frame),draws,borrowOverlay(image));
            acknowledgeOverlay(preparedOverlay);
            loadState.presented(OVERLAY_COVERAGE&&authority,world.getChunkCount(),count/8+catalogCoveredSources,remainingRepacks);
            long end=System.nanoTime();frameWork(new float[]{(end-frameStart)/1000000f,(textureEnd-frameStart)/1000000f,(geometryEnd-textureEnd)/1000000f,(spriteEnd-geometryEnd)/1000000f,(end-skyEnd)/1000000f,chunks.size(),residentChunkBytes,frame.getDepthFrame()==null?0:1,(lightingEnd-spriteEnd)/1000000f,(skyEnd-lightingEnd)/1000000f,SPRITE_CACHE?1:0,spriteCache.getHits(),spriteCache.getMisses(),spriteCache.getCacheBytes()},pose(frame));
            if(loadProfile!=null)loadProfile.values[BrowserLoadProfile.PATH]=3;
        } finally { RendererFrameLighting.restore(previous); }
    }
    static final class ChunkDraws {
        final int[] draws;final int remainingRepacks;
        ChunkDraws(int[] draws,int remainingRepacks){this.draws=draws;this.remainingRepacks=remainingRepacks;}
    }
    // Production native identity: static chunks retain immutable/rebased storage;
    // animated chunks own one positional slot and replace its latest exact pose.
    static Set<WorldChunkBufferKey> activeKeys(Renderer3DWorldChunkFrame world) {
        Set<WorldChunkBufferKey> active=new HashSet<WorldChunkBufferKey>();
        for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
            if(!active.add(chunkKey(chunk))&&chunk.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS)
                throw new IllegalStateException("Conflicting animated browser chunk identity");
        }
        return active;
    }
    void evictInactive(Set<WorldChunkBufferKey> active) {
        if(!ACTIVE_ONLY)return;
        int[] deleted=new int[chunks.size()];int count=0;
        Iterator<Map.Entry<WorldChunkBufferKey,CachedChunk>> iterator=chunks.entrySet().iterator();
        while(iterator.hasNext()){Map.Entry<WorldChunkBufferKey,CachedChunk> entry=iterator.next();if(!active.contains(entry.getKey())){deleted[count++]=entry.getValue().id;residentChunkBytes-=entry.getValue().bytes;iterator.remove();}}
        if(count>0)chunkBackend.delete(Arrays.copyOf(deleted,count));
    }
    ChunkDraws prepareChunks(Renderer3DFrame frame,Renderer3DWorldChunkFrame world,Set<WorldChunkBufferKey> active) {
        int[] packingSettings=packing.backendPackingSettings();
        int[] draws=new int[world.getChunkCount()*8];int count=0,uploaded=0,remainingRepacks=0;
        List<PendingRepack> pending=new ArrayList<PendingRepack>();
        for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
            CachedChunk cached=chunks.get(chunkKey(chunk));if(cached==null)continue;
            int[] materialState=cached.materials;
            if(cached.signature!=chunk.getStorageSignature()||cached.materialRevision!=materialRevision){materialState=BrowserChunkVariant.materials(frame,chunk);if(Arrays.equals(materialState,cached.materials))cached.materialRevision=materialRevision;}
            if(cached.signature!=chunk.getStorageSignature()||cached.vertices!=chunk.getVertexCount()||cached.indices!=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3)*3||!Arrays.equals(cached.settings,packingSettings)||!Arrays.equals(cached.materials,materialState))pending.add(new PendingRepack(cached,chunk,materialState));
        }
        Collections.sort(pending,(a,b)->Long.compare(a.cached.packedAt,b.cached.packedAt));
        for(PendingRepack update:pending) {
            // Settings-only repacks retain the existing bounded budget. A changed
            // animation pose must be uploaded now, never drawn from an older pose.
            boolean poseChanged=update.cached.signature!=update.chunk.getStorageSignature();
            if(!poseChanged&&uploaded>0&&uploaded+update.chunk.getVertexCount()>UPLOAD_VERTICES_PER_FRAME){remainingRepacks++;continue;}
            updateChunk(frame,update.chunk,update.cached,packingSettings,update.materials,active);
            // Mandatory animation uploads are separate from discretionary load/
            // settings work. Otherwise every frame loses the uploaded==0 escape
            // that lets one new oversized chunk make forward progress.
            if(!poseChanged)uploaded+=update.chunk.getVertexCount();
        }
        for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
            WorldChunkBufferKey key=chunkKey(chunk);CachedChunk cached=chunks.get(key);
            if(cached==null && (uploaded==0||uploaded+chunk.getVertexCount()<=UPLOAD_VERTICES_PER_FRAME)) {
                long required=chunkBytes(chunk);
                makeRoom(active,required);
                cached=upload(frame,chunk,required,packingSettings);chunks.put(key,cached);residentChunkBytes+=required;uploaded+=chunk.getVertexCount();
            }
            if(cached==null)continue;
            draws[count++]=cached.id;draws[count++]=chunk.getVertexOffsetX()-cached.x;draws[count++]=chunk.getVertexOffsetZ()-cached.z;
            draws[count++]=chunk.getLogicalWorldOffsetX();draws[count++]=chunk.getLogicalWorldOffsetZ();
            int visibility=0;for(Renderer3DModelKind kind:Renderer3DModelKind.values())if(frame.isWorldChunkModelKindVisible(kind,chunk.getPlane()))visibility|=1<<kind.ordinal();
            draws[count++]=visibility;draws[count++]=chunk.getPlane();draws[count++]=chunk.getChunkRole();
        }
        return new ChunkDraws(Arrays.copyOf(draws,count),remainingRepacks);
    }
    private CachedChunk updateChunk(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk,CachedChunk cached,int[] settings,int[] materials,Set<WorldChunkBufferKey> active) {
        int packedIndices=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3)*3;
        if(cached.vertices!=chunk.getVertexCount()||cached.indices!=packedIndices) {
            long required=chunkBytes(chunk);makeRoom(active,required);
            CachedChunk replacement=upload(frame,chunk,required,settings);
            // Allocate+validate first. A failed native upload leaves the old cache
            // entry and accounting intact; it can never masquerade as this pose.
            chunks.put(chunkKey(chunk),replacement);
            residentChunkBytes+=required-cached.bytes;
            chunkBackend.delete(new int[]{cached.id});
            return replacement;
        }
        BrowserChunkMaterials.Buffers buffers=packMaterials(frame,chunk);
        submitChunk(true,cached.id,chunk,packVertices(frame,chunk),buffers.indices,buffers.batches);
        cached.x=chunk.getVertexOffsetX();cached.z=chunk.getVertexOffsetZ();cached.settings=settings;cached.materials=materials;
        cached.signature=chunk.getStorageSignature();cached.materialRevision=materialRevision;cached.packedAt=++packingSequence;
        return cached;
    }
    private static long chunkBytes(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        if(chunk.getVertexCount()>MAX_VERTICES||chunk.getIndexCount()>MAX_VERTICES*3)throw new IllegalStateException("Browser chunk exceeds capacity");
        return (long)chunk.getVertexCount()*28*4+(long)Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3)*3*4;
    }
    private mudclient currentClient() {
        try {
            if(mainClientField==null){mainClientField=ORSCApplet.class.getDeclaredField("mudclient");mainClientField.setAccessible(true);}
            return (mudclient)mainClientField.get(null);
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Browser cannot inspect its current surface",e);}
    }
    private PreparedOverlay prepareOverlay(BufferedImage image,GraphicsController surface) {
        if(!OVERLAY_COVERAGE)return null;
        PreparedOverlay prepared=null;
        if(GraphicsController.BROWSER_OVERLAY_JOURNAL_OBSERVE&&surface!=null)surface.observeOverlayJournalBarrier(OverlayJournalObserver.AWT);
        int[] pixels=borrowOverlay(image);int width=image.getWidth(),height=image.getHeight();
        boolean reset=pixels!=overlayOwner||width!=overlayWidth||height!=overlayHeight;
        WorldOverlayCoverage.Frame coverage=surface==null?null:surface.borrowWorldOverlayCoverage();
        if(OVERLAY_DELTA){
            Object owner=coverage==null?null:coverage.deltaTransportOwner();
            if(owner!=overlayDeltaOwner)reset=true;
            overlayDeltaOwner=owner;
        }
        if(coverage!=null) {
            if(coverage.transparent&&(RendererDebugSettings.isOverlayEnabled()||WorldEditorBuildSettings.isEnabled()))surface.invalidateWorldOverlayCoverage("Raw debug/editor framebuffer writer is unsupported by browser coverage");
            if(!coverage.valid||!surface.isWorldOverlayCoverageCurrent(coverage))throw new IllegalStateException("Browser overlay rejected: "+coverage.reason);
            if(coverage.transparent&&(coverage.ownerPixels!=pixels||coverage.width!=width||coverage.height!=height))throw new IllegalStateException("Browser overlay pixel owner/dimensions changed");
            if(coverage.generation<0||coverage.generation>9007199254740991L||coverage.frameSequence<0||coverage.frameSequence>9007199254740991L)throw new IllegalStateException("Browser overlay token exceeds exact JavaScript integer range");
            OverlayJournalObserver.Snapshot journal=GraphicsController.BROWSER_OVERLAY_JOURNAL_OBSERVE&&surface!=null?surface.finishOverlayJournal(coverage):null;
            WorldOverlayCoverage.Delta delta=OVERLAY_DELTA?coverage.prepareDeltaTransport(reset||overlayDeltaReset):null;
            if(delta!=null){
                if(journal!=null)overlayDeltaFrameObserved(width,height,coverage.generation,coverage.frameSequence,reset,delta.partial,
                    delta.baseGeneration,delta.baseFrameSequence,delta.bands(),delta.work(),pixels,coverage.transmittance,
                    journal.header,journal.ops,journal.glyphs,journal.fonts,journal.runs,journal.stats);
                else {
                overlayDeltaFrame(width,height,coverage.generation,coverage.frameSequence,reset,delta.partial,
                    delta.baseGeneration,delta.baseFrameSequence,delta.bands(),delta.work(),pixels,coverage.transmittance);
                }
                prepared=new PreparedOverlay(coverage,delta);
            }else{
                if(OVERLAY_DELTA)overlayDeltaReset=true;
                overlayFrame(width,height,coverage.mode,coverage.generation,coverage.frameSequence,coverage.retainedFadeCount,reset,coverage.transmittance);
            }
        } else {
            if(OVERLAY_DELTA)overlayDeltaReset=true;
            overlayFrame(width,height,WorldOverlayCoverage.MODE_OPAQUE,0,++opaqueSequence,0,reset,null);
        }
        overlayOwner=pixels;overlayWidth=width;overlayHeight=height;
        return prepared;
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
    private int uploadSprites(Renderer3DFrame frame) {
        if(frame.getWorldSpriteSnapshotCount()>1024)throw new IllegalStateException("Browser world sprite count exceeds capacity");
        BrowserWorldSpriteBatch batch=new BrowserWorldSpriteBatch();
        // Strong references protect the entire gathered frame from CPU LRU evictions.
        for(Renderer3DFrame.WorldSpriteSnapshot snapshot:frame.getWorldSpriteSnapshots()) {
            if(snapshot==null||snapshot.getAnchor()==null||snapshot.getLayerCount()==0)continue;
            BrowserWorldSpriteCache.Result cached=null;
            if(SPRITE_CACHE)try{cached=spriteCache.compose(snapshot,spriteWorkspace);}catch(IllegalArgumentException unsupported){/* Use the same production builder below. */}
            CompositeWorldSpriteTexture composite;
            if(cached!=null)composite=new CompositeWorldSpriteTexture(cached.x,cached.y,cached.width,cached.height,null);
            else{
                composite=OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(snapshot,spriteWorkspace);if(composite==null)continue;
                ByteBuffer pixels=composite.textureData.pixels();byte[] rgba=new byte[pixels.remaining()];pixels.get(rgba);
                cached=new BrowserWorldSpriteCache.Result(composite.x,composite.y,composite.width,composite.height,rgba,spriteCache.nextImageId());
            }
            if(composite.width<1||composite.height<1||(long)composite.width*composite.height>MAX_PIXELS)throw new IllegalStateException("Browser world sprite exceeds capacity");
            batch.add(cached,spriteQuad(frame,snapshot.getAnchor(),composite));
        }
        if(spriteSequence>=9007199254740991L)throw new IllegalStateException("Sprite sequence exhausted");
        long sequence=++spriteSequence;int epoch=spriteCache.getEpoch();
        int[] missing=planWorldSprites(epoch,sequence,batch.descriptors());
        commitWorldSprites(epoch,sequence,batch.pixels(missing),batch.vertices());
        return batch.size();
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

    private void makeRoom(Set<WorldChunkBufferKey> active,long required) {
        if(required>CHUNK_BUDGET)throw new IllegalStateException("Browser chunk exceeds resident byte budget");
        int[] deleted=new int[chunks.size()];int count=0;
        Iterator<Map.Entry<WorldChunkBufferKey,CachedChunk>> iterator=chunks.entrySet().iterator();
        while((residentChunkBytes+required>CHUNK_BUDGET||chunks.size()>=MAX_CHUNKS*2)&&iterator.hasNext()) {
            Map.Entry<WorldChunkBufferKey,CachedChunk> entry=iterator.next();if(active.contains(entry.getKey()))continue;
            deleted[count++]=entry.getValue().id;residentChunkBytes-=entry.getValue().bytes;iterator.remove();
        }
        if(count>0)chunkBackend.delete(Arrays.copyOf(deleted,count));
        if(residentChunkBytes+required>CHUNK_BUDGET||chunks.size()>=MAX_CHUNKS*2)throw new IllegalStateException("Active browser scene exceeds resident byte budget");
    }
    private CachedChunk upload(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk,long bytes,int[] settings) {
        BrowserChunkMaterials.Buffers materials=packMaterials(frame,chunk);
        int id=nextId++;submitChunk(false,id,chunk,packVertices(frame,chunk),materials.indices,materials.batches);
        return new CachedChunk(id,chunk,bytes,settings,BrowserChunkVariant.materials(frame,chunk),materialRevision,++packingSequence);
    }
    private BrowserChunkMaterials.Buffers packMaterials(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk){
        if(loadProfile==null)return BrowserChunkMaterials.pack(packing,frame,chunk);
        long start=System.nanoTime();try{return BrowserChunkMaterials.pack(packing,frame,chunk);}finally{loadProfile.elapsed(BrowserLoadProfile.MATERIALS,start);loadProfile.values[32]++;}
    }
    private float[] packVertices(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk){
        if(loadProfile==null)return packing.copyBackendChunkVertices(chunk,frame);
        long start=System.nanoTime();try{return packing.copyBackendChunkVertices(chunk,frame);}finally{loadProfile.elapsed(BrowserLoadProfile.VERTICES,start);loadProfile.values[32]++;}
    }
    private void submitChunk(boolean replacement,int id,Renderer3DWorldChunkFrame.ChunkMesh chunk,float[] vertices,int[] indices,int[] batches){
        long start=loadProfile==null?0:System.nanoTime();
        try{if(replacement)chunkBackend.replace(id,vertices,indices,batches);else chunkBackend.upload(id,vertices,indices,batches);
            if(loadProfile!=null)loadProfile.submitted(replacement,chunk.getVertexCount(),(long)vertices.length*4+(long)indices.length*4+(long)batches.length*4);
        }finally{if(loadProfile!=null)loadProfile.elapsed(BrowserLoadProfile.SUBMIT,start);}
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
        final int id,vertices,indices;int x,z;final long bytes;int[] settings,materials;long signature,materialRevision,packedAt;
        CachedChunk(int id,Renderer3DWorldChunkFrame.ChunkMesh chunk,long bytes,int[] settings,int[] materials,long materialRevision,long packedAt){this.id=id;this.x=chunk.getVertexOffsetX();this.z=chunk.getVertexOffsetZ();this.vertices=chunk.getVertexCount();this.indices=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3)*3;this.signature=chunk.getStorageSignature();this.bytes=bytes;this.settings=settings;this.materials=materials;this.materialRevision=materialRevision;this.packedAt=packedAt;}
    }
}
