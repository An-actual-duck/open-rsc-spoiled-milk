package orsc;

import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Exact stream equivalence to the former boxed-index encoder. No GPU/performance claim. */
public final class BrowserChunkMaterialsFixture {
    static BrowserChunkMaterials.Buffers reference(OpenGLWorldChunkRenderer packing,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk) {
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
        return new BrowserChunkMaterials.Buffers(index,batch);
    }
    public static void main(String[] args) {
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
        int checks=0;
        for(int trial=0;trial<300;trial++) {
            int triangles=trial<2?trial:random.nextInt(300),vertices=15;
            int[] coordinates=new int[vertices*3],indices=new int[triangles*3],textures=new int[triangles],fallbacks=new int[triangles];
            Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];
            for(int n=0;n<coordinates.length;n++)coordinates[n]=random.nextInt(256);
            for(int n=0;n<indices.length;n++)indices[n]=random.nextInt(vertices);
            for(int n=0;n<triangles;n++){textures[n]=new int[]{-2,-1,0,1,7,Integer.MAX_VALUE}[random.nextInt(6)];fallbacks[n]=random.nextInt(8);kinds[n]=Renderer3DModelKind.values()[random.nextInt(Renderer3DModelKind.values().length)];}
            Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,coordinates,new float[vertices],new float[vertices],new int[vertices],indices,textures,fallbacks,kinds,null,null,0,0,0,true,1,trial+1);
            BrowserChunkMaterials.Buffers before=reference(renderer,frame,chunk),after=BrowserChunkMaterials.pack(renderer,frame,chunk);
            if(!Arrays.equals(before.indices,after.indices)||!Arrays.equals(before.batches,after.batches))throw new AssertionError("Material/index stream differs at trial "+trial);
            for(int at=0;at<after.batches.length;at+=5){int begin=after.batches[at+1],count=after.batches[at+2];if(begin<0||count<0||begin+count>after.indices.length)throw new AssertionError("Invalid range");checks++;}
            checks+=2;
        }
        frame.release();System.out.println("PASS "+checks+" exact old/new material stream and range checks across300 deterministic real chunk cases");
    }
}
