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
    private static Throwable failure(boolean sync){
        try{if(sync)failureSync();else failureAsync();}
        catch(Throwable error){return error;}
        throw new AssertionError("Failing native returned normally");
    }
    public static void main(String[] args){
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
        Throwable async=failure(false);report("asyncFailureClass",async.getClass().getName());report("asyncFailureMessage",String.valueOf(async.getMessage()));
        check(scalarSync(7,5)==scalar(7,5),"Caller did not continue after rejected Promise");
        Throwable sync=failure(true);report("syncFailureClass",sync.getClass().getName());report("syncFailureMessage",String.valueOf(sync.getMessage()));
        check(async.getClass().getName().equals(sync.getClass().getName()),"Async/sync JavaThrowable class differs");
        check(String.valueOf(async.getMessage()).equals(String.valueOf(sync.getMessage())),"Async/sync JavaThrowable message differs");
        check(scalarAsync(7,5)==scalar(7,5),"Caller did not continue after direct throw");
        Throwable protocol=null;try{unexpectedThenable();}catch(Throwable error){protocol=error;}
        check(protocol!=null,"Unexpected thenable was accepted");
        report("thenableFailureClass",protocol.getClass().getName());report("thenableFailureMessage",String.valueOf(protocol.getMessage()));
        check(String.valueOf(protocol.getMessage()).contains("Unexpected asynchronous result"),"Protocol error lost its message");
        check(scalarSync(Integer.MIN_VALUE,Integer.MAX_VALUE)==scalar(Integer.MIN_VALUE,Integer.MAX_VALUE),"Caller did not continue after protocol failure");
        report("checks",Integer.toString(checks));report("completed","true");
    }
}
