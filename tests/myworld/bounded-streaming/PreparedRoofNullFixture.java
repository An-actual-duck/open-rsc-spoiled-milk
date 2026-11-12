package orsc.graphics.three;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import orsc.Config;
import orsc.graphics.two.GraphicsController;

/** Production complete codec plus detached owned activation, using its original source-bound JAR. */
public final class PreparedRoofNullFixture {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static Field field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    public static void main(String[] args)throws Exception{
        WholeRamPrepared prepared;
        try(InputStream in=new BufferedInputStream(Files.newInputStream(Paths.get(args[0])),65536)){prepared=WholeRamPrepared.read(in);check(in.read()==-1,"complete codec consumes exact payload");}
        check(prepared.recipes()==445&&prepared.queryCells()==1330,"full useful cook roster");
        Field descriptor=field(World.PreparedActivation.class,"d");int roofReferences=0;
        for(String key:prepared.recipeKeys()){
            String[] parts=key.split(":");World.PreparedActivation recipe=prepared.recipe(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));int[] d=(int[])descriptor.get(recipe);
            check(d.length==746&&d[3]==1,"actual recipe hidden-roof declaration");
            for(int i=600;i<744;i++){check(d[i]==-1,"decoded actual roof model reference is null");roofReferences++;}
        }
        Config.S_WANT_CUSTOM_SPRITES=true;Config.S_WANT_CUSTOM_LANDSCAPE=false;Config.C_HIDE_ROOFS=true;Config.F_CACHE_DIR=args[1];
        Constructor<GraphicsController> ctor=GraphicsController.class.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);GraphicsController surface=ctor.newInstance(64,64,1);
        Scene scene=new Scene(surface,4,16,4);World world=new World(scene,surface);
        World.PreparedActivation recipe=prepared.recipe(2,13);check(recipe!=null,"profile-region recipe exists");
        recipe.activate(world);
        int nullRequests=0;for(int i=0;i<world.modelRoofGrid[0].length;i++)for(RSModel model:new RSModel[]{world.modelRoofGrid[0][i],world.modelWallGrid[1][i],world.modelRoofGrid[1][i],world.modelWallGrid[2][i],world.modelRoofGrid[2][i]}){check(model==null,"actual activated drawGame removal argument is null");nullRequests++;}
        check(nullRequests==720,"actual activation has all720 null requests");int active=field(Scene.class,"modelCount").getInt(scene);check(active>0,"active nonnull wall scene makes old null scans nonvacuous");
        RSModel[] models=(RSModel[])field(Scene.class,"models").get(scene);for(int i=0;i<active;i++)check(models[i]!=null,"actual prepared active scene prefix nonnull");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"decodedNullRoofReferences\":"+roofReferences+",\"recipes\":"+prepared.recipes()+",\"activatedNullRemovalRequests\":"+nullRequests+",\"activatedSceneModelCount\":"+active+",\"scope\":\"Actual source-bound complete codec and detached production recipe activation; no live Java array capture, browser/GPU or FPS claim.\"}");
    }
}
