package orsc;

import java.lang.reflect.Field;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Production bridge cache and production packing, with only the native upload boundary replaced. */
public final class BrowserAnimatedChunkFixture {
    private static int checks;
    private static void require(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Data {
        final float[] vertices;final int[] indices,batches;
        Data(float[] v,int[] i,int[] b){vertices=v.clone();indices=i.clone();batches=b.clone();}
    }
    private static final class Backend implements BrowserWebGLBridge.ChunkBackend {
        final Map<Integer,Data> resources=new HashMap<Integer,Data>();
        int uploads,replaces,deletes;boolean failUpload,failReplace;
        public void upload(int id,float[] v,int[] i,int[] b){if(failUpload)throw new IllegalStateException("fixture upload failure");require(!resources.containsKey(id),"Upload reused live ID");resources.put(id,new Data(v,i,b));uploads++;}
        public void replace(int id,float[] v,int[] i,int[] b){if(failReplace)throw new IllegalStateException("fixture replace failure");Data old=resources.get(id);require(old!=null&&old.vertices.length==v.length&&old.indices.length==i.length,"replace changed buffer shape");resources.put(id,new Data(v,i,b));replaces++;}
        public void delete(int[] ids){for(int id:ids){require(resources.remove(id)!=null,"Deleted absent resource");deletes++;}}
    }
    private static Renderer3DWorldChunkFrame.ChunkMesh mesh(int role,int plane,int section,int origin,int pose,int triangles,long signature,int texture){
        int[] xyz=new int[triangles*9],indices=new int[triangles*3],lights=new int[triangles*3],textures=new int[triangles],fallback=new int[triangles];float[] u=new float[triangles*3],v=new float[triangles*3];Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];
        for(int t=0;t<triangles;t++){
            int at=t*9;xyz[at]=t*160+pose;xyz[at+1]=-pose;xyz[at+2]=0;xyz[at+3]=t*160+128;xyz[at+4]=-16;xyz[at+5]=0;xyz[at+6]=t*160;xyz[at+7]=-32;xyz[at+8]=128;
            indices[t*3]=t*3;indices[t*3+1]=t*3+1;indices[t*3+2]=t*3+2;u[t*3+1]=1;v[t*3+2]=1;textures[t]=texture;fallback[t]=0x336699;kinds[t]=Renderer3DModelKind.GAME_OBJECT;
        }
        return new Renderer3DWorldChunkFrame.ChunkMesh(plane,section,section,origin,origin,xyz,u,v,lights,indices,textures,fallback,kinds,null,0,0,0,true,role,signature);
    }
    private static Renderer3DWorldChunkFrame world(Renderer3DWorldChunkFrame.ChunkMesh... chunks){return Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(chunks));}
    private static BrowserWebGLBridge.ChunkDraws prepare(BrowserWebGLBridge bridge,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh... chunks){Renderer3DWorldChunkFrame world=world(chunks);Set<WorldChunkBufferKey> active=BrowserWebGLBridge.activeKeys(world);bridge.evictInactive(active);Renderer3DFrame old=RendererFrameLighting.bind(frame);try{return bridge.prepareChunks(frame,world,active);}finally{RendererFrameLighting.restore(old);}}
    private static long bytes(BrowserWebGLBridge bridge)throws Exception{Field f=BrowserWebGLBridge.class.getDeclaredField("residentChunkBytes");f.setAccessible(true);return f.getLong(bridge);}
    private static void setLong(BrowserWebGLBridge bridge,String name,long value)throws Exception{Field f=BrowserWebGLBridge.class.getDeclaredField(name);f.setAccessible(true);f.setLong(bridge,value);}
    private static void reference(Backend backend,int id,Renderer3DFrame frame,Renderer3DWorldChunkFrame.ChunkMesh mesh){OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);Renderer3DFrame old=RendererFrameLighting.bind(frame);try{Data actual=backend.resources.get(id);BrowserChunkMaterials.Buffers material=BrowserChunkMaterials.pack(renderer,frame,mesh);require(Arrays.equals(actual.vertices,renderer.copyBackendChunkVertices(mesh,frame)),"Current pose vertices differ from production packing");require(Arrays.equals(actual.indices,material.indices)&&Arrays.equals(actual.batches,material.batches),"Current material/index bytes differ from production packing");}finally{RendererFrameLighting.restore(old);}}
    private static void fails(Runnable action,String why){boolean failed=false;try{action.run();}catch(IllegalStateException expected){failed=true;}require(failed,why);}
    private static void immutableControl(Renderer3DFrame frame)throws Exception {
        int animated=Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,statik=Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS;
        Backend backend=new Backend();BrowserWebGLBridge bridge=new BrowserWebGLBridge(backend);
        Renderer3DWorldChunkFrame.ChunkMesh first=mesh(animated,-1,7,100,1,2,20000,0),second=mesh(animated,-1,7,100,2,2,20001,0);
        require(WorldChunkBufferKey.from(first,false).equals(WorldChunkBufferKey.from(second,false)),"Native false parameter unexpectedly toggled animated identity");
        require(!BrowserWebGLBridge.chunkKey(first).equals(BrowserWebGLBridge.chunkKey(second)),"OFF did not select per-pose immutable storage");
        int firstId=prepare(bridge,frame,first).draws[0];reference(backend,firstId,frame,first);
        for(int n=1;n<100;n++){
            Renderer3DWorldChunkFrame.ChunkMesh pose=mesh(animated,-1,7,100,n+1,2,20000+n,0);int id=prepare(bridge,frame,pose).draws[0];require(id!=firstId,"OFF reused mutable pose ID");reference(backend,id,frame,pose);
        }
        require(backend.uploads==100&&backend.replaces==0,"OFF did not restore per-pose uploads");
        boolean active="active".equals(System.getProperty("spoiledmilk.browserCachePolicy"));
        require(backend.resources.size()==(active?1:100),"OFF resident identities do not follow existing cache policy");
        int returnedId=prepare(bridge,frame,first).draws[0];require(active?returnedId!=firstId:returnedId==firstId,"OFF prior pose cache reuse differs from policy");reference(backend,returnedId,frame,first);
        Renderer3DWorldChunkFrame.ChunkMesh fixed=mesh(statik,-1,7,100,0,2,4000,0),rebased=fixed.rebaseStaticObjectPresentation(8,8,128,-128);
        require(BrowserWebGLBridge.chunkKey(fixed).equals(WorldChunkBufferKey.from(fixed,true)),"Private control changed static identity");
        int fixedId=prepare(bridge,frame,fixed).draws[0],uploads=backend.uploads;BrowserWebGLBridge.ChunkDraws draw=prepare(bridge,frame,rebased);
        require(draw.draws[0]==fixedId&&backend.uploads==uploads&&draw.draws[1]==128&&draw.draws[2]==-128,"OFF changed static rebase handling");
        for(int n=0;n<520;n++)prepare(bridge,frame,mesh(animated,-1,7,100,n,2,30000+n,0));
        require(backend.resources.size()<=(active?1:512),"OFF bypassed resident bound");long live=0;for(Data data:backend.resources.values())live+=data.vertices.length*4L+data.indices.length*4L;require(live==bytes(bridge),"OFF resident accounting differs from live boundary");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
        Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.setFrustum(0,11,7,1);
        byte[] texture=new byte[4096];Arrays.fill(texture,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x112233},0,texture);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();
        if(System.getProperty("spoiledmilk.browserAnimatedBufferReuse")==null)require(BrowserWebGLBridge.isAnimatedBufferReuseEnabled(),"Default animated reuse changed");
        if(!BrowserWebGLBridge.isAnimatedBufferReuseEnabled()){
            immutableControl(frame);frame.release();System.out.println("PASS "+checks+" production immutable-pose OFF control checks ("+System.getProperty("spoiledmilk.browserCachePolicy","lru")+"); no GPU/FPS claim");return;
        }
        int animated=Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,statik=Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS;
        Backend backend=new Backend();BrowserWebGLBridge bridge=new BrowserWebGLBridge(backend);
        Renderer3DWorldChunkFrame.ChunkMesh a=mesh(animated,-1,7,100,0,2,101,0),b=mesh(animated,-1,7,100,11,2,102,0);
        int id=prepare(bridge,frame,a).draws[0];reference(backend,id,frame,a);long initialBytes=bytes(bridge);
        require(WorldChunkBufferKey.from(a,true).equals(WorldChunkBufferKey.from(b,true)),"Pose changed logical native identity");
        for(int n=0;n<100;n++){Renderer3DWorldChunkFrame.ChunkMesh pose=mesh(animated,-1,7,100,n+1,2,1000+n,0);require(prepare(bridge,frame,pose).draws[0]==id,"Animated pose allocated another ID");reference(backend,id,frame,pose);}
        require(backend.uploads==1&&backend.replaces==100&&backend.deletes==0&&backend.resources.size()==1&&bytes(bridge)==initialBytes,"Animated poses churned cache/bytes");
        require(prepare(bridge,frame,a).draws[0]==id,"Returning prior pose allocated a second resource");reference(backend,id,frame,a);
        int before=backend.replaces;Renderer3DWorldChunkFrame.ChunkMesh materialPose=mesh(animated,-1,7,100,5,2,2000,-1);prepare(bridge,frame,materialPose);reference(backend,id,frame,materialPose);require(backend.replaces==before+1,"New pose/material not replaced");
        RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);prepare(bridge,frame,materialPose);reference(backend,id,frame,materialPose);require(backend.replaces==before+2,"Packing settings not invalidated");RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
        backend.failReplace=true;fails(()->prepare(bridge,frame,b),"Replace failure swallowed");backend.failReplace=false;require(prepare(bridge,frame,b).draws[0]==id,"Failed replace lost old ID");reference(backend,id,frame,b);
        Renderer3DWorldChunkFrame.ChunkMesh larger=mesh(animated,-1,7,100,20,3,3000,0);backend.failUpload=true;fails(()->prepare(bridge,frame,larger),"Topology upload failure swallowed");require(backend.resources.size()==1&&bytes(bridge)==initialBytes,"Failed topology replacement changed owned cache");backend.failUpload=false;
        setLong(bridge,"residentChunkBytes",224L*1024*1024-1);int beforeUploads=backend.uploads;fails(()->prepare(bridge,frame,larger),"Peak allocation budget bypassed");require(backend.uploads==beforeUploads&&backend.resources.containsKey(id),"Budget failure allocated/deleted active buffer");setLong(bridge,"residentChunkBytes",initialBytes);
        int largerId=prepare(bridge,frame,larger).draws[0];require(largerId!=id&&!backend.resources.containsKey(id)&&backend.resources.size()==1,"Topology did not commit new then delete old");reference(backend,largerId,frame,larger);require(bytes(bridge)==backend.resources.get(largerId).vertices.length*4L+backend.resources.get(largerId).indices.length*4L,"Topology bytes misaccounted");
        int smallerId=prepare(bridge,frame,b).draws[0];require(smallerId!=largerId&&backend.resources.size()==1&&!backend.resources.containsKey(largerId),"Topology shrink did not release old capacity");reference(backend,smallerId,frame,b);
        largerId=prepare(bridge,frame,larger).draws[0];
        Renderer3DWorldChunkFrame.ChunkMesh negative=mesh(animated,-2,7,100,20,3,3001,0);require(!WorldChunkBufferKey.from(larger,true).equals(WorldChunkBufferKey.from(negative,true)),"Negative levels aliased");require(prepare(bridge,frame,larger,negative).draws[8]!=largerId,"Planes share one animated buffer");
        Renderer3DWorldChunkFrame.ChunkMesh moved=mesh(animated,-1,8,228,20,3,3002,0);require(!WorldChunkBufferKey.from(larger,true).equals(WorldChunkBufferKey.from(moved,true)),"Animated logical rebase reused wrong positional owner");prepare(bridge,frame,moved);reference(backend,prepare(bridge,frame,moved).draws[0],frame,moved);
        Renderer3DWorldChunkFrame.ChunkMesh fixed=mesh(statik,-1,7,100,0,2,4000,0),rebased=fixed.rebaseStaticObjectPresentation(8,8,128,-128);int fixedId=prepare(bridge,frame,fixed).draws[0],uploadBefore=backend.uploads;BrowserWebGLBridge.ChunkDraws rebasedDraw=prepare(bridge,frame,rebased);require(rebasedDraw.draws[0]==fixedId&&backend.uploads==uploadBefore&&rebasedDraw.draws[1]==128&&rebasedDraw.draws[2]==-128,"Immutable static rebase cache changed");
        fails(()->prepare(bridge,frame,a,b),"Conflicting simultaneous animated identity accepted");
        fails(()->BrowserWebGLBridge.activeKeys(world(a,b),new boolean[]{true,true}),"Demand accepted conflicting animated poses");
        fails(()->BrowserWebGLBridge.activeKeys(world(a,b),new boolean[]{true,false}),"Demand hid conflicting omitted animated pose");
        fails(()->BrowserWebGLBridge.activeKeys(world(a,b),new boolean[]{false,false}),"Empty demand hid conflicting source identities");
        // Full repack budget cannot defer a changed animation pose behind immutable settings work.
        Backend busy=new Backend();BrowserWebGLBridge busyBridge=new BrowserWebGLBridge(busy);Renderer3DWorldChunkFrame.ChunkMesh largeStatic=mesh(statik,0,0,0,0,11000,9000,0);prepare(busyBridge,frame,largeStatic,a);prepare(busyBridge,frame,largeStatic,a);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED);BrowserWebGLBridge.ChunkDraws busyDraw=prepare(busyBridge,frame,largeStatic,b);reference(busy,busyDraw.draws[8],frame,b);require(busyDraw.remainingRepacks==0,"Animated current pose was deferred by immutable repack budget");
        // Animation work must not consume the discretionary loading budget: the
        // first new chunk may itself exceed the cap, as in the production loader.
        Backend loading=new Backend();BrowserWebGLBridge loadingBridge=new BrowserWebGLBridge(loading);prepare(loadingBridge,frame,a);
        for(int n=0;n<4;n++){
            Renderer3DWorldChunkFrame.ChunkMesh currentPose=mesh(animated,-1,7,100,30+n,2,5000+n,0);
            Renderer3DWorldChunkFrame.ChunkMesh[] sceneChunks=new Renderer3DWorldChunkFrame.ChunkMesh[101];Arrays.fill(sceneChunks,largeStatic);sceneChunks[100]=currentPose;
            BrowserWebGLBridge.ChunkDraws progress=prepare(loadingBridge,frame,sceneChunks);
            require(progress.draws.length==101*8,"Changed animation permanently starved first oversized loading chunk");
            require(progress.draws[0]==progress.draws[99*8],"Duplicate static draw instances did not share storage");
            reference(loading,progress.draws[100*8],frame,currentPose);
        }
        require(loading.uploads==2&&loading.replaces==4,"Loading/animation schedule allocated redundant resources");
        Backend topologyLoad=new Backend();BrowserWebGLBridge topologyBridge=new BrowserWebGLBridge(topologyLoad);prepare(topologyBridge,frame,a);
        BrowserWebGLBridge.ChunkDraws topologyProgress=prepare(topologyBridge,frame,largeStatic,larger);
        require(topologyProgress.draws.length==16&&topologyLoad.uploads==3&&topologyLoad.deletes==1,"Mandatory topology change starved first oversized loading chunk");reference(topologyLoad,topologyProgress.draws[8],frame,larger);
        // Settings-only work still has its original cap, without making every
        // pending repack mandatory just because current animation is exempt.
        Backend settings=new Backend();BrowserWebGLBridge settingsBridge=new BrowserWebGLBridge(settings);
        Renderer3DWorldChunkFrame.ChunkMesh staticA=mesh(statik,0,1,1,0,6667,9100,0),staticB=mesh(statik,0,2,2,0,6667,9101,0);
        prepare(settingsBridge,frame,staticA,staticB);prepare(settingsBridge,frame,staticA,staticB);
        RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
        require(prepare(settingsBridge,frame,staticA,staticB).remainingRepacks==1&&settings.replaces==1,"Settings-only repacks bypassed discretionary cap");
        require(prepare(settingsBridge,frame,staticA,staticB).remainingRepacks==0&&settings.replaces==2,"Deferred settings repack never completed");
        // Same geometry/material IDs, changed texture metadata must repack without a pose change.
        scene.loadTexture(0,new int[]{0xff00ff,0x99cc11},0,texture);scene.endScene(0);Renderer3DFrame recolored=scene.getRenderer3DFrame();
        int materialId=prepare(bridge,frame,b).draws[0],materialReplaces=backend.replaces;
        setLong(bridge,"materialRevision",1);prepare(bridge,recolored,b);require(backend.replaces==materialReplaces+1,"Independent material revision did not repack");reference(backend,materialId,recolored,b);recolored.release();
        Backend bounded=new Backend();BrowserWebGLBridge boundedBridge=new BrowserWebGLBridge(bounded);
        for(int n=0;n<520;n++)prepare(boundedBridge,frame,mesh(statik,0,0,0,0,1,10000+n,-1));
        require(bounded.resources.size()<=("active".equals(System.getProperty("spoiledmilk.browserCachePolicy"))?1:512),"Resident entry bound exceeded");
        long expectedBytes=0;for(Data data:bounded.resources.values())expectedBytes+=data.vertices.length*4L+data.indices.length*4L;require(bytes(boundedBridge)==expectedBytes,"Eviction accounting differs from live GPU boundary");
        frame.release();System.out.println("PASS "+checks+" production animated buffer identity/pose/material/topology/negative-level/rebase/budget/failure checks ("+System.getProperty("spoiledmilk.browserCachePolicy","lru")+"); no GPU/FPS claim");
    }
}
