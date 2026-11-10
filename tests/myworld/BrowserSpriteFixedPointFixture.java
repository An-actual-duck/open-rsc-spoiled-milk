package orsc.graphics.two;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import com.openrsc.client.model.Sprite;
import orsc.BrowserFrameSinkRegistry;
import orsc.Config;
import orsc.graphics.*;
import orsc.graphics.three.Renderer3DFrame;
/** Same fixture executes in isolated JVMs against literal frozen/current GraphicsController. */
public final class BrowserSpriteFixedPointFixture {
    static final int W=64,H=64;static int fast,fallback,composites;
    static Field field(String n)throws Exception{Field f=GraphicsController.class.getDeclaredField(n);f.setAccessible(true);return f;}
    static void set(Object g,String n,Object v)throws Exception{field(n).set(g,v);}
    static Renderer3DFrame frame()throws Exception{return SceneSpriteRasterPolicyFixture.frame();}
    static final class Actor extends GraphicsController {
        Sprite source;boolean mirror;int skew,opacity=255;
        Actor(Sprite s){super(W,H,1);source=s;}
        @Override public void drawEntity(int id,int x,int y,int w,int h,int move,int top,int pick){
            withRenderer2DLegacySpriteId(5,()->drawSpriteClipping(source,x,y,w,h,0x44aa88,0xff7744,0x3355ff,mirror,skew,1,(opacity<<24)|0xeeeeee));
        }
    }
    static void begin(Actor g,Renderer3DFrame f,GraphicsController.SceneSpriteRasterPolicy policy)throws Exception{
        g.beginRenderer2DFrame();g.setWorldOverlayCoverageEnabled(true);g.beginTransparentWorldOverlay();g.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);
        set(g,"sceneSpriteRasterPolicy",policy);set(g,"renderer2DSceneFrame",f);set(g,"renderer2DSceneSpriteAnchorIndex",0);set(g,"renderer2DSceneSpriteDrawOrder",7);set(g,"renderer2DLegacySpriteId",5);
    }
    static void value(DataOutputStream out,Object value)throws Exception{
        if(value==null){out.writeUTF("null");return;}out.writeUTF(value.getClass().getName());
        if(value instanceof Float){out.writeInt(Float.floatToRawIntBits((Float)value));return;}
        if(value instanceof Double){out.writeLong(Double.doubleToRawLongBits((Double)value));return;}
        if(value instanceof Number){out.writeLong(((Number)value).longValue());return;}
        if(value instanceof Boolean){out.writeBoolean((Boolean)value);return;}
        if(value instanceof Enum||value instanceof String){out.writeUTF(value.toString());return;}
        if(value instanceof Sprite){Sprite s=(Sprite)value;out.writeInt(s.getWidth());out.writeInt(s.getHeight());out.writeInt(s.getXShift());out.writeInt(s.getYShift());out.writeInt(s.getSomething1());out.writeInt(s.getSomething2());out.writeBoolean(s.requiresShift());for(int p:s.getPixels())out.writeInt(p);return;}
        Field[] fields=value.getClass().getDeclaredFields();Arrays.sort(fields,Comparator.comparing(Field::getName));
        for(Field f:fields)if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);out.writeUTF(f.getName());value(out,f.get(value));}
    }
    @SuppressWarnings("unchecked") static void write(Actor g,DataOutputStream out,boolean fullPixels)throws Exception{
        List<Renderer2DFrame.SpriteCommand> commands=(List<Renderer2DFrame.SpriteCommand>)field("renderer2DSpriteCommands").get(g);out.writeInt(commands.size());
        for(Renderer2DFrame.SpriteCommand c:commands){if(c.getSprite()!=g.source)throw new AssertionError("Source sprite identity changed");value(out,c);}
        for(String name:new String[]{"renderer2DCaptureAttempts","renderer2DCaptureAccepted","renderer2DCaptureSkippedInvalid","renderer2DCaptureSkippedSource","renderer2DCaptureSkippedBounds","renderer2DCaptureSkippedTransform","renderer2DCommandSequence","sceneSpriteLastLayerEmpty","sceneSpriteRasterFailure"})value(out,field(name).get(g));
        WorldOverlayCoverage.Frame coverage=g.borrowWorldOverlayCoverage();out.writeBoolean(coverage.valid);out.writeInt(coverage.mode);
        if(fullPixels){for(int p:g.pixelData)out.writeInt(p);for(float t:coverage.transmittance)out.writeInt(Float.floatToRawIntBits(t));}
        else {for(int p:g.pixelData)if(p!=0)throw new AssertionError("Capture painted RGB");for(float t:coverage.transmittance)if(t!=1f)throw new AssertionError("Capture changed coverage");}
        boolean bounded=!commands.isEmpty();for(Renderer2DFrame.SpriteCommand c:commands)bounded&=c.getWidth()>0&&c.getHeight()>0&&(long)c.getWidth()*c.getHeight()<=65536&&c.getWidth()<=512&&c.getHeight()<=512;
        out.writeBoolean(bounded);if(bounded){Class<?> builder=Class.forName("orsc.OpenGLSpriteTextureBuilder");Method m=builder.getDeclaredMethod("buildCompositeCharacterSpriteTexture",List.class);m.setAccessible(true);Object composite=m.invoke(null,commands);out.writeBoolean(composite!=null);if(composite!=null){for(String n:new String[]{"x","y","width","height"}){Field f=composite.getClass().getDeclaredField(n);f.setAccessible(true);out.writeInt(f.getInt(composite));}Field t=composite.getClass().getDeclaredField("textureData");t.setAccessible(true);Object texture=t.get(composite);Method pixels=texture.getClass().getDeclaredMethod("pixels");pixels.setAccessible(true);ByteBuffer bytes=(ByteBuffer)pixels.invoke(texture);out.writeInt(bytes.remaining());while(bytes.hasRemaining())out.writeByte(bytes.get());composites++;}}
    }
    static boolean expected(int[] a,boolean mirror,boolean gpu){
        int y=a[0]/W,col=a[1],w=a[2],h=a[3],sx=a[4],sy=a[5],dx=a[6],dy=a[7],skew=a[8];
        if(!gpu||a[0]<0||mirror||skew!=0||y<0||y>32767||col<0||col>0x3fffffff||w<1||w>32767||h<1||h>32767||sx<0||sy<0||dx<0||dy<0)return false;
        BigInteger max=BigInteger.valueOf(0x3fffffff),x=BigInteger.valueOf(w-1).multiply(BigInteger.valueOf(dx)).add(BigInteger.valueOf(sx)),z=BigInteger.valueOf(h-1).multiply(BigInteger.valueOf(dy)).add(BigInteger.valueOf(sy));
        return x.compareTo(max)<=0&&z.compareTo(max)<=0;
    }
    static int proofBoundaries(Actor g,Method gate)throws Exception {
        if(gate==null)return 0;int checked=0;g.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);
        set(g,"sceneSpriteRasterPolicy",GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);
        int bound=0x3fffffff;
        for(int width:new int[]{1,2,3,16384,32767,32768})
        for(int start:new int[]{0,1,bound-1,bound,bound+1,Integer.MAX_VALUE})
        for(int scale:new int[]{0,1,32768,65536,0x1fffffff,bound,Integer.MAX_VALUE})
        for(boolean x:new boolean[]{false,true}) {
            int[] a={10*W,10<<16,x?width:4,x?4:width,x?start:0,x?0:start,x?scale:0,x?0:scale,0};
            boolean actual=(Boolean)gate.invoke(g,10,a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],false);
            if(actual!=expected(a,false,true))throw new AssertionError("Exact boundary proof differs");checked++;
        }
        for(int y:new int[]{-1,0,32767,32768,Integer.MAX_VALUE})for(int column:new int[]{-1,0,bound,bound+1,Integer.MAX_VALUE}){
            boolean actual=(Boolean)gate.invoke(g,y,column,4,4,0,0,1,1,0,false);
            if(actual!=(y>=0&&y<=32767&&column>=0&&column<=bound))throw new AssertionError("Destination boundary proof differs");checked++;
        }
        BrowserFrameSinkRegistry.install(null);
        if((Boolean)gate.invoke(g,10,10<<16,4,4,0,0,65536,65536,0,false))throw new AssertionError("Native owner used browser math");
        BrowserFrameSinkRegistry.install((image,c,f)->{});return checked+1;
    }
    public static void main(String[] args)throws Exception{
        Config.S_WANT_CUSTOM_SPRITES=true;
        BrowserFrameSinkRegistry.install((image,c,f)->{});
        Method capture=GraphicsController.class.getDeclaredMethod("recordRenderer2DMaskedScaledSprite",Sprite.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,boolean.class,RendererSpriteTransform.class);capture.setAccessible(true);
        Method gate=null;try{gate=GraphicsController.class.getDeclaredMethod("canUseBrowserSpriteIntMath",int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,boolean.class);gate.setAccessible(true);}catch(NoSuchMethodException baseline){}
        Sprite sprite=new Sprite(new int[]{0,0xff000000,0x808080,0xff4444,0x998877,0xffffff,0x112233,0x334455,0x557799,0x123456,0,0xff7788,0xffffff,0xcccccc,0x8899aa,0},4,4);sprite.setSomething(4,4);Actor g=new Actor(sprite);
        Random r=new Random(618523);int[] extremes={Integer.MIN_VALUE,Integer.MAX_VALUE,-1,0,1,32767,32768,65535,65536,0x3ffffffe,0x3fffffff,0x40000000};
        int cases=12000;int boundaries=proofBoundaries(g,gate);
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){
            out.writeInt(cases);
            for(int trial=0;trial<cases;trial++){
                int w=1+r.nextInt(60),h=1+r.nextInt(60);int[] a={r.nextInt(60)*W,r.nextInt(60)<<16,w,h,0,0,(4<<16)/w,(4<<16)/h,0};boolean mirror=false;
                if(trial%4==1){mirror=r.nextBoolean();a[1]=(r.nextInt(100)-30)<<16;a[8]=r.nextInt(50000)-25000;a[4]=r.nextInt(65536);a[5]=r.nextInt(65536);}
                if(trial%4==2){for(int n=0;n<a.length;n++)a[n]=extremes[r.nextInt(extremes.length)];mirror=r.nextBoolean();}
                if(trial%4==3){for(int n=0;n<a.length;n++)a[n]=r.nextInt();mirror=r.nextBoolean();}
                boolean gpu=trial%7!=0;Renderer3DFrame f=frame();begin(g,f,gpu?GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED:GraphicsController.SceneSpriteRasterPolicy.FORCE_SOFTWARE);
                RendererSpriteTransform transform=RendererSpriteTransform.legacyMasks(0x44aa88,0xff7744,0x3355ff,0xffeeeeee);
                if(gate!=null){boolean actual=a[0]>=0&&(Boolean)gate.invoke(g,a[0]/W,a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],mirror);if(actual!=expected(a,mirror,gpu))throw new AssertionError("Int proof differs from BigInteger at"+trial);if(actual)fast++;else fallback++;}
                boolean accepted=(Boolean)capture.invoke(g,sprite,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],mirror,transform);out.writeBoolean(accepted);write(g,out,false);f.release();
                if(trial%37==0)sprite.getPixels()[trial%sprite.getPixels().length]^=0x00442211;
            }
            // Actual public clipping/raster+coverage and owned snapshot path, including shifted/mirrored/skewed actors.
            for(int trial=0;trial<600;trial++){
                Renderer3DFrame f=frame();begin(g,f,GraphicsController.SceneSpriteRasterPolicy.DEFAULT);g.mirror=trial%2==0;g.skew=trial%5==0?0:trial%3-1;g.opacity=trial%4==0?128:255;
                sprite.setRequiresShift(trial%5==0);sprite.setSomething(6,7);sprite.setShift(1,1);int x=r.nextInt(90)-20,y=r.nextInt(90)-20;int width=4+r.nextInt(40),height=4+r.nextInt(40);
                if(trial==0){g.mirror=false;g.skew=0;g.opacity=255;sprite.setRequiresShift(false);x=10;y=10;width=16;height=20;}
                g.drawSceneEntity(5,x,y,width,height,0,g.skew,0,0,7,f,trial%3==0?GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED:GraphicsController.SceneSpriteRasterPolicy.FORCE_SOFTWARE);write(g,out,true);f.release();
            }
        }
        BrowserFrameSinkRegistry.install(null);
        if(gate!=null&&(fast<1000||fallback<1000))throw new AssertionError("Branches not exercised");
        System.out.println("PASS12000 exact capture inputs +600 actual clipping/RGB/T/composite cases; fast="+fast+", fallback="+fallback+", composites="+composites+", BigIntegerBoundaries="+boundaries);
    }
}
