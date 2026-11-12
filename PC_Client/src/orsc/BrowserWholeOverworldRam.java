package orsc;

import orsc.graphics.three.*;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Private full-source + consumed CPU preparation owner. No local server authority is cached. */
public final class BrowserWholeOverworldRam implements WholeOverworldRamRegistry.Provider {
    private static final boolean PUBLIC_ROOF_FALLBACK=Boolean.getBoolean("spoiledmilk.browserPublicAlpha");
    private static volatile java.lang.ref.WeakReference<BrowserWholeOverworldRam> roofSelection=new java.lang.ref.WeakReference<BrowserWholeOverworldRam>(null);
    static void validateInitialRoofOption(boolean preparedHiddenRoofs) {
        if(preparedHiddenRoofs!=Config.C_HIDE_ROOFS&&!PUBLIC_ROOF_FALLBACK)throw new IllegalStateException("Whole-RAM CPU roof option mismatch: prepared.hiddenRoofs="+preparedHiddenRoofs+", Config.C_HIDE_ROOFS="+Config.C_HIDE_ROOFS+"; bake matching CPU data without changing the player setting");
    }
    public static int[] getRoofSelectionWork() {
        BrowserWholeOverworldRam owner=roofSelection.get();boolean ready=owner!=null&&owner.ready;
        boolean hidden=owner!=null&&owner.hiddenRoofs,compatible=ready&&hidden==Config.C_HIDE_ROOFS;
        return new int[]{1,PUBLIC_ROOF_FALLBACK?1:0,ready?1:0,hidden?1:0,Config.C_HIDE_ROOFS?1:0,compatible?1:0};
    }
    private final byte[] source;
    private final WholeRamPrepared prepared;
    private final String manifestSha;
    private final Map<String,String> sectors=new HashMap<String,String>();
    private final Set<String> l0=new HashSet<String>(),query=new HashSet<String>();
    private final int[] settings;
    private final boolean hiddenRoofs;
    private volatile boolean ready;
    private NativeLayeredTerrainSnapshot validated;
    private int activations,hits,models,avoided,validations,prepareMillis,activationMillis;
    private long copiedBytes;
    public static boolean isEnabled(){return WholeOverworldRamRegistry.isEnabled();}
    private static String sha(byte[] bytes,int offset,int size)throws GeneralSecurityException{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(bytes,offset,size);return hex(d.digest());}
    private static String hex(byte[] bytes){char[] result=new char[bytes.length*2],digits="0123456789abcdef".toCharArray();for(int i=0;i<bytes.length;i++){int v=bytes[i]&255;result[i*2]=digits[v>>>4];result[i*2+1]=digits[v&15];}return new String(result);}
    private static byte[] read(File file,int cap)throws IOException{long n=file.length();if(n<1||n>cap)throw new IOException("Whole-RAM file bounds: "+file);byte[] bytes=new byte[(int)n];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(bytes);if(in.read()!=-1)throw new IOException("Whole-RAM file changed");}return bytes;}
    private static int sat(long n){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,n));}
    private static int[] words(JSONArray values){int[] out=new int[values.length()];for(int i=0;i<out.length;i++)out[i]=values.getInt(i);return out;}
    public BrowserWholeOverworldRam(String expectedMap,int[] expectedSettings){
        if(!isEnabled()||!WorldBuilderTerrainBootstrap.isNativeOnly()||WorldEditorBuildSettings.isEnabled()||!Renderer3DSettings.canSkipProjectedWorldCapture())throw new IllegalStateException("Whole-RAM requires private native-only whole-zone browser ownership");
        long start=System.nanoTime();int[] pending=new int[18];pending[0]=1;BrowserWebGLBridge.publishWholeRamWork(pending);
        try{
            File dir=new File(Config.F_CACHE_DIR,"whole-ram");byte[] manifestBytes=read(new File(dir,"manifest.json"),4*1024*1024);if(!sha(manifestBytes,0,manifestBytes.length).equals(System.getProperty("spoiledmilk.browserWholeRamManifestSha256","")))throw new IllegalStateException("Whole-RAM staged manifest identity mismatch");JSONObject m=new JSONObject(new String(manifestBytes,StandardCharsets.UTF_8));
            if(m.getInt("schemaVersion")!=1||!"spoiled-milk-whole-overworld-ram".equals(m.getString("type"))||!m.getBoolean("complete"))throw new IllegalStateException("Whole-RAM needs a complete useful CPU cook, not source-only delivery");
            manifestSha=m.getString("sourceManifestSha256");if(!manifestSha.equals(expectedMap)||!manifestSha.equals(WorldBuilderTerrainBootstrap.mapIdentity()))throw new IllegalStateException("Whole-RAM map identity differs from GPU/server source");
            if(!m.getString("clientJarSha256").equals(System.getProperty("spoiledmilk.browserWholeZoneClientSha256",""))||!m.getString("gpuCatalogSha256").equals(System.getProperty("spoiledmilk.browserWholeZoneCatalogSha256","")))throw new IllegalStateException("Whole-RAM CPU/GPU/client artifact binding mismatch");
            settings=words(m.getJSONArray("settings"));if(settings.length!=4||!Arrays.equals(settings,expectedSettings))throw new IllegalStateException("Whole-RAM packing options mismatch");
            JSONObject bundle=m.getJSONObject("sourceBundle");if(!"source.bin".equals(bundle.getString("path"))||bundle.getInt("headerBytes")!=16||bundle.getInt("fileCount")!=1797||bundle.getInt("rawBytes")!=46101718||bundle.getInt("bytes")!=46101734)throw new IllegalStateException("Whole-RAM source declaration");
            source=read(new File(dir,"source.bin"),64*1024*1024);if(source.length!=bundle.getInt("bytes")||!sha(source,0,source.length).equals(bundle.getString("sha256")))throw new IllegalStateException("Whole-RAM source bundle hash");
            ByteBuffer header=ByteBuffer.wrap(source,0,16).order(ByteOrder.LITTLE_ENDIAN);if(header.getInt()!=0x57524131||header.getInt()!=1||header.getInt()!=1797||header.getInt()!=46101718)throw new IllegalStateException("WRA1 header");
            JSONArray files=m.getJSONArray("files");if(files.length()!=1797)throw new IllegalStateException("Whole-RAM raw roster");int offset=16;String previous="";MessageDigest fingerprint=MessageDigest.getInstance("SHA-256");JSONObject original=null;Map<String,JSONObject> fileByPath=new HashMap<String,JSONObject>();
            for(Object value:files){JSONObject r=(JSONObject)value;String path=r.getString("path");int at=r.getInt("offset"),size=r.getInt("bytes");if(path.length()>512||!path.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")||Arrays.asList(path.split("/")).contains("..")||path.compareTo(previous)<=0||at!=offset||size<1||size>source.length-at)throw new IllegalStateException("Whole-RAM source intervals/order");String hash=sha(source,at,size);if(!hash.equals(r.getString("sha256")))throw new IllegalStateException("Whole-RAM source payload hash: "+path);for(String word:new String[]{path,Integer.toString(size),hash}){fingerprint.update(word.getBytes(StandardCharsets.UTF_8));fingerprint.update((byte)0);}fileByPath.put(path,r);if(path.equals("manifest.json")){if(size>4*1024*1024||!hash.equals(manifestSha))throw new IllegalStateException("Whole-RAM original manifest");original=new JSONObject(new String(source,at,size,StandardCharsets.UTF_8));}offset+=size;previous=path;}
            if(offset!=source.length||!hex(fingerprint.digest()).equals(m.getString("packageFingerprintSha256"))||original==null)throw new IllegalStateException("Whole-RAM full source fingerprint");
            JSONArray terrain=original.getJSONArray("terrainSectors"),placements=original.getJSONArray("placementSets");if(terrain.length()!=1787||placements.length()!=9||original.getJSONObject("storage").getInt("sectorSize")!=48||original.getJSONObject("storage").getInt("presentationChunkSize")!=24)throw new IllegalStateException("Whole-RAM selected source schema");
            Set<String> named=new HashSet<String>();named.add("manifest.json");
            for(Object value:terrain){JSONObject row=(JSONObject)value;String path=row.getString("path");JSONObject owned=fileByPath.get(path);if(owned==null||!named.add(path)||!"terrain".equals(owned.getString("kind"))||!owned.getString("sha256").equals(row.getString("sha256"))||!owned.getString("encoding").equals(row.getString("encoding"))||owned.getInt("level")!=row.getInt("level")||!owned.getString("worldSpace").equals(row.getString("worldSpace"))||owned.getInt("sectorX")!=row.getInt("sectorX")||owned.getInt("sectorY")!=row.getInt("sectorY"))throw new IllegalStateException("Whole-RAM typed terrain roster");if("global".equals(row.getString("worldSpace"))&&row.getInt("level")>=0&&row.getInt("level")<=2){String key=WholeRamPrepared.key(row.getInt("level"),row.getInt("sectorX"),row.getInt("sectorY"));if(sectors.put(key,row.getString("sha256"))!=null)throw new IllegalStateException("Duplicate RAM source cell");query.add(key);if(row.getInt("level")==0)l0.add(key);}}
            for(Object value:placements){JSONObject row=(JSONObject)value;String path=row.getString("path");JSONObject owned=fileByPath.get(path);if(owned==null||!named.add(path)||!"placements".equals(owned.getString("kind"))||!owned.getString("sha256").equals(row.getString("sha256"))||owned.getInt("level")!=row.getInt("level")||!owned.getString("worldSpace").equals(row.getString("worldSpace")))throw new IllegalStateException("Whole-RAM placement roster");}
            if(named.size()!=1797||l0.size()!=445||query.size()!=1330)throw new IllegalStateException("Whole-RAM complete source membership");
            pending[1]=1;pending[3]=1797;pending[4]=46101718;pending[7]=source.length;pending[16]=sat((System.nanoTime()-start)/1000000);BrowserWebGLBridge.publishWholeRamWork(pending);
            JSONObject product=m.getJSONObject("prepared");if(product.getInt("schemaVersion")!=1||!"prepared.bin".equals(product.getString("path"))||product.getInt("recipes")!=445||product.getInt("queryCells")!=1330)throw new IllegalStateException("Whole-RAM prepared declaration");
            File data=new File(dir,"prepared.bin");if(data.length()!=product.getLong("bytes")||data.length()<1||data.length()>WholeRamData.MAX_BYTES)throw new IllegalStateException("Whole-RAM prepared payload budget");MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new BufferedInputStream(new DigestInputStream(new FileInputStream(data),digest),65536)){prepared=WholeRamPrepared.read(in);if(in.read()!=-1)throw new IllegalStateException("Whole-RAM prepared trailing data");}
            if(!hex(digest.digest()).equals(product.getString("sha256"))||!prepared.recipeKeys().equals(l0)||!prepared.queryKeys().equals(query)||prepared.pool.size()!=product.getInt("pageCount")||prepared.pool.bytes()!=product.getLong("retainedPrimitiveBytes")||prepared.modelTemplates()!=product.getInt("modelTemplateCount"))throw new IllegalStateException("Whole-RAM prepared hash/roster/accounting mismatch");
            hiddenRoofs=product.getBoolean("hiddenRoofs");validateInitialRoofOption(hiddenRoofs);ready=true;roofSelection=new java.lang.ref.WeakReference<BrowserWholeOverworldRam>(this);prepareMillis=sat((System.nanoTime()-start)/1000000);WholeOverworldRamRegistry.install(this);BrowserWebGLBridge.publishWholeRamWork(diagnostics());
        }catch(IOException|GeneralSecurityException|RuntimeException e){BrowserWebGLBridge.clearWholeRamReady();throw new IllegalStateException("Cannot prepare complete private overworld RAM data",e);}catch(Error error){BrowserWebGLBridge.clearWholeRamReady();throw error;}
    }
    public boolean accepts(World world,NativeLayeredTerrainSnapshot snapshot){
        if(!ready)return false;if(snapshot==null)return false;
        if(!"global".equals(snapshot.getWorldSpace())||snapshot.getLevel()!=0)return false;
        if(!manifestSha.equals(snapshot.getManifestSha256()))throw new IllegalStateException("Whole-RAM source manifest changed: expected="+manifestSha+" actual="+snapshot.getManifestSha256());
        if(world.hasWholeOverworldTerrainPatches())throw new IllegalStateException("Whole-RAM rejects unbaked terrain editor patches");
        WholeRamBasis checkedBasis=world.getWholeRamBasis();
        if(!world.isWholeRamSourceBasisSupported())throw new IllegalStateException("Whole-RAM native activation unsupported: nativeOnly="+WorldBuilderTerrainBootstrap.isNativeOnly()+", projectedSkip="+Renderer3DSettings.canSkipProjectedWorldCapture()+", editor="+WorldEditorBuildSettings.isEnabled()+", alignedOffset="+checkedBasis.offsetTilesX+","+checkedBasis.offsetTilesZ);
        if(!WorldBuilderTerrainBootstrap.isNativeOnly()||!Renderer3DSettings.canSkipProjectedWorldCapture())throw new IllegalStateException("Whole-RAM native/GPU ownership changed");
        // A public account may prefer the other roof variant. Data remains verified
        // and retained, but only the original parser may publish that variant.
        if(PUBLIC_ROOF_FALLBACK&&Config.C_HIDE_ROOFS!=hiddenRoofs){validateSnapshotSource(snapshot);return false;}
        if(Config.C_HIDE_ROOFS!=hiddenRoofs)throw new IllegalStateException("Whole-RAM CPU roof option changed: expected="+hiddenRoofs+" actual="+Config.C_HIDE_ROOFS);
        validateSnapshotSource(snapshot);return true;
    }
    /** Background work owns its captured scope; current World/options only participate in publish guards. */
    public boolean acceptsCaptured(NativeLayeredTerrainSnapshot snapshot,int offsetX,int offsetZ,boolean includeRoofGeometry){
        if(!ready||snapshot==null||!"global".equals(snapshot.getWorldSpace())||snapshot.getLevel()!=0)return false;
        validateSnapshotSource(snapshot);
        if(includeRoofGeometry==hiddenRoofs||Math.floorMod(offsetX,48)!=0||Math.floorMod(offsetZ,48)!=0)return false;
        new WholeRamBasis(offsetX,offsetZ);return true;
    }
    private synchronized void validateSnapshotSource(NativeLayeredTerrainSnapshot snapshot){
        if(!manifestSha.equals(snapshot.getManifestSha256()))throw new IllegalStateException("Whole-RAM captured source manifest changed: expected="+manifestSha+" actual="+snapshot.getManifestSha256());
        if(snapshot.getProtocolVersion()<5||snapshot.getPresentationChunkSize()!=48)throw new IllegalStateException("Whole-RAM requires a full48-tile native authority window (protocol5+)");
        if(snapshot!=validated){for(int i=0;i<snapshot.getChunkSlotCount();i++){NativeLayeredTerrainChunk c=snapshot.getChunkMetadata(i);if(c.getSize()!=48)throw new IllegalStateException("Whole-RAM rejects mixed-size source slots");String expected=sectors.get(WholeRamPrepared.key(0,c.getChunkX(),c.getChunkY()));if(c.isAvailable()){if(expected==null||c.getSourceSectorX()!=c.getChunkX()||c.getSourceSectorY()!=c.getChunkY()||!expected.equals(c.getSourcePayloadSha256()))throw new IllegalStateException("Whole-RAM live source differs from owned preparation");}else if(expected!=null)throw new IllegalStateException("Whole-RAM authored source page was presented as void");}validated=snapshot;validations=sat((long)validations+1);}
    }
    public boolean activate(World world,int plane,int sx,int sy){
        if(plane!=0)return false;long start=System.nanoTime();
        try{WholeRamBasis basis=world.getWholeRamBasis();int logicalX=basis.logicalX(sx),logicalY=basis.logicalZ(sy);NativeLayeredTerrainSnapshot current=world.getWholeOverworldSourceSnapshot();if(current==null||current.getCurrentChunkX()!=logicalX||current.getCurrentChunkY()!=logicalY)throw new IllegalStateException("Whole-RAM activation carrier/source centre mismatch: carrier="+sx+","+sy+" logical="+logicalX+","+logicalY);
            World.PreparedActivation recipe=prepared.recipe(logicalX,logicalY);if(recipe==null)throw new IllegalStateException("Global L0 centre is outside complete authored RAM recipe roster: logical="+logicalX+":"+logicalY+" carrier="+sx+":"+sy);Renderer3DWorldChunkFrame frame=prepared.queryFrame(logicalX,logicalY,basis.offsetTilesX,basis.offsetTilesZ);long bytes=world.publishWholeRamActivation(recipe,frame);copiedBytes+=bytes;activations=sat((long)activations+1);hits=sat((long)hits+1);models=sat((long)models+(hiddenRoofs?144:288));activationMillis=sat((long)activationMillis+(System.nanoTime()-start)/1000000);return true;}
        catch(RuntimeException|Error failure){invalidate();BrowserWebGLBridge.publishWholeRamWork(diagnostics());throw failure;}
    }
    public Renderer3DWorldChunkFrame queryFrame(World world,int sx,int sy,int capturedOffsetTilesX,int capturedOffsetTilesZ){return prepared.queryFrame(sx,sy,capturedOffsetTilesX,capturedOffsetTilesZ);}
    public synchronized void avoidedBuild(){avoided=sat((long)avoided+1);}
    public void validateOptions(int[] current){if(!ready||!Arrays.equals(settings,current)||!PUBLIC_ROOF_FALLBACK&&hiddenRoofs!=Config.C_HIDE_ROOFS)throw new IllegalStateException("Whole-RAM render/CPU options changed");}
    public void invalidate(){ready=false;validated=null;WholeOverworldRamRegistry.clear();}
    public int[] diagnostics(){long bytes=source.length+prepared.pool.bytes();return new int[]{1,ready?1:0,ready?1:0,1797,46101718,prepared.recipes(),prepared.queryCells(),(int)bytes,(int)(bytes>>>32),activations,hits,models,(int)copiedBytes,(int)(copiedBytes>>>32),avoided,validations,prepareMillis,activationMillis};}
}
