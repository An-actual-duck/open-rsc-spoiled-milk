package orsc;
import java.nio.*;import java.util.*;
/** Contract/fault tests for the isolated scheduler, not production readiness proof. */
public final class NativeTransitionPrototypeFixture {
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static final class Memory implements NativeTransitionUploadPrototype.Backend {
        int serial;boolean fail;Map<Integer,byte[]> buffers=new HashMap<Integer,byte[]>();
        public int allocate(int n){buffers.put(++serial,new byte[n]);return serial;}
        public void upload(int h,int offset,ByteBuffer b){if(fail)throw new IllegalStateException("injected upload failure");b.get(buffers.get(h),offset,b.remaining());}
        public void delete(int h){require(buffers.remove(h)!=null,"double delete");}
    }
    static NativeTransitionUploadPrototype.Identity id(long source,int level,long settings,long atlas,long context){return new NativeTransitionUploadPrototype.Identity(source,level,settings,atlas,context);}
    static NativeTransitionUploadPrototype.FrameToken frame(NativeTransitionUploadPrototype.Identity id,long pose,long animation){return new NativeTransitionUploadPrototype.FrameToken(id,pose,animation,pose,new byte[]{(byte)pose,(byte)animation});}
    static NativeTransitionUploadPrototype.Snapshot snapshot(NativeTransitionUploadPrototype.Identity id){return new NativeTransitionUploadPrototype.Snapshot(id,new byte[][]{new byte[112],new byte[12],new byte[12]});}
    public static void main(String[] args)throws Exception {
        Memory backend=new Memory();NativeTransitionUploadPrototype s=new NativeTransitionUploadPrototype(backend,2000,7,128);
        NativeTransitionUploadPrototype.Identity first=id(1,0,1,1,1);NativeTransitionUploadPrototype.FrameToken initial=frame(first,1,1);
        byte[][] owned={new byte[112],new byte[12],new byte[12]};owned[0][0]=42;NativeTransitionUploadPrototype.Snapshot snap=new NativeTransitionUploadPrototype.Snapshot(first,owned);owned[0][0]=99;
        require(s.request(snap,initial,0),"request");int ticks=0;long g=s.generation();
        while(!s.ready()){require(s.tick(g,ticks++)<=7,"slice bound");require(s.live()==null,"partial publication");require(!s.allowsInput(initial),"pending picking");}
        require(!s.publish(g,initial,false,ticks),"unowned frame/atlas/shadow/input");require(s.publish(g,initial,true,ticks),"publish");require(backend.buffers.get(s.live().handles[0])[0]==42,"mutable source leaked");require(s.allowsInput(initial),"live input");
        NativeTransitionUploadPrototype.Published old=s.live();NativeTransitionUploadPrototype.Identity second=id(2,-1,2,2,1);NativeTransitionUploadPrototype.FrameToken latest=frame(second,2,2);s.request(snapshot(second),latest,100);g=s.generation();
        ticks=0;while(!s.ready()){
            latest=frame(second,++ticks,100+ticks);s.updateFrame(latest);require(s.generation()==g,"animation starvation");s.tick(g,100+ticks);
            require(s.live()==old&&backend.buffers.containsKey(old.handles[0]),"old scene destroyed");require(!s.allowsInput(initial),"stale coordinates admitted");
        }
        require(!s.publish(g,frame(second,0,0),true,150),"stale animation published");require(s.publish(g,latest,true,150),"latest complete publication");require(s.live().frame.animation==100+ticks,"animation freshness");require(!s.allowsInput(initial)&&s.allowsInput(latest),"input transform owner");
        for(NativeTransitionUploadPrototype.Identity changed:new NativeTransitionUploadPrototype.Identity[]{id(3,-1,2,2,1),id(2,2,2,2,1),id(2,-1,3,2,1),id(2,-1,2,3,1),id(2,-1,2,2,2)}){
            s.request(snapshot(second),latest,200);g=s.generation();s.tick(g,201);old=s.live();s.updateFrame(frame(changed,0,0));require(!s.ready()&&s.tick(g,202)==0&&s.live()==old,"source/level/settings/atlas/context cancel");
        }
        s.request(snapshot(second),latest,300);g=s.generation();s.tick(g,301);backend.fail=true;try{s.tick(g,302);throw new AssertionError("error not injected");}catch(IllegalStateException expected){}backend.fail=false;require(s.live()!=null&&!s.ready(),"failure damaged live");
        require(!s.request(new NativeTransitionUploadPrototype.Snapshot(second,new byte[][]{new byte[129]}),latest,400),"oversize plane");
        require(!s.request(new NativeTransitionUploadPrototype.Snapshot(second,new byte[][]{new byte[2000]}),latest,400),"capacity overflow");
        s.close();require(backend.buffers.isEmpty(),"resources leaked");System.out.println("PASS isolated scheduler ownership, bounded slices, both indices, immutable source, stale generations, level/settings/atlas/context, animation starvation, input, errors and cleanup; NOT integrated with production presenter");
    }
}
