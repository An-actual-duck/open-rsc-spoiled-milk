package orsc;
import java.io.*;
import java.util.*;
public final class BrowserWorldPreparationProgressFixture {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  final long[] clock={0};List<int[]> reports=new ArrayList<>();
  BrowserWorldPreparationProgress p=new BrowserWorldPreparationProgress(v->reports.add(v.clone()),()->clock[0]);
  byte[] data=new byte[3*1048576+17];new Random(77).nextBytes(data);
  p.stage(4,data.length);ByteArrayOutputStream copied=new ByteArrayOutputStream();
  try(InputStream in=new BufferedInputStream(p.reading(new ByteArrayInputStream(data),data.length),65536)){
   byte[] b=new byte[25003];int n;while((n=in.read(b))!=-1){copied.write(b,0,n);clock[0]+=1000000;}
  }
  check(Arrays.equals(data,copied.toByteArray()),"buffered decoder bytes remain literal");
  check(reports.size()>=5&&reports.size()<32,"bounded byte milestones, not per read/byte");
  check(reports.get(reports.size()-1)[4]==data.length,"counts every actual input byte exactly once");
  int seq=0,done=0;for(int[] v:reports){check(v.length==8&&v[0]==1&&v[2]>seq&&v[4]>=done&&v[4]<=v[5],"monotonic typed fields");seq=v[2];done=v[4];}
  p.stage(5,0);p.stage(6,0);check(reports.get(reports.size()-1)[3]==6,"data verification observation");
  p.fail(3);int terminal=reports.size();p.advance(1);p.stage(1,0);p.fail(1);check(reports.size()==terminal,"genuine failure is terminal");
  check(reports.get(terminal-1)[7]==3,"controlled render failure only");
  BrowserWorldPreparationProgress passive=new BrowserWorldPreparationProgress(v->{throw new OutOfMemoryError("observer");});
  passive.stage(2,data.length);try(InputStream in=passive.reading(new ByteArrayInputStream(data),data.length)){check(Arrays.equals(data,read(in)),"throwing observer never changes bytes");}passive.fail(1);
  final boolean[] closed={false};IOException original=new IOException("original read");
  InputStream broken=new InputStream(){public int read()throws IOException{throw original;}public int read(byte[]b,int o,int n)throws IOException{throw original;}public void close(){closed[0]=true;}};
  try(InputStream in=p.reading(broken,12)){in.read(new byte[8]);throw new AssertionError("read failure swallowed");}catch(IOException e){check(e==original,"same original read exception");}check(closed[0],"source closure preserved");
  BrowserWorldPreparationProgress off=new BrowserWorldPreparationProgress(null);InputStream literal=new ByteArrayInputStream(data);check(off.reading(literal,data.length)==literal,"private disabled wrapper retains original input");
  List<int[]> timed=new ArrayList<>();clock[0]=0;BrowserWorldPreparationProgress slow=new BrowserWorldPreparationProgress(v->timed.add(v),()->clock[0]);slow.stage(3,1797);clock[0]=300000000;slow.advance(1);check(timed.size()==2&&timed.get(1)[4]==1,"slow progress uses real time without artificial increments");
  System.out.println("PASS "+checks+" world progress buffered bytes, bounded callback counts, closure/error identity, passive observers and terminal failure checks");
 }
 static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] bytes=new byte[65536];for(int n;(n=in.read(bytes))!=-1;)b.write(bytes,0,n);return b.toByteArray();}
}
