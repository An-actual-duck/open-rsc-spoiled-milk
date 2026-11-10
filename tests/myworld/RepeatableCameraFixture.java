package orsc.browser;

import java.lang.reflect.Field;
import orsc.mudclient;
import orsc.enumerations.GameMode;

/** The real private camera helper against a real client object, with no UI/network. */
public final class RepeatableCameraFixture {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Field field(String name)throws Exception{Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);return f;}
    public static void main(String[] args)throws Exception {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        mudclient client=(mudclient)((sun.misc.Unsafe)singleton.get(null)).allocateInstance(mudclient.class);
        RepeatableCamera camera=new RepeatableCamera();
        field("currentViewMode").set(client,GameMode.LOGIN);
        check(camera.observe(client)[0]==0,"Login was camera-pinned");
        field("currentViewMode").set(client,GameMode.GAME);
        check(camera.observe(client)[0]==0,"Incomplete region was pinned");
        field("hasCompletedInitialRegionLoad").setBoolean(client,true);
        field("showDialogMessage").setBoolean(client,true);
        field("cameraAutoMoveX").setInt(client,42);field("cameraAutoMoveZ").setInt(client,-16);
        field("cameraAutoMoveAmountX").setInt(client,2);field("cameraAutoMoveAmountZ").setInt(client,-2);
        field("cameraAutoMoveFrameCount").setInt(client,499);field("optionCameraModeAuto").setBoolean(client,true);
        field("npcCount").setInt(client,32);field("playerCount").setInt(client,1);
        int[] receipt=camera.observe(client);
        check(receipt[0]==1&&receipt[1]==32&&receipt[2]==1&&receipt[3]==0,"Actual helper receipt differs");
        for(String name:new String[]{"cameraAutoMoveX","cameraAutoMoveZ","cameraAutoMoveAmountX","cameraAutoMoveAmountZ"})check(field(name).getInt(client)==0,"Drift source remains: "+name);
        check(!field("optionCameraModeAuto").getBoolean(client),"Automatic camera mode remains enabled");
        check(client.cameraRotation==128&&field("cameraPitch").getInt(client)==912,"Private orientation not pinned");
        orsc.graphics.three.Scene scene=new orsc.graphics.three.Scene(new orsc.graphics.two.MudClientGraphics(64,64,1),4,16,4);
        scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-160,0,client.getCameraPitch(),client.cameraRotation*4,0,1500);scene.endScene(0);
        orsc.graphics.three.Renderer3DFrame actual=scene.getRenderer3DFrame();
        check(actual.getCameraRotationX()==112&&actual.getCameraRotationY()==512,"Private source pose did not produce accepted actual projection rotation");actual.release();
        check(orsc.osConfig.C_LAST_ZOOM==75,"Private zoom not pinned");
        check(field("cameraAutoMoveFrameCount").getInt(client)==499,"Client timing/animation clock was changed");
        field("cameraAutoMoveX").setInt(client,7);field("showDialogMessage").setBoolean(client,true);int[] later=camera.observe(client);
        check(later[3]==1&&field("showDialogMessage").getBoolean(client),"Later UI was suppressed by a recurring observer");
        check(field("cameraAutoMoveX").getInt(client)==7,"Observer secretly repinned every sample instead of exposing drift");
        field("currentViewMode").set(client,GameMode.LOGIN);camera.observe(client);
        field("currentViewMode").set(client,GameMode.GAME);check(camera.observe(client)[0]==1&&field("cameraAutoMoveX").getInt(client)==0,"Relog did not renew camera pin");
        System.out.println("PASS "+checks+" actual private camera/count/login/reset checks; no GPU, server or FPS claim");
    }
}
