package orsc;
import java.util.*;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import orsc.graphics.three.*;
/** Private exact source-template copier, never scene/placement authority. */
final class BrowserPresentationCloneCache {
    static final int LEGACY_DELTA=0,BASELINE=1,CANONICAL=2,MAX_TEMPLATES=256;
    static final long MAX_BYTES=16L*1024*1024,TRANSIENT_RESERVE=2L*1024*1024;
    private static final boolean ENABLED=Boolean.getBoolean("spoiledmilk.browserBenchmark")
        &&Boolean.getBoolean("spoiledmilk.browserCloneTemplates");
    private final Map<Integer,Entry> entries=new HashMap<Integer,Entry>();
    private final long[] requests=new long[3],hits=new long[3],misses=new long[3];
    private String scope;private long bytes,peak,retainedPeak,refusals,resets,copied;
    static boolean isEnabled(){return ENABLED;}
    synchronized void clear(){entries.clear();scope=null;bytes=0;resets++;}
    synchronized long[] work(){return new long[]{1,ENABLED?1:0,requests[0],hits[0],misses[0],requests[1],hits[1],misses[1],requests[2],hits[2],misses[2],refusals,resets,bytes,peak,entries.size(),copied,retainedPeak};}
    synchronized RSModel clone(mudclient client,int id,int owner){
        if(owner<0||owner>2)throw new IllegalArgumentException("Clone owner");requests[owner]++;
        GameObjectDef def=EntityHandler.getObjectDef(id);RSModel source=def==null||def.modelID<0?null:client.getModelCacheItem(def.modelID);
        if(source==null)throw new IllegalStateException("Missing game-object clone source");
        NativeLayeredTerrainSnapshot terrain=client.getWorld()==null?null:client.getWorld().getWholeOverworldSourceSnapshot();
        if(!ENABLED||!Boolean.getBoolean("spoiledmilk.browserWholeRam")||!WholeOverworldZoneRegistry.isStaticSceneReuseEnabled()
            ||!Renderer3DSettings.isBrowserPresentationAvailable()||terrain==null||terrain.getLevel()!=0||!"global".equals(terrain.getWorldSpace())
            ||client.getRequestedPlane()!=0||client.getWorld().hasWholeOverworldTerrainPatches()||!client.isBrowserBaselineStaticObject(id)){
            if(!entries.isEmpty()||scope!=null)clear();refusals++;return source.clone();
        }
        String context=terrain.packageIdentity()+":"+Config.S_WANT_CUSTOM_SPRITES+":"+Config.S_WANT_CUSTOM_LANDSCAPE
            +":"+RendererGeometrySettings.getMode()+":"+RendererLightingSettings.getMode()+":"+RendererToneSettings.getMode()+":"+RendererFogSettings.getMode();
        if(!context.equals(scope)){clear();scope=context;}
        Entry entry=entries.get(id);RSModel.BrowserCloneTemplate template=entry==null?null:entry.template;
        if(template!=null&&entry.matches(def,source)&&template.matches(source)){
            hits[owner]++;copied++;return template.instantiate();
        }
        misses[owner]++;
        if(template!=null){entries.remove(id);bytes-=template.retainedBytes();}
        // Literal clone commits source transforms. The post-commit state, clone
        // material metadata and every captured scalar/array form the new recipe.
        RSModel result=source.clone();long remaining=MAX_BYTES-TRANSIENT_RESERVE-bytes;
        if(entries.size()>=MAX_TEMPLATES||remaining<4096){refusals++;return result;}
        peak=Math.max(peak,bytes+remaining+TRANSIENT_RESERVE);
        template=RSModel.browserCloneTemplate(source,result,remaining);
        if(template==null){refusals++;return result;}
        entries.put(id,new Entry(def,source,template));bytes+=template.retainedBytes();retainedPeak=Math.max(retainedPeak,bytes);peak=Math.max(peak,bytes+TRANSIENT_RESERVE);
        if(bytes+TRANSIENT_RESERVE>MAX_BYTES)throw new IllegalStateException("Clone template budget");
        return result;
    }
    private static final class Entry {
        final RSModel source;final RSModel.BrowserCloneTemplate template;
        final int model,width,height,type,ground,id;final boolean interaction;final String name,modelName,command,command2,description;
        Entry(GameObjectDef d,RSModel s,RSModel.BrowserCloneTemplate t){source=s;template=t;model=d.modelID;width=d.getWidth();height=d.getHeight();type=d.getType();interaction=d.isInteractionOnly();name=d.getName();modelName=d.getObjectModel();command=d.getCommand1();command2=d.getCommand2();description=d.getDescription();ground=d.getGroundItemVar();id=d.id;}
        boolean matches(GameObjectDef d,RSModel s){return source==s&&model==d.modelID&&width==d.getWidth()&&height==d.getHeight()&&type==d.getType()&&interaction==d.isInteractionOnly()&&Objects.equals(name,d.getName())&&Objects.equals(modelName,d.getObjectModel())&&Objects.equals(command,d.getCommand1())&&Objects.equals(command2,d.getCommand2())&&Objects.equals(description,d.getDescription())&&ground==d.getGroundItemVar()&&id==d.id;}
    }
}
