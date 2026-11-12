package orsc.browser;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;

/** Explicit public preset authority before any client/renderer static initialization. */
final class BrowserPublicAlpha {
    static final String ORIGIN="https://play.rscspoiledmilk.com", PRESET="alpha-v1";
    static final String ADDRESS="132.145.137.142";
    static final int PORT=43605;
    private BrowserPublicAlpha() { }

    static boolean enabled(Properties properties) {
        String value=properties.getProperty("spoiledmilk.browserPublicAlpha");
        if(value==null||"false".equals(value))return false;
        if(!"true".equals(value))throw new IllegalArgumentException("Invalid public alpha selection");
        return true;
    }
    static void validate(Properties p,boolean webgl) {
        if(!enabled(p))return;
        if(!webgl||!ORIGIN.equals(p.getProperty("spoiledmilk.browserPublicOrigin"))||!PRESET.equals(p.getProperty("spoiledmilk.browserPublicPreset")))throw new IllegalArgumentException("Public alpha origin/preset mismatch");
        assetRoot(p);
        String[][] required={{"browserBenchmark","true"},{"browserWholeZone","true"},{"browserWholeRam","true"},{"browserStaticSceneReuse","true"},{"browserBatchedModelKernel","true"},{"browserTerrainQueryIndex","true"},{"browserRegionBuild","true"},{"browserCachePolicy","lru"},{"browserUnderlay","initial"},{"browserDepthSource","gpu"},{"browserLoop","modern"},{"browserEarlyFaceCull","true"},{"browserOwnsWallsAndRoofs","true"},{"browserSpriteCache","cached"},{"browserOverlayCoverage","true"}};
        for(String[] pair:required)if(!pair[1].equals(p.getProperty("spoiledmilk."+pair[0])))throw new IllegalArgumentException("Public alpha setting mismatch: "+pair[0]);
        String[] forbidden={"browserRepeatableFixture","browserPrivateLoopbackPort","browserProfile","browserInputDiagnostics","browserLoadProfile","browserBaselineReuse","browserCloneTemplates","browserGeometryTransferReuse","browserRegionPreparation","browserResidentDescriptorWriting","browserHaloHeightLookup","browserSceneryProjectionWork","browserPrimitiveChunkCoverage","browserOverlayDeltaTransport","browserOverlayJournalObserve"};
        for(String name:forbidden){String value=p.getProperty("spoiledmilk."+name);if(value!=null&&!"false".equals(value))throw new IllegalArgumentException("Public alpha forbids test option: "+name);}
        String rect=p.getProperty("spoiledmilk.browserOverlayRectMode","off");if(!"off".equals(rect))throw new IllegalArgumentException("Public alpha overlay mode");
    }
    static String assetRoot(Properties p) {
        String root=p.getProperty("spoiledmilk.browserAssetRoot");
        if(!enabled(p)){
            if(root!=null)throw new IllegalArgumentException("Private asset root cannot be overridden");
            return "/app/";
        }
        if(root==null||!root.matches("/app/assets/[a-z0-9][a-z0-9-]{6,63}/"))throw new IllegalArgumentException("Public alpha asset root");
        return root;
    }
    static void validateConnection(InetAddress host,int port)throws IOException {
        if(host==null||!ADDRESS.equals(host.getHostAddress())||port!=PORT)throw new IOException("Public alpha permits only its game endpoint");
    }
}
