import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.constants.*;
import com.openrsc.server.content.clan.Clan;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.*;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.net.rsc.*;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.AttackHandler;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.util.rsc.DataConversions;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Local-only controller. Opponents are registered Players using normal game combat. */
public final class PrivatePvpServer {
    static Path root; static Server server; static Player human;
    static final List<Player> bots=new ArrayList<>();
    static String preset="melee",style="melee",lastStatus="",message="Ready to log in";
    static boolean active,validated;static long serial;static int x=180,y=200;
    static final String BOT="private_pvp_opponent";
    public static void main(String[] args)throws Exception {
        root=Paths.get("..").toRealPath();
        if(!root.startsWith(Paths.get("/home/justin/Core-Framework/output/tests/pvp-readiness")) || !Files.isRegularFile(root.resolve("PRIVATE-PVP.json")))throw new IllegalStateException("Private copy required");
        com.openrsc.server.config.ServerConfigurationLoadResult loaded=ServerConfigurationLoader.load("myworld.conf");
        com.openrsc.server.ServerConfiguration cfg=loaded.getLegacyConfiguration();
        if(cfg.DB_TYPE!=DatabaseType.SQLITE || !cfg.DB_NAME.equals("myworld_dev") || loaded.getProcessNetwork().getServerPort()!=43635 || !cfg.SERVER_BIND_ADDRESS.equals("127.0.0.1") || cfg.WANT_FEATURE_WEBSOCKETS || !cfg.WANT_PVP)throw new IllegalStateException("Private endpoint refused");
        if(cfg.WANT_DISCORD_BOT || cfg.WANT_DISCORD_GENERAL_LOGGING || cfg.WANT_DISCORD_AUCTION_UPDATES || cfg.WANT_DISCORD_ITEM_SHOWCASE || cfg.WANT_DISCORD_MONITORING_UPDATES)throw new IllegalStateException("External services must be disabled");
        server=Server.startServer("myworld.conf");findArena();
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(),null,1,"Private PvP control",DuplicationStrategy.ONE_PER_SERVER) {
            public void run(){try{tick();}catch(Exception e){e.printStackTrace();message=e.toString();try{writeStatus();}catch(Exception ignored){}}}
        });
        boolean stopping=false;
        while(server.isRunning()) {
            if(!stopping && Files.exists(root.resolve("test-support/STOP"))) {stopping=true;Server.closeProcess(5,"Private PvP test closing");}
            server.checkShutdown();Thread.sleep(500);
        }
        System.exit(0);
    }
    static boolean clear(int px,int py) {
        com.openrsc.server.model.world.region.TileValue tile=server.getWorld().getTile(Point.location(px,py));
        return (tile.traversalMask&255)==0 && (tile.overlay&255)!=10 && Point.location(px,py).inWilderness();
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
        if(!validated){validateKits();validated=true;}
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
            if(active)for(Player bot:bots)if(!bot.killed && bot.getLevel(Skill.HITS.id())>0 && bot.getOutgoingCombatTarget()==null)attack(bot,human);
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
        for(Player p:bots){p.setLoggedIn(false);p.remove();server.getWorld().getPlayers().remove(p);server.getWorld().removePlayer(p.getUsernameHash());}
        bots.clear();
    }
    static void command(String action)throws Exception {
        if(action.startsWith("preset-")){preset=action.substring(7);if(human!=null)reset();}
        else if(action.equals("reset")){if(human!=null)reset();}
        else if(action.equals("pause")){pause();message="Opponents paused. You can still attack them.";}
        else if(action.startsWith("attack-")) {
            style=action.substring(7);pause();if(human!=null){for(Player bot:bots)configureBot(bot,style);active=true;message="Opponents attacking with "+style+".";}
        }
        else if(action.equals("safe-opponent") && !bots.isEmpty()) {bots.get(0).teleport(180,427,false);message="Pvp Red moved outside Wilderness. Queued hits must stop.";}
        else if(action.equals("safe-self") && human!=null){human.teleport(181,427,false);message="You moved outside Wilderness. Player-owned damage must stop.";}
        else if(action.equals("edge") && human!=null){pause();human.teleport(180,426,false);for(int i=0;i<bots.size();i++)bots.get(i).teleport(181+i,426,false);message="Wilderness edge: y426 is Wilderness; y427 is safe.";}
        else if(action.equals("allies") && human!=null && bots.size()==3){Party party=new Party(server.getWorld());human.setParty(party);bots.get(1).setParty(party);Clan clan=new Clan(server.getWorld());clan.setClanID(9876);human.setClan(clan);bots.get(2).setClan(clan);message="Pvp Blue is a party ally; Pvp Gold is a clan ally. Only Pvp Red should take collateral damage.";}
        else if(action.equals("no-allies") && human!=null){human.setParty(null);human.setClan(null);for(Player bot:bots){bot.setParty(null);bot.setClan(null);}message="All opponents are enemies again.";}
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
        ActionSender.sendInventory(human);ActionSender.sendEquipment(human);ActionSender.sendEquipmentStats(human);ActionSender.sendStats(human);ActionSender.sendFatigue(human);
        message="Ready: "+preset+". Opponents are passive; use browser controls to start their attacks.";human.message(message);
    }
    static void validateKits()throws Exception {
        Player probe=bot("Pvp Preview",x,y,0);bots.remove(probe);human=probe;JSONArray checked=new JSONArray();
        try {
            for(String kit:new String[]{"melee","ranged","magic","cleric"}) {
                preset=kit;reset();
                if(bots.size()!=3 || !human.getLocation().inWilderness())throw new IllegalStateException("Private scene incomplete");
                checked.put(new JSONObject().put("kit",kit).put("inventorySlots",human.getCarriedItems().getInventory().size())
                    .put("combatLevel",human.getCombatLevel()).put("opponentCombatLevel",bots.get(0).getCombatLevel()));
                for(Player target:bots)if(!com.openrsc.server.model.combat.PlayerPvpCombat.eligible(human,target,com.openrsc.server.model.combat.CombatStyle.MELEE))throw new IllegalStateException("Private opponents are not legally attackable");
            }
        } finally {pause();removeBots();probe.setLoggedIn(false);probe.remove();server.getWorld().getPlayers().remove(probe);server.getWorld().removePlayer(probe.getUsernameHash());human=null;preset="melee";message="Ready to log in";}
        Files.write(root.resolve("test-support/kit-validation.json"),checked.toString(2).getBytes("UTF-8"));
    }
    static void writeStatus()throws Exception {
        JSONObject status=new JSONObject().put("ready",true).put("preset",preset).put("active",active).put("message",message).put("online",human==null?0:1).put("x",x).put("y",y);
        JSONArray opponents=new JSONArray();for(Player p:bots)opponents.put(new JSONObject().put("name",p.getUsername()).put("hp",p.getLevel(Skill.HITS.id())).put("x",p.getX()).put("y",p.getY()));status.put("opponents",opponents);
        String body=status.toString();if(!body.equals(lastStatus)){Files.write(root.resolve("test-support/status.json"),body.getBytes("UTF-8"));lastStatus=body;}
    }
}
