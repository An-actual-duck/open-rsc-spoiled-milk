package orsc.browser;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** One source lease per closed Java copy. The browser transport owns verification. */
public final class BrowserBulkStaging {
    private BrowserBulkStaging() { }
    public interface Source {
        /** Complete leased input filename; the public caller validates its flat namespace. */
        String prepare(String relative) throws IOException;
        void release(String relative, boolean copied) throws IOException;
        boolean cancelled();
    }
    public static void copy(List<String> paths, File destinationRoot, int bufferSize,
                            Source source, BrowserBootstrapFiles.Progress progress) throws IOException {
        if(!java.util.Arrays.asList("Cache/whole-ram/manifest.json","Cache/whole-ram/source.bin","Cache/whole-ram/prepared.bin").equals(paths))throw new IOException("Invalid public startup roster");
        final long started=System.nanoTime();long completedBytes=0;int completedFiles=0;
        for(String relative:paths) {
            if(source.cancelled())throw new java.io.InterruptedIOException("Game startup cancelled");
            final String sourceFile=source.prepare(relative);boolean copied=false;Throwable failure=null;
            final long priorBytes=completedBytes;final int priorFiles=completedFiles;final long[] written={0};
            try {
                BrowserBootstrapFiles.copyOne(relative,sourceFile,destinationRoot,bufferSize,
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
    static String validateSource(String sourceFile,String relative) throws IOException {
        String name=relative==null?"":relative.substring(relative.lastIndexOf('/')+1);
        if(!java.util.Arrays.asList("manifest.json","source.bin","prepared.bin").contains(name)
            ||!("Cache/whole-ram/"+name).equals(relative)||sourceFile==null
            ||!sourceFile.matches("/str/browser-startup-[1-9][0-9]*-"+java.util.regex.Pattern.quote(name)))throw new IOException("Invalid game startup source");
        return sourceFile;
    }
}
