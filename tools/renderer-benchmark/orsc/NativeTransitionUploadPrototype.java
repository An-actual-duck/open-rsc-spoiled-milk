package orsc;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Laboratory-only native transition experiment. NOT connected to the presenter.
 * Uses owned immutable planes, bounded work and separate pending/live resources.
 * Full frame/atlas/input ownership still has to be supplied by a real presenter;
 * tokens here are an executable contract, not a claim of production integration. */
final class NativeTransitionUploadPrototype implements AutoCloseable {
    interface Backend {
        int allocate(int bytes) throws Exception;
        void upload(int handle,int offset,ByteBuffer bytes) throws Exception;
        void delete(int handle) throws Exception;
    }
    static final class Identity {
        final long source,settings,atlas,context; final int level;
        Identity(long source,int level,long settings,long atlas,long context){this.source=source;this.level=level;this.settings=settings;this.atlas=atlas;this.context=context;}
        boolean same(Identity o){return o!=null&&source==o.source&&level==o.level&&settings==o.settings&&atlas==o.atlas&&context==o.context;}
    }
    static final class FrameToken {
        final Identity identity; final long pose,animation,picking; final byte[] camera,material,shadow,image;
        FrameToken(Identity id,long pose,long animation,long picking,byte[] bytes){identity=id;this.pose=pose;this.animation=animation;this.picking=picking;camera=bytes.clone();material=bytes.clone();shadow=bytes.clone();image=bytes.clone();}
    }
    static final class Snapshot {
        final Identity identity; final byte[][] planes; final long bytes;
        Snapshot(Identity id,byte[][] input){identity=id;planes=new byte[input.length][];long n=0;for(int i=0;i<input.length;i++){planes[i]=input[i].clone();n=Math.addExact(n,input[i].length);}bytes=n;}
    }
    static final class Published {
        final Identity identity; final FrameToken frame; final int[] handles; final long bytes;
        Published(Identity id,FrameToken frame,int[] handles,long bytes){identity=id;this.frame=frame;this.handles=handles.clone();this.bytes=bytes;}
    }
    private final Backend backend; private final long cap; private final int sliceBytes,maxPlaneBytes;
    private Snapshot pending; private FrameToken latest; private int[] handles; private int plane,offset;
    private Published live; private long generation,pendingStart,liveSince;
    long uploadedBytes,allocations,cancellations,publications,maxTickBytes;
    NativeTransitionUploadPrototype(Backend backend,long cap,int sliceBytes,int maxPlaneBytes){
        if(cap<1||sliceBytes<1||maxPlaneBytes<1)throw new IllegalArgumentException("bounds");
        this.backend=backend;this.cap=cap;this.sliceBytes=sliceBytes;this.maxPlaneBytes=maxPlaneBytes;
    }
    /** Inputs have already been copied. Accounting includes pending CPU and GPU planes. */
    boolean request(Snapshot snapshot,FrameToken frame,long now) throws Exception {
        if(!snapshot.identity.same(frame.identity))throw new IllegalArgumentException("frame/source mismatch");
        if(pending!=null&&pending.identity.same(snapshot.identity)){latest=frame;return true;}
        cancel();
        long required=Math.addExact(live==null?0:live.bytes,Math.multiplyExact(snapshot.bytes,2));
        if(required>cap)return false;
        for(byte[] p:snapshot.planes)if(p.length>maxPlaneBytes)return false;
        pending=snapshot;latest=frame;handles=new int[snapshot.planes.length];plane=offset=0;pendingStart=now;generation++;return true;
    }
    void updateFrame(FrameToken frame)throws Exception {
        if(pending==null)return;
        if(!pending.identity.same(frame.identity)){cancel();return;}
        latest=frame; // Same-source pose/animation changes must not restart static work.
    }
    /** Returns bytes submitted, with a soft 3ms limit, 64KiB writes and at most32 bounded allocations per tick. */
    int tick(long expectedGeneration,long now) throws Exception {
        if(pending==null||generation!=expectedGeneration)return 0;
        int budget=sliceBytes,submitted=0,allocated=0;long start=System.nanoTime();
        try {
            while(plane<handles.length&&budget>0&&System.nanoTime()-start<3_000_000L){
                if(handles[plane]==0){if(allocated>=32)break;handles[plane]=backend.allocate(pending.planes[plane].length);allocations++;allocated++;}
                byte[] source=pending.planes[plane];int n=Math.min(65536,Math.min(budget,source.length-offset));
                if(n>0){backend.upload(handles[plane],offset,ByteBuffer.wrap(source,offset,n).slice().asReadOnlyBuffer());offset+=n;budget-=n;submitted+=n;uploadedBytes+=n;}
                if(offset==source.length){plane++;offset=0;}
            }
            maxTickBytes=Math.max(maxTickBytes,submitted);return submitted;
        }catch(Exception failure){cancel();throw failure;}
    }
    boolean ready(){return pending!=null&&plane==handles.length;}
    /** Owner may publish only a fully refreshed current frame, never the old target animation. */
    boolean publish(long expectedGeneration,FrameToken refreshed,boolean ownsFrameAtlasShadowAndInput,long now)throws Exception {
        if(!ready()||generation!=expectedGeneration||!ownsFrameAtlasShadowAndInput||refreshed!=latest)return false;
        if(!pending.identity.same(refreshed.identity))return false;
        Published old=live;Published next=new Published(pending.identity,refreshed,handles,pending.bytes);
        live=next;pending=null;handles=null;latest=null;publications++;liveSince=now;
        if(old!=null)for(int h:old.handles)backend.delete(h);
        return true;
    }
    boolean allowsInput(FrameToken frame){return pending==null&&live!=null&&live.frame==frame;}
    long generation(){return generation;}
    long holdAge(long now){return pending==null?0:Math.max(0,now-pendingStart);}
    long oldImageAge(long now){return live==null?0:Math.max(0,now-liveSince);}
    Published live(){return live;}
    void cancel()throws Exception {if(pending==null)return;int[] doomed=handles;pending=null;handles=null;latest=null;generation++;cancellations++;Exception error=null;for(int h:doomed)if(h!=0)try{backend.delete(h);}catch(Exception e){error=e;}if(error!=null)throw error;}
    public void close()throws Exception{cancel();if(live!=null){Published old=live;live=null;for(int h:old.handles)backend.delete(h);}}
}
