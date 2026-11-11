package orsc.browser;

import java.lang.reflect.InvocationTargetException;

/** Executes the actual optional observer path through pre-Scene and ready states. */
public final class BrowserBootstrapStartupFixture {
    private static int checks;
    public static final class ReadyScene {
        public final int[] work=new int[14];
        public int reads;
        public int[] getBrowserBatchedModelKernelWork(){reads++;return work;}
    }
    public static final class WrongScene {
        public Object getBrowserBatchedModelKernelWork(){return "wrong";}
    }
    public static final class ShortScene {
        public int[] getBrowserBatchedModelKernelWork(){return new int[13];}
    }
    public static final class NullWorkScene {
        public int[] getBrowserBatchedModelKernelWork(){return null;}
    }
    public static final class FailedScene {
        public int[] getBrowserBatchedModelKernelWork(){throw new IllegalStateException("actual fixture failure");}
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        // Before login, mudclient exists but Scene does not. The observer must
        // keep reporting LOGIN without reporting a fabricated work receipt.
        for(int tick=0;tick<4;tick++) {
            check(BrowserBootstrap.snapshotBatchedModelWork(null,true)==null,"Requested startup path stopped before Scene existed");
            check(BrowserBootstrap.snapshotBatchedModelWork(null,false)==null,"Disabled startup fabricated work");
            boolean[] policy=BenchmarkCandidatePolicies.inspectInstance(null,"isBrowserBatchedModelKernelEnabled");
            check(!policy[0]&&!policy[1],"Missing Scene falsely reported available or enabled");
        }
        check(BrowserBootstrap.snapshotBatchedModelWork(new Object(),false)==null,"OFF reflected unavailable getter");
        ReadyScene first=new ReadyScene();first.work[0]=17;
        check(BrowserBootstrap.snapshotBatchedModelWork(first,true)==first.work,"Ready observer lost actual counters");
        check(first.reads==1,"Ready getter did not execute exactly once");
        first.work[0]=18;
        check(BrowserBootstrap.snapshotBatchedModelWork(first,true)[0]==18,"Observer retained stale counters");
        check(BrowserBootstrap.snapshotBatchedModelWork(null,true)==null,"Transient missing Scene stopped observer");
        ReadyScene replacement=new ReadyScene();replacement.work[0]=29;
        check(BrowserBootstrap.snapshotBatchedModelWork(replacement,true)[0]==29,"Replacement Scene was not observed");
        try {BrowserBootstrap.snapshotBatchedModelWork(new Object(),true);throw new AssertionError("Old JAR getter absence concealed");}
        catch(NoSuchMethodException expected){checks++;}
        for(Object bad:new Object[]{new WrongScene(),new ShortScene(),new NullWorkScene()}) {
            try {BrowserBootstrap.snapshotBatchedModelWork(bad,true);throw new AssertionError("Invalid work getter accepted");}
            catch(IllegalStateException expected){checks++;}
        }
        try {BrowserBootstrap.snapshotBatchedModelWork(new FailedScene(),true);throw new AssertionError("Getter failure concealed");}
        catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"Original failure cause lost");}
        System.out.println("PASS bootstrap pre-Scene startup, disabled, ready, replacement and malformed getter paths: "+checks+" checks; CPU only");
    }
}
