package orsc.graphics.two;

import java.lang.reflect.Field;
import java.util.Arrays;

/** Observer-only ownership/order/lifecycle assertions; never renders a replacement frame. */
public final class OverlayJournalCoreIndependentFixture {
    private static int checks;
    private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static final class Surface {
        final int[] c=new int[8*6];final WorldOverlayCoverage coverage=new WorldOverlayCoverage();
        WorldOverlayCoverage.Frame frame(){return coverage.borrow(c,8,6);}
        void fresh(){coverage.beginFrame();check(coverage.begin(c,8,6),"fresh");}
        Surface(){fresh();}
    }
    private static byte[] font(){byte[] f=new byte[15];f[2]=9;f[3]=3;f[4]=2;for(int i=9;i<15;i++)f[i]=(byte)(i*19);return f;}
    private static void noBorrowedReferences(OverlayJournalObserver observer,int[] c,float[] t)throws Exception{
        for(Field field:OverlayJournalObserver.class.getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())){
            field.setAccessible(true);Object value=field.get(observer);check(value!=c&&value!=t,"borrowed source retained by "+field.getName());
        }
        check(observer.retainedFontCopies()==0,"font copies retained after finish/close");
    }
    private static void text(OverlayJournalObserver o,Surface s,byte[] f,int[] plots){o.text(s.c,s.frame(),f,plots,plots.length/5,0,0,8,6,2);}
    public static void main(String[] args)throws Exception{
        Surface s=new Surface();OverlayJournalObserver o=new OverlayJournalObserver();
        int[] original=s.c.clone();float[] t=s.frame().transmittance,before=t.clone();o.begin(s.frame());
        byte[] f=font(),fFirst=f.clone();int[] plot={1,1,0x123456,0,1};text(o,s,f,plot);
        f[9]=0;plot[0]=4;text(o,s,f,plot);plot[0]=99;
        o.rectangle(s.c,s.frame(),OverlayJournalObserver.BOX,0,0,2,1,0x102030,256,0,0,8,6,2,false);
        OverlayJournalObserver.Snapshot out=o.finish(s.c,s.frame());
        check(out!=null&&out.header.length==16&&out.header[3]==1,"complete owned snapshot");
        check(out.header[4]==3&&out.header[5]==2&&out.header[6]==2&&out.header[7]==30,"ordered text/rect counters");
        check(out.ops[0]==1&&out.ops[16]==1&&out.ops[32]==2,"operation order");
        check(out.ops[2]==0&&out.ops[18]==15&&out.glyphs[0]==1&&out.glyphs[5]==4,"plot/font offsets copied at record");
        check(Arrays.equals(Arrays.copyOfRange(out.fonts,0,15),fFirst)&&out.fonts[24]==0,"font identity is not incorrectly memoized");
        check(out.runs.length==6&&out.runs[0]==0&&out.runs[1]==3&&out.runs[2]==2&&out.runs[3]==1&&out.runs[4]==2,"known contiguous run summary");
        check(out.header[9]==14&&out.runs[5]==14,"bounded work counts selected glyph cells including holes, plus rectangle");
        check(Arrays.equals(s.c,original)&&Arrays.equals(t,before),"observer changed literal C/T");
        check(o.finish(s.c,s.frame())==null,"finish consumed exactly once");noBorrowedReferences(o,s.c,t);

        // A barrier permanently prevents complete replay; later known work is
        // still observable in a separate bounded contiguous-run summary.
        o.begin(s.frame());text(o,s,font(),new int[]{1,1,1,0,0});o.barrier(s.c,OverlayJournalObserver.MINIMAP,2);
        text(o,s,font(),new int[]{2,1,2,0,0});out=o.finish(s.c,s.frame());
        check(out.header[3]==0&&out.header[8]==1,"unknown path sticky incomplete");check(out.ops[16]==9&&out.runs.length==12,"barrier separates ordered known runs");
        noBorrowedReferences(o,s.c,t);

        // Foreign targets classify auxiliary surfaces, rather than pretending
        // they modify the observed primary or silently adopting their owner.
        o.begin(s.frame());o.barrier(new int[48],OverlayJournalObserver.SPRITE,2);out=o.finish(s.c,s.frame());check(out.header[3]==1&&out.header[4]==0,"foreign barrier does not poison primary");
        o.softwareOwner();o.text(new int[48],s.frame(),font(),new int[]{1,1,0,0,0},1,0,0,8,6,2);check(o.finish(s.c,s.frame())==null,"scratch owner has no primary journal");noBorrowedReferences(o,s.c,t);
        OverlayJournalObserver scratch=new OverlayJournalObserver();scratch.softwareOwner();
        scratch.text(new int[48],s.frame(),font(),new int[]{1,1,0,0,0},1,0,0,8,6,2);
        for(String name:new String[]{"ops","glyphs","runs"}){Field field=OverlayJournalObserver.class.getDeclaredField(name);field.setAccessible(true);check(field.get(scratch)==null,"scratch eagerly allocates "+name);}

        for(int event=0;event<8;event++){
            s=new Surface();o.begin(s.frame());
            switch(event){
                case 0:s.fresh();break;
                case 1:s.coverage.reset(false);break;
                case 2:s.coverage.discard();break;
                case 3:s.coverage.invalidate("external writer");break;
                case 4:s.coverage.beginFrame();break;
                case 5:o.rectangle(s.c,s.frame(),2,0,0,2,2,0,256,0,0,8,6,2,true);break;
                case 6:o.text(s.c,s.frame(),font(),new int[]{1,1,0,99,1},1,0,0,8,6,2);break;
                default:o.text(s.c,s.frame(),null,new int[]{1,1,0,0,1},1,0,0,8,6,2);break;
            }
            out=o.finish(s.c,s.frame());check(out!=null&&out.header[3]==0,"lifecycle/malformed event incomplete "+event);noBorrowedReferences(o,s.c,t);
        }
        s=new Surface();o.begin(s.frame());for(int n=0;n<257;n++)text(o,s,font(),new int[]{1,1,0,0,0});out=o.finish(s.c,s.frame());
        check(out.header[3]==0&&out.header[6]==256&&out.header[4]<=4096,"text cap fails observer only");check(Arrays.equals(s.c,new int[48]),"overflow altered primary");noBorrowedReferences(o,s.c,t);
        o.begin(s.frame());text(o,s,font(),new int[4096*5]);text(o,s,font(),new int[]{1,1,0,0,0});out=o.finish(s.c,s.frame());
        check(out.header[3]==0&&out.header[5]==4096&&out.glyphs.length==4096*5,"glyph cap rejects following text without overrun");noBorrowedReferences(o,s.c,t);
        o.begin(s.frame());for(int n=0;n<4097;n++)o.rectangle(s.c,s.frame(),2,0,0,0,0,0,256,0,0,8,6,2,false);out=o.finish(s.c,s.frame());
        check(out.header[3]==0&&out.header[4]==4096&&out.ops.length==4096*16,"op cap remains bounded including empty rectangles");noBorrowedReferences(o,s.c,t);
        o.begin(s.frame());byte[] oversized=new byte[8*1024*1024+1];text(o,s,oversized,new int[]{0,0,0,0,0});out=o.finish(s.c,s.frame());
        check(out.header[3]==0&&out.header[7]==0&&out.fonts.length==0,"oversized font rejected without retained copy");noBorrowedReferences(o,s.c,t);
        int[] large=new int[1024*1024];WorldOverlayCoverage largeCoverage=new WorldOverlayCoverage();largeCoverage.beginFrame();check(largeCoverage.begin(large,1024,1024),"large work-cap owner");
        WorldOverlayCoverage.Frame largeFrame=largeCoverage.borrow(large,1024,1024);o.begin(largeFrame);
        for(int n=0;n<3;n++)o.rectangle(large,largeFrame,2,0,0,1024,1024,0,256,0,0,1024,1024,2,false);
        out=o.finish(large,largeFrame);check(out.header[3]==0&&out.header[9]==2*1024*1024&&out.header[4]==3,"work cap preserves only two bounded operations plus barrier");
        check(large[0]==0&&large[large.length-1]==0&&largeFrame.transmittance[0]==1f,"work overflow does not raster primary");noBorrowedReferences(o,large,largeFrame.transmittance);
        o.begin(s.frame());o.close(OverlayJournalObserver.RESET);check(o.finish(s.c,s.frame())==null,"closed observer remains retired");noBorrowedReferences(o,s.c,t);
        System.out.println("PASS independent journal core order/source snapshots/owner/epoch/caps/unknown/no-C-T-mutation: "+checks+" checks; CPU only");
    }
}
