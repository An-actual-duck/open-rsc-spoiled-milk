package orsc.browser;
import java.util.Properties;
import java.net.InetAddress;
public final class BootstrapFixture {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Properties valid(){
        Properties p=new Properties();
        p.setProperty("spoiledmilk.browserPublicAlpha","true");p.setProperty("spoiledmilk.browserPublicOrigin",BrowserPublicAlpha.ORIGIN);p.setProperty("spoiledmilk.browserPublicPreset","alpha-v1");p.setProperty("spoiledmilk.browserAssetRoot","/app/assets/alpha-test1/");
        String[] yes={"browserBenchmark","browserWholeZone","browserWholeRam","browserStaticSceneReuse","browserBatchedModelKernel","browserTerrainQueryIndex","browserRegionBuild","browserEarlyFaceCull","browserOwnsWallsAndRoofs","browserOverlayCoverage"};
        for(String name:yes)p.setProperty("spoiledmilk."+name,"true");
        for(String[] pair:new String[][]{{"browserCachePolicy","lru"},{"browserUnderlay","initial"},{"browserDepthSource","gpu"},{"browserLoop","modern"},{"browserSpriteCache","cached"}})p.setProperty("spoiledmilk."+pair[0],pair[1]);
        return p;
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){Properties actual=new Properties();try(java.io.FileReader r=new java.io.FileReader(args[0])){actual.load(r);}BrowserPublicAlpha.validate(actual,true);checks++;}
        Properties p=valid();BrowserPublicAlpha.validate(p,true);checks++;check(BrowserPublicAlpha.assetRoot(p).equals("/app/assets/alpha-test1/"),"versioned app root");
        for(String[] pair:new String[][]{{"browserPublicAlpha","yes"},{"browserPublicOrigin","http://play.rscspoiledmilk.com"},{"browserPublicPreset","other"},{"browserAssetRoot","/app/../../"},{"browserAssetRoot","/app/assets/alpha-test1/evil/"},{"browserWholeRam","false"},{"browserBenchmark","false"},{"browserPrivateLoopbackPort","43649"},{"browserRepeatableFixture","repeatable-v1"},{"browserBaselineReuse","true"},{"browserCloneTemplates","true"},{"browserGeometryTransferReuse","true"},{"browserProfile","true"},{"browserOverlayRectMode","packed"}}){p=valid();p.setProperty("spoiledmilk."+pair[0],pair[1]);try{BrowserPublicAlpha.validate(p,true);throw new AssertionError("Invalid public setting accepted: "+pair[0]);}catch(IllegalArgumentException expected){checks++;}}
        try{BrowserPublicAlpha.validate(valid(),false);throw new AssertionError("public software accepted");}catch(IllegalArgumentException expected){checks++;}
        check(BrowserPublicAlpha.assetRoot(new Properties()).equals("/app/"),"private app root unchanged");
        Properties badPrivate=new Properties();badPrivate.setProperty("spoiledmilk.browserAssetRoot","/app/assets/alpha-test1/");try{BrowserPublicAlpha.assetRoot(badPrivate);throw new AssertionError("private root override accepted");}catch(IllegalArgumentException expected){checks++;}
        InetAddress publicHost=InetAddress.getByAddress(new byte[]{(byte)132,(byte)145,(byte)137,(byte)142}),loop=InetAddress.getByAddress(new byte[]{127,0,0,1});
        BrowserPublicAlpha.validateConnection(publicHost,43605);checks++;
        for(InetAddress host:new InetAddress[]{publicHost,loop,InetAddress.getByAddress(new byte[]{1,2,3,4})})for(int port:new int[]{43505,43605,43629}){if(host.equals(publicHost)&&port==43605)continue;try{BrowserPublicAlpha.validateConnection(host,port);throw new AssertionError("wrong public target accepted");}catch(java.io.IOException expected){checks++;}}
        BrowserBootstrap.validatePrivateConnection(loop,43629,null,null);checks++;
        try{BrowserBootstrap.validatePrivateConnection(publicHost,43605,null,null);throw new AssertionError("private gate widened");}catch(java.io.IOException expected){checks++;}
        System.out.println("PASS "+checks+" public bootstrap preset/endpoint/basis and unchanged private connection guards; CPU only");
    }
}
