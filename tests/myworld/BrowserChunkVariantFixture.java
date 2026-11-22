package orsc;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Native atlas sampling and packed-material dependency contracts. No GPU/performance claim. */
public final class BrowserChunkVariantFixture {
    static int checks;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Renderer3DFrame frame(Scene scene){scene.endScene(0);return scene.getRenderer3DFrame();}
    static Renderer3DWorldChunkFrame.ChunkMesh mesh(){return mesh(0,0,31);}
    static Renderer3DWorldChunkFrame.ChunkMesh mesh(int role,int texture,long signature){return new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,new int[]{0,0,0,128,0,0,0,0,128},new float[]{0,.5f,1},new float[]{1,.5f,0},new int[3],new int[]{0,1,2},new int[]{texture},new int[]{0},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},null,null,1,0,0,false,role,signature);}
    private static long mix(long hash, long word) { return (hash ^ word) * 1099511628211L; }
    private static void worldFrameSignatures(List<Renderer3DWorldChunkFrame.ChunkMesh> chunks) {
        int[] start = Renderer3DWorldChunkFrame.getBrowserFrameSignatureWork();
        Renderer3DWorldChunkFrame frame = Renderer3DWorldChunkFrame.fromChunks(chunks);
        boolean deferred = Renderer3DWorldChunkFrame.isBrowserFrameSignatureDeferred();
        if (deferred && !chunks.isEmpty()) {
            int[] built = Renderer3DWorldChunkFrame.getBrowserFrameSignatureWork();
            require(built[0] == start[0] + 1 && built[1] == start[1] && built[2] == start[2],
                "Frame computed a deferred signature while it was constructed");
        }
        int staticCount = 0;
        long staticHash = 1469598103934665603L;
        long textureHash = mix(staticHash, chunks.size());
        for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunks) {
            textureHash = mix(mix(textureHash, chunk.getSignature()), chunk.getTriangleCount());
            if (chunk.getChunkRole() != Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS) {
                for (long word : new long[]{chunk.getPlane(), chunk.getCenterSectionX(), chunk.getCenterSectionY(),
                        chunk.getOriginWorldX(), chunk.getOriginWorldZ(), chunk.getChunkRole(), chunk.getSignature(),
                        chunk.getTriangleCount()}) staticHash = mix(staticHash, word);
                staticCount++;
            }
        }
        staticHash = mix(staticHash, staticCount);
        textureHash = mix(textureHash, frame.getReferencedTextureCount());
        for (int i = 0; i < frame.getReferencedTextureCount(); i++) textureHash = mix(textureHash, frame.getReferencedTextureId(i));
        for (int i = 0; i < 3; i++) {
            require(frame.getStaticPresentationSignature() == staticHash && frame.getStaticPresentationChunkCount() == staticCount,
                "Static signature changed");
            require(frame.getTextureReferenceSignature() == textureHash, "Texture reference signature changed");
        }
        if (deferred) {
            int[] finished = Renderer3DWorldChunkFrame.getBrowserFrameSignatureWork();
            require(finished[1] == start[1] + 1 && finished[2] == start[2] + 1, "Frame did not reuse computed signatures");
        }
    }
    static WorldChunkBuffer nativeBuffer(int[] state){WorldChunkBuffer b=new WorldChunkBuffer(0,0,0);b.signature=31;b.vertexCount=3;b.indexCount=3;b.triangleCount=1;b.brightnessBits=state[0];b.fogModeBits=state[1];b.lightingModeBits=state[2];b.geometryModeBits=state[3];b.replacementCompositeDrawOnly=true;return b;}
    static boolean matches(WorldChunkBuffer b,int[] s){return b.matches(31,3,3,1,false,s[0],s[1],s[2],s[3],true,0);}
    public static void main(String[] args)throws Exception {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Renderer3DWorldChunkFrame.ChunkMesh first = mesh(0, 17, 91);
        Renderer3DWorldChunkFrame.ChunkMesh second = mesh(1, 6, -953);
        Renderer3DWorldChunkFrame.ChunkMesh animated = mesh(2, 17, 114);
        worldFrameSignatures(Collections.emptyList());
        worldFrameSignatures(Arrays.asList(first, animated, second));
        worldFrameSignatures(Arrays.asList(second, animated, first));
        Constructor<OpenGLTextureAtlas> ctor=OpenGLTextureAtlas.class.getDeclaredConstructor(LwjglBindings.class,int.class,int.class,int.class);ctor.setAccessible(true);
        Method center=OpenGLTextureAtlas.class.getDeclaredMethod("texelCenterU",int.class);center.setAccessible(true);
        StringBuilder rows=new StringBuilder();
        for(int size:new int[]{1,2,3,7,64,127,128}) {
            OpenGLTextureAtlas atlas=ctor.newInstance(null,1,size,size);float first=(Float)center.invoke(atlas,0),last=(Float)center.invoke(atlas,size-1);
            OpenGLTextureRegion region=new OpenGLTextureRegion(1,0,0,size,size,first,first,last,last,false);
            for(float source:new float[]{-2,0,.01f,.25f,.5f,.75f,.999f,1,2}) {
                float unit=source<=0?0:source>=1?1:source;
                float expected=region.getU0()+(region.getU1()-region.getU0())*unit;
                require(Float.floatToIntBits(expected)==Float.floatToIntBits(OpenGLWorldChunkRenderer.backendTexelCenter(source,size)),"Standalone UV differs from native texel-center region for size"+size);
            }
            OpenGLTextureAtlas spriteAtlas=ctor.newInstance(null,2,2048,2048);float lo=(Float)center.invoke(spriteAtlas,23),hi=(Float)center.invoke(spriteAtlas,23+size-1);
            for(float unit:new float[]{0,.01f,.25f,.5f,.75f,1}) {
                float expected=lo+(hi-lo)*unit;rows.append(23).append('\t').append(size).append('\t').append(unit).append('\t').append(2048).append('\t').append(expected).append('\n');
                float mirrored=lo+(hi-lo)*(1-unit);rows.append(23).append('\t').append(size).append('\t').append(1-unit).append('\t').append(2048).append('\t').append(mirrored).append('\n');
            }
        }
        if(args.length>0)Files.write(Paths.get(args[0]),rows.toString().getBytes("UTF-8"));
        Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.setFrustum(0,11,7,1);
        byte[] pixels=new byte[4096];for(int n=0;n<pixels.length;n++)pixels[n]=(byte)(n%2+1);
        scene.loadTexture(0,new int[]{0xff00ff,0x111111,0x333333},0,pixels);Renderer3DFrame a=frame(scene);Renderer3DWorldChunkFrame.ChunkMesh mesh=mesh();
        int[] metadata=BrowserChunkVariant.texture(a.getTexture(0)),materials=BrowserChunkVariant.materials(a,mesh);
        for(int n=0;n<pixels.length;n++)pixels[n]=(byte)(2-n%2);
        scene.loadTexture(0,new int[]{0xff00ff,0x111111,0x333333},0,pixels);Renderer3DFrame b=frame(scene);
        require(a.getTexture(0).getSignature()!=b.getTexture(0).getSignature(),"Fixture pixels did not animate");require(BrowserChunkVariant.sameTexture(metadata,b.getTexture(0))&&Arrays.equals(materials,BrowserChunkVariant.materials(b,mesh)),"Pixel-only animation invalidated material state");
        scene.loadTexture(0,new int[]{0xff00ff,0x555555,0x333333},0,pixels);Renderer3DFrame c=frame(scene);require(!BrowserChunkVariant.sameTexture(metadata,c.getTexture(0)),"Average material color override did not invalidate");
        pixels[0]=0;scene.loadTexture(0,new int[]{0xff00ff,0x111111,0x333333},0,pixels);Renderer3DFrame d=frame(scene);require(d.getTexture(0).hasTransparency()&&!Arrays.equals(materials,BrowserChunkVariant.materials(d,mesh)),"Alpha-class change did not invalidate");
        OpenGLWorldChunkRenderer packing=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
        if (args.length > 0) {
            float[] legacyVertices = packing.copyBackendChunkVertices(mesh, b);
            int[] compactVertices = packing.copyBackendCompactChunkVertices(mesh, b);
            require(compactVertices != null && compactVertices.length == mesh.getVertexCount() * 12,
                "Compact fixture mesh could not be represented");
            OpenGLWorldChunkRenderer.PendingBackendVertices slicedCompact = packing.new PendingBackendVertices(mesh, true);
            OpenGLWorldChunkRenderer.PendingBackendVertices slicedLegacy = packing.new PendingBackendVertices(mesh, false);
            require(slicedCompact.advance(b, Long.MAX_VALUE) && Arrays.equals(compactVertices, slicedCompact.compact),
                "Speculative compact packing changed vertex bits");
            require(slicedLegacy.advance(b, Long.MAX_VALUE) && Arrays.equals(legacyVertices, slicedLegacy.legacy),
                "Speculative legacy packing changed vertex bits");
            ByteBuffer legacyBytes = ByteBuffer.allocate(legacyVertices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer compactBytes = ByteBuffer.allocate(compactVertices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            legacyBytes.asFloatBuffer().put(legacyVertices);
            compactBytes.asIntBuffer().put(compactVertices);
            Files.write(Paths.get(args[0] + ".legacy"), legacyBytes.array());
            Files.write(Paths.get(args[0] + ".compact"), compactBytes.array());
        }
        require(BrowserChunkMaterials.pack(packing,d,mesh).batches[4]==1,"Current alpha classification not exported");
        scene.setFrustum(0,11,7,0);Renderer3DFrame absent=frame(scene);require(!Arrays.equals(materials,BrowserChunkVariant.materials(absent,mesh))&&BrowserChunkMaterials.pack(packing,absent,mesh).batches.length==0,"Texture removal did not alter effective material");
        scene.setFrustum(0,11,7,1);byte[] large=new byte[16384];Arrays.fill(large,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x111111},1,large);Renderer3DFrame resized=frame(scene);require(!BrowserChunkVariant.sameTexture(metadata,resized.getTexture(0)),"Texture dimensions did not invalidate UV state");
        float[] vertices=packing.copyBackendChunkVertices(mesh,resized);for(int n=0;n<3;n++){require(vertices[n*28+7]==OpenGLWorldChunkRenderer.backendTexelCenter(mesh.getVertexTextureU(n),128),"Actual world packing lost native-centered U");require(vertices[n*28+8]==OpenGLWorldChunkRenderer.backendTexelCenter(mesh.getVertexTextureV(n),128),"Actual world packing lost native-centered V");}
        RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.HIGH);
        int[] initial=packing.backendPackingSettings();WorldChunkBuffer nativeState=nativeBuffer(initial);require(matches(nativeState,initial),"Native packing fixture does not match");
        RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);require(!matches(nativeState,packing.backendPackingSettings()),"Geometry dependency differs from native cache");RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
        RendererFogSettings.setMode(RendererFogSettings.Mode.OFF);require(!matches(nativeState,packing.backendPackingSettings()),"Fog dependency differs from native cache");RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
        RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.LOW);require(Arrays.equals(initial,packing.backendPackingSettings()),"Unbaked directional brightness caused unnecessary VBO updates");
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.CLASSIC);int[] legacy=packing.backendPackingSettings();require(!matches(nativeState,legacy),"Lighting dependency differs from native cache");RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.HIGH);require(legacy[0]!=packing.backendPackingSettings()[0],"Baked legacy brightness dependency omitted");
        for(Renderer3DFrame f:new Renderer3DFrame[]{a,b,c,d,absent,resized})f.release();System.out.println("PASS "+checks+" native UV and exact material/packing invalidation checks");
    }
}
