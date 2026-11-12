package orsc.browser;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Actual filesystem copy/close/cancellation authority. No CheerpJ, server or network. */
public final class BrowserBulkStagingFixture {
 static int checks;static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static final List<String> PATHS=Arrays.asList("Cache/whole-ram/manifest.json","Cache/whole-ram/source.bin","Cache/whole-ram/prepared.bin");
 static void noOpen(Path p)throws Exception {
  Path fds=Paths.get("/proc/self/fd");if(!Files.isDirectory(fds))return;
  int open=0;try(java.util.stream.Stream<Path> list=Files.list(fds)){for(Path fd:(Iterable<Path>)list::iterator)try{if(Files.readSymbolicLink(fd).toString().equals(p.toAbsolutePath().toString()))open++;}catch(IOException ignored){}}
  check(open==0,"Input/output descriptor closed before source release: "+p.getFileName());
 }
 static class Lease implements BrowserBulkStaging.Source {
  final Path source,target;int prepared,released;boolean active,cancel,failCleanup;String current;List<String> calls=new ArrayList<String>();
  Lease(Path source,Path target){this.source=source;this.target=target;}
  public String prepare(String relative){check(!active,"Only one source lease at a time");active=true;current=relative;prepared++;calls.add("prepare:"+relative);return source.resolve(relative.substring(relative.lastIndexOf('/')+1)).toString();}
  public void release(String relative,boolean copied)throws IOException {
   check(active&&relative.equals(current),"Release owns current source");
   try{noOpen(source.resolve(relative.substring(relative.lastIndexOf('/')+1)));noOpen(target.resolve(relative));}catch(Exception e){throw new IOException(e);}
   calls.add("release:"+copied+":"+relative);active=false;released++;if(failCleanup)throw new IOException("cleanup marker");
  }
  public boolean cancelled(){try{return cancel||current!=null&&current.endsWith("source.bin")&&Files.exists(target.resolve(current))&&Files.size(target.resolve(current))>=1048576;}catch(IOException e){throw new RuntimeException(e);}}
 }
 static void seed(Path root)throws Exception {for(String name:PATHS){Path p=root.resolve(name.substring(name.lastIndexOf('/')+1));Files.createDirectories(p.getParent());byte[] bytes=new byte[name.endsWith("source.bin")?9*1048576+17:7];Arrays.fill(bytes,(byte)name.length());Files.write(p,bytes);}}
 public static void main(String[] args)throws Exception {
  Path root=Paths.get(args[0]);Files.createDirectories(root);Path source=root.resolve("source");seed(source);
  final long[] last={0};final int[] files={0};Lease complete=new Lease(source,root.resolve("complete")){public boolean cancelled(){return false;}};
  BrowserBulkStaging.copy(PATHS,complete.target.toFile(),65536,complete,(b,f,t,e)->{check(b>=last[0]&&f>=files[0]&&t==3,"Cumulative successful-written progress");last[0]=b;files[0]=f;});
  for(String p:PATHS)check(Arrays.equals(Files.readAllBytes(source.resolve(p.substring(p.lastIndexOf('/')+1))),Files.readAllBytes(complete.target.resolve(p))),"Exact original source bytes in original destination");
  check(complete.prepared==3&&complete.released==3&&!complete.active&&files[0]==3,"Every successful copy closes/releases before next source");
  Lease cancelled=new Lease(source,root.resolve("cancelled"));try{BrowserBulkStaging.copy(PATHS,cancelled.target.toFile(),65536,cancelled,null);throw new AssertionError("Cancellation ignored");}catch(InterruptedIOException expected){checks++;}
  check(cancelled.prepared==2&&cancelled.released==2&&!cancelled.active,"Mid-copy cancellation releases and never opens next file");check(Files.size(cancelled.target.resolve(PATHS.get(1)))==1048576,"Cancellation polled at exact one-MiB boundary");
  Lease before=new Lease(source,root.resolve("pre-cancel"));before.cancel=true;try{BrowserBulkStaging.copy(PATHS,before.target.toFile(),65536,before,null);throw new AssertionError("Pre-cancel ignored");}catch(InterruptedIOException expected){checks++;}check(before.prepared==0&&!Files.exists(before.target),"Pre-cancel opens no source/output");
  Lease failure=new Lease(root.resolve("missing"),root.resolve("failure")){public boolean cancelled(){return false;}};failure.failCleanup=true;
  try{BrowserBulkStaging.copy(PATHS,failure.target.toFile(),65536,failure,null);throw new AssertionError("I/O failure ignored");}catch(FileNotFoundException expected){check(expected.getSuppressed().length==1&&expected.getSuppressed()[0].getMessage().contains("cleanup marker"),"Original input error preserved with cleanup suppressed");}check(failure.prepared==1&&failure.released==1&&!failure.active,"Failed input closes/releases before abort");
  for(List<String> bad:Arrays.asList(Collections.singletonList("Cache/uid.dat"),Arrays.asList(PATHS.get(1),PATHS.get(0),PATHS.get(2)),Arrays.asList(PATHS.get(0),PATHS.get(1)))){
   Lease rejected=new Lease(source,root.resolve("bad"));try{BrowserBulkStaging.copy(bad,rejected.target.toFile(),65536,rejected,null);throw new AssertionError("Unsafe roster accepted");}catch(IOException expected){checks++;}check(rejected.prepared==0&&!Files.exists(rejected.target),"Whole roster validated before any source/output");
  }
  check(BrowserBulkStaging.validateSource("/str/browser-startup-1-manifest.json",PATHS.get(0)).equals("/str/browser-startup-1-manifest.json"),"Exact source namespace accepted");
  for(String bad:new String[]{null,"/app/","/str/browser-startup-0/","/str/browser-startup-1/../","/str/browser-startup-1-source.bin","/str/browser-startup-1-evil.json","https://evil/"})try{BrowserBulkStaging.validateSource(bad,PATHS.get(0));throw new AssertionError("Unsafe source namespace");}catch(IOException expected){checks++;}
  System.out.println("PASS "+checks+" actual bulk orchestration byte/FD-close/finally/cancel/roster/namespace checks");
 }
}
