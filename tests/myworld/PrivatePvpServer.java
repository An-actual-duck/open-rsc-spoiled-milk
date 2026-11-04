import org.json.JSONArray;
import org.json.JSONObject;
import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.content.clan.Clan;
import com.openrsc.server.content.clan.ClanPlayer;
import com.openrsc.server.content.clan.ClanRank;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.cleric.ClericResourceCost;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.runtime.ClericSupportCasting;
import com.openrsc.server.content.cleric.runtime.ClericTimedEffectRuntime;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.PlayerPvpCombat;
import com.openrsc.server.util.rsc.CombatEffectUtil;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.container.Bank;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.AttackHandler;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Local-only controller. Opponents are registered Players using normal game combat. */
public final class PrivatePvpServer {
    static Path root; static Server server; static Player human;
    static final List<Player> bots=new ArrayList<>();
    static String preset="melee",style="melee",lastStatus="",message="Ready to log in";
    static boolean active,validated,failed,teammates=true,friendBlue,blueFriendsYou;static long serial;static int x=180,y=200,edgeX=180;
    static final String BOT="private_pvp_opponent";
    public static void main(String[] args)throws Exception {
        root=Paths.get("..").toRealPath();
        if(!root.startsWith(Paths.get("/home/justin/Core-Framework/output/tests/pvp-readiness")) || !Files.isRegularFile(root.resolve("PRIVATE-PVP.json")))throw new IllegalStateException("Private copy required");
        com.openrsc.server.config.ServerConfigurationLoadResult loaded=ServerConfigurationLoader.load("myworld.conf");
        com.openrsc.server.ServerConfiguration cfg=loaded.getLegacyConfiguration();
        if(cfg.DB_TYPE!=DatabaseType.SQLITE || !cfg.DB_NAME.equals("myworld_dev") || loaded.getProcessNetwork().getServerPort()!=43635 || !cfg.SERVER_BIND_ADDRESS.equals("127.0.0.1") || cfg.WANT_FEATURE_WEBSOCKETS || !cfg.WANT_PVP)throw new IllegalStateException("Private endpoint refused");
        if(cfg.WANT_DISCORD_BOT || cfg.WANT_DISCORD_GENERAL_LOGGING || cfg.WANT_DISCORD_AUCTION_UPDATES || cfg.WANT_DISCORD_ITEM_SHOWCASE || cfg.WANT_DISCORD_MONITORING_UPDATES)throw new IllegalStateException("External services must be disabled");
        server=Server.startServer("myworld.conf");
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(),null,1,"Private PvP control",DuplicationStrategy.ONE_PER_SERVER) {
            public void run(){try{PrivatePvpServer.tick();}catch(Exception e){failed=true;e.printStackTrace();message=e.toString();try{writeStatus();}catch(Exception ignored){}}}
        });
        boolean stopping=false;
        while(server.isRunning()) {
            if(!stopping && Files.exists(root.resolve("test-support/STOP"))) {stopping=true;Server.closeProcess(5,"Private PvP test closing");}
            server.checkShutdown();Thread.sleep(500);
        }
        System.exit(0);
    }
    static boolean tileClear(int px,int py) {
        com.openrsc.server.model.world.region.TileValue tile=server.getWorld().getTile(com.openrsc.server.model.world.coordinate.WorldLocation.global(new com.openrsc.server.model.world.coordinate.WorldCoordinate(px,py,0)));
        return (tile.traversalMask&255)==0 && (tile.overlay&255)!=10;
    }
    static boolean clear(int px,int py) { return Point.location(px,py).inWilderness() && tileClear(px,py); }
    static void findEdge() {
        for(int candidate=150;candidate<220;candidate++) {
            boolean ok=true;
            for(int dx=0;dx<4;dx++)for(int py=425;py<=428;py++)ok &= tileClear(candidate+dx,py);
            if(ok){edgeX=candidate;return;}
        }
        throw new IllegalStateException("No clear Wilderness edge");
    }
    static void findArena() {
        for(int radius=0;radius<30;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++) {
            int px=180+dx,py=200+dy;boolean ok=true;
            for(int a=-3;a<=3;a++)for(int b=-3;b<=3;b++)ok &= clear(px+a,py+b);
            if(ok){x=px;y=py;return;}
        }
        throw new IllegalStateException("No clear Wilderness arena");
    }
    static void tick()throws Exception {
        if(failed)return;
        if(!validated){findArena();findEdge();validateKits();validated=true;}
        Player logged=null;
        for(Player p:server.getWorld().getPlayers())if(p.isLoggedIn()&&!p.getAttribute(BOT,false)){logged=p;break;}
        if(logged!=human) {
            pause();removeBots();human=logged;
            if(human!=null)reset();
        }
        Path request=root.resolve("test-support/request.json");
        if(Files.isRegularFile(request)) {
            JSONObject command=new JSONObject(new String(Files.readAllBytes(request),"UTF-8"));Files.delete(request);
            command(command.getString("action"));
        }
        if(human!=null) {
            if(human.killed){pause();message="Your test character died. Reset the scene to restock and return.";}
            if(active)for(Player bot:bots)if(!bot.killed && bot.getLevel(Skill.HITS.id())>0
                    && bot.getOutgoingCombatTarget()==null && PlayerPvpCombat.eligibleEnemy(bot,human))attack(bot,human);
        }
        writeStatus();
    }
    static void attack(Player source,Player target)throws Exception {
        TargetMobStruct request=new TargetMobStruct();request.setOpcode(OpcodeIn.PLAYER_ATTACK);request.serverIndex=target.getIndex();
        new AttackHandler().process(request,source);
    }
    static void pause() {
        active=false;
        for(Player bot:bots){bot.resetAll();bot.resetCombatEvent();bot.resetRange();bot.setAutoCastSpell(null);bot.resetPath();bot.resetFollowing();bot.getCache().store("setting_auto_retaliate",false);}
    }
    static void removeBots() {
        clearPracticeFriendship();
        clearTeams();
        for(Player p:bots){p.setLoggedIn(false);p.remove();server.getWorld().getPlayers().remove(p);server.getWorld().removePlayer(p.getUsernameHash());}
        bots.clear();
    }
    static void command(String action)throws Exception {
        if(action.startsWith("preset-")){preset=action.substring(7);if(human!=null)reset();}
        else if(action.equals("reset")){if(human!=null)reset();}
        else if(action.equals("pause")){pause();message="Opponents paused. You can still attack them.";}
        else if(action.startsWith("attack-")) {
            style=action.substring(7);pause();if(human!=null){for(Player bot:bots)if(PlayerPvpCombat.eligibleEnemy(bot,human))configureBot(bot,style);active=true;message="Enemy players attacking with "+style+". Teammates remain passive.";}
        }
        else if(action.equals("safe-opponent") && !bots.isEmpty()) {bots.get(0).teleport(edgeX,427,false);message="Pvp Red moved outside Wilderness. Queued hits must stop.";}
        else if(action.equals("safe-self") && human!=null){human.teleport(edgeX+1,427,false);message="You moved outside Wilderness. Player-owned damage must stop.";}
        else if(action.equals("edge") && human!=null){pause();human.teleport(edgeX,426,false);for(int i=0;i<bots.size();i++)bots.get(i).teleport(edgeX+1+i,426,false);message="Wilderness edge: y426 is Wilderness; y427 is safe.";}
        else if(action.equals("allies")){teammates=true;pause();assignTeams();message="Pvp Blue is in your party; Pvp Gold is in your clan. Pvp Red is the enemy.";}
        else if(action.equals("no-allies")){teammates=false;pause();assignTeams();message="All three practice players are enemies. This setting carries across kits.";}
        else if(action.equals("friend-blue")){pause();friendBlue=true;blueFriendsYou=false;applyPracticeFriendship();message="Blue is on your friend list only. Your area effects exclude Blue; Blue's effects can still hit you. Use all-enemy mode to isolate this test.";}
        else if(action.equals("blue-friends-you")){pause();friendBlue=false;blueFriendsYou=true;applyPracticeFriendship();message="You are on Blue's friend list only. Blue's area effects exclude you; your effects can still hit Blue. Use all-enemy mode to isolate this test.";}
        else if(action.equals("no-friends")){pause();friendBlue=false;blueFriendsYou=false;applyPracticeFriendship();message="One-way test friendships removed.";}
    }
    static void stats(Player p) {
        for(int skill=0;skill<server.getConstants().getSkills().getSkillsCount();skill++)p.getSkills().setTemporaryLevelAndMaxStat(skill,99,99,false);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),250,250,false);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),163,163,false);
        p.setFatigue(0);p.setCacheInvisible(false);p.setCacheInvulnerable(false);p.getCache().store("setting_auto_retaliate",true);
    }
    static void clearGear(Player p,boolean preserve)throws Exception {
        Inventory inventory=p.getCarriedItems().getInventory();Equipment equipment=p.getCarriedItems().getEquipment();
        for(Item item:new ArrayList<Item>(inventory.getItems())){if(preserve&&!p.getBank().add(item,false))throw new IllegalStateException("Private bank full");inventory.remove(item,false);}
        for(int slot=0;slot<Equipment.SLOT_COUNT;slot++){Item item=equipment.get(slot);if(item!=null){if(preserve&&!p.getBank().add(item,false))throw new IllegalStateException("Private bank full");equipment.remove(item,item.getAmount(),false);}}
        p.setWornItems(p.getSettings().getAppearance().getSprites());
    }
    static void add(Player p,int id,int amount) {if(!p.getCarriedItems().getInventory().add(new Item(id,amount)))throw new IllegalStateException("Cannot fund "+id);}
    static void equip(Player p,int id,int amount)throws Exception {
        Field list=Equipment.class.getDeclaredField("list");list.setAccessible(true);
        com.openrsc.server.external.ItemDefinition def=server.getEntityHandler().getItemDef(id);
        Item item=new Item(id,amount,false,90_000_000L+(++serial));item.setWielded(true);
        ((Item[])list.get(p.getCarriedItems().getEquipment()))[def.getWieldPosition()]=item;
        p.updateWornItems(def.getWieldPosition(),def.getAppearanceId(),def.getWearableId(),true);
    }
    static Player bot(String name,int px,int py,int colour)throws Exception {
        Player p=new Player(server.getWorld(),DataConversions.usernameToHash(name));p.setAttribute("dummyplayer",true);p.setAttribute(BOT,true);p.setBank(new Bank(p));
        p.setClientVersion(server.getConfig().CLIENT_VERSION);p.setClientLimitations(ClientLimitations.forVersion(server.getConfig().CLIENT_VERSION));p.getClientLimitations().maxItemId=65535;
        p.getSettings().setAppearance(new PlayerAppearance(colour,colour,0,0,1,2));p.setInitialLocation(Point.location(px,py));
        p.setWornItems(p.getSettings().getAppearance().getSprites());server.getWorld().getPlayers().add(p);p.updateRegion();p.setLoggedIn(true);p.setBusy(false);stats(p);p.getCache().store("setting_auto_retaliate",false);bots.add(p);return p;
    }
    static void configureBot(Player p,String kind)throws Exception {
        p.resetAll();p.resetCombatEvent();p.resetRange();p.setAutoCastSpell(null);clearGear(p,false);
        if(kind.equals("magic")){for(Map.Entry<Integer,Integer> rune:server.getEntityHandler().getSpellDef(Spells.WIND_STRIKE).getRunesRequired())add(p,rune.getKey(),10000);p.setAutoCastSpell(Spells.WIND_STRIKE);}
        else if(kind.equals("ranged"))equip(p,ItemId.TIN_SHURIKEN.id(),10000);
        else equip(p,ItemId.BRONZE_LONG_SWORD.id(),1);
    }
    static void reset()throws Exception {
        pause();removeBots();human.resetAll();human.resetCombatEvent();human.resetRange();human.setAutoCastSpell(null);human.setParty(null);human.setClan(null);
        human.getTransientEffectState().clearAll();clearGear(human,true);stats(human);human.teleport(x,y,false);
        if(preset.equals("magic")||preset.equals("cleric")) {
            for(int id:new int[]{2074,2087,2100})equip(human,id,1);
            if(preset.equals("magic")){equip(human,ItemId.STAFF_OF_IBAN.id(),1);for(int id:new int[]{ItemId.AIR_RUNE.id(),ItemId.FIRE_RUNE.id(),ItemId.DEATH_RUNE.id(),ItemId.MIND_RUNE.id()})add(human,id,10000);human.setAutoCastSpell(Spells.IBAN_BLAST);}
            else {Set<Integer> ids=new LinkedHashSet<>();for(ClericSpellDefinition spell:ClericSpellCatalog.getAll())if(spell.getId().isActive())for(ClericResourceCost cost:spell.getResourceCosts())ids.add(cost.getItemId());for(int id:ids)add(human,id,10000);}
        } else if(preset.equals("ranged")){for(int id=1935;id<=1939;id++)equip(human,id,1);equip(human,ItemId.TIN_SHURIKEN.id(),10000);}
        else {equip(human,ItemId.RUNE_CHAIN_MAIL_BODY.id(),1);equip(human,ItemId.RUNE_PLATE_MAIL_LEGS.id(),1);equip(human,ItemId.TIN_SCYTHE.id(),1);add(human,3411,1);}
        add(human,ItemId.LOBSTER.id(),10);
        bot("Pvp Red",x+1,y,1);bot("Pvp Blue",x+1,y+1,2);bot("Pvp Gold",x,y+1,3);
        for(Player p:bots)configureBot(p,"melee");
        assignTeams();
        applyPracticeFriendship();
        ActionSender.sendInventory(human);ActionSender.sendEquipment(human);ActionSender.sendEquipmentStats(human);ActionSender.sendStats(human);ActionSender.sendFatigue(human);
        message="Ready: "+preset+". "+(teammates?"Blue is your party teammate; Gold is your clan teammate; Red is the enemy.":"All three practice players are enemies.");human.message(message);
    }
    static void clearTeams() {
        if(human!=null){
            if(human.getParty()!=null)ActionSender.sendLeaveParty(human);
            if(human.getClan()!=null)ActionSender.sendLeaveClan(human);
            human.setParty(null);human.setClan(null);
        }
        for(Player bot:bots){bot.setParty(null);bot.setClan(null);}
    }
    static void assignTeams() {
        clearTeams();
        if(human==null || bots.size()!=3)return;
        if(teammates){
            Party party=new Party(server.getWorld());party.setPartyName("PvP test party");party.setPartyTag("TEST");
            party.addPlayer(human);party.addPlayer(bots.get(1));
            practiceClan(9876,"PvP test clan",human,bots.get(2));
        }else{
            Party enemies=new Party(server.getWorld());
            for(Player bot:bots)enemies.addPlayer(bot);
            practiceClan(9875,"Practice opponents",bots.toArray(new Player[0]));
        }
    }
    /** Real roster and UI, kept transient so repeated presets cannot write fake clan accounts. */
    static void practiceClan(int id,String name,Player... members) {
        Clan clan=new Clan(server.getWorld());clan.setClanID(id);clan.setClanName(name);clan.setClanTag("TEST");
        clan.setKickSetting(1);clan.setInviteSetting(1);
        for(Player player:members){
            ClanPlayer member=new ClanPlayer(player.getUsername());member.setPlayerReference(player);
            member.setRank(clan.getPlayers().isEmpty()?ClanRank.LEADER:ClanRank.NORMAL);
            if(clan.getPlayers().isEmpty())clan.setLeader(member);
            clan.getPlayers().add(member);player.setClan(clan);
        }
        clan.updateClanGUI();clan.updateClanSettings();
    }
    static void clearPracticeFriendship() {
        if(human==null || bots.size()<2)return;
        human.getSocial().removeFriend(bots.get(1).getUsernameHash());
        bots.get(1).getSocial().removeFriend(human.getUsernameHash());
    }
    static void applyPracticeFriendship() {
        if(human==null || bots.size()!=3)return;
        clearPracticeFriendship();Player blue=bots.get(1);
        if(friendBlue)human.getSocial().addFriend(blue.getUsernameHash(),0,blue.getUsername(),"");
        if(blueFriendsYou)blue.getSocial().addFriend(human.getUsernameHash(),0,human.getUsername(),"");
        ActionSender.sendFriendList(human);
    }
    static void validateKits()throws Exception {
        Player probe=bot("Pvp Preview",x,y,0);bots.remove(probe);human=probe;JSONArray checked=new JSONArray();
        boolean previousMode=teammates,previousFriend=friendBlue,previousReverse=blueFriendsYou;
        try {
            teammates=true;friendBlue=false;blueFriendsYou=false;
            for(String kit:new String[]{"melee","ranged","magic","cleric"}) {
                preset=kit;reset();
                if(bots.size()!=3 || !human.getLocation().inWilderness())throw new IllegalStateException("Private scene incomplete");
                checked.put(new JSONObject().put("kit",kit).put("inventorySlots",human.getCarriedItems().getInventory().size())
                    .put("combatLevel",human.getCombatLevel()).put("opponentCombatLevel",bots.get(0).getCombatLevel()));
                validateTeams();
                if(kit.equals("cleric"))validatePartyBlessing();
                checked.getJSONObject(checked.length()-1).put("partyTeammate",bots.get(1).getUsername()).put("clanTeammate",bots.get(2).getUsername())
                    .put("partyRosterSize",human.getParty().getPlayers().size()).put("clanRosterSize",human.getClan().getPlayers().size())
                    .put("partyBuffVerified",kit.equals("cleric"));
            }
            teammates=false;reset();
            if(human.getParty()!=null || human.getClan()!=null)throw new IllegalStateException("All-enemy mode left teammate membership");
            for(Player target:bots)if(!PlayerPvpCombat.eligibleEnemy(human,target))throw new IllegalStateException("All-enemy mode did not restore enemies");
            preset="magic";reset();
            if(teammates || human.getParty()!=null || human.getClan()!=null)throw new IllegalStateException("Kit switch changed selected team mode");
        } finally {pause();removeBots();teammates=previousMode;friendBlue=previousFriend;blueFriendsYou=previousReverse;probe.setLoggedIn(false);probe.remove();server.getWorld().getPlayers().remove(probe);server.getWorld().removePlayer(probe.getUsernameHash());human=null;preset="melee";message="Ready to log in";}
        Files.write(root.resolve("test-support/kit-validation.json"),checked.toString(2).getBytes("UTF-8"));
    }
    static void validateTeams() {
        Player red=bots.get(0),blue=bots.get(1),gold=bots.get(2);
        if(!PlayerPvpCombat.eligibleEnemy(human,red) || PlayerPvpCombat.eligibleEnemy(human,blue) || PlayerPvpCombat.eligibleEnemy(human,gold))throw new IllegalStateException("Practice team targeting is wrong");
        if(blue.getParty()!=human.getParty() || human.getParty().getPlayers().size()!=2
                || human.getParty().getPlayer(blue.getUsername())==null || blue.getClan()!=null)throw new IllegalStateException("Party teammate roster is incomplete");
        if(gold.getClan()!=human.getClan() || human.getClan().getPlayers().size()!=2
                || human.getClan().getPlayer(gold.getUsername())==null || gold.getParty()!=null)throw new IllegalStateException("Clan teammate roster is incomplete");
        if(CombatEffectUtil.isPlayerOwnedPvpSplashTarget(human,red,blue) || CombatEffectUtil.isPlayerOwnedPvpSplashTarget(human,red,gold))throw new IllegalStateException("Teammates are not excluded from splash");
    }
    static void validatePartyBlessing() {
        if(ClericSupportCasting.cast(human,ClericSpellCatalog.get(ClericSpellId.GUTHIX_RESPITE)).getOutcome()!=ClericSupportCasting.Outcome.SUCCESS
                || ClericTimedEffectRuntime.blessing(bots.get(1),ClericEffectFamily.GUTHIX_BLESSING)==null
                || ClericTimedEffectRuntime.blessing(bots.get(2),ClericEffectFamily.GUTHIX_BLESSING)!=null)throw new IllegalStateException("Cleric party recipient setup failed");
    }
    static String role(Player player) {
        if(human!=null && player.getParty()!=null && player.getParty()==human.getParty())return "Party teammate";
        if(human!=null && player.getClan()!=null && player.getClan()==human.getClan())return "Clan teammate";
        if(human!=null && human.getSocial().isFriendsWith(player.getUsernameHash()))return "Friend (your list)";
        return "Enemy";
    }
    static void writeStatus()throws Exception {
        JSONObject status=new JSONObject().put("ready",validated).put("failed",failed).put("teammates",teammates).put("friendBlue",friendBlue).put("blueFriendsYou",blueFriendsYou).put("preset",preset).put("active",active).put("message",message).put("online",human==null?0:1).put("x",x).put("y",y);
        JSONArray opponents=new JSONArray();for(Player p:bots)opponents.put(new JSONObject().put("name",p.getUsername()).put("role",role(p)).put("hp",p.getLevel(Skill.HITS.id())).put("x",p.getX()).put("y",p.getY()));status.put("opponents",opponents);
        String body=status.toString();if(!body.equals(lastStatus)){Files.write(root.resolve("test-support/status.json"),body.getBytes("UTF-8"));lastStatus=body;}
    }
}
