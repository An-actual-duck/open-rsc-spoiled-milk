package orsc.browser;

/** Executes actual observer validation and defensive ownership; no native call. */
public final class StaticSceneObserverFixture {
    private static int checks;
    public static final class Ready {public int[] work={1,1,9,8,7,6,5,4};public int[] getBrowserStaticIdentityWork(){return work;}}
    public static final class Wrong {public Object getBrowserStaticIdentityWork(){return "wrong";}}
    public static final class Short {public int[] getBrowserStaticIdentityWork(){return new int[7];}}
    public static final class Schema {public int[] getBrowserStaticIdentityWork(){return new int[8];}}
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        System.clearProperty("spoiledmilk.browserStaticSceneReuse");
        check(BrowserBootstrap.snapshotStaticSceneWork().length==0,"new Java bridge remains pending until real work");
        check(BrowserBootstrap.snapshotStaticIdentityWork(new Object()).length==0,"OFF legacy getter absence stays pending");
        Ready first=new Ready();int[] captured=BrowserBootstrap.snapshotStaticIdentityWork(first);
        check(captured!=first.work&&captured[2]==9,"observer clones actual work");captured[2]=99;
        check(BrowserBootstrap.snapshotStaticIdentityWork(first)[2]==9,"consumer cannot overwrite owner counters");
        first.work[2]=10;check(BrowserBootstrap.snapshotStaticIdentityWork(first)[2]==10,"observer sees fresh counters");
        Ready next=new Ready();next.work[2]=12;check(BrowserBootstrap.snapshotStaticIdentityWork(next)[2]==12,"replacement owner counters are current");
        for(Object malformed:new Object[]{new Wrong(),new Short(),new Schema()})try{BrowserBootstrap.snapshotStaticIdentityWork(malformed);throw new AssertionError("malformed work accepted");}catch(IllegalStateException expected){checks++;}
        System.setProperty("spoiledmilk.browserStaticSceneReuse","true");
        try{BrowserBootstrap.snapshotStaticIdentityWork(new Object());throw new AssertionError("requested absent getter accepted");}catch(NoSuchMethodException expected){checks++;}
        System.clearProperty("spoiledmilk.browserStaticSceneReuse");
        System.out.println("PASS actual static-scene observer pending/owner/clone/schema/refusal: "+checks+" checks");
    }
}
