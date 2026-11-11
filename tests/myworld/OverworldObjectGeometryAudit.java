package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import com.openrsc.client.entityhandling.defs.DoorDef;
import com.openrsc.data.DataOperations;
import orsc.Config;
import orsc.mudclient;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.security.MessageDigest;
import org.json.*;

/** Bounded per-definition production model decoder/export audit, no client constructor. */
public final class OverworldObjectGeometryAudit {
    private static Method method(Class<?> type,String name,Class<?>... args)throws Exception {Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static byte[] archive(Path path)throws Exception {
        byte[] file=Files.readAllBytes(path);if(file.length<6||file.length>32*1024*1024)throw new IllegalArgumentException("Archive size");
        int plain=(file[0]&255)<<16|(file[1]&255)<<8|file[2]&255;
        int packed=(file[3]&255)<<16|(file[4]&255)<<8|file[5]&255;
        if(plain<1||plain>32*1024*1024||packed!=file.length-6)throw new IllegalArgumentException("Archive header");
        if(plain==packed)return Arrays.copyOfRange(file,6,file.length);
        byte[] result=new byte[plain];Class<?> decrypter=Class.forName("com.openrsc.data.DataFileDecrypter");
        decrypter.getMethod("unpackData",byte[].class,int.class,byte[].class,int.class,int.class).invoke(null,result,plain,file,packed,6);return result;
    }
    private static String geometryHash(Renderer3DWorldChunkFrame.ChunkMesh mesh)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        Field[] fields=mesh.getClass().getDeclaredFields();Arrays.sort(fields,new Comparator<Field>(){public int compare(Field a,Field b){return a.getName().compareTo(b.getName());}});
        for(Field f:fields) {
            if(!f.getType().isArray())continue;Class<?> component=f.getType().getComponentType();
            if(!component.isPrimitive()&&!component.isEnum())continue;
            f.setAccessible(true);Object values=f.get(mesh);digest.update(f.getName().getBytes(StandardCharsets.UTF_8));
            if(values==null)continue;int length=Array.getLength(values);
            for(int i=0;i<length;i++) {
                long value=component==float.class?Float.floatToRawIntBits(Array.getFloat(values,i)):component==long.class?Array.getLong(values,i):component==int.class?Array.getInt(values,i):((Enum<?>)Array.get(values,i)).ordinal();
                for(int b=0;b<(component==long.class?8:4);b++)digest.update((byte)(value >>> (b*8)));
            }
        }
        StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format("%02x",b&255));return out.toString();
    }
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]).toRealPath();List<String> lines=Files.readAllLines(Paths.get(args[1]),StandardCharsets.UTF_8);
        int maximum=Integer.parseInt(args[2]);if(lines.size()>maximum||maximum>2048)throw new IllegalArgumentException("Definition cap");
        Config.S_WANT_CUSTOM_SPRITES=true;EntityHandler.load(true);byte[] models=archive(root.resolve("Client_Base/Cache/video/models.orsc"));
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");Field f=unsafeClass.getDeclaredField("theUnsafe");f.setAccessible(true);
        mudclient client=(mudclient)unsafeClass.getMethod("allocateInstance",Class.class).invoke(f.get(null),mudclient.class);
        Method generated=method(mudclient.class,"createGeneratedModel",String.class),normalized=method(mudclient.class,"normalizeLoadedModel",String.class,RSModel.class),prepared=method(mudclient.class,"prepareGameObjectInstanceModel",int.class,RSModel.class),animated=method(mudclient.class,"isAnimatedResidentObjectChunkModel",Renderer3DModelKind.class,int.class),metadata=method(mudclient.class,"applyRenderer3DMaterialMetadata",Renderer3DModelKind.class,int.class,RSModel.class);
        Set<String> seen=new HashSet<String>();
        for(String line:lines) {
            String[] parts=line.split(",");String family=parts[0];int id=Integer.parseInt(parts[1]);if(!seen.add(family+":"+id))throw new IllegalArgumentException("Duplicate definition");
            RSModel model;String name;String resolution="archive";boolean isAnimated=false;int sourceVertices,sourceFaces;
            if("scenery".equals(family)) {
                GameObjectDef definition=EntityHandler.getObjectDef(id);if(definition==null)throw new IllegalArgumentException("Unknown scenery "+id);
                name=definition.getObjectModel();int offset=DataOperations.getDataFileOffset(name+".ob3",models);
                if(offset==0 && !"myworld_air_glyph".equals(name) && !name.startsWith("myworld_cosmic_sparkles")) {
                    System.out.println(new JSONObject().put("family",family).put("definitionId",id).put("modelName",name).put("resolved",false).put("resolution","missing archive entry; production placeholder is not counted as resolved art").toString());continue;
                }
                if(offset==0)resolution="explicit generated model";
                model=offset==0?(RSModel)generated.invoke(client,name):new RSModel(models,offset,true);
                model=(RSModel)normalized.invoke(client,name,model);
                sourceVertices=model.vertHead;sourceFaces=model.faceHead;
                model=model.clone();model.setDiffuseLightAndColor(-50,-10,-50,48,48,true,117);model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
                model=(RSModel)prepared.invoke(client,id,model);isAnimated=((Boolean)animated.invoke(client,Renderer3DModelKind.GAME_OBJECT,id)).booleanValue();
            } else if("boundaries".equals(family)) {
                DoorDef definition=EntityHandler.getDoorDef(id);if(definition==null)throw new IllegalArgumentException("Unknown boundary "+id);
                resolution="production static boundary quad";name=definition.getName();int height=definition.getWallObjectHeight();model=new RSModel(4,1);
                int a=model.insertVertex(0,0,0),b=model.insertVertex(0,-height,0),c=model.insertVertex(128,-height,0),d=model.insertVertex(128,0,0);
                model.insertFace(4,new int[]{a,b,c,d},definition.getModelVar2(),definition.getModelVar3(),false);
                model.setRenderer3DModelKind(Renderer3DModelKind.WALL_OBJECT);model.setDiffuseLightAndColor(-50,-10,-50,60,24,false,-95);metadata.invoke(client,Renderer3DModelKind.WALL_OBJECT,id,model);
                sourceVertices=model.vertHead;sourceFaces=model.faceHead;
            } else throw new IllegalArgumentException("Unsupported family");
            Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{model},1);
            long vertices=mesh.getVertexCount(),triangles=mesh.getTriangleCount();
            JSONObject out=new JSONObject().put("family",family).put("definitionId",id).put("modelName",name).put("resolved",true).put("resolution",resolution).put("sourceVertices",sourceVertices).put("sourceFaces",sourceFaces).put("vertices",vertices).put("triangles",triangles).put("packedVertexBytes",vertices*112).put("packedIndexBytes",mesh.getIndexCount()*4L).put("geometrySha256",geometryHash(mesh)).put("primitiveMeshLowerBoundBytes",vertices*44+triangles*24).put("shadowCasters",mesh.getShadowCasterCount()).put("glowEmitters",mesh.getGlowEmitterCount()).put("animatedResident",isAnimated).put("interactionOnly",model.isInteractionOnly()).put("usedHeapBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            System.out.println(out.toString());System.out.flush();
        }
    }
}
