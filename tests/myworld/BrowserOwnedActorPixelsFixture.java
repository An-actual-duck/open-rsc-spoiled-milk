package orsc;

import com.openrsc.client.model.Sprite;
import java.nio.ByteBuffer;
import java.util.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DFrame.SpriteCommand;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DSettings;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;
import orsc.graphics.two.SpriteArchive.Subspace;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.SpriteArchive.Workspace;

/** Frozen pre-change compositor is the independent RGBA oracle. No GPU/timing. */
public final class BrowserOwnedActorPixelsFixture {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++; if (!value) throw new AssertionError(why);
    }
    private static Sprite sprite(int... pixels) { return new Sprite(pixels, pixels.length, 1); }
    private static List<SpriteCommand> one(Sprite s) {
        return Collections.singletonList(new SpriteCommand(s, 3, -2, 9, 7, 255));
    }
    private static SpriteCommand replace(SpriteCommand c, Sprite s) {
        return new SpriteCommand(s,c.getX(),c.getY(),c.getWidth(),c.getHeight(),
            c.getSourceX(),c.getSourceY(),c.getSourceWidth(),c.getSourceHeight(),
            c.getSourceStartX16(),c.getSourceStartY16(),c.getSourceScaleX16(),c.getSourceScaleY16(),
            c.getAlpha(),c.getTransform(),c.getTopX16(),c.getBottomX16(),c.isMirrorX(),
            c.requiresOrderedReplay(),c.getLegacySpriteId(),c.getPhase(),c.getSequence());
    }
    private static List<SpriteCommand> reference(List<SpriteCommand> commands) {
        List<SpriteCommand> result = new ArrayList<SpriteCommand>();
        for (SpriteCommand c : commands) {
            if (c == null) { result.add(null); continue; }
            Sprite s=c.getSprite(); Sprite.OwnedPixels owned=s.getOwnedPixels();
            int[] pixels;
            if (owned == null) pixels=s.getPixels().clone();
            else { pixels=new int[owned.length()]; for(int i=0;i<pixels.length;i++)pixels[i]=owned.get(i); }
            result.add(replace(c,new Sprite(pixels,s.getWidth(),s.getHeight())));
        }
        return result;
    }
    private static BrowserWorldSpriteCache.Result parity(BrowserWorldSpriteCache cache,
            List<SpriteCommand> commands, OpenGLPixelWorkspace scratch) {
        CompositeWorldSpriteTexture expected=FrozenActorSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(reference(commands));
        BrowserWorldSpriteCache.Result actual=cache.compose(commands,scratch);
        check((expected==null)==(actual==null),"null parity");
        if(expected!=null) {
            byte[] bytes=new byte[expected.width*expected.height*4]; expected.textureData.pixels().get(bytes);
            check(actual.x==expected.x&&actual.y==expected.y&&actual.width==expected.width&&actual.height==expected.height,"bounds parity");
            check(Arrays.equals(actual.rgba,bytes),"frozen RGBA parity");
        }
        return actual;
    }
    private static final class VirtualSprite extends Sprite {
        final int[] plane={0x447799,0x88aabb};
        VirtualSprite(){super(new int[]{0x111111,0x222222},2,1);}
        @Override public int[] getPixels(){return plane;}
    }
    private static final class VirtualFrame extends Frame {
        VirtualFrame(){super(2,1,false,0,0,2,1);}
        @Override public int[] getPixels(){return new int[]{0x123456,0xabcdef};}
    }
    private static void archive(String path) throws Exception {
        Workspace workspace=new Unpacker().unpackArchive(new java.io.File(path));
        check(workspace!=null,"actual archive decoded");
        Map<String,Map<String,Entry>> tree=new HashMap<String,Map<String,Entry>>();
        for(Subspace subspace:workspace.getSubspaces()) {
            Map<String,Entry> entries=new HashMap<String,Entry>();
            for(Entry entry:subspace.getEntryList())entries.put(entry.getID(),entry);
            tree.put(subspace.getName(),entries);
        }
        // Real staff cloning/recolouring occurs BEFORE the publication boundary.
        MaterialEquipmentSprites.install(tree.get("equipment"));
        Map<Frame,byte[]> old=new IdentityHashMap<Frame,byte[]>();
        List<Frame> before=new ArrayList<Frame>();List<Entry> sources=new ArrayList<Entry>();
        for(Map<String,Entry> entries:tree.values())for(Entry entry:entries.values()) {
            sources.add(entry);
            for(Frame frame:entry.getFrames()) {
                before.add(frame);ByteBuffer packed=frame.getSprite().pack();byte[] data=new byte[packed.remaining()];packed.get(data);old.put(frame,data);
            }
        }
        Renderer3DSettings.setBrowserPresentationAvailable(true);BrowserOwnedActorPixels.publish(tree);
        int at=0,owned=0,legacy=0;long pixels=0;
        for(Entry entry:sources)for(Frame frame:entry.getFrames()) {
            Frame previous=before.get(at++);ByteBuffer packed=frame.getSprite().pack();byte[] data=new byte[packed.remaining()];packed.get(data);
            check(Arrays.equals(old.get(previous),data),"actual archive pixels/metadata unchanged");
            check(frame.equals(previous),"actual Frame metadata/plane parity");
            Sprite.OwnedPixels source=frame.getSprite().getOwnedPixels();
            if(source!=null){owned++;pixels+=source.length();}else legacy++;
        }
        check(owned>1000&&legacy>0,"real actor coverage and generic fallback");
        System.out.println("PASS actual decoded/recoloured archive checks="+checks+" ownedFrames="+owned+" legacyFrames="+legacy+" ownedPixels="+pixels);
    }
    public static void main(String[] args) throws Exception {
        boolean enabled=Boolean.getBoolean("spoiledmilk.browserOwnedActorPixels");
        if(args.length>0&&args[0].equals("archive")){archive(args[1]);return;}
        if(args.length>0) {
            Frame original=new Frame(2,1,false,0,0,2,1);
            Entry entry=new Entry("actor",Entry.TYPE.NPC,null,1);entry.getFrames()[0]=original;
            Map<String,Entry> entries=new HashMap<String,Entry>();entries.put("actor",entry);
            Map<String,Map<String,Entry>> tree=new HashMap<String,Map<String,Entry>>();tree.put("npc",entries);
            Renderer3DSettings.setBrowserPresentationAvailable(false);BrowserOwnedActorPixels.publish(tree);
            check(entry.getFrames()[0]==original,"native publication disabled");
            Renderer3DSettings.setBrowserPresentationAvailable(true);BrowserOwnedActorPixels.publish(tree);
            check((entry.getFrames()[0]!=original)==enabled,"explicit private policy");
            System.out.println("PASS publication policy enabled="+enabled+" checks="+checks);return;
        }
        OpenGLPixelWorkspace scratch=new OpenGLPixelWorkspace();
        BrowserWorldSpriteCache cache=new BrowserWorldSpriteCache();
        int[] caller={0x123456,0xabcdef}; Sprite legacy=new Sprite(caller,2,1);
        check(legacy.getOwnedPixels()==null,"constructor aliases untrusted");
        Sprite owned=legacy.copyForOwnedActor();
        check(owned!=legacy&&owned.getOwnedPixels()!=null,"explicit detached publication");
        caller[0]=0x778899;
        check(legacy.getPixel(0)==caller[0]&&owned.getPixel(0)==0x123456,"old aliases and copy-in isolation");
        Object token=owned.getOwnedPixels().version();
        BrowserWorldSpriteCache.Result before=parity(cache,one(owned),scratch);
        check(parity(cache,one(owned),scratch).id==before.id,"owned repeat hit");
        owned.setPixel(0,0xaabbcc);
        check(token!=owned.getOwnedPixels().version(),"tracked edits advance token");
        check(parity(cache,one(owned),scratch).id!=before.id,"tracked edit invalidates");
        int[] saved=owned.getPixels();
        check(owned.getOwnedPixels()==null&&owned.copyForOwnedActor()==null,"raw escape permanently untrusted");
        before=parity(cache,one(owned),scratch); saved[1]=0x445566;
        check(parity(cache,one(owned),scratch).id!=before.id,"saved raw alias mutation after intervening hit");
        int[] replacement={0x667788,0x99aabb};owned.setPixels(replacement);
        check(owned.getPixels()==replacement&&owned.getOwnedPixels()==null,"setPixels preserves caller alias");
        before=parity(cache,one(owned),scratch); replacement[0]=0x223344;
        check(parity(cache,one(owned),scratch).id!=before.id,"replacement alias changes visible output");

        Frame original=new Frame(2,1,true,2,3,8,9);original.getPixels()[0]=0x334455;original.getPixels()[1]=0xaabbcc;
        Frame frame=original.copyForOwnedActor();
        check(frame!=original&&frame.getSprite().getOwnedPixels()!=null,"owned frame publication");
        check(frame.getSprite().getXShift()==2&&frame.getSprite().getSomething1()==8,"Sprite anchor metadata copied");
        original.getPixels()[0]=0x778899;check(frame.getSprite().getPixel(0)==0x334455,"old frame aliases detached explicitly");
        frame.getSprite().setPixel(1,0x668899);
        check(frame.clone().getPixels()[1]==0x668899,"nonescaping clone shares current plane values");
        check(frame.getSprite().getOwnedPixels()!=null,"clone does not revoke original");
        int[] frameAlias=frame.getPixels();
        check(frame.getSprite().getOwnedPixels()==null,"Frame exposure revokes Sprite trust");
        before=parity(cache,one(frame.getSprite()),scratch);frameAlias[0]=0x115577;
        check(parity(cache,one(frame.getSprite()),scratch).id!=before.id,"Frame saved alias changes cached sprite");
        check(frame.copyForOwnedActor()==frame&&frame.getSprite().getOwnedPixels()==null,"no reseal of escaped frame");
        frame=original.copyForOwnedActor();Sprite oldSprite=frame.getSprite();
        int[] separate={0x998877,0x112233};frame.changePixels(separate);
        check(frame.getPixels()==separate&&oldSprite.getPixel(0)==original.getSprite().getPixel(0),"Frame-only changePixels preserved");
        check(oldSprite.getOwnedPixels()!=null,"disconnected Frame writes cannot revoke unrelated Sprite plane");
        frame.changeDimensions(3,4);check(oldSprite.getWidth()==2&&oldSprite.getHeight()==1,"Frame-only dimensions preserved");
        Frame mismatched=new Frame(2,1,false,0,0,2,1);mismatched.changePixels(new int[]{3,4});
        check(mismatched.copyForOwnedActor()==mismatched,"legacy mismatched Frame fallback");
        VirtualFrame virtualFrame=new VirtualFrame();
        check(virtualFrame.copyForOwnedActor()==virtualFrame,"Frame subclass fallback");
        check(Arrays.equals(virtualFrame.clone().getPixels(),virtualFrame.getPixels()),"subclass clone retains virtual getter behaviour");
        VirtualSprite virtual=new VirtualSprite();check(virtual.copyForOwnedActor()==null,"Sprite subclass cannot certify");
        before=parity(cache,one(virtual),scratch);virtual.plane[0]=0xbbccdd;
        check(parity(cache,one(virtual),scratch).id!=before.id,"virtual Sprite backing is observed");
        Sprite malformed=new Sprite(new int[]{1},2,2);check(malformed.copyForOwnedActor()==null,"incomplete source fallback");
        check(new Sprite(null,2,2).copyForOwnedActor()==null,"null source fallback");
        Sprite auxiliary=sprite(0x556677,0x99aabb);auxiliary.setTransparentPixels(auxiliary.getPixels());
        auxiliary=auxiliary.copyForOwnedActor();check(auxiliary.getOwnedPixels()!=null,"auxiliary alias initially owned");
        before=parity(cache,one(auxiliary),scratch);int[] auxAlias=auxiliary.getTransparentPixels();
        check(auxiliary.getOwnedPixels()==null,"auxiliary raw alias revokes primary owner");
        auxAlias[0]=0xccddaa;check(parity(cache,one(auxiliary),scratch).id!=before.id,"auxiliary alias mutation invalidates");
        Sprite largeAux=sprite(1);largeAux.setUnknownByteArray(new byte[2097153]);
        check(largeAux.copyForOwnedActor()==null,"auxiliary publication size bounded");
        Sprite exposed=sprite(0x112244).copyForOwnedActor();Sprite.OwnedPixels reader=exposed.getOwnedPixels();
        before=parity(cache,one(exposed),scratch);reader.expose()[0]=0x557799;
        check(reader.version()==null&&exposed.getOwnedPixels()==null,"read source raw exposure revokes version");
        check(parity(cache,one(exposed),scratch).id!=before.id,"direct source exposure invalidates");

        // Publication is after all mutations and preserves repeated-frame identity.
        Entry actor=new Entry("body",Entry.TYPE.NPC,null,2);actor.getFrames()[0]=original;actor.getFrames()[1]=original;
        Entry generic=new Entry("texture",Entry.TYPE.SPRITE,null,1);generic.getFrames()[0]=original;
        Map<String,Entry> entries=new HashMap<String,Entry>();entries.put("body",actor);entries.put("generic",generic);
        Map<String,Map<String,Entry>> tree=new HashMap<String,Map<String,Entry>>();tree.put("any-name",entries);
        Renderer3DSettings.setBrowserPresentationAvailable(false);BrowserOwnedActorPixels.publish(tree);
        check(actor.getFrames()[0]==original,"no native publication even with private property");
        Renderer3DSettings.setBrowserPresentationAvailable(true);BrowserOwnedActorPixels.publish(tree);
        check((actor.getFrames()[0]!=original)==enabled,"private publication policy");
        check(actor.getFrames()[0]==actor.getFrames()[1],"shared Frame references preserved");
        check(generic.getFrames()[0]==original,"generic/texture entry unchanged");
        Frame published=actor.getFrames()[0];BrowserOwnedActorPixels.publish(tree);
        check(actor.getFrames()[0]==published,"repeated publication bounded/no per-frame copying");
        actor.getFrames()[0]=new Frame(2,1,false,0,0,2,1);actor.getFrames()[0].getPixels()[0]=0xee7733;
        parity(cache,one(actor.getFrames()[0].getSprite()),scratch);

        Random random=new Random(0x419007);
        for(int sample=0;sample<700;sample++) {
            List<SpriteCommand> layers=new ArrayList<SpriteCommand>();
            for(int l=0;l<1+random.nextInt(7);l++) {
                int sw=1+random.nextInt(11),sh=1+random.nextInt(11),w=2+random.nextInt(30),h=2+random.nextInt(30);
                int[] pixels=new int[sw*sh];
                for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt(5)==0?0:random.nextInt(0x1000000);
                pixels[0]=0xff000000;
                Sprite source=new Sprite(pixels,sw,sh);
                if(l%3!=0)source=source.copyForOwnedActor();
                if(l%3==2)source.getPixels(); // mixed legacy, trusted and escaped layers
                boolean mirror=random.nextBoolean();int sx=mirror?(sw-1)<<16:0,dx=(sw<<16)/w*(mirror?-1:1);
                int x=random.nextInt(15)-7,y=random.nextInt(15)-7;
                RendererSpriteTransform transform=RendererSpriteTransform.legacyMasks(random.nextInt(0x1000000),random.nextInt(0x1000000),random.nextInt(0x1000000),random.nextInt());
                layers.add(new SpriteCommand(source,x,y,w,h,0,0,sw,sh,sx,-32768,dx,(sh<<16)/h,
                    1+random.nextInt(255),transform,(x<<16)+random.nextInt(65536),(x<<16)+random.nextInt(200000)-100000,mirror,true,
                    l,Renderer2DFrame.Phase.UNKNOWN,l));
            }
            BrowserWorldSpriteCache.Result a=parity(cache,layers,scratch),b=parity(cache,layers,scratch);
            if(a!=null)check(a.id==b.id&&a.rgba==b.rgba,"mixed-layer repeat ID and immutable result");
            check(cache.getCacheBytes()<=BrowserWorldSpriteCache.MAX_CACHE_BYTES&&cache.getEntryCount()<=128,"bounded cache");
        }
        // Metadata always participates, even for one trusted source token.
        Sprite source=sprite(0x445566,0x778899).copyForOwnedActor();
        cache.clear();before=parity(cache,one(source),scratch);
        List<SpriteCommand> alpha=Collections.singletonList(new SpriteCommand(source,3,-2,9,7,70));
        check(parity(cache,alpha,scratch).id!=before.id,"owned alpha key remains exact");
        Sprite samePixels=sprite(0x445566,0x778899).copyForOwnedActor();
        check(Arrays.equals(parity(cache,one(samePixels),scratch).rgba,before.rgba),"different owners equal art");
        BrowserWorldSpriteCache tiny=new BrowserWorldSpriteCache(1800,2);
        BrowserWorldSpriteCache.Result held=parity(tiny,one(source),scratch);byte[] heldBytes=held.rgba.clone();
        for(int i=0;i<20;i++)parity(tiny,one(sprite(0x223300+i,0x665500+i).copyForOwnedActor()),scratch);
        check(tiny.getCacheBytes()<=1800&&tiny.getEntryCount()<=2,"owned LRU/budget enforced");
        check(Arrays.equals(held.rgba,heldBytes),"eviction cannot corrupt held frame");
        int epoch=tiny.getEpoch();tiny.clear();check(tiny.getEpoch()==epoch+1&&tiny.getEntryCount()==0,"context reset clears ownership cache");
        double[] counts=cache.diagnostics();
        check(counts[4]>0&&counts[5]>0&&counts[6]>0,"legacy hash/compare/copy work accounted");
        check((counts[0]>0)==enabled&&(counts[7]>0)==enabled&&(counts[8]>0)==enabled,"trusted coverage and actual avoided work match opt-in");
        System.out.println("PASS owned actor pixels checks="+checks+" enabled="+enabled+" counters="+Arrays.toString(counts));
    }
}
