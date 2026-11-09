package orsc;

import com.openrsc.client.model.Sprite;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DFrame.SpriteCommand;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;

/** Uses the production compositor as the independent pixel/bounds oracle. */
public final class BrowserWorldSpriteCacheFixture {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static List<SpriteCommand> one(Sprite sprite) {
        return Collections.singletonList(new SpriteCommand(sprite, 5, -3, 7, 9, 255));
    }
    private static SpriteCommand translate(SpriteCommand c, int dx, int dy) {
        if (c == null) return null;
        return new SpriteCommand(c.getSprite(), c.getX()+dx, c.getY()+dy, c.getWidth(), c.getHeight(),
            c.getSourceX(), c.getSourceY(), c.getSourceWidth(), c.getSourceHeight(),
            c.getSourceStartX16(), c.getSourceStartY16(), c.getSourceScaleX16(), c.getSourceScaleY16(),
            c.getAlpha(), c.getTransform(), c.getTopX16()+(dx<<16), c.getBottomX16()+(dx<<16),
            c.isMirrorX(), c.requiresOrderedReplay(), 999, 123, 456, Renderer2DFrame.Phase.UNKNOWN, 789);
    }
    private static byte[] reference(List<SpriteCommand> commands) {
        CompositeWorldSpriteTexture c = OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(commands);
        if (c == null) return null;
        byte[] bytes = new byte[c.width*c.height*4]; c.textureData.pixels().get(bytes); return bytes;
    }
    private static BrowserWorldSpriteCache.Result parity(BrowserWorldSpriteCache cache,
            List<SpriteCommand> commands, OpenGLPixelWorkspace scratch) {
        CompositeWorldSpriteTexture expected = OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(commands);
        BrowserWorldSpriteCache.Result actual = cache.compose(commands, scratch);
        check((expected == null) == (actual == null), "null parity");
        if (expected != null) {
            byte[] bytes = new byte[expected.width*expected.height*4]; expected.textureData.pixels().get(bytes);
            check(expected.x == actual.x && expected.y == actual.y && expected.width == actual.width
                && expected.height == actual.height, "bounds parity");
            check(Arrays.equals(bytes, actual.rgba), "RGBA parity");
        }
        return actual;
    }
    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "unsupported input must fail closed");
    }
    public static void main(String[] args) throws Exception {
        BrowserWorldSpriteCache cache = new BrowserWorldSpriteCache();
        OpenGLPixelWorkspace scratch = new OpenGLPixelWorkspace();
        Random random = new Random(992713);
        for (int sample=0; sample<600; sample++) {
            List<SpriteCommand> layers = new ArrayList<SpriteCommand>();
            for (int l=0; l<1+random.nextInt(6); l++) {
                int sw=2+random.nextInt(10), sh=2+random.nextInt(10), w=2+random.nextInt(25), h=2+random.nextInt(25);
                int[] pixels = new int[sw*sh];
                for (int p=0; p<pixels.length; p++) pixels[p]=random.nextInt(4)==0 ? 0 : random.nextInt(0x1000000);
                pixels[0]=0xff000000; // legitimate opaque black, not the transparent sentinel
                int x=random.nextInt(21)-10, y=random.nextInt(21)-10;
                int startX=(random.nextInt(sw+2)-1)<<16, scaleX=(sw<<16)/w;
                boolean mirror=random.nextBoolean(); if(mirror) {startX=(sw-1)<<16; scaleX=-scaleX;}
                RendererSpriteTransform transform = random.nextBoolean() ? RendererSpriteTransform.IDENTITY
                    : RendererSpriteTransform.legacyMasks(0xaaffcc, 0xff7799, 0x3344ff, 0xddeeffaa);
                layers.add(new SpriteCommand(new Sprite(pixels,sw,sh),x,y,w,h,0,0,sw,sh,
                    startX,random.nextInt(3)*65536-65536,scaleX,(sh<<16)/h,1+random.nextInt(255),transform,
                    (x<<16)+random.nextInt(65536),(x<<16)+random.nextInt(5*65536)-2*65536,mirror,true,
                    l,0,l,Renderer2DFrame.Phase.UNKNOWN,l));
                if (random.nextInt(5)==0) layers.add(null);
            }
            BrowserWorldSpriteCache.Result first=parity(cache,layers,scratch);
            long hits=cache.getHits();
            BrowserWorldSpriteCache.Result same=parity(cache,layers,scratch);
            List<SpriteCommand> moved=new ArrayList<SpriteCommand>();
            for(SpriteCommand c:layers) moved.add(translate(c,43,-29));
            BrowserWorldSpriteCache.Result shifted=parity(cache,moved,scratch);
            if(first!=null) {
                check(cache.getHits()==hits+2, "identical/translated cache hits");
                check(first.rgba==same.rgba && first.rgba==shifted.rgba, "translation shares owned RGBA");
                check(first.id>0 && first.id==same.id && first.id==shifted.id,"exact translated cache hits share positive identity");
                check(shifted.x==first.x+43 && shifted.y==first.y-29, "translation updates position");
                byte[] saved=first.rgba.clone();
                OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(one(new Sprite(new int[]{0xabcdef},1,1)),scratch);
                check(Arrays.equals(saved,first.rgba),"cache must not retain workspace bytes");
            }
            check(cache.getCacheBytes()<=BrowserWorldSpriteCache.MAX_CACHE_BYTES && cache.getEntryCount()<=128,"default bounds");
        }
        // Mutating the original raw array, setPixel and replacing pixels must invalidate safely.
        Sprite mutable=new Sprite(new int[]{0x123456,0xabcdef},2,1);
        cache.clear(); List<SpriteCommand> commands=one(mutable);
        BrowserWorldSpriteCache.Result a=parity(cache,commands,scratch); byte[] saved=a.rgba.clone();
        long misses=cache.getMisses();
        mutable.getPixels()[0]=0xaabbcc; check(parity(cache,commands,scratch).id!=a.id,"mutation receives new identity");
        mutable.setPixel(1,0x445566); parity(cache,commands,scratch);
        mutable.setPixels(new int[]{0x778899,0x445566}); parity(cache,commands,scratch);
        check(cache.getMisses()==misses+3,"all mutation APIs invalidate");
        check(Arrays.equals(a.rgba,saved),"stored RGBA independent of subsequent sprite changes");
        long hits=cache.getHits();
        parity(cache,one(new Sprite(new int[]{0x778899,0x445566,0x999999},2,1)),scratch);
        check(cache.getHits()==hits+1,"equal active source content hits across identity and unused tail");
        // Deliberate Arrays.hashCode collision, not merely a random collision assumption.
        int[] p={0x112233,0x445566}, q={0x112234,0x445547};
        check(Arrays.hashCode(p)==Arrays.hashCode(q),"collision fixture");
        cache.clear(); int collisionPredecessor=parity(cache,one(new Sprite(p,2,1)),scratch).id; misses=cache.getMisses();
        BrowserWorldSpriteCache.Result collision=parity(cache,one(new Sprite(q,2,1)),scratch);
        check(collision.id!=collisionPredecessor,"hash collision must not reuse residency identity");
        check(cache.getMisses()==misses+1 && !Arrays.equals(reference(one(new Sprite(p,2,1))),collision.rgba),"hash collision exact comparison");
        // Every pixel-input metadata group changes independently; ordered overlap also matters.
        Sprite sprite=new Sprite(new int[]{0x333333,0xff3333,0x3344aa,0xffffff},2,2);
        for(int variant=0;variant<11;variant++) {
            cache.clear(); SpriteCommand base=new SpriteCommand(sprite,1,2,6,7,255);
            parity(cache,Collections.singletonList(base),scratch); misses=cache.getMisses();
            SpriteCommand changed=new SpriteCommand(sprite,1,variant==0?3:2,variant==1?7:6,variant==2?8:7,
                variant==3?1:0,0,variant==3?1:2,2,variant==4?65536:0,variant==5?-65536:0,
                variant==6?-21845:21845,18724,variant==7?100:255,
                variant==8?RendererSpriteTransform.legacyMasks(0xff3322,0x44ffaa,0x123456,0xffaaffdd):RendererSpriteTransform.IDENTITY,
                65536,variant==9?130001:65536,variant==10,false,0,0,0,Renderer2DFrame.Phase.UNKNOWN,0);
            parity(cache,Collections.singletonList(changed),scratch);
            // Y-only changes are whole-composite translations and should hit.
            check(variant==0 ? cache.getMisses()==misses : cache.getMisses()==misses+1,"metadata identity group "+variant);
        }
        List<SpriteCommand> order=new ArrayList<SpriteCommand>();
        order.add(new SpriteCommand(sprite,0,0,8,8,100));
        order.add(new SpriteCommand(new Sprite(new int[]{0xaabbcc},1,1),0,0,8,8,170));
        cache.clear(); byte[] forward=parity(cache,order,scratch).rgba;
        Collections.reverse(order); byte[] backward=parity(cache,order,scratch).rgba;
        check(!Arrays.equals(forward,backward),"ordered alpha layers");
        // Bounded LRU, reuse A after B, and clear never retain old storage.
        BrowserWorldSpriteCache tiny=new BrowserWorldSpriteCache(4096,2);
        List<SpriteCommand> ca=one(new Sprite(new int[]{1},1,1)), cb=one(new Sprite(new int[]{2},1,1)), cc=one(new Sprite(new int[]{3},1,1));
        BrowserWorldSpriteCache.Result held=tiny.compose(ca,scratch);byte[] ar=held.rgba;
        tiny.compose(cb,scratch); check(tiny.compose(ca,scratch).rgba==ar,"A-B-cached A");
        tiny.compose(cc,scratch); misses=tiny.getMisses(); tiny.compose(cb,scratch);
        check(tiny.getMisses()==misses+1 && tiny.getEntryCount()==2,"LRU entry eviction");
        check(held.id!=tiny.compose(cb,scratch).id && Arrays.equals(ar,held.rgba),"evicted frame result remains strongly owned");
        int epoch=tiny.getEpoch();tiny.clear();check(tiny.getEpoch()>epoch,"clear advances session epoch"); check(tiny.getCacheBytes()==0 && tiny.getEntryCount()==0,"clear storage");
        check(tiny.compose(ca,scratch).rgba!=ar,"clear recomposes independent bytes");
        BrowserWorldSpriteCache bytesBound=new BrowserWorldSpriteCache(1400,20);
        for(List<SpriteCommand> item:Arrays.asList(ca,cb,cc)) {parity(bytesBound,item,scratch); check(bytesBound.getCacheBytes()<=1400,"byte eviction");}
        check(bytesBound.getEntryCount()==1,"byte budget counts keys and source copies");
        BrowserWorldSpriteCache oversized=new BrowserWorldSpriteCache(100,2);
        byte[] first=parity(oversized,ca,scratch).rgba, second=parity(oversized,ca,scratch).rgba;
        check(oversized.compose(ca,scratch).id!=oversized.compose(ca,scratch).id,"transient images never alias identities");
        check(first!=second && oversized.getEntryCount()==0 && oversized.getCacheBytes()==0,"oversized uncached fallback");
        BrowserWorldSpriteCache largeSources=new BrowserWorldSpriteCache(4096,2);
        parity(largeSources,one(new Sprite(new int[2048],2048,1)),scratch);
        check(largeSources.getEntryCount()==0,"transparent output stores no key");
        int[] large=new int[2048]; large[0]=0x112233;
        parity(largeSources,one(new Sprite(large,2048,1)),scratch);
        check(largeSources.getEntryCount()==0,"source copies counted even for tiny output");
        // Real snapshot overload; anchors/depth are deliberately outside the cache.
        Constructor<?> ctor=null;
        for(Constructor<?> candidate:Renderer3DFrame.WorldSpriteSnapshot.class.getDeclaredConstructors())
            if(candidate.getParameterTypes().length==4) ctor=candidate;
        check(ctor!=null,"snapshot production constructor"); ctor.setAccessible(true);
        Renderer3DFrame.WorldSpriteSnapshot snapshot=(Renderer3DFrame.WorldSpriteSnapshot)ctor.newInstance(27,null,null,null);
        Method add=snapshot.getClass().getDeclaredMethod("addLayer",SpriteCommand.class); add.setAccessible(true); add.invoke(snapshot,ca.get(0));
        cache.clear(); BrowserWorldSpriteCache.Result snap=cache.compose(snapshot,scratch);
        check(snap.rgba==cache.compose(ca,scratch).rgba,"snapshot/list key parity");
        reject(()->new BrowserWorldSpriteCache(BrowserWorldSpriteCache.MAX_CACHE_BYTES+1,2));
        reject(()->new BrowserWorldSpriteCache(100,257));
        reject(()->cache.compose(one(new Sprite(new int[0],1,1)),scratch));
        reject(()->cache.compose(Collections.singletonList(new SpriteCommand(sprite,0,0,2000,2000,255)),scratch));
        reject(()->cache.compose(Collections.singletonList(new SpriteCommand(sprite,0,Integer.MAX_VALUE,2,2,255)),scratch));
        reject(()->cache.compose(Collections.nCopies(257,ca.get(0)),scratch));
        check(cache.compose(Collections.<SpriteCommand>emptyList(),scratch)==null,"empty input");
        check(cache.compose(Collections.<SpriteCommand>singletonList(null),scratch)==null,"null layers");
        System.out.println("PASS "+checks+" production-reference cache checks; 600 seeded alpha/skew/mirror/clipping/recolor composites and integer translations");
    }
}
