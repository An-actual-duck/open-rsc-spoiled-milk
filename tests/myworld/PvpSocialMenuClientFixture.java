package orsc;

import com.openrsc.interfaces.misc.party.Party;
import com.openrsc.interfaces.misc.clan.Clan;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import orsc.enumerations.MenuItemAction;
import orsc.graphics.gui.Menu;
import orsc.graphics.gui.SocialLists;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.MudClientGraphics;

/** Actual player menu construction and sorting, including no-ground-action menus. */
public final class PvpSocialMenuClientFixture {
    private static int checks;
    private static sun.misc.Unsafe unsafe;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    public static void main(String[] args)throws Exception {
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);
        for(int i=0;i<50;i++)Fonts.addFont(new byte[10000]);
        mudclient mc=(mudclient)unsafe.allocateInstance(mudclient.class);
        mc.localPlayer=new ORSCharacter();mc.localPlayer.level=100;mc.packetHandler=new PacketHandler(mc);
        set(mc,"playerLocalX",180);set(mc,"playerLocalZ",200);set(mc,"selectedSpell",-1);set(mc,"selectedItemInventoryIndex",-1);
        Party party=(Party)unsafe.allocateInstance(Party.class);party.username=new String[25];party.partyMembersTotal=new int[99];mc.party=party;
        Clan clan=(Clan)unsafe.allocateInstance(Clan.class);clan.username=new String[200];mc.clan=clan;
        ORSCharacter target=new ORSCharacter();target.displayName="Pvp Target";target.accountName="pvp target";target.serverIndex=2;target.groupID=Group.DEFAULT_GROUP;
        target.currentX=181*128+64;target.currentZ=200*128+64;set(mc,"players",new ORSCharacter[]{target});
        Method build=mudclient.class.getDeclaredMethod("addPlayerToMenu",int.class);build.setAccessible(true);
        MudClientGraphics surface=new MudClientGraphics(512,346,100);Config.S_WANT_PARTIES=true;
        for(String relationship:new String[]{"enemy","friend","party","clan","stale-party","stale-clan"})for(int level:new int[]{40,99,100,101,160})for(boolean ground:new boolean[]{false,true}) {
            SocialLists.partyListCount=0;SocialLists.clanListCount=0;SocialLists.friendListCount=0;Arrays.fill(SocialLists.friendList,null);Arrays.fill(party.username,null);Arrays.fill(clan.username,null);party.putParty(false);clan.putClan(false);
            if(relationship.equals("friend")){SocialLists.friendListCount=1;SocialLists.friendList[0]="PVP_TARGET";}
            if(relationship.equals("party")){party.putParty(true);SocialLists.partyListCount=25;party.username[24]="PVP TARGET";party.partyMembersTotal[0]=25;}
            if(relationship.equals("clan")){clan.putClan(true);SocialLists.clanListCount=200;clan.username[199]="PVP TARGET";}
            if(relationship.equals("stale-party")){party.putParty(true);party.username[24]="Pvp Target";}
            if(relationship.equals("stale-clan")){clan.putClan(true);clan.username[199]="Pvp Target";}
            target.level=level;mc.menuCommon=new Menu(surface,1);
            if(ground)mc.menuCommon.addItem(MenuItemAction.LANDSCAPE_WALK_HERE,"","Walk here");
            build.invoke(mc,0);mc.menuCommon.sort();
            int attacks=0;boolean ally=relationship.equals("friend") || relationship.equals("party") || relationship.equals("clan");
            for(int i=0;i<mc.menuCommon.getItemCount(0);i++) {
                MenuItemAction action=mc.menuCommon.getItemAction(i);
                if(action==MenuItemAction.PLAYER_ATTACK_SIMILAR || action==MenuItemAction.PLAYER_ATTACK_DIVERGENT || action==MenuItemAction.PLAYER_ATTACK_ALLY) {
                    attacks++;check(!ally || action==MenuItemAction.PLAYER_ATTACK_ALLY,"ally Attack always has the secondary priority");
                }
            }
            check(attacks==1,"explicit Attack remains available in the menu");
            MenuItemAction first=mc.menuCommon.getItemAction(0);
            if(ally)check(first!=MenuItemAction.PLAYER_ATTACK_ALLY && first!=MenuItemAction.PLAYER_ATTACK_SIMILAR && first!=MenuItemAction.PLAYER_ATTACK_DIVERGENT,"ally Attack is never the primary action, even without terrain");
            else if(level==99 || level==100)check(first==MenuItemAction.PLAYER_ATTACK_SIMILAR,"enemy level priority remains unchanged");
        }
        SocialLists.partyListCount=0;SocialLists.clanListCount=0;SocialLists.friendListCount=0;Arrays.fill(SocialLists.friendList,null);party.putParty(false);clan.putClan(false);
        System.out.println("PASS "+checks+" actual PvP menu checks across friend,party,clan,levels and terrain availability");
    }
}
