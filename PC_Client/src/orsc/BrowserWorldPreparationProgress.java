package orsc;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/** Passive public initial-world observations. Never authorizes data or changes errors. */
final class BrowserWorldPreparationProgress {
    interface Sink { void report(int[] value); }
    interface Clock { long nanoTime(); }
    static final int METADATA=1, SOURCE_READ=2, SOURCE_CHECK=3, PREPARED_READ=4, VERIFY=5, VERIFIED=6, FAILED=7;
    private static final AtomicInteger OWNERS=new AtomicInteger();
    private final Sink sink;
    private final Clock clock;
    private final int owner;
    private final long started;
    private int sequence,stage,total,completed,lastCompleted;
    private long lastTime;
    private boolean failed;
    BrowserWorldPreparationProgress(Sink sink) { this(sink,System::nanoTime); }
    BrowserWorldPreparationProgress(Sink sink,Clock clock) {
        this.sink=sink;this.clock=clock;owner=OWNERS.incrementAndGet();started=clock.nanoTime();lastTime=started;
    }
    synchronized void stage(int next,int count) {
        if(sink==null||failed)return;
        stage=next;total=count;completed=lastCompleted=0;emit(0);
    }
    synchronized void advance(int count) {
        if(sink==null||failed)return;
        completed=Math.min(total,Math.max(completed,count));
        int step=stage==SOURCE_CHECK?64:1048576;
        long now=clock.nanoTime();
        if(completed==total||completed-lastCompleted>=step||now-lastTime>=250000000L)emit(0);
    }
    synchronized void fail(int code) {
        if(sink==null||failed)return;failed=true;stage=FAILED;completed=total=0;emit(code);
    }
    private void emit(int error) {
        long now=clock.nanoTime();lastCompleted=completed;lastTime=now;
        int elapsed=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,(now-started)/1000000L));
        try { sink.report(new int[]{1,owner,++sequence,stage,completed,total,elapsed,error}); }
        catch(Throwable ignored) { /* Observations cannot replace original read/validation outcomes. */ }
    }
    InputStream reading(InputStream source,int length) {
        if(sink==null)return source;
        return new FilterInputStream(source) {
            private long count,nextClock=65536;
            private void observed(int amount) {
                if(amount<=0)return;count+=amount;
                // This wrapper sits BELOW buffering. Even an unusual single-byte
                // caller checks a clock only after another 64 KiB, never per byte.
                if(count>=nextClock||count>=length){nextClock=count+65536;advance((int)Math.min(length,count));}
            }
            @Override public int read()throws IOException {int n=in.read();if(n>=0)observed(1);return n;}
            @Override public int read(byte[] b)throws IOException{return read(b,0,b.length);}
            @Override public int read(byte[] b,int off,int len)throws IOException {
                int n=in.read(b,off,Math.min(len,65536));observed(n);return n;
            }
            @Override public long skip(long amount)throws IOException {
                long n=in.skip(amount);if(n>0){count+=n;if(count>=nextClock||count>=length){nextClock=count+65536;advance((int)Math.min(length,count));}}return n;
            }
        };
    }
}
