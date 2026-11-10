package orsc.browser;

import orsc.graphics.three.Renderer3DSettings;

public final class BenchmarkCandidatePoliciesFixture {
    public static boolean disabled(){return false;}
    public static String wrong(){return "wrong";}
    public static final class FakeWorld {long hits;public long[] getBrowserRegionPreparationDiagnostics(){return new long[]{hits,2,1672704};}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        boolean[] missing=BenchmarkCandidatePolicies.inspect("orsc.BrowserWebGLBridge","missingGetter");check(!missing[0]&&!missing[1],"Missing getter was inferred active");
        boolean[] off=BenchmarkCandidatePolicies.inspect(BenchmarkCandidatePoliciesFixture.class.getName(),"disabled");check(off[0]&&!off[1],"Actual false getter lost availability");
        try{BenchmarkCandidatePolicies.inspect(BenchmarkCandidatePoliciesFixture.class.getName(),"wrong");throw new AssertionError("Wrong type accepted");}catch(IllegalStateException expected){}
        BenchmarkCandidatePolicies.RegionDiagnostics diagnostics=new BenchmarkCandidatePolicies.RegionDiagnostics();
        check(diagnostics.snapshot(new Object())==null,"Absent diagnostics inferred supported");
        FakeWorld world=new FakeWorld();check(diagnostics.snapshot(world)[2]==1672704,"Exact byte receipt changed");world.hits=11;check(diagnostics.snapshot(world)[0]==11,"Cached method retained stale values");
        world.hits=-1;try{diagnostics.snapshot(world);throw new AssertionError("Negative diagnostics accepted");}catch(IllegalStateException expected){}
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        String[][] policies={{"orsc.BrowserWebGLBridge","isGeometryTransferReuseEnabled","spoiledmilk.browserGeometryTransferReuse"},{"orsc.graphics.three.World","isBrowserRegionPreparationEnabled","spoiledmilk.browserRegionPreparation"},{"orsc.mudclient","isBrowserResidentDescriptorWritingEnabled","spoiledmilk.browserResidentDescriptorWriting"}};
        for(String[] policy:policies){boolean[] value=BenchmarkCandidatePolicies.inspect(policy[0],policy[1]);check(value[0],"Composite actual getter absent: "+policy[1]);check(value[1]==Boolean.getBoolean(policy[2]),"Actual getter differs: "+policy[1]);}
        System.out.println("PASS actual optional-getter availability, false/wrong type and three composite candidate policies; CPU only");
    }
}
