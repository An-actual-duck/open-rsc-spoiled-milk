package orsc.browser;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Independent real-copy orchestration proof; no mounted browser filesystem. */
public final class BrowserBulkStagingReviewFixture {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static final List<String> PATHS=Arrays.asList("Cache/whole-ram/manifest.json","Cache/whole-ram/source.bin","Cache/whole-ram/prepared.bin");
 static final class Source implements BrowserBulkStaging.Source {
  final String root;int prepares,releases,polls;int cancelAt=Integer.MAX_VALUE;boolean cleanupError;List<Boolean> copied=new ArrayList<Boolean>();
  Source(Path root){this.root=root.toString()+"/";}
  public String prepare(String relative){check(prepares==releases,"onlyone sourcelease atatime");check(relative.equals(PATHS.get(prepares)),"fixedordered prepare");prepares++;return root;}
  public void release(String relative,boolean success)throws IOException{releases++;copied.add(success);if(cleanupError)throw new IOException("cleanup marker");}
  public boolean cancelled(){return ++polls>=cancelAt;}
 }
 public static void main(String[] args)throws Exception{
  Path run=Paths.get(args[0]);Files.createDirectories(run);Path input=run.resolve("input");Files.createDirectories(input.resolve("Cache/whole-ram"));byte[] large=new byte[3*1048576+7];for(int i=0;i<large.length;i++)large[i]=(byte)i;
  Files.write(input.resolve(PATHS.get(0)),large);Files.write(input.resolve(PATHS.get(1)),new byte[]{4});Files.write(input.resolve(PATHS.get(2)),new byte[]{5,6});
  final List<long[]> progress=new ArrayList<long[]>();Source success=new Source(input);Path output=run.resolve("output");
  BrowserBulkStaging.copy(PATHS,output.toFile(),65536,success,new BrowserBootstrapFiles.Progress(){public void update(long b,int f,int t,long ms){progress.add(new long[]{b,f,t,ms});}});
  check(success.prepares==3&&success.releases==3&&success.copied.equals(Arrays.asList(true,true,true)),"all3closedleases completed");for(String path:PATHS)check(Arrays.equals(Files.readAllBytes(input.resolve(path)),Files.readAllBytes(output.resolve(path))),"eachcopiedpayload exact");
  long lastBytes=-1,lastFiles=-1,lastTime=-1;for(long[] row:progress){check(row[0]>=lastBytes&&row[1]>=lastFiles&&row[2]==3&&row[3]>=lastTime,"globalcopyprogress monotonic acrossleases");lastBytes=row[0];lastFiles=row[1];lastTime=row[3];}check(lastBytes==large.length+3&&lastFiles==3,"globalbyte/filecount exact");
  Source missing=new Source(run.resolve("missing"));missing.cleanupError=true;try{BrowserBulkStaging.copy(PATHS,run.resolve("missing-output").toFile(),65536,missing,null);throw new AssertionError("sourceerror ignored");}catch(FileNotFoundException expected){check(expected.getSuppressed().length==1&&expected.getSuppressed()[0].getMessage().equals("cleanup marker"),"originalIOerror preservescleanup suppressed");}check(missing.prepares==1&&missing.releases==1&&missing.copied.equals(Arrays.asList(false)),"failure releasesexactlease andstopsbeforesecond");
  Source cancelled=new Source(input);cancelled.cancelAt=3;try{BrowserBulkStaging.copy(PATHS,run.resolve("cancelled").toFile(),65536,cancelled,null);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){checks++;}check(cancelled.prepares==1&&cancelled.releases==1&&cancelled.copied.equals(Arrays.asList(false)),"cancelledcopy finallyreleasesclosedfailedlease");check(Files.size(run.resolve("cancelled").resolve(PATHS.get(0)))==1048576,"bounded1MiB cancellation");
  Source cleanup=new Source(input);cleanup.cleanupError=true;try{BrowserBulkStaging.copy(PATHS,run.resolve("cleanup").toFile(),65536,cleanup,null);throw new AssertionError("releaseerror ignored");}catch(IOException expected){check(expected.getMessage().equals("cleanup marker"),"successfulcopy cleanupfailure surfaces");}check(cleanup.prepares==1&&cleanup.releases==1&&cleanup.copied.equals(Arrays.asList(true)),"cleanupfailure preventsnextlease");
  Source reporter=new Source(input);BrowserBulkStaging.copy(PATHS,run.resolve("reporter").toFile(),32768,reporter,new BrowserBootstrapFiles.Progress(){public void update(long b,int f,int t,long ms){throw new IllegalStateException("UI marker");}});check(reporter.copied.equals(Arrays.asList(true,true,true)),"passivereportererror cannotabortcopies");
  for(String invalid:new String[]{null,"/app/","/str/browser-startup-0/","/str/browser-startup-1/../","/str/browser-startup-1", "https://example/"})try{BrowserBulkStaging.validateRoot(invalid);throw new AssertionError("unsafeRoot");}catch(IOException expected){checks++;}
  check(BrowserBulkStaging.validateRoot("/str/browser-startup-1/").equals("/str/browser-startup-1/"),"exactsource root admitted");
  System.out.println("PASS "+checks+" independent actual-copy lease/originalIO/cleanup/cancel/byte-progress/source-root checks; no browser/network");
 }
}
