import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import orsc.Config;
import orsc.mudclient;
import orsc.PacketHandler;
import orsc.ORSCharacter;
import java.lang.reflect.*;

public final class AgilityUtilityClientFixture {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[0]);EntityHandler.load(true);
        String[] names={"Boots of Swiftness","Gloves of Suregrip","Cape of Traversal"},families={"woolboots","woolgloves","cape"};
        for(int i=0;i<3;i++){
            ItemDef item=EntityHandler.getItemDef(3472+i);check(item.getName().equals(names[i]),"catalog name");
            check(item.wieldable&&item.untradeable&&!item.noteable&&!item.stackable,"equipment flags");
            AnimationDef art=EntityHandler.getAnimationDef(1189+i);check(art.getName().equals(families[i])&&art.getCharColour()==0x65B8AC,"native held family/palette");
        }
        check(EntityHandler.animationCount()==1192,"append-only appearances");
        check(java.util.Arrays.equals(EntityHandler.getItemDef(3474).getCommand(),new String[]{"Teleport","Check"}),"cape commands");
        Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);mudclient mc=(mudclient)((sun.misc.Unsafe)u.get(null)).allocateInstance(mudclient.class);
        Field rates=mudclient.class.getDeclaredField("playerMovementRatePercent");rates.setAccessible(true);rates.set(mc,new int[65536]);
        check(mc.playerMovementRate(7)==100,"normal default");
        PacketHandler handler=new PacketHandler(mc);handler.getPacketsIncoming().dataBuffer=new byte[]{(byte)161,0,7,0,(byte)150};handler.getPacketsIncoming().packetEnd=1;
        handler.handlePacket(161,5);check(mc.playerMovementRate(7)==150,"actual actor rate receipt");check(mc.playerMovementRate(8)==100,"other actors unchanged");
        mc.applyPlayerMovementRate(7,999);check(mc.playerMovementRate(7)==150,"invalid percentage rejected");
        mc.applyPlayerMovementRate(7,100);check(mc.playerMovementRate(7)==100,"normal speed restored");
        Method movement=mudclient.class.getDeclaredMethod("movementAmountForFrame",ORSCharacter.class,int.class,long.class,int.class);movement.setAccessible(true);
        ORSCharacter normal=new ORSCharacter(),fast=new ORSCharacter();normal.movementLastFrameMillis=1000;fast.movementLastFrameMillis=1000;
        int slowPixels=(Integer)movement.invoke(mc,normal,6,1020L,100),fastPixels=(Integer)movement.invoke(mc,fast,6,1020L,150);
        check(slowPixels==6&&fastPixels==9,"smooth1.5x interpolation; no NPC/global speed change");
        System.out.println("PASS "+checks+" agility utility client catalog, appearance, packet and interpolation checks; custom="+args[0]);
    }
}
