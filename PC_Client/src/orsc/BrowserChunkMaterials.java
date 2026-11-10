package orsc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Stable two-pass material grouping without boxing every triangle index. */
final class BrowserChunkMaterials {
    static final class Buffers {
        final int[] indices, batches;
        Buffers(int[] indices, int[] batches) { this.indices=indices;this.batches=batches; }
    }
    static Buffers pack(OpenGLWorldChunkRenderer renderer, Renderer3DFrame frame,
                        Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        int triangles=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3);
        int[] triangleGroups=new int[triangles];
        Map<Long,Integer> groups=new LinkedHashMap<Long,Integer>();
        List<int[]> materials=new ArrayList<int[]>();
        for(int triangle=0;triangle<triangles;triangle++) {
            int sourceTexture=chunk.getTriangleTexture(triangle),fallback=chunk.getTriangleFallbackColor(triangle);
            if(!renderer.backendMaterialDrawable(frame,sourceTexture,fallback)) {
                triangleGroups[triangle]=-1;
                continue;
            }
            int textureId=renderer.backendTextureId(frame,sourceTexture,fallback);
            Renderer3DTextureData texture=frame.getTexture(textureId);
            if(texture==null)textureId=-1;
            int kind=chunk.getTriangleModelKind(triangle).ordinal();
            int transparent=texture!=null&&texture.hasTransparency()?1:0;
            long key=((long)textureId+1L)*16L+kind*2+transparent;
            Integer group=groups.get(key);
            if(group==null) {
                group=materials.size();groups.put(key,group);
                materials.add(new int[]{textureId,kind,transparent,0});
            }
            triangleGroups[triangle]=group;materials.get(group)[3]+=3;
        }
        // Keep the original allocation capacity for resident in-place updates.
        // Only batch ranges are submitted; unused trailing indices never draw.
        int[] indices=new int[triangles*3],batches=new int[materials.size()*5],cursor=new int[materials.size()];
        int start=0;
        for(int group=0;group<materials.size();group++) {
            int[] material=materials.get(group);int at=group*5;
            batches[at]=material[0];batches[at+1]=start;batches[at+2]=material[3];
            batches[at+3]=material[1];batches[at+4]=material[2];cursor[group]=start;start+=material[3];
        }
        for(int triangle=0;triangle<triangles;triangle++) {
            int group=triangleGroups[triangle];
            if(group<0)continue;
            int at=cursor[group];
            for(int corner=0;corner<3;corner++)indices[at+corner]=chunk.getIndex(triangle*3+corner);
            cursor[group]+=3;
        }
        return new Buffers(indices,batches);
    }
}
