package orsc.browser;

import java.net.URLClassLoader;
import java.nio.file.Paths;

public final class BrowserOptimizedDefaultsFixture {
    private static int checks;
    private static void check(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    private static void clear(){System.clearProperty(BrowserOptimizedDefaults.BATCH);System.clearProperty(BrowserOptimizedDefaults.TERRAIN);for(String s:new String[]{"scene","kernel","terrain","chunk"})System.clearProperty("sentinel."+s);}
    public static void main(String[] args)throws Exception {
        for(int variant=0;variant<3;variant++)try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{Paths.get(args[variant]).toUri().toURL()},null)){
            clear();BrowserOptimizedDefaults.Capability caps=BrowserOptimizedDefaults.capabilities(loader);
            boolean expected=variant==0;
            check(caps.available(true)==expected,"batch capability variant"+variant);check(caps.available(false)==expected,"terrain capability variant"+variant);
            for(String s:new String[]{"scene","kernel","terrain","chunk"})check(System.getProperty("sentinel."+s)==null,"capability initialized "+s);
            BrowserOptimizedDefaults.Resolution r=BrowserOptimizedDefaults.apply(true,false,caps);
            check(r.requested[0]&&r.requested[1],"normal requests adopted defaults");
            check(Boolean.parseBoolean(System.getProperty(BrowserOptimizedDefaults.BATCH))==expected,"resolved batch");check(Boolean.parseBoolean(System.getProperty(BrowserOptimizedDefaults.TERRAIN))==expected,"resolved terrain");
            Class<?> terrain=Class.forName("orsc.NativeLayeredTerrainSnapshot",true,loader);
            check(((Boolean)terrain.getField("initializedValue").get(null)).booleanValue()==expected,"terrain initialized after final policy");
            clear();BrowserOptimizedDefaults.apply(false,false,caps);check(System.getProperty(BrowserOptimizedDefaults.BATCH)==null&&System.getProperty(BrowserOptimizedDefaults.TERRAIN)==null,"software leaves keys unset");
            clear();BrowserOptimizedDefaults.apply(true,true,caps);check("false".equals(System.getProperty(BrowserOptimizedDefaults.BATCH))&&"false".equals(System.getProperty(BrowserOptimizedDefaults.TERRAIN)),"benchmark omitted OFF");
            clear();System.setProperty(BrowserOptimizedDefaults.BATCH,"true");System.setProperty(BrowserOptimizedDefaults.TERRAIN,"true");
            if(expected)BrowserOptimizedDefaults.apply(true,true,caps);else{
                try{BrowserOptimizedDefaults.apply(true,true,caps);throw new AssertionError("unsupported private ON accepted");}catch(IllegalStateException refused){checks++;}
                check("true".equals(System.getProperty(BrowserOptimizedDefaults.TERRAIN)),"rejection does not partially write policy");
            }
        }
        clear();System.setProperty(BrowserOptimizedDefaults.TERRAIN,"invalid");try{BrowserOptimizedDefaults.apply(true,true,new BrowserOptimizedDefaults.Capability(){public boolean available(boolean batch){return true;}});throw new AssertionError("bad property accepted");}catch(IllegalArgumentException expected){checks++;}
        clear();BrowserOptimizedDefaults.Resolution unsupported=new BrowserOptimizedDefaults.Resolution(new boolean[]{true,true},new boolean[]{false,false});check(!BrowserOptimizedDefaults.actual(new Object(),unsupported,0)[0],"normal missing old getter quiet");
        System.out.println("PASS "+checks+" non-initializing old/new/incompatible capability, normal/software/benchmark and literal fallback checks; CPU only");
    }
}
