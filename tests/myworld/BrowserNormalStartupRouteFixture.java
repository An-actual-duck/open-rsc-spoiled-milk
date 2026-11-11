package orsc.browser;
import java.net.InetAddress;
import java.io.File;
import java.util.ArrayList;
public final class BrowserNormalStartupRouteFixture {
    static int checks;static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        check(BrowserBootstrap.privateFixturePort(null,null)==43629,"absent route unchanged");
        for(String p:new String[]{"43639","43649"})check(BrowserBootstrap.privateFixturePort(null,p)==Integer.parseInt(p),"normal staged route");
        check(BrowserBootstrap.privateFixturePort("renderer-combat-v1","43639")==43639,"matching fixture");
        for(String[] pair:new String[][]{{null,"0"},{null,"43629"},{null,"99999"},{null,"043639"},{"renderer-combat-v1","43649"},{"unknown","43639"}}){try{BrowserBootstrap.privateFixturePort(pair[0],pair[1]);throw new AssertionError("invalid staged route accepted");}catch(IllegalArgumentException expected){checks++;}}
        InetAddress loop=InetAddress.getByAddress(new byte[]{127,0,0,1}),remote=InetAddress.getByAddress(new byte[]{1,2,3,4});
        BrowserBootstrap.validatePrivateConnection(loop,43639,null,"43639");checks++;
        for(InetAddress h:new InetAddress[]{loop,remote})try{BrowserBootstrap.validatePrivateConnection(h,43629,null,"43639");throw new AssertionError("wrong route accepted");}catch(java.io.IOException expected){checks++;}
        final boolean[] chained={false};Thread.UncaughtExceptionHandler prior=Thread.getDefaultUncaughtExceptionHandler();
        try{
            Thread.setDefaultUncaughtExceptionHandler((t,e)->chained[0]=true);
            final ArrayList<String> events=new ArrayList<String>();BrowserStartupDiagnostics d=new BrowserStartupDiagnostics((p,s)->events.add(p+":"+s));d.installChainedHandler();
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),new IllegalStateException("fixture-original-cause"));
            check(chained[0]&&events.get(0).contains("fixture-original-cause"),"prior logger chained with original exception");
            check(BrowserStartupDiagnostics.describe(null,null).contains("loop=absent"),"pre-AWT startup trace");
            Thread alive=new Thread(()->{},"fixture-client-loop");alive.setDaemon(false);check(BrowserStartupDiagnostics.describe(alive,null).contains("daemon=false"),"literal loop lifetime observed, not replaced");alive.start();alive.join();check(BrowserStartupDiagnostics.describe(alive,null).contains("TERMINATED"),"terminated loop observed");
            File log=new File(args[0]);try(java.io.FileWriter w=new java.io.FileWriter(log)){w.write("fixture log exact cause");}check(BrowserStartupDiagnostics.describe(null,log).contains("fixture log exact cause"),"bounded existing runtime log captured");
            for(int n=0;n<20;n++)d.sample("fixture");check(events.size()<=13,"startup sample cap");
        }finally{Thread.setDefaultUncaughtExceptionHandler(prior);}
        System.out.println("PASS "+checks+" normal staged Java TCP guard and bounded chained startup/lifecycle observations; no game/GPU");
    }
}
