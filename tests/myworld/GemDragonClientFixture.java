package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.RendererTextureData;
import orsc.multiclient.ClientPort;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Native loading, alias allocation, software draw and the production OpenGL texture bridge. */
public final class GemDragonClientFixture {
    static void check(boolean value,String label) {if(!value)throw new AssertionError(label);}
    static final int[] COLOURS={0x004CFF,0x33CC33,0xFF3300,0xFFFFFF,0xBB00FF};
    public static void main(String[] args)throws Exception {
        EntityHandler.load(true);
        String[] expectedStats=args[1].split(";");
        String[] gems={"sapphire","emerald","ruby","diamond","dragonstone"};
        ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[0]),GemDragonClientFixture.class);
        File source=loader.findFirstFile(new String[]{"dev/myworld/assets/sprites/npcs/gem-dragons"},"gemdragon.png");
        BufferedImage sheet=loader.readAssetImage(source);
        int[] widths={226,226,226,226,226,335};
        Entry entry=loader.loadExternalNpcDirectionSheet(source,"gemdragon",widths,3);
        check(entry!=null&&entry.getFrames().length==18,"one neutral family contains all native frames");
        Config.S_WANT_CUSTOM_SPRITES=true;
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        mudclient client=(mudclient)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(mudclient.class);
        MudClientGraphics surface=new MudClientGraphics(340,168,54);client.setSurface(surface);
        Field external=mudclient.class.getDeclaredField("externalAssetLoader");external.setAccessible(true);external.set(client,loader);
        AnimationDef[] aliases=new AnimationDef[5];
        for(int tier=0;tier<5;tier++) {
            NPCDef npc=EntityHandler.getNpcDef(877+tier);
            check(npc.getName().equals(Character.toUpperCase(gems[tier].charAt(0))+gems[tier].substring(1)+" Dragon"),"NPC identity");
            String[] expectedTier=expectedStats[tier].split(",");
            check(npc.getAtt()==Integer.parseInt(expectedTier[0])&&npc.getStr()==Integer.parseInt(expectedTier[1])
                &&npc.getHits()==Integer.parseInt(expectedTier[2])&&npc.getDef()==Integer.parseInt(expectedTier[3]),"server/client stat parity");
            int id=EntityHandler.getGemDragonAnimationId(tier);aliases[tier]=EntityHandler.getAnimationDef(id);
            check(npc.sprites[0]==id,"registered gem appearance, not green fallback");
            check(aliases[tier].getName().equals("gemdragon")&&aliases[tier].getCharColour()==COLOURS[tier],"shared family and native gem tint");
        }
        // Execute the real authentic allocator on an isolated subset of the same
        // AnimationDef objects, avoiding unrelated vanilla archive reads.
        Field animationsField=EntityHandler.class.getDeclaredField("animations");animationsField.setAccessible(true);
        @SuppressWarnings("unchecked") ArrayList<AnimationDef> animations=(ArrayList<AnimationDef>)animationsField.get(null);
        ArrayList<AnimationDef> saved=new ArrayList<AnimationDef>(animations);
        ClientPort previous=mudclient.clientPort;
        try {
            animations.clear();animations.addAll(Arrays.asList(aliases));
            AnimationDef sentinel=new AnimationDef("gorak","npc",0,0,true,false,0);animations.add(sentinel);
            mudclient.clientPort=(ClientPort)Proxy.newProxyInstance(ClientPort.class.getClassLoader(),new Class<?>[]{ClientPort.class},(proxy,method,values)->null);
            Method authentic=mudclient.class.getDeclaredMethod("loadEntitiesAuthentic");authentic.setAccessible(true);authentic.invoke(client);
            for(AnimationDef alias:aliases)check(alias.getNumber()==0,"real authentic allocator aliases the neutral frame range");
            check(sentinel.getNumber()==27,"five aliases reserve only one authentic frame range");
        } finally {animations.clear();animations.addAll(saved);mudclient.clientPort=previous;}
        Method load=mudclient.class.getDeclaredMethod("loadGemDragonSprites");load.setAccessible(true);
        Config.S_WANT_CUSTOM_SPRITES=false;surface.sprites=new Sprite[54];load.invoke(client);
        for(int frame=0;frame<18;frame++)for(AnimationDef alias:aliases)
            check(client.spriteSelect(alias,frame)==surface.sprites[frame],"classic renderer shares loaded native frame objects");
        Config.S_WANT_CUSTOM_SPRITES=true;surface.spriteTree.put("npc",new HashMap<String,Entry>());load.invoke(client);
        check(surface.spriteTree.get("npc").size()==1&&surface.spriteTree.get("npc").containsKey("gemdragon"),"named renderer stores one shared entry");
        for(int frame=0;frame<18;frame++)for(AnimationDef alias:aliases)
            check(client.spriteSelect(alias,frame)==surface.spriteTree.get("npc").get("gemdragon").getFrames()[frame].getSprite(),"named renderer shares exact neutral frames");
        int x0=0;
        for(int column=0;column<6;column++) {
            for(int row=0;row<3;row++) {
                Sprite sprite=entry.getFrames()[column*3+row].getSprite();int[] pixels=sprite.getPixels();
                check(pixels.length==widths[column]*163,"native logical canvas");
                for(int y=0;y<163;y++)for(int x=0;x<widths[column];x++) {
                    int argb=sheet.getRGB(x0+x,row*163+y);int expected=(argb>>>24)<64?0:argb&0xffffff;
                    if(expected==0&&(argb>>>24)>=64)expected=0x010101;
                    check(pixels[y*widths[column]+x]==expected,"native pixels and offsets decoded exactly");
                }
                for(int tier=0;tier<5;tier++) {
                    int mask=aliases[tier].getCharColour();
                    RendererSpriteTransform transform=RendererSpriteTransform.legacyMasks(mask,0,0,0xffffffff);
                    // This is the same converted pixel buffer used by OpenGLSpriteTextureCache.
                    int[] gl=RendererTextureData.fromSprite(sprite,transform).getRgbaPixels();
                    Arrays.fill(surface.pixelData,0x112233);
                    surface.drawSpriteClipping(sprite,1,1,sprite.getWidth(),sprite.getHeight(),mask,0,0,false,0,1,0xffffffff);
                    for(int y=0;y<163;y++)for(int x=0;x<widths[column];x++) {
                        int index=y*widths[column]+x,base=pixels[index];
                        int software=surface.pixelData[(y+1)*surface.width2+x+1];
                        if(base==0) {check(gl[index]==0&&software==0x112233,"transparent pixels preserved by both renderers");continue;}
                        int textureRgb=gl[index]>>>8;
                        // Native software's opacity255 blend uses /256, unlike the
                        // uncomposited GL texture. Preserve that existing rounding.
                        int composite=((((textureRgb>>16&255)*255+0x11)>>8)<<16)
                            |((((textureRgb>>8&255)*255+0x22)>>8)<<8)|(((textureRgb&255)*255+0x33)>>8);
                        check(software==composite,"actual software draw preserves native compositing arithmetic over the GL texture colour");
                        int red=base>>16&255,green=base>>8&255,blue=base&255;
                        if(red==green&&green==blue) {
                            red=red*(mask>>16&255)>>8;green=green*(mask>>8&255)>>8;blue=blue*(mask&255)>>8;
                        }
                        int expected=((red*255>>8)<<16)|((green*255>>8)<<8)|(blue*255>>8);
                        check(textureRgb==expected,"only exact neutral pixels receive gem tint; common native colour-transform arithmetic retained");
                    }
                }
            }
            x0+=widths[column];
        }
        for(int id=3400;id<=3407;id++)check(EntityHandler.getItemDef(id).getName().equals("Unobtanium"),"reserved sparse IDs remain placeholders");
        check(EntityHandler.getItemDef(3408).getName().equals("Gem cutter gloves"),"gloves definition");
        check(EntityHandler.getItemDef(3409).getName().equals("Gem cutter's monocle"),"monocle definition");
        System.out.println("PASS one shared neutral sheet, five native tints, actual authentic alias allocation, named/classic sprite selection, all 18 frames, software/OpenGL texture parity, sparse IDs and stat parity");
    }
}
