package com.openrsc.server.io;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.DoorDef;
import com.openrsc.client.model.Sector;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import orsc.graphics.three.World;
import orsc.graphics.three.RSModel;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.Scene;
import orsc.graphics.two.MudClientGraphics;

/** Offline maintained client/server definition, native band and roof/collision proof. */
public final class TallStoneWindowTest {
    private static int checks;
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Method method(Class<?> c,String name,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    private static String value(Element row,String key){NodeList list=row.getElementsByTagName(key);return list.getLength()==0?"0":list.item(0).getTextContent();}
    private static int number(Element row,String key){return Integer.parseInt(value(row,key));}
    private static int[] face(RSModel m,int index)throws Exception{
        int[] indices=((int[][])field(RSModel.class,"faceIndices").get(m))[index];
        int[] x=(int[])field(RSModel.class,"vertX").get(m),y=(int[])field(RSModel.class,"vertY").get(m),z=(int[])field(RSModel.class,"vertZ").get(m);
        int[] result=new int[12];for(int i=0;i<4;i++){result[i*3]=x[indices[i]];result[i*3+1]=y[indices[i]];result[i*3+2]=z[indices[i]];}return result;
    }
    private static com.openrsc.server.external.DoorDef server(int lower,int front,int back){
        com.openrsc.server.external.DoorDef d=new com.openrsc.server.external.DoorDef();
        d.name="test";d.command1="WalkTo";d.command2="Examine";d.doorType=1;d.modelVar1=275;d.modelVar2=5;d.modelVar3=5;
        d.wallLowerHeight=lower;d.wallUpperFront=front;d.wallUpperBack=back;return d;
    }
    private static void validateBoth(int lower,int front,int back,int opening,int source,int kind,int unknown,String command,boolean valid){
        boolean clientValid=true,serverValid=true;
        try{new DoorDef("test","",command,"Examine",kind,unknown,275,5,5,0,opening,source,lower,front,back);}catch(IllegalArgumentException e){clientValid=false;}
        com.openrsc.server.external.DoorDef d=server(lower,front,back);d.doorOpeningHeight=opening;d.doorSurroundFor=source;d.doorType=kind;d.unknown=unknown;d.command1=command;
        try{d.validateWallBands();}catch(IllegalArgumentException e){serverValid=false;}
        check(clientValid==valid&&serverValid==valid,"metadata validation parity lower="+lower+" front="+front+" back="+back);
    }
    private static void renderComparisons(orsc.mudclient client,Path textures,Path output)throws Exception{
        Files.createDirectories(output);
        Method load=method(orsc.mudclient.class,"loadTextureFromPixels",int.class,int[].class,int.class,int.class,int.class);
        int[] originals={3,6,13,34};
        for(int variant=0;variant<4;variant++){
            MudClientGraphics graphics=new MudClientGraphics(960,512,1);Arrays.fill(graphics.pixelData,0x8d987e);
            Scene scene=new Scene(graphics,16,4096,4);scene.setMidpoints(256,true,960,480,256,9,480);
            scene.fogLandscapeDistance=4000;scene.fogEntityDistance=4000;scene.setFrustum(0,11,7,EntityHandler.textureCount());
            field(orsc.mudclient.class,"scene").set(client,scene);
            for(int texture:new int[]{2,5,7,18,27}){
                Path p=textures.resolve(String.format("id-%05d.png",3225+texture));
                BufferedImage image=ImageIO.read(p.toFile());int w=image.getWidth(),h=image.getHeight();
                int[] pixels=image.getRGB(0,0,w,h,null,0,w);for(int i=0;i<pixels.length;i++)pixels[i]&=0xffffff;
                // Exercise the maintained client's exact histogram/palette conversion.
                load.invoke(client,texture,pixels,w,h,w/64-1);
            }
            int[] ids={7,originals[variant],216+variant,214};
            for(int i=0;i<ids.length;i++)scene.addModel(client.createWallObjectModel(i,0,ids[i],0,i));
            scene.setCamera(256,-137,0,1008,512,0,550);scene.forceLegacyWorldRasterOnce();scene.endScene(0);
            BufferedImage image=new BufferedImage(960,512,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,960,512,graphics.pixelData,0,960);
            Path p=output.resolve("window-"+(216+variant)+"-comparison.png");ImageIO.write(image,"png",p.toFile());
            System.out.println("TEXTURED COMPARISON "+p+" screen left-to-right: approved header214, tall"+(216+variant)+", original"+originals[variant]+", Highwall7");
        }
    }
    public static void main(String[]args)throws Exception{
        EntityHandler.load(true);
        NodeList rows=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(args[0])).getDocumentElement().getElementsByTagName("DoorDef");
        check(rows.getLength()>=220&&rows.getLength()==EntityHandler.doorCount(),"definition counts");
        com.openrsc.server.external.DoorDef[] definitions=new com.openrsc.server.external.DoorDef[rows.getLength()];
        for(int id=0;id<rows.getLength();id++){
            Element row=(Element)rows.item(id);DoorDef client=EntityHandler.getDoorDef(id);
            com.openrsc.server.external.DoorDef d=server(number(row,"wallLowerHeight"),number(row,"wallUpperFront"),number(row,"wallUpperBack"));
            d.name=value(row,"name");d.command1=value(row,"command1");d.command2=value(row,"command2");
            d.doorType=number(row,"doorType");d.unknown=number(row,"unknown");d.modelVar1=number(row,"modelVar1");
            d.modelVar2=number(row,"modelVar2");d.modelVar3=number(row,"modelVar3");
            d.doorOpeningHeight=number(row,"doorOpeningHeight");d.doorSurroundFor=number(row,"doorSurroundFor");definitions[id]=d;
            check(client.getWallLowerHeight()==d.getWallLowerHeight()&&client.getWallUpperFront()==d.getWallUpperFront()&&client.getWallUpperBack()==d.getWallUpperBack(),"band parity "+id);
            check(client.getWallObjectHeight()==d.modelVar1&&client.getModelVar2()==d.modelVar2&&client.getModelVar3()==d.modelVar3,"geometry parity "+id);
            if(id<216)check(d.wallLowerHeight==0&&d.wallUpperFront==0&&d.wallUpperBack==0,"legacy defaults "+id);
        }
        for(com.openrsc.server.external.DoorDef d:definitions)d.validateDoorSurround(definitions);
        validateBoth(0,0,0,0,0,1,0,"WalkTo",true);
        validateBoth(192,2,2,0,0,1,0,"WalkTo",true);
        validateBoth(192,0,0,0,0,1,0,"WalkTo",true); // texture zero is a real material.
        validateBoth(192,-32768,-1,0,0,1,0,"WalkTo",true);
        for(int lower:new int[]{-1,0,275,276,Integer.MAX_VALUE})validateBoth(lower,2,2,0,0,1,0,"WalkTo",false);
        validateBoth(192,32767,2,0,0,1,0,"WalkTo",false);
        validateBoth(192,2,-32769,0,0,1,0,"WalkTo",false);
        validateBoth(192,2,2,10,0,1,0,"WalkTo",false);
        validateBoth(192,2,2,0,7,1,0,"WalkTo",false);
        validateBoth(192,2,2,0,0,0,0,"WalkTo",false);
        validateBoth(192,2,2,0,0,1,1,"WalkTo",false);
        validateBoth(192,2,2,0,0,1,0,"Open",false);

        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        World world=(World)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,World.class);
        int side=200;int[][] elevations=new int[side][side];for(int x=0;x<side;x++)for(int z=0;z<side;z++)elevations[x][z]=17+x*3+z*5;
        Sector[] sectors=new Sector[16];for(int i=0;i<sectors.length;i++)sectors[i]=Sector.blankLoaded();
        field(World.class,"sectors").set(world,sectors);field(World.class,"tileElevationCache").set(world,elevations);
        field(World.class,"modelLandscapeGrid").set(world,new RSModel[1024]);field(World.class,"collisionFlags").set(world,new int[side][side]);
        orsc.mudclient client=(orsc.mudclient)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,orsc.mudclient.class);
        field(orsc.mudclient.class,"world").set(client,world);field(orsc.mudclient.class,"tileSize").setInt(client,128);
        Class<?> sourceType=Class.forName("orsc.graphics.three.World$TerrainModelInputSource");
        Constructor<?> sourceCtor=sourceType.getDeclaredConstructor(Sector[].class);sourceCtor.setAccessible(true);Object source=sourceCtor.newInstance((Object)sectors);
        Method add=method(World.class,"addWallSegmentInput",List.class,sourceType,int[][].class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class);
        Method insert=method(World.class,"insertWallIntoModel",int.class,RSModel.class,int.class,int.class,int.class,int.class,int.class);
        Class<?> segmentType=Class.forName("orsc.graphics.three.World$WallSegmentInput"),inputType=Class.forName("orsc.graphics.three.World$WallModelInput"),rasterType=Class.forName("orsc.graphics.three.World$NativeMinimapRaster");
        Constructor<?> inputCtor=inputType.getDeclaredConstructor(Array.newInstance(segmentType,0).getClass());inputCtor.setAccessible(true);
        Method emit=method(World.class,"emitWallProduct",inputType,boolean.class,rasterType);
        int[] originals={3,6,13,34};
        int[][] endpoints={{2,2,3,2},{2,2,2,3},{3,2,2,3},{2,2,3,3}};
        for(int id=216;id<220;id++)for(int kind=0;kind<4;kind++){
            int[] e=endpoints[kind];List<Object> bands=new ArrayList<Object>(),old=new ArrayList<Object>();
            add.invoke(world,bands,source,elevations,id+1,kind,e[0],e[1],e[2],e[1],e[0],e[3]);
            add.invoke(world,old,source,elevations,originals[id-216]+1,kind,e[0],e[1],e[2],e[1],e[0],e[3]);
            check(bands.size()==2&&old.size()==1,"two declared bands");
            int[] lower=(int[])field(segmentType,"vertexCoords").get(bands.get(0)),upper=(int[])field(segmentType,"vertexCoords").get(bands.get(1));
            check(Arrays.equals(lower,(int[])field(segmentType,"vertexCoords").get(old.get(0))),"original lower window coordinates unchanged");
            check(lower[4]==upper[1]&&lower[7]==upper[10],"shared seam on sloped endpoints");
            check(upper[1]-upper[4]==83&&upper[10]-upper[7]==83,"upper stone band height83");
            check((Integer)field(segmentType,"frontTexture").get(bands.get(0))==definitions[id].modelVar2&&(Integer)field(segmentType,"frontTexture").get(bands.get(1))==2,"static materials");
            Object array=Array.newInstance(segmentType,2);for(int i=0;i<2;i++)Array.set(array,i,bands.get(i));
            RSModel nativeModel=new RSModel(8,2);field(World.class,"modelAccumulate").set(world,nativeModel);emit.invoke(world,inputCtor.newInstance(array),false,null);
            check((Integer)field(RSModel.class,"faceHead").get(nativeModel)==2,"native emission two faces");
            check(Arrays.equals(face(nativeModel,0),lower)&&Arrays.equals(face(nativeModel,1),upper),"native emission matches immutable input");
            RSModel legacy=new RSModel(8,2);insert.invoke(world,id,legacy,e[2],e[1],e[0],-14584,e[3]);
            check(Arrays.equals(face(legacy,0),lower)&&Arrays.equals(face(legacy,1),upper),"legacy/native sloped geometry parity");
            RSModel directed=client.createWallObjectModel(2,2,id,kind,0),original=client.createWallObjectModel(2,2,originals[id-216],kind,0);
            check(Arrays.equals(face(directed,0),face(original,0)),"directed original192 window shape unchanged");
            int[] low=face(directed,0),high=face(directed,1);check(low[1]==0&&low[4]==-192&&high[1]==-192&&high[4]==-275,"directed total height275");
            Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{directed},1);
            check(mesh.getTriangleCount()==8,"renderer-v2 retains both bands and sides");
            int minY=0,maxY=Integer.MIN_VALUE;for(int i=0;i<mesh.getVertexCount();i++){minY=Math.min(minY,mesh.getVertexCoord(i*3+1));maxY=Math.max(maxY,mesh.getVertexCoord(i*3+1));}
            check(minY==-275&&maxY==0,"renderer-v2 full bounds");
            check(mesh.getShadowCasterCount()==1&&mesh.getShadowCaster(0).getHeight()==275,"renderer-v2 shadow height275");
            world.applyWallToCollisionFlags(id,2,2,kind);check(((int[][])field(World.class,"collisionFlags").get(world))[2][2]!=0,"window remains blocking");
        }
        Class<?> roofType=Class.forName("orsc.graphics.three.World$RoofElevationWorkspace");Constructor<?> roofCtor=roofType.getDeclaredConstructor(int[][].class);roofCtor.setAccessible(true);
        Method support=method(World.class,"applyWallToElevationCache",roofType,int.class,int.class,int.class,int.class,int.class),clear=method(roofType,"clearRoofMarkers");
        for(int id=216;id<220;id++){int[][] levels=new int[side][side];Object roof=roofCtor.newInstance((Object)levels);support.invoke(world,roof,id,2,2,3,2);support.invoke(world,roof,id,3,2,4,2);clear.invoke(roof);check(levels[2][2]==275&&levels[3][2]==275&&levels[4][2]==275,"roof support total275, not192 or doubled");}
        System.out.println("PASS "+checks+" tall stone window validation/native/legacy/directed/roof/collision/renderer-v2 checks");
        if(args.length==3)renderComparisons(client,Paths.get(args[1]),Paths.get(args[2]));
    }
}
