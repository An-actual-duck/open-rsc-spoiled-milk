package orsc.browser;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real filesystem copies; no Java runtime/browser, networking or world authority. */
public final class BrowserBootstrapFilesFixture {
    static int checks;
    static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    static byte[] bytes(int size){byte[] data=new byte[size];for(int i=0;i<size;i++)data[i]=(byte)(i*31);return data;}
    static void write(Path root,String name,byte[] data)throws Exception{Path file=root.resolve(name);Files.createDirectories(file.getParent());Files.write(file,data);}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);Files.createDirectories(root);Path source=root.resolve("source"),destination=root.resolve("destination");Files.createDirectories(source);
        final List<long[]> records=new ArrayList<long[]>();
        BrowserBootstrapFiles.Progress progress=new BrowserBootstrapFiles.Progress(){public void update(long bytes,int files,int total,long millis){records.add(new long[]{bytes,files,total,millis});}};
        byte[] first=bytes(17*1024*1024+19),second=bytes(4097);write(source,"Cache/one.bin",first);write(source,"Cache/two.bin",second);
        BrowserBootstrapFiles.copy(Arrays.asList("Cache/one.bin","Cache/two.bin"),source.toString()+"/",destination.toFile(),65536,progress);
        check(Arrays.equals(Files.readAllBytes(destination.resolve("Cache/one.bin")),first),"large copy exact");check(Arrays.equals(Files.readAllBytes(destination.resolve("Cache/two.bin")),second),"small copy exact");
        check(records.size()>=5&&records.size()<20,"bounded intermediate8MiB callbacks plus file finishes");check(Arrays.equals(records.get(0),new long[]{0,0,2,0}),"initial progress before writes");
        long lastBytes=-1,lastFiles=-1,lastMillis=-1;for(long[] record:records){check(record[0]>=lastBytes&&record[1]>=lastFiles&&record[2]==2&&record[3]>=lastMillis,"cumulative monotonic actual written bytes/files/time");lastBytes=record[0];lastFiles=record[1];lastMillis=record[3];}
        check(lastBytes==(long)first.length+second.length&&lastFiles==2,"final exact byte and closed-file count");
        // Existing output is truncated exactly as the old bootstrap path did.
        write(source,"Cache/two.bin",new byte[]{8});records.clear();BrowserBootstrapFiles.copy(Arrays.asList("Cache/two.bin"),source.toString()+"/",destination.toFile(),32768,progress);check(Files.size(destination.resolve("Cache/two.bin"))==1,"existing output truncated");
        // Error keeps original I/O failure and emits no completed-file observation.
        records.clear();try{BrowserBootstrapFiles.copy(Arrays.asList("missing.bin"),source.toString()+"/",destination.toFile(),65536,progress);throw new AssertionError("missing source accepted");}catch(FileNotFoundException expected){checks++;}check(records.size()==1&&records.get(0)[1]==0,"failed copy has no false completion");
        for(String path:Arrays.asList("../outside","/absolute","Cache\\unsafe","","Cache/../../escape")){try{BrowserBootstrapFiles.copy(Arrays.asList("Cache/two.bin",path),source.toString()+"/",root.resolve("invalid").toFile(),65536,progress);throw new AssertionError("unsafe path accepted");}catch(IOException expected){checks++;}}check(!Files.exists(root.resolve("invalid/Cache/two.bin")),"roster refusal before any output");
        for(int buffer:new int[]{0,-1,1048577}){try{BrowserBootstrapFiles.copy(Collections.<String>emptyList(),source.toString()+"/",destination.toFile(),buffer,progress);throw new AssertionError("bad buffer");}catch(IllegalArgumentException expected){checks++;}}
        records.clear();BrowserBootstrapFiles.copy(Collections.<String>emptyList(),source.toString()+"/",destination.toFile(),32768,progress);check(records.size()==1&&records.get(0)[2]==0,"empty roster valid zero-work observation");
        BrowserBootstrapFiles.copy(Arrays.asList("Cache/two.bin"),source.toString()+"/",destination.toFile(),32768,new BrowserBootstrapFiles.Progress(){public void update(long b,int f,int t,long m){throw new IllegalStateException("display unavailable");}});check(Files.size(destination.resolve("Cache/two.bin"))==1,"observer failure cannot replace successful file copy");
        System.out.println("PASS "+checks+" actual ordered startup filesystem copy/progress/error/path/buffer checks; byte counts are writes, not downloads");
    }
}
