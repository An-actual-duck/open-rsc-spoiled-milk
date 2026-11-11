package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;
import orsc.graphics.three.*;

/** Explicit offline CPU cook; never started by gameplay. Partial pilots cannot become ready. */
public final class BrowserWholeRamBaker {
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Method method(Class<?> c,String n,Class<?>...types)throws Exception{Method m=c.getDeclaredMethod(n,types);m.setAccessible(true);return m;}
    private static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
    private static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    private static String sha(Path file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte v:d.digest())h.append(String.format("%02x",v&255));return h.toString();}
    public static void main(String[] args)throws Exception{
        if(args.length!=7&&args.length!=8)throw new IllegalArgumentException("root selectedPackage miniCache rawBundleDir NEWoutput clientJarSha recipeLimit [sx:sy,...]");
        Path root=Paths.get(args[0]).toRealPath(),source=Paths.get(args[1]).toRealPath(),mini=Paths.get(args[2]).toRealPath(),raw=Paths.get(args[3]).toRealPath(),out=Paths.get(args[4]).toAbsolutePath().normalize();int limit=Integer.parseInt(args[6]);if(limit<1||limit>445||!args[5].matches("[a-f0-9]{64}")||Files.exists(out))throw new IllegalArgumentException("Preserve prior cook; exact artifact and recipe limit1..445 required");
        JSONObject manifest=new JSONObject(new String(Files.readAllBytes(raw.resolve("manifest.json")),StandardCharsets.UTF_8));if(!manifest.getString("clientJarSha256").equals(args[5])||manifest.getBoolean("complete")||manifest.getJSONObject("sourceBundle").getLong("bytes")!=Files.size(raw.resolve("source.bin"))||!manifest.getJSONObject("sourceBundle").getString("sha256").equals(sha(raw.resolve("source.bin"))))throw new IllegalStateException("Verified source-only delivery from this exact artifact required");
        Files.createDirectories(out);long start=System.nanoTime();
        for(String flag:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldReplacementComposite","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster"})System.setProperty("spoiledmilk."+flag,"true");
        Class<?> type=Class.forName("orsc.BrowserWholeZoneBaker$Context");Constructor<?> constructor=type.getDeclaredConstructor(Path.class,Path.class,Path.class,Path.class);constructor.setAccessible(true);Object c=constructor.newInstance(root,source,mini,out.resolve("cook-assets"));
        mudclient client=(mudclient)get(c,"client");World queryWorld=(World)get(c,"world");orsc.graphics.two.GraphicsController surface=client.getSurface();Scene scene=client.getScene();World publication=new World(scene,surface);Renderer3DSettings.setBrowserPresentationAvailable(true);
        JSONObject original=(JSONObject)get(c,"sourceManifest");if(!manifest.getString("sourceManifestSha256").equals(get(c,"sourceManifestHash"))||!manifest.getJSONArray("externalAssets").toString().equals(get(c,"initialExternalAssets").toString()))throw new IllegalStateException("CPU cook/source assets mismatch");
        WholeRamData.Pool pool=new WholeRamData.Pool();WholeRamPrepared prepared=new WholeRamPrepared(pool);
        @SuppressWarnings("unchecked") Map<String,JSONObject> pages=(Map<String,JSONObject>)get(c,"pages");TreeSet<String> positions=new TreeSet<String>();for(String key:pages.keySet()){String[] k=key.split(":");positions.add(k[1]+":"+k[2]);}Set<String> chosen=args.length==8?new HashSet<String>(Arrays.asList(args[7].split(","))):null;
        Method snapshot=method(type,"snapshot",int.class,int.class,int.class),window=(Method)get(c,"window"),terrain=(Method)get(c,"terrain"),walls=(Method)get(c,"walls"),roofs=(Method)get(c,"roofs"),endpoint=(Method)get(c,"endpointShadow"),build=(Method)get(c,"build");
        int visited=0;boolean stop=false;
        for(String position:positions){if(chosen!=null&&!chosen.contains(position))continue;String[] xy=position.split(":");int x=Integer.parseInt(xy[0]),y=Integer.parseInt(xy[1]);if(pages.containsKey("0:"+position)){if(prepared.recipes()>=limit){stop=true;break;}NativeLayeredTerrainSnapshot snap=(NativeLayeredTerrainSnapshot)snapshot.invoke(c,0,x,y);prepared.addRecipe(publication.prepareWholeRamActivation(snap,pool));}
            int[][] base=null;for(int plane=0;plane<=2;plane++){NativeLayeredTerrainSnapshot snap=(NativeLayeredTerrainSnapshot)snapshot.invoke(c,plane,x,y);Object w=window.invoke(queryWorld,plane,x,y,snap,0,0);com.openrsc.client.model.Sector[] sectors=(com.openrsc.client.model.Sector[])get(w,"sectors");set(queryWorld,"sectors",sectors);set(queryWorld,"nativeLayeredTerrainSnapshot",snap);set(queryWorld,"nativeLayeredTerrainAppliedSectionX",x);set(queryWorld,"nativeLayeredTerrainAppliedSectionY",y);Object wall=walls.invoke(queryWorld,sectors,base),roof=roofs.invoke(queryWorld,sectors,base),ground=plane==0?terrain.invoke(queryWorld,plane,x,y,sectors):null;if(ground!=null)ground=endpoint.invoke(null,ground,wall);Object product=build.invoke(null,plane,x,y,ground,wall,roof,true);if(pages.containsKey(plane+":"+position)){Renderer3DWorldChunkFrame.ChunkMesh mesh=(Renderer3DWorldChunkFrame.ChunkMesh)method(product.getClass(),"toRenderer3DWorldChunkMesh").invoke(product);prepared.addQueryCell(plane,x,y,mesh.captureWholeRamCell(pool));}base=(int[][])get(roof,"finalElevations");}
            visited++;System.out.println("WHOLE_RAM_COOK centre="+position+" recipes="+prepared.recipes()+" query="+prepared.queryCells()+" primitiveBytes="+pool.bytes());System.out.flush();
        }
        Object currentAssets=method(BrowserWholeZoneBaker.class,"externalAssets",Path.class).invoke(null,root);if(!currentAssets.toString().equals(manifest.getJSONArray("externalAssets").toString())||!sha(mini.resolve("video/Custom_Sprites.osar")).equals(get(c,"derivativeTextureHash"))||!sha(source.resolve("manifest.json")).equals(manifest.getString("sourceManifestSha256")))throw new IllegalStateException("CPU cook source/assets changed during preparation");
        pool.discardIndex();try(OutputStream stream=new BufferedOutputStream(Files.newOutputStream(out.resolve("prepared.bin")),65536)){prepared.write(stream);}
        if(Files.size(out.resolve("prepared.bin"))>WholeRamData.MAX_BYTES)throw new IllegalStateException("Prepared file exceeds1536MiB budget");
        Files.copy(raw.resolve("source.bin"),out.resolve("source.bin"));boolean complete=prepared.recipes()==445&&prepared.queryCells()==1330;
        JSONObject extension=new JSONObject().put("schemaVersion",1).put("path","prepared.bin").put("bytes",Files.size(out.resolve("prepared.bin"))).put("sha256",sha(out.resolve("prepared.bin"))).put("recipes",prepared.recipes()).put("queryCells",prepared.queryCells()).put("levels",new JSONArray(new int[]{0,1,2})).put("pageCount",pool.size()).put("modelTemplateCount",prepared.modelTemplates()).put("retainedPrimitiveBytes",pool.bytes()).put("hiddenRoofs",Config.C_HIDE_ROOFS);
        manifest.put("prepared",extension).put("complete",complete).put("cookMilliseconds",(System.nanoTime()-start)/1000000.0).put("cookVisitedPositions",visited).put("endObservedHeapUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("sourceOnlyNotice","Source and CPU recipes/query data are separate; ready requires Java validation and consumption metrics. Local scene instance work remains.");Files.write(out.resolve("manifest.json"),manifest.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println("WHOLE_RAM_COOK complete="+complete+" manifest="+out.resolve("manifest.json"));
    }
}
