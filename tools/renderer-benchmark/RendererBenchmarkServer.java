import com.openrsc.server.Server;
import com.openrsc.server.config.ServerConfigurationLoader;
import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.container.Bank;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.util.rsc.DataConversions;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;

/** Disposable loopback world. Never use an owner database or a public endpoint. */
public final class RendererBenchmarkServer {
    private static final List<Player> actors = new ArrayList<Player>();
    private static final List<Npc> npcs = new ArrayList<Npc>();
    private static final List<Point> positions = new ArrayList<Point>();
    private static Player viewer;
    private static Server worldServer;
    private static boolean busy;
    private static boolean configured;
    private static int tick;
    private static int removedMapNpcs;
    private static int samePositionTeleports, areaTeleports, levelTeleports;
    private static long serial;
    private static String phase = "dense-idle";
    private static boolean sustainSummonsForBenchmark;
    private static int sustainedSummons;
    private static final Set<Npc> sustainedBearInstances=Collections.newSetFromMap(new IdentityHashMap<Npc,Boolean>());
    private static String[] summonSurvivalKeys;
    private static final String[] COMBAT_FAMILIES={"ice","thunder","wood","ranged","throwing","melee"};
    private static final int SURVIVAL_HITS=30000;

    private static boolean realCombat() { return phase.equals("combat-real"); }
    /** A clear tile alone does not prove the edge to the target permits a cast. */
    private static boolean combatPlacement(int x,int y,Npc target) {
        if(!clear(x,y))return false;
        WorldLocation from=WorldLocation.global(new WorldCoordinate(x,y,0));
        return PathValidation.checkCombatProjectilePath(worldServer.getWorld(),from,target.getWorldLocation())
            && PathValidation.checkAdjacentDistance(worldServer.getWorld(),from,target.getWorldLocation(),false,true);
    }
    private static void equipAmount(Player p,int id,int amount)throws Exception {
        java.lang.reflect.Field f=Equipment.class.getDeclaredField("list");f.setAccessible(true);
        com.openrsc.server.external.ItemDefinition d=worldServer.getEntityHandler().getItemDef(id);
        Item item=new Item(id,amount,false,90_000_000L + ++serial);item.setWielded(true);
        ((Item[])f.get(p.getCarriedItems().getEquipment()))[d.getWieldPosition()]=item;
        p.updateWornItems(d.getWieldPosition(),d.getAppearanceId(),d.getWearableId(),true);
    }
    private static void beginRealCombat()throws Exception {
        Set<UUID> identities=new HashSet<UUID>();Set<Integer> indices=new HashSet<Integer>();
        for(Player p:actors)if(!identities.add(p.getUUID())||!indices.add(p.getIndex())
            ||worldServer.getWorld().getPlayer(p.getIndex())!=p)throw new IllegalStateException("Combat actors must have unique registered identities and indices");
        for(Npc n:npcs) {
            n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),SURVIVAL_HITS,SURVIVAL_HITS,false);
        }
        for(Player p:actors)p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),SURVIVAL_HITS,SURVIVAL_HITS,false);
        for(int i=0;i<COMBAT_FAMILIES.length;i++) {
            Player p=actors.get(i);Npc target=npcs.get(i*3);
            boolean placed=false;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(!placed&&combatPlacement(target.getX()+d[0],target.getY()+d[1],target)){p.teleport(target.getX()+d[0],target.getY()+d[1],false);placed=true;}
            if(!placed)throw new IllegalStateException("No walkable adjacent combat tile with a clear production projectile/action edge");
            java.lang.reflect.Field equipment=Equipment.class.getDeclaredField("list");equipment.setAccessible(true);
            Arrays.fill((Item[])equipment.get(p.getCarriedItems().getEquipment()),null);
            p.setWornItems(p.getSettings().getAppearance().getSprites());
            if(i<3) {
                equipAmount(p,ItemId.STAFF.id(),1);
                Spells spell=new Spells[]{Spells.ICE_CRYSTAL,Spells.THUNDER_STRIKE,Spells.BATTERING_RAM}[i];
                for(Map.Entry<Integer,Integer> rune:worldServer.getEntityHandler().getSpellDef(spell).getRunesRequired())
                    if(!p.getCarriedItems().getInventory().add(new Item(rune.getKey(),10000),false))throw new IllegalStateException("Could not seed combat rune "+rune.getKey());
                p.setAutoCastSpell(spell);
                if(!MagicCombatEvent.start(p,target))throw new IllegalStateException("Real magic combat did not start "+spell);
            }else if(i==3) {
                equipAmount(p,ItemId.SHORTBOW.id(),1);equipAmount(p,ItemId.TIN_ARROWS.id(),10000);
                RangeEvent event=new RangeEvent(worldServer.getWorld(),p,1L,target);p.setRangeEvent(event);worldServer.getGameEventHandler().add(event);
            }else if(i==4) {
                equipAmount(p,ItemId.TIN_SHURIKEN.id(),10000);
                ThrowingEvent event=new ThrowingEvent(worldServer.getWorld(),p,1L,target);p.setThrowingEvent(event);worldServer.getGameEventHandler().add(event);
            }else {equipAmount(p,ItemId.RUNE_LONG_SWORD.id(),1);p.startCombat(target);}
        }
    }
    private static JSONObject combatState() throws Exception {
        JSONObject result=new JSONObject().put("authoritative",realCombat());
        if(!realCombat()||actors.size()<6)return result;
        Set<GameTickEvent> scheduled=Collections.newSetFromMap(new IdentityHashMap<GameTickEvent,Boolean>());
        scheduled.addAll(worldServer.getGameEventHandler().getEvents());
        JSONObject damage=new JSONObject(),resources=new JSONObject(),credited=new JSONObject(),channels=new JSONObject();int summonDamage=0;int damaged=0,active=0,summons=0,summonsEngaged=0,minSummonHits=Integer.MAX_VALUE;
        for(int i=0;i<6;i++) {
            Player p=actors.get(i);Npc target=npcs.get(i*3);
            damage.put(COMBAT_FAMILIES[i],SURVIVAL_HITS-target.getSkills().getLevel(Skill.HITS.id()));
            java.lang.reflect.Field ledgerField=Npc.class.getDeclaredField("contributionLedger");ledgerField.setAccessible(true);
            int contribution=0;
            for(Npc n:npcs) {
                com.openrsc.server.model.entity.npc.NpcContributionLedger ledger=(com.openrsc.server.model.entity.npc.NpcContributionLedger)ledgerField.get(n);
                com.openrsc.server.model.entity.npc.NpcContributionRole role=i<3?com.openrsc.server.model.entity.npc.NpcContributionRole.MAGIC:i<5?com.openrsc.server.model.entity.npc.NpcContributionRole.RANGED:com.openrsc.server.model.entity.npc.NpcContributionRole.MELEE;
                contribution+=ledger.get(role,p.getUUID()).getDamage();
                summonDamage+=ledger.get(com.openrsc.server.model.entity.npc.NpcContributionRole.SUMMON,p.getUUID()).getDamage();
            }
            credited.put(COMBAT_FAMILIES[i],contribution);
            if(i<3) {
                Spells spell=new Spells[]{Spells.ICE_CRYSTAL,Spells.THUNDER_STRIKE,Spells.BATTERING_RAM}[i];
                int spent=0;for(Map.Entry<Integer,Integer> rune:worldServer.getEntityHandler().getSpellDef(spell).getRunesRequired())spent+=10000-p.getCarriedItems().getInventory().countId(rune.getKey());
                resources.put(COMBAT_FAMILIES[i],spent);
            }else if(i<5) {
                int id=i==3?ItemId.TIN_ARROWS.id():ItemId.TIN_SHURIKEN.id();int remaining=0;
                int slot=p.getCarriedItems().getEquipment().searchEquipmentForItem(id);Item item=p.getCarriedItems().getEquipment().get(slot);if(item!=null)remaining=item.getAmount();
                resources.put(COMBAT_FAMILIES[i],10000-remaining);
            }else resources.put("meleeXp",p.getSkills().getExperience(Skill.MELEE.id()));
            GameTickEvent event=i<3?p.getMagicCombatEvent():i==3?p.getRangeEvent():i==4?p.getThrowingEvent():p.getPvmMeleeEvent();
            boolean registered=event!=null&&scheduled.contains(event);
            boolean running=event!=null&&event.isRunning()&&event.getOwner()==p
                &&registered&&worldServer.getWorld().getPlayer(p.getIndex())==p;
            if(running)active++;
            channels.put(COMBAT_FAMILIES[i],new JSONObject().put("running",running)
                .put("hasHandle",event!=null).put("registered",registered)
                .put("schedulerKeyPresent",event!=null&&worldServer.getGameEventHandler().has(event))
                .put("eventIdentity",event==null?"":event.getUUID().toString())
                .put("error",event==null?"Missing expected event":!registered?"Expected event is not the exact scheduled instance":!event.isRunning()?"Expected event stopped":event.getOwner()!=p?"Unexpected event owner":"")
                .put("playerRegistered",worldServer.getWorld().getPlayer(p.getIndex())==p)
                .put("actorIndex",p.getIndex()).put("actorIdentity",p.getUUID().toString()).put("sessionId",p.sessionId)
                .put("eventTicks",event==null?0:event.getTimesRan()).put("x",p.getX()).put("y",p.getY())
                .put("targetX",target.getX()).put("targetY",target.getY())
                .put("walkAction",p.getWalkToAction()!=null).put("busy",p.isBusy())
                .put("walkActionExecuted",p.getWalkToAction()!=null&&p.getWalkToAction().isExecuted())
                .put("movementHeld",worldServer.getGameUpdater().isNativeTerrainActivationMovementHeld(p))
                .put("loggedIn",p.loggedIn()).put("removed",p.isRemoved())
                .put("magicReady",p.combatCastTimer(p.getConfig().RAPID_CAST_SPELLS))
                .put("clearProjectilePath",PathValidation.checkCombatProjectilePath(worldServer.getWorld(),p.getWorldLocation(),target.getWorldLocation())));
        }
        for(Npc n:npcs)if(n.getSkills().getLevel(Skill.HITS.id())<SURVIVAL_HITS)damaged++;
        for(Npc n:worldServer.getWorld().getNpcs())if(Summoning.isSummon(n)) {summons++;minSummonHits=Math.min(minSummonHits,Summoning.getSummonCurrentHits(n));if(n.getPvmMeleeEvent()!=null||n.inCombat())summonsEngaged++;}
        boolean complete=active==6;
        for(String family:COMBAT_FAMILIES)complete&=credited.getInt(family)>0;
        for(int i=0;i<5;i++)complete&=resources.getInt(COMBAT_FAMILIES[i])>0;
        return result.put("primaryDamageByFamily",damage).put("creditedDamageByFamily",credited).put("summonCreditedDamage",summonDamage).put("resourceUnitsSpent",resources).put("damagedTargets",damaged).put("activeChannels",active).put("channels",channels).put("workloadComplete",complete)
            .put("summonsPresent",summons).put("summonsEngaged",summonsEngaged).put("minCurrentSummonHits",summons==0?-1:minSummonHits).put("sustainedSummons",sustainedSummons);
    }
    private static int viewerX = 502, viewerY = 453;

    private static boolean clear(int x, int y) {
        com.openrsc.server.model.world.region.TileValue tile = worldServer.getWorld().getTile(
            com.openrsc.server.model.world.coordinate.WorldLocation.global(
                new com.openrsc.server.model.world.coordinate.WorldCoordinate(x, y, 0)));
        return (tile.traversalMask & 255) == 0 && (tile.overlay & 255) != 10;
    }

    private static void removeActors() {
        for (Player actor : actors) {
            Summoning.dismissAll(actor); actor.setLoggedIn(false); actor.remove();
            worldServer.getWorld().getPlayers().remove(actor); worldServer.getWorld().removePlayer(actor.getUsernameHash());
        }
        for (Npc npc : npcs) worldServer.getWorld().unregisterNpc(npc);
        actors.clear(); npcs.clear(); positions.clear(); sustainedSummons=0; sustainedBearInstances.clear();
    }

    private static void equip(Player player, int id) throws Exception {
        java.lang.reflect.Field slots = Equipment.class.getDeclaredField("list"); slots.setAccessible(true);
        com.openrsc.server.external.ItemDefinition definition = worldServer.getEntityHandler().getItemDef(id);
        Item item = new Item(id, 1, false, 90_000_000L + ++serial); item.setWielded(true);
        ((Item[]) slots.get(player.getCarriedItems().getEquipment()))[definition.getWieldPosition()] = item;
        player.updateWornItems(definition.getWieldPosition(), definition.getAppearanceId(), definition.getWearableId(), true);
    }

    private static void createActors() throws Exception {
        List<Point> tiles = new ArrayList<Point>();
        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) == radius && clear(502 + dx, 453 + dy)) tiles.add(Point.location(502 + dx, 453 + dy));
            }
        }
        if (tiles.size() < 48) throw new IllegalStateException("Insufficient authored walkable crowd tiles");
        for (int index = 0; index < 16; index++) {
            Point point = tiles.get(index); Player actor = new Player(worldServer.getWorld(), DataConversions.usernameToHash("Render " + index));
            actor.setAttribute("dummyplayer", true); actor.setAttribute("renderer_benchmark_actor", true); actor.setBank(new Bank(actor));
            actor.setClientVersion(worldServer.getConfig().CLIENT_VERSION); actor.setClientLimitations(ClientLimitations.forVersion(worldServer.getConfig().CLIENT_VERSION));
            actor.getClientLimitations().maxItemId = 65535;
            actor.getSettings().setAppearance(new PlayerAppearance(index % 8, (index * 3) % 8, 0, 0, 1, 2));
            actor.setInitialLocation(point); actor.setWornItems(actor.getSettings().getAppearance().getSprites());
            worldServer.getWorld().getPlayers().add(actor); actor.updateRegion(); actor.setLoggedIn(true); actor.setBusy(false);
            for (int skill = 0; skill < worldServer.getConstants().getSkills().getSkillsCount(); skill++) actor.getSkills().setTemporaryLevelAndMaxStat(skill, 99, 99, false);
            actor.getCache().store("setting_auto_retaliate", false);
            for (int id : new int[]{ItemId.RUNE_PLATE_MAIL_BODY.id(), ItemId.RUNE_PLATE_MAIL_LEGS.id(), ItemId.LARGE_RUNE_HELMET.id(), ItemId.RUNE_LONG_SWORD.id(), ItemId.RUNE_SQUARE_SHIELD.id()}) equip(actor, id);
            actors.add(actor); positions.add(point);
        }
        for (int index = 0; index < 32; index++) {
            Point point = tiles.get(16 + index); Npc npc = new Npc(worldServer.getWorld(), 3, point.getX(), point.getY(), 0);
            npc.setAttribute("renderer_benchmark_actor", true); worldServer.getWorld().registerNpc(npc); npcs.add(npc);
        }
        for (int index = 0; index < 8; index++) {
            Summoning.summonTestBear(actors.get(index));
            sustainOwnedBenchmarkBear(actors.get(index),index);
        }
    }

    /** One-time survival budget only. Production damage, absorption and attacks remain active. */
    private static void sustainOwnedBenchmarkBear(Player owner,int index)throws Exception {
        if(!sustainSummonsForBenchmark || !realCombat())return;
        if(index<0||index>=8||index>=actors.size()||actors.get(index)!=owner
            || !owner.getAttribute("renderer_benchmark_actor",false))throw new IllegalStateException("Only controlled benchmark bear owners may be sustained");
        if(summonSurvivalKeys==null) {
            String[] fields={"MANUAL_SUMMON_KEY","SUMMON_OWNER_KEY","SUMMON_CURRENT_HITS_KEY","SUMMON_MAX_HITS_KEY","SUMMON_KIND_KEY","KIND_BEAR"};
            String[] keys=new String[fields.length];
            for(int i=0;i<fields.length;i++) {
                java.lang.reflect.Field field=Summoning.class.getDeclaredField(fields[i]);field.setAccessible(true);
                keys[i]=(String)field.get(null);
                if(keys[i]==null||keys[i].isEmpty())throw new IllegalStateException("Frozen summon metadata key unavailable");
            }
            summonSurvivalKeys=keys;
        }
        String[] keys=summonSurvivalKeys;
        Npc bear=owner.getAttribute(keys[0],null);
        if(bear==null||bear.isRemoved()||!Summoning.isSummon(bear)
            || bear.getAttribute(keys[1],-1L)!=owner.getUsernameHash()
            || !keys[5].equals(bear.getAttribute(keys[4],"")))throw new IllegalStateException("Expected immediately spawned owned benchmark bear");
        if(!sustainedBearInstances.add(bear))throw new IllegalStateException("Benchmark bear survival budget may only be assigned once");
        bear.setAttribute(keys[2],SURVIVAL_HITS);bear.setAttribute(keys[3],SURVIVAL_HITS);
        bear.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),SURVIVAL_HITS,SURVIVAL_HITS,false);
        sustainedSummons++;
    }

    private static void driveViewer() {
        if (viewer == null || !phase.equals("busy-walk") || tick % 16 != 0) return;
        // A short authored, obstruction-free corridor, traversed through normal queues.
        int[][] axes = {{1,0},{0,1},{-1,0},{0,-1}};
        for (int[] axis : axes) {
            boolean corridor = true;
            for (int step=1;step<=4;step++) corridor &= clear(viewerX+axis[0]*step,viewerY+axis[1]*step);
            if (corridor) {
                int step = (tick / 16) % 2 == 0 ? 0 : 4;
                viewer.walk(viewerX+axis[0]*step,viewerY+axis[1]*step); return;
            }
        }
        throw new IllegalStateException("No authored4-tile viewer walk corridor");
    }
    private static void driveActors() {
        if (!busy || actors.isEmpty() || realCombat()) return;
        for (int index = 0; index < actors.size(); index++) {
            Player actor = actors.get(index); Point origin = positions.get(index);
            if (tick % 4 == 0) {
                int x = origin.getX() + (tick % 8 == 0 ? 1 : 0);
                if (clear(x, origin.getY())) actor.walk(x, origin.getY());
            }
            if (index < 8 && tick % 2 == 0) {
                int[] types = {Projectile.THUNDER_BALL, Projectile.ICICLE_SHOT, Projectile.WOOD_LEAD_2, Projectile.ELECTRIC_DISC};
                Npc target = npcs.get(index * 3);
                target.getUpdateFlags().setProjectile(new Projectile(actor, target, types[index % types.length]));
            }
        }
    }
    public static void main(String[] args) throws Exception {
        final Path root = Paths.get(".").toRealPath();
        final Path run = root.getParent();
        if (!Files.isRegularFile(run.resolve("RENDERER-BENCHMARK.json"))) {
            throw new IllegalStateException("Marked renderer fixture required");
        }
        sustainSummonsForBenchmark=new JSONObject(new String(Files.readAllBytes(run.resolve("RENDERER-BENCHMARK.json")),"UTF-8")).optBoolean("sustainSummonsForBenchmark",false);
        com.openrsc.server.config.ServerConfigurationLoadResult loaded = ServerConfigurationLoader.load("myworld.conf");
        com.openrsc.server.ServerConfiguration config = loaded.getLegacyConfiguration();
        if (!"127.0.0.1".equals(config.SERVER_BIND_ADDRESS)
                || loaded.getProcessNetwork().getServerPort() != 43639
                || loaded.getProcessNetwork().getWebsocketPort() != 43539
                || config.DB_TYPE != DatabaseType.SQLITE || !"renderer_benchmark_dev".equals(config.DB_NAME)) {
            throw new IllegalStateException("Renderer-only loopback bindings/database required");
        }
        if (config.WANT_DISCORD_BOT || config.WANT_DISCORD_GENERAL_LOGGING || config.WANT_DISCORD_AUCTION_UPDATES
                || config.WANT_DISCORD_ITEM_SHOWCASE || config.WANT_DISCORD_MONITORING_UPDATES) {
            throw new IllegalStateException("External services must be disabled");
        }
        Files.write(run.resolve("server.pid"), java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes("UTF-8"));
        final Server server = Server.startServer("myworld.conf");
        worldServer = server;
        if (!server.getDatabase().playerExists("renderdemo")) {
            int id = server.getDatabase().createPlayer("renderdemo", "", DataConversions.hashPassword("RendererProof13", null), System.currentTimeMillis() / 1000, "127.0.0.1");
            if (id < 0) throw new IllegalStateException("Disposable account creation failed");
        }
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("inc/sqlite/renderer_benchmark_dev.db"));
             java.sql.PreparedStatement st = c.prepareStatement("UPDATE players SET login_date=1,x=502,y=453 WHERE username=?")) {
            st.setString(1, "renderdemo"); st.executeUpdate();
            // Ordinary account preference, seeded before login and packet26; no renderer flags are forced.
            boolean hideRoofs=new JSONObject(new String(Files.readAllBytes(run.resolve("RENDERER-BENCHMARK.json")),"UTF-8")).optBoolean("hideRoofsPreference",true);
            try(java.sql.PreparedStatement remove=c.prepareStatement("DELETE FROM player_cache WHERE playerID=(SELECT id FROM players WHERE username=?) AND `key`=?")) {
                remove.setString(1,"renderdemo"); remove.setString(2,"setting_showroof"); remove.executeUpdate();
            }
            try(java.sql.PreparedStatement preference=c.prepareStatement("INSERT INTO player_cache (playerID,type,`key`,value) SELECT id,2,?,? FROM players WHERE username=?")) {
                preference.setString(1,"setting_showroof"); preference.setString(2,String.valueOf(hideRoofs)); preference.setString(3,"renderdemo"); preference.executeUpdate();
            }
        }
        server.getGameEventHandler().add(new GameTickEvent(server.getWorld(), null, 1, "Renderer benchmark status", DuplicationStrategy.ONE_PER_SERVER) {
            public void run() {
                try {
                    Player player = server.getWorld().getPlayer(DataConversions.usernameToHash("renderdemo"));
                    if (!configured) {
                        List<Npc> ambient = new ArrayList<Npc>(server.getWorld().getNpcs());
                        removedMapNpcs = ambient.size();
                        for (Npc npc : ambient) server.getWorld().unregisterNpc(npc);
                        configured = true;
                    }
                    Path request = run.resolve("request.json");
                    if (Files.isRegularFile(request)) {
                        JSONObject command = new JSONObject(new String(Files.readAllBytes(request), "UTF-8"));
                        Files.delete(request); removeActors();
                        busy = command.optBoolean("busy", false); phase = command.optString("phase", "dense-idle");
                        viewerX = command.optInt("x", 502); viewerY = command.optInt("y", 453);
                        viewer = null; tick = 0;
                        server.getWorldDayNightClock().setCurrentCycleMillis(phase.equals("night") ? 2700000 : 900000);
                        if (player != null) ActionSender.sendWorldTime(player);
                    }
                    if (player != viewer) {
                        removeActors(); viewer = player; tick = 0;
                        if (viewer != null) {
                            if (viewerX != 502 || viewerY != 453) {
                                if (!clear(viewerX, viewerY)) throw new IllegalStateException("Requested visual camera tile is not authored walkable ground");
                                viewer.teleport(viewerX, viewerY, false);
                            }
                            if (busy) {createActors();if(realCombat())beginRealCombat();}
                        }
                    }
                    if(viewer!=null && phase.equals("lifecycle") && Files.isRegularFile(run.resolve("motion-request"))) {
                        String motion=new String(Files.readAllBytes(run.resolve("motion-request")),"UTF-8").trim();Files.delete(run.resolve("motion-request"));
                        int x=502,y=453,level=0;
                        if(motion.equals("same")){x=viewer.getX();y=viewer.getY();level=viewer.getWorldLocation().getCoordinate().getLevel();samePositionTeleports++;}
                        else if(motion.equals("area")){x=478;y=453;areaTeleports++;}
                        else if(motion.equals("level")){x=685;y=508;level=2;levelTeleports++;}
                        else if(!motion.equals("return"))throw new IllegalStateException("Unexpected private motion request");
                        if(!motion.equals("same")) {
                            boolean found=false;
                            for(int radius=0;radius<=5&&!found;radius++)for(int dx=-radius;dx<=radius&&!found;dx++)for(int dy=-radius;dy<=radius&&!found;dy++) {
                                com.openrsc.server.model.world.region.TileValue tile=server.getWorld().getTile(com.openrsc.server.model.world.coordinate.WorldLocation.global(new com.openrsc.server.model.world.coordinate.WorldCoordinate(x+dx,y+dy,level)));
                                if((tile.traversalMask&255)==0 && (tile.overlay&255)!=10){x+=dx;y+=dy;found=true;}
                            }
                            if(!found)throw new IllegalStateException("No authored walkable lifecycle destination near "+x+","+y+","+level);
                        }
                        viewer.teleport(x,y,level,false);
                    }
                    if(viewer!=null && phase.equals("lifecycle") && Files.isRegularFile(run.resolve("logout-request"))) {
                        Files.delete(run.resolve("logout-request"));viewer.logout();
                    }
                    driveActors(); driveViewer(); tick++;
                    if (viewer != null && phase.equals("same-position") && tick == 50) {
                        viewer.teleport(viewer.getX(), viewer.getY(), false); samePositionTeleports++;
                        System.out.println("BENCHMARK same-position teleport x="+viewer.getX()+" y="+viewer.getY()+" tick="+tick);
                    }
                    if (viewer != null && phase.equals("transition") && (tick == 50 || tick == 100)) {
                        int targetX = tick == 50 ? 478 : 502;
                        boolean moved = false;
                        for (int radius = 0; radius <= 5 && !moved; radius++) {
                            for (int dx = -radius; dx <= radius && !moved; dx++) for (int dy = -radius; dy <= radius && !moved; dy++) {
                                if (clear(targetX + dx, 453 + dy)) { viewer.teleport(targetX + dx, 453 + dy, false); moved = true; }
                            }
                        }
                        if (!moved) throw new IllegalStateException("No valid authored transition landing");
                    }
                    JSONObject combat=combatState();
                    JSONObject state = new JSONObject().put("online", player != null).put("private", true);
                    if (player != null) state.put("x", player.getX()).put("y", player.getY()).put("level", player.getWorldLocation().getCoordinate().getLevel());
                    state.put("phase", phase).put("phaseTick", tick).put("samePositionTeleports", samePositionTeleports).put("areaTeleports",areaTeleports).put("levelTeleports",levelTeleports).put("controlledPlayers", actors.size()).put("controlledNpcs", npcs.size())
                        .put("summonsRequested", actors.isEmpty() ? 0 : 8).put("visualProjectileEmitters", actors.isEmpty() || realCombat() ? 0 : 8)
                        .put("visualProjectilesDamage", false).put("sustainSummonsForBenchmark",sustainSummonsForBenchmark).put("realCombat",combat).put("removedMapNpcs", removedMapNpcs)
                        .put("cycleMillis", server.getWorldDayNightClock().getCurrentCycleMillis())
                        .put("cycleLengthMillis", server.getWorldDayNightClock().getCycleMillis()).put("cycleRate", server.getWorldDayNightClock().getRateMultiplier());
                    Path next = run.resolve("status.next"); Files.write(next, state.toString().getBytes("UTF-8"));
                    Files.move(next, run.resolve("status.json"), StandardCopyOption.REPLACE_EXISTING);
                    if(realCombat()&&viewer!=null&&tick>=48&&!combat.optBoolean("workloadComplete",false))
                        throw new IllegalStateException("Incomplete real combat workload after48ticks: "+combat);
                } catch (Exception error) {
                    System.err.println("RENDERER_FIXTURE_SETUP_FAILURE " + error);
                    error.printStackTrace();
                    try { Files.write(run.resolve("FIXTURE-FAILED"), error.toString().getBytes("UTF-8")); }
                    catch (Exception writeFailure) { writeFailure.printStackTrace(); }
                }
            }
        });
        boolean stopping = false;
        while (server.isRunning()) {
            if (!stopping && Files.exists(run.resolve("STOP"))) {
                stopping = true; Server.closeProcess(5, "Private renderer benchmark closing");
            }
            server.checkShutdown(); Thread.sleep(500);
        }
        Files.deleteIfExists(run.resolve("server.pid"));
        System.exit(0);
    }
}
