package orsc.graphics.two;

import com.openrsc.client.model.Sprite;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import orsc.Config;

/** Actual software writers, with the ordinary renderer over independent background images. */
public final class WorldOverlayCoverageFixture {
    interface Draw { void run(GraphicsController g) throws Exception; }
    static final int W=40,H=32;
    static int checks,maxError;
    static long squareError,channels;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static GraphicsController graphics(){return new GraphicsController(W,H,1);}
    static Sprite sprite(boolean shifted){
        Sprite s=new Sprite(new int[]{0,0xff000000,0x808080,0xff6666,0xffffff,0x550055,0x5577cc,0,
            0x123456,0xabcdef,0,0x8080ff,0x010101,0xfefefe,0xff000000,0x112233},4,4);
        s.setSomething(4,4);s.setRequiresShift(shifted);return s;
    }
    static void call(GraphicsController g,String name,Object...args)throws Exception{
        Class<?>[] types=new Class<?>[args.length];
        for(int i=0;i<types.length;i++) types[i]=args[i] instanceof Integer?int.class:
            args[i] instanceof Byte?byte.class:args[i] instanceof Boolean?boolean.class:args[i].getClass();
        Method m=GraphicsController.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(g,args);
    }
    static List<Draw> operations(){
        List<Draw> ops=new ArrayList<Draw>();
        for(final int color:new int[]{0,0xffffff,0x808080,0xff000000,0x123456}){
            ops.add(g->g.drawBox(-2,3,19,12,color));
            ops.add(g->g.drawLineHoriz(-3,4,32,color));
            ops.add(g->g.drawLineVert(4,-2,color,29));
            ops.add(g->g.setPixel(7,7,color));
            ops.add(g->g.drawBoxBorder(1,20,2,11,color));
            for(final int alpha:new int[]{0,1,128,255,256}){
                ops.add(g->g.drawBoxAlpha(-3,3,25,12,color,alpha));
                ops.add(g->g.drawCircle(8,8,10,color,alpha,0));
            }
        }
        ops.add(g->g.drawVerticalGradient(-3,2,24,22,0xffffff,0x202020));
        ops.add(g->g.blackScreen(false));ops.add(g->g.blackScreen(true));
        for(final int alpha:new int[]{0,1,128,255,256}){
            ops.add(g->g.a(sprite(false),0,-1,alpha,2));
            ops.add(g->g.drawSprite(sprite(false),-1,2,16,19,0,alpha));
            ops.add(g->g.spriteClipping(sprite(false),(byte)0,19,-1,16,2,alpha));
        }
        ops.add(g->g.drawSprite(sprite(false),-1,2));
        ops.add(g->g.drawSprite(sprite(false),-1,2,16,19,0));
        ops.add(g->g.spriteClip3(-1,0x88ff44,sprite(false),2,19,(byte)0,16));
        for(final boolean mirror:new boolean[]{false,true})for(final int opacity:new int[]{0,1,128,255}){
            ops.add(g->g.drawSpriteClipping(sprite(false),-1,2,16,19,0x44aa88,0xffffff,0x3355ff,mirror,3,0,(opacity<<24)|0xeeeeee));
            ops.add(g->g.drawSpriteClipping(sprite(false),-1,2,16,19,0x44aa88,0xff7744,0x3355ff,mirror,3,0,(opacity<<24)|0xeeeeee));
        }
        ops.add(g->g.drawMinimapSprite(sprite(false),10,12,0,256,20));
        ops.add(g->g.drawMinimapSprite(sprite(true),10,12,0,256,20));
        ops.add(g->call(g,"plotLetter",0,g.pixelData,3+3*W,W-8,2,8,0,
            new byte[]{0,1,1,0,1,0,1,1,1,1,0,1,0,1,1,1},0));
        for(final int color:new int[]{0,0xffffff,0x808080})ops.add(g->call(g,"plotLetterAntialiased",
            new byte[]{0,1,30,31,64,(byte)128,(byte)229,(byte)230,(byte)255,31,30,0},color,6,3+3*W,2,0,W-6,g.pixelData,0));
        // Indexed legacy sprite writer, which is not selected by modern Sprite entrypoints.
        ops.add(g->g.plot_trans_scale_lookup_with_mask(new byte[]{0,1,2,3},0x44aa88,0,0,
            new int[]{0,0,0xffffff,0x808080},2,0,65536,3<<16,65536,g.pixelData,0,0,0,3*W,2,2));
        return ops;
    }
    static int[] background(int mode){
        int[] b=new int[W*H];for(int i=0;i<b.length;i++)b[i]=mode==0?0:mode==1?0xffffff:
            mode==2?0x808080:((i/W+i%W)&1)==0?0x103050:0xf0d0b0;return b;
    }
    static void affine(Draw op,boolean interlace,boolean clip,int allowed)throws Exception{
        GraphicsController overlay=graphics();overlay.interlace=interlace;
        if(clip)overlay.setClip(2,W-3,H-2,1);
        overlay.setWorldOverlayCoverageEnabled(true);check(overlay.beginTransparentWorldOverlay(),"begin overlay");
        op.run(overlay);WorldOverlayCoverage.Frame frame=overlay.borrowWorldOverlayCoverage();
        check(frame.valid&&frame.transparent,"pointwise frame valid");
        check(frame.ownerPixels==overlay.pixelData&&frame.width==W&&frame.height==H,"exact source identity");
        for(int bg=0;bg<4;bg++){
            GraphicsController software=graphics();software.interlace=interlace;if(clip)software.setClip(2,W-3,H-2,1);
            int[] base=background(bg);System.arraycopy(base,0,software.pixelData,0,base.length);op.run(software);
            for(int i=0;i<base.length;i++){
                check(frame.transmittance[i]>=0f&&frame.transmittance[i]<=1f,"finite bounded T");
                for(int shift:new int[]{0,8,16}){
                    int actual=(software.pixelData[i]>>shift)&255;
                    int flat=Math.min(255,Math.max(0,Math.round(((overlay.pixelData[i]>>shift)&255)
                        +frame.transmittance[i]*((base[i]>>shift)&255))));
                    int error=Math.abs(actual-flat);maxError=Math.max(maxError,error);squareError+=(long)error*error;channels++;
                    check(error<=allowed,"affine error "+error+" > "+allowed+" at "+i+" bg="+bg+" interlace="+interlace);
                }
            }
        }
    }
    static String nativeOracle(List<Draw> ops)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(boolean interlace:new boolean[]{false,true})for(boolean clip:new boolean[]{false,true})for(Draw op:ops){
            GraphicsController g=graphics();g.interlace=interlace;if(clip)g.setClip(2,W-3,H-2,1);
            int[] base=background(3);System.arraycopy(base,0,g.pixelData,0,base.length);op.run(g);
            ByteBuffer bytes=ByteBuffer.allocate(g.pixelData.length*4);for(int pixel:g.pixelData)bytes.putInt(pixel);hash.update(bytes.array());
        }
        GraphicsController g=graphics();Arrays.fill(g.pixelData,0xffffff);
        for(int i=0;i<50;i++){g.beginRenderer2DFrame();g.fade2black(0);ByteBuffer b=ByteBuffer.allocate(g.pixelData.length*4);for(int pixel:g.pixelData)b.putInt(pixel);hash.update(b.array());}
        g.a(2,1,1,1,0,2,1);g.storeSpriteVert(0,0,0,2,2);
        g.copyPixelDataToSurface(GraphicsController.SPRITE_LAYER.MINIMAP,0,0,2,2);
        ByteBuffer captured=ByteBuffer.allocate(16);for(int pixel:g.spriteVerts[0].getPixels())captured.putInt(pixel);hash.update(captured.array());
        StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format("%02x",b&255));return text.toString();
    }
    static int exactFade(int pixel){int c=pixel&0xffffff;return ((c>>>4)&0x0f0f0f)+((c&0xf8f8f9)>>>3)+((c&0xfefeff)>>>1)+((c>>>2)&0x803f3f3f);}
    static int[] flatten(WorldOverlayCoverage.Frame frame,int[] base){
        int[] result=new int[base.length];
        for(int i=0;i<result.length;i++)for(int shift:new int[]{0,8,16})result[i]|=Math.min(255,Math.round(
            ((frame.ownerPixels[i]>>shift)&255)+frame.transmittance[i]*((base[i]>>shift)&255)))<<shift;
        return result;
    }
    static void retainedProtocol()throws Exception{
        GraphicsController g=graphics();g.setWorldOverlayCoverageEnabled(true);g.beginRenderer2DFrame();g.beginTransparentWorldOverlay();
        int[] lastFinal=background(1),lastBase=null;WorldOverlayCoverage.Frame fresh=g.borrowWorldOverlayCoverage();
        long generation=fresh.generation,sequence=fresh.frameSequence;
        check(fresh.mode==WorldOverlayCoverage.MODE_WORLD,"fresh world mode");
        // GPU-reference simulation: exact packed fade always reads prior FULL output.
        for(int tick=0;tick<50;tick++){
            g.beginRenderer2DFrame();WorldOverlayCoverage.Frame before=g.borrowWorldOverlayCoverage();g.fade2black(0);
            WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();
            check(f.valid&&f.mode==WorldOverlayCoverage.MODE_RETAINED_FADE&&f.retainedFadeCount==1,"retained first fade request");
            check(f.frameSequence>sequence&&f.generation==generation,"frame token advances, retained owner stable");
            check(!g.isWorldOverlayCoverageCurrent(before),"pre-fade mode descriptor expires");sequence=f.frameSequence;
            for(int i=0;i<g.pixelData.length;i++)check(g.pixelData[i]==0&&f.transmittance[i]==1f,"fade resets UI delta only");
            lastBase=lastFinal.clone();for(int i=0;i<lastBase.length;i++)lastBase[i]=exactFade(lastBase[i]);
            // Exact repeated white fade: no accumulating 15/16 approximation.
            lastFinal=flatten(f,lastBase);
            GraphicsController nativeFade=graphics();Arrays.fill(nativeFade.pixelData,0xffffff);for(int j=0;j<=tick;j++)nativeFade.fade2black(0);
            check(lastFinal[0]==nativeFade.pixelData[0],"repeated exact retained fade "+tick);
        }
        g.drawBox(0,0,2,2,0x123456);WorldOverlayCoverage.Frame delta=g.borrowWorldOverlayCoverage();
        int[] presented=flatten(delta,lastBase);
        g.beginRenderer2DFrame();WorldOverlayCoverage.Frame retained=g.borrowWorldOverlayCoverage();
        check(retained.mode==WorldOverlayCoverage.MODE_RETAINED_BASE&&retained.retainedFadeCount==0,"no-fade retention reuses base");
        check(Arrays.equals(presented,flatten(retained,lastBase)),"delta is reapplied to cached base, not previous full image");
        g.fade2black(0);WorldOverlayCoverage.Frame faded=g.borrowWorldOverlayCoverage();
        int[] nextBase=presented.clone();for(int i=0;i<nextBase.length;i++)nextBase[i]=exactFade(nextBase[i]);
        check(Arrays.equals(nextBase,flatten(faded,nextBase)),"next fade includes previously presented UI in full source");
        g.drawBoxAlpha(0,0,3,3,0xffffff,128);
        GraphicsController expectedDelta=graphics();System.arraycopy(nextBase,0,expectedDelta.pixelData,0,nextBase.length);
        expectedDelta.drawBoxAlpha(0,0,3,3,0xffffff,128);int[] withDelta=flatten(g.borrowWorldOverlayCoverage(),nextBase);
        for(int i=0;i<withDelta.length;i++)for(int shift:new int[]{0,8,16})check(Math.abs(((withDelta[i]>>shift)&255)-((expectedDelta.pixelData[i]>>shift)&255))<=1,"post-fade UI delta");
        g.beginRenderer2DFrame();check(Arrays.equals(withDelta,flatten(g.borrowWorldOverlayCoverage(),nextBase)),"semi-transparent delta not doubled on retained frame");
        g.fade2black(0);g.drawBoxAlpha(0,0,3,3,0xffffff,128);g.fade2black(0);
        check(!g.borrowWorldOverlayCoverage().valid,"fade after delta writes fails closed");
        g.beginTransparentWorldOverlay();g.beginRenderer2DFrame();
        for(int i=0;i<WorldOverlayCoverage.MAX_RETAINED_FADES;i++)g.fade2black(0);
        check(g.borrowWorldOverlayCoverage().valid&&g.borrowWorldOverlayCoverage().retainedFadeCount==16,"bounded consecutive fades before drawing");
        g.fade2black(0);check(!g.borrowWorldOverlayCoverage().valid,"excess fades rejected");
        g.beginTransparentWorldOverlay();check(g.borrowWorldOverlayCoverage().mode==WorldOverlayCoverage.MODE_WORLD,"fresh scene resets retained mode");
        g.beginRenderer2DFrame();g.drawBox(0,0,1,1,0);g.fade2black(0);check(!g.borrowWorldOverlayCoverage().valid,"fade after earlier ordinary draw rejected");
        g.beginOpaqueWorldOverlay();Arrays.fill(g.pixelData,0xffffff);g.fade2black(0);
        check(g.pixelData[0]==exactFade(0xffffff)&&g.borrowWorldOverlayCoverage().mode==WorldOverlayCoverage.MODE_OPAQUE,"legacy opaque fade unaffected");
    }
    public static void main(String[] args)throws Exception{
        Config.S_WANT_CUSTOM_SPRITES=true;List<Draw> ops=operations();
        if(args.length>0&&args[0].equals("native")){System.out.println("NATIVE_RGB_SHA256="+nativeOracle(ops));return;}
        retainedProtocol();
        for(boolean interlace:new boolean[]{false,true})for(boolean clip:new boolean[]{false,true})for(Draw op:ops)affine(op,interlace,clip,1);
        // A real UI-like overlap sequence: window, muted panel, sprite, AA label, border.
        affine(g->{g.drawBoxAlpha(2,2,25,23,0xffffff,128);g.drawBoxAlpha(5,5,20,16,0x202020,160);
            g.drawSprite(sprite(false),4,4,12,14,0,128);
            call(g,"plotLetterAntialiased",new byte[]{31,64,(byte)128,(byte)229,(byte)230,(byte)255},0xffffff,6,7+7*W,1,0,W-6,g.pixelData,0);
            g.drawBoxBorder(2,25,2,23,0);},false,false,4);
        GraphicsController owner=graphics(),scratch=graphics();owner.setWorldOverlayCoverageEnabled(true);
        WorldOverlayCoverage.Frame initial=owner.borrowWorldOverlayCoverage();check(initial.valid&&!initial.transparent&&initial.transmittance==null,"initial image opaque");
        owner.beginTransparentWorldOverlay();WorldOverlayCoverage.Frame frame=owner.borrowWorldOverlayCoverage();float[] plane=frame.transmittance;
        check(!owner.isWorldOverlayCoverageCurrent(initial),"old opaque view expires");
        owner.beginRenderer2DFrame();check(!owner.isWorldOverlayCoverageCurrent(frame)&&owner.borrowWorldOverlayCoverage().transmittance==plane,"retained pixels keep T, old frame token expires");
        scratch.drawBox(0,0,W,H,0);check(scratch.borrowWorldOverlayCoverage()==null,"scratch stays plain RGB");
        call(owner,"plotLetter",0,scratch.pixelData,0,W-1,1,1,0,new byte[]{1},0);
        check(plane[0]==1f,"non-owner destination leaves T untouched");
        owner.drawBox(0,0,1,1,0);check(plane[0]==0f,"opaque real black");
        owner.beginTransparentWorldOverlay();owner.drawSprite(new Sprite(new int[]{0xff000000,0},2,1),3,3);
        check(plane[3+3*W]==0f&&plane[4+3*W]==1f,"ARGB black is opaque, raw-zero sprite is transparent");
        owner.beginTransparentWorldOverlay();check(owner.borrowWorldOverlayCoverage().transmittance==plane,"viewport plane reused");
        owner.fade2black(0);check(!owner.borrowWorldOverlayCoverage().valid,"fade immediately after fresh world is unsupported");
        owner.beginOpaqueWorldOverlay();owner.a(2,1,1,1,0,2,1);owner.storeSpriteVert(0,0,0,2,2);
        check(owner.borrowWorldOverlayCoverage().valid&&!owner.borrowWorldOverlayCoverage().transparent,"ordinary login blur/capture unchanged");
        owner.beginTransparentWorldOverlay();owner.a(2,1,1,1,0,2,1);check(!owner.borrowWorldOverlayCoverage().valid,"active nonlocal blur invalidates");
        owner.beginTransparentWorldOverlay();owner.storeSpriteVert(0,0,0,2,2);check(!owner.borrowWorldOverlayCoverage().valid,"active capture invalidates");
        owner.beginTransparentWorldOverlay();owner.copyPixelDataToSurface(GraphicsController.SPRITE_LAYER.MINIMAP,0,0,2,2);check(!owner.borrowWorldOverlayCoverage().valid,"readback invalidates");
        owner.beginTransparentWorldOverlay();owner.invalidateWorldOverlayCoverage("external debug write");check(!owner.borrowWorldOverlayCoverage().valid,"external guard");
        owner.beginTransparentWorldOverlay();frame=owner.borrowWorldOverlayCoverage();owner.pixelData=owner.pixelData.clone();
        check(!owner.isWorldOverlayCoverageCurrent(frame)&&!owner.borrowWorldOverlayCoverage().valid,"raw buffer replacement invalidates");
        owner.beginTransparentWorldOverlay();frame=owner.borrowWorldOverlayCoverage();owner.resize(10,12);
        check(!owner.isWorldOverlayCoverageCurrent(frame),"resize stale view");check(owner.borrowWorldOverlayCoverage().transmittance==null,"resize released plane");
        owner.beginTransparentWorldOverlay();check(owner.borrowWorldOverlayCoverage().transmittance.length==120,"new dimensions exact");
        owner.beginSoftwareOwnedRenderer2DFrame();check(!owner.borrowWorldOverlayCoverage().transparent,"software loading image opaque");
        frame=owner.borrowWorldOverlayCoverage();owner.setWorldOverlayCoverageEnabled(false);check(owner.borrowWorldOverlayCoverage()==null,"disable releases");
        owner.setWorldOverlayCoverageEnabled(true);check(!owner.isWorldOverlayCoverageCurrent(frame),"new opt-in owner expires old opaque view");
        owner.beginTransparentWorldOverlay();frame=owner.borrowWorldOverlayCoverage();owner.clearWorldOverlayCoverage();check(!owner.isWorldOverlayCoverageCurrent(frame),"clear expires view");
        WorldOverlayCoverage bounded=new WorldOverlayCoverage();check(!bounded.begin(new int[1],1,WorldOverlayCoverage.MAX_PIXELS+1),"oversized viewport rejected");
        check(!bounded.borrow(new int[1],1,1).valid,"oversized view invalid");
        // Demonstrate why retained legacy fade cannot use a simple affine multiplier.
        GraphicsController fade=graphics();Arrays.fill(fade.pixelData,0xffffff);for(int i=0;i<30;i++)fade.fade2black(0);
        int exact=fade.pixelData[0]&255,approx=Math.round(255*(float)Math.pow(15.0/16,30));check(approx-exact>15,"repeated fade divergence quantified");
        System.out.println("FADE30 exact="+exact+" naiveAffine="+approx);
        System.out.println("PASS "+checks+" coverage checks; "+ops.size()+" real raster operations, max ordinary reconstruction error="+maxError+" RMS="+Math.sqrt((double)squareError/channels));
    }
}
