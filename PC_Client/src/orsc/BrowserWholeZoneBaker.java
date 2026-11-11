package orsc;

import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
import orsc.multiclient.ClientPort;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sector;
import com.openrsc.data.DataOperations;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Explicit offline exporter only. Never constructed by gameplay or browser startup. */
public final class BrowserWholeZoneBaker {
    private static Method method(Class<?> c,String name,Class<?>...types)throws Exception{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object get(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}
    private static void set(Object o,String name,Object value)throws Exception{field(o.getClass(),name).set(o,value);}
    private static String sha(byte[] data)throws Exception{StringBuilder b=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(data))b.append(String.format("%02x",x&255));return b.toString();}
    private static String hex(long value){return String.format("%016x",value);}
    private static JSONObject json(Path path)throws Exception{return new JSONObject(new String(Files.readAllBytes(path),StandardCharsets.UTF_8));}
    private static JSONArray ints(int[] values){JSONArray a=new JSONArray();for(int v:values)a.put(v);return a;}
    private static byte[] little(int[] values){ByteBuffer b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(int v:values)b.putInt(v);return b.array();}
    private static JSONObject descriptor(Renderer3DWorldChunkFrame.ChunkMesh mesh,int originX,int originZ)throws Exception{
        JSONArray casters=new JSONArray(),glows=new JSONArray();
        for(int i=0;i<mesh.getShadowCasterCount();i++){
            Renderer3DWorldChunkFrame.ShadowCaster c=mesh.getShadowCaster(i);
            casters.put(new JSONArray(new int[]{c.getModelKind().ordinal(),Math.addExact(c.getBaseX0(),originX),c.getBaseY(),Math.addExact(c.getBaseZ0(),originZ),Math.addExact(c.getBaseX1(),originX),Math.addExact(c.getBaseZ1(),originZ),c.getHeight(),c.getWidth(),c.getOpacity(),c.isOutdoorOnly()?1:0}));
        }
        for(int i=0;i<mesh.getGlowEmitterCount();i++){
            Renderer3DWorldChunkFrame.GlowEmitter g=mesh.getGlowEmitter(i);
            glows.put(new JSONArray(new int[]{g.getModelKind().ordinal(),Math.addExact(g.getCenterX(),originX),g.getCenterY(),Math.addExact(g.getCenterZ(),originZ),g.getRadius(),g.getColor(),g.getIntensity()}));
        }
        return new JSONObject().put("shadowCasters",casters).put("glowEmitters",glows);
    }
    private static final class Context {
        final Path root,source,output;final JSONObject sourceManifest;
        final Map<String,JSONObject> pages=new HashMap<String,JSONObject>();
        final Map<String,List<SceneBaselineState.Record>> records=new HashMap<String,List<SceneBaselineState.Record>>();
        final World world;final mudclient client;final Renderer3DFrame frame;
        final OpenGLWorldChunkRenderer packing=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
        final JSONArray chunks=new JSONArray(),worldCells=new JSONArray(),objectCells=new JSONArray(),textures=new JSONArray();
        final List<Integer> materials=new ArrayList<Integer>();int nextId=1000000,preparedSectors,preparedPlacements;
        final Class<?> terrainType=Class.forName("orsc.graphics.three.World$TerrainModelInput"),wallType=Class.forName("orsc.graphics.three.World$WallModelInput"),roofType=Class.forName("orsc.graphics.three.World$RoofModelInput");
        final Method window=method(World.class,"buildNativeLayeredCpuSectionWindow",int.class,int.class,int.class,NativeLayeredTerrainSnapshot.class,int.class,int.class);
        final Method terrain=method(World.class,"buildTerrainModelInput",int.class,int.class,int.class,Sector[].class),walls=method(World.class,"buildWallModelInput",Sector[].class,int[][].class),roofs=method(World.class,"buildRoofModelInput",Sector[].class,int[][].class);
        final Method endpointShadow=method(World.class,"applyWallEndpointShadows",terrainType,wallType),build=method(World.class,"buildWorldGpuChunkMesh",int.class,int.class,int.class,terrainType,wallType,roofType,boolean.class);
        final Method objectModel=method(mudclient.class,"createStaticPresentationGameObjectModel",SceneBaselineState.Record.class,int.class,int.class),wallModel=method(mudclient.class,"createStaticPresentationWallObjectModel",SceneBaselineState.Record.class,int.class,int.class,int.class);
        Context(Path root,Path source,Path miniCache,Path output)throws Exception{
            this.root=root;this.source=source;this.output=output;sourceManifest=json(source.resolve("manifest.json"));
            for(Object o:sourceManifest.getJSONArray("terrainSectors")){JSONObject e=(JSONObject)o;if("global".equals(e.getString("worldSpace"))&&e.getInt("level")>=0&&e.getInt("level")<=2)pages.put(e.getInt("level")+":"+e.getInt("sectorX")+":"+e.getInt("sectorY"),e);}
            for(Object o:sourceManifest.getJSONArray("placementSets")){JSONObject e=(JSONObject)o;int level=e.getInt("level");if(level<0||level>2||!"global".equals(e.getString("worldSpace")))continue;JSONObject data=json(source.resolve(e.getString("path")));for(String family:new String[]{"scenery","boundaries"})for(Object item:data.getJSONArray(family)){
                JSONObject r=(JSONObject)item,p=r.getJSONObject("position");int x=p.getInt("x"),y=p.getInt("y");String key=level+":"+Math.floorDiv(x,24)+":"+Math.floorDiv(y,24);List<SceneBaselineState.Record> list=records.get(key);if(list==null){list=new ArrayList<SceneBaselineState.Record>();records.put(key,list);}list.add(new SceneBaselineState.Record(r.getInt(family.equals("scenery")?"sceneryId":"boundaryId"),x,y,r.getInt("direction"),family.equals("scenery")?0:1));}}
            System.setProperty("openrsc.worldBuilderInstalledClientProfile",root.resolve("Client_Base/world-builder-configs/installed-client.json").toString());System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
            Config.S_WANT_CUSTOM_SPRITES=true;Config.F_CACHE_DIR=miniCache.toString();EntityHandler.load(true);
            Class<?> uc=Class.forName("sun.misc.Unsafe");Field uf=field(uc,"theUnsafe");client=(mudclient)uc.getMethod("allocateInstance",Class.class).invoke(uf.get(null),mudclient.class);
            MudClientGraphics surface=new MudClientGraphics(64,64,1);if(!surface.fillSpriteTree())throw new IllegalStateException("Exact textures-only source missing");
            Scene scene=new Scene(surface,4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);set(client,"surface",surface);set(client,"scene",scene);set(client,"externalAssetLoader",new ClientExternalAssetLoader());set(client,"tileSize",128);
            mudclient.clientPort=(ClientPort)Proxy.newProxyInstance(ClientPort.class.getClassLoader(),new Class<?>[]{ClientPort.class},new InvocationHandler(){public Object invoke(Object proxy,Method m,Object[] args){if("showLoadingProgress".equals(m.getName()))return null;throw new IllegalStateException("Offline baker cannot invoke client port "+m.getName());}});
            method(mudclient.class,"loadTextures").invoke(client);scene.setCamera(0,-4,0,768,0,0,600);scene.endScene(0);frame=scene.getRenderer3DFrame();if(frame==null||frame.getTextures().length<1)throw new IllegalStateException("Actual texture frame unavailable");
            for(Renderer3DTextureData texture:frame.getTextures()){if(texture==null)continue;int id=texture.getTextureId();for(int word:new int[]{id,texture.getWidth(),texture.getHeight(),texture.getAverageOpaqueRgb(),texture.hasTransparency()?1:0})materials.add(word);byte[] bytes=little(texture.getRgbaPixels());String path="textures/t"+id+".bin";Files.createDirectories(output.resolve("textures"));Files.write(output.resolve(path),bytes);textures.put(new JSONObject().put("id",id).put("width",texture.getWidth()).put("height",texture.getHeight()).put("transparent",texture.hasTransparency()).put("bytes",bytes.length).put("sha256",sha(bytes)).put("path",path));}
            byte[] file=Files.readAllBytes(root.resolve("Client_Base/Cache/video/models.orsc"));int plain=(file[0]&255)<<16|(file[1]&255)<<8|file[2]&255,compressed=(file[3]&255)<<16|(file[4]&255)<<8|file[5]&255;if(plain>32*1024*1024||compressed!=file.length-6)throw new IllegalArgumentException("Models archive bound");byte[] archive=Arrays.copyOfRange(file,6,file.length);if(plain!=compressed){archive=new byte[plain];com.openrsc.data.DataFileDecrypter.unpackData(archive,plain,file,compressed,6);}
            Set<Integer> requiredModels=new HashSet<Integer>();for(List<SceneBaselineState.Record> list:records.values())for(SceneBaselineState.Record r:list)if(r.type==0)requiredModels.add(EntityHandler.getObjectDef(r.id).modelID);
            RSModel[] cache=new RSModel[EntityHandler.getModelCount()];Method generated=method(mudclient.class,"createGeneratedModel",String.class),normalize=method(mudclient.class,"normalizeLoadedModel",String.class,RSModel.class);
            for(int i=0;i<cache.length;i++){String name=EntityHandler.getModelName(i);int offset=DataOperations.getDataFileOffset(name+".ob3",archive);if(offset==0&&!name.equals("myworld_air_glyph")&&!name.startsWith("myworld_cosmic_sparkles")){if(requiredModels.contains(i))throw new IllegalStateException("Unresolved required model "+name);cache[i]=new RSModel(1,1);continue;}RSModel m=offset==0?(RSModel)generated.invoke(client,name):new RSModel(archive,offset,true);cache[i]=(RSModel)normalize.invoke(client,name,m);}
            set(client,"modelCache",cache);world=new World(null,null);set(client,"world",world);
        }
        NativeLayeredTerrainSnapshot snapshot(int plane,int sx,int sy)throws Exception{
            NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[9];int at=0;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                int x=sx+dx,y=sy+dy;JSONObject page=pages.get(plane+":"+x+":"+y);
                if(page==null)chunks[at++]=NativeLayeredTerrainChunk.voidChunk(48,x,y);
                else {byte[] raw=Files.readAllBytes(source.resolve(page.getString("path")));if(raw.length>48*48*11||!sha(raw).equals(page.getString("sha256")))throw new IllegalStateException("Source terrain hash mismatch");chunks[at++]=NativeLayeredTerrainChunk.available(48,x,y,x,y,page.getString("encoding"),page.getString("sha256"),raw);}
            }
            return new NativeLayeredTerrainSnapshot(8,sourceManifest.getString("packageId"),sourceManifest.getString("packageVersion"),sha(Files.readAllBytes(source.resolve("manifest.json"))),48,"global",plane,sx,sy,1,chunks);
        }
        JSONObject emit(float[] vertices,int[] indices,int[] batches,int plane,int role,int cellSize,int cellX,int cellY,int originX,int originZ,String kind,JSONObject effects)throws Exception{
            if(vertices.length%28!=0||indices.length%3!=0||batches.length%5!=0||vertices.length/28>524288)throw new IllegalStateException("Baked resource dimensions");
            int bytes=Math.addExact(16,Math.multiplyExact(4,Math.addExact(vertices.length,Math.addExact(indices.length,batches.length))));if(bytes>64*1024*1024)throw new IllegalStateException("Baked resource transport cap");
            ByteBuffer buffer=ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);buffer.putInt(0x575a4331).putInt(vertices.length/28).putInt(indices.length).putInt(batches.length);for(float v:vertices)buffer.putInt(Float.floatToRawIntBits(v));for(int v:indices)buffer.putInt(v);for(int v:batches)buffer.putInt(v);
            int id=nextId++;String path="chunks/c"+id+".bin";Files.createDirectories(output.resolve("chunks"));byte[] raw=buffer.array();Files.write(output.resolve(path),raw);
            float minX=0,maxX=0,minY=0,maxY=0,minZ=0,maxZ=0;
            for(int i=0;i<vertices.length;i+=28){float x=vertices[i]+originX,y=vertices[i+1],z=vertices[i+2]+originZ;if(i==0){minX=maxX=x;minY=maxY=y;minZ=maxZ=z;}else{minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}}
            JSONObject row=new JSONObject().put("id",id).put("plane",plane).put("role",role).put("cellSize",cellSize).put("cellX",cellX).put("cellY",cellY).put("originX",originX).put("originZ",originZ).put("sourceKind",kind).put("visibilityMask",127).put("vertexCount",vertices.length/28).put("indexCount",indices.length).put("batchIntCount",batches.length).put("bytes",bytes).put("sha256",sha(raw)).put("path",path).put("bounds",new JSONArray(new float[]{minX,minY,minZ,maxX,maxY,maxZ}));
            row.put("shadowCasters",effects.getJSONArray("shadowCasters")).put("glowEmitters",effects.getJSONArray("glowEmitters"));chunks.put(row);return row;
        }
        JSONObject emitWorld(Renderer3DWorldChunkFrame.ChunkMesh full,int sx,int sy)throws Exception{
            float[] packed=packing.copyBackendChunkVertices(full,frame);BrowserChunkMaterials.Buffers material=BrowserChunkMaterials.pack(packing,frame,full);
            int[] remap=new int[full.getVertexCount()];Arrays.fill(remap,-1);int selected=0;
            for(int t=0;t<full.getTriangleCount();t++){
                long x=0,z=0;for(int c=0;c<3;c++){int v=full.getIndex(t*3+c);if(v!=t*3+c)throw new IllegalStateException("World baker requires production independent triangle vertices");x+=full.getVertexCoord(v*3);z+=full.getVertexCoord(v*3+2);}
                if(x>=48L*128*3&&x<96L*128*3&&z>=48L*128*3&&z<96L*128*3)for(int c=0;c<3;c++)remap[t*3+c]=selected++;
            }
            float[] vertices=new float[selected*28];for(int v=0;v<remap.length;v++)if(remap[v]>=0)System.arraycopy(packed,v*28,vertices,remap[v]*28,28);
            int[] indices=new int[selected];List<Integer> batches=new ArrayList<Integer>();int at=0;
            for(int b=0;b<material.batches.length;b+=5){int start=at;for(int i=material.batches[b+1];i<material.batches[b+1]+material.batches[b+2];i+=3){int first=material.indices[i];if(remap[first]<0)continue;indices[at++]=remap[first];indices[at++]=remap[material.indices[i+1]];indices[at++]=remap[material.indices[i+2]];}if(at>start)for(int v:new int[]{material.batches[b],start,at-start,material.batches[b+3],material.batches[b+4]})batches.add(v);}
            // Preserve original capacity semantics: unused/undrawable index tail is zero.
            int[] grouped=new int[batches.size()];for(int i=0;i<grouped.length;i++)grouped[i]=batches.get(i);
            if(selected==0)return null;
            JSONObject effects=new JSONObject().put("shadowCasters",new JSONArray()).put("glowEmitters",new JSONArray());
            return emit(vertices,indices,grouped,full.getPlane(),Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,48,sx,sy,(sx-1)*48*128,(sy-1)*48*128,"world",effects);
        }
        int elevation(int pixelX,int pixelZ,int[][] stacked){
            if(stacked==null)return world.getPresentationTerrainElevation(pixelX,pixelZ);
            int x=pixelX>>7,z=pixelZ>>7,lx=pixelX&127,lz=pixelZ&127;if(x<0||z<0||x+1>=stacked.length||z+1>=stacked[x].length)throw new IllegalStateException("Stacked object sample outside prepared window");
            int a=stacked[x][z],b=stacked[x+1][z],c=stacked[x][z+1],d=stacked[x+1][z+1];return lx<=128-lz?a+(b-a)*lx/128+(c-a)*lz/128:d+(c-d)*(128-lx)/128+(b-d)*(128-lz)/128;
        }
        void emitObjects(int plane,int sx,int sy,Renderer3DWorldChunkFrame.ChunkMesh anchor,int[][] stacked)throws Exception{
            Class<?> identityType=Class.forName("orsc.mudclient$ResidentObjectCanonicalIdentity"),builderType=Class.forName("orsc.mudclient$ResidentObjectChunkInputBuilder");
            Constructor<?> identityCtor=identityType.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,int.class,int.class);identityCtor.setAccessible(true);
            Constructor<?> builderCtor=builderType.getDeclaredConstructor(Renderer3DWorldChunkFrame.ChunkMesh.class,long.class,int.class,int.class,int.class,int.class,int.class,int.class);builderCtor.setAccessible(true);
            for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++){
                int cellX=sx*2+dx,cellY=sy*2+dy;List<SceneBaselineState.Record> list=records.get(plane+":"+cellX+":"+cellY);if(list==null)list=Collections.emptyList();
                Object builder=builderCtor.newInstance(anchor,0L,0,0,cellX,cellY,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,24);
                @SuppressWarnings("unchecked") List<Object> identities=(List<Object>)get(builder,"canonicalIdentities");List<RSModel> models=new ArrayList<RSModel>();List<int[]> descriptorRows=new ArrayList<int[]>();
                set(client,"midRegionBaseX",cellX*24);set(client,"midRegionBaseZ",cellY*24);
                for(SceneBaselineState.Record record:list){
                    int localX=record.x-(sx-1)*48,localZ=record.y-(sy-1)*48,e1,e2,kind;
                    if(record.type==0){com.openrsc.client.entityhandling.defs.GameObjectDef def=EntityHandler.getObjectDef(record.id);int width=record.direction==0||record.direction==4?def.getWidth():def.getHeight(),height=record.direction==0||record.direction==4?def.getHeight():def.getWidth();e1=e2=elevation((2*localX+width)*64,(2*localZ+height)*64,stacked);kind=Renderer3DModelKind.GAME_OBJECT.ordinal();models.add((RSModel)objectModel.invoke(client,record,models.size(),e1));}
                    else {int x1=localX,z1=localZ,x2=localX,z2=localZ;if(record.direction==0)x2++;else if(record.direction==1)z2++;else if(record.direction==2){x1++;z2++;}else if(record.direction==3){x2++;z2++;}e1=elevation(x1*128,z1*128,stacked);e2=elevation(x2*128,z2*128,stacked);kind=Renderer3DModelKind.WALL_OBJECT.ordinal();models.add((RSModel)wallModel.invoke(client,record,models.size(),e1,e2));}
                    if(e1==World.PRESENTATION_ELEVATION_UNAVAILABLE||e2==World.PRESENTATION_ELEVATION_UNAVAILABLE)throw new IllegalStateException("Object source elevation unavailable");
                    identities.add(identityCtor.newInstance(kind,record.x,record.y,record.id,record.direction,e1,e2));descriptorRows.add(new int[]{kind,record.x,record.y,record.id,record.direction,e1,e2});preparedPlacements++;
                }
                @SuppressWarnings({"unchecked","rawtypes"}) List raw=identities;Collections.sort(raw);
                long key=((Long)method(builderType,"buildCanonicalContentKey").invoke(builder)).longValue();JSONArray canonical=new JSONArray();for(Object id:identities)for(String f:new String[]{"kind","worldTileX","worldTileZ","objectId","direction","elevation1","elevation2"})canonical.put(((Integer)get(id,f)).intValue());
                JSONObject cell=new JSONObject().put("plane",plane).put("cellX",cellX).put("cellY",cellY).put("canonicalContentKey",hex(key)).put("emptyCanonicalContentKey",hex(mudclient.wholeOverworldEmptyObjectKey(plane,cellX,cellY))).put("placementCount",list.size()).put("identities",canonical);
                if(list.isEmpty())cell.put("resourceId",JSONObject.NULL).put("vertexCount",0).put("indexCount",0);
                else {Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(plane,sx,sy,cellX*24*128,cellY*24*128,models.toArray(new RSModel[models.size()]),models.size());float[] vertices=packing.copyBackendChunkVertices(mesh,frame);BrowserChunkMaterials.Buffers buffers=BrowserChunkMaterials.pack(packing,frame,mesh);JSONObject resource=emit(vertices,buffers.indices,buffers.batches,plane,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,24,cellX,cellY,cellX*24*128,cellY*24*128,"objects",descriptor(mesh,cellX*24*128,cellY*24*128));resource.put("canonicalContentKey",hex(key)).put("placementCount",list.size());cell.put("resourceId",resource.getInt("id")).put("vertexCount",resource.getInt("vertexCount")).put("indexCount",resource.getInt("indexCount"));}
                objectCells.put(cell);
            }
        }
        void bake(int limit)throws Exception{
            TreeSet<String> positions=new TreeSet<String>();for(String key:pages.keySet()){String[] p=key.split(":");positions.add(p[1]+":"+p[2]);}
            for(String position:positions){String[] p=position.split(":");int sx=Integer.parseInt(p[0]),sy=Integer.parseInt(p[1]);int[][] base=null;
                for(int plane=0;plane<=2;plane++){
                    if(preparedSectors>=limit)return;JSONObject page=pages.get(plane+":"+sx+":"+sy);if(page==null)continue;
                    NativeLayeredTerrainSnapshot snapshot=snapshot(plane,sx,sy);Sector[] sectors=(Sector[])get(window.invoke(world,plane,sx,sy,snapshot,0,0),"sectors");set(world,"sectors",sectors);
                    set(world,"nativeLayeredTerrainSnapshot",snapshot);set(world,"nativeLayeredTerrainAppliedSectionX",sx);set(world,"nativeLayeredTerrainAppliedSectionY",sy);
                    Object wallInput=walls.invoke(world,sectors,base),roofInput=roofs.invoke(world,sectors,base),terrainInput=plane==0?terrain.invoke(world,plane,sx,sy,sectors):null;
                    if(terrainInput!=null)terrainInput=endpointShadow.invoke(null,terrainInput,wallInput);
                    Object product=build.invoke(null,plane,sx,sy,terrainInput,wallInput,roofInput,true);
                    Renderer3DWorldChunkFrame.ChunkMesh full=(Renderer3DWorldChunkFrame.ChunkMesh)method(product.getClass(),"toRenderer3DWorldChunkMesh").invoke(product);
                    JSONObject resource=emitWorld(full,sx,sy);JSONObject cell=new JSONObject().put("plane",plane).put("cellX",sx).put("cellY",sy).put("sourceSha256",page.getString("sha256"));
                    if(resource==null)cell.put("resourceId",JSONObject.NULL).put("vertexCount",0).put("indexCount",0);
                    else cell.put("resourceId",resource.getInt("id")).put("vertexCount",resource.getInt("vertexCount")).put("indexCount",resource.getInt("indexCount"));
                    worldCells.put(cell);emitObjects(plane,sx,sy,full,base);base=(int[][])get(roofInput,"finalElevations");preparedSectors++;
                    System.out.println("WHOLE_ZONE_BAKE sector="+plane+":"+sx+":"+sy+" prepared="+preparedSectors+" resources="+chunks.length());System.out.flush();
                }
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=6)throw new IllegalArgumentException("root selectedPackage miniCache output clientJarSha256 limitSectors");
        Path root=Paths.get(args[0]).toRealPath(),source=Paths.get(args[1]).toRealPath(),miniCache=Paths.get(args[2]).toRealPath(),output=Paths.get(args[3]).toAbsolutePath().normalize();
        if(!args[4].matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Exact full-source artifact hash required");int limit=Integer.parseInt(args[5]);if(limit<1||limit>1330)throw new IllegalArgumentException("Sector cap1..1330");
        if(Files.exists(output))throw new IllegalArgumentException("Preserve previous bakes; output must be new");Files.createDirectories(output);
        long started=System.nanoTime();Context c=new Context(root,source,miniCache,output);c.bake(limit);
        int[] metadata=new int[c.materials.size()];for(int i=0;i<metadata.length;i++)metadata[i]=c.materials.get(i);
        JSONObject scope=new JSONObject().put("sectors",new JSONObject().put("0",445).put("1",444).put("2",441)).put("placements",new JSONObject().put("0",22981).put("1",1260).put("2",339));
        byte[] original=Files.readAllBytes(source.resolve("manifest.json"));Files.write(output.resolve("source-manifest.json"),original);
        JSONObject manifest=new JSONObject().put("schemaVersion",1).put("type","spoiled-milk-whole-overworld-zone").put("packageFingerprintSha256",source.getParent().getFileName().toString()).put("manifestSha256",sha(original)).put("sourceManifestBytes",original.length).put("clientJarSha256",args[4]).put("settings",ints(c.packing.backendPackingSettings())).put("materialMetadata",ints(metadata)).put("textureCatalogSha256",sha(little(metadata))).put("scope",scope).put("chunks",c.chunks).put("worldCells",c.worldCells).put("objectCells",c.objectCells).put("textures",c.textures).put("complete",c.preparedSectors==1330&&c.preparedPlacements==24580).put("preparedSectors",c.preparedSectors).put("preparedPlacements",c.preparedPlacements).put("bakeMilliseconds",(System.nanoTime()-started)/1000000.0).put("upperGeometryPolicy","ground-view stacked roof elevations; upper object resources retained but not drawn from ground");
        Files.write(output.resolve("manifest.json"),manifest.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println("WHOLE_ZONE_BAKE manifest="+output.resolve("manifest.json")+" complete="+manifest.getBoolean("complete"));
    }
}
