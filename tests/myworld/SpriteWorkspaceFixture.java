package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Pixel oracle and ownership probes; runs baseline API against the frozen released JAR too. */
public final class SpriteWorkspaceFixture {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static byte[] bytes(ByteBuffer p){byte[] b=new byte[p.remaining()];p.get(b);return b;}
    static Object workspace()throws Exception {Constructor<?> c=Class.forName("orsc.OpenGLPixelWorkspace").getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
    static Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String name,Class<?>[] types,Object...args)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static CompositeWorldSpriteTexture build(List<Renderer2DFrame.SpriteCommand> layers,Object scratch)throws Exception {
        if(scratch==null)return OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(layers);
        Method m=OpenGLSpriteTextureBuilder.class.getDeclaredMethod("buildCompositeCharacterSpriteTexture",List.class,scratch.getClass());m.setAccessible(true);return (CompositeWorldSpriteTexture)m.invoke(null,layers,scratch);
    }
    static CompositeWorldSpriteTexture snapshot(List<Renderer2DFrame.SpriteCommand> layers,Object scratch)throws Exception {
        Constructor<?> c=null;for(Constructor<?> candidate:Renderer3DFrame.WorldSpriteSnapshot.class.getDeclaredConstructors())if(candidate.getParameterTypes().length==4)c=candidate;require(c!=null,"Snapshot constructor unavailable");c.setAccessible(true);Object s=c.newInstance(0,null,null,null);
        Method add=s.getClass().getDeclaredMethod("addLayer",Renderer2DFrame.SpriteCommand.class);add.setAccessible(true);for(Renderer2DFrame.SpriteCommand layer:layers)add.invoke(s,layer);
        if(scratch==null)return OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture((Renderer3DFrame.WorldSpriteSnapshot)s);
        Method m=OpenGLSpriteTextureBuilder.class.getDeclaredMethod("buildCompositeCharacterSpriteTexture",s.getClass(),scratch.getClass());m.setAccessible(true);return (CompositeWorldSpriteTexture)m.invoke(null,s,scratch);
    }
    static ByteBuffer padded(ByteBuffer source,int w,int h,int pad,Object scratch)throws Exception {
        if(scratch==null)return OpenGLTexturePixels.buildPaddedRgbaPixels(source,w,h,pad);
        Method m=OpenGLTexturePixels.class.getDeclaredMethod("buildPaddedRgbaPixels",ByteBuffer.class,int.class,int.class,int.class,scratch.getClass());m.setAccessible(true);return (ByteBuffer)m.invoke(null,source,w,h,pad,scratch);
    }
    static void digest(MessageDigest hash,CompositeWorldSpriteTexture c){
        if(c==null){hash.update((byte)0);return;}
        hash.update((c.x+","+c.y+","+c.width+","+c.height+","+c.textureData.visiblePixelCount+";").getBytes(java.nio.charset.StandardCharsets.UTF_8));hash.update(bytes(c.textureData.pixels()));
    }
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    public static void main(String[] args)throws Exception {
        boolean reuse=args.length>0&&args[0].equals("reuse");Object composite=reuse?workspace():null,padding=reuse?workspace():null;
        Random random=new Random(610314);MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(int sample=0;sample<1000;sample++){
            List<Renderer2DFrame.SpriteCommand> layers=new ArrayList<>();int count=1+random.nextInt(8);
            for(int layer=0;layer<count;layer++){
                int sw=1+random.nextInt(20),sh=1+random.nextInt(20);int[] pixels=new int[sw*sh];
                for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt(5)==0?0:random.nextInt(0x1000000);
                int x=random.nextInt(25)-12,y=random.nextInt(25)-12,w=1+random.nextInt(60),h=1+random.nextInt(60),alpha=random.nextInt(257);
                layers.add(new Renderer2DFrame.SpriteCommand(new Sprite(pixels,sw,sh),x,y,w,h,0,0,sw,sh,alpha,RendererSpriteTransform.IDENTITY,x<<16,(x+random.nextInt(15)-7)<<16,false));
            }
            CompositeWorldSpriteTexture c=build(layers,composite);digest(digest,c);
            CompositeWorldSpriteTexture s=snapshot(layers,composite);digest(digest,s);
            if(c!=null){
                ByteBuffer input=s.textureData.pixels();int pos=input.position(),limit=input.limit();
                for(int pad:new int[]{0,1,2})digest.update(bytes(padded(input,c.width,c.height,pad,padding)));
                require(input.position()==pos&&input.limit()==limit,"Padding changed caller view");
            }
        }
        // Exact RGBA, border replication and transparency independent of the implementation's oracle.
        ByteBuffer source=ByteBuffer.allocateDirect(8);source.putInt(0x112233ff).putInt(0x77889900).flip();
        ByteBuffer p=padded(source,2,1,1,padding);for(int y=0;y<3;y++)for(int x=0;x<4;x++)require(p.getInt((y*4+x)*4)==(x<2?0x112233ff:0x77889900),"RGBA byte order / padded edge");
        if(reuse){
            int[] a=(int[])call(composite,"clearedComposite",new Class[]{int.class},4096);Arrays.fill(a,-1);
            require(call(composite,"clearedComposite",new Class[]{int.class},20)==a,"Shrink allocated new composite");for(int i=0;i<20;i++)require(a[i]==0,"Stale transparent pixel");
            ByteBuffer b=(ByteBuffer)call(composite,"rgba",new Class[]{int.class},16384);b.order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer small=(ByteBuffer)call(composite,"rgba",new Class[]{int.class},80);require(b==small&&small.position()==0&&small.limit()==80&&small.order()==ByteOrder.BIG_ENDIAN,"Reused RGBA view wrong");
            Object other=workspace();require(call(other,"rgba",new Class[]{int.class},80)!=b,"Workspace owners alias");
            int max=1<<20;Object beforeInts=field(composite,"composite"),beforeBytes=field(composite,"rgba");
            int[] huge=(int[])call(composite,"clearedComposite",new Class[]{int.class},max+1);ByteBuffer hugeBytes=(ByteBuffer)call(composite,"rgba",new Class[]{int.class},max*4+4);
            require(huge.length==max+1&&hugeBytes.capacity()==max*4+4&&field(composite,"composite")==beforeInts&&field(composite,"rgba")==beforeBytes,"Oversize retained / wrong fallback");
            List<Renderer2DFrame.SpriteCommand> one=Collections.singletonList(new Renderer2DFrame.SpriteCommand(new Sprite(new int[]{0x123456},1,1),0,0,1,1,255));
            CompositeWorldSpriteTexture independent=build(one,null);byte[] kept=bytes(independent.textureData.pixels());
            build(Collections.singletonList(new Renderer2DFrame.SpriteCommand(new Sprite(new int[]{0xabcdef},1,1),0,0,3,3,255)),composite);
            require(Arrays.equals(kept,bytes(independent.textureData.pixels())),"Independent API aliased scratch");
            call(composite,"clear",new Class[0]);call(composite,"clear",new Class[0]);require(((int[])field(composite,"composite")).length==0&&field(composite,"rgba")==null,"Scratch lifetime not released");
            OpenGLWorldSpriteDrawController controller=new OpenGLWorldSpriteDrawController(null,null,null,null);Object owned=field(controller,"compositeWorkspace");call(owned,"rgba",new Class[]{int.class},1024);controller.clearScratch();require(field(owned,"rgba")==null,"Controller cleanup retained workspace");
        }
        System.out.println("PIXEL_ORACLE_SHA256="+hex(digest.digest()));
        System.out.println("PASS 1000 seeded list/snapshot composites, alpha/order/shear, padding, transparency and "+(reuse?"bounded ownership/reuse/release":"released independent-buffer oracle"));
    }
}
