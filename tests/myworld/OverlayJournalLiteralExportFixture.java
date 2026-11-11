package orsc.graphics.two;

import java.nio.file.Files;
import java.nio.file.Paths;
import orsc.graphics.Renderer2DFrame;

/** Actual Java literal outputs and actual hooked snapshots, for independent JS proof. */
public final class OverlayJournalLiteralExportFixture {
    private static void array(StringBuilder out,int[] data){out.append('[');for(int n=0;n<data.length;n++){if(n>0)out.append(',');out.append(data[n]);}out.append(']');}
    private static void array(StringBuilder out,byte[] data){int[] words=new int[data.length];for(int n=0;n<data.length;n++)words[n]=data[n];array(out,words);}
    private static void font(boolean aa){int size=Fonts.inputFilterChars.length()*9;byte[] f=new byte[size+6];for(int n=0;n<size;n+=9){f[n]=(byte)(size>>14);f[n+1]=(byte)((size>>7)&127);f[n+2]=(byte)(size&127);f[n+3]=2;f[n+4]=3;f[n+6]=3;f[n+7]=3;}byte[] pixels={0,31,64,(byte)128,(byte)230,(byte)255};System.arraycopy(pixels,0,f,size,6);for(int n=0;n<Fonts.fontData.length;n++)Fonts.fontData[n]=f;for(int n=0;n<Fonts.fontAntiAliased.length;n++)Fonts.fontAntiAliased[n]=aa;}
    public static void main(String[] args)throws Exception{
        StringBuilder out=new StringBuilder("{\"scope\":\"Actual Java literal C/raw-T and hooked owned journals; no JS self-reference\",\"frames\":[");int count=0;
        for(boolean aa:new boolean[]{false,true})for(boolean clipped:new boolean[]{false,true})for(boolean shadow:new boolean[]{false,true}){
            font(aa);GraphicsController g=new GraphicsController(64,48,1);g.loggedIn=shadow;g.setWorldOverlayCoverageEnabled(true);g.beginRenderer2DFrame();g.setRenderer2DPhase(Renderer2DFrame.Phase.UI_OVERLAY);if(clipped)g.setClip(2,60,45,1);
            if(!g.beginTransparentWorldOverlay())throw new AssertionError("fresh world");
            g.drawBox(0,0,11,7,0x112233);g.drawBoxAlpha(3,2,12,8,0x8ac754,128);g.drawLineHoriz(0,8,20,0xff000000);g.drawLineVert(15,1,0x659834,16);
            g.drawColoredString(1,10,"A@red@Bc",0,0xabcdef,0);Fonts.fontData[0][Fonts.fontData[0].length-2]=0;
            g.drawBoxAlpha(4,6,10,4,0x543219,255);g.drawColoredString(1,10,"ABC",0,0x7fcd51,0);g.drawColoredString(-100,-100,"A",0,0x123456,0);
            WorldOverlayCoverage.Frame frame=g.borrowWorldOverlayCoverage();OverlayJournalObserver.Snapshot journal=g.finishOverlayJournal(frame);
            if(journal==null||journal.header[3]!=1)throw new AssertionError("Known-only case not complete "+aa+"/"+clipped+"/"+shadow);
            if(count++>0)out.append(',');out.append("{\"width\":64,\"height\":48,\"generation\":").append(frame.generation).append(",\"sequence\":").append(frame.frameSequence);
            out.append(",\"c\":");array(out,g.pixelData);int[] bits=new int[frame.transmittance.length];for(int n=0;n<bits.length;n++)bits[n]=Float.floatToRawIntBits(frame.transmittance[n]);out.append(",\"tBits\":");array(out,bits);
            out.append(",\"header\":");array(out,journal.header);out.append(",\"ops\":");array(out,journal.ops);out.append(",\"glyphs\":");array(out,journal.glyphs);out.append(",\"fonts\":");array(out,journal.fonts);out.append(",\"runs\":");array(out,journal.runs);out.append(",\"stats\":");array(out,journal.stats);out.append('}');
        }
        out.append("]}\n");Files.write(Paths.get(args[0]),out.toString().getBytes("UTF-8"));System.out.println("PASS exported "+count+" complete actual-GC journals plus literal C/T; CPU only");
    }
}
