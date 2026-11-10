package orsc;

import java.util.Arrays;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Browser-only source readiness. No GPU/frame ownership or gameplay authority is fabricated. */
final class BrowserWorldLoadState {
    static final int LOGIN=0, LOADING=1, WORLD=2, UI=3;
    private int epoch,sourceEpoch;
    private int[] region;
    private boolean coherentWorld, transitionPending;
    int sourceEpoch(){return sourceEpoch;}int geometryEpoch(){return epoch;}
    boolean isCoherent(){return coherentWorld;}boolean isTransitionPending(){return transitionPending;}
    void beginDemandTransition(){if(epoch==Integer.MAX_VALUE)throw new IllegalStateException("Browser load epoch exhausted");epoch++;transitionPending=true;}
    void beginTransition() { transitionPending=true; }
    boolean shouldHold(boolean authority,int required,int ready,int repacks) {
        return coherentWorld && (!authority || required!=ready || transitionPending&&repacks!=0);
    }
    void presented(boolean authority,int required,int ready,int repacks) {
        if(authority&&required>0&&required==ready&&repacks==0){coherentWorld=true;transitionPending=false;}
    }
    int[] frame(boolean game,boolean modal,boolean authority,Renderer3DFrame frame,
                int required,int ready,int repacks,int sprites,int uploaded,int width,int height,int textures) {
        if(!game){region=null;coherentWorld=false;transitionPending=false;}
        if(game&&frame!=null&&frame.getWorldChunkFrame()!=null) {
            Renderer3DWorldChunkFrame.ChunkMesh anchor=null;
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:frame.getWorldChunkFrame().getChunks()) {
                if(anchor==null)anchor=chunk;
                if(chunk.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD){anchor=chunk;break;}
            }
            if(anchor!=null){
                int[] next={anchor.getCenterSectionX(),anchor.getCenterSectionY(),anchor.getLogicalWorldOffsetX(),anchor.getLogicalWorldOffsetZ(),frame.getSignedWorldLevel(),frame.getActivePlane()};
                if(!Arrays.equals(region,next)){if(epoch==Integer.MAX_VALUE)throw new IllegalStateException("Browser load epoch exhausted");epoch++;sourceEpoch++;region=next;transitionPending=true;}
            }
        }
        int mode=!game?LOGIN:modal?UI:authority&&required>0?WORLD:LOADING;
        return new int[]{1,mode,epoch,required,ready,repacks,sprites,uploaded,authority?1:0,width,height,textures};
    }
}
