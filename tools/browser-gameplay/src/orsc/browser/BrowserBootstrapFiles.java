package orsc.browser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Ordered startup copies; observations count successful writes, never downloads. */
public final class BrowserBootstrapFiles {
    private BrowserBootstrapFiles() {}
    public interface Progress {
        void update(long bytes, int completedFiles, int totalFiles, long elapsedMillis);
    }
    private static void observe(Progress progress,long bytes,int files,int total,long elapsed){
        if(progress!=null)try{progress.update(bytes,files,total,elapsed);}catch(Throwable ignored){/* Display observations cannot replace I/O success or failure. */}
    }
    public static void copy(List<String> paths, String sourceRoot, File destinationRoot,
                            int bufferSize, Progress progress) throws IOException {
        if(bufferSize<=0||bufferSize>1048576)throw new IllegalArgumentException("Invalid copy buffer");
        // Validate the entire already-authorized roster before opening outputs.
        // Caller retains its public personal-file policy and exact RAM roster.
        for(String path:paths)if(path==null||path.length()==0||path.startsWith("/")||path.contains("..")||path.contains("\\"))throw new IOException("Unsafe staged path");
        final long started=System.nanoTime();long bytes=0,lastBytes=0,lastMillis=0;int files=0;
        observe(progress,0,0,paths.size(),0);
        for(String relative:paths){
            File destination=new File(destinationRoot,relative);
            File parent=destination.getParentFile();if(parent!=null)parent.mkdirs();
            try(InputStream input=new FileInputStream(sourceRoot+relative);OutputStream output=new FileOutputStream(destination)){
                byte[] buffer=new byte[bufferSize];int count;
                while((count=input.read(buffer))!=-1){
                    output.write(buffer,0,count);bytes+=count;
                    long elapsed=(System.nanoTime()-started)/1000000;
                    if(bytes-lastBytes>=8388608||elapsed-lastMillis>=500){
                        observe(progress,bytes,files,paths.size(),elapsed);
                        lastBytes=bytes;lastMillis=elapsed;
                    }
                }
            }
            files++;lastBytes=bytes;lastMillis=(System.nanoTime()-started)/1000000;
            observe(progress,bytes,files,paths.size(),lastMillis);
        }
    }
}
