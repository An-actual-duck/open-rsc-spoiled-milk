package orsc;
import orsc.graphics.three.*;
/** Startup/refusal policy only; never constructs a client, Scene or GPU backend. */
public final class BrowserWholeZonePolicyFixture {
    private static int checks,calls;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception{
        boolean expected=Boolean.parseBoolean(args[0]);check(WholeOverworldZoneRegistry.isEnabled()==expected);check(BrowserWholeOverworldZone.isEnabled()==expected);check(BrowserWebGLBridge.isWholeZoneEnabled()==expected);
        WholeOverworldZoneRegistry.clear();check(WholeOverworldZoneRegistry.resolveStatic(0,1,2,3,4,5)==null);
        try{WholeOverworldZoneRegistry.install((plane,x,y,key,bx,bz)->{calls++;check(plane==0&&x==1&&y==2&&key==3&&bx==4&&bz==5);return null;});check(expected);}catch(IllegalStateException refusal){check(!expected);}
        check(WholeOverworldZoneRegistry.resolveStatic(0,1,2,3,4,5)==null);check(calls==(expected?1:0));WholeOverworldZoneRegistry.clear();check(WholeOverworldZoneRegistry.resolveStatic(0,1,2,3,4,5)==null);check(calls==(expected?1:0));
        check(BrowserWebGLBridge.getWholeZoneWork().length==0);check(BrowserWebGLBridge.getWholeZoneWork()!=BrowserWebGLBridge.getWholeZoneWork());
        Class.forName("orsc.BrowserWholeZoneBaker",false,BrowserWholeZonePolicyFixture.class.getClassLoader());
        System.out.println("PASS "+checks+" whole-zone policy/registry checks enabled="+expected+"; no GPU/game/filesystem catalog load");
    }
}
