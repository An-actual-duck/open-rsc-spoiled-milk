package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Exact packed-material dependencies, independent of animated pixel signatures. */
final class BrowserChunkVariant {
    private BrowserChunkVariant(){}
    static int[] texture(Renderer3DTextureData texture) {
        return texture==null?new int[4]:new int[]{texture.getWidth(),texture.getHeight(),texture.getAverageOpaqueRgb(),texture.hasTransparency()?1:0};
    }
    static boolean sameTexture(int[] state,Renderer3DTextureData texture) {
        return state!=null&&texture!=null&&state[0]==texture.getWidth()&&state[1]==texture.getHeight()&&state[2]==texture.getAverageOpaqueRgb()&&state[3]==(texture.hasTransparency()?1:0);
    }
    static int[] materials(Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        int[] state=new int[chunk.getReferencedTextureCount()*5];
        for(int n=0;n<chunk.getReferencedTextureCount();n++) {
            int id=chunk.getReferencedTextureId(n),at=n*5;Renderer3DTextureData texture=frame.getTexture(id);state[at]=id;
            if(texture!=null){state[at+1]=texture.getWidth();state[at+2]=texture.getHeight();state[at+3]=texture.getAverageOpaqueRgb();state[at+4]=texture.hasTransparency()?1:0;}
        }
        return state;
    }
}
