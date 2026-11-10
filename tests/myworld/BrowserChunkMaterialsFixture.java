package orsc;

import java.util.*;
import java.lang.reflect.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.DoorDef;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Native missing-material parity plus stable two-pass grouping. No GPU/performance claim. */
public final class BrowserChunkMaterialsFixture {
    static BrowserChunkMaterials.Buffers reference(OpenGLWorldChunkRenderer packing,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        Map<String,List<Integer>> indices=new LinkedHashMap<String,List<Integer>>();
        Map<String,int[]> materials=new LinkedHashMap<String,int[]>();
        int triangles=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3);
        for(int triangle=0;triangle<triangles;triangle++) {
            if(!nativeDrawable(packing,frame,chunk.getTriangleTexture(triangle),chunk.getTriangleFallbackColor(triangle)))continue;
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
        return new BrowserChunkMaterials.Buffers(index,batch);
    }
    static boolean nativeDrawable(OpenGLWorldChunkRenderer renderer,Renderer3DFrame frame,int texture,int fallback) {
        // Invoke the actual native draw decision independently of the browser predicate.
        // The fixture has no GPU atlas: real nonempty textures take its flat fallback.
        try {
            WorldChunkMaterialBatch batch=new WorldChunkMaterialBatch(Renderer3DModelKind.WALL,texture,fallback,0,3,0,0,0,1,0,1,0,1);
            Method method=OpenGLWorldChunkRenderer.class.getDeclaredMethod("chunkBatchDrawState",Renderer3DFrame.class,WorldChunkMaterialBatch.class,boolean.class);
            method.setAccessible(true);Object state=method.invoke(renderer,frame,batch,true);
            Field result=state.getClass().getDeclaredField("bindResult");result.setAccessible(true);
            return !"SKIPPED".equals(result.get(state).toString());
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static Method method(Class<?> type,String name,Class<?>...types)throws Exception{Method m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    static int invisibleWallProducers(OpenGLWorldChunkRenderer renderer,Renderer3DFrame frame)throws Exception {
        EntityHandler.load(true);
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        World world=(World)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,World.class);
        Sector[] sectors=new Sector[16];for(int n=0;n<sectors.length;n++)sectors[n]=Sector.blankLoaded();
        Class<?> sourceType=Class.forName("orsc.graphics.three.World$TerrainModelInputSource"),segmentType=Class.forName("orsc.graphics.three.World$WallSegmentInput"),inputType=Class.forName("orsc.graphics.three.World$WallModelInput"),builderType=Class.forName("orsc.graphics.three.World$WorldGpuChunkMeshBuilder");
        Constructor<?> sourceCtor=sourceType.getDeclaredConstructor(Sector[].class);sourceCtor.setAccessible(true);Object source=sourceCtor.newInstance((Object)sectors);
        Constructor<?> inputCtor=inputType.getDeclaredConstructor(Array.newInstance(segmentType,0).getClass());inputCtor.setAccessible(true);
        Constructor<?> builderCtor=builderType.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,boolean.class);builderCtor.setAccessible(true);
        Method add=method(World.class,"addWallSegmentInput",List.class,sourceType,int[][].class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class);
        int checks=0;
        for(int id:new int[]{16,17})for(int direction=0;direction<4;direction++) {
            DoorDef def=EntityHandler.getDoorDef(id);
            if(!def.getName().endsWith("blank")||def.getDoorType()!=0||def.getModelVar2()!=12345678||def.getModelVar3()!=12345678)throw new AssertionError("Authentic passable invisible wall definition changed");
            List<Object> segments=new ArrayList<Object>();int[][] endpoints={{2,2,3,2},{2,2,2,3},{3,2,2,3},{2,2,3,3}};int[] e=endpoints[direction];
            add.invoke(world,segments,source,new int[200][200],id+1,direction,e[0],e[1],e[2],e[1],e[0],e[3]);
            Object array=Array.newInstance(segmentType,segments.size());for(int n=0;n<segments.size();n++)Array.set(array,n,segments.get(n));
            Object builder=builderCtor.newInstance(0,0,0,0,0,false);
            method(World.class,"addWallGpuChunkMesh",builderType,inputType,int.class,int.class).invoke(null,builder,inputCtor.newInstance(array),0,0);
            Object product=method(builderType,"build").invoke(builder);
            Renderer3DWorldChunkFrame.ChunkMesh mesh=(Renderer3DWorldChunkFrame.ChunkMesh)method(product.getClass(),"toRenderer3DWorldChunkMesh").invoke(product);
            int[] before=new int[mesh.getIndexCount()];for(int n=0;n<before.length;n++)before[n]=mesh.getIndex(n);
            BrowserChunkMaterials.Buffers packed=BrowserChunkMaterials.pack(renderer,frame,mesh);
            if(mesh.getTriangleCount()!=2||packed.batches.length!=0||packed.indices.length!=6)throw new AssertionError("Invisible wall emitted a color/shadow draw or changed resident capacity");
            for(int n=0;n<before.length;n++)if(before[n]!=mesh.getIndex(n))throw new AssertionError("World source index mutated");
            checks+=3;
        }
        return checks;
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Scene scene=new Scene(new MudClientGraphics(64,64,2),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);
        scene.setFrustum(0,11,7,2);byte[] pixels=new byte[4096];Arrays.fill(pixels,(byte)1);
        scene.loadTexture(0,new int[]{0xff00ff,0x777777},0,pixels);
        for(int n=0;n<pixels.length;n+=2)pixels[n]=0;
        scene.loadTexture(1,new int[]{0xff00ff,0x337755},0,pixels);
        scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
        if(frame.getTexture(0)==null||frame.getTexture(1)==null||frame.getTexture(0).hasTransparency()||!frame.getTexture(1).hasTransparency())throw new AssertionError("Real texture fixture did not capture opaque and cutout materials");
        OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
        Random random=new Random(386511);
        int checks=invisibleWallProducers(renderer,frame);
        for(int trial=0;trial<300;trial++) {
            int triangles=trial<2?trial:random.nextInt(300),vertices=15;
            int[] coordinates=new int[vertices*3],indices=new int[triangles*3],textures=new int[triangles],fallbacks=new int[triangles];
            Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];
            for(int n=0;n<coordinates.length;n++)coordinates[n]=random.nextInt(256);
            for(int n=0;n<indices.length;n++)indices[n]=random.nextInt(vertices);
            for(int n=0;n<triangles;n++){textures[n]=new int[]{-2,-1,0,1,7,Integer.MAX_VALUE,12345678}[random.nextInt(7)];fallbacks[n]=new int[]{0,1,7,12345678,-12345,0x224466}[random.nextInt(6)];kinds[n]=Renderer3DModelKind.values()[random.nextInt(Renderer3DModelKind.values().length)];}
            Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,coordinates,new float[vertices],new float[vertices],new int[vertices],indices,textures,fallbacks,kinds,null,null,0,0,0,true,1,trial+1);
            BrowserChunkMaterials.Buffers before=reference(renderer,frame,chunk),after=BrowserChunkMaterials.pack(renderer,frame,chunk);
            if(!Arrays.equals(before.indices,after.indices)||!Arrays.equals(before.batches,after.batches))throw new AssertionError("Material/index stream differs at trial "+trial);
            for(int at=0;at<after.batches.length;at+=5){int begin=after.batches[at+1],count=after.batches[at+2];if(begin<0||count<0||begin+count>after.indices.length)throw new AssertionError("Invalid range");checks++;}
            checks+=2;
        }
        frame.release();System.out.println("PASS "+checks+" native material draw parity/range checks across300 deterministic chunks and authentic blank/highblank wall producers in4 directions");
    }
}
