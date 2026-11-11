package orsc.browser;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.io.PrintWriter;

/** Bounded normal-startup observations only; never owns client thread lifetime. */
final class BrowserStartupDiagnostics {
    interface Sink { void report(String phase,String detail); }
    private final Sink sink;
    private int samples;
    BrowserStartupDiagnostics(Sink sink){this.sink=sink;}
    void installChainedHandler() {
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread thread,Throwable error){
            try {StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));emit("uncaught",thread.getName()+"\n"+text);}
            finally {if(previous!=null)previous.uncaughtException(thread,error);else error.printStackTrace();}
        }});
    }
    private void emit(String phase,String detail){try{sink.report(phase,detail.length()>8192?detail.substring(0,8192):detail);}catch(Throwable ignored){/* diagnostics cannot replace original failure */}}
    static String describe(Thread loop,File log) {
        String text="loop="+(loop==null?"absent":loop.getName()+":"+loop.getState()+":daemon="+loop.isDaemon()+":alive="+loop.isAlive());
        if(log!=null&&log.isFile())try(RandomAccessFile in=new RandomAccessFile(log,"r")){
            long length=in.length();int count=(int)Math.min(4096L,length);byte[] tail=new byte[count];in.seek(length-count);in.readFully(tail);
            text+="\nlogBytes="+length+"\n"+new String(tail,"UTF-8");
        }catch(Exception error){text+="\nlog-read="+error;}
        return text;
    }
    void sample(String phase) {
        if(samples++>=12)return;
        try {
            java.lang.reflect.Field client=orsc.ORSCApplet.class.getDeclaredField("mudclient");client.setAccessible(true);Object owner=client.get(null);
            Thread loop=owner==null?null:(Thread)owner.getClass().getField("clientBaseThread").get(owner);
            String configured=System.getProperty("spoiledmilk.clientLog");File log=new File(configured==null?"/files/spoiled-milk-client.log":configured);
            emit(phase,describe(loop,log));
        }catch(Throwable error){emit(phase,"diagnostic-unavailable:"+error);}
    }
}
