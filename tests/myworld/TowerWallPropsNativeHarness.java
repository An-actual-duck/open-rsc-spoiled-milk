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
import orsc.util.GenUtil;

/** Actual native rotation, wall-context visibility and far-wall occlusion checks. */
public final class TowerWallPropsNativeHarness {
    private static final String[] MODELS = {"sm_rusted_shackles", "sm_tattered_banner", "sm_smashed_mirror"};
    private static final int[] VERTICES = {692,82,88}, FACES = {518,63,64};
    private static byte[] archive;
    private static final Method APPLY;
    private static final Field TX, TZ;
    static {
        try {
            APPLY=RSModel.class.getDeclaredMethod("resetTransformCache",int.class); APPLY.setAccessible(true);
            TX=RSModel.class.getDeclaredField("vertXTransform"); TX.setAccessible(true);
            TZ=RSModel.class.getDeclaredField("vertZTransform"); TZ.setAccessible(true);
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static RSModel model(int asset,int direction)throws Exception {
        int offset=DataOperations.getDataFileOffset(MODELS[asset]+".ob3",archive);
        if(offset==0)throw new AssertionError("Missing model");
        RSModel m=new RSModel(archive,offset,true);
        if(m.vertHead!=VERTICES[asset] || m.faceHead!=FACES[asset])throw new AssertionError("Wrong mesh count");
        m.addRotation(0,direction*32,0); // Exactly the ordinary game scenery path.
        m.setDiffuseLightAndColor(-50,-10,-50,48,48,true,117);
        return m;
    }
    private static RSModel wall(int direction) {
        RSModel wall=new RSModel(4,1);
        int a=wall.insertVertex(-128,0,-64), b=wall.insertVertex(-128,-275,-64);
        int c=wall.insertVertex(128,-275,-64), d=wall.insertVertex(128,0,-64);
        int stone=GenUtil.colorToResource(104,104,104);
        wall.insertFace(4,new int[]{a,b,c,d},stone,stone,false);
        wall.addRotation(0,direction*32,0);
        wall.setRenderer3DModelKind(Renderer3DModelKind.WALL_OBJECT);
        wall.setDiffuseLightAndColor(-50,-10,-50,60,24,false,-95);
        return wall;
    }
    private static int[] render(int asset,int direction,boolean front,boolean prop) throws Exception {
        MudClientGraphics graphics=new MudClientGraphics(512,384,1);
        Arrays.fill(graphics.pixelData,0x8d987e);
        Scene scene=new Scene(graphics,8,4096,4);
        scene.setMidpoints(192,true,512,256,192,9,256);
        scene.fogLandscapeDistance=4000; scene.fogEntityDistance=4000;
        scene.addModel(wall(direction));
        if(prop)scene.addModel(model(asset,direction));
        scene.setCamera(0,-128,0,960,(direction*128+(front?512:0))&1023,0,700);
        scene.forceLegacyWorldRasterOnce();scene.endScene(0);
        return Arrays.copyOf(graphics.pixelData,graphics.pixelData.length);
    }
    public static void main(String[] args)throws Exception {
        byte[] full=Files.readAllBytes(Paths.get(args[0]));archive=Arrays.copyOfRange(full,6,full.length);
        Files.createDirectories(Paths.get(args[1]));
        EntityHandler.load(true);Config.S_WANT_CUSTOM_SPRITES=true;
        if(EntityHandler.getModelCount()+24>=1000)throw new AssertionError("Model cache capacity");
        for(int asset=0;asset<MODELS.length;asset++) {
            if(!MODELS[asset].equals(EntityHandler.getObjectDef(1345+asset).getObjectModel()))throw new AssertionError("Registry mismatch");
            RSModel nativeModel=model(asset,0);
            int largest=0;
            for(int[] face:nativeModel.faceIndices)for(int i:face)largest=Math.max(largest,i);
            if(asset==0 && largest!=691)throw new AssertionError("Wide index decoding truncated shackles");
            for(int direction:new int[]{0,2,4,6}) {
                RSModel rotated=model(asset,direction);APPLY.invoke(rotated,7972);
                int[] x=(int[])TX.get(rotated),z=(int[])TZ.get(rotated);
                int gap=Integer.MAX_VALUE;
                for(int i=0;i<rotated.vertHead;i++) {
                    int depth=direction==0?z[i]:direction==2?x[i]:direction==4?-z[i]:-x[i];
                    gap=Math.min(gap,depth+64);
                }
                if(gap<2)throw new AssertionError("Mounted prop clips rotated wall, direction="+direction);
                for(boolean front:new boolean[]{true,false}) {
                    int[] base=render(asset,direction,front,false),shown=render(asset,direction,front,true);
                    int difference=0;
                    for(int i=0;i<base.length;i++)if(base[i]!=shown[i])difference++;
                    if(front && difference<100)throw new AssertionError("Front-mounted prop invisible: "+MODELS[asset]);
                    if(!front && difference!=0)throw new AssertionError("Prop leaks through far wall: "+MODELS[asset]+" pixels="+difference);
                    BufferedImage image=new BufferedImage(512,384,BufferedImage.TYPE_INT_RGB);
                    image.setRGB(0,0,512,384,shown,0,512);
                    ImageIO.write(image,"png",Paths.get(args[1],MODELS[asset]+"-dir"+direction+(front?"-front":"-back")+".png").toFile());
                    System.out.println(MODELS[asset]+" direction="+direction+" front="+front+" differingPixels="+difference+" wallGap="+gap);
                }
            }
        }
        System.out.println("PASS 3 native models, 4 cardinal mount rotations, 12 front views and 12 completely wall-occluded rear views; modelCount="+EntityHandler.getModelCount());
    }
}
