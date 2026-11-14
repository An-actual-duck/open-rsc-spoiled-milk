package com.openrsc.server.combat;

import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemStatus;
import com.openrsc.server.model.entity.player.Player;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Production assignment/inventory/cache transactions over an offline combat harness. */
public final class SlayerTaskSupplyFixture {
    private static final Path DEFINITIONS = Paths.get("conf/server/defs/extras/MonsterSlayer.json");
    private static int serial, checks;
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass a disposable test output directory");
        Path output = Paths.get(args[0]);Files.createDirectories(output);
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            MonsterSlayerData.ReferenceCatalog catalog = new MonsterSlayerData.ReferenceCatalog() {
                public boolean npcExists(int id) { return true; }
                public boolean npcAttackable(int id) { return true; }
                public boolean npcSpawned(int id) { return true; }
                public boolean itemExists(int id) { return h.server().getEntityHandler().getItemDef(id) != null; }
            };
            MonsterSlayerData data = MonsterSlayerData.load(DEFINITIONS, catalog, true);h.installMonsterSlayerData(data);
            allGimmickAssignmentsAndReminders(h, data);
            claimsSurviveItemLossAndReload(h, data);
            sharedItemAndExistingOwnership(h, data);
            fullInventoryAndUnsupportedClient(h, data);
            cacheFailureRestoresItemsAndState(h, data);
            concurrentRequestsGrantExactlyOnce(h, data);
            multiCounterTasksAreAtomic(h, catalog, output);
            System.out.println("PASS Slayer assignment supplies: " + checks + " assertions; all seven gimmick tasks, first-only mandatory/repeatable grants, shared-item claims, cancellation, item loss/banking and serialized cache reload, reminder protection, full inventory, unsupported client, failed/partial grants, cache rollback, metadata preservation and concurrent assignment");
        }
    }
    private static void allGimmickAssignmentsAndReminders(CurrentCombatHarness h, MonsterSlayerData data) {
        MonsterSlayerTaskService service = new MonsterSlayerTaskService(data);int families=0;
        Map<String,Integer> expected = new LinkedHashMap<String,Integer>();
        expected.put("giant_frog",3318);expected.put("cockatrice",3321);expected.put("banshee",3324);
        expected.put("stirge",3462);expected.put("terror_dog",3327);expected.put("dark_beast",3330);expected.put("abyssal_demon",3318);
        for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) {
            for (MonsterSlayerDefinitions.Task task : contact.getMandatoryTasks()) {
                List<Integer> supplies = MonsterSlayerTaskSupplies.itemIds(task);
                check(supplies.equals(expected.containsKey(task.getFamilyKey())
                    ? Collections.singletonList(expected.get(task.getFamilyKey())) : Collections.emptyList()),
                    "actual encounter receives its matching combat counter: " + task.getFamilyKey());
                if (supplies.isEmpty()) continue;
                families++;
                Player mandatory = ready(h, data, contact, task, false);
                check(service.assignMandatory(mandatory, contact.getKey()).isAccepted(), "mandatory assigned " + task.getKey());
                for (int id : supplies) {
                    check(mandatory.getCarriedItems().getInventory().countId(id)==1,"one full counter " + id);
                    check(Boolean.TRUE.equals(cache(mandatory).get("monster_slayer_starter_"+id)),"claim for delivered full three-use item");
                    check(!h.server().getEntityHandler().getItemDef(id).isStackable(),"full counter nonstackable");
                }
                check(service.assignMandatory(mandatory, contact.getKey()).getReason()==MonsterSlayerState.TaskResult.Reason.ACTIVE_TASK,"reminder is not another assignment");
                check(new MonsterSlayerTaskService(data).assignMandatory(mandatory,contact.getKey()).getReason()==MonsterSlayerState.TaskResult.Reason.ACTIVE_TASK,"new service/reconnect cannot grant again");
                for(int id:supplies)check(mandatory.getCarriedItems().getInventory().countId(id)==1,"no duplicate reminder supply");
                MonsterSlayerDefinitions.Task repeatable = data.getTask(task.getKey()+".repeatable");
                Player repeat = ready(h,data,contact,repeatable,true);
                check(service.assignRepeatable(repeat,contact.getKey(),repeatable.getKey()).isAccepted(),"repeatable assigned");
                check(service.completeActiveTaskForDevelopment(repeat).getReason()==MonsterSlayerState.TaskResult.Reason.COMPLETED,"repeatable completed through normal transition");
                check(service.assignRepeatable(repeat,contact.getKey(),repeatable.getKey()).isAccepted(),"next repeatable assigned");
                for(int id:supplies)check(repeat.getCarriedItems().getInventory().countId(id)==1,"later assignments do not repeat the starter gift");
            }
        }
        check(families==7,"all seven implemented gimmick encounters covered, including shared solvent");
        for(MonsterSlayerDefinitions.Contact contact:data.getContactsInChallengeOrder())for(MonsterSlayerDefinitions.Task task:contact.getMandatoryTasks())
            if(task.getHazards().contains(MonsterSlayerHazard.NAGA_STRATEGY)||task.getHazards().contains(MonsterSlayerHazard.BLOODVELD_STRATEGY))
                check(MonsterSlayerTaskSupplies.itemIds(task).isEmpty(),"strategy-only task grants no counter");
    }
    private static void claimsSurviveItemLossAndReload(CurrentCombatHarness h, MonsterSlayerData data) throws Exception {
        MonsterSlayerTaskService service = new MonsterSlayerTaskService(data);
        MonsterSlayerDefinitions.Contact contact = data.getContact("falador");
        MonsterSlayerDefinitions.Task task = data.getTask("falador.giant_frog");
        for (String destination : Arrays.asList("dropped", "sold", "banked", "consumed")) {
            Player p = ready(h, data, contact, task, false);
            check(service.assignMandatory(p, "falador").isAccepted(), "first grant before " + destination);
            check(Boolean.TRUE.equals(cache(p).get("monster_slayer_starter_3318")), "successful delivery records persistent item claim");
            Item gift = p.getCarriedItems().getInventory().getItems().remove(0);
            if (destination.equals("banked")) {
                p.setBank(new com.openrsc.server.model.container.Bank(p));
                check(p.getBank().add(gift, false), "gift moved to bank");
            }
            MonsterSlayerState.write(p.getCache(), data, MonsterSlayerState.cancelTask(MonsterSlayerState.read(p.getCache(), data), data, task.getKey()));
            check(service.assignMandatory(p, "falador").isAccepted(), "cancel/reassign after " + destination);
            check(p.getCarriedItems().getInventory().countId(3318) == 0, "no replacement after " + destination);
            // Exercise the database cache's persisted text/type representation in a fresh player.
            Player reloaded = h.player("tasksupply" + serial++, 450, 450);
            reloaded.getClientLimitations().maxItemId = Integer.MAX_VALUE;
            for (Map.Entry<String,Object> entry : cache(p).entrySet()) {
                Object value = entry.getValue(); String text = value.toString();
                Object decoded;
                if (value instanceof Boolean) decoded = Boolean.parseBoolean(text);
                else if (value instanceof Integer) decoded = Integer.valueOf(text);
                else if (value instanceof Long) decoded = Long.valueOf(text);
                else decoded = text;
                reloaded.getCache().put(entry.getKey(), decoded);
                if (entry.getKey().startsWith("monster_slayer_starter_"))
                    check(entry.getKey().length() <= 32 && text.length() <= 150, "starter claim fits persistence schema");
            }
            MonsterSlayerState.write(reloaded.getCache(), data, MonsterSlayerState.cancelTask(MonsterSlayerState.read(reloaded.getCache(), data), data, task.getKey()));
            while (reloaded.getCarriedItems().getInventory().size() < reloaded.getCarriedItems().getInventory().getCapacity())
                check(reloaded.getCarriedItems().getInventory().add(new Item(259), false), "fill reload inventory");
            check(new MonsterSlayerTaskService(data).assignMandatory(reloaded, "falador").isAccepted(), "claimed task needs no spare slot after reload");
            check(reloaded.getCarriedItems().getInventory().countId(3318) == 0, "reload cannot reopen entitlement");
        }
    }
    private static void sharedItemAndExistingOwnership(CurrentCombatHarness h, MonsterSlayerData data) {
        MonsterSlayerTaskService service = new MonsterSlayerTaskService(data);
        Player p = ready(h, data, data.getContact("falador"), data.getTask("falador.giant_frog"), false);
        p.getCarriedItems().getInventory().add(new Item(3318), false);
        check(service.assignMandatory(p, "falador").isAccepted(), "existing purchased/untracked item does not consume starter entitlement");
        check(p.getCarriedItems().getInventory().countId(3318) == 2, "one starter in addition to existing ownership");
        for (MonsterSlayerDefinitions.Contact contact : data.getContactsInChallengeOrder()) {
            for (MonsterSlayerDefinitions.Task task : contact.getMandatoryTasks()) {
                if (!task.getFamilyKey().equals("abyssal_demon")) continue;
                Player progress = ready(h, data, contact, task, false);
                MonsterSlayerState.write(p.getCache(), data, MonsterSlayerState.read(progress.getCache(), data));
                check(service.assignMandatory(p, contact.getKey()).isAccepted(), "related solvent task assigned");
                check(p.getCarriedItems().getInventory().countId(3318) == 2, "different task sharing solvent grants no second starter");
            }
        }
    }
    private static void fullInventoryAndUnsupportedClient(CurrentCombatHarness h,MonsterSlayerData data) {
        MonsterSlayerDefinitions.Contact contact=data.getContact("falador");MonsterSlayerDefinitions.Task task=data.getTask("falador.giant_frog");
        Player full=ready(h,data,contact,task,false);while(full.getCarriedItems().getInventory().size()<full.getCarriedItems().getInventory().getCapacity())full.getCarriedItems().getInventory().add(new Item(259),false);
        Map<String,Object> before=cache(full);List<Item> original=new ArrayList<Item>(full.getCarriedItems().getInventory().getItems());
        check(new MonsterSlayerTaskService(data).assignMandatory(full,"falador").getReason()==MonsterSlayerState.TaskResult.Reason.INVENTORY_FULL,"full inventory blocks both assignment and gift");
        check(cache(full).equals(before)&&full.getCarriedItems().getInventory().getItems().equals(original),"full inventory unchanged");
        full.getCarriedItems().getInventory().getItems().remove(0);
        check(new MonsterSlayerTaskService(data).assignMandatory(full,"falador").isAccepted(),"freeing a slot retains initial entitlement");
        check(full.getCarriedItems().getInventory().countId(3318)==1,"capacity retry grants exactly once");
        Player old=ready(h,data,contact,task,false);old.getClientLimitations().maxItemId=3317;before=cache(old);
        check(new MonsterSlayerTaskService(data).assignMandatory(old,"falador").getReason()==MonsterSlayerState.TaskResult.Reason.SUPPLIES_UNAVAILABLE,"unsupported client cannot spill supply onto ground");check(cache(old).equals(before)&&old.getCarriedItems().getInventory().size()==0,"unsupported client state unchanged");
        Player normal=ready(h,data,contact,contact.getMandatoryTasks().get(0),false);check(MonsterSlayerTaskSupplies.itemIds(contact.getMandatoryTasks().get(0)).isEmpty(),"first ordinary task has no counter");
        while(normal.getCarriedItems().getInventory().size()<normal.getCarriedItems().getInventory().getCapacity())normal.getCarriedItems().getInventory().add(new Item(259),false);
        check(new MonsterSlayerTaskService(data).assignMandatory(normal,"falador").isAccepted(),"ordinary task needs no spare gift slot");
    }
    private static void cacheFailureRestoresItemsAndState(CurrentCombatHarness h,MonsterSlayerData data)throws Exception {
        Player p=ready(h,data,data.getContact("falador"),data.getTask("falador.giant_frog"),false);
        p.getCarriedItems().getInventory().add(new Item(259),false);Item original=p.getCarriedItems().getInventory().getItems().get(0);original.getItemStatus().setDurability(83);ItemStatus status=original.getItemStatus();long identity=original.getItemId();
        FailingCache failing=new FailingCache();failing.getCacheMap().putAll(p.getCache().getCacheMap());failing.store("unrelated","preserve");Field field=Player.class.getDeclaredField("cache");field.setAccessible(true);field.set(p,failing);Map<String,Object> before=cache(p);failing.fail=true;
        check(new MonsterSlayerTaskService(data).assignMandatory(p,"falador").getReason()==MonsterSlayerState.TaskResult.Reason.SUPPLIES_FAILED,"partial cache failure rejects assignment");
        check(cache(p).equals(before),"owned cache keys restored and unrelated evidence retained");check(p.getCarriedItems().getInventory().size()==1&&p.getCarriedItems().getInventory().getItems().get(0)==original,"exact item and order restored");
        check(original.getItemId()==identity&&original.getItemStatus()==status&&status.getDurability()==83,"identity/status/durability preserved");check(p.getCarriedItems().getInventory().countId(3318)==0,"failed cache write leaves no free counter");
        check(new MonsterSlayerTaskService(data).assignMandatory(p,"falador").isAccepted(),"retry after cache failure grants once");check(p.getCarriedItems().getInventory().countId(3318)==1,"retry gets exactly one counter");
        Player claimPlayer=ready(h,data,data.getContact("falador"),data.getTask("falador.giant_frog"),false);
        FailingCache claimCache=new FailingCache();claimCache.getCacheMap().putAll(cache(claimPlayer));field.set(claimPlayer,claimCache);before=cache(claimPlayer);claimCache.failClaim=true;
        check(new MonsterSlayerTaskService(data).assignMandatory(claimPlayer,"falador").getReason()==MonsterSlayerState.TaskResult.Reason.SUPPLIES_FAILED,"failure after claim store rejects assignment");
        check(cache(claimPlayer).equals(before)&&claimPlayer.getCarriedItems().getInventory().size()==0,"claim failure rolls back claim, task and inventory");
        check(new MonsterSlayerTaskService(data).assignMandatory(claimPlayer,"falador").isAccepted(),"claim write failure preserves entitlement");
    }
    private static void concurrentRequestsGrantExactlyOnce(CurrentCombatHarness h,MonsterSlayerData data)throws Exception {
        Player p=ready(h,data,data.getContact("falador"),data.getTask("falador.giant_frog"),false);CountDownLatch start=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<Throwable>();List<MonsterSlayerState.TaskResult> results=Collections.synchronizedList(new ArrayList<MonsterSlayerState.TaskResult>());
        Runnable request=()->{try{start.await();results.add(new MonsterSlayerTaskService(data).assignMandatory(p,"falador"));}catch(Throwable ex){failure.set(ex);}};
        Thread first=new Thread(request),second=new Thread(request);first.start();second.start();start.countDown();first.join(5000);second.join(5000);check(!first.isAlive()&&!second.isAlive()&&failure.get()==null,"concurrent requests finish safely");
        check(results.size()==2&&results.stream().filter(MonsterSlayerState.TaskResult::isAccepted).count()==1,"one concurrent assignment commits");check(p.getCarriedItems().getInventory().countId(3318)==1,"one concurrent gift");
    }
    private static void multiCounterTasksAreAtomic(CurrentCombatHarness h,MonsterSlayerData.ReferenceCatalog catalog,Path output)throws Exception {
        Files.copy(DEFINITIONS,output.resolve("MonsterSlayer.json"),StandardCopyOption.REPLACE_EXISTING);
        Files.copy(DEFINITIONS.resolveSibling("MonsterSlayerRelease.json"),output.resolve("MonsterSlayerRelease.json"),StandardCopyOption.REPLACE_EXISTING);
        Files.copy(DEFINITIONS.resolveSibling("MonsterSlayerStirge.json"),output.resolve("MonsterSlayerStirge.json"),StandardCopyOption.REPLACE_EXISTING);
        JSONObject tower=new JSONObject(new String(Files.readAllBytes(DEFINITIONS.resolveSibling("MonsterSlayerTower.json")),StandardCharsets.UTF_8));
        tower.getJSONArray("additions").getJSONObject(0).getJSONObject("task").put("hazards",new JSONArray().put("FROG_SPIT").put("STICKY_FLESH").put("STONY_GLARE"));
        Files.write(output.resolve("MonsterSlayerTower.json"),tower.toString().getBytes(StandardCharsets.UTF_8));MonsterSlayerData data=MonsterSlayerData.load(output.resolve("MonsterSlayer.json"),catalog,true);h.installMonsterSlayerData(data);
        MonsterSlayerDefinitions.Contact contact=data.getContact("falador");MonsterSlayerDefinitions.Task task=data.getTask("falador.giant_frog");check(MonsterSlayerTaskSupplies.itemIds(task).equals(Arrays.asList(3318,3321)),"shared solvent requirement deduplicated");
        Player p=ready(h,data,contact,task,false);Map<String,Object> before=cache(p);final int[] adds={0};MonsterSlayerTaskService fail=new MonsterSlayerTaskService(data,(staged,id)->++adds[0]==1?staged.add(new Item(id),false):false);
        check(fail.assignMandatory(p,"falador").getReason()==MonsterSlayerState.TaskResult.Reason.SUPPLIES_FAILED,"second staged grant failure refuses whole task");check(cache(p).equals(before)&&p.getCarriedItems().getInventory().size()==0,"partial staged grant never escapes");
        MonsterSlayerTaskService dishonest=new MonsterSlayerTaskService(data,(staged,id)->true);check(!dishonest.assignMandatory(p,"falador").isAccepted()&&p.getCarriedItems().getInventory().size()==0,"grant success without item cannot commit assignment");
        MonsterSlayerTaskService throwing=new MonsterSlayerTaskService(data,(staged,id)->{staged.add(new Item(id),false);throw new IllegalStateException("injected");});check(!throwing.assignMandatory(p,"falador").isAccepted()&&cache(p).equals(before)&&p.getCarriedItems().getInventory().size()==0,"exception after staged add is atomic");
        check(new MonsterSlayerTaskService(data).assignMandatory(p,"falador").isAccepted(),"multi-counter assignment commits together");check(p.getCarriedItems().getInventory().countId(3318)==1&&p.getCarriedItems().getInventory().countId(3321)==1,"one of each distinct full item");
        Player partlyClaimed=ready(h,data,contact,task,false);partlyClaimed.getCache().store("monster_slayer_starter_3318",Boolean.TRUE);
        while(partlyClaimed.getCarriedItems().getInventory().size()<partlyClaimed.getCarriedItems().getInventory().getCapacity()-1)partlyClaimed.getCarriedItems().getInventory().add(new Item(259),false);
        MonsterSlayerState.TaskResult partialResult=new MonsterSlayerTaskService(data).assignMandatory(partlyClaimed,"falador");
        check(partialResult.isAccepted(),"partly claimed task needs room only for its new starter");
        check(partialResult.getGrantedStarterItems().equals(Collections.singletonList(3321)),"receipt contains only newly delivered starter");
        check(partlyClaimed.getCarriedItems().getInventory().countId(3318)==0&&partlyClaimed.getCarriedItems().getInventory().countId(3321)==1,"claimed solvent is not replenished alongside new eye drops");
    }
    private static Player ready(CurrentCombatHarness h,MonsterSlayerData data,MonsterSlayerDefinitions.Contact contact,MonsterSlayerDefinitions.Task task,boolean repeatable) {
        Player p=h.player("tasksupply"+serial++,450,450);p.getClientLimitations().maxItemId=Integer.MAX_VALUE;Map<String,Integer> cursors=new LinkedHashMap<String,Integer>();
        for(MonsterSlayerDefinitions.Contact other:data.getContactsInChallengeOrder())cursors.put(other.getKey(),other.getChallenge().getCode()<contact.getChallenge().getCode()?other.getMandatoryTasks().size():other==contact?(repeatable?contact.getMandatoryTasks().size():contact.getMandatoryTasks().indexOf(task)):0);
        MonsterSlayerState.write(p.getCache(),data,MonsterSlayerState.create(2,repeatable?contact.getAwardedRank():contact.getRequiredRank(),MonsterSlayerBalances.zero(),cursors,null,0,50,0,1,MonsterSlayerState.LegacyStatus.NONE,0,data));return p;
    }
    private static Map<String,Object> cache(Player p){return new LinkedHashMap<String,Object>(p.getCache().getCacheMap());}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class FailingCache extends Cache {
        boolean fail;
        boolean failClaim;
        @Override public void store(String key, Boolean value) { super.store(key,value); if(failClaim && key.startsWith("monster_slayer_starter_")) {failClaim=false;throw new IllegalStateException("injected partial claim write");} }
        @Override public void store(String key,String value){super.store(key,value);if(fail&&key.equals("monster_slayer_active_task")){fail=false;throw new IllegalStateException("injected partial cache write");}}
    }
}
