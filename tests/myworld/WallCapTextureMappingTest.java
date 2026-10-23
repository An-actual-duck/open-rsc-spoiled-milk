package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.DoorDef;
import com.openrsc.client.model.Sector;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import orsc.graphics.two.MudClientGraphics;

/** Tests actual producers, mesh/projected/software consumers and model lifecycle. */
public final class WallCapTextureMappingTest {
    private static int checks;
    private static void check(boolean good,String message){checks++;if(!good)throw new AssertionError(message);}
    private static void close(float actual,float expected,String message){check(Math.abs(actual-expected)<1e-5,message+": "+actual+" != "+expected);}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Method method(Class<?> c,String name,Class<?>...types)throws Exception{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object blank(Class<?> type)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");return u.getMethod("allocateInstance",Class.class).invoke(field(u,"theUnsafe").get(null),type);}
    private static RSModel quad(int bottom,int top,int height,boolean mapped,int material){
        RSModel m=new RSModel(4,1);int[] v={m.insertVertex(0,-bottom,0),m.insertVertex(0,-top,0),m.insertVertex(128,-top,0),m.insertVertex(128,-bottom,0)};
        int face=m.insertFace(4,v,material,material,false);if(mapped)m.setFaceTextureURange(face,bottom,top,height);
        m.setRenderer3DModelKind(Renderer3DModelKind.WALL_OBJECT);return m;
    }
    private static void range(RSModel m,int face,int bottom,int top,int total){
        close(m.getFaceTextureUStart(face),bottom==0?0:(float)bottom/total,"range start");
        close(m.getFaceTextureUScale(face),bottom==0?1:(float)(top-bottom)/total,"range scale");
    }
    private static void lifecycle()throws Exception{
        RSModel cap=quad(192,275,275,true,0);range(cap,0,192,275,275);
        check(cap.vertHead==4&&cap.faceHead==1,"mapping adds no geometry or ghost vertices");
        int before=cap.getRenderer3DTransformVersion();cap.setFaceTextureURange(0,100,275,275);check(before!=cap.getRenderer3DTransformVersion(),"range invalidates resident cache identity");cap.setFaceTextureURange(0,192,275,275);
        RSModel clone=cap.clone();range(clone,0,192,275,275);clone.addRotation(0,64,0);clone.setTranslate(13,7,21);range(clone,0,192,275,275);
        Constructor<RSModel> mergeConstructor=RSModel.class.getDeclaredConstructor(RSModel[].class,int.class);mergeConstructor.setAccessible(true);RSModel merged=mergeConstructor.newInstance(new RSModel[]{cap,quad(0,275,275,false,0)},2);range(merged,0,192,275,275);range(merged,1,0,275,275);
        RSModel[] split=cap.divideModelByGrid(0,1,1536,-120,1,100,1536,true,0);range(split[0],0,192,275,275);
        // Capacity growth preserves prior ranges and defaults new faces to legacy mapping.
        int[] first=cap.faceIndices[0];for(int i=0;i<10;i++)cap.insertFace(4,first,0,0,false);
        range(cap,0,192,275,275);range(cap,10,0,275,275);
        cap.resetFaceVertHead(1);int[] newVerts={cap.insertVertex(0,0,0),cap.insertVertex(0,-275,0),cap.insertVertex(128,-275,0),cap.insertVertex(128,0,0)};
        cap.insertFace(4,newVerts,0,0,false);range(cap,0,0,275,275);
        for(int[] bad:new int[][]{{-1,275,275},{192,192,275},{0,276,275},{0,0,0}}){boolean refused=false;try{cap.setFaceTextureURange(0,bad[0],bad[1],bad[2]);}catch(IllegalArgumentException e){refused=true;}check(refused,"invalid range refused");}
    }
    private static void residentAndProjected()throws Exception{
        RSModel cap=quad(192,275,275,true,0);
        Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{cap},1);
        for(int i=0;i<mesh.getVertexCount();i++)close(mesh.getVertexTextureU(i),-mesh.getVertexCoord(i*3+1)/275.0f,"resident full-wall phase");
        RSModel old=quad(192,275,275,false,0);
        Renderer3DWorldChunkFrame.ChunkMesh previous=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{old},1);
        check(mesh.getSignature()!=previous.getSignature(),"UV correction changes actual resident mesh signature");
    }
    private static void projected()throws Exception{
        RSModel m=quad(192,275,275,true,0);int[] ys=(int[])field(RSModel.class,"vertY").get(m);
        for(int i=0;i<m.vertHead;i++){m.vertXRot[i]=m.vertX[i];m.vertYRot[i]=ys[i];m.vertZRot[i]=550;}
        Constructor<?> c=Renderer3DFrame.FaceCommand.class.getDeclaredConstructor(int.class);c.setAccessible(true);Renderer3DFrame.FaceCommand face=(Renderer3DFrame.FaceCommand)c.newInstance(4);
        method(Renderer3DFrame.FaceCommand.class,"reset",Renderer3DModelKind.class,int.class,int.class,int.class,int.class,int.class,int.class,RSModel.class,int[].class,int[].class,int[].class).invoke(face,Renderer3DModelKind.WALL_OBJECT,0,0,0,0,0,0,m,m.faceIndices[0],new int[4],new int[4]);
        for(int i=0;i<4;i++)close(face.getTextureU()[i],-ys[m.faceIndices[0][i]]/275.0f,"projected original basis phase");
        int[] cx={0,0,64,64},cy={-230,-275,-275,-230},cz={550,550,550,550};
        method(Renderer3DFrame.FaceCommand.class,"setLegacyClippedGeometry",int[].class,int[].class,int[].class,int[].class,int[].class,int[].class,int[].class,int.class).invoke(face,cx,cy,cz,new int[4],new int[4],new int[4],new int[4],4);
        for(int i=0;i<4;i++)close(face.getRenderTextureU()[i],-cy[i]/275.0f,"clipped coordinates keep original full-wall basis");
        // Reuse command for an ordinary face: no stale mapping or clipped coordinates.
        RSModel full=quad(0,275,275,false,0);int[] fy=(int[])field(RSModel.class,"vertY").get(full);
        for(int i=0;i<4;i++){full.vertXRot[i]=full.vertX[i];full.vertYRot[i]=fy[i];full.vertZRot[i]=550;}
        method(Renderer3DFrame.FaceCommand.class,"reset",Renderer3DModelKind.class,int.class,int.class,int.class,int.class,int.class,int.class,RSModel.class,int[].class,int[].class,int[].class).invoke(face,Renderer3DModelKind.WALL_OBJECT,0,0,0,0,0,0,full,full.faceIndices[0],new int[4],new int[4]);
        for(int i=0;i<4;i++)close(face.getRenderTextureU()[i],-fy[full.faceIndices[0][i]]/275.0f,"pooled default face reset");
    }
    private static orsc.mudclient producers()throws Exception{
        World world=(World)blank(World.class);int side=200;int[][] heights=new int[side][side];for(int x=0;x<side;x++)for(int z=0;z<side;z++)heights[x][z]=17+x*3+z*5;
        Sector[] sectors=new Sector[16];for(int i=0;i<16;i++)sectors[i]=Sector.blankLoaded();field(World.class,"sectors").set(world,sectors);field(World.class,"tileElevationCache").set(world,heights);field(World.class,"modelLandscapeGrid").set(world,new RSModel[1024]);field(World.class,"collisionFlags").set(world,new int[side][side]);
        orsc.mudclient client=(orsc.mudclient)blank(orsc.mudclient.class);field(orsc.mudclient.class,"world").set(client,world);field(orsc.mudclient.class,"tileSize").setInt(client,128);
        Class<?> src=Class.forName("orsc.graphics.three.World$TerrainModelInputSource"),segment=Class.forName("orsc.graphics.three.World$WallSegmentInput"),input=Class.forName("orsc.graphics.three.World$WallModelInput"),raster=Class.forName("orsc.graphics.three.World$NativeMinimapRaster"),builder=Class.forName("orsc.graphics.three.World$WorldGpuChunkMeshBuilder");
        Constructor<?> sc=src.getDeclaredConstructor(Sector[].class);sc.setAccessible(true);Object source=sc.newInstance((Object)sectors);
        Constructor<?> ic=input.getDeclaredConstructor(Array.newInstance(segment,0).getClass());ic.setAccessible(true);
        Constructor<?> bc=builder.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,boolean.class);bc.setAccessible(true);
        Method add=method(World.class,"addWallSegmentInput",List.class,src,int[][].class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class),emit=method(World.class,"emitWallProduct",input,boolean.class,raster),insert=method(World.class,"insertWallIntoModel",int.class,RSModel.class,int.class,int.class,int.class,int.class,int.class);
        @SuppressWarnings("unchecked") List<DoorDef> defs=(List<DoorDef>)field(EntityHandler.class,"doors").get(null);
        int synthetic=defs.size();defs.add(new DoorDef("generic","","WalkTo","Examine",1,0,360,47,47,synthetic,0,0,123,9,9));
        try{
            for(int id:new int[]{7,214,215,216,217,218,219,synthetic})for(int direction=0;direction<4;direction++){
                DoorDef d=EntityHandler.getDoorDef(id);int[][] endpoints={{2,2,3,2},{2,2,2,3},{3,2,2,3},{2,2,3,3}};int[] e=endpoints[direction];List<Object> segments=new ArrayList<Object>();
                add.invoke(world,segments,source,heights,id+1,direction,e[0],e[1],e[2],e[1],e[0],e[3]);Object array=Array.newInstance(segment,segments.size());for(int i=0;i<segments.size();i++)Array.set(array,i,segments.get(i));Object wallInput=ic.newInstance(array);
                RSModel nativeModel=new RSModel(8,2);field(World.class,"modelAccumulate").set(world,nativeModel);emit.invoke(world,wallInput,false,null);
                RSModel legacy=new RSModel(8,2);insert.invoke(world,id,legacy,e[2],e[1],e[0],-14584,e[3]);RSModel directed=client.createWallObjectModel(2,2,id,direction,0);
                for(int face=0;face<segments.size();face++){
                    int bottom=face==0?d.getDoorOpeningHeight():d.getWallLowerHeight(),top=face==0&&d.getWallLowerHeight()>0?d.getWallLowerHeight():d.getWallObjectHeight();
                    for(RSModel m:new RSModel[]{nativeModel,legacy,directed})range(m,face,bottom,top,d.getWallObjectHeight());
                }
                Object gpuBuilder=bc.newInstance(0,0,0,0,0,false);method(World.class,"addWallGpuChunkMesh",builder,input,int.class,int.class).invoke(null,gpuBuilder,wallInput,0,0);Object gpu=method(builder,"build").invoke(gpuBuilder);
                float[] u=(float[])field(gpu.getClass(),"vertexTextureU").get(gpu);int k=0;
                for(int f=0;f<segments.size();f++){
                    int bottom=f==0?d.getDoorOpeningHeight():d.getWallLowerHeight(),top=f==0&&d.getWallLowerHeight()>0?d.getWallLowerHeight():d.getWallObjectHeight();float start=bottom==0?0:(float)bottom/d.getWallObjectHeight(),end=bottom==0?1:(float)top/d.getWallObjectHeight();
                    for(float expected:new float[]{start,end,end,start,end,start})close(u[k++],expected,"native cached GPU range actual slope/direction");
                }
                check(k==u.length,"all native GPU UV consumed");
                if(id==214){
                    long signature=(Long)field(gpu.getClass(),"signature").get(gpu);
                    method(segment,"setTextureRange",int.class,int.class,int.class).invoke(segments.get(0),0,83,83);
                    Object legacyBuilder=bc.newInstance(0,0,0,0,0,false);method(World.class,"addWallGpuChunkMesh",builder,input,int.class,int.class).invoke(null,legacyBuilder,wallInput,0,0);
                    Object changed=method(builder,"build").invoke(legacyBuilder);
                    check(signature!=(Long)field(changed.getClass(),"signature").get(changed),"cached wall mesh signature includes mapping without geometry changes");
                }
            }
        }finally{defs.remove(defs.size()-1);}
        return client;
    }
    private static int[] raster(RSModel m,int angle){
        MudClientGraphics g=new MudClientGraphics(512,512,1);Arrays.fill(g.pixelData,0x8d987e);Scene s=new Scene(g,8,2048,4);s.setMidpoints(256,true,512,256,256,9,256);s.fogLandscapeDistance=4000;s.fogEntityDistance=4000;s.setFrustum(0,11,7,1);
        int[] palette=new int[256];palette[0]=0xff00ff;byte[] indices=new byte[64*64];for(int i=1;i<256;i++)palette[i]=((i*47&255)<<16)|((i*83&255)<<8)|(i*131&255);for(int y=0;y<64;y++)for(int x=0;x<64;x++)indices[y*64+x]=(byte)(1+x/4);
        s.loadTexture(0,palette,0,indices);s.addModel(m);s.setCamera(64,-137,0,1008,angle,0,550);s.forceLegacyWorldRasterOnce();s.endScene(0);return g.pixelData;
    }
    private static void software()throws Exception{
        for(int angle:new int[]{512,640,384}){
            int[] whole=raster(quad(0,275,275,false,0),angle),crop=raster(quad(192,275,275,true,0),angle),wrong=raster(quad(192,275,275,false,0),angle);int compared=0,different=0,wrongDifferent=0;
            for(int y=2;y<510;y++)for(int x=2;x<510;x++){int i=y*512+x;if(crop[i]==0x8d987e||whole[i]==0x8d987e)continue;if(crop[i-1]==0x8d987e||crop[i+1]==0x8d987e||crop[i-512]==0x8d987e||crop[i+512]==0x8d987e)continue;compared++;if(crop[i]!=whole[i])different++;if(wrong[i]!=whole[i])wrongDifferent++;}
            check(compared>100,"software sample coverage");check(different<compared*.035,"software cap matches whole-wall crop: "+different+"/"+compared);check(wrongDifferent>compared*.4,"old stretched cap is distinguishable");System.out.println("SOFTWARE phase angle="+angle+" mismatch="+different+"/"+compared+" stretched="+wrongDifferent);
        }
    }
    private static void diagnostics(orsc.mudclient client,Path textures,Path output)throws Exception{
        Files.createDirectories(output);Method load=method(orsc.mudclient.class,"loadTextureFromPixels",int.class,int[].class,int.class,int.class,int.class);int[] originals={3,6,13,34};
        for(int variant=0;variant<4;variant++)for(boolean corrected:new boolean[]{false,true}){
            MudClientGraphics g=new MudClientGraphics(960,512,1);Arrays.fill(g.pixelData,0x8d987e);Scene s=new Scene(g,16,4096,4);s.setMidpoints(256,true,960,480,256,9,480);s.fogLandscapeDistance=4000;s.fogEntityDistance=4000;s.setFrustum(0,11,7,EntityHandler.textureCount());field(orsc.mudclient.class,"scene").set(client,s);
            for(int texture:new int[]{2,5,7,18,27}){BufferedImage image=ImageIO.read(textures.resolve(String.format("id-%05d.png",3225+texture)).toFile());int w=image.getWidth(),h=image.getHeight();int[] pixels=image.getRGB(0,0,w,h,null,0,w);for(int i=0;i<pixels.length;i++)pixels[i]&=0xffffff;load.invoke(client,texture,pixels,w,h,w/64-1);}
            int[] ids={7,originals[variant],216+variant,214};for(int i=0;i<ids.length;i++){RSModel m=client.createWallObjectModel(i,0,ids[i],0,i);if(!corrected)for(int f=0;f<m.faceHead;f++)m.setFaceTextureURange(f,0,275,275);s.addModel(m);}
            s.setCamera(256,-137,0,1008,512,0,550);s.forceLegacyWorldRasterOnce();s.endScene(0);BufferedImage image=new BufferedImage(960,512,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,960,512,g.pixelData,0,960);ImageIO.write(image,"png",output.resolve("window-"+(216+variant)+(corrected?"-after":"-before")+".png").toFile());
        }
        System.out.println("Actual stone before/after: "+output+"; screen left-to-right header, tall window, original window, Highwall");
    }
    public static void main(String[]args)throws Exception{
        EntityHandler.load(true);lifecycle();residentAndProjected();projected();orsc.mudclient client=producers();software();if(args.length==2)diagnostics(client,Paths.get(args[0]),Paths.get(args[1]));System.out.println("PASS "+checks+" actual wall-cap texture mapping checks");
    }
}
