package orsc;

import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Actual bridge packing/cache and source publication decisions; only native GL is recorded. */
public final class BrowserRegionStreamingFixture {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final class Upload {final float[] v;final int[] i,b;Upload(float[]v,int[]i,int[]b){this.v=v.clone();this.i=i.clone();this.b=b.clone();}}
    static final class Backend implements BrowserWebGLBridge.ChunkBackend {
        final Map<Integer,Upload> live=new HashMap<Integer,Upload>();int uploads;
        public void upload(int id,float[]v,int[]i,int[]b){check(!live.containsKey(id),"reused live ID");live.put(id,new Upload(v,i,b));uploads++;}
        public void replace(int id,float[]v,int[]i,int[]b){check(live.containsKey(id),"replaced absent ID");live.put(id,new Upload(v,i,b));}
        public void delete(int[]ids){for(int id:ids)check(live.remove(id)!=null,"deleted absent ID");}
    }
    static Renderer3DWorldChunkFrame.ChunkMesh mesh(int id){
        int triangles=4000,vertices=triangles*3;int[]xyz=new int[vertices*3],ix=new int[vertices],light=new int[vertices],texture=new int[triangles],color=new int[triangles];float[]uv=new float[vertices];Renderer3DModelKind[]kind=new Renderer3DModelKind[triangles];
        for(int t=0;t<triangles;t++){int p=t*9;xyz[p]=t*2;xyz[p+3]=t*2+128;xyz[p+4]=-32;xyz[p+6]=t*2;xyz[p+8]=128;ix[t*3]=t*3;ix[t*3+1]=t*3+1;ix[t*3+2]=t*3+2;texture[t]=-1;color[t]=0x336699+id;kind[t]=Renderer3DModelKind.GAME_OBJECT;}
        return new Renderer3DWorldChunkFrame.ChunkMesh(0,id,id,0,0,xyz,uv,uv,light,ix,texture,color,kind,null,0,0,0,true,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,1000+id);
    }
    static int load(BrowserWebGLBridge bridge,Backend backend,BrowserWorldLoadState state,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh[] meshes,boolean initial){
        Renderer3DWorldChunkFrame world=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(meshes));frame.setWorldChunkFrame(world);int passes=0,held=0;
        while(true){
            check(++passes<=meshes.length,"upload starvation");Set<WorldChunkBufferKey>active=BrowserWebGLBridge.activeKeys(world);bridge.evictInactive(active);
            Renderer3DFrame previous=RendererFrameLighting.bind(frame);BrowserWebGLBridge.ChunkDraws result;
            try{result=bridge.prepareChunks(frame,world,active);}finally{RendererFrameLighting.restore(previous);}
            int ready=result.draws.length/8;state.frame(true,false,true,frame,meshes.length,ready,result.remainingRepacks,0,0,64,64,0);
            if(ready<meshes.length){check(initial||state.shouldHold(true,meshes.length,ready,result.remainingRepacks),"partial region was presented over previous coherent image");held++;continue;}
            check(!state.shouldHold(true,meshes.length,ready,result.remainingRepacks),"complete region never published");
            OpenGLWorldChunkRenderer nativeRenderer=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);previous=RendererFrameLighting.bind(frame);
            try{for(int n=0;n<meshes.length;n++){Upload actual=backend.live.get(result.draws[n*8]);BrowserChunkMaterials.Buffers expected=BrowserChunkMaterials.pack(nativeRenderer,frame,meshes[n]);check(Arrays.equals(actual.v,nativeRenderer.copyBackendChunkVertices(meshes[n],frame)),"cached source vertex bytes differ");check(Arrays.equals(actual.i,expected.indices)&&Arrays.equals(actual.b,expected.batches),"cached material/index bytes differ");}}
            finally{RendererFrameLighting.restore(previous);}
            state.presented(true,meshes.length,ready,result.remainingRepacks);return held;
        }
    }
    public static void main(String[]args){System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
        Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();Backend backend=new Backend();BrowserWebGLBridge bridge=new BrowserWebGLBridge(backend);BrowserWorldLoadState state=new BrowserWorldLoadState();
        Renderer3DWorldChunkFrame.ChunkMesh[]all=new Renderer3DWorldChunkFrame.ChunkMesh[10];for(int n=0;n<all.length;n++)all[n]=mesh(n);Renderer3DWorldChunkFrame.ChunkMesh[]first=Arrays.copyOfRange(all,0,7),next=Arrays.copyOfRange(all,3,10);
        long start=System.nanoTime();int initialHeld=load(bridge,backend,state,frame,first,true);check(initialHeld==3,"bounded initial geometry schedule changed");check(backend.uploads==7,"initial uploads mismatch");
        state.beginTransition();int streamedHeld=load(bridge,backend,state,frame,next,false);check(streamedHeld==1,"overlap reuse did not bound crossing preparation");check(backend.uploads==10,"overlap was unnecessarily reuploaded");
        state.beginTransition();check(state.shouldHold(false,0,0,0),"Java loading overlay overwrote coherent world");int before=backend.uploads;int returnedHeld=load(bridge,backend,state,frame,first,false);check(returnedHeld==0&&backend.uploads==before,"LRU return crossing discarded immutable resources");
        state.frame(false,false,false,null,0,0,0,0,0,64,64,0);check(!state.shouldHold(false,0,0,0),"login retained previous session");frame.release();
        System.out.println("PASS "+checks+" actual packed chunk/cache/source-publish checks; initialHeld="+initialHeld+", crossingHeld="+streamedHeld+", returnHeld="+returnedHeld+", uploads="+backend.uploads+", fixtureCpuMs="+(System.nanoTime()-start)/1000000.0+"; no GPU/FPS claim");
    }
}
