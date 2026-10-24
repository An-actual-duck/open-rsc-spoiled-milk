package orsc.graphics.three;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.data.DataOperations;
import orsc.Config;
import orsc.graphics.two.MudClientGraphics;

/** Actual native decoder, mounting rotation and opaque stone-wall occlusion proof. */
public final class PointedWindowNativeHarness {
    private static final String MODEL = "sm_pointed_window";
    private static byte[] archive;
    private static int[] stonePixels;
    private static int stoneWidth, stoneHeight;
    private static final Method APPLY, LOAD_TEXTURE;
    private static final Field TX, TZ, CLIENT_SCENE;
    static {
        try {
            APPLY=RSModel.class.getDeclaredMethod("resetTransformCache",int.class); APPLY.setAccessible(true);
            TX=RSModel.class.getDeclaredField("vertXTransform"); TX.setAccessible(true);
            TZ=RSModel.class.getDeclaredField("vertZTransform"); TZ.setAccessible(true);
            CLIENT_SCENE=orsc.mudclient.class.getDeclaredField("scene"); CLIENT_SCENE.setAccessible(true);
            LOAD_TEXTURE=orsc.mudclient.class.getDeclaredMethod("loadTextureFromPixels",int.class,int[].class,int.class,int.class,int.class);
            LOAD_TEXTURE.setAccessible(true);
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static Object blank(Class<?> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");
        Field instance=unsafe.getDeclaredField("theUnsafe");instance.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(instance.get(null),type);
    }
    private static RSModel model(int direction)throws Exception {
        int offset=DataOperations.getDataFileOffset(MODEL+".ob3",archive);
        if(offset==0)throw new AssertionError("Missing pointed-window model");
        RSModel m=new RSModel(archive,offset,true);
        m.addRotation(0,direction*32,0);
        m.setDiffuseLightAndColor(-50,-10,-50,48,48,true,117);
        return m;
    }
    private static RSModel wall(int direction) {
        RSModel wall=new RSModel(12,3);
        // Ordinary 128-unit spans preserve the actual full-wall stone scale.
        for(int x=-192;x<192;x+=128){
            int a=wall.insertVertex(x,0,-64), b=wall.insertVertex(x,-275,-64);
            int c=wall.insertVertex(x+128,-275,-64),d=wall.insertVertex(x+128,0,-64);
            wall.insertFace(4,new int[]{a,b,c,d},2,2,false);
        }
        wall.addRotation(0,direction*32,0);
        wall.setRenderer3DModelKind(Renderer3DModelKind.WALL_OBJECT);
        wall.setDiffuseLightAndColor(-50,-10,-50,60,24,false,-95);
        return wall;
    }
    private static int[] render(int direction,boolean front,boolean prop)throws Exception {
        MudClientGraphics graphics=new MudClientGraphics(512,384,1);
        Arrays.fill(graphics.pixelData,0x8d987e);
        Scene scene=new Scene(graphics,8,8192,4);
        scene.setMidpoints(192,true,512,256,192,9,256);
        scene.fogLandscapeDistance=4000;scene.fogEntityDistance=4000;
        scene.setFrustum(0,11,7,EntityHandler.textureCount());
        Object client=blank(orsc.mudclient.class);CLIENT_SCENE.set(client,scene);
        LOAD_TEXTURE.invoke(client,2,stonePixels,stoneWidth,stoneHeight,stoneWidth/64-1);
        scene.addModel(wall(direction));
        if(prop)scene.addModel(model(direction));
        scene.setCamera(0,-128,0,960,(direction*128+(front?512:0))&1023,0,700);
        scene.forceLegacyWorldRasterOnce();scene.endScene(0);
        return Arrays.copyOf(graphics.pixelData,graphics.pixelData.length);
    }
    public static void main(String[] args)throws Exception {
        byte[] full=Files.readAllBytes(Paths.get(args[0]));archive=Arrays.copyOfRange(full,6,full.length);
        Files.createDirectories(Paths.get(args[1]));
        BufferedImage stone=ImageIO.read(Paths.get(args[2]).toFile());
        stoneWidth=stone.getWidth();stoneHeight=stone.getHeight();
        stonePixels=stone.getRGB(0,0,stoneWidth,stoneHeight,null,0,stoneWidth);
        for(int i=0;i<stonePixels.length;i++)stonePixels[i]&=0xffffff;
        EntityHandler.load(true);Config.S_WANT_CUSTOM_SPRITES=true;
        if(EntityHandler.getModelCount()+24>=1000)throw new AssertionError("Model cache capacity");
        if(!MODEL.equals(EntityHandler.getObjectDef(1349).getObjectModel()))throw new AssertionError("Registry mismatch");
        RSModel decoded=model(0);
        for(int direction:new int[]{0,2,4,6}){
            RSModel rotated=model(direction);APPLY.invoke(rotated,7972);
            int[] x=(int[])TX.get(rotated),z=(int[])TZ.get(rotated);int gap=Integer.MAX_VALUE;
            for(int i=0;i<rotated.vertHead;i++){
                int depth=direction==0?z[i]:direction==2?x[i]:direction==4?-z[i]:-x[i];
                gap=Math.min(gap,depth+64);
            }
            if(gap<2)throw new AssertionError("Pointed window clips wall: "+direction);
            for(boolean front:new boolean[]{true,false}){
                int[] base=render(direction,front,false),shown=render(direction,front,true);int difference=0,readable=0,peak=0;
                for(int i=0;i<base.length;i++)if(base[i]!=shown[i]){
                    difference++;int value=(shown[i]>>16)&255;peak=Math.max(peak,value);
                    if(value>=40)readable++;
                }
                BufferedImage image=new BufferedImage(512,384,BufferedImage.TYPE_INT_RGB);
                image.setRGB(0,0,512,384,shown,0,512);
                ImageIO.write(image,"png",Paths.get(args[1],MODEL+"-dir"+direction+(front?"-front":"-back")+".png").toFile());
                image.setRGB(0,0,512,384,base,0,512);
                ImageIO.write(image,"png",Paths.get(args[1],MODEL+"-dir"+direction+(front?"-front":"-back")+"-wall-only.png").toFile());
                System.out.println("direction="+direction+" front="+front+" differingPixels="+difference+" wallGap="+gap+" greyPixelsAtLeast40="+readable+" peakRed="+peak);
                if(front&&difference<100)throw new AssertionError("Front-mounted window invisible");
                if(front&&readable<100)throw new AssertionError("Stone frame lost readable gray highlights");
                if(!front&&difference!=0)throw new AssertionError("Window leaks through solid wall: "+difference);
            }
        }
        System.out.println("PASS pointed window native decoder/registry: "+decoded.vertHead+" vertices/"+decoded.faceHead
            +" faces; four front views and four completely wall-occluded rear views; modelCount="+EntityHandler.getModelCount());
    }
}
