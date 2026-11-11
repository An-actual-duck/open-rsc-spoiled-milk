package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sector;
import orsc.Config;
import orsc.NativeLayeredTerrainChunk;
import orsc.NativeLayeredTerrainSnapshot;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Read-only, one-sector production geometry census. No Scene, GPU, client or server. */
public final class OverworldGeometryAudit {
    private static Method method(Class<?> type,String name,Class<?>... args)throws Exception {
        Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    private static Object field(Object owner,String name)throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static String hash(byte[] bytes)throws Exception {
        StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]).toRealPath(),selected=Paths.get(args[1]).toRealPath();
        int maximum=Integer.parseInt(args[3]);if(maximum<1||maximum>2048)throw new IllegalArgumentException("Explicit bounded sector limit required");
        JSONObject manifest=new JSONObject(new String(Files.readAllBytes(selected.resolve("manifest.json")),StandardCharsets.UTF_8));
        JSONArray entries=manifest.getJSONArray("terrainSectors");Map<String,JSONObject> pages=new HashMap<String,JSONObject>();
        for(int i=0;i<entries.length();i++) {JSONObject e=entries.getJSONObject(i);if("global".equals(e.getString("worldSpace")))pages.put(e.getInt("level")+":"+e.getInt("sectorX")+":"+e.getInt("sectorY"),e);}
        System.setProperty("openrsc.worldBuilderInstalledClientProfile",root.resolve("Client_Base/world-builder-configs/installed-client.json").toString());
        Config.S_WANT_CUSTOM_SPRITES=true;EntityHandler.load(true);
        World world=new World(null,null);
        String manifestHash=hash(Files.readAllBytes(selected.resolve("manifest.json")));
        Class<?> terrainType=Class.forName("orsc.graphics.three.World$TerrainModelInput"),wallType=Class.forName("orsc.graphics.three.World$WallModelInput"),roofType=Class.forName("orsc.graphics.three.World$RoofModelInput");
        Method window=method(World.class,"buildNativeLayeredCpuSectionWindow",int.class,int.class,int.class,NativeLayeredTerrainSnapshot.class,int.class,int.class);
        Method terrain=method(World.class,"buildTerrainModelInput",int.class,int.class,int.class,Sector[].class),walls=method(World.class,"buildWallModelInput",Sector[].class),roofs=method(World.class,"buildRoofModelInput",Sector[].class);
        Method shadow=method(World.class,"applyWallEndpointShadows",terrainType,wallType),build=method(World.class,"buildWorldGpuChunkMesh",int.class,int.class,int.class,terrainType,wallType,roofType,boolean.class);
        List<String> request=Files.readAllLines(Paths.get(args[2]),StandardCharsets.UTF_8);int done=0;
        for(String row:request) {
            if(row.trim().isEmpty())continue;if(++done>maximum)throw new IllegalArgumentException("Sector limit exceeded");
            String[] parts=row.split(",");int level=Integer.parseInt(parts[0]),sx=Integer.parseInt(parts[1]),sy=Integer.parseInt(parts[2]);
            if(level<0||level>2)throw new IllegalArgumentException("Overworld plus L1/L2 only");
            if(!pages.containsKey(level+":"+sx+":"+sy))throw new IllegalArgumentException("Not an authored page");
            long started=System.nanoTime();NativeLayeredTerrainChunk[] chunks=new NativeLayeredTerrainChunk[9];int n=0, availableNeighbors=0;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
                int x=sx+dx,y=sy+dy;JSONObject e=pages.get(level+":"+x+":"+y);
                if(e==null)chunks[n++]=NativeLayeredTerrainChunk.voidChunk(48,x,y);
                else {
                    Path p=selected.resolve(e.getString("path")).normalize();if(!p.startsWith(selected))throw new IllegalArgumentException("Unsafe page path");
                    availableNeighbors++;byte[] raw=Files.readAllBytes(p);if(raw.length>48*48*11||!hash(raw).equals(e.getString("sha256")))throw new IllegalArgumentException("Page hash/size mismatch");
                    chunks[n++]=NativeLayeredTerrainChunk.available(48,x,y,x,y,e.getString("encoding"),e.getString("sha256"),raw);
                }
            }
            NativeLayeredTerrainSnapshot snapshot=new NativeLayeredTerrainSnapshot(8,manifest.getString("packageId"),manifest.getString("packageVersion"),manifestHash,48,"global",level,sx,sy,1,chunks);
            Sector[] sectors=(Sector[])field(window.invoke(world,level,sx,sy,snapshot,0,0),"sectors");
            Object w=walls.invoke(world,(Object)sectors),t=level==0?terrain.invoke(world,level,sx,sy,sectors):null;
            if(t!=null)t=shadow.invoke(null,t,w);
            Object mesh=build.invoke(null,level,sx,sy,t,w,roofs.invoke(world,(Object)sectors),true);
            int[] xyz=(int[])field(mesh,"vertexCoords"),indices=(int[])field(mesh,"indices"),textures=(int[])field(mesh,"triangleTextures"),fallback=(int[])field(mesh,"triangleFallbackColors");
            Renderer3DModelKind[] kinds=(Renderer3DModelKind[])field(mesh,"triangleModelKinds");
            Renderer3DMaterialFamily[] families=(Renderer3DMaterialFamily[])field(mesh,"triangleMaterialFamilies");
            JSONObject[] cells=new JSONObject[4];long[][] counts=new long[4][4];List<Set<String>> materials=new ArrayList<Set<String>>();
            for(int c=0;c<4;c++){cells[c]=new JSONObject().put("level",level).put("cellX",sx*2+c/2).put("cellY",sy*2+c%2);materials.add(new HashSet<String>());}
            for(int triangle=0;triangle<textures.length;triangle++) {
                long sumX=0,sumZ=0;for(int corner=0;corner<3;corner++){int i=indices[triangle*3+corner];if(i!=triangle*3+corner)throw new AssertionError("Unexpected shared builder vertex layout");sumX+=xyz[i*3];sumZ+=xyz[i*3+2];}
                long lx=sumX-48L*128*3,lz=sumZ-48L*128*3;
                if(lx<0||lz<0||lx>=48L*128*3||lz>=48L*128*3)continue;
                int c=(int)(lx/(24L*128*3))*2+(int)(lz/(24L*128*3));counts[c][0]++;
                if(kinds[triangle]==Renderer3DModelKind.TERRAIN)counts[c][1]++;else if(kinds[triangle]==Renderer3DModelKind.WALL)counts[c][2]++;else if(kinds[triangle]==Renderer3DModelKind.ROOF)counts[c][3]++;
                materials.get(c).add(textures[triangle]+":"+fallback[triangle]+":"+kinds[triangle]+":"+families[triangle]);
            }
            for(int c=0;c<4;c++) {
                long triangles=counts[c][0],vertices=triangles*3;
                cells[c].put("triangles",triangles).put("vertices",vertices).put("terrainTriangles",counts[c][1]).put("wallTriangles",counts[c][2]).put("roofTriangles",counts[c][3]);
                cells[c].put("packedVertexBytes",vertices*112).put("packedIndexBytes",triangles*12).put("rawMaterialGroups",materials.get(c).size()).put("rawMaterialGroupBatchBytes",materials.get(c).size()*20L);
                // Owned primitive mesh arrays + generated normals; excludes headers, refs, effects and GPU staging.
                cells[c].put("primitiveMeshLowerBoundBytes",vertices*44+triangles*24);
            }
            Renderer3DWorldChunkFrame.ChunkMesh cropped=(Renderer3DWorldChunkFrame.ChunkMesh)method(mesh.getClass(),"toRenderer3DWorldPresentationCell",int.class,int.class).invoke(mesh,1,1);
            long compactVertices=cropped==null?0:cropped.getVertexCount();
            JSONArray output=new JSONArray();for(JSONObject c:cells)output.put(c);
            JSONObject result=new JSONObject().put("level",level).put("sectorX",sx).put("sectorY",sy).put("cells",output).put("availableNeighborPages",availableNeighbors).put("missingNeighborPages",9-availableNeighbors).put("actualPositiveStitchCellVertices",compactVertices).put("actualPositiveStitchCellTriangles",cropped==null?0:cropped.getTriangleCount()).put("actualPositiveStitchPackedBytes",compactVertices*112+(cropped==null?0:cropped.getIndexCount()*4L)).put("fullWindowVertices",xyz.length/3).put("fullWindowTriangles",textures.length).put("elapsedMs",(System.nanoTime()-started)/1000000.0).put("usedHeapBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("upperFloorGeometry",level>0?"floor-local wall/roof topology; no upper terrain":"surface terrain/walls/roofs");
            System.out.println(result.toString());System.out.flush();
        }
    }
}
