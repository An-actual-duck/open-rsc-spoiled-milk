package orsc.graphics.two;
import java.util.Arrays;
public final class OverlayJournalFixture {
 static int checks;
 static void require(boolean x){checks++;if(!x)throw new AssertionError("check"+checks);}
 static WorldOverlayCoverage coverage=new WorldOverlayCoverage();static int[] c=new int[64*32];
 static WorldOverlayCoverage.Frame fresh(){coverage.beginFrame();require(coverage.begin(c,64,32));return coverage.borrow(c,64,32);}
 static byte[] font(){byte[] f=new byte[16];f[2]=9;f[3]=2;f[4]=2;f[6]=2;f[9]=1;f[12]=1;return f;}
 static void text(OverlayJournalObserver o,WorldOverlayCoverage.Frame f,byte[] font){o.text(c,f,font,new int[]{1,3,0x123456,0,0},1,0,0,64,32,1);}
 public static void main(String[] args){
  OverlayJournalObserver o=new OverlayJournalObserver();WorldOverlayCoverage.Frame f=fresh();o.begin(f);int[] old=c.clone();float[] oldT=f.transmittance.clone();byte[] font=font();text(o,f,font);font[9]=7;text(o,f,font);require(o.retainedFontCopies()==2);OverlayJournalObserver.Snapshot s=o.finish(c,f);require(s.header[3]==1&&s.header[6]==2&&s.header[5]==2);require(s.fonts[9]==1&&s.fonts[font.length+9]==7);require(Arrays.equals(c,old)&&Arrays.equals(f.transmittance,oldT));require(o.retainedFontCopies()==0);require(s.runs.length==6&&s.runs[2]==2&&s.runs[4]==2);
  f=fresh();o.begin(f);text(o,f,font);o.barrier(c,OverlayJournalObserver.MINIMAP,1);text(o,f,font);o.barrier(c,OverlayJournalObserver.READ,1);s=o.finish(c,f);require(s.header[3]==0&&s.header[8]==2&&s.header[10]==2);require(s.runs[2]==1&&s.runs[8]==1);
  f=fresh();o.begin(f);for(int i=0;i<257;i++)text(o,f,font);s=o.finish(c,f);require(s.header[3]==0&&s.header[6]==256);require(s.header[7]<=OverlayJournalObserver.MAX_FONT_BYTES);
  f=fresh();o.begin(f);text(o,f,font);coverage.beginFrame();WorldOverlayCoverage.Frame stale=coverage.borrow(c,64,32);s=o.finish(c,stale);require(s.header[3]==0);
  f=fresh();o.begin(f);text(o,f,font);o.close(OverlayJournalObserver.RESET);require(o.retainedFontCopies()==0&&o.finish(c,f)==null);
  f=fresh();o.begin(f);o.text(c,f,font,new int[]{0,0,0,Integer.MAX_VALUE,0},1,0,0,64,32,1);s=o.finish(c,f);require(s.header[3]==0&&s.header[5]==0);
  OverlayJournalObserver scratch=new OverlayJournalObserver();scratch.softwareOwner();text(scratch,f,font);require(scratch.finish(c,f)==null&&scratch.retainedFontCopies()==0);
  f=fresh();o.begin(f);o.rectangle(c,f,OverlayJournalObserver.ALPHA_BOX,0,0,64,32,0,128,0,0,64,32,1,false);s=o.finish(c,f);require(s.header[3]==1&&s.header[9]==2048&&s.runs[3]==1);
  f=fresh();o.begin(f);o.rectangle(c,f,OverlayJournalObserver.BOX,0,0,64,32,0,256,0,0,64,32,1,true);s=o.finish(c,f);require(s.header[3]==0);
  f=fresh();o.begin(f);for(int i=0;i<4098;i++)o.barrier(c,OverlayJournalObserver.UNKNOWN,1);s=o.finish(c,f);require(s.ops.length==OverlayJournalObserver.MAX_OPS*16&&s.header[3]==0);
  System.out.println("PASS "+checks+" observer ownership/snapshot/bounds/lifecycle checks; original C/T unchanged");
 }
}
