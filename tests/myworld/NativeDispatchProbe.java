package orsc.browser;

/** Small authentic JNI contract probe. No game classes, display, server or data. */
public final class NativeDispatchProbe {
    private static native int scalarAsync(int a,int b);
    private static native int scalarSync(int a,int b);
    private static native int[] vectorAsync(int seed,int[] values);
    private static native int[] vectorSync(int seed,int[] values);
    private static native void failureAsync();
    private static native void failureSync();
    private static native void unexpectedThenable();
    private static native void report(String name,String value);
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static int scalar(int a,int b){return a*1664525+b;}
    public static void main(String[] args){
        String mode=args.length==0?"values":args[0];
        if(!"values".equals(mode)){
            report("beforeNative",mode);
            if("async-fatal".equals(mode))failureAsync();
            else if("sync-fatal".equals(mode))failureSync();
            else if("thenable-fatal".equals(mode))unexpectedThenable();
            else throw new AssertionError("Unknown native probe mode");
            report("afterNative",mode);
            throw new AssertionError("Fatal native unexpectedly returned");
        }
        int[] values={Integer.MIN_VALUE,Integer.MAX_VALUE,-1,0,1,32768,-65536,0xabcdef01};
        int state=0x325bf42b;
        for(int n=0;n<100;n++){
            state=state*1103515245+12345;int a=values[n%values.length],b=state;
            check(scalarAsync(a,b)==scalar(a,b),"Async scalar/sign mismatch");
            check(scalarSync(a,b)==scalar(a,b),"Sync scalar/sign mismatch");
            int[] async=vectorAsync(state,values),sync=vectorSync(state,values);
            check(async!=null&&sync!=null&&async.length==values.length&&sync.length==values.length,"Native int[] type/length mismatch");
            for(int i=0;i<values.length;i++){
                check(async[i]==(values[i]^state),"Async vector/sign mismatch");
                check(sync[i]==(values[i]^state),"Sync vector/sign mismatch");
            }
        }
        check(values[0]==Integer.MIN_VALUE&&values[1]==Integer.MAX_VALUE&&values[7]==0xabcdef01,"Native changed caller input");
        report("checks",Integer.toString(checks));report("completed","true");
    }
}
