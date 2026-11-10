package orsc.browser;

import orsc.graphics.three.Renderer3DSettings;

public final class BenchmarkCandidatePoliciesFixture {
    public static boolean disabled(){return false;}
    public static String wrong(){return "wrong";}
    public static final class FakeInstance {
        boolean enabled;
        public boolean enabled(){return enabled;}
        public String wrong(){return "wrong";}
        public boolean failed(){throw new IllegalArgumentException("fixture getter failed");}
    }
    public static final class FakeWorld {long hits;public long[] getBrowserRegionPreparationDiagnostics(){return new long[]{hits,2,1672704};}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        // Configure the exact browser ownership prerequisites BEFORE any class
        // can initialize Renderer3DSettings' immutable startup flags.
        for(String name:new String[]{"spoiledmilk.openglWorldMesh","spoiledmilk.openglWorldChunksTexturedVisible","spoiledmilk.openglWorldChunksReplacementComposite","spoiledmilk.openglWorldChunksTrustedReplacement","spoiledmilk.openglWorldChunksResidentObjects","spoiledmilk.browserOwnsWallsAndRoofs","spoiledmilk.browserModelPickBounds"})System.setProperty(name,"true");
        System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
        boolean[] missing=BenchmarkCandidatePolicies.inspect("orsc.BrowserWebGLBridge","missingGetter");check(!missing[0]&&!missing[1],"Missing getter was inferred active");
        boolean[] off=BenchmarkCandidatePolicies.inspect(BenchmarkCandidatePoliciesFixture.class.getName(),"disabled");check(off[0]&&!off[1],"Actual false getter lost availability");
        try{BenchmarkCandidatePolicies.inspect(BenchmarkCandidatePoliciesFixture.class.getName(),"wrong");throw new AssertionError("Wrong type accepted");}catch(IllegalStateException expected){}
        FakeInstance instance=new FakeInstance();
        check(!BenchmarkCandidatePolicies.inspectInstance(null,"enabled")[0],"Null instance inferred supported");
        check(!BenchmarkCandidatePolicies.inspectInstance(instance,"missing")[0],"Absent instance getter inferred supported");
        boolean[] instanceOff=BenchmarkCandidatePolicies.inspectInstance(instance,"enabled");check(instanceOff[0]&&!instanceOff[1],"False instance getter availability lost");
        instance.enabled=true;check(BenchmarkCandidatePolicies.inspectInstance(instance,"enabled")[1],"Instance result was stale");
        try{BenchmarkCandidatePolicies.inspectInstance(instance,"wrong");throw new AssertionError("Wrong instance type accepted");}catch(IllegalStateException expected){}
        try{BenchmarkCandidatePolicies.inspectInstance(instance,"failed");throw new AssertionError("Failed instance getter concealed");}catch(IllegalStateException expected){check(expected.getCause() instanceof IllegalArgumentException,"Actual getter failure cause lost");}
        BenchmarkCandidatePolicies.RegionDiagnostics diagnostics=new BenchmarkCandidatePolicies.RegionDiagnostics();
        check(diagnostics.snapshot(new Object())==null,"Absent diagnostics inferred supported");
        FakeWorld world=new FakeWorld();check(diagnostics.snapshot(world)[2]==1672704,"Exact byte receipt changed");world.hits=11;check(diagnostics.snapshot(world)[0]==11,"Cached method retained stale values");
        world.hits=-1;try{diagnostics.snapshot(world);throw new AssertionError("Negative diagnostics accepted");}catch(IllegalStateException expected){}
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        String[][] policies={{"orsc.BrowserWebGLBridge","isGeometryTransferReuseEnabled","spoiledmilk.browserGeometryTransferReuse"},{"orsc.graphics.three.World","isBrowserRegionPreparationEnabled","spoiledmilk.browserRegionPreparation"},{"orsc.mudclient","isBrowserResidentDescriptorWritingEnabled","spoiledmilk.browserResidentDescriptorWriting"},{"orsc.graphics.three.World","isBrowserHaloHeightLookupEnabled","spoiledmilk.browserHaloHeightLookup"}};
        boolean requireCandidates=args.length>0&&"--require-candidates".equals(args[0]);
        for(String[] policy:policies){boolean[] value=BenchmarkCandidatePolicies.inspect(policy[0],policy[1]);if(requireCandidates)check(value[0],"Composite actual getter absent: "+policy[1]);if(value[0])check(value[1]==Boolean.getBoolean(policy[2]),"Actual getter differs: "+policy[1]);else check(!value[1],"Unavailable candidate was inferred enabled: "+policy[1]);}
        System.setProperty("spoiledmilk.browserOwnsWallsAndRoofs","true");
        System.setProperty("spoiledmilk.browserModelPickBounds","true");
        Renderer3DSettings.setBrowserCpuDepthRequired(false);
        orsc.graphics.three.Scene scene=new orsc.graphics.three.Scene(new orsc.graphics.two.MudClientGraphics(64,64,1),4,16,4);
        boolean[] projection=BenchmarkCandidatePolicies.inspectInstance(scene,"isBrowserSceneryProjectionWorkEnabled");
        boolean requireProjection=java.util.Arrays.asList(args).contains("--require-projection");
        if(requireCandidates||requireProjection)check(projection[0],"Composite scenery projection getter absent");
        if(projection[0])check(projection[1]==(Boolean.getBoolean("spoiledmilk.browserSceneryProjectionWork")&&scene.isBrowserModelPickBoundsEnabled()),"Actual instance projection gate differs");
        else check(!projection[1],"Absent instance policy inferred enabled");
        if(projection[0]){
            Renderer3DSettings.setBrowserPresentationAvailable(false);
            check(!BenchmarkCandidatePolicies.inspectInstance(scene,"isBrowserSceneryProjectionWorkEnabled")[1],"Projection receipt ignored browser gate");
            Renderer3DSettings.setBrowserPresentationAvailable(true);Renderer3DSettings.setBrowserCpuDepthRequired(true);
            check(!BenchmarkCandidatePolicies.inspectInstance(scene,"isBrowserSceneryProjectionWorkEnabled")[1],"Projection receipt ignored CPU depth consumer");
            Renderer3DSettings.setBrowserCpuDepthRequired(false);
            check(BenchmarkCandidatePolicies.inspectInstance(scene,"isBrowserSceneryProjectionWorkEnabled")[1]==Boolean.getBoolean("spoiledmilk.browserSceneryProjectionWork"),"Projection receipt failed to restore exact eligible mode");
        }
        System.out.println("PASS actual optional static/instance getter availability, wrong type/failure and five candidate policies; CPU only");
    }
}
