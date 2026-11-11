package orsc;

import orsc.graphics.three.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Private immutable catalog index; normal local gameplay authority is never replaced. */
public final class BrowserWholeOverworldZone implements WholeOverworldZoneRegistry.Resolver {
    public static boolean isEnabled(){return WholeOverworldZoneRegistry.isEnabled();}
    private final String manifestHash;
    private final int[] settings,materials;
    private final Map<String,JSONObject> objects=new HashMap<String,JSONObject>();
    private final Map<String,String> sources=new HashMap<String,String>();
    private final Map<String,JSONObject> resources=new HashMap<String,JSONObject>();
    private final Map<String,Renderer3DWorldChunkFrame.ChunkMesh> descriptors=new HashMap<String,Renderer3DWorldChunkFrame.ChunkMesh>();
    private final Map<String,int[]> observed=new HashMap<String,int[]>();
    private NativeLayeredTerrainSnapshot validatedSource;
    private mudclient currentClient;private boolean ready,active;private int baseX,baseZ;
    private int[] rows=new int[0],metadata=new int[18];
    private int resolvedStatic,liveStatic,skippedWorld;
    private final int expectedResources;
    private static String key(int p,int x,int y){return p+":"+x+":"+y;}
    private static long parseKey(String value){if(!value.matches("[0-9a-f]{16}"))throw new IllegalStateException("Invalid whole-zone canonical key");return Long.parseUnsignedLong(value,16);}
    private static int[] array(JSONArray a){int[] out=new int[a.length()];for(int i=0;i<out.length;i++)out[i]=a.getInt(i);return out;}
    public BrowserWholeOverworldZone() {
        if(!isEnabled())throw new IllegalStateException("Whole-zone reader requires explicit private browser mode");
        if(Boolean.getBoolean("spoiledmilk.browserOverlayDeltaTransport"))throw new IllegalStateException("Whole-zone first prototype requires full overlay transport");
        File path=new File(Config.F_CACHE_DIR,"whole-zone/manifest.json");
        try {
            if(path.length()<1||path.length()>32L*1024*1024)throw new IllegalStateException("Private whole-zone catalog is absent or exceeds32MiB: "+path);
            byte[] bytes=new byte[(int)path.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(path))){in.readFully(bytes);if(in.read()!=-1)throw new IllegalStateException("Catalog changed while reading");}
            String expectedCatalog=System.getProperty("spoiledmilk.browserWholeZoneCatalogSha256","");MessageDigest catalogDigest=MessageDigest.getInstance("SHA-256");StringBuilder actualCatalog=new StringBuilder();for(byte value:catalogDigest.digest(bytes))actualCatalog.append(String.format("%02x",value&255));if(!expectedCatalog.matches("[a-f0-9]{64}")||!expectedCatalog.equals(actualCatalog.toString()))throw new IllegalStateException("Whole-zone Cache/native catalog identity mismatch");
            JSONObject m=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(m.getInt("schemaVersion")!=1||!"spoiled-milk-whole-overworld-zone".equals(m.getString("type"))||!m.getBoolean("complete"))throw new IllegalStateException("Complete full-source whole-zone catalog required");
            String artifact=System.getProperty("spoiledmilk.browserWholeZoneClientSha256","");if(!artifact.matches("[a-f0-9]{64}")||!artifact.equals(m.getString("clientJarSha256")))throw new IllegalStateException("Whole-zone bake/client artifact mismatch; rebuild the catalog from this exact client");
            JSONArray external=m.getJSONArray("externalAssets");if(external.length()!=5)throw new IllegalStateException("Whole-zone external asset roster");
            Set<String> required=new HashSet<String>(Arrays.asList("Cache/video/Authentic_Landscape.orsc","Cache/video/Authentic_Sprites.orsc","Cache/video/Custom_Sprites.osar","Cache/video/library.orsc","Cache/video/models.orsc"));
            for(Object item:external){JSONObject asset=(JSONObject)item;String relative=asset.getString("path");if(!required.remove(relative))throw new IllegalStateException("Unexpected/duplicate whole-zone asset");File file=new File(Config.F_CACHE_DIR,relative.substring(6));if(file.length()!=asset.getLong("bytes")||file.length()>16L*1024*1024)throw new IllegalStateException("Whole-zone Cache asset size mismatch: "+relative);MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);}StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!hash.toString().equals(asset.getString("sha256")))throw new IllegalStateException("Whole-zone Cache asset hash mismatch: "+relative);}
            manifestHash=m.getString("manifestSha256");settings=array(m.getJSONArray("settings"));materials=array(m.getJSONArray("materialMetadata"));if(settings.length!=4||materials.length>10240||materials.length%5!=0)throw new IllegalStateException("Whole-zone packing/material schema");
            JSONArray world=m.getJSONArray("worldCells"),cells=m.getJSONArray("objectCells"),chunks=m.getJSONArray("chunks");if(world.length()!=1330||cells.length()!=5320||chunks.length()>8192)throw new IllegalStateException("Full whole-zone source roster required");
            for(Object value:world){JSONObject w=(JSONObject)value;String id=key(w.getInt("plane"),w.getInt("cellX"),w.getInt("cellY"));if(sources.put(id,w.getString("sourceSha256"))!=null)throw new IllegalStateException("Duplicate whole-zone sector");}
            for(Object value:chunks){JSONObject c=(JSONObject)value;if("objects".equals(c.getString("sourceKind")))resources.put(key(c.getInt("plane"),c.getInt("cellX"),c.getInt("cellY")),c);}
            for(Object value:cells){JSONObject c=(JSONObject)value;String id=key(c.getInt("plane"),c.getInt("cellX"),c.getInt("cellY"));if(objects.put(id,c)!=null)throw new IllegalStateException("Duplicate whole-zone object cell");parseKey(c.getString("canonicalContentKey"));parseKey(c.getString("emptyCanonicalContentKey"));if(c.getJSONArray("identities").length()!=c.getInt("placementCount")*7)throw new IllegalStateException("Whole-zone identity roster mismatch");}
            expectedResources=chunks.length();
        } catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Whole-zone asset digest unavailable",e);} catch(IOException e){throw new IllegalStateException("Cannot read private whole-zone catalog "+path,e);}
    }
    public String manifestHash(){return manifestHash;}
    public int[] settings(){return settings.clone();}
    public int[] materials(){return materials.clone();}
    public void uploaded(int count){if(count!=expectedResources)throw new IllegalStateException("Whole-zone upload did not cover the full catalog");ready=true;WholeOverworldZoneRegistry.install(this);}
    private void validateSource(World world){
        if(world.hasWholeOverworldTerrainPatches())throw new IllegalStateException("Whole-zone does not support unbaked live terrain editor patches; rebuild source/catalog");
        NativeLayeredTerrainSnapshot source=world.getWholeOverworldSourceSnapshot();if(source==null)throw new IllegalStateException("Whole-zone requires authoritative native layered terrain");
        if(!manifestHash.equals(source.getManifestSha256()))throw new IllegalStateException("Whole-zone source manifest changed; rebuild the full catalog");
        if(source.getProtocolVersion()<4)throw new IllegalStateException("Whole-zone requires chunked native source protocol");
        if(source==validatedSource)return;
        if(source.getLevel()<0||source.getLevel()>2){validatedSource=source;return;}
        for(int i=0;i<source.getChunkSlotCount();i++){NativeLayeredTerrainChunk c=source.getChunkMetadata(i);String expected=sources.get(key(source.getLevel(),c.getSourceSectorX(),c.getSourceSectorY()));if(c.isAvailable()&&(expected==null||!expected.equals(c.getSourcePayloadSha256())))throw new IllegalStateException("Whole-zone live terrain differs from baked source at "+source.getLevel()+":"+c.getSourceSectorX()+":"+c.getSourceSectorY());}
        validatedSource=source;
    }
    static int[] materialMetadata(Renderer3DFrame frame){
        TreeMap<Integer,Renderer3DTextureData> sorted=new TreeMap<Integer,Renderer3DTextureData>();for(Renderer3DTextureData t:frame.getTextures())if(t!=null)sorted.put(t.getTextureId(),t);
        int[] out=new int[sorted.size()*5];int at=0;for(Renderer3DTextureData t:sorted.values())for(int value:new int[]{t.getTextureId(),t.getWidth(),t.getHeight(),t.getAverageOpaqueRgb(),t.hasTransparency()?1:0})out[at++]=value;return out;
    }
    private boolean absentCellKnown(mudclient client,int plane,int x,int y,JSONObject cell){
        if(client.isWholeZoneObjectCellAuthoritative(plane,x,y))return true;
        JSONArray ids=cell.getJSONArray("identities");if(ids.length()==0)return false;
        for(int at=0;at<ids.length();at+=7)if(!client.isWholeZoneObjectPositionAuthoritative(plane,ids.getInt(at+1),ids.getInt(at+2)))return false;
        return true;
    }
    public void frame(mudclient client,Renderer3DFrame frame,int[] currentSettings){
        if(!Arrays.equals(settings,currentSettings)||!Arrays.equals(materials,materialMetadata(frame)))throw new IllegalStateException("Whole-zone graphics/material settings changed; rebuild matching full catalog");
        this.currentClient=client;World world=client.getWorld();validateSource(world);int nextX=client.getMidRegionBaseX(),nextZ=client.getMidRegionBaseZ();if(baseX!=nextX||baseZ!=nextZ){descriptors.clear();baseX=nextX;baseZ=nextZ;}
        active=frame.getActivePlane()==0;observed.clear();int[] local=client.getWholeOverworldObjectCells();if(local.length%6!=0||local.length>6144)throw new IllegalStateException("Whole-zone local identity schema");
        for(int i=0;i<local.length;i+=6)observed.put(key(local[i],local[i+1],local[i+2]),Arrays.copyOfRange(local,i,i+6));
        NativeLayeredTerrainSnapshot snapshot=world.getWholeOverworldSourceSnapshot();int centerX=Math.floorDiv(snapshot.getCurrentChunkX()*snapshot.getPresentationChunkSize(),48),centerY=Math.floorDiv(snapshot.getCurrentChunkY()*snapshot.getPresentationChunkSize(),48);List<Integer> output=new ArrayList<Integer>();
        for(int plane=0;plane<=2;plane++)for(int cx=(centerX-2)*2;cx<(centerX+3)*2;cx++)for(int cy=(centerY-2)*2;cy<(centerY+3)*2;cy++){
            JSONObject cell=objects.get(key(plane,cx,cy));if(cell==null)continue;int[] known=observed.get(key(plane,cx,cy));long value;int reason;
            if(known!=null){value=((long)known[4]<<32)|(known[3]&0xffffffffL);reason=known[5];}
            else if(absentCellKnown(client,plane,cx,cy,cell)){value=parseKey(cell.getString("emptyCanonicalContentKey"));reason=0;}
            else {value=parseKey(cell.getString("canonicalContentKey"));reason=3;}
            long expected=parseKey(cell.getString("canonicalContentKey"));if(reason==0&&value!=expected)reason=1;
            else if(reason==0&&!absentCellKnown(client,plane,cx,cy,cell))reason=3;
            for(int word:new int[]{plane,cx,cy,(int)value,(int)(value>>>32),reason})output.add(word);
        }
        rows=new int[output.size()];for(int i=0;i<rows.length;i++)rows[i]=output.get(i);
        metadata=new int[18];metadata[0]=1;metadata[1]=active?1:0;metadata[2]=frame.getActivePlane();metadata[3]=Math.multiplyExact(baseX,128);metadata[4]=Math.multiplyExact(baseZ,128);metadata[5]=centerX;metadata[6]=centerY;metadata[7]=2;
        for(int plane=0;plane<=2;plane++){int mask=0;for(Renderer3DModelKind kind:Renderer3DModelKind.values())if((kind!=Renderer3DModelKind.ROOF||!Config.C_HIDE_ROOFS)&&frame.isWorldChunkModelKindVisible(kind,plane)&&(plane==0||kind!=Renderer3DModelKind.GAME_OBJECT&&kind!=Renderer3DModelKind.WALL_OBJECT))mask|=1<<kind.ordinal();metadata[8+plane]=mask;}
        System.arraycopy(settings,0,metadata,11,4);long epoch=world.getWholeOverworldSourceRevision();metadata[15]=(int)epoch;metadata[16]=(int)(epoch>>>32);resolvedStatic=liveStatic=skippedWorld=0;
    }
    public void invalidate(){ready=false;active=false;descriptors.clear();WholeOverworldZoneRegistry.clear();}
    public boolean ready(){return ready;}
    public int[] diagnostics(){return new int[]{1,ready?1:0,active?1:0,expectedResources,sources.size(),objects.size(),resolvedStatic,liveStatic,skippedWorld};}
    public int[] metadata(){return metadata;}
    public int[] objectRows(){return rows;}
    public Renderer3DWorldChunkFrame filter(Renderer3DWorldChunkFrame world){
        if(!ready||!active)return world;List<Renderer3DWorldChunkFrame.ChunkMesh> keep=new ArrayList<Renderer3DWorldChunkFrame.ChunkMesh>();
        for(Renderer3DWorldChunkFrame.ChunkMesh c:world.getChunks()){
            if(c.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD&&c.getPlane()>=0&&c.getPlane()<=2){skippedWorld++;continue;}
            if(c.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS){int x=Math.floorDiv(c.getOriginWorldX(),24*128),y=Math.floorDiv(c.getOriginWorldZ(),24*128);int[] actual=observed.get(key(c.getPlane(),x,y));JSONObject expected=objects.get(key(c.getPlane(),x,y));if(actual!=null&&actual[5]!=2&&expected!=null&&(((long)actual[4]<<32)|(actual[3]&0xffffffffL))==parseKey(expected.getString("canonicalContentKey"))){resolvedStatic++;continue;}liveStatic++;}
            keep.add(c);
        }
        return Renderer3DWorldChunkFrame.fromChunks(keep);
    }
    @Override public Renderer3DWorldChunkFrame.ChunkMesh resolveStatic(int plane,int x,int y,long value,int presentationX,int presentationZ){
        // Never suppress static geometry in a mixed animated cell: the native side masks all of it.
        if(!ready||!active||currentClient==null||currentClient.getRequestedPlane()!=0)return null;int[] local=currentClient.getWholeOverworldObjectCells();for(int i=0;i<local.length;i+=6)if(local[i]==plane&&local[i+1]==x&&local[i+2]==y&&local[i+5]==2)return null;
        JSONObject expected=objects.get(key(plane,x,y));if(expected==null||value!=parseKey(expected.getString("canonicalContentKey")))return null;
        String cache=key(plane,x,y)+":"+presentationX+":"+presentationZ;Renderer3DWorldChunkFrame.ChunkMesh old=descriptors.get(cache);if(old!=null)return old;
        JSONObject resource=resources.get(key(plane,x,y));List<Renderer3DWorldChunkFrame.ShadowCaster> casters=new ArrayList<Renderer3DWorldChunkFrame.ShadowCaster>();List<Renderer3DWorldChunkFrame.GlowEmitter> glows=new ArrayList<Renderer3DWorldChunkFrame.GlowEmitter>();
        if(resource!=null){for(Object v:resource.getJSONArray("shadowCasters")){int[] r=array((JSONArray)v);casters.add(new Renderer3DWorldChunkFrame.ShadowCaster(Renderer3DModelKind.values()[r[0]],r[1]-presentationX*128,r[2],r[3]-presentationZ*128,r[4]-presentationX*128,r[5]-presentationZ*128,r[6],r[7],r[8],r[9]!=0));}for(Object v:resource.getJSONArray("glowEmitters")){int[] r=array((JSONArray)v);glows.add(new Renderer3DWorldChunkFrame.GlowEmitter(Renderer3DModelKind.values()[r[0]],r[1]-presentationX*128,r[2],r[3]-presentationZ*128,r[4],r[5],r[6]));}}
        Renderer3DWorldChunkFrame.ChunkMesh descriptor=new Renderer3DWorldChunkFrame.ChunkMesh(plane,0,0,x*24*128,y*24*128,new int[0],new float[0],new float[0],new int[0],new int[0],new int[0],new int[0],new Renderer3DModelKind[0],casters.toArray(new Renderer3DWorldChunkFrame.ShadowCaster[casters.size()]),glows.toArray(new Renderer3DWorldChunkFrame.GlowEmitter[glows.size()]),0,0,0,true,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,value);
        if(descriptors.size()>1024)descriptors.clear();descriptors.put(cache,descriptor);return descriptor;
    }
}
