package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.RendererTextureData;
import orsc.multiclient.ClientPort;
import java.lang.reflect.*;
import java.nio.file.Paths;
import java.util.*;

/** Native family/alias loading and actual software/GL texture data; never starts a game. */
public final class BattleaxeCoatingClientFixture {
    static int assertions;
    static void check(boolean v,String s){assertions++;if(!v)throw new AssertionError(s);}
    static final int[] BASES={89,90,91,92,93,205,429,2002,2013,2024,2035,2156,2752,3118,3269};
    public static void main(String[] args)throws Exception {
        Config.S_WANT_BANK_NOTES=true;Config.S_WANT_CERT_AS_NOTES=true;
        Entry packaged=null;
        for(Entry e:new Unpacker().unpackArchive(new java.io.File(args[0]+"/Client_Base/Cache/video/Custom_Sprites.osar")).getSubspaceByName("equipment").getEntryList())if(e.getID().equals("viscousbattleaxe"))packaged=e;
        check(packaged!=null&&packaged.getFrames().length==18&&packaged.getLayer()==Frame.LAYER.MAIN_HAND,"one complete native mainhand family");
        Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);Config.F_CACHE_DIR=args[0]+"/Client_Base/Cache";
        ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[0]),BattleaxeCoatingClientFixture.class);
        Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
        mudclient client=(mudclient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(mudclient.class);
        MudClientGraphics surface=new MudClientGraphics(180,170,4501);client.setSurface(surface);
        for(boolean custom:new boolean[]{Boolean.parseBoolean(args[1])}) {
            Config.S_WANT_CUSTOM_SPRITES=custom;EntityHandler.load(true);
            check(EntityHandler.animationCount()==1112,"stable appended alias IDs in both renderer modes");
            ItemDef dose=EntityHandler.getItemDef(3410);check(dose.command==null&&!dose.wieldable&&!dose.stackable&&dose.basePrice==300&&dose.membersItem,"single-use inert dose, no Drink or decant forms");
            check(loader.getExternalItemSprite(dose)!=null,"packaged coating icon");
            AnimationDef[] aliases=new AnimationDef[15];
            for(int n=0;n<15;n++) {
                ItemDef base=EntityHandler.getItemDef(BASES[n]),coated=EntityHandler.getItemDef(3411+n);
                check(coated.name.equalsIgnoreCase("Viscous "+base.name)&&coated.basePrice==base.basePrice+300,"effective client identity/value parity "+BASES[n]+" base="+base.name+"/"+base.basePrice+" coated="+coated.name+"/"+coated.basePrice);
                check(coated.wieldable==base.wieldable&&coated.wearableID==base.wearableID&&coated.membersItem==base.membersItem&&coated.untradeable==base.untradeable&&coated.noteable==base.noteable&&!coated.stackable,"actual final client policy fields inherit base");
                check(coated.command.length==1&&coated.command[0].equals("Wipe clean")&&coated.wieldable,"Wield affordance and explicit Wipe command coexist");
                check(coated.description.contains("Slow1")&&!coated.description.contains("cap 12"),"plain-language Slow tier tooltip");
                Sprite inventory=loader.getExternalItemSprite(coated);
                check(inventory!=null&&coated.getPictureMask()==base.getPictureMask(),"native inventory palette mask inherited exactly");
                Entry original=null;
                for(Entry e:new Unpacker().unpackArchive(new java.io.File(args[0]+"/Client_Base/Cache/video/Custom_Sprites.osar")).getSubspaceByName("items").getEntryList())if(e.getID().equals(BASES[n]==2752?"272":"84"))original=e;
                Sprite nativeIcon=original.getFrames()[0].getSprite();
                RendererSpriteTransform itemTint=RendererSpriteTransform.legacyMasks(coated.getPictureMask(),0,0,0xffffffff);
                int[] texture=RendererTextureData.fromSprite(inventory,itemTint).getRgbaPixels();
                Arrays.fill(surface.pixelData,0x112233);surface.drawSpriteClipping(inventory,1,1,48,32,coated.getPictureMask(),0,0,false,0,1,0xffffffff);
                for(int y=0;y<nativeIcon.getHeight();y++)for(int x=0;x<nativeIcon.getWidth();x++) {
                    int index=(y+nativeIcon.getYShift())*48+x+nativeIcon.getXShift();int originalPixel=nativeIcon.getPixels()[y*nativeIcon.getWidth()+x];
                    if(originalPixel==0||originalPixel!=inventory.getPixels()[index])continue;
                    int expected=itemTint.apply(originalPixel);check((texture[index]>>>8)==expected,"unchanged inventory metal/handle has exactly original native tint, no extra rounding pass");
                    int composite=((((expected>>16&255)*255+0x11)>>8)<<16)|((((expected>>8&255)*255+0x22)>>8)<<8)|(((expected&255)*255+0x33)>>8);
                    check(surface.pixelData[(y+nativeIcon.getYShift()+1)*surface.width2+x+nativeIcon.getXShift()+1]==composite,"actual inventory software draw matches original metal/handle tint");
                }
                check(EntityHandler.getItemDef(3411+n,true).stackable&&!EntityHandler.getItemDef(3411+n,true).wieldable,"note form remains safe");
                aliases[n]=EntityHandler.getAnimationDef(1097+n);check(aliases[n].getName().equals("viscousbattleaxe"),"new IDs share explicit named family");
            }
            Field archive=orsc.graphics.two.GraphicsController.class.getDeclaredField("spriteArchive");archive.setAccessible(true);
            archive.set(surface,new java.util.zip.ZipFile(args[0]+"/Client_Base/Cache/video/Authentic_Sprites.orsc"));
            if(surface.sprites==null)surface.sprites=new Sprite[4501];
            Field f=EntityHandler.class.getDeclaredField("animations");f.setAccessible(true);
            @SuppressWarnings("unchecked") ArrayList<AnimationDef> all=(ArrayList<AnimationDef>)f.get(null);
            ArrayList<AnimationDef> saved=new ArrayList<AnimationDef>(all);ClientPort port=mudclient.clientPort;
            try {
                all.clear();all.addAll(Arrays.asList(aliases));AnimationDef sentinel=new AnimationDef("gorak","npc",0,0,true,false,0);all.add(sentinel);
                mudclient.clientPort=(ClientPort)Proxy.newProxyInstance(ClientPort.class.getClassLoader(),new Class<?>[]{ClientPort.class},(p,m,a)->null);
                Method allocate=mudclient.class.getDeclaredMethod("loadEntitiesAuthentic");allocate.setAccessible(true);allocate.invoke(client);
                for(AnimationDef alias:aliases)check(alias.getNumber()==4000,"actual classic allocator shares reserved native range");check(sentinel.getNumber()==4027,"one family reserves27 slots, not15copies");
            }finally{all.clear();all.addAll(saved);mudclient.clientPort=port;}
            surface.spriteTree.put("equipment",new HashMap<String,Entry>());surface.spriteTree.get("equipment").put("viscousbattleaxe",packaged);
            for(int frame=0;frame<18;frame++)for(AnimationDef alias:aliases) {
                Sprite sprite=client.spriteSelect(alias,frame),reference=packaged.getFrames()[frame].getSprite();
                check(Arrays.equals(sprite.getPixels(),reference.getPixels())&&sprite.getWidth()==reference.getWidth()&&sprite.getHeight()==reference.getHeight()&&sprite.getXShift()==reference.getXShift()&&sprite.getYShift()==reference.getYShift()&&sprite.getSomething1()==reference.getSomething1()&&sprite.getSomething2()==reference.getSomething2(),"actual named/classic selection retains native crop/offset/canvas");
                int colour=alias.getCharColour();RendererSpriteTransform transform=RendererSpriteTransform.legacyMasks(colour,0,0,0xffffffff);
                int[] gl=RendererTextureData.fromSprite(sprite,transform).getRgbaPixels();
                Arrays.fill(surface.pixelData,0x112233);
                surface.drawSpriteClipping(sprite,20,20,sprite.getSomething1(),sprite.getSomething2(),colour,0,0,false,0,1,0xffffffff);
                for(int y=0;y<sprite.getHeight();y++)for(int x=0;x<sprite.getWidth();x++) {
                    int pixel=sprite.getPixels()[y*sprite.getWidth()+x],rgba=gl[y*sprite.getWidth()+x];
                    if(pixel==0){check(rgba==0,"hand gaps remain transparent in GL bridge");continue;}
                    check((rgba>>>8)==transform.apply(pixel),"actual GL texture uses native mask transform");
                    int rgb=rgba>>>8;
                    int expected=((((rgb>>16&255)*255+0x11)>>8)<<16)|((((rgb>>8&255)*255+0x22)>>8)<<8)|(((rgb&255)*255+0x33)>>8);
                    int ox=20+sprite.getXShift()+x,oy=20+sprite.getYShift()+y;
                    check(surface.pixelData[oy*surface.width2+ox]==expected,"actual software draw agrees with GL texture and native opacity rounding");
                }
            }
        }
        System.out.println("PASS coating client: "+assertions+" checks;15icons,18nativeframes,classic/named alias allocation,software/GL masks and policy fields");
    }
}
