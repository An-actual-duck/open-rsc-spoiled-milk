package orsc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.lang.reflect.Field;
import java.util.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.*;

/** Experimental local WebGL2 backend. Native calls synchronously copy/upload borrowed primitive arrays. */
public final class BrowserWebGLBridge implements BrowserFrameSinkRegistry.Sink {
    private static final int MAX_CHUNKS=256, MAX_VERTICES=524288, MAX_PIXELS=2097152, UPLOAD_VERTICES_PER_FRAME=32768;
    private final Map<Long,CachedChunk> chunks=new LinkedHashMap<Long,CachedChunk>();
    private final Map<Integer,Long> textures=new HashMap<Integer,Long>();
    private final OpenGLWorldChunkRenderer packing=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
    private int nextId=1;
    private boolean initialized;
    private static native void initialize(String vertex, String fragment);
    private static native void uploadChunk(int id,float[] vertices,int[] indices,int[] batches);
    private static native void uploadTexture(int id,int width,int height,int[] rgba);
    private static native void present(int width,int height,boolean world,float[] matrices,float[] uniforms,int[] draws,int[] overlay);
    private static native void reset();
    public static void install() { BrowserFrameSinkRegistry.install(new BrowserWebGLBridge()); }
    private static String shader(String name) throws Exception {
        Field f=OpenGLShaderProgram.class.getDeclaredField(name);f.setAccessible(true);return (String)f.get(null);
    }
    @Override public void present(BufferedImage image,Renderer2DFrame commands,Renderer3DFrame frame) {
        int w=image.getWidth(),h=image.getHeight();
        if(w<1||h<1||(long)w*h>MAX_PIXELS)throw new IllegalStateException("Browser viewport exceeds bounded backend capacity");
        if(!initialized) {
            try { initialize(shader("RESIDENT_CHUNK_PARITY_VERTEX_SHADER"),shader("RESIDENT_CHUNK_PARITY_FRAGMENT_SHADER")); }
            catch(Exception e) { throw new IllegalStateException("Browser shader initialization failed",e); }
            initialized=true;
        }
        Renderer3DWorldChunkFrame world=frame==null?null:frame.getWorldChunkFrame();
        boolean hasWorld=world!=null&&world.getChunkCount()>0;
        if(!hasWorld) {
            present(w,h,false,new float[32],new float[30],new int[0],borrowOverlay(image));
            return;
        }
        if(world.getChunkCount()>MAX_CHUNKS)throw new IllegalStateException("Browser scene exceeds chunk capacity");
        Renderer3DFrame previous=RendererFrameLighting.bind(frame);
        try {
            if(chunks.size()+world.getChunkCount()>MAX_CHUNKS*2){reset();chunks.clear();textures.clear();}
            for(Renderer3DTextureData texture:frame.getTextures()) {
                if(texture==null)continue;
                int id=texture.getTextureId();Long old=textures.get(id);
                if(old!=null&&old.longValue()==texture.getSignature())continue;
                if(texture.getWidth()<1||texture.getHeight()<1||(long)texture.getWidth()*texture.getHeight()>262144||textures.size()>2048)
                    throw new IllegalStateException("Browser texture capacity exceeded");
                uploadTexture(id,texture.getWidth(),texture.getHeight(),texture.getRgbaPixels());
                textures.put(id,texture.getSignature());
            }
            int[] draws=new int[world.getChunkCount()*6];int count=0,uploaded=0;
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
                long signature=chunk.getStorageSignature();CachedChunk cached=chunks.get(signature);
                if(cached==null && (uploaded==0||uploaded+chunk.getVertexCount()<=UPLOAD_VERTICES_PER_FRAME)) {
                    if(chunk.getVertexCount()>MAX_VERTICES||chunk.getIndexCount()>MAX_VERTICES*3)throw new IllegalStateException("Browser chunk exceeds capacity");
                    cached=upload(frame,chunk);chunks.put(signature,cached);uploaded+=chunk.getVertexCount();
                }
                if(cached==null)continue;
                draws[count++]=cached.id;draws[count++]=chunk.getVertexOffsetX()-cached.x;draws[count++]=chunk.getVertexOffsetZ()-cached.z;
                draws[count++]=chunk.getLogicalWorldOffsetX();draws[count++]=chunk.getLogicalWorldOffsetZ();draws[count++]=chunk.getPlane();
            }
            float[] view=OpenGLWorldChunkRenderer.worldViewMatrix(frame);
            float[] clip=OpenGLWorldChunkRenderer.multiply(OpenGLWorldChunkRenderer.projectionMatrix(frame),view);
            float[] matrices=new float[32];columnMajor(clip,matrices,0);columnMajor(view,matrices,16);
            present(w,h,true,matrices,uniforms(frame),Arrays.copyOf(draws,count),borrowOverlay(image));
        } finally { RendererFrameLighting.restore(previous); }
    }
    private CachedChunk upload(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        Map<String,List<Integer>> indices=new LinkedHashMap<String,List<Integer>>();
        Map<String,int[]> materials=new LinkedHashMap<String,int[]>();
        int triangles=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3);
        for(int triangle=0;triangle<triangles;triangle++) {
            int tex=packing.backendTextureId(frame,chunk.getTriangleTexture(triangle),chunk.getTriangleFallbackColor(triangle));
            Renderer3DTextureData texture=frame.getTexture(tex);if(texture==null)tex=-1;
            int kind=chunk.getTriangleModelKind(triangle).ordinal(),transparent=texture!=null&&texture.hasTransparency()?1:0;
            String key=tex+":"+kind+":"+transparent;
            List<Integer> list=indices.get(key);if(list==null){list=new ArrayList<Integer>();indices.put(key,list);materials.put(key,new int[]{tex,kind,transparent});}
            for(int j=0;j<3;j++)list.add(chunk.getIndex(triangle*3+j));
        }
        int[] index=new int[triangles*3],batch=new int[indices.size()*5];int at=0,b=0;
        for(Map.Entry<String,List<Integer>> group:indices.entrySet()) {
            int[] material=materials.get(group.getKey());batch[b++]=material[0];batch[b++]=at;batch[b++]=group.getValue().size();batch[b++]=material[1];batch[b++]=material[2];
            for(Integer value:group.getValue())index[at++]=value;
        }
        int id=nextId++;uploadChunk(id,packing.copyBackendChunkVertices(chunk,frame),index,batch);
        return new CachedChunk(id,chunk.getVertexOffsetX(),chunk.getVertexOffsetZ());
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
    private static final class CachedChunk {final int id,x,z;CachedChunk(int id,int x,int z){this.id=id;this.x=x;this.z=z;}}
}
