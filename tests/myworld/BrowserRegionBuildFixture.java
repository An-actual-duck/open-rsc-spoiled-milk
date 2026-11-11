package orsc.graphics.three;
import java.util.Arrays;
import java.util.Random;
/** Literal arithmetic/primitive staging reference, no browser or game. */
public final class BrowserRegionBuildFixture {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check "+checks); }
    public static void main(String[] args) {
        check(BrowserRegionBuild.isEnabled()==Boolean.parseBoolean(args[0]));
        Random random=new Random(0x7369676e61747572L);
        int[] edge={0,1,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,65535,65536,-65536};
        for(int trial=0;trial<200;trial++) {
            long expected=random.nextLong();
            BrowserRegionBuild.Fnv64 actual=new BrowserRegionBuild.Fnv64(expected);
            for(int n=0;n<4096;n++) {
                int word=n<edge.length?edge[n]:random.nextInt();
                expected=(expected^(word&0xffffffffL))*1099511628211L;
                actual.add(word); check(actual.value()==expected);
            }
        }
        BrowserRegionBuild.IntValues ints=new BrowserRegionBuild.IntValues();
        BrowserRegionBuild.FloatValues floats=new BrowserRegionBuild.FloatValues();
        for(int n=0;n<1048579;n++) {
            ints.add(n^0x87654321);
            floats.add(Float.intBitsToFloat(n%3==0?0x80000000:n%3==1?0x7fc12345:n));
        }
        int[] result=ints.array(); float[] fresult=floats.array();
        check(result.length==1048579&&fresult.length==result.length);
        for(int n=0;n<result.length;n++) {
            check(result[n]==(n^0x87654321));
            check(Float.floatToRawIntBits(fresult[n])==(n%3==0?0x80000000:n%3==1?0x7fc12345:n));
        }
        result[0]=0;fresult[0]=1;
        check(ints.array()[0]==0x87654321);
        check(Float.floatToRawIntBits(floats.array()[0])==0x80000000);
        System.out.println("PASS BrowserRegionBuildFixture checks="+checks+" enabled="+BrowserRegionBuild.isEnabled());
    }
}
