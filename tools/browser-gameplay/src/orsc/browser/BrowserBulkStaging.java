package orsc.browser;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** One source lease per closed Java copy. The browser transport owns verification. */
public final class BrowserBulkStaging {
    private BrowserBulkStaging() { }
    public interface Source {
        String prepare(String relative) throws IOException;
        void release(String relative, boolean copied) throws IOException;
        boolean cancelled();
    }
    public static void copy(List<String> paths, File destinationRoot, int bufferSize,
                            Source source, BrowserBootstrapFiles.Progress progress) throws IOException {
        final long started=System.nanoTime();long completedBytes=0;int completedFiles=0;
        for(String relative:paths) {
            if(source.cancelled())throw new java.io.InterruptedIOException("Game startup cancelled");
            final String root=source.prepare(relative);boolean copied=false;Throwable failure=null;
            final long priorBytes=completedBytes;final int priorFiles=completedFiles;final long[] written={0};
            try {
                BrowserBootstrapFiles.copy(Collections.singletonList(relative),root,destinationRoot,bufferSize,
                    new BrowserBootstrapFiles.Progress(){public void update(long bytes,int files,int total,long elapsed){
                        written[0]=bytes;
                        if(progress!=null)progress.update(priorBytes+bytes,priorFiles+files,paths.size(),(System.nanoTime()-started)/1000000);
                    }},new BrowserBootstrapFiles.Cancellation(){public boolean cancelled(){return source.cancelled();}});
                copied=true;completedBytes+=written[0];completedFiles++;
            } catch(IOException|RuntimeException|Error error) { failure=error;throw error; }
            finally {
                // BrowserBootstrapFiles returns/throws only after closing both streams.
                try { source.release(relative,copied); }
                catch(IOException|RuntimeException|Error cleanup) { if(failure==null)throw cleanup;failure.addSuppressed(cleanup); }
            }
        }
    }
    static String validateRoot(String root) throws IOException {
        if(root==null||!root.matches("/str/browser-startup-[1-9][0-9]*/"))throw new IOException("Invalid game startup source");
        return root;
    }
}
