package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import orsc.graphics.two.SpriteArchive.Entry;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.io.File;

/** Production client decoding and definition identities, no window or network. */
public final class GemDragonClientFixture {
    static void check(boolean value,String label) {if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        EntityHandler.load(true);
        String[] expectedStats=args[1].split(";");
        String[] gems={"sapphire","emerald","ruby","diamond","dragonstone"};
        ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[0]),GemDragonClientFixture.class);
        int[] widths={226,226,226,226,226,335};
        for(int tier=0;tier<gems.length;tier++) {
            NPCDef npc=EntityHandler.getNpcDef(877+tier);
            check(npc.getName().equals(Character.toUpperCase(gems[tier].charAt(0))+gems[tier].substring(1)+" Dragon"),"NPC identity");
            String[] expectedTier=expectedStats[tier].split(",");
            check(npc.getAtt()==Integer.parseInt(expectedTier[0])&&npc.getStr()==Integer.parseInt(expectedTier[1])
                &&npc.getHits()==Integer.parseInt(expectedTier[2])&&npc.getDef()==Integer.parseInt(expectedTier[3]),"server/client stat parity");
            int animation=EntityHandler.getGemDragonAnimationId(tier);
            check(npc.sprites[0]==animation,"registered animation identity, not green fallback");
            check(EntityHandler.getAnimationDef(animation).getName().equals("gemdragon"+gems[tier]),"variant animation name");
            File source=loader.findFirstFile(new String[]{"dev/myworld/assets/sprites/npcs/gem-dragons"},gems[tier]+".png");
            BufferedImage sheet=loader.readAssetImage(source);
            Entry entry=loader.loadExternalNpcDirectionSheet(source,"gemdragon"+gems[tier],widths,3);
            check(entry!=null&&entry.getFrames().length==18,"all native frames decoded");
            int x0=0;
            for(int column=0;column<6;column++) {
                for(int row=0;row<3;row++) {
                    int[] pixels=entry.getFrames()[column*3+row].getPixels();
                    check(pixels.length==widths[column]*163,"native logical canvas");
                    for(int y=0;y<163;y++)for(int x=0;x<widths[column];x++) {
                        int argb=sheet.getRGB(x0+x,row*163+y);int expected=(argb>>>24)<64?0:argb&0xffffff;
                        if(expected==0&&(argb>>>24)>=64)expected=0x010101;
                        check(pixels[y*widths[column]+x]==expected,"sprite pixel/offset survives runtime decoding");
                    }
                }
                x0+=widths[column];
            }
        }
        for(int id=3400;id<=3407;id++)check(EntityHandler.getItemDef(id).getName().equals("Unobtanium"),"reserved sparse IDs remain placeholders");
        check(EntityHandler.getItemDef(3408).getName().equals("Gem cutter gloves"),"gloves client definition");
        check(EntityHandler.getItemDef(3409).getName().equals("Gem cutter's monocle"),"monocle client definition");
        System.out.println("PASS five production client definitions,18 frames/variant, exact pixels, sparse IDs, disk/JAR loading");
    }
}
