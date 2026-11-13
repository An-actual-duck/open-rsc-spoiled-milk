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
    // At most 24 KiB per resident entry (512 entries): bounded exact input proof.
    // No mutable ChunkMesh arrays are retained and no hash is accepted as proof.
    static int[] topologyProof(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        int count=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3);
        if(count>1024)return null;
        int[] proof=new int[count*6];
        for(int t=0;t<count;t++) {
            int at=t*6;
            for(int c=0;c<3;c++)proof[at+c]=chunk.getIndex(t*3+c);
            proof[at+3]=chunk.getTriangleTexture(t);proof[at+4]=chunk.getTriangleFallbackColor(t);
            proof[at+5]=chunk.getTriangleModelKind(t).ordinal();
        }
        return proof;
    }
    static boolean topologyMatches(int[] proof,Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        int count=Math.min(chunk.getTriangleCount(),chunk.getIndexCount()/3);
        if(proof==null||proof.length!=count*6)return false;
        for(int t=0;t<count;t++) {
            int at=t*6;
            for(int c=0;c<3;c++)if(proof[at+c]!=chunk.getIndex(t*3+c))return false;
            if(proof[at+3]!=chunk.getTriangleTexture(t)||proof[at+4]!=chunk.getTriangleFallbackColor(t)
                ||proof[at+5]!=chunk.getTriangleModelKind(t).ordinal())return false;
        }
        return true;
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
